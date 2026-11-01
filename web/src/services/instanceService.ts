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

import { isMockMode } from './dataMode';
import * as instanceApi from '../api/instance';
import type {
  Instance,
  CreateInstanceRequest,
  InstanceQuery,
  InstanceVendor,
  UpdateInstanceRequest,
  InstanceCapabilities,
} from '../api/instance';
import { getMockInstanceCapabilities, mockInstances } from '../mock/instances';

// Compile-time switch: mock or real API

function copyInstance(instance: Instance): Instance {
  return { ...instance };
}

function matchesType(instance: Instance, type?: Instance['type']) {
  if (!type) return true;
  return instance.type === type;
}

const inflightListRequests = new Map<string, Promise<Instance[]>>();

// A mutation that just completed must not be shadowed by a list request that
// started earlier: callers issuing a read after a write would otherwise join
// the stale inflight snapshot, so drop them after every successful mutation.
function invalidateInflightListRequests(): void {
  inflightListRequests.clear();
}

export function listInstances(query: InstanceQuery = {}): Promise<Instance[]> {
  const mockMode = isMockMode();
  // withCounts is part of the identity: a counts-free response must never satisfy a caller that
  // renders the counts (it would show zeros), nor the other way round.
  const key = JSON.stringify([
    mockMode,
    query.type ?? null,
    query.search?.trim() || null,
    query.withCounts ?? false,
  ]);
  const inflight = inflightListRequests.get(key);
  if (inflight) {
    return inflight.then((items) => items.map(copyInstance));
  }
  const request = fetchInstances(query, mockMode).finally(() => {
    // A mutation may have replaced this request; only remove the entry we installed.
    if (inflightListRequests.get(key) === request) inflightListRequests.delete(key);
  });
  inflightListRequests.set(key, request);
  return request.then((items) => items.map(copyInstance));
}

async function fetchInstances(query: InstanceQuery, mockMode: boolean): Promise<Instance[]> {
  if (mockMode) {
    const search = query.search?.trim().toLowerCase();
    return mockInstances
      .filter((instance) => matchesType(instance, query.type))
      .filter(
        (instance) =>
          !search ||
          [instance.name, instance.endpoint, instance.remark].some((value) =>
            value?.toLowerCase().includes(search),
          ),
      )
      .map(copyInstance);
  }
  return instanceApi.listInstances(query);
}

export async function getInstanceCapabilities(instanceId: string): Promise<InstanceCapabilities> {
  if (!isMockMode()) {
    return instanceApi.getInstanceCapabilities(instanceId);
  }
  const instance = mockInstances.find((candidate) => candidate.name === instanceId);
  if (!instance) throw new Error(`Instance not found: ${instanceId}`);
  const vendor = instance.vendor ?? 'APACHE';
  return {
    instanceId: instance.name,
    vendor,
    accessType: instance.type,
    capabilities: getMockInstanceCapabilities(instance),
  };
}

export async function createInstance(data: CreateInstanceRequest): Promise<Instance> {
  if (isMockMode()) {
    const requestedName = data.name?.trim();
    if (requestedName && mockInstances.some((instance) => instance.name.trim() === requestedName)) {
      throw new Error(`Instance name already exists: ${requestedName}`);
    }
    const cloudManaged = data.vendor === 'ALIYUN' || data.vendor === 'TENCENT';
    const instance: Instance = {
      id: Date.now(),
      ...data,
      name: data.name || '',
      type: cloudManaged ? 'CLOUD' : data.type || 'PROXY_CLUSTER',
      endpoint: data.endpoint || '',
      vendor: data.vendor || 'APACHE',
      remark: data.remark || '',
      topicCount: 0,
      consumerGroupCount: 0,
      gmtCreate: new Date().toISOString().replace('T', ' ').slice(0, 19),
      gmtModified: new Date().toISOString().replace('T', ' ').slice(0, 19),
    };
    mockInstances.push(instance);
    invalidateInflightListRequests();
    return copyInstance(instance);
  }
  const created = await instanceApi.createInstance(data);
  invalidateInflightListRequests();
  return created;
}

export async function importCloudInstances(data: {
  vendor: Exclude<InstanceVendor, 'APACHE'>;
  credentialId: number;
}): Promise<instanceApi.CloudImportResult> {
  if (isMockMode()) {
    const result = { discovered: 0, imported: 0, skipped: 0, failed: [] };
    invalidateInflightListRequests();
    return result;
  }
  const result = await instanceApi.importCloudInstances(data);
  invalidateInflightListRequests();
  return result;
}

export async function deleteInstancesBatch(ids: string[]): Promise<instanceApi.BatchDeleteResult> {
  if (isMockMode()) {
    const known = new Set(mockInstances.map((instance) => instance.name));
    const failed = ids
      .filter((id) => !known.has(id))
      .map((id) => `${id}: Instance not found: ${id}`);
    for (const id of ids) {
      const idx = mockInstances.findIndex((instance) => instance.name === id);
      if (idx >= 0) mockInstances.splice(idx, 1);
    }
    invalidateInflightListRequests();
    return { deleted: ids.length - failed.length, failed };
  }
  const result = await instanceApi.deleteInstancesBatch(ids);
  invalidateInflightListRequests();
  return result;
}

export async function updateInstance(data: UpdateInstanceRequest): Promise<Instance> {
  if (isMockMode()) {
    const { instanceId, ...changes } = data;
    const idx = mockInstances.findIndex((i) => i.name === instanceId);
    if (idx >= 0) {
      Object.assign(mockInstances[idx], changes, {
        gmtModified: new Date().toISOString().replace('T', ' ').slice(0, 19),
      });
      invalidateInflightListRequests();
      return copyInstance(mockInstances[idx]);
    }
    throw new Error('Instance not found');
  }
  const updated = await instanceApi.updateInstance(data);
  invalidateInflightListRequests();
  return updated;
}

export async function deleteInstance(instanceId: string): Promise<void> {
  if (isMockMode()) {
    const idx = mockInstances.findIndex((i) => i.name === instanceId);
    if (idx < 0) throw new Error(`Instance not found: ${instanceId}`);
    mockInstances.splice(idx, 1);
    invalidateInflightListRequests();
    return;
  }
  await instanceApi.deleteInstance(instanceId);
  invalidateInflightListRequests();
}
