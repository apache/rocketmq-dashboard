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
  consumeMessageDirectly,
  getMessageTrace,
  getQueueOffsets,
  listDLQMessages,
  pullMessageAtOffset,
  queryMessagePage,
  queryMessages,
  resendDLQSelected,
} from './message';

const mock = new MockAdapter(client);

describe('message API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('sends the backend-supported query fields with epoch timestamps', async () => {
    const params = {
      instanceId: 'instance-1',
      topic: 'orders',
      tag: 'created',
      key: 'order-1',
      startTime: 1784246400000,
      endTime: 1784332800000,
    };
    mock.onGet('/messages').reply((config) => {
      expect(config.params).toEqual(params);
      return [200, { code: 200, data: [] }];
    });

    await expect(queryMessages(params)).resolves.toEqual([]);
  });

  it('sorts message query results by store time descending', async () => {
    mock.onGet('/messages').reply(200, {
      code: 200,
      data: [
        {
          msgId: 'msg-old',
          topic: 'orders',
          tag: 'created',
          key: 'order-1',
          body: '{}',
          storeTime: '2026-07-23T10:00:00.000Z',
          bornHost: '10.0.0.1:1000',
          storeHost: '10.0.0.2:10911',
          properties: {},
          size: 2,
        },
        {
          msgId: 'msg-new',
          topic: 'orders',
          tag: 'created',
          key: 'order-2',
          body: '{}',
          storeTime: 1784804400000,
          bornHost: '10.0.0.1:1001',
          storeHost: '10.0.0.2:10911',
          properties: {},
          size: 2,
        },
      ],
    });

    await expect(queryMessages({ topic: 'orders' })).resolves.toMatchObject([
      { msgId: 'msg-new' },
      { msgId: 'msg-old' },
    ]);
  });

  it('uses the paged query contract and preserves its truncation state', async () => {
    const params = { instanceId: 'instance-1', topic: 'orders', page: 2, pageSize: 50 };
    const page = { items: [], total: 200, page: 2, size: 50, resultMayBeTruncated: true };
    mock.onGet('/messages/page').reply((config) => {
      expect(config.params).toEqual(params);
      return [200, { code: 200, data: page }];
    });

    await expect(queryMessagePage(params)).resolves.toEqual(page);
  });

  it('unwraps trace records with numeric timestamps', async () => {
    const trace = {
      nodes: [
        {
          title: 'Stored',
          timestamp: 1784246400000,
          status: 'finish',
          costTime: 3,
          description: 'ok',
        },
      ],
      consumerStatus: [
        {
          group: 'cg-orders',
          deliveryStatus: 'SUCCESS',
          consumeTime: 1784246401000,
          retryCount: 0,
        },
      ],
    };
    mock
      .onGet('/messages/msg-1/trace', { params: { instanceId: 'instance-1', topic: 'orders' } })
      .reply(200, { code: 200, data: trace });

    await expect(getMessageTrace('msg-1', 'instance-1', 'orders')).resolves.toEqual(trace);
  });

  it('maps backend trace node statuses to Ant Design step statuses', async () => {
    const trace = {
      nodes: [
        {
          title: 'Produce',
          timestamp: 1784246400000,
          status: 'failed',
          costTime: 1,
          description: 'x',
        },
        {
          title: 'Consume',
          timestamp: 1784246400000,
          status: 'finish',
          costTime: 1,
          description: 'x',
        },
        {
          title: 'In flight',
          timestamp: 1784246400000,
          status: 'process',
          costTime: 1,
          description: 'x',
        },
        {
          title: 'Unknown',
          timestamp: 1784246400000,
          status: 'something-else',
          costTime: 1,
          description: 'x',
        },
      ],
      consumerStatus: [],
    };
    mock
      .onGet('/messages/msg-2/trace', { params: { instanceId: 'instance-1' } })
      .reply(200, { code: 200, data: trace });

    const mapped = await getMessageTrace('msg-2', 'instance-1');
    expect(mapped.nodes.map((node) => node.status)).toEqual(['error', 'finish', 'process', 'wait']);
    expect(mapped.consumerStatus).toEqual([]);
  });

  it('encodes message IDs before requesting trace records', async () => {
    const trace = {
      nodes: [],
      consumerStatus: [],
    };
    mock
      .onGet('/messages/AC1E0A64%2F0000%202A9F%3A1/trace', {
        params: { instanceId: 'instance-1', topic: 'orders' },
      })
      .reply(200, { code: 200, data: trace });

    await expect(getMessageTrace('AC1E0A64/0000 2A9F:1', 'instance-1', 'orders')).resolves.toEqual(
      trace,
    );
  });

  it('loads queue offsets and pulls a message at an offset', async () => {
    mock.onGet('/messages/queues').reply((config) => {
      expect(config.params).toEqual({ instanceId: 'instance-1', topic: 'orders' });
      return [200, { code: 200, data: [{ brokerName: 'broker-a', queueId: 3 }] }];
    });
    mock.onGet('/messages/queue-message').reply((config) => {
      expect(config.params).toEqual({
        instanceId: 'instance-1',
        topic: 'orders',
        brokerName: 'broker-a',
        queueId: 3,
        offset: 99,
      });
      return [200, { code: 200, data: { msgId: 'msg-1' } }];
    });

    await expect(getQueueOffsets({ instanceId: 'instance-1', topic: 'orders' })).resolves.toEqual([
      { brokerName: 'broker-a', queueId: 3 },
    ]);
    await expect(
      pullMessageAtOffset({
        instanceId: 'instance-1',
        topic: 'orders',
        brokerName: 'broker-a',
        queueId: 3,
        offset: 99,
      }),
    ).resolves.toEqual({ msgId: 'msg-1' });
  });

  it('lists DLQ messages and resends a selected batch', async () => {
    mock.onGet('/dlq/cg-orders/messages').reply((config) => {
      expect(config.params).toEqual({ instanceId: 'instance-1' });
      return [
        200,
        {
          code: 200,
          data: { items: [{ msgId: 'msg-1' }], total: 1, page: 1, size: 20 },
        },
      ];
    });
    mock.onPost('/dlq/resend-selected').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        instanceId: 'instance-1',
        groupName: 'cg-orders',
        msgIds: ['msg-1'],
      });
      return [200, { code: 200, data: { succeeded: 1, failed: [] } }];
    });

    await expect(
      listDLQMessages({ instanceId: 'instance-1', groupName: 'cg-orders' }),
    ).resolves.toMatchObject({ total: 1 });
    await expect(
      resendDLQSelected({ instanceId: 'instance-1', groupName: 'cg-orders', msgIds: ['msg-1'] }),
    ).resolves.toEqual({ succeeded: 1, failed: [] });
  });

  it('posts direct consumption to the message API', async () => {
    const request = {
      instanceId: 'instance-1',
      topic: 'orders',
      msgId: 'msg-1',
      consumerGroup: 'billing',
      clientId: 'client-a',
    };
    const result = {
      consumeResult: 'CR_SUCCESS',
      spentTimeMillis: 8,
      order: false,
      autoCommit: true,
    };
    mock.onPost('/messages/direct-consume', request).reply(200, { code: 200, data: result });

    await expect(consumeMessageDirectly(request)).resolves.toEqual(result);
  });
});
