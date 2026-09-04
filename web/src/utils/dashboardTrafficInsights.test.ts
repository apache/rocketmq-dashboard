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

import { describe, expect, it } from 'vitest';
import type { ClusterOverview, DashboardData } from '../api/metrics';
import { buildDashboardTrafficInsights } from './dashboardTrafficInsights';

const baseCluster = (overrides: Partial<ClusterOverview> = {}): ClusterOverview => ({
  id: 'cluster-a',
  name: 'cluster-a',
  type: 'V5_PROXY_CLUSTER',
  status: 'healthy',
  brokers: 4,
  proxies: 2,
  topics: 32,
  groups: 16,
  tpsIn: 500,
  tpsOut: 500,
  version: '5.2.0',
  throughput: [900, 940, 980, 1000, 1020, 1040],
  ...overrides,
});

const dashboard = (clusters: ClusterOverview[]): DashboardData => ({
  stats: {
    totalClusters: clusters.length,
    healthyClusters: clusters.filter((cluster) => cluster.status === 'healthy').length,
    totalBrokers: clusters.reduce((sum, cluster) => sum + cluster.brokers, 0),
    totalProxies: clusters.reduce((sum, cluster) => sum + (cluster.proxies ?? 0), 0),
    totalNameServers: 2,
    totalTopics: clusters.reduce((sum, cluster) => sum + cluster.topics, 0),
    totalConsumerGroups: clusters.reduce((sum, cluster) => sum + cluster.groups, 0),
    totalMessagesToday: 0,
    messagesPerSecond: clusters.reduce((sum, cluster) => sum + cluster.tpsIn + cluster.tpsOut, 0),
    tpsIn: clusters.reduce((sum, cluster) => sum + cluster.tpsIn, 0),
    tpsOut: clusters.reduce((sum, cluster) => sum + cluster.tpsOut, 0),
  },
  clusters,
});

describe('buildDashboardTrafficInsights', () => {
  it('summarizes balanced healthy cluster traffic without warnings', () => {
    const result = buildDashboardTrafficInsights(
      dashboard([
        baseCluster({ id: 'cluster-a', name: 'cluster-a', tpsIn: 500, tpsOut: 500 }),
        baseCluster({ id: 'cluster-b', name: 'cluster-b', tpsIn: 450, tpsOut: 550 }),
      ]),
    );

    expect(result.level).toBe('healthy');
    expect(result.totalTps).toBe(2000);
    expect(result.activeClusterCount).toBe(2);
    expect(result.topCluster?.name).toBe('cluster-a');
    expect(result.topClusterSharePercent).toBe(50);
    expect(result.balanceScore).toBe(100);
    expect(result.averagePerBrokerTps).toBe(250);
    expect(result.issues).toEqual([]);
    expect(result.rows.map((row) => row.name)).toEqual(['cluster-a', 'cluster-b']);
  });

  it('flags traffic concentration when one cluster owns most active throughput', () => {
    const result = buildDashboardTrafficInsights(
      dashboard([
        baseCluster({ id: 'prod', name: 'prod', brokers: 8, tpsIn: 1400, tpsOut: 1600 }),
        baseCluster({ id: 'pre', name: 'pre', brokers: 4, tpsIn: 100, tpsOut: 100 }),
      ]),
    );

    expect(result.level).toBe('critical');
    expect(result.topCluster?.name).toBe('prod');
    expect(result.topClusterSharePercent).toBe(93.8);
    expect(result.balanceScore).toBeLessThan(30);
    expect(result.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'TRAFFIC_CONCENTRATION',
          level: 'critical',
          clusterName: 'prod',
        }),
      ]),
    );
  });

  it('surfaces unhealthy clusters carrying live traffic', () => {
    const result = buildDashboardTrafficInsights(
      dashboard([
        baseCluster({ id: 'healthy', name: 'healthy', tpsIn: 700, tpsOut: 700 }),
        baseCluster({
          id: 'degraded',
          name: 'degraded',
          status: 'warning',
          tpsIn: 300,
          tpsOut: 300,
        }),
      ]),
    );

    expect(result.unhealthyClusterCount).toBe(1);
    expect(result.unhealthyTrafficTps).toBe(600);
    expect(result.unhealthyTrafficPercent).toBe(30);
    expect(result.level).toBe('critical');
    expect(result.rows.find((row) => row.name === 'degraded')?.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'UNHEALTHY_TRAFFIC',
          level: 'critical',
          clusterName: 'degraded',
        }),
      ]),
    );
  });

  it('detects broker load skew using normalized per-broker traffic', () => {
    const result = buildDashboardTrafficInsights(
      dashboard([
        baseCluster({ id: 'hot', name: 'hot', brokers: 1, tpsIn: 900, tpsOut: 900 }),
        baseCluster({ id: 'wide-a', name: 'wide-a', brokers: 12, tpsIn: 300, tpsOut: 300 }),
        baseCluster({ id: 'wide-b', name: 'wide-b', brokers: 12, tpsIn: 300, tpsOut: 300 }),
      ]),
    );

    expect(result.averagePerBrokerTps).toBe(120);
    expect(result.rows[0].perBrokerTps).toBe(1800);
    expect(result.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'BROKER_LOAD_SKEW',
          level: 'warning',
          clusterName: 'hot',
          threshold: 300,
        }),
      ]),
    );
  });

  it('classifies falling aggregate traffic from the dashboard history', () => {
    const result = buildDashboardTrafficInsights(
      dashboard([
        baseCluster({
          id: 'cluster-a',
          throughput: [1000, 1000, 1000, 400, 400, 400],
        }),
        baseCluster({
          id: 'cluster-b',
          throughput: [500, 500, 500, 200, 200, 200],
        }),
      ]),
    );

    expect(result.trendDirection).toBe('falling');
    expect(result.trendDeltaPercent).toBe(-60);
    expect(result.aggregateThroughput).toEqual([1500, 1500, 1500, 600, 600, 600]);
    expect(result.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'RECENT_TRAFFIC_DROP',
          level: 'warning',
          value: -60,
        }),
      ]),
    );
  });

  it('classifies recent spikes as informational notices instead of errors', () => {
    const result = buildDashboardTrafficInsights(
      dashboard([
        baseCluster({
          id: 'cluster-a',
          throughput: [200, 200, 200, 500, 500, 500],
        }),
      ]),
    );

    expect(result.trendDirection).toBe('rising');
    expect(result.trendDeltaPercent).toBe(150);
    expect(result.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'RECENT_TRAFFIC_SPIKE',
          level: 'notice',
          value: 150,
        }),
      ]),
    );
  });

  it('marks missing topology counts without treating them as zero-capacity proxies', () => {
    const result = buildDashboardTrafficInsights(
      dashboard([
        baseCluster({
          id: 'proxy-cluster',
          name: 'proxy-cluster',
          proxies: null,
          tpsIn: 100,
          tpsOut: 100,
        }),
      ]),
    );

    expect(result.rows[0].proxies).toBeNull();
    expect(result.rows[0].issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'TOPOLOGY_COUNT_UNAVAILABLE',
          level: 'notice',
          clusterName: 'proxy-cluster',
        }),
      ]),
    );
  });

  it('treats empty dashboards as no active traffic with an empty row set', () => {
    const result = buildDashboardTrafficInsights(null);

    expect(result.totalTps).toBe(0);
    expect(result.activeClusterCount).toBe(0);
    expect(result.totalClusterCount).toBe(0);
    expect(result.topCluster).toBeNull();
    expect(result.rows).toEqual([]);
    expect(result.issues).toEqual([
      expect.objectContaining({
        code: 'NO_ACTIVE_TRAFFIC',
        level: 'notice',
      }),
    ]);
  });

  it('normalizes invalid and negative numeric samples before computing shares', () => {
    const malformed = baseCluster({
      id: 'malformed',
      name: 'malformed',
      brokers: -1,
      topics: -20,
      groups: Number.NaN,
      tpsIn: Number.POSITIVE_INFINITY,
      tpsOut: -100,
      throughput: [Number.NaN, -10, 20],
    }) as ClusterOverview;

    const result = buildDashboardTrafficInsights(dashboard([malformed]));

    expect(result.totalTps).toBe(0);
    expect(result.rows[0]).toEqual(
      expect.objectContaining({
        brokers: 0,
        topics: 0,
        groups: 0,
        totalTps: 0,
        sharePercent: 0,
        perBrokerTps: 0,
        throughput: [0, 0, 20],
      }),
    );
  });
});
