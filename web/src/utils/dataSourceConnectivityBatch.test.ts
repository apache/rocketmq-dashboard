/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import { describe, expect, it, vi } from 'vitest';
import type { DataSource } from '../api/settings';
import {
  checkDataSourceConnectivity,
  dataSourceNeedsRuntimeSecret,
  filterDataSourceConnectivity,
} from './dataSourceConnectivityBatch';

const source = (key: string, overrides: Partial<DataSource> = {}): DataSource => ({
  key,
  name: key,
  type: 'Prometheus',
  url: `http://${key}:9090`,
  auth: 'None',
  instanceIds: [],
  ...overrides,
});

describe('data source connectivity batch', () => {
  it('recognizes both credential-bearing authentication modes', () => {
    expect(dataSourceNeedsRuntimeSecret(source('basic', { auth: 'Basic Auth' }))).toBe(true);
    expect(dataSourceNeedsRuntimeSecret(source('bearer', { auth: 'Bearer Token' }))).toBe(true);
    expect(dataSourceNeedsRuntimeSecret(source('none'))).toBe(false);
  });

  it('tests unauthenticated sources and summarizes successes', async () => {
    const test = vi.fn().mockResolvedValue({ success: true, message: 'connected' });
    const result = await checkDataSourceConnectivity([source('one'), source('two')], test);
    expect(test).toHaveBeenCalledTimes(2);
    expect(test).toHaveBeenCalledWith({ type: 'Prometheus', url: 'http://one:9090', auth: 'None' });
    expect(result.summary).toMatchObject({ total: 2, tested: 2, succeeded: 2, failed: 0 });
  });

  it('never sends an authenticated source without runtime credentials', async () => {
    const test = vi.fn();
    const result = await checkDataSourceConnectivity(
      [
        source('basic', { auth: 'Basic Auth', username: 'saved-name' }),
        source('bearer', { auth: 'Bearer Token' }),
      ],
      test,
    );
    expect(test).not.toHaveBeenCalled();
    expect(result.results.map((item) => item.status)).toEqual(['SKIPPED_AUTH', 'SKIPPED_AUTH']);
    expect(result.summary).toMatchObject({ tested: 0, skippedAuth: 2, averageLatencyMs: null });
  });

  it('records business failures returned by the endpoint', async () => {
    const result = await checkDataSourceConnectivity([source('down')], async () => ({
      success: false,
      message: 'connection refused',
    }));
    expect(result.results[0]).toMatchObject({ status: 'FAILED', message: 'connection refused' });
  });

  it('records rejected requests and continues the batch', async () => {
    const result = await checkDataSourceConnectivity(
      [source('down'), source('up')],
      async (item) => {
        if (item.url.includes('down')) throw new Error('timeout');
        return { success: true, message: 'ok' };
      },
    );
    expect(result.summary).toMatchObject({ succeeded: 1, failed: 1 });
    expect(result.results[0]).toMatchObject({ key: 'down', status: 'FAILED', message: 'timeout' });
  });

  it('deduplicates source keys before testing', async () => {
    const test = vi.fn().mockResolvedValue({ success: true, message: 'ok' });
    const result = await checkDataSourceConnectivity([source('same'), source('same')], test);
    expect(test).toHaveBeenCalledTimes(1);
    expect(result.summary.total).toBe(1);
  });

  it('respects the configured concurrency bound', async () => {
    let active = 0;
    let maximum = 0;
    const releases: Array<() => void> = [];
    const test = vi.fn(async () => {
      active += 1;
      maximum = Math.max(maximum, active);
      await new Promise<void>((resolve) => releases.push(resolve));
      active -= 1;
      return { success: true, message: 'ok' };
    });
    const promise = checkDataSourceConnectivity(
      [source('1'), source('2'), source('3'), source('4')],
      test,
      2,
    );
    await vi.waitFor(() => expect(test).toHaveBeenCalledTimes(2));
    releases.splice(0).forEach((release) => release());
    await vi.waitFor(() => expect(test).toHaveBeenCalledTimes(4));
    releases.splice(0).forEach((release) => release());
    await promise;
    expect(maximum).toBe(2);
  });

  it('calculates deterministic latency aggregates with an injected clock', async () => {
    const times = [100, 120, 200, 260];
    const result = await checkDataSourceConnectivity(
      [source('one'), source('two')],
      async () => ({ success: true, message: 'ok' }),
      1,
      () => times.shift() ?? 0,
    );
    expect(result.summary).toMatchObject({ averageLatencyMs: 40, slowestLatencyMs: 60 });
  });

  it('retains applicable instance counts', async () => {
    const result = await checkDataSourceConnectivity(
      [source('global'), source('scoped', { instanceIds: ['a', 'b'] })],
      async () => ({ success: true, message: 'ok' }),
    );
    expect(result.results.find((item) => item.key === 'global')?.instanceCount).toBe(0);
    expect(result.results.find((item) => item.key === 'scoped')?.instanceCount).toBe(2);
  });

  it('filters across metadata, URL, result message, and status', async () => {
    const batch = await checkDataSourceConnectivity(
      [source('prod', { name: 'Production Prometheus' }), source('dr', { type: 'Thanos' })],
      async (item) => ({
        success: !item.url.includes('dr'),
        message: item.url.includes('dr') ? 'timeout' : 'ok',
      }),
    );
    expect(filterDataSourceConnectivity(batch.results, 'production')).toHaveLength(1);
    expect(filterDataSourceConnectivity(batch.results, 'THANOS')).toHaveLength(1);
    expect(filterDataSourceConnectivity(batch.results, 'timeout')).toHaveLength(1);
    expect(filterDataSourceConnectivity(batch.results, '', 'FAILED')).toHaveLength(1);
  });

  it('returns a stable empty batch', async () => {
    const result = await checkDataSourceConnectivity([], vi.fn());
    expect(result).toEqual({
      results: [],
      summary: {
        total: 0,
        tested: 0,
        succeeded: 0,
        failed: 0,
        skippedAuth: 0,
        averageLatencyMs: null,
        slowestLatencyMs: null,
      },
    });
  });
});
