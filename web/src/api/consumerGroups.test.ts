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
import { getConsumerGroup, listConsumerGroups, resetConsumerOffset } from './metadata';

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
  createdAt: '2026-07-17T00:00:00Z',
  updatedAt: '2026-07-17T00:00:00Z',
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

  it('unwraps detail records and sends numeric reset timestamps', async () => {
    const reset = { name: group.name, topic: 'orders', timestamp: 1784246400000 };
    mock.onGet('/groups/orders').reply(200, { code: 200, data: group });
    mock.onPost('/groups/reset-offset').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(reset);
      return [200, { code: 200, data: null }];
    });

    await expect(getConsumerGroup(group.name)).resolves.toEqual(group);
    await expect(resetConsumerOffset(reset)).resolves.toBeUndefined();
  });
});
