/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import {
  getDashboard,
  listGrafanaDashboards,
  getGrafanaDashboard,
  exportGrafanaDashboard,
  listMetricProfiles,
  queryMetrics,
} from './metrics';

const mock = new MockAdapter(client);
const dashboard = {
  stats: {
    totalClusters: 1,
    healthyClusters: 1,
    totalBrokers: 2,
    totalProxies: 1,
    totalNameServers: 1,
    totalTopics: 4,
    totalConsumerGroups: 3,
    totalMessagesToday: 100,
    messagesPerSecond: 10,
    tpsIn: 8,
    tpsOut: 7,
  },
  clusters: [],
};

describe('metrics API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('loads dashboard data from the dashboard endpoint', async () => {
    mock.onGet('/dashboard').reply(200, { code: 200, data: dashboard });

    await expect(getDashboard()).resolves.toEqual(dashboard);
  });

  it('posts a metrics query and returns its result', async () => {
    const query = { metric: 'TPS_IN', start: 1, end: 2, step: '1m' };
    const result = {
      resultType: 'matrix',
      series: [
        {
          labels: {
            __name__: 'rocketmq_messages_in_total',
            cluster: 'rmq-cn-v5-prod-01',
          },
          values: [{ timestamp: 1, value: '8' }],
          histograms: [],
        },
      ],
      warnings: [],
    };

    mock.onPost('/metrics/query').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(query);
      return [200, { code: 200, data: result }];
    });

    await expect(queryMetrics(query)).resolves.toEqual(result);
  });

  it('loads version-aware metric profiles', async () => {
    const profiles = [
      {
        id: 'rocketmq5-native',
        name: 'RocketMQ 5.x Native',
        description: 'RocketMQ 5.x native Prometheus metrics',
        metrics: [
          {
            semanticMetric: 'message_in_tps',
            name: 'Message In TPS',
            unit: 'messages/s',
            prometheusMetric: 'rocketmq_messages_in_total',
            promql: 'sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)',
            labels: ['cluster', 'node_id'],
          },
        ],
      },
    ];

    mock.onGet('/metrics/profiles').reply(200, { code: 200, data: profiles });

    await expect(listMetricProfiles()).resolves.toEqual(profiles);
  });

  it('lists bundled Grafana dashboards', async () => {
    const dashboards = [
      {
        uid: 'rocketmq-overview',
        title: 'RocketMQ Cluster Overview',
        description: 'd',
        tags: ['rocketmq'],
      },
      { uid: 'rocketmq-broker', title: 'RocketMQ Broker', description: 'd', tags: ['rocketmq'] },
    ];

    mock.onGet('/metrics/grafana/dashboards').reply(200, { code: 200, data: dashboards });

    await expect(listGrafanaDashboards()).resolves.toEqual(dashboards);
  });

  it('loads a single Grafana dashboard model', async () => {
    const model = {
      uid: 'rocketmq-overview',
      title: 'RocketMQ Cluster Overview',
      schemaVersion: 39,
    };

    mock
      .onGet('/metrics/grafana/dashboards/rocketmq-overview')
      .reply(200, { code: 200, data: model });

    await expect(getGrafanaDashboard('rocketmq-overview')).resolves.toEqual(model);
  });

  it('exports a Grafana dashboard as a blob', async () => {
    const blob = new Blob(['{"uid":"rocketmq-overview"}'], { type: 'application/json' });

    mock.onGet('/metrics/grafana/dashboards/rocketmq-overview/export').reply(200, blob);

    const result = await exportGrafanaDashboard('rocketmq-overview');
    expect(result).toBeInstanceOf(Blob);
    await expect(result.text()).resolves.toContain('rocketmq-overview');
  });
});
