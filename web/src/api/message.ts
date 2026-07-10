/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */

import client from './client';

// ─── Types ──────────────────────────────────────────────────────
export interface MessageRecord {
  msgId: string;
  topic: string;
  tag: string;
  key: string;
  body: string;
  storeTime: string;
  bornHost: string;
  storeHost: string;
  properties: Record<string, string>;
  size: number;
}

export interface TraceNode {
  title: string;
  timestamp: string;
  costTime: number;
  host: string;
  status: string;
  detail?: string;
}

export interface ConsumerStatus {
  group: string;
  clientAddr: string;
  status: string;
  consumeTps: number;
  lastConsumedTime: string;
}

export interface TraceRecord {
  nodes: TraceNode[];
  consumerStatus: ConsumerStatus[];
}

export interface DLQGroup {
  groupName: string;
  dlqTopic: string;
  messageCount: number;
  lastEnqueueTime: string;
  retryCount: number;
  status: string;
}

// ─── Message API ────────────────────────────────────────────────
// Backend: MessageController at /message
// GET  /message/viewMessage.query?topic=xxx&msgId=xxx → view message
// POST /message/queryMessagePageByTopic.query         → paginated query
// GET  /message/queryMessageByTopicAndKey.query?topic=xxx&key=xxx → search by key
// GET  /message/queryMessageByTopic.query?topic=xxx&begin=xxx&end=xxx → search by topic
// POST /message/consumeMessageDirectly.do             → consume directly

export async function viewMessage(topic: string, msgId: string) {
  const res = await client.get('/message/viewMessage.query', {
    params: { topic, msgId },
  });
  return res.data;
}

export async function queryMessagePageByTopic(data: {
  topic: string;
  begin: number;
  end: number;
  pageNum?: number;
  pageSize?: number;
}) {
  const res = await client.post('/message/queryMessagePageByTopic.query', data);
  return res.data;
}

export async function queryMessageByTopicAndKey(topic: string, key: string) {
  const res = await client.get('/message/queryMessageByTopicAndKey.query', {
    params: { topic, key },
  });
  return res.data;
}

export async function queryMessageByTopic(topic: string, begin: number, end: number) {
  const res = await client.get('/message/queryMessageByTopic.query', {
    params: { topic, begin, end },
  });
  return res.data;
}

// Legacy query (kept for mock compatibility)
export async function queryMessages(params: {
  mode: string;
  topic?: string;
  key?: string;
  msgId?: string;
  startTime?: string;
  endTime?: string;
}) {
  if (params.msgId && params.topic) {
    return viewMessage(params.topic, params.msgId);
  }
  if (params.key && params.topic) {
    return queryMessageByTopicAndKey(params.topic, params.key);
  }
  if (params.topic) {
    const begin = params.startTime ? new Date(params.startTime).getTime() : Date.now() - 86400000;
    const end = params.endTime ? new Date(params.endTime).getTime() : Date.now();
    return queryMessageByTopic(params.topic, begin, end);
  }
  return [];
}

export async function getMessageTrace(msgId: string) {
  // Backend: MessageTraceController at /messageTrace
  // GET /messageTrace/viewMessageTraceDetail.query?msgId=xxx
  const res = await client.get('/messageTrace/viewMessageTraceDetail.query', {
    params: { msgId },
  });
  return res.data;
}

// ─── DLQ API ────────────────────────────────────────────────────
// Backend: DlqMessageController at /dlqMessage
// POST /dlqMessage/queryDlqMessageByConsumerGroup.query → list DLQ messages
// POST /dlqMessage/batchResendDlqMessage.do             → resend DLQ messages

export async function listDLQGroups() {
  // DLQ groups are derived from consumer groups with DLQ topics
  // No direct backend endpoint; use consumer group list and filter
  const res = await client.get('/consumer/groupList.query');
  return res.data;
}

export async function queryDlqMessages(data: {
  consumerGroup: string;
  pageNum?: number;
  pageSize?: number;
}) {
  const res = await client.post('/dlqMessage/queryDlqMessageByConsumerGroup.query', data);
  return res.data;
}

export async function resendDLQ(data: {
  groupName: string;
  startTime: string;
  endTime: string;
  targetTopic?: string;
}) {
  await client.post('/dlqMessage/batchResendDlqMessage.do', data);
}
