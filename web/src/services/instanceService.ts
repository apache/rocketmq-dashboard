import { isMockMode } from './dataMode';
import * as instanceApi from '../api/instance';
import type {
  Instance,
  CreateInstanceRequest,
  InstanceQuery,
  UpdateInstanceRequest,
} from '../api/instance';
import { mockInstances } from '../mock/instances';

// Compile-time switch: mock or real API

function copyInstance(instance: Instance): Instance {
  return { ...instance };
}

export async function listInstances(query: InstanceQuery = {}): Promise<Instance[]> {
  if (isMockMode()) {
    const search = query.search?.trim().toLowerCase();
    return mockInstances
      .filter((instance) => !query.type || instance.type === query.type)
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

export async function createInstance(data: CreateInstanceRequest): Promise<Instance> {
  if (isMockMode()) {
    const instance: Instance = {
      id: Date.now(),
      ...data,
      name: data.name || '',
      type: data.type || 'PROXY',
      endpoint: data.endpoint || '',
      vendor: data.vendor || 'APACHE',
      remark: data.remark || '',
      topicCount: 0,
      consumerGroupCount: 0,
      gmtCreate: new Date().toISOString().replace('T', ' ').slice(0, 19),
      gmtModified: new Date().toISOString().replace('T', ' ').slice(0, 19),
    };
    mockInstances.push(instance);
    return copyInstance(instance);
  }
  return instanceApi.createInstance(data);
}

export async function updateInstance(data: UpdateInstanceRequest): Promise<Instance> {
  if (isMockMode()) {
    const idx = mockInstances.findIndex((i) => i.id === data.id);
    if (idx >= 0) {
      Object.assign(mockInstances[idx], data, {
        gmtModified: new Date().toISOString().replace('T', ' ').slice(0, 19),
      });
      return copyInstance(mockInstances[idx]);
    }
    throw new Error('Instance not found');
  }
  return instanceApi.updateInstance(data);
}

export async function deleteInstance(id: number): Promise<void> {
  if (isMockMode()) {
    const idx = mockInstances.findIndex((i) => i.id === id);
    if (idx < 0) throw new Error(`Instance not found: ${id}`);
    mockInstances.splice(idx, 1);
    return;
  }
  return instanceApi.deleteInstance(id);
}
