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

import { describe, expect, it, vi } from 'vitest';
import {
  createAclRule,
  createAclUser,
  createAndUpdatePlainAccessConfig,
  deleteAclRule,
  deleteAclUser,
  examineBrokerClusterAclConfig,
  getAclUserCredentials,
  listAclRules,
  listAclUsers,
  pageAclUsers,
  updateAclRule,
  updateAclUser,
} from './aclService';

vi.mock('./dataMode', () => ({ isMockMode: () => true }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));

describe('ACL service mock data', () => {
  it('returns copied ACL rule rows', async () => {
    const first = await listAclRules({ principal: 'user-admin' });
    expect(first.items[0].principal).toBe('user-admin');

    first.items[0].principal = 'mutated-principal';
    first.items[0].actions.push('MUTATED');

    const second = await listAclRules({ principal: 'user-admin' });
    expect(second.items[0].principal).toBe('user-admin');
    expect(second.items[0].actions).toEqual(['ALL']);
    expect(second.items[0]).not.toBe(first.items[0]);
  });

  it('copies ACL rule arrays on create and update', async () => {
    const actions = ['PUB'];
    const created = await createAclRule({
      principal: 'user-created-copy-test',
      resource: 'created-topic',
      actions,
    });
    actions.push('SUB');
    created.actions.push('MUTATED');

    const afterCreate = await listAclRules({ principal: 'user-created-copy-test' });
    expect(afterCreate.items[0].actions).toEqual(['PUB']);

    const updateActions = ['SUB'];
    const updated = await updateAclRule({ id: created.id, actions: updateActions });
    updateActions.push('PUB');
    updated.actions.push('MUTATED');

    const afterUpdate = await listAclRules({ principal: 'user-created-copy-test' });
    expect(afterUpdate.items[0].actions).toEqual(['SUB']);
  });

  it('returns copied ACL user rows', async () => {
    const first = await listAclUsers({ keyword: 'user-admin' });
    expect(first[0].username).toBe('user-admin');

    first[0].username = 'mutated-user';
    first[0].clusters.push('mutated-cluster');

    const second = await listAclUsers({ keyword: 'user-admin' });
    expect(second[0].username).toBe('user-admin');
    expect(second[0].clusters).not.toContain('mutated-cluster');
    expect(second[0]).not.toBe(first[0]);
  });

  it('validates ACL user pagination like the backend', async () => {
    await expect(pageAclUsers({ page: 0, pageSize: 20 })).rejects.toThrow('page must be >= 1');
    await expect(pageAclUsers({ page: 1, pageSize: 101 })).rejects.toThrow(
      'pageSize must be between 1 and 100',
    );
  });

  it('copies ACL user arrays on create and update', async () => {
    const clusters = ['rmq-created'];
    const created = await createAclUser({
      username: 'user-created-copy-test',
      clusters,
    });
    clusters.push('rmq-mutated');
    created.clusters.push('rmq-mutated-return');

    const afterCreate = await listAclUsers({ keyword: 'user-created-copy-test' });
    expect(afterCreate[0].clusters).toEqual(['rmq-created']);

    const updateClusters = ['rmq-updated'];
    const updated = await updateAclUser({ id: created.id, clusters: updateClusters });
    updateClusters.push('rmq-mutated');
    updated.clusters.push('rmq-mutated-return');

    const afterUpdate = await listAclUsers({ keyword: 'user-created-copy-test' });
    expect(afterUpdate[0].clusters).toEqual(['rmq-updated']);
  });

  it('builds cluster ACL config from mock accounts', async () => {
    const config = await examineBrokerClusterAclConfig('DefaultCluster');
    expect(config.aclEnabled).toBe(true);
    expect(config.aclVersion).toBe('ACL 2.0');
    expect(config.globalWhiteRemoteAddresses).toContain('192.168.0.0/16');
    expect(config.accountCount).toBe(config.accounts.length);
    expect(config.accounts[0].accessKey).toBe('user-admin');
  });

  it('creates and updates a plain access account in mock state', async () => {
    const created = await createAndUpdatePlainAccessConfig({
      accessKey: 'svc-mock',
      secretKey: 'svc-mock-secret-value',
      admin: false,
      defaultTopicPerm: 'PUB',
      topicPerms: ['a=PUB'],
    });
    expect(created.accessKey).toBe('svc-mock');
    // The secret is echoed only when it was just provided.
    expect(created.secretKey).toBe('svc-mock-secret-value');

    const updated = await createAndUpdatePlainAccessConfig({
      accessKey: 'svc-mock',
      admin: true,
      defaultTopicPerm: 'ALL',
    });
    expect(updated.admin).toBe(true);
    // A blank secret keeps the stored one and is not echoed back.
    expect(updated.secretKey).toBeNull();

    const config = await examineBrokerClusterAclConfig('c');
    const account = config.accounts.find((a) => a.accessKey === 'svc-mock');
    expect(account && account.admin).toBe(true);
    // Read-back views mask the secret instead of exposing the plaintext.
    expect(account?.secretKey).not.toBe('svc-mock-secret-value');
    expect(account?.secretKey).toContain('****');
  });

  it('rejects a new plain access account without a secret', async () => {
    await expect(createAndUpdatePlainAccessConfig({ accessKey: 'svc-no-secret' })).rejects.toThrow(
      'secretKey is required',
    );
  });

  it('removes a rule from mock state and tolerates an unknown id', async () => {
    const created = await createAclRule({
      principal: 'user-delete-candidate',
      resource: 'delete-me',
      actions: ['PUB'],
    });
    expect(created.id).toBeGreaterThan(0);

    await deleteAclRule(created.id);

    const after = await listAclRules({ principal: 'user-delete-candidate' });
    expect(after.total).toBe(0);
    await expect(deleteAclRule(99999999)).resolves.toBeUndefined();
  });

  it('removes a user from mock state and tolerates an unknown id', async () => {
    const created = await createAclUser({
      username: 'user-delete-candidate',
      clusters: ['rmq-delete-test'],
    });

    await deleteAclUser(created.id);

    const after = await listAclUsers({ keyword: 'user-delete-candidate' });
    expect(after).toHaveLength(0);
    await expect(deleteAclUser(99999999)).resolves.toBeUndefined();
  });

  it('returns a defensive copy of stored credentials for a known user', async () => {
    const credentials = await getAclUserCredentials(1);
    expect(credentials.username).toBe('user-admin');
    expect(credentials.secretKey).toBe('HqWz****xK8P');

    credentials.clusters.push('mutated-cluster');
    const again = await getAclUserCredentials(1);
    expect(again.clusters).not.toContain('mutated-cluster');
    expect(again).not.toBe(credentials);
  });

  it('throws for credentials of an unknown user', async () => {
    await expect(getAclUserCredentials(99999999)).rejects.toThrow('ACL user not found');
  });

  it('throws when updating a rule or user that does not exist', async () => {
    await expect(
      updateAclRule({ id: 99999999, principal: 'user-missing' }),
    ).rejects.toThrow('ACL rule not found');
    await expect(
      updateAclUser({ id: 99999999, username: 'user-missing' }),
    ).rejects.toThrow('ACL user not found');
  });

  it('filters rules by scope, decision, ACL version, and resource', async () => {
    const namespaced = await listAclRules({ scope: 'namespace' });
    expect(namespaced.items.length).toBeGreaterThan(0);
    expect(namespaced.items.every((rule) => rule.scope === 'namespace')).toBe(true);

    const denied = await listAclRules({ decision: 'DENY' });
    expect(denied.items.length).toBeGreaterThan(0);
    expect(denied.items.every((rule) => rule.decision === 'DENY')).toBe(true);

    const v1 = await listAclRules({ aclVersion: '1.0' });
    expect(v1.items.length).toBeGreaterThan(0);
    expect(v1.items.every((rule) => String(rule.aclVersion) === '1.0')).toBe(true);

    const resource = await listAclRules({ resource: 'payment' });
    expect(resource.items.length).toBeGreaterThan(0);
    expect(resource.items.every((rule) => rule.resource.toLowerCase().includes('payment'))).toBe(
      true,
    );
  });

  it('filters rules case-insensitively by principal', async () => {
    const lower = await listAclRules({ principal: 'user-admin' });
    const upper = await listAclRules({ principal: 'USER-ADMIN' });
    expect(lower.items.length).toBeGreaterThan(0);
    expect(upper.items).toEqual(lower.items);
  });

  it('pages rule results with a page size and total', async () => {
    const first = await listAclRules({ page: 1, pageSize: 3 });
    expect(first.items).toHaveLength(3);
    expect(first.total).toBeGreaterThan(3);
    expect(first.page).toBe(1);

    const second = await listAclRules({ page: 2, pageSize: 3 });
    expect(second.items).toHaveLength(3);
    expect(second.items).not.toEqual(first.items);
    expect(second.total).toBe(first.total);
  });

  it('filters users by a case-insensitive keyword', async () => {
    const upper = await listAclUsers({ keyword: 'USER-ADMIN' });
    expect(upper[0].username).toBe('user-admin');

    const partial = await listAclUsers({ keyword: 'order' });
    expect(partial.every((user) => user.username.toLowerCase().includes('order'))).toBe(true);
    expect(partial.some((user) => user.username === 'user-order-service')).toBe(true);
  });
});
