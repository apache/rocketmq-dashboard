import { isMockMode } from './dataMode';
import * as instanceApi from '../api/instance';
import type {
  Instance,
  CreateInstanceRequest,
  InstanceQuery,
  UpdateInstanceRequest,
  InstanceCapabilities,
} from '../api/instance';
import { mockInstances } from '../mock/instances';

// Compile-time switch: mock or real API

function copyInstance(instance: Instance): Instance {
  return { ...instance };
}

function matchesType(instance: Instance, type?: Instance['type']) {
  if (!type) return true;
  return type === 'PROXY' ? instance.type !== 'DIRECT' : instance.type === type;
}

const APACHE_CAPABILITIES: InstanceCapabilities['capabilities'] = [
  'TOPIC_MANAGEMENT',
  'CONSUMER_GROUP_MANAGEMENT',
  'MESSAGE_QUERY',
  'MESSAGE_TRACE',
  'ACL_MANAGEMENT',
  'DLQ_MANAGEMENT',
];

const CLOUD_CAPABILITIES: InstanceCapabilities['capabilities'] = [
  'TOPIC_MANAGEMENT',
  'CONSUMER_GROUP_MANAGEMENT',
  'MESSAGE_QUERY',
  'MESSAGE_TRACE',
  'ACL_MANAGEMENT',
];

export async function listInstances(query: InstanceQuery = {}): Promise<Instance[]> {
  if (isMockMode()) {
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
    capabilities: [...(vendor === 'APACHE' ? APACHE_CAPABILITIES : CLOUD_CAPABILITIES)],
  };
}

export async function createInstance(data: CreateInstanceRequest): Promise<Instance> {
  if (isMockMode()) {
    const cloudManaged = data.vendor === 'ALIYUN' || data.vendor === 'TENCENT';
    const instance: Instance = {
      id: Date.now(),
      ...data,
      name: data.name || '',
      type: cloudManaged
        ? 'PROXY'
        : data.type === 'PROXY'
          ? 'PROXY_CLUSTER'
          : data.type || 'PROXY_CLUSTER',
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
    const { instanceId, ...changes } = data;
    const idx = mockInstances.findIndex((i) => i.name === instanceId);
    if (idx >= 0) {
      Object.assign(mockInstances[idx], changes, {
        gmtModified: new Date().toISOString().replace('T', ' ').slice(0, 19),
      });
      return copyInstance(mockInstances[idx]);
    }
    throw new Error('Instance not found');
  }
  return instanceApi.updateInstance(data);
}

export async function deleteInstance(instanceId: string): Promise<void> {
  if (isMockMode()) {
    const idx = mockInstances.findIndex((i) => i.name === instanceId);
    if (idx < 0) throw new Error(`Instance not found: ${instanceId}`);
    mockInstances.splice(idx, 1);
    return;
  }
  return instanceApi.deleteInstance(instanceId);
}
