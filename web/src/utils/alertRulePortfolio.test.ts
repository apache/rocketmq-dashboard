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
import type { AlertRule } from '../api/ops';
import { analyzeAlertRulePortfolio, filterAlertPortfolioRows } from './alertRulePortfolio';

const rule = (overrides: Partial<AlertRule>): AlertRule => ({
  id: 1,
  name: 'Broker unavailable',
  metric: 'broker.availability',
  operator: 'UNAVAILABLE',
  threshold: 0,
  duration: '1m',
  channels: ['dingtalk'],
  enabled: true,
  lastTriggered: null,
  description: '',
  instanceId: 'production',
  ...overrides,
});

describe('alertRulePortfolio', () => {
  it('detects semantically identical enabled rules across duration spellings', () => {
    const portfolio = analyzeAlertRulePortfolio(
      [rule({ id: 1, duration: '1m' }), rule({ id: 2, name: 'Broker down', duration: '60s' })],
      [{ name: 'production' }],
    );
    expect(portfolio.summary.exactDuplicateGroups).toBe(1);
    expect(portfolio.issues[0]).toMatchObject({
      code: 'EXACT_DUPLICATE',
      severity: 'CRITICAL',
      ruleIds: [1, 2],
    });
  });

  it('does not call different thresholds or disabled rules exact duplicates', () => {
    const portfolio = analyzeAlertRulePortfolio(
      [
        rule({ id: 1, threshold: 80 }),
        rule({ id: 2, threshold: 90 }),
        rule({ id: 3, threshold: 80, enabled: false }),
      ],
      [{ name: 'production' }],
    );
    expect(portfolio.summary.exactDuplicateGroups).toBe(0);
  });

  it('reports duplicate names, missing channels, and deleted instance references', () => {
    const portfolio = analyzeAlertRulePortfolio(
      [
        rule({ id: 1, name: 'Disk high', channels: [] }),
        rule({ id: 2, name: ' disk HIGH ', instanceId: 'deleted-instance', threshold: 91 }),
      ],
      [{ name: 'production' }],
    );
    expect(portfolio.issues.map((item) => item.code)).toEqual([
      'NO_CHANNELS',
      'DUPLICATE_NAME',
      'UNKNOWN_INSTANCE',
    ]);
    expect(portfolio.summary).toMatchObject({ affectedRules: 2, unknownInstanceRules: 1 });
  });

  it('reports a scope whose rules are all disabled once', () => {
    const portfolio = analyzeAlertRulePortfolio(
      [rule({ id: 3, enabled: false }), rule({ id: 4, name: 'second', enabled: false })],
      [{ name: 'production' }],
    );
    expect(portfolio.issues).toHaveLength(1);
    expect(portfolio.issues[0]).toMatchObject({
      code: 'DISABLED_ONLY_SCOPE',
      severity: 'INFO',
      ruleIds: [3, 4],
    });
  });

  it('keeps global rules valid when no instance is specified', () => {
    const portfolio = analyzeAlertRulePortfolio([rule({ instanceId: undefined })], []);
    expect(portfolio.issues).toEqual([]);
    expect(portfolio.rows[0].scope).toContain('*');
  });

  it('sorts affected rules first and retains deterministic issue codes', () => {
    const portfolio = analyzeAlertRulePortfolio(
      [rule({ id: 2, name: 'healthy', threshold: 1 }), rule({ id: 1, name: 'bad', channels: [] })],
      [{ name: 'production' }],
    );
    expect(portfolio.rows.map((row) => row.id)).toEqual([1, 2]);
    expect(portfolio.rows[0]).toMatchObject({
      highestSeverity: 'CRITICAL',
      issueCodes: ['NO_CHANNELS'],
    });
  });

  it('filters the portfolio by state, severity, issue, and text', () => {
    const portfolio = analyzeAlertRulePortfolio(
      [
        rule({ id: 1, name: 'missing channel', channels: [] }),
        rule({ id: 2, name: 'disabled lag', enabled: false, metric: 'consumer.lag.total' }),
      ],
      [{ name: 'production' }],
    );
    const base = {
      search: '',
      enabled: 'ALL' as const,
      severity: 'ALL' as const,
      issueCode: 'ALL' as const,
    };
    expect(filterAlertPortfolioRows(portfolio.rows, { ...base, enabled: 'DISABLED' })).toHaveLength(
      1,
    );
    expect(filterAlertPortfolioRows(portfolio.rows, { ...base, severity: 'CRITICAL' })[0].id).toBe(
      1,
    );
    expect(
      filterAlertPortfolioRows(portfolio.rows, { ...base, issueCode: 'NO_CHANNELS' })[0].id,
    ).toBe(1);
    expect(
      filterAlertPortfolioRows(portfolio.rows, { ...base, search: 'consumer.lag' })[0].id,
    ).toBe(2);
  });
});
