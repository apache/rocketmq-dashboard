import { USE_MOCK } from '../config';
import * as opsApi from '../api/ops';
import type { AlertRule, SystemAlert, AuditQuery, AuditRecord, PageResult } from '../api/ops';
import { mockAlertRules } from '../mock/alerts';
import { mockAuditRecords } from '../mock/audit';
import { systemAlerts as mockSystemAlerts } from '../mock/dashboard';

let auditRecordsState = mockAuditRecords as unknown as AuditRecord[];

export async function listAlertRules(): Promise<AlertRule[]> {
  if (USE_MOCK) return mockAlertRules as unknown as AlertRule[];
  return opsApi.listAlertRules();
}

export async function createAlertRule(data: Partial<AlertRule>): Promise<void> {
  if (USE_MOCK) {
    mockAlertRules.push(data as never);
    return;
  }
  return opsApi.createAlertRule(data);
}

export async function toggleAlertRule(id: string, enabled: boolean): Promise<void> {
  if (USE_MOCK) {
    const r = mockAlertRules.find((r: Record<string, unknown>) => r.id === id);
    if (r) (r as Record<string, unknown>).enabled = enabled;
    return;
  }
  return opsApi.toggleAlertRule(id, enabled);
}

export async function deleteAlertRule(id: string): Promise<void> {
  if (USE_MOCK) {
    const idx = mockAlertRules.findIndex((r: Record<string, unknown>) => r.id === id);
    if (idx >= 0) mockAlertRules.splice(idx, 1);
    return;
  }
  return opsApi.deleteAlertRule(id);
}

export async function listSystemAlerts(): Promise<SystemAlert[]> {
  if (USE_MOCK) return mockSystemAlerts as unknown as SystemAlert[];
  return opsApi.listSystemAlerts();
}

export async function acknowledgeAlert(id: string): Promise<void> {
  if (USE_MOCK) {
    const a = mockSystemAlerts.find((a: Record<string, unknown>) => a.id === id);
    if (a) (a as Record<string, unknown>).acknowledged = true;
    return;
  }
  return opsApi.acknowledgeAlert(id);
}

export async function listAuditRecords(
  params: AuditQuery = {},
): Promise<PageResult<AuditRecord>> {
  if (!USE_MOCK) return opsApi.listAuditRecords(params);

  const page = params.page ?? 1;
  const pageSize = params.pageSize ?? 20;
  const records = auditRecordsState.filter((record) => {
    const search = params.search?.trim().toLowerCase();
    if (
      search &&
      !record.operator.toLowerCase().includes(search) &&
      !record.target.toLowerCase().includes(search) &&
      !record.detail.toLowerCase().includes(search)
    ) {
      return false;
    }
    if (params.operationType && record.operationType !== params.operationType) return false;
    if (params.startDate && record.timestamp < params.startDate) return false;
    if (params.endDate && record.timestamp > `${params.endDate} 23:59:59`) return false;
    return !params.result || record.result.toUpperCase() === params.result.toUpperCase();
  });
  const from = (page - 1) * pageSize;
  return { items: records.slice(from, from + pageSize), total: records.length, page, size: pageSize };
}

export async function cleanupAuditLogs(beforeDays: number): Promise<number> {
  if (USE_MOCK) {
    const cutoff = new Date(Date.now() - beforeDays * 24 * 60 * 60 * 1000);
    const remaining = auditRecordsState.filter((record) => new Date(record.timestamp) >= cutoff);
    const deleted = auditRecordsState.length - remaining.length;
    auditRecordsState = remaining;
    return deleted;
  }
  const result = await opsApi.cleanupAuditLogs(beforeDays);
  return result.deleted;
}
