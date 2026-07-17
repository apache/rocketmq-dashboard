import client from './client';

// Matches mock/messages.ts
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

// Matches mock/dlq.ts
export interface DLQGroup {
  groupName: string;
  dlqTopic: string;
  messageCount: number;
  lastEnqueueTime: string;
  retryCount: number;
  status: string;
}

// ─── Messages ───────────────────────────────────────────────────
export async function queryMessages(params: {
  mode: string;
  topic?: string;
  key?: string;
  msgId?: string;
  startTime?: string;
  endTime?: string;
}) {
  const res = await client.get<{ data: MessageRecord[] }>('/messages', { params });
  return res.data.data;
}

export async function getMessageTrace(msgId: string) {
  const res = await client.get<{ data: TraceRecord }>(`/messages/${msgId}/trace`);
  return res.data.data;
}

// ─── DLQ ────────────────────────────────────────────────────────
export async function listDLQGroups() {
  const res = await client.get<{ data: DLQGroup[] }>('/dlq');
  return res.data.data;
}

export async function resendDLQ(data: {
  groupName: string;
  startTime: number;
  endTime: number;
  targetTopic?: string;
}) {
  await client.post('/dlq/resend', data);
}
