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
import { listTencentInstances, listTencentRegions } from './tencentCatalog';

const mock = new MockAdapter(client);

describe('Tencent cloud catalog API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('loads regions for the selected credential', async () => {
    const regions = [{ regionId: 'ap-guangzhou', regionName: '广州' }];
    mock.onGet('/cloud/tencent/regions').reply((config) => {
      expect(config.params).toStrictEqual({ credentialId: 7 });
      return [200, { code: 200, data: regions }];
    });

    await expect(listTencentRegions(7)).resolves.toStrictEqual(regions);
  });

  it('forwards credential, region, and search when listing instances', async () => {
    const instances = [
      {
        instanceId: 'rocketmq-prod',
        instanceName: 'prod',
        status: 'RUNNING',
        regionId: 'ap-guangzhou',
      },
    ];
    mock.onGet('/cloud/tencent/instances').reply((config) => {
      expect(config.params).toStrictEqual({
        credentialId: 7,
        regionId: 'ap-guangzhou',
        search: 'prod',
      });
      return [200, { code: 200, data: instances }];
    });

    await expect(listTencentInstances(7, 'ap-guangzhou', 'prod')).resolves.toStrictEqual(instances);
  });

  it('omits the search parameter when no search is supplied', async () => {
    mock.onGet('/cloud/tencent/instances').reply((config) => {
      expect(config.params).toStrictEqual({ credentialId: 7, regionId: 'ap-guangzhou' });
      return [200, { code: 200, data: [] }];
    });

    await expect(listTencentInstances(7, 'ap-guangzhou')).resolves.toStrictEqual([]);
  });
});
