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

vi.mock('./dataMode', () => ({ isMockMode: () => true }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));

import { listTopicsPage } from './topicService';

// Demo/mock-mode alignment only: these tests pin the pagination contract of the
// demo-mode implementation of listTopicsPage against the backend
// MetadataService.listTopicsPage contract. No production path is exercised.
describe('mock topic list pagination contract', () => {
  it('rejects pageSize above the backend maximum', async () => {
    // The backend rejects pageSize > 100 with 400 "pageSize must be between 1
    // and 100"; the mock used to clamp it to 100 and quietly answer.
    await expect(listTopicsPage({ page: 1, pageSize: 500 })).rejects.toThrow(
      /pageSize must be between 1 and 100/,
    );
  });

  it('rejects page below 1 instead of clamping it', async () => {
    // The backend rejects page < 1 with 400 "page must be greater than zero";
    // the mock used Math.max(.., 1) and quietly returned the first page.
    await expect(listTopicsPage({ page: 0, pageSize: 20 })).rejects.toThrow(
      /page must be greater than zero/,
    );
  });

  it('rejects pageSize below 1 instead of clamping it', async () => {
    await expect(listTopicsPage({ page: 1, pageSize: 0 })).rejects.toThrow(
      /pageSize must be between 1 and 100/,
    );
  });

  it('returns the requested page within bounds (control)', async () => {
    const result = await listTopicsPage({ page: 1, pageSize: 20 });
    expect(result.items.length).toBeGreaterThan(0);
    expect(result.page).toBe(1);
    expect(result.size).toBe(20);
    expect(result.total).toBe(result.items.length);
  });

  it('returns an empty page past the end instead of clamping (control)', async () => {
    const result = await listTopicsPage({ page: 1000, pageSize: 20 });
    expect(result.items).toEqual([]);
    expect(result.page).toBe(1000);
  });
});
