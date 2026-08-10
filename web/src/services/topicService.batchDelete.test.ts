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

const metadataApiMocks = vi.hoisted(() => ({
  deleteTopic: vi.fn(),
}));

vi.mock('../config', () => ({
  API_BASE_URL: '/api',
  USE_MOCK: false,
}));

vi.mock('../api/metadata', () => metadataApiMocks);

import { batchDeleteTopics } from './topicService';

describe('topic service batch deletion', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('keeps same-name targets distinct and reports each outcome by row key', async () => {
    metadataApiMocks.deleteTopic.mockImplementation(
      (_name: string, _instanceId: string, clusterId: string) =>
        clusterId === 'cluster-2' ? Promise.reject(new Error('delete failed')) : Promise.resolve(),
    );

    await expect(
      batchDeleteTopics(
        [
          { key: 'cluster-1/orders', name: 'orders', clusterId: 'cluster-1' },
          { key: 'cluster-2/orders', name: 'orders', clusterId: 'cluster-2' },
          { key: 'cluster-3/payments', name: 'payments', clusterId: 'cluster-3' },
        ],
        'instance-a',
      ),
    ).resolves.toEqual({
      deleted: ['cluster-1/orders', 'cluster-3/payments'],
      failed: ['cluster-2/orders'],
    });
    expect(metadataApiMocks.deleteTopic.mock.calls.map(([name]) => name)).toEqual([
      'orders',
      'orders',
      'payments',
    ]);
    expect(metadataApiMocks.deleteTopic).toHaveBeenNthCalledWith(
      2,
      'orders',
      'instance-a',
      'cluster-2',
    );
  });
});
