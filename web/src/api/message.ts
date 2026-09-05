import client from './client';

// Matches mock/messages.ts
export interface MessageRecord {
  msgId: string;
  topic: string;
  tag: string | null;
  key: string | null;
  brokerName: string | null;
  queueId: number | null;
  queueOffset: number | null;
  body: string;
  storeTime: number | string;
  bornHost: string;
  storeHost: string;
  properties: Record<string, string>;
  propertiesTruncated?: boolean;
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

export interface MessageQueryPage {
  items: MessageRecord[];
  total: number;
  page: number;
  size: number;
  resultMayBeTruncated: boolean;
}

export interface DirectConsumeMessageRequest {
  instanceId: string;
  topic: string;
  msgId: string;
  consumerGroup: string;
  clientId: string;
}

export interface DirectConsumeMessageResult {
  consumeResult: string;
  remark?: string;
  spentTimeMillis: number;
  order: boolean;
  autoCommit: boolean;
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

export interface DLQMessage {
  msgId: string;
  topic: string;
  queueId: number;
  offset: number;
  storeTime: number;
  keys: string | null;
  body: string | null;
  bodyBase64: string | null;
}

export interface DLQMessagePage {
  items: DLQMessage[];
  total: number;
  page: number;
  size: number;
}

// ─── Messages ───────────────────────────────────────────────────
export async function queryMessages(params: MessageQuery) {
  const res = await client.get<{ data: MessageRecord[] }>('/messages', { params });
  return sortMessagesByStoreTimeDesc(res.data.data);
}

export async function queryMessagePage(
  params: MessageQuery & { page?: number; pageSize?: number },
) {
  const res = await client.get<{ data: MessageQueryPage }>('/messages/page', { params });
  return { ...res.data.data, items: sortMessagesByStoreTimeDesc(res.data.data.items) };
}

// The backend reports business statuses ("finish" | "failed") on trace nodes,
// while the Ant Design Steps component only understands
// 'error' | 'wait' | 'process' | 'finish'. Map at the API boundary so the UI
// never sees a status it cannot render.
const mapTraceNodeStatus = (status: unknown): TraceNode['status'] => {
  if (status === 'failed') return 'error';
  if (status === 'finish' || status === 'process' || status === 'error' || status === 'wait') {
    return status;
  }
  return 'wait';
};

export async function getMessageTrace(
  msgId: string,
  instanceId?: string,
  topic?: string,
  traceTopic?: string,
) {
  const params: Record<string, string> = {};
  if (instanceId !== undefined) params.instanceId = instanceId;
  if (topic !== undefined) params.topic = topic;
  if (traceTopic !== undefined && traceTopic.trim()) params.traceTopic = traceTopic.trim();
  const res = await client.get<{ data: TraceRecord }>(
    `/messages/${encodeURIComponent(msgId)}/trace`,
    { params },
  );
  const trace = res.data.data;
  return {
    ...trace,
    nodes: (trace.nodes ?? []).map((node) => ({
      ...node,
      status: mapTraceNodeStatus(node.status),
    })),
  };
}

export async function consumeMessageDirectly(data: DirectConsumeMessageRequest) {
  const res = await client.post<{ data: DirectConsumeMessageResult }>(
    '/messages/direct-consume',
    data,
  );
  return res.data.data;
}

export async function getMessageTraceByKey(
  key: string,
  instanceId?: string,
  topic?: string,
  traceTopic?: string,
): Promise<TraceRecord | null> {
  const params: Record<string, string> = { key };
  if (instanceId !== undefined) params.instanceId = instanceId;
  if (topic !== undefined) params.topic = topic;
  if (traceTopic !== undefined && traceTopic.trim()) params.traceTopic = traceTopic.trim();
  const res = await client.get<{ data: TraceRecord }>('/messages/trace-by-key', { params });
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

export interface DLQExportMeta {
  truncated: boolean;
  failedQueueCount: number;
  limit: number;
}

export async function exportDLQMessages(params: {
  instanceId: string;
  groupName: string;
  startTime?: number;
  endTime?: number;
  maxCount?: number;
}): Promise<{ blob: Blob; meta: DLQExportMeta }> {
  const res = await client.get<Blob>('/dlq/export', { params, responseType: 'blob' });
  const header = (name: string): string => String(res.headers[name] ?? '');
  return {
    blob: res.data,
    meta: {
      truncated: header('x-dlq-export-truncated') === 'true',
      failedQueueCount: Number.parseInt(header('x-dlq-export-failedqueues'), 10) || 0,
      limit: Number.parseInt(header('x-dlq-export-limit'), 10) || 0,
    },
  };
}

// ─── Queue Browser ─────────────────────────────────────────────────
export interface QueueOffset {
  brokerName: string;
  queueId: number;
  minOffset: number;
  maxOffset: number;
}

export async function getQueueOffsets(params: { instanceId: string; topic: string }) {
  const res = await client.get<{ data: QueueOffset[] }>('/messages/queues', { params });
  return res.data.data;
}

export async function pullMessageAtOffset(params: {
  instanceId: string;
  topic: string;
  brokerName: string;
  queueId: number;
  offset: number;
}) {
  const res = await client.get<{ data: MessageRecord | null }>('/messages/queue-message', {
    params,
  });
  return res.data.data;
}

export async function listDLQMessages(params: {
  instanceId: string;
  groupName: string;
  startTime?: number;
  endTime?: number;
  page?: number;
  pageSize?: number;
}): Promise<DLQMessagePage> {
  const res = await client.get<{ data: DLQMessagePage }>(
    `/dlq/${encodeURIComponent(params.groupName)}/messages`,
    { params },
  );
  return res.data.data;
}

export async function resendDLQSelected(data: {
  instanceId: string;
  groupName: string;
  msgIds: string[];
  targetTopic?: string;
}): Promise<DLQResendResult> {
  const res = await client.post<{ data: DLQResendResult }>('/dlq/resend-selected', data);
  return res.data.data;
}

export async function exportDLQExcel(params: {
  instanceId: string;
  groupName: string;
  startTime?: number;
  endTime?: number;
  msgIds?: string[];
}): Promise<{ blob: Blob; meta: DLQExportMeta }> {
  const res = await client.get<Blob>('/dlq/export-excel', {
    params,
    responseType: 'blob',
    // Repeat the parameter name per item (`msgIds=a&msgIds=b`) instead of axios's default
    // bracketed form (`msgIds[]=a`), which Spring's @RequestParam List<String> would not
    // bind — the request would silently fall back to exporting the whole time window.
    paramsSerializer: { indexes: null },
  });
  const header = (name: string): string => String(res.headers[name] ?? '');
  return {
    blob: res.data,
    meta: {
      truncated: header('x-dlq-export-truncated') === 'true',
      failedQueueCount: Number.parseInt(header('x-dlq-export-failedqueues'), 10) || 0,
      limit: Number.parseInt(header('x-dlq-export-limit'), 10) || 0,
    },
  };
}
