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

// Matches mock/clients.ts
export interface ClientConnection {
  clientId?: string | null;
  type: string;
  groupOrTopic: string;
  protocol: string | null;
  address?: string | null;
  language?: string | null;
  version: string | null;
  connectedAt?: string | null;
  partial?: boolean;
  clusterName: string;
}

export interface ClientConnectionQuery {
  namesrvAddr: string;
  clusterId?: string;
  type?: string;
}

export async function listConnections(params?: ClientConnectionQuery) {
  const res = await client.get<{ data: ClientConnection[] }>('/clients', { params });
  return res.data.data;
}
