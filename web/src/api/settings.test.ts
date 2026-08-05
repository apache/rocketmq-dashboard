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
  createDataSource,
  deleteDataSource,
  listDataSources,
  testDataSource,
  updateDataSource,
} from './settings';
import type { DataSource } from './settings';

const mock = new MockAdapter(client);
const source: DataSource = {
  key: 'source-1',
  name: 'Prometheus',
  type: 'Prometheus',
  url: 'http://prometheus:9090',
  auth: 'None',
  status: 'healthy',
};

describe('data sources API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('loads and returns created or updated data sources', async () => {
    mock.onGet('/settings/datasources').reply(200, { code: 200, data: [source] });
    mock.onPost('/settings/datasources/create').reply(200, { code: 200, data: source });
    mock.onPost('/settings/datasources/update').reply(200, { code: 200, data: source });

    await expect(listDataSources()).resolves.toEqual([source]);
    await expect(createDataSource({ name: source.name })).resolves.toEqual(source);
    await expect(updateDataSource(source)).resolves.toEqual(source);
  });

  it('uses a key query parameter for deletion and sends test auth details', async () => {
    mock.onPost('/settings/datasources/delete').reply((config) => {
      expect(config.params).toEqual({ key: source.key });
      return [200, { code: 200, data: null }];
    });
    mock.onPost('/settings/datasources/test').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        type: source.type,
        url: source.url,
        auth: source.auth,
      });
      return [200, { code: 200, data: { success: true, message: 'Connection successful' } }];
    });

    await expect(deleteDataSource(source.key)).resolves.toBeUndefined();
    await expect(
      testDataSource({ type: source.type, url: source.url, auth: source.auth }),
    ).resolves.toEqual({ success: true, message: 'Connection successful' });
  });
});
