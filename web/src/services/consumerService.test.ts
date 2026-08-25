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
  getConsumerStack,
  getConsumerSubscriptions,
  listAllConsumerGroups,
  listConsumerGroupPage,
  listConsumerGroups,
} from './consumerService';

const { mode, metadataApi } = vi.hoisted(() => ({
  mode: { mock: true },
  metadataApi: {
    getConsumerGroup: vi.fn(),
    listConsumerGroupPage: vi.fn(),
    listConsumerGroups: vi.fn(),
  },
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

  it('returns paged mock consumer groups with the filtered total', async () => {
    const page = await listConsumerGroupPage({
      search: 'cg-order-notify',
      page: 1,
      pageSize: 1,
    });

    expect(page.items.map((group) => group.name)).toEqual(['cg-order-notify']);
    expect(page.total).toBe(1);
    expect(page.page).toBe(1);
    expect(page.size).toBe(1);
  });

  it('returns an empty page when the one-based offset starts past the filtered total', async () => {
    const page = await listConsumerGroupPage({
      search: 'cg-order-notify',
      page: 2,
      pageSize: 1,
    });

    expect(page.items).toEqual([]);
    expect(page.total).toBe(1);
    expect(page.page).toBe(2);
    expect(page.size).toBe(1);
  });

  it('loads every API consumer group page matching the export filters', async () => {
    mode.mock = false;
    const firstGroup = { name: 'cg-a', subscribedTopics: null, instances: null };
    const secondGroup = { name: 'cg-b', subscribedTopics: ['topic-b'], instances: [] };
    metadataApi.listConsumerGroupPage
      .mockResolvedValueOnce({
        items: [firstGroup],
        total: 2,
        page: 1,
        size: 100,
      })
      .mockResolvedValueOnce({
        items: [secondGroup],
        total: 2,
        page: 2,
        size: 100,
      });
    try {
      const groups = await listAllConsumerGroups({
        instanceId: 'instance-1',
        search: 'cg',
      });

      expect(metadataApi.listConsumerGroupPage).toHaveBeenNthCalledWith(1, {
        instanceId: 'instance-1',
        search: 'cg',
        page: 1,
        pageSize: 100,
      });
      expect(metadataApi.listConsumerGroupPage).toHaveBeenNthCalledWith(2, {
        instanceId: 'instance-1',
        search: 'cg',
        page: 2,
        pageSize: 100,
      });
      expect(groups).toEqual([
        { name: 'cg-a', subscribedTopics: [], instances: [] },
        { name: 'cg-b', subscribedTopics: ['topic-b'], instances: [] },
      ]);
    } finally {
      mode.mock = true;
    }
  });

  it('stops API consumer group export when pagination exceeds the safety limit', async () => {
    mode.mock = false;
    metadataApi.listConsumerGroupPage.mockReset();
    metadataApi.listConsumerGroupPage.mockResolvedValue({
      items: [{ name: 'cg-a', subscribedTopics: null, instances: null }],
      total: Number.MAX_SAFE_INTEGER,
      page: 1,
      size: 100,
    });
    try {
      await expect(listAllConsumerGroups()).rejects.toThrow(
        'Consumer group export exceeded 100 pages',
      );
      expect(metadataApi.listConsumerGroupPage).toHaveBeenCalledTimes(100);
      expect(metadataApi.listConsumerGroupPage).toHaveBeenLastCalledWith({
        page: 100,
        pageSize: 100,
      });
    } finally {
      mode.mock = true;
    }
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

  it('returns an empty mock consumer stack trace', async () => {
    const stack = await getConsumerStack('cg-order-notify', 'client-1');

    expect(stack.groupName).toBe('cg-order-notify');
    expect(stack.clientId).toBe('client-1');
    expect(stack.threadCount).toBe(0);
    expect(stack.threads).toEqual([]);
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

      await expect(getConsumerGroup('cg-orders', 'instance-1')).resolves.toEqual(detail);
      expect(metadataApi.getConsumerGroup).toHaveBeenCalledWith('cg-orders', 'instance-1');
    } finally {
      mode.mock = true;
    }
  });

  it('normalizes null subscribedTopics and instances from the backend', async () => {
    mode.mock = false;
    const rawGroup = { name: 'cg-nulls', subscribedTopics: null, instances: null };
    metadataApi.listConsumerGroups.mockResolvedValue([rawGroup]);
    metadataApi.getConsumerGroup.mockResolvedValue(rawGroup);
    try {
      const groups = await listConsumerGroups();
      expect(groups[0].subscribedTopics).toEqual([]);
      expect(groups[0].instances).toEqual([]);
      const detail = await getConsumerGroup('cg-nulls');
      expect(detail.subscribedTopics).toEqual([]);
      expect(detail.instances).toEqual([]);
    } finally {
      mode.mock = true;
    }
  });
});
