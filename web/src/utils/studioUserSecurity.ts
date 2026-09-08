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

import type { StudioUser } from '../api/studioUsers';

export type StudioPasswordLevel = 'empty' | 'weak' | 'fair' | 'strong';
export type StudioPasswordCheckSeverity = 'success' | 'warning' | 'error';

export type StudioPasswordCheckCode =
  | 'MIN_LENGTH'
  | 'LONG_LENGTH'
  | 'MIXED_CASE'
  | 'DIGIT'
  | 'SYMBOL'
  | 'NO_COMMON_WORD'
  | 'NO_SEQUENCE'
  | 'NO_REPEATED_CHARS'
  | 'NO_USERNAME'
  | 'NOT_CURRENT_PASSWORD';

export interface StudioPasswordCheck {
  code: StudioPasswordCheckCode;
  label: string;
  passed: boolean;
  severity: StudioPasswordCheckSeverity;
  detail: string;
}

export interface StudioPasswordEvaluationContext {
  username?: string | null;
  currentPassword?: string | null;
}

export interface StudioPasswordEvaluation {
  score: number;
  level: StudioPasswordLevel;
  label: string;
  color: string;
  checks: StudioPasswordCheck[];
  failedChecks: StudioPasswordCheck[];
  suggestions: string[];
}

export type StudioUserSecurityStatus = 'healthy' | 'warning' | 'critical';
export type StudioUserSecurityRiskSeverity = Exclude<StudioUserSecurityStatus, 'healthy'> | 'info';

export type StudioUserSecurityRiskCode =
  | 'NO_VISIBLE_USERS'
  | 'STALE_PASSWORD'
  | 'UNKNOWN_PASSWORD_AGE'
  | 'STALE_ADMIN_PASSWORD'
  | 'ONLY_ONE_ACTIVE_ADMIN';

export interface StudioUserSecurityRisk {
  code: StudioUserSecurityRiskCode;
  severity: StudioUserSecurityRiskSeverity;
  title: string;
  description: string;
  count: number;
  users: string[];
  recommendation: string;
}

export type StudioUserPasswordRotationStatus = 'fresh' | 'stale' | 'unknown';

export interface StudioUserPasswordRotation {
  status: StudioUserPasswordRotationStatus;
  label: string;
  color: string;
  daysSinceChange: number | null;
}

export interface StudioUserSecuritySummary {
  status: StudioUserSecurityStatus;
  statusText: string;
  statusColor: 'success' | 'warning' | 'error';
  score: number;
  inspectedCount: number;
  totalCount: number;
  totalMayExceedInspected: boolean;
  adminCount: number;
  activeAdminCount: number;
  enabledCount: number;
  disabledCount: number;
  stalePasswordCount: number;
  staleAdminPasswordCount: number;
  unknownPasswordAgeCount: number;
  risks: StudioUserSecurityRisk[];
  recommendations: string[];
}

const MIN_PASSWORD_LENGTH = 8;
const STRONG_PASSWORD_LENGTH = 12;
const PASSWORD_ROTATION_DAYS = 180;
const PASSWORD_ROTATION_MS = PASSWORD_ROTATION_DAYS * 24 * 60 * 60 * 1000;
const LISTED_USER_LIMIT = 5;
const COMMON_PASSWORD_PARTS = [
  'password',
  'admin',
  'root',
  'rocketmq',
  'welcome',
  'qwerty',
  'letmein',
  'test123',
  'abc123',
  '123456',
  '111111',
  '000000',
];
const SEQUENCE_ALPHABETS = ['abcdefghijklmnopqrstuvwxyz', '0123456789', 'qwertyuiop', 'asdfghjkl'];

const clampScore = (value: number) => Math.max(0, Math.min(100, Math.round(value)));

const normalizeText = (value?: string | null) => (value ?? '').trim();

const normalizePassword = (value?: string | null) => value ?? '';

const includesUsername = (password: string, username?: string | null) => {
  const normalizedUsername = normalizeText(username).toLowerCase();
  return normalizedUsername.length >= 3 && password.toLowerCase().includes(normalizedUsername);
};

const hasCommonPasswordPart = (password: string) => {
  const normalized = password.toLowerCase();
  return COMMON_PASSWORD_PARTS.some((part) => normalized.includes(part));
};

const hasRepeatedRun = (password: string) => /(.)\1{2,}/.test(password);

const reverseText = (value: string) => value.split('').reverse().join('');

const hasSequence = (password: string) => {
  const normalized = password.toLowerCase();
  if (normalized.length < 4) return false;

  for (let size = 4; size >= 3; size -= 1) {
    for (let index = 0; index <= normalized.length - size; index += 1) {
      const token = normalized.slice(index, index + size);
      if (
        SEQUENCE_ALPHABETS.some(
          (alphabet) => alphabet.includes(token) || reverseText(alphabet).includes(token),
        )
      ) {
        return true;
      }
    }
  }
  return false;
};

const characterClassCount = (password: string) =>
  [
    /[a-z]/.test(password),
    /[A-Z]/.test(password),
    /\d/.test(password),
    /[^A-Za-z0-9]/.test(password),
  ].filter(Boolean).length;

const uniqueCharacterCount = (password: string) => new Set(password.split('')).size;

const check = (
  code: StudioPasswordCheckCode,
  label: string,
  passed: boolean,
  severity: StudioPasswordCheckSeverity,
  detail: string,
): StudioPasswordCheck => ({ code, label, passed, severity, detail });

const passwordLevel = (password: string, score: number): StudioPasswordLevel => {
  if (!password) return 'empty';
  if (score >= 75) return 'strong';
  if (score >= 50) return 'fair';
  return 'weak';
};

const passwordLevelMeta: Record<StudioPasswordLevel, { label: string; color: string }> = {
  empty: { label: '待输入', color: 'default' },
  weak: { label: '弱', color: '#ff4d4f' },
  fair: { label: '中等', color: '#faad14' },
  strong: { label: '强', color: '#52c41a' },
};

export function evaluateStudioPassword(
  passwordValue: string | undefined,
  context: StudioPasswordEvaluationContext = {},
): StudioPasswordEvaluation {
  const password = normalizePassword(passwordValue);
  const classCount = characterClassCount(password);
  const longEnough = password.length >= STRONG_PASSWORD_LENGTH;
  const hasDigit = /\d/.test(password);
  const hasSymbol = /[^A-Za-z0-9]/.test(password);
  const hasMixedCase = /[a-z]/.test(password) && /[A-Z]/.test(password);
  const repeatedRun = hasRepeatedRun(password);
  const sequence = hasSequence(password);
  const commonPart = password.length > 0 && hasCommonPasswordPart(password);
  const usernameIncluded = password.length > 0 && includesUsername(password, context.username);
  const currentPassword = normalizePassword(context.currentPassword);
  const sameAsCurrent = Boolean(currentPassword) && password === currentPassword;

  let score = 0;
  if (password.length >= MIN_PASSWORD_LENGTH) score += 24;
  if (password.length >= STRONG_PASSWORD_LENGTH) score += 16;
  if (password.length >= 16) score += 8;
  score += classCount * 10;
  if (uniqueCharacterCount(password) >= 8) score += 8;
  if (uniqueCharacterCount(password) >= 12) score += 4;
  if (longEnough && classCount >= 3) score += 10;

  if (password.length > 0 && password.length < MIN_PASSWORD_LENGTH) score -= 18;
  if (commonPart) score -= 24;
  if (sequence) score -= 14;
  if (repeatedRun) score -= 14;
  if (usernameIncluded) score -= 18;
  if (sameAsCurrent) score -= 24;

  const normalizedScore = password ? clampScore(score) : 0;
  const checks: StudioPasswordCheck[] = [
    check(
      'MIN_LENGTH',
      '至少 8 位',
      password.length >= MIN_PASSWORD_LENGTH,
      'error',
      '当前前端和服务端基础规则均要求密码不少于 8 位。',
    ),
    check(
      'LONG_LENGTH',
      '建议 12 位以上',
      longEnough,
      'warning',
      '更长的密码能显著降低被穷举的风险。',
    ),
    check('MIXED_CASE', '包含大小写字母', hasMixedCase, 'warning', '避免只使用单一字母形式。'),
    check('DIGIT', '包含数字', hasDigit, 'warning', '混合数字能增加密码组合空间。'),
    check('SYMBOL', '包含符号', hasSymbol, 'warning', '符号可减少常见词典命中概率。'),
    check(
      'NO_COMMON_WORD',
      '避开常见弱口令',
      !commonPart,
      'error',
      '不要包含 admin、password、rocketmq、123456 等常见片段。',
    ),
    check('NO_SEQUENCE', '避开连续序列', !sequence, 'warning', '连续键盘或数字序列容易被猜测。'),
    check(
      'NO_REPEATED_CHARS',
      '避开重复字符',
      !repeatedRun,
      'warning',
      '连续重复字符会降低密码强度。',
    ),
    check(
      'NO_USERNAME',
      '不包含用户名',
      !usernameIncluded,
      'error',
      '密码不应直接包含账号名或账号名前缀。',
    ),
    check(
      'NOT_CURRENT_PASSWORD',
      '不同于当前密码',
      !sameAsCurrent,
      'error',
      '修改自己的密码时，新密码不能和当前密码完全一致。',
    ),
  ];
  const failedChecks = checks.filter((item) => !item.passed);
  const level = passwordLevel(password, normalizedScore);
  const levelMeta = passwordLevelMeta[level];

  return {
    score: normalizedScore,
    level,
    label: levelMeta.label,
    color: levelMeta.color,
    checks,
    failedChecks,
    suggestions: failedChecks.slice(0, 4).map((item) => item.detail),
  };
}

const parseTimestamp = (value?: string | null) => {
  const normalized = normalizeText(value);
  if (!normalized) return null;
  const timestamp = Date.parse(normalized);
  return Number.isFinite(timestamp) ? timestamp : null;
};

const userNames = (users: StudioUser[]) =>
  users
    .map((user) => user.username || `#${user.id}`)
    .slice(0, LISTED_USER_LIMIT)
    .sort((left, right) => left.localeCompare(right));

const risk = (
  code: StudioUserSecurityRiskCode,
  severity: StudioUserSecurityRiskSeverity,
  title: string,
  description: string,
  users: StudioUser[],
  recommendation: string,
): StudioUserSecurityRisk => ({
  code,
  severity,
  title,
  description,
  count: users.length,
  users: userNames(users),
  recommendation,
});

export function getStudioUserPasswordRotation(
  user: StudioUser,
  now: Date = new Date(),
): StudioUserPasswordRotation {
  const changedAt = parseTimestamp(user.passwordChangedAt);
  if (changedAt === null) {
    return {
      status: 'unknown',
      label: '未知',
      color: 'default',
      daysSinceChange: null,
    };
  }

  const ageMs = Math.max(0, now.getTime() - changedAt);
  const daysSinceChange = Math.floor(ageMs / (24 * 60 * 60 * 1000));
  if (ageMs > PASSWORD_ROTATION_MS) {
    return {
      status: 'stale',
      label: '需轮换',
      color: 'warning',
      daysSinceChange,
    };
  }

  return {
    status: 'fresh',
    label: '正常',
    color: 'success',
    daysSinceChange,
  };
}

const statusMeta: Record<
  StudioUserSecurityStatus,
  { text: string; color: 'success' | 'warning' | 'error' }
> = {
  healthy: { text: '账号安全状态正常', color: 'success' },
  warning: { text: '账号安全需要关注', color: 'warning' },
  critical: { text: '账号安全存在高风险', color: 'error' },
};

export function analyzeStudioUsers(
  users: StudioUser[],
  totalCount = users.length,
  now: Date = new Date(),
): StudioUserSecuritySummary {
  const inspectedCount = users.length;
  const enabledUsers = users.filter((user) => user.enabled);
  const disabledUsers = users.filter((user) => !user.enabled);
  const adminUsers = users.filter((user) => user.admin);
  const activeAdmins = adminUsers.filter((user) => user.enabled);
  const stalePasswordUsers = users.filter(
    (user) => getStudioUserPasswordRotation(user, now).status === 'stale',
  );
  const staleAdminPasswordUsers = stalePasswordUsers.filter((user) => user.admin && user.enabled);
  const unknownPasswordAgeUsers = users.filter(
    (user) => getStudioUserPasswordRotation(user, now).status === 'unknown',
  );
  const risks: StudioUserSecurityRisk[] = [];

  if (inspectedCount === 0) {
    risks.push(
      risk(
        'NO_VISIBLE_USERS',
        'info',
        '当前筛选条件下没有账号',
        '用户列表为空，无法从当前页面评估账号状态。',
        [],
        '清除筛选条件或刷新列表后再检查账号安全状态。',
      ),
    );
  }

  if (stalePasswordUsers.length > 0) {
    risks.push(
      risk(
        'STALE_PASSWORD',
        'warning',
        '存在长期未轮换密码',
        `密码超过 ${PASSWORD_ROTATION_DAYS} 天未变更的账号需要安排轮换。`,
        stalePasswordUsers,
        '优先联系账号负责人完成密码轮换，或由管理员重置密码。',
      ),
    );
  }

  if (staleAdminPasswordUsers.length > 0) {
    risks.push(
      risk(
        'STALE_ADMIN_PASSWORD',
        'critical',
        '管理员密码长期未轮换',
        '具备管理员权限的账号长期未改密会扩大控制台误操作和凭据泄露风险。',
        staleAdminPasswordUsers,
        '先处理管理员账号，完成轮换后再复查普通账号。',
      ),
    );
  }

  if (unknownPasswordAgeUsers.length > 0) {
    risks.push(
      risk(
        'UNKNOWN_PASSWORD_AGE',
        'warning',
        '部分账号缺少改密时间',
        '这些账号无法判断密码轮换周期，通常来自旧数据或后端未返回改密字段。',
        unknownPasswordAgeUsers,
        '补齐 passwordChangedAt 数据，或在下次登录后触发一次改密。',
      ),
    );
  }

  if (inspectedCount > 1 && activeAdmins.length === 1) {
    risks.push(
      risk(
        'ONLY_ONE_ACTIVE_ADMIN',
        'warning',
        '仅有一个可用管理员',
        '当前页只看到一个启用的管理员账号，管理员失效时可能影响控制台维护。',
        activeAdmins,
        '确认完整账号列表中至少有两个可用管理员，避免单点账号风险。',
      ),
    );
  }

  const criticalRisks = risks.filter((item) => item.severity === 'critical').length;
  const warningRisks = risks.filter((item) => item.severity === 'warning').length;
  const score = clampScore(
    100 -
      staleAdminPasswordUsers.length * 18 -
      stalePasswordUsers.length * 10 -
      unknownPasswordAgeUsers.length * 8 -
      warningRisks * 6 -
      criticalRisks * 12,
  );
  const status: StudioUserSecurityStatus =
    criticalRisks > 0 ? 'critical' : warningRisks > 0 ? 'warning' : 'healthy';
  const meta = statusMeta[status];

  return {
    status,
    statusText: meta.text,
    statusColor: meta.color,
    score,
    inspectedCount,
    totalCount,
    totalMayExceedInspected: totalCount > inspectedCount,
    adminCount: adminUsers.length,
    activeAdminCount: activeAdmins.length,
    enabledCount: enabledUsers.length,
    disabledCount: disabledUsers.length,
    stalePasswordCount: stalePasswordUsers.length,
    staleAdminPasswordCount: staleAdminPasswordUsers.length,
    unknownPasswordAgeCount: unknownPasswordAgeUsers.length,
    risks,
    recommendations:
      risks.length > 0
        ? Array.from(new Set(risks.map((item) => item.recommendation)))
        : ['保持用户列表定期复查，并对管理员账号执行更短周期的密码轮换。'],
  };
}
