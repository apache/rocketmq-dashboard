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
import { createTopic, deleteTopic, listTopics, sendTopicMessage } from './metadata';

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
      createdAt: '2026-07-17T00:00:00Z',
      updatedAt: '2026-07-17T00:00:00Z',
    };
    mock.onPost('/topics/create').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ name: topic.name, writeQueues: 8, readQueues: 8 });
      return [200, { code: 200, data: topic }];
    });
    mock.onPost('/topics/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ name: topic.name });
      return [200, { code: 200, data: null }];
    });
    mock.onPost('/topics/send').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ topic: topic.name, body: '{"id":1}' });
      return [200, { code: 200, data: { msgId: 'msg-1', sendTime: 1, offsetMsgId: 'offset-1' } }];
    });

    await expect(createTopic(topic)).resolves.toEqual(topic);
    await expect(deleteTopic(topic.name)).resolves.toBeUndefined();
    await expect(sendTopicMessage({ topic: topic.name, body: '{"id":1}' })).resolves.toMatchObject({ msgId: 'msg-1' });
  });
});
