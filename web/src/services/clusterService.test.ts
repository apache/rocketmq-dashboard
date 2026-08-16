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

import {
  createK8sCert,
  deleteK8sCert,
  getCluster,
  getNameServerConfigDiff,
  listClusters,
  listK8sCerts,
  updateClusterConfig,
  updateK8sCert,
  updateNameServer,
} from './clusterService';

describe('clusterService mock clusters', () => {
  it('returns defensive copies from cluster detail reads', async () => {
    const cluster = await getCluster('cluster-prod');
    const originalBrokerStatus = cluster.brokers[0].status;
    const originalProxyConnections = cluster.proxies[0].connections;
    const originalNameServerAddr = cluster.nameServers[0].addr;
    const originalFlushDiskType = cluster.config.flushDiskType;
    const originalFirstTps = cluster.tpsHistory[0];

    cluster.brokers[0].status = 'offline';
    cluster.proxies[0].connections = 0;
    cluster.nameServers[0].addr = '127.0.0.1:9876';
    cluster.config.flushDiskType = 'ASYNC_FLUSH';
    cluster.tpsHistory[0] = 0;

    const fresh = await getCluster('cluster-prod');

    expect(fresh.brokers[0].status).toBe(originalBrokerStatus);
    expect(fresh.proxies[0].connections).toBe(originalProxyConnections);
    expect(fresh.nameServers[0].addr).toBe(originalNameServerAddr);
    expect(fresh.config.flushDiskType).toBe(originalFlushDiskType);
    expect(fresh.tpsHistory[0]).toBe(originalFirstTps);
  });

  it('does not share nested references between list and detail reads', async () => {
    const [listed] = await listClusters();
    const detail = await getCluster(listed.id);

    expect(detail).toEqual(listed);
    expect(detail).not.toBe(listed);
    expect(detail.brokers).not.toBe(listed.brokers);
    expect(detail.brokers[0]).not.toBe(listed.brokers[0]);
    expect(detail.proxies).not.toBe(listed.proxies);
    expect(detail.nameServers).not.toBe(listed.nameServers);
    expect(detail.config).not.toBe(listed.config);
    expect(detail.tpsHistory).not.toBe(listed.tpsHistory);
  });

  it('returns a complete mock NameServer drift result for the selected cluster', async () => {
    const result = await getNameServerConfigDiff('cluster-prod');

    expect(result.cluster).toBe('cluster-prod');
    expect(result.nodeCount).toBeGreaterThan(1);
    expect(result.reachableNodeCount).toBe(result.nodeCount);
    expect(result.driftDetected).toBe(true);
    expect(result.differences).toEqual([
      expect.objectContaining({
        key: 'serverWorkerThreads',
        values: expect.arrayContaining([
          expect.objectContaining({ address: expect.any(String), configured: true }),
        ]),
      }),
    ]);
  });

  it('persists partial mock config updates without copying id into config', async () => {
    const before = await getCluster('cluster-prod');
    const originalConfig = { ...before.config };
    const nextQueueCount = originalConfig.writeQueueNums + 1;

    try {
      await updateClusterConfig({
        id: before.id,
        writeQueueNums: nextQueueCount,
      });

      const updated = (await listClusters()).find((cluster) => cluster.id === before.id);
      expect(updated?.config.writeQueueNums).toBe(nextQueueCount);
      expect(updated?.config.readQueueNums).toBe(originalConfig.readQueueNums);
      expect(updated?.config.flushDiskType).toBe(originalConfig.flushDiskType);
      expect(updated?.config).not.toHaveProperty('id');
    } finally {
      await updateClusterConfig({
        id: before.id,
        ...originalConfig,
      });
    }
  });

  it('rejects NameServer edits that would duplicate an address in the same cluster', async () => {
    const clusterId = 'cluster-prod';
    const original = await getCluster(clusterId);
    const [first, second] = original.nameServers;

    try {
      await expect(
        updateNameServer({
          clusterId,
          addr: first.addr,
          newAddr: second.addr,
        }),
      ).rejects.toThrow(`NameServer already exists: ${second.addr}`);

      const fresh = await getCluster(clusterId);
      expect(fresh.nameServers.map((item) => item.addr)).toEqual(
        original.nameServers.map((item) => item.addr),
      );
    } finally {
      const current = await getCluster(clusterId);
      for (let index = 0; index < original.nameServers.length; index += 1) {
        const currentAddr = current.nameServers[index]?.addr;
        const originalAddr = original.nameServers[index].addr;
        if (currentAddr && currentAddr !== originalAddr) {
          await updateNameServer({ clusterId, addr: currentAddr, newAddr: originalAddr });
        }
      }
    }
  });

  it('copies certificate SAN arrays before writing them into the mock store', async () => {
    const san = ['proxy.example.com'];
    const created = await createK8sCert({
      name: 'cert-copy-test',
      cluster: 'cluster-prod',
      san,
    });

    try {
      san.push('mutated-create.example.com');

      let stored = (await listK8sCerts()).items.find((cert) => cert.id === created.id);
      expect(stored?.san).toEqual(['proxy.example.com']);

      const nextSan = ['proxy-next.example.com'];
      await updateK8sCert({
        id: created.id,
        san: nextSan,
      });
      nextSan.push('mutated-update.example.com');

      stored = (await listK8sCerts()).items.find((cert) => cert.id === created.id);
      expect(stored?.san).toEqual(['proxy-next.example.com']);
    } finally {
      await deleteK8sCert(created.id);
    }
  });
});
