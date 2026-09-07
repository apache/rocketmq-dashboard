/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import type { MessageRecord } from '../api/message';

export interface MessageDimensionRow {
  dimension: 'TAG' | 'BROKER' | 'QUEUE' | 'BORN_HOST' | 'STORE_HOST' | 'HOUR';
  value: string;
  count: number;
  percent: number;
  bytes: number;
}
export interface MessageResultInsights {
  dimensions: MessageDimensionRow[];
  summary: {
    loadedMessages: number;
    serverTotal: number;
    loadedPercent: number;
    totalBytes: number;
    averageBytes: number;
    largestBytes: number;
    uniqueTags: number;
    uniqueBrokers: number;
    uniqueQueues: number;
    firstStoreTime: number | null;
    lastStoreTime: number | null;
    invalidTimestamps: number;
    missingKeys: number;
    missingTags: number;
    missingRoutes: number;
  };
  sizeBuckets: Array<{ bucket: string; count: number; percent: number; bytes: number }>;
}

const timestamp = (value: number | string) => {
  const parsed = typeof value === 'number' ? value : Date.parse(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
};
const safeSize = (value: number) => (Number.isFinite(value) && value > 0 ? value : 0);
const percent = (count: number, total: number) =>
  total ? Number(((count * 100) / total).toFixed(2)) : 0;

const dimensionRows = (
  messages: MessageRecord[],
  dimension: MessageDimensionRow['dimension'],
  valueOf: (message: MessageRecord) => string,
) => {
  const groups = new Map<string, { count: number; bytes: number }>();
  messages.forEach((message) => {
    const value = valueOf(message) || '(missing)';
    const current = groups.get(value) ?? { count: 0, bytes: 0 };
    current.count += 1;
    current.bytes += safeSize(message.size);
    groups.set(value, current);
  });
  return [...groups.entries()]
    .map<MessageDimensionRow>(([value, group]) => ({
      dimension,
      value,
      count: group.count,
      percent: percent(group.count, messages.length),
      bytes: group.bytes,
    }))
    .sort((a, b) => b.count - a.count || a.value.localeCompare(b.value));
};

export const buildMessageResultInsights = (
  messages: MessageRecord[],
  serverTotal = messages.length,
): MessageResultInsights => {
  const validTimes = messages
    .map((message) => timestamp(message.storeTime))
    .filter((value): value is number => value !== null);
  const dimensions = [
    ...dimensionRows(messages, 'TAG', (message) => message.tag ?? '(missing)'),
    ...dimensionRows(messages, 'BROKER', (message) => message.brokerName ?? '(missing)'),
    ...dimensionRows(messages, 'QUEUE', (message) =>
      message.brokerName !== null && message.queueId !== null
        ? `${message.brokerName} / ${message.queueId}`
        : '(missing)',
    ),
    ...dimensionRows(messages, 'BORN_HOST', (message) => message.bornHost || '(missing)'),
    ...dimensionRows(messages, 'STORE_HOST', (message) => message.storeHost || '(missing)'),
    ...dimensionRows(messages, 'HOUR', (message) => {
      const value = timestamp(message.storeTime);
      if (value === null) return '(invalid)';
      return new Date(value).toISOString().slice(0, 13) + ':00Z';
    }),
  ];
  const bucketFor = (size: number) =>
    size < 1024
      ? '< 1 KiB'
      : size < 16 * 1024
        ? '1–16 KiB'
        : size < 128 * 1024
          ? '16–128 KiB'
          : size < 1024 * 1024
            ? '128 KiB–1 MiB'
            : '>= 1 MiB';
  const sizeGroups = new Map<string, { count: number; bytes: number }>();
  messages.forEach((message) => {
    const bytes = safeSize(message.size);
    const bucket = bucketFor(bytes);
    const current = sizeGroups.get(bucket) ?? { count: 0, bytes: 0 };
    current.count += 1;
    current.bytes += bytes;
    sizeGroups.set(bucket, current);
  });
  const bucketOrder = ['< 1 KiB', '1–16 KiB', '16–128 KiB', '128 KiB–1 MiB', '>= 1 MiB'];
  const totalBytes = messages.reduce((sum, message) => sum + safeSize(message.size), 0);
  return {
    dimensions,
    summary: {
      loadedMessages: messages.length,
      serverTotal,
      loadedPercent: percent(messages.length, Math.max(serverTotal, messages.length)),
      totalBytes,
      averageBytes: messages.length ? Math.round(totalBytes / messages.length) : 0,
      largestBytes: Math.max(0, ...messages.map((message) => safeSize(message.size))),
      uniqueTags: new Set(messages.map((message) => message.tag).filter(Boolean)).size,
      uniqueBrokers: new Set(messages.map((message) => message.brokerName).filter(Boolean)).size,
      uniqueQueues: new Set(
        messages
          .map((message) => `${message.brokerName}/${message.queueId}`)
          .filter((value) => !value.includes('null')),
      ).size,
      firstStoreTime: validTimes.length ? Math.min(...validTimes) : null,
      lastStoreTime: validTimes.length ? Math.max(...validTimes) : null,
      invalidTimestamps: messages.length - validTimes.length,
      missingKeys: messages.filter((message) => !message.key).length,
      missingTags: messages.filter((message) => !message.tag).length,
      missingRoutes: messages.filter(
        (message) => message.brokerName === null || message.queueId === null,
      ).length,
    },
    sizeBuckets: bucketOrder.map((bucket) => ({
      bucket,
      count: sizeGroups.get(bucket)?.count ?? 0,
      percent: percent(sizeGroups.get(bucket)?.count ?? 0, messages.length),
      bytes: sizeGroups.get(bucket)?.bytes ?? 0,
    })),
  };
};

export const filterMessageDimensionRows = (
  rows: MessageDimensionRow[],
  dimension?: MessageDimensionRow['dimension'],
  search = '',
) => {
  const keyword = search.trim().toLocaleLowerCase();
  return rows
    .filter((row) => !dimension || row.dimension === dimension)
    .filter((row) => !keyword || row.value.toLocaleLowerCase().includes(keyword));
};
