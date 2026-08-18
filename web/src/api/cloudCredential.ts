/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
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

export async function listCloudCredentials() {
  const res = await client.get<{ data: CloudCredential[] }>('/cloud-credentials');
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
