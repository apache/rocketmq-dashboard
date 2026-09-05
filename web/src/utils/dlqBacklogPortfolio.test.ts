/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import { describe, expect, it } from 'vitest';
import type { DLQGroup } from '../api/message';
import { buildDLQBacklogPortfolio, filterDLQBacklogRows } from './dlqBacklogPortfolio';

const NOW = Date.parse('2026-09-05T10:00:00Z');
const group = (overrides: Partial<DLQGroup> = {}): DLQGroup => ({
  groupName: 'orders-consumer',
  dlqTopic: '%DLQ%orders-consumer',
  messageCount: 10,
  lastEnqueueTime: '2026-09-05T09:30:00Z',
  retryCount: 3,
  status: 'active',
  statsAvailable: true,
  ...overrides,
});

describe('DLQ backlog portfolio', () => {
  it('summarizes available backlog and retry counts', () => {
    const result = buildDLQBacklogPortfolio(
      [group(), group({ groupName: 'payment', messageCount: 30, retryCount: 5 })],
      NOW,
    );
    expect(result.summary).toMatchObject({
      groups: 2,
      availableGroups: 2,
      groupsWithBacklog: 2,
      totalMessages: 40,
      largestGroupMessages: 30,
      averageMessages: 20,
      retryCount: 8,
    });
    expect(result.rows[0].groupName).toBe('payment');
    expect(result.rows[0].backlogShare).toBe(75);
  });

  it('classifies all documented age buckets', () => {
    const result = buildDLQBacklogPortfolio(
      [
        group({ groupName: 'empty', messageCount: 0 }),
        group({ groupName: 'hour', lastEnqueueTime: '2026-09-05T09:30:00Z' }),
        group({ groupName: 'today', lastEnqueueTime: '2026-09-05T05:00:00Z' }),
        group({ groupName: 'week', lastEnqueueTime: '2026-09-03T10:00:00Z' }),
        group({ groupName: 'dormant', lastEnqueueTime: '2026-08-01T10:00:00Z' }),
        group({ groupName: 'unknown', lastEnqueueTime: 'invalid' }),
        group({ groupName: 'unavailable', statsAvailable: false }),
      ],
      NOW,
    );
    expect(new Set(result.rows.map((row) => row.ageBucket))).toEqual(
      new Set(['EMPTY', 'LAST_HOUR', 'TODAY', 'THIS_WEEK', 'DORMANT', 'UNKNOWN', 'UNAVAILABLE']),
    );
    expect(result.summary).toMatchObject({
      dormantGroups: 1,
      unknownAgeGroups: 1,
      unavailableGroups: 1,
    });
  });

  it('does not include unavailable group counters in totals', () => {
    const result = buildDLQBacklogPortfolio(
      [
        group({ messageCount: 10 }),
        group({
          groupName: 'unavailable',
          messageCount: 9999,
          retryCount: 9999,
          statsAvailable: false,
        }),
      ],
      NOW,
    );
    expect(result.summary).toMatchObject({ totalMessages: 10, retryCount: 3, availableGroups: 1 });
  });

  it('normalizes negative and non-finite counters', () => {
    const result = buildDLQBacklogPortfolio(
      [
        group({ messageCount: -1, retryCount: Number.NaN }),
        group({ groupName: 'infinite', messageCount: Number.POSITIVE_INFINITY }),
      ],
      NOW,
    );
    expect(result.summary).toMatchObject({ totalMessages: 0, retryCount: 3, groupsWithBacklog: 0 });
  });

  it('clamps future enqueue times to the newest age bucket', () => {
    const result = buildDLQBacklogPortfolio(
      [group({ lastEnqueueTime: '2026-09-06T10:00:00Z' })],
      NOW,
    );
    expect(result.rows[0]).toMatchObject({ ageBucket: 'LAST_HOUR', ageMs: 0 });
  });

  it('builds status groups with message totals', () => {
    const result = buildDLQBacklogPortfolio(
      [
        group({ status: 'active', messageCount: 30 }),
        group({ groupName: 'two', status: 'paused', messageCount: 10 }),
        group({ groupName: 'three', status: 'active', messageCount: 20 }),
      ],
      NOW,
    );
    expect(result.statusBuckets).toEqual([
      { status: 'active', groups: 2, messages: 50 },
      { status: 'paused', groups: 1, messages: 10 },
    ]);
  });

  it('builds ordered age bucket totals and percentages', () => {
    const result = buildDLQBacklogPortfolio(
      [
        group({ groupName: 'recent', messageCount: 25 }),
        group({ groupName: 'old', messageCount: 75, lastEnqueueTime: '2026-08-01T00:00:00Z' }),
      ],
      NOW,
    );
    expect(result.ageBuckets[0]).toMatchObject({
      bucket: 'DORMANT',
      groups: 1,
      messages: 75,
      percent: 75,
    });
    expect(result.ageBuckets.reduce((sum, bucket) => sum + bucket.messages, 0)).toBe(100);
  });

  it('filters by age and across group, topic, and status', () => {
    const rows = buildDLQBacklogPortfolio(
      [
        group({ groupName: 'order-prod', status: 'ACTIVE' }),
        group({
          groupName: 'payment',
          dlqTopic: '%DLQ%billing',
          lastEnqueueTime: '2026-08-01T00:00:00Z',
          status: 'PAUSED',
        }),
      ],
      NOW,
    ).rows;
    expect(filterDLQBacklogRows(rows, '', 'DORMANT')).toHaveLength(1);
    expect(filterDLQBacklogRows(rows, 'ORDER')).toHaveLength(1);
    expect(filterDLQBacklogRows(rows, 'billing')).toHaveLength(1);
    expect(filterDLQBacklogRows(rows, 'active')).toHaveLength(1);
  });

  it('returns stable empty totals', () => {
    expect(buildDLQBacklogPortfolio([], NOW).summary).toEqual({
      groups: 0,
      availableGroups: 0,
      unavailableGroups: 0,
      groupsWithBacklog: 0,
      totalMessages: 0,
      largestGroupMessages: 0,
      averageMessages: 0,
      retryCount: 0,
      dormantGroups: 0,
      unknownAgeGroups: 0,
    });
  });
});
