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

import type { ConsumerStatus, TraceNode, TraceRecord } from '../api/message';

export type TraceDiagnosticStatus = 'healthy' | 'warning' | 'critical';

export type TraceIssueCode =
  | 'NO_TRACE_NODES'
  | 'FAILED_TRACE_NODE'
  | 'WAITING_TRACE_NODE'
  | 'INVALID_TRACE_TIMESTAMP'
  | 'TRACE_TIMESTAMP_REGRESSION'
  | 'INVALID_TRACE_COST'
  | 'SLOW_TRACE_NODE'
  | 'SLOW_TRACE_GAP'
  | 'SLOW_END_TO_END_TRACE'
  | 'MISSING_CONSUMER_STATUS'
  | 'FAILED_CONSUMER_DELIVERY'
  | 'PENDING_CONSUMER_DELIVERY'
  | 'UNKNOWN_CONSUMER_DELIVERY'
  | 'RETRIED_CONSUMER_DELIVERY'
  | 'INVALID_CONSUME_TIME';

export interface TraceDiagnosticIssue {
  id: string;
  code: TraceIssueCode;
  severity: Exclude<TraceDiagnosticStatus, 'healthy'>;
  title: string;
  description: string;
  subject?: string;
}

export interface TraceDiagnosticPhase {
  key: string;
  title: string;
  status: TraceNode['status'];
  timestampMs: number | null;
  costTimeMs: number | null;
  latencyFromPreviousMs: number | null;
  latencyFromStartMs: number | null;
}

export interface ConsumerDeliveryDiagnostic {
  key: string;
  group: string;
  deliveryStatus: string;
  severity: TraceDiagnosticStatus;
  consumeTimeMs: number | null;
  retryCount: number;
  latencyFromTraceStartMs: number | null;
}

export interface TraceLatencyHotspot {
  key: string;
  title: string;
  valueMs: number;
}

export interface TraceDiagnosticSummary {
  nodeCount: number;
  consumerGroupCount: number;
  failedNodeCount: number;
  waitingNodeCount: number;
  failedConsumerCount: number;
  pendingConsumerCount: number;
  retriedConsumerCount: number;
  totalNodeCostMs: number;
  endToEndLatencyMs: number | null;
  successfulConsumerRate: number | null;
  slowestNode: TraceLatencyHotspot | null;
  slowestGap: TraceLatencyHotspot | null;
}

export interface MessageTraceDiagnostics {
  status: TraceDiagnosticStatus;
  statusText: string;
  statusColor: 'success' | 'warning' | 'error';
  score: number;
  summary: TraceDiagnosticSummary;
  phases: TraceDiagnosticPhase[];
  deliveries: ConsumerDeliveryDiagnostic[];
  issues: TraceDiagnosticIssue[];
  recommendations: string[];
}

export interface TraceDiagnosticOptions {
  slowNodeCostMs?: number;
  criticalNodeCostMs?: number;
  slowGapMs?: number;
  slowEndToEndMs?: number;
  criticalEndToEndMs?: number;
}

const DEFAULT_OPTIONS: Required<TraceDiagnosticOptions> = {
  slowNodeCostMs: 500,
  criticalNodeCostMs: 5000,
  slowGapMs: 1000,
  slowEndToEndMs: 5000,
  criticalEndToEndMs: 30000,
};

const STATUS_TEXT: Record<TraceDiagnosticStatus, string> = {
  healthy: '轨迹健康',
  warning: '需要关注',
  critical: '投递异常',
};

const STATUS_COLOR: Record<TraceDiagnosticStatus, 'success' | 'warning' | 'error'> = {
  healthy: 'success',
  warning: 'warning',
  critical: 'error',
};

const STATUS_ORDER: Record<TraceDiagnosticStatus, number> = {
  healthy: 0,
  warning: 1,
  critical: 2,
};

const ISSUE_SCORE_PENALTY: Record<Exclude<TraceDiagnosticStatus, 'healthy'>, number> = {
  warning: 8,
  critical: 24,
};

const ISSUE_RECOMMENDATIONS: Record<TraceIssueCode, string> = {
  NO_TRACE_NODES: '确认消息轨迹已开启，并检查是否需要指定自定义轨迹 Topic。',
  FAILED_TRACE_NODE:
    '优先查看失败阶段对应的生产者、Broker 或消费者日志，确认失败返回码和异常堆栈。',
  WAITING_TRACE_NODE: '等待或处理中阶段需要结合消费者在线状态和堆积情况确认是否仍在推进。',
  INVALID_TRACE_TIMESTAMP: '检查生产者、Broker 与消费者机器时间，避免时钟漂移影响轨迹判断。',
  TRACE_TIMESTAMP_REGRESSION: '轨迹时间出现回退时，先确认各节点 NTP 同步和跨机房时间源配置。',
  INVALID_TRACE_COST: '忽略异常耗时值后再判断链路瓶颈，并核对服务端轨迹采集字段是否完整。',
  SLOW_TRACE_NODE: '对耗时最高的阶段做分段排查，区分发送、存储和消费处理时间。',
  SLOW_TRACE_GAP: '相邻阶段间隔过大时，检查 Broker 拉取、客户端长轮询和消费线程池排队。',
  SLOW_END_TO_END_TRACE:
    '端到端耗时过高时，结合 Topic 队列分布、Consumer Group 进度和客户端负载一起排查。',
  MISSING_CONSUMER_STATUS:
    '缺少消费状态时，可用直接消费或 Consumer Group 进度进一步确认消息是否可达。',
  FAILED_CONSUMER_DELIVERY: '失败消费组需要检查消费异常、重试 Topic 和业务幂等处理。',
  PENDING_CONSUMER_DELIVERY:
    '等待中的消费组需要确认客户端是否在线、订阅是否匹配以及是否存在明显堆积。',
  UNKNOWN_CONSUMER_DELIVERY: '未知消费状态需要回查服务端返回值，避免把未识别状态误判为成功。',
  RETRIED_CONSUMER_DELIVERY: '存在重试时，检查消费耗时、异常类型和重试次数是否符合预期。',
  INVALID_CONSUME_TIME: '消费时间不可解析时，检查 trace 数据生成端是否返回了完整时间字段。',
};

const parseTimestamp = (value: number | string): number | null => {
  if (typeof value === 'number') {
    return Number.isFinite(value) && value > 0 ? value : null;
  }
  const trimmed = value.trim();
  if (!trimmed || trimmed === '-') return null;
  const parsed = Date.parse(trimmed);
  return Number.isNaN(parsed) ? null : parsed;
};

const normalizeCostTime = (value: number): number | null =>
  Number.isFinite(value) && value >= 0 ? value : null;

const normalizeRetryCount = (value: number): number =>
  Number.isFinite(value) && value > 0 ? Math.floor(value) : 0;

const normalizeDeliveryStatus = (value: string): string => value.trim().toLowerCase();

const buildIssue = (
  code: TraceIssueCode,
  severity: Exclude<TraceDiagnosticStatus, 'healthy'>,
  title: string,
  description: string,
  subject?: string,
): TraceDiagnosticIssue => ({
  id: subject ? `${code}:${subject}` : code,
  code,
  severity,
  title,
  description,
  subject,
});

const maxStatus = (issues: TraceDiagnosticIssue[]): TraceDiagnosticStatus =>
  issues.reduce<TraceDiagnosticStatus>(
    (status, issue) =>
      STATUS_ORDER[issue.severity] > STATUS_ORDER[status] ? issue.severity : status,
    'healthy',
  );

const calculateScore = (issues: TraceDiagnosticIssue[]): number => {
  const penalty = issues.reduce((sum, issue) => sum + ISSUE_SCORE_PENALTY[issue.severity], 0);
  return Math.max(0, 100 - penalty);
};

const severityForLatency = (
  value: number,
  warningThreshold: number,
  criticalThreshold: number,
): Exclude<TraceDiagnosticStatus, 'healthy'> =>
  value >= criticalThreshold ? 'critical' : value >= warningThreshold ? 'warning' : 'warning';

const collectPhaseIssues = (
  phase: TraceDiagnosticPhase,
  options: Required<TraceDiagnosticOptions>,
): TraceDiagnosticIssue[] => {
  const issues: TraceDiagnosticIssue[] = [];

  if (phase.status === 'error') {
    issues.push(
      buildIssue(
        'FAILED_TRACE_NODE',
        'critical',
        '轨迹阶段失败',
        `${phase.title} 阶段返回失败状态。`,
        phase.key,
      ),
    );
  } else if (phase.status === 'wait' || phase.status === 'process') {
    issues.push(
      buildIssue(
        'WAITING_TRACE_NODE',
        'warning',
        '轨迹阶段未完成',
        `${phase.title} 阶段仍处于等待或处理中状态。`,
        phase.key,
      ),
    );
  }

  if (phase.timestampMs == null) {
    issues.push(
      buildIssue(
        'INVALID_TRACE_TIMESTAMP',
        'warning',
        '轨迹时间不可用',
        `${phase.title} 阶段没有可解析的时间戳。`,
        phase.key,
      ),
    );
  }

  if (phase.costTimeMs == null) {
    issues.push(
      buildIssue(
        'INVALID_TRACE_COST',
        'warning',
        '阶段耗时不可用',
        `${phase.title} 阶段返回了无效耗时。`,
        phase.key,
      ),
    );
  } else if (phase.costTimeMs >= options.slowNodeCostMs) {
    issues.push(
      buildIssue(
        'SLOW_TRACE_NODE',
        severityForLatency(phase.costTimeMs, options.slowNodeCostMs, options.criticalNodeCostMs),
        '阶段耗时偏高',
        `${phase.title} 阶段耗时 ${phase.costTimeMs} ms。`,
        phase.key,
      ),
    );
  }

  if (phase.latencyFromPreviousMs != null && phase.latencyFromPreviousMs >= options.slowGapMs) {
    issues.push(
      buildIssue(
        'SLOW_TRACE_GAP',
        'warning',
        '相邻阶段间隔偏高',
        `${phase.title} 与上一阶段相隔 ${phase.latencyFromPreviousMs} ms。`,
        phase.key,
      ),
    );
  }

  return issues;
};

const mapPhases = (nodes: TraceNode[]): TraceDiagnosticPhase[] => {
  const firstTimestamp = nodes
    .map((node) => parseTimestamp(node.timestamp))
    .find((ts) => ts != null);
  let previousTimestamp: number | null = null;

  return nodes.map((node, index) => {
    const timestampMs = parseTimestamp(node.timestamp);
    const latencyFromPreviousMs =
      timestampMs != null && previousTimestamp != null ? timestampMs - previousTimestamp : null;
    const latencyFromStartMs =
      timestampMs != null && firstTimestamp != null ? timestampMs - firstTimestamp : null;
    if (timestampMs != null) previousTimestamp = timestampMs;

    return {
      key: `${index}:${node.title}`,
      title: node.title || `Trace node ${index + 1}`,
      status: node.status,
      timestampMs,
      costTimeMs: normalizeCostTime(node.costTime),
      latencyFromPreviousMs,
      latencyFromStartMs,
    };
  });
};

const collectTimelineIssues = (phases: TraceDiagnosticPhase[]): TraceDiagnosticIssue[] => {
  const issues: TraceDiagnosticIssue[] = [];

  phases.forEach((phase) => {
    if (phase.latencyFromPreviousMs != null && phase.latencyFromPreviousMs < 0) {
      issues.push(
        buildIssue(
          'TRACE_TIMESTAMP_REGRESSION',
          'warning',
          '轨迹时间发生回退',
          `${phase.title} 比上一阶段早 ${Math.abs(phase.latencyFromPreviousMs)} ms。`,
          phase.key,
        ),
      );
    }
  });

  return issues;
};

const consumerDeliverySeverity = (
  deliveryStatus: string,
  retryCount: number,
): TraceDiagnosticStatus => {
  if (deliveryStatus === 'failed') return 'critical';
  if (deliveryStatus === 'pending' || retryCount > 0) return 'warning';
  if (deliveryStatus === 'success') return 'healthy';
  return 'warning';
};

const mapDeliveries = (
  consumerStatus: ConsumerStatus[],
  traceStartMs: number | null,
): ConsumerDeliveryDiagnostic[] =>
  consumerStatus.map((status, index) => {
    const consumeTimeMs = parseTimestamp(status.consumeTime);
    const retryCount = normalizeRetryCount(status.retryCount);
    const deliveryStatus = normalizeDeliveryStatus(status.deliveryStatus);
    return {
      key: `${index}:${status.group || 'unknown'}`,
      group: status.group || 'UNKNOWN',
      deliveryStatus: deliveryStatus || 'unknown',
      severity: consumerDeliverySeverity(deliveryStatus, retryCount),
      consumeTimeMs,
      retryCount,
      latencyFromTraceStartMs:
        traceStartMs != null && consumeTimeMs != null ? consumeTimeMs - traceStartMs : null,
    };
  });

const collectDeliveryIssues = (
  deliveries: ConsumerDeliveryDiagnostic[],
): TraceDiagnosticIssue[] => {
  const issues: TraceDiagnosticIssue[] = [];

  deliveries.forEach((delivery) => {
    if (delivery.deliveryStatus === 'failed') {
      issues.push(
        buildIssue(
          'FAILED_CONSUMER_DELIVERY',
          'critical',
          '消费投递失败',
          `${delivery.group} 返回失败消费状态。`,
          delivery.key,
        ),
      );
    } else if (delivery.deliveryStatus === 'pending') {
      issues.push(
        buildIssue(
          'PENDING_CONSUMER_DELIVERY',
          'warning',
          '消费投递等待中',
          `${delivery.group} 尚未完成消费。`,
          delivery.key,
        ),
      );
    } else if (delivery.deliveryStatus !== 'success') {
      issues.push(
        buildIssue(
          'UNKNOWN_CONSUMER_DELIVERY',
          'warning',
          '消费状态未知',
          `${delivery.group} 返回未识别状态 ${delivery.deliveryStatus}。`,
          delivery.key,
        ),
      );
    }

    if (delivery.retryCount > 0) {
      issues.push(
        buildIssue(
          'RETRIED_CONSUMER_DELIVERY',
          'warning',
          '消费发生重试',
          `${delivery.group} 已重试 ${delivery.retryCount} 次。`,
          delivery.key,
        ),
      );
    }

    if (delivery.deliveryStatus !== 'pending' && delivery.consumeTimeMs == null) {
      issues.push(
        buildIssue(
          'INVALID_CONSUME_TIME',
          'warning',
          '消费时间不可用',
          `${delivery.group} 没有可解析的消费时间。`,
          delivery.key,
        ),
      );
    }
  });

  return issues;
};

const maxByValue = <T>(items: T[], extractor: (item: T) => number | null): T | null => {
  let selected: T | null = null;
  let selectedValue = Number.NEGATIVE_INFINITY;

  items.forEach((item) => {
    const value = extractor(item);
    if (value != null && value > selectedValue) {
      selected = item;
      selectedValue = value;
    }
  });

  return selected;
};

const buildSummary = (
  phases: TraceDiagnosticPhase[],
  deliveries: ConsumerDeliveryDiagnostic[],
): TraceDiagnosticSummary => {
  const totalNodeCostMs = phases.reduce((sum, phase) => sum + (phase.costTimeMs ?? 0), 0);
  const validTimestamps = phases
    .map((phase) => phase.timestampMs)
    .filter((value): value is number => value != null);
  const endToEndLatencyMs =
    validTimestamps.length >= 2
      ? Math.max(...validTimestamps) - Math.min(...validTimestamps)
      : null;
  const successCount = deliveries.filter(
    (delivery) => delivery.deliveryStatus === 'success',
  ).length;
  const slowestNode = maxByValue(phases, (phase) => phase.costTimeMs);
  const slowestGap = maxByValue(phases, (phase) =>
    phase.latencyFromPreviousMs != null && phase.latencyFromPreviousMs >= 0
      ? phase.latencyFromPreviousMs
      : null,
  );

  return {
    nodeCount: phases.length,
    consumerGroupCount: deliveries.length,
    failedNodeCount: phases.filter((phase) => phase.status === 'error').length,
    waitingNodeCount: phases.filter(
      (phase) => phase.status === 'wait' || phase.status === 'process',
    ).length,
    failedConsumerCount: deliveries.filter((delivery) => delivery.deliveryStatus === 'failed')
      .length,
    pendingConsumerCount: deliveries.filter((delivery) => delivery.deliveryStatus === 'pending')
      .length,
    retriedConsumerCount: deliveries.filter((delivery) => delivery.retryCount > 0).length,
    totalNodeCostMs,
    endToEndLatencyMs,
    successfulConsumerRate:
      deliveries.length === 0 ? null : Math.round((successCount / deliveries.length) * 1000) / 10,
    slowestNode:
      slowestNode && slowestNode.costTimeMs != null
        ? { key: slowestNode.key, title: slowestNode.title, valueMs: slowestNode.costTimeMs }
        : null,
    slowestGap:
      slowestGap && slowestGap.latencyFromPreviousMs != null
        ? {
            key: slowestGap.key,
            title: slowestGap.title,
            valueMs: slowestGap.latencyFromPreviousMs,
          }
        : null,
  };
};

const collectSummaryIssues = (
  summary: TraceDiagnosticSummary,
  options: Required<TraceDiagnosticOptions>,
): TraceDiagnosticIssue[] => {
  const issues: TraceDiagnosticIssue[] = [];

  if (summary.nodeCount === 0) {
    issues.push(
      buildIssue('NO_TRACE_NODES', 'warning', '缺少轨迹节点', '当前消息没有返回可展示的轨迹阶段。'),
    );
  }

  if (summary.nodeCount > 0 && summary.consumerGroupCount === 0) {
    issues.push(
      buildIssue(
        'MISSING_CONSUMER_STATUS',
        'warning',
        '缺少消费状态',
        '轨迹中没有返回任何消费组的投递状态。',
      ),
    );
  }

  if (summary.endToEndLatencyMs != null && summary.endToEndLatencyMs >= options.slowEndToEndMs) {
    issues.push(
      buildIssue(
        'SLOW_END_TO_END_TRACE',
        severityForLatency(
          summary.endToEndLatencyMs,
          options.slowEndToEndMs,
          options.criticalEndToEndMs,
        ),
        '端到端轨迹耗时偏高',
        `首尾轨迹阶段相隔 ${summary.endToEndLatencyMs} ms。`,
      ),
    );
  }

  return issues;
};

const buildRecommendations = (issues: TraceDiagnosticIssue[]): string[] => {
  const codes = new Set(issues.map((issue) => issue.code));
  const recommendations = [...codes].map((code) => ISSUE_RECOMMENDATIONS[code]);
  return recommendations.filter((item, index) => recommendations.indexOf(item) === index);
};

export function analyzeMessageTrace(
  trace: TraceRecord | null | undefined,
  options: TraceDiagnosticOptions = {},
): MessageTraceDiagnostics {
  const normalizedOptions = { ...DEFAULT_OPTIONS, ...options };
  const phases = mapPhases(trace?.nodes ?? []);
  const traceStartMs =
    phases.map((phase) => phase.timestampMs).find((value) => value != null) ?? null;
  const deliveries = mapDeliveries(trace?.consumerStatus ?? [], traceStartMs);
  const summary = buildSummary(phases, deliveries);
  const issues = [
    ...collectSummaryIssues(summary, normalizedOptions),
    ...phases.flatMap((phase) => collectPhaseIssues(phase, normalizedOptions)),
    ...collectTimelineIssues(phases),
    ...collectDeliveryIssues(deliveries),
  ];
  const status = maxStatus(issues);

  return {
    status,
    statusText: STATUS_TEXT[status],
    statusColor: STATUS_COLOR[status],
    score: calculateScore(issues),
    summary,
    phases,
    deliveries,
    issues,
    recommendations: buildRecommendations(issues),
  };
}
