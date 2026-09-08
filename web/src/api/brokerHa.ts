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

export interface HaConnection {
  address: string;
  inSync: boolean;
  ackOffset: string;
  differenceBytes: string;
  transferOffset: string;
  bytesPerSecond: string;
}

export interface HaNode {
  brokerId: string;
  address: string;
  error: string | null;
  master: boolean | null;
  maxOffset: string | null;
  inSyncSlaveCount: number | null;
  connections: HaConnection[];
  replica: {
    masterAddress: string | null;
    maxOffset: string;
    masterFlushOffset: string;
    bytesPerSecond: string;
    lastReadTimestamp: number;
    lastWriteTimestamp: number;
  } | null;
}

export interface BrokerHaSnapshot {
  brokerName: string;
  sampledAt: number;
  complete: boolean;
  nodes: HaNode[];
}

export async function inspectBrokerHa(instanceId: string, brokerName: string, signal: AbortSignal) {
  const response = await client.get<{ data: BrokerHaSnapshot }>('/brokers/ha', {
    params: { instanceId, brokerName },
    signal,
  });
  return response.data.data;
}
