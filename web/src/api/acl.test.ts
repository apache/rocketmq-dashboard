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

import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import { createAclRule, createAclUser, listAclRules } from './acl';

const mock = new MockAdapter(client);

describe('ACL API contract', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('uses the controller-supported ACL rule filters', async () => {
    const params = { clusterId: 'cluster-a', principal: 'orders' };
    mock.onGet('/acl/rules').reply((config) => {
      expect(config.params).toEqual(params);
      return [200, { code: 200, data: [] }];
    });

    await expect(listAclRules(params)).resolves.toEqual([]);
  });

  it('returns records created by rule and user APIs', async () => {
    const rule = { id: 'rule-1', principal: 'orders', resource: 'orders-*', resourceType: 'Topic', resourcePattern: 'PREFIX', actions: ['PUB'], decision: 'ALLOW', scope: 'cluster', aclVersion: 2, createdAt: '2026-07-17T00:00:00Z' };
    const user = { id: 'user-1', username: 'orders', accessKey: 'ak', secretKey: 'sk', admin: false, clusters: ['cluster-a'], createdAt: '2026-07-17T00:00:00Z' };
    mock.onPost('/acl/rules/create').reply(200, { code: 200, data: rule });
    mock.onPost('/acl/users/create').reply(200, { code: 200, data: user });

    await expect(createAclRule({ principal: rule.principal })).resolves.toEqual(rule);
    await expect(createAclUser({ username: user.username })).resolves.toEqual(user);
  });
});
