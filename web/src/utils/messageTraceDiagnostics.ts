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
  titleKey: string;
  descriptionKey: string;
  params?: Record<string, string | number>;
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
  statusKey: string;
  statusColor: 'success' | 'warning' | 'error';
  score: number;
  summary: TraceDiagnosticSummary;
  phases: TraceDiagnosticPhase[];
  deliveries: ConsumerDeliveryDiagnostic[];
  issues: TraceDiagnosticIssue[];
  recommendationCodes: TraceIssueCode[];
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

const STATUS_KEY: Record<TraceDiagnosticStatus, string> = {
  healthy: 'messagePage.traceStatusHealthy',
  warning: 'messagePage.traceStatusWarning',
  critical: 'messagePage.traceStatusDeliveryCritical',
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
  titleKey: string,
  descriptionKey: string,
  params?: Record<string, string | number>,
  subject?: string,
): TraceDiagnosticIssue => ({
  id: subject ? `${code}:${subject}` : code,
  code,
  severity,
  titleKey,
  descriptionKey,
  params,
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
        'messagePage.issue.FAILED_TRACE_NODE.title',
        'messagePage.issue.FAILED_TRACE_NODE.description',
        { phase: phase.title },
        phase.key,
      ),
    );
  } else if (phase.status === 'wait' || phase.status === 'process') {
    issues.push(
      buildIssue(
        'WAITING_TRACE_NODE',
        'warning',
        'messagePage.issue.WAITING_TRACE_NODE.title',
        'messagePage.issue.WAITING_TRACE_NODE.description',
        { phase: phase.title },
        phase.key,
      ),
    );
  }

  if (phase.timestampMs == null) {
    issues.push(
      buildIssue(
        'INVALID_TRACE_TIMESTAMP',
        'warning',
        'messagePage.issue.INVALID_TRACE_TIMESTAMP.title',
        'messagePage.issue.INVALID_TRACE_TIMESTAMP.description',
        { phase: phase.title },
        phase.key,
      ),
    );
  }

  if (phase.costTimeMs == null) {
    issues.push(
      buildIssue(
        'INVALID_TRACE_COST',
        'warning',
        'messagePage.issue.INVALID_TRACE_COST.title',
        'messagePage.issue.INVALID_TRACE_COST.description',
        { phase: phase.title },
        phase.key,
      ),
    );
  } else if (phase.costTimeMs >= options.slowNodeCostMs) {
    issues.push(
      buildIssue(
        'SLOW_TRACE_NODE',
        severityForLatency(phase.costTimeMs, options.slowNodeCostMs, options.criticalNodeCostMs),
        'messagePage.issue.SLOW_TRACE_NODE.title',
        'messagePage.issue.SLOW_TRACE_NODE.description',
        { phase: phase.title, cost: phase.costTimeMs },
        phase.key,
      ),
    );
  }

  if (phase.latencyFromPreviousMs != null && phase.latencyFromPreviousMs >= options.slowGapMs) {
    issues.push(
      buildIssue(
        'SLOW_TRACE_GAP',
        'warning',
        'messagePage.issue.SLOW_TRACE_GAP.title',
        'messagePage.issue.SLOW_TRACE_GAP.description',
        { phase: phase.title, gap: phase.latencyFromPreviousMs },
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
          'messagePage.issue.TRACE_TIMESTAMP_REGRESSION.title',
          'messagePage.issue.TRACE_TIMESTAMP_REGRESSION.description',
          { phase: phase.title, gap: Math.abs(phase.latencyFromPreviousMs) },
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
          'messagePage.issue.FAILED_CONSUMER_DELIVERY.title',
          'messagePage.issue.FAILED_CONSUMER_DELIVERY.description',
          { group: delivery.group },
          delivery.key,
        ),
      );
    } else if (delivery.deliveryStatus === 'pending') {
      issues.push(
        buildIssue(
          'PENDING_CONSUMER_DELIVERY',
          'warning',
          'messagePage.issue.PENDING_CONSUMER_DELIVERY.title',
          'messagePage.issue.PENDING_CONSUMER_DELIVERY.description',
          { group: delivery.group },
          delivery.key,
        ),
      );
    } else if (delivery.deliveryStatus !== 'success') {
      issues.push(
        buildIssue(
          'UNKNOWN_CONSUMER_DELIVERY',
          'warning',
          'messagePage.issue.UNKNOWN_CONSUMER_DELIVERY.title',
          'messagePage.issue.UNKNOWN_CONSUMER_DELIVERY.description',
          { group: delivery.group, status: delivery.deliveryStatus },
          delivery.key,
        ),
      );
    }

    if (delivery.retryCount > 0) {
      issues.push(
        buildIssue(
          'RETRIED_CONSUMER_DELIVERY',
          'warning',
          'messagePage.issue.RETRIED_CONSUMER_DELIVERY.title',
          'messagePage.issue.RETRIED_CONSUMER_DELIVERY.description',
          { group: delivery.group, retry: delivery.retryCount },
          delivery.key,
        ),
      );
    }

    if (delivery.deliveryStatus !== 'pending' && delivery.consumeTimeMs == null) {
      issues.push(
        buildIssue(
          'INVALID_CONSUME_TIME',
          'warning',
          'messagePage.issue.INVALID_CONSUME_TIME.title',
          'messagePage.issue.INVALID_CONSUME_TIME.description',
          { group: delivery.group },
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
      buildIssue(
        'NO_TRACE_NODES',
        'warning',
        'messagePage.issue.NO_TRACE_NODES.title',
        'messagePage.issue.NO_TRACE_NODES.description',
      ),
    );
  }

  if (summary.nodeCount > 0 && summary.consumerGroupCount === 0) {
    issues.push(
      buildIssue(
        'MISSING_CONSUMER_STATUS',
        'warning',
        'messagePage.issue.MISSING_CONSUMER_STATUS.title',
        'messagePage.issue.MISSING_CONSUMER_STATUS.description',
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
        'messagePage.issue.SLOW_END_TO_END_TRACE.title',
        'messagePage.issue.SLOW_END_TO_END_TRACE.description',
        { latency: summary.endToEndLatencyMs },
      ),
    );
  }

  return issues;
};

const buildRecommendationCodes = (issues: TraceDiagnosticIssue[]): TraceIssueCode[] => [
  ...new Set(issues.map((issue) => issue.code)),
];

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
    statusKey: STATUS_KEY[status],
    statusColor: STATUS_COLOR[status],
    score: calculateScore(issues),
    summary,
    phases,
    deliveries,
    issues,
    recommendationCodes: buildRecommendationCodes(issues),
  };
}
