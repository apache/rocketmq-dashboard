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
import client from '../client';
import { listTencentInstances, listTencentRegions } from '../tencentCatalog';

const mock = new MockAdapter(client);

describe('tencentCatalog API', () => {
  beforeEach(() => {
    mock.reset();
  });

  afterEach(() => {
    mock.reset();
  });

  it('lists Tencent regions for a credential', async () => {
    const regions = [{ regionId: 'ap-guangzhou', regionName: '广州' }];
    mock.onGet('/cloud/tencent/regions').reply((config) => {
      expect(config.params).toEqual({ credentialId: 7 });
      return [200, { data: regions }];
    });

    await expect(listTencentRegions(7)).resolves.toEqual(regions);
  });

  it('forwards optional search when listing Tencent instances', async () => {
    const instances = [{ instanceId: 'rocketmq-prod', instanceName: 'prod' }];
    mock.onGet('/cloud/tencent/instances').reply((config) => {
      expect(config.params).toEqual({
        credentialId: 7,
        regionId: 'ap-guangzhou',
        search: 'prod',
      });
      return [200, { data: instances }];
    });

    await expect(listTencentInstances(7, 'ap-guangzhou', 'prod')).resolves.toEqual(instances);
  });
});
