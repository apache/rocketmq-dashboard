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

import { describe, expect, it } from 'vitest';
import type { AclClusterConfig, PlainAccessConfig } from '../api/acl';
import { analyzeAclRisk } from './aclRiskDiagnostics';

const account = (overrides: Partial<PlainAccessConfig>): PlainAccessConfig => ({
  accessKey: 'svc-order',
  secretKey: '******',
  whiteRemoteAddress: '10.0.2.15',
  admin: false,
  defaultTopicPerm: 'DENY',
  defaultGroupPerm: 'DENY',
  topicPerms: ['order-events=PUB'],
  groupPerms: ['cg-order=SUB'],
  ...overrides,
});

const config = (overrides: Partial<AclClusterConfig>): AclClusterConfig => ({
  clusterId: 'DefaultCluster',
  aclEnabled: true,
  aclVersion: 'ACL 2.0',
  globalWhiteRemoteAddresses: [],
  accounts: [account({})],
  accountCount: 1,
  ...overrides,
});

describe('ACL risk diagnostics', () => {
  it('marks a least-privilege ACL config as healthy', () => {
    const diagnostics = analyzeAclRisk(config({}));

    expect(diagnostics.status).toBe('healthy');
    expect(diagnostics.score).toBe(100);
    expect(diagnostics.issues).toEqual([]);
    expect(diagnostics.summary).toEqual({
      accountCount: 1,
      adminAccountCount: 0,
      defaultAllowAccountCount: 0,
      wildcardPermissionAccountCount: 0,
      broadWhitelistCount: 0,
      duplicateAccessKeyCount: 0,
    });
    expect(diagnostics.recommendations).toEqual([
      '保持默认权限为 DENY，新增账号时继续按业务资源最小授权。',
    ]);
  });

  it('flags disabled ACL and missing accounts as critical risks', () => {
    const diagnostics = analyzeAclRisk(
      config({
        aclEnabled: false,
        aclVersion: 'ACL 1.0',
        accounts: [],
        accountCount: 0,
        globalWhiteRemoteAddresses: ['*'],
      }),
    );

    expect(diagnostics.status).toBe('critical');
    expect(diagnostics.score).toBeLessThan(60);
    expect(diagnostics.summary.accountCount).toBe(0);
    expect(diagnostics.summary.broadWhitelistCount).toBe(1);
    expect(diagnostics.issues.map((item) => item.code)).toEqual(
      expect.arrayContaining([
        'ACL_DISABLED',
        'LEGACY_ACL_VERSION',
        'NO_PLAIN_ACCESS_ACCOUNTS',
        'BROAD_GLOBAL_WHITELIST',
      ]),
    );
  });

  it('finds duplicated access keys and over-broad admin access', () => {
    const diagnostics = analyzeAclRisk(
      config({
        accounts: [
          account({
            accessKey: 'admin-ak',
            admin: true,
            whiteRemoteAddress: '0.0.0.0/0',
            defaultTopicPerm: 'ALL',
            defaultGroupPerm: 'ALL',
          }),
          account({
            accessKey: 'admin-ak',
            admin: true,
            whiteRemoteAddress: '10.0.0.0/8',
            defaultTopicPerm: 'DENY',
            defaultGroupPerm: 'DENY',
          }),
        ],
        accountCount: 2,
      }),
    );

    expect(diagnostics.status).toBe('critical');
    expect(diagnostics.summary).toMatchObject({
      accountCount: 2,
      adminAccountCount: 2,
      defaultAllowAccountCount: 1,
      broadWhitelistCount: 2,
      duplicateAccessKeyCount: 1,
    });
    expect(diagnostics.issues.map((item) => item.code)).toEqual(
      expect.arrayContaining([
        'MULTIPLE_ADMIN_ACCOUNTS',
        'DUPLICATE_ACCESS_KEY',
        'BROAD_ACCOUNT_WHITELIST',
        'ADMIN_WITH_BROAD_ACCESS',
        'DEFAULT_TOPIC_ALLOW',
        'DEFAULT_GROUP_ALLOW',
      ]),
    );
  });

  it('reports wildcard permissions and malformed permission entries', () => {
    const diagnostics = analyzeAclRisk(
      config({
        accounts: [
          account({
            topicPerms: ['*=ALL', 'billing-events=PUB'],
            groupPerms: ['*=SUB', 'invalid-entry'],
          }),
        ],
      }),
    );

    expect(diagnostics.status).toBe('critical');
    expect(diagnostics.summary.wildcardPermissionAccountCount).toBe(1);
    expect(diagnostics.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'WILDCARD_TOPIC_PERMISSION',
          severity: 'critical',
          evidence: ['*=ALL'],
        }),
        expect.objectContaining({
          code: 'WILDCARD_GROUP_PERMISSION',
          severity: 'warning',
          evidence: ['*=SUB'],
        }),
        expect.objectContaining({
          code: 'INVALID_PERMISSION_ENTRY',
          severity: 'warning',
          evidence: ['invalid-entry'],
        }),
      ]),
    );
    expect(diagnostics.recommendations).toEqual(
      expect.arrayContaining([
        '将通配 Topic 授权收敛为具体 Topic 或业务前缀，并避免 *=ALL。',
        '按 resource=PUB、resource=SUB、resource=ALL 或 resource=DENY 的格式修正条目。',
      ]),
    );
  });
});
