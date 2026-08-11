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
  examineBrokerClusterAclConfig,
  listAclRules,
  listAclUsers,
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
    expect(first[0].principal).toBe('user-admin');

    first[0].principal = 'mutated-principal';
    first[0].actions.push('MUTATED');

    const second = await listAclRules({ principal: 'user-admin' });
    expect(second[0].principal).toBe('user-admin');
    expect(second[0].actions).toEqual(['ALL']);
    expect(second[0]).not.toBe(first[0]);
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
    expect(afterCreate[0].actions).toEqual(['PUB']);

    const updateActions = ['SUB'];
    const updated = await updateAclRule({ id: created.id, actions: updateActions });
    updateActions.push('PUB');
    updated.actions.push('MUTATED');

    const afterUpdate = await listAclRules({ principal: 'user-created-copy-test' });
    expect(afterUpdate[0].actions).toEqual(['SUB']);
  });

  it('assigns unique ACL rule IDs within the same millisecond', async () => {
    const now = vi.spyOn(Date, 'now').mockReturnValue(1_800_000_000_000);
    const first = await createAclRule({ principal: 'same-clock-rule-a' });
    const second = await createAclRule({ principal: 'same-clock-rule-b' });

    try {
      expect(first.id).not.toBe(second.id);
      expect(first.id).toContain('1800000000000');
      expect(second.id).toContain('1800000000000');
    } finally {
      now.mockRestore();
      await deleteAclRule(first.id);
      await deleteAclRule(second.id);
    }
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
});
