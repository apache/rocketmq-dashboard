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
  status: string;
  instanceIds?: string[];
}

export interface SslSettings {
  enabled: boolean;
  protocol: string;
  clientAuth: string;
  keyStoreType: string;
  keyStorePath: string;
  keyStorePasswordConfigured: boolean;
  trustStoreType: string;
  trustStorePath: string;
  trustStorePasswordConfigured: boolean;
  restartRequired: boolean;
}

export type SslSettingsUpdate = Omit<
  SslSettings,
  'keyStorePasswordConfigured' | 'trustStorePasswordConfigured' | 'restartRequired'
> & {
  keyStorePassword?: string;
  clearKeyStorePassword?: boolean;
  trustStorePassword?: string;
  clearTrustStorePassword?: boolean;
};

export interface SslSettingsValidationResult {
  success: boolean;
  message: string;
  warnings: string[];
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

// ─── SSL Settings ────────────────────────────────────────────────
export async function getSslSettings() {
  const res = await client.get<{ data: SslSettings }>('/settings/ssl');
  return res.data.data;
}

export async function saveSslSettings(data: SslSettingsUpdate) {
  const payload = cleanSslSettingsPayload(data);
  const res = await client.post<{ data: SslSettings }>('/settings/ssl/save', payload);
  return res.data.data;
}

export async function validateSslSettings(data: SslSettingsUpdate) {
  const payload = cleanSslSettingsPayload(data);
  const res = await client.post<{ data: SslSettingsValidationResult }>(
    '/settings/ssl/validate',
    payload,
  );
  return res.data.data;
}

function cleanSslSettingsPayload(data: SslSettingsUpdate) {
  const payload = { ...data };
  if (!payload.keyStorePassword?.trim()) delete payload.keyStorePassword;
  if (!payload.trustStorePassword?.trim()) delete payload.trustStorePassword;
  return payload;
}

// ─── Data Sources ───────────────────────────────────────────────
export async function listDataSources() {
  const res = await client.get<{ data: DataSource[] }>('/settings/datasources');
  return res.data.data;
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
