/*
 * Licensed to the Apache Software Foundation (ASF)  See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership.  The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

describe('tencentCatalog API contract', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('lists regions for a credential with exactly the credentialId param', async () => {
    mock.onGet('/cloud/tencent/regions').reply((config) => {
      expect(config.params).toStrictEqual({ credentialId: 9 });
      return [
        200,
        {
          code: 200,
          data: [{ regionId: 'ap-guangzhou', regionName: '华南地区（广州）' }],
        },
      ];
    });

    const regions = await listTencentRegions(9);

    expect(regions[0].regionId).toBe('ap-guangzhou');
  });

  it('omits the search param entirely when no search is given', async () => {
    mock.onGet('/cloud/tencent/instances').reply((config) => {
      // toStrictEqual: a key present with an undefined value would also fail,
      // pinning the `...(search ? { search } : {})` omission contract.
      expect(config.params).toStrictEqual({ credentialId: 9, regionId: 'ap-guangzhou' });
      return [
        200,
        {
          code: 200,
          data: [
            {
              instanceId: 'rmq-ap-xxx',
              instanceName: 'prod-mq',
              status: 'RUNNING',
              regionId: 'ap-guangzhou',
            },
          ],
        },
      ];
    });

    const instances = await listTencentInstances(9, 'ap-guangzhou');

    expect(instances[0].instanceId).toBe('rmq-ap-xxx');
  });

  it('forwards the search param when one is given', async () => {
    mock.onGet('/cloud/tencent/instances').reply((config) => {
      expect(config.params).toStrictEqual({
        credentialId: 9,
        regionId: 'ap-guangzhou',
        search: 'prod',
      });
      return [200, { code: 200, data: [] }];
    });

    await expect(listTencentInstances(9, 'ap-guangzhou', 'prod')).resolves.toEqual([]);
  });
});
