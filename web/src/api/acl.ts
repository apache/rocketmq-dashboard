/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */

import client from './client';

// ─── Types ──────────────────────────────────────────────────────
export interface AclRule {
  id: string;
  principal: string;
  resource: string;
  resourceType: string;
  resourcePattern: string;
  actions: string[];
  decision: string;
  scope: string;
  aclVersion: string;
  createdAt: string;
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

// ─── ACL API ────────────────────────────────────────────────────
// Backend: AclController at /acl
// GET    /acl/users.query?username=xxx   → list users
// GET    /acl/acls.query?username=xxx    → list ACL policies
// POST   /acl/createAcl.do              → create ACL policy
// POST   /acl/updateAcl.do              → update ACL policy
// DELETE /acl/deleteAcl.do?subject=xxx&resource=xxx → delete ACL
// POST   /acl/createUser.do             → create user
// POST   /acl/updateUser.do             → update user
// DELETE /acl/deleteUser.do?username=xxx → delete user

export async function listAclUsers(params?: { keyword?: string }) {
  const res = await client.get('/acl/users.query', {
    params: params?.keyword ? { username: params.keyword } : undefined,
  });
  return res.data;
}

export async function listAclRules(params?: {
  keyword?: string;
  version?: string;
  decision?: string;
}) {
  const res = await client.get('/acl/acls.query', {
    params: params?.keyword ? { username: params.keyword } : undefined,
  });
  return res.data;
}

export async function createAclRule(data: Partial<AclRule>) {
  await client.post('/acl/createAcl.do', data);
}

export async function updateAclRule(data: Partial<AclRule>) {
  await client.post('/acl/updateAcl.do', data);
}

export async function deleteAclRule(subject: string, resource?: string) {
  await client.delete('/acl/deleteAcl.do', {
    params: { subject, resource },
  });
}

export async function createAclUser(data: Partial<AclUser>) {
  await client.post('/acl/createUser.do', data);
}

export async function updateAclUser(data: Partial<AclUser>) {
  await client.post('/acl/updateUser.do', data);
}

export async function deleteAclUser(username: string) {
  await client.delete('/acl/deleteUser.do', {
    params: { username },
  });
}
