/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import type { AuditSummary } from '../../api/audit';
import type { AuditRecord } from '../../api/ops';
import {
  getAuditOperationPresentation,
  isControlPlaneAuditRecord,
  normalizeAuditCode,
} from './auditPresentation';

export type AuditRiskLevel = 'healthy' | 'notice' | 'warning' | 'critical';

export type AuditRiskIssueCode =
  | 'NO_MATCHING_RECORDS'
  | 'HIGH_FAILURE_RATE'
  | 'PARTIAL_OUTCOMES'
  | 'CONTROL_PLANE_FAILURES'
  | 'HIGH_RISK_FAILURES'
  | 'REPEATED_TARGET_FAILURES'
  | 'OPERATOR_CONCENTRATION';

export interface AuditRiskIssue {
  code: AuditRiskIssueCode;
  level: AuditRiskLevel;
  count?: number;
  percent?: number;
  operator?: string;
  target?: string;
  threshold?: number;
}

export interface AuditRiskTarget {
  key: string;
  target: string;
  resourceType: string;
  clusterId: string;
  operationTypes: string[];
  total: number;
  failed: number;
  partial: number;
  latestAt: string | null;
  level: AuditRiskLevel;
}

export interface AuditRiskOperator {
  name: string;
  count: number;
  failed: number;
  partial: number;
  percent: number;
  level: AuditRiskLevel;
}

export interface AuditRiskRecord {
  id: number;
  timestamp: string;
  operator: string;
  operationType: string;
  resourceType: string;
  target: string;
  clusterId: string;
  result: string;
  level: AuditRiskLevel;
  reason: 'failed' | 'partial' | 'high-risk';
}

export interface AuditRiskInsights {
  level: AuditRiskLevel;
  total: number;
  pageRecordCount: number;
  failed: number;
  partial: number;
  failureRate: number;
  partialRate: number;
  controlPlaneFailureCount: number;
  highRiskFailureCount: number;
  topOperator: AuditRiskOperator | null;
  hotTargets: AuditRiskTarget[];
  riskyRecords: AuditRiskRecord[];
  issues: AuditRiskIssue[];
}

const FAILURE_RATE_WARNING_PERCENT = 10;
const FAILURE_RATE_CRITICAL_PERCENT = 30;
const PARTIAL_RATE_NOTICE_PERCENT = 15;
const OPERATOR_CONCENTRATION_NOTICE_PERCENT = 60;
const REPEATED_TARGET_FAILURE_THRESHOLD = 2;
const MIN_RECORDS_FOR_RATE = 5;
const MIN_RECORDS_FOR_OPERATOR_CONCENTRATION = 4;

const levelWeight: Record<AuditRiskLevel, number> = {
  healthy: 0,
  notice: 1,
  warning: 2,
  critical: 3,
};

const highRiskOperationCodes = new Set([
  'DELETE_TOPIC',
  'DELETE_GROUP',
  'RESET_OFFSET',
  'REMOVE_PROXY_ADDRESS',
  'RELOAD_PROXY_CONFIG',
  'UPDATE_BROKER_CONFIG',
  'UPDATE_CLUSTER_CONFIG',
  'RESTART_BROKER',
  'DELETE_ACL_RULE',
  'DELETE_ACL_USER',
  'UPSERT_PLAIN_ACCESS_CONFIG',
  'DELETE_DATA_SOURCE',
  'DELETE_CLOUD_CREDENTIAL',
  'DELETE_ALERT_RULE',
  'CLEAR_ACKNOWLEDGED_SYSTEM_ALERTS',
  'DELETE_INSTANCE',
  'RENEW_K8S_CERTIFICATE',
  'DELETE_K8S_CERTIFICATE',
]);

const roundPercent = (value: number): number => Math.round(value * 10) / 10;

const percent = (count: number, total: number): number => {
  if (total <= 0) return 0;
  return roundPercent((count / total) * 100);
};

const maxLevel = (levels: AuditRiskLevel[]): AuditRiskLevel =>
  levels.reduce<AuditRiskLevel>(
    (current, next) => (levelWeight[next] > levelWeight[current] ? next : current),
    'healthy',
  );

const normalizeText = (value: string | null | undefined, fallback = '-'): string => {
  const text = value?.trim();
  return text || fallback;
};

export const normalizeAuditResult = (result: string | null | undefined): string => {
  const normalized = normalizeAuditCode(result);
  return normalized === 'FAILURE' ? 'FAILED' : normalized;
};

export const isFailedAuditResult = (result: string | null | undefined): boolean =>
  normalizeAuditResult(result) === 'FAILED';

export const isPartialAuditResult = (result: string | null | undefined): boolean =>
  normalizeAuditResult(result) === 'PARTIAL';

export const isUnsuccessfulAuditResult = (result: string | null | undefined): boolean =>
  isFailedAuditResult(result) || isPartialAuditResult(result);

export const isHighRiskAuditOperation = (operationType: string | null | undefined): boolean => {
  const normalized = normalizeAuditCode(operationType);
  return (
    highRiskOperationCodes.has(normalized) ||
    normalized.startsWith('DELETE_') ||
    normalized.startsWith('REMOVE_') ||
    normalized.startsWith('RESET_') ||
    normalized.startsWith('RESTART_') ||
    normalized.startsWith('CLEAR_')
  );
};

const compareTimestampDesc = (left: string | null, right: string | null): number => {
  const leftTime = left ? new Date(left).getTime() : 0;
  const rightTime = right ? new Date(right).getTime() : 0;
  return rightTime - leftTime;
};

const buildTargetKey = (record: AuditRecord): string =>
  [
    normalizeText(record.clusterId, 'global'),
    normalizeAuditCode(record.resourceType) || 'RESOURCE',
    normalizeText(record.target, '-'),
  ].join('|');

const toRiskRecord = (record: AuditRecord): AuditRiskRecord | null => {
  const failed = isFailedAuditResult(record.result);
  const partial = isPartialAuditResult(record.result);
  const highRisk = isHighRiskAuditOperation(record.operationType);
  if (!failed && !partial && !highRisk) return null;

  return {
    id: record.id,
    timestamp: record.timestamp,
    operator: normalizeText(record.operator),
    operationType: normalizeAuditCode(record.operationType) || normalizeText(record.operationType),
    resourceType: normalizeAuditCode(record.resourceType) || normalizeText(record.resourceType),
    target: normalizeText(record.target),
    clusterId: normalizeText(record.clusterId),
    result: normalizeAuditResult(record.result) || normalizeText(record.result),
    level: failed && highRisk ? 'critical' : failed ? 'warning' : partial ? 'notice' : 'notice',
    reason: failed ? 'failed' : partial ? 'partial' : 'high-risk',
  };
};

const buildHotTargets = (records: AuditRecord[]): AuditRiskTarget[] => {
  const groups = new Map<string, AuditRiskTarget>();
  records.forEach((record) => {
    if (!isUnsuccessfulAuditResult(record.result)) return;
    const key = buildTargetKey(record);
    const existing =
      groups.get(key) ??
      ({
        key,
        target: normalizeText(record.target),
        resourceType: normalizeAuditCode(record.resourceType) || normalizeText(record.resourceType),
        clusterId: normalizeText(record.clusterId),
        operationTypes: [],
        total: 0,
        failed: 0,
        partial: 0,
        latestAt: null,
        level: 'notice',
      } satisfies AuditRiskTarget);
    existing.total += 1;
    if (isFailedAuditResult(record.result)) existing.failed += 1;
    if (isPartialAuditResult(record.result)) existing.partial += 1;
    const operationType =
      normalizeAuditCode(record.operationType) || normalizeText(record.operationType);
    if (!existing.operationTypes.includes(operationType))
      existing.operationTypes.push(operationType);
    if (compareTimestampDesc(record.timestamp, existing.latestAt) < 0) {
      existing.latestAt = record.timestamp;
    }
    existing.level =
      existing.failed >= REPEATED_TARGET_FAILURE_THRESHOLD ||
      existing.operationTypes.some(isHighRiskAuditOperation)
        ? 'warning'
        : 'notice';
    groups.set(key, existing);
  });

  return [...groups.values()]
    .filter((target) => target.total >= REPEATED_TARGET_FAILURE_THRESHOLD)
    .sort(
      (left, right) =>
        levelWeight[right.level] - levelWeight[left.level] ||
        right.failed - left.failed ||
        right.partial - left.partial ||
        compareTimestampDesc(left.latestAt, right.latestAt) ||
        left.target.localeCompare(right.target),
    )
    .slice(0, 5);
};

const buildTopOperator = (records: AuditRecord[]): AuditRiskOperator | null => {
  if (records.length === 0) return null;
  const operators = new Map<string, AuditRiskOperator>();
  records.forEach((record) => {
    const name = normalizeText(record.operator, 'system');
    const existing =
      operators.get(name) ??
      ({
        name,
        count: 0,
        failed: 0,
        partial: 0,
        percent: 0,
        level: 'healthy',
      } satisfies AuditRiskOperator);
    existing.count += 1;
    if (isFailedAuditResult(record.result)) existing.failed += 1;
    if (isPartialAuditResult(record.result)) existing.partial += 1;
    operators.set(name, existing);
  });
  const [topOperator] = [...operators.values()].sort(
    (left, right) => right.count - left.count || left.name.localeCompare(right.name),
  );
  if (!topOperator) return null;

  topOperator.percent = percent(topOperator.count, records.length);
  topOperator.level =
    records.length >= MIN_RECORDS_FOR_OPERATOR_CONCENTRATION &&
    topOperator.percent >= OPERATOR_CONCENTRATION_NOTICE_PERCENT
      ? 'notice'
      : 'healthy';
  return topOperator;
};

const buildRiskIssues = (params: {
  total: number;
  failed: number;
  partial: number;
  failureRate: number;
  partialRate: number;
  controlPlaneFailureCount: number;
  highRiskFailureCount: number;
  topOperator: AuditRiskOperator | null;
  hotTargets: AuditRiskTarget[];
}): AuditRiskIssue[] => {
  const issues: AuditRiskIssue[] = [];

  if (params.total === 0) {
    issues.push({ code: 'NO_MATCHING_RECORDS', level: 'notice' });
    return issues;
  }

  if (params.total >= MIN_RECORDS_FOR_RATE && params.failureRate >= FAILURE_RATE_WARNING_PERCENT) {
    issues.push({
      code: 'HIGH_FAILURE_RATE',
      level:
        params.failureRate >= FAILURE_RATE_CRITICAL_PERCENT || params.failed >= 5
          ? 'critical'
          : 'warning',
      count: params.failed,
      percent: params.failureRate,
      threshold: FAILURE_RATE_WARNING_PERCENT,
    });
  }

  if (params.partialRate >= PARTIAL_RATE_NOTICE_PERCENT) {
    issues.push({
      code: 'PARTIAL_OUTCOMES',
      level: 'notice',
      count: params.partial,
      percent: params.partialRate,
      threshold: PARTIAL_RATE_NOTICE_PERCENT,
    });
  }

  if (params.controlPlaneFailureCount > 0) {
    issues.push({
      code: 'CONTROL_PLANE_FAILURES',
      level: params.controlPlaneFailureCount >= 3 ? 'warning' : 'notice',
      count: params.controlPlaneFailureCount,
    });
  }

  if (params.highRiskFailureCount > 0) {
    issues.push({
      code: 'HIGH_RISK_FAILURES',
      level: params.highRiskFailureCount >= 2 ? 'critical' : 'warning',
      count: params.highRiskFailureCount,
    });
  }

  const repeatedTarget = params.hotTargets[0];
  if (repeatedTarget) {
    issues.push({
      code: 'REPEATED_TARGET_FAILURES',
      level: repeatedTarget.level,
      count: repeatedTarget.total,
      target: repeatedTarget.target,
    });
  }

  if (params.topOperator?.level === 'notice') {
    issues.push({
      code: 'OPERATOR_CONCENTRATION',
      level: 'notice',
      count: params.topOperator.count,
      percent: params.topOperator.percent,
      operator: params.topOperator.name,
      threshold: OPERATOR_CONCENTRATION_NOTICE_PERCENT,
    });
  }

  return issues.sort(
    (left, right) =>
      levelWeight[right.level] - levelWeight[left.level] ||
      (right.count ?? 0) - (left.count ?? 0) ||
      left.code.localeCompare(right.code),
  );
};

export function buildAuditRiskInsights(
  summary: AuditSummary | null | undefined,
  records: AuditRecord[] | null | undefined,
): AuditRiskInsights {
  const safeRecords = records ?? [];
  const total = Math.max(0, summary?.total ?? safeRecords.length);
  const failed = Math.max(
    0,
    summary?.failed ?? safeRecords.filter((record) => isFailedAuditResult(record.result)).length,
  );
  const partial = Math.max(
    0,
    summary?.partial ?? safeRecords.filter((record) => isPartialAuditResult(record.result)).length,
  );
  const failureRate = percent(failed, total);
  const partialRate = percent(partial, total);
  const hotTargets = buildHotTargets(safeRecords);
  const topOperator = buildTopOperator(safeRecords);
  const riskyRecords = safeRecords
    .map(toRiskRecord)
    .filter((record): record is AuditRiskRecord => record != null)
    .sort(
      (left, right) =>
        levelWeight[right.level] - levelWeight[left.level] ||
        compareTimestampDesc(left.timestamp, right.timestamp),
    )
    .slice(0, 6);
  const controlPlaneFailureCount = safeRecords.filter(
    (record) => isControlPlaneAuditRecord(record) && isUnsuccessfulAuditResult(record.result),
  ).length;
  const highRiskFailureCount = safeRecords.filter(
    (record) =>
      isHighRiskAuditOperation(record.operationType) && isFailedAuditResult(record.result),
  ).length;
  const issues = buildRiskIssues({
    total,
    failed,
    partial,
    failureRate,
    partialRate,
    controlPlaneFailureCount,
    highRiskFailureCount,
    topOperator,
    hotTargets,
  });

  return {
    level: maxLevel(issues.map((issue) => issue.level)),
    total,
    pageRecordCount: safeRecords.length,
    failed,
    partial,
    failureRate,
    partialRate,
    controlPlaneFailureCount,
    highRiskFailureCount,
    topOperator,
    hotTargets,
    riskyRecords,
    issues,
  };
}

export function auditRiskOperationLabel(operationType: string): string {
  const presentation = getAuditOperationPresentation(operationType);
  return presentation.label;
}
