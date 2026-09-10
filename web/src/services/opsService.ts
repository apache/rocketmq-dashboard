import {
  exportAuditLogs as exportAuditLogsApi,
  fetchAuditFilterOptions,
  fetchAuditSummary,
} from '../api/audit';
import type { AuditFilter, AuditFilterOptions, AuditSummary } from '../api/audit';
import { isMockMode } from './dataMode';
import * as opsApi from '../api/ops';
import type {
  AlertRule,
  AlertRuleQuery,
  AlertRuleRuntime,
  AlertRuleBulkResult,
  AlertRuleDomain,
  AlertRuleTestResult,
  AlertRuleTransfer,
  AlertRuleImportConflictStrategy,
  AlertRuleImportPreview,
  AlertRuleImportPreviewItem,
  AlertRuleImportResult,
  NativeAlertMetricInfo,
  CollectorStatus,
  SystemAlert,
  SystemAlertQuery,
  AuditQuery,
  AuditRecord,
  PageResult,
  NotificationDelivery,
  NotificationDeliveryBulkRetryResult,
  NotificationDeliveryQuery,
  NotificationDeliveryRecord,
  AlertSilenceQuery,
  AlertSilence,
  CreateAlertSilence,
} from '../api/ops';
import { mockAlertRules } from '../mock/alerts';
import { mockAuditRecords } from '../mock/audit';
import { systemAlerts as mockSystemAlerts } from '../mock/dashboard';

let auditRecordsState = mockAuditRecords as unknown as AuditRecord[];
const initialAlertRules = mockAlertRules as unknown as AlertRule[];
const alertRulesState: Record<AlertRuleDomain, AlertRule[]> = {
  CLUSTER: initialAlertRules.map(copyAlertRule),
  BUSINESS: initialAlertRules.map(copyAlertRule),
};
let alertSilencesState: AlertSilence[] = [];

const ratioAlertMetrics = new Set([
  'broker.disk.usage_ratio',
  'broker.jvm.heap.usage_ratio',
  'broker.send_queue.usage_ratio',
]);

function copyAlertRule(rule: AlertRule): AlertRule {
  return {
    ...rule,
    channels: [...rule.channels],
  };
}

function nextMockAlertRuleId(domain: AlertRuleDomain): number {
  return Math.max(0, ...alertRulesState[domain].map((rule) => rule.id)) + 1;
}

function normalizedAlertRuleThreshold(rule: Partial<AlertRule>): number {
  return rule.thresholdUnit === '%' && ratioAlertMetrics.has(rule.metric?.trim() ?? '')
    ? (rule.threshold ?? 0) / 100
    : (rule.threshold ?? 0);
}

function alertRuleSemanticKey(rule: Partial<AlertRule>, domain: AlertRuleDomain): string {
  const text = (value?: string | null) => value?.trim() ?? '';
  return JSON.stringify([
    domain,
    text(rule.instanceId),
    text(rule.metric),
    text(rule.operator).toUpperCase(),
    normalizedAlertRuleThreshold(rule),
    text(rule.duration).toLowerCase(),
    text(rule.aggregation ?? 'LAST').toUpperCase(),
    Math.max(0, rule.windowSeconds ?? 0),
    text(rule.brokerName),
    text(rule.clusterName),
    text(rule.consumerGroup),
    text(rule.topic),
    Math.max(1, rule.consecutiveSamples ?? 1),
  ]);
}

function mockAlertRuleImportPreview(
  transfer: AlertRuleTransfer,
  domain: AlertRuleDomain,
): AlertRuleImportPreview {
  const existingByKey = new Map(
    alertRulesState[domain].map((rule) => [alertRuleSemanticKey(rule, domain), rule]),
  );
  const importedRowsByKey = new Map<string, number>();
  const items: AlertRuleImportPreviewItem[] = transfer.rules.map((rule, index) => {
    const rowNumber = index + 1;
    if (!rule?.name?.trim()) {
      return {
        rowNumber,
        status: 'INVALID',
        name: rule?.name,
        metric: rule?.metric,
        error: 'name: name is required',
      };
    }
    const key = alertRuleSemanticKey(rule, domain);
    const firstRow = importedRowsByKey.get(key);
    if (firstRow != null) {
      return {
        rowNumber,
        status: 'INVALID',
        name: rule.name,
        metric: rule.metric,
        error: `Duplicate evaluation conditions within import document; matches row ${firstRow}`,
      };
    }
    importedRowsByKey.set(key, rowNumber);
    const existing = existingByKey.get(key);
    return {
      rowNumber,
      status: existing ? 'DUPLICATE' : 'NEW',
      name: rule.name,
      metric: rule.metric,
      existingRuleId: existing?.id,
      existingRuleName: existing?.name,
    };
  });
  const count = (status: AlertRuleImportPreviewItem['status']) =>
    items.filter((item) => item.status === status).length;
  return {
    totalCount: items.length,
    newCount: count('NEW'),
    duplicateCount: count('DUPLICATE'),
    invalidCount: count('INVALID'),
    items,
  };
}

function copySystemAlert(alert: SystemAlert): SystemAlert {
  return { ...alert };
}

function copyAuditRecord(record: AuditRecord): AuditRecord {
  return { ...record };
}

function includesIgnoreCase(value: string | null | undefined, search: string): boolean {
  return (value ?? '').toLowerCase().includes(search);
}

function distinctSorted(values: Array<string | null | undefined>): string[] {
  return [...new Set(values.filter((value) => value?.trim()) as string[])].sort();
}

function getMockAuditFilterOptions(): AuditFilterOptions {
  return {
    operationTypes: distinctSorted(auditRecordsState.map((record) => record.operationType)),
    resourceTypes: distinctSorted(auditRecordsState.map((record) => record.resourceType)),
    clusterIds: distinctSorted(auditRecordsState.map((record) => record.clusterId)),
    results: distinctSorted(auditRecordsState.map((record) => record.result)),
  };
}

function filterAuditRecords(params: AuditFilter): AuditRecord[] {
  return auditRecordsState.filter((record) => {
    const search = params.search?.trim().toLowerCase();
    if (
      search &&
      !includesIgnoreCase(record.operator, search) &&
      !includesIgnoreCase(record.target, search) &&
      !includesIgnoreCase(record.detail, search)
    ) {
      return false;
    }
    if (params.operationType && record.operationType !== params.operationType) return false;
    if (params.resourceType && record.resourceType !== params.resourceType) return false;
    if (params.clusterId && record.clusterId !== params.clusterId) return false;
    if (params.startDate && record.timestamp < params.startDate) return false;
    if (params.endDate && record.timestamp > `${params.endDate} 23:59:59`) return false;
    return !params.result || record.result.toUpperCase() === params.result.toUpperCase();
  });
}

function toCsvCell(value: string | null | undefined): string {
  let text = value ?? '';
  if (text.length > 0 && '=+-@\t\r\n'.includes(text[0])) text = `'${text}`;
  return `"${text.replace(/"/g, '""')}"`;
}

function formatAuditCsv(records: AuditRecord[]): string {
  const header =
    'timestamp,operator,operationType,resourceType,target,clusterId,detail,result,errorMessage\r\n';
  const rows = records.map((record) =>
    [
      record.timestamp,
      record.operator,
      record.operationType,
      record.resourceType,
      record.target,
      record.clusterId,
      record.detail,
      record.result,
      record.errorMessage,
    ]
      .map(toCsvCell)
      .join(','),
  );
  return `\uFEFF${header}${rows.length > 0 ? `${rows.join('\r\n')}\r\n` : ''}`;
}

export async function listAlertRules(domain: AlertRuleDomain = 'CLUSTER'): Promise<AlertRule[]> {
  if (isMockMode()) return alertRulesState[domain].map(copyAlertRule);
  return opsApi.listAlertRules(domain);
}
export async function listAlertRulesPage(
  domain: AlertRuleDomain = 'CLUSTER',
  query: AlertRuleQuery = {},
): Promise<PageResult<AlertRule>> {
  if (!isMockMode()) return opsApi.listAlertRulesPage(domain, query);

  const search = query.search?.trim().toLowerCase();
  const page = Math.max(1, query.page ?? 1);
  const pageSize = Math.min(100, Math.max(1, query.pageSize ?? 20));
  const filtered = alertRulesState[domain]
    .filter((rule) => query.enabled == null || rule.enabled === query.enabled)
    .filter(
      (rule) =>
        !search || includesIgnoreCase(rule.name, search) || includesIgnoreCase(rule.metric, search),
    )
    .sort((left, right) => left.name.localeCompare(right.name) || left.id - right.id);
  const start = (page - 1) * pageSize;
  return {
    items: filtered.slice(start, start + pageSize).map(copyAlertRule),
    total: filtered.length,
    page,
    size: pageSize,
  };
}
export async function listAlertRuleRuntime(
  domain: AlertRuleDomain = 'CLUSTER',
): Promise<AlertRuleRuntime[]> {
  if (isMockMode()) return [];
  return opsApi.listAlertRuleRuntime(domain);
}

export async function exportAlertRulesTransfer(
  domain: AlertRuleDomain = 'CLUSTER',
): Promise<AlertRuleTransfer> {
  if (isMockMode())
    return { version: 1, domain, rules: alertRulesState[domain].map(copyAlertRule) };
  return opsApi.exportAlertRulesTransfer(domain);
}

export async function importAlertRulesTransfer(
  data: AlertRuleTransfer,
  domain: AlertRuleDomain = 'CLUSTER',
): Promise<AlertRule[]> {
  if (!isMockMode()) return opsApi.importAlertRulesTransfer(data, domain);
  const startId = nextMockAlertRuleId(domain);
  const imported = data.rules.map((rule, index) => ({
    ...copyAlertRule(rule as AlertRule),
    id: startId + index,
  }));
  alertRulesState[domain].push(...imported);
  return imported.map(copyAlertRule);
}

export async function previewAlertRulesImport(
  data: AlertRuleTransfer,
  domain: AlertRuleDomain = 'CLUSTER',
): Promise<AlertRuleImportPreview> {
  if (!isMockMode()) return opsApi.previewAlertRulesImport(data, domain);
  return mockAlertRuleImportPreview(data, domain);
}

export async function applyAlertRulesImport(
  transfer: AlertRuleTransfer,
  strategy: AlertRuleImportConflictStrategy,
  domain: AlertRuleDomain = 'CLUSTER',
): Promise<AlertRuleImportResult> {
  if (!isMockMode()) return opsApi.applyAlertRulesImport(transfer, strategy, domain);
  const preview = mockAlertRuleImportPreview(transfer, domain);
  if (preview.invalidCount > 0) throw new Error('Alert rule import contains invalid rows');
  if (strategy === 'FAIL' && preview.duplicateCount > 0) {
    throw new Error('Alert rule import contains duplicate evaluation conditions');
  }

  const changedRules: AlertRule[] = [];
  let createdCount = 0;
  let replacedCount = 0;
  let skippedCount = 0;
  let nextId = nextMockAlertRuleId(domain);
  for (const item of preview.items) {
    const imported = transfer.rules[item.rowNumber - 1] as AlertRule;
    if (item.status === 'NEW') {
      const created = { ...copyAlertRule(imported), id: nextId++, lastTriggered: null };
      alertRulesState[domain].push(created);
      changedRules.push(copyAlertRule(created));
      createdCount += 1;
    } else if (strategy === 'SKIP') {
      skippedCount += 1;
    } else {
      const existingIndex = alertRulesState[domain].findIndex(
        (rule) => rule.id === item.existingRuleId,
      );
      const replacement = {
        ...copyAlertRule(imported),
        id: item.existingRuleId ?? alertRulesState[domain][existingIndex].id,
        lastTriggered: null,
      };
      alertRulesState[domain][existingIndex] = replacement;
      changedRules.push(copyAlertRule(replacement));
      replacedCount += 1;
    }
  }
  return { strategy, createdCount, replacedCount, skippedCount, changedRules };
}

export async function listNativeAlertMetrics(
  instanceId: string,
  domain: AlertRuleDomain,
): Promise<NativeAlertMetricInfo[]> {
  if (isMockMode()) {
    return domain === 'BUSINESS'
      ? [
          {
            key: 'consumer.lag.total',
            label: 'Consumer lag total',
            thresholdUnit: 'messages',
            supportsConsumerGroup: true,
          },
          {
            key: 'consumer.lag.max_queue',
            label: 'Consumer lag max queue',
            thresholdUnit: 'messages',
            supportsConsumerGroup: true,
          },
          {
            key: 'dlq.message.count',
            label: 'DLQ message count',
            thresholdUnit: 'messages',
            supportsConsumerGroup: true,
          },
        ]
      : [
          {
            key: 'nameserver.availability',
            label: 'NameServer availability',
            thresholdUnit: '',
            supportsConsumerGroup: false,
          },
          {
            key: 'broker.availability',
            label: 'Broker availability',
            thresholdUnit: '',
            supportsConsumerGroup: false,
          },
          {
            key: 'broker.disk.usage_ratio',
            label: 'Broker disk usage ratio',
            thresholdUnit: 'ratio',
            supportsConsumerGroup: false,
          },
        ];
  }
  return opsApi.listNativeAlertMetrics(instanceId, domain);
}

export async function createAlertRule(
  data: Partial<AlertRule>,
  domain: AlertRuleDomain = 'CLUSTER',
): Promise<AlertRule> {
  if (isMockMode()) {
    const rule: AlertRule = {
      id: nextMockAlertRuleId(domain),
      name: '',
      metric: '',
      operator: '>',
      threshold: 0,
      thresholdUnit: '',
      duration: '',
      enabled: true,
      lastTriggered: null,
      description: '',
      ...data,
      channels: [...(data.channels ?? [])],
    };
    alertRulesState[domain].push(rule);
    return copyAlertRule(rule);
  }
  return opsApi.createAlertRule(data, domain);
}

export async function updateAlertRule(
  data: AlertRule,
  domain: AlertRuleDomain = 'CLUSTER',
): Promise<AlertRule> {
  if (isMockMode()) {
    const index = alertRulesState[domain].findIndex((rule) => rule.id === data.id);
    if (index < 0) throw new Error(`Alert rule not found: ${data.id}`);
    const rule = copyAlertRule(data);
    alertRulesState[domain][index] = rule;
    return copyAlertRule(rule);
  }
  return opsApi.updateAlertRule(data, domain);
}

export async function toggleAlertRule(
  id: number,
  enabled: boolean,
  domain: AlertRuleDomain = 'CLUSTER',
): Promise<AlertRule> {
  if (isMockMode()) {
    const rule = alertRulesState[domain].find((item) => item.id === id);
    if (!rule) throw new Error(`Alert rule not found: ${id}`);
    rule.enabled = enabled;
    return copyAlertRule(rule);
  }
  return opsApi.toggleAlertRule(id, enabled, domain);
}

export async function deleteAlertRule(
  id: number,
  domain: AlertRuleDomain = 'CLUSTER',
): Promise<void> {
  if (isMockMode()) {
    const idx = alertRulesState[domain].findIndex((rule) => rule.id === id);
    if (idx >= 0) alertRulesState[domain].splice(idx, 1);
    return;
  }
  return opsApi.deleteAlertRule(id, domain);
}

export async function bulkToggleAlertRules(
  ids: number[],
  enabled: boolean,
  domain: AlertRuleDomain = 'CLUSTER',
): Promise<AlertRuleBulkResult> {
  if (!isMockMode()) return opsApi.bulkToggleAlertRules(ids, enabled, domain);
  const succeededIds: number[] = [];
  const failures: Record<string, string> = {};
  const updatedRules: AlertRule[] = [];
  for (const id of [...new Set(ids)]) {
    const rule = alertRulesState[domain].find((item) => item.id === id);
    if (!rule) {
      failures[String(id)] = 'Alert rule not found';
      continue;
    }
    rule.enabled = enabled;
    succeededIds.push(id);
    updatedRules.push(copyAlertRule(rule));
  }
  return { succeededIds, failures, updatedRules };
}

export async function bulkDeleteAlertRules(
  ids: number[],
  domain: AlertRuleDomain = 'CLUSTER',
): Promise<AlertRuleBulkResult> {
  if (!isMockMode()) return opsApi.bulkDeleteAlertRules(ids, domain);
  const succeededIds: number[] = [];
  const failures: Record<string, string> = {};
  for (const id of [...new Set(ids)]) {
    const index = alertRulesState[domain].findIndex((item) => item.id === id);
    if (index < 0) {
      failures[String(id)] = 'Alert rule not found';
      continue;
    }
    alertRulesState[domain].splice(index, 1);
    succeededIds.push(id);
  }
  return { succeededIds, failures, updatedRules: [] };
}

export async function testAlertRule(
  data: Partial<AlertRule>,
  domain: AlertRuleDomain = 'CLUSTER',
): Promise<AlertRuleTestResult> {
  if (isMockMode()) return { samples: [] };
  return opsApi.testAlertRule(data, domain);
}

export async function listSystemAlerts(): Promise<SystemAlert[]> {
  if (isMockMode()) return (mockSystemAlerts as unknown as SystemAlert[]).map(copySystemAlert);
  return opsApi.listSystemAlerts();
}

export async function listSystemAlertsPage(
  params: SystemAlertQuery = {},
): Promise<PageResult<SystemAlert>> {
  if (!isMockMode()) return opsApi.listSystemAlertsPage(params);
  const page = params.page ?? 1;
  const pageSize = params.pageSize ?? 20;
  const normalizedLevel = params.level?.toLowerCase();
  const normalizedTransition = params.transition?.toUpperCase();
  const filtered = (mockSystemAlerts as unknown as SystemAlert[]).filter((alert) => {
    if (normalizedLevel && alert.level.toLowerCase() !== normalizedLevel) return false;
    if (params.domain && alert.domain !== params.domain) return false;
    if (params.instanceId && alert.instanceId !== params.instanceId) return false;
    if (normalizedTransition && alert.transition !== normalizedTransition) return false;
    if (params.labelKey && alert.labels?.[params.labelKey] !== params.labelValue) return false;
    if (
      params.notificationSuppressed != null &&
      alert.notificationSuppressed !== params.notificationSuppressed
    ) {
      return false;
    }
    const alertTime = new Date(alert.time).getTime();
    if (params.from && alertTime < new Date(params.from).getTime()) return false;
    if (params.to && alertTime > new Date(params.to).getTime()) return false;
    return true;
  });
  const start = (page - 1) * pageSize;
  return {
    items: filtered.slice(start, start + pageSize).map(copySystemAlert),
    total: filtered.length,
    page,
    size: pageSize,
  };
}

export async function listRelatedSystemAlerts(id: number): Promise<SystemAlert[]> {
  if (isMockMode()) return [];
  return opsApi.listRelatedSystemAlerts(id);
}

export async function getCollectorStatus(): Promise<CollectorStatus> {
  if (isMockMode()) {
    return {
      collectionInterval: 'PT30S',
      clusterCollectorCount: 0,
      businessCollectorCount: 0,
    };
  }
  return opsApi.getCollectorStatus();
}

export async function acknowledgeAlert(id: number): Promise<void> {
  if (isMockMode()) {
    const a = mockSystemAlerts.find((a: Record<string, unknown>) => a.id === id);
    if (a) (a as Record<string, unknown>).acknowledged = true;
    return;
  }
  return opsApi.acknowledgeAlert(id);
}

export async function clearAcknowledgedAlerts(): Promise<number> {
  if (isMockMode()) {
    const acknowledged = mockSystemAlerts.filter((alert) => alert.acknowledged).length;
    const remaining = mockSystemAlerts.filter((alert) => !alert.acknowledged);
    mockSystemAlerts.splice(0, mockSystemAlerts.length, ...remaining);
    return acknowledged;
  }
  const result = await opsApi.clearAcknowledgedAlerts();
  return result.cleared;
}

export async function listAlertDeliveries(id: number): Promise<NotificationDelivery[]> {
  if (isMockMode()) return [];
  return opsApi.listAlertDeliveries(id);
}

export async function retryAlertDelivery(id: number): Promise<void> {
  if (isMockMode()) return;
  return opsApi.retryAlertDelivery(id);
}

export async function retryAlertDeliveries(
  ids: number[],
): Promise<NotificationDeliveryBulkRetryResult> {
  if (isMockMode()) return { succeededIds: ids, failures: {} };
  return opsApi.retryAlertDeliveries(ids);
}

export async function listAlertDeliveriesPage(
  params: NotificationDeliveryQuery = {},
): Promise<PageResult<NotificationDeliveryRecord>> {
  if (isMockMode()) {
    return { items: [], total: 0, page: params.page ?? 1, size: params.pageSize ?? 20 };
  }
  return opsApi.listAlertDeliveriesPage(params);
}

export async function listAlertSilences(): Promise<AlertSilence[]> {
  if (isMockMode()) return alertSilencesState.map((silence) => ({ ...silence }));
  return opsApi.listAlertSilences();
}

export async function listAlertSilencesPage(
  params: AlertSilenceQuery = {},
): Promise<PageResult<AlertSilence>> {
  if (!isMockMode()) return opsApi.listAlertSilencesPage(params);
  const page = params.page ?? 1;
  const pageSize = params.pageSize ?? 10;
  const start = (page - 1) * pageSize;
  const items = alertSilencesState
    .slice(start, start + pageSize)
    .map((silence) => ({ ...silence }));
  return { items, total: alertSilencesState.length, page, size: pageSize };
}

export async function createAlertSilence(data: CreateAlertSilence): Promise<AlertSilence> {
  if (!isMockMode()) return opsApi.createAlertSilence(data);
  const silence = { ...data, id: Date.now(), createdBy: 'admin' } as AlertSilence;
  alertSilencesState = [silence, ...alertSilencesState];
  return silence;
}

export async function deleteAlertSilence(id: number): Promise<void> {
  if (!isMockMode()) return opsApi.deleteAlertSilence(id);
  alertSilencesState = alertSilencesState.filter((silence) => silence.id !== id);
}

export async function listAuditRecords(params: AuditQuery = {}): Promise<PageResult<AuditRecord>> {
  if (!isMockMode()) return opsApi.listAuditRecords(params);

  const page = params.page ?? 1;
  const pageSize = params.pageSize ?? 20;
  const records = filterAuditRecords(params);
  const from = (page - 1) * pageSize;
  return {
    items: records.slice(from, from + pageSize).map(copyAuditRecord),
    total: records.length,
    page,
    size: pageSize,
  };
}

export async function getAuditFilterOptions(): Promise<AuditFilterOptions> {
  if (!isMockMode()) return fetchAuditFilterOptions();
  return getMockAuditFilterOptions();
}

export async function exportAuditLogs(params: AuditFilter = {}): Promise<string> {
  if (!isMockMode()) return exportAuditLogsApi(params);
  return formatAuditCsv(filterAuditRecords(params));
}

export async function getAuditSummary(params: AuditFilter = {}): Promise<AuditSummary> {
  if (!isMockMode()) return fetchAuditSummary(params);
  const records = filterAuditRecords(params);
  const countBy = (field: 'operationType' | 'resourceType') =>
    Array.from(
      records.reduce((counts, record) => {
        const name = record[field] || 'UNKNOWN';
        counts.set(name, (counts.get(name) || 0) + 1);
        return counts;
      }, new Map<string, number>()),
      ([name, count]) => ({ name, count }),
    )
      .sort((left, right) => right.count - left.count || left.name.localeCompare(right.name))
      .slice(0, 8);
  const resultCount = (name: string) =>
    records.filter((record) => record.result.toUpperCase() === name).length;
  const timestamps = records.map((record) => record.timestamp).sort();
  return {
    total: records.length,
    successful: resultCount('SUCCESS'),
    failed: resultCount('FAILED'),
    partial: resultCount('PARTIAL'),
    uniqueOperators: new Set(records.map((record) => record.operator).filter(Boolean)).size,
    latestAt: timestamps[timestamps.length - 1] || null,
    byOperation: countBy('operationType'),
    byResourceType: countBy('resourceType'),
  };
}

export async function cleanupAuditLogs(beforeDays: number): Promise<number> {
  if (isMockMode()) {
    const cutoff = new Date(Date.now() - beforeDays * 24 * 60 * 60 * 1000);
    const remaining = auditRecordsState.filter((record) => new Date(record.timestamp) >= cutoff);
    const deleted = auditRecordsState.length - remaining.length;
    auditRecordsState = remaining;
    return deleted;
  }
  const result = await opsApi.cleanupAuditLogs(beforeDays);
  return result.deleted;
}
