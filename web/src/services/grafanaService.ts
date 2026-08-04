// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.

import { isMockMode } from './dataMode';
import * as metricsApi from '../api/metrics';
import type { GrafanaDashboardInfo } from '../api/metrics';
import { mockGrafanaDashboards } from '../mock/grafanaDashboards';

export async function listGrafanaDashboards(): Promise<GrafanaDashboardInfo[]> {
  if (isMockMode()) {
    return mockGrafanaDashboards.map(({ uid, title, description, tags }) => ({
      uid,
      title,
      description,
      tags,
    }));
  }
  return metricsApi.listGrafanaDashboards();
}

export async function getGrafanaDashboard(uid: string): Promise<Record<string, unknown>> {
  if (isMockMode()) {
    const found = mockGrafanaDashboards.find((dashboard) => dashboard.uid === uid);
    if (!found) {
      throw new Error(`Grafana dashboard not found: ${uid}`);
    }
    return found.model;
  }
  return metricsApi.getGrafanaDashboard(uid);
}

export async function exportGrafanaDashboard(uid: string): Promise<Blob> {
  if (isMockMode()) {
    const found = mockGrafanaDashboards.find((dashboard) => dashboard.uid === uid);
    const model = found ? found.model : { uid };
    return new Blob([JSON.stringify(model, null, 2)], { type: 'application/json' });
  }
  return metricsApi.exportGrafanaDashboard(uid);
}
