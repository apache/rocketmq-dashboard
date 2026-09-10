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

import type { LiteTopicItem, LiteTopicQuota } from '../api/liteTopic';

export type LiteTopicCapacityLevel = 'healthy' | 'notice' | 'warning' | 'critical';

export type LiteTopicCapacityIssueCode =
  | 'TOPIC_QUOTA_CRITICAL'
  | 'TOPIC_QUOTA_WARNING'
  | 'SESSION_QUOTA_CRITICAL'
  | 'SESSION_QUOTA_WARNING'
  | 'CREATION_RATE_CRITICAL'
  | 'CREATION_RATE_WARNING'
  | 'EXPIRED_PATTERNS'
  | 'EXPIRING_PATTERNS'
  | 'BACKLOG_HOTSPOT'
  | 'UNKNOWN_TTL_STATUS'
  | 'IDLE_PATTERNS';

export type LiteTopicRecommendationCode =
  | 'REQUEST_TOPIC_QUOTA'
  | 'REQUEST_SESSION_QUOTA'
  | 'THROTTLE_CREATION'
  | 'EXTEND_TTL'
  | 'CLEAN_EXPIRED'
  | 'DRAIN_BACKLOG'
  | 'CHECK_UNKNOWN_STATUS'
  | 'REVIEW_IDLE_PATTERNS';

export interface LiteTopicCapacityIssue {
  code: LiteTopicCapacityIssueCode;
  level: LiteTopicCapacityLevel;
  count?: number;
  percent?: number;
  pattern?: string;
  namespace?: string;
  value?: number;
  limit?: number;
}

export interface LiteTopicPatternSignal {
  key: string;
  namespace: string;
  topicPattern: string;
  topicCount: number;
  consumerCount: number;
  sessionCount: number;
  totalBacklog: number;
  averageTTL: number | null;
  ttlStatus: string;
  lastActiveTime: number | null;
  idleHours: number | null;
  level: LiteTopicCapacityLevel;
  reasons: LiteTopicCapacityIssueCode[];
}

export interface LiteTopicNamespaceSignal {
  namespace: string;
  patternCount: number;
  topicCount: number;
  consumerCount: number;
  sessionCount: number;
  totalBacklog: number;
  expiredCount: number;
  expiringSoonCount: number;
  level: LiteTopicCapacityLevel;
}

export interface LiteTopicCapacityInsights {
  level: LiteTopicCapacityLevel;
  totalPatterns: number;
  totalTopics: number;
  totalConsumers: number;
  totalSessions: number;
  totalBacklog: number;
  topicUsagePercent: number | null;
  sessionUsagePercent: number | null;
  creationRatePercent: number | null;
  remainingTopicSlots: number | null;
  remainingSessionSlots: number | null;
  activeCount: number;
  expiringSoonCount: number;
  expiredCount: number;
  unknownStatusCount: number;
  idleCount: number;
  issues: LiteTopicCapacityIssue[];
  recommendations: LiteTopicRecommendationCode[];
  backlogHotspots: LiteTopicPatternSignal[];
  ttlAttentionPatterns: LiteTopicPatternSignal[];
  idlePatterns: LiteTopicPatternSignal[];
  namespaceSignals: LiteTopicNamespaceSignal[];
}

const TOPIC_QUOTA_WARNING_PERCENT = 75;
const TOPIC_QUOTA_CRITICAL_PERCENT = 90;
const SESSION_QUOTA_WARNING_PERCENT = 75;
const SESSION_QUOTA_CRITICAL_PERCENT = 90;
const CREATION_RATE_WARNING_PERCENT = 80;
const CREATION_RATE_CRITICAL_PERCENT = 95;
const BACKLOG_WARNING_THRESHOLD = 10_000;
const BACKLOG_CRITICAL_THRESHOLD = 100_000;
const IDLE_HOURS_THRESHOLD = 72;
const TOP_N = 5;

const knownTtlStatuses = new Set(['ACTIVE', 'EXPIRING_SOON', 'EXPIRED']);

const levelWeight: Record<LiteTopicCapacityLevel, number> = {
  healthy: 0,
  notice: 1,
  warning: 2,
  critical: 3,
};

const normalizeNumber = (value: number | undefined | null): number => {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return 0;
  return Math.max(0, parsed);
};

const normalizeOptionalNumber = (value: number | undefined | null): number | null => {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return null;
  return Math.max(0, parsed);
};

const roundPercent = (value: number): number => Math.round(value * 10) / 10;

const clampPercent = (value: number): number => Math.max(0, Math.min(100, roundPercent(value)));

const percentFromRatioOrCounters = (
  ratio: number | undefined,
  current: number | undefined,
  max: number | undefined,
): number | null => {
  if (Number.isFinite(ratio)) {
    const raw = Number(ratio);
    return clampPercent(raw <= 1 ? raw * 100 : raw);
  }
  const safeCurrent = normalizeOptionalNumber(current);
  const safeMax = normalizeOptionalNumber(max);
  if (safeCurrent == null || safeMax == null || safeMax <= 0) return null;
  return clampPercent((safeCurrent / safeMax) * 100);
};

const quotaRemaining = (current: number | undefined, max: number | undefined): number | null => {
  const safeCurrent = normalizeOptionalNumber(current);
  const safeMax = normalizeOptionalNumber(max);
  if (safeCurrent == null || safeMax == null || safeMax <= 0) return null;
  return Math.max(0, safeMax - safeCurrent);
};

const normalizeText = (value: string | undefined | null, fallback = '-'): string => {
  const text = value?.trim();
  return text || fallback;
};

const normalizeTtlStatus = (value: string | undefined | null): string => {
  const status = value?.trim().toUpperCase();
  return status || 'UNKNOWN';
};

const maxLevel = (levels: LiteTopicCapacityLevel[]): LiteTopicCapacityLevel =>
  levels.reduce<LiteTopicCapacityLevel>(
    (current, next) => (levelWeight[next] > levelWeight[current] ? next : current),
    'healthy',
  );

const issueLevel = (code: LiteTopicCapacityIssueCode): LiteTopicCapacityLevel => {
  switch (code) {
    case 'TOPIC_QUOTA_CRITICAL':
    case 'SESSION_QUOTA_CRITICAL':
    case 'CREATION_RATE_CRITICAL':
      return 'critical';
    case 'TOPIC_QUOTA_WARNING':
    case 'SESSION_QUOTA_WARNING':
    case 'CREATION_RATE_WARNING':
    case 'EXPIRED_PATTERNS':
    case 'BACKLOG_HOTSPOT':
      return 'warning';
    case 'EXPIRING_PATTERNS':
    case 'UNKNOWN_TTL_STATUS':
    case 'IDLE_PATTERNS':
      return 'notice';
    default:
      return 'notice';
  }
};

const addUnique = <T>(items: T[], item: T) => {
  if (!items.includes(item)) items.push(item);
};

const patternKey = (item: LiteTopicItem): string =>
  JSON.stringify([normalizeText(item.namespace, 'default'), normalizeText(item.topicPattern)]);

const idleHours = (lastActiveTime: number | undefined | null, now: number): number | null => {
  const time = normalizeOptionalNumber(lastActiveTime);
  if (time == null || time <= 0 || time > now) return null;
  return Math.floor((now - time) / 3_600_000);
};

const toPatternSignal = (item: LiteTopicItem, now: number): LiteTopicPatternSignal => {
  const totalBacklog = normalizeNumber(item.totalBacklog);
  const ttlStatus = normalizeTtlStatus(item.ttlStatus);
  const idleForHours = idleHours(item.lastActiveTime, now);
  const reasons: LiteTopicCapacityIssueCode[] = [];

  if (ttlStatus === 'EXPIRED') reasons.push('EXPIRED_PATTERNS');
  if (ttlStatus === 'EXPIRING_SOON') reasons.push('EXPIRING_PATTERNS');
  if (!knownTtlStatuses.has(ttlStatus)) reasons.push('UNKNOWN_TTL_STATUS');
  if (totalBacklog >= BACKLOG_WARNING_THRESHOLD) reasons.push('BACKLOG_HOTSPOT');
  if (idleForHours != null && idleForHours >= IDLE_HOURS_THRESHOLD && totalBacklog === 0) {
    reasons.push('IDLE_PATTERNS');
  }

  const backlogLevel =
    totalBacklog >= BACKLOG_CRITICAL_THRESHOLD
      ? 'critical'
      : totalBacklog >= BACKLOG_WARNING_THRESHOLD
        ? 'warning'
        : 'healthy';

  return {
    key: patternKey(item),
    namespace: normalizeText(item.namespace, 'default'),
    topicPattern: normalizeText(item.topicPattern),
    topicCount: normalizeNumber(item.topicCount),
    consumerCount: normalizeNumber(item.consumerCount),
    sessionCount: item.sessionIds?.length ?? 0,
    totalBacklog,
    averageTTL: normalizeOptionalNumber(item.averageTTL),
    ttlStatus,
    lastActiveTime: normalizeOptionalNumber(item.lastActiveTime),
    idleHours: idleForHours,
    level: maxLevel([backlogLevel, ...reasons.map(issueLevel)]),
    reasons,
  };
};

const compareSignalSeverity = (left: LiteTopicPatternSignal, right: LiteTopicPatternSignal) =>
  levelWeight[right.level] - levelWeight[left.level] ||
  right.totalBacklog - left.totalBacklog ||
  right.topicCount - left.topicCount ||
  left.namespace.localeCompare(right.namespace) ||
  left.topicPattern.localeCompare(right.topicPattern);

const namespaceSignals = (signals: LiteTopicPatternSignal[]): LiteTopicNamespaceSignal[] => {
  const namespaces = new Map<string, LiteTopicNamespaceSignal>();
  for (const signal of signals) {
    const current =
      namespaces.get(signal.namespace) ??
      ({
        namespace: signal.namespace,
        patternCount: 0,
        topicCount: 0,
        consumerCount: 0,
        sessionCount: 0,
        totalBacklog: 0,
        expiredCount: 0,
        expiringSoonCount: 0,
        level: 'healthy',
      } satisfies LiteTopicNamespaceSignal);
    current.patternCount += 1;
    current.topicCount += signal.topicCount;
    current.consumerCount += signal.consumerCount;
    current.sessionCount += signal.sessionCount;
    current.totalBacklog += signal.totalBacklog;
    if (signal.ttlStatus === 'EXPIRED') current.expiredCount += 1;
    if (signal.ttlStatus === 'EXPIRING_SOON') current.expiringSoonCount += 1;
    current.level = maxLevel([current.level, signal.level]);
    namespaces.set(signal.namespace, current);
  }

  return [...namespaces.values()].sort(
    (left, right) =>
      levelWeight[right.level] - levelWeight[left.level] ||
      right.totalBacklog - left.totalBacklog ||
      right.topicCount - left.topicCount ||
      left.namespace.localeCompare(right.namespace),
  );
};

const pushQuotaIssue = (
  issues: LiteTopicCapacityIssue[],
  code: LiteTopicCapacityIssueCode,
  percentValue: number | null,
  value: number | undefined,
  limit: number | undefined,
) => {
  if (percentValue == null) return;
  issues.push({
    code,
    level: issueLevel(code),
    percent: percentValue,
    value: normalizeOptionalNumber(value) ?? undefined,
    limit: normalizeOptionalNumber(limit) ?? undefined,
  });
};

const buildIssues = (
  quota: LiteTopicQuota | null | undefined,
  signals: LiteTopicPatternSignal[],
  topicUsagePercent: number | null,
  sessionUsagePercent: number | null,
  creationRatePercent: number | null,
): LiteTopicCapacityIssue[] => {
  const issues: LiteTopicCapacityIssue[] = [];

  if (topicUsagePercent != null && topicUsagePercent >= TOPIC_QUOTA_CRITICAL_PERCENT) {
    pushQuotaIssue(
      issues,
      'TOPIC_QUOTA_CRITICAL',
      topicUsagePercent,
      quota?.currentTopicCount,
      quota?.maxTopicCount,
    );
  } else if (topicUsagePercent != null && topicUsagePercent >= TOPIC_QUOTA_WARNING_PERCENT) {
    pushQuotaIssue(
      issues,
      'TOPIC_QUOTA_WARNING',
      topicUsagePercent,
      quota?.currentTopicCount,
      quota?.maxTopicCount,
    );
  }

  if (sessionUsagePercent != null && sessionUsagePercent >= SESSION_QUOTA_CRITICAL_PERCENT) {
    pushQuotaIssue(
      issues,
      'SESSION_QUOTA_CRITICAL',
      sessionUsagePercent,
      quota?.currentSessionCount,
      quota?.maxSessionCount,
    );
  } else if (sessionUsagePercent != null && sessionUsagePercent >= SESSION_QUOTA_WARNING_PERCENT) {
    pushQuotaIssue(
      issues,
      'SESSION_QUOTA_WARNING',
      sessionUsagePercent,
      quota?.currentSessionCount,
      quota?.maxSessionCount,
    );
  }

  if (creationRatePercent != null && creationRatePercent >= CREATION_RATE_CRITICAL_PERCENT) {
    pushQuotaIssue(
      issues,
      'CREATION_RATE_CRITICAL',
      creationRatePercent,
      quota?.currentCreationRate,
      quota?.maxCreationRate,
    );
  } else if (creationRatePercent != null && creationRatePercent >= CREATION_RATE_WARNING_PERCENT) {
    pushQuotaIssue(
      issues,
      'CREATION_RATE_WARNING',
      creationRatePercent,
      quota?.currentCreationRate,
      quota?.maxCreationRate,
    );
  }

  const expired = signals.filter((signal) => signal.ttlStatus === 'EXPIRED');
  const expiring = signals.filter((signal) => signal.ttlStatus === 'EXPIRING_SOON');
  const unknown = signals.filter((signal) => !knownTtlStatuses.has(signal.ttlStatus));
  const idle = signals.filter((signal) => signal.reasons.includes('IDLE_PATTERNS'));
  const backlogHotspot = signals.find((signal) => signal.reasons.includes('BACKLOG_HOTSPOT'));

  if (expired.length > 0) {
    issues.push({
      code: 'EXPIRED_PATTERNS',
      level: 'warning',
      count: expired.length,
      pattern: expired[0].topicPattern,
      namespace: expired[0].namespace,
    });
  }
  if (expiring.length > 0) {
    issues.push({
      code: 'EXPIRING_PATTERNS',
      level: expiring.length >= 3 ? 'warning' : 'notice',
      count: expiring.length,
      pattern: expiring[0].topicPattern,
      namespace: expiring[0].namespace,
    });
  }
  if (backlogHotspot) {
    issues.push({
      code: 'BACKLOG_HOTSPOT',
      level: backlogHotspot.totalBacklog >= BACKLOG_CRITICAL_THRESHOLD ? 'critical' : 'warning',
      count: backlogHotspot.totalBacklog,
      pattern: backlogHotspot.topicPattern,
      namespace: backlogHotspot.namespace,
    });
  }
  if (unknown.length > 0) {
    issues.push({
      code: 'UNKNOWN_TTL_STATUS',
      level: 'notice',
      count: unknown.length,
      pattern: unknown[0].topicPattern,
      namespace: unknown[0].namespace,
    });
  }
  if (idle.length > 0) {
    issues.push({
      code: 'IDLE_PATTERNS',
      level: 'notice',
      count: idle.length,
      pattern: idle[0].topicPattern,
      namespace: idle[0].namespace,
    });
  }

  return issues.sort(
    (left, right) =>
      levelWeight[right.level] - levelWeight[left.level] ||
      (right.percent ?? right.count ?? 0) - (left.percent ?? left.count ?? 0) ||
      left.code.localeCompare(right.code),
  );
};

const recommendationsForIssues = (
  issues: LiteTopicCapacityIssue[],
): LiteTopicRecommendationCode[] => {
  const recommendations: LiteTopicRecommendationCode[] = [];
  for (const issue of issues) {
    switch (issue.code) {
      case 'TOPIC_QUOTA_CRITICAL':
      case 'TOPIC_QUOTA_WARNING':
        addUnique(recommendations, 'REQUEST_TOPIC_QUOTA');
        break;
      case 'SESSION_QUOTA_CRITICAL':
      case 'SESSION_QUOTA_WARNING':
        addUnique(recommendations, 'REQUEST_SESSION_QUOTA');
        break;
      case 'CREATION_RATE_CRITICAL':
      case 'CREATION_RATE_WARNING':
        addUnique(recommendations, 'THROTTLE_CREATION');
        break;
      case 'EXPIRED_PATTERNS':
        addUnique(recommendations, 'CLEAN_EXPIRED');
        break;
      case 'EXPIRING_PATTERNS':
        addUnique(recommendations, 'EXTEND_TTL');
        break;
      case 'BACKLOG_HOTSPOT':
        addUnique(recommendations, 'DRAIN_BACKLOG');
        break;
      case 'UNKNOWN_TTL_STATUS':
        addUnique(recommendations, 'CHECK_UNKNOWN_STATUS');
        break;
      case 'IDLE_PATTERNS':
        addUnique(recommendations, 'REVIEW_IDLE_PATTERNS');
        break;
      default:
        break;
    }
  }
  return recommendations.slice(0, 6);
};

export function buildLiteTopicCapacityInsights(
  quota: LiteTopicQuota | null | undefined,
  items: LiteTopicItem[] | null | undefined,
  now = Date.now(),
): LiteTopicCapacityInsights {
  const signals = (items ?? []).map((item) => toPatternSignal(item, now));
  const totalTopics =
    normalizeOptionalNumber(quota?.currentTopicCount) ??
    signals.reduce((sum, signal) => sum + signal.topicCount, 0);
  const totalSessions =
    normalizeOptionalNumber(quota?.currentSessionCount) ??
    signals.reduce((sum, signal) => sum + signal.sessionCount, 0);
  const totalConsumers = signals.reduce((sum, signal) => sum + signal.consumerCount, 0);
  const totalBacklog = signals.reduce((sum, signal) => sum + signal.totalBacklog, 0);
  const topicUsagePercent = percentFromRatioOrCounters(
    quota?.usageRate,
    quota?.currentTopicCount,
    quota?.maxTopicCount,
  );
  const sessionUsagePercent = percentFromRatioOrCounters(
    quota?.sessionUsageRate,
    quota?.currentSessionCount,
    quota?.maxSessionCount,
  );
  const creationRatePercent = percentFromRatioOrCounters(
    undefined,
    quota?.currentCreationRate,
    quota?.maxCreationRate,
  );
  const issues = buildIssues(
    quota,
    signals,
    topicUsagePercent,
    sessionUsagePercent,
    creationRatePercent,
  );

  const backlogSignals = signals
    .filter((signal) => signal.totalBacklog > 0)
    .sort(compareSignalSeverity);
  const ttlAttentionSignals = signals
    .filter(
      (signal) =>
        ['EXPIRED', 'EXPIRING_SOON'].includes(signal.ttlStatus) ||
        signal.reasons.includes('UNKNOWN_TTL_STATUS'),
    )
    .sort(compareSignalSeverity);
  const idleSignals = signals
    .filter((signal) => signal.reasons.includes('IDLE_PATTERNS'))
    .sort((left, right) => (right.idleHours ?? 0) - (left.idleHours ?? 0));

  return {
    level: maxLevel(issues.map((issue) => issue.level)),
    totalPatterns: signals.length,
    totalTopics,
    totalConsumers,
    totalSessions,
    totalBacklog,
    topicUsagePercent,
    sessionUsagePercent,
    creationRatePercent,
    remainingTopicSlots: quotaRemaining(quota?.currentTopicCount, quota?.maxTopicCount),
    remainingSessionSlots: quotaRemaining(quota?.currentSessionCount, quota?.maxSessionCount),
    activeCount: signals.filter((signal) => signal.ttlStatus === 'ACTIVE').length,
    expiringSoonCount: signals.filter((signal) => signal.ttlStatus === 'EXPIRING_SOON').length,
    expiredCount: signals.filter((signal) => signal.ttlStatus === 'EXPIRED').length,
    unknownStatusCount: signals.filter((signal) => !knownTtlStatuses.has(signal.ttlStatus)).length,
    idleCount: idleSignals.length,
    issues,
    recommendations: recommendationsForIssues(issues),
    backlogHotspots: backlogSignals.slice(0, TOP_N),
    ttlAttentionPatterns: ttlAttentionSignals.slice(0, TOP_N),
    idlePatterns: idleSignals.slice(0, TOP_N),
    namespaceSignals: namespaceSignals(signals).slice(0, TOP_N),
  };
}

export function liteTopicCapacityLevelRank(level: LiteTopicCapacityLevel): number {
  return levelWeight[level];
}
