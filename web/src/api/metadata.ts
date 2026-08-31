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

export interface TopicExportQuery extends TopicQuery {
  names?: string[];
}

export interface BrokerRoute {
  brokerName: string;
  brokerAddr: string;
  masterAddr?: string;
  brokerAddrs?: Record<string, string>;
  brokerIds?: number[];
  replicaCount?: number;
  writeQueues: number;
  readQueues: number;
  perm: string;
  permCode?: number;
  readable?: boolean;
  writable?: boolean;
  topicSysFlag?: number;
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

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface ImportTopicsRequest {
  instanceId: string;
  topics: Partial<Topic>[];
}

export interface ImportTopicsFailure {
  index: number;
  name?: string;
  message: string;
}

export interface ImportTopicsResult {
  imported: number;
  failed: number;
  topics: Topic[];
  failures: ImportTopicsFailure[];
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

export interface ConsumerGroupSettings {
  groupName: string;
  retryQueueNums: number;
  retryMaxTimes: number;
}

export interface QueueProgress {
  topic: string;
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

export interface ConsumerGroupPageQuery extends ConsumerGroupQuery {
  page?: number;
  pageSize?: number;
}

export interface ConsumerGroupExportQuery extends ConsumerGroupQuery {
  names?: string[];
  subscriptionMode?: string;
}

export interface ImportConsumerGroupsRequest {
  instanceId: string;
  groups: Partial<ConsumerGroup>[];
}

export interface ImportConsumerGroupsFailure {
  index: number;
  name?: string;
  message: string;
}

export interface ImportConsumerGroupsResult {
  imported: number;
  failed: number;
  groups: ConsumerGroup[];
  failures: ImportConsumerGroupsFailure[];
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

export async function exportTopics(params?: TopicExportQuery) {
  const res = await client.get<{ data: string }>('/topics/export', {
    params: { ...params, names: params?.names?.join(',') },
  });
  return res.data.data;
}

export async function importTopics(data: ImportTopicsRequest) {
  const res = await client.post<{ data: ImportTopicsResult }>('/topics/import', data);
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

export async function listConsumerGroupPage(params?: ConsumerGroupPageQuery) {
  const res = await client.get<{ data: PageResult<ConsumerGroup> }>('/groups/page', { params });
  return res.data.data;
}

export async function getConsumerGroup(name: string, instanceId?: string) {
  const res = await client.get<{ data: ConsumerGroupDetail }>(
    `/groups/${encodeURIComponent(name)}`,
    { params: instanceId ? { instanceId } : {} },
  );
  return res.data.data;
}

export async function refreshConsumerGroup(name: string, instanceId?: string) {
  const res = await client.get<{ data: ConsumerGroup | null }>(
    `/groups/${encodeURIComponent(name)}/refresh`,
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

export async function getConsumerGroupSettings(name: string, instanceId: string) {
  const res = await client.get<{ data: ConsumerGroupSettings }>(
    `/groups/${encodeURIComponent(name)}/settings`,
    { params: { instanceId } },
  );
  return res.data.data;
}

export async function updateConsumerGroupSettings(
  data: Omit<ConsumerGroupSettings, 'groupName'> & { instanceId: string; name: string },
) {
  const res = await client.post<{ data: ConsumerGroupSettings }>('/groups/settings', data);
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

export interface ResetConsumerOffsetQueuePreview {
  topic: string;
  broker: string;
  queueId: number;
  minOffset: number;
  maxOffset: number;
  brokerOffset: number;
  consumerOffset: number;
  targetOffset: number;
  currentLag: number;
  projectedLag: number;
  offsetDelta: number;
  riskLevel: 'INFO' | 'WARNING' | 'ERROR' | string;
  message: string;
}

export interface ResetConsumerOffsetPreview {
  instanceId?: string;
  groupName: string;
  topic: string;
  timestamp: number;
  complete: boolean;
  allowReset: boolean;
  queueCount: number;
  warningCount: number;
  rewindQueueCount: number;
  fastForwardQueueCount: number;
  currentTotalLag: number;
  projectedTotalLag: number;
  totalOffsetDelta: number;
  warnings: string[];
  queues: ResetConsumerOffsetQueuePreview[];
}

export async function previewConsumerOffsetReset(data: ResetConsumerOffsetRequest) {
  const res = await client.post<{ data: ResetConsumerOffsetPreview }>(
    '/groups/reset-offset/preview',
    data,
  );
  return res.data.data;
}

export async function resetConsumerOffset(data: ResetConsumerOffsetRequest) {
  await client.post('/groups/reset-offset', data);
}

export async function importConsumerGroups(data: ImportConsumerGroupsRequest) {
  const res = await client.post<{ data: ImportConsumerGroupsResult }>('/groups/import', data);
  return res.data.data;
}

export async function exportConsumerGroups(params?: ConsumerGroupExportQuery) {
  const res = await client.get<{ data: string }>('/groups/export', {
    params: { ...params, names: params?.names?.join(',') },
  });
  return res.data.data;
}
