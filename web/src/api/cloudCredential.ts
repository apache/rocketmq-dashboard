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
import type { InstanceVendor } from './instance';

export interface CloudCredential {
  id: number;
  name: string;
  vendor: InstanceVendor;
  accessKey: string;
  secretKey?: string;
  remark?: string;
  gmtCreate: string;
}

export interface CloudCredentialPage {
  items: CloudCredential[];
  total: number;
  page: number;
  size: number;
}

export async function listCloudCredentials(
  vendor?: InstanceVendor,
  search?: string,
  page = 1,
  pageSize = 20,
): Promise<CloudCredentialPage> {
  const res = await client.get<{ data: CloudCredentialPage }>('/cloud-credentials', {
    params: { vendor, search, page, pageSize },
  });
  return res.data.data;
}

export interface CreateCloudCredentialRequest {
  name: string;
  vendor: InstanceVendor;
  accessKey: string;
  secretKey: string;
  remark?: string;
}

export async function createCloudCredential(request: CreateCloudCredentialRequest) {
  const res = await client.post<{ data: CloudCredential }>('/cloud-credentials/create', request);
  return res.data.data;
}

export interface UpdateCloudCredentialRequest {
  id: number;
  name?: string;
  secretKey?: string;
  remark?: string;
}

export async function updateCloudCredential(request: UpdateCloudCredentialRequest) {
  const res = await client.post<{ data: CloudCredential }>('/cloud-credentials/update', request);
  return res.data.data;
}

export async function deleteCloudCredential(id: number) {
  await client.post('/cloud-credentials/delete', { id: String(id) });
}

export async function exportCloudCredentials(
  vendor?: InstanceVendor,
  search?: string,
): Promise<string> {
  const res = await client.get<{ data: string }>('/cloud-credentials/export', {
    params: { vendor, search },
  });
  return res.data.data;
}
