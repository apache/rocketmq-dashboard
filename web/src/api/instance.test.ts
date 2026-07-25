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
import { createInstance, deleteInstance, listInstances, updateInstance } from './instance';

const mock = new MockAdapter(client);
const instance = {
  id: 'instance-1',
  name: 'orders',
  type: 'PROXY' as const,
  endpoint: 'proxy:8080',
  remark: '',
  topicCount: 0,
  consumerGroupCount: 0,
  createdAt: '2026-07-18T00:00:00Z',
  updatedAt: '2026-07-18T00:00:00Z',
};

describe('instance API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('loads and returns persisted instance records', async () => {
    mock.onGet('/instances').reply(200, { code: 200, data: [instance] });
    mock.onPost('/instances/create').reply(200, { code: 200, data: instance });
    mock.onPost('/instances/update').reply(200, { code: 200, data: instance });

    await expect(listInstances()).resolves.toEqual([instance]);
    await expect(
      createInstance({ name: instance.name, type: instance.type, endpoint: instance.endpoint }),
    ).resolves.toEqual(instance);
    await expect(updateInstance({ id: instance.id, remark: 'updated' })).resolves.toEqual(instance);
  });

  it('sends the instance id when deleting', async () => {
    mock.onPost('/instances/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: instance.id });
      return [200, { code: 200, data: null }];
    });

    await expect(deleteInstance(instance.id)).resolves.toBeUndefined();
  });
});
