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
import { fetchTopicList, queryProducerConnection } from './producer';

const mock = new MockAdapter(client);

describe('Producer API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('fetches topic list sorted alphabetically', async () => {
    mock.onGet('/topics').reply(200, {
      topicList: ['order-events', 'user-signup', 'batch-process'],
    });

    const result = await fetchTopicList();
    expect(result).toEqual(['batch-process', 'order-events', 'user-signup']);
  });

  it('handles empty topic list', async () => {
    mock.onGet('/topics').reply(200, { topicList: [] });

    const result = await fetchTopicList();
    expect(result).toEqual([]);
  });

  it('queries producer connections by topic and group', async () => {
    const connections = [
      {
        clientId: 'producer-1',
        clientAddr: '192.168.1.10',
        language: 'JAVA',
        versionDesc: '5.1.0',
      },
      {
        clientId: 'producer-2',
        clientAddr: '192.168.1.11',
        language: 'JAVA',
        versionDesc: '5.1.0',
      },
    ];
    mock.onGet('/producer/connection').reply((config) => {
      expect(config.params.topic).toBe('order-events');
      expect(config.params.producerGroup).toBe('order-producer');
      return [200, { connectionSet: connections }];
    });

    const result = await queryProducerConnection('order-events', 'order-producer');
    expect(result).toHaveLength(2);
    expect(result[0].clientId).toBe('producer-1');
  });

  it('handles empty producer connections', async () => {
    mock.onGet('/producer/connection').reply(200, { connectionSet: [] });

    const result = await queryProducerConnection('topic', 'group');
    expect(result).toEqual([]);
  });
});
