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

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface AuditQuery {
  page?: number;
  pageSize?: number;
  search?: string;
  operationType?: string;
  startDate?: string;
  endDate?: string;
  result?: string;
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
  const res = await client.post<{ data: { cleared: number } }>('/system-alerts/clear-acknowledged');
  return res.data.data;
}

// ─── Audit Logs ─────────────────────────────────────────────────
export async function listAuditRecords(params?: AuditQuery) {
  const res = await client.get<{ data: PageResult<AuditRecord> }>('/audit-logs', {
    params,
  });
  return res.data.data;
}

export async function cleanupAuditLogs(beforeDays: number) {
  const res = await client.post<{ data: { deleted: number } }>('/audit-logs/cleanup', {
    beforeDays,
  });
  return res.data.data;
}

// ─── NameServer Operations ──────────────────────────────────────
export interface OpsHomeData {
  namesvrAddrList: string[];
  useVIPChannel: boolean;
  useTLS: boolean;
  currentNamesrv: string;
}

export async function queryOpsHomePage(): Promise<OpsHomeData> {
  const res = await client.get<{ data: OpsHomeData }>('/ops/homePage');
  return res.data.data;
}

export async function updateNameSvrAddr(namesrvAddr: string): Promise<void> {
  await client.post('/ops/updateNameSvrAddr', { namesrvAddr });
}

export async function addNameSvrAddr(namesrvAddr: string): Promise<void> {
  await client.post('/ops/addNameSvrAddr', { namesrvAddr });
}

export async function updateIsVIPChannel(useVIPChannel: boolean): Promise<void> {
  await client.post('/ops/updateIsVIPChannel', { useVIPChannel });
}

export async function updateUseTLS(useTLS: boolean): Promise<void> {
  await client.post('/ops/updateUseTLS', { useTLS });
}
