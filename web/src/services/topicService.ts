import { isMockMode } from './dataMode';
import * as metadataApi from '../api/metadata';
import type {
  Topic,
  TopicExportQuery,
  TopicQuery,
  TopicPage,
  BrokerRoute,
  ConsumerGroupInfo,
  TopicConsumerPage,
  SendTopicMessageRequest,
  SendTopicMessageResult,
  ImportTopicsResult,
} from '../api/metadata';
import { topics as mockTopics, topicRoutes, topicConsumers } from '../mock/topics';
import { buildCsv, type CsvColumn } from '../utils/download';

const EXPORT_PAGE_SIZE = 100;
const MAX_EXPORT_PAGES = 100;
const TOPIC_EXPORT_COLUMNS: CsvColumn<Topic>[] = [
  { header: 'Name', value: (topic) => topic.name },
  { header: 'Namespace', value: (topic) => topic.namespace },
  { header: 'Type', value: (topic) => topic.type },
  { header: 'Cluster ID', value: (topic) => topic.clusterId },
  { header: 'Write Queues', value: (topic) => topic.writeQueues },
  { header: 'Read Queues', value: (topic) => topic.readQueues },
  { header: 'Permission', value: (topic) => topic.perm },
  { header: 'Message Count', value: (topic) => topic.messageCount },
  { header: 'TPS', value: (topic) => topic.tps },
  { header: 'Consumer Groups', value: (topic) => topic.consumerGroupCount },
  { header: 'Remark', value: (topic) => topic.remark },
  { header: 'Created At', value: (topic) => topic.gmtCreate },
  { header: 'Updated At', value: (topic) => topic.gmtModified },
];

const cloneTopic = (topic: Topic): Topic => ({ ...topic });
const cloneRoutes = (routes: BrokerRoute[]): BrokerRoute[] =>
  routes.map((route) => ({
    ...route,
    brokerAddrs: route.brokerAddrs ? { ...route.brokerAddrs } : undefined,
    brokerIds: route.brokerIds ? [...route.brokerIds] : undefined,
  }));
const cloneConsumers = (consumers: ConsumerGroupInfo[]): ConsumerGroupInfo[] =>
  consumers.map((consumer) => ({ ...consumer }));

function filterMockTopics(params?: TopicQuery): Topic[] {
  let result = [...mockTopics];
  if (params?.search) {
    const keyword = params.search.trim().toLowerCase();
    if (keyword) result = result.filter((topic) => topic.name.toLowerCase().includes(keyword));
  }
  if (params?.type) result = result.filter((t) => t.type === params.type);
  if (params?.clusterId) result = result.filter((t) => t.clusterId === params.clusterId);
  if (params?.instanceId) result = result.filter((t) => t.instanceId === params.instanceId);
  return (result as unknown as Topic[]).map(cloneTopic);
}

function visibleExportTopics(topics: Topic[], params?: TopicExportQuery): Topic[] {
  let result = topics;
  if (params?.names?.length) {
    const selectedNames = new Set(params.names);
    result = result.filter((topic) => selectedNames.has(topic.name));
  }
  return [...result].sort((left, right) => left.name.localeCompare(right.name));
}

export async function listTopics(params?: TopicQuery): Promise<Topic[]> {
  if (isMockMode()) {
    return filterMockTopics(params);
  }
  return metadataApi.listTopics(params);
}

export async function listTopicsPage(
  params?: TopicQuery & { page?: number; pageSize?: number },
): Promise<TopicPage> {
  if (isMockMode()) {
    const filtered = filterMockTopics(params);
    const page = Math.max(params?.page ?? 1, 1);
    const pageSize = Math.min(Math.max(params?.pageSize ?? 20, 1), 100);
    const from = Math.min((page - 1) * pageSize, filtered.length);
    return {
      items: filtered.slice(from, from + pageSize),
      total: filtered.length,
      page,
      size: pageSize,
    };
  }
  return metadataApi.listTopicsPage(params);
}

export const listAllTopics = async (params: TopicQuery = {}): Promise<Topic[]> => {
  const topics: Topic[] = [];
  let page = 1;

  while (page <= MAX_EXPORT_PAGES) {
    const result = await listTopicsPage({ ...params, page, pageSize: EXPORT_PAGE_SIZE });
    // A success envelope may still carry a null or item-less payload; treat it as an
    // empty page instead of crashing the whole export walk.
    const items = Array.isArray(result?.items) ? result.items : [];
    topics.push(...items);
    const total =
      result && typeof result.total === 'number' && Number.isFinite(result.total)
        ? result.total
        : topics.length;
    if (items.length === 0 || topics.length >= total) return topics;
    page += 1;
  }

  throw new Error(`Topic export exceeded ${MAX_EXPORT_PAGES} pages`);
};

export async function createTopic(data: Partial<Topic>): Promise<Topic> {
  if (isMockMode()) {
    const duplicate = mockTopics.some(
      (topic) => topic.name === data.name && topic.clusterId === data.clusterId,
    );
    if (duplicate) throw new Error(`Topic already exists: ${data.name}`);

    const topic = {
      ...data,
      gmtCreate: new Date().toISOString(),
      gmtModified: new Date().toISOString(),
      messageCount: 0,
      tps: 0,
      consumerGroupCount: 0,
    } as unknown as Topic;
    mockTopics.unshift(topic as never);
    return cloneTopic(topic);
  }
  return metadataApi.createTopic(data);
}

export async function importTopics(
  instanceId: string,
  topics: Partial<Topic>[],
): Promise<ImportTopicsResult> {
  if (isMockMode()) {
    const imported: Topic[] = [];
    const failures: ImportTopicsResult['failures'] = [];
    for (const [index, topic] of topics.entries()) {
      try {
        imported.push(await createTopic({ ...topic, instanceId }));
      } catch (error) {
        failures.push({
          index,
          name: topic.name,
          message: error instanceof Error ? error.message : '创建失败',
        });
      }
    }
    return { imported: imported.length, failed: failures.length, topics: imported, failures };
  }
  return metadataApi.importTopics({ instanceId, topics });
}

export async function exportTopics(params: TopicExportQuery = {}): Promise<string> {
  if (isMockMode()) {
    const topics = await listAllTopics(params);
    return buildCsv(TOPIC_EXPORT_COLUMNS, visibleExportTopics(topics, params));
  }
  return metadataApi.exportTopics(params);
}

export async function updateTopic(data: Partial<Topic>): Promise<Topic> {
  if (isMockMode()) {
    const idx = mockTopics.findIndex((t) => t.name === data.name);
    if (idx < 0) throw new Error(`Topic not found: ${data.name}`);
    Object.assign(mockTopics[idx], data, { gmtModified: new Date().toISOString() });
    return cloneTopic(mockTopics[idx] as unknown as Topic);
  }
  return metadataApi.updateTopic(data);
}

export async function deleteTopic(name: string, instanceId?: string): Promise<void> {
  if (isMockMode()) {
    const idx = mockTopics.findIndex((t) => t.name === name);
    if (idx >= 0) mockTopics.splice(idx, 1);
    return;
  }
  return metadataApi.deleteTopic(name, instanceId);
}

export interface BatchDeleteTopicsResult {
  deleted: string[];
  failed: string[];
}

// Batch delete: attempt every selected topic and report partial failures.
export async function batchDeleteTopics(
  names: string[],
  instanceId?: string,
): Promise<BatchDeleteTopicsResult> {
  const result: BatchDeleteTopicsResult = { deleted: [], failed: [] };
  for (const name of names) {
    try {
      await deleteTopic(name, instanceId);
      result.deleted.push(name);
    } catch {
      result.failed.push(name);
    }
  }
  return result;
}

export async function getTopicRoutes(name: string, instanceId?: string): Promise<BrokerRoute[]> {
  if (isMockMode()) return cloneRoutes((topicRoutes[name] as unknown as BrokerRoute[]) ?? []);
  return metadataApi.getTopicRoutes(name, instanceId);
}

export async function getTopicConsumers(
  name: string,
  instanceId?: string,
): Promise<ConsumerGroupInfo[]> {
  if (isMockMode())
    return cloneConsumers((topicConsumers[name] as unknown as ConsumerGroupInfo[]) ?? []);
  return metadataApi.getTopicConsumers(name, instanceId);
}

export async function getTopicConsumerPage(
  name: string,
  instanceId: string | undefined,
  page: number,
  pageSize: number,
): Promise<TopicConsumerPage> {
  if (isMockMode()) {
    const consumers = cloneConsumers(
      (topicConsumers[name] as unknown as ConsumerGroupInfo[]) ?? [],
    );
    const from = Math.min((page - 1) * pageSize, consumers.length);
    return {
      items: consumers.slice(from, from + pageSize),
      total: consumers.length,
      page,
      pageSize,
    };
  }
  return metadataApi.getTopicConsumerPage(name, instanceId, page, pageSize);
}

export async function sendTopicMessage(
  data: SendTopicMessageRequest,
): Promise<SendTopicMessageResult> {
  if (isMockMode()) {
    // Simulate a short delay
    await new Promise((r) => setTimeout(r, 400));
    return {
      msgId: `7F${Math.random().toString(16).slice(2, 18).toUpperCase()}`,
      sendTime: new Date().toISOString(),
      offsetMsgId: `7F${Math.random().toString(16).slice(2, 18).toUpperCase()}-0:0:0:0`,
    };
  }
  return metadataApi.sendTopicMessage(data);
}
