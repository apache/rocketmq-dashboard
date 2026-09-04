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

import type { AlertRule } from '../api/ops';
import type { Instance } from '../api/instance';

export type AlertPortfolioIssueCode =
  'EXACT_DUPLICATE' | 'DUPLICATE_NAME' | 'NO_CHANNELS' | 'UNKNOWN_INSTANCE' | 'DISABLED_ONLY_SCOPE';

export type AlertPortfolioSeverity = 'CRITICAL' | 'WARNING' | 'INFO';

export interface AlertPortfolioIssue {
  key: string;
  code: AlertPortfolioIssueCode;
  severity: AlertPortfolioSeverity;
  ruleIds: number[];
  ruleNames: string[];
  scope: string;
  evidence: string;
}

export interface AlertPortfolioRuleRow {
  key: number;
  id: number;
  name: string;
  metric: string;
  scope: string;
  condition: string;
  channels: string[];
  enabled: boolean;
  issueCodes: AlertPortfolioIssueCode[];
  highestSeverity: AlertPortfolioSeverity | 'NONE';
}

export interface AlertPortfolioSummary {
  rules: number;
  enabled: number;
  disabled: number;
  affectedRules: number;
  criticalIssues: number;
  warningIssues: number;
  infoIssues: number;
  exactDuplicateGroups: number;
  unknownInstanceRules: number;
}

export interface AlertRulePortfolio {
  issues: AlertPortfolioIssue[];
  rows: AlertPortfolioRuleRow[];
  summary: AlertPortfolioSummary;
}

export interface AlertPortfolioFilters {
  search: string;
  enabled: 'ALL' | 'ENABLED' | 'DISABLED';
  severity: AlertPortfolioSeverity | 'NONE' | 'ALL';
  issueCode: AlertPortfolioIssueCode | 'ALL';
}

const severityRank: Record<AlertPortfolioSeverity | 'NONE', number> = {
  NONE: 0,
  INFO: 1,
  WARNING: 2,
  CRITICAL: 3,
};

const normalizedText = (value?: string | null) => value?.trim() ?? '';

const normalizedDurationMs = (value?: string | null): string => {
  const normalized = normalizedText(value).toLocaleLowerCase();
  const match = /^(\d+(?:\.\d+)?)\s*(ms|s|m|h)$/.exec(normalized);
  if (!match) return normalized;
  const amount = Number(match[1]);
  const unit = match[2] as 'ms' | 's' | 'm' | 'h';
  const multiplier: Record<typeof unit, number> = {
    ms: 1,
    s: 1000,
    m: 60_000,
    h: 3_600_000,
  };
  return String(amount * multiplier[unit]);
};

export const alertRuleScope = (rule: AlertRule): string =>
  [
    normalizedText(rule.instanceId) || '*',
    normalizedText(rule.metric),
    normalizedText(rule.consumerGroup) || '*',
    normalizedText(rule.topic) || '*',
  ].join(' / ');

const evaluationSignature = (rule: AlertRule): string =>
  JSON.stringify([
    alertRuleScope(rule),
    normalizedText(rule.operator).toLocaleUpperCase(),
    rule.threshold,
    normalizedText(rule.thresholdUnit).toLocaleLowerCase(),
    normalizedDurationMs(rule.duration),
    rule.aggregation ?? 'LAST',
    rule.windowSeconds ?? null,
    rule.consecutiveSamples ?? null,
    rule.reminderInterval ? normalizedDurationMs(rule.reminderInterval) : null,
  ]);

const conditionText = (rule: AlertRule): string =>
  `${rule.aggregation ?? 'LAST'} ${rule.operator} ${rule.threshold}${rule.thresholdUnit ?? ''} / ${rule.duration}`;

const issue = (
  code: AlertPortfolioIssueCode,
  severity: AlertPortfolioSeverity,
  rules: AlertRule[],
  scope: string,
  evidence: string,
): AlertPortfolioIssue => ({
  key: `${code}:${rules
    .map((rule) => rule.id)
    .sort((a, b) => a - b)
    .join(',')}`,
  code,
  severity,
  ruleIds: rules.map((rule) => rule.id).sort((a, b) => a - b),
  ruleNames: rules.map((rule) => rule.name).sort(),
  scope,
  evidence,
});

const groupsBy = (rules: AlertRule[], keyOf: (rule: AlertRule) => string) => {
  const groups = new Map<string, AlertRule[]>();
  rules.forEach((rule) => {
    const key = keyOf(rule);
    groups.set(key, [...(groups.get(key) ?? []), rule]);
  });
  return groups;
};

export const analyzeAlertRulePortfolio = (
  rules: AlertRule[],
  instances: Pick<Instance, 'name'>[],
): AlertRulePortfolio => {
  const issues: AlertPortfolioIssue[] = [];
  const knownInstances = new Set(instances.map((instance) => instance.name));
  const enabledRules = rules.filter((rule) => rule.enabled);

  groupsBy(enabledRules, evaluationSignature).forEach((group) => {
    if (group.length > 1) {
      issues.push(
        issue(
          'EXACT_DUPLICATE',
          'CRITICAL',
          group,
          alertRuleScope(group[0]),
          `Enabled rules share the same evaluation signature: ${group.map((rule) => rule.id).join(', ')}`,
        ),
      );
    }
  });

  groupsBy(rules, (rule) => normalizedText(rule.name).toLocaleLowerCase()).forEach(
    (group, name) => {
      if (name && group.length > 1) {
        issues.push(
          issue(
            'DUPLICATE_NAME',
            'WARNING',
            group,
            alertRuleScope(group[0]),
            `Rule name is used ${group.length} times: ${group[0].name}`,
          ),
        );
      }
    },
  );

  rules.forEach((rule) => {
    if (rule.enabled && (rule.channels ?? []).length === 0) {
      issues.push(
        issue(
          'NO_CHANNELS',
          'CRITICAL',
          [rule],
          alertRuleScope(rule),
          `Enabled rule ${rule.id} has no notification channel`,
        ),
      );
    }
    const instanceId = normalizedText(rule.instanceId);
    if (instanceId && !knownInstances.has(instanceId)) {
      issues.push(
        issue(
          'UNKNOWN_INSTANCE',
          'WARNING',
          [rule],
          alertRuleScope(rule),
          `Referenced instance does not exist: ${instanceId}`,
        ),
      );
    }
  });

  groupsBy(rules, alertRuleScope).forEach((group, scope) => {
    if (group.length > 0 && group.every((rule) => !rule.enabled)) {
      issues.push(
        issue(
          'DISABLED_ONLY_SCOPE',
          'INFO',
          group,
          scope,
          `All ${group.length} rules for this scope are disabled`,
        ),
      );
    }
  });

  issues.sort((left, right) => {
    const severity = severityRank[right.severity] - severityRank[left.severity];
    return severity !== 0 ? severity : left.key.localeCompare(right.key);
  });

  const issuesByRule = new Map<number, AlertPortfolioIssue[]>();
  issues.forEach((item) =>
    item.ruleIds.forEach((id) => issuesByRule.set(id, [...(issuesByRule.get(id) ?? []), item])),
  );
  const rows = rules
    .map((rule): AlertPortfolioRuleRow => {
      const ruleIssues = issuesByRule.get(rule.id) ?? [];
      const highestSeverity = ruleIssues.reduce<AlertPortfolioSeverity | 'NONE'>(
        (highest, item) =>
          severityRank[item.severity] > severityRank[highest] ? item.severity : highest,
        'NONE',
      );
      return {
        key: rule.id,
        id: rule.id,
        name: rule.name,
        metric: rule.metric,
        scope: alertRuleScope(rule),
        condition: conditionText(rule),
        channels: [...(rule.channels ?? [])].sort(),
        enabled: rule.enabled,
        issueCodes: [...new Set(ruleIssues.map((item) => item.code))],
        highestSeverity,
      };
    })
    .sort((left, right) => {
      const severity = severityRank[right.highestSeverity] - severityRank[left.highestSeverity];
      return severity !== 0 ? severity : left.name.localeCompare(right.name);
    });

  return {
    issues,
    rows,
    summary: {
      rules: rules.length,
      enabled: enabledRules.length,
      disabled: rules.length - enabledRules.length,
      affectedRules: rows.filter((row) => row.issueCodes.length > 0).length,
      criticalIssues: issues.filter((item) => item.severity === 'CRITICAL').length,
      warningIssues: issues.filter((item) => item.severity === 'WARNING').length,
      infoIssues: issues.filter((item) => item.severity === 'INFO').length,
      exactDuplicateGroups: issues.filter((item) => item.code === 'EXACT_DUPLICATE').length,
      unknownInstanceRules: issues.filter((item) => item.code === 'UNKNOWN_INSTANCE').length,
    },
  };
};

export const filterAlertPortfolioRows = (
  rows: AlertPortfolioRuleRow[],
  filters: AlertPortfolioFilters,
): AlertPortfolioRuleRow[] => {
  const search = filters.search.trim().toLocaleLowerCase();
  return rows.filter(
    (row) =>
      (filters.enabled === 'ALL' || row.enabled === (filters.enabled === 'ENABLED')) &&
      (filters.severity === 'ALL' || row.highestSeverity === filters.severity) &&
      (filters.issueCode === 'ALL' || row.issueCodes.includes(filters.issueCode)) &&
      (!search ||
        [row.name, row.metric, row.scope, row.condition, row.channels.join(' ')].some((value) =>
          value.toLocaleLowerCase().includes(search),
        )),
  );
};
