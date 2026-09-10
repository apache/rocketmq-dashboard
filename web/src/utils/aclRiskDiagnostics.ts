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
  title: string;
  description: string;
  account?: string;
  evidence: string[];
  recommendation: string;
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
  statusText: string;
  statusColor: 'success' | 'warning' | 'error';
  score: number;
  summary: AclRiskSummary;
  issues: AclRiskIssue[];
  recommendations: string[];
}

type AclPermission = 'DENY' | 'PUB' | 'SUB' | 'ALL' | 'UNKNOWN';

interface ParsedPermissionEntry {
  raw: string;
  resource: string;
  permission: AclPermission;
}

type WhitelistClass = 'open' | 'broad' | 'scoped';

const STATUS_TEXT: Record<AclRiskStatus, string> = {
  healthy: 'ACL 配置健康',
  warning: 'ACL 配置需要关注',
  critical: 'ACL 配置存在高风险',
};

const STATUS_TEXT_EN: Record<AclRiskStatus, string> = {
  healthy: 'ACL configuration healthy',
  warning: 'ACL configuration needs attention',
  critical: 'ACL configuration carries high risk',
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

const normalizePermission = (value?: string | null): AclPermission => {
  const normalized = (value ?? '').trim().toUpperCase();
  if (
    normalized === 'DENY' ||
    normalized === 'PUB' ||
    normalized === 'SUB' ||
    normalized === 'ALL'
  ) {
    return normalized;
  }
  return 'UNKNOWN';
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
  title: string,
  description: string,
  recommendation: string,
  options: {
    account?: string;
    evidence?: string[];
    id?: string;
  } = {},
): AclRiskIssue => ({
  id: options.id ?? [options.account, code, ...(options.evidence ?? [])].filter(Boolean).join(':'),
  code,
  severity,
  title,
  description,
  account: options.account,
  evidence: options.evidence ?? [],
  recommendation,
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
  lang: 'zh' | 'en' = 'zh',
) => {
  const topicPermission = normalizePermission(account.defaultTopicPerm);
  const groupPermission = normalizePermission(account.defaultGroupPerm);

  if (topicPermission === 'ALL') {
    issues.push(
      issue(
        'DEFAULT_TOPIC_ALLOW',
        'critical',
        (lang === 'en' ? 'Default Topic permission is too broad' : '默认 Topic 权限过大'),
        (lang === 'en' ? 'This account defaults to all Topic operations, so new Topics inherit high permissions automatically.' : '该账号默认允许所有 Topic 操作，新增 Topic 会自动继承高权限。'),
        (lang === 'en' ? 'Change the default Topic permission to DENY and grant minimal permissions per Topic that truly needs access.' : '将默认 Topic 权限改为 DENY，并为确需访问的 Topic 配置最小权限。'),
        { account: accessKey, evidence: [`defaultTopicPerm=${account.defaultTopicPerm ?? '-'}`] },
      ),
    );
  } else if (topicPermission === 'PUB' || topicPermission === 'SUB') {
    issues.push(
      issue(
        'DEFAULT_TOPIC_ALLOW',
        'warning',
        (lang === 'en' ? 'Default Topic permission is not DENY' : '默认 Topic 权限非 DENY'),
        (lang === 'en' ? 'The account automatically gains default access to new Topics; the boundary relies on naming conventions.' : '该账号会自动获得新增 Topic 的默认访问能力，权限边界依赖命名规范。'),
        (lang === 'en' ? 'Prefer DENY as the default Topic permission and authorize through the Topic permission list.' : '优先使用 DENY 作为默认 Topic 权限，再通过 Topic 权限列表授权。'),
        { account: accessKey, evidence: [`defaultTopicPerm=${account.defaultTopicPerm ?? '-'}`] },
      ),
    );
  }

  if (groupPermission === 'ALL') {
    issues.push(
      issue(
        'DEFAULT_GROUP_ALLOW',
        'critical',
        (lang === 'en' ? 'Default Group permission is too broad' : '默认 Group 权限过大'),
        (lang === 'en' ? 'This account defaults to all Consumer Group operations, so new Groups inherit high permissions automatically.' : '该账号默认允许所有 Consumer Group 操作，新增 Group 会自动继承高权限。'),
        (lang === 'en' ? 'Change the default Group permission to DENY and grant minimal permissions per Group that truly needs it.' : '将默认 Group 权限改为 DENY，并为确需订阅的 Group 配置最小权限。'),
        { account: accessKey, evidence: [`defaultGroupPerm=${account.defaultGroupPerm ?? '-'}`] },
      ),
    );
  } else if (groupPermission === 'PUB' || groupPermission === 'SUB') {
    issues.push(
      issue(
        'DEFAULT_GROUP_ALLOW',
        'warning',
        (lang === 'en' ? 'Default Group permission is not DENY' : '默认 Group 权限非 DENY'),
        (lang === 'en' ? 'The account automatically gains default access to new Consumer Groups.' : '该账号会自动获得新增 Consumer Group 的默认访问能力。'),
        (lang === 'en' ? 'Prefer DENY as the default Group permission and authorize through the Group permission list.' : '优先使用 DENY 作为默认 Group 权限，再通过 Group 权限列表授权。'),
        { account: accessKey, evidence: [`defaultGroupPerm=${account.defaultGroupPerm ?? '-'}`] },
      ),
    );
  }
};

const addWildcardPermissionIssues = (
  issues: AclRiskIssue[],
  account: PlainAccessConfig,
  accessKey: string,
  lang: 'zh' | 'en' = 'zh',
) => {
  const topicWildcards = wildcardEntries(parsePermissionEntries(account.topicPerms));
  const groupWildcards = wildcardEntries(parsePermissionEntries(account.groupPerms));

  topicWildcards.forEach((entry) => {
    issues.push(
      issue(
        'WILDCARD_TOPIC_PERMISSION',
        entry.permission === 'ALL' ? 'critical' : 'warning',
        (lang === 'en' ? 'Topic wildcard grants are too broad' : 'Topic 通配授权过大'),
        (lang === 'en' ? 'The account gains Topic access through wildcard resources, which may cover Topics created later.' : '该账号通过通配资源获得 Topic 访问能力，可能覆盖未来新增 Topic。'),
        (lang === 'en' ? 'Narrow wildcard Topic grants to specific Topics or business prefixes and avoid *=ALL.' : '将通配 Topic 授权收敛为具体 Topic 或业务前缀，并避免 *=ALL。'),
        { account: accessKey, evidence: [entry.raw] },
      ),
    );
  });

  groupWildcards.forEach((entry) => {
    issues.push(
      issue(
        'WILDCARD_GROUP_PERMISSION',
        entry.permission === 'ALL' ? 'critical' : 'warning',
        (lang === 'en' ? 'Group wildcard grants are too broad' : 'Group 通配授权过大'),
        (lang === 'en' ? 'The account gains Consumer Group access through wildcard resources, which may cover Groups created later.' : '该账号通过通配资源获得 Consumer Group 访问能力，可能覆盖未来新增 Group。'),
        (lang === 'en' ? 'Narrow wildcard Group grants to specific Groups or business prefixes and avoid *=ALL.' : '将通配 Group 授权收敛为具体 Group 或业务前缀，并避免 *=ALL。'),
        { account: accessKey, evidence: [entry.raw] },
      ),
    );
  });
};

const addInvalidPermissionEntryIssues = (
  issues: AclRiskIssue[],
  account: PlainAccessConfig,
  accessKey: string,
  lang: 'zh' | 'en' = 'zh',
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
        (lang === 'en' ? 'Permission entry format is unrecognized' : '权限条目格式无法识别'),
        (lang === 'en' ? 'The entry has no clear PUB, SUB, ALL or DENY decision, so the effective permission cannot be judged.' : '该权限条目没有明确的 PUB、SUB、ALL 或 DENY 决策，诊断无法判断最终权限。'),
        (lang === 'en' ? 'Fix entries to the resource=PUB, resource=SUB, resource=ALL or resource=DENY format.' : '按 resource=PUB、resource=SUB、resource=ALL 或 resource=DENY 的格式修正条目。'),
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
  lang: 'zh' | 'en' = 'zh',
) => {
  const accessKey = accountKey(account, index);
  const whitelistClass = accountWhitelistClass(account);
  const whitelist = splitWhitelist(account.whiteRemoteAddress);

  if (!account.accessKey?.trim()) {
    issues.push(
      issue(
        'MISSING_ACCESS_KEY',
        'critical',
        (lang === 'en' ? 'Missing Access Key' : 'Access Key 缺失'),
        (lang === 'en' ? 'A Plain Access account without an Access Key cannot form an auditable identity boundary.' : 'Plain Access 账号缺少 Access Key，无法形成可审计的身份边界。'),
        (lang === 'en' ? 'Fill in the Access Key or remove account configurations without a recognizable identity.' : '补全 Access Key，或删除无法识别身份的账号配置。'),
        { account: accessKey, id: `${index}:MISSING_ACCESS_KEY` },
      ),
    );
  }

  if (duplicateAccessKeys.has(accessKey)) {
    issues.push(
      issue(
        'DUPLICATE_ACCESS_KEY',
        'critical',
        (lang === 'en' ? 'Duplicate Access Key' : 'Access Key 重复'),
        (lang === 'en' ? 'The same Access Key appears in multiple Plain Access accounts, so merged permissions can be misjudged.' : '同一个 Access Key 出现在多个 Plain Access 账号中，权限合并结果容易被误判。'),
        (lang === 'en' ? 'Keep a single account definition, merge needed permissions and remove duplicates.' : '保留唯一账号定义，合并必要权限后删除重复条目。'),
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
        (lang === 'en' ? 'Account IP whitelist is too broad' : '账号 IP 白名单范围过大'),
        (lang === 'en' ? 'The whitelist is too wide and weakens the dual constraint of the ACL account and network source.' : '该账号的 IP 白名单覆盖范围过宽，弱化了 ACL 账号和网络来源的双重约束。'),
        (lang === 'en' ? 'Narrow the account whitelist to application egress addresses or trusted subnets.' : '将账号白名单收敛到应用出口地址或可信网段。'),
        { account: accessKey, evidence: whitelist.length ? whitelist : ['<empty>'] },
      ),
    );
  }

  if (account.admin && (whitelistClass === 'open' || whitelistClass === 'broad')) {
    issues.push(
      issue(
        'ADMIN_WITH_BROAD_ACCESS',
        'critical',
        (lang === 'en' ? 'Admin account accessible from broad networks' : '管理员账号可从宽网段访问'),
        (lang === 'en' ? 'An admin account with a loose IP whitelist widens the blast radius of misuse or leaks to the whole cluster.' : '管理员账号叠加宽松 IP 白名单后，误用或泄露影响范围会扩大到整个集群。'),
        (lang === 'en' ? 'Give admin accounts dedicated Access Keys and tightly constrained IP whitelists, and minimize long-lived admins.' : '为管理员账号配置专用 Access Key、强约束 IP 白名单，并尽量减少长期管理员账号。'),
        { account: accessKey, evidence: whitelist.length ? whitelist : ['<empty>'] },
      ),
    );
  }

  addDefaultPermissionIssues(issues, account, accessKey, lang);
  addWildcardPermissionIssues(issues, account, accessKey, lang);
  addInvalidPermissionEntryIssues(issues, account, accessKey, lang);
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

const buildRecommendations = (
  issues: AclRiskIssue[],
  lang: 'zh' | 'en' = 'zh',
): string[] => {
  const recommendations: string[] = [];
  const seen = new Set<string>();

  issues.forEach((item) => {
    if (seen.has(item.recommendation)) return;
    seen.add(item.recommendation);
    recommendations.push(item.recommendation);
  });

  if (recommendations.length === 0) {
    recommendations.push(
      lang === 'en'
        ? 'Keep the default permission as DENY and grant new accounts minimal access per business resource.'
        : '保持默认权限为 DENY，新增账号时继续按业务资源最小授权。',
    );
  }

  return recommendations.slice(0, 6);
};

export const analyzeAclRisk = (
  config: AclClusterConfig,
  lang: 'zh' | 'en' = 'zh',
): AclRiskDiagnostics => {
  const issues: AclRiskIssue[] = [];
  const accounts = config.accounts ?? [];
  const duplicateAccessKeys = collectDuplicateAccessKeys(accounts);
  const globalWhitelistClass = classifyWhitelist(config.globalWhiteRemoteAddresses ?? []);

  if (!config.aclEnabled) {
    issues.push(
      issue(
        'ACL_DISABLED',
        'critical',
        (lang === 'en' ? 'ACL is disabled' : 'ACL 未启用'),
        (lang === 'en' ? 'ACL is not enabled on this cluster; client access relies mainly on the network boundary.' : '当前集群没有启用 ACL，客户端访问主要依赖网络边界。'),
        (lang === 'en' ? 'Enable ACL on production clusters and grant minimal permissions to admin and application accounts.' : '在生产集群启用 ACL，并为管理员和应用账号配置最小权限。'),
        { evidence: [`clusterId=${config.clusterId}`] },
      ),
    );
  }

  if (!config.aclVersion.toUpperCase().includes('2.0')) {
    issues.push(
      issue(
        'LEGACY_ACL_VERSION',
        'info',
        (lang === 'en' ? 'ACL version is old or unknown' : 'ACL 版本较旧或未知'),
        (lang === 'en' ? 'The current version is not clearly ACL 2.0, so some fine-grained permission capabilities may be unavailable.' : '当前版本不是明确的 ACL 2.0，部分细粒度权限能力可能不可用。'),
        (lang === 'en' ? 'Confirm the cluster ACL version and evaluate migrating to ACL 2.0 in an upgrade window.' : '确认集群 ACL 版本，并在升级窗口评估迁移到 ACL 2.0。'),
        { evidence: [config.aclVersion || '<unknown>'] },
      ),
    );
  }

  if (accounts.length === 0) {
    issues.push(
      issue(
        'NO_PLAIN_ACCESS_ACCOUNTS',
        config.aclEnabled ? 'critical' : 'warning',
        (lang === 'en' ? 'No Plain Access accounts configured' : '未配置 Plain Access 账号'),
        (lang === 'en' ? 'No Plain Access accounts are configured; enabling ACL may block client or operator authentication.' : '集群配置中没有 Plain Access 账号，启用 ACL 后可能导致客户端或运维账号无法认证。'),
        (lang === 'en' ? 'Configure at least one controlled admin account plus the needed application accounts before enabling strict ACL.' : '至少配置一个受控管理员账号和必要的应用账号，再启用严格 ACL 策略。'),
        { evidence: [`accountCount=${config.accountCount ?? 0}`] },
      ),
    );
  }

  if (globalWhitelistClass === 'open' || globalWhitelistClass === 'broad') {
    issues.push(
      issue(
        'BROAD_GLOBAL_WHITELIST',
        globalWhitelistClass === 'open' ? 'critical' : 'warning',
        (lang === 'en' ? 'Global IP whitelist is too broad' : '全局 IP 白名单范围过大'),
        (lang === 'en' ? 'The global whitelist bypasses account-level checks; overly broad networks weaken ACL isolation.' : '全局白名单会绕过账号级权限判断，过宽网段会降低 ACL 的实际隔离效果。'),
        (lang === 'en' ? 'Remove the global wildcard whitelist and configure per-account application egress addresses instead.' : '删除全局通配白名单，改为按账号配置必要的应用出口地址。'),
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
        (lang === 'en' ? 'Too many admin accounts' : '管理员账号数量偏多'),
        (lang === 'en' ? 'Multiple long-lived admin accounts raise the cost of credential rotation and misauthorization.' : '多个长期管理员账号会增加凭据轮转和误授权的管理成本。'),
        (lang === 'en' ? 'Keep the fewest admin accounts and move routine application access to non-admin accounts.' : '保留最少数量的管理员账号，并将日常应用访问改为非管理员账号。'),
        { evidence: adminAccounts.map((account, index) => accountKey(account, index)) },
      ),
    );
  }

  accounts.forEach((account, index) => {
    addAccountIssues(issues, account, index, duplicateAccessKeys, lang);
  });

  const score = scoreDiagnostics(issues);
  const status = statusFromIssues(issues, score);

  return {
    status,
    statusText: lang === 'en' ? STATUS_TEXT_EN[status] : STATUS_TEXT[status],
    statusColor: STATUS_COLOR[status],
    score,
    summary: buildSummary({
      ...config,
      accounts,
    }),
    issues,
    recommendations: buildRecommendations(issues, lang),
  };
};
