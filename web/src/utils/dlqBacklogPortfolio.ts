/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import type { DLQGroup } from '../api/message';

export type DLQAgeBucket =
  'EMPTY' | 'LAST_HOUR' | 'TODAY' | 'THIS_WEEK' | 'DORMANT' | 'UNKNOWN' | 'UNAVAILABLE';
export interface DLQBacklogRow extends DLQGroup {
  ageBucket: DLQAgeBucket;
  ageMs: number | null;
  backlogShare: number;
}
export interface DLQBacklogPortfolio {
  rows: DLQBacklogRow[];
  ageBuckets: Array<{ bucket: DLQAgeBucket; groups: number; messages: number; percent: number }>;
  statusBuckets: Array<{ status: string; groups: number; messages: number }>;
  summary: {
    groups: number;
    availableGroups: number;
    unavailableGroups: number;
    groupsWithBacklog: number;
    totalMessages: number;
    largestGroupMessages: number;
    averageMessages: number;
    retryCount: number;
    dormantGroups: number;
    unknownAgeGroups: number;
  };
}
const safeCount = (value: number) => (Number.isFinite(value) && value > 0 ? Math.floor(value) : 0);
const parsedTime = (value?: string | null) => {
  if (!value) return null;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
};
const classifyAge = (
  group: DLQGroup,
  now: number,
): { bucket: DLQAgeBucket; ageMs: number | null } => {
  if (group.statsAvailable === false) return { bucket: 'UNAVAILABLE', ageMs: null };
  if (safeCount(group.messageCount) === 0) return { bucket: 'EMPTY', ageMs: null };
  const time = parsedTime(group.lastEnqueueTime);
  if (time === null) return { bucket: 'UNKNOWN', ageMs: null };
  const ageMs = Math.max(0, now - time);
  if (ageMs < 60 * 60 * 1000) return { bucket: 'LAST_HOUR', ageMs };
  if (ageMs < 24 * 60 * 60 * 1000) return { bucket: 'TODAY', ageMs };
  if (ageMs < 7 * 24 * 60 * 60 * 1000) return { bucket: 'THIS_WEEK', ageMs };
  return { bucket: 'DORMANT', ageMs };
};
const percent = (value: number, total: number) =>
  total ? Number(((value * 100) / total).toFixed(2)) : 0;
const ageOrder: DLQAgeBucket[] = [
  'DORMANT',
  'UNKNOWN',
  'THIS_WEEK',
  'TODAY',
  'LAST_HOUR',
  'EMPTY',
  'UNAVAILABLE',
];

export const buildDLQBacklogPortfolio = (
  groups: DLQGroup[],
  now = Date.now(),
): DLQBacklogPortfolio => {
  const totalMessages = groups.reduce(
    (sum, group) => sum + (group.statsAvailable === false ? 0 : safeCount(group.messageCount)),
    0,
  );
  const rows = groups
    .map<DLQBacklogRow>((group) => {
      const age = classifyAge(group, now);
      const messages = group.statsAvailable === false ? 0 : safeCount(group.messageCount);
      return {
        ...group,
        messageCount: messages,
        retryCount: safeCount(group.retryCount),
        ageBucket: age.bucket,
        ageMs: age.ageMs,
        backlogShare: percent(messages, totalMessages),
      };
    })
    .sort((a, b) => b.messageCount - a.messageCount || a.groupName.localeCompare(b.groupName));
  const ageBuckets = ageOrder.map((bucket) => {
    const matches = rows.filter((row) => row.ageBucket === bucket);
    const messages = matches.reduce((sum, row) => sum + row.messageCount, 0);
    return { bucket, groups: matches.length, messages, percent: percent(messages, totalMessages) };
  });
  const statusMap = new Map<string, DLQBacklogRow[]>();
  rows.forEach((row) =>
    statusMap.set(row.status || '(unknown)', [
      ...(statusMap.get(row.status || '(unknown)') ?? []),
      row,
    ]),
  );
  const statusBuckets = [...statusMap.entries()]
    .map(([status, matches]) => ({
      status,
      groups: matches.length,
      messages: matches.reduce((sum, row) => sum + row.messageCount, 0),
    }))
    .sort((a, b) => b.messages - a.messages || a.status.localeCompare(b.status));
  const available = rows.filter((row) => row.statsAvailable !== false);
  return {
    rows,
    ageBuckets,
    statusBuckets,
    summary: {
      groups: rows.length,
      availableGroups: available.length,
      unavailableGroups: rows.length - available.length,
      groupsWithBacklog: available.filter((row) => row.messageCount > 0).length,
      totalMessages,
      largestGroupMessages: Math.max(0, ...available.map((row) => row.messageCount)),
      averageMessages: available.length ? Math.round(totalMessages / available.length) : 0,
      retryCount: available.reduce((sum, row) => sum + row.retryCount, 0),
      dormantGroups: rows.filter((row) => row.ageBucket === 'DORMANT').length,
      unknownAgeGroups: rows.filter((row) => row.ageBucket === 'UNKNOWN').length,
    },
  };
};

export const filterDLQBacklogRows = (rows: DLQBacklogRow[], search = '', bucket?: DLQAgeBucket) => {
  const keyword = search.trim().toLocaleLowerCase();
  return rows
    .filter((row) => !bucket || row.ageBucket === bucket)
    .filter(
      (row) =>
        !keyword ||
        [row.groupName, row.dlqTopic, row.status].join('\n').toLocaleLowerCase().includes(keyword),
    );
};
