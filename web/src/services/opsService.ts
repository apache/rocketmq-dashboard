import { USE_MOCK } from '../config';
import * as opsApi from '../api/ops';
import type { AlertRule, SystemAlert, AuditRecord } from '../api/ops';
import { mockAlertRules } from '../mock/alerts';
import { mockAuditRecords } from '../mock/audit';
import { systemAlerts as mockSystemAlerts } from '../mock/dashboard';

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
  _params?: Record<string, unknown>,
): Promise<{ list: AuditRecord[]; total: number }> {
  if (USE_MOCK)
    return { list: mockAuditRecords as unknown as AuditRecord[], total: mockAuditRecords.length };
  return opsApi.listAuditRecords(_params);
}
