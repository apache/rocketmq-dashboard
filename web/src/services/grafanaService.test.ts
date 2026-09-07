// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as grafanaApi from '../api/metrics';
import {
  exportGrafanaDashboard,
  exportGrafanaDashboards,
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

  it('does not expose mutable mock dashboard state', async () => {
    const dashboards = await listGrafanaDashboards();
    dashboards[0].tags.push('mutated');
    const model = await getGrafanaDashboard('rocketmq-overview');
    (model.panels as Array<Record<string, unknown>>)[0].title = 'mutated';

    const freshDashboards = await listGrafanaDashboards();
    const freshModel = await getGrafanaDashboard('rocketmq-overview');
    expect(freshDashboards[0].tags).not.toContain('mutated');
    expect((freshModel.panels as Array<Record<string, unknown>>)[0].title).toBe('Messages In TPS');
  });

  it('throws for an unknown dashboard uid in mock mode', async () => {
    await expect(getGrafanaDashboard('nope')).rejects.toThrow();
  });

  it('exports a dashboard as a blob in mock mode', async () => {
    const blob = await exportGrafanaDashboard('rocketmq-overview');
    expect(blob).toBeInstanceOf(Blob);
    await expect(blob.text()).resolves.toContain('rocketmq-overview');
  });

  it('exports all mock dashboards as a JSON download', async () => {
    const download = await exportGrafanaDashboards();
    expect(download.filename).toBe('rocketmq-grafana-dashboards.json');
    expect(download.blob).toBeInstanceOf(Blob);
    expect(download.blob.type).toBe('application/json');
    await expect(download.blob.text()).resolves.toContain('rocketmq-overview.json');
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

  it('delegates bulk export to the api in real mode', async () => {
    isMockModeMock.isMockMode = () => false;
    const exportSpy = vi
      .spyOn(grafanaApi, 'exportGrafanaDashboards')
      .mockResolvedValue(new Blob(['zip-content'], { type: 'application/zip' }));

    const result = await exportGrafanaDashboards();
    expect(exportSpy).toHaveBeenCalledTimes(1);
    expect(result.filename).toBe('rocketmq-grafana-dashboards.zip');
    expect(result.blob.type).toBe('application/zip');
  });
});
