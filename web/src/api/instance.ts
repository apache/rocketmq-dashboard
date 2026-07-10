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
export interface Instance {
  id: string;
  name: string;
  remark: string;
  type: 'PROXY' | 'DIRECT';
  endpoint: string;
  topicCount: number;
  consumerGroupCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateInstanceRequest {
  name: string;
  type: 'PROXY' | 'DIRECT';
  endpoint: string;
  remark?: string;
}

export interface UpdateInstanceRequest {
  id: string;
  name?: string;
  type?: 'PROXY' | 'DIRECT';
  endpoint?: string;
  remark?: string;
}

// ─── Instance API ──────────────────────────────────────────────
// Backend: ProxyController at /proxy
// GET  /proxy/homePage.query       → proxy home page (instance list)
// POST /proxy/addProxyAddr.do      → add proxy address
// POST /proxy/updateProxyAddr.do   → update proxy address
//
// Note: The backend "instance" concept maps to ProxyController.
// These endpoints are kept for mock compatibility until the backend
// provides full CRUD for instances.

export async function listInstances() {
  // No direct backend endpoint for instance list; use proxy homePage
  const res = await client.get('/proxy/homePage.query');
  return res.data;
}

export async function createInstance(data: CreateInstanceRequest) {
  const res = await client.post('/proxy/addProxyAddr.do', null, {
    params: { proxyAddr: data.endpoint },
  });
  return res.data;
}

export async function updateInstance(data: UpdateInstanceRequest) {
  const res = await client.post('/proxy/updateProxyAddr.do', null, {
    params: { proxyAddr: data.endpoint },
  });
  return res.data;
}

export async function deleteInstance(id: string) {
  // No direct delete endpoint in ProxyController
  // Kept for mock compatibility
  await client.post('/instances/delete', { id });
}
