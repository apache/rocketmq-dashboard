import { USE_MOCK } from '../config';
import * as metadataApi from '../api/metadata';
import type {
  ConsumerGroup,
  ConsumerGroupQuery,
  ConsumerGroupDetail,
  QueueProgress,
  ResetConsumerOffsetRequest,
  SubscriptionEntry,
} from '../api/metadata';
import { mockConsumerGroups, mockQueueProgress, mockSubscriptions } from '../mock/consumers';

const consumerGroupsState = mockConsumerGroups as unknown as ConsumerGroup[];

export async function listConsumerGroups(params?: ConsumerGroupQuery): Promise<ConsumerGroup[]> {
  if (USE_MOCK) {
    let result = [...consumerGroupsState];
    if (params?.clusterId) result = result.filter((group) => group.clusterId === params.clusterId);
    if (params?.search) {
      const kw = params.search.toLowerCase();
      result = result.filter((g) => g.name.toLowerCase().includes(kw));
    }
    return result;
  }
  return metadataApi.listConsumerGroups(params);
}

export async function getConsumerProgress(name: string): Promise<QueueProgress[]> {
  if (USE_MOCK) return (mockQueueProgress[name] as unknown as QueueProgress[]) ?? [];
  return metadataApi.getConsumerProgress(name);
}

export async function getConsumerGroup(name: string): Promise<ConsumerGroupDetail> {
  if (USE_MOCK) {
    const group = mockConsumerGroups.find((item) => item.name === name);
    if (!group) throw new Error(`Consumer group not found: ${name}`);
    return group as unknown as ConsumerGroupDetail;
  }
  return metadataApi.getConsumerGroup(name);
}

export async function getConsumerSubscriptions(name: string): Promise<SubscriptionEntry[]> {
  if (USE_MOCK) return (mockSubscriptions[name] as unknown as SubscriptionEntry[]) ?? [];
  return metadataApi.getConsumerSubscriptions(name);
}

export async function createConsumerGroup(data: Partial<ConsumerGroup>): Promise<ConsumerGroup> {
  if (USE_MOCK) {
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
      createdAt: now,
      updatedAt: now,
      delaySeconds: 0,
      instances: [],
    };
    mockConsumerGroups.unshift(group as never);
    return group as ConsumerGroup;
  }
  return metadataApi.createConsumerGroup(data);
}

export async function deleteConsumerGroup(name: string): Promise<void> {
  if (USE_MOCK) {
    const idx = consumerGroupsState.findIndex((group) => group.name === name);
    if (idx >= 0) consumerGroupsState.splice(idx, 1);
    return;
  }
  return metadataApi.deleteConsumerGroup(name);
}

export async function resetConsumerOffset(data: ResetConsumerOffsetRequest): Promise<void> {
  if (USE_MOCK) return;
  return metadataApi.resetConsumerOffset(data);
}

// Batch delete: loop through single delete calls
export async function batchDeleteConsumerGroups(names: string[]): Promise<void> {
  for (const name of names) {
    await deleteConsumerGroup(name);
  }
}
