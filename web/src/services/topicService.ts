import { USE_MOCK } from '../config';
import * as metadataApi from '../api/metadata';
import type {
  Topic,
  TopicQuery,
  BrokerRoute,
  ConsumerGroupInfo,
  SendTopicMessageRequest,
  SendTopicMessageResult,
} from '../api/metadata';
import { topics as mockTopics, topicRoutes, topicConsumers } from '../mock/topics';

export async function listTopics(params?: TopicQuery): Promise<Topic[]> {
  if (USE_MOCK) {
    let result = [...mockTopics];
    if (params?.search) {
      const kw = params.search.toLowerCase();
      result = result.filter((t) => t.name.toLowerCase().includes(kw));
    }
    if (params?.type) result = result.filter((t) => t.type === params.type);
    if (params?.clusterId) result = result.filter((t) => t.clusterId === params.clusterId);
    return result as unknown as Topic[];
  }
  return metadataApi.listTopics(params);
}

export async function createTopic(data: Partial<Topic>): Promise<Topic> {
  if (USE_MOCK) {
    const topic = {
      ...data,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      messageCount: 0,
      tps: 0,
      consumerGroupCount: 0,
    } as unknown as Topic;
    mockTopics.unshift(topic as never);
    return topic;
  }
  return metadataApi.createTopic(data);
}

export async function updateTopic(data: Partial<Topic>): Promise<Topic> {
  if (USE_MOCK) {
    const idx = mockTopics.findIndex((t) => t.name === data.name);
    if (idx < 0) throw new Error(`Topic not found: ${data.name}`);
    Object.assign(mockTopics[idx], data, { updatedAt: new Date().toISOString() });
    return mockTopics[idx] as unknown as Topic;
  }
  return metadataApi.updateTopic(data);
}

export async function deleteTopic(name: string): Promise<void> {
  if (USE_MOCK) {
    const idx = mockTopics.findIndex((t) => t.name === name);
    if (idx >= 0) mockTopics.splice(idx, 1);
    return;
  }
  return metadataApi.deleteTopic(name);
}

// Batch delete: loop through single delete calls
export async function batchDeleteTopics(names: string[]): Promise<void> {
  for (const name of names) {
    await deleteTopic(name);
  }
}

export async function getTopicRoutes(name: string): Promise<BrokerRoute[]> {
  if (USE_MOCK) return (topicRoutes[name] as unknown as BrokerRoute[]) ?? [];
  return metadataApi.getTopicRoutes(name);
}

export async function getTopicConsumers(name: string): Promise<ConsumerGroupInfo[]> {
  if (USE_MOCK) return (topicConsumers[name] as unknown as ConsumerGroupInfo[]) ?? [];
  return metadataApi.getTopicConsumers(name);
}

export async function sendTopicMessage(
  data: SendTopicMessageRequest,
): Promise<SendTopicMessageResult> {
  if (USE_MOCK) {
    // Simulate a short delay
    await new Promise((r) => setTimeout(r, 400));
    return {
      msgId: `7F${Math.random().toString(16).slice(2, 18).toUpperCase()}`,
      sendTime: new Date().toISOString(),
      offsetMsgId: `7F${Math.random().toString(16).slice(2, 18).toUpperCase()}-0:0:0:0`,
    };
  }
  return metadataApi.sendTopicMessage(data);
}
