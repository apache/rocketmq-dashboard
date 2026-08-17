/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */

import client from './client';
import type { CloudInstanceOption, CloudRegion } from './aliyunCatalog';

export async function listTencentRegions(credentialId: number) {
  const res = await client.get<{ data: CloudRegion[] }>('/cloud/tencent/regions', {
    params: { credentialId },
  });
  return res.data.data;
}

export async function listTencentInstances(
  credentialId: number,
  regionId: string,
  search?: string,
) {
  const res = await client.get<{ data: CloudInstanceOption[] }>('/cloud/tencent/instances', {
    params: { credentialId, regionId, ...(search ? { search } : {}) },
  });
  return res.data.data;
}
