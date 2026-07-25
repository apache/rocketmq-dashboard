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
