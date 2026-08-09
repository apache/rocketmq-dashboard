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
import {
  createConsumerGroup,
  getConsumerGroup,
  getConsumerProgress,
  getConsumerSubscriptions,
  listConsumerGroups,
} from './consumerService';

const { mode, metadataApi } = vi.hoisted(() => ({
  mode: { mock: true },
  metadataApi: { getConsumerGroup: vi.fn() },
}));

vi.mock('./dataMode', () => ({ isMockMode: () => mode.mock }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));
vi.mock('../api/metadata', () => metadataApi);

describe('consumer service mock data', () => {
  it('returns copied consumer group rows', async () => {
    const first = await listConsumerGroups({ search: 'cg-order-notify' });
    expect(first[0].name).toBe('cg-order-notify');

    first[0].name = 'mutated-group';
    first[0].subscribedTopics.push('mutated-topic');
    first[0].instances[0].topicLag['order-create'] = 999999;

    const second = await listConsumerGroups({ search: 'cg-order-notify' });
    expect(second[0].name).toBe('cg-order-notify');
    expect(second[0].subscribedTopics).not.toContain('mutated-topic');
    expect(second[0].instances[0].topicLag['order-create']).toBe(180);
    expect(second[0]).not.toBe(first[0]);
    expect(second[0].instances[0]).not.toBe(first[0].instances[0]);
  });

  it('returns copied consumer group details', async () => {
    const first = await getConsumerGroup('cg-order-notify');
    first.instances[0].subscribedTopics.push('mutated-topic');

    const second = await getConsumerGroup('cg-order-notify');
    expect(second.instances[0].subscribedTopics).not.toContain('mutated-topic');
    expect(second.instances[0]).not.toBe(first.instances[0]);
  });

  it('trims search text before filtering consumer group names', async () => {
    const groups = await listConsumerGroups({ search: '  CG-ORDER-NOTIFY  ' });

    expect(groups.map((group) => group.name)).toEqual(['cg-order-notify']);
  });

  it('ignores blank search text', async () => {
    const allGroups = await listConsumerGroups();
    const blankSearchGroups = await listConsumerGroups({ search: '   ' });

    expect(blankSearchGroups).toHaveLength(allGroups.length);
  });

  it('returns copied progress and subscription rows', async () => {
    const firstProgress = await getConsumerProgress('cg-order-notify');
    const firstSubscriptions = await getConsumerSubscriptions('cg-order-notify');
    firstProgress[0].broker = 'mutated-broker';
    firstSubscriptions[0].topic = 'mutated-topic';

    const secondProgress = await getConsumerProgress('cg-order-notify');
    const secondSubscriptions = await getConsumerSubscriptions('cg-order-notify');
    expect(secondProgress[0].broker).not.toBe('mutated-broker');
    expect(secondSubscriptions[0].topic).not.toBe('mutated-topic');
    expect(secondProgress[0]).not.toBe(firstProgress[0]);
    expect(secondSubscriptions[0]).not.toBe(firstSubscriptions[0]);
  });

  it('returns a copy after creating consumer groups', async () => {
    const created = await createConsumerGroup({
      name: 'cg-created-copy-test',
      subscribedTopics: ['created-topic'],
    });
    created.subscribedTopics.push('mutated-topic');

    const detail = await getConsumerGroup('cg-created-copy-test');
    expect(detail.subscribedTopics).toEqual(['created-topic']);
    expect(detail).not.toBe(created);
  });

  it('forwards the selected instance when loading consumer group details in API mode', async () => {
    mode.mock = false;
    const detail = {
      name: 'cg-orders',
      subscribedTopics: [],
      instances: [],
    };
    try {
      metadataApi.getConsumerGroup.mockResolvedValue(detail);

      await expect(getConsumerGroup('cg-orders', 'instance-a')).resolves.toEqual(detail);
      expect(metadataApi.getConsumerGroup).toHaveBeenCalledWith('cg-orders', 'instance-a');
    } finally {
      mode.mock = true;
    }
  });
});
