import { isMockMode } from './dataMode';
import * as metadataApi from '../api/metadata';
import type {
  ConsumerGroup,
  ConsumerGroupPageQuery,
  ConsumerGroupQuery,
  ConsumerGroupSettings,
  ConsumerGroupDetail,
  ConsumerStackTrace,
  PageResult,
  QueueProgress,
  ResetConsumerOffsetRequest,
  SubscriptionEntry,
} from '../api/metadata';
import { mockConsumerGroups, mockQueueProgress, mockSubscriptions } from '../mock/consumers';

const consumerGroupsState = mockConsumerGroups as unknown as ConsumerGroup[];

function copyConsumerInstance(
  instance: ConsumerGroup['instances'][number],
): ConsumerGroup['instances'][number] {
  return {
    ...instance,
    subscribedTopics: [...instance.subscribedTopics],
    topicLag: { ...instance.topicLag },
  };
}

function copyConsumerGroup(group: ConsumerGroup): ConsumerGroup {
  return {
    ...group,
    subscribedTopics: [...group.subscribedTopics],
    instances: group.instances.map(copyConsumerInstance),
  };
}

function copyQueueProgress(progress: QueueProgress): QueueProgress {
  return { ...progress };
}

function copySubscription(subscription: SubscriptionEntry): SubscriptionEntry {
  return { ...subscription };
}

const normalizeConsumerGroup = <T extends ConsumerGroup>(group: T): T => ({
  ...group,
  subscribedTopics: group.subscribedTopics ?? [],
  instances: group.instances ?? [],
});

function filterConsumerGroups(params?: ConsumerGroupQuery): ConsumerGroup[] {
  let result = [...consumerGroupsState];
  if (params?.clusterId) result = result.filter((group) => group.clusterId === params.clusterId);
  if (params?.search) {
    const kw = params.search.trim().toLowerCase();
    if (kw) result = result.filter((group) => group.name.toLowerCase().includes(kw));
  }
  return result;
}

export async function listConsumerGroups(params?: ConsumerGroupQuery): Promise<ConsumerGroup[]> {
  if (isMockMode()) {
    return filterConsumerGroups(params).map(copyConsumerGroup);
  }
  return (await metadataApi.listConsumerGroups(params)).map(normalizeConsumerGroup);
}

export async function listConsumerGroupPage(
  params: ConsumerGroupPageQuery = {},
): Promise<PageResult<ConsumerGroup>> {
  if (isMockMode()) {
    const page = params.page ?? 1;
    const pageSize = params.pageSize ?? 20;
    const groups = filterConsumerGroups(params);
    const from = Math.min((page - 1) * pageSize, groups.length);
    return {
      items: groups.slice(from, from + pageSize).map(copyConsumerGroup),
      total: groups.length,
      page,
      size: pageSize,
    };
  }
  return metadataApi.listConsumerGroupPage(params);
}

export async function getConsumerProgress(
  name: string,
  instanceId?: string,
): Promise<QueueProgress[]> {
  if (isMockMode()) {
    return ((mockQueueProgress[name] as unknown as QueueProgress[]) ?? []).map(copyQueueProgress);
  }
  return metadataApi.getConsumerProgress(name, instanceId);
}

export async function getConsumerGroup(
  name: string,
  instanceId?: string,
): Promise<ConsumerGroupDetail> {
  if (isMockMode()) {
    const group = mockConsumerGroups.find((item) => item.name === name);
    if (!group) throw new Error(`Consumer group not found: ${name}`);
    return copyConsumerGroup(group as unknown as ConsumerGroupDetail) as ConsumerGroupDetail;
  }
  return normalizeConsumerGroup(await metadataApi.getConsumerGroup(name, instanceId));
}

export async function getConsumerGroupSettings(
  name: string,
  instanceId: string,
): Promise<ConsumerGroupSettings> {
  if (isMockMode()) return { groupName: name, retryQueueNums: 1, retryMaxTimes: 16 };
  return metadataApi.getConsumerGroupSettings(name, instanceId);
}

export async function updateConsumerGroupSettings(
  data: Omit<ConsumerGroupSettings, 'groupName'> & { instanceId: string; name: string },
) {
  if (isMockMode())
    return {
      groupName: data.name,
      retryQueueNums: data.retryQueueNums,
      retryMaxTimes: data.retryMaxTimes,
    };
  return metadataApi.updateConsumerGroupSettings(data);
}

export async function getConsumerSubscriptions(
  name: string,
  instanceId?: string,
): Promise<SubscriptionEntry[]> {
  if (isMockMode()) {
    return ((mockSubscriptions[name] as unknown as SubscriptionEntry[]) ?? []).map(
      copySubscription,
    );
  }
  return metadataApi.getConsumerSubscriptions(name, instanceId);
}

export async function getConsumerStack(
  name: string,
  clientId: string,
  instanceId?: string,
): Promise<ConsumerStackTrace> {
  if (isMockMode()) {
    return {
      groupName: name,
      clientId,
      capturedAt: new Date().toISOString(),
      threadCount: 0,
      threads: [],
    };
  }
  return metadataApi.getConsumerStack(name, clientId, instanceId);
}

export async function createConsumerGroup(data: Partial<ConsumerGroup>): Promise<ConsumerGroup> {
  if (isMockMode()) {
    const now = new Date().toISOString();
    const group = {
      name: data.name ?? '',
      namespace: data.namespace ?? 'default',
      clusterId: data.clusterId ?? '',
      subscriptionMode: data.subscriptionMode ?? 'Push',
      consumeType: data.consumeType ?? 'CLUSTERING',
      onlineInstances: 0,
      totalLag: 0,
      subscribedTopics: data.subscribedTopics ?? [],
      subscriptionDataType: data.subscriptionDataType ?? 'NORMAL',
      retryMaxTimes: data.retryMaxTimes ?? 16,
      gmtCreate: now,
      gmtModified: now,
      delaySeconds: 0,
      instances: [],
    } as ConsumerGroup;
    mockConsumerGroups.unshift(group as never);
    return copyConsumerGroup(group);
  }
  return metadataApi.createConsumerGroup(data);
}

export async function deleteConsumerGroup(name: string, instanceId?: string): Promise<void> {
  if (isMockMode()) {
    const idx = consumerGroupsState.findIndex((group) => group.name === name);
    if (idx >= 0) consumerGroupsState.splice(idx, 1);
    return;
  }
  return metadataApi.deleteConsumerGroup(name, instanceId);
}

export async function resetConsumerOffset(data: ResetConsumerOffsetRequest): Promise<void> {
  if (isMockMode()) return;
  return metadataApi.resetConsumerOffset(data);
}

export interface BatchDeleteConsumerGroupsResult {
  deleted: string[];
  failed: string[];
}

// Batch delete: attempt every selected group and report partial failures so a single
// failing group cannot silently abort the whole batch.
export async function batchDeleteConsumerGroups(
  names: string[],
  instanceId?: string,
): Promise<BatchDeleteConsumerGroupsResult> {
  const result: BatchDeleteConsumerGroupsResult = { deleted: [], failed: [] };
  for (const name of names) {
    try {
      await deleteConsumerGroup(name, instanceId);
      result.deleted.push(name);
    } catch {
      result.failed.push(name);
    }
  }
  return result;
}
