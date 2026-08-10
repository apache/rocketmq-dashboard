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

import {
  createCloudCredential,
  deleteCloudCredential,
  listCloudCredentials,
  updateCloudCredential,
} from './cloudCredentialService';

describe('cloudCredentialService mock credentials', () => {
  it('returns defensive copies from list reads', async () => {
    const first = await listCloudCredentials();
    const originalName = first[0].name;

    first[0].name = 'mutated-name';

    const second = await listCloudCredentials();
    expect(second[0].name).toBe(originalName);
    expect(second[0]).not.toBe(first[0]);
  });

  it('creates masked mock credentials without exposing the secret key', async () => {
    const created = await createCloudCredential({
      name: 'mock-created-credential',
      vendor: 'ALIYUN',
      accessKey: 'LTAI5tCreatedKey0000000001',
      secretKey: 'plain-secret',
      remark: 'created in mock mode',
    });

    expect(created).toMatchObject({
      name: 'mock-created-credential',
      vendor: 'ALIYUN',
      accessKey: 'LTAI****0001',
      remark: 'created in mock mode',
    });
    expect(created.secretKey).toBeUndefined();

    const listed = await listCloudCredentials();
    expect(listed.find((item) => item.id === created.id)?.secretKey).toBeUndefined();
  });

  it('updates metadata while keeping secret replacement write-only in mock mode', async () => {
    const created = await createCloudCredential({
      name: 'mock-update-credential',
      vendor: 'ALIYUN',
      accessKey: 'LTAI5tUpdateKey0000000001',
      secretKey: 'old-secret',
    });

    const updated = await updateCloudCredential({
      id: created.id,
      name: 'mock-updated-credential',
      secretKey: 'new-secret',
      remark: '',
    });

    expect(updated).toMatchObject({
      id: created.id,
      name: 'mock-updated-credential',
      accessKey: 'LTAI****0001',
      remark: '',
    });
    expect(updated.secretKey).toBeUndefined();
  });

  it('rejects deleting missing mock credentials', async () => {
    const before = await listCloudCredentials();

    await expect(deleteCloudCredential('missing-credential')).rejects.toThrow(
      'Cloud credential not found: missing-credential',
    );

    await expect(listCloudCredentials()).resolves.toEqual(before);
  });
});
