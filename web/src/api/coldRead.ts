/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

export interface ColdReadGroup {
  name: string;
  configuredThreshold: string | null;
  adaptiveThreshold: string | null;
  coldBytes: string | null;
  lastReadMillis: string | null;
}

export interface ColdReadSnapshot {
  brokerName: string;
  address: string;
  sampledAt: number;
  enabled: string | null;
  adaptiveEnabled: string | null;
  defaultThreshold: string | null;
  globalThreshold: string | null;
  globalBytes: string | null;
  groups: ColdReadGroup[];
}

export interface ColdReadCommand {
  instanceId: string;
  brokerName: string;
  group: string;
  action: 'SET' | 'REMOVE';
  threshold?: string;
}

export interface ColdReadReceipt {
  address: string;
  group: string;
  action: 'SET' | 'REMOVE';
  threshold: string | null;
  verified: boolean;
  verificationError: string | null;
}

export async function inspectColdRead(instanceId: string, brokerName: string, signal: AbortSignal) {
  const res = await client.get<{ data: ColdReadSnapshot }>('/brokers/cold-read', {
    params: { instanceId, brokerName },
    signal,
  });
  return res.data.data;
}

export async function changeColdRead(command: ColdReadCommand) {
  const res = await client.post<{ data: ColdReadReceipt }>('/brokers/cold-read/config', command);
  return res.data.data;
}
