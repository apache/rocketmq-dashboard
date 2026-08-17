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
}

export interface CloudCredentialPage { items: CloudCredential[]; total: number; page: number; size: number; }

export async function listCloudCredentials(vendor?: InstanceVendor, search?: string, page = 1, pageSize = 20): Promise<CloudCredentialPage | CloudCredential[]> {
  const res = await client.get<{ data: CloudCredentialPage }>('/cloud-credentials', { params: { vendor, search, page, pageSize } });
  return res.data.data;
}
