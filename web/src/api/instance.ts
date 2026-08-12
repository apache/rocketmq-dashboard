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
export type InstanceVendor = 'APACHE' | 'ALIYUN' | 'TENCENT';
export type InstanceCapability =
  | 'TOPIC_MANAGEMENT'
  | 'CONSUMER_GROUP_MANAGEMENT'
  | 'MESSAGE_QUERY'
  | 'MESSAGE_TRACE'
  | 'ACL_MANAGEMENT'
  | 'DLQ_MANAGEMENT';

export interface Instance {
  id: string;
  name: string;
  remark: string | null;
  type: 'PROXY' | 'DIRECT';
  endpoint: string;
  vendor?: InstanceVendor;
  cloudInstanceId?: string;
  credentialId?: string;
  adminCredentialRef?: string;
  regionId?: string;
  topicCount: number;
  consumerGroupCount: number;
  resourceCountsAvailable?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateInstanceRequest {
  name?: string;
  type?: 'PROXY' | 'DIRECT';
  endpoint?: string;
  remark?: string;
  vendor?: InstanceVendor;
  cloudInstanceId?: string;
  credentialId?: string;
  adminCredentialRef?: string;
  regionId?: string;
}

export interface UpdateInstanceRequest {
  id: string;
  name?: string;
  type?: 'PROXY' | 'DIRECT';
  endpoint?: string;
  remark?: string;
  adminCredentialRef?: string;
}

export interface InstanceQuery {
  type?: Instance['type'];
  search?: string;
}

/** Whether an instance can use Apache MQAdmin-backed runtime diagnostics. */
export function supportsApacheRuntime(instance: Pick<Instance, 'vendor'>): boolean {
  return instance.vendor === undefined || instance.vendor === 'APACHE';
}

export interface InstanceCapabilities {
  instanceId: string;
  vendor: InstanceVendor;
  accessType: Instance['type'];
  capabilities: InstanceCapability[];
}

// ─── Instance CRUD ──────────────────────────────────────────────
export async function listInstances(query: InstanceQuery = {}) {
  const search = query.search?.trim();
  const params = {
    ...(query.type ? { type: query.type } : {}),
    ...(search ? { search } : {}),
  };
  const res = await client.get<{ data: Instance[] }>('/instances', { params });
  return res.data.data;
}

export async function getInstanceCapabilities(instanceId: string) {
  const res = await client.get<{ data: InstanceCapabilities }>(
    `/instances/${encodeURIComponent(instanceId)}/capabilities`,
  );
  return res.data.data;
}

export async function createInstance(data: CreateInstanceRequest) {
  const res = await client.post<{ data: Instance }>('/instances/create', data);
  return res.data.data;
}

export async function updateInstance(data: UpdateInstanceRequest) {
  const res = await client.post<{ data: Instance }>('/instances/update', data);
  return res.data.data;
}

export async function deleteInstance(id: string) {
  await client.post('/instances/delete', { id });
}
