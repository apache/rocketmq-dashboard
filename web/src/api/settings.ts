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
export interface GeneralSettings {
  theme: string;
  compact: boolean;
  desktopNotify: boolean;
  notifySound: boolean;
  sessionTimeout: number;
  requireLogin: boolean;
  llmProvider: string;
  apiKeyConfigured: boolean;
  model: string;
  baseUrl: string;
  dingtalkWebhook?: string;
  emailRecipients?: string;
  smsWebhook?: string;
}

export type GeneralSettingsUpdate = Omit<GeneralSettings, 'apiKeyConfigured'> & {
  apiKey?: string;
  clearApiKey?: boolean;
};

export interface DataSource {
  key: string;
  name: string;
  type: string;
  url: string;
  auth: string;
  username?: string;
  password?: string;
  bearerToken?: string;
  status?: string | null;
  instanceIds?: string[];
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

// ─── General Settings ───────────────────────────────────────────
export async function getGeneralSettings() {
  const res = await client.get<{ data: GeneralSettings }>('/settings/general');
  return res.data.data;
}

export async function saveGeneralSettings(data: GeneralSettingsUpdate) {
  const payload = { ...data } as GeneralSettingsUpdate & { apiKeyConfigured?: boolean };
  delete payload.apiKeyConfigured;
  if (!payload.apiKey?.trim()) delete payload.apiKey;
  await client.post('/settings/general/save', payload);
}

// ─── Data Sources ───────────────────────────────────────────────
export async function listDataSourcePage(params?: { page?: number; pageSize?: number }) {
  const res = await client.get<{ data: PageResult<DataSource> }>('/settings/datasources', {
    params,
  });
  return res.data.data;
}

export async function listDataSources() {
  const pageSize = 100;
  let page = 1;
  const items: DataSource[] = [];
  while (true) {
    const result = await listDataSourcePage({ page, pageSize });
    items.push(...result.items);
    if (!result.items.length || items.length >= result.total) {
      return items;
    }
    page += 1;
  }
}

export async function createDataSource(data: Partial<DataSource>) {
  const res = await client.post<{ data: DataSource }>('/settings/datasources/create', data);
  return res.data.data;
}

export async function updateDataSource(data: Partial<DataSource>) {
  const res = await client.post<{ data: DataSource }>('/settings/datasources/update', data);
  return res.data.data;
}

export async function deleteDataSource(key: string) {
  await client.post('/settings/datasources/delete', undefined, { params: { key } });
}

export async function testDataSource(data: {
  type: string;
  url: string;
  auth?: string;
  username?: string;
  password?: string;
  bearerToken?: string;
}) {
  const res = await client.post<{ data: { success: boolean; message: string } }>(
    '/settings/datasources/test',
    data,
  );
  return res.data.data;
}
