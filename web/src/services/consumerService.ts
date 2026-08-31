import { isMockMode } from './dataMode';
import * as metadataApi from '../api/metadata';
import type {
  ConsumerGroup,
  ConsumerGroupExportQuery,
  ConsumerGroupPageQuery,
  ConsumerGroupQuery,
  ConsumerGroupSettings,
  ConsumerGroupDetail,
  ConsumerStackTrace,
  ImportConsumerGroupsResult,
  PageResult,
  QueueProgress,
  ResetConsumerOffsetPreview,
  ResetConsumerOffsetQueuePreview,
  ResetConsumerOffsetRequest,
  SubscriptionEntry,
} from '../api/metadata';
import { mockConsumerGroups, mockQueueProgress, mockSubscriptions } from '../mock/consumers';
import { buildCsv, type CsvColumn } from '../utils/download';

const consumerGroupsState = mockConsumerGroups as unknown as ConsumerGroup[];
const EXPORT_PAGE_SIZE = 100;
const MAX_EXPORT_PAGES = 100;
const GROUP_EXPORT_COLUMNS: CsvColumn<ConsumerGroup>[] = [
  { header: 'Name', value: (group) => group.name },
  { header: 'Namespace', value: (group) => group.namespace },
  { header: 'Cluster ID', value: (group) => group.clusterId },
  { header: 'Subscription Mode', value: (group) => group.subscriptionMode },
  { header: 'Consume Type', value: (group) => group.consumeType },
  { header: 'Online Instances', value: (group) => group.onlineInstances },
  { header: 'Total Lag', value: (group) => group.totalLag },
  { header: 'Delay Seconds', value: (group) => group.delaySeconds },
  { header: 'Subscription Data Type', value: (group) => group.subscriptionDataType },
  { header: 'Delivery Order Type', value: (group) => group.deliveryOrderType },
  { header: 'Retry Max Times', value: (group) => group.retryMaxTimes },
  { header: 'Subscribed Topics', value: (group) => (group.subscribedTopics ?? []).join(';') },
  { header: 'Created At', value: (group) => group.gmtCreate },
  { header: 'Updated At', value: (group) => group.gmtModified },
];

function copyConsumerInstance(
  instance: ConsumerGroup['instances'][number],
): ConsumerGroup['instances'][number] {
  return {
    ...instance,
    subscribedTopics: [...instance.subscribedTopics],
    topicLag: { ...instance.topicLag },
  };
}

function copyConsumerGroup(group: ConsumerGroup): ConsumerGroup {
  return {
    ...group,
    subscribedTopics: [...group.subscribedTopics],
    instances: group.instances.map(copyConsumerInstance),
  };
}

function copyQueueProgress(progress: QueueProgress): QueueProgress {
  return { ...progress };
}

function copySubscription(subscription: SubscriptionEntry): SubscriptionEntry {
  return { ...subscription };
}

function copyResetOffsetPreview(preview: ResetConsumerOffsetPreview): ResetConsumerOffsetPreview {
  return {
    ...preview,
    warnings: [...preview.warnings],
    queues: preview.queues.map((queue) => ({ ...queue })),
  };
}

const normalizeConsumerGroup = <T extends ConsumerGroup>(group: T): T => ({
  ...group,
  subscribedTopics: group.subscribedTopics ?? [],
  instances: group.instances ?? [],
});

function filterConsumerGroups(params?: ConsumerGroupQuery): ConsumerGroup[] {
  let result = [...consumerGroupsState];
  if (params?.clusterId) result = result.filter((group) => group.clusterId === params.clusterId);
  if (params?.search) {
    const kw = params.search.trim().toLowerCase();
    if (kw) result = result.filter((group) => group.name.toLowerCase().includes(kw));
  }
  return result;
}

function visibleConsumerGroups(groups: ConsumerGroup[], params?: ConsumerGroupExportQuery) {
  let result = groups;
  if (params?.names?.length) {
    const selectedNames = new Set(params.names);
    result = result.filter((group) => selectedNames.has(group.name));
  }
  if (params?.subscriptionMode && params.subscriptionMode !== 'ALL') {
    result = result.filter((group) => group.subscriptionMode === params.subscriptionMode);
  }
  return [...result].sort((left, right) => left.name.localeCompare(right.name));
}

export async function listConsumerGroups(params?: ConsumerGroupQuery): Promise<ConsumerGroup[]> {
  if (isMockMode()) {
    return filterConsumerGroups(params).map(copyConsumerGroup);
  }
  return (await metadataApi.listConsumerGroups(params)).map(normalizeConsumerGroup);
}

export async function listConsumerGroupPage(
  params: ConsumerGroupPageQuery = {},
): Promise<PageResult<ConsumerGroup>> {
  if (isMockMode()) {
    const page = params.page ?? 1;
    const pageSize = params.pageSize ?? 20;
    const groups = filterConsumerGroups(params);
    const from = Math.min((page - 1) * pageSize, groups.length);
    return {
      items: groups.slice(from, from + pageSize).map(copyConsumerGroup),
      total: groups.length,
      page,
      size: pageSize,
    };
  }
  return metadataApi.listConsumerGroupPage(params);
}

export async function listAllConsumerGroups(
  params: ConsumerGroupQuery = {},
): Promise<ConsumerGroup[]> {
  const groups: ConsumerGroup[] = [];
  let page = 1;

  while (page <= MAX_EXPORT_PAGES) {
    const result = await listConsumerGroupPage({
      ...params,
      page,
      pageSize: EXPORT_PAGE_SIZE,
    });
    groups.push(...result.items);
    const total = result.total ?? groups.length;
    if (result.items.length === 0 || groups.length >= total) {
      return groups.map(normalizeConsumerGroup);
    }
    page += 1;
  }

  throw new Error(`Consumer group export exceeded ${MAX_EXPORT_PAGES} pages`);
}

export async function getConsumerProgress(
  name: string,
  instanceId?: string,
): Promise<QueueProgress[]> {
  if (isMockMode()) {
    return ((mockQueueProgress[name] as unknown as QueueProgress[]) ?? []).map(copyQueueProgress);
  }
  return metadataApi.getConsumerProgress(name, instanceId);
}

export async function getConsumerGroup(
  name: string,
  instanceId?: string,
): Promise<ConsumerGroupDetail> {
  if (isMockMode()) {
    const group = mockConsumerGroups.find((item) => item.name === name);
    if (!group) throw new Error(`Consumer group not found: ${name}`);
    return copyConsumerGroup(group as unknown as ConsumerGroupDetail) as ConsumerGroupDetail;
  }
  return normalizeConsumerGroup(await metadataApi.getConsumerGroup(name, instanceId));
}

export async function getConsumerGroupSettings(
  name: string,
  instanceId: string,
): Promise<ConsumerGroupSettings> {
  if (isMockMode()) return { groupName: name, retryQueueNums: 1, retryMaxTimes: 16 };
  return metadataApi.getConsumerGroupSettings(name, instanceId);
}

export async function updateConsumerGroupSettings(
  data: Omit<ConsumerGroupSettings, 'groupName'> & { instanceId: string; name: string },
) {
  if (isMockMode())
    return {
      groupName: data.name,
      retryQueueNums: data.retryQueueNums,
      retryMaxTimes: data.retryMaxTimes,
    };
  return metadataApi.updateConsumerGroupSettings(data);
}

export async function refreshConsumerGroup(
  name: string,
  instanceId?: string,
): Promise<ConsumerGroup | null> {
  if (isMockMode()) {
    const group = mockConsumerGroups.find((item) => item.name === name);
    return group ? copyConsumerGroup(group) : null;
  }
  const data = await metadataApi.refreshConsumerGroup(name, instanceId);
  return data ? normalizeConsumerGroup(data) : null;
}

export async function getConsumerSubscriptions(
  name: string,
  instanceId?: string,
): Promise<SubscriptionEntry[]> {
  if (isMockMode()) {
    return ((mockSubscriptions[name] as unknown as SubscriptionEntry[]) ?? []).map(
      copySubscription,
    );
  }
  return metadataApi.getConsumerSubscriptions(name, instanceId);
}

export async function getConsumerStack(
  name: string,
  clientId: string,
  instanceId?: string,
): Promise<ConsumerStackTrace> {
  if (isMockMode()) {
    return {
      groupName: name,
      clientId,
      capturedAt: new Date().toISOString(),
      threadCount: 0,
      threads: [],
    };
  }
  return metadataApi.getConsumerStack(name, clientId, instanceId);
}

export async function createConsumerGroup(data: Partial<ConsumerGroup>): Promise<ConsumerGroup> {
  if (isMockMode()) {
    const now = new Date().toISOString();
    const group = {
      name: data.name ?? '',
      namespace: data.namespace ?? 'default',
      clusterId: data.clusterId ?? '',
      subscriptionMode: data.subscriptionMode ?? 'Push',
      consumeType: data.consumeType ?? 'CLUSTERING',
      onlineInstances: 0,
      totalLag: 0,
      subscribedTopics: data.subscribedTopics ?? [],
      subscriptionDataType: data.subscriptionDataType ?? 'NORMAL',
      retryMaxTimes: data.retryMaxTimes ?? 16,
      gmtCreate: now,
      gmtModified: now,
      delaySeconds: 0,
      instances: [],
    } as ConsumerGroup;
    mockConsumerGroups.unshift(group as never);
    return copyConsumerGroup(group);
  }
  return metadataApi.createConsumerGroup(data);
}

export async function importConsumerGroups(
  instanceId: string,
  groups: Partial<ConsumerGroup>[],
): Promise<ImportConsumerGroupsResult> {
  if (isMockMode()) {
    const imported: ConsumerGroup[] = [];
    const failures: ImportConsumerGroupsResult['failures'] = [];
    for (const [index, group] of groups.entries()) {
      try {
        imported.push(await createConsumerGroup({ ...group, instanceId }));
      } catch (error) {
        failures.push({
          index,
          name: group.name,
          message: error instanceof Error ? error.message : '创建失败',
        });
      }
    }
    return { imported: imported.length, failed: failures.length, groups: imported, failures };
  }
  const result = await metadataApi.importConsumerGroups({ instanceId, groups });
  return {
    ...result,
    groups: result.groups.map(normalizeConsumerGroup),
  };
}

export async function exportConsumerGroups(params: ConsumerGroupExportQuery = {}): Promise<string> {
  if (isMockMode()) {
    const groups = await listAllConsumerGroups(params);
    return buildCsv(GROUP_EXPORT_COLUMNS, visibleConsumerGroups(groups, params));
  }
  return metadataApi.exportConsumerGroups(params);
}

export async function deleteConsumerGroup(name: string, instanceId?: string): Promise<void> {
  if (isMockMode()) {
    const idx = consumerGroupsState.findIndex((group) => group.name === name);
    if (idx >= 0) consumerGroupsState.splice(idx, 1);
    return;
  }
  return metadataApi.deleteConsumerGroup(name, instanceId);
}

export async function resetConsumerOffset(data: ResetConsumerOffsetRequest): Promise<void> {
  if (isMockMode()) return;
  return metadataApi.resetConsumerOffset(data);
}

export async function previewConsumerOffsetReset(
  data: ResetConsumerOffsetRequest,
): Promise<ResetConsumerOffsetPreview> {
  if (isMockMode()) return buildMockResetOffsetPreview(data);
  return metadataApi.previewConsumerOffsetReset(data);
}

function buildMockResetOffsetPreview(data: ResetConsumerOffsetRequest): ResetConsumerOffsetPreview {
  const progressRows = ((mockQueueProgress[data.name] as unknown as QueueProgress[]) ?? []).filter(
    (progress) => !progress.topic || progress.topic === data.topic,
  );
  const queues = progressRows.map((progress) =>
    buildMockResetOffsetQueuePreview(data.topic, progress),
  );
  const currentTotalLag = queues.reduce((sum, queue) => sum + queue.currentLag, 0);
  const projectedTotalLag = queues.reduce((sum, queue) => sum + queue.projectedLag, 0);
  const rewindQueueCount = queues.filter((queue) => queue.offsetDelta < 0).length;
  const fastForwardQueueCount = queues.filter((queue) => queue.offsetDelta > 0).length;
  const warnings = buildMockResetOffsetWarnings(queues, rewindQueueCount, fastForwardQueueCount);

  return copyResetOffsetPreview({
    instanceId: data.instanceId,
    groupName: data.name,
    topic: data.topic,
    timestamp: data.timestamp,
    complete: queues.length > 0,
    allowReset: queues.length > 0,
    queueCount: queues.length,
    warningCount: warnings.length,
    rewindQueueCount,
    fastForwardQueueCount,
    currentTotalLag,
    projectedTotalLag,
    totalOffsetDelta: queues.reduce((sum, queue) => sum + queue.offsetDelta, 0),
    warnings,
    queues,
  });
}

function buildMockResetOffsetQueuePreview(
  topic: string,
  progress: QueueProgress,
): ResetConsumerOffsetQueuePreview {
  const maxOffset = progress.brokerOffset;
  const minOffset = 0;
  const rewindWindow = Math.min(500, Math.max(1, Math.floor(Math.max(progress.diffTotal, 1) / 2)));
  const targetOffset = Math.max(
    minOffset,
    Math.min(maxOffset, progress.consumerOffset - rewindWindow),
  );
  const offsetDelta = targetOffset - progress.consumerOffset;
  const currentLag = Math.max(0, progress.brokerOffset - progress.consumerOffset);
  const projectedLag = Math.max(0, progress.brokerOffset - targetOffset);

  return {
    topic: progress.topic || topic,
    broker: progress.broker,
    queueId: progress.queueId,
    minOffset,
    maxOffset,
    brokerOffset: progress.brokerOffset,
    consumerOffset: progress.consumerOffset,
    targetOffset,
    currentLag,
    projectedLag,
    offsetDelta,
    riskLevel: offsetDelta === 0 ? 'INFO' : 'WARNING',
    message:
      offsetDelta === 0
        ? 'Offset unchanged'
        : `Replays ${Math.abs(offsetDelta).toLocaleString()} message(s)`,
  };
}

function buildMockResetOffsetWarnings(
  queues: ResetConsumerOffsetQueuePreview[],
  rewindQueueCount: number,
  fastForwardQueueCount: number,
): string[] {
  if (queues.length === 0) return ['No consume offset data found for the selected topic'];
  const warnings: string[] = [];
  if (fastForwardQueueCount > 0) {
    warnings.push(`${fastForwardQueueCount} queue(s) will move forward and may skip messages`);
  }
  if (rewindQueueCount > 0) {
    warnings.push(`${rewindQueueCount} queue(s) will replay consumed messages`);
  }
  return warnings;
}

export interface BatchDeleteConsumerGroupsResult {
  deleted: string[];
  failed: string[];
}

// Batch delete: attempt every selected group and report partial failures so a single
// failing group cannot silently abort the whole batch.
export async function batchDeleteConsumerGroups(
  names: string[],
  instanceId?: string,
): Promise<BatchDeleteConsumerGroupsResult> {
  const result: BatchDeleteConsumerGroupsResult = { deleted: [], failed: [] };
  for (const name of names) {
    try {
      await deleteConsumerGroup(name, instanceId);
      result.deleted.push(name);
    } catch {
      result.failed.push(name);
    }
  }
  return result;
}
