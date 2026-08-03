/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */

import client from './client';

// ─── Types ──────────────────────────────────────────────────────
export interface DashboardStats {
  totalClusters: number;
  healthyClusters: number;
  totalBrokers: number;
  totalProxies: number;
  totalNameServers: number;
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
  proxies: number;
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
export async function getDashboard() {
  const res = await client.get<{ data: DashboardData }>('/dashboard');
  return res.data.data;
}

// ─── Metrics ────────────────────────────────────────────────────
export async function queryMetrics(query: MetricQuery) {
  const res = await client.post<{ data: MetricData }>('/metrics/query', query);
  return res.data.data;
}

export async function listMetricProfiles() {
  const res = await client.get<{ data: MetricProfile[] }>('/metrics/profiles');
  return res.data.data;
}

// ─── Metrics data sources (multi-backend) ──────────────────────
export type MetricsProviderType =
  'PROMETHEUS' | 'VICTORIAMETRICS' | 'THANOS' | 'CORTEX' | 'MIMIR' | 'ARMS' | 'CUSTOM';

export interface MetricsDataSource {
  name: string;
  providerType: MetricsProviderType;
  url: string;
  authType?: string;
  username?: string;
  password?: string;
  bearerToken?: string;
  tlsEnabled?: boolean;
  scrapeInterval?: number;
  enabled?: boolean;
}

export async function listMetricDataSources(): Promise<MetricsDataSource[]> {
  const res = await client.get<{ data: MetricsDataSource[] }>('/metrics/datasources');
  return res.data.data;
}

export async function createMetricDataSource(
  config: MetricsDataSource,
): Promise<MetricsDataSource> {
  const res = await client.post<{ data: MetricsDataSource }>('/metrics/datasources/create', config);
  return res.data.data;
}

export async function updateMetricDataSource(
  config: MetricsDataSource,
): Promise<MetricsDataSource> {
  const res = await client.post<{ data: MetricsDataSource }>('/metrics/datasources/update', config);
  return res.data.data;
}

export async function deleteMetricDataSource(name: string): Promise<void> {
  await client.delete<void>(`/metrics/datasources?name=${encodeURIComponent(name)}`);
}

export async function queryMetricDataSource(
  dataSource: string,
  query: MetricQuery,
): Promise<MetricData> {
  const res = await client.post<{ data: MetricData }>(
    `/metrics/datasources/query?dataSource=${encodeURIComponent(dataSource)}`,
    query,
  );
  return res.data.data;
}
