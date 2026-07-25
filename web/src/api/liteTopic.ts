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

// ─── Interfaces ──────────────────────────────────────────────────

export interface LiteTopicQuota {
  currentTopicCount: number;
  maxTopicCount: number;
  currentSessionCount: number;
  maxSessionCount: number;
  currentCreationRate: number;
  maxCreationRate: number;
  usageRate?: number;
  sessionUsageRate?: number;
  defaultTTL?: number;
  maxTTL?: number;
  remainingQuota?: number;
  consumerDensity?: number;
}

export interface LiteTopicItem {
  topicPattern: string;
  topicCount?: number;
  consumerCount?: number;
  totalBacklog?: number;
  averageTTL?: number;
  ttlStatus?: string;
  lastActiveTime?: number;
  sessionIds?: string[];
}

export interface LiteTopicSession {
  sessionId: string;
  clientId?: string;
  clientAddress?: string;
  parentTopic?: string;
  consumerGroup?: string;
  createTime?: number;
  lastActiveTime?: number;
  ttl?: number;
  ttlRemaining?: number;
  status?: string;
  totalMessages?: number;
  consumedMessages?: number;
  pendingMessages?: number;
  popProgress?: number;
  liteTopicCreationCount?: number;
  liteTopics?: { topicName: string; status: string; ttlRemaining?: number }[];
}

export interface LiteTopicCapability {
  supported: boolean;
}

// ─── API Functions ───────────────────────────────────────────────

export async function queryLiteTopicList(
  pattern?: string,
  namespace?: string,
): Promise<LiteTopicItem[]> {
  const params = new URLSearchParams();
  if (pattern) params.append('pattern', pattern);
  if (namespace) params.append('namespace', namespace);
  const res = await client.get<{ data: LiteTopicItem[] }>(`/liteTopic/list?${params.toString()}`);
  return res.data.data;
}

export async function queryLiteTopicSession(sessionId: string): Promise<LiteTopicSession> {
  const res = await client.get<{ data: LiteTopicSession }>(
    `/liteTopic/session/${encodeURIComponent(sessionId)}`,
  );
  return res.data.data;
}

export async function extendLiteTopicTTL(topicPattern: string, newTTL: number): Promise<void> {
  await client.post('/liteTopic/extendTTL', { topicPattern, newTTL });
}

export async function queryLiteTopicQuota(namespace?: string): Promise<LiteTopicQuota> {
  const params = new URLSearchParams();
  if (namespace) params.append('namespace', namespace);
  const res = await client.get<{ data: LiteTopicQuota }>(`/liteTopic/quota?${params.toString()}`);
  return res.data.data;
}

export async function queryLiteTopicCapability(): Promise<LiteTopicCapability> {
  const res = await client.get<{ data: LiteTopicCapability }>('/liteTopic/capability');
  return res.data.data;
}
