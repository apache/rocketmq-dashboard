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
import type { ConsumerGroup, SubscriptionEntry } from '../api/metadata';
import {
  buildConsumerSubscriptionPortfolio,
  classifySubscriptionExpression,
  loadConsumerSubscriptionSnapshots,
  type ConsumerSubscriptionSnapshot,
} from './consumerSubscriptionPortfolio';

const group = (name: string, overrides: Partial<ConsumerGroup> = {}): ConsumerGroup => ({
  name,
  namespace: 'production',
  clusterId: 'cluster-a',
  instanceId: 'instance-a',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 2,
  totalLag: 0,
  subscribedTopics: [],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  gmtCreate: '2026-01-01T00:00:00Z',
  gmtModified: '2026-01-02T00:00:00Z',
  delaySeconds: 0,
  instances: [],
  ...overrides,
});

const subscription = (
  topic: string,
  expression: string,
  overrides: Partial<SubscriptionEntry> = {},
): SubscriptionEntry => ({
  topic,
  expression,
  type: 'TAG',
  filterMode: 'TAG',
  consistency: 'CONSISTENT',
  ...overrides,
});

const snapshot = (
  groupName: string,
  subscriptions: SubscriptionEntry[],
  error: string | null = null,
): ConsumerSubscriptionSnapshot => ({ group: group(groupName), subscriptions, error });

describe('classifySubscriptionExpression', () => {
  it('recognizes the wildcard as an all-message subscription', () => {
    expect(classifySubscriptionExpression('*', 'TAG')).toBe('ALL');
    expect(classifySubscriptionExpression(' * ', 'TAG')).toBe('ALL');
  });

  it('recognizes individual and combined tag expressions', () => {
    expect(classifySubscriptionExpression('created', 'TAG')).toBe('TAG_SET');
    expect(classifySubscriptionExpression('created || paid', 'TAG')).toBe('TAG_SET');
    expect(classifySubscriptionExpression('order-v2.*', 'TAG')).toBe('TAG_SET');
  });

  it('recognizes SQL mode and common SQL operators', () => {
    expect(classifySubscriptionExpression("region = 'cn'", 'SQL92')).toBe('SQL');
    expect(classifySubscriptionExpression('price BETWEEN 10 AND 20', '')).toBe('SQL');
    expect(classifySubscriptionExpression("region IN ('cn', 'us')", 'PROPERTY')).toBe('SQL');
  });

  it('keeps empty and unusual expressions visible', () => {
    expect(classifySubscriptionExpression('', 'TAG')).toBe('EMPTY');
    expect(classifySubscriptionExpression('   ', 'TAG')).toBe('EMPTY');
    expect(classifySubscriptionExpression(undefined, undefined)).toBe('EMPTY');
    expect(classifySubscriptionExpression('tag-a && tag-b', 'TAG')).toBe('OTHER');
  });
});

describe('loadConsumerSubscriptionSnapshots', () => {
  it('loads groups in deterministic name order', async () => {
    const loader = vi.fn().mockResolvedValue([]);

    const result = await loadConsumerSubscriptionSnapshots(
      [group('zeta'), group('alpha'), group('middle')],
      loader,
      1,
    );

    expect(loader.mock.calls.map(([name]) => name)).toEqual(['alpha', 'middle', 'zeta']);
    expect(result.snapshots.map((item) => item.group.name)).toEqual(['alpha', 'middle', 'zeta']);
  });

  it('caps requests and reports omitted groups', async () => {
    const loader = vi.fn().mockResolvedValue([]);
    const groups = Array.from({ length: 8 }, (_, index) => group(`group-${index}`));

    const result = await loadConsumerSubscriptionSnapshots(groups, loader, 2, 3);

    expect(loader).toHaveBeenCalledTimes(3);
    expect(result.snapshots).toHaveLength(3);
    expect(result.omittedGroups).toBe(5);
  });

  it('isolates loader failures and retains the affected group', async () => {
    const loader = vi.fn(async (name: string) => {
      if (name === 'broken') throw new Error('NameServer timeout');
      return [subscription('orders', '*')];
    });

    const result = await loadConsumerSubscriptionSnapshots(
      [group('healthy'), group('broken')],
      loader,
      1,
    );

    expect(result.snapshots).toEqual([
      expect.objectContaining({
        group: expect.objectContaining({ name: 'broken' }),
        error: 'NameServer timeout',
        subscriptions: [],
      }),
      expect.objectContaining({ group: expect.objectContaining({ name: 'healthy' }), error: null }),
    ]);
  });

  it('uses a safe fallback for non-Error loader failures', async () => {
    const result = await loadConsumerSubscriptionSnapshots(
      [group('broken')],
      vi.fn().mockRejectedValue({ status: 500 }),
    );

    expect(result.snapshots[0].error).toBe('Load failed');
  });

  it('limits concurrent group requests', async () => {
    let active = 0;
    let maximum = 0;
    const releases: Array<() => void> = [];
    const loader = vi.fn(
      () =>
        new Promise<SubscriptionEntry[]>((resolve) => {
          active += 1;
          maximum = Math.max(maximum, active);
          releases.push(() => {
            active -= 1;
            resolve([]);
          });
        }),
    );
    const groups = [group('a'), group('b'), group('c'), group('d')];

    const promise = loadConsumerSubscriptionSnapshots(groups, loader, 2);
    await vi.waitFor(() => expect(loader).toHaveBeenCalledTimes(2));
    expect(maximum).toBe(2);
    releases.shift()?.();
    await vi.waitFor(() => expect(loader).toHaveBeenCalledTimes(3));
    expect(maximum).toBe(2);
    releases.shift()?.();
    await vi.waitFor(() => expect(loader).toHaveBeenCalledTimes(4));
    expect(maximum).toBe(2);
    releases.splice(0).forEach((release) => release());

    await expect(promise).resolves.toMatchObject({ omittedGroups: 0 });
  });

  it('coerces invalid concurrency to one worker', async () => {
    const order: string[] = [];
    await loadConsumerSubscriptionSnapshots(
      [group('b'), group('a')],
      async (name) => {
        order.push(name);
        return [];
      },
      Number.NaN,
    );

    expect(order).toEqual(['a', 'b']);
  });

  it('allows a zero group cap without calling the loader', async () => {
    const loader = vi.fn().mockResolvedValue([]);

    const result = await loadConsumerSubscriptionSnapshots([group('a')], loader, 4, 0);

    expect(loader).not.toHaveBeenCalled();
    expect(result).toEqual({ snapshots: [], omittedGroups: 1 });
  });
});

describe('buildConsumerSubscriptionPortfolio', () => {
  it('groups subscriptions by expression kind, filter mode, type, and consistency', () => {
    const portfolio = buildConsumerSubscriptionPortfolio([
      snapshot('group-a', [subscription('orders', 'created')]),
      snapshot('group-b', [subscription('payments', 'paid')]),
    ]);

    expect(portfolio.profiles).toHaveLength(1);
    expect(portfolio.profiles[0]).toMatchObject({
      expressionKind: 'TAG_SET',
      filterMode: 'TAG',
      type: 'TAG',
      consistency: 'CONSISTENT',
      subscriptionCount: 2,
      groupCount: 2,
      topicCount: 2,
      inconsistentCount: 0,
      sampleGroups: ['group-a', 'group-b'],
      sampleTopics: ['orders', 'payments'],
      sampleExpressions: ['created', 'paid'],
    });
  });

  it('separates profiles whose normalized dimensions differ', () => {
    const portfolio = buildConsumerSubscriptionPortfolio([
      snapshot('group-a', [
        subscription('orders', '*'),
        subscription('payments', "region = 'cn'", { type: 'SQL92', filterMode: 'SQL92' }),
        subscription('audit', '', { consistency: 'UNKNOWN' }),
      ]),
    ]);

    expect(portfolio.profiles.map((profile) => profile.expressionKind).sort()).toEqual([
      'ALL',
      'EMPTY',
      'SQL',
    ]);
  });

  it('counts common inconsistent values case-insensitively', () => {
    const portfolio = buildConsumerSubscriptionPortfolio([
      snapshot('group-a', [
        subscription('one', '*', { consistency: 'false' }),
        subscription('two', '*', { consistency: 'MISMATCH' }),
        subscription('three', '*', { consistency: 'Conflict' }),
      ]),
    ]);

    expect(portfolio.summary.inconsistentSubscriptions).toBe(3);
    expect(portfolio.profiles.reduce((sum, profile) => sum + profile.inconsistentCount, 0)).toBe(3);
  });

  it('excludes failed groups from subscription and empty-group totals', () => {
    const portfolio = buildConsumerSubscriptionPortfolio([
      snapshot('healthy-empty', []),
      snapshot('failed', [], 'timeout'),
      snapshot('healthy', [subscription('orders', '*')]),
    ]);

    expect(portfolio.summary).toMatchObject({
      inspectedGroups: 3,
      failedGroups: 1,
      emptyGroups: 1,
      subscriptions: 1,
    });
  });

  it('reports capped coverage against all available groups', () => {
    const portfolio = buildConsumerSubscriptionPortfolio(
      [snapshot('group-a', []), snapshot('group-b', [])],
      10,
    );

    expect(portfolio.summary).toMatchObject({
      availableGroups: 10,
      inspectedGroups: 2,
      omittedGroups: 8,
    });
  });

  it('counts unique topics across profiles', () => {
    const portfolio = buildConsumerSubscriptionPortfolio([
      snapshot('group-a', [subscription('orders', '*'), subscription('payments', 'paid')]),
      snapshot('group-b', [subscription('orders', 'created')]),
    ]);

    expect(portfolio.summary.topics).toBe(2);
    expect(portfolio.summary.subscriptions).toBe(3);
  });

  it('limits sorted group, topic, and expression samples to five values', () => {
    const subscriptions = Array.from({ length: 8 }, (_, index) =>
      subscription(`topic-${index}`, `tag-${index}`),
    );
    const portfolio = buildConsumerSubscriptionPortfolio([
      snapshot('zeta', subscriptions),
      snapshot('alpha', [subscription('topic-0', 'tag-0')]),
    ]);

    expect(portfolio.profiles[0].sampleGroups).toEqual(['alpha', 'zeta']);
    expect(portfolio.profiles[0].sampleTopics).toHaveLength(5);
    expect(portfolio.profiles[0].sampleExpressions).toHaveLength(5);
  });

  it('sorts larger profiles before smaller profiles', () => {
    const portfolio = buildConsumerSubscriptionPortfolio([
      snapshot('group-a', [
        subscription('one', '*'),
        subscription('two', '*'),
        subscription('three', 'tag-a'),
      ]),
    ]);

    expect(portfolio.profiles.map((profile) => profile.subscriptionCount)).toEqual([2, 1]);
  });

  it('does not mutate snapshots or subscription entries', () => {
    const snapshots = [snapshot('group-a', [subscription('orders', '*')])];
    const source = structuredClone(snapshots);

    buildConsumerSubscriptionPortfolio(snapshots);

    expect(snapshots).toEqual(source);
  });

  it('returns a complete empty summary', () => {
    expect(buildConsumerSubscriptionPortfolio([])).toEqual({
      profiles: [],
      snapshots: [],
      summary: {
        availableGroups: 0,
        inspectedGroups: 0,
        omittedGroups: 0,
        failedGroups: 0,
        subscriptions: 0,
        topics: 0,
        profiles: 0,
        inconsistentSubscriptions: 0,
        emptyGroups: 0,
      },
    });
  });
});
