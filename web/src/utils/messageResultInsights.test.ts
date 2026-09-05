/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import { describe, expect, it } from 'vitest';
import type { MessageRecord } from '../api/message';
import { buildMessageResultInsights, filterMessageDimensionRows } from './messageResultInsights';

const message = (overrides: Partial<MessageRecord> = {}): MessageRecord => ({
  msgId: 'msg-1',
  topic: 'orders',
  tag: 'created',
  key: 'order-1',
  brokerName: 'broker-a',
  queueId: 0,
  queueOffset: 10,
  body: '{}',
  storeTime: '2026-09-05T01:15:00Z',
  bornHost: 'producer-a:8080',
  storeHost: 'broker-a:10911',
  properties: {},
  size: 512,
  ...overrides,
});

describe('message result insights', () => {
  it('summarizes loaded and server result coverage', () => {
    const result = buildMessageResultInsights([message(), message({ msgId: 'msg-2' })], 10);
    expect(result.summary).toMatchObject({ loadedMessages: 2, serverTotal: 10, loadedPercent: 20 });
  });

  it('groups tags with deterministic percentages and bytes', () => {
    const result = buildMessageResultInsights([
      message({ msgId: '1', tag: 'created', size: 512 }),
      message({ msgId: '2', tag: 'created', size: 1024 }),
      message({ msgId: '3', tag: 'paid', size: 2048 }),
    ]);
    expect(result.dimensions.filter((row) => row.dimension === 'TAG')).toEqual([
      { dimension: 'TAG', value: 'created', count: 2, percent: 66.67, bytes: 1536 },
      { dimension: 'TAG', value: 'paid', count: 1, percent: 33.33, bytes: 2048 },
    ]);
  });

  it('builds Broker and queue dimensions independently', () => {
    const result = buildMessageResultInsights([
      message(),
      message({ msgId: '2', queueId: 1 }),
      message({ msgId: '3', brokerName: 'broker-b', queueId: 0 }),
    ]);
    expect(result.summary).toMatchObject({ uniqueBrokers: 2, uniqueQueues: 3 });
    expect(result.dimensions.filter((row) => row.dimension === 'BROKER')).toHaveLength(2);
    expect(result.dimensions.filter((row) => row.dimension === 'QUEUE')).toHaveLength(3);
  });

  it('groups valid timestamps by UTC hour and reports the time span', () => {
    const result = buildMessageResultInsights([
      message({ storeTime: '2026-09-05T01:15:00Z' }),
      message({ msgId: '2', storeTime: '2026-09-05T01:55:00Z' }),
      message({ msgId: '3', storeTime: '2026-09-05T03:00:00Z' }),
    ]);
    expect(result.dimensions.filter((row) => row.dimension === 'HOUR')[0]).toMatchObject({
      value: '2026-09-05T01:00Z',
      count: 2,
    });
    expect(result.summary.firstStoreTime).toBe(Date.parse('2026-09-05T01:15:00Z'));
    expect(result.summary.lastStoreTime).toBe(Date.parse('2026-09-05T03:00:00Z'));
  });

  it('classifies message sizes into ordered buckets', () => {
    const result = buildMessageResultInsights([
      message({ size: 100 }),
      message({ msgId: '2', size: 1024 }),
      message({ msgId: '3', size: 20 * 1024 }),
      message({ msgId: '4', size: 200 * 1024 }),
      message({ msgId: '5', size: 2 * 1024 * 1024 }),
    ]);
    expect(result.sizeBuckets.map((bucket) => bucket.count)).toEqual([1, 1, 1, 1, 1]);
    expect(result.summary.largestBytes).toBe(2 * 1024 * 1024);
  });

  it('does not let non-finite or negative sizes corrupt totals', () => {
    const result = buildMessageResultInsights([
      message({ size: Number.NaN }),
      message({ msgId: '2', size: -1 }),
    ]);
    expect(result.summary).toMatchObject({ totalBytes: 0, averageBytes: 0, largestBytes: 0 });
  });

  it('counts missing routing and optional metadata', () => {
    const result = buildMessageResultInsights([
      message({ key: null, tag: null, brokerName: null, queueId: null }),
    ]);
    expect(result.summary).toMatchObject({ missingKeys: 1, missingTags: 1, missingRoutes: 1 });
    expect(result.dimensions.find((row) => row.dimension === 'QUEUE')).toMatchObject({
      value: '(missing)',
    });
  });

  it('counts invalid timestamps without inventing a range', () => {
    const result = buildMessageResultInsights([message({ storeTime: 'not-a-time' })]);
    expect(result.summary).toMatchObject({
      invalidTimestamps: 1,
      firstStoreTime: null,
      lastStoreTime: null,
    });
  });

  it('filters dimensions and values case-insensitively', () => {
    const result = buildMessageResultInsights([message({ brokerName: 'Broker-PROD' })]);
    expect(filterMessageDimensionRows(result.dimensions, 'BROKER')).toHaveLength(1);
    expect(filterMessageDimensionRows(result.dimensions, undefined, 'broker-prod')).toHaveLength(2);
    expect(filterMessageDimensionRows(result.dimensions, 'TAG', 'missing')).toHaveLength(0);
  });

  it('returns stable zero-valued insight data for no results', () => {
    const result = buildMessageResultInsights([], 0);
    expect(result.summary).toMatchObject({
      loadedMessages: 0,
      serverTotal: 0,
      loadedPercent: 0,
      totalBytes: 0,
      firstStoreTime: null,
      lastStoreTime: null,
    });
    expect(result.sizeBuckets).toHaveLength(5);
  });
});
