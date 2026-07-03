import { USE_MOCK } from '../config';
import * as metadataApi from '../api/metadata';
import type { ConsumerGroup, QueueProgress, SubscriptionEntry } from '../api/metadata';
import { mockConsumerGroups, mockQueueProgress, mockSubscriptions } from '../mock/consumers';

export async function listConsumerGroups(params?: { keyword?: string }): Promise<ConsumerGroup[]> {
  if (USE_MOCK) {
    let result = [...mockConsumerGroups];
    if (params?.keyword) {
      const kw = params.keyword.toLowerCase();
      result = result.filter((g) => g.name.toLowerCase().includes(kw));
    }
    return result as unknown as ConsumerGroup[];
  }
  return metadataApi.listConsumerGroups(params);
}

export async function getConsumerProgress(name: string): Promise<QueueProgress[]> {
  if (USE_MOCK) return (mockQueueProgress[name] as unknown as QueueProgress[]) ?? [];
  return metadataApi.getConsumerProgress(name);
}

export async function getConsumerSubscriptions(name: string): Promise<SubscriptionEntry[]> {
  if (USE_MOCK) return (mockSubscriptions[name] as unknown as SubscriptionEntry[]) ?? [];
  return metadataApi.getConsumerSubscriptions(name);
}

export async function createConsumerGroup(data: Partial<ConsumerGroup>): Promise<void> {
  if (USE_MOCK) return;
  return metadataApi.createConsumerGroup(data);
}

export async function deleteConsumerGroup(name: string): Promise<void> {
  if (USE_MOCK) {
    const idx = mockConsumerGroups.findIndex((g) => g.name === name);
    if (idx >= 0) mockConsumerGroups.splice(idx, 1);
    return;
  }
  return metadataApi.deleteConsumerGroup(name);
}

// Batch delete: loop through single delete calls
export async function batchDeleteConsumerGroups(names: string[]): Promise<void> {
  for (const name of names) {
    await deleteConsumerGroup(name);
  }
}
