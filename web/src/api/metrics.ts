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

// ─── Dashboard API ──────────────────────────────────────────────
// Backend: DashboardController at /dashboard
// GET /dashboard/broker.query?date=xxx       → broker stats
// GET /dashboard/topic.query?date=xxx        → topic stats
// GET /dashboard/topicCurrent.query          → current topic data
// GET /dashboard/accumulation.query?date=xxx → accumulation data

export async function getDashboardBrokerStats(date: string) {
  const res = await client.get('/dashboard/broker.query', { params: { date } });
  return res.data;
}

export async function getDashboardTopicStats(date: string, topicName?: string) {
  const res = await client.get('/dashboard/topic.query', {
    params: { date, topicName },
  });
  return res.data;
}

export async function getDashboardTopicCurrent() {
  const res = await client.get('/dashboard/topicCurrent.query');
  return res.data;
}

export async function getDashboardAccumulation(date: string, topicName?: string) {
  const res = await client.get('/dashboard/accumulation.query', {
    params: { date, topicName },
  });
  return res.data;
}

// ─── Dashboard (legacy — kept for mock compatibility) ───────────
export async function getDashboard() {
  // This endpoint doesn't exist on the backend; the dashboard is assembled
  // from individual /dashboard/*.query endpoints. Kept for mock fallback.
  const res = await client.get('/dashboard');
  return res.data;
}

// ─── Metrics (no backend equivalent yet) ────────────────────────
export async function queryMetrics(query: {
  metric: string;
  start: number;
  end: number;
  step?: string;
}) {
  const res = await client.post('/metrics/query', query);
  return res.data;
}
