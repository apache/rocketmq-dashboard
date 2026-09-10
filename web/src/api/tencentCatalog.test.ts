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

describe('tencentCatalog API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('lists regions for a credential', async () => {
    mock.onGet('/cloud/tencent/regions').reply(200, {
      code: 200,
      data: [{ regionId: 'ap-guangzhou', regionName: '华南地区（广州）' }],
    });

    const regions = await listTencentRegions(12);

    expect(regions[0].regionId).toBe('ap-guangzhou');
  });

  it('lists cloud instances with credential and region params', async () => {
    mock.onGet('/cloud/tencent/instances').reply((config) => {
      expect(config.params).toMatchObject({ credentialId: 12, regionId: 'ap-guangzhou' });
      return [
        200,
        {
          code: 200,
          data: [
            {
              instanceId: 'rocketmq-xxxxx',
              instanceName: 'prod-mq',
              status: 'RUNNING',
              regionId: 'ap-guangzhou',
            },
          ],
        },
      ];
    });

    const instances = await listTencentInstances(12, 'ap-guangzhou');

    expect(instances[0].instanceId).toBe('rocketmq-xxxxx');
  });

  it('sends the search term only when provided', async () => {
    mock.onGet('/cloud/tencent/instances').reply((config) => {
      expect(config.params).toMatchObject({ credentialId: 12, regionId: 'ap-guangzhou' });
      expect(config.params.search).toBeUndefined();
      return [200, { code: 200, data: [] }];
    });
    await listTencentInstances(12, 'ap-guangzhou');

    mock.onGet('/cloud/tencent/instances').reply((config) => {
      expect(config.params.search).toBe('prod');
      return [200, { code: 200, data: [] }];
    });
    await listTencentInstances(12, 'ap-guangzhou', 'prod');
  });
});
