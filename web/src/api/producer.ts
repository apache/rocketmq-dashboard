/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import client from './client';

// ─── Types ──────────────────────────────────────────────────────
export interface ProducerConnection {
  clientId: string;
  clientAddr: string;
  language: string;
  versionDesc: string;
}

export type ProducerReadiness = 'READY' | 'WARNING' | 'UNAVAILABLE';

export type ProducerConnectionWarning =
  'NO_CONNECTIONS' | 'DUPLICATE_CLIENT_ID' | 'MIXED_CLIENT_VERSION' | 'INCOMPLETE_CLIENT_METADATA';

export interface ProducerConnectionSummaryItem {
  value: string;
  count: number;
}

export interface ProducerConnectionSummary {
  totalConnections: number;
  uniqueClientCount: number;
  uniqueAddressCount: number;
  uniqueLanguageCount: number;
  uniqueVersionCount: number;
  languages: ProducerConnectionSummaryItem[];
  versions: ProducerConnectionSummaryItem[];
  duplicateClientIds: string[];
  warnings: ProducerConnectionWarning[];
  readiness: ProducerReadiness;
}

export interface ProducerConnectionResult {
  connectionSet: ProducerConnection[];
  summary: ProducerConnectionSummary;
}

interface TopicRecord {
  name: string;
}

interface TopicListResponse {
  data?: TopicRecord[];
  topicList?: string[];
}

interface ProducerConnectionResponse {
  connectionSet?: ProducerConnection[];
  summary?: ProducerConnectionSummary;
}

// ─── API ────────────────────────────────────────────────────────

const normalizeIdentity = (value?: string | null) => {
  const normalized = value?.trim();
  return normalized && normalized.toLowerCase() !== 'null' ? normalized : undefined;
};

const hasText = (value?: string | null) => Boolean(normalizeIdentity(value));

const normalizeDimension = (value?: string | null) => (hasText(value) ? value!.trim() : 'UNKNOWN');

const countDistinct = (
  connections: ProducerConnection[],
  extractor: (connection: ProducerConnection) => string,
) =>
  new Set(
    connections
      .map(extractor)
      .map(normalizeIdentity)
      .filter((value): value is string => Boolean(value)),
  ).size;

const distribution = (
  connections: ProducerConnection[],
  extractor: (connection: ProducerConnection) => string,
): ProducerConnectionSummaryItem[] => {
  const counts = new Map<string, number>();
  connections.forEach((connection) => {
    const value = normalizeDimension(extractor(connection));
    counts.set(value, (counts.get(value) ?? 0) + 1);
  });
  return [...counts]
    .map(([value, count]) => ({ value, count }))
    .sort((a, b) => b.count - a.count || a.value.localeCompare(b.value));
};

export function buildProducerConnectionSummary(
  connections: ProducerConnection[],
): ProducerConnectionSummary {
  const duplicateClientIds = [
    ...connections.reduce((counts, connection) => {
      const clientId = normalizeIdentity(connection.clientId);
      if (clientId) counts.set(clientId, (counts.get(clientId) ?? 0) + 1);
      return counts;
    }, new Map<string, number>()),
  ]
    .filter(([, count]) => count > 1)
    .map(([clientId]) => clientId)
    .sort();
  const languages = distribution(connections, (connection) => connection.language);
  const versions = distribution(connections, (connection) => connection.versionDesc);
  const warnings: ProducerConnectionWarning[] = [];

  if (connections.length === 0) {
    warnings.push('NO_CONNECTIONS');
  } else {
    if (duplicateClientIds.length > 0) warnings.push('DUPLICATE_CLIENT_ID');
    if (versions.length > 1) warnings.push('MIXED_CLIENT_VERSION');
    if (
      connections.some(
        (connection) =>
          !hasText(connection.clientId) ||
          !hasText(connection.clientAddr) ||
          !hasText(connection.language) ||
          !hasText(connection.versionDesc),
      )
    ) {
      warnings.push('INCOMPLETE_CLIENT_METADATA');
    }
  }

  return {
    totalConnections: connections.length,
    uniqueClientCount: countDistinct(connections, (connection) => connection.clientId),
    uniqueAddressCount: countDistinct(connections, (connection) => connection.clientAddr),
    uniqueLanguageCount: languages.length,
    uniqueVersionCount: versions.length,
    languages,
    versions,
    duplicateClientIds,
    warnings,
    readiness: connections.length === 0 ? 'UNAVAILABLE' : warnings.length > 0 ? 'WARNING' : 'READY',
  };
}

/** Fetch topic names for a managed instance. */
export async function fetchTopicList(instanceId: string): Promise<string[]> {
  const res = await client.get<TopicListResponse>('/topics', { params: { instanceId } });
  const topics = res.data.data?.map((topic) => topic.name) ?? res.data.topicList ?? [];
  return topics.sort();
}

/** Fetch active producer groups for query suggestions */
export async function fetchProducerGroups(
  instanceId: string,
  options: {
    topic?: string;
    query?: string;
    limit?: number;
  } = {},
): Promise<string[]> {
  const res = await client.get<{ data?: string[] }>('/producer/groups', {
    params: {
      instanceId,
      topic: options.topic,
      query: options.query,
      limit: options.limit,
    },
  });
  return res.data.data ?? [];
}

/** Query producer connections by topic and producer group */
export async function queryProducerConnection(
  instanceId: string,
  topic: string,
  producerGroup: string,
): Promise<ProducerConnectionResult> {
  const res = await client.get<ProducerConnectionResponse>('/producer/connection', {
    params: { instanceId, topic, producerGroup },
  });
  const connectionSet = res.data?.connectionSet ?? [];
  return {
    connectionSet,
    summary: res.data?.summary ?? buildProducerConnectionSummary(connectionSet),
  };
}
