import { USE_MOCK } from '../config';
import * as messageApi from '../api/message';
import { sortMessagesByStoreTimeDesc } from '../api/message';
import type { MessageQuery, MessageRecord, TraceRecord, DLQGroup } from '../api/message';
import { mockMessages, mockMessageTraces } from '../mock/messages';
import { mockDLQGroups } from '../mock/dlq';

export async function queryMessages(params: MessageQuery): Promise<MessageRecord[]> {
  if (USE_MOCK) {
    let result = [...mockMessages];
    if (params.topic) result = result.filter((m) => m.topic === params.topic);
    if (params.tag) result = result.filter((m) => m.tag === params.tag);
    if (params.key) result = result.filter((m) => m.key.includes(params.key!));
    if (params.msgId) result = result.filter((m) => m.msgId === params.msgId);
    return sortMessagesByStoreTimeDesc(result as unknown as MessageRecord[]);
  }
  return messageApi.queryMessages(params);
}

export async function getMessageTrace(msgId: string): Promise<TraceRecord | null> {
  if (USE_MOCK) return (mockMessageTraces[msgId] as unknown as TraceRecord) ?? null;
  return messageApi.getMessageTrace(msgId);
}

export async function listDLQGroups(): Promise<DLQGroup[]> {
  if (USE_MOCK) return mockDLQGroups as unknown as DLQGroup[];
  return messageApi.listDLQGroups();
}

export async function resendDLQ(data: {
  groupName: string;
  startTime: number;
  endTime: number;
  targetTopic?: string;
}): Promise<void> {
  if (USE_MOCK) return;
  return messageApi.resendDLQ(data);
}
