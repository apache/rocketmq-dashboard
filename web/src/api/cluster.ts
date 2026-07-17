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

// ─── Types ──────────────────────────────────────────────────────
export interface ClusterInfo {
  id: string;
  name: string;
  type: string;
  status: string;
  version: string;
  brokers: number;
  proxies: number;
  topics: number;
  groups: number;
  tpsIn: number;
  tpsOut: number;
}

export interface ClusterDetail extends ClusterInfo {
  brokerList: BrokerInfo[];
  proxyList: ProxyInfo[];
  nameServerList: NameServerInfo[];
  config: ClusterConfig;
}

export interface BrokerInfo {
  addr: string;
  brokerName: string;
  brokerId: number;
  clusterName: string;
  status: string;
  tpsIn: number;
  tpsOut: number;
  diskUsage: number;
  version: string;
}

export interface ProxyInfo {
  addr: string;
  clusterName: string;
  status: string;
  connections: number;
  grpcPort: number;
  remotingPort: number;
}

export interface NameServerInfo {
  addr: string;
  clusterName: string;
  status: string;
  version: string;
}

export interface ClusterConfig {
  flushDiskType: string;
  autoCreateTopicEnable: boolean;
  autoCreateSubscriptionGroup: boolean;
  maxMessageSize: number;
  fileReservedTime: number;
  writeQueueNums: number;
  readQueueNums: number;
  brokerPermission: number;
}

export interface K8sCertInfo {
  id: string;
  name: string;
  namespace: string;
  cluster: string;
  type: string;
  issuer: string;
  notBefore: string;
  notAfter: string;
  status: string;
  daysRemaining: number;
  san: string[];
}

// ─── Cluster ────────────────────────────────────────────────────
export async function listClusters() {
  const res = await client.get<{ data: ClusterInfo[] }>('/clusters');
  return res.data.data;
}

export async function getCluster(id: string) {
  const res = await client.get<{ data: ClusterDetail }>(`/clusters/${id}`);
  return res.data.data;
}

export async function updateClusterConfig(data: { id: string } & Partial<ClusterConfig>) {
  await client.post('/clusters/config/update', data);
}

export async function restartBroker(clusterId: string, brokerName: string) {
  const res = await client.post<{ data: { success: boolean; message: string } }>(
    `/clusters/${clusterId}/brokers/${brokerName}/restart`,
  );
  return res.data.data;
}

// ─── NameServer ─────────────────────────────────────────────────
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

export async function renewK8sCert(id: string) {
  const res = await client.post<{ data: K8sCertInfo }>('/k8s-certs/renew', { id });
  return res.data.data;
}

export async function deleteK8sCert(id: string) {
  await client.post('/k8s-certs/delete', { id });
}
