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
import type { StudioUser } from '../api/studioUsers';
import {
  analyzeStudioUsers,
  evaluateStudioPassword,
  getStudioUserPasswordRotation,
} from './studioUserSecurity';

const now = new Date('2026-09-07T00:00:00Z');

const user = (overrides: Partial<StudioUser>): StudioUser => ({
  id: 1,
  username: 'operator',
  admin: false,
  enabled: true,
  passwordChangedAt: '2026-08-01T00:00:00Z',
  gmtCreate: '2026-08-01T00:00:00Z',
  gmtModified: '2026-08-01T00:00:00Z',
  ...overrides,
});

const failedCodes = (password: string, context = {}) =>
  evaluateStudioPassword(password, context).failedChecks.map((check) => check.code);

describe('studio user password evaluation', () => {
  it('scores a long mixed password as strong', () => {
    const result = evaluateStudioPassword('Ops#2026-ClusterSafe', { username: 'operator' });

    expect(result.level).toBe('strong');
    expect(result.score).toBeGreaterThanOrEqual(75);
    expect(result.failedChecks.map((check) => check.code)).not.toEqual(
      expect.arrayContaining(['MIN_LENGTH', 'NO_COMMON_WORD', 'NO_USERNAME']),
    );
  });

  it('flags common short passwords', () => {
    const result = evaluateStudioPassword('admin123');

    expect(result.level).toBe('weak');
    expect(result.failedChecks).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ code: 'LONG_LENGTH' }),
        expect.objectContaining({ code: 'MIXED_CASE' }),
        expect.objectContaining({ code: 'SYMBOL' }),
        expect.objectContaining({ code: 'NO_COMMON_WORD' }),
        expect.objectContaining({ code: 'NO_SEQUENCE' }),
      ]),
    );
    expect(result.suggestions.length).toBeGreaterThan(0);
  });

  it('detects username reuse and current password reuse', () => {
    expect(
      failedCodes('operator#2026A', {
        username: 'operator',
        currentPassword: 'operator#2026A',
      }),
    ).toEqual(expect.arrayContaining(['NO_USERNAME', 'NOT_CURRENT_PASSWORD']));
  });

  it('detects repeated characters', () => {
    expect(failedCodes('AAAbbb2026!')).toContain('NO_REPEATED_CHARS');
  });
});

describe('studio user password rotation', () => {
  it('marks recent password changes as fresh', () => {
    expect(
      getStudioUserPasswordRotation(user({ passwordChangedAt: '2026-08-01T00:00:00Z' }), now),
    ).toMatchObject({
      status: 'fresh',
      label: '正常',
      daysSinceChange: 37,
    });
  });

  it('marks old password changes as stale', () => {
    expect(
      getStudioUserPasswordRotation(user({ passwordChangedAt: '2025-12-01T00:00:00Z' }), now),
    ).toMatchObject({
      status: 'stale',
      label: '需轮换',
    });
  });

  it('marks invalid password change timestamps as unknown', () => {
    expect(getStudioUserPasswordRotation(user({ passwordChangedAt: '' }), now)).toMatchObject({
      status: 'unknown',
      label: '未知',
      daysSinceChange: null,
    });
  });
});

describe('studio user security summary', () => {
  it('keeps a small recent account set healthy', () => {
    const summary = analyzeStudioUsers(
      [
        user({ id: 1, username: 'admin-a', admin: true }),
        user({ id: 2, username: 'admin-b', admin: true }),
        user({ id: 3, username: 'reader-a' }),
      ],
      3,
      now,
    );

    expect(summary.status).toBe('healthy');
    expect(summary.risks).toEqual([]);
  });

  it('reports stale admin passwords as critical', () => {
    const summary = analyzeStudioUsers(
      [
        user({
          id: 1,
          username: 'root-admin',
          admin: true,
          passwordChangedAt: '2025-12-01T00:00:00Z',
        }),
        user({ id: 2, username: 'operator-a' }),
      ],
      2,
      now,
    );

    expect(summary.status).toBe('critical');
    expect(summary.stalePasswordCount).toBe(1);
    expect(summary.staleAdminPasswordCount).toBe(1);
    expect(summary.risks).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ code: 'STALE_ADMIN_PASSWORD', severity: 'critical' }),
        expect.objectContaining({ code: 'ONLY_ONE_ACTIVE_ADMIN', severity: 'warning' }),
      ]),
    );
  });

  it('reports unknown password ages', () => {
    const summary = analyzeStudioUsers(
      [
        user({
          id: 1,
          username: 'disabled-admin',
          admin: true,
          enabled: false,
          passwordChangedAt: '',
        }),
        user({ id: 2, username: 'operator-a', passwordChangedAt: 'not-a-date' }),
      ],
      10,
      now,
    );

    expect(summary.status).toBe('warning');
    expect(summary.totalMayExceedInspected).toBe(true);
    expect(summary.unknownPasswordAgeCount).toBe(2);
    expect(summary.risks.map((risk) => risk.code)).toContain('UNKNOWN_PASSWORD_AGE');
  });
});
