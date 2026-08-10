/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */

import client from './client';
import type { InstanceVendor } from './instance';

export interface CloudCredential {
  id: string;
  name: string;
  vendor: InstanceVendor;
  accessKey: string;
  secretKey?: string;
  remark?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateCloudCredentialRequest {
  name: string;
  vendor: Exclude<InstanceVendor, 'APACHE'>;
  accessKey: string;
  secretKey: string;
  remark?: string;
}

export interface UpdateCloudCredentialRequest {
  id: string;
  name?: string;
  secretKey?: string;
  remark?: string;
}

export async function listCloudCredentials() {
  const res = await client.get<{ data: CloudCredential[] }>('/cloud-credentials');
  return res.data.data;
}

export async function createCloudCredential(data: CreateCloudCredentialRequest) {
  const res = await client.post<{ data: CloudCredential }>('/cloud-credentials/create', data);
  return res.data.data;
}

export async function updateCloudCredential(data: UpdateCloudCredentialRequest) {
  const res = await client.post<{ data: CloudCredential }>('/cloud-credentials/update', data);
  return res.data.data;
}

export async function deleteCloudCredential(id: string) {
  await client.post('/cloud-credentials/delete', { id });
}
