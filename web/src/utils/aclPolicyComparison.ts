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

import type { AclRule, AclUser } from '../api/acl';

export type AclComparisonKind = 'USER' | 'RULE';
export type AclComparisonStatus = 'MATCH' | 'DRIFT' | 'ONLY_SOURCE' | 'ONLY_TARGET';
export type AclComparableField =
  'admin' | 'clusters' | 'permRead' | 'permWrite' | 'actions' | 'decision' | 'aclVersion';

export interface AclFieldDifference {
  field: AclComparableField;
  sourceValue: string;
  targetValue: string;
}

export interface AclPolicyComparisonRow {
  key: string;
  kind: AclComparisonKind;
  identity: string;
  status: AclComparisonStatus;
  sourceUser?: AclUser;
  targetUser?: AclUser;
  sourceRule?: AclRule;
  targetRule?: AclRule;
  differences: AclFieldDifference[];
}

export interface AclPolicyComparisonSummary {
  total: number;
  users: number;
  rules: number;
  matches: number;
  drifted: number;
  onlySource: number;
  onlyTarget: number;
}

export interface AclPolicyComparisonResult {
  rows: AclPolicyComparisonRow[];
  summary: AclPolicyComparisonSummary;
}

const text = (value: unknown) => (value == null ? '' : String(value).trim());
const booleanText = (value: boolean | undefined) => (value === true ? 'true' : 'false');
const sortedText = (values: string[] | undefined) =>
  [...(values ?? [])].map(text).filter(Boolean).sort().join(';');

const ruleIdentity = (rule: AclRule) =>
  [rule.principal, rule.resourceType, rule.resource, rule.resourcePattern, rule.scope]
    .map(text)
    .join(' / ');

const userDifferences = (source: AclUser, target: AclUser): AclFieldDifference[] => {
  const candidates: Array<[AclComparableField, string, string]> = [
    ['admin', booleanText(source.admin), booleanText(target.admin)],
    ['clusters', sortedText(source.clusters), sortedText(target.clusters)],
    ['permRead', booleanText(source.permRead), booleanText(target.permRead)],
    ['permWrite', booleanText(source.permWrite), booleanText(target.permWrite)],
  ];
  return candidates.flatMap(([field, sourceValue, targetValue]) =>
    sourceValue === targetValue ? [] : [{ field, sourceValue, targetValue }],
  );
};

const ruleDifferences = (source: AclRule, target: AclRule): AclFieldDifference[] => {
  const candidates: Array<[AclComparableField, string, string]> = [
    ['actions', sortedText(source.actions), sortedText(target.actions)],
    ['decision', text(source.decision), text(target.decision)],
    ['aclVersion', text(source.aclVersion), text(target.aclVersion)],
  ];
  return candidates.flatMap(([field, sourceValue, targetValue]) =>
    sourceValue === targetValue ? [] : [{ field, sourceValue, targetValue }],
  );
};

const groupRulesByIdentity = (rules: AclRule[]) => {
  const grouped = new Map<string, AclRule[]>();
  rules.forEach((rule) => {
    const identity = ruleIdentity(rule);
    const values = grouped.get(identity) ?? [];
    values.push(rule);
    grouped.set(identity, values);
  });
  grouped.forEach((values) =>
    values.sort((left, right) => {
      const leftConfig = `${sortedText(left.actions)}|${text(left.decision)}|${text(left.aclVersion)}`;
      const rightConfig = `${sortedText(right.actions)}|${text(right.decision)}|${text(right.aclVersion)}`;
      return leftConfig.localeCompare(rightConfig);
    }),
  );
  return grouped;
};

const compareUsers = (sourceUsers: AclUser[], targetUsers: AclUser[]) => {
  const sourceByName = new Map(sourceUsers.map((user) => [text(user.username), user]));
  const targetByName = new Map(targetUsers.map((user) => [text(user.username), user]));
  const names = [...new Set([...sourceByName.keys(), ...targetByName.keys()])]
    .filter(Boolean)
    .sort((left, right) => left.localeCompare(right));

  return names.map((identity): AclPolicyComparisonRow => {
    const sourceUser = sourceByName.get(identity);
    const targetUser = targetByName.get(identity);
    if (!sourceUser) {
      return {
        key: `USER:${identity}`,
        kind: 'USER',
        identity,
        status: 'ONLY_TARGET',
        targetUser,
        differences: [],
      };
    }
    if (!targetUser) {
      return {
        key: `USER:${identity}`,
        kind: 'USER',
        identity,
        status: 'ONLY_SOURCE',
        sourceUser,
        differences: [],
      };
    }
    const differences = userDifferences(sourceUser, targetUser);
    return {
      key: `USER:${identity}`,
      kind: 'USER',
      identity,
      status: differences.length === 0 ? 'MATCH' : 'DRIFT',
      sourceUser,
      targetUser,
      differences,
    };
  });
};

const compareRules = (sourceRules: AclRule[], targetRules: AclRule[]) => {
  const sourceByIdentity = groupRulesByIdentity(sourceRules);
  const targetByIdentity = groupRulesByIdentity(targetRules);
  const identities = [...new Set([...sourceByIdentity.keys(), ...targetByIdentity.keys()])].sort(
    (left, right) => left.localeCompare(right),
  );
  const rows: AclPolicyComparisonRow[] = [];

  identities.forEach((identity) => {
    const sourceValues = sourceByIdentity.get(identity) ?? [];
    const targetValues = targetByIdentity.get(identity) ?? [];
    const count = Math.max(sourceValues.length, targetValues.length);
    for (let index = 0; index < count; index += 1) {
      const sourceRule = sourceValues[index];
      const targetRule = targetValues[index];
      const key = `RULE:${identity}:${index}`;
      if (!sourceRule) {
        rows.push({
          key,
          kind: 'RULE',
          identity,
          status: 'ONLY_TARGET',
          targetRule,
          differences: [],
        });
      } else if (!targetRule) {
        rows.push({
          key,
          kind: 'RULE',
          identity,
          status: 'ONLY_SOURCE',
          sourceRule,
          differences: [],
        });
      } else {
        const differences = ruleDifferences(sourceRule, targetRule);
        rows.push({
          key,
          kind: 'RULE',
          identity,
          status: differences.length === 0 ? 'MATCH' : 'DRIFT',
          sourceRule,
          targetRule,
          differences,
        });
      }
    }
  });
  return rows;
};

export const compareAclPolicies = (
  sourceUsers: AclUser[],
  targetUsers: AclUser[],
  sourceRules: AclRule[],
  targetRules: AclRule[],
): AclPolicyComparisonResult => {
  const rows = [
    ...compareUsers(sourceUsers, targetUsers),
    ...compareRules(sourceRules, targetRules),
  ];
  return {
    rows,
    summary: {
      total: rows.length,
      users: rows.filter((row) => row.kind === 'USER').length,
      rules: rows.filter((row) => row.kind === 'RULE').length,
      matches: rows.filter((row) => row.status === 'MATCH').length,
      drifted: rows.filter((row) => row.status === 'DRIFT').length,
      onlySource: rows.filter((row) => row.status === 'ONLY_SOURCE').length,
      onlyTarget: rows.filter((row) => row.status === 'ONLY_TARGET').length,
    },
  };
};

export const filterAclPolicyComparisonRows = (
  rows: AclPolicyComparisonRow[],
  kind: AclComparisonKind | 'ALL',
  status: AclComparisonStatus | 'ALL',
  search: string,
) => {
  const query = search.trim().toLocaleLowerCase();
  return rows.filter(
    (row) =>
      (kind === 'ALL' || row.kind === kind) &&
      (status === 'ALL' || row.status === status) &&
      (!query || row.identity.toLocaleLowerCase().includes(query)),
  );
};

export const formatAclDifferences = (differences: AclFieldDifference[]) =>
  differences
    .map(({ field, sourceValue, targetValue }) => `${field}: ${sourceValue} -> ${targetValue}`)
    .join('; ');
