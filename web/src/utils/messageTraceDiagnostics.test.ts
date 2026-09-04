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
import type { ConsumerStatus, TraceNode, TraceRecord } from '../api/message';
import { analyzeMessageTrace } from './messageTraceDiagnostics';

const node = (
  title: string,
  timestamp: string,
  costTime: number,
  status: TraceNode['status'] = 'finish',
): TraceNode => ({
  title,
  timestamp,
  costTime,
  status,
  description: `${title} detail`,
});

const delivery = (
  group: string,
  deliveryStatus = 'success',
  consumeTime = '2026-07-01T10:00:00.120Z',
  retryCount = 0,
): ConsumerStatus => ({ group, deliveryStatus, consumeTime, retryCount });

const baseTrace = (overrides: Partial<TraceRecord> = {}): TraceRecord => ({
  nodes: [
    node('Producer 发送', '2026-07-01T10:00:00.000Z', 4),
    node('Broker 存储', '2026-07-01T10:00:00.040Z', 8),
    node('Consumer 消费', '2026-07-01T10:00:00.120Z', 70),
  ],
  consumerStatus: [delivery('cg-orders')],
  ...overrides,
});

const issueCodes = (trace: TraceRecord) =>
  analyzeMessageTrace(trace).issues.map((issue) => issue.code);

describe('message trace diagnostics', () => {
  it('summarizes a complete trace as healthy', () => {
    const diagnostics = analyzeMessageTrace(baseTrace());

    expect(diagnostics.status).toBe('healthy');
    expect(diagnostics.score).toBe(100);
    expect(diagnostics.summary).toMatchObject({
      nodeCount: 3,
      consumerGroupCount: 1,
      totalNodeCostMs: 82,
      endToEndLatencyMs: 120,
      successfulConsumerRate: 100,
    });
    expect(diagnostics.summary.slowestNode).toMatchObject({
      title: 'Consumer 消费',
      valueMs: 70,
    });
    expect(diagnostics.issues).toEqual([]);
    expect(diagnostics.recommendations).toEqual([]);
  });

  it('returns guidance when trace nodes are missing', () => {
    const diagnostics = analyzeMessageTrace({ nodes: [], consumerStatus: [] });

    expect(diagnostics.status).toBe('warning');
    expect(diagnostics.summary.endToEndLatencyMs).toBeNull();
    expect(diagnostics.issues).toEqual([
      expect.objectContaining({ code: 'NO_TRACE_NODES', severity: 'warning' }),
    ]);
    expect(diagnostics.recommendations).toContain(
      '确认消息轨迹已开启，并检查是否需要指定自定义轨迹 Topic。',
    );
  });

  it('flags failed and waiting trace phases with latency hotspots', () => {
    const diagnostics = analyzeMessageTrace(
      baseTrace({
        nodes: [
          node('Producer 发送', '2026-07-01T10:00:00.000Z', 4),
          node('Broker 存储', '2026-07-01T10:00:02.200Z', 620),
          node('Consumer 消费', '2026-07-01T10:00:02.240Z', 9000, 'error'),
          node('通知下游', '2026-07-01T10:00:02.260Z', 30, 'wait'),
        ],
      }),
    );

    expect(diagnostics.status).toBe('critical');
    expect(diagnostics.summary).toMatchObject({
      failedNodeCount: 1,
      waitingNodeCount: 1,
      endToEndLatencyMs: 2260,
    });
    expect(diagnostics.summary.slowestNode).toMatchObject({
      title: 'Consumer 消费',
      valueMs: 9000,
    });
    expect(diagnostics.summary.slowestGap).toMatchObject({
      title: 'Broker 存储',
      valueMs: 2200,
    });
    expect(diagnostics.issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining([
        'FAILED_TRACE_NODE',
        'WAITING_TRACE_NODE',
        'SLOW_TRACE_NODE',
        'SLOW_TRACE_GAP',
      ]),
    );
  });

  it('flags consumer delivery failures, pending statuses, unknown states and retries', () => {
    const diagnostics = analyzeMessageTrace(
      baseTrace({
        consumerStatus: [
          delivery('cg-orders', 'success', '2026-07-01T10:00:00.120Z', 1),
          delivery('cg-billing', 'failed', '2026-07-01T10:00:05.000Z', 3),
          delivery('cg-notification', 'pending', '-', 0),
          delivery('cg-search', 'paused', 'bad timestamp', 0),
        ],
      }),
    );

    expect(diagnostics.status).toBe('critical');
    expect(diagnostics.summary).toMatchObject({
      consumerGroupCount: 4,
      failedConsumerCount: 1,
      pendingConsumerCount: 1,
      retriedConsumerCount: 2,
      successfulConsumerRate: 25,
    });
    expect(diagnostics.deliveries).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          group: 'cg-billing',
          severity: 'critical',
          retryCount: 3,
          latencyFromTraceStartMs: 5000,
        }),
        expect.objectContaining({ group: 'cg-search', consumeTimeMs: null }),
      ]),
    );
    expect(diagnostics.issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining([
        'FAILED_CONSUMER_DELIVERY',
        'PENDING_CONSUMER_DELIVERY',
        'UNKNOWN_CONSUMER_DELIVERY',
        'RETRIED_CONSUMER_DELIVERY',
        'INVALID_CONSUME_TIME',
      ]),
    );
  });

  it('detects invalid timing, timestamp regressions and slow end-to-end traces', () => {
    expect(
      issueCodes(
        baseTrace({
          nodes: [
            node('Producer 发送', '2026-07-01T10:00:03.000Z', 4),
            node('Broker 存储', 'bad timestamp', -1),
            node('Consumer 消费', '2026-07-01T10:00:01.500Z', 20),
          ],
        }),
      ),
    ).toEqual(
      expect.arrayContaining([
        'INVALID_TRACE_TIMESTAMP',
        'INVALID_TRACE_COST',
        'TRACE_TIMESTAMP_REGRESSION',
      ]),
    );

    const slowTrace = analyzeMessageTrace({
      nodes: [
        node('Producer 发送', '2026-07-01T10:00:00.000Z', 5),
        node('Broker 存储', '2026-07-01T10:00:45.000Z', 12),
      ],
      consumerStatus: [],
    });

    expect(slowTrace.status).toBe('critical');
    expect(slowTrace.summary).toMatchObject({
      consumerGroupCount: 0,
      endToEndLatencyMs: 45000,
      successfulConsumerRate: null,
    });
    expect(slowTrace.issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining(['MISSING_CONSUMER_STATUS', 'SLOW_END_TO_END_TRACE']),
    );
  });
});
