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
import { getMessageTrace, listDLQGroups, queryMessages } from './messageService';

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
    const first = await listDLQGroups();
    expect(first[0].groupName).toBe('cg-order-processor');

    first[0].groupName = 'mutated-group';

    const second = await listDLQGroups();
    expect(second[0].groupName).toBe('cg-order-processor');
    expect(second[0]).not.toBe(first[0]);
  });
});
