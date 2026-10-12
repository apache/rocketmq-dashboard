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

  it('reads the comma-joined action list the server writes for a multi-action rule', () => {
    const diagnostics = analyzeAclRisk(
      config({
        accounts: [
          account({
            // One rule with PUB and SUB checked is stored with its actions joined by a comma
            // (MybatisPlusAclRepository#joinNormalizedCsv), and examineBrokerClusterAclConfig
            // hands that value back verbatim as "resource=PUB,SUB".
            topicPerms: ['order-events=PUB,SUB'],
            groupPerms: ['cg-order=PUB,SUB'],
          }),
        ],
      }),
    );

    expect(diagnostics.issues.map((item) => item.code)).not.toContain('INVALID_PERMISSION_ENTRY');
    expect(diagnostics.summary.wildcardPermissionAccountCount).toBe(0);
  });

  it('counts a comma-joined default permission as an allow', () => {
    const diagnostics = analyzeAclRisk(
      config({
        accounts: [account({ defaultTopicPerm: 'PUB,SUB', defaultGroupPerm: 'DENY' })],
      }),
    );

    expect(diagnostics.summary.defaultAllowAccountCount).toBe(1);
    expect(diagnostics.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'DEFAULT_TOPIC_ALLOW',
          severity: 'critical',
          evidence: ['defaultTopicPerm=PUB,SUB'],
        }),
      ]),
    );
  });

  it('ranks a publish+subscribe wildcard as full access', () => {
    const diagnostics = analyzeAclRisk(
      config({
        accounts: [account({ topicPerms: ['*=PUB,SUB'] })],
      }),
    );

    expect(diagnostics.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'WILDCARD_TOPIC_PERMISSION',
          severity: 'critical',
          evidence: ['*=PUB,SUB'],
        }),
      ]),
    );
  });
});

describe('native address scope', () => {
  it.each([
    '10.0.2.*',
    '10.0.0-255.*',
    '2001:db8:0:0:0:0:0:*',
    '192.168.*.*',
    '1050::0005:0600:300c:1-20:*',
  ])('does not grade a bounded native range %s as unrestricted Test', (whiteRemoteAddress) => {
    const result = analyzeAclRisk(config({ accounts: [account({ whiteRemoteAddress })] }));
    expect(result.summary.broadWhitelistCount).toBe(0);
    expect(result.issues.map((item) => item.code)).not.toContain('BROAD_ACCOUNT_WHITELIST');
  });
  it('grades a native /8-sized range as broad rather than all addresses Test', () => {
    const result = analyzeAclRisk(
      config({ accounts: [account({ whiteRemoteAddress: '10.*.*.*' })] }),
    );
    expect(result.issues.find((item) => item.code === 'BROAD_ACCOUNT_WHITELIST')?.severity).toBe(
      'warning',
    );
  });
  it.each(['*', '*.*.*.*', '*:*:*:*:*:*:*:*'])(
    'retains unrestricted-source warnings for %s Test',
    (whiteRemoteAddress) => {
      const result = analyzeAclRisk(config({ accounts: [account({ whiteRemoteAddress })] }));
      expect(result.issues.find((item) => item.code === 'BROAD_ACCOUNT_WHITELIST')?.severity).toBe(
        'critical',
      );
    },
  );
  it('keeps actual administrator and default-permission warnings Test', () => {
    const result = analyzeAclRisk(
      config({
        accounts: [
          account({ whiteRemoteAddress: '10.*.*.*', admin: true, defaultTopicPerm: 'ALL' }),
        ],
      }),
    );
    expect(result.issues.map((item) => item.code)).toEqual(
      expect.arrayContaining(['ADMIN_WITH_BROAD_ACCESS', 'DEFAULT_TOPIC_ALLOW']),
    );
  });
  it.each(['192.168.1.{1,2,3}', '192.168.1.10,192.168.1.11', '1050::0005:0600:300c:{1,2,3}'])(
    'retains bounded literal/set/list controls %s Test',
    (whiteRemoteAddress) => {
      const result = analyzeAclRisk(config({ accounts: [account({ whiteRemoteAddress })] }));
      expect(result.summary.broadWhitelistCount).toBe(0);
    },
  );
  it('keeps an unrecognised wildcard conservative Test', () => {
    const result = analyzeAclRisk(
      config({ accounts: [account({ whiteRemoteAddress: 'unparsed:*' })] }),
    );
    expect(result.issues.find((item) => item.code === 'BROAD_ACCOUNT_WHITELIST')?.severity).toBe(
      'critical',
    );
  });
});

describe('native address classification controls', () => {
  it.each(['10.0-255.*.*', '10.0.0.0/8', '2001:db8::/16'])(
    'retains the broad threshold for %s Test',
    (whiteRemoteAddress) => {
      const result = analyzeAclRisk(config({ accounts: [account({ whiteRemoteAddress })] }));
      expect(result.issues.find((item) => item.code === 'BROAD_ACCOUNT_WHITELIST')?.severity).toBe(
        'warning',
      );
    },
  );
  it.each([
    '10.0-254.*.*',
    '2001:db8::/17',
    '2001:DB8::0-FFFF:*',
    '192.168.1-100.*',
    '192.168.1.10-20',
  ])('keeps narrower ranges scoped for %s Test', (whiteRemoteAddress) => {
    const result = analyzeAclRisk(config({ accounts: [account({ whiteRemoteAddress })] }));
    expect(result.summary.broadWhitelistCount).toBe(0);
    expect(result.status).toBe('healthy');
  });
  it.each([
    '10.*.1.*',
    '192.168.300.*',
    '192.168.100-1.*',
    '*.*.*.1',
    '::*',
    '::1-20:*',
    '1050::0005:*:300c:1',
    '2001:db8:::*',
    '2001:db8::gggg:*',
    '2001:db8:*',
    '10.0.1-2.3-4',
  ])('does not downgrade an unrecognized wildcard %s Test', (whiteRemoteAddress) => {
    const result = analyzeAclRisk(config({ accounts: [account({ whiteRemoteAddress })] }));
    const risk = result.issues.find((item) => item.code === 'BROAD_ACCOUNT_WHITELIST');
    // Preserve the existing fallback: unknown wildcards are critical, other unknown forms scoped.
    expect(risk?.severity).toBe(whiteRemoteAddress.includes('*') ? 'critical' : undefined);
  });
  it('uses the same classifier for the global whitelist contract Test', () => {
    const result = analyzeAclRisk(config({ globalWhiteRemoteAddresses: ['10.0.2.*', '10.*.*.*'] }));
    expect(result.summary.broadWhitelistCount).toBe(1);
    expect(result.issues.find((item) => item.code === 'BROAD_GLOBAL_WHITELIST')?.severity).toBe(
      'warning',
    );
  });
  it('retains the strongest finding across account whitelist entries Test', () => {
    const result = analyzeAclRisk(
      config({ accounts: [account({ whiteRemoteAddress: '10.0.2.*,10.*.*.*' })] }),
    );
    expect(result.summary.broadWhitelistCount).toBe(1);
    expect(result.issues.find((item) => item.code === 'BROAD_ACCOUNT_WHITELIST')?.severity).toBe(
      'warning',
    );
  });
  it('keeps permission findings for a bounded administrator address Test', () => {
    const result = analyzeAclRisk(
      config({
        accounts: [
          account({
            whiteRemoteAddress: '10.0.2.*',
            admin: true,
            defaultTopicPerm: 'ALL',
            groupPerms: ['*=SUB'],
          }),
        ],
      }),
    );
    expect(result.issues.map((item) => item.code)).toEqual(
      expect.arrayContaining(['DEFAULT_TOPIC_ALLOW', 'WILDCARD_GROUP_PERMISSION']),
    );
    expect(result.issues.map((item) => item.code)).not.toContain('ADMIN_WITH_BROAD_ACCESS');
  });
});
