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

import type {
  MessageQueryHistory,
  QueryHistorySummary,
  TraceQueryHistory,
} from '../api/messageHistory';

export type MessageHistoryInsightLevel = 'healthy' | 'notice' | 'warning' | 'critical';

export type MessageHistoryInsightCode =
  | 'NO_HISTORY'
  | 'STALE_HISTORY'
  | 'TRACE_UNDERUSED'
  | 'ZERO_RESULT_QUERIES'
  | 'BROAD_TOPIC_QUERIES'
  | 'LARGE_RESULT_QUERIES'
  | 'TRACE_WITHOUT_NODES'
  | 'TRACE_WITHOUT_CONSUMERS'
  | 'FRAGMENTED_TRACE_TOPICS'
  | 'UNKNOWN_OPERATORS';

export type MessageHistoryRecommendationCode =
  | 'NARROW_QUERY_FILTERS'
  | 'REPLAY_ZERO_RESULTS'
  | 'PAIR_TRACE_LOOKUPS'
  | 'STANDARDIZE_TRACE_TOPIC'
  | 'CHECK_TRACE_COLLECTION'
  | 'ADD_OPERATOR_CONTEXT'
  | 'KEEP_RECENT_BASELINE';

export interface MessageHistoryInsightIssue {
  code: MessageHistoryInsightCode;
  level: MessageHistoryInsightLevel;
  count?: number;
  ratio?: number;
  value?: number;
  queryType?: MessageQueryHistory['queryType'];
  topic?: string;
  traceTopic?: string;
  operator?: string;
}

export interface MessageHistoryNotableRow {
  key: string;
  kind: 'message' | 'trace';
  level: MessageHistoryInsightLevel;
  reasons: MessageHistoryInsightCode[];
  topic: string;
  detail: string;
  queriedBy: string;
  queriedAt: string;
  resultCount?: number;
  nodeCount?: number;
  consumerCount?: number;
  traceTopic?: string;
}

export interface MessageHistoryInsightStats {
  totalQueries: number;
  messageQueries: number;
  traceQueries: number;
  messageQueryRatio: number | null;
  traceQueryRatio: number | null;
  latestQueryAgeHours: number | null;
  visibleMessageRows: number;
  visibleTraceRows: number;
  zeroResultQueries: number;
  broadTopicQueries: number;
  largeResultQueries: number;
  traceRowsWithoutNodes: number;
  traceRowsWithoutConsumers: number;
  customTraceTopicCount: number;
  unknownOperators: number;
}

export interface MessageHistoryInsights {
  level: MessageHistoryInsightLevel;
  score: number;
  stats: MessageHistoryInsightStats;
  issues: MessageHistoryInsightIssue[];
  recommendations: MessageHistoryRecommendationCode[];
  notableRows: MessageHistoryNotableRow[];
}

const BROAD_QUERY_WINDOW_HOURS = 24;
const STALE_HISTORY_HOURS = 72;
const TRACE_UNDERUSED_MIN_TOTAL = 10;
const TRACE_UNDERUSED_RATIO = 10;
const LARGE_RESULT_WARNING = 500;
const LARGE_RESULT_CRITICAL = 2_000;
const MAX_NOTABLE_ROWS = 8;

const levelWeight: Record<MessageHistoryInsightLevel, number> = {
  healthy: 0,
  notice: 1,
  warning: 2,
  critical: 3,
};

const issuePenalty: Record<MessageHistoryInsightCode, number> = {
  NO_HISTORY: 12,
  STALE_HISTORY: 8,
  TRACE_UNDERUSED: 10,
  ZERO_RESULT_QUERIES: 12,
  BROAD_TOPIC_QUERIES: 12,
  LARGE_RESULT_QUERIES: 16,
  TRACE_WITHOUT_NODES: 18,
  TRACE_WITHOUT_CONSUMERS: 10,
  FRAGMENTED_TRACE_TOPICS: 8,
  UNKNOWN_OPERATORS: 6,
};

const addUnique = <T>(items: T[], item: T) => {
  if (!items.includes(item)) items.push(item);
};

const normalizeText = (value: string | undefined | null, fallback = '-'): string => {
  const trimmed = value?.trim();
  return trimmed || fallback;
};

const normalizeCount = (value: number | undefined | null): number => {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return 0;
  return Math.max(0, parsed);
};

const parseTimestamp = (value: string | number | undefined | null): number | null => {
  if (value == null || value === '') return null;
  const timestamp = typeof value === 'number' ? value : Date.parse(value);
  return Number.isFinite(timestamp) && timestamp > 0 ? timestamp : null;
};

const roundedPercent = (value: number): number => Math.round(value * 10) / 10;

const percent = (part: number, total: number): number | null => {
  if (total <= 0) return null;
  return roundedPercent((part / total) * 100);
};

const maxLevel = (levels: MessageHistoryInsightLevel[]): MessageHistoryInsightLevel =>
  levels.reduce<MessageHistoryInsightLevel>(
    (current, next) => (levelWeight[next] > levelWeight[current] ? next : current),
    'healthy',
  );

const issueLevel = (code: MessageHistoryInsightCode): MessageHistoryInsightLevel => {
  switch (code) {
    case 'LARGE_RESULT_QUERIES':
    case 'TRACE_WITHOUT_NODES':
      return 'warning';
    case 'ZERO_RESULT_QUERIES':
    case 'BROAD_TOPIC_QUERIES':
    case 'TRACE_UNDERUSED':
    case 'TRACE_WITHOUT_CONSUMERS':
    case 'FRAGMENTED_TRACE_TOPICS':
      return 'notice';
    case 'NO_HISTORY':
    case 'STALE_HISTORY':
    case 'UNKNOWN_OPERATORS':
      return 'notice';
    default:
      return 'notice';
  }
};

const scoreForIssues = (issues: MessageHistoryInsightIssue[]): number => {
  const penalty = issues.reduce(
    (sum, issue) => sum + issuePenalty[issue.code] * Math.max(1, Math.min(issue.count ?? 1, 5)),
    0,
  );
  return Math.max(0, Math.min(100, 100 - penalty));
};

const queryWindowHours = (row: MessageQueryHistory): number | null => {
  const start = parseTimestamp(row.startTime);
  const end = parseTimestamp(row.endTime);
  if (start == null || end == null || end <= start) return null;
  return roundedPercent((end - start) / 3_600_000);
};

const isBroadTopicQuery = (row: MessageQueryHistory): boolean => {
  if (row.queryType !== 'TOPIC') return false;
  const hasNarrowField = Boolean(row.tag?.trim() || row.messageKey?.trim() || row.msgId?.trim());
  if (hasNarrowField) return false;
  const hours = queryWindowHours(row);
  return hours == null || hours >= BROAD_QUERY_WINDOW_HOURS;
};

const messageRowReasons = (row: MessageQueryHistory): MessageHistoryInsightCode[] => {
  const reasons: MessageHistoryInsightCode[] = [];
  const resultCount = normalizeCount(row.resultCount);

  if (resultCount === 0) reasons.push('ZERO_RESULT_QUERIES');
  if (isBroadTopicQuery(row)) reasons.push('BROAD_TOPIC_QUERIES');
  if (resultCount >= LARGE_RESULT_WARNING) reasons.push('LARGE_RESULT_QUERIES');
  if (!row.queriedBy?.trim()) reasons.push('UNKNOWN_OPERATORS');

  return reasons;
};

const traceRowReasons = (row: TraceQueryHistory): MessageHistoryInsightCode[] => {
  const reasons: MessageHistoryInsightCode[] = [];
  if (normalizeCount(row.nodeCount) === 0) reasons.push('TRACE_WITHOUT_NODES');
  if (normalizeCount(row.consumerCount) === 0) reasons.push('TRACE_WITHOUT_CONSUMERS');
  if (!row.queriedBy?.trim()) reasons.push('UNKNOWN_OPERATORS');
  return reasons;
};

const messageDetail = (row: MessageQueryHistory): string => {
  if (row.msgId?.trim()) return row.msgId.trim();
  if (row.messageKey?.trim()) return row.messageKey.trim();
  if (row.tag?.trim()) return row.tag.trim();
  const hours = queryWindowHours(row);
  return hours == null ? row.queryType : `${row.queryType} / ${hours}h`;
};

const toMessageNotableRow = (
  row: MessageQueryHistory,
  reasons: MessageHistoryInsightCode[],
): MessageHistoryNotableRow => ({
  key: `message-${row.id}`,
  kind: 'message',
  level:
    normalizeCount(row.resultCount) >= LARGE_RESULT_CRITICAL
      ? 'critical'
      : maxLevel(reasons.map(issueLevel)),
  reasons,
  topic: normalizeText(row.topic),
  detail: messageDetail(row),
  queriedBy: normalizeText(row.queriedBy),
  queriedAt: normalizeText(row.queriedAt),
  resultCount: normalizeCount(row.resultCount),
});

const toTraceNotableRow = (
  row: TraceQueryHistory,
  reasons: MessageHistoryInsightCode[],
): MessageHistoryNotableRow => ({
  key: `trace-${row.id}`,
  kind: 'trace',
  level: maxLevel(reasons.map(issueLevel)),
  reasons,
  topic: normalizeText(row.topic),
  detail: normalizeText(row.msgId),
  queriedBy: normalizeText(row.queriedBy),
  queriedAt: normalizeText(row.queriedAt),
  nodeCount: normalizeCount(row.nodeCount),
  consumerCount: normalizeCount(row.consumerCount),
  traceTopic: normalizeText(row.traceTopic, 'default'),
});

const compareNotableRows = (left: MessageHistoryNotableRow, right: MessageHistoryNotableRow) =>
  levelWeight[right.level] - levelWeight[left.level] ||
  (right.resultCount ?? right.nodeCount ?? 0) - (left.resultCount ?? left.nodeCount ?? 0) ||
  left.topic.localeCompare(right.topic) ||
  left.detail.localeCompare(right.detail);

const pushIssue = (
  issues: MessageHistoryInsightIssue[],
  code: MessageHistoryInsightCode,
  count: number,
  details: Partial<MessageHistoryInsightIssue> = {},
) => {
  if (count <= 0) return;
  issues.push({
    code,
    level: details.level ?? issueLevel(code),
    count,
    ...details,
  });
};

const recommendationsForIssues = (
  issues: MessageHistoryInsightIssue[],
): MessageHistoryRecommendationCode[] => {
  const recommendations: MessageHistoryRecommendationCode[] = [];
  for (const issue of issues) {
    switch (issue.code) {
      case 'NO_HISTORY':
      case 'STALE_HISTORY':
        addUnique(recommendations, 'KEEP_RECENT_BASELINE');
        break;
      case 'TRACE_UNDERUSED':
        addUnique(recommendations, 'PAIR_TRACE_LOOKUPS');
        break;
      case 'ZERO_RESULT_QUERIES':
        addUnique(recommendations, 'REPLAY_ZERO_RESULTS');
        break;
      case 'BROAD_TOPIC_QUERIES':
      case 'LARGE_RESULT_QUERIES':
        addUnique(recommendations, 'NARROW_QUERY_FILTERS');
        break;
      case 'TRACE_WITHOUT_NODES':
      case 'TRACE_WITHOUT_CONSUMERS':
        addUnique(recommendations, 'CHECK_TRACE_COLLECTION');
        break;
      case 'FRAGMENTED_TRACE_TOPICS':
        addUnique(recommendations, 'STANDARDIZE_TRACE_TOPIC');
        break;
      case 'UNKNOWN_OPERATORS':
        addUnique(recommendations, 'ADD_OPERATOR_CONTEXT');
        break;
      default:
        break;
    }
  }
  return recommendations.slice(0, 6);
};

export function buildMessageHistoryInsights(
  summary: QueryHistorySummary | undefined,
  messageRows: MessageQueryHistory[] | null | undefined,
  traceRows: TraceQueryHistory[] | null | undefined,
  now = Date.now(),
): MessageHistoryInsights {
  const messages = messageRows ?? [];
  const traces = traceRows ?? [];
  const messageQueries = normalizeCount(summary?.messageQueries);
  const traceQueries = normalizeCount(summary?.traceQueries);
  const totalQueries = messageQueries + traceQueries;
  const latestQueryAt = parseTimestamp(summary?.latestQueryAt);
  const latestQueryAgeHours =
    latestQueryAt == null ? null : Math.max(0, roundedPercent((now - latestQueryAt) / 3_600_000));

  const messageNotables = messages
    .map((row) => {
      const reasons = messageRowReasons(row);
      return reasons.length > 0 ? toMessageNotableRow(row, reasons) : null;
    })
    .filter((row): row is MessageHistoryNotableRow => row != null);
  const traceNotables = traces
    .map((row) => {
      const reasons = traceRowReasons(row);
      return reasons.length > 0 ? toTraceNotableRow(row, reasons) : null;
    })
    .filter((row): row is MessageHistoryNotableRow => row != null);

  const zeroResultQueries = messageNotables.filter((row) =>
    row.reasons.includes('ZERO_RESULT_QUERIES'),
  ).length;
  const broadTopicQueries = messageNotables.filter((row) =>
    row.reasons.includes('BROAD_TOPIC_QUERIES'),
  ).length;
  const largeResultQueries = messageNotables.filter((row) =>
    row.reasons.includes('LARGE_RESULT_QUERIES'),
  ).length;
  const traceRowsWithoutNodes = traceNotables.filter((row) =>
    row.reasons.includes('TRACE_WITHOUT_NODES'),
  ).length;
  const traceRowsWithoutConsumers = traceNotables.filter((row) =>
    row.reasons.includes('TRACE_WITHOUT_CONSUMERS'),
  ).length;
  const unknownOperators = [...messageNotables, ...traceNotables].filter((row) =>
    row.reasons.includes('UNKNOWN_OPERATORS'),
  ).length;
  const customTraceTopics = [
    ...new Set(
      traces
        .map((row) => row.traceTopic?.trim())
        .filter((traceTopic): traceTopic is string => Boolean(traceTopic)),
    ),
  ].sort();

  const issues: MessageHistoryInsightIssue[] = [];
  if (totalQueries === 0) {
    pushIssue(issues, 'NO_HISTORY', 1);
  }
  if (
    totalQueries > 0 &&
    latestQueryAgeHours != null &&
    latestQueryAgeHours >= STALE_HISTORY_HOURS
  ) {
    pushIssue(issues, 'STALE_HISTORY', 1, { value: latestQueryAgeHours });
  }
  const traceRatio = percent(traceQueries, totalQueries);
  if (
    totalQueries >= TRACE_UNDERUSED_MIN_TOTAL &&
    traceRatio != null &&
    traceRatio < TRACE_UNDERUSED_RATIO
  ) {
    pushIssue(issues, 'TRACE_UNDERUSED', 1, { ratio: traceRatio });
  }
  pushIssue(issues, 'ZERO_RESULT_QUERIES', zeroResultQueries, {
    ratio: percent(zeroResultQueries, Math.max(messages.length, 1)) ?? undefined,
    topic: messageNotables.find((row) => row.reasons.includes('ZERO_RESULT_QUERIES'))?.topic,
  });
  pushIssue(issues, 'BROAD_TOPIC_QUERIES', broadTopicQueries, {
    topic: messageNotables.find((row) => row.reasons.includes('BROAD_TOPIC_QUERIES'))?.topic,
  });
  pushIssue(issues, 'LARGE_RESULT_QUERIES', largeResultQueries, {
    level: messageNotables.some(
      (row) =>
        row.reasons.includes('LARGE_RESULT_QUERIES') &&
        (row.resultCount ?? 0) >= LARGE_RESULT_CRITICAL,
    )
      ? 'critical'
      : 'warning',
    topic: messageNotables.find((row) => row.reasons.includes('LARGE_RESULT_QUERIES'))?.topic,
  });
  pushIssue(issues, 'TRACE_WITHOUT_NODES', traceRowsWithoutNodes, {
    topic: traceNotables.find((row) => row.reasons.includes('TRACE_WITHOUT_NODES'))?.topic,
  });
  pushIssue(issues, 'TRACE_WITHOUT_CONSUMERS', traceRowsWithoutConsumers, {
    topic: traceNotables.find((row) => row.reasons.includes('TRACE_WITHOUT_CONSUMERS'))?.topic,
  });
  if (customTraceTopics.length > 1) {
    pushIssue(issues, 'FRAGMENTED_TRACE_TOPICS', customTraceTopics.length, {
      traceTopic: customTraceTopics[0],
    });
  }
  pushIssue(issues, 'UNKNOWN_OPERATORS', unknownOperators, {
    operator: '-',
  });

  const sortedIssues = issues.sort(
    (left, right) =>
      levelWeight[right.level] - levelWeight[left.level] ||
      (right.count ?? 0) - (left.count ?? 0) ||
      left.code.localeCompare(right.code),
  );
  const score = scoreForIssues(sortedIssues);

  return {
    level: maxLevel(sortedIssues.map((issue) => issue.level)),
    score,
    stats: {
      totalQueries,
      messageQueries,
      traceQueries,
      messageQueryRatio: percent(messageQueries, totalQueries),
      traceQueryRatio: traceRatio,
      latestQueryAgeHours,
      visibleMessageRows: messages.length,
      visibleTraceRows: traces.length,
      zeroResultQueries,
      broadTopicQueries,
      largeResultQueries,
      traceRowsWithoutNodes,
      traceRowsWithoutConsumers,
      customTraceTopicCount: customTraceTopics.length,
      unknownOperators,
    },
    issues: sortedIssues,
    recommendations: recommendationsForIssues(sortedIssues),
    notableRows: [...messageNotables, ...traceNotables]
      .sort(compareNotableRows)
      .slice(0, MAX_NOTABLE_ROWS),
  };
}

export function messageHistoryInsightLevelRank(level: MessageHistoryInsightLevel): number {
  return levelWeight[level];
}
