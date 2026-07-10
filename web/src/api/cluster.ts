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

// ─── Cluster API ────────────────────────────────────────────────
// Backend: ClusterController at /cluster
// GET /cluster/list.query           → list clusters
// GET /cluster/brokerConfig.query   → broker config

export async function listClusters() {
  const res = await client.get('/cluster/list.query');
  return res.data;
}

export async function getCluster(id: string) {
  // Backend doesn't have a single-cluster endpoint; list all and filter
  const res = await client.get('/cluster/list.query');
  const clusters = res.data;
  return Array.isArray(clusters) ? clusters.find((c: ClusterInfo) => c.id === id) : null;
}

export async function getBrokerConfig(brokerAddr: string) {
  const res = await client.get('/cluster/brokerConfig.query', { params: { brokerAddr } });
  return res.data;
}

// ─── NameServer API ─────────────────────────────────────────────
// Backend: NamesvrController at /rocketmq
// GET /rocketmq/nsaddr.query        → get nameserver addresses

export async function getNameServerAddresses() {
  const res = await client.get('/rocketmq/nsaddr.query');
  return res.data;
}

// ─── Cluster Config & Broker Ops (no direct backend endpoint) ───
// These are kept for mock compatibility; the backend cluster controller
// only provides list.query and brokerConfig.query

export async function updateClusterConfig(data: { id: string } & Record<string, unknown>) {
  // No direct backend endpoint; kept for mock compatibility
  await client.post('/clusters/config/update', data);
}

export async function restartBroker(clusterId: string, brokerName: string) {
  // No direct backend endpoint; kept for mock compatibility
  const res = await client.post<{ success: boolean; message: string }>(
    `/clusters/${clusterId}/brokers/${brokerName}/restart`,
  );
  return res.data;
}

// ─── K8s Certs (no backend equivalent yet — uses mock) ──────────
export async function listK8sCerts() {
  // No backend endpoint for K8s certs yet
  const res = await client.get('/k8s-certs');
  return res.data;
}

export async function createK8sCert(data: Partial<K8sCertInfo>) {
  await client.post('/k8s-certs/create', data);
}

export async function updateK8sCert(data: Partial<K8sCertInfo>) {
  await client.post('/k8s-certs/update', data);
}

export async function renewK8sCert(id: string) {
  await client.post('/k8s-certs/renew', { id });
}

export async function deleteK8sCert(id: string) {
  await client.post('/k8s-certs/delete', { id });
}
