// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.

import { isMockMode } from './dataMode';
import * as metricsApi from '../api/metrics';
import type { GrafanaDashboardInfo } from '../api/metrics';
import { mockGrafanaDashboards } from '../mock/grafanaDashboards';

export interface GrafanaDashboardBundleExport {
  blob: Blob;
  filename: string;
}

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

export async function exportGrafanaDashboards(): Promise<GrafanaDashboardBundleExport> {
  if (isMockMode()) {
    const bundle = mockGrafanaDashboards.map(({ uid, model }) => ({
      filename: `${uid}.json`,
      model,
    }));
    return {
      blob: new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' }),
      filename: 'rocketmq-grafana-dashboards.json',
    };
  }
  return {
    blob: await metricsApi.exportGrafanaDashboards(),
    filename: 'rocketmq-grafana-dashboards.zip',
  };
}
