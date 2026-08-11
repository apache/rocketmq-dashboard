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

import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as messageApi from '../api/message';
import { getMessageTrace, listDLQGroups, queryMessages } from './messageService';

const { mode } = vi.hoisted(() => ({ mode: { mock: true } }));

vi.mock('./dataMode', () => ({ isMockMode: () => mode.mock }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));
vi.mock('../api/message', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/message')>();
  return { ...actual, getMessageTrace: vi.fn() };
});

describe('message service mock data', () => {
  beforeEach(() => {
    mode.mock = true;
    vi.clearAllMocks();
  });

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

  it('converts an ISO store time before requesting a trace in API mode', async () => {
    mode.mock = false;
    vi.mocked(messageApi.getMessageTrace).mockResolvedValue({ nodes: [], consumerStatus: [] });

    await getMessageTrace('msg-1', 'instance-a', '2026-07-31T00:00:00Z');

    expect(messageApi.getMessageTrace).toHaveBeenCalledWith(
      'msg-1',
      'instance-a',
      Date.parse('2026-07-31T00:00:00Z'),
    );
  });

  it('omits an invalid store time when requesting a trace in API mode', async () => {
    mode.mock = false;
    vi.mocked(messageApi.getMessageTrace).mockResolvedValue({ nodes: [], consumerStatus: [] });

    await getMessageTrace('msg-1', 'instance-a', 'not-a-time');

    expect(messageApi.getMessageTrace).toHaveBeenCalledWith('msg-1', 'instance-a', undefined);
  });

  it('returns copied DLQ group rows', async () => {
    const first = await listDLQGroups('instance-1');
    expect(first[0].groupName).toBe('cg-order-processor');

    first[0].groupName = 'mutated-group';

    const second = await listDLQGroups('instance-1');
    expect(second[0].groupName).toBe('cg-order-processor');
    expect(second[0]).not.toBe(first[0]);
  });
});
