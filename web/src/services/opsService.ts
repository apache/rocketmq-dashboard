import { exportAuditLogs as exportAuditLogsApi } from '../api/audit';
import type { AuditFilter } from '../api/audit';
import { isMockMode } from './dataMode';
import * as opsApi from '../api/ops';
import type { AlertRule, SystemAlert, AuditQuery, AuditRecord, PageResult } from '../api/ops';
import { mockAlertRules } from '../mock/alerts';
import { mockAuditRecords } from '../mock/audit';
import { systemAlerts as mockSystemAlerts } from '../mock/dashboard';

let auditRecordsState = mockAuditRecords as unknown as AuditRecord[];
const alertRulesState = mockAlertRules as unknown as AlertRule[];

function copyAlertRule(rule: AlertRule): AlertRule {
  return {
    ...rule,
    channels: [...rule.channels],
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
  const header = 'timestamp,operator,operationType,target,detail,ipAddress,result\r\n';
  const rows = records.map((record) =>
    [
      record.timestamp,
      record.operator,
      record.operationType,
      record.target,
      record.detail,
      record.ipAddress,
      record.result,
    ]
      .map(toCsvCell)
      .join(','),
  );
  return `\uFEFF${header}${rows.length > 0 ? `${rows.join('\r\n')}\r\n` : ''}`;
}

export async function listAlertRules(): Promise<AlertRule[]> {
  if (isMockMode()) return alertRulesState.map(copyAlertRule);
  return opsApi.listAlertRules();
}

export async function createAlertRule(data: Partial<AlertRule>): Promise<AlertRule> {
  if (isMockMode()) {
    const rule: AlertRule = {
      id: `alert-${Date.now()}`,
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
    alertRulesState.push(rule);
    return copyAlertRule(rule);
  }
  return opsApi.createAlertRule(data);
}

export async function updateAlertRule(data: AlertRule): Promise<AlertRule> {
  if (isMockMode()) {
    const index = alertRulesState.findIndex((rule) => rule.id === data.id);
    if (index < 0) throw new Error(`Alert rule not found: ${data.id}`);
    const rule = copyAlertRule(data);
    alertRulesState[index] = rule;
    return copyAlertRule(rule);
  }
  return opsApi.updateAlertRule(data);
}

export async function toggleAlertRule(id: string, enabled: boolean): Promise<AlertRule> {
  if (isMockMode()) {
    const rule = alertRulesState.find((item) => item.id === id);
    if (!rule) throw new Error(`Alert rule not found: ${id}`);
    rule.enabled = enabled;
    return copyAlertRule(rule);
  }
  return opsApi.toggleAlertRule(id, enabled);
}

export async function deleteAlertRule(id: string): Promise<void> {
  if (isMockMode()) {
    const idx = alertRulesState.findIndex((rule) => rule.id === id);
    if (idx >= 0) alertRulesState.splice(idx, 1);
    return;
  }
  return opsApi.deleteAlertRule(id);
}

export async function listSystemAlerts(): Promise<SystemAlert[]> {
  if (isMockMode()) return (mockSystemAlerts as unknown as SystemAlert[]).map(copySystemAlert);
  return opsApi.listSystemAlerts();
}

export async function acknowledgeAlert(id: string): Promise<void> {
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

export async function exportAuditLogs(params: AuditFilter = {}): Promise<string> {
  if (!isMockMode()) return exportAuditLogsApi(params);
  return formatAuditCsv(filterAuditRecords(params));
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
