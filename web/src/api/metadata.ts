/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */

import client from './client';

// ─── Topic (matches mock/topics.ts) ────────────────────────────
export interface Topic {
  name: string;
  namespace: string;
  type: string;
  clusterId: string;
  writeQueues: number;
  readQueues: number;
  perm: string;
  messageCount: number;
  tps: number;
  consumerGroupCount: number;
  remark: string;
  createdAt: string;
  updatedAt: string;
}

export interface TopicQuery {
  clusterId?: string;
  type?: string;
  search?: string;
}

export interface BrokerRoute {
  brokerName: string;
  brokerAddr: string;
  writeQueues: number;
  readQueues: number;
  perm: string;
}

export interface ConsumerGroupInfo {
  group: string;
  consumeType: string;
  messageModel: string;
  consumeTps: number;
  diffTotal: number;
}

// ─── Consumer Group (matches mock/consumers.ts) ─────────────────
export interface ConsumerGroup {
  name: string;
  namespace: string;
  clusterId: string;
  subscriptionMode: string;
  consumeType: string;
  onlineInstances: number;
  totalLag: number;
  subscribedTopics: string[];
  subscriptionDataType: string;
  deliveryOrderType?: string;
  retryMaxTimes: number;
  createdAt: string;
  updatedAt: string;
  delaySeconds: number;
  instances: ConsumerInstance[];
}

export interface ConsumerInstance {
  clientId: string;
  protocol: string;
  address: string;
  subscribedTopics: string[];
  lastHeartbeat: string;
  topicLag: Record<string, number>;
}

export interface ConsumerGroupDetail extends ConsumerGroup {
  instances: ConsumerInstance[];
}

export interface QueueProgress {
  broker: string;
  queueId: number;
  brokerOffset: number;
  consumerOffset: number;
  diffTotal: number;
}

export interface SubscriptionEntry {
  topic: string;
  expression: string;
  type: string;
  filterMode: string;
  consistency: string;
}

export interface ConsumerGroupQuery {
  clusterId?: string;
  search?: string;
}

export interface ResetConsumerOffsetRequest {
  name: string;
  timestamp: number;
  topic?: string;
}

// ─── Topic API ──────────────────────────────────────────────────
export async function listTopics(params?: TopicQuery) {
  const res = await client.get<{ data: Topic[] }>('/topics', { params });
  return res.data.data;
}

export async function createTopic(data: Partial<Topic>) {
  const res = await client.post<{ data: Topic }>('/topics/create', data);
  return res.data.data;
}

export async function updateTopic(data: Partial<Topic>) {
  const res = await client.post<{ data: Topic }>('/topics/update', data);
  return res.data.data;
}

export async function deleteTopic(name: string) {
  await client.post('/topics/delete', { name });
}

export async function getTopicRoutes(name: string) {
  const res = await client.get<{ data: BrokerRoute[] }>(`/topics/${name}/routes`);
  return res.data.data;
}

export async function getTopicConsumers(name: string) {
  const res = await client.get<{ data: ConsumerGroupInfo[] }>(`/topics/${name}/consumers`);
  return res.data.data;
}

export interface SendTopicMessageRequest {
  topic: string;
  tag?: string;
  key?: string;
  body: string;
  properties?: Record<string, string>;
}

export interface SendTopicMessageResult {
  msgId: string;
  sendTime: string;
  offsetMsgId: string;
}

export async function sendTopicMessage(data: SendTopicMessageRequest) {
  const res = await client.post<{ data: SendTopicMessageResult }>('/topics/send', data);
  return res.data.data;
}

// ─── Consumer Group API ─────────────────────────────────────────
export async function listConsumerGroups(params?: ConsumerGroupQuery) {
  const res = await client.get<{ data: ConsumerGroup[] }>('/groups', { params });
  return res.data.data;
}

export async function getConsumerGroup(name: string) {
  const res = await client.get<{ data: ConsumerGroupDetail }>(`/groups/${name}`);
  return res.data.data;
}

export async function getConsumerProgress(name: string) {
  const res = await client.get<{ data: QueueProgress[] }>(`/groups/${name}/progress`);
  return res.data.data;
}

export async function getConsumerSubscriptions(name: string) {
  const res = await client.get<{ data: SubscriptionEntry[] }>(`/groups/${name}/subscriptions`);
  return res.data.data;
}

export async function createConsumerGroup(data: Partial<ConsumerGroup>) {
  const res = await client.post<{ data: ConsumerGroup }>('/groups/create', data);
  return res.data.data;
}

export async function deleteConsumerGroup(name: string) {
  await client.post('/groups/delete', { name });
}

export interface ResetConsumerOffsetRequest {
  name: string;
  timestamp: number;
  topic?: string;
}

export async function resetConsumerOffset(data: ResetConsumerOffsetRequest) {
  await client.post('/groups/reset-offset', data);
}

export async function importConsumerGroups(data: string) {
  await client.post('/groups/import', { data });
}

export async function exportConsumerGroups(names?: string[]) {
  const res = await client.get<{ data: string }>('/groups/export', {
    params: { names: names?.join(',') },
  });
  return res.data.data;
}
