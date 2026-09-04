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

import { describe, expect, it } from 'vitest';
import type { Instance } from '../api/instance';
import type { ConsumerGroup, Topic } from '../api/metadata';
import {
  buildFleetResourceInventory,
  filterFleetResourceRows,
  summarizeVisibleFleetResources,
} from './fleetResourceInventory';

const instances: Instance[] = [
  {
    id: 1,
    name: 'production',
    remark: null,
    type: 'DIRECT',
    endpoint: 'prod:9876',
    vendor: 'APACHE',
    topicCount: 0,
    consumerGroupCount: 0,
    gmtCreate: '',
    gmtModified: '',
  },
  {
    id: 2,
    name: 'staging',
    remark: null,
    type: 'CLOUD',
    endpoint: 'staging',
    vendor: 'ALIYUN',
    topicCount: 0,
    consumerGroupCount: 0,
    gmtCreate: '',
    gmtModified: '',
  },
];

const topic = (name: string, overrides: Partial<Topic> = {}): Topic => ({
  name,
  namespace: 'orders',
  type: 'NORMAL',
  clusterId: 'cluster-a',
  writeQueues: 8,
  readQueues: 8,
  perm: 'RW',
  messageCount: 10,
  tps: 2,
  consumerGroupCount: 1,
  remark: '',
  gmtCreate: '',
  gmtModified: '',
  ...overrides,
});

const group = (name: string, overrides: Partial<ConsumerGroup> = {}): ConsumerGroup => ({
  name,
  namespace: 'orders',
  clusterId: 'cluster-a',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 1,
  totalLag: 0,
  subscribedTopics: [],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  gmtCreate: '',
  gmtModified: '',
  delaySeconds: 0,
  instances: [],
  ...overrides,
});

const inventory = () =>
  buildFleetResourceInventory(
    instances,
    {
      production: [topic('orders'), topic('prod-only')],
      staging: [topic('orders', { type: 'FIFO' }), topic('stage-only')],
    },
    {
      production: [group('workers')],
      staging: [group('workers'), group('stage-workers')],
    },
  );

describe('buildFleetResourceInventory', () => {
  it('maps topics and consumer groups with instance metadata', () => {
    const result = inventory();
    expect(result.summary).toEqual({
      instances: 2,
      topics: 4,
      consumerGroups: 3,
      sharedNames: 2,
      uniqueNames: 5,
    });
    expect(result.rows).toContainEqual(
      expect.objectContaining({
        key: 'TOPIC:production:orders',
        vendor: 'APACHE',
        configuration: 'NORMAL · 8/8 · RW',
      }),
    );
  });

  it('reports every instance carrying a shared resource name', () => {
    const rows = inventory().rows.filter((row) => row.name === 'orders');
    expect(rows).toHaveLength(2);
    expect(rows[0].occurrenceCount).toBe(2);
    expect(rows[0].otherInstances).toHaveLength(1);
  });

  it('does not combine a topic and consumer group with the same name', () => {
    const result = buildFleetResourceInventory(
      instances,
      { production: [topic('orders')] },
      { staging: [group('orders')] },
    );
    expect(result.summary.sharedNames).toBe(0);
    expect(result.rows.every((row) => row.occurrenceCount === 1)).toBe(true);
  });

  it('uses Apache as the vendor fallback', () => {
    const result = buildFleetResourceInventory(
      [{ ...instances[0], vendor: undefined }],
      { production: [topic('orders')] },
      {},
    );
    expect(result.rows[0].vendor).toBe('APACHE');
  });

  it('tolerates missing resource results for a partially failed instance', () => {
    const result = buildFleetResourceInventory(instances, { production: [topic('orders')] }, {});
    expect(result.summary.topics).toBe(1);
    expect(result.summary.consumerGroups).toBe(0);
  });
});

describe('fleet inventory filters', () => {
  it('filters by kind and shared names', () => {
    const result = filterFleetResourceRows(inventory().rows, {
      kind: 'TOPIC',
      instanceId: 'ALL',
      vendor: 'ALL',
      sharedOnly: true,
      search: '',
    });
    expect(result.map((row) => row.name)).toEqual(['orders', 'orders']);
  });

  it('filters by instance, vendor, and normalized content search', () => {
    const result = filterFleetResourceRows(inventory().rows, {
      kind: 'ALL',
      instanceId: 'staging',
      vendor: 'ALIYUN',
      sharedOnly: false,
      search: ' FIFO ',
    });
    expect(result).toHaveLength(1);
    expect(result[0].key).toBe('TOPIC:staging:orders');
  });

  it('summarizes filtered rows without double counting names', () => {
    const rows = inventory().rows.filter((row) => row.name === 'orders');
    expect(summarizeVisibleFleetResources(rows)).toEqual({ resources: 2, instances: 2, names: 1 });
  });
});
