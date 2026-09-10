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
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import client from './client';
import { listTencentInstances, listTencentRegions } from './tencentCatalog';

const mock = new MockAdapter(client);

describe('tencentCatalog API', () => {
  beforeEach(() => mock.reset());
  afterEach(() => mock.reset());

  it('lists regions for a credential', async () => {
    mock.onGet('/cloud/tencent/regions').reply(200, {
      code: 200,
      data: [{ regionId: 'ap-guangzhou', regionName: '华南地区（广州）' }],
    });

    const regions = await listTencentRegions(9);

    expect(regions[0].regionId).toBe('ap-guangzhou');
  });

  it('lists instances with credential and region params', async () => {
    mock.onGet('/cloud/tencent/instances').reply((config) => {
      expect(config.params).toMatchObject({ credentialId: 9, regionId: 'ap-guangzhou' });
      return [200, { code: 200, data: [{ instanceId: 'rocketmq-xxx', regionId: 'ap-guangzhou' }] }];
    });

    const instances = await listTencentInstances(9, 'ap-guangzhou');

    expect(instances[0].instanceId).toBe('rocketmq-xxx');
  });

  it('includes an optional search filter when provided', async () => {
    mock.onGet('/cloud/tencent/instances').reply((config) => {
      expect(config.params).toMatchObject({
        credentialId: 9,
        regionId: 'ap-guangzhou',
        search: 'prod',
      });
      return [200, { code: 200, data: [] }];
    });

    await listTencentInstances(9, 'ap-guangzhou', 'prod');
  });

  it('omits the search filter when it is absent', async () => {
    mock.onGet('/cloud/tencent/instances').reply((config) => {
      expect(config.params.search).toBeUndefined();
      return [200, { code: 200, data: [] }];
    });

    await listTencentInstances(9, 'ap-guangzhou');
  });
});
