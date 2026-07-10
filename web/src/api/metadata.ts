/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */

import client from './client';

// ─── Topic Types ───────────────────────────────────────────────
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

// ─── Consumer Group Types ──────────────────────────────────────
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
}

export interface ConsumerInstance {
  clientId: string;
  clientAddr: string;
  language: string;
  version: string;
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

// ─── Topic API ──────────────────────────────────────────────────
// Backend: TopicController at /topic
// GET  /topic/list.query                    → list topics
// GET  /topic/route.query?topic=xxx         → topic route info
// GET  /topic/queryConsumerByTopic.query?topic=xxx → consumer groups by topic
// POST /topic/createOrUpdate.do             → create or update topic
// POST /topic/deleteTopic.do                → delete topic
// POST /topic/sendTopicMessage.do           → send test message

export async function listTopics(params?: { keyword?: string; type?: string; namespace?: string }) {
  const res = await client.get('/topic/list.query', { params });
  return res.data;
}

export async function createTopic(data: Partial<Topic>) {
  const res = await client.post('/topic/createOrUpdate.do', data);
  return res.data;
}

export async function updateTopic(data: Partial<Topic>) {
  const res = await client.post('/topic/createOrUpdate.do', data);
  return res.data;
}

export async function deleteTopic(name: string) {
  await client.post('/topic/deleteTopic.do', null, { params: { topic: name } });
}

export async function getTopicRoutes(name: string) {
  const res = await client.get('/topic/route.query', { params: { topic: name } });
  return res.data;
}

export async function getTopicConsumers(name: string) {
  const res = await client.get('/topic/queryConsumerByTopic.query', { params: { topic: name } });
  return res.data;
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
  const res = await client.post('/topic/sendTopicMessage.do', data);
  return res.data;
}

// ─── Consumer Group API ─────────────────────────────────────────
// Backend: ConsumerController at /consumer
// GET  /consumer/groupList.query             → list consumer groups
// GET  /consumer/group.query?consumerGroup=xxx → group detail
// GET  /consumer/queryTopicByConsumer.query?consumerGroup=xxx → progress
// GET  /consumer/consumerConnection.query?consumerGroup=xxx → connections
// POST /consumer/createOrUpdate.do           → create or update group
// POST /consumer/deleteSubGroup.do           → delete group
// POST /consumer/resetOffset.do              → reset offset

export async function listConsumerGroups(params?: { keyword?: string }) {
  const res = await client.get('/consumer/groupList.query', { params });
  return res.data;
}

export async function getConsumerGroup(name: string) {
  const res = await client.get('/consumer/group.query', { params: { consumerGroup: name } });
  return res.data;
}

export async function getConsumerProgress(name: string) {
  const res = await client.get('/consumer/queryTopicByConsumer.query', {
    params: { consumerGroup: name },
  });
  return res.data;
}

export async function getConsumerSubscriptions(name: string) {
  const res = await client.get('/consumer/consumerConnection.query', {
    params: { consumerGroup: name },
  });
  return res.data;
}

export async function createConsumerGroup(data: Partial<ConsumerGroup>) {
  await client.post('/consumer/createOrUpdate.do', data);
}

export async function deleteConsumerGroup(name: string) {
  await client.post('/consumer/deleteSubGroup.do', { consumerGroup: name });
}

export async function resetConsumerOffset(data: Record<string, unknown>) {
  await client.post('/consumer/resetOffset.do', data);
}

export async function importConsumerGroups(data: string) {
  await client.post('/consumer/createOrUpdate.do', { data });
}

export async function exportConsumerGroups(names?: string[]) {
  const res = await client.get('/consumer/groupList.query', {
    params: { names: names?.join(',') },
  });
  return res.data;
}
