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
  instanceId?: string;
  writeQueues: number;
  readQueues: number;
  perm: string;
  messageCount: number;
  tps: number;
  consumerGroupCount: number;
  remark: string;
  gmtCreate: string;
  gmtModified: string;
}

export interface TopicQuery {
  clusterId?: string;
  instanceId?: string;
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
  metricsAvailable?: boolean;
}

export interface TopicConsumerPage {
  items: ConsumerGroupInfo[];
  total: number;
  page: number;
  pageSize: number;
}

// ─── Consumer Group (matches mock/consumers.ts) ─────────────────
export interface ConsumerGroup {
  name: string;
  namespace: string;
  clusterId: string;
  instanceId?: string;
  subscriptionMode: string;
  consumeType: string;
  onlineInstances: number;
  totalLag: number;
  subscribedTopics: string[];
  subscriptionDataType: string;
  deliveryOrderType?: string;
  retryMaxTimes: number;
  gmtCreate: string;
  gmtModified: string;
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

export interface ConsumerThreadStack {
  threadName: string;
  threadId: number;
  state: string;
  blockedTime: number;
  waitedTime: number;
  stackTrace: string[];
}

export interface ConsumerStackTrace {
  groupName: string;
  clientId: string;
  capturedAt: string;
  threadCount: number;
  threads: ConsumerThreadStack[];
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
  instanceId?: string;
  clusterId?: string;
  search?: string;
}

export interface ResetConsumerOffsetRequest {
  name: string;
  timestamp: number;
  topic: string;
}

// ─── Topic API ──────────────────────────────────────────────────
export async function listTopics(params?: TopicQuery) {
  const res = await client.get<{ data: Topic[] }>('/topics', { params });
  return res.data.data;
}

export interface TopicPage {
  items: Topic[];
  total: number;
  page: number;
  size: number;
}

export async function listTopicsPage(params?: TopicQuery & { page?: number; pageSize?: number }) {
  const res = await client.get<{ data: TopicPage }>('/topics/page', { params });
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

export async function deleteTopic(name: string, instanceId?: string) {
  await client.post('/topics/delete', { name, ...(instanceId ? { instanceId } : {}) });
}

export async function getTopicRoutes(name: string, instanceId?: string) {
  const res = await client.get<{ data: BrokerRoute[] }>(
    `/topics/${encodeURIComponent(name)}/routes`,
    { params: instanceId ? { instanceId } : {} },
  );
  return res.data.data;
}

export async function getTopicConsumers(name: string, instanceId?: string) {
  const res = await client.get<{ data: ConsumerGroupInfo[] }>(
    `/topics/${encodeURIComponent(name)}/consumers`,
    { params: instanceId ? { instanceId } : {} },
  );
  return res.data.data;
}

export async function getTopicConsumerPage(
  name: string,
  instanceId: string | undefined,
  page: number,
  pageSize: number,
) {
  const res = await client.get<{ data: TopicConsumerPage }>(
    `/topics/${encodeURIComponent(name)}/consumers/page`,
    { params: { ...(instanceId ? { instanceId } : {}), page, pageSize } },
  );
  return res.data.data;
}

export interface SendTopicMessageRequest {
  topic: string;
  instanceId?: string;
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

export async function getConsumerGroup(name: string, instanceId?: string) {
  const res = await client.get<{ data: ConsumerGroupDetail }>(
    `/groups/${encodeURIComponent(name)}`,
    { params: instanceId ? { instanceId } : {} },
  );
  return res.data.data;
}

export async function getConsumerProgress(name: string, instanceId?: string) {
  const res = await client.get<{ data: QueueProgress[] }>(
    `/groups/${encodeURIComponent(name)}/progress`,
    { params: instanceId ? { instanceId } : {} },
  );
  return res.data.data;
}

export async function getConsumerSubscriptions(name: string, instanceId?: string) {
  const res = await client.get<{ data: SubscriptionEntry[] }>(
    `/groups/${encodeURIComponent(name)}/subscriptions`,
    { params: instanceId ? { instanceId } : {} },
  );
  return res.data.data;
}

export async function getConsumerStack(name: string, clientId: string, instanceId?: string) {
  const res = await client.get<{ data: ConsumerStackTrace }>(
    `/groups/${encodeURIComponent(name)}/instances/${encodeURIComponent(clientId)}/stack`,
    { params: instanceId ? { instanceId } : {} },
  );
  return res.data.data;
}

export async function createConsumerGroup(data: Partial<ConsumerGroup>) {
  const res = await client.post<{ data: ConsumerGroup }>('/groups/create', data);
  return res.data.data;
}

export async function deleteConsumerGroup(name: string, instanceId?: string) {
  await client.post('/groups/delete', { name, ...(instanceId ? { instanceId } : {}) });
}

export interface ResetConsumerOffsetRequest {
  name: string;
  instanceId?: string;
  timestamp: number;
  topic: string;
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
