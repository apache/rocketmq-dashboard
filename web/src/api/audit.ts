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
import type { AuditQuery } from './ops';

export type AuditFilter = Omit<AuditQuery, 'page' | 'pageSize'>;

export interface AuditFilterOptions {
  operationTypes: string[];
  resourceTypes: string[];
  clusterIds: string[];
  results: string[];
}

export async function fetchAuditFilterOptions(): Promise<AuditFilterOptions> {
  const res = await client.get<{ data: AuditFilterOptions }>('/audit-logs/filter-options');
  return res.data.data;
}

export async function exportAuditLogs(params?: AuditFilter): Promise<string> {
  const res = await client.get<{ data: string }>('/audit-logs/export', { params });
  return res.data.data;
}
