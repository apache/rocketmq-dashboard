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
  createNameServer,
  deleteK8sCert,
  deleteNameServer,
  getCluster,
  getNameServerConfigDiff,
  listClusters,
  listK8sCerts,
  previewClusterConfig,
  restartBroker,
  restartNameServer,
  restartProxy,
  updateClusterConfig,
  updateK8sCert,
  updateNameServer,
  upgradeNameServer,
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

  it('previews mock config updates without mutating the cluster', async () => {
    const before = await getCluster('cluster-prod');

    const preview = await previewClusterConfig({
      id: before.id,
      writeQueueNums: before.config.writeQueueNums + 1,
    });

    expect(preview.changed).toBe(true);
    expect(preview.brokerProperties).toMatchObject({
      defaultTopicQueueNums: String(before.config.writeQueueNums + 1),
    });
    expect(preview.targetBrokers.map((broker) => broker.address)).toEqual(
      before.brokers.map((broker) => broker.addr),
    );
    expect(preview.changes).toEqual([
      expect.objectContaining({
        field: 'writeQueueNums',
        brokerProperty: 'defaultTopicQueueNums',
      }),
    ]);
    await expect(getCluster('cluster-prod')).resolves.toMatchObject({ config: before.config });
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

  it('returns a lifecycle result when restarting a mock broker', async () => {
    const cluster = await getCluster('cluster-prod');
    const broker = cluster.brokers[0];

    await expect(restartBroker(cluster.id, broker.name)).resolves.toMatchObject({
      operation: 'BROKER_RESTART',
      clusterId: cluster.id,
      target: broker.name,
      accepted: true,
    });
  });

  it('returns a lifecycle result when restarting a mock NameServer', async () => {
    const cluster = await getCluster('cluster-prod');
    const nameServer = cluster.nameServers[0];

    await expect(restartNameServer({ clusterId: cluster.id, addr: nameServer.addr })).resolves
      .toMatchObject({
        operation: 'NAMESERVER_RESTART',
        clusterId: cluster.id,
        target: nameServer.addr,
        accepted: true,
      });
  });

  it('returns a lifecycle result when upgrading a mock NameServer', async () => {
    const cluster = await getCluster('cluster-prod');
    const nameServer = cluster.nameServers[0];

    await expect(upgradeNameServer({
      clusterId: cluster.id,
      addr: nameServer.addr,
      version: '5.4.0',
    })).resolves.toMatchObject({
      operation: 'NAMESERVER_UPGRADE',
      clusterId: cluster.id,
      target: nameServer.addr,
      accepted: true,
    });
  });

  it('returns a lifecycle result when deleting a mock NameServer', async () => {
    const cluster = await getCluster('cluster-prod');
    const nameServer = cluster.nameServers[0];

    try {
      const deleteResult = await deleteNameServer({ clusterId: cluster.id, addr: nameServer.addr });

      expect(deleteResult).toMatchObject({
        operation: 'NAMESERVER_DELETE',
        clusterId: cluster.id,
        target: nameServer.addr,
        accepted: true,
      });
      expect(deleteResult.requestId).toMatch(/^mock-/);
      expect(deleteResult.message).toContain(nameServer.addr);
    } finally {
      await createNameServer({ clusterId: cluster.id, addr: nameServer.addr });
    }
  });

  it('returns a lifecycle result and records pending health when creating a mock NameServer', async () => {
    const cluster = await getCluster('cluster-prod');
    const addr = '10.101.2.99:9876';

    try {
      const createResult = await createNameServer({
        clusterId: cluster.id,
        addr,
        version: '5.4.0',
      });

      expect(createResult).toMatchObject({
        operation: 'NAMESERVER_CREATE',
        clusterId: cluster.id,
        target: addr,
        accepted: true,
      });
      expect(createResult.requestId).toMatch(/^mock-/);
      expect(createResult.message).toContain(addr);
      const updated = await getCluster(cluster.id);
      expect(updated.nameServers.find((item) => item.addr === addr)).toMatchObject({
        addr,
        status: 'warning',
      });
    } finally {
      const current = await getCluster(cluster.id);
      if (current.nameServers.some((item) => item.addr === addr)) {
        await deleteNameServer({ clusterId: cluster.id, addr });
      }
    }
  });

  it('returns a lifecycle result when updating a mock NameServer address', async () => {
    const cluster = await getCluster('cluster-prod');
    const originalAddr = cluster.nameServers[0].addr;
    const newAddr = '10.101.2.98:9876';

    try {
      const updateResult = await updateNameServer({
        clusterId: cluster.id,
        addr: originalAddr,
        newAddr,
        version: '5.4.0',
      });

      expect(updateResult).toMatchObject({
        operation: 'NAMESERVER_UPDATE',
        clusterId: cluster.id,
        target: originalAddr,
        accepted: true,
      });
      expect(updateResult.requestId).toMatch(/^mock-/);
      const updated = await getCluster(cluster.id);
      expect(updated.nameServers.some((item) => item.addr === originalAddr)).toBe(false);
      expect(updated.nameServers.find((item) => item.addr === newAddr)).toMatchObject({
        addr: newAddr,
        status: cluster.nameServers[0].status,
      });
    } finally {
      const current = await getCluster(cluster.id);
      if (current.nameServers.some((item) => item.addr === newAddr)) {
        await updateNameServer({ clusterId: cluster.id, addr: newAddr, newAddr: originalAddr });
      }
    }
  });

  it('returns a lifecycle result when restarting a mock Proxy', async () => {
    const cluster = await getCluster('cluster-prod');
    const proxy = cluster.proxies[0];

    await expect(restartProxy({ clusterId: cluster.id, addr: proxy.addr })).resolves.toMatchObject({
      operation: 'PROXY_RESTART',
      clusterId: cluster.id,
      target: proxy.addr,
      accepted: true,
    });
  });

  it('copies certificate SAN arrays before writing them into the mock store', async () => {
    const san = ['proxy.example.com'];
    const created = await createK8sCert({
      k8sId: 'cert-copy-test',
      cluster: 'cluster-prod',
      san,
    });

    try {
      san.push('mutated-create.example.com');

      let stored = (await listK8sCerts()).find((cert) => cert.id === created.id);
      expect(stored?.san).toEqual(['proxy.example.com']);

      const nextSan = ['proxy-next.example.com'];
      await updateK8sCert({
        id: created.id,
        san: nextSan,
      });
      nextSan.push('mutated-update.example.com');

      stored = (await listK8sCerts()).find((cert) => cert.id === created.id);
      expect(stored?.san).toEqual(['proxy-next.example.com']);
    } finally {
      await deleteK8sCert(created.id);
    }
  });
});
