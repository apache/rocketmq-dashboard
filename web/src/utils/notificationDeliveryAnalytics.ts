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

import type { NotificationDeliveryRecord } from '../api/ops';

export type DeliveryHealth = 'HEALTHY' | 'DEGRADED' | 'CRITICAL' | 'IN_FLIGHT';

export interface DeliveryAnalyticsRow extends NotificationDeliveryRecord {
  latencyMs: number | null;
  ageMs: number | null;
  health: DeliveryHealth;
  errorSignature: string;
}

export interface DeliveryChannelAnalytics {
  key: string;
  channel: string;
  total: number;
  delivered: number;
  failed: number;
  inFlight: number;
  retried: number;
  successRate: number;
  averageLatencyMs: number | null;
  p95LatencyMs: number | null;
}

export interface DeliveryErrorAnalytics {
  key: string;
  signature: string;
  channel: string;
  count: number;
  affectedAlerts: number;
  latestAt: string;
}

export interface NotificationDeliveryAnalytics {
  rows: DeliveryAnalyticsRow[];
  channels: DeliveryChannelAnalytics[];
  errors: DeliveryErrorAnalytics[];
  summary: {
    total: number;
    delivered: number;
    failed: number;
    inFlight: number;
    stuck: number;
    retried: number;
    successRate: number;
    p95LatencyMs: number | null;
  };
}

export interface DeliveryAnalyticsFilters {
  search: string;
  channel: string | 'ALL';
  health: DeliveryHealth | 'ALL';
  retriedOnly: boolean;
}

const timestamp = (value?: string | null): number | null => {
  if (!value) return null;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
};

const percentile = (values: number[], ratio: number): number | null => {
  if (values.length === 0) return null;
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1)] ?? null;
};

export const normalizeDeliveryError = (value?: string | null): string => {
  if (!value?.trim()) return '';
  return value
    .trim()
    .toLocaleLowerCase()
    .replace(/https?:\/\/[^\s]+/g, '<url>')
    .replace(/\b[0-9a-f]{16,}\b/gi, '<id>')
    .replace(/\b\d{3,}\b/g, '<n>')
    .replace(/\s+/g, ' ')
    .slice(0, 240);
};

const buildRow = (
  delivery: NotificationDeliveryRecord,
  referenceTimeMs: number,
  stuckAfterMs: number,
): DeliveryAnalyticsRow => {
  const createdAt = timestamp(delivery.createdAt);
  const deliveredAt = timestamp(delivery.deliveredAt);
  const latencyMs =
    createdAt !== null && deliveredAt !== null && deliveredAt >= createdAt
      ? deliveredAt - createdAt
      : null;
  const ageMs = createdAt === null ? null : Math.max(0, referenceTimeMs - createdAt);
  const inFlight = ['PENDING', 'SENDING', 'RETRY_WAIT'].includes(delivery.status);
  const health: DeliveryHealth =
    delivery.status === 'FAILED'
      ? 'CRITICAL'
      : inFlight && ageMs !== null && ageMs >= stuckAfterMs
        ? 'DEGRADED'
        : inFlight
          ? 'IN_FLIGHT'
          : 'HEALTHY';
  return {
    ...delivery,
    latencyMs,
    ageMs,
    health,
    errorSignature: normalizeDeliveryError(delivery.lastError),
  };
};

const channelAnalytics = (rows: DeliveryAnalyticsRow[]): DeliveryChannelAnalytics[] => {
  const groups = new Map<string, DeliveryAnalyticsRow[]>();
  rows.forEach((row) => groups.set(row.channel, [...(groups.get(row.channel) ?? []), row]));
  return [...groups.entries()]
    .map(([channel, items]) => {
      const delivered = items.filter((item) => item.status === 'DELIVERED').length;
      const failed = items.filter((item) => item.status === 'FAILED').length;
      const terminal = delivered + failed;
      const latencies = items.flatMap((item) => (item.latencyMs === null ? [] : [item.latencyMs]));
      return {
        key: channel,
        channel,
        total: items.length,
        delivered,
        failed,
        inFlight: items.length - terminal,
        retried: items.filter((item) => item.attemptCount > 1).length,
        successRate: terminal === 0 ? 0 : Math.round((delivered / terminal) * 1000) / 10,
        averageLatencyMs:
          latencies.length === 0
            ? null
            : Math.round(latencies.reduce((sum, value) => sum + value, 0) / latencies.length),
        p95LatencyMs: percentile(latencies, 0.95),
      };
    })
    .sort(
      (left, right) =>
        left.successRate - right.successRate || left.channel.localeCompare(right.channel),
    );
};

const errorAnalytics = (rows: DeliveryAnalyticsRow[]): DeliveryErrorAnalytics[] => {
  const groups = new Map<string, DeliveryAnalyticsRow[]>();
  rows
    .filter((row) => row.errorSignature)
    .forEach((row) => {
      const key = `${row.channel}:${row.errorSignature}`;
      groups.set(key, [...(groups.get(key) ?? []), row]);
    });
  return [...groups.entries()]
    .map(([key, items]) => ({
      key,
      signature: items[0]?.errorSignature ?? '',
      channel: items[0]?.channel ?? '',
      count: items.length,
      affectedAlerts: new Set(items.map((item) => item.alertId)).size,
      latestAt: items.reduce(
        (latest, item) => (item.createdAt > latest ? item.createdAt : latest),
        '',
      ),
    }))
    .sort((left, right) => right.count - left.count || left.key.localeCompare(right.key));
};

export const analyzeNotificationDeliveries = (
  deliveries: NotificationDeliveryRecord[],
  referenceTimeMs: number,
  stuckAfterMs = 15 * 60 * 1000,
): NotificationDeliveryAnalytics => {
  const rows = deliveries
    .map((delivery) => buildRow(delivery, referenceTimeMs, stuckAfterMs))
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt) || right.id - left.id);
  const delivered = rows.filter((row) => row.status === 'DELIVERED').length;
  const failed = rows.filter((row) => row.status === 'FAILED').length;
  const terminal = delivered + failed;
  const latencies = rows.flatMap((row) => (row.latencyMs === null ? [] : [row.latencyMs]));
  return {
    rows,
    channels: channelAnalytics(rows),
    errors: errorAnalytics(rows),
    summary: {
      total: rows.length,
      delivered,
      failed,
      inFlight: rows.length - terminal,
      stuck: rows.filter((row) => row.health === 'DEGRADED').length,
      retried: rows.filter((row) => row.attemptCount > 1).length,
      successRate: terminal === 0 ? 0 : Math.round((delivered / terminal) * 1000) / 10,
      p95LatencyMs: percentile(latencies, 0.95),
    },
  };
};

export const filterDeliveryAnalyticsRows = (
  rows: DeliveryAnalyticsRow[],
  filters: DeliveryAnalyticsFilters,
): DeliveryAnalyticsRow[] => {
  const search = filters.search.trim().toLocaleLowerCase();
  return rows.filter(
    (row) =>
      (filters.channel === 'ALL' || row.channel === filters.channel) &&
      (filters.health === 'ALL' || row.health === filters.health) &&
      (!filters.retriedOnly || row.attemptCount > 1) &&
      (!search ||
        [row.alertTitle, row.instanceId, row.errorSignature, String(row.alertId)].some((value) =>
          value?.toLocaleLowerCase().includes(search),
        )),
  );
};
