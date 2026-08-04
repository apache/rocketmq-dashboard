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
import type { MetricsDataSource } from './metrics';
import {
  getDashboard,
  listMetricProfiles,
  queryMetrics,
  listMetricDataSources,
  createMetricDataSource,
  updateMetricDataSource,
  deleteMetricDataSource,
  queryMetricDataSource,
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
});

describe('metrics data sources', () => {
  it('lists configured data sources', async () => {
    mock.onGet('/metrics/datasources').reply(200, {
      code: 200,
      data: [
        { name: 'prometheus-prod', providerType: 'PROMETHEUS', url: 'http://prometheus:9090' },
      ],
    });

    const sources = await listMetricDataSources();

    expect(sources).toHaveLength(1);
    expect(sources[0].name).toBe('prometheus-prod');
    expect(sources[0].providerType).toBe('PROMETHEUS');
  });

  it('creates a data source', async () => {
    const config: MetricsDataSource = {
      name: 'cortex-prod',
      providerType: 'CORTEX',
      url: 'http://cortex:9009',
    };
    mock.onPost('/metrics/datasources/create').reply(200, { code: 200, data: config });

    const created = await createMetricDataSource(config);

    expect(created.name).toBe('cortex-prod');
    expect(created.providerType).toBe('CORTEX');
  });

  it('updates a data source', async () => {
    const config: MetricsDataSource = {
      name: 'cortex-prod',
      providerType: 'CORTEX',
      url: 'http://cortex:9010',
    };
    mock.onPost('/metrics/datasources/update').reply(200, { code: 200, data: config });

    const updated = await updateMetricDataSource(config);

    expect(updated.url).toBe('http://cortex:9010');
  });

  it('deletes a data source', async () => {
    mock.onDelete(/\/metrics\/datasources/).reply(200, { code: 200, data: null });

    await expect(deleteMetricDataSource('cortex-prod')).resolves.toBeUndefined();
  });

  it('queries a named data source', async () => {
    const data = { resultType: 'matrix', series: [], warnings: [] };
    mock.onPost(/\/metrics\/datasources\/query/).reply(200, { code: 200, data });

    const result = await queryMetricDataSource('victoriametrics-prod', {
      metric: 'up',
      start: 1,
      end: 2,
      step: '30s',
    });

    expect(result.resultType).toBe('matrix');
  });
});
