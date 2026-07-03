import { USE_MOCK } from '../config';
import * as instanceApi from '../api/instance';
import type { Instance, CreateInstanceRequest, UpdateInstanceRequest } from '../api/instance';
import { mockInstances } from '../mock/instances';

// Compile-time switch: mock or real API
// Vite replaces USE_MOCK with a literal at build time → tree-shaking removes unused branch

export async function listInstances(): Promise<Instance[]> {
  if (USE_MOCK) return mockInstances;
  return instanceApi.listInstances();
}

export async function createInstance(data: CreateInstanceRequest): Promise<Instance> {
  if (USE_MOCK) {
    const instance: Instance = {
      id: String(Date.now()),
      ...data,
      remark: data.remark || '',
      topicCount: 0,
      consumerGroupCount: 0,
      createdAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
      updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
    };
    mockInstances.push(instance);
    return instance;
  }
  return instanceApi.createInstance(data);
}

export async function updateInstance(data: UpdateInstanceRequest): Promise<Instance> {
  if (USE_MOCK) {
    const idx = mockInstances.findIndex((i) => i.id === data.id);
    if (idx >= 0) {
      Object.assign(mockInstances[idx], data, {
        updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
      });
      return mockInstances[idx];
    }
    throw new Error('Instance not found');
  }
  return instanceApi.updateInstance(data);
}

export async function deleteInstance(id: string): Promise<void> {
  if (USE_MOCK) {
    const idx = mockInstances.findIndex((i) => i.id === id);
    if (idx >= 0) mockInstances.splice(idx, 1);
    return;
  }
  return instanceApi.deleteInstance(id);
}
