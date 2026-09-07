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
import type { ConsumerGroup } from '../api/metadata';
import {
  compareConsumerGroupInventories,
  CONSUMER_GROUP_CONFIG_FIELDS,
  filterConsumerGroupComparisonRows,
  formatConsumerGroupDifferences,
} from './consumerGroupConfigComparison';

const group = (name: string, overrides: Partial<ConsumerGroup> = {}): ConsumerGroup => ({
  name,
  namespace: 'orders',
  clusterId: 'cluster-a',
  instanceId: 'source',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 3,
  totalLag: 120,
  subscribedTopics: ['orders-created'],
  subscriptionDataType: 'NORMAL',
  deliveryOrderType: 'CONCURRENTLY',
  retryMaxTimes: 16,
  gmtCreate: '2026-09-01 00:00:00',
  gmtModified: '2026-09-01 00:00:00',
  delaySeconds: 2,
  instances: [],
  ...overrides,
});

describe('compareConsumerGroupInventories', () => {
  it('reports matching stable configuration', () => {
    const result = compareConsumerGroupInventories(
      [group('order-workers')],
      [group('order-workers', { instanceId: 'target', clusterId: 'cluster-b' })],
    );

    expect(result.rows[0]).toEqual(
      expect.objectContaining({ groupName: 'order-workers', status: 'MATCH', differences: [] }),
    );
    expect(result.summary).toEqual({
      total: 1,
      matches: 1,
      drifted: 0,
      onlySource: 0,
      onlyTarget: 0,
    });
  });

  it('compares every declared configuration field', () => {
    const target = group('order-workers', {
      namespace: 'payments',
      subscriptionMode: 'Pop',
      consumeType: 'BROADCASTING',
      subscriptionDataType: 'SQL92',
      deliveryOrderType: 'ORDERLY',
      retryMaxTimes: 32,
    });

    const result = compareConsumerGroupInventories([group('order-workers')], [target]);

    expect(result.rows[0].status).toBe('DRIFT');
    expect(result.rows[0].differences.map((difference) => difference.field)).toEqual(
      CONSUMER_GROUP_CONFIG_FIELDS,
    );
    expect(result.summary.drifted).toBe(1);
  });

  it('excludes runtime observations and instance identity', () => {
    const target = group('order-workers', {
      instanceId: 'target',
      clusterId: 'cluster-b',
      onlineInstances: 99,
      totalLag: 8_000,
      delaySeconds: 600,
      subscribedTopics: ['different-runtime-subscription'],
      instances: [
        {
          clientId: 'client-a',
          protocol: 'JAVA',
          address: '127.0.0.1',
          subscribedTopics: [],
          lastHeartbeat: '2026-09-04 00:00:00',
          topicLag: {},
        },
      ],
      gmtCreate: '2025-01-01 00:00:00',
      gmtModified: '2026-09-04 00:00:00',
    });

    expect(compareConsumerGroupInventories([group('order-workers')], [target]).rows[0].status).toBe(
      'MATCH',
    );
  });

  it('normalizes provider string padding and missing optional values', () => {
    const source = group('order-workers', {
      namespace: ' orders ',
      subscriptionMode: ' Push ',
      deliveryOrderType: undefined,
    });
    const target = group('order-workers', { deliveryOrderType: '' });

    expect(compareConsumerGroupInventories([source], [target]).rows[0].status).toBe('MATCH');
  });

  it('reports source-only and target-only groups deterministically', () => {
    const result = compareConsumerGroupInventories(
      [group('source-only'), group('shared')],
      [group('target-only'), group('shared')],
    );

    expect(result.rows.map(({ groupName, status }) => ({ groupName, status }))).toEqual([
      { groupName: 'shared', status: 'MATCH' },
      { groupName: 'source-only', status: 'ONLY_SOURCE' },
      { groupName: 'target-only', status: 'ONLY_TARGET' },
    ]);
    expect(result.summary).toEqual({
      total: 3,
      matches: 1,
      drifted: 0,
      onlySource: 1,
      onlyTarget: 1,
    });
  });

  it('filters by status and normalized name search', () => {
    const rows = compareConsumerGroupInventories(
      [group('orders-workers'), group('billing-workers')],
      [group('orders-workers', { retryMaxTimes: 32 }), group('shipment-workers')],
    ).rows;

    expect(filterConsumerGroupComparisonRows(rows, 'DRIFT', ' Orders ')).toHaveLength(1);
    expect(filterConsumerGroupComparisonRows(rows, 'ONLY_SOURCE', 'billing')).toHaveLength(1);
    expect(filterConsumerGroupComparisonRows(rows, 'ONLY_TARGET', 'shipment')).toHaveLength(1);
    expect(filterConsumerGroupComparisonRows(rows, 'MATCH', '')).toHaveLength(0);
  });

  it('formats field changes for CSV output', () => {
    expect(
      formatConsumerGroupDifferences([
        { field: 'retryMaxTimes', sourceValue: 16, targetValue: 32 },
        { field: 'subscriptionMode', sourceValue: 'Push', targetValue: 'Pop' },
      ]),
    ).toBe('retryMaxTimes: 16 -> 32; subscriptionMode: Push -> Pop');
  });

  it('returns an empty summary for two empty inventories', () => {
    expect(compareConsumerGroupInventories([], [])).toEqual({
      rows: [],
      summary: { total: 0, matches: 0, drifted: 0, onlySource: 0, onlyTarget: 0 },
    });
  });
});
