import { isMockMode } from './dataMode';
import * as messageApi from '../api/message';
import { sortMessagesByStoreTimeDesc } from '../api/message';
import type {
  MessageQuery,
  MessageRecord,
  TraceRecord,
  DLQGroup,
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
  storeTime?: MessageRecord['storeTime'],
): Promise<TraceRecord | null> {
  if (isMockMode()) {
    const trace = mockMessageTraces[msgId] as unknown as TraceRecord | undefined;
    return trace ? cloneTrace(trace) : null;
  }
  const storeTimestamp = storeTime === undefined ? undefined : toStoreTimestamp(storeTime);
  const validStoreTimestamp =
    storeTimestamp !== undefined && Number.isFinite(storeTimestamp) && storeTimestamp > 0;
  return messageApi.getMessageTrace(
    msgId,
    instanceId,
    validStoreTimestamp ? storeTimestamp : undefined,
  );
}

export async function listDLQGroups(instanceId: string): Promise<DLQGroup[]> {
  if (isMockMode()) return (mockDLQGroups as unknown as DLQGroup[]).map(cloneDLQGroup);
  return messageApi.listDLQGroups(instanceId);
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
