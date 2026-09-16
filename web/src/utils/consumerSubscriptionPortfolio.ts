/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import type { ConsumerGroup, SubscriptionEntry } from '../api/metadata';

export type SubscriptionExpressionKind = 'ALL' | 'TAG_SET' | 'SQL' | 'EMPTY' | 'OTHER';
export interface ConsumerSubscriptionSnapshot {
  group: ConsumerGroup;
  subscriptions: SubscriptionEntry[];
  error: string | null;
}
export interface SubscriptionPortfolioProfile {
  key: string;
  expressionKind: SubscriptionExpressionKind;
  filterMode: string;
  type: string;
  consistency: string;
  subscriptionCount: number;
  groupCount: number;
  topicCount: number;
  inconsistentCount: number;
  sampleGroups: string[];
  sampleTopics: string[];
  sampleExpressions: string[];
}
export interface ConsumerSubscriptionPortfolio {
  profiles: SubscriptionPortfolioProfile[];
  snapshots: ConsumerSubscriptionSnapshot[];
  summary: {
    availableGroups: number;
    inspectedGroups: number;
    omittedGroups: number;
    failedGroups: number;
    subscriptions: number;
    topics: number;
    profiles: number;
    inconsistentSubscriptions: number;
    emptyGroups: number;
  };
}

const safeText = (value?: string | null, fallback = '-') => value?.trim() || fallback;
const uniqueSorted = (values: string[]) => [...new Set(values.filter(Boolean))].sort();
const isInconsistent = (value?: string | null) => {
  const normalized = safeText(value, '').toLowerCase();
  return ['false', 'inconsistent', 'mismatch', 'conflict', 'no'].includes(normalized);
};

export const classifySubscriptionExpression = (
  expression?: string | null,
  filterMode?: string | null,
): SubscriptionExpressionKind => {
  const normalized = safeText(expression, '');
  const mode = safeText(filterMode, '').toUpperCase();
  if (!normalized) return 'EMPTY';
  if (normalized === '*') return 'ALL';
  if (mode.includes('SQL') || /\b(?:AND|OR|BETWEEN|IN|IS|LIKE)\b|[<>=]/i.test(normalized)) {
    return 'SQL';
  }
  if (normalized.includes('||') || /^[\w%*.-]+$/.test(normalized)) return 'TAG_SET';
  return 'OTHER';
};

/** 以有界并发读取消费组订阅，单组失败不会终止整个组合报告。 */
export const loadConsumerSubscriptionSnapshots = async (
  groups: ConsumerGroup[],
  loader: (groupName: string) => Promise<SubscriptionEntry[]>,
  concurrency = 4,
  maxGroups = 100,
): Promise<{ snapshots: ConsumerSubscriptionSnapshot[]; omittedGroups: number }> => {
  const limit = Number.isFinite(maxGroups) ? Math.max(0, Math.floor(maxGroups)) : 100;
  const selected = [...groups].sort((a, b) => a.name.localeCompare(b.name)).slice(0, limit);
  const snapshots: ConsumerSubscriptionSnapshot[] = new Array(selected.length);
  const requestedWorkers = Number.isFinite(concurrency) ? Math.floor(concurrency) : 1;
  const workerCount = Math.min(Math.max(1, requestedWorkers), selected.length);
  let cursor = 0;
  const worker = async () => {
    while (cursor < selected.length) {
      const index = cursor;
      cursor += 1;
      const group = selected[index];
      try {
        snapshots[index] = { group, subscriptions: await loader(group.name), error: null };
      } catch (error) {
        snapshots[index] = {
          group,
          subscriptions: [],
          error: error instanceof Error && error.message ? error.message : 'Load failed',
        };
      }
    }
  };
  await Promise.all(Array.from({ length: workerCount }, worker));
  return { snapshots, omittedGroups: Math.max(0, groups.length - selected.length) };
};

interface ProfileAccumulator {
  expressionKind: SubscriptionExpressionKind;
  filterMode: string;
  type: string;
  consistency: string;
  entries: Array<{ group: string; subscription: SubscriptionEntry }>;
}

export const buildConsumerSubscriptionPortfolio = (
  snapshots: ConsumerSubscriptionSnapshot[],
  availableGroups = snapshots.length,
): ConsumerSubscriptionPortfolio => {
  const grouped = new Map<string, ProfileAccumulator>();
  snapshots.forEach((snapshot) => {
    if (snapshot.error) return;
    snapshot.subscriptions.forEach((subscription) => {
      const expressionKind = classifySubscriptionExpression(
        subscription.expression,
        subscription.filterMode,
      );
      const filterMode = safeText(subscription.filterMode, 'UNKNOWN').toUpperCase();
      const type = safeText(subscription.type, 'UNKNOWN').toUpperCase();
      const consistency = safeText(subscription.consistency, 'UNKNOWN').toUpperCase();
      const key = [expressionKind, filterMode, type, consistency].join('|');
      const current = grouped.get(key);
      if (current) {
        current.entries.push({ group: snapshot.group.name, subscription });
      } else {
        grouped.set(key, {
          expressionKind,
          filterMode,
          type,
          consistency,
          entries: [{ group: snapshot.group.name, subscription }],
        });
      }
    });
  });

  const profiles = [...grouped.entries()]
    .map(([key, profile]): SubscriptionPortfolioProfile => ({
      key,
      expressionKind: profile.expressionKind,
      filterMode: profile.filterMode,
      type: profile.type,
      consistency: profile.consistency,
      subscriptionCount: profile.entries.length,
      groupCount: uniqueSorted(profile.entries.map((entry) => entry.group)).length,
      topicCount: uniqueSorted(profile.entries.map((entry) => entry.subscription.topic)).length,
      inconsistentCount: profile.entries.filter((entry) =>
        isInconsistent(entry.subscription.consistency),
      ).length,
      sampleGroups: uniqueSorted(profile.entries.map((entry) => entry.group)).slice(0, 5),
      sampleTopics: uniqueSorted(profile.entries.map((entry) => entry.subscription.topic)).slice(
        0,
        5,
      ),
      sampleExpressions: uniqueSorted(
        profile.entries.map((entry) => safeText(entry.subscription.expression)),
      ).slice(0, 5),
    }))
    .sort((a, b) => b.subscriptionCount - a.subscriptionCount || a.key.localeCompare(b.key));
  const successful = snapshots.filter((snapshot) => !snapshot.error);
  const subscriptions = successful.flatMap((snapshot) => snapshot.subscriptions);

  return {
    profiles,
    snapshots,
    summary: {
      availableGroups,
      inspectedGroups: snapshots.length,
      omittedGroups: Math.max(0, availableGroups - snapshots.length),
      failedGroups: snapshots.filter((snapshot) => snapshot.error).length,
      subscriptions: subscriptions.length,
      topics: uniqueSorted(subscriptions.map((subscription) => subscription.topic)).length,
      profiles: profiles.length,
      inconsistentSubscriptions: subscriptions.filter((subscription) =>
        isInconsistent(subscription.consistency),
      ).length,
      emptyGroups: successful.filter((snapshot) => snapshot.subscriptions.length === 0).length,
    },
  };
};
