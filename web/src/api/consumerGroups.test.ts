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

import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import {
  getConsumerGroup,
  getConsumerGroupSettings,
  getConsumerProgress,
  getConsumerSubscriptions,
  listConsumerGroupPage,
  listConsumerGroups,
  deleteConsumerGroup,
  previewConsumerOffsetReset,
  resetConsumerOffset,
  updateConsumerGroupSettings,
} from './metadata';

const mock = new MockAdapter(client);
const group = {
  name: 'orders',
  namespace: 'default',
  clusterId: 'cluster-a',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 1,
  totalLag: 0,
  subscribedTopics: ['orders'],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  gmtCreate: '2026-07-17T00:00:00Z',
  gmtModified: '2026-07-17T00:00:00Z',
  delaySeconds: 0,
  instances: [],
};

describe('consumer groups API contract', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('uses the controller-supported list query fields', async () => {
    const params = { clusterId: 'cluster-a', search: 'orders' };
    mock.onGet('/groups').reply((config) => {
      expect(config.params).toEqual(params);
      return [200, { code: 200, data: [group] }];
    });

    await expect(listConsumerGroups(params)).resolves.toEqual([group]);
  });

  it('uses the paged inventory query fields supported by the backend', async () => {
    const params = {
      instanceId: 'instance-1',
      clusterId: 'cluster-a',
      search: 'orders',
      page: 2,
      pageSize: 10,
    };
    const page = { items: [group], total: 11, page: 2, size: 10 };
    mock.onGet('/groups/page').reply((config) => {
      expect(config.params).toEqual(params);
      return [200, { code: 200, data: page }];
    });

    await expect(listConsumerGroupPage(params)).resolves.toEqual(page);
  });

  it('encodes consumer group names and passes instance context for runtime queries', async () => {
    const groupName = '%RETRY%cg-order';
    const instanceId = 'instance-1';
    mock.onGet('/groups/%25RETRY%25cg-order').reply(200, { code: 200, data: group });
    mock.onGet('/groups/%25RETRY%25cg-order/progress').reply(200, { code: 200, data: [] });
    mock.onGet('/groups/%25RETRY%25cg-order/subscriptions').reply(200, { code: 200, data: [] });

    await expect(getConsumerGroup(groupName)).resolves.toEqual(group);
    await expect(getConsumerProgress(groupName, instanceId)).resolves.toEqual([]);
    await expect(getConsumerSubscriptions(groupName, instanceId)).resolves.toEqual([]);

    expect(mock.history.get[1].params).toEqual({ instanceId });
    expect(mock.history.get[2].params).toEqual({ instanceId });
  });

  it('unwraps detail records and sends numeric reset timestamps', async () => {
    const reset = {
      name: group.name,
      instanceId: 'instance-1',
      topic: 'orders',
      timestamp: 1784246400000,
    };
    mock.onGet('/groups/orders').reply(200, { code: 200, data: group });
    mock.onPost('/groups/reset-offset').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(reset);
      return [200, { code: 200, data: null }];
    });

    await expect(getConsumerGroup(group.name)).resolves.toEqual(group);
    await expect(resetConsumerOffset(reset)).resolves.toBeUndefined();
  });

  it('posts reset preview requests and unwraps queue impact data', async () => {
    const request = {
      name: group.name,
      instanceId: 'instance-1',
      topic: 'orders',
      timestamp: 1784246400000,
    };
    const preview = {
      instanceId: 'instance-1',
      groupName: group.name,
      topic: 'orders',
      timestamp: 1784246400000,
      complete: true,
      allowReset: true,
      queueCount: 1,
      warningCount: 1,
      rewindQueueCount: 1,
      fastForwardQueueCount: 0,
      currentTotalLag: 30,
      projectedTotalLag: 40,
      totalOffsetDelta: -10,
      warnings: ['1 queue(s) will move backward and may replay consumed messages'],
      queues: [
        {
          topic: 'orders',
          broker: 'broker-a',
          queueId: 0,
          minOffset: 0,
          maxOffset: 200,
          brokerOffset: 120,
          consumerOffset: 90,
          targetOffset: 80,
          currentLag: 30,
          projectedLag: 40,
          offsetDelta: -10,
          riskLevel: 'WARNING',
          message: 'Replays 10 message(s)',
        },
      ],
    };
    mock.onPost('/groups/reset-offset/preview').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(request);
      return [200, { code: 200, data: preview }];
    });

    await expect(previewConsumerOffsetReset(request)).resolves.toEqual(preview);
  });

  it('includes selected instance context when deleting a consumer group', async () => {
    mock.onPost('/groups/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ name: group.name, instanceId: 'instance-1' });
      return [200, { code: 200, data: null }];
    });

    await expect(deleteConsumerGroup(group.name, 'instance-1')).resolves.toBeUndefined();
  });

  it('gets and updates settings in the selected Apache instance', async () => {
    const settings = { groupName: group.name, retryQueueNums: 2, retryMaxTimes: 8 };
    mock
      .onGet(`/groups/${encodeURIComponent(group.name)}/settings`, {
        params: { instanceId: 'instance-1' },
      })
      .reply(200, { code: 200, data: settings });
    await expect(getConsumerGroupSettings(group.name, 'instance-1')).resolves.toEqual(settings);

    const payload = {
      instanceId: 'instance-1',
      name: group.name,
      retryQueueNums: 2,
      retryMaxTimes: 8,
    };
    mock.onPost('/groups/settings').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(payload);
      return [200, { code: 200, data: settings }];
    });
    await expect(updateConsumerGroupSettings(payload)).resolves.toEqual(settings);
  });
});
