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
import type { SystemAlert } from '../api/ops';
import {
  analyzeSystemAlertIncidents,
  filterSystemAlertIncidents,
  systemAlertCorrelationKey,
} from './systemAlertIncidents';

const alert = (overrides: Partial<SystemAlert>): SystemAlert => ({
  id: 1,
  level: 'warning',
  title: 'Consumer lag',
  description: '',
  time: '2026-09-04T00:00:00Z',
  acknowledged: false,
  domain: 'BUSINESS',
  ruleId: 10,
  fingerprint: 'lag:orders',
  transition: 'FIRING',
  instanceId: 'production',
  ...overrides,
});

describe('systemAlertIncidents', () => {
  it('prefers explicit fingerprints as correlation evidence', () => {
    expect(systemAlertCorrelationKey(alert({ fingerprint: ' fp-1 ' }))).toEqual({
      key: 'fingerprint:fp-1',
      source: 'FINGERPRINT',
    });
  });

  it('uses a stable rule, instance, and sorted-label fallback', () => {
    const correlation = systemAlertCorrelationKey(
      alert({ fingerprint: null, labels: { topic: 'orders', group: 'workers' } }),
    );
    expect(correlation.source).toBe('RULE_SCOPE');
    expect(correlation.key).toContain('group=workers,topic=orders');
  });

  it('keeps alerts without fingerprint or rule evidence isolated', () => {
    expect(systemAlertCorrelationKey(alert({ fingerprint: null, ruleId: null, id: 99 }))).toEqual({
      key: 'alert:99',
      source: 'ISOLATED',
    });
  });

  it('builds a resolved incident timeline with duration and counts', () => {
    const analysis = analyzeSystemAlertIncidents([
      alert({ id: 1, time: '2026-09-04T00:00:00Z', transition: 'FIRING' }),
      alert({ id: 2, time: '2026-09-04T00:05:00Z', transition: 'FIRING', acknowledged: true }),
      alert({ id: 3, time: '2026-09-04T00:10:00Z', transition: 'RESOLVED' }),
    ]);
    expect(analysis.incidents[0]).toMatchObject({
      status: 'RESOLVED',
      eventCount: 3,
      firingCount: 2,
      resolvedCount: 1,
      acknowledgedCount: 1,
      durationMs: 600_000,
    });
  });

  it('selects the highest severity and sorts active incidents first', () => {
    const analysis = analyzeSystemAlertIncidents([
      alert({ id: 1, fingerprint: 'resolved', transition: 'RESOLVED' }),
      alert({ id: 2, fingerprint: 'active', level: 'error', transition: 'FIRING' }),
    ]);
    expect(analysis.incidents.map((incident) => incident.status)).toEqual(['ACTIVE', 'RESOLVED']);
    expect(analysis.incidents[0].level).toBe('error');
  });

  it('does not fabricate duration when timestamps are invalid', () => {
    const analysis = analyzeSystemAlertIncidents([
      alert({ id: 1, time: 'invalid' }),
      alert({ id: 2, time: 'also-invalid', transition: 'RESOLVED' }),
    ]);
    expect(analysis.incidents[0].durationMs).toBeNull();
    expect(analysis.summary.longestDurationMs).toBeNull();
  });

  it('summarizes suppressed and unacknowledged events', () => {
    const analysis = analyzeSystemAlertIncidents([
      alert({ id: 1, notificationSuppressed: true }),
      alert({ id: 2, acknowledged: true, transition: 'RESOLVED' }),
    ]);
    expect(analysis.summary).toMatchObject({
      incidents: 1,
      resolved: 1,
      suppressedEvents: 1,
      unacknowledgedEvents: 1,
    });
  });

  it('filters incidents by status, domain, level, acknowledgement, and text', () => {
    const incidents = analyzeSystemAlertIncidents([
      alert({ id: 1, fingerprint: 'active-business', level: 'error' }),
      alert({
        id: 2,
        fingerprint: 'resolved-cluster',
        domain: 'CLUSTER',
        transition: 'RESOLVED',
        acknowledged: true,
        title: 'Disk usage',
      }),
    ]).incidents;
    const base = {
      search: '',
      status: 'ALL' as const,
      domain: 'ALL' as const,
      level: 'ALL',
      unacknowledgedOnly: false,
    };
    expect(filterSystemAlertIncidents(incidents, { ...base, status: 'ACTIVE' })).toHaveLength(1);
    expect(filterSystemAlertIncidents(incidents, { ...base, domain: 'CLUSTER' })[0].title).toBe(
      'Disk usage',
    );
    expect(filterSystemAlertIncidents(incidents, { ...base, level: 'error' })[0].status).toBe(
      'ACTIVE',
    );
    expect(
      filterSystemAlertIncidents(incidents, { ...base, unacknowledgedOnly: true }),
    ).toHaveLength(1);
    expect(filterSystemAlertIncidents(incidents, { ...base, search: 'disk' })[0].domain).toBe(
      'CLUSTER',
    );
  });
});
