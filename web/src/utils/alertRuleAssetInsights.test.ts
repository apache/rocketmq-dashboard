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

import type { AlertRuleAssetInfo } from '../api/alertRuleAssets';
import {
  buildAlertRuleAssetCatalogCsv,
  buildAlertRuleAssetCatalogFilename,
  buildAlertRuleAssetInsights,
  formatAlertRuleAssetPercent,
  inferAlertRuleAssetDomain,
  normalizeAlertRuleAssetSeverities,
} from './alertRuleAssetInsights';

const completeAssets: AlertRuleAssetInfo[] = [
  {
    name: 'rocketmq-broker-down',
    group: 'rocketmq-broker.rules',
    ruleCount: 2,
    severities: ['critical', 'warning'],
  },
  {
    name: 'rocketmq-consumer-lag-critical',
    group: 'rocketmq-consumer.rules',
    ruleCount: 1,
    severities: ['critical'],
  },
  {
    name: 'rocketmq-producer-send-failure',
    group: 'rocketmq-client.rules',
    ruleCount: 1,
    severities: ['critical'],
  },
  {
    name: 'rocketmq-topic-accumulation',
    group: 'rocketmq-topic.rules',
    ruleCount: 1,
    severities: ['warning'],
  },
  {
    name: 'rocketmq-proxy-down',
    group: 'rocketmq-proxy.rules',
    ruleCount: 1,
    severities: ['critical'],
  },
];

describe('alert rule asset insights', () => {
  it('normalizes severities and infers operational domains from asset metadata', () => {
    expect(normalizeAlertRuleAssetSeverities([' Warning ', 'critical', 'warning', 'Info'])).toEqual(
      ['critical', 'warning', 'info'],
    );

    expect(
      inferAlertRuleAssetDomain({
        name: 'rocketmq-producer-latency-high',
        group: 'rocketmq-client.rules',
      }),
    ).toBe('producer');
    expect(
      inferAlertRuleAssetDomain({
        name: 'rocketmq-exception-rate',
        group: 'rocketmq-errors.rules',
      }),
    ).toBe('error');
    expect(inferAlertRuleAssetDomain({ name: 'custom', group: 'custom.rules' })).toBe('unknown');
  });

  it('builds coverage rows, severity counts, and soft findings for covered catalogs', () => {
    const insights = buildAlertRuleAssetInsights(completeAssets);

    expect(insights.totalAssets).toBe(5);
    expect(insights.totalRules).toBe(6);
    expect(insights.groupCount).toBe(5);
    expect(insights.coveredExpectedDomainCount).toBe(5);
    expect(insights.coveragePercent).toBe(100);
    expect(insights.criticalAssetCount).toBe(4);
    expect(insights.warningAssetCount).toBe(2);
    expect(insights.infoAssetCount).toBe(0);
    expect(insights.score).toBe(95);
    expect(insights.level).toBe('notice');
    expect(insights.issues).toEqual([
      {
        code: 'DOMAIN_WITHOUT_CRITICAL',
        level: 'notice',
        domain: 'topic',
        value: 1,
      },
    ]);

    expect(insights.domainRows).toMatchObject([
      {
        domain: 'broker',
        assetCount: 1,
        ruleCount: 2,
        criticalAssetCount: 1,
        warningAssetCount: 1,
        coveragePercent: 33.3,
        groups: ['rocketmq-broker.rules'],
      },
      {
        domain: 'consumer',
        assetCount: 1,
        ruleCount: 1,
        criticalAssetCount: 1,
        coveragePercent: 16.7,
      },
      {
        domain: 'producer',
        assetCount: 1,
        ruleCount: 1,
        criticalAssetCount: 1,
      },
      {
        domain: 'topic',
        assetCount: 1,
        ruleCount: 1,
        criticalAssetCount: 0,
        issues: ['DOMAIN_WITHOUT_CRITICAL'],
      },
      {
        domain: 'proxy',
        assetCount: 1,
        ruleCount: 1,
        criticalAssetCount: 1,
      },
    ]);
  });

  it('reports missing coverage, empty assets, missing severities, and duplicate names', () => {
    const insights = buildAlertRuleAssetInsights([
      completeAssets[0],
      { ...completeAssets[0], group: 'rocketmq-broker-copy.rules' },
      {
        name: 'rocketmq-consumer-lag-high',
        group: 'rocketmq-consumer.rules',
        ruleCount: 0,
        severities: [],
      },
    ]);

    expect(insights.level).toBe('warning');
    expect(insights.score).toBe(0);
    expect(insights.unknownSeverityAssetCount).toBe(1);
    expect(insights.issues).toHaveLength(8);
    expect(insights.issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining([
        'DUPLICATE_ASSET_NAME',
        'EMPTY_RULE_ASSET',
        'DOMAIN_UNCOVERED',
        'MISSING_SEVERITY',
        'DOMAIN_WITHOUT_CRITICAL',
      ]),
    );
    expect(
      insights.domainRows
        .filter((row) => row.issues.includes('DOMAIN_UNCOVERED'))
        .map((row) => row.domain),
    ).toEqual(['producer', 'topic', 'proxy']);
  });

  it('treats an empty catalog as critical instead of reporting synthetic domain gaps', () => {
    const insights = buildAlertRuleAssetInsights([]);

    expect(insights.level).toBe('critical');
    expect(insights.score).toBe(0);
    expect(insights.totalAssets).toBe(0);
    expect(insights.totalRules).toBe(0);
    expect(insights.issues).toEqual([{ code: 'NO_ASSETS', level: 'critical' }]);
    expect(insights.domainRows).toHaveLength(5);
  });

  it('exports the analyzed catalog as formula-safe CSV with deterministic filenames', () => {
    const csv = buildAlertRuleAssetCatalogCsv([
      {
        name: '=rocketmq-broker-down',
        group: 'rocketmq-broker.rules',
        ruleCount: 1,
        severities: ['critical'],
      },
    ]);

    expect(csv).toContain('"Name","Group","Domain","Rules","Severities","Has Critical","Issues"');
    expect(csv).toContain('"\'=rocketmq-broker-down"');
    expect(csv).toContain('"broker"');
    expect(csv).toContain('"yes"');
    expect(buildAlertRuleAssetCatalogFilename(new Date('2026-09-12T01:02:03.004Z'))).toBe(
      'rocketmq-studio-alert-rule-assets-2026-09-12T01-02-03-004Z.csv',
    );
  });

  it('formats percentages without dropping useful precision', () => {
    expect(formatAlertRuleAssetPercent(100)).toBe('100%');
    expect(formatAlertRuleAssetPercent(33.3)).toBe('33.3%');
  });
});
