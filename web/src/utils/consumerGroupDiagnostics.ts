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
  title: string;
  description: string;
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
  statusText: string;
  statusColor: 'success' | 'warning' | 'error';
  summary: ConsumerGroupHealthSummary;
  issues: ConsumerGroupHealthIssue[];
  recommendations: string[];
}

export interface ConsumerGroupHealthOptions {
  lang?: 'zh' | 'en';
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

const STATUS_TEXT: Record<ConsumerGroupHealthStatus, string> = {
  healthy: '消费组健康',
  warning: '需要关注',
  critical: '消费风险',
};

const STATUS_TEXT_EN: Record<ConsumerGroupHealthStatus, string> = {
  healthy: 'Consumer group healthy',
  warning: 'Attention needed',
  critical: 'Consumption risk',
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
  title: string,
  description: string,
  subject?: string,
): ConsumerGroupHealthIssue => ({
  id: [code, subject].filter(Boolean).join(':'),
  code,
  severity,
  title,
  description,
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

const subscriptionIssues = (
  subscriptions: SubscriptionEntry[],
  lang: 'zh' | 'en' = 'zh',
): ConsumerGroupHealthIssue[] => {
  const pick = (zh: string, en: string): string => (lang === 'en' ? en : zh);
  if (subscriptions.length === 0) {
    return [
      issue(
        'NO_SUBSCRIPTION_DATA',
        'warning',
        pick('暂无订阅明细', 'No subscription details'),
        pick(
          '无法从当前结果判断客户端订阅表达式是否一致。',
          'Subscription expression consistency cannot be judged from the current result.',
        ),
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
          pick('订阅表达式不一致', 'Inconsistent subscription expressions'),
          lang === 'en'
            ? `Subscription expressions of ${subscription.topic} differ across clients, which may drop or duplicate messages.`
            : `${subscription.topic} 的订阅表达式在客户端之间不一致，可能导致消息遗漏或重复消费。`,
          subscription.topic,
        ),
      );
    } else if (!isConsistentSubscription(subscription)) {
      issues.push(
        issue(
          'SUBSCRIPTION_UNKNOWN',
          'warning',
          pick('订阅一致性未知', 'Subscription consistency unknown'),
          lang === 'en'
            ? `Consistency of ${subscription.topic} is unknown; re-check the client subscriptions.`
            : `${subscription.topic} 的一致性状态未知，建议重新检查客户端订阅。`,
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
  lang: 'zh' | 'en' = 'zh',
): ConsumerGroupHealthIssue[] => {
  const pick = (zh: string, en: string): string => (lang === 'en' ? en : zh);
  if (progress.length === 0) return [];

  const issues: ConsumerGroupHealthIssue[] = [];
  const skew = lagSkewRatio(knownQueueLags);
  const totalLag = knownQueueLags.reduce((sum, lag) => sum + lag, 0);

  if (unknownQueueCount > 0) {
    issues.push(
      issue(
        'UNKNOWN_QUEUE_LAG',
        'warning',
        pick('部分 Queue 堆积不可用', 'Some Queue lags are unavailable'),
        lang === 'en'
          ? `${unknownQueueCount} Queues cannot compute lag; the total lag only includes available data.`
          : `${unknownQueueCount} 个 Queue 无法计算堆积，当前总堆积只包含可用数据。`,
      ),
    );
  }
  if (skew >= options.skewCriticalRatio) {
    issues.push(
      issue(
        'QUEUE_LAG_SKEW',
        'critical',
        pick('Queue 堆积分布严重倾斜', 'Queue lags are severely skewed'),
        lang === 'en'
          ? `Max/min Queue lag ratio is about ${skew}:1; there may be a single-queue hotspot or uneven consumer assignment.`
          : `最大/最小 Queue 堆积约为 ${skew}:1，可能存在单队列热点或消费者分配不均。`,
      ),
    );
  } else if (skew >= options.skewWarningRatio) {
    issues.push(
      issue(
        'QUEUE_LAG_SKEW',
        'warning',
        pick('Queue 堆积分布不均', 'Queue lags are unevenly distributed'),
        lang === 'en'
          ? `Max/min Queue lag ratio is about ${skew}:1; watch whether it keeps growing.`
          : `最大/最小 Queue 堆积约为 ${skew}:1，建议观察是否持续扩大。`,
      ),
    );
  }
  if (totalLag >= options.criticalLagThreshold) {
    issues.push(
      issue(
        'HIGH_GROUP_LAG',
        'critical',
        pick('Group 总堆积过高', 'Group lag is too high'),
        lang === 'en'
          ? `Known lag of the Group has reached ${totalLag.toLocaleString()} messages.`
          : `Group 当前已知堆积达到 ${totalLag.toLocaleString()} 条。`,
      ),
    );
  } else if (totalLag >= options.highLagThreshold) {
    issues.push(
      issue(
        'HIGH_GROUP_LAG',
        'warning',
        pick('Group 总堆积偏高', 'Group lag is high'),
        lang === 'en'
          ? `Known lag of the Group has reached ${totalLag.toLocaleString()} messages.`
          : `Group 当前已知堆积达到 ${totalLag.toLocaleString()} 条。`,
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
  lang: 'zh' | 'en' = 'zh',
): ConsumerGroupHealthIssue[] => {
  const pick = (zh: string, en: string): string => (lang === 'en' ? en : zh);
  const issues: ConsumerGroupHealthIssue[] = [];
  if ((group.onlineInstances ?? 0) === 0 && (lag ?? 0) > 0) {
    issues.push(
      issue(
        'NO_ACTIVE_CLIENTS_WITH_LAG',
        'critical',
        pick('有堆积但无在线客户端', 'Lag exists but no online clients'),
        pick(
          '消费组存在未消费消息，但当前没有在线客户端处理这些消息。',
          'The Group has unconsumed messages but no online clients are processing them.',
        ),
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
          pick('客户端心跳过期', 'Client heartbeat expired'),
          lang === 'en'
            ? `Last heartbeat of ${client.clientId} exceeded ${options.staleHeartbeatSeconds} seconds ago.`
            : `${client.clientId} 的最后心跳已超过 ${options.staleHeartbeatSeconds} 秒。`,
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
        pick('消费延迟过高', 'Consume delay is too high'),
        lang === 'en'
          ? `Consume delay of the Group is about ${delaySeconds.toLocaleString()} seconds; the business may already perceive it.`
          : `Group 当前消费延迟约 ${delaySeconds.toLocaleString()} 秒，业务可能已经感知延迟。`,
      ),
    );
  } else if (delaySeconds >= options.highDelaySeconds) {
    issues.push(
      issue(
        'HIGH_CONSUME_DELAY',
        'warning',
        pick('消费延迟偏高', 'Consume delay is high'),
        lang === 'en'
          ? `Consume delay of the Group is about ${delaySeconds.toLocaleString()} seconds; keep watching the trend.`
          : `Group 当前消费延迟约 ${delaySeconds.toLocaleString()} 秒，建议继续观察趋势。`,
      ),
    );
  }
  return issues;
};

const recommendations = (
  issues: ConsumerGroupHealthIssue[],
  lang: 'zh' | 'en' = 'zh',
): string[] => {
  const codes = new Set(issues.map((item) => item.code));
  const pick = (zh: string, en: string): string => (lang === 'en' ? en : zh);
  const result: string[] = [];
  if (codes.has('NO_ACTIVE_CLIENTS_WITH_LAG') || codes.has('STALE_HEARTBEAT')) {
    result.push(pick('先确认消费者进程、Proxy/Broker 网络连通性和客户端心跳是否恢复。', 'First confirm the consumer process, Proxy/Broker network connectivity and client heartbeats have recovered.'));
  }
  if (codes.has('SUBSCRIPTION_INCONSISTENT') || codes.has('SUBSCRIPTION_UNKNOWN')) {
    result.push(pick('统一同一 Group 内所有客户端的订阅表达式，避免灰度期间同时运行不同过滤条件。', 'Unify the subscription expressions of all clients in the Group to avoid running different filter conditions during gray releases.'));
  }
  if (codes.has('QUEUE_LAG_SKEW')) {
    result.push(pick('检查热点 Queue 的分配、消费者线程池和单分区顺序消费阻塞情况。', 'Check hotspot Queue assignment, the consumer thread pool and blocked ordered consumption on single partitions.'));
  }
  if (codes.has('HIGH_GROUP_LAG') || codes.has('HIGH_CONSUME_DELAY')) {
    result.push(pick('结合消费 TPS、业务耗时和重试堆积判断是否需要扩容消费者或限流生产端。', 'Combine consume TPS, business latency and retry backlog to decide whether to scale consumers or throttle producers.'));
  }
  if (codes.has('UNKNOWN_QUEUE_LAG')) {
    result.push(pick('当堆积不可用时，优先确认 Proxy 指标采集和 Broker offset 查询权限。', 'When lag is unavailable, first verify Proxy metric collection and Broker offset query permissions.'));
  }
  return result;
};

export const analyzeConsumerGroupHealth = (
  group: ConsumerGroup,
  subscriptions: SubscriptionEntry[],
  progress: QueueProgress[],
  options: ConsumerGroupHealthOptions = {},
): ConsumerGroupHealthDiagnostics => {
  const lang = options.lang ?? 'zh';
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
    ...subscriptionIssues(subscriptions, lang),
    ...progressIssues(progress, knownQueueLags, unknownQueueCount, normalizedOptions, lang),
    ...runtimeIssues(group, summaryReportedLag, now, normalizedOptions, lang),
  ];
  const status = maxStatus(issues);

  return {
    status,
    statusText: lang === 'en' ? STATUS_TEXT_EN[status] : STATUS_TEXT[status],
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
    recommendations: recommendations(issues, lang),
  };
};
