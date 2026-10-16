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

import type { ConsumerGroup, QueueProgress, SubscriptionEntry } from '../api/metadata';
import { isLagAvailable } from './consumerLag';

export type ConsumerGroupHealthStatus = 'healthy' | 'warning' | 'critical';

export type ConsumerGroupHealthIssueCode =
  | 'CONNECTION_STATUS_UNKNOWN'
  | 'NO_ACTIVE_CLIENTS_WITH_LAG'
  | 'NO_SUBSCRIPTION_DATA'
  | 'SUBSCRIPTION_INCONSISTENT'
  | 'SUBSCRIPTION_UNKNOWN'
  | 'QUEUE_LAG_SKEW'
  | 'HIGH_GROUP_LAG'
  | 'HIGH_CONSUME_DELAY'
  | 'UNKNOWN_QUEUE_LAG'
  | 'STALE_HEARTBEAT';

export interface ConsumerGroupHealthIssue {
  id: string;
  code: ConsumerGroupHealthIssueCode;
  severity: Exclude<ConsumerGroupHealthStatus, 'healthy'>;
  titleKey: string;
  descriptionKey: string;
  params?: Record<string, string | number>;
  subject?: string;
}

export interface ConsumerGroupHealthSummary {
  healthScore: number;
  onlineInstances: number;
  subscribedTopicCount: number;
  queueCount: number;
  lagQueueCount: number;
  unknownQueueCount: number;
  totalKnownLag: number;
  reportedLag: number | null;
  maxQueueLag: number | null;
  maxHeartbeatAgeSeconds: number | null;
  staleClientCount: number;
}

export interface ConsumerGroupHealthDiagnostics {
  status: ConsumerGroupHealthStatus;
  statusKey: string;
  statusColor: 'success' | 'warning' | 'error';
  summary: ConsumerGroupHealthSummary;
  issues: ConsumerGroupHealthIssue[];
  recommendationKeys: string[];
}

export interface ConsumerGroupHealthOptions {
  now?: Date | string | number;
  staleHeartbeatSeconds?: number;
  highLagThreshold?: number;
  criticalLagThreshold?: number;
  highDelaySeconds?: number;
  criticalDelaySeconds?: number;
  skewWarningRatio?: number;
  skewCriticalRatio?: number;
}

const STATUS_ORDER: Record<ConsumerGroupHealthStatus, number> = {
  healthy: 0,
  warning: 1,
  critical: 2,
};

// The analyzer returns translation keys, not display text: the page resolves them
// through t(key, params), the same contract as messageTraceDiagnostics.
const STATUS_KEY: Record<ConsumerGroupHealthStatus, string> = {
  healthy: 'consumerHealth.statusHealthy',
  warning: 'consumerHealth.statusWarning',
  critical: 'consumerHealth.statusCritical',
};

const STATUS_COLOR: Record<ConsumerGroupHealthStatus, 'success' | 'warning' | 'error'> = {
  healthy: 'success',
  warning: 'warning',
  critical: 'error',
};

const DEFAULT_STALE_HEARTBEAT_SECONDS = 300;
const DEFAULT_HIGH_LAG_THRESHOLD = 1_000;
const DEFAULT_CRITICAL_LAG_THRESHOLD = 10_000;
const DEFAULT_HIGH_DELAY_SECONDS = 300;
const DEFAULT_CRITICAL_DELAY_SECONDS = 1_800;
const DEFAULT_SKEW_WARNING_RATIO = 2;
const DEFAULT_SKEW_CRITICAL_RATIO = 5;

const issue = (
  code: ConsumerGroupHealthIssueCode,
  severity: Exclude<ConsumerGroupHealthStatus, 'healthy'>,
  titleKey: string,
  descriptionKey: string,
  params?: Record<string, string | number>,
  subject?: string,
): ConsumerGroupHealthIssue => ({
  id: [code, subject].filter(Boolean).join(':'),
  code,
  severity,
  titleKey,
  descriptionKey,
  params,
  subject,
});

const normalizeText = (value?: string | null): string => value?.trim() ?? '';

const normalizeKey = (value?: string | null): string => normalizeText(value).toLowerCase();

const isConsistentSubscription = (subscription: SubscriptionEntry): boolean =>
  ['consistent', '一致'].includes(normalizeKey(subscription.consistency));

const isInconsistentSubscription = (subscription: SubscriptionEntry): boolean =>
  ['inconsistent', '不一致'].includes(normalizeKey(subscription.consistency));

const parseTimestamp = (value: Date | string | number | undefined): number | null => {
  if (value === undefined || value === null || value === '') return null;
  const timestamp = value instanceof Date ? value.getTime() : new Date(value).getTime();
  return Number.isFinite(timestamp) ? timestamp : null;
};

const heartbeatAgeSeconds = (lastHeartbeat: string | undefined, now: number): number | null => {
  const timestamp = parseTimestamp(lastHeartbeat);
  if (timestamp === null) return null;
  return Math.max(0, Math.floor((now - timestamp) / 1000));
};

const knownLag = (progress: QueueProgress[]): number =>
  progress.reduce((sum, queue) => sum + (isLagAvailable(queue.diffTotal) ? queue.diffTotal : 0), 0);

const reportedLag = (group: ConsumerGroup, fallback: number): number | null =>
  isLagAvailable(group.totalLag) ? group.totalLag : fallback;

const topicCount = (group: ConsumerGroup, subscriptions: SubscriptionEntry[]): number => {
  const topics = new Set<string>();
  for (const topic of group.subscribedTopics ?? []) {
    const normalized = normalizeText(topic);
    if (normalized) topics.add(normalized);
  }
  for (const subscription of subscriptions) {
    const normalized = normalizeText(subscription.topic);
    if (normalized) topics.add(normalized);
  }
  return topics.size;
};

const lagSkewRatio = (knownQueueLags: number[]): number => {
  const positive = knownQueueLags.filter((lag) => lag > 0);
  if (positive.length <= 1) return 0;
  const min = Math.min(...positive);
  if (min === 0) return 0;
  return Math.round((Math.max(...positive) / min) * 100) / 100;
};

const maxStatus = (issues: ConsumerGroupHealthIssue[]): ConsumerGroupHealthStatus =>
  issues.reduce<ConsumerGroupHealthStatus>(
    (status, current) =>
      STATUS_ORDER[current.severity] > STATUS_ORDER[status] ? current.severity : status,
    'healthy',
  );

const healthScore = (issues: ConsumerGroupHealthIssue[]): number => {
  const penalties = issues.reduce(
    (sum, current) => sum + (current.severity === 'critical' ? 25 : 12),
    0,
  );
  return Math.max(0, Math.min(100, Math.round(100 - penalties)));
};

const subscriptionIssues = (subscriptions: SubscriptionEntry[]): ConsumerGroupHealthIssue[] => {
  if (subscriptions.length === 0) {
    return [
      issue(
        'NO_SUBSCRIPTION_DATA',
        'warning',
        'consumerHealth.noSubscriptionData.title',
        'consumerHealth.noSubscriptionData.desc',
      ),
    ];
  }

  const issues: ConsumerGroupHealthIssue[] = [];
  for (const subscription of subscriptions) {
    if (isInconsistentSubscription(subscription)) {
      issues.push(
        issue(
          'SUBSCRIPTION_INCONSISTENT',
          'critical',
          'consumerHealth.subscriptionInconsistent.title',
          'consumerHealth.subscriptionInconsistent.desc',
          { topic: subscription.topic },
          subscription.topic,
        ),
      );
    } else if (!isConsistentSubscription(subscription)) {
      issues.push(
        issue(
          'SUBSCRIPTION_UNKNOWN',
          'warning',
          'consumerHealth.subscriptionUnknown.title',
          'consumerHealth.subscriptionUnknown.desc',
          { topic: subscription.topic },
          subscription.topic,
        ),
      );
    }
  }
  return issues;
};

const progressIssues = (
  progress: QueueProgress[],
  knownQueueLags: number[],
  unknownQueueCount: number,
  options: Required<
    Pick<
      ConsumerGroupHealthOptions,
      'skewWarningRatio' | 'skewCriticalRatio' | 'highLagThreshold' | 'criticalLagThreshold'
    >
  >,
): ConsumerGroupHealthIssue[] => {
  if (progress.length === 0) return [];

  const issues: ConsumerGroupHealthIssue[] = [];
  const skew = lagSkewRatio(knownQueueLags);
  const totalLag = knownQueueLags.reduce((sum, lag) => sum + lag, 0);

  if (unknownQueueCount > 0) {
    issues.push(
      issue(
        'UNKNOWN_QUEUE_LAG',
        'warning',
        'consumerHealth.unknownQueueLag.title',
        'consumerHealth.unknownQueueLag.desc',
        { count: unknownQueueCount },
      ),
    );
  }
  if (skew >= options.skewCriticalRatio) {
    issues.push(
      issue(
        'QUEUE_LAG_SKEW',
        'critical',
        'consumerHealth.queueLagSkewCritical.title',
        'consumerHealth.queueLagSkewCritical.desc',
        { ratio: skew },
      ),
    );
  } else if (skew >= options.skewWarningRatio) {
    issues.push(
      issue(
        'QUEUE_LAG_SKEW',
        'warning',
        'consumerHealth.queueLagSkewWarning.title',
        'consumerHealth.queueLagSkewWarning.desc',
        { ratio: skew },
      ),
    );
  }
  if (totalLag >= options.criticalLagThreshold) {
    issues.push(
      issue(
        'HIGH_GROUP_LAG',
        'critical',
        'consumerHealth.highGroupLagCritical.title',
        'consumerHealth.highGroupLag.desc',
        { count: totalLag.toLocaleString() },
      ),
    );
  } else if (totalLag >= options.highLagThreshold) {
    issues.push(
      issue(
        'HIGH_GROUP_LAG',
        'warning',
        'consumerHealth.highGroupLagWarning.title',
        'consumerHealth.highGroupLag.desc',
        { count: totalLag.toLocaleString() },
      ),
    );
  }
  return issues;
};

const runtimeIssues = (
  group: ConsumerGroup,
  lag: number | null,
  now: number,
  options: Required<
    Pick<
      ConsumerGroupHealthOptions,
      'staleHeartbeatSeconds' | 'highDelaySeconds' | 'criticalDelaySeconds'
    >
  >,
): ConsumerGroupHealthIssue[] => {
  const issues: ConsumerGroupHealthIssue[] = [];
  if (group.onlineInstances < 0) {
    issues.push(
      issue(
        'CONNECTION_STATUS_UNKNOWN',
        'warning',
        'consumerHealth.connectionUnknown.title',
        'consumerHealth.connectionUnknown.desc',
      ),
    );
  } else if ((group.onlineInstances ?? 0) === 0 && (lag ?? 0) > 0) {
    issues.push(
      issue(
        'NO_ACTIVE_CLIENTS_WITH_LAG',
        'critical',
        'consumerHealth.noActiveClients.title',
        'consumerHealth.noActiveClients.desc',
      ),
    );
  }

  for (const client of group.instances ?? []) {
    const age = heartbeatAgeSeconds(client.lastHeartbeat, now);
    if (age !== null && age > options.staleHeartbeatSeconds) {
      issues.push(
        issue(
          'STALE_HEARTBEAT',
          'warning',
          'consumerHealth.staleHeartbeat.title',
          'consumerHealth.staleHeartbeat.desc',
          { client: client.clientId, seconds: options.staleHeartbeatSeconds },
          client.clientId,
        ),
      );
    }
  }

  const delaySeconds = Number.isFinite(group.delaySeconds) ? group.delaySeconds : 0;
  if (delaySeconds >= options.criticalDelaySeconds) {
    issues.push(
      issue(
        'HIGH_CONSUME_DELAY',
        'critical',
        'consumerHealth.highDelayCritical.title',
        'consumerHealth.highDelayCritical.desc',
        { seconds: delaySeconds.toLocaleString() },
      ),
    );
  } else if (delaySeconds >= options.highDelaySeconds) {
    issues.push(
      issue(
        'HIGH_CONSUME_DELAY',
        'warning',
        'consumerHealth.highDelayWarning.title',
        'consumerHealth.highDelayWarning.desc',
        { seconds: delaySeconds.toLocaleString() },
      ),
    );
  }
  return issues;
};

const recommendations = (issues: ConsumerGroupHealthIssue[]): string[] => {
  const codes = new Set(issues.map((item) => item.code));
  const result: string[] = [];
  if (
    codes.has('CONNECTION_STATUS_UNKNOWN') ||
    codes.has('NO_ACTIVE_CLIENTS_WITH_LAG') ||
    codes.has('STALE_HEARTBEAT')
  ) {
    result.push('consumerHealth.recommendation.connectivity');
  }
  if (codes.has('SUBSCRIPTION_INCONSISTENT') || codes.has('SUBSCRIPTION_UNKNOWN')) {
    result.push('consumerHealth.recommendation.subscription');
  }
  if (codes.has('QUEUE_LAG_SKEW')) {
    result.push('consumerHealth.recommendation.skew');
  }
  if (codes.has('HIGH_GROUP_LAG') || codes.has('HIGH_CONSUME_DELAY')) {
    result.push('consumerHealth.recommendation.capacity');
  }
  if (codes.has('UNKNOWN_QUEUE_LAG')) {
    result.push('consumerHealth.recommendation.lagSource');
  }
  return result;
};

export const analyzeConsumerGroupHealth = (
  group: ConsumerGroup,
  subscriptions: SubscriptionEntry[],
  progress: QueueProgress[],
  options: ConsumerGroupHealthOptions = {},
): ConsumerGroupHealthDiagnostics => {
  const normalizedOptions = {
    staleHeartbeatSeconds: options.staleHeartbeatSeconds ?? DEFAULT_STALE_HEARTBEAT_SECONDS,
    highLagThreshold: options.highLagThreshold ?? DEFAULT_HIGH_LAG_THRESHOLD,
    criticalLagThreshold: options.criticalLagThreshold ?? DEFAULT_CRITICAL_LAG_THRESHOLD,
    highDelaySeconds: options.highDelaySeconds ?? DEFAULT_HIGH_DELAY_SECONDS,
    criticalDelaySeconds: options.criticalDelaySeconds ?? DEFAULT_CRITICAL_DELAY_SECONDS,
    skewWarningRatio: options.skewWarningRatio ?? DEFAULT_SKEW_WARNING_RATIO,
    skewCriticalRatio: options.skewCriticalRatio ?? DEFAULT_SKEW_CRITICAL_RATIO,
  };
  const now = parseTimestamp(options.now ?? Date.now()) ?? Date.now();
  const knownQueueLags = progress
    .map((queue) => queue.diffTotal)
    .filter((lag): lag is number => isLagAvailable(lag));
  const totalKnownLag = knownLag(progress);
  const summaryReportedLag = reportedLag(group, totalKnownLag);
  const unknownQueueCount = progress.length - knownQueueLags.length;
  const heartbeatAges = (group.instances ?? [])
    .map((client) => heartbeatAgeSeconds(client.lastHeartbeat, now))
    .filter((age): age is number => age !== null);
  const issues = [
    ...subscriptionIssues(subscriptions),
    ...progressIssues(progress, knownQueueLags, unknownQueueCount, normalizedOptions),
    ...runtimeIssues(group, summaryReportedLag, now, normalizedOptions),
  ];
  const status = maxStatus(issues);

  return {
    status,
    statusKey: STATUS_KEY[status],
    statusColor: STATUS_COLOR[status],
    summary: {
      healthScore: healthScore(issues),
      onlineInstances: group.onlineInstances ?? 0,
      subscribedTopicCount: topicCount(group, subscriptions),
      queueCount: progress.length,
      lagQueueCount: knownQueueLags.filter((lag) => lag > 0).length,
      unknownQueueCount,
      totalKnownLag,
      reportedLag: summaryReportedLag,
      maxQueueLag: knownQueueLags.length > 0 ? Math.max(...knownQueueLags) : null,
      maxHeartbeatAgeSeconds: heartbeatAges.length > 0 ? Math.max(...heartbeatAges) : null,
      staleClientCount: issues.filter((item) => item.code === 'STALE_HEARTBEAT').length,
    },
    issues,
    recommendationKeys: recommendations(issues),
  };
};
