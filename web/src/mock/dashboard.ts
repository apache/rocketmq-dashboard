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

export const dashboardStats = {
  totalClusters: 2,
  healthyClusters: 2,
  totalBrokers: 12,
  totalProxies: 5,
  totalNameServers: 5,
  totalTopics: 304,
  totalConsumerGroups: 152,
  totalMessagesToday: 18_420_000,
  messagesPerSecond: 85_800,
  tpsIn: 85_800,
  tpsOut: 222_400,
};

export const clusterOverview = [
  {
    id: 'cluster-prod',
    name: 'rocketmq-prod',
    type: 'V5_PROXY_CLUSTER',
    status: 'healthy' as const,
    brokers: 8,
    proxies: 3,
    topics: 256,
    groups: 128,
    tpsIn: 78_000,
    tpsOut: 203_600,
    version: '5.2.0',
    throughput: [
      62000, 68000, 74000, 71000, 78000, 82000, 79000, 85000, 81000, 78000, 76000, 80000,
    ],
  },
  {
    id: 'cluster-pre',
    name: 'rocketmq-pre',
    type: 'V5_PROXY_CLUSTER',
    status: 'healthy' as const,
    brokers: 4,
    proxies: 2,
    topics: 48,
    groups: 24,
    tpsIn: 7_800,
    tpsOut: 21_400,
    version: '5.2.0',
    throughput: [5200, 5800, 6400, 6000, 7200, 6800, 6200, 7000, 7400, 6800, 7200, 7600],
  },
];

export const recentActivities = [
  {
    time: '18:32:05',
    type: 'topic',
    message: 'Topic order-payment 创建于 rocketmq-prod',
    color: 'blue',
  },
  {
    time: '18:28:17',
    type: 'consumer',
    message: '消费者组 cg-order-notify 重置消费位点至 1 小时前',
    color: 'orange',
  },
  {
    time: '18:15:42',
    type: 'alert',
    message: 'rocketmq-prod-5 磁盘使用率达到 86%，已切为只读',
    color: 'red',
  },
  {
    time: '17:58:30',
    type: 'acl',
    message: 'ACL 规则更新: user-order-service → Topic order-* [PUB, SUB]',
    color: 'purple',
  },
  {
    time: '17:42:11',
    type: 'cluster',
    message: 'rocketmq-prod-7 进入维护模式，禁止读写',
    color: 'gold',
  },
  {
    time: '17:30:00',
    type: 'topic',
    message: 'LiteTopic chat/sess-a3f8* 自动创建 128 个 (TTL: 24h)',
    color: 'magenta',
  },
  {
    time: '16:55:23',
    type: 'consumer',
    message: '消费者组 cg-payment-callback 新增 2 个在线实例',
    color: 'cyan',
  },
  {
    time: '16:20:45',
    type: 'alert',
    message: 'rocketmq-prod-7 磁盘使用率达到 91%，触发维护',
    color: 'red',
  },
];

export const topTopicsByThroughput = [
  {
    name: 'order-create',
    namespace: 'trade',
    type: 'TRANSACTION',
    tps: 12480,
    messages: 3_680_000,
  },
  { name: 'payment-notify', namespace: 'trade', type: 'NORMAL', tps: 11800, messages: 3_480_000 },
  { name: 'user-login-event', namespace: 'user', type: 'NORMAL', tps: 10500, messages: 3_100_000 },
  { name: 'inventory-sync', namespace: 'supply', type: 'FIFO', tps: 9800, messages: 2_890_000 },
  { name: 'promo-push', namespace: 'marketing', type: 'DELAY', tps: 8600, messages: 2_540_000 },
];

export const systemAlerts = [
  {
    id: 'alert-1',
    level: 'warning' as const,
    title: 'rocketmq-prod-5 磁盘使用率偏高',
    description: '磁盘使用率 86%，已自动切为只读模式',
    time: '18:15',
    acknowledged: false,
  },
  {
    id: 'alert-2',
    level: 'error' as const,
    title: 'rocketmq-prod-7 进入维护模式',
    description: '磁盘使用率 91%，已禁止读写，需人工介入处理',
    time: '17:42',
    acknowledged: false,
  },
  {
    id: 'alert-3',
    level: 'info' as const,
    title: 'rocketmq-prod-7 版本落后',
    description: '当前版本 5.1.4，集群版本 5.2.0，建议升级',
    time: '16:30',
    acknowledged: true,
  },
];
