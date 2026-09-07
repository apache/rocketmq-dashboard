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

import type { BrokerRoute, ConsumerGroupInfo, Topic } from '../api/metadata';

export type TopicDeleteRiskLevel = 'low' | 'medium' | 'high' | 'unknown';
export type TopicDeleteIssueSeverity = 'info' | 'warning' | 'critical';

export type TopicDeleteIssueCode =
  | 'SYSTEM_TOPIC'
  | 'CONSUMER_GROUPS_ATTACHED'
  | 'ACTIVE_CONSUMPTION'
  | 'BACKLOG_REMAINS'
  | 'RECENT_TRAFFIC'
  | 'RETAINED_MESSAGES'
  | 'WRITABLE_ROUTE'
  | 'NO_ROUTE'
  | 'ROUTE_LOOKUP_FAILED'
  | 'CONSUMER_LOOKUP_FAILED'
  | 'CONSUMER_PAGE_TRUNCATED'
  | 'CONSUMER_METRICS_UNAVAILABLE'
  | 'CLOUD_ROUTE_UNAVAILABLE';

export interface TopicDeleteIssue {
  code: TopicDeleteIssueCode;
  severity: TopicDeleteIssueSeverity;
  title: string;
  description: string;
}

export interface TopicDeleteImpactInput {
  topic: Topic;
  consumers?: ConsumerGroupInfo[];
  consumerTotal?: number;
  routes?: BrokerRoute[];
  consumerLookupFailed?: boolean;
  routeLookupFailed?: boolean;
  isCloudInstance?: boolean;
}

export interface TopicDeleteImpactAssessment {
  topicName: string;
  riskLevel: TopicDeleteRiskLevel;
  riskScore: number;
  canDelete: boolean;
  issues: TopicDeleteIssue[];
  stats: {
    consumerGroups: number;
    activeConsumerGroups: number;
    consumerGroupsWithLag: number;
    metricsUnavailableGroups: number;
    knownTotalLag: number;
    topicMessageCount: number;
    topicTps: number;
    routeCount: number;
    writableRoutes: number;
    truncatedConsumers: boolean;
    consumerLookupFailed: boolean;
    routeLookupFailed: boolean;
  };
}

export interface TopicDeleteImpactSummary {
  totalTopics: number;
  riskLevel: TopicDeleteRiskLevel;
  canDelete: boolean;
  highRiskTopics: number;
  mediumRiskTopics: number;
  lowRiskTopics: number;
  unknownRiskTopics: number;
  blockedTopics: string[];
  totalConsumerGroups: number;
  activeConsumerGroups: number;
  consumerGroupsWithLag: number;
  metricsUnavailableGroups: number;
  knownTotalLag: number;
  topicMessageCount: number;
  topicTps: number;
  writableRoutes: number;
  routeLookupFailedTopics: number;
  consumerLookupFailedTopics: number;
  truncatedConsumerTopics: number;
}

const SEVERITY_SCORE: Record<TopicDeleteIssueSeverity, number> = {
  info: 1,
  warning: 2,
  critical: 4,
};

const SYSTEM_TOPIC_PREFIXES = ['%RETRY%', '%DLQ%', 'SCHEDULE_TOPIC_', 'RMQ_SYS_', 'TBW102'];
const SYSTEM_TOPIC_NAMES = new Set([
  'SELF_TEST_TOPIC',
  'OFFSET_MOVED_EVENT',
  'TRANS_CHECK_MAX_TIME_TOPIC',
  'BenchmarkTest',
]);

const num = (value: unknown): number =>
  typeof value === 'number' && Number.isFinite(value) ? Math.max(0, value) : 0;

const routeWritable = (route: BrokerRoute): boolean => {
  if (typeof route.writable === 'boolean') return route.writable;
  return route.perm === 'RW' || route.perm === 'WO';
};

const issue = (
  code: TopicDeleteIssueCode,
  severity: TopicDeleteIssueSeverity,
  title: string,
  description: string,
): TopicDeleteIssue => ({ code, severity, title, description });

export const isSystemTopicName = (topicName: string): boolean => {
  const normalized = topicName.trim();
  return (
    SYSTEM_TOPIC_NAMES.has(normalized) ||
    SYSTEM_TOPIC_PREFIXES.some((prefix) => normalized.startsWith(prefix))
  );
};

const riskLevel = (
  issues: TopicDeleteIssue[],
  input: Pick<TopicDeleteImpactInput, 'consumerLookupFailed' | 'routeLookupFailed'>,
): TopicDeleteRiskLevel => {
  if (issues.some((item) => item.severity === 'critical')) return 'high';
  if (input.consumerLookupFailed || input.routeLookupFailed) return 'unknown';
  if (issues.some((item) => item.severity === 'warning')) return 'medium';
  return 'low';
};

export const analyzeTopicDeleteImpact = (
  input: TopicDeleteImpactInput,
): TopicDeleteImpactAssessment => {
  const consumers = input.consumers ?? [];
  const routes = input.routes ?? [];
  const consumerGroups = Math.max(
    num(input.topic.consumerGroupCount),
    consumers.length,
    num(input.consumerTotal),
  );
  const activeConsumerGroups = consumers.filter(
    (item) => item.metricsAvailable !== false && num(item.consumeTps) > 0,
  ).length;
  const consumerGroupsWithLag = consumers.filter(
    (item) => item.metricsAvailable !== false && num(item.diffTotal) > 0,
  ).length;
  const metricsUnavailableGroups = consumers.filter(
    (item) => item.metricsAvailable === false,
  ).length;
  const knownTotalLag = consumers.reduce(
    (sum, item) => (item.metricsAvailable === false ? sum : sum + num(item.diffTotal)),
    0,
  );
  const topicMessageCount = num(input.topic.messageCount);
  const topicTps = num(input.topic.tps);
  const writableRoutes = routes.filter(routeWritable).length;
  const issues: TopicDeleteIssue[] = [];

  if (isSystemTopicName(input.topic.name)) {
    issues.push(
      issue(
        'SYSTEM_TOPIC',
        'critical',
        '系统 Topic',
        '名称符合 RocketMQ 内部 Topic 命名，误删可能影响重试、死信或调度链路。',
      ),
    );
  }
  if (input.consumerLookupFailed) {
    issues.push(
      issue(
        'CONSUMER_LOOKUP_FAILED',
        'warning',
        '消费者依赖不可确认',
        '消费者列表加载失败，无法判断是否仍有订阅关系。',
      ),
    );
  } else if (consumerGroups > 0) {
    issues.push(
      issue(
        'CONSUMER_GROUPS_ATTACHED',
        'critical',
        '仍有关联消费者组',
        `当前至少有 ${consumerGroups} 个消费者组订阅该 Topic。`,
      ),
    );
  }
  if (activeConsumerGroups > 0) {
    issues.push(
      issue(
        'ACTIVE_CONSUMPTION',
        'critical',
        '存在活跃消费',
        `${activeConsumerGroups} 个消费者组仍有消费 TPS。`,
      ),
    );
  }
  if (consumerGroupsWithLag > 0 || knownTotalLag > 0) {
    issues.push(
      issue(
        'BACKLOG_REMAINS',
        'critical',
        '仍有消费堆积',
        `${consumerGroupsWithLag} 个消费者组存在堆积，已知堆积量 ${knownTotalLag.toLocaleString()}。`,
      ),
    );
  }
  if (consumerGroups > consumers.length) {
    issues.push(
      issue(
        'CONSUMER_PAGE_TRUNCATED',
        'warning',
        '消费者列表未完整展开',
        `已加载 ${consumers.length} / ${consumerGroups} 个消费者组。`,
      ),
    );
  }
  if (metricsUnavailableGroups > 0) {
    issues.push(
      issue(
        'CONSUMER_METRICS_UNAVAILABLE',
        'warning',
        '消费者指标不可用',
        `${metricsUnavailableGroups} 个消费者组指标不可用。`,
      ),
    );
  }
  if (topicTps > 0) {
    issues.push(
      issue(
        'RECENT_TRAFFIC',
        'warning',
        'Topic 仍有写入流量',
        `当前 TPS 为 ${topicTps.toLocaleString()}。`,
      ),
    );
  }
  if (topicMessageCount > 0) {
    issues.push(
      issue(
        'RETAINED_MESSAGES',
        'warning',
        '仍有消息统计',
        `今日消息量为 ${topicMessageCount.toLocaleString()}。`,
      ),
    );
  }
  if (input.isCloudInstance) {
    issues.push(
      issue(
        'CLOUD_ROUTE_UNAVAILABLE',
        'info',
        '云实例路由不可见',
        '云厂商托管实例不直接展示 Broker 路由影响。',
      ),
    );
  } else if (input.routeLookupFailed) {
    issues.push(
      issue(
        'ROUTE_LOOKUP_FAILED',
        'warning',
        '路由影响不可确认',
        'Broker 路由加载失败，无法判断生产链路是否仍可写。',
      ),
    );
  } else if (routes.length === 0) {
    issues.push(
      issue('NO_ROUTE', 'info', '未发现 Broker 路由', '该 Topic 可能只存在于 Studio 元数据中。'),
    );
  } else if (writableRoutes > 0) {
    issues.push(
      issue(
        'WRITABLE_ROUTE',
        'warning',
        'Broker 路由仍可写',
        `${writableRoutes} 个 Broker 路由仍允许写入。`,
      ),
    );
  }

  return {
    topicName: input.topic.name,
    riskLevel: riskLevel(issues, input),
    riskScore: issues.reduce((sum, item) => sum + SEVERITY_SCORE[item.severity], 0),
    canDelete: !isSystemTopicName(input.topic.name),
    issues,
    stats: {
      consumerGroups,
      activeConsumerGroups,
      consumerGroupsWithLag,
      metricsUnavailableGroups,
      knownTotalLag,
      topicMessageCount,
      topicTps,
      routeCount: routes.length,
      writableRoutes,
      truncatedConsumers: consumerGroups > consumers.length,
      consumerLookupFailed: Boolean(input.consumerLookupFailed),
      routeLookupFailed: Boolean(input.routeLookupFailed),
    },
  };
};

const summaryRiskLevel = (items: TopicDeleteImpactAssessment[]): TopicDeleteRiskLevel => {
  if (items.some((item) => item.riskLevel === 'high')) return 'high';
  if (items.some((item) => item.riskLevel === 'unknown')) return 'unknown';
  if (items.some((item) => item.riskLevel === 'medium')) return 'medium';
  return 'low';
};

const countByRisk = (items: TopicDeleteImpactAssessment[], risk: TopicDeleteRiskLevel) =>
  items.filter((item) => item.riskLevel === risk).length;

const sum = (
  items: TopicDeleteImpactAssessment[],
  selector: (item: TopicDeleteImpactAssessment) => number,
) => items.reduce((total, item) => total + selector(item), 0);

export const summarizeTopicDeleteImpacts = (
  assessments: TopicDeleteImpactAssessment[],
): TopicDeleteImpactSummary => ({
  totalTopics: assessments.length,
  riskLevel: summaryRiskLevel(assessments),
  canDelete: assessments.every((item) => item.canDelete),
  highRiskTopics: countByRisk(assessments, 'high'),
  mediumRiskTopics: countByRisk(assessments, 'medium'),
  lowRiskTopics: countByRisk(assessments, 'low'),
  unknownRiskTopics: countByRisk(assessments, 'unknown'),
  blockedTopics: assessments.filter((item) => !item.canDelete).map((item) => item.topicName),
  totalConsumerGroups: sum(assessments, (item) => item.stats.consumerGroups),
  activeConsumerGroups: sum(assessments, (item) => item.stats.activeConsumerGroups),
  consumerGroupsWithLag: sum(assessments, (item) => item.stats.consumerGroupsWithLag),
  metricsUnavailableGroups: sum(assessments, (item) => item.stats.metricsUnavailableGroups),
  knownTotalLag: sum(assessments, (item) => item.stats.knownTotalLag),
  topicMessageCount: sum(assessments, (item) => item.stats.topicMessageCount),
  topicTps: sum(assessments, (item) => item.stats.topicTps),
  writableRoutes: sum(assessments, (item) => item.stats.writableRoutes),
  routeLookupFailedTopics: assessments.filter((item) => item.stats.routeLookupFailed).length,
  consumerLookupFailedTopics: assessments.filter((item) => item.stats.consumerLookupFailed).length,
  truncatedConsumerTopics: assessments.filter((item) => item.stats.truncatedConsumers).length,
});
