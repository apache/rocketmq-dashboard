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
  queryLiteTopicList,
  queryLiteTopicSession,
  extendLiteTopicTTL,
  queryLiteTopicQuota,
  queryLiteTopicCapability,
  type LiteTopicItem,
  type LiteTopicQuota,
  type LiteTopicSession,
} from './liteTopic';

const mock = new MockAdapter(client);

const sampleItem: LiteTopicItem = {
  topicPattern: 'order-*',
  topicCount: 15,
  consumerCount: 3,
  totalBacklog: 1200,
  averageTTL: 3600,
  ttlStatus: 'active',
  lastActiveTime: Date.now(),
  sessionIds: ['sess-1', 'sess-2'],
};

const sampleQuota: LiteTopicQuota = {
  currentTopicCount: 50,
  maxTopicCount: 200,
  currentSessionCount: 10,
  maxSessionCount: 100,
  currentCreationRate: 5,
  maxCreationRate: 50,
  defaultTTL: 3600,
  maxTTL: 86400,
  remainingQuota: 150,
  consumerDensity: 0.3,
};

const sampleSession: LiteTopicSession = {
  sessionId: 'sess-1',
  clientId: 'client-001',
  clientAddress: '192.168.1.10',
  parentTopic: 'order-events',
  consumerGroup: 'order-processor',
  createTime: Date.now() - 3600000,
  lastActiveTime: Date.now(),
  ttl: 3600,
  ttlRemaining: 1800,
  status: 'active',
  totalMessages: 5000,
  consumedMessages: 4800,
  pendingMessages: 200,
  popProgress: 96,
};

describe('LiteTopic API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('queries lite topic list without filters', async () => {
    mock.onGet(/\/liteTopic\/list/).reply(200, { code: 200, data: [sampleItem] });

    const result = await queryLiteTopicList();
    expect(result).toHaveLength(1);
    expect(result[0].topicPattern).toBe('order-*');
  });

  it('queries lite topic list with pattern and namespace', async () => {
    mock.onGet(/\/liteTopic\/list/).reply((config) => {
      expect(config.url).toContain('pattern=order');
      expect(config.url).toContain('namespace=ns1');
      return [200, { code: 200, data: [sampleItem] }];
    });

    const result = await queryLiteTopicList('order', 'ns1');
    expect(result).toHaveLength(1);
  });

  it('queries lite topic session by id', async () => {
    mock.onGet('/liteTopic/session/sess-1').reply(200, { code: 200, data: sampleSession });

    const result = await queryLiteTopicSession('sess-1');
    expect(result.sessionId).toBe('sess-1');
    expect(result.popProgress).toBe(96);
  });

  it('extends lite topic TTL', async () => {
    mock.onPost('/liteTopic/extendTTL').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.topicPattern).toBe('order-*');
      expect(body.newTTL).toBe(7200);
      return [200, { code: 200 }];
    });

    await extendLiteTopicTTL('order-*', 7200);
  });

  it('queries lite topic quota without namespace', async () => {
    mock.onGet(/\/liteTopic\/quota/).reply(200, { code: 200, data: sampleQuota });

    const result = await queryLiteTopicQuota();
    expect(result.currentTopicCount).toBe(50);
    expect(result.maxTopicCount).toBe(200);
    expect(result.defaultTTL).toBe(3600);
  });

  it('queries lite topic quota with namespace', async () => {
    mock.onGet(/\/liteTopic\/quota/).reply((config) => {
      expect(config.url).toContain('namespace=ns1');
      return [200, { code: 200, data: sampleQuota }];
    });

    const result = await queryLiteTopicQuota('ns1');
    expect(result.currentTopicCount).toBe(50);
  });

  it('queries lite topic capability as supported', async () => {
    mock.onGet('/liteTopic/capability').reply(200, { code: 200, data: { supported: true } });

    const result = await queryLiteTopicCapability();
    expect(result.supported).toBe(true);
  });

  it('queries lite topic capability as unsupported', async () => {
    mock.onGet('/liteTopic/capability').reply(200, { code: 200, data: { supported: false } });

    const result = await queryLiteTopicCapability();
    expect(result.supported).toBe(false);
  });
});
