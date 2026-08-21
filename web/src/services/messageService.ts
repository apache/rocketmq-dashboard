import { isMockMode } from './dataMode';
import * as messageApi from '../api/message';
import { sortMessagesByStoreTimeDesc } from '../api/message';
import type {
  MessageQuery,
  MessageRecord,
  TraceRecord,
  DLQGroup,
  DLQGroupPage,
  DLQResendResult,
} from '../api/message';
import { mockMessages, mockMessageTraces } from '../mock/messages';
import { mockDLQGroups } from '../mock/dlq';

const cloneMessage = (message: MessageRecord): MessageRecord => ({
  ...message,
  properties: { ...message.properties },
});

const cloneTrace = (trace: TraceRecord): TraceRecord => ({
  nodes: trace.nodes.map((node) => ({ ...node })),
  consumerStatus: trace.consumerStatus.map((status) => ({ ...status })),
});

const cloneDLQGroup = (group: DLQGroup): DLQGroup => ({ ...group });

const toStoreTimestamp = (storeTime: MessageRecord['storeTime']): number => {
  if (typeof storeTime === 'number') return storeTime;

  const parsed = Date.parse(storeTime);
  return Number.isNaN(parsed) ? 0 : parsed;
};

export async function queryMessages(params: MessageQuery): Promise<MessageRecord[]> {
  if (isMockMode()) {
    let result = [...mockMessages];
    if (params.topic) result = result.filter((m) => m.topic === params.topic);
    if (params.tag) result = result.filter((m) => m.tag === params.tag);
    if (params.key) result = result.filter((m) => m.key.includes(params.key!));
    if (params.msgId) result = result.filter((m) => m.msgId === params.msgId);
    if (params.startTime !== undefined) {
      result = result.filter((m) => toStoreTimestamp(m.storeTime) >= params.startTime!);
    }
    if (params.endTime !== undefined) {
      result = result.filter((m) => toStoreTimestamp(m.storeTime) <= params.endTime!);
    }
    return sortMessagesByStoreTimeDesc((result as unknown as MessageRecord[]).map(cloneMessage));
  }
  return messageApi.queryMessages(params);
}

export async function getMessageTrace(
  msgId: string,
  instanceId?: string,
  topic?: string,
): Promise<TraceRecord | null> {
  if (isMockMode()) {
    const trace = mockMessageTraces[msgId] as unknown as TraceRecord | undefined;
    return trace ? cloneTrace(trace) : null;
  }
  return messageApi.getMessageTrace(msgId, instanceId, topic);
}

export async function listDLQGroups(
  instanceId: string,
  search?: string,
  page = 1,
  pageSize = 20,
): Promise<DLQGroupPage> {
  if (isMockMode()) {
    const groups = (mockDLQGroups as unknown as DLQGroup[]).filter(
      (group) => !search || group.groupName.includes(search) || group.dlqTopic.includes(search),
    );
    const from = Math.min((page - 1) * pageSize, groups.length);
    return {
      items: groups.slice(from, from + pageSize).map(cloneDLQGroup),
      total: groups.length,
      page,
      size: pageSize,
    };
  }
  return messageApi.listDLQGroups(instanceId, search, page, pageSize);
}

export async function resendDLQ(data: {
  instanceId: string;
  groupName: string;
  startTime: number;
  endTime: number;
  targetTopic?: string;
}): Promise<DLQResendResult> {
  if (isMockMode()) return { matched: 0, resent: 0, failed: 0, outcome: 'SUCCESS' };
  return messageApi.resendDLQ(data);
}

export async function listDLQMessages(params: {
  instanceId: string;
  groupName: string;
  startTime?: number;
  endTime?: number;
  page?: number;
  pageSize?: number;
}) {
  if (isMockMode()) {
    return {
      items: [],
      total: 0,
      page: params.page ?? 1,
      size: params.pageSize ?? 20,
      scanIncomplete: false,
      failedQueueCount: 0,
    };
  }
  return messageApi.listDLQMessages(params);
}

export async function resendSelectedDLQMessages(data: {
  instanceId: string;
  groupName: string;
  startTime?: number;
  endTime?: number;
  targetTopic?: string;
  messages: Array<{ msgId: string; queueId: number; offset: number }>;
}) {
  if (isMockMode())
    return {
      matched: data.messages.length,
      resent: data.messages.length,
      failed: 0,
      outcome: 'SUCCESS' as const,
    };
  return messageApi.resendSelectedDLQMessages(data);
}

export async function exportDLQMessages(params: {
  instanceId: string;
  groupName: string;
  startTime?: number;
  endTime?: number;
  maxCount?: number;
}): Promise<Blob> {
  if (isMockMode()) return new Blob(['[]'], { type: 'application/json' });
  return messageApi.exportDLQMessages(params);
}
