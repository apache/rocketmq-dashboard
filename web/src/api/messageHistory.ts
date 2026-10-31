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

export interface HistoryPage<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface MessageQueryHistory {
  id: number;
  queryType: 'TOPIC' | 'KEY' | 'MSG_ID';
  topic?: string;
  msgId?: string;
  tag?: string;
  messageKey?: string;
  startTime?: number;
  endTime?: number;
  resultCount: number;
  clusterId?: string;
  queriedBy?: string;
  queriedAt: string;
}

export interface TraceQueryHistory {
  id: number;
  msgId: string;
  topic?: string;
  /** The custom trace topic used by the lookup; absent means the provider default. */
  traceTopic?: string;
  nodeCount: number;
  consumerCount: number;
  clusterId?: string;
  queriedBy?: string;
  queriedAt: string;
}

export interface QueryHistorySummary {
  messageQueries: number;
  traceQueries: number;
  latestQueryAt?: string;
}

export async function listMessageQueryHistory(params: {
  clusterId?: string;
  queryType?: string;
  search?: string;
  page?: number;
  pageSize?: number;
}) {
  const response = await client.get<{ data: HistoryPage<MessageQueryHistory> }>(
    '/query-history/messages',
    { params },
  );
  return response.data.data;
}

export async function listTraceQueryHistory(params: {
  clusterId?: string;
  search?: string;
  page?: number;
  pageSize?: number;
}) {
  const response = await client.get<{ data: HistoryPage<TraceQueryHistory> }>(
    '/query-history/traces',
    { params },
  );
  return response.data.data;
}

export async function getQueryHistorySummary(clusterId?: string) {
  const response = await client.get<{ data: QueryHistorySummary }>('/query-history/summary', {
    params: clusterId ? { clusterId } : undefined,
  });
  return response.data.data;
}

export interface MessageResultSnapshot {
  msgId: string;
  topic: string;
  tag: string;
  key: string;
  brokerName: string;
  queueId: number | null;
  queueOffset: number | null;
  storeTime: number;
  bornHost: string;
  storeHost: string;
  size: number;
}

export async function getMessageQueryResults(id: number) {
  const response = await client.get<{ data: MessageResultSnapshot[] }>(
    `/query-history/messages/${id}/results`,
  );
  return response.data.data;
}
