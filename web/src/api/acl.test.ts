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
import {
  createAclRule,
  createAclUser,
  createAndUpdatePlainAccessConfig,
  deleteAclRule,
  deleteAclUser,
  examineBrokerClusterAclConfig,
  getAclUserCredentials,
  listAclRules,
  updateAclRule,
  updateAclUser,
} from './acl';

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
    const params = {
      principal: 'orders',
      resource: 'orders-*',
      scope: 'cluster',
      decision: 'ALLOW',
      aclVersion: '2.0',
      page: 2,
      pageSize: 10,
    };
    mock.onGet('/acl/rules').reply((config) => {
      expect(config.params).toEqual(params);
      return [200, { code: 200, data: { items: [], total: 21, page: 2, size: 10 } }];
    });

    await expect(listAclRules(params)).resolves.toEqual({
      items: [],
      total: 21,
      page: 2,
      size: 10,
    });
  });

  it('returns records created by rule and user APIs', async () => {
    const rule = {
      id: 11,
      principal: 'orders',
      resource: 'orders-*',
      resourceType: 'Topic',
      resourcePattern: 'PREFIX',
      actions: ['PUB'],
      decision: 'ALLOW',
      scope: 'cluster',
      aclVersion: 2,
      gmtCreate: '2026-07-17T00:00:00Z',
    };
    const user = {
      id: 21,
      username: 'orders',
      accessKey: 'ak',
      secretKey: 'sk',
      admin: false,
      clusters: ['cluster-a'],
      gmtCreate: '2026-07-17T00:00:00Z',
    };
    mock.onPost('/acl/rules/create').reply(200, { code: 200, data: rule });
    mock.onPost('/acl/users/create').reply(200, { code: 200, data: user });

    await expect(createAclRule({ principal: rule.principal })).resolves.toEqual(rule);
    await expect(createAclUser({ username: user.username })).resolves.toEqual(user);
  });

  it('uses backend update and delete endpoints for rules and users', async () => {
    const rule = {
      id: 11,
      principal: 'orders',
      resource: 'orders-*',
      resourceType: 'Topic',
      resourcePattern: 'PREFIX',
      actions: ['SUB'],
      decision: 'DENY',
      scope: 'cluster',
      aclVersion: 2,
      gmtCreate: '2026-07-17T00:00:00Z',
    };
    const user = {
      id: 21,
      username: 'orders',
      accessKey: 'ak',
      secretKey: 'sk',
      admin: true,
      clusters: ['cluster-a'],
      gmtCreate: '2026-07-17T00:00:00Z',
    };
    mock.onPost('/acl/rules/update').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ id: rule.id, decision: 'DENY' });
      return [200, { code: 200, data: rule }];
    });
    mock.onPost('/acl/users/update').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: user.id, admin: true });
      return [200, { code: 200, data: user }];
    });
    mock.onPost('/acl/rules/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: rule.id });
      return [200, { code: 200 }];
    });
    mock.onPost('/acl/users/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: user.id });
      return [200, { code: 200 }];
    });

    await expect(updateAclRule({ id: rule.id, decision: 'DENY' })).resolves.toEqual(rule);
    await expect(updateAclUser({ id: user.id, admin: true })).resolves.toEqual(user);
    await expect(deleteAclRule(rule.id)).resolves.toBeUndefined();
    await expect(deleteAclUser(user.id)).resolves.toBeUndefined();
  });

  it('passes Tencent role names as ACL entity identifiers', async () => {
    mock.onPost('/acl/rules/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: 'reader-role', instanceId: 'tencent-rmq' });
      return [200, { code: 200 }];
    });
    mock.onPost('/acl/users/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: 'reader-role', instanceId: 'tencent-rmq' });
      return [200, { code: 200 }];
    });
    mock.onGet('/acl/users/reader-role/credentials').reply((config) => {
      expect(config.params).toEqual({ instanceId: 'tencent-rmq' });
      return [
        200,
        {
          code: 200,
          data: {
            id: 'reader-role',
            username: 'reader-role',
            accessKey: 'ak',
            secretKey: 'sk',
            admin: false,
            clusters: ['rmq-cloud'],
          },
        },
      ];
    });

    await expect(deleteAclRule('reader-role', 'tencent-rmq')).resolves.toBeUndefined();
    await expect(deleteAclUser('reader-role', 'tencent-rmq')).resolves.toBeUndefined();
    await expect(getAclUserCredentials('reader-role', 'tencent-rmq')).resolves.toMatchObject({
      username: 'reader-role',
      secretKey: 'sk',
    });
  });

  it('fetches cluster ACL config by clusterId', async () => {
    mock.onGet('/acl/cluster-config').reply((config) => {
      expect(config.params).toEqual({ clusterId: 'cluster-a' });
      return [
        200,
        {
          code: 200,
          data: {
            clusterId: 'cluster-a',
            aclEnabled: true,
            aclVersion: 'ACL 2.0',
            globalWhiteRemoteAddresses: ['10.0.0.0/8'],
            accounts: [],
            accountCount: 0,
          },
        },
      ];
    });

    const result = await examineBrokerClusterAclConfig('cluster-a');
    expect(result.clusterId).toBe('cluster-a');
    expect(result.aclVersion).toBe('ACL 2.0');
    expect(result.accountCount).toBe(0);
  });

  it('posts plain access config to create or update', async () => {
    const payload = {
      accessKey: 'svc-x',
      admin: false,
      defaultTopicPerm: 'PUB',
      topicPerms: ['t=PUB'],
    };
    mock.onPost('/acl/plain-access-config').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(payload);
      return [200, { code: 200, data: payload }];
    });

    await expect(createAndUpdatePlainAccessConfig(payload)).resolves.toEqual(payload);
  });
});
