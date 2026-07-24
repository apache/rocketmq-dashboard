import client from './client';

// Matches mock/messages.ts
export interface MessageRecord {
  msgId: string;
  topic: string;
  tag: string;
  key: string;
  body: string;
  storeTime: number | string;
  bornHost: string;
  storeHost: string;
  properties: Record<string, string>;
  size: number;
}

export interface TraceNode {
  title: string;
  timestamp: number | string;
  costTime: number;
  status: 'error' | 'wait' | 'process' | 'finish';
  description: string;
}

export interface ConsumerStatus {
  group: string;
  deliveryStatus: string;
  consumeTime: number | string;
  retryCount: number;
}

export interface TraceRecord {
  nodes: TraceNode[];
  consumerStatus: ConsumerStatus[];
}

export interface MessageQuery {
  topic?: string;
  key?: string;
  msgId?: string;
  startTime?: number;
  endTime?: number;
}

const toStoreTimestamp = (storeTime: MessageRecord['storeTime']): number => {
  if (typeof storeTime === 'number') return storeTime;

  const parsed = Date.parse(storeTime);
  return Number.isNaN(parsed) ? 0 : parsed;
};

export const sortMessagesByStoreTimeDesc = (messages: MessageRecord[]): MessageRecord[] =>
  [...messages].sort((a, b) => toStoreTimestamp(b.storeTime) - toStoreTimestamp(a.storeTime));

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
export async function queryMessages(params: MessageQuery) {
  const res = await client.get<{ data: MessageRecord[] }>('/messages', { params });
  return sortMessagesByStoreTimeDesc(res.data.data);
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
