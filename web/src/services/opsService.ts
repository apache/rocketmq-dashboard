import { USE_MOCK } from '../config';
import * as opsApi from '../api/ops';
import type { AlertRule, SystemAlert, AuditRecord } from '../api/ops';
import { mockAlertRules } from '../mock/alerts';
import { mockAuditRecords } from '../mock/audit';
import { systemAlerts as mockSystemAlerts } from '../mock/dashboard';

const alertRulesState = mockAlertRules as unknown as AlertRule[];

export async function listAlertRules(): Promise<AlertRule[]> {
  if (USE_MOCK) return alertRulesState;
  return opsApi.listAlertRules();
}

export async function createAlertRule(data: Partial<AlertRule>): Promise<AlertRule> {
  if (USE_MOCK) {
    const rule: AlertRule = {
      id: `alert-${Date.now()}`,
      name: '',
      metric: '',
      operator: '>',
      threshold: 0,
      thresholdUnit: '',
      duration: '',
      channels: [],
      enabled: true,
      lastTriggered: null,
      description: '',
      ...data,
    };
    alertRulesState.push(rule);
    return rule;
  }
  return opsApi.createAlertRule(data);
}

export async function updateAlertRule(data: AlertRule): Promise<AlertRule> {
  if (USE_MOCK) {
    const index = alertRulesState.findIndex((rule) => rule.id === data.id);
    if (index >= 0) alertRulesState[index] = data;
    return data;
  }
  return opsApi.updateAlertRule(data);
}

export async function toggleAlertRule(id: string, enabled: boolean): Promise<AlertRule> {
  if (USE_MOCK) {
    const rule = alertRulesState.find((item) => item.id === id);
    if (!rule) throw new Error(`Alert rule not found: ${id}`);
    rule.enabled = enabled;
    return rule;
  }
  return opsApi.toggleAlertRule(id, enabled);
}

export async function deleteAlertRule(id: string): Promise<void> {
  if (USE_MOCK) {
    const idx = alertRulesState.findIndex((rule) => rule.id === id);
    if (idx >= 0) alertRulesState.splice(idx, 1);
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
