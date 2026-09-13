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

import type { AlertRuleAssetInfo } from '../api/alertRuleAssets';
import { buildCsv, type CsvColumn } from './download';

export type AlertRuleAssetHealthLevel = 'healthy' | 'notice' | 'warning' | 'critical';

export type AlertRuleAssetDomain =
  | 'broker'
  | 'consumer'
  | 'producer'
  | 'topic'
  | 'proxy'
  | 'client'
  | 'dlq'
  | 'error'
  | 'runtime'
  | 'unknown';

export type AlertRuleAssetIssueCode =
  | 'NO_ASSETS'
  | 'DUPLICATE_ASSET_NAME'
  | 'EMPTY_RULE_ASSET'
  | 'MISSING_SEVERITY'
  | 'DOMAIN_UNCOVERED'
  | 'DOMAIN_WITHOUT_CRITICAL';

export interface AlertRuleAssetIssue {
  code: AlertRuleAssetIssueCode;
  level: AlertRuleAssetHealthLevel;
  assetName?: string;
  domain?: AlertRuleAssetDomain;
  value?: number;
}

export interface AlertRuleAssetInsightRow {
  key: string;
  name: string;
  group: string;
  domain: AlertRuleAssetDomain;
  ruleCount: number;
  severities: string[];
  hasCritical: boolean;
  issues: AlertRuleAssetIssueCode[];
}

export interface AlertRuleAssetDomainInsight {
  domain: AlertRuleAssetDomain;
  assetCount: number;
  ruleCount: number;
  criticalAssetCount: number;
  warningAssetCount: number;
  infoAssetCount: number;
  coveragePercent: number;
  groups: string[];
  assetNames: string[];
  issues: AlertRuleAssetIssueCode[];
}

export interface AlertRuleAssetInsights {
  level: AlertRuleAssetHealthLevel;
  score: number;
  totalAssets: number;
  totalRules: number;
  groupCount: number;
  expectedDomainCount: number;
  coveredExpectedDomainCount: number;
  coveragePercent: number;
  criticalAssetCount: number;
  warningAssetCount: number;
  infoAssetCount: number;
  unknownSeverityAssetCount: number;
  rows: AlertRuleAssetInsightRow[];
  domainRows: AlertRuleAssetDomainInsight[];
  issues: AlertRuleAssetIssue[];
}

export const EXPECTED_ALERT_RULE_ASSET_DOMAINS: AlertRuleAssetDomain[] = [
  'broker',
  'consumer',
  'producer',
  'topic',
  'proxy',
];

const DOMAIN_ORDER: AlertRuleAssetDomain[] = [
  ...EXPECTED_ALERT_RULE_ASSET_DOMAINS,
  'client',
  'dlq',
  'error',
  'runtime',
  'unknown',
];

const SEVERITY_ORDER = ['critical', 'warning', 'info'];
const EMPTY_ASSET_PENALTY = 16;
const MISSING_SEVERITY_PENALTY = 10;
const DUPLICATE_NAME_PENALTY = 12;
const UNCOVERED_DOMAIN_PENALTY = 16;
const SOFT_DOMAIN_GAP_PENALTY = 5;

const issueLevelWeight: Record<AlertRuleAssetHealthLevel, number> = {
  healthy: 0,
  notice: 1,
  warning: 2,
  critical: 3,
};

const domainPatterns: Array<[AlertRuleAssetDomain, RegExp]> = [
  ['broker', /\bbroker\b|broker[-_.]?down|threadpool|disk|jvm/],
  ['consumer', /\bconsumer\b|consume|lag/],
  ['producer', /\bproducer\b|send[-_.]?failure|send[-_.]?latency/],
  ['topic', /\btopic\b|dispatch|messages[-_.]?in/],
  ['proxy', /\bproxy\b/],
  ['dlq', /\bdlq\b|dead[-_.]?letter/],
  ['error', /\berror\b|\berrors\b|exception/],
  ['client', /\bclient\b|connection/],
  ['runtime', /\bruntime\b|jvm|gc|cpu|memory/],
];

const normalizeText = (value: string | null | undefined) => (value ?? '').trim();

const finiteRuleCount = (value: number | null | undefined) =>
  typeof value === 'number' && Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0;

const sortSeverities = (left: string, right: string) => {
  const leftIndex = SEVERITY_ORDER.indexOf(left);
  const rightIndex = SEVERITY_ORDER.indexOf(right);
  if (leftIndex !== -1 || rightIndex !== -1) {
    return (
      (leftIndex === -1 ? SEVERITY_ORDER.length : leftIndex) -
      (rightIndex === -1 ? SEVERITY_ORDER.length : rightIndex)
    );
  }
  return left.localeCompare(right);
};

export const normalizeAlertRuleAssetSeverities = (severities: string[] | null | undefined) =>
  Array.from(
    new Set(
      (severities ?? [])
        .map((severity) => severity.trim().toLowerCase())
        .filter((severity) => severity.length > 0),
    ),
  ).sort(sortSeverities);

export const inferAlertRuleAssetDomain = (asset: Pick<AlertRuleAssetInfo, 'name' | 'group'>) => {
  const searchable = `${asset.name ?? ''} ${asset.group ?? ''}`.toLowerCase();
  return domainPatterns.find(([, pattern]) => pattern.test(searchable))?.[0] ?? 'unknown';
};

const percent = (value: number, total: number) => {
  if (total <= 0) return 0;
  return Math.round((value / total) * 1000) / 10;
};

export const formatAlertRuleAssetPercent = (value: number) =>
  `${Number.isInteger(value) ? value.toFixed(0) : value.toFixed(1)}%`;

const maxIssueLevel = (issues: AlertRuleAssetIssue[]): AlertRuleAssetHealthLevel =>
  issues.reduce<AlertRuleAssetHealthLevel>(
    (current, issue) =>
      issueLevelWeight[issue.level] > issueLevelWeight[current] ? issue.level : current,
    'healthy',
  );

const healthLevelFromScore = (
  score: number,
  issues: AlertRuleAssetIssue[],
): AlertRuleAssetHealthLevel => {
  const maxLevel = maxIssueLevel(issues);
  if (maxLevel === 'critical') return 'critical';
  if (maxLevel === 'warning') return score < 70 ? 'warning' : 'notice';
  if (score < 70) return 'warning';
  if (score < 90 || maxLevel === 'notice') return 'notice';
  return 'healthy';
};

const buildRows = (assets: AlertRuleAssetInfo[]): AlertRuleAssetInsightRow[] => {
  const nameCounts = new Map<string, number>();
  assets.forEach((asset) => {
    const name = normalizeText(asset.name);
    if (name) nameCounts.set(name, (nameCounts.get(name) ?? 0) + 1);
  });

  return assets
    .map((asset, index) => {
      const name = normalizeText(asset.name);
      const group = normalizeText(asset.group);
      const ruleCount = finiteRuleCount(asset.ruleCount);
      const severities = normalizeAlertRuleAssetSeverities(asset.severities);
      const issues: AlertRuleAssetIssueCode[] = [];
      if (name && (nameCounts.get(name) ?? 0) > 1) issues.push('DUPLICATE_ASSET_NAME');
      if (ruleCount === 0) issues.push('EMPTY_RULE_ASSET');
      if (severities.length === 0) issues.push('MISSING_SEVERITY');
      return {
        key: name || `asset-${index}`,
        name,
        group,
        domain: inferAlertRuleAssetDomain(asset),
        ruleCount,
        severities,
        hasCritical: severities.includes('critical'),
        issues,
      };
    })
    .sort(
      (left, right) =>
        DOMAIN_ORDER.indexOf(left.domain) - DOMAIN_ORDER.indexOf(right.domain) ||
        left.group.localeCompare(right.group) ||
        left.name.localeCompare(right.name),
    );
};

const buildDomainRows = (
  rows: AlertRuleAssetInsightRow[],
  totalRules: number,
): AlertRuleAssetDomainInsight[] => {
  const byDomain = new Map<AlertRuleAssetDomain, AlertRuleAssetDomainInsight>();
  const ensure = (domain: AlertRuleAssetDomain) => {
    const current = byDomain.get(domain);
    if (current) return current;
    const next: AlertRuleAssetDomainInsight = {
      domain,
      assetCount: 0,
      ruleCount: 0,
      criticalAssetCount: 0,
      warningAssetCount: 0,
      infoAssetCount: 0,
      coveragePercent: 0,
      groups: [],
      assetNames: [],
      issues: [],
    };
    byDomain.set(domain, next);
    return next;
  };

  EXPECTED_ALERT_RULE_ASSET_DOMAINS.forEach(ensure);
  rows.forEach((row) => {
    const domain = ensure(row.domain);
    domain.assetCount += 1;
    domain.ruleCount += row.ruleCount;
    if (row.hasCritical) domain.criticalAssetCount += 1;
    if (row.severities.includes('warning')) domain.warningAssetCount += 1;
    if (row.severities.includes('info')) domain.infoAssetCount += 1;
    if (row.group && !domain.groups.includes(row.group)) domain.groups.push(row.group);
    if (row.name) domain.assetNames.push(row.name);
  });

  return Array.from(byDomain.values())
    .map((domain) => {
      const issues: AlertRuleAssetIssueCode[] = [];
      if (domain.assetCount === 0) issues.push('DOMAIN_UNCOVERED');
      else if (
        EXPECTED_ALERT_RULE_ASSET_DOMAINS.includes(domain.domain) &&
        domain.criticalAssetCount === 0
      ) {
        issues.push('DOMAIN_WITHOUT_CRITICAL');
      }
      return {
        ...domain,
        coveragePercent: percent(domain.ruleCount, totalRules),
        groups: domain.groups.sort((left, right) => left.localeCompare(right)),
        assetNames: domain.assetNames.sort((left, right) => left.localeCompare(right)),
        issues,
      };
    })
    .sort(
      (left, right) =>
        DOMAIN_ORDER.indexOf(left.domain) - DOMAIN_ORDER.indexOf(right.domain) ||
        left.domain.localeCompare(right.domain),
    );
};

const buildIssues = (
  rows: AlertRuleAssetInsightRow[],
  domainRows: AlertRuleAssetDomainInsight[],
): AlertRuleAssetIssue[] => {
  if (rows.length === 0) {
    return [{ code: 'NO_ASSETS', level: 'critical' }];
  }

  return [
    ...rows.flatMap((row): AlertRuleAssetIssue[] =>
      row.issues.map((code) => ({
        code,
        level:
          code === 'DUPLICATE_ASSET_NAME' || code === 'EMPTY_RULE_ASSET' ? 'warning' : 'notice',
        assetName: row.name,
        domain: row.domain,
        value: row.ruleCount,
      })),
    ),
    ...domainRows.flatMap((row): AlertRuleAssetIssue[] =>
      row.issues.map((code) => ({
        code,
        level: code === 'DOMAIN_UNCOVERED' ? 'warning' : 'notice',
        domain: row.domain,
        value: row.assetCount,
      })),
    ),
  ].sort(
    (left, right) =>
      issueLevelWeight[right.level] - issueLevelWeight[left.level] ||
      left.code.localeCompare(right.code) ||
      (left.assetName ?? '').localeCompare(right.assetName ?? '') ||
      (left.domain ?? '').localeCompare(right.domain ?? ''),
  );
};

const scorePenalty = (issues: AlertRuleAssetIssue[]) =>
  issues.reduce((total, issue) => {
    switch (issue.code) {
      case 'EMPTY_RULE_ASSET':
        return total + EMPTY_ASSET_PENALTY;
      case 'MISSING_SEVERITY':
        return total + MISSING_SEVERITY_PENALTY;
      case 'DUPLICATE_ASSET_NAME':
        return total + DUPLICATE_NAME_PENALTY;
      case 'DOMAIN_UNCOVERED':
        return total + UNCOVERED_DOMAIN_PENALTY;
      case 'DOMAIN_WITHOUT_CRITICAL':
        return total + SOFT_DOMAIN_GAP_PENALTY;
      case 'NO_ASSETS':
        return 100;
      default:
        return total;
    }
  }, 0);

export const buildAlertRuleAssetInsights = (
  assets: AlertRuleAssetInfo[],
): AlertRuleAssetInsights => {
  const rows = buildRows(assets);
  const totalRules = rows.reduce((sum, row) => sum + row.ruleCount, 0);
  const domainRows = buildDomainRows(rows, totalRules);
  const issues = buildIssues(rows, domainRows);
  const coveredExpectedDomainCount = EXPECTED_ALERT_RULE_ASSET_DOMAINS.filter((domain) =>
    domainRows.some((row) => row.domain === domain && row.assetCount > 0),
  ).length;
  const score = Math.max(0, 100 - scorePenalty(issues));

  return {
    level: healthLevelFromScore(score, issues),
    score,
    totalAssets: rows.length,
    totalRules,
    groupCount: new Set(rows.map((row) => row.group).filter(Boolean)).size,
    expectedDomainCount: EXPECTED_ALERT_RULE_ASSET_DOMAINS.length,
    coveredExpectedDomainCount,
    coveragePercent: percent(coveredExpectedDomainCount, EXPECTED_ALERT_RULE_ASSET_DOMAINS.length),
    criticalAssetCount: rows.filter((row) => row.hasCritical).length,
    warningAssetCount: rows.filter((row) => row.severities.includes('warning')).length,
    infoAssetCount: rows.filter((row) => row.severities.includes('info')).length,
    unknownSeverityAssetCount: rows.filter((row) => row.severities.length === 0).length,
    rows,
    domainRows,
    issues,
  };
};

export const ALERT_RULE_ASSET_CATALOG_COLUMNS: CsvColumn<AlertRuleAssetInsightRow>[] = [
  { header: 'Name', value: (row) => row.name },
  { header: 'Group', value: (row) => row.group },
  { header: 'Domain', value: (row) => row.domain },
  { header: 'Rules', value: (row) => row.ruleCount },
  { header: 'Severities', value: (row) => row.severities.join(';') },
  { header: 'Has Critical', value: (row) => (row.hasCritical ? 'yes' : 'no') },
  { header: 'Issues', value: (row) => row.issues.join(';') },
];

export const buildAlertRuleAssetCatalogCsv = (assets: AlertRuleAssetInfo[]) =>
  buildCsv(ALERT_RULE_ASSET_CATALOG_COLUMNS, buildAlertRuleAssetInsights(assets).rows);

export const buildAlertRuleAssetCatalogFilename = (now = new Date()) =>
  `rocketmq-studio-alert-rule-assets-${now.toISOString().replace(/[:.]/g, '-')}.csv`;
