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

import { describe, expect, it, vi } from 'vitest';
import type { ConsumerGroup, Topic } from '../api/metadata';
import {
  parseResourceBundle,
  previewResourcePlan,
  type ResourceBundle,
} from './resourcePlanService';

const topicServiceMocks = vi.hoisted(() => ({
  listTopics: vi.fn(),
}));
const consumerServiceMocks = vi.hoisted(() => ({
  listConsumerGroups: vi.fn(),
}));

vi.mock('./topicService', () => topicServiceMocks);
vi.mock('./consumerService', () => consumerServiceMocks);

const existingTopic: Topic = {
  name: 'order-create',
  namespace: 'trade',
  type: 'NORMAL',
  clusterId: 'rmq-cn-v5-prod-01',
  instanceId: 'instance-proxy-1',
  writeQueues: 16,
  readQueues: 16,
  perm: 'RW',
  messageCount: 0,
  tps: 0,
  consumerGroupCount: 1,
  remark: 'old',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const existingGroup: ConsumerGroup = {
  name: 'cg-order-notify',
  namespace: 'trade',
  clusterId: 'hz-prod',
  instanceId: 'instance-proxy-1',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 1,
  totalLag: 0,
  subscribedTopics: ['order-create'],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  delaySeconds: 0,
  instances: [],
};

describe('resource plan service', () => {
  it('parses JSON resource bundles and rejects malformed shapes', () => {
    const bundle = parseResourceBundle('{"topics":[{"name":"orders"}],"consumerGroups":[]}');
    expect(bundle).toEqual({ topics: [{ name: 'orders' }], consumerGroups: [] });

    expect(() => parseResourceBundle('{')).toThrow('Resource bundle must be valid JSON');
    expect(() => parseResourceBundle('[]')).toThrow('Resource bundle must be a JSON object');
    expect(() => parseResourceBundle('{"topics":{}}')).toThrow('topics must be an array');
  });

  it('builds a mock preview with create, update, conflict and invalid entries', async () => {
    topicServiceMocks.listTopics.mockResolvedValue([existingTopic]);
    consumerServiceMocks.listConsumerGroups.mockResolvedValue([existingGroup]);

    const bundle: ResourceBundle = {
      topics: [
        { name: 'order-create', namespace: 'trade', writeQueues: 32 },
        { name: 'new-topic', type: 'NORMAL', writeQueues: 8, readQueues: 8, perm: 'RW' },
        { name: 'new-topic', type: 'NORMAL' },
      ],
      consumerGroups: [
        { name: 'cg-order-notify', consumeType: 'BROADCASTING' },
        { name: 'cg-new', consumeType: 'CLUSTERING', retryMaxTimes: 16 },
      ],
    };

    const plan = await previewResourcePlan({ instanceId: 'instance-proxy-1', ...bundle });

    expect(plan.summary).toMatchObject({
      total: 5,
      creates: 2,
      updates: 1,
      conflicts: 1,
      invalids: 1,
      applicable: 3,
    });
    expect(plan.entries.map((entry) => entry.action)).toEqual([
      'UPDATE',
      'CREATE',
      'INVALID',
      'CONFLICT',
      'CREATE',
    ]);
    expect(plan.entries[0].changes).toEqual([
      { field: 'writeQueues', currentValue: '16', desiredValue: '32' },
    ]);
  });

  it('rejects numeric fields that are not non-negative integers at runtime', async () => {
    topicServiceMocks.listTopics.mockResolvedValue([]);
    consumerServiceMocks.listConsumerGroups.mockResolvedValue([]);
    const bundle = parseResourceBundle(`{
      "topics": [
        { "name": "string-queues", "writeQueues": "8" },
        { "name": "fractional-queues", "readQueues": 1.5 }
      ],
      "consumerGroups": [
        { "name": "negative-retries", "retryMaxTimes": -1 },
        { "name": "null-delay", "delaySeconds": null }
      ]
    }`);

    const plan = await previewResourcePlan({ instanceId: 'instance-proxy-1', ...bundle });

    expect(plan.summary).toMatchObject({
      total: 4,
      invalids: 4,
      applicable: 0,
    });
    expect(plan.entries).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ name: 'string-queues', action: 'INVALID' }),
        expect.objectContaining({ name: 'fractional-queues', action: 'INVALID' }),
        expect.objectContaining({ name: 'negative-retries', action: 'INVALID' }),
        expect.objectContaining({ name: 'null-delay', action: 'INVALID' }),
      ]),
    );
  });
});
