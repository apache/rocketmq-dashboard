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

import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./dataMode', () => ({ isMockMode: () => true }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));

import { listAclRules } from './aclService';
import { aclRules } from '../mock/acl';

describe('mock acl rules pagination contract', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('rejects pageSize above the backend maximum', async () => {
    // The backend AclService.requireValidPageSize rejects pageSize > 100
    // with 400; the sibling mock pageAclUsers enforces the same bound,
    // but listAclRules used to silently return every row.
    await expect(listAclRules({ page: 1, pageSize: 500 })).rejects.toThrow(/between 1 and 100/);
  });

  it('rejects page below 1 instead of clamping it', async () => {
    // The backend rejects page < 1 with 400; the mock used Math.max(.., 1)
    // and quietly returned the first page.
    await expect(listAclRules({ page: 0, pageSize: 20 })).rejects.toThrow(/page must be >= 1/);
  });

  it('returns the requested page within bounds (control)', async () => {
    const result = await listAclRules({ page: 1, pageSize: 20 });
    expect(result.items.length).toBeGreaterThan(0);
    expect(result.page).toBe(1);
    expect(result.size).toBe(20);
  });

  it('returns an empty page past the end instead of clamping (control)', async () => {
    const result = await listAclRules({ page: 1000, pageSize: 20 });
    expect(result.items).toEqual([]);
    expect(result.page).toBe(1000);
  });
});
