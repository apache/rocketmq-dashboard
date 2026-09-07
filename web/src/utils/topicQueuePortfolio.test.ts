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
import { buildTopicQueuePortfolio, classifyTopicQueueProfile } from './topicQueuePortfolio';

const topic = (name: string, overrides: Partial<Topic> = {}): Topic => ({
  name,
  namespace: 'production',
  type: 'NORMAL',
  clusterId: 'cluster-a',
  instanceId: 'instance-a',
  writeQueues: 8,
  readQueues: 8,
  perm: 'RW',
  messageCount: 100,
  tps: 2.5,
  consumerGroupCount: 2,
  remark: '',
  gmtCreate: '2026-01-01T00:00:00Z',
  gmtModified: '2026-01-02T00:00:00Z',
  ...overrides,
});

describe('classifyTopicQueueProfile', () => {
  it('classifies equal read and write queues as balanced', () => {
    expect(classifyTopicQueueProfile(8, 8, 'RW')).toBe('BALANCED');
    expect(classifyTopicQueueProfile(0, 0, 'WR')).toBe('BALANCED');
  });

  it('classifies unequal read and write queues as asymmetric', () => {
    expect(classifyTopicQueueProfile(16, 8, 'RW')).toBe('ASYMMETRIC');
    expect(classifyTopicQueueProfile(4, 12, 'WR')).toBe('ASYMMETRIC');
  });

  it('recognizes read-only and write-only permissions before queue symmetry', () => {
    expect(classifyTopicQueueProfile(8, 8, 'R')).toBe('READ_ONLY');
    expect(classifyTopicQueueProfile(8, 8, 'W')).toBe('WRITE_ONLY');
  });

  it('recognizes common no-access values', () => {
    expect(classifyTopicQueueProfile(8, 8, 'NONE')).toBe('NO_ACCESS');
    expect(classifyTopicQueueProfile(8, 8, 'DENY')).toBe('NO_ACCESS');
    expect(classifyTopicQueueProfile(8, 8, '0')).toBe('NO_ACCESS');
  });

  it('normalizes known permissions without hiding unknown values', () => {
    expect(classifyTopicQueueProfile(8, 8, ' rw ')).toBe('BALANCED');
    expect(classifyTopicQueueProfile(8, 8, 'ADMIN')).toBe('UNKNOWN_PERMISSION');
    expect(classifyTopicQueueProfile(8, 8, '')).toBe('UNKNOWN_PERMISSION');
    expect(classifyTopicQueueProfile(8, 8)).toBe('UNKNOWN_PERMISSION');
  });
});

describe('buildTopicQueuePortfolio', () => {
  it('groups identical queue configurations into one profile', () => {
    const portfolio = buildTopicQueuePortfolio([topic('orders'), topic('payments')]);

    expect(portfolio.profiles).toHaveLength(1);
    expect(portfolio.profiles[0]).toMatchObject({
      type: 'NORMAL',
      namespace: 'production',
      writeQueues: 8,
      readQueues: 8,
      permission: 'RW',
      status: 'BALANCED',
      topicCount: 2,
      sharePercent: 100,
      totalWriteQueues: 16,
      totalReadQueues: 16,
      messageCount: 200,
      tps: 5,
      consumerGroups: 4,
      sampleTopics: ['orders', 'payments'],
    });
  });

  it('keeps type, namespace, queue counts, and permission as profile dimensions', () => {
    const portfolio = buildTopicQueuePortfolio([
      topic('base'),
      topic('fifo', { type: 'FIFO' }),
      topic('staging', { namespace: 'staging' }),
      topic('wide', { writeQueues: 16 }),
      topic('read-only', { perm: 'R' }),
    ]);

    expect(portfolio.profiles).toHaveLength(5);
    expect(portfolio.summary.profiles).toBe(5);
  });

  it('calculates fleet totals by topic rather than by profile', () => {
    const portfolio = buildTopicQueuePortfolio([
      topic('orders', { writeQueues: 8, readQueues: 8 }),
      topic('payments', { writeQueues: 8, readQueues: 8 }),
      topic('audit', { writeQueues: 2, readQueues: 4 }),
    ]);

    expect(portfolio.summary).toMatchObject({
      topics: 3,
      profiles: 2,
      writeQueues: 18,
      readQueues: 20,
      balancedTopics: 2,
      asymmetricTopics: 1,
    });
    expect(portfolio.summary.dominantProfilePercent).toBeCloseTo(66.7);
  });

  it('counts restricted and unknown permission topics separately', () => {
    const portfolio = buildTopicQueuePortfolio([
      topic('read-only', { perm: 'R' }),
      topic('write-only', { perm: 'W' }),
      topic('disabled', { perm: 'NONE' }),
      topic('custom', { perm: 'CUSTOM' }),
      topic('healthy'),
    ]);

    expect(portfolio.summary).toMatchObject({
      restrictedTopics: 3,
      unknownPermissionTopics: 1,
      balancedTopics: 1,
    });
  });

  it('uses stable fallbacks for blank type, namespace, and permission', () => {
    const portfolio = buildTopicQueuePortfolio([
      topic('legacy', { type: ' ', namespace: '', perm: '' }),
    ]);

    expect(portfolio.profiles[0]).toMatchObject({
      type: 'UNKNOWN',
      namespace: '(default)',
      permission: 'UNKNOWN',
      status: 'UNKNOWN_PERMISSION',
    });
  });

  it('clamps negative and non-finite counters to zero', () => {
    const portfolio = buildTopicQueuePortfolio([
      topic('broken', {
        writeQueues: -1,
        readQueues: Number.NaN,
        messageCount: Number.POSITIVE_INFINITY,
        tps: -4,
        consumerGroupCount: -2,
      }),
    ]);

    expect(portfolio.profiles[0]).toMatchObject({
      writeQueues: 0,
      readQueues: 0,
      totalWriteQueues: 0,
      totalReadQueues: 0,
      messageCount: 0,
      tps: 0,
      consumerGroups: 0,
    });
    expect(portfolio.summary.writeQueues).toBe(0);
    expect(portfolio.summary.readQueues).toBe(0);
  });

  it('rounds aggregate TPS to two decimal places', () => {
    const portfolio = buildTopicQueuePortfolio([
      topic('one', { tps: 0.105 }),
      topic('two', { tps: 0.106 }),
    ]);

    expect(portfolio.profiles[0].tps).toBe(0.21);
  });

  it('limits samples to five sorted topic names', () => {
    const portfolio = buildTopicQueuePortfolio([
      topic('zeta'),
      topic('echo'),
      topic('delta'),
      topic('charlie'),
      topic('bravo'),
      topic('alpha'),
      topic('foxtrot'),
    ]);

    expect(portfolio.profiles[0].sampleTopics).toEqual([
      'alpha',
      'bravo',
      'charlie',
      'delta',
      'echo',
    ]);
  });

  it('sorts larger profiles first and uses the profile key as a tie breaker', () => {
    const portfolio = buildTopicQueuePortfolio([
      topic('normal-2'),
      topic('normal-1'),
      topic('fifo', { type: 'FIFO' }),
      topic('read-only', { perm: 'R' }),
    ]);

    expect(portfolio.profiles[0].topicCount).toBe(2);
    expect(portfolio.profiles.slice(1).map((profile) => profile.key)).toEqual(
      [...portfolio.profiles.slice(1).map((profile) => profile.key)].sort(),
    );
  });

  it('keeps queue profiles separate when normalized permissions differ', () => {
    const portfolio = buildTopicQueuePortfolio([
      topic('rw-upper', { perm: 'RW' }),
      topic('rw-lower', { perm: 'rw' }),
      topic('wr-order', { perm: 'WR' }),
    ]);

    expect(portfolio.profiles).toHaveLength(2);
    expect(portfolio.profiles.find((profile) => profile.permission === 'RW')?.topicCount).toBe(2);
    expect(portfolio.profiles.find((profile) => profile.permission === 'WR')?.topicCount).toBe(1);
  });

  it('does not mutate or reorder the source topic list', () => {
    const topics = [topic('zeta'), topic('alpha')];
    const snapshot = structuredClone(topics);

    buildTopicQueuePortfolio(topics);

    expect(topics).toEqual(snapshot);
    expect(topics.map((item) => item.name)).toEqual(['zeta', 'alpha']);
  });

  it('returns a complete empty summary for an instance without topics', () => {
    expect(buildTopicQueuePortfolio([])).toEqual({
      profiles: [],
      summary: {
        topics: 0,
        profiles: 0,
        balancedTopics: 0,
        asymmetricTopics: 0,
        restrictedTopics: 0,
        unknownPermissionTopics: 0,
        writeQueues: 0,
        readQueues: 0,
        dominantProfilePercent: 0,
      },
    });
  });
});
