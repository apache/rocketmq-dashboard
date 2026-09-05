import client from './client';

// Matches mock/alerts.ts (inferred from data)
export interface AlertRule {
  id: number;
  name: string;
  metric: string;
  operator: string;
  threshold: number;
  thresholdUnit?: string | null;
  duration: string;
  /** Optional English display copy for demo (mock-mode) alert rules. */
  enName?: string | null;
  enDuration?: string | null;
  reminderInterval?: string;
  aggregation?: 'LAST' | 'MAX' | 'MIN' | 'AVG' | 'SUM';
  windowSeconds?: number;
  channels: string[];
  enabled: boolean;
  lastTriggered: string | null;
  description: string;
  instanceId?: string;
  consumerGroup?: string;
  topic?: string;
  consecutiveSamples?: number;
  notificationTemplate?: string;
}

export type AlertRuleDomain = 'BUSINESS' | 'CLUSTER';

export interface AlertRuleQuery {
  page?: number;
  pageSize?: number;
  search?: string;
  enabled?: boolean;
}

export interface NativeAlertMetricInfo {
  key: string;
  label: string;
  thresholdUnit: string;
  supportsConsumerGroup: boolean;
}

export interface AlertRuleBulkResult {
  succeededIds: number[];
  failures: Record<string, string>;
  updatedRules: AlertRule[];
}

export interface AlertRuleTransfer {
  version: number;
  domain: AlertRuleDomain;
  rules: Array<Omit<AlertRule, 'id' | 'lastTriggered'>>;
}

export interface AlertRuleTestResult {
  samples: Array<{
    labels: Record<string, string>;
    availability: string;
    unavailableReason?: string | null;
    currentValue: number | null;
    conditionMet: boolean;
  }>;
}
export interface AlertRuleRuntime {
  ruleId: number;
  fingerprint: string;
  status: string;
  consecutiveHits: number;
  currentValue?: number | null;
  nextReminderAt?: string | null;
}

// Matches mock/dashboard.ts systemAlerts
export interface SystemAlert {
  id: number;
  level: string;
  title: string;
  description: string;
  time: string;
  acknowledged: boolean;
  acknowledgedBy?: string | null;
  acknowledgedAt?: string | null;
  domain?: 'BUSINESS' | 'CLUSTER' | null;
  ruleId?: number | null;
  fingerprint?: string | null;
  transition?: 'FIRING' | 'RESOLVED' | null;
  instanceId?: string | null;
  currentValue?: number | null;
  notificationSuppressed?: boolean;
  suppressionCauseAlertId?: number | null;
  suppressionReason?: string | null;
  labels?: Record<string, string>;
}

export interface CollectorStatus {
  collectionInterval: string;
  clusterCollectorCount: number;
  businessCollectorCount: number;
}

export interface SystemAlertQuery {
  level?: string;
  domain?: 'BUSINESS' | 'CLUSTER';
  instanceId?: string;
  transition?: string;
  labelKey?: string;
  labelValue?: string;
  from?: string;
  to?: string;
  notificationSuppressed?: boolean;
  page?: number;
  pageSize?: number;
}

export interface NotificationDelivery {
  id: number;
  channel: string;
  status: 'PENDING' | 'SENDING' | 'DELIVERED' | 'RETRY_WAIT' | 'FAILED';
  attemptCount: number;
  nextAttemptAt?: string | null;
  lastError?: string | null;
  deliveredAt?: string | null;
}

export interface NotificationDeliveryBulkRetryResult {
  succeededIds: number[];
  failures: Record<string, string>;
}

export interface NotificationDeliveryRecord extends NotificationDelivery {
  id: number;
  alertId: number;
  createdAt: string;
  messageContent?: string | null;
  alertTitle: string;
  alertDomain?: 'BUSINESS' | 'CLUSTER' | null;
  transition?: 'FIRING' | 'RESOLVED' | null;
  instanceId?: string | null;
}

export interface NotificationDeliveryQuery {
  channel?: string;
  status?: NotificationDelivery['status'];
  instanceId?: string;
  page?: number;
  pageSize?: number;
}

export interface AlertSilence {
  id: number;
  domain?: 'BUSINESS' | 'CLUSTER' | null;
  ruleId?: number | null;
  instanceId?: string | null;
  labels?: Record<string, string>;
  startsAt: string;
  endsAt: string;
  reason?: string | null;
  createdBy: string;
}

export type CreateAlertSilence = Omit<AlertSilence, 'id' | 'createdBy'>;

// Matches mock/audit.ts (inferred from data)
export interface AuditRecord {
  id: number;
  timestamp: string;
  operator: string;
  operationType: string;
  resourceType: string;
  target: string;
  clusterId: string;
  detail: string;
  result: string;
  errorMessage: string;
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
  resourceType?: string;
  clusterId?: string;
  startDate?: string;
  endDate?: string;
  result?: string;
}

// ─── Alert Rules ────────────────────────────────────────────────
const alertRulePath = (domain: AlertRuleDomain) =>
  domain === 'BUSINESS' ? '/business-alert-rules' : '/cluster-alert-rules';

export async function listNativeAlertMetrics(instanceId: string, domain: AlertRuleDomain) {
  const res = await client.get<{ data: NativeAlertMetricInfo[] }>('/native-alert-metrics', {
    params: { instanceId, domain },
  });
  return res.data.data;
}

export async function listAlertRules(domain: AlertRuleDomain = 'CLUSTER') {
  const res = await client.get<{ data: AlertRule[] }>(alertRulePath(domain));
  return res.data.data;
}
export async function listAlertRulesPage(
  domain: AlertRuleDomain = 'CLUSTER',
  query: AlertRuleQuery = {},
) {
  const res = await client.get<{ data: PageResult<AlertRule> }>(`${alertRulePath(domain)}/page`, {
    params: query,
  });
  return res.data.data;
}
export async function listAlertRuleRuntime(domain: AlertRuleDomain = 'CLUSTER') {
  const res = await client.get<{ data: AlertRuleRuntime[] }>(`${alertRulePath(domain)}/runtime`);
  return res.data.data;
}

export async function exportAlertRulesTransfer(domain: AlertRuleDomain = 'CLUSTER') {
  const res = await client.get<{ data: AlertRuleTransfer }>(`${alertRulePath(domain)}/transfer`);
  return res.data.data;
}

export async function importAlertRulesTransfer(
  data: AlertRuleTransfer,
  domain: AlertRuleDomain = 'CLUSTER',
) {
  const res = await client.post<{ data: AlertRule[] }>(`${alertRulePath(domain)}/import`, data);
  return res.data.data;
}

export async function createAlertRule(
  data: Partial<AlertRule>,
  domain: AlertRuleDomain = 'CLUSTER',
) {
  const res = await client.post<{ data: AlertRule }>(`${alertRulePath(domain)}/create`, data);
  return res.data.data;
}

export async function updateAlertRule(data: AlertRule, domain: AlertRuleDomain = 'CLUSTER') {
  const res = await client.post<{ data: AlertRule }>(`${alertRulePath(domain)}/update`, data);
  return res.data.data;
}

export async function toggleAlertRule(
  id: number,
  enabled: boolean,
  domain: AlertRuleDomain = 'CLUSTER',
) {
  const res = await client.post<{ data: AlertRule }>(`${alertRulePath(domain)}/toggle`, {
    id,
    enabled,
  });
  return res.data.data;
}

export async function deleteAlertRule(id: number, domain: AlertRuleDomain = 'CLUSTER') {
  await client.post(`${alertRulePath(domain)}/delete`, { id });
}

export async function bulkToggleAlertRules(
  ids: number[],
  enabled: boolean,
  domain: AlertRuleDomain = 'CLUSTER',
) {
  const res = await client.post<{ data: AlertRuleBulkResult }>(
    `${alertRulePath(domain)}/bulk-toggle`,
    {
      ids,
      enabled,
    },
  );
  return res.data.data;
}

export async function bulkDeleteAlertRules(ids: number[], domain: AlertRuleDomain = 'CLUSTER') {
  const res = await client.post<{ data: AlertRuleBulkResult }>(
    `${alertRulePath(domain)}/bulk-delete`,
    {
      ids,
    },
  );
  return res.data.data;
}

export async function testAlertRule(data: Partial<AlertRule>, domain: AlertRuleDomain = 'CLUSTER') {
  const res = await client.post<{ data: AlertRuleTestResult }>(
    `${alertRulePath(domain)}/test`,
    data,
  );
  return res.data.data;
}

// ─── System Alerts ──────────────────────────────────────────────
export async function listSystemAlerts(params?: SystemAlertQuery) {
  const res = await client.get<{ data: SystemAlert[] }>('/system-alerts', { params });
  return res.data.data;
}

export async function listSystemAlertsPage(params: SystemAlertQuery = {}) {
  const res = await client.get<{ data: PageResult<SystemAlert> }>('/system-alerts/page', {
    params,
  });
  return res.data.data;
}

export async function listRelatedSystemAlerts(id: number) {
  const res = await client.get<{ data: SystemAlert[] }>(`/system-alerts/${id}/related`);
  return res.data.data;
}

export async function getCollectorStatus() {
  const res = await client.get<{ data: CollectorStatus }>('/alert-collector-status');
  return res.data.data;
}

export async function acknowledgeAlert(id: number) {
  await client.post('/system-alerts/acknowledge', { id });
}

export async function clearAcknowledgedAlerts() {
  const res = await client.post<{ data: { cleared: number } }>('/system-alerts/clear-acknowledged');
  return res.data.data;
}

export async function listAlertDeliveries(id: number) {
  const res = await client.get<{ data: NotificationDelivery[] }>(`/system-alerts/${id}/deliveries`);
  return res.data.data;
}

export async function retryAlertDelivery(id: number) {
  await client.post(`/system-alerts/deliveries/${id}/retry`);
}

export async function retryAlertDeliveries(ids: number[]) {
  const res = await client.post<{ data: NotificationDeliveryBulkRetryResult }>(
    '/system-alerts/deliveries/retry',
    ids,
  );
  return res.data.data;
}

export async function listAlertDeliveriesPage(params: NotificationDeliveryQuery = {}) {
  const res = await client.get<{ data: PageResult<NotificationDeliveryRecord> }>(
    '/system-alerts/deliveries/page',
    { params },
  );
  return res.data.data;
}

export async function listAlertSilences() {
  const res = await client.get<{ data: AlertSilence[] }>('/alert-silences');
  return res.data.data;
}

export async function createAlertSilence(data: CreateAlertSilence) {
  const res = await client.post<{ data: AlertSilence }>('/alert-silences', data);
  return res.data.data;
}

export async function deleteAlertSilence(id: number) {
  await client.delete(`/alert-silences/${id}`);
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
  configurationAvailable: boolean;
  unavailableReason?: string;
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

export async function deleteNameSvrAddr(namesrvAddr: string): Promise<void> {
  await client.post('/ops/deleteNameSvrAddr', { namesrvAddr });
}

export async function updateIsVIPChannel(useVIPChannel: boolean): Promise<void> {
  await client.post('/ops/updateIsVIPChannel', { useVIPChannel });
}

export async function updateUseTLS(useTLS: boolean): Promise<void> {
  await client.post('/ops/updateUseTLS', { useTLS });
}
