// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as grafanaApi from '../api/metrics';
import {
  exportGrafanaDashboard,
  getGrafanaDashboard,
  listGrafanaDashboards,
} from './grafanaService';

const isMockModeMock = vi.hoisted(() => ({ isMockMode: () => true as boolean }));
vi.mock('./dataMode', () => isMockModeMock);

describe('grafanaService', () => {
  beforeEach(() => {
    isMockModeMock.isMockMode = () => true;
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('lists at least 10 dashboards in mock mode', async () => {
    const dashboards = await listGrafanaDashboards();
    expect(dashboards.length).toBeGreaterThanOrEqual(10);
    expect(dashboards.every((d) => d.uid && d.title)).toBe(true);
  });

  it('returns a dashboard model by uid in mock mode', async () => {
    const model = await getGrafanaDashboard('rocketmq-overview');
    expect(model.uid).toBe('rocketmq-overview');
  });

  it('throws for an unknown dashboard uid in mock mode', async () => {
    await expect(getGrafanaDashboard('nope')).rejects.toThrow();
  });

  it('exports a dashboard as a blob in mock mode', async () => {
    const blob = await exportGrafanaDashboard('rocketmq-overview');
    expect(blob).toBeInstanceOf(Blob);
    await expect(blob.text()).resolves.toContain('rocketmq-overview');
  });

  it('delegates to the api in real mode', async () => {
    isMockModeMock.isMockMode = () => false;
    const listSpy = vi
      .spyOn(grafanaApi, 'listGrafanaDashboards')
      .mockResolvedValue([
        { uid: 'rocketmq-overview', title: 'Overview', description: '', tags: ['rocketmq'] },
      ]);

    const result = await listGrafanaDashboards();
    expect(listSpy).toHaveBeenCalledTimes(1);
    expect(result[0].uid).toBe('rocketmq-overview');
  });
});
