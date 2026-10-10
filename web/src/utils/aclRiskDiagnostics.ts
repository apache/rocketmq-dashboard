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

import type { AclClusterConfig, PlainAccessConfig } from '../api/acl';

export type AclRiskStatus = 'healthy' | 'warning' | 'critical';
export type AclRiskSeverity = Exclude<AclRiskStatus, 'healthy'> | 'info';

export type AclRiskIssueCode =
  | 'ACL_DISABLED'
  | 'NO_PLAIN_ACCESS_ACCOUNTS'
  | 'LEGACY_ACL_VERSION'
  | 'BROAD_GLOBAL_WHITELIST'
  | 'BROAD_ACCOUNT_WHITELIST'
  | 'DUPLICATE_ACCESS_KEY'
  | 'MISSING_ACCESS_KEY'
  | 'MULTIPLE_ADMIN_ACCOUNTS'
  | 'ADMIN_WITH_BROAD_ACCESS'
  | 'DEFAULT_TOPIC_ALLOW'
  | 'DEFAULT_GROUP_ALLOW'
  | 'WILDCARD_TOPIC_PERMISSION'
  | 'WILDCARD_GROUP_PERMISSION'
  | 'INVALID_PERMISSION_ENTRY';

export interface AclRiskIssue {
  id: string;
  code: AclRiskIssueCode;
  severity: AclRiskSeverity;
  titleKey: string;
  descriptionKey: string;
  account?: string;
  evidence: string[];
  recommendationKey: string;
}

export interface AclRiskSummary {
  accountCount: number;
  adminAccountCount: number;
  defaultAllowAccountCount: number;
  wildcardPermissionAccountCount: number;
  broadWhitelistCount: number;
  duplicateAccessKeyCount: number;
}

export interface AclRiskDiagnostics {
  status: AclRiskStatus;
  statusKey: string;
  statusColor: 'success' | 'warning' | 'error';
  score: number;
  summary: AclRiskSummary;
  issues: AclRiskIssue[];
  recommendationKeys: string[];
}

type AclPermission = 'DENY' | 'PUB' | 'SUB' | 'ALL' | 'UNKNOWN';

interface ParsedPermissionEntry {
  raw: string;
  resource: string;
  permission: AclPermission;
}

type WhitelistClass = 'open' | 'broad' | 'scoped';

// The analyzer returns translation keys, not display text: the page resolves them
// through t(), the same contract as messageTraceDiagnostics.
const STATUS_KEY: Record<AclRiskStatus, string> = {
  healthy: 'aclRisk.statusHealthy',
  warning: 'aclRisk.statusWarning',
  critical: 'aclRisk.statusCritical',
};

const STATUS_COLOR: Record<AclRiskStatus, 'success' | 'warning' | 'error'> = {
  healthy: 'success',
  warning: 'warning',
  critical: 'error',
};

const PERMISSION_ALLOW_RANK: Record<AclPermission, number> = {
  DENY: 0,
  PUB: 1,
  SUB: 1,
  ALL: 2,
  UNKNOWN: 0,
};

const OPEN_WHITELIST_VALUES = new Set(['*', '0.0.0.0/0', '::/0', '0/0']);

/**
 * The rule editor lets one rule carry several actions, and the repository stores them as a
 * single comma-joined value (MybatisPlusAclRepository#joinNormalizedCsv) that
 * examineBrokerClusterAclConfig hands back verbatim as e.g. "order-events=PUB,SUB".
 * Rank the strongest action of such a list instead of treating the whole value as unknown:
 * PUB+SUB grants the same access as ALL, while a value with no recognised action stays UNKNOWN.
 */
const normalizePermission = (value?: string | null): AclPermission => {
  const actions = (value ?? '')
    .split(/[,\s;|]+/)
    .map((action) => action.trim().toUpperCase())
    .filter(Boolean);
  if (actions.includes('ALL') || (actions.includes('PUB') && actions.includes('SUB'))) {
    return 'ALL';
  }
  if (actions.includes('PUB')) return 'PUB';
  if (actions.includes('SUB')) return 'SUB';
  return actions.includes('DENY') ? 'DENY' : 'UNKNOWN';
};

const splitWhitelist = (value?: string | null): string[] =>
  (value ?? '')
    .split(/[,\s;]+/)
    .map((item) => item.trim())
    .filter(Boolean);

const cidrPrefix = (address: string): number | null => {
  const match = address.match(/\/(\d{1,3})$/);
  if (!match) return null;
  const prefix = Number(match[1]);
  return Number.isFinite(prefix) ? prefix : null;
};

const classifyWhitelistAddress = (address: string): WhitelistClass => {
  const normalized = address.trim().toLowerCase();
  if (!normalized) return 'scoped';
  if (OPEN_WHITELIST_VALUES.has(normalized)) return 'open';
  if (normalized.includes('*')) return 'open';

  const prefix = cidrPrefix(normalized);
  if (prefix === null) return 'scoped';
  if (normalized.includes(':')) {
    return prefix <= 16 ? 'broad' : 'scoped';
  }
  return prefix <= 8 ? 'broad' : 'scoped';
};

const classifyWhitelist = (addresses: string[]): WhitelistClass => {
  if (addresses.some((address) => classifyWhitelistAddress(address) === 'open')) return 'open';
  if (addresses.some((address) => classifyWhitelistAddress(address) === 'broad')) return 'broad';
  return 'scoped';
};

const parsePermissionEntry = (entry: string): ParsedPermissionEntry => {
  const raw = entry.trim();
  const separatorIndex = raw.search(/[:=]/);
  if (separatorIndex < 0) {
    return {
      raw,
      resource: raw,
      permission: 'UNKNOWN',
    };
  }
  return {
    raw,
    resource: raw.slice(0, separatorIndex).trim(),
    permission: normalizePermission(raw.slice(separatorIndex + 1)),
  };
};

const parsePermissionEntries = (entries?: string[]): ParsedPermissionEntry[] =>
  (entries ?? []).map(parsePermissionEntry).filter((entry) => entry.raw.length > 0);

const isWildcardResource = (resource: string): boolean => {
  const normalized = resource.trim();
  return normalized === '*' || normalized === '*>*' || normalized === '*/*';
};

const accountKey = (account: PlainAccessConfig, index: number): string =>
  (account.accessKey || `account-${index + 1}`).trim();

const issue = (
  code: AclRiskIssueCode,
  severity: AclRiskSeverity,
  titleKey: string,
  descriptionKey: string,
  recommendationKey: string,
  options: {
    account?: string;
    evidence?: string[];
    id?: string;
  } = {},
): AclRiskIssue => ({
  id: options.id ?? [options.account, code, ...(options.evidence ?? [])].filter(Boolean).join(':'),
  code,
  severity,
  titleKey,
  descriptionKey,
  account: options.account,
  evidence: options.evidence ?? [],
  recommendationKey,
});

const hasDefaultAllow = (account: PlainAccessConfig): boolean =>
  PERMISSION_ALLOW_RANK[normalizePermission(account.defaultTopicPerm)] > 0 ||
  PERMISSION_ALLOW_RANK[normalizePermission(account.defaultGroupPerm)] > 0;

const wildcardEntries = (entries: ParsedPermissionEntry[]): ParsedPermissionEntry[] =>
  entries.filter((entry) => isWildcardResource(entry.resource) && entry.permission !== 'DENY');

const hasWildcardPermission = (account: PlainAccessConfig): boolean =>
  wildcardEntries(parsePermissionEntries(account.topicPerms)).length > 0 ||
  wildcardEntries(parsePermissionEntries(account.groupPerms)).length > 0;

const collectDuplicateAccessKeys = (accounts: PlainAccessConfig[]): Set<string> => {
  const seen = new Set<string>();
  const duplicates = new Set<string>();

  accounts.forEach((account, index) => {
    const key = accountKey(account, index);
    if (!account.accessKey?.trim()) return;
    if (seen.has(key)) duplicates.add(key);
    seen.add(key);
  });

  return duplicates;
};

const accountWhitelistClass = (account: PlainAccessConfig): WhitelistClass =>
  classifyWhitelist(splitWhitelist(account.whiteRemoteAddress));

const addDefaultPermissionIssues = (
  issues: AclRiskIssue[],
  account: PlainAccessConfig,
  accessKey: string,
) => {
  const topicPermission = normalizePermission(account.defaultTopicPerm);
  const groupPermission = normalizePermission(account.defaultGroupPerm);

  if (topicPermission === 'ALL') {
    issues.push(
      issue(
        'DEFAULT_TOPIC_ALLOW',
        'critical',
        'aclRisk.defaultTopicAllowCritical.title',
        'aclRisk.defaultTopicAllowCritical.desc',
        'aclRisk.defaultTopicAllow.recommendation',
        { account: accessKey, evidence: [`defaultTopicPerm=${account.defaultTopicPerm ?? '-'}`] },
      ),
    );
  } else if (topicPermission === 'PUB' || topicPermission === 'SUB') {
    issues.push(
      issue(
        'DEFAULT_TOPIC_ALLOW',
        'warning',
        'aclRisk.defaultTopicAllowWarning.title',
        'aclRisk.defaultTopicAllowWarning.desc',
        'aclRisk.defaultTopicDenyFirst.recommendation',
        { account: accessKey, evidence: [`defaultTopicPerm=${account.defaultTopicPerm ?? '-'}`] },
      ),
    );
  }

  if (groupPermission === 'ALL') {
    issues.push(
      issue(
        'DEFAULT_GROUP_ALLOW',
        'critical',
        'aclRisk.defaultGroupAllowCritical.title',
        'aclRisk.defaultGroupAllowCritical.desc',
        'aclRisk.defaultGroupAllow.recommendation',
        { account: accessKey, evidence: [`defaultGroupPerm=${account.defaultGroupPerm ?? '-'}`] },
      ),
    );
  } else if (groupPermission === 'PUB' || groupPermission === 'SUB') {
    issues.push(
      issue(
        'DEFAULT_GROUP_ALLOW',
        'warning',
        'aclRisk.defaultGroupAllowWarning.title',
        'aclRisk.defaultGroupAllowWarning.desc',
        'aclRisk.defaultGroupDenyFirst.recommendation',
        { account: accessKey, evidence: [`defaultGroupPerm=${account.defaultGroupPerm ?? '-'}`] },
      ),
    );
  }
};

const addWildcardPermissionIssues = (
  issues: AclRiskIssue[],
  account: PlainAccessConfig,
  accessKey: string,
) => {
  const topicWildcards = wildcardEntries(parsePermissionEntries(account.topicPerms));
  const groupWildcards = wildcardEntries(parsePermissionEntries(account.groupPerms));

  topicWildcards.forEach((entry) => {
    issues.push(
      issue(
        'WILDCARD_TOPIC_PERMISSION',
        entry.permission === 'ALL' ? 'critical' : 'warning',
        'aclRisk.wildcardTopicPermission.title',
        'aclRisk.wildcardTopicPermission.desc',
        'aclRisk.wildcardTopicPermission.recommendation',
        { account: accessKey, evidence: [entry.raw] },
      ),
    );
  });

  groupWildcards.forEach((entry) => {
    issues.push(
      issue(
        'WILDCARD_GROUP_PERMISSION',
        entry.permission === 'ALL' ? 'critical' : 'warning',
        'aclRisk.wildcardGroupPermission.title',
        'aclRisk.wildcardGroupPermission.desc',
        'aclRisk.wildcardGroupPermission.recommendation',
        { account: accessKey, evidence: [entry.raw] },
      ),
    );
  });
};

const addInvalidPermissionEntryIssues = (
  issues: AclRiskIssue[],
  account: PlainAccessConfig,
  accessKey: string,
) => {
  const invalidEntries = [
    ...parsePermissionEntries(account.topicPerms),
    ...parsePermissionEntries(account.groupPerms),
  ].filter((entry) => entry.permission === 'UNKNOWN');

  invalidEntries.forEach((entry) => {
    issues.push(
      issue(
        'INVALID_PERMISSION_ENTRY',
        'warning',
        'aclRisk.invalidPermissionEntry.title',
        'aclRisk.invalidPermissionEntry.desc',
        'aclRisk.invalidPermissionEntry.recommendation',
        { account: accessKey, evidence: [entry.raw] },
      ),
    );
  });
};

const addAccountIssues = (
  issues: AclRiskIssue[],
  account: PlainAccessConfig,
  index: number,
  duplicateAccessKeys: Set<string>,
) => {
  const accessKey = accountKey(account, index);
  const whitelistClass = accountWhitelistClass(account);
  const whitelist = splitWhitelist(account.whiteRemoteAddress);

  if (!account.accessKey?.trim()) {
    issues.push(
      issue(
        'MISSING_ACCESS_KEY',
        'critical',
        'aclRisk.missingAccessKey.title',
        'aclRisk.missingAccessKey.desc',
        'aclRisk.missingAccessKey.recommendation',
        { account: accessKey, id: `${index}:MISSING_ACCESS_KEY` },
      ),
    );
  }

  if (duplicateAccessKeys.has(accessKey)) {
    issues.push(
      issue(
        'DUPLICATE_ACCESS_KEY',
        'critical',
        'aclRisk.duplicateAccessKey.title',
        'aclRisk.duplicateAccessKey.desc',
        'aclRisk.duplicateAccessKey.recommendation',
        {
          account: accessKey,
          evidence: [accessKey],
          id: `${index}:${accessKey}:DUPLICATE_ACCESS_KEY`,
        },
      ),
    );
  }

  if (whitelistClass === 'open' || whitelistClass === 'broad') {
    issues.push(
      issue(
        'BROAD_ACCOUNT_WHITELIST',
        whitelistClass === 'open' ? 'critical' : 'warning',
        'aclRisk.broadAccountWhitelist.title',
        'aclRisk.broadAccountWhitelist.desc',
        'aclRisk.broadAccountWhitelist.recommendation',
        { account: accessKey, evidence: whitelist.length ? whitelist : ['<empty>'] },
      ),
    );
  }

  if (account.admin && (whitelistClass === 'open' || whitelistClass === 'broad')) {
    issues.push(
      issue(
        'ADMIN_WITH_BROAD_ACCESS',
        'critical',
        'aclRisk.adminWithBroadAccess.title',
        'aclRisk.adminWithBroadAccess.desc',
        'aclRisk.adminWithBroadAccess.recommendation',
        { account: accessKey, evidence: whitelist.length ? whitelist : ['<empty>'] },
      ),
    );
  }

  addDefaultPermissionIssues(issues, account, accessKey);
  addWildcardPermissionIssues(issues, account, accessKey);
  addInvalidPermissionEntryIssues(issues, account, accessKey);
};

const buildSummary = (config: AclClusterConfig): AclRiskSummary => {
  const duplicateAccessKeys = collectDuplicateAccessKeys(config.accounts);
  const broadGlobalCount = config.globalWhiteRemoteAddresses.filter((address) => {
    const whitelistClass = classifyWhitelistAddress(address);
    return whitelistClass === 'open' || whitelistClass === 'broad';
  }).length;

  const accountBroadCount = config.accounts.filter((account) => {
    const whitelistClass = accountWhitelistClass(account);
    return whitelistClass === 'open' || whitelistClass === 'broad';
  }).length;

  return {
    accountCount: config.accountCount ?? config.accounts.length,
    adminAccountCount: config.accounts.filter((account) => account.admin).length,
    defaultAllowAccountCount: config.accounts.filter(hasDefaultAllow).length,
    wildcardPermissionAccountCount: config.accounts.filter(hasWildcardPermission).length,
    broadWhitelistCount: broadGlobalCount + accountBroadCount,
    duplicateAccessKeyCount: duplicateAccessKeys.size,
  };
};

const scoreDiagnostics = (issues: AclRiskIssue[]): number => {
  const penalty = issues.reduce((sum, item) => {
    if (item.severity === 'critical') return sum + 25;
    if (item.severity === 'warning') return sum + 10;
    return sum + 4;
  }, 0);
  return Math.max(0, 100 - penalty);
};

const statusFromIssues = (issues: AclRiskIssue[], score: number): AclRiskStatus => {
  if (issues.some((item) => item.severity === 'critical') || score < 60) return 'critical';
  if (issues.some((item) => item.severity === 'warning') || score < 90) return 'warning';
  return 'healthy';
};

const buildRecommendations = (issues: AclRiskIssue[]): string[] => {
  const recommendations: string[] = [];
  const seen = new Set<string>();

  issues.forEach((item) => {
    if (seen.has(item.recommendationKey)) return;
    seen.add(item.recommendationKey);
    recommendations.push(item.recommendationKey);
  });

  if (recommendations.length === 0) {
    recommendations.push('aclRisk.recommendation.default');
  }

  return recommendations.slice(0, 6);
};

export const analyzeAclRisk = (config: AclClusterConfig): AclRiskDiagnostics => {
  const issues: AclRiskIssue[] = [];
  const accounts = config.accounts ?? [];
  const duplicateAccessKeys = collectDuplicateAccessKeys(accounts);
  const globalWhitelistClass = classifyWhitelist(config.globalWhiteRemoteAddresses ?? []);

  if (!config.aclEnabled) {
    issues.push(
      issue(
        'ACL_DISABLED',
        'critical',
        'aclRisk.aclDisabled.title',
        'aclRisk.aclDisabled.desc',
        'aclRisk.aclDisabled.recommendation',
        { evidence: [`clusterId=${config.clusterId}`] },
      ),
    );
  }

  if (!config.aclVersion.toUpperCase().includes('2.0')) {
    issues.push(
      issue(
        'LEGACY_ACL_VERSION',
        'info',
        'aclRisk.legacyAclVersion.title',
        'aclRisk.legacyAclVersion.desc',
        'aclRisk.legacyAclVersion.recommendation',
        { evidence: [config.aclVersion || '<unknown>'] },
      ),
    );
  }

  if (accounts.length === 0) {
    issues.push(
      issue(
        'NO_PLAIN_ACCESS_ACCOUNTS',
        config.aclEnabled ? 'critical' : 'warning',
        'aclRisk.noPlainAccessAccounts.title',
        'aclRisk.noPlainAccessAccounts.desc',
        'aclRisk.noPlainAccessAccounts.recommendation',
        { evidence: [`accountCount=${config.accountCount ?? 0}`] },
      ),
    );
  }

  if (globalWhitelistClass === 'open' || globalWhitelistClass === 'broad') {
    issues.push(
      issue(
        'BROAD_GLOBAL_WHITELIST',
        globalWhitelistClass === 'open' ? 'critical' : 'warning',
        'aclRisk.broadGlobalWhitelist.title',
        'aclRisk.broadGlobalWhitelist.desc',
        'aclRisk.broadGlobalWhitelist.recommendation',
        { evidence: config.globalWhiteRemoteAddresses },
      ),
    );
  }

  const adminAccounts = accounts.filter((account) => account.admin);
  if (adminAccounts.length > 1) {
    issues.push(
      issue(
        'MULTIPLE_ADMIN_ACCOUNTS',
        'warning',
        'aclRisk.multipleAdminAccounts.title',
        'aclRisk.multipleAdminAccounts.desc',
        'aclRisk.multipleAdminAccounts.recommendation',
        { evidence: adminAccounts.map((account, index) => accountKey(account, index)) },
      ),
    );
  }

  accounts.forEach((account, index) => {
    addAccountIssues(issues, account, index, duplicateAccessKeys);
  });

  const score = scoreDiagnostics(issues);
  const status = statusFromIssues(issues, score);

  return {
    status,
    statusKey: STATUS_KEY[status],
    statusColor: STATUS_COLOR[status],
    score,
    summary: buildSummary({
      ...config,
      accounts,
    }),
    issues,
    recommendationKeys: buildRecommendations(issues),
  };
};
