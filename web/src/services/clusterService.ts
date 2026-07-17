import { USE_MOCK } from '../config';
import * as clusterApi from '../api/cluster';
import type { ClusterInfo, K8sCertInfo } from '../api/cluster';
import clusters, { mockK8sCerts } from '../mock/clusters';

const mockCertStore: K8sCertInfo[] = mockK8sCerts.map((cert) => ({
  ...cert,
  san: [...cert.san],
}));

export async function listClusters(): Promise<ClusterInfo[]> {
  if (USE_MOCK) {
    return clusters.map((c) => ({
      id: c.id,
      name: c.name,
      nsClusterName: c.nsClusterName,
      type: c.type,
      endpoint: c.endpoint,
      status: c.status,
      version: c.version,
      brokers: c.brokers.map((broker) => ({ ...broker })),
      proxies: c.proxies.map((proxy) => ({ ...proxy })),
      nameServers: c.nameServers.map((nameServer) => ({ ...nameServer })),
      config: { ...c.config },
      topicCount: c.topicCount,
      groupCount: c.groupCount,
      tpsHistory: [...c.tpsHistory],
    }));
  }
  return clusterApi.listClusters();
}

export async function getCluster(id: string) {
  if (USE_MOCK) {
    const c = clusters.find((c) => c.id === id);
    if (!c) throw new Error('Cluster not found');
    return c;
  }
  return clusterApi.getCluster(id);
}

export async function listK8sCerts(): Promise<K8sCertInfo[]> {
  if (USE_MOCK) return mockCertStore.map((cert) => ({ ...cert, san: [...cert.san] }));
  return clusterApi.listK8sCerts();
}

export async function createK8sCert(data: Partial<K8sCertInfo>): Promise<K8sCertInfo> {
  if (USE_MOCK) {
    const now = new Date();
    const notAfter = new Date(now);
    notAfter.setFullYear(notAfter.getFullYear() + 1);
    const cert: K8sCertInfo = {
      id: `cert-${Date.now()}`,
      name: data.name ?? '',
      namespace: data.namespace ?? '',
      cluster: data.cluster ?? '',
      type: data.type ?? 'TLS',
      issuer: data.issuer ?? '',
      notBefore: now.toISOString(),
      notAfter: notAfter.toISOString(),
      status: 'valid',
      daysRemaining: 365,
      san: data.san ?? [],
    };
    mockCertStore.push(cert);
    return { ...cert, san: [...cert.san] };
  }
  return clusterApi.createK8sCert(data);
}

export async function updateK8sCert(data: Partial<K8sCertInfo>): Promise<K8sCertInfo> {
  if (USE_MOCK) {
    const existing = mockCertStore.find((cert) => cert.id === data.id);
    if (!existing) throw new Error(`Certificate not found: ${data.id}`);
    Object.assign(existing, data, { san: data.san ?? existing.san });
    return { ...existing, san: [...existing.san] };
  }
  return clusterApi.updateK8sCert(data);
}

export async function deleteK8sCert(id: string): Promise<void> {
  if (USE_MOCK) {
    const index = mockCertStore.findIndex((cert) => cert.id === id);
    if (index < 0) throw new Error(`Certificate not found: ${id}`);
    mockCertStore.splice(index, 1);
    return;
  }
  return clusterApi.deleteK8sCert(id);
}

export async function updateClusterConfig(data: { id: string } & Record<string, unknown>) {
  if (USE_MOCK) return;
  return clusterApi.updateClusterConfig(data);
}

export async function restartBroker(clusterId: string, brokerName: string) {
  if (USE_MOCK) return { success: true, message: `Broker ${brokerName} restarted (mock)` };
  return clusterApi.restartBroker(clusterId, brokerName);
}
