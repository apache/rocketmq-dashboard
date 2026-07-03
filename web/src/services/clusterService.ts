import { USE_MOCK } from '../config';
import * as clusterApi from '../api/cluster';
import type { ClusterInfo, K8sCertInfo } from '../api/cluster';
import clusters from '../mock/clusters';

export async function listClusters(): Promise<ClusterInfo[]> {
  if (USE_MOCK) {
    return clusters.map((c) => ({
      id: c.id,
      name: c.name,
      type: c.type,
      status: c.status,
      version: c.version,
      brokers: c.brokers.length,
      proxies: c.proxies.length,
      topics: c.topicCount,
      groups: c.groupCount,
      tpsIn: c.brokers.reduce((s, b) => s + b.tpsIn, 0),
      tpsOut: c.brokers.reduce((s, b) => s + b.tpsOut, 0),
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
  if (USE_MOCK) return []; // certs mock not yet defined
  return clusterApi.listK8sCerts();
}

export async function updateClusterConfig(data: { id: string } & Record<string, unknown>) {
  if (USE_MOCK) return;
  return clusterApi.updateClusterConfig(data);
}

export async function restartBroker(clusterId: string, brokerName: string) {
  if (USE_MOCK) return { success: true, message: `Broker ${brokerName} restarted (mock)` };
  return clusterApi.restartBroker(clusterId, brokerName);
}
