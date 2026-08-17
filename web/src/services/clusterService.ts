import { isMockMode } from './dataMode';
import * as clusterApi from '../api/cluster';
import type {
  ClusterConfig,
  ClusterConfigUpdateResult,
  ClusterInfo,
  ClusterProbeResult,
  K8sCertInfo,
  NameServerConfigDiffResult,
} from '../api/cluster';
import clusters, { mockK8sCerts } from '../mock/clusters';

const mockCertStore: K8sCertInfo[] = mockK8sCerts.map((cert) => ({
  ...cert,
  san: [...cert.san],
}));

function copyCluster(cluster: ClusterInfo): ClusterInfo {
  return {
    id: cluster.id,
    name: cluster.name,
    nsClusterName: cluster.nsClusterName,
    type: cluster.type,
    endpoint: cluster.endpoint,
    status: cluster.status,
    version: cluster.version,
    brokers: cluster.brokers.map((broker) => ({ ...broker })),
    proxies: cluster.proxies.map((proxy) => ({ ...proxy })),
    nameServers: cluster.nameServers.map((nameServer) => ({ ...nameServer })),
    config: { ...cluster.config },
    topicCount: cluster.topicCount,
    groupCount: cluster.groupCount,
    tpsHistory: [...cluster.tpsHistory],
  };
}

export async function listClusters(instanceId?: number): Promise<ClusterInfo[]> {
  if (isMockMode()) {
    return clusters.map(copyCluster);
  }
  return clusterApi.listClusters(instanceId);
}

export async function testClusterConnection(namesrvAddr: string): Promise<ClusterProbeResult> {
  if (isMockMode()) {
    const trimmed = namesrvAddr.trim();
    const cluster = clusters[0];
    const brokerNames = cluster ? cluster.brokers.map((broker) => broker.name) : [];
    return {
      connected: true,
      namesrvAddr: trimmed,
      clusterName: cluster?.nsClusterName ?? 'DefaultCluster',
      brokerCount: brokerNames.length,
      brokerNames,
      elapsedMillis: 12,
      message: `Connected to ${brokerNames.length} broker(s) (mock)`,
    };
  }
  return clusterApi.testClusterConnection(namesrvAddr);
}

export async function getCluster(id: string, instanceId?: number): Promise<ClusterInfo> {
  if (isMockMode()) {
    const cluster = clusters.find((item) => item.id === id);
    if (!cluster) throw new Error('Cluster not found');
    return copyCluster(cluster);
  }
  return clusterApi.getCluster(id, instanceId);
}

export async function getNameServerConfigDiff(
  clusterId: string,
  instanceId?: number,
): Promise<NameServerConfigDiffResult> {
  if (!isMockMode()) return clusterApi.getNameServerConfigDiff(clusterId, instanceId);

  const cluster = getMockCluster(clusterId);
  const nodes = cluster.nameServers.map((nameServer) => ({
    address: nameServer.addr,
    reachable: nameServer.status !== 'offline',
  }));
  const reachableAddresses = nodes.filter((node) => node.reachable).map((node) => node.address);
  const driftDetected = cluster.id === 'cluster-prod' && reachableAddresses.length > 1;

  return {
    cluster: cluster.id,
    complete: reachableAddresses.length === nodes.length,
    driftDetected,
    nodeCount: nodes.length,
    reachableNodeCount: reachableAddresses.length,
    comparedKeys: ['listenPort', 'serverWorkerThreads', 'clientRequestThreadPoolNums'],
    nodes,
    differences: driftDetected
      ? [
          {
            key: 'serverWorkerThreads',
            values: reachableAddresses.map((address, index) => ({
              address,
              configured: true,
              value: index === 0 ? '8' : '12',
            })),
          },
        ]
      : [],
  };
}

export async function listK8sCerts(): Promise<K8sCertInfo[]> {
  if (isMockMode()) return mockCertStore.map((cert) => ({ ...cert, san: [...cert.san] }));
  return clusterApi.listK8sCerts();
}

export async function createK8sCert(data: Partial<K8sCertInfo>): Promise<K8sCertInfo> {
  if (isMockMode()) {
    const now = new Date();
    const notAfter = new Date(now);
    notAfter.setFullYear(notAfter.getFullYear() + 1);
    const cert: K8sCertInfo = {
      id: Date.now(),
      name: data.name ?? '',
      cluster: data.cluster ?? '',
      type: data.type ?? 'TLS',
      issuer: data.issuer ?? '',
      notBefore: now.toISOString(),
      notAfter: notAfter.toISOString(),
      status: 'valid',
      daysRemaining: 365,
      san: [...(data.san ?? [])],
    };
    mockCertStore.push(cert);
    return { ...cert, san: [...cert.san] };
  }
  return clusterApi.createK8sCert(data);
}

export async function updateK8sCert(data: Partial<K8sCertInfo>): Promise<K8sCertInfo> {
  if (isMockMode()) {
    const existing = mockCertStore.find((cert) => cert.id === data.id);
    if (!existing) throw new Error(`Certificate not found: ${data.id}`);
    Object.assign(existing, data, { san: data.san ? [...data.san] : existing.san });
    return { ...existing, san: [...existing.san] };
  }
  return clusterApi.updateK8sCert(data);
}

export async function renewK8sCert(id: number): Promise<K8sCertInfo> {
  if (isMockMode()) {
    const existing = mockCertStore.find((cert) => cert.id === id);
    if (!existing) throw new Error(`Certificate not found: ${id}`);
    const now = new Date();
    const notAfter = new Date(now);
    notAfter.setFullYear(notAfter.getFullYear() + 1);
    Object.assign(existing, {
      notBefore: now.toISOString(),
      notAfter: notAfter.toISOString(),
      status: 'valid',
      daysRemaining: Math.round((notAfter.getTime() - now.getTime()) / (24 * 60 * 60 * 1000)),
    });
    return { ...existing, san: [...existing.san] };
  }
  return clusterApi.renewK8sCert(id);
}

export async function deleteK8sCert(id: number): Promise<void> {
  if (isMockMode()) {
    const index = mockCertStore.findIndex((cert) => cert.id === id);
    if (index < 0) throw new Error(`Certificate not found: ${id}`);
    mockCertStore.splice(index, 1);
    return;
  }
  return clusterApi.deleteK8sCert(id);
}

export async function updateClusterConfig(
  data: { id: string; instanceId?: number } & Partial<ClusterConfig>,
) {
  if (isMockMode()) {
    const { id, ...config } = data;
    const cluster = getMockCluster(id);
    Object.assign(cluster.config, config);
    return {
      cluster: copyCluster(cluster),
      status: 'SUCCESS',
      successfulBrokers: cluster.brokers.map((broker) => broker.addr),
      failedBrokers: [],
    } satisfies ClusterConfigUpdateResult;
  }
  return clusterApi.updateClusterConfig(data);
}

export async function restartBroker(clusterId: string, brokerName: string) {
  if (isMockMode()) return { success: true, message: `Broker ${brokerName} restarted (mock)` };
  return clusterApi.restartBroker(clusterId, brokerName);
}

function getMockCluster(clusterId: string) {
  const cluster = clusters.find((item) => item.id === clusterId);
  if (!cluster) throw new Error(`Cluster not found: ${clusterId}`);
  return cluster;
}

export async function restartNameServer(data: { clusterId: string; addr: string }): Promise<void> {
  if (isMockMode()) {
    const nameServer = getMockCluster(data.clusterId).nameServers.find(
      (item) => item.addr === data.addr,
    );
    if (!nameServer) throw new Error(`NameServer not found: ${data.addr}`);
    nameServer.status = 'healthy';
    return;
  }
  return clusterApi.restartNameServer(data);
}

export async function upgradeNameServer(data: {
  clusterId: string;
  addr: string;
  version: string;
}): Promise<void> {
  if (isMockMode()) {
    const exists = getMockCluster(data.clusterId).nameServers.some(
      (item) => item.addr === data.addr,
    );
    if (!exists) throw new Error(`NameServer not found: ${data.addr}`);
    return;
  }
  return clusterApi.upgradeNameServer(data);
}

export async function deleteNameServer(data: { clusterId: string; addr: string }): Promise<void> {
  if (isMockMode()) {
    const nameServers = getMockCluster(data.clusterId).nameServers;
    const index = nameServers.findIndex((item) => item.addr === data.addr);
    if (index < 0) throw new Error(`NameServer not found: ${data.addr}`);
    nameServers.splice(index, 1);
    return;
  }
  return clusterApi.deleteNameServer(data);
}

export async function createNameServer(data: { clusterId: string; addr: string }): Promise<void> {
  if (isMockMode()) {
    const nameServers = getMockCluster(data.clusterId).nameServers;
    if (nameServers.some((item) => item.addr === data.addr)) {
      throw new Error(`NameServer already exists: ${data.addr}`);
    }
    nameServers.push({ addr: data.addr, status: 'healthy' });
    return;
  }
  return clusterApi.createNameServer(data);
}

export async function updateNameServer(data: {
  clusterId: string;
  addr: string;
  newAddr?: string;
}): Promise<void> {
  if (isMockMode()) {
    const nameServers = getMockCluster(data.clusterId).nameServers;
    const nameServer = nameServers.find((item) => item.addr === data.addr);
    if (!nameServer) throw new Error(`NameServer not found: ${data.addr}`);
    if (data.newAddr && data.newAddr !== data.addr) {
      const duplicate = nameServers.some(
        (item) => item !== nameServer && item.addr === data.newAddr,
      );
      if (duplicate) throw new Error(`NameServer already exists: ${data.newAddr}`);
    }
    if (data.newAddr) nameServer.addr = data.newAddr;
    return;
  }
  return clusterApi.updateNameServer(data);
}

export async function restartProxy(data: { clusterId: string; addr: string }): Promise<void> {
  if (isMockMode()) {
    const proxy = getMockCluster(data.clusterId).proxies.find((item) => item.addr === data.addr);
    if (!proxy) throw new Error(`Proxy not found: ${data.addr}`);
    proxy.status = 'healthy';
    return;
  }
  return clusterApi.restartProxy(data);
}
