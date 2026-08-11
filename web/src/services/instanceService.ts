import { isMockMode } from './dataMode';
import * as instanceApi from '../api/instance';
import type {
  Instance,
  CreateInstanceRequest,
  InstanceExportBundle,
  InstanceImportRequest,
  InstanceImportResult,
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
      id: String(Date.now()),
      ...data,
      name: data.name || '',
      type: data.type || 'PROXY',
      endpoint: data.endpoint || '',
      vendor: data.vendor || 'APACHE',
      remark: data.remark || '',
      topicCount: 0,
      consumerGroupCount: 0,
      createdAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
      updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
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
        updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
      });
      return copyInstance(mockInstances[idx]);
    }
    throw new Error('Instance not found');
  }
  return instanceApi.updateInstance(data);
}

export async function deleteInstance(id: string): Promise<void> {
  if (isMockMode()) {
    const idx = mockInstances.findIndex((i) => i.id === id);
    if (idx < 0) throw new Error(`Instance not found: ${id}`);
    mockInstances.splice(idx, 1);
    return;
  }
  return instanceApi.deleteInstance(id);
}

const toImportItem = (instance: Instance) => ({
  id: instance.id,
  name: instance.name,
  remark: instance.remark,
  type: instance.type,
  endpoint: instance.endpoint,
  vendor: instance.vendor || ('APACHE' as const),
  cloudInstanceId: instance.cloudInstanceId,
  credentialId: instance.credentialId,
  adminCredentialRef: instance.adminCredentialRef,
  regionId: instance.regionId,
});

export async function exportInstances(): Promise<InstanceExportBundle> {
  if (isMockMode()) {
    return {
      schemaVersion: 1,
      exportedAt: new Date().toISOString(),
      instances: mockInstances
        .slice()
        .sort((left, right) => left.name.localeCompare(right.name))
        .map(toImportItem),
    };
  }
  return instanceApi.exportInstances();
}

export async function importInstances(
  request: InstanceImportRequest,
): Promise<InstanceImportResult> {
  if (!isMockMode()) return instanceApi.importInstances(request);

  const result: InstanceImportResult = {
    createdIds: [],
    updatedIds: [],
    skippedIds: [],
    errors: {},
    dryRun: request.dryRun,
  };
  const seenIds = new Set<string>();
  request.instances.forEach((item, index) => {
    const id = item.id?.trim() || `mock-import-${Date.now()}-${index}`;
    if (seenIds.has(id)) {
      result.errors[id] = 'Duplicate instance id in import file';
      return;
    }
    seenIds.add(id);
    const existingIndex = mockInstances.findIndex((instance) => instance.id === id);
    if (existingIndex >= 0 && !request.overwrite) {
      result.skippedIds.push(id);
      return;
    }
    if (!item.name?.trim() || !item.type || !item.endpoint?.trim()) {
      result.errors[id] = 'name, type and endpoint are required';
      return;
    }
    const now = new Date().toISOString().replace('T', ' ').slice(0, 19);
    const imported: Instance = {
      ...item,
      id,
      name: item.name.trim(),
      endpoint: item.endpoint.trim(),
      remark: item.remark || '',
      vendor: item.vendor || 'APACHE',
      topicCount: 0,
      consumerGroupCount: 0,
      createdAt: existingIndex >= 0 ? mockInstances[existingIndex].createdAt : now,
      updatedAt: now,
    };
    if (existingIndex >= 0) {
      result.updatedIds.push(id);
      if (!request.dryRun) mockInstances.splice(existingIndex, 1, imported);
    } else {
      result.createdIds.push(id);
      if (!request.dryRun) mockInstances.push(imported);
    }
  });
  return result;
}
