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
  deleteConsumerGroup: vi.fn(),
}));

vi.mock('../config', () => ({
  API_BASE_URL: '/api',
  USE_MOCK: false,
}));

vi.mock('../api/metadata', () => metadataApiMocks);

import { batchDeleteConsumerGroups } from './consumerService';

describe('consumer service batch deletion', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('continues deleting after a failure and reports each outcome', async () => {
    metadataApiMocks.deleteConsumerGroup.mockImplementation((name: string) =>
      name === 'group-02' ? Promise.reject(new Error('delete failed')) : Promise.resolve(),
    );

    await expect(batchDeleteConsumerGroups(['group-01', 'group-02', 'group-03'], 'instance-a')).resolves.toEqual({
      deleted: ['group-01', 'group-03'],
      failed: ['group-02'],
    });
    expect(metadataApiMocks.deleteConsumerGroup.mock.calls).toEqual([
      ['group-01', 'instance-a'],
      ['group-02', 'instance-a'],
      ['group-03', 'instance-a'],
    ]);
  });
});
