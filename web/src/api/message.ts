import client from './client';

// Matches mock/messages.ts
export interface MessageRecord {
  msgId: string;
  topic: string;
  tag: string | null;
  key: string | null;
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
  instanceId?: string;
  topic?: string;
  tag?: string;
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
  lastEnqueueTime?: string | null;
  retryCount: number;
  status: string;
  statsAvailable?: boolean;
}

export interface DLQGroupPage {
  items: DLQGroup[];
  total: number;
  page: number;
  size: number;
}

export interface DLQResendResult {
  matched: number;
  resent: number;
  failed: number;
  outcome: 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'NO_MESSAGES';
  scanIncomplete?: boolean;
  failedQueueCount?: number;
}

// ─── Messages ───────────────────────────────────────────────────
export async function queryMessages(params: MessageQuery) {
  const res = await client.get<{ data: MessageRecord[] }>('/messages', { params });
  return sortMessagesByStoreTimeDesc(res.data.data);
}

export async function getMessageTrace(msgId: string, instanceId?: string, topic?: string) {
  const params: Record<string, string> = {};
  if (instanceId !== undefined) params.instanceId = instanceId;
  if (topic !== undefined) params.topic = topic;
  const res = await client.get<{ data: TraceRecord }>(
    `/messages/${encodeURIComponent(msgId)}/trace`,
    { params },
  );
  return res.data.data;
}

// ─── DLQ ────────────────────────────────────────────────────────
export async function listDLQGroups(instanceId: string, search?: string, page = 1, pageSize = 20) {
  const res = await client.get<{ data: DLQGroupPage }>('/dlq', {
    params: { instanceId, search, page, pageSize },
  });
  return res.data.data;
}

export async function resendDLQ(data: {
  instanceId: string;
  groupName: string;
  startTime: number;
  endTime: number;
  targetTopic?: string;
}): Promise<DLQResendResult> {
  const res = await client.post<{ data: DLQResendResult }>('/dlq/resend', data);
  return res.data.data;
}

export async function exportDLQMessages(params: {
  instanceId: string;
  groupName: string;
  startTime?: number;
  endTime?: number;
  maxCount?: number;
}): Promise<Blob> {
  const res = await client.get<Blob>('/dlq/export', { params, responseType: 'blob' });
  return res.data;
}
