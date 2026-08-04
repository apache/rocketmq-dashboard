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

import type { GrafanaDashboardInfo } from '../api/metrics';

export interface MockGrafanaDashboard extends GrafanaDashboardInfo {
  model: Record<string, unknown>;
}

export const mockGrafanaDashboards: MockGrafanaDashboard[] = [
  {
    uid: 'rocketmq-overview',
    title: 'RocketMQ Cluster Overview',
    description: 'Cluster-wide throughput, topic/group counts and producer footprint.',
    tags: ['rocketmq'],
    model: {
      uid: 'rocketmq-overview',
      title: 'RocketMQ Cluster Overview',
      schemaVersion: 39,
      tags: ['rocketmq'],
      panels: [
        { id: 1, title: 'Messages In TPS', type: 'timeseries' },
        { id: 2, title: 'Messages Out TPS', type: 'timeseries' },
      ],
    },
  },
  {
    uid: 'rocketmq-broker',
    title: 'RocketMQ Broker',
    description: 'Per-broker throughput, dispatch backlog and thread pool pressure.',
    tags: ['rocketmq'],
    model: {
      uid: 'rocketmq-broker',
      title: 'RocketMQ Broker',
      schemaVersion: 39,
      tags: ['rocketmq'],
      panels: [{ id: 1, title: 'Broker Messages In TPS', type: 'timeseries' }],
    },
  },
  {
    uid: 'rocketmq-producer',
    title: 'RocketMQ Producer',
    description: 'Producer presence, send size and per-topic ingress.',
    tags: ['rocketmq'],
    model: {
      uid: 'rocketmq-producer',
      title: 'RocketMQ Producer',
      schemaVersion: 39,
      tags: ['rocketmq'],
      panels: [{ id: 1, title: 'Producer Count', type: 'stat' }],
    },
  },
  {
    uid: 'rocketmq-consumer',
    title: 'RocketMQ Consumer',
    description: 'Consumer group egress, lag and client footprint.',
    tags: ['rocketmq'],
    model: {
      uid: 'rocketmq-consumer',
      title: 'RocketMQ Consumer',
      schemaVersion: 39,
      tags: ['rocketmq'],
      panels: [{ id: 1, title: 'Messages Out TPS by Group', type: 'timeseries' }],
    },
  },
  {
    uid: 'rocketmq-topic',
    title: 'RocketMQ Topic',
    description: 'Per-topic ingress/egress throughput and dispatch backlog.',
    tags: ['rocketmq'],
    model: {
      uid: 'rocketmq-topic',
      title: 'RocketMQ Topic',
      schemaVersion: 39,
      tags: ['rocketmq'],
      panels: [{ id: 1, title: 'Topic Messages In TPS', type: 'timeseries' }],
    },
  },
  {
    uid: 'rocketmq-tps',
    title: 'RocketMQ TPS',
    description: 'Cluster and per-broker message throughput trends.',
    tags: ['rocketmq'],
    model: {
      uid: 'rocketmq-tps',
      title: 'RocketMQ TPS',
      schemaVersion: 39,
      tags: ['rocketmq'],
      panels: [{ id: 1, title: 'Cluster TPS In', type: 'timeseries' }],
    },
  },
  {
    uid: 'rocketmq-storage',
    title: 'RocketMQ Storage',
    description: 'Broker disk usage and JVM heap footprint.',
    tags: ['rocketmq'],
    model: {
      uid: 'rocketmq-storage',
      title: 'RocketMQ Storage',
      schemaVersion: 39,
      tags: ['rocketmq'],
      panels: [{ id: 1, title: 'Disk Use Ratio', type: 'gauge' }],
    },
  },
  {
    uid: 'rocketmq-jvm',
    title: 'RocketMQ Broker JVM',
    description: 'JVM memory, threads and garbage collection for brokers.',
    tags: ['rocketmq'],
    model: {
      uid: 'rocketmq-jvm',
      title: 'RocketMQ Broker JVM',
      schemaVersion: 39,
      tags: ['rocketmq'],
      panels: [{ id: 1, title: 'JVM Heap', type: 'timeseries' }],
    },
  },
  {
    uid: 'rocketmq-threadpool',
    title: 'RocketMQ Thread Pool',
    description: 'Broker thread pool queue depth, capacity and rejections.',
    tags: ['rocketmq'],
    model: {
      uid: 'rocketmq-threadpool',
      title: 'RocketMQ Thread Pool',
      schemaVersion: 39,
      tags: ['rocketmq'],
      panels: [{ id: 1, title: 'Queue Size', type: 'timeseries' }],
    },
  },
  {
    uid: 'rocketmq-dlq',
    title: 'RocketMQ DLQ & Retry',
    description: 'Dead-letter queue resend volume and latency.',
    tags: ['rocketmq'],
    model: {
      uid: 'rocketmq-dlq',
      title: 'RocketMQ DLQ & Retry',
      schemaVersion: 39,
      tags: ['rocketmq'],
      panels: [{ id: 1, title: 'DLQ Resend Count', type: 'timeseries' }],
    },
  },
  {
    uid: 'rocketmq-latency',
    title: 'RocketMQ Latency',
    description: 'Dispatch and client push latency percentiles.',
    tags: ['rocketmq'],
    model: {
      uid: 'rocketmq-latency',
      title: 'RocketMQ Latency',
      schemaVersion: 39,
      tags: ['rocketmq'],
      panels: [{ id: 1, title: 'Dispatch Latency p99', type: 'timeseries' }],
    },
  },
  {
    uid: 'rocketmq-network',
    title: 'RocketMQ Network & Connections',
    description: 'Client connections and produced/consumed connection counts.',
    tags: ['rocketmq'],
    model: {
      uid: 'rocketmq-network',
      title: 'RocketMQ Network & Connections',
      schemaVersion: 39,
      tags: ['rocketmq'],
      panels: [{ id: 1, title: 'Client Connections', type: 'stat' }],
    },
  },
];
