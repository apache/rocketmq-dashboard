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

export interface QuotaConfig {
  maxTopicCount?: number;
  maxConsumerGroupCount?: number;
  storageQuotaGB?: number;
  qpsLimit?: number;
  connectionLimit?: number;
}

export interface NamespaceItem {
  namespaceName: string;
  displayName?: string;
  description?: string;
  clusterName?: string;
  status: string;
  defaultNamespace?: boolean;
  createTime?: number;
  quotaConfig?: QuotaConfig;
}

export interface NamespaceCapability {
  namespaceSupported: boolean;
}

export async function queryNamespaceList(): Promise<NamespaceItem[]> {
  const res = await client.get<{ data: NamespaceItem[] }>('/namespace/list');
  return res.data.data;
}

export async function queryNamespaceDetail(name: string): Promise<NamespaceItem> {
  const res = await client.get<{ data: NamespaceItem }>(`/namespace/${encodeURIComponent(name)}`);
  return res.data.data;
}

export async function createNamespace(payload: Partial<NamespaceItem>): Promise<void> {
  await client.post('/namespace/create', payload);
}

export async function updateNamespace(payload: Partial<NamespaceItem>): Promise<void> {
  await client.put('/namespace/update', payload);
}

export async function deleteNamespace(name: string): Promise<void> {
  await client.delete(`/namespace/${encodeURIComponent(name)}`);
}

export async function queryNamespaceCapability(): Promise<NamespaceCapability> {
  const res = await client.get<{ data: NamespaceCapability }>('/namespace/capability');
  return res.data.data;
}
