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

import { describe, expect, it } from 'vitest';
import type {
  MessageQueryHistory,
  QueryHistorySummary,
  TraceQueryHistory,
} from '../api/messageHistory';
import {
  buildMessageHistoryInsights,
  messageHistoryInsightLevelRank,
} from './messageHistoryInsights';

const NOW = Date.UTC(2026, 8, 10, 10, 0, 0);

const summary = (overrides: Partial<QueryHistorySummary> = {}): QueryHistorySummary => ({
  messageQueries: 20,
  traceQueries: 2,
  latestQueryAt: new Date(NOW - 30 * 60_000).toISOString(),
  ...overrides,
});

const messageRow = (overrides: Partial<MessageQueryHistory> = {}): MessageQueryHistory => ({
  id: 1,
  queryType: 'KEY',
  topic: 'orders',
  messageKey: 'order-1',
  resultCount: 3,
  queriedBy: 'alice',
  queriedAt: new Date(NOW - 10 * 60_000).toISOString(),
  ...overrides,
});

const traceRow = (overrides: Partial<TraceQueryHistory> = {}): TraceQueryHistory => ({
  id: 11,
  msgId: 'msg-1',
  topic: 'orders',
  traceTopic: 'RMQ_SYS_TRACE_TOPIC',
  nodeCount: 4,
  consumerCount: 2,
  queriedBy: 'alice',
  queriedAt: new Date(NOW - 5 * 60_000).toISOString(),
  ...overrides,
});

describe('message history insights', () => {
  it('returns a healthy result for recent balanced history rows', () => {
    const insights = buildMessageHistoryInsights(
      summary({ messageQueries: 6, traceQueries: 4 }),
      [messageRow()],
      [traceRow()],
      NOW,
    );

    expect(insights.level).toBe('healthy');
    expect(insights.score).toBe(100);
    expect(insights.stats.totalQueries).toBe(10);
    expect(insights.stats.messageQueryRatio).toBe(60);
    expect(insights.stats.traceQueryRatio).toBe(40);
    expect(insights.issues).toEqual([]);
    expect(insights.recommendations).toEqual([]);
    expect(insights.notableRows).toEqual([]);
  });

  it('flags zero-result, broad, and large message searches', () => {
    const insights = buildMessageHistoryInsights(
      summary({ messageQueries: 30, traceQueries: 0 }),
      [
        messageRow({
          id: 2,
          queryType: 'TOPIC',
          topic: 'orders',
          tag: '',
          messageKey: '',
          resultCount: 0,
          startTime: NOW - 48 * 3_600_000,
          endTime: NOW,
        }),
        messageRow({
          id: 3,
          queryType: 'TOPIC',
          topic: 'audit',
          messageKey: '',
          resultCount: 2_500,
          startTime: NOW - 72 * 3_600_000,
          endTime: NOW,
          queriedBy: '',
        }),
      ],
      [],
      NOW,
    );

    expect(insights.level).toBe('critical');
    expect(insights.score).toBeLessThan(70);
    expect(insights.stats.zeroResultQueries).toBe(1);
    expect(insights.stats.broadTopicQueries).toBe(2);
    expect(insights.stats.largeResultQueries).toBe(1);
    expect(insights.issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining([
        'ZERO_RESULT_QUERIES',
        'BROAD_TOPIC_QUERIES',
        'LARGE_RESULT_QUERIES',
        'TRACE_UNDERUSED',
        'UNKNOWN_OPERATORS',
      ]),
    );
    expect(insights.recommendations).toEqual(
      expect.arrayContaining(['NARROW_QUERY_FILTERS', 'REPLAY_ZERO_RESULTS']),
    );
    expect(insights.notableRows[0]).toEqual(
      expect.objectContaining({
        kind: 'message',
        level: 'critical',
        topic: 'audit',
        resultCount: 2500,
      }),
    );
  });

  it('flags trace rows without nodes, consumers, or a common trace topic', () => {
    const insights = buildMessageHistoryInsights(
      summary({ messageQueries: 5, traceQueries: 5 }),
      [],
      [
        traceRow({
          id: 4,
          msgId: 'msg-a',
          traceTopic: 'TRACE_A',
          nodeCount: 0,
          consumerCount: 0,
        }),
        traceRow({
          id: 5,
          msgId: 'msg-b',
          traceTopic: 'TRACE_B',
          nodeCount: 1,
          consumerCount: 0,
          queriedBy: '',
        }),
      ],
      NOW,
    );

    expect(insights.level).toBe('warning');
    expect(insights.stats.traceRowsWithoutNodes).toBe(1);
    expect(insights.stats.traceRowsWithoutConsumers).toBe(2);
    expect(insights.stats.customTraceTopicCount).toBe(2);
    expect(insights.issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining([
        'TRACE_WITHOUT_NODES',
        'TRACE_WITHOUT_CONSUMERS',
        'FRAGMENTED_TRACE_TOPICS',
        'UNKNOWN_OPERATORS',
      ]),
    );
    expect(insights.recommendations).toEqual(
      expect.arrayContaining(['CHECK_TRACE_COLLECTION', 'STANDARDIZE_TRACE_TOPIC']),
    );
  });

  it('reports stale or empty query history from the summary', () => {
    const stale = buildMessageHistoryInsights(
      summary({
        messageQueries: 1,
        traceQueries: 0,
        latestQueryAt: new Date(NOW - 96 * 3_600_000).toISOString(),
      }),
      [],
      [],
      NOW,
    );
    const empty = buildMessageHistoryInsights(
      summary({ messageQueries: 0, traceQueries: 0, latestQueryAt: undefined }),
      [],
      [],
      NOW,
    );

    expect(stale.stats.latestQueryAgeHours).toBe(96);
    expect(stale.issues.map((issue) => issue.code)).toContain('STALE_HISTORY');
    expect(empty.stats.totalQueries).toBe(0);
    expect(empty.issues.map((issue) => issue.code)).toContain('NO_HISTORY');
    expect(empty.recommendations).toContain('KEEP_RECENT_BASELINE');
  });

  it('normalizes invalid counters and exposes stable severity ranking', () => {
    const insights = buildMessageHistoryInsights(
      summary({
        messageQueries: Number.NaN,
        traceQueries: -1,
        latestQueryAt: 'not-a-date',
      }),
      [messageRow({ resultCount: Number.NaN })],
      [traceRow({ nodeCount: -1, consumerCount: Number.NaN })],
      NOW,
    );

    expect(insights.stats.totalQueries).toBe(0);
    expect(insights.stats.latestQueryAgeHours).toBeNull();
    expect(insights.stats.zeroResultQueries).toBe(1);
    expect(insights.stats.traceRowsWithoutNodes).toBe(1);
    expect(messageHistoryInsightLevelRank('healthy')).toBeLessThan(
      messageHistoryInsightLevelRank('warning'),
    );
    expect(messageHistoryInsightLevelRank('critical')).toBeGreaterThan(
      messageHistoryInsightLevelRank('notice'),
    );
  });
});
