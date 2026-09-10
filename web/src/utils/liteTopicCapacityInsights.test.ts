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
import type { LiteTopicItem, LiteTopicQuota } from '../api/liteTopic';
import {
  buildLiteTopicCapacityInsights,
  liteTopicCapacityLevelRank,
} from './liteTopicCapacityInsights';

const NOW = Date.UTC(2026, 8, 4, 10, 0, 0);

const quota = (overrides: Partial<LiteTopicQuota> = {}): LiteTopicQuota => ({
  currentTopicCount: 91,
  maxTopicCount: 100,
  currentSessionCount: 76,
  maxSessionCount: 100,
  currentCreationRate: 82,
  maxCreationRate: 100,
  usageRate: undefined,
  sessionUsageRate: undefined,
  ...overrides,
});

const topic = (overrides: Partial<LiteTopicItem> = {}): LiteTopicItem => ({
  namespace: 'default',
  topicPattern: 'orders-*',
  topicCount: 10,
  consumerCount: 2,
  totalBacklog: 0,
  averageTTL: 86_400_000,
  ttlStatus: 'ACTIVE',
  lastActiveTime: NOW - 3_600_000,
  sessionIds: ['session-a'],
  ...overrides,
});

describe('LiteTopic capacity insights', () => {
  it('classifies quota saturation, creation rate pressure, and TTL risks', () => {
    const insights = buildLiteTopicCapacityInsights(
      quota(),
      [
        topic({
          topicPattern: 'expired-*',
          ttlStatus: 'EXPIRED',
          totalBacklog: 120_000,
          sessionIds: ['session-a', 'session-b'],
        }),
        topic({
          topicPattern: 'expiring-*',
          ttlStatus: 'EXPIRING_SOON',
          totalBacklog: 12_000,
        }),
      ],
      NOW,
    );

    expect(insights.level).toBe('critical');
    expect(insights.topicUsagePercent).toBe(91);
    expect(insights.sessionUsagePercent).toBe(76);
    expect(insights.creationRatePercent).toBe(82);
    expect(insights.remainingTopicSlots).toBe(9);
    expect(insights.totalBacklog).toBe(132_000);
    expect(insights.issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining([
        'TOPIC_QUOTA_CRITICAL',
        'SESSION_QUOTA_WARNING',
        'CREATION_RATE_WARNING',
        'EXPIRED_PATTERNS',
        'EXPIRING_PATTERNS',
        'BACKLOG_HOTSPOT',
      ]),
    );
    expect(insights.recommendations).toEqual(
      expect.arrayContaining([
        'REQUEST_TOPIC_QUOTA',
        'REQUEST_SESSION_QUOTA',
        'THROTTLE_CREATION',
        'CLEAN_EXPIRED',
        'EXTEND_TTL',
        'DRAIN_BACKLOG',
      ]),
    );
  });

  it('uses provider ratios when present and accepts percent-shaped ratio values', () => {
    const insights = buildLiteTopicCapacityInsights(
      quota({
        currentTopicCount: 10,
        maxTopicCount: 100,
        currentSessionCount: 10,
        maxSessionCount: 100,
        usageRate: 0.805,
        sessionUsageRate: 88,
      }),
      [topic()],
      NOW,
    );

    expect(insights.topicUsagePercent).toBe(80.5);
    expect(insights.sessionUsagePercent).toBe(88);
    expect(insights.issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining(['TOPIC_QUOTA_WARNING', 'SESSION_QUOTA_WARNING']),
    );
  });

  it('normalizes numeric API values before calculating insight totals', () => {
    const insights = buildLiteTopicCapacityInsights(
      quota({
        currentTopicCount: '45' as unknown as number,
        maxTopicCount: '50' as unknown as number,
        currentSessionCount: '-1' as unknown as number,
        maxSessionCount: '100' as unknown as number,
        usageRate: undefined,
        sessionUsageRate: undefined,
      }),
      [topic({ totalBacklog: '15000' as unknown as number })],
      NOW,
    );

    expect(insights.topicUsagePercent).toBe(90);
    expect(insights.sessionUsagePercent).toBe(0);
    expect(insights.totalBacklog).toBe(15_000);
    expect(insights.issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining(['TOPIC_QUOTA_CRITICAL', 'BACKLOG_HOTSPOT']),
    );
  });

  it('falls back to visible topic and session counts when quota is unavailable', () => {
    const insights = buildLiteTopicCapacityInsights(
      null,
      [
        topic({ topicPattern: 'a-*', topicCount: 3, sessionIds: ['a', 'b'] }),
        topic({ topicPattern: 'b-*', topicCount: 5, consumerCount: 4, sessionIds: ['c'] }),
      ],
      NOW,
    );

    expect(insights.totalPatterns).toBe(2);
    expect(insights.totalTopics).toBe(8);
    expect(insights.totalConsumers).toBe(6);
    expect(insights.totalSessions).toBe(3);
    expect(insights.topicUsagePercent).toBeNull();
    expect(insights.level).toBe('healthy');
  });

  it('flags long-idle and unknown-status patterns without marking them critical', () => {
    const insights = buildLiteTopicCapacityInsights(
      quota({
        currentTopicCount: 20,
        maxTopicCount: 100,
        currentSessionCount: 10,
        maxSessionCount: 100,
        currentCreationRate: 1,
        maxCreationRate: 100,
      }),
      [
        topic({
          topicPattern: 'idle-*',
          totalBacklog: 0,
          lastActiveTime: NOW - 96 * 3_600_000,
        }),
        topic({
          topicPattern: 'mystery-*',
          ttlStatus: 'UNKNOWN',
          lastActiveTime: NOW - 30 * 60_000,
        }),
      ],
      NOW,
    );

    expect(insights.level).toBe('notice');
    expect(insights.idleCount).toBe(1);
    expect(insights.unknownStatusCount).toBe(1);
    expect(insights.issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining(['IDLE_PATTERNS', 'UNKNOWN_TTL_STATUS']),
    );
    expect(insights.recommendations).toEqual(
      expect.arrayContaining(['REVIEW_IDLE_PATTERNS', 'CHECK_UNKNOWN_STATUS']),
    );
  });

  it('keeps full idle counts even when the insight table is capped', () => {
    const idleTopics = Array.from({ length: 7 }, (_, index) =>
      topic({
        topicPattern: `idle-${index}-*`,
        totalBacklog: 0,
        lastActiveTime: NOW - (100 + index) * 3_600_000,
      }),
    );

    const insights = buildLiteTopicCapacityInsights(
      quota({
        currentTopicCount: 20,
        maxTopicCount: 100,
        currentSessionCount: 10,
        maxSessionCount: 100,
        currentCreationRate: 1,
        maxCreationRate: 100,
      }),
      idleTopics,
      NOW,
    );

    expect(insights.idleCount).toBe(7);
    expect(insights.idlePatterns).toHaveLength(5);
    expect(insights.issues).toEqual(
      expect.arrayContaining([expect.objectContaining({ code: 'IDLE_PATTERNS', count: 7 })]),
    );
  });

  it('sorts hotspot and namespace signals by severity, backlog, and stable names', () => {
    const insights = buildLiteTopicCapacityInsights(
      quota({ currentTopicCount: 30, maxTopicCount: 100, currentSessionCount: 3 }),
      [
        topic({
          namespace: 'beta',
          topicPattern: 'medium-*',
          topicCount: 4,
          totalBacklog: 15_000,
          ttlStatus: 'ACTIVE',
        }),
        topic({
          namespace: 'alpha',
          topicPattern: 'critical-*',
          topicCount: 8,
          totalBacklog: 200_000,
          ttlStatus: 'ACTIVE',
        }),
        topic({
          namespace: 'alpha',
          topicPattern: 'expired-*',
          topicCount: 2,
          totalBacklog: 0,
          ttlStatus: 'EXPIRED',
        }),
      ],
      NOW,
    );

    expect(insights.backlogHotspots.map((signal) => signal.topicPattern)).toEqual([
      'critical-*',
      'medium-*',
    ]);
    expect(insights.namespaceSignals[0]).toEqual(
      expect.objectContaining({
        namespace: 'alpha',
        patternCount: 2,
        topicCount: 10,
        level: 'critical',
      }),
    );
  });

  it('exposes a stable severity rank for UI sorting', () => {
    expect(liteTopicCapacityLevelRank('healthy')).toBeLessThan(
      liteTopicCapacityLevelRank('warning'),
    );
    expect(liteTopicCapacityLevelRank('critical')).toBeGreaterThan(
      liteTopicCapacityLevelRank('notice'),
    );
  });
});
