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
import { listDLQGroups, resendDLQ } from './message';
import type { DLQGroup } from './message';

const mock = new MockAdapter(client);
const group: DLQGroup = {
  groupName: 'order-consumer',
  dlqTopic: '%DLQ%order-consumer',
  messageCount: 3,
  lastEnqueueTime: '2026-07-17T00:00:00Z',
  retryCount: 16,
  status: 'active',
};

describe('DLQ API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('loads and unwraps DLQ groups', async () => {
    mock.onGet('/dlq').reply(200, { code: 200, data: [group] });

    await expect(listDLQGroups()).resolves.toEqual([group]);
  });

  it('sends epoch milliseconds for the resend time range', async () => {
    const payload = {
      groupName: group.groupName,
      startTime: 1784246400000,
      endTime: 1784332800000,
      targetTopic: 'orders-retry',
    };
    mock.onPost('/dlq/resend').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(payload);
      return [200, { code: 200, data: null }];
    });

    await expect(resendDLQ(payload)).resolves.toBeUndefined();
  });
});
