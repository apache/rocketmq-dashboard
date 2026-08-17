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
  createCloudCredential,
  deleteCloudCredential,
  listCloudCredentials,
  updateCloudCredential,
} from './cloudCredential';

const mock = new MockAdapter(client);

describe('cloudCredential API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('returns masked credentials from the backend', async () => {
    mock.onGet('/cloud-credentials').reply(200, {
      code: 200,
      data: [
        {
          id: 'cred-1',
          name: 'aliyun-test',
          vendor: 'ALIYUN',
          accessKey: 'LTAI****0001',
          createdAt: '2026-08-06T00:00:00Z',
        },
      ],
    });

    const credentials = await listCloudCredentials();

    const items = Array.isArray(credentials) ? credentials : credentials.items;
    expect(items).toHaveLength(1);
    expect(items[0].vendor).toBe('ALIYUN');
    expect(items[0].secretKey).toBeUndefined();
  });

  it('creates a credential with the full payload', async () => {
    mock.onPost('/cloud-credentials/create').reply((config) => {
      const body = JSON.parse(config.data);
      return [
        200,
        {
          code: 200,
          data: {
            id: 1,
            name: body.name,
            vendor: body.vendor,
            accessKey: 'LTAI****0001',
            gmtCreate: '2026-08-18T00:00:00',
          },
        },
      ];
    });

    const saved = await createCloudCredential({
      name: 'aliyun-test',
      vendor: 'ALIYUN',
      accessKey: 'LTAI00000001',
      secretKey: 'secret-0001',
      remark: 'test account',
    });

    expect(saved.id).toBe(1);
    expect(JSON.parse(mock.history.post[0].data)).toMatchObject({
      name: 'aliyun-test',
      vendor: 'ALIYUN',
      accessKey: 'LTAI00000001',
      secretKey: 'secret-0001',
    });
  });

  it('updates only provided fields and deletes by string id', async () => {
    mock.onPost('/cloud-credentials/update').reply(200, {
      code: 200,
      data: { id: 1, name: 'renamed', vendor: 'ALIYUN', accessKey: 'LTAI****0001' },
    });
    mock.onPost('/cloud-credentials/delete').reply(200, { code: 200 });

    const saved = await updateCloudCredential({ id: 1, name: 'renamed' });
    expect(saved.name).toBe('renamed');
    expect(JSON.parse(mock.history.post[0].data)).toEqual({ id: 1, name: 'renamed' });

    await deleteCloudCredential(1);
    expect(JSON.parse(mock.history.post[1].data)).toEqual({ id: '1' });
  });
});
