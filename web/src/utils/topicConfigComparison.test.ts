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
import type { Topic } from '../api/metadata';
import {
  compareTopicInventories,
  filterTopicComparisonRows,
  formatTopicDifferences,
  TOPIC_CONFIG_FIELDS,
} from './topicConfigComparison';

const topic = (name: string, overrides: Partial<Topic> = {}): Topic => ({
  name,
  namespace: 'orders',
  type: 'NORMAL',
  clusterId: 'cluster-a',
  instanceId: 'source',
  writeQueues: 8,
  readQueues: 8,
  perm: 'RW',
  messageCount: 100,
  tps: 12,
  consumerGroupCount: 2,
  remark: '',
  gmtCreate: '2026-09-01 00:00:00',
  gmtModified: '2026-09-01 00:00:00',
  ...overrides,
});

describe('compareTopicInventories', () => {
  it('reports matching stable configuration', () => {
    const result = compareTopicInventories(
      [topic('orders-created')],
      [topic('orders-created', { instanceId: 'target', clusterId: 'cluster-b' })],
    );

    expect(result.rows).toEqual([
      expect.objectContaining({
        topicName: 'orders-created',
        status: 'MATCH',
        differences: [],
      }),
    ]);
    expect(result.summary).toEqual({
      total: 1,
      matches: 1,
      drifted: 0,
      onlySource: 0,
      onlyTarget: 0,
    });
  });

  it('compares every declared configuration field', () => {
    const source = topic('orders-created');
    const target = topic('orders-created', {
      type: 'FIFO',
      namespace: 'payments',
      writeQueues: 16,
      readQueues: 4,
      perm: 'RO',
    });

    const result = compareTopicInventories([source], [target]);

    expect(result.rows[0].status).toBe('DRIFT');
    expect(result.rows[0].differences.map((difference) => difference.field)).toEqual(
      TOPIC_CONFIG_FIELDS,
    );
    expect(result.summary.drifted).toBe(1);
  });

  it('ignores runtime values, timestamps, remarks, instance and cluster identity', () => {
    const source = topic('orders-created');
    const target = topic('orders-created', {
      clusterId: 'cluster-b',
      instanceId: 'target',
      messageCount: 9_999,
      tps: 999,
      consumerGroupCount: 20,
      remark: 'different operational note',
      gmtCreate: '2025-01-01 00:00:00',
      gmtModified: '2026-09-04 00:00:00',
    });

    expect(compareTopicInventories([source], [target]).rows[0].status).toBe('MATCH');
  });

  it('trims string configuration returned by heterogeneous providers', () => {
    const source = topic('orders-created', { namespace: ' orders ', perm: ' RW ' });
    const target = topic('orders-created', { namespace: 'orders', perm: 'RW' });

    expect(compareTopicInventories([source], [target]).rows[0].status).toBe('MATCH');
  });

  it('reports source-only and target-only topics', () => {
    const result = compareTopicInventories(
      [topic('source-only'), topic('shared')],
      [topic('target-only'), topic('shared')],
    );

    expect(result.rows.map(({ topicName, status }) => ({ topicName, status }))).toEqual([
      { topicName: 'shared', status: 'MATCH' },
      { topicName: 'source-only', status: 'ONLY_SOURCE' },
      { topicName: 'target-only', status: 'ONLY_TARGET' },
    ]);
    expect(result.summary).toEqual({
      total: 3,
      matches: 1,
      drifted: 0,
      onlySource: 1,
      onlyTarget: 1,
    });
  });

  it('returns deterministic name ordering regardless of API order', () => {
    const result = compareTopicInventories(
      [topic('z-topic'), topic('a-topic')],
      [topic('m-topic'), topic('z-topic')],
    );

    expect(result.rows.map((row) => row.topicName)).toEqual(['a-topic', 'm-topic', 'z-topic']);
  });

  it('handles empty inventories', () => {
    expect(compareTopicInventories([], [])).toEqual({
      rows: [],
      summary: { total: 0, matches: 0, drifted: 0, onlySource: 0, onlyTarget: 0 },
    });
  });
});

describe('comparison row helpers', () => {
  const rows = compareTopicInventories(
    [topic('Orders.Created'), topic('payments-settled')],
    [topic('Orders.Created', { writeQueues: 16 }), topic('target-only')],
  ).rows;

  it('filters by status', () => {
    expect(filterTopicComparisonRows(rows, 'DRIFT', '').map((row) => row.topicName)).toEqual([
      'Orders.Created',
    ]);
    expect(filterTopicComparisonRows(rows, 'ONLY_TARGET', '').map((row) => row.topicName)).toEqual([
      'target-only',
    ]);
  });

  it('searches topic names case-insensitively and trims input', () => {
    expect(filterTopicComparisonRows(rows, 'ALL', '  orders. ')).toHaveLength(1);
  });

  it('combines status and search filters', () => {
    expect(filterTopicComparisonRows(rows, 'ONLY_SOURCE', 'payments')).toHaveLength(1);
    expect(filterTopicComparisonRows(rows, 'DRIFT', 'payments')).toHaveLength(0);
  });

  it('formats field differences for CSV and text views', () => {
    const drift = rows.find((row) => row.status === 'DRIFT')!;
    expect(formatTopicDifferences(drift.differences)).toBe('writeQueues: 8 -> 16');
    expect(formatTopicDifferences([])).toBe('');
  });
});
