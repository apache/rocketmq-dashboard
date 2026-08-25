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
  consumeMessageDirectly,
  getMessageTrace,
  listDLQGroups,
  queryMessages,
} from './messageService';

vi.mock('./dataMode', () => ({ isMockMode: () => true }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));

describe('message service mock data', () => {
  it('returns copied message rows and properties', async () => {
    const first = await queryMessages({ msgId: 'AC1E0A6400002A9F0000000001A3F2B1' });
    expect(first[0].topic).toBe('order-create');
    expect(first[0].properties.KEYS).toBe('order-12345');

    first[0].topic = 'mutated-topic';
    first[0].properties.KEYS = 'mutated-key';

    const second = await queryMessages({ msgId: 'AC1E0A6400002A9F0000000001A3F2B1' });
    expect(second[0].topic).toBe('order-create');
    expect(second[0].properties.KEYS).toBe('order-12345');
    expect(second[0]).not.toBe(first[0]);
    expect(second[0].properties).not.toBe(first[0].properties);
  });

  it('filters mock topic queries by the selected store-time range', async () => {
    const messages = await queryMessages({
      topic: 'order-create',
      startTime: Date.parse('2026-07-01T10:24:00.000Z'),
      endTime: Date.parse('2026-07-01T10:26:00.000Z'),
    });

    expect(messages.map((message) => message.msgId)).toEqual(['AC1E0A6400002A9F0000000001A3F7C2']);
  });

  it('returns copied message trace rows', async () => {
    const first = await getMessageTrace('AC1E0A6400002A9F0000000001A3F2B1');
    expect(first?.nodes[0].title).toBe('Producer 发送');
    expect(first?.consumerStatus[0].group).toBe('cg-order-processor');

    first!.nodes[0].title = 'mutated-node';
    first!.consumerStatus[0].group = 'mutated-group';

    const second = await getMessageTrace('AC1E0A6400002A9F0000000001A3F2B1');
    expect(second?.nodes[0].title).toBe('Producer 发送');
    expect(second?.consumerStatus[0].group).toBe('cg-order-processor');
    expect(second?.nodes[0]).not.toBe(first?.nodes[0]);
    expect(second?.consumerStatus[0]).not.toBe(first?.consumerStatus[0]);
  });

  it('returns copied DLQ group rows', async () => {
    const first = await listDLQGroups('instance-1');
    expect(first.items[0].groupName).toBe('cg-order-processor');
    expect(first.total).toBeGreaterThanOrEqual(1);

    first.items[0].groupName = 'mutated-group';

    const second = await listDLQGroups('instance-1');
    expect(second.items[0].groupName).toBe('cg-order-processor');
    expect(second.items[0]).not.toBe(first.items[0]);
  });

  it('marks direct-consume results as mock-only in mock mode', async () => {
    await expect(
      consumeMessageDirectly({
        instanceId: 'instance-1',
        topic: 'orders',
        msgId: 'msg-1',
        consumerGroup: 'billing',
        clientId: 'client-a',
      }),
    ).resolves.toMatchObject({
      consumeResult: 'CR_SUCCESS',
      remark: expect.stringContaining('Mock mode'),
    });
  });

  it('filters and pages mock DLQ groups', async () => {
    const filtered = await listDLQGroups('instance-1', 'order', 1, 20);
    expect(filtered.items.every((group) => group.groupName.includes('order'))).toBe(true);
    expect(filtered.total).toBe(filtered.items.length);
    expect(filtered.page).toBe(1);
    expect(filtered.size).toBe(20);

    const all = await listDLQGroups('instance-1', undefined, 1, 1);
    expect(all.items).toHaveLength(1);
    expect(all.total).toBeGreaterThanOrEqual(1);
  });
});
