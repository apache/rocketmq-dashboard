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

    expect(credentials).toHaveLength(1);
    expect(credentials[0].vendor).toBe('ALIYUN');
    expect(credentials[0].secretKey).toBeUndefined();
  });

  it('creates a cloud credential', async () => {
    mock.onPost('/cloud-credentials/create').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        name: 'aliyun-prod',
        vendor: 'ALIYUN',
        accessKey: 'LTAI5tUnitTestKey000000001',
        secretKey: 'secret',
        remark: 'prod',
      });
      return [
        200,
        {
          code: 200,
          data: {
            id: 'cred-1',
            name: 'aliyun-prod',
            vendor: 'ALIYUN',
            accessKey: 'LTAI****0001',
            remark: 'prod',
            createdAt: '2026-08-06T00:00:00Z',
          },
        },
      ];
    });

    const credential = await createCloudCredential({
      name: 'aliyun-prod',
      vendor: 'ALIYUN',
      accessKey: 'LTAI5tUnitTestKey000000001',
      secretKey: 'secret',
      remark: 'prod',
    });

    expect(credential.accessKey).toBe('LTAI****0001');
    expect(credential.secretKey).toBeUndefined();
  });

  it('updates a cloud credential without requiring the access key', async () => {
    mock.onPost('/cloud-credentials/update').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        id: 'cred-1',
        name: 'aliyun-renamed',
        remark: 'updated',
      });
      return [
        200,
        {
          code: 200,
          data: {
            id: 'cred-1',
            name: 'aliyun-renamed',
            vendor: 'ALIYUN',
            accessKey: 'LTAI****0001',
            remark: 'updated',
            createdAt: '2026-08-06T00:00:00Z',
          },
        },
      ];
    });

    const credential = await updateCloudCredential({
      id: 'cred-1',
      name: 'aliyun-renamed',
      remark: 'updated',
    });

    expect(credential.name).toBe('aliyun-renamed');
  });

  it('deletes a cloud credential by id', async () => {
    mock.onPost('/cloud-credentials/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: 'cred-1' });
      return [200, { code: 200 }];
    });

    await expect(deleteCloudCredential('cred-1')).resolves.toBeUndefined();
  });
});
