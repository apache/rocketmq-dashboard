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
import type { AuditSummary } from '../../../api/audit';
import type { AuditRecord } from '../../../api/ops';
import {
  buildAuditRiskInsights,
  isFailedAuditResult,
  isHighRiskAuditOperation,
  isPartialAuditResult,
  normalizeAuditResult,
} from '../auditRiskInsightModel';

const summary = (overrides: Partial<AuditSummary> = {}): AuditSummary => ({
  total: 10,
  successful: 6,
  failed: 3,
  partial: 1,
  uniqueOperators: 3,
  latestAt: '2026-08-01 10:00:00',
  byOperation: [],
  byResourceType: [],
  ...overrides,
});

const record = (overrides: Partial<AuditRecord> = {}): AuditRecord => ({
  id: 1,
  timestamp: '2026-08-01 10:00:00',
  operator: 'admin',
  operationType: 'CREATE_TOPIC',
  resourceType: 'TOPIC',
  target: 'orders',
  clusterId: 'prod-cn',
  detail: '',
  result: 'SUCCESS',
  errorMessage: '',
  ...overrides,
});

describe('audit risk insights', () => {
  it('normalizes backend result aliases before classifying failures', () => {
    expect(normalizeAuditResult(' failure ')).toBe('FAILED');
    expect(isFailedAuditResult('FAILURE')).toBe(true);
    expect(isFailedAuditResult('FAILED')).toBe(true);
    expect(isPartialAuditResult('partial')).toBe(true);
    expect(isFailedAuditResult('SUCCESS')).toBe(false);
  });

  it('classifies destructive and control-plane operations as high-risk signals', () => {
    expect(isHighRiskAuditOperation('DELETE_TOPIC')).toBe(true);
    expect(isHighRiskAuditOperation('RESET_OFFSET')).toBe(true);
    expect(isHighRiskAuditOperation('REMOVE_PROXY_ADDRESS')).toBe(true);
    expect(isHighRiskAuditOperation('UPDATE_CLUSTER_CONFIG')).toBe(true);
    expect(isHighRiskAuditOperation('SEND_MESSAGE')).toBe(false);
  });

  it('combines filtered summary rates with current-page risky records', () => {
    const insights = buildAuditRiskInsights(
      summary({ total: 10, successful: 5, failed: 4, partial: 1 }),
      [
        record({
          id: 1,
          operationType: 'DELETE_TOPIC',
          target: 'orders',
          result: 'FAILED',
          timestamp: '2026-08-01 10:05:00',
        }),
        record({
          id: 2,
          operationType: 'DELETE_TOPIC',
          target: 'orders',
          result: 'FAILED',
          timestamp: '2026-08-01 10:06:00',
        }),
        record({
          id: 3,
          operationType: 'RELOAD_PROXY_CONFIG',
          resourceType: 'PROXY',
          target: '10.0.0.1:8081',
          result: 'PARTIAL',
          timestamp: '2026-08-01 10:07:00',
        }),
        record({
          id: 4,
          operationType: 'SEND_MESSAGE',
          resourceType: 'MESSAGE',
          target: 'orders',
          result: 'SUCCESS',
        }),
      ],
    );

    expect(insights.level).toBe('critical');
    expect(insights.failureRate).toBe(40);
    expect(insights.partialRate).toBe(10);
    expect(insights.highRiskFailureCount).toBe(2);
    expect(insights.controlPlaneFailureCount).toBe(3);
    expect(insights.hotTargets[0]).toEqual(
      expect.objectContaining({
        target: 'orders',
        failed: 2,
        partial: 0,
        level: 'warning',
      }),
    );
    expect(insights.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ code: 'HIGH_FAILURE_RATE', level: 'critical' }),
        expect.objectContaining({ code: 'HIGH_RISK_FAILURES', level: 'critical' }),
        expect.objectContaining({ code: 'REPEATED_TARGET_FAILURES', target: 'orders' }),
      ]),
    );
  });

  it('detects operator concentration without marking it as an error', () => {
    const insights = buildAuditRiskInsights(null, [
      record({ id: 1, operator: 'ops-a' }),
      record({ id: 2, operator: 'ops-a', target: 'payments' }),
      record({ id: 3, operator: 'ops-a', target: 'refunds' }),
      record({ id: 4, operator: 'ops-b', target: 'invoices' }),
    ]);

    expect(insights.level).toBe('notice');
    expect(insights.topOperator).toEqual(
      expect.objectContaining({
        name: 'ops-a',
        count: 3,
        percent: 75,
        level: 'notice',
      }),
    );
    expect(insights.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ code: 'OPERATOR_CONCENTRATION', level: 'notice' }),
      ]),
    );
  });

  it('falls back to current-page records when server summary is unavailable', () => {
    const insights = buildAuditRiskInsights(null, [
      record({ id: 1, result: 'FAILED' }),
      record({ id: 2, result: 'PARTIAL' }),
      record({ id: 3, result: 'SUCCESS' }),
    ]);

    expect(insights.total).toBe(3);
    expect(insights.failed).toBe(1);
    expect(insights.partial).toBe(1);
    expect(insights.failureRate).toBe(33.3);
    expect(insights.partialRate).toBe(33.3);
  });

  it('does not list successful low-risk control-plane records as risky records', () => {
    const insights = buildAuditRiskInsights(null, [
      record({
        id: 1,
        operationType: 'CREATE_TOPIC',
        resourceType: 'TOPIC',
        result: 'SUCCESS',
      }),
      record({
        id: 2,
        operationType: 'DELETE_TOPIC',
        resourceType: 'TOPIC',
        result: 'SUCCESS',
      }),
    ]);

    expect(insights.controlPlaneFailureCount).toBe(0);
    expect(insights.riskyRecords).toEqual([
      expect.objectContaining({
        id: 2,
        operationType: 'DELETE_TOPIC',
        reason: 'high-risk',
      }),
    ]);
  });

  it('reports an empty filtered result as a notice', () => {
    const insights = buildAuditRiskInsights(
      summary({ total: 0, successful: 0, failed: 0, partial: 0 }),
      [],
    );

    expect(insights.level).toBe('notice');
    expect(insights.issues).toEqual([
      expect.objectContaining({ code: 'NO_MATCHING_RECORDS', level: 'notice' }),
    ]);
    expect(insights.hotTargets).toEqual([]);
    expect(insights.riskyRecords).toEqual([]);
  });
});
