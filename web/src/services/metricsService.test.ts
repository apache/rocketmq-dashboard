// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.

import { afterEach, describe, expect, it, vi } from 'vitest';

const { isMockModeMock } = vi.hoisted(() => ({ isMockModeMock: vi.fn(() => true) }));

vi.mock('./dataMode', () => ({ isMockMode: () => isMockModeMock() }));
vi.mock('../api/metrics', () => ({
  listMetricDataSources: vi.fn(),
  createMetricDataSource: vi.fn(),
  updateMetricDataSource: vi.fn(),
  deleteMetricDataSource: vi.fn(),
  queryMetricDataSource: vi.fn(),
}));

import * as api from '../api/metrics';
import type { MetricsDataSource } from '../api/metrics';

async function loadService() {
  vi.resetModules();
  return import('./metricsService');
}

function source(name: string, providerType: MetricsDataSource['providerType']): MetricsDataSource {
  return { name, providerType, url: `http://${name}:9090`, authType: 'none', enabled: true };
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('metricsService (mock mode)', () => {
  it('lists the seeded data sources for every backend type', async () => {
    isMockModeMock.mockReturnValue(true);
    const svc = await loadService();

    const sources = await svc.listMetricDataSources();

    expect(sources.length).toBeGreaterThanOrEqual(5);
    const types = sources.map((s) => s.providerType);
    expect(types).toEqual(
      expect.arrayContaining([
        'PROMETHEUS',
        'VICTORIAMETRICS',
        'THANOS',
        'CORTEX',
        'MIMIR',
        'ARMS',
      ]),
    );
  });

  it('creates and then lists a new data source', async () => {
    isMockModeMock.mockReturnValue(true);
    const svc = await loadService();

    await svc.createMetricDataSource(source('cortex-prod', 'CORTEX'));
    const names = (await svc.listMetricDataSources()).map((s) => s.name);

    expect(names).toContain('cortex-prod');
  });

  it('updates an existing data source', async () => {
    isMockModeMock.mockReturnValue(true);
    const svc = await loadService();

    await svc.updateMetricDataSource({
      ...source('cortex-prod', 'CORTEX'),
      url: 'http://cortex:9010',
    });
    const updated = (await svc.listMetricDataSources()).find((s) => s.name === 'cortex-prod');

    expect(updated?.url).toBe('http://cortex:9010');
  });

  it('deletes a data source', async () => {
    isMockModeMock.mockReturnValue(true);
    const svc = await loadService();

    await svc.deleteMetricDataSource('cortex-prod');
    const names = (await svc.listMetricDataSources()).map((s) => s.name);

    expect(names).not.toContain('cortex-prod');
  });

  it('returns an empty series when querying in mock mode', async () => {
    isMockModeMock.mockReturnValue(true);
    const svc = await loadService();

    const result = await svc.queryMetricDataSource('prometheus-prod', {
      metric: 'up',
      start: 1,
      end: 2,
      step: '30s',
    });

    expect(result.series).toEqual([]);
  });
});

describe('metricsService (real mode)', () => {
  it('delegates list to the api', async () => {
    isMockModeMock.mockReturnValue(false);
    const svc = await loadService();
    vi.mocked(api.listMetricDataSources).mockResolvedValue([
      source('prometheus-prod', 'PROMETHEUS'),
    ]);

    const result = await svc.listMetricDataSources();

    expect(api.listMetricDataSources).toHaveBeenCalled();
    expect(result[0].name).toBe('prometheus-prod');
  });

  it('delegates create to the api', async () => {
    isMockModeMock.mockReturnValue(false);
    const svc = await loadService();
    vi.mocked(api.createMetricDataSource).mockResolvedValue(source('mimir-prod', 'MIMIR'));

    const created = await svc.createMetricDataSource(source('mimir-prod', 'MIMIR'));

    expect(api.createMetricDataSource).toHaveBeenCalled();
    expect(created.providerType).toBe('MIMIR');
  });

  it('delegates delete to the api', async () => {
    isMockModeMock.mockReturnValue(false);
    const svc = await loadService();
    vi.mocked(api.deleteMetricDataSource).mockResolvedValue(undefined);

    await svc.deleteMetricDataSource('arms-prod');

    expect(api.deleteMetricDataSource).toHaveBeenCalledWith('arms-prod');
  });
});
