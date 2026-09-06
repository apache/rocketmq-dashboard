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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as connApi from '../api/connections';

const isMockModeMock = vi.hoisted(() => ({ isMockMode: () => true as boolean }));
vi.mock('./dataMode', () => isMockModeMock);
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));

import { listConnections } from './connectionsService';

describe('connectionsService mock connections', () => {
  beforeEach(() => {
    isMockModeMock.isMockMode = () => true;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

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

  it('normalizes optional filters like the real endpoint', async () => {
    const padded = await listConnections({
      namesrvAddr: '10.101.2.1:9876',
      clusterId: ' ns-prod ',
      type: ' Consumer ',
    });
    const blank = await listConnections({
      namesrvAddr: '10.101.2.1:9876',
      clusterId: '  ',
      type: '  ',
    });

    expect(padded).not.toHaveLength(0);
    expect(padded.every((connection) => connection.clusterName === 'ns-prod')).toBe(true);
    expect(padded.every((connection) => connection.type === 'Consumer')).toBe(true);
    expect(blank).not.toHaveLength(0);
  });

  it('accepts a padded NameServer address before resolving the owning cluster', async () => {
    const padded = await listConnections({ namesrvAddr: '  10.102.5.1:9876  ' });

    expect(padded).not.toHaveLength(0);
    expect(padded.every((connection) => connection.clusterName === 'ns-pre')).toBe(true);
  });

  it('narrows the preproduction inventory when a Producer type filter is supplied', async () => {
    const producers = await listConnections({
      namesrvAddr: '10.102.5.1:9876',
      type: 'Producer',
    });

    expect(producers).not.toHaveLength(0);
    expect(producers.every((connection) => connection.clusterName === 'ns-pre')).toBe(true);
    expect(producers.every((connection) => connection.type === 'Producer')).toBe(true);
    expect(producers.map((connection) => connection.groupOrTopic)).toEqual(
      expect.arrayContaining(['metrics-raw', 'binlog-event']),
    );
  });

  it('does not mistake a protocol value for a connection type', async () => {
    const remotingProducers = await listConnections({
      namesrvAddr: '10.101.2.1:9876',
      type: 'Remoting',
    });

    expect(remotingProducers).toEqual([]);
    const actual = await listConnections({
      namesrvAddr: '10.101.2.1:9876',
      type: 'Producer',
    });
    expect(actual.some((connection) => connection.protocol === 'Remoting')).toBe(true);
  });

  it('delegates to the api in real mode when mock data mode is disabled', async () => {
    isMockModeMock.isMockMode = () => false;
    const remoteConnection: connApi.ClientConnection = {
      clientId: 'remote-0@10.200.1.1:6000',
      type: 'Consumer',
      groupOrTopic: 'cg-remote-sync',
      protocol: 'gRPC',
      address: '10.200.1.1:6000',
      language: 'Java',
      version: '5.0.7',
      connectedAt: '2026-07-02 09:00:00',
      clusterName: 'ns-prod',
    };
    const listSpy = vi
      .spyOn(connApi, 'listConnections')
      .mockResolvedValue([remoteConnection]);

    const result = await listConnections({ namesrvAddr: '10.101.2.1:9876' });

    expect(listSpy).toHaveBeenCalledTimes(1);
    expect(result).toEqual([remoteConnection]);
  });

  it('forwards the caller query verbatim to the api in real mode', async () => {
    isMockModeMock.isMockMode = () => false;
    const params: connApi.ClientConnectionQuery = {
      namesrvAddr: '10.101.2.1:9876',
      clusterId: 'ns-prod',
      type: 'Producer',
    };
    const listSpy = vi.spyOn(connApi, 'listConnections').mockResolvedValue([]);

    await listConnections(params);

    expect(listSpy).toHaveBeenCalledWith(params);
  });

  it('surfaces api results for a NameServer the mock inventory has never seen', async () => {
    isMockModeMock.isMockMode = () => false;
    const remoteConnection: connApi.ClientConnection = {
      clientId: 'isolated-0@10.210.9.9:7000',
      type: 'Consumer',
      groupOrTopic: 'cg-isolated',
      protocol: 'gRPC',
      address: '10.210.9.9:7000',
      language: 'Go',
      version: '5.0.3',
      connectedAt: '2026-07-02 10:00:00',
      clusterName: 'ns-edge',
    };
    const listSpy = vi
      .spyOn(connApi, 'listConnections')
      .mockResolvedValue([remoteConnection]);

    const result = await listConnections({ namesrvAddr: '10.103.9.1:9876' });

    expect(listSpy).toHaveBeenCalledTimes(1);
    expect(result).toEqual([remoteConnection]);
  });
});
