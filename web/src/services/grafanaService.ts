// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
      tags: [...tags],
    }));
  }
  return metricsApi.listGrafanaDashboards();
}

function getMockGrafanaDashboard(uid: string) {
  const found = mockGrafanaDashboards.find((dashboard) => dashboard.uid === uid);
  if (!found) {
    throw new Error(`Grafana dashboard not found: ${uid}`);
  }
  return found;
}

export async function getGrafanaDashboard(uid: string): Promise<Record<string, unknown>> {
  if (isMockMode()) {
    return structuredClone(getMockGrafanaDashboard(uid).model);
  }
  return metricsApi.getGrafanaDashboard(uid);
}

export async function exportGrafanaDashboard(uid: string): Promise<Blob> {
  if (isMockMode()) {
    // The export endpoint answers 404 for an unknown uid; the mock must not fabricate a
    // document for one, or the console reports a successful export that contains nothing.
    const model = getMockGrafanaDashboard(uid).model;
    return new Blob([JSON.stringify(model, null, 2)], { type: 'application/json' });
  }
  return metricsApi.exportGrafanaDashboard(uid);
}

export interface GrafanaDashboardDownload {
  blob: Blob;
  filename: string;
}

export async function exportGrafanaDashboards(): Promise<GrafanaDashboardDownload> {
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
