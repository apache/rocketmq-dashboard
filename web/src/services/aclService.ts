import { USE_MOCK } from '../config';
import * as aclApi from '../api/acl';
import type { AclRule, AclRuleQuery, AclUser } from '../api/acl';
import { aclRules as mockRules, aclUsers as mockUsers } from '../mock/acl';

const aclRulesState = mockRules as unknown as AclRule[];
const aclUsersState = mockUsers as unknown as AclUser[];

export async function listAclRules(params?: AclRuleQuery): Promise<AclRule[]> {
  if (USE_MOCK) {
    let result = [...aclRulesState];
    if (params?.principal) {
      const principal = params.principal.toLowerCase();
      result = result.filter((rule) => rule.principal.toLowerCase().includes(principal));
    }
    return result;
  }
  return aclApi.listAclRules(params);
}

export async function listAclUsers(params?: { keyword?: string }): Promise<AclUser[]> {
  if (USE_MOCK) {
    let result = [...aclUsersState];
    if (params?.keyword) {
      const kw = params.keyword.toLowerCase();
      result = result.filter((u) => u.username.toLowerCase().includes(kw));
    }
    return result;
  }
  return aclApi.listAclUsers(params);
}

export async function createAclRule(data: Partial<AclRule>): Promise<AclRule> {
  if (USE_MOCK) {
    const rule: AclRule = {
      id: `acl-${Date.now()}`,
      principal: '',
      resource: '',
      resourceType: '',
      resourcePattern: '',
      actions: [],
      decision: '',
      scope: '',
      aclVersion: 2,
      createdAt: new Date().toISOString(),
      ...data,
    };
    aclRulesState.push(rule);
    return rule;
  }
  return aclApi.createAclRule(data);
}

export async function updateAclRule(data: Partial<AclRule>): Promise<AclRule> {
  if (USE_MOCK) {
    const idx = aclRulesState.findIndex((rule) => rule.id === data.id);
    if (idx < 0) throw new Error(`ACL rule not found: ${data.id}`);
    aclRulesState[idx] = {
      ...aclRulesState[idx],
      ...data,
    };
    return aclRulesState[idx];
  }
  return aclApi.updateAclRule(data);
}

export async function deleteAclRule(id: string): Promise<void> {
  if (USE_MOCK) {
    const idx = aclRulesState.findIndex((rule) => rule.id === id);
    if (idx >= 0) aclRulesState.splice(idx, 1);
    return;
  }
  return aclApi.deleteAclRule(id);
}

export async function createAclUser(data: Partial<AclUser>): Promise<AclUser> {
  if (USE_MOCK) {
    const user: AclUser = {
      id: `user-${Date.now()}`,
      username: '',
      accessKey: '',
      secretKey: '',
      admin: false,
      clusters: [],
      createdAt: new Date().toISOString(),
      ...data,
    };
    aclUsersState.push(user);
    return user;
  }
  return aclApi.createAclUser(data);
}

export async function updateAclUser(data: Partial<AclUser>): Promise<AclUser> {
  if (USE_MOCK) {
    const idx = aclUsersState.findIndex((user) => user.id === data.id);
    if (idx < 0) throw new Error(`ACL user not found: ${data.id}`);
    aclUsersState[idx] = {
      ...aclUsersState[idx],
      ...data,
    };
    return aclUsersState[idx];
  }
  return aclApi.updateAclUser(data);
}

export async function deleteAclUser(id: string): Promise<void> {
  if (USE_MOCK) {
    const idx = aclUsersState.findIndex((user) => user.id === id);
    if (idx >= 0) aclUsersState.splice(idx, 1);
    return;
  }
  return aclApi.deleteAclUser(id);
}
