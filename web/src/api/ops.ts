import client from './client';

// Matches mock/alerts.ts (inferred from data)
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

// Matches mock/dashboard.ts systemAlerts
export interface SystemAlert {
  id: string;
  level: string;
  title: string;
  description: string;
  time: string;
  acknowledged: boolean;
}

// Matches mock/audit.ts (inferred from data)
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

// ─── Alert Rules ────────────────────────────────────────────────
export async function listAlertRules() {
  const res = await client.get<{ data: AlertRule[] }>('/alert-rules');
  return res.data.data;
}

export async function createAlertRule(data: Partial<AlertRule>) {
  const res = await client.post<{ data: AlertRule }>('/alert-rules/create', data);
  return res.data.data;
}

export async function updateAlertRule(data: AlertRule) {
  const res = await client.post<{ data: AlertRule }>('/alert-rules/update', data);
  return res.data.data;
}

export async function toggleAlertRule(id: string, enabled: boolean) {
  const res = await client.post<{ data: AlertRule }>('/alert-rules/toggle', { id, enabled });
  return res.data.data;
}

export async function deleteAlertRule(id: string) {
  await client.post('/alert-rules/delete', { id });
}

// ─── System Alerts ──────────────────────────────────────────────
export async function listSystemAlerts() {
  const res = await client.get<{ data: SystemAlert[] }>('/system-alerts');
  return res.data.data;
}

export async function acknowledgeAlert(id: string) {
  await client.post('/system-alerts/acknowledge', { id });
}

export async function clearAcknowledgedAlerts() {
  await client.post('/system-alerts/clear-acknowledged');
}

// ─── Audit Logs ─────────────────────────────────────────────────
export async function listAuditRecords(params?: Record<string, unknown>) {
  const res = await client.get<{ data: { list: AuditRecord[]; total: number } }>('/audit-logs', {
    params,
  });
  return res.data.data;
}

export async function cleanupAuditLogs(beforeDays: number) {
  await client.post('/audit-logs/cleanup', { beforeDays });
}
