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

const pathSegment = (value: string): string => encodeURIComponent(value);

// ─── Types ──────────────────────────────────────────────────────
export interface ClusterInfo {
  id: string;
  name: string;
  nsClusterName: string;
  type: string;
  endpoint: string;
  status: string;
  version: string;
  brokers: BrokerInfo[];
  proxies: ProxyInfo[];
  nameServers: NameServerInfo[];
  config: ClusterConfig;
  topicCount: number;
  groupCount: number;
  tpsHistory: number[];
}

export interface BrokerInfo {
  addr: string;
  name: string;
  status: string;
  tpsIn: number;
  tpsOut: number;
  diskUsage: number;
  version?: string | null;
  runtimeStatsAvailable?: boolean;
}

export interface ProxyInfo {
  addr: string;
  status: string;
  connections: number;
  grpcPort: number;
  remotingPort: number;
}

export interface NameServerInfo {
  addr: string;
  status: string;
}

export interface NameserverRegistryEntry {
  id: number;
  name: string;
  namesrvAddr: string;
  k8sNamespace: string | null;
  k8sId: string | null;
  status: string | null;
  description: string | null;
  gmtCreate: string | null;
  gmtModified: string | null;
}

export interface ClusterConfig {
  flushDiskType: string;
  autoCreateTopicEnable: boolean;
  autoCreateSubscriptionGroup: boolean;
  maxMessageSize: number;
  msgTraceTopicName: string;
  fileReservedTime: number;
  writeQueueNums: number;
  readQueueNums: number;
  brokerPermission: number;
  deleteWhen: string;
}

export type ClusterConfigUpdateStatus = 'SUCCESS' | 'PARTIAL' | 'FAILED';

export interface BrokerConfigUpdateFailure {
  address: string;
  message: string;
}

export interface ClusterConfigUpdateResult {
  cluster: ClusterInfo;
  status: ClusterConfigUpdateStatus;
  successfulBrokers: string[];
  failedBrokers: BrokerConfigUpdateFailure[];
}

export interface ClusterProbeResult {
  connected: boolean;
  namesrvAddr: string;
  clusterName: string;
  brokerCount: number;
  brokerNames: string[];
  elapsedMillis: number;
  message: string;
}

export interface K8sCertInfo {
  id: number;
  k8sId: string;
  cluster: string;
  type: string;
  issuer: string;
  notBefore: string;
  notAfter: string;
  status: string;
  daysRemaining: number;
  san: string[];
  certPem?: string;
  keyPem?: string;
}

export interface NameServerConfigValue {
  address: string;
  configured: boolean;
  value: string | null;
}

export interface NameServerConfigDifference {
  key: string;
  values: NameServerConfigValue[];
}

export interface NameServerConfigDiffResult {
  cluster: string;
  complete: boolean;
  driftDetected: boolean;
  nodeCount: number;
  reachableNodeCount: number;
  comparedKeys: string[];
  nodes: Array<{ address: string; reachable: boolean }>;
  differences: NameServerConfigDifference[];
}

// ─── Cluster ────────────────────────────────────────────────────
export async function listClusters(instanceId?: string) {
  const res = await client.get<{ data: ClusterInfo[] }>('/clusters', {
    params: instanceId ? { instanceId } : undefined,
  });
  return res.data.data;
}

export async function listRegistryClusters() {
  const res = await client.get<{ data: ClusterInfo[] }>('/clusters/registry');
  return res.data.data ?? [];
}

export async function testClusterConnection(namesrvAddr: string) {
  const res = await client.post<{ data: ClusterProbeResult }>('/clusters/test-connection', {
    namesrvAddr,
  });
  return res.data.data;
}

export async function getCluster(id: string, instanceId?: string) {
  const res = await client.get<{ data: ClusterInfo }>(`/clusters/${pathSegment(id)}`, {
    params: instanceId ? { instanceId } : undefined,
  });
  return res.data.data;
}

export async function updateClusterConfig(
  data: { id: string; instanceId?: string } & Partial<ClusterConfig>,
) {
  const res = await client.post<{ data: ClusterConfigUpdateResult }>(
    '/clusters/config/update',
    data,
  );
  return res.data.data;
}

export async function restartBroker(clusterId: string, brokerName: string) {
  const res = await client.post<{ data: { success: boolean; message: string } }>(
    `/clusters/${pathSegment(clusterId)}/brokers/${pathSegment(brokerName)}/restart`,
  );
  return res.data.data;
}

// ─── NameServer ─────────────────────────────────────────────────
export async function listNameserverRegistry() {
  const res = await client.get<{ data: NameserverRegistryEntry[] }>('/nameservers');
  return res.data.data ?? [];
}

export async function createNameserverRegistry(data: {
  name: string;
  namesrvAddr: string;
  k8sNamespace?: string;
  k8sId?: string;
  description?: string;
}) {
  const res = await client.post<{ data: NameserverRegistryEntry }>(
    '/nameservers/registry/create',
    data,
  );
  return res.data.data;
}

export async function updateNameserverRegistry(data: {
  id: number;
  name: string;
  namesrvAddr: string;
  k8sNamespace?: string;
  k8sId?: string;
  description?: string;
}) {
  const res = await client.post<{ data: NameserverRegistryEntry }>(
    '/nameservers/registry/update',
    data,
  );
  return res.data.data;
}

export async function deleteNameserverRegistry(id: number) {
  await client.post('/nameservers/registry/delete', { id });
}

export async function restartNameServer(data: { clusterId: string; addr: string }) {
  await client.post('/nameservers/restart', data);
}

export async function upgradeNameServer(data: {
  clusterId: string;
  addr: string;
  version: string;
}) {
  await client.post('/nameservers/upgrade', data);
}

export async function deleteNameServer(data: { clusterId: string; addr: string }) {
  await client.post('/nameservers/delete', data);
}

export async function createNameServer(data: { clusterId: string; addr: string }) {
  await client.post('/nameservers/create', data);
}

export async function updateNameServer(data: {
  clusterId: string;
  addr: string;
  newAddr?: string;
}) {
  await client.post('/nameservers/update', data);
}

export async function getNameServerConfigDiff(clusterId: string, instanceId?: string) {
  const res = await client.get<{ data: NameServerConfigDiffResult }>('/nameservers/config-diff', {
    params: { clusterId, ...(instanceId ? { instanceId } : {}) },
  });
  return res.data.data;
}

// ─── Proxy ──────────────────────────────────────────────────────
export async function restartProxy(data: { clusterId: string; addr: string }) {
  await client.post('/proxies/restart', data);
}

// ─── K8s Certs ──────────────────────────────────────────────────
export async function listK8sCerts() {
  const res = await client.get<{ data: K8sCertInfo[] }>('/k8s-certs');
  return res.data.data;
}

export async function createK8sCert(data: Partial<K8sCertInfo>) {
  const res = await client.post<{ data: K8sCertInfo }>('/k8s-certs/create', data);
  return res.data.data;
}

export async function updateK8sCert(data: Partial<K8sCertInfo>) {
  const res = await client.post<{ data: K8sCertInfo }>('/k8s-certs/update', data);
  return res.data.data;
}

export async function renewK8sCert(id: number) {
  const res = await client.post<{ data: K8sCertInfo }>('/k8s-certs/renew', { id });
  return res.data.data;
}

export async function deleteK8sCert(id: number) {
  await client.post('/k8s-certs/delete', { id });
}
