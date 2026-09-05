/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import type { Topic } from '../api/metadata';

export type TopicQueueProfileStatus =
  'BALANCED' | 'ASYMMETRIC' | 'READ_ONLY' | 'WRITE_ONLY' | 'NO_ACCESS' | 'UNKNOWN_PERMISSION';

export interface TopicQueueProfile {
  key: string;
  type: string;
  namespace: string;
  writeQueues: number;
  readQueues: number;
  permission: string;
  status: TopicQueueProfileStatus;
  topicCount: number;
  sharePercent: number;
  totalWriteQueues: number;
  totalReadQueues: number;
  messageCount: number;
  tps: number;
  consumerGroups: number;
  sampleTopics: string[];
}

export interface TopicQueuePortfolio {
  profiles: TopicQueueProfile[];
  summary: {
    topics: number;
    profiles: number;
    balancedTopics: number;
    asymmetricTopics: number;
    restrictedTopics: number;
    unknownPermissionTopics: number;
    writeQueues: number;
    readQueues: number;
    dominantProfilePercent: number;
  };
}

const safeNumber = (value: number) => (Number.isFinite(value) && value > 0 ? value : 0);
const safeText = (value?: string | null, fallback = '-') => value?.trim() || fallback;

export const classifyTopicQueueProfile = (
  writeQueues: number,
  readQueues: number,
  permission?: string | null,
): TopicQueueProfileStatus => {
  const normalizedPermission = safeText(permission, '').toUpperCase();
  const knownPermission = ['RW', 'WR', 'R', 'W', 'NONE', 'DENY', '0'];
  if (!knownPermission.includes(normalizedPermission)) return 'UNKNOWN_PERMISSION';
  const readable = normalizedPermission.includes('R');
  const writable = normalizedPermission.includes('W');
  if (!readable && !writable) return 'NO_ACCESS';
  if (readable && !writable) return 'READ_ONLY';
  if (!readable && writable) return 'WRITE_ONLY';
  return safeNumber(writeQueues) === safeNumber(readQueues) ? 'BALANCED' : 'ASYMMETRIC';
};

const profileKey = (topic: Topic) => {
  const type = safeText(topic.type, 'UNKNOWN');
  const namespace = safeText(topic.namespace, '(default)');
  const permission = safeText(topic.perm, 'UNKNOWN').toUpperCase();
  return [
    type,
    namespace,
    safeNumber(topic.writeQueues),
    safeNumber(topic.readQueues),
    permission,
  ].join('|');
};

interface ProfileAccumulator {
  type: string;
  namespace: string;
  writeQueues: number;
  readQueues: number;
  permission: string;
  status: TopicQueueProfileStatus;
  topics: Topic[];
}

/** 将当前实例全部 Topic 聚合为可审计的队列/权限配置组合，不修改原始列表。 */
export const buildTopicQueuePortfolio = (topics: Topic[]): TopicQueuePortfolio => {
  const grouped = new Map<string, ProfileAccumulator>();
  topics.forEach((topic) => {
    const key = profileKey(topic);
    const current = grouped.get(key);
    if (current) {
      current.topics.push(topic);
      return;
    }
    const writeQueues = safeNumber(topic.writeQueues);
    const readQueues = safeNumber(topic.readQueues);
    const permission = safeText(topic.perm, 'UNKNOWN').toUpperCase();
    grouped.set(key, {
      type: safeText(topic.type, 'UNKNOWN'),
      namespace: safeText(topic.namespace, '(default)'),
      writeQueues,
      readQueues,
      permission,
      status: classifyTopicQueueProfile(writeQueues, readQueues, permission),
      topics: [topic],
    });
  });

  const profiles: TopicQueueProfile[] = [...grouped.entries()]
    .map(([key, profile]) => ({
      key,
      type: profile.type,
      namespace: profile.namespace,
      writeQueues: profile.writeQueues,
      readQueues: profile.readQueues,
      permission: profile.permission,
      status: profile.status,
      topicCount: profile.topics.length,
      sharePercent: topics.length
        ? Math.round((profile.topics.length / topics.length) * 1000) / 10
        : 0,
      totalWriteQueues: profile.writeQueues * profile.topics.length,
      totalReadQueues: profile.readQueues * profile.topics.length,
      messageCount: profile.topics.reduce((sum, topic) => sum + safeNumber(topic.messageCount), 0),
      tps:
        Math.round(profile.topics.reduce((sum, topic) => sum + safeNumber(topic.tps), 0) * 100) /
        100,
      consumerGroups: profile.topics.reduce(
        (sum, topic) => sum + safeNumber(topic.consumerGroupCount),
        0,
      ),
      sampleTopics: profile.topics
        .map((topic) => topic.name)
        .sort()
        .slice(0, 5),
    }))
    .sort((left, right) => right.topicCount - left.topicCount || left.key.localeCompare(right.key));

  const topicsWithStatus = topics.map((topic) =>
    classifyTopicQueueProfile(topic.writeQueues, topic.readQueues, topic.perm),
  );
  const count = (statuses: TopicQueueProfileStatus[]) =>
    topicsWithStatus.filter((status) => statuses.includes(status)).length;

  return {
    profiles,
    summary: {
      topics: topics.length,
      profiles: profiles.length,
      balancedTopics: count(['BALANCED']),
      asymmetricTopics: count(['ASYMMETRIC']),
      restrictedTopics: count(['READ_ONLY', 'WRITE_ONLY', 'NO_ACCESS']),
      unknownPermissionTopics: count(['UNKNOWN_PERMISSION']),
      writeQueues: topics.reduce((sum, topic) => sum + safeNumber(topic.writeQueues), 0),
      readQueues: topics.reduce((sum, topic) => sum + safeNumber(topic.readQueues), 0),
      dominantProfilePercent: profiles[0]?.sharePercent ?? 0,
    },
  };
};
