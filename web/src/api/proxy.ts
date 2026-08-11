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

export async function queryProxyHomePage(instanceId: string): Promise<ProxyHomePageData> {
  const res = await client.get<{ data: ProxyHomePageData }>('/proxy/homePage.query', {
    params: { instanceId },
  });
  return res.data.data;
}
