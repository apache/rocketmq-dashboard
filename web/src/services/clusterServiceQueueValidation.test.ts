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

vi.mock('./dataMode', () => ({ isMockMode: () => true }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));

import clusters from '../mock/clusters';
import { previewClusterConfig, updateClusterConfig } from './clusterService';

const CLUSTER_ID = clusters[0].id;

describe('mock cluster config queue count validation', () => {
  beforeEach(() => {
    // The mock module state is shared; restore the queue counts so cases
    // cannot leak into each other.
    clusters[0].config.writeQueueNums = 8;
    clusters[0].config.readQueueNums = 8;
  });

  it('rejects a preview with mismatched write/read queue counts', async () => {
    // The backend rejects this combination in all three endpoints
    // (ClusterService.requireMatchingDefaultQueueNums); the mock preview
    // used to silently let readQueueNums overwrite writeQueueNums.
    await expect(
      previewClusterConfig({ id: CLUSTER_ID, writeQueueNums: 16, readQueueNums: 8 }),
    ).rejects.toThrow(/matching writeQueueNums and readQueueNums/);
  });

  it('rejects an update with mismatched write/read queue counts', async () => {
    await expect(
      updateClusterConfig({ id: CLUSTER_ID, writeQueueNums: 16, readQueueNums: 8 } as never),
    ).rejects.toThrow(/matching writeQueueNums and readQueueNums/);
  });

  it('previews matching queue counts with the agreed value', async () => {
    const result = await previewClusterConfig({
      id: CLUSTER_ID,
      writeQueueNums: 12,
      readQueueNums: 12,
    });
    const brokerProps = result.brokerProperties as Record<string, string>;
    expect(brokerProps.defaultTopicQueueNums).toBe('12');
  });

  it('still previews a one-sided queue count change', async () => {
    const result = await previewClusterConfig({ id: CLUSTER_ID, readQueueNums: 6 });
    const brokerProps = result.brokerProperties as Record<string, string>;
    expect(brokerProps.defaultTopicQueueNums).toBe('6');
  });
});
