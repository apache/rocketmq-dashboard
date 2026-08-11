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

// ─── Interfaces ──────────────────────────────────────────────────

export interface ProxyHomePageData {
  proxyAddrList: string[];
  currentProxyAddr: string;
}

export interface ProxyNode {
  key: string;
  address: string;
  status: 'healthy' | 'unhealthy' | 'warning' | 'unknown';
  version: string | null;
  connections: number | null;
  tps: number | null;
  memory: number | null;
  cpu: number | null;
  uptime: string | null;
  isSelected: boolean;
}

// ─── API Functions ───────────────────────────────────────────────

export async function queryProxyHomePage(clusterId?: string): Promise<ProxyHomePageData> {
  const res = await client.get<{ data: ProxyHomePageData }>('/proxy/homePage.query', {
    params: clusterId ? { clusterId } : undefined,
  });
  return res.data.data;
}

export async function addProxyAddress(clusterId: string, address: string): Promise<void> {
  const body = new URLSearchParams({ clusterId, newProxyAddr: address });
  await client.post('/proxy/addProxyAddr.do', body);
}

export async function removeProxyAddress(clusterId: string, address: string): Promise<void> {
  const body = new URLSearchParams({ clusterId, proxyAddr: address });
  await client.post('/proxy/removeProxyAddr.do', body);
}

/**
 * Trigger a configuration hot-reload for a proxy.
 * Uses the same DTO as restartProxy ({ clusterId, addr }).
 */
export async function reloadProxyConfig(
  clusterId: string,
  addr: string,
): Promise<{ success: boolean }> {
  const res = await client.post<{ data: { success: boolean } }>('/proxies/config/reload', {
    clusterId,
    addr,
  });
  return res.data.data;
}
