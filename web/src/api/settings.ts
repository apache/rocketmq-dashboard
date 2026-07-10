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
  siteName: string;
  language: string;
  theme: string;
  autoRefreshInterval: number;
  notificationChannels: string[];
  llmProvider: string;
  llmModel: string;
}

export interface DataSource {
  id: string;
  name: string;
  type: string;
  url: string;
  isDefault: boolean;
  status: string;
}

// ─── Settings API ───────────────────────────────────────────────
// Note: The backend OpsController provides some ops-related settings.
// General settings and data sources have no direct backend equivalent yet.
// These endpoints are kept for mock compatibility.

// ─── General Settings ───────────────────────────────────────────
export async function getGeneralSettings() {
  // No direct backend endpoint; use ops/homePage.query for some settings
  const res = await client.get('/settings/general');
  return res.data;
}

export async function saveGeneralSettings(data: Partial<GeneralSettings>) {
  await client.post('/settings/general/save', data);
}

// ─── Data Sources ───────────────────────────────────────────────
export async function listDataSources() {
  const res = await client.get('/settings/datasources');
  return res.data;
}

export async function createDataSource(data: Partial<DataSource>) {
  await client.post('/settings/datasources/create', data);
}

export async function updateDataSource(data: Partial<DataSource>) {
  await client.post('/settings/datasources/update', data);
}

export async function deleteDataSource(id: string) {
  await client.post('/settings/datasources/delete', { id });
}

export async function testDataSource(data: { type: string; url: string }) {
  const res = await client.post<{ success: boolean; message: string }>(
    '/settings/datasources/test',
    data,
  );
  return res.data;
}
