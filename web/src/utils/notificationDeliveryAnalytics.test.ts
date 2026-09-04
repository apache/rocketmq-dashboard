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
import type { NotificationDeliveryRecord } from '../api/ops';
import {
  analyzeNotificationDeliveries,
  filterDeliveryAnalyticsRows,
  normalizeDeliveryError,
} from './notificationDeliveryAnalytics';

const delivery = (overrides: Partial<NotificationDeliveryRecord>): NotificationDeliveryRecord => ({
  id: 1,
  alertId: 10,
  alertTitle: 'Broker unavailable',
  channel: 'dingtalk',
  status: 'DELIVERED',
  attemptCount: 1,
  createdAt: '2026-09-04T00:00:00Z',
  deliveredAt: '2026-09-04T00:00:02Z',
  ...overrides,
});

describe('notificationDeliveryAnalytics', () => {
  it('calculates terminal success rate and latency percentiles', () => {
    const analytics = analyzeNotificationDeliveries(
      [
        delivery({ id: 1, deliveredAt: '2026-09-04T00:00:01Z' }),
        delivery({ id: 2, deliveredAt: '2026-09-04T00:00:03Z' }),
        delivery({ id: 3, status: 'FAILED', deliveredAt: null, lastError: 'timeout' }),
        delivery({ id: 4, status: 'PENDING', deliveredAt: null }),
      ],
      Date.parse('2026-09-04T00:05:00Z'),
    );
    expect(analytics.summary).toMatchObject({
      total: 4,
      delivered: 2,
      failed: 1,
      inFlight: 1,
      successRate: 66.7,
      p95LatencyMs: 3000,
    });
  });

  it('marks old in-flight records as degraded without calling them failed', () => {
    const analytics = analyzeNotificationDeliveries(
      [delivery({ status: 'RETRY_WAIT', deliveredAt: null })],
      Date.parse('2026-09-04T00:20:00Z'),
    );
    expect(analytics.rows[0]).toMatchObject({ health: 'DEGRADED', ageMs: 1_200_000 });
    expect(analytics.summary).toMatchObject({ failed: 0, stuck: 1, inFlight: 1 });
  });

  it('does not manufacture latency from invalid or reversed timestamps', () => {
    const analytics = analyzeNotificationDeliveries(
      [
        delivery({ id: 1, createdAt: 'invalid' }),
        delivery({ id: 2, deliveredAt: '2026-09-03T23:59:00Z' }),
      ],
      Date.now(),
    );
    expect(analytics.rows.every((row) => row.latencyMs === null)).toBe(true);
    expect(analytics.summary.p95LatencyMs).toBeNull();
  });

  it('normalizes volatile identifiers, URLs, numbers, and whitespace in errors', () => {
    expect(
      normalizeDeliveryError(' HTTP 503 at https://hooks.test/a/12345  Request ABCDEF1234567890 '),
    ).toBe('http <n> at <url> request <id>');
  });

  it('groups recurring failures by channel and normalized signature', () => {
    const analytics = analyzeNotificationDeliveries(
      [
        delivery({ id: 1, alertId: 10, status: 'FAILED', lastError: 'HTTP 503 request 1001' }),
        delivery({ id: 2, alertId: 11, status: 'FAILED', lastError: 'http 504 request 9999' }),
        delivery({
          id: 3,
          alertId: 11,
          channel: 'email',
          status: 'FAILED',
          lastError: 'HTTP 503 request 2002',
        }),
      ],
      Date.now(),
    );
    expect(analytics.errors).toHaveLength(2);
    expect(analytics.errors[0]).toMatchObject({ channel: 'dingtalk', count: 2, affectedAlerts: 2 });
  });

  it('builds channel-level retry and latency aggregates', () => {
    const analytics = analyzeNotificationDeliveries(
      [
        delivery({ id: 1, attemptCount: 2 }),
        delivery({ id: 2, channel: 'email', status: 'FAILED', deliveredAt: null }),
      ],
      Date.now(),
    );
    expect(analytics.channels.find((item) => item.channel === 'dingtalk')).toMatchObject({
      successRate: 100,
      retried: 1,
      averageLatencyMs: 2000,
    });
    expect(analytics.channels.find((item) => item.channel === 'email')?.successRate).toBe(0);
  });

  it('filters rows by channel, health, retry state, and text', () => {
    const analytics = analyzeNotificationDeliveries(
      [
        delivery({ id: 1, attemptCount: 2 }),
        delivery({ id: 2, channel: 'email', status: 'FAILED', lastError: 'SMTP denied' }),
      ],
      Date.now(),
    );
    const base = {
      search: '',
      channel: 'ALL' as const,
      health: 'ALL' as const,
      retriedOnly: false,
    };
    expect(filterDeliveryAnalyticsRows(analytics.rows, { ...base, channel: 'email' })).toHaveLength(
      1,
    );
    expect(filterDeliveryAnalyticsRows(analytics.rows, { ...base, health: 'CRITICAL' })[0].id).toBe(
      2,
    );
    expect(filterDeliveryAnalyticsRows(analytics.rows, { ...base, retriedOnly: true })[0].id).toBe(
      1,
    );
    expect(filterDeliveryAnalyticsRows(analytics.rows, { ...base, search: 'smtp' })[0].id).toBe(2);
  });
});
