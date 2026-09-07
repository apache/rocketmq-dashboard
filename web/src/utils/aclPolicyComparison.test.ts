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
import type { AclRule, AclUser } from '../api/acl';
import {
  compareAclPolicies,
  filterAclPolicyComparisonRows,
  formatAclDifferences,
} from './aclPolicyComparison';

const user = (username: string, overrides: Partial<AclUser> = {}): AclUser => ({
  id: username,
  username,
  accessKey: `${username}-access-key`,
  secretKey: 'must-never-be-compared',
  admin: false,
  clusters: ['cluster-b', 'cluster-a'],
  permRead: true,
  permWrite: false,
  gmtCreate: '2026-09-01 00:00:00',
  ...overrides,
});

const rule = (principal: string, resource: string, overrides: Partial<AclRule> = {}): AclRule => ({
  id: `${principal}-${resource}`,
  principal,
  resource,
  resourceType: 'Topic',
  resourcePattern: 'LITERAL',
  actions: ['SUB', 'PUB'],
  decision: 'ALLOW',
  scope: 'cluster',
  aclVersion: '2.0',
  gmtCreate: '2026-09-01 00:00:00',
  ...overrides,
});

describe('compareAclPolicies', () => {
  it('matches users and rules after normalizing unordered collections', () => {
    const result = compareAclPolicies(
      [user('order-service')],
      [
        user('order-service', {
          id: 99,
          accessKey: 'different-display-access-key',
          secretKey: 'different-secret',
          clusters: ['cluster-a', 'cluster-b'],
          gmtCreate: '2025-01-01 00:00:00',
        }),
      ],
      [rule('order-service', 'orders')],
      [rule('order-service', 'orders', { id: 100, actions: ['PUB', 'SUB'] })],
    );

    expect(result.rows.map((row) => row.status)).toEqual(['MATCH', 'MATCH']);
    expect(result.summary).toEqual({
      total: 2,
      users: 1,
      rules: 1,
      matches: 2,
      drifted: 0,
      onlySource: 0,
      onlyTarget: 0,
    });
  });

  it('reports every stable user policy field and excludes credentials', () => {
    const result = compareAclPolicies(
      [user('order-service')],
      [
        user('order-service', {
          admin: true,
          clusters: ['cluster-c'],
          permRead: false,
          permWrite: true,
          secretKey: 'rotated-secret',
        }),
      ],
      [],
      [],
    );

    expect(result.rows[0].status).toBe('DRIFT');
    expect(result.rows[0].differences.map((item) => item.field)).toEqual([
      'admin',
      'clusters',
      'permRead',
      'permWrite',
    ]);
    expect(JSON.stringify(result.rows[0].differences)).not.toContain('secret');
  });

  it('reports rule action, decision, and version drift', () => {
    const result = compareAclPolicies(
      [],
      [],
      [rule('order-service', 'orders')],
      [
        rule('order-service', 'orders', {
          actions: ['SUB'],
          decision: 'DENY',
          aclVersion: 1,
        }),
      ],
    );

    expect(result.rows[0].differences).toEqual([
      { field: 'actions', sourceValue: 'PUB;SUB', targetValue: 'SUB' },
      { field: 'decision', sourceValue: 'ALLOW', targetValue: 'DENY' },
      { field: 'aclVersion', sourceValue: '2.0', targetValue: '1' },
    ]);
  });

  it('uses rule scope and resource coordinates as identity', () => {
    const result = compareAclPolicies(
      [],
      [],
      [rule('order-service', 'orders')],
      [rule('order-service', 'payments')],
    );

    expect(result.rows.map((row) => row.status).sort()).toEqual(['ONLY_SOURCE', 'ONLY_TARGET']);
    expect(result.rows[0].identity).toContain('order-service');
  });

  it('keeps duplicate logical rules visible with deterministic keys', () => {
    const result = compareAclPolicies(
      [],
      [],
      [rule('service', 'orders'), rule('service', 'orders', { id: 2, decision: 'DENY' })],
      [rule('service', 'orders')],
    );

    expect(result.rows).toHaveLength(2);
    expect(new Set(result.rows.map((row) => row.key)).size).toBe(2);
    expect(result.summary.onlySource).toBe(1);
  });

  it('reports policies found on only one instance', () => {
    const result = compareAclPolicies(
      [user('source-user')],
      [user('target-user')],
      [rule('source-user', 'orders')],
      [rule('target-user', 'orders')],
    );

    expect(result.summary.onlySource).toBe(2);
    expect(result.summary.onlyTarget).toBe(2);
    expect(result.summary.total).toBe(4);
  });

  it('filters by kind, status, and normalized identity search', () => {
    const rows = compareAclPolicies(
      [user('order-service'), user('source-only')],
      [user('order-service', { admin: true })],
      [rule('order-service', 'orders')],
      [rule('order-service', 'orders')],
    ).rows;

    expect(filterAclPolicyComparisonRows(rows, 'USER', 'DRIFT', ' ORDER ')).toHaveLength(1);
    expect(filterAclPolicyComparisonRows(rows, 'RULE', 'MATCH', 'orders')).toHaveLength(1);
    expect(filterAclPolicyComparisonRows(rows, 'ALL', 'ONLY_SOURCE', 'source')).toHaveLength(1);
  });

  it('formats differences for an audit-friendly CSV cell', () => {
    expect(
      formatAclDifferences([
        { field: 'admin', sourceValue: 'false', targetValue: 'true' },
        { field: 'actions', sourceValue: 'PUB', targetValue: 'PUB;SUB' },
      ]),
    ).toBe('admin: false -> true; actions: PUB -> PUB;SUB');
  });

  it('returns an empty summary when both instances have no policies', () => {
    expect(compareAclPolicies([], [], [], [])).toEqual({
      rows: [],
      summary: {
        total: 0,
        users: 0,
        rules: 0,
        matches: 0,
        drifted: 0,
        onlySource: 0,
        onlyTarget: 0,
      },
    });
  });
});
