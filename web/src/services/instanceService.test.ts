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

import { beforeEach, describe, expect, it, vi } from 'vitest';

const dataModeMock = vi.hoisted(() => ({ isMockMode: vi.fn(() => true) }));
vi.mock('./dataMode', () => dataModeMock);
const instanceApiMock = vi.hoisted(() => ({
  listInstances: vi.fn(),
  createInstance: vi.fn(),
  updateInstance: vi.fn(),
  deleteInstance: vi.fn(),
  deleteInstancesBatch: vi.fn(),
}));
vi.mock('../api/instance', () => instanceApiMock);
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));

import {
  createInstance,
  deleteInstance,
  deleteInstancesBatch,
  getInstanceCapabilities,
  listInstances,
  updateInstance,
} from './instanceService';
import type { Instance } from '../api/instance';

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
    expect(byType.map((instance) => instance.id)).toEqual([1, 2]);

    const byEndpoint = await listInstances({ search: '  10.0.2.21  ' });
    expect(byEndpoint.map((instance) => instance.id)).toEqual([3]);

    const combined = await listInstances({ type: 'DIRECT', search: 'instance-direct-2' });
    expect(combined.map((instance) => instance.id)).toEqual([2]);

    const clusterOnly = await listInstances({ type: 'PROXY_CLUSTER' });
    expect(clusterOnly.map((instance) => instance.id)).toEqual([3, 4]);
    await expect(listInstances({ type: 'PROXY_LOCAL' })).resolves.toEqual([
      expect.objectContaining({ type: 'PROXY_LOCAL' }),
    ]);
    await expect(listInstances({ type: 'CLOUD' })).resolves.toEqual([]);
  });

  it('does not expose created or updated store records by reference', async () => {
    const created = await createInstance({
      name: 'rocketmq-copy-test',
      type: 'PROXY_CLUSTER',
      endpoint: 'proxy-copy-test:8080',
      remark: 'created',
    });

    created.name = 'mutated-created';
    created.remark = 'mutated-created-remark';

    const afterCreate = await listInstances();
    const storedCreated = afterCreate.find((instance) => instance.id === created.id);
    expect(storedCreated).toMatchObject({
      name: 'rocketmq-copy-test',
      type: 'PROXY_CLUSTER',
      remark: 'created',
    });

    const updated = await updateInstance({
      instanceId: 'rocketmq-copy-test',
      remark: 'updated',
    });
    updated.remark = 'mutated-updated';

    const afterUpdate = await listInstances();
    const storedUpdated = afterUpdate.find((instance) => instance.id === created.id);
    expect(storedUpdated?.remark).toBe('updated');
  });

  it('rejects deleting missing mock instances', async () => {
    const before = await listInstances();

    await expect(deleteInstance('missing-instance')).rejects.toThrow(
      'Instance not found: missing-instance',
    );

    await expect(listInstances()).resolves.toEqual(before);
  });

  it('returns provider-specific mock capabilities without sharing mutable arrays', async () => {
    const first = await getInstanceCapabilities('instance-direct-1');
    expect(first.instanceId).toBe('instance-direct-1');
    expect(first.vendor).toBe('APACHE');
    first.capabilities.length = 0;

    const second = await getInstanceCapabilities('instance-direct-1');

    expect(second.capabilities).toEqual(
      expect.arrayContaining(['DLQ_MANAGEMENT', 'DIRECT_MESSAGE_CONSUME']),
    );
    await expect(getInstanceCapabilities('missing-instance')).rejects.toThrow(
      'Instance not found: missing-instance',
    );
  });
});

describe('instanceService list request dedupe', () => {
  beforeEach(() => {
    dataModeMock.isMockMode.mockReturnValue(true);
    instanceApiMock.listInstances.mockReset();
  });

  it('shares one inflight list request between concurrent callers', async () => {
    dataModeMock.isMockMode.mockReturnValue(false);
    const fixture: Instance[] = [
      {
        id: 1,
        name: 'shared-instance',
        remark: null,
        type: 'PROXY_CLUSTER',
        endpoint: '10.0.0.1:8080',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      },
    ];
    let resolveList!: (value: Instance[]) => void;
    instanceApiMock.listInstances.mockImplementation(
      () =>
        new Promise<Instance[]>((resolve) => {
          resolveList = resolve;
        }),
    );

    const first = listInstances({});
    const second = listInstances({ search: '   ' });
    resolveList(fixture);

    const [a, b] = await Promise.all([first, second]);
    expect(instanceApiMock.listInstances).toHaveBeenCalledTimes(1);
    expect(a).toEqual(b);
    expect(a).not.toBe(b);

    instanceApiMock.listInstances.mockResolvedValue(fixture);
    await listInstances({});
    expect(instanceApiMock.listInstances).toHaveBeenCalledTimes(2);
  });

  it('does not share inflight requests across data modes', async () => {
    dataModeMock.isMockMode.mockReturnValue(false);
    let resolveRealList!: (value: Instance[]) => void;
    instanceApiMock.listInstances.mockImplementationOnce(
      () =>
        new Promise<Instance[]>((resolve) => {
          resolveRealList = resolve;
        }),
    );

    const realRequest = listInstances({});
    dataModeMock.isMockMode.mockReturnValue(true);
    const mockResult = await listInstances({});

    expect(mockResult.length).toBeGreaterThan(0);
    expect(instanceApiMock.listInstances).toHaveBeenCalledTimes(1);

    resolveRealList([]);
    await expect(realRequest).resolves.toEqual([]);
  });
});

describe('instanceService dedupe invalidation after mutations', () => {
  beforeEach(() => {
    dataModeMock.isMockMode.mockReturnValue(false);
    instanceApiMock.listInstances.mockReset();
  });

  function instanceFixture(name: string, remark: string | null = null): Instance {
    return {
      id: 1,
      name,
      remark,
      type: 'PROXY_CLUSTER',
      endpoint: '10.0.0.1:8080',
      topicCount: 0,
      consumerGroupCount: 0,
      gmtCreate: '2026-01-01T00:00:00Z',
      gmtModified: '2026-01-01T00:00:00Z',
    };
  }

  function holdFirstListRequest(): { resolve: (value: Instance[]) => void } {
    const holder: { resolve?: (value: Instance[]) => void } = {};
    instanceApiMock.listInstances.mockImplementationOnce(
      () =>
        new Promise<Instance[]>((resolve) => {
          holder.resolve = resolve;
        }),
    );
    return holder as { resolve: (value: Instance[]) => void };
  }

  it('does not serve the pre-create snapshot to a list request issued after createInstance', async () => {
    const before = [instanceFixture('kept')];
    const after = [instanceFixture('kept'), instanceFixture('created')];
    const first = holdFirstListRequest();
    instanceApiMock.listInstances.mockResolvedValueOnce(after);

    const staleRead = listInstances({});
    await createInstance({
      name: 'created',
      type: 'PROXY_CLUSTER',
      endpoint: '10.0.0.2:8080',
    });
    const refresh = listInstances({});
    first.resolve(before);

    await expect(staleRead).resolves.toEqual(before);
    await expect(refresh).resolves.toEqual(after);
    expect(instanceApiMock.listInstances).toHaveBeenCalledTimes(2);
  });

  it('does not serve the pre-update snapshot to a list request issued after updateInstance', async () => {
    const before = [instanceFixture('kept', 'old')];
    const after = [instanceFixture('kept', 'updated')];
    const first = holdFirstListRequest();
    instanceApiMock.listInstances.mockResolvedValueOnce(after);

    const staleRead = listInstances({});
    await updateInstance({ instanceId: 'kept', remark: 'updated' });
    const refresh = listInstances({});
    first.resolve(before);

    await expect(staleRead).resolves.toEqual(before);
    await expect(refresh).resolves.toEqual(after);
    expect(instanceApiMock.listInstances).toHaveBeenCalledTimes(2);
  });

  it('does not serve the pre-delete snapshot to a list request issued after deleteInstance', async () => {
    const before = [instanceFixture('removed'), instanceFixture('kept')];
    const after = [instanceFixture('kept')];
    const first = holdFirstListRequest();
    instanceApiMock.listInstances.mockResolvedValueOnce(after);

    const staleRead = listInstances({});
    await deleteInstance('removed');
    const refresh = listInstances({});
    first.resolve(before);

    await expect(staleRead).resolves.toEqual(before);
    await expect(refresh).resolves.toEqual(after);
    expect(instanceApiMock.listInstances).toHaveBeenCalledTimes(2);
  });

  it('does not serve the pre-delete snapshot to a list request issued after deleteInstancesBatch', async () => {
    const before = [instanceFixture('removed'), instanceFixture('kept')];
    const after = [instanceFixture('kept')];
    const first = holdFirstListRequest();
    instanceApiMock.listInstances.mockResolvedValueOnce(after);

    const staleRead = listInstances({});
    await deleteInstancesBatch(['removed']);
    const refresh = listInstances({});
    first.resolve(before);

    await expect(staleRead).resolves.toEqual(before);
    await expect(refresh).resolves.toEqual(after);
    expect(instanceApiMock.listInstances).toHaveBeenCalledTimes(2);
  });
});
