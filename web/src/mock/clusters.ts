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

export interface BrokerInfo {
  name: string;
  addr: string;
  version: string;
  status: 'running' | 'readonly' | 'maintenance';
  diskUsage: number;
  tpsIn: number;
  tpsOut: number;
}

export interface ProxyInfo {
  addr: string;
  status: 'healthy' | 'warning' | 'error' | 'offline';
  connections: number;
  grpcPort: number;
  remotingPort: number;
}

export interface NameServerInfo {
  addr: string;
  status: 'healthy' | 'warning' | 'error' | 'offline';
}

export interface K8sCertInfo {
  id: string;
  name: string;
  namespace: string;
  cluster: string;
  type: 'TLS' | 'mTLS' | 'ServiceAccount';
  issuer: string;
  notBefore: string;
  notAfter: string;
  status: 'valid' | 'expiring' | 'expired';
  daysRemaining: number;
  san: string[];
}

export const mockK8sCerts: K8sCertInfo[] = [
  {
    id: 'cert-001',
    name: 'ca90643d13159433da1dadd826160b2c1',
    namespace: 'kube-system',
    cluster: 'rocketmq-prod',
    type: 'TLS',
    issuer: 'kubernetes-ca',
    notBefore: '2025-06-01T00:00:00Z',
    notAfter: '2026-06-01T00:00:00Z',
    status: 'valid',
    daysRemaining: 335,
    san: ['kubernetes', 'kubernetes.default', 'kubernetes.default.svc'],
  },
  {
    id: 'cert-002',
    name: 'e7f3a8c291b04d56a1e2f4c6b8d0a9235',
    namespace: 'kube-system',
    cluster: 'rocketmq-prod',
    type: 'mTLS',
    issuer: 'etcd-ca',
    notBefore: '2025-06-01T00:00:00Z',
    notAfter: '2026-06-01T00:00:00Z',
    status: 'valid',
    daysRemaining: 335,
    san: ['etcd-0', 'etcd-1', 'etcd-2'],
  },
  {
    id: 'cert-003',
    name: 'b2d4f6a8c0e2f4a6b8d0c2e4f6a8b0d2',
    namespace: 'cert-manager',
    cluster: 'rocketmq-prod',
    type: 'TLS',
    issuer: 'letsencrypt-prod',
    notBefore: '2025-01-15T00:00:00Z',
    notAfter: '2025-07-15T00:00:00Z',
    status: 'expiring',
    daysRemaining: 12,
    san: ['*.rocketmq-prod.example.com', 'rocketmq-prod.example.com'],
  },
  {
    id: 'cert-004',
    name: 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6',
    namespace: 'kube-system',
    cluster: 'rocketmq-pre',
    type: 'TLS',
    issuer: 'kubernetes-ca',
    notBefore: '2025-09-01T00:00:00Z',
    notAfter: '2026-09-01T00:00:00Z',
    status: 'valid',
    daysRemaining: 427,
    san: ['kubernetes', 'kubernetes.default', 'kubernetes.default.svc'],
  },
  {
    id: 'cert-005',
    name: 'f9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4',
    namespace: 'istio-system',
    cluster: 'rocketmq-pre',
    type: 'mTLS',
    issuer: 'istio-ca',
    notBefore: '2025-09-01T00:00:00Z',
    notAfter: '2026-09-01T00:00:00Z',
    status: 'valid',
    daysRemaining: 427,
    san: ['*.rocketmq-pre.svc.cluster.local', 'istiod.istio-system.svc'],
  },
  {
    id: 'cert-006',
    name: 'c0ffee1bad2bad3caffe4bad5coffee6bad7',
    namespace: 'kube-system',
    cluster: 'rocketmq-prod',
    type: 'TLS',
    issuer: 'self-signed',
    notBefore: '2025-03-01T00:00:00Z',
    notAfter: '2025-06-20T00:00:00Z',
    status: 'expired',
    daysRemaining: -10,
    san: ['kubernetes', 'localhost'],
  },
  {
    id: 'cert-007',
    name: 'd3adb33fd3adb33fd3adb33fd3adb33f',
    namespace: 'kube-system',
    cluster: 'rocketmq-pre',
    type: 'ServiceAccount',
    issuer: 'k8s-sa',
    notBefore: '2026-01-01T00:00:00Z',
    notAfter: '2027-01-01T00:00:00Z',
    status: 'valid',
    daysRemaining: 549,
    san: ['system:serviceaccount:rocketmq:broker-sa'],
  },
  {
    id: 'cert-008',
    name: '5ca1ab1e5ca1ab1e5ca1ab1e5ca1ab1e',
    namespace: 'kube-system',
    cluster: 'rocketmq-prod',
    type: 'mTLS',
    issuer: 'vault-pki',
    notBefore: '2025-12-01T00:00:00Z',
    notAfter: '2026-12-01T00:00:00Z',
    status: 'valid',
    daysRemaining: 518,
    san: ['*.rocketmq-prod.svc.cluster.local'],
  },
];

export interface ClusterConfig {
  writeQueueNums: number;
  readQueueNums: number;
  maxMessageSize: number;
  msgTraceTopicName: string;
  autoCreateTopicEnable: boolean;
  autoCreateSubscriptionGroup: boolean;
  deleteWhen: string;
  fileReservedTime: number;
  flushDiskType: 'ASYNC_FLUSH' | 'SYNC_FLUSH';
  brokerPermission: number;
}

export interface ClusterInfo {
  id: string;
  name: string;
  nsClusterName: string;
  type: 'V5_PROXY_CLUSTER';
  endpoint: string;
  status: 'healthy' | 'warning' | 'error' | 'offline';
  version: string;
  brokers: BrokerInfo[];
  proxies: ProxyInfo[];
  nameServers: NameServerInfo[];
  config: ClusterConfig;
  topicCount: number;
  groupCount: number;
  tpsHistory: number[];
}

const clusters: ClusterInfo[] = [
  {
    id: 'cluster-prod',
    name: 'rocketmq-prod',
    nsClusterName: 'ns-prod',
    type: 'V5_PROXY_CLUSTER',
    endpoint: '10.101.2.1:9876',
    status: 'healthy',
    version: '5.2.0',
    brokers: [
      {
        name: 'rocketmq-prod-0',
        addr: '10.101.2.11:10911',
        version: '5.2.0',
        status: 'running',
        diskUsage: 62,
        tpsIn: 12480,
        tpsOut: 34560,
      },
      {
        name: 'rocketmq-prod-1',
        addr: '10.101.2.12:10911',
        version: '5.2.0',
        status: 'running',
        diskUsage: 58,
        tpsIn: 11800,
        tpsOut: 31200,
      },
      {
        name: 'rocketmq-prod-2',
        addr: '10.101.2.13:10911',
        version: '5.2.0',
        status: 'running',
        diskUsage: 71,
        tpsIn: 11200,
        tpsOut: 28900,
      },
      {
        name: 'rocketmq-prod-3',
        addr: '10.101.2.14:10911',
        version: '5.2.0',
        status: 'running',
        diskUsage: 55,
        tpsIn: 10500,
        tpsOut: 27800,
      },
      {
        name: 'rocketmq-prod-4',
        addr: '10.101.2.15:10911',
        version: '5.2.0',
        status: 'running',
        diskUsage: 48,
        tpsIn: 9800,
        tpsOut: 25600,
      },
      {
        name: 'rocketmq-prod-5',
        addr: '10.101.2.16:10911',
        version: '5.2.0',
        status: 'readonly',
        diskUsage: 86,
        tpsIn: 0,
        tpsOut: 18400,
      },
      {
        name: 'rocketmq-prod-6',
        addr: '10.101.2.17:10911',
        version: '5.2.0',
        status: 'running',
        diskUsage: 43,
        tpsIn: 8600,
        tpsOut: 22400,
      },
      {
        name: 'rocketmq-prod-7',
        addr: '10.101.2.18:10911',
        version: '5.1.4',
        status: 'maintenance',
        diskUsage: 91,
        tpsIn: 0,
        tpsOut: 0,
      },
    ],
    proxies: [
      {
        addr: '10.101.2.21:8081',
        status: 'healthy',
        connections: 1842,
        grpcPort: 8081,
        remotingPort: 8080,
      },
      {
        addr: '10.101.2.22:8081',
        status: 'healthy',
        connections: 1653,
        grpcPort: 8081,
        remotingPort: 8080,
      },
      {
        addr: '10.101.2.23:8081',
        status: 'healthy',
        connections: 1724,
        grpcPort: 8081,
        remotingPort: 8080,
      },
    ],
    nameServers: [
      { addr: '10.101.2.1:9876', status: 'healthy' },
      { addr: '10.101.2.2:9876', status: 'healthy' },
      { addr: '10.101.2.3:9876', status: 'healthy' },
    ],
    config: {
      writeQueueNums: 16,
      readQueueNums: 16,
      maxMessageSize: 4194304,
      msgTraceTopicName: 'RMQ_SYS_TRACE_TOPIC4',
      autoCreateTopicEnable: false,
      autoCreateSubscriptionGroup: false,
      deleteWhen: '04',
      fileReservedTime: 72,
      flushDiskType: 'SYNC_FLUSH',
      brokerPermission: 6,
    },
    topicCount: 256,
    groupCount: 128,
    tpsHistory: [
      18200, 19800, 21400, 20100, 23680, 24100, 22500, 25800, 24600, 23200, 22800, 24500,
    ],
  },
  {
    id: 'cluster-pre',
    name: 'rocketmq-pre',
    nsClusterName: 'ns-pre',
    type: 'V5_PROXY_CLUSTER',
    endpoint: '10.102.5.1:9876',
    status: 'healthy',
    version: '5.2.0',
    brokers: [
      {
        name: 'rocketmq-pre-0',
        addr: '10.102.5.11:10911',
        version: '5.2.0',
        status: 'running',
        diskUsage: 35,
        tpsIn: 2400,
        tpsOut: 6800,
      },
      {
        name: 'rocketmq-pre-1',
        addr: '10.102.5.12:10911',
        version: '5.2.0',
        status: 'running',
        diskUsage: 28,
        tpsIn: 2100,
        tpsOut: 5600,
      },
      {
        name: 'rocketmq-pre-2',
        addr: '10.102.5.13:10911',
        version: '5.2.0',
        status: 'running',
        diskUsage: 42,
        tpsIn: 1800,
        tpsOut: 4800,
      },
      {
        name: 'rocketmq-pre-3',
        addr: '10.102.5.14:10911',
        version: '5.2.0',
        status: 'running',
        diskUsage: 31,
        tpsIn: 1500,
        tpsOut: 4200,
      },
    ],
    proxies: [
      {
        addr: '10.102.5.21:8081',
        status: 'healthy',
        connections: 424,
        grpcPort: 8081,
        remotingPort: 8080,
      },
      {
        addr: '10.102.5.22:8081',
        status: 'healthy',
        connections: 382,
        grpcPort: 8081,
        remotingPort: 8080,
      },
    ],
    nameServers: [
      { addr: '10.102.5.1:9876', status: 'healthy' },
      { addr: '10.102.5.2:9876', status: 'healthy' },
    ],
    config: {
      writeQueueNums: 8,
      readQueueNums: 8,
      maxMessageSize: 4194304,
      msgTraceTopicName: 'RMQ_SYS_TRACE_TOPIC4',
      autoCreateTopicEnable: true,
      autoCreateSubscriptionGroup: true,
      deleteWhen: '04',
      fileReservedTime: 24,
      flushDiskType: 'ASYNC_FLUSH',
      brokerPermission: 6,
    },
    topicCount: 48,
    groupCount: 24,
    tpsHistory: [5200, 5800, 6400, 6000, 7200, 6800, 6200, 7000, 7400, 6800, 7200, 7600],
  },
];

export default clusters;
