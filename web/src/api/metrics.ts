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

import client from './client';

// ─── Types ──────────────────────────────────────────────────────
export interface DashboardStats {
  totalClusters: number;
  healthyClusters: number;
  totalBrokers: number;
  totalProxies: number | null;
  totalNameServers: number | null;
  totalTopics: number;
  totalConsumerGroups: number;
  totalMessagesToday: number;
  messagesPerSecond: number;
  tpsIn: number;
  tpsOut: number;
}

export interface ClusterOverview {
  id: string;
  name: string;
  type: string;
  status: string;
  brokers: number;
  proxies: number | null;
  topics: number;
  groups: number;
  tpsIn: number;
  tpsOut: number;
  version: string;
  throughput: number[];
}

export interface DashboardData {
  stats: DashboardStats;
  clusters: ClusterOverview[];
}

export interface MetricSample {
  timestamp: number;
  value: string;
}

export interface MetricHistogram {
  count: string;
  sum: string;
  buckets: [number, string, string, string][];
}

export interface MetricHistogramSample {
  timestamp: number;
  histogram: MetricHistogram;
}

export interface MetricSeries {
  labels: Record<string, string>;
  values: MetricSample[];
  histograms: MetricHistogramSample[];
}

export interface MetricData {
  resultType: string;
  series: MetricSeries[];
  warnings: string[];
}

export interface MetricQuery {
  metric: string;
  start: number;
  end: number;
  step: string;
}

export interface MetricMapping {
  semanticMetric: string;
  name: string;
  unit: string;
  prometheusMetric: string;
  promql: string;
  labels: string[];
}

export interface MetricProfile {
  id: string;
  name: string;
  description: string;
  metrics: MetricMapping[];
}

// ─── Dashboard ──────────────────────────────────────────────────
export async function getDashboard(instanceId?: string) {
  const res = await client.get<{ data: DashboardData }>('/dashboard', {
    params: instanceId ? { instanceId } : undefined,
  });
  return res.data.data;
}

// ─── Metrics ────────────────────────────────────────────────────
export async function queryMetrics(query: MetricQuery) {
  const res = await client.post<{ data: MetricData }>('/metrics/query', query);
  return res.data.data;
}

export interface DataSourceQuery {
  key: string;
  query: MetricQuery;
  instanceId?: string;
  username?: string;
  password?: string;
  bearerToken?: string;
}

// Runs a PromQL range query against a configured data source (key identifies the
// persisted source; credentials are supplied per request and are never persisted).
export async function queryByDataSource(params: DataSourceQuery) {
  const { key, query, instanceId, username, password, bearerToken } = params;
  const res = await client.post<{ data: MetricData }>(
    '/metrics/query/datasource',
    { query, instanceId, username, password, bearerToken },
    { params: { key } },
  );
  return res.data.data;
}

export async function listMetricProfiles() {
  const res = await client.get<{ data: MetricProfile[] }>('/metrics/profiles');
  return res.data.data;
}

// ─── Grafana dashboards ─────────────────────────────────────────
export interface GrafanaDashboardInfo {
  uid: string;
  title: string;
  description: string;
  tags: string[];
}

export async function listGrafanaDashboards(): Promise<GrafanaDashboardInfo[]> {
  const res = await client.get<{ data: GrafanaDashboardInfo[] }>('/metrics/grafana/dashboards');
  return res.data.data;
}

export async function getGrafanaDashboard(uid: string): Promise<Record<string, unknown>> {
  const res = await client.get<{ data: Record<string, unknown> }>(
    `/metrics/grafana/dashboards/${encodeURIComponent(uid)}`,
  );
  return res.data.data;
}

export async function exportGrafanaDashboard(uid: string): Promise<Blob> {
  const res = await client.get<Blob>(
    `/metrics/grafana/dashboards/${encodeURIComponent(uid)}/export`,
    { responseType: 'blob' },
  );
  return res.data;
}

export async function exportGrafanaDashboards(): Promise<Blob> {
  const res = await client.get<Blob>('/metrics/grafana/dashboards/export', {
    responseType: 'blob',
  });
  return res.data;
}
