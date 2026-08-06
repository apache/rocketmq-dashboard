/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */

import client from './client';

export interface CloudRegion {
  regionId: string;
  regionName: string;
}

export interface CloudInstanceOption {
  instanceId: string;
  instanceName: string;
  status: string;
  regionId: string;
  topicCount?: number;
  groupCount?: number;
  remark?: string;
}

export async function listAliyunRegions(credentialId: string) {
  const res = await client.get<{ data: CloudRegion[] }>('/cloud/aliyun/regions', {
    params: { credentialId },
  });
  return res.data.data;
}

export async function listAliyunInstances(credentialId: string, regionId: string, search?: string) {
  const res = await client.get<{ data: CloudInstanceOption[] }>('/cloud/aliyun/instances', {
    params: { credentialId, regionId, ...(search ? { search } : {}) },
  });
  return res.data.data;
}
