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
});
