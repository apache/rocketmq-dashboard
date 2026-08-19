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
    const pageData = { items: [group], total: 1, page: 1, size: 20 };
    mock
      .onGet('/dlq')
      .reply((config) =>
        config.params?.instanceId === 'instance-1' &&
        config.params?.page === 1 &&
        config.params?.pageSize === 20
          ? [200, { code: 200, data: pageData }]
          : [404, {}],
      );

    await expect(listDLQGroups('instance-1')).resolves.toEqual(pageData);
  });

  it('passes paged search parameters through to the backend contract', async () => {
    const pageData = { items: [group], total: 1, page: 2, size: 50 };
    mock
      .onGet('/dlq')
      .reply((config) =>
        config.params?.instanceId === 'instance-1' &&
        config.params?.search === 'order' &&
        config.params?.page === 2 &&
        config.params?.pageSize === 50
          ? [200, { code: 200, data: pageData }]
          : [404, {}],
      );

    await expect(listDLQGroups('instance-1', 'order', 2, 50)).resolves.toEqual(pageData);
  });

  it('sends epoch milliseconds for the resend time range', async () => {
    const payload = {
      instanceId: 'instance-1',
      groupName: group.groupName,
      startTime: 1784246400000,
      endTime: 1784332800000,
      targetTopic: 'orders-retry',
    };
    const result = { matched: 2, resent: 2, failed: 0, outcome: 'SUCCESS' };
    mock.onPost('/dlq/resend').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(payload);
      return [200, { code: 200, data: result }];
    });

    await expect(resendDLQ(payload)).resolves.toEqual(result);
  });
});
