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
import type { BrokerRoute, ConsumerGroupInfo, Topic } from '../api/metadata';
import {
  analyzeTopicDeleteImpact,
  isSystemTopicName,
  summarizeTopicDeleteImpacts,
} from './topicDeleteImpact';

const baseTopic = (overrides: Partial<Topic> = {}): Topic => ({
  name: 'orders-topic',
  namespace: 'default',
  type: 'NORMAL',
  clusterId: 'rmq-cluster',
  instanceId: 'instance-a',
  writeQueues: 8,
  readQueues: 8,
  perm: 'RW',
  messageCount: 0,
  tps: 0,
  consumerGroupCount: 0,
  remark: '',
  gmtCreate: '2026-01-01T00:00:00Z',
  gmtModified: '2026-01-01T00:00:00Z',
  ...overrides,
});

const route = (overrides: Partial<BrokerRoute> = {}): BrokerRoute => ({
  brokerName: 'broker-a',
  brokerAddr: '10.0.0.1:10911',
  masterAddr: '10.0.0.1:10911',
  brokerAddrs: { '0': '10.0.0.1:10911' },
  brokerIds: [0],
  replicaCount: 0,
  writeQueues: 8,
  readQueues: 8,
  perm: 'RW',
  readable: true,
  writable: true,
  topicSysFlag: 0,
  ...overrides,
});

const consumer = (overrides: Partial<ConsumerGroupInfo> = {}): ConsumerGroupInfo => ({
  group: 'GID_orders',
  consumeType: 'CONSUME_ACTIVELY',
  messageModel: '集群消费',
  consumeTps: 0,
  diffTotal: 0,
  ...overrides,
});

describe('topic delete impact diagnostics', () => {
  it('recognizes RocketMQ internal topic names as protected', () => {
    expect(isSystemTopicName('%RETRY%GID_orders')).toBe(true);
    expect(isSystemTopicName('%DLQ%GID_orders')).toBe(true);
    expect(isSystemTopicName('SCHEDULE_TOPIC_XXXX')).toBe(true);
    expect(isSystemTopicName('orders-topic')).toBe(false);
  });

  it('marks topics with consumers, active traffic, and backlog as high risk', () => {
    const assessment = analyzeTopicDeleteImpact({
      topic: baseTopic({ consumerGroupCount: 2, messageCount: 1200, tps: 12 }),
      consumers: [
        consumer({ consumeTps: 8, diffTotal: 600 }),
        consumer({ group: 'GID_billing', consumeTps: 0, diffTotal: 0 }),
      ],
      consumerTotal: 2,
      routes: [route()],
    });

    expect(assessment.riskLevel).toBe('high');
    expect(assessment.canDelete).toBe(true);
    expect(assessment.stats.consumerGroups).toBe(2);
    expect(assessment.stats.activeConsumerGroups).toBe(1);
    expect(assessment.stats.knownTotalLag).toBe(600);
    expect(assessment.issues.map((item) => item.code)).toEqual(
      expect.arrayContaining([
        'CONSUMER_GROUPS_ATTACHED',
        'ACTIVE_CONSUMPTION',
        'BACKLOG_REMAINS',
        'RECENT_TRAFFIC',
        'RETAINED_MESSAGES',
        'WRITABLE_ROUTE',
      ]),
    );
  });

  it('keeps metadata-only topics as low risk when no dependency is visible', () => {
    const assessment = analyzeTopicDeleteImpact({
      topic: baseTopic(),
      consumers: [],
      consumerTotal: 0,
      routes: [],
    });

    expect(assessment.riskLevel).toBe('low');
    expect(assessment.stats.routeCount).toBe(0);
    expect(assessment.issues.map((item) => item.code)).toEqual(['NO_ROUTE']);
  });

  it('reports unknown risk when dependency lookups fail', () => {
    const assessment = analyzeTopicDeleteImpact({
      topic: baseTopic(),
      consumerLookupFailed: true,
      routeLookupFailed: true,
    });

    expect(assessment.riskLevel).toBe('unknown');
    expect(assessment.stats.consumerLookupFailed).toBe(true);
    expect(assessment.stats.routeLookupFailed).toBe(true);
    expect(assessment.issues.map((item) => item.code)).toEqual([
      'CONSUMER_LOOKUP_FAILED',
      'ROUTE_LOOKUP_FAILED',
    ]);
  });

  it('blocks system topic deletion and includes it in the summary', () => {
    const systemTopic = analyzeTopicDeleteImpact({
      topic: baseTopic({ name: '%RETRY%GID_orders' }),
      consumers: [],
      routes: [],
    });
    const normalTopic = analyzeTopicDeleteImpact({
      topic: baseTopic({ name: 'orders-topic' }),
      consumers: [],
      routes: [],
    });
    const summary = summarizeTopicDeleteImpacts([systemTopic, normalTopic]);

    expect(systemTopic.canDelete).toBe(false);
    expect(summary.canDelete).toBe(false);
    expect(summary.riskLevel).toBe('high');
    expect(summary.blockedTopics).toEqual(['%RETRY%GID_orders']);
  });
});
