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
  dingtalkSigningSecret?: string;
  clearDingtalkSigningSecret?: boolean;
  dingtalkWebhookConfigured?: boolean;
  dingtalkSigningSecretConfigured?: boolean;
  emailRecipients?: string;
  smsWebhook?: string;
  smsWebhookConfigured?: boolean;
}

export type GeneralSettingsUpdate = Omit<
  GeneralSettings,
  | 'apiKeyConfigured'
  | 'dingtalkWebhookConfigured'
  | 'dingtalkSigningSecretConfigured'
  | 'smsWebhookConfigured'
> & {
  apiKey?: string;
  clearApiKey?: boolean;
  clearDingtalkSigningSecret?: boolean;
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

export interface DataSourcePage {
  items: DataSource[];
  total: number;
  page: number;
  size: number;
}

const DATA_SOURCE_EXPORT_PAGE_SIZE = 100;
const DATA_SOURCE_MAX_EXPORT_PAGES = 100;

// ─── General Settings ───────────────────────────────────────────
export async function getGeneralSettings() {
  const res = await client.get<{ data: GeneralSettings }>('/settings/general');
  return res.data.data;
}

export async function saveGeneralSettings(data: GeneralSettingsUpdate) {
  const payload = {
    ...data,
  } as GeneralSettingsUpdate & {
    apiKeyConfigured?: boolean;
    dingtalkWebhookConfigured?: boolean;
    dingtalkSigningSecretConfigured?: boolean;
    smsWebhookConfigured?: boolean;
  };
  delete payload.apiKeyConfigured;
  delete payload.dingtalkWebhookConfigured;
  delete payload.dingtalkSigningSecretConfigured;
  delete payload.smsWebhookConfigured;
  if (!payload.apiKey?.trim()) delete payload.apiKey;
  await client.post('/settings/general/save', payload);
}

export async function testNotification(channel: 'dingtalk' | 'email' | 'sms') {
  await client.post('/settings/general/test-notification', null, { params: { channel } });
}

// ─── Data Sources ───────────────────────────────────────────────
export async function listDataSources() {
  const res = await client.get<{ data: DataSource[] }>('/settings/datasources');
  return res.data.data;
}

export async function listDataSourcesPage(params: {
  search?: string;
  type?: string;
  page?: number;
  pageSize?: number;
}) {
  const res = await client.get<{ data: DataSourcePage }>('/settings/datasources/page', {
    params,
  });
  return res.data.data;
}

export async function listAllDataSources(params: { search?: string; type?: string } = {}) {
  const allDataSources: DataSource[] = [];
  let page = 1;

  while (page <= DATA_SOURCE_MAX_EXPORT_PAGES) {
    const result = await listDataSourcesPage({
      ...params,
      page,
      pageSize: DATA_SOURCE_EXPORT_PAGE_SIZE,
    });
    allDataSources.push(...result.items);
    const total = result.total ?? allDataSources.length;
    if (result.items.length === 0 || allDataSources.length >= total) {
      return allDataSources;
    }
    page += 1;
  }

  throw new Error(`Data source export exceeded ${DATA_SOURCE_MAX_EXPORT_PAGES} pages`);
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
