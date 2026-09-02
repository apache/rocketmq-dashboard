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
import type { ConsumerGroup, QueueProgress, SubscriptionEntry } from '../api/metadata';
import { analyzeConsumerGroupHealth } from './consumerGroupDiagnostics';

const group = (overrides: Partial<ConsumerGroup> = {}): ConsumerGroup => ({
  name: 'orders-cg',
  namespace: 'default',
  clusterId: 'cluster-a',
  instanceId: 'instance-a',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 1,
  totalLag: 12,
  subscribedTopics: ['orders'],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  gmtCreate: '2026-08-31T10:00:00Z',
  gmtModified: '2026-08-31T10:00:00Z',
  delaySeconds: 10,
  instances: [
    {
      clientId: 'orders-cg-0@10.0.0.1',
      protocol: 'GRPC',
      address: '10.0.0.1:49152',
      subscribedTopics: ['orders'],
      lastHeartbeat: '2026-08-31T12:00:00Z',
      topicLag: { orders: 12 },
    },
  ],
  ...overrides,
});

const subscription = (overrides: Partial<SubscriptionEntry> = {}): SubscriptionEntry => ({
  topic: 'orders',
  expression: '*',
  type: 'NORMAL',
  filterMode: '全量',
  consistency: '一致',
  ...overrides,
});

const queue = (overrides: Partial<QueueProgress> = {}): QueueProgress => ({
  topic: 'orders',
  broker: 'broker-a',
  queueId: 0,
  brokerOffset: 120,
  consumerOffset: 114,
  diffTotal: 6,
  ...overrides,
});

describe('consumer group diagnostics', () => {
  it('summarizes an active group with balanced queues as healthy', () => {
    const diagnostics = analyzeConsumerGroupHealth(
      group(),
      [subscription()],
      [queue(), queue({ broker: 'broker-b', queueId: 1 })],
      { now: '2026-08-31T12:01:00Z' },
    );

    expect(diagnostics.status).toBe('healthy');
    expect(diagnostics.summary).toMatchObject({
      healthScore: 100,
      onlineInstances: 1,
      subscribedTopicCount: 1,
      queueCount: 2,
      lagQueueCount: 2,
      totalKnownLag: 12,
      reportedLag: 12,
      staleClientCount: 0,
    });
    expect(diagnostics.issues).toEqual([]);
  });

  it('flags critical subscription, queue and runtime risks', () => {
    const diagnostics = analyzeConsumerGroupHealth(
      group({
        onlineInstances: 0,
        totalLag: 2_400,
        delaySeconds: 1_900,
        instances: [],
      }),
      [subscription({ consistency: '不一致', expression: 'tagA' })],
      [queue({ diffTotal: 10 }), queue({ broker: 'broker-b', queueId: 1, diffTotal: 1_100 })],
      { now: '2026-08-31T12:01:00Z' },
    );

    expect(diagnostics.status).toBe('critical');
    expect(diagnostics.summary.healthScore).toBeLessThan(50);
    expect(diagnostics.issues.map((item) => item.code)).toEqual(
      expect.arrayContaining([
        'SUBSCRIPTION_INCONSISTENT',
        'QUEUE_LAG_SKEW',
        'HIGH_GROUP_LAG',
        'NO_ACTIVE_CLIENTS_WITH_LAG',
        'HIGH_CONSUME_DELAY',
      ]),
    );
    expect(diagnostics.recommendations).toEqual(
      expect.arrayContaining([
        '先确认消费者进程、Proxy/Broker 网络连通性和客户端心跳是否恢复。',
        '检查热点 Queue 的分配、消费者线程池和单分区顺序消费阻塞情况。',
      ]),
    );
  });

  it('keeps warnings for unknown lag, unknown subscriptions and stale clients', () => {
    const diagnostics = analyzeConsumerGroupHealth(
      group({
        totalLag: -1,
        delaySeconds: 360,
        instances: [
          {
            clientId: 'orders-cg-0@10.0.0.1',
            protocol: 'GRPC',
            address: '10.0.0.1:49152',
            subscribedTopics: ['orders'],
            lastHeartbeat: '2026-08-31T11:50:00Z',
            topicLag: {},
          },
        ],
      }),
      [subscription({ consistency: 'unknown' })],
      [queue({ diffTotal: -1 })],
      { now: '2026-08-31T12:01:00Z' },
    );

    expect(diagnostics.status).toBe('warning');
    expect(diagnostics.summary).toMatchObject({
      reportedLag: 0,
      unknownQueueCount: 1,
      maxQueueLag: null,
      maxHeartbeatAgeSeconds: 660,
      staleClientCount: 1,
    });
    expect(diagnostics.issues.map((item) => item.code)).toEqual(
      expect.arrayContaining(['SUBSCRIPTION_UNKNOWN', 'UNKNOWN_QUEUE_LAG', 'STALE_HEARTBEAT']),
    );
  });
});
