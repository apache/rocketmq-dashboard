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

import { describe, expect, it, vi } from 'vitest';

vi.mock('./dataMode', () => ({ isMockMode: () => true }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));

import { listConnections } from './connectionsService';

describe('connectionsService mock connections', () => {
  it('isolates client inventories by the required NameServer address', async () => {
    const production = await listConnections({ namesrvAddr: '10.101.2.1:9876' });
    const preproduction = await listConnections({ namesrvAddr: '10.102.5.1:9876' });

    expect(production).not.toHaveLength(0);
    expect(preproduction).not.toHaveLength(0);
    expect(production.every((connection) => connection.clusterName === 'ns-prod')).toBe(true);
    expect(preproduction.every((connection) => connection.clusterName === 'ns-pre')).toBe(true);
    expect(new Set(production.map((connection) => connection.clientId))).not.toEqual(
      new Set(preproduction.map((connection) => connection.clientId)),
    );
  });

  it('returns an empty inventory for an unknown NameServer address', async () => {
    await expect(listConnections({ namesrvAddr: '10.103.9.1:9876' })).resolves.toEqual([]);
  });

  it('requires the same NameServer parameter as the real endpoint', async () => {
    await expect(listConnections()).rejects.toThrow('namesrvAddr is required');
    await expect(listConnections({ namesrvAddr: '  ' })).rejects.toThrow('namesrvAddr is required');
  });

  it('returns defensive copies after applying NameServer, cluster, and type filters', async () => {
    const connections = await listConnections({
      namesrvAddr: '10.101.2.1:9876',
      clusterId: 'ns-prod',
      type: 'Consumer',
    });
    const originalClientId = connections[0].clientId;
    const originalAddress = connections[0].address;

    connections[0].clientId = 'mutated-client';
    connections[0].address = '127.0.0.1:8081';

    const fresh = await listConnections({
      namesrvAddr: '10.101.2.1:9876',
      clusterId: 'ns-prod',
      type: 'Consumer',
    });

    expect(fresh[0].clientId).toBe(originalClientId);
    expect(fresh[0].address).toBe(originalAddress);
    expect(fresh[0]).not.toBe(connections[0]);
    expect(fresh.every((connection) => connection.clusterName === 'ns-prod')).toBe(true);
    expect(fresh.every((connection) => connection.type === 'Consumer')).toBe(true);
  });

  it('does not leak another NameServer when a conflicting cluster filter is supplied', async () => {
    await expect(
      listConnections({ namesrvAddr: '10.101.2.1:9876', clusterId: 'ns-pre' }),
    ).resolves.toEqual([]);
  });
});
