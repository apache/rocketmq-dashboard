/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */

import client from './client';

// ─── Types ──────────────────────────────────────────────────────
export interface AlertRule {
  id: string;
  name: string;
  metric: string;
  operator: string;
  threshold: number;
  thresholdUnit: string;
  duration: string;
  channels: string[];
  enabled: boolean;
  lastTriggered: string | null;
  description: string;
}

export interface SystemAlert {
  id: string;
  level: string;
  title: string;
  description: string;
  time: string;
  acknowledged: boolean;
}

export interface AuditRecord {
  id: string;
  timestamp: string;
  operator: string;
  operationType: string;
  target: string;
  detail: string;
  ipAddress: string;
  result: string;
}

// ─── Ops API ────────────────────────────────────────────────────
// Backend: OpsController at /ops
// GET  /ops/homePage.query          → home page info
// POST /ops/updateNameSvrAddr.do    → update nameserver address
// POST /ops/addNameSvrAddr.do       → add nameserver address
// POST /ops/updateIsVIPChannel.do   → update VIP channel
// GET  /ops/rocketMqStatus.query    → cluster status check
// POST /ops/updateUseTLS.do         → update TLS setting

export async function getOpsHomePage() {
  const res = await client.get('/ops/homePage.query');
  return res.data;
}

export async function updateNameServerAddr(nameSvrAddrList: string) {
  await client.post('/ops/updateNameSvrAddr.do', null, {
    params: { nameSvrAddrList },
  });
}

export async function addNameServerAddr(newNamesrvAddr: string) {
  await client.post('/ops/addNameSvrAddr.do', null, {
    params: { newNamesrvAddr },
  });
}

export async function updateVIPChannel(useVIPChannel: string) {
  await client.post('/ops/updateIsVIPChannel.do', null, {
    params: { useVIPChannel },
  });
}

export async function getRocketMQStatus() {
  const res = await client.get('/ops/rocketMqStatus.query');
  return res.data;
}

export async function updateUseTLS(useTLS: string) {
  await client.post('/ops/updateUseTLS.do', null, {
    params: { useTLS },
  });
}

// ─── Alert Rules (no backend equivalent yet — uses mock) ────────
export async function listAlertRules() {
  const res = await client.get('/alert-rules');
  return res.data;
}

export async function createAlertRule(data: Partial<AlertRule>) {
  await client.post('/alert-rules/create', data);
}

export async function updateAlertRule(data: Partial<AlertRule>) {
  await client.post('/alert-rules/update', data);
}

export async function toggleAlertRule(id: string, enabled: boolean) {
  await client.post('/alert-rules/toggle', { id, enabled });
}

export async function deleteAlertRule(id: string) {
  await client.post('/alert-rules/delete', { id });
}

// ─── System Alerts (no backend equivalent yet — uses mock) ──────
export async function listSystemAlerts() {
  const res = await client.get('/system-alerts');
  return res.data;
}

export async function acknowledgeAlert(id: string) {
  await client.post('/system-alerts/acknowledge', { id });
}

export async function clearAcknowledgedAlerts() {
  await client.post('/system-alerts/clear-acknowledged');
}

// ─── Audit Logs (no backend equivalent yet — uses mock) ─────────
export async function listAuditRecords(params?: Record<string, unknown>) {
  const res = await client.get('/audit-logs', { params });
  return res.data;
}

export async function cleanupAuditLogs(beforeDays: number) {
  await client.post('/audit-logs/cleanup', { beforeDays });
}
