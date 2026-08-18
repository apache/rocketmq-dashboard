import client from './client';

// Matches mock/acl.ts
export interface AclRule {
  id: number;
  principal: string;
  resource: string;
  resourceType: string;
  resourcePattern: string;
  actions: string[];
  decision: string;
  scope: string;
  aclVersion: number | string;
  gmtCreate?: string | null;
}

export interface AclRuleQuery {
  clusterId?: string;
  principal?: string;
  instanceId?: string;
}

// Users list query
interface AclUserQuery {
  keyword?: string;
  instanceId?: string;
}

export interface AclUser {
  id: number;
  username: string;
  accessKey: string;
  secretKey: string;
  admin: boolean;
  clusters: string[];
  permRead?: boolean;
  permWrite?: boolean;
  gmtCreate?: string | null;
}

export async function listAclRules(params?: AclRuleQuery) {
  const res = await client.get<{ data: AclRule[] }>('/acl/rules', { params });
  return res.data.data;
}

export async function createAclRule(data: Partial<AclRule> & { instanceId?: string }) {
  const res = await client.post<{ data: AclRule }>('/acl/rules/create', data);
  return res.data.data;
}

export async function updateAclRule(data: Partial<AclRule> & { instanceId?: string }) {
  const res = await client.post<{ data: AclRule }>('/acl/rules/update', data);
  return res.data.data;
}

export async function deleteAclRule(id: number, instanceId?: string) {
  await client.post('/acl/rules/delete', { id, instanceId });
}

export async function listAclUsers(params?: AclUserQuery) {
  const res = await client.get<{ data: AclUser[] }>('/acl/users', { params });
  return res.data.data;
}

export async function pageAclUsers(params: AclUserQuery & { page: number; pageSize: number }) {
  const res = await client.get<{ data: AclUserPage }>('/acl/users/page', { params });
  return res.data.data;
}

export async function getAclUserCredentials(id: number, instanceId?: string) {
  const res = await client.get<{ data: AclUser }>(
    `/acl/users/${encodeURIComponent(id)}/credentials`,
    { params: { instanceId } },
  );
  return res.data.data;
}

export async function createAclUser(data: Partial<AclUser> & { instanceId?: string }) {
  const res = await client.post<{ data: AclUser }>('/acl/users/create', data);
  return res.data.data;
}

export async function updateAclUser(data: Partial<AclUser> & { instanceId?: string }) {
  const res = await client.post<{ data: AclUser }>('/acl/users/update', data);
  return res.data.data;
}

export async function deleteAclUser(id: number, instanceId?: string) {
  await client.post('/acl/users/delete', { id, instanceId });
}

// ============ ACL 2.0: cluster config & plain access ============

export interface PlainAccessConfig {
  accessKey: string;
  secretKey?: string | null;
  whiteRemoteAddress?: string | null;
  admin: boolean;
  defaultTopicPerm?: string;
  defaultGroupPerm?: string;
  topicPerms?: string[];
  groupPerms?: string[];
  gmtCreate?: string | null;
}

export interface AclClusterConfig {
  clusterId: string;
  aclEnabled: boolean;
  aclVersion: string;
  globalWhiteRemoteAddresses: string[];
  accounts: PlainAccessConfig[];
  accountCount: number;
}

export async function examineBrokerClusterAclConfig(clusterId: string) {
  const res = await client.get<{ data: AclClusterConfig }>('/acl/cluster-config', {
    params: { clusterId },
  });
  return res.data.data;
}

export async function createAndUpdatePlainAccessConfig(data: Partial<PlainAccessConfig>) {
  const res = await client.post<{ data: PlainAccessConfig }>('/acl/plain-access-config', data);
  return res.data.data;
}
