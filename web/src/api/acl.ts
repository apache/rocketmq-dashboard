import client from './client';

// Matches mock/acl.ts
export interface AclRule {
  id: string;
  principal: string;
  resource: string;
  resourceType: string;
  resourcePattern: string;
  actions: string[];
  decision: string;
  scope: string;
  aclVersion: number | string;
  createdAt: string;
}

export interface AclRuleQuery {
  clusterId?: string;
  principal?: string;
}

export interface AclUser {
  id: string;
  username: string;
  accessKey: string;
  secretKey: string;
  admin: boolean;
  clusters: string[];
  createdAt: string;
}

export async function listAclRules(params?: AclRuleQuery) {
  const res = await client.get<{ data: AclRule[] }>('/acl/rules', { params });
  return res.data.data;
}

export async function createAclRule(data: Partial<AclRule>) {
  const res = await client.post<{ data: AclRule }>('/acl/rules/create', data);
  return res.data.data;
}

export async function updateAclRule(data: Partial<AclRule>) {
  const res = await client.post<{ data: AclRule }>('/acl/rules/update', data);
  return res.data.data;
}

export async function deleteAclRule(id: string) {
  await client.post('/acl/rules/delete', { id });
}

export async function listAclUsers(params?: { keyword?: string }) {
  const res = await client.get<{ data: AclUser[] }>('/acl/users', { params });
  return res.data.data;
}

export async function createAclUser(data: Partial<AclUser>) {
  const res = await client.post<{ data: AclUser }>('/acl/users/create', data);
  return res.data.data;
}

export async function updateAclUser(data: Partial<AclUser>) {
  const res = await client.post<{ data: AclUser }>('/acl/users/update', data);
  return res.data.data;
}

export async function deleteAclUser(id: string) {
  await client.post('/acl/users/delete', { id });
}
