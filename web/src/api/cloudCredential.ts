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

export async function listCloudCredentials() {
  const res = await client.get<{ data: CloudCredential[] }>('/cloud-credentials');
  return res.data.data;
}
