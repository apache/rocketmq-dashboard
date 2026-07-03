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

/* ─── Types ─── */

export interface ClientConnection {
  clientId: string;
  type: 'Producer' | 'Consumer';
  groupOrTopic: string;
  protocol: 'gRPC' | 'Remoting';
  address: string;
  language: 'Java' | 'Go' | 'Python' | 'Rust';
  version: string;
  connectedAt: string;
  clusterName: string;
}

/* ─── Mock Data ─── */

export const mockClients: ClientConnection[] = [
  {
    clientId: 'order-svc-0@10.0.1.12:49152',
    type: 'Producer',
    groupOrTopic: 'order-create',
    protocol: 'gRPC',
    address: '10.0.1.12:49152',
    language: 'Java',
    version: '5.0.7',
    connectedAt: '2026-07-01 08:30:00',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'order-svc-1@10.0.1.13:49200',
    type: 'Producer',
    groupOrTopic: 'order-status-change',
    protocol: 'gRPC',
    address: '10.0.1.13:49200',
    language: 'Java',
    version: '5.0.7',
    connectedAt: '2026-07-01 08:30:05',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'payment-svc-0@10.0.2.10:50100',
    type: 'Producer',
    groupOrTopic: 'payment-callback',
    protocol: 'gRPC',
    address: '10.0.2.10:50100',
    language: 'Java',
    version: '5.0.7',
    connectedAt: '2026-07-01 08:32:10',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'cg-order-notify-0@10.0.1.12:49160',
    type: 'Consumer',
    groupOrTopic: 'cg-order-notify',
    protocol: 'gRPC',
    address: '10.0.1.12:49160',
    language: 'Java',
    version: '5.0.7',
    connectedAt: '2026-07-01 08:31:00',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'cg-order-notify-1@10.0.1.13:49210',
    type: 'Consumer',
    groupOrTopic: 'cg-order-notify',
    protocol: 'gRPC',
    address: '10.0.1.13:49210',
    language: 'Java',
    version: '5.0.7',
    connectedAt: '2026-07-01 08:31:05',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'cg-payment-cb-0@10.0.2.10:50110',
    type: 'Consumer',
    groupOrTopic: 'cg-payment-callback',
    protocol: 'gRPC',
    address: '10.0.2.10:50110',
    language: 'Java',
    version: '5.0.7',
    connectedAt: '2026-07-01 08:33:00',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'user-svc-0@10.0.3.20:51100',
    type: 'Producer',
    groupOrTopic: 'user-activity-log',
    protocol: 'Remoting',
    address: '10.0.3.20:51100',
    language: 'Go',
    version: '5.0.3',
    connectedAt: '2026-07-01 09:00:00',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'cg-user-act-0@10.0.3.20:51110',
    type: 'Consumer',
    groupOrTopic: 'cg-user-activity',
    protocol: 'Remoting',
    address: '10.0.3.20:51110',
    language: 'Go',
    version: '5.0.3',
    connectedAt: '2026-07-01 09:00:30',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'log-collector-0@10.0.5.40:53100',
    type: 'Consumer',
    groupOrTopic: 'cg-log-collector',
    protocol: 'gRPC',
    address: '10.0.5.40:53100',
    language: 'Rust',
    version: '5.0.2',
    connectedAt: '2026-07-01 07:00:00',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'log-collector-1@10.0.5.41:53200',
    type: 'Consumer',
    groupOrTopic: 'cg-log-collector',
    protocol: 'gRPC',
    address: '10.0.5.41:53200',
    language: 'Rust',
    version: '5.0.2',
    connectedAt: '2026-07-01 07:00:10',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'notif-gw-0@10.0.6.50:54100',
    type: 'Producer',
    groupOrTopic: 'notification-push',
    protocol: 'gRPC',
    address: '10.0.6.50:54100',
    language: 'Python',
    version: '5.0.1',
    connectedAt: '2026-07-01 08:45:00',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'cg-notif-push-0@10.0.6.50:54110',
    type: 'Consumer',
    groupOrTopic: 'cg-notification-push',
    protocol: 'gRPC',
    address: '10.0.6.50:54110',
    language: 'Python',
    version: '5.0.1',
    connectedAt: '2026-07-01 08:45:30',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'ai-infer-0@10.0.7.60:55100',
    type: 'Consumer',
    groupOrTopic: 'cg-ai-task-worker',
    protocol: 'gRPC',
    address: '10.0.7.60:55100',
    language: 'Python',
    version: '5.0.1',
    connectedAt: '2026-07-01 10:00:00',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'ai-dispatch-0@10.0.7.70:55200',
    type: 'Producer',
    groupOrTopic: 'ai-task-dispatch',
    protocol: 'gRPC',
    address: '10.0.7.70:55200',
    language: 'Java',
    version: '5.0.7',
    connectedAt: '2026-07-01 10:00:15',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'metrics-agent-0@10.0.8.70:56100',
    type: 'Producer',
    groupOrTopic: 'metrics-raw',
    protocol: 'Remoting',
    address: '10.0.8.70:56100',
    language: 'Go',
    version: '5.0.3',
    connectedAt: '2026-07-01 06:00:00',
    clusterName: 'ns-pre',
  },
  {
    clientId: 'cg-metrics-agg-0@10.0.8.70:56110',
    type: 'Consumer',
    groupOrTopic: 'cg-metrics-aggregator',
    protocol: 'Remoting',
    address: '10.0.8.70:56110',
    language: 'Go',
    version: '5.0.3',
    connectedAt: '2026-07-01 06:00:30',
    clusterName: 'ns-pre',
  },
  {
    clientId: 'risk-engine-0@10.0.9.80:57100',
    type: 'Consumer',
    groupOrTopic: 'cg-risk-control',
    protocol: 'gRPC',
    address: '10.0.9.80:57100',
    language: 'Java',
    version: '5.0.7',
    connectedAt: '2026-07-01 08:00:00',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'binlog-bridge-0@10.0.10.90:58100',
    type: 'Producer',
    groupOrTopic: 'binlog-event',
    protocol: 'gRPC',
    address: '10.0.10.90:58100',
    language: 'Java',
    version: '5.0.7',
    connectedAt: '2026-07-01 06:30:00',
    clusterName: 'ns-pre',
  },
  {
    clientId: 'cg-data-sync-0@10.0.10.90:58110',
    type: 'Consumer',
    groupOrTopic: 'cg-data-sync',
    protocol: 'gRPC',
    address: '10.0.10.90:58110',
    language: 'Java',
    version: '5.0.7',
    connectedAt: '2026-07-01 06:30:30',
    clusterName: 'ns-pre',
  },
  {
    clientId: 'search-idx-0@10.0.11.100:59100',
    type: 'Consumer',
    groupOrTopic: 'cg-search-indexer',
    protocol: 'gRPC',
    address: '10.0.11.100:59100',
    language: 'Rust',
    version: '5.0.2',
    connectedAt: '2026-07-01 09:15:00',
    clusterName: 'ns-pre',
  },
];
