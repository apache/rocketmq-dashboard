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

import { createInstance, listInstances, updateInstance } from './instanceService';

describe('instanceService mock instances', () => {
  it('returns defensive copies from list reads', async () => {
    const instances = await listInstances();
    const originalName = instances[0].name;
    const originalRemark = instances[0].remark;

    instances[0].name = 'mutated-name';
    instances[0].remark = 'mutated-remark';

    const fresh = await listInstances();

    expect(fresh[0].name).toBe(originalName);
    expect(fresh[0].remark).toBe(originalRemark);
    expect(fresh[0]).not.toBe(instances[0]);
  });

  it('filters mock instances with the same type and search semantics as the API', async () => {
    const byType = await listInstances({ type: 'DIRECT' });
    expect(byType.map((instance) => instance.id)).toEqual([
      'instance-direct-1',
      'instance-direct-2',
    ]);

    const byEndpoint = await listInstances({ search: '  10.0.2.21  ' });
    expect(byEndpoint.map((instance) => instance.id)).toEqual(['instance-proxy-1']);

    const combined = await listInstances({ type: 'DIRECT', search: 'instance-direct-2' });
    expect(combined.map((instance) => instance.id)).toEqual(['instance-direct-2']);
  });

  it('does not expose created or updated store records by reference', async () => {
    const created = await createInstance({
      name: 'rocketmq-copy-test',
      type: 'PROXY',
      endpoint: 'proxy-copy-test:8080',
      remark: 'created',
    });

    created.name = 'mutated-created';
    created.remark = 'mutated-created-remark';

    const afterCreate = await listInstances();
    const storedCreated = afterCreate.find((instance) => instance.id === created.id);
    expect(storedCreated).toMatchObject({
      name: 'rocketmq-copy-test',
      remark: 'created',
    });

    const updated = await updateInstance({
      id: created.id,
      remark: 'updated',
    });
    updated.remark = 'mutated-updated';

    const afterUpdate = await listInstances();
    const storedUpdated = afterUpdate.find((instance) => instance.id === created.id);
    expect(storedUpdated?.remark).toBe('updated');
  });
});
