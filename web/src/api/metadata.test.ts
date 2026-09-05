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
  createTopic,
  deleteTopic,
  exportConsumerGroups,
  getConsumerGroup,
  getConsumerGroupSettings,
  getConsumerProgress,
  getConsumerStack,
  getConsumerSubscriptions,
  getTopicConsumerPage,
  getTopicConsumers,
  getTopicRoutes,
  importConsumerGroups,
  listTopics,
  previewConsumerOffsetReset,
  refreshConsumerGroup,
  resetConsumerOffset,
  sendTopicMessage,
  exportTopics,
  importTopics,
} from './metadata';

const mock = new MockAdapter(client);

describe('topic metadata API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('uses the topic query fields supported by the backend', async () => {
    const params = { clusterId: 'cluster-a', type: 'NORMAL', search: 'orders' };
    mock.onGet('/topics').reply((config) => {
      expect(config.params).toEqual(params);
      return [200, { code: 200, data: [] }];
    });

    await expect(listTopics(params)).resolves.toEqual([]);
  });

  it('encodes topic names used in path segments', async () => {
    const topicName = '%DLQ%cg-order';
    mock
      .onGet('/topics/%25DLQ%25cg-order/routes', { params: { instanceId: 'instance-1' } })
      .reply(200, { code: 200, data: [] });
    mock
      .onGet('/topics/%25DLQ%25cg-order/consumers', { params: { instanceId: 'instance-1' } })
      .reply(200, { code: 200, data: [] });
    mock
      .onGet('/topics/%25DLQ%25cg-order/consumers/page', {
        params: { instanceId: 'instance-1', page: 2, pageSize: 20 },
      })
      .reply(200, {
        code: 200,
        data: { items: [], total: 21, page: 2, pageSize: 20 },
      });

    await expect(getTopicRoutes(topicName, 'instance-1')).resolves.toEqual([]);
    await expect(getTopicConsumers(topicName, 'instance-1')).resolves.toEqual([]);
    await expect(getTopicConsumerPage(topicName, 'instance-1', 2, 20)).resolves.toEqual({
      items: [],
      total: 21,
      page: 2,
      pageSize: 20,
    });
  });

  it('encodes consumer stack route parameters and passes instanceId', async () => {
    const stack = {
      groupName: 'cg/orders',
      clientId: 'client/10.0.0.1',
      capturedAt: '2026-07-23T00:00:00Z',
      threadCount: 0,
      threads: [],
    };
    mock
      .onGet('/groups/cg%2Forders/instances/client%2F10.0.0.1/stack', {
        params: { instanceId: 'instance-1' },
      })
      .reply(200, { code: 200, data: stack });

    await expect(getConsumerStack('cg/orders', 'client/10.0.0.1', 'instance-1')).resolves.toEqual(
      stack,
    );
  });

  it('persists topic creation, deletion, and sending through API endpoints', async () => {
    const topic = {
      name: 'orders',
      namespace: 'default',
      type: 'NORMAL',
      clusterId: 'cluster-a',
      writeQueues: 8,
      readQueues: 8,
      perm: 'RW',
      messageCount: 0,
      tps: 0,
      consumerGroupCount: 0,
      remark: '',
      gmtCreate: '2026-07-17T00:00:00Z',
      gmtModified: '2026-07-17T00:00:00Z',
    };
    mock.onPost('/topics/create').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({
        name: topic.name,
        writeQueues: 8,
        readQueues: 8,
      });
      return [200, { code: 200, data: topic }];
    });
    mock.onPost('/topics/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ name: topic.name, instanceId: 'instance-1' });
      return [200, { code: 200, data: null }];
    });
    mock.onPost('/topics/send').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({
        topic: topic.name,
        instanceId: 'instance-1',
        body: '{"id":1}',
      });
      return [200, { code: 200, data: { msgId: 'msg-1', sendTime: 1, offsetMsgId: 'offset-1' } }];
    });

    await expect(createTopic(topic)).resolves.toEqual(topic);
    await expect(deleteTopic(topic.name, 'instance-1')).resolves.toBeUndefined();
    await expect(
      sendTopicMessage({ topic: topic.name, instanceId: 'instance-1', body: '{"id":1}' }),
    ).resolves.toMatchObject({ msgId: 'msg-1' });
  });

  it('passes consumer group import and export contracts through API endpoints', async () => {
    mock.onGet('/groups/export').reply((config) => {
      expect(config.params).toEqual({
        instanceId: 'instance-1',
        search: 'orders',
        subscriptionMode: 'Pop',
        names: 'cg-a,cg-b',
      });
      return [200, { code: 200, data: '"Name"\n"cg-a"' }];
    });
    mock.onPost('/groups/import').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        instanceId: 'instance-1',
        groups: [
          {
            name: 'cg-a',
            subscriptionMode: 'Push',
            consumeType: 'CLUSTERING',
            retryMaxTimes: 16,
          },
        ],
      });
      return [
        200,
        {
          code: 200,
          data: {
            imported: 1,
            failed: 0,
            groups: [],
            failures: [],
          },
        },
      ];
    });

    await expect(
      exportConsumerGroups({
        instanceId: 'instance-1',
        search: 'orders',
        subscriptionMode: 'Pop',
        names: ['cg-a', 'cg-b'],
      }),
    ).resolves.toBe('"Name"\n"cg-a"');
    await expect(
      importConsumerGroups({
        instanceId: 'instance-1',
        groups: [
          {
            name: 'cg-a',
            subscriptionMode: 'Push',
            consumeType: 'CLUSTERING',
            retryMaxTimes: 16,
          },
        ],
      }),
    ).resolves.toMatchObject({ imported: 1, failed: 0 });
  });

  it('passes topic import and export contracts through API endpoints', async () => {
    mock.onGet('/topics/export').reply((config) => {
      expect(config.params).toEqual({
        instanceId: 'instance-1',
        type: 'FIFO',
        search: 'orders',
        names: 'topic-a,topic-b',
      });
      return [200, { code: 200, data: '"Name"\n"topic-a"' }];
    });
    mock.onPost('/topics/import').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        instanceId: 'instance-1',
        topics: [
          {
            name: 'topic-a',
            type: 'NORMAL',
            writeQueues: 8,
            readQueues: 8,
            perm: 'RW',
          },
        ],
      });
      return [
        200,
        {
          code: 200,
          data: {
            imported: 1,
            failed: 0,
            topics: [],
            failures: [],
          },
        },
      ];
    });

    await expect(
      exportTopics({
        instanceId: 'instance-1',
        type: 'FIFO',
        search: 'orders',
        names: ['topic-a', 'topic-b'],
      }),
    ).resolves.toBe('"Name"\n"topic-a"');
    await expect(
      importTopics({
        instanceId: 'instance-1',
        topics: [
          {
            name: 'topic-a',
            type: 'NORMAL',
            writeQueues: 8,
            readQueues: 8,
            perm: 'RW',
          },
        ],
      }),
    ).resolves.toMatchObject({ imported: 1, failed: 0 });
  });

  it('loads consumer group details, progress, subscriptions and settings', async () => {
    const group = { groupName: 'orders', clusterId: 'cluster-a' };
    const progress = [{ topic: 'orders', diffTotal: 12 }];
    const subscriptions = [{ topic: 'orders', expression: '*' }];
    const settings = { groupName: 'orders', retryMaxTimes: 16 };
    mock.onGet('/groups/orders').reply(200, { code: 200, data: group });
    mock.onGet('/groups/orders/progress').reply(200, { code: 200, data: progress });
    mock.onGet('/groups/orders/subscriptions').reply(200, { code: 200, data: subscriptions });
    mock.onGet('/groups/orders/settings', { params: { instanceId: 'instance-1' } }).reply(200, {
      code: 200,
      data: settings,
    });
    mock.onGet('/groups/orders/refresh').reply(200, { code: 200, data: group });

    await expect(getConsumerGroup('orders', 'instance-1')).resolves.toEqual(group);
    await expect(getConsumerProgress('orders', 'instance-1')).resolves.toEqual(progress);
    await expect(getConsumerSubscriptions('orders', 'instance-1')).resolves.toEqual(
      subscriptions,
    );
    await expect(getConsumerGroupSettings('orders', 'instance-1')).resolves.toEqual(settings);
    await expect(refreshConsumerGroup('orders', 'instance-1')).resolves.toEqual(group);
  });

  it('previews and applies consumer offset resets', async () => {
    const request = { name: 'orders', instanceId: 'instance-1', timestamp: 1784107658, topic: 'orders' };
    const preview = { groupName: 'orders', complete: true, allowReset: true, queueCount: 8 };
    mock.onPost('/groups/reset-offset/preview').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(request);
      return [200, { code: 200, data: preview }];
    });
    mock.onPost('/groups/reset-offset').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(request);
      return [200, { code: 200, data: null }];
    });

    await expect(previewConsumerOffsetReset(request)).resolves.toEqual(preview);
    await expect(resetConsumerOffset(request)).resolves.toBeUndefined();
  });
});
