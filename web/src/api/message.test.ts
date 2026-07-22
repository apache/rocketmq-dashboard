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
import { getMessageTrace, queryMessages } from './message';

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
      topic: 'orders',
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
    mock.onGet('/messages/msg-1/trace').reply(200, { code: 200, data: trace });

    await expect(getMessageTrace('msg-1')).resolves.toEqual(trace);
  });
});
