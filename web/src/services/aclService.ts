import { isMockMode } from './dataMode';
import * as aclApi from '../api/acl';
import type {
  AclRule,
  AclRuleQuery,
  AclUser,
  AclClusterConfig,
  PlainAccessConfig,
} from '../api/acl';
import { aclRules as mockRules, aclUsers as mockUsers } from '../mock/acl';

const aclRulesState = mockRules as unknown as AclRule[];
const aclUsersState = mockUsers as unknown as AclUser[];
const aclPlainAccessState = (mockUsers as unknown as AclUser[]).map((u): PlainAccessConfig => ({
  accessKey: u.username,
  secretKey: u.secretKey,
  whiteRemoteAddress: '',
  admin: u.admin,
  defaultTopicPerm: 'DENY',
  defaultGroupPerm: 'DENY',
  topicPerms: u.admin ? ['*=ALL'] : [],
  groupPerms: u.admin ? ['*=ALL'] : [],
  createdAt: u.createdAt,
}));

function copyAclRule(rule: AclRule): AclRule {
  return {
    ...rule,
    actions: [...rule.actions],
  };
}

function copyAclUser(user: AclUser): AclUser {
  return {
    ...user,
    clusters: [...user.clusters],
  };
}

export async function listAclRules(params?: AclRuleQuery): Promise<AclRule[]> {
  if (isMockMode()) {
    let result = [...aclRulesState];
    if (params?.principal) {
      const principal = params.principal.toLowerCase();
      result = result.filter((rule) => rule.principal.toLowerCase().includes(principal));
    }
    return result.map(copyAclRule);
  }
  return aclApi.listAclRules(params);
}

export async function listAclUsers(params?: { keyword?: string }): Promise<AclUser[]> {
  if (isMockMode()) {
    let result = [...aclUsersState];
    if (params?.keyword) {
      const kw = params.keyword.toLowerCase();
      result = result.filter((u) => u.username.toLowerCase().includes(kw));
    }
    return result.map(copyAclUser);
  }
  return aclApi.listAclUsers(params);
}

export async function getAclUserCredentials(id: string): Promise<AclUser> {
  if (isMockMode()) {
    const user = aclUsersState.find((u) => u.id === id);
    if (!user) throw new Error(`ACL user not found: ${id}`);
    return copyAclUser(user);
  }
  return aclApi.getAclUserCredentials(id);
}

export async function createAclRule(data: Partial<AclRule>): Promise<AclRule> {
  if (isMockMode()) {
    const rule: AclRule = {
      id: `acl-${Date.now()}`,
      principal: '',
      resource: '',
      resourceType: '',
      resourcePattern: '',
      decision: '',
      scope: '',
      aclVersion: '2.0',
      createdAt: new Date().toISOString(),
      ...data,
      actions: [...(data.actions ?? [])],
    };
    aclRulesState.push(rule);
    return copyAclRule(rule);
  }
  return aclApi.createAclRule(data);
}

export async function updateAclRule(data: Partial<AclRule>): Promise<AclRule> {
  if (isMockMode()) {
    const idx = aclRulesState.findIndex((rule) => rule.id === data.id);
    if (idx < 0) throw new Error(`ACL rule not found: ${data.id}`);
    aclRulesState[idx] = {
      ...aclRulesState[idx],
      ...data,
      actions: data.actions ? [...data.actions] : [...aclRulesState[idx].actions],
    };
    return copyAclRule(aclRulesState[idx]);
  }
  return aclApi.updateAclRule(data);
}

export async function deleteAclRule(id: string): Promise<void> {
  if (isMockMode()) {
    const idx = aclRulesState.findIndex((rule) => rule.id === id);
    if (idx < 0) throw new Error(`ACL rule not found: ${id}`);
    aclRulesState.splice(idx, 1);
    return;
  }
  return aclApi.deleteAclRule(id);
}

export async function createAclUser(data: Partial<AclUser>): Promise<AclUser> {
  if (isMockMode()) {
    const user: AclUser = {
      id: `user-${Date.now()}`,
      username: '',
      accessKey: '',
      secretKey: '',
      admin: false,
      createdAt: new Date().toISOString(),
      ...data,
      clusters: [...(data.clusters ?? [])],
    };
    aclUsersState.push(user);
    return copyAclUser(user);
  }
  return aclApi.createAclUser(data);
}

export async function updateAclUser(data: Partial<AclUser>): Promise<AclUser> {
  if (isMockMode()) {
    const idx = aclUsersState.findIndex((user) => user.id === data.id);
    if (idx < 0) throw new Error(`ACL user not found: ${data.id}`);
    aclUsersState[idx] = {
      ...aclUsersState[idx],
      ...data,
      clusters: data.clusters ? [...data.clusters] : [...aclUsersState[idx].clusters],
    };
    return copyAclUser(aclUsersState[idx]);
  }
  return aclApi.updateAclUser(data);
}

export async function deleteAclUser(id: string): Promise<void> {
  if (isMockMode()) {
    const idx = aclUsersState.findIndex((user) => user.id === id);
    if (idx >= 0) aclUsersState.splice(idx, 1);
    return;
  }
  return aclApi.deleteAclUser(id);
}

/* ═══════════════════════════════════════════
   ACL 2.0: cluster config & plain access
   ═════════════════════════════════════════ */

function maskCredential(value: string | null | undefined): string | null {
  if (!value) return null;
  if (value.length < 17) return '****';
  return `${value.slice(0, 4)}****${value.slice(-4)}`;
}

export async function examineBrokerClusterAclConfig(clusterId: string): Promise<AclClusterConfig> {
  if (isMockMode()) {
    // Read-back views only carry masked secrets, mirroring the backend contract.
    const accounts = aclPlainAccessState.map((a) => ({
      ...a,
      secretKey: maskCredential(a.secretKey),
    }));
    return {
      clusterId,
      aclEnabled: true,
      aclVersion: 'ACL 2.0',
      globalWhiteRemoteAddresses: ['192.168.0.0/16', '10.0.0.0/8'],
      accounts,
      accountCount: accounts.length,
    };
  }
  return aclApi.examineBrokerClusterAclConfig(clusterId);
}

export async function createAndUpdatePlainAccessConfig(
  data: Partial<PlainAccessConfig>,
): Promise<PlainAccessConfig> {
  if (isMockMode()) {
    const accessKey = (data.accessKey ?? '').trim();
    const providedSecret = (data.secretKey ?? '').trim();
    const existing = aclPlainAccessState.find((a) => a.accessKey === accessKey);
    if (!providedSecret && !existing) {
      throw new Error('secretKey is required for a new plain access account');
    }
    // A blank secret on an existing account keeps the stored secret unchanged.
    const storedSecret = providedSecret || existing?.secretKey || '';
    const saved: PlainAccessConfig = {
      accessKey,
      secretKey: providedSecret || null,
      whiteRemoteAddress: data.whiteRemoteAddress ?? existing?.whiteRemoteAddress ?? '',
      admin: data.admin ?? existing?.admin ?? false,
      defaultTopicPerm: data.defaultTopicPerm ?? existing?.defaultTopicPerm ?? 'DENY',
      defaultGroupPerm: data.defaultGroupPerm ?? existing?.defaultGroupPerm ?? 'DENY',
      topicPerms: [...(data.topicPerms ?? existing?.topicPerms ?? [])],
      groupPerms: [...(data.groupPerms ?? existing?.groupPerms ?? [])],
      createdAt: existing?.createdAt ?? new Date().toISOString(),
    };
    if (existing) {
      Object.assign(existing, saved, { secretKey: storedSecret });
    } else {
      aclPlainAccessState.push({ ...saved, secretKey: storedSecret });
    }
    return { ...saved };
  }
  return aclApi.createAndUpdatePlainAccessConfig(data);
}
