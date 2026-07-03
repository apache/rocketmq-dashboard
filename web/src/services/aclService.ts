import { USE_MOCK } from '../config';
import * as aclApi from '../api/acl';
import type { AclRule, AclUser } from '../api/acl';
import { aclRules as mockRules, aclUsers as mockUsers } from '../mock/acl';

export async function listAclRules(params?: {
  keyword?: string;
  version?: string;
  decision?: string;
}): Promise<AclRule[]> {
  if (USE_MOCK) {
    let result = [...mockRules];
    if (params?.keyword) {
      const kw = params.keyword.toLowerCase();
      result = result.filter(
        (r) => r.principal.toLowerCase().includes(kw) || r.resource.toLowerCase().includes(kw),
      );
    }
    if (params?.version && params.version !== 'all')
      result = result.filter((r) => r.aclVersion === params.version);
    if (params?.decision && params.decision !== 'all')
      result = result.filter((r) => r.decision === params.decision);
    return result as unknown as AclRule[];
  }
  return aclApi.listAclRules(params);
}

export async function listAclUsers(params?: { keyword?: string }): Promise<AclUser[]> {
  if (USE_MOCK) {
    let result = [...mockUsers];
    if (params?.keyword) {
      const kw = params.keyword.toLowerCase();
      result = result.filter((u) => u.username.toLowerCase().includes(kw));
    }
    return result as unknown as AclUser[];
  }
  return aclApi.listAclUsers(params);
}

export async function createAclRule(data: Partial<AclRule>): Promise<void> {
  if (USE_MOCK) {
    mockRules.push(data as never);
    return;
  }
  return aclApi.createAclRule(data);
}

export async function deleteAclRule(id: string): Promise<void> {
  if (USE_MOCK) {
    const idx = mockRules.findIndex((r) => r.id === id);
    if (idx >= 0) mockRules.splice(idx, 1);
    return;
  }
  return aclApi.deleteAclRule(id);
}

export async function createAclUser(data: Partial<AclUser>): Promise<void> {
  if (USE_MOCK) {
    mockUsers.push(data as never);
    return;
  }
  return aclApi.createAclUser(data);
}

export async function deleteAclUser(id: string): Promise<void> {
  if (USE_MOCK) {
    const idx = mockUsers.findIndex((u) => u.id === id);
    if (idx >= 0) mockUsers.splice(idx, 1);
    return;
  }
  return aclApi.deleteAclUser(id);
}
