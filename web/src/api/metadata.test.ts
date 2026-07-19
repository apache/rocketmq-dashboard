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
  createConsumerGroup,
  getConsumerGroup,
  resetConsumerOffset,
} from './metadata';

const mock = new MockAdapter(client);
const group = {
  name: 'cg-orders',
  namespace: 'trade',
  clusterId: 'cluster-a',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 1,
  totalLag: 0,
  subscribedTopics: ['orders'],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  createdAt: '2026-07-19T00:00:00Z',
};

describe('consumer group API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('returns a consumer group with its online instances', async () => {
    const detail = { ...group, instances: [{ clientId: 'client-1', clientAddr: '127.0.0.1', language: 'JAVA', version: '5.0' }] };
    mock.onGet(`/groups/${group.name}`).reply(200, { code: 200, data: detail });

    await expect(getConsumerGroup(group.name)).resolves.toEqual(detail);
  });

  it('returns the persisted consumer group after create', async () => {
    mock.onPost('/groups/create').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ name: group.name, namespace: group.namespace });
      return [200, { code: 200, data: group }];
    });

    await expect(createConsumerGroup({ name: group.name, namespace: group.namespace })).resolves.toEqual(group);
  });

  it('sends typed reset-offset payloads', async () => {
    const payload = { name: group.name, timestamp: 1_752_883_200_000, topic: 'orders' };
    mock.onPost('/groups/reset-offset').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(payload);
      return [200, { code: 200, data: null }];
    });

    await expect(resetConsumerOffset(payload)).resolves.toBeUndefined();
  });
});
