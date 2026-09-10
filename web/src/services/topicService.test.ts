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

import {
  createTopic,
  deleteTopic,
  getTopicConsumerPage,
  getTopicConsumers,
  getTopicRoutes,
  listAllTopics,
  listTopics,
  updateTopic,
} from './topicService';
import { topics as mockTopics } from '../mock/topics';

vi.mock('./dataMode', () => ({ isMockMode: () => true }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));

describe('topic service mock data', () => {
  afterEach(() => {
    for (let idx = mockTopics.length - 1; idx >= 0; idx -= 1) {
      if (mockTopics[idx].name.startsWith('instance-scope-test-')) mockTopics.splice(idx, 1);
    }
  });

  it('returns copied topic rows', async () => {
    const first = await listTopics({ search: 'order-create' });
    expect(first[0].name).toBe('order-create');

    first[0].name = 'mutated-topic';

    const second = await listTopics({ search: 'order-create' });
    expect(second[0].name).toBe('order-create');
    expect(second[0]).not.toBe(first[0]);
  });

  it('returns copied topic route rows', async () => {
    const first = await getTopicRoutes('order-create');
    expect(first[0].brokerName).toBe('broker-a-0');

    first[0].brokerName = 'mutated-broker';
    if (first[0].brokerAddrs) first[0].brokerAddrs['0'] = '127.0.0.1:10911';
    if (first[0].brokerIds) first[0].brokerIds.push(99);

    const second = await getTopicRoutes('order-create');
    expect(second[0].brokerName).toBe('broker-a-0');
    expect(second[0].brokerAddrs?.['0']).toBe('10.0.1.10:10911');
    expect(second[0].brokerIds).toEqual([0, 1]);
    expect(second[0]).not.toBe(first[0]);
  });

  it('returns copied topic consumer rows', async () => {
    const first = await getTopicConsumers('order-create');
    expect(first[0].group).toBe('GID_order_service');

    first[0].group = 'mutated-group';

    const second = await getTopicConsumers('order-create');
    expect(second[0].group).toBe('GID_order_service');
    expect(second[0]).not.toBe(first[0]);
  });

  it('paginates copied topic consumer rows', async () => {
    const page = await getTopicConsumerPage('order-create', undefined, 1, 1);

    expect(page).toMatchObject({ total: 4, page: 1, pageSize: 1 });
    expect(page.items[0].group).toBe('GID_order_service');
  });

  it('trims search text before filtering topic names', async () => {
    const topics = await listTopics({ search: '  ORDER-CREATE  ' });

    expect(topics.map((topic) => topic.name)).toEqual(['order-create']);
  });

  it('ignores blank search text', async () => {
    const allTopics = await listTopics();
    const blankSearchTopics = await listTopics({ search: '   ' });

    expect(blankSearchTopics).toHaveLength(allTopics.length);
  });

  it('filters mock topics by instance ID', async () => {
    const topics = await listTopics({ instanceId: 'instance-proxy-1' });
    const directTopics = await listTopics({ instanceId: 'instance-proxy-1', search: 'order' });
    const exportedTopics = await listAllTopics({
      instanceId: 'instance-proxy-1',
      search: 'order',
    });

    expect(topics).not.toHaveLength(0);
    expect(topics.every((topic) => topic.instanceId === 'instance-proxy-1')).toBe(true);
    expect(exportedTopics).toEqual(directTopics);
  });

  it('rejects duplicate topic creates in the same cluster', async () => {
    const existing = (await listTopics({ search: 'order-create' }))[0];
    const before = await listTopics({ clusterId: existing.clusterId });

    await expect(
      createTopic({
        name: existing.name,
        clusterId: existing.clusterId,
        namespace: existing.namespace,
        type: existing.type,
        writeQueues: existing.writeQueues,
        readQueues: existing.readQueues,
        perm: existing.perm,
      }),
    ).rejects.toThrow(`Topic already exists: ${existing.name}`);

    const after = await listTopics({ clusterId: existing.clusterId });
    expect(after).toEqual(before);
  });

  it('allows the same topic name and cluster in different mock instances', async () => {
    const topic = {
      name: 'instance-scope-test-create',
      clusterId: 'rmq-cn-v5-prod-01',
      namespace: 'trade',
      type: 'NORMAL',
      writeQueues: 4,
      readQueues: 4,
      perm: 'RW',
    };

    const first = await createTopic({ ...topic, instanceId: 'instance-proxy-1' });
    const second = await createTopic({ ...topic, instanceId: 'instance-proxy-2' });

    expect(first.instanceId).toBe('instance-proxy-1');
    expect(second.instanceId).toBe('instance-proxy-2');
    await expect(createTopic({ ...topic, instanceId: 'instance-proxy-1' })).rejects.toThrow(
      `Topic already exists: ${topic.name}`,
    );
  });

  it('updates only the matching mock topic instance when instance ID is supplied', async () => {
    const topic = {
      name: 'instance-scope-test-update',
      clusterId: 'rmq-cn-v5-prod-01',
      namespace: 'trade',
      type: 'NORMAL',
      writeQueues: 4,
      readQueues: 4,
      perm: 'RW',
      remark: 'original',
    };
    await createTopic({ ...topic, instanceId: 'instance-proxy-1' });
    await createTopic({ ...topic, instanceId: 'instance-proxy-2' });

    const updated = await updateTopic({
      name: topic.name,
      instanceId: 'instance-proxy-2',
      remark: 'updated second instance',
    });

    expect(updated.remark).toBe('updated second instance');
    const firstInstance = await listTopics({ instanceId: 'instance-proxy-1', search: topic.name });
    const secondInstance = await listTopics({ instanceId: 'instance-proxy-2', search: topic.name });
    expect(firstInstance).toHaveLength(1);
    expect(secondInstance).toHaveLength(1);
    expect(firstInstance[0].remark).toBe('original');
    expect(secondInstance[0].remark).toBe('updated second instance');
  });

  it('deletes only the matching mock topic instance when instance ID is supplied', async () => {
    const topic = {
      name: 'instance-scope-test-delete',
      clusterId: 'rmq-cn-v5-prod-01',
      namespace: 'trade',
      type: 'NORMAL',
      writeQueues: 4,
      readQueues: 4,
      perm: 'RW',
    };
    await createTopic({ ...topic, instanceId: 'instance-proxy-1' });
    await createTopic({ ...topic, instanceId: 'instance-proxy-2' });

    await deleteTopic(topic.name, 'instance-proxy-2');

    const firstInstance = await listTopics({ instanceId: 'instance-proxy-1', search: topic.name });
    const secondInstance = await listTopics({ instanceId: 'instance-proxy-2', search: topic.name });
    expect(firstInstance).toHaveLength(1);
    expect(secondInstance).toHaveLength(0);
  });
});
