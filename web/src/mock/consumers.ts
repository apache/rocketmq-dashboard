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

export interface ConsumerInstance {
  clientId: string;
  protocol: 'REMOTING' | 'GRPC';
  address: string;
  subscribedTopics: string[];
  lastHeartbeat: string;
  topicLag: Record<string, number>;
}

export interface ConsumerGroup {
  name: string;
  namespace: string;
  clusterId: string;
  subscriptionMode: 'Push' | 'Pop';
  consumeType: 'CLUSTERING' | 'BROADCASTING';
  onlineInstances: number;
  totalLag: number;
  subscribedTopics: string[];
  subscriptionDataType: 'NORMAL' | 'FIFO' | 'DELAY' | 'TRANSACTION';
  deliveryOrderType?: 'PARTITON_ORDER' | 'MESSAGES_ORDER';
  retryMaxTimes: number;
  createdAt: string;
  updatedAt: string;
  delaySeconds: number;
  instances: ConsumerInstance[];
}

export interface QueueProgress {
  broker: string;
  queueId: number;
  brokerOffset: number;
  consumerOffset: number;
  diffTotal: number;
}

export interface SubscriptionEntry {
  topic: string;
  expression: string;
  type: 'NORMAL' | 'FIFO' | 'DELAY' | 'TRANSACTION';
  filterMode: 'Tag 过滤' | 'SQL92 过滤' | '全量';
  consistency: '一致' | '不一致';
}

/* ─── Mock Data ─── */

export const mockConsumerGroups: ConsumerGroup[] = [
  {
    name: 'cg-order-notify',
    namespace: 'trade',
    clusterId: 'hz-prod',
    subscriptionMode: 'Push',
    consumeType: 'CLUSTERING',
    onlineInstances: 6,
    totalLag: 2340,
    subscribedTopics: ['order-create', 'order-status-change', 'payment-callback'],
    subscriptionDataType: 'NORMAL',
    retryMaxTimes: 16,
    createdAt: '2026-03-15 10:30:00',
    updatedAt: '2026-06-28 15:20:00',
    delaySeconds: 245,
    instances: [
      {
        clientId: 'order-notify-0@10.0.1.12',
        protocol: 'GRPC',
        address: '10.0.1.12:49152',
        subscribedTopics: ['order-create', 'order-status-change'],
        lastHeartbeat: '2026-07-01 18:32:05',
        topicLag: { 'order-create': 180, 'order-status-change': 95 },
      },
      {
        clientId: 'order-notify-1@10.0.1.13',
        protocol: 'GRPC',
        address: '10.0.1.13:49200',
        subscribedTopics: ['order-create', 'payment-callback'],
        lastHeartbeat: '2026-07-01 18:32:03',
        topicLag: { 'order-create': 210, 'payment-callback': 88 },
      },
      {
        clientId: 'order-notify-2@10.0.1.14',
        protocol: 'GRPC',
        address: '10.0.1.14:49300',
        subscribedTopics: ['order-status-change', 'payment-callback'],
        lastHeartbeat: '2026-07-01 18:32:01',
        topicLag: { 'order-status-change': 156, 'payment-callback': 42 },
      },
      {
        clientId: 'order-notify-3@10.0.1.15',
        protocol: 'REMOTING',
        address: '10.0.1.15:49400',
        subscribedTopics: ['order-create', 'order-status-change', 'payment-callback'],
        lastHeartbeat: '2026-07-01 18:31:58',
        topicLag: { 'order-create': 320, 'order-status-change': 201, 'payment-callback': 67 },
      },
      {
        clientId: 'order-notify-4@10.0.1.16',
        protocol: 'GRPC',
        address: '10.0.1.16:49500',
        subscribedTopics: ['order-create'],
        lastHeartbeat: '2026-07-01 18:32:04',
        topicLag: { 'order-create': 455 },
      },
      {
        clientId: 'order-notify-5@10.0.1.17',
        protocol: 'GRPC',
        address: '10.0.1.17:49600',
        subscribedTopics: ['order-status-change', 'payment-callback'],
        lastHeartbeat: '2026-07-01 18:32:02',
        topicLag: { 'order-status-change': 312, 'payment-callback': 214 },
      },
    ],
  },
  {
    name: 'cg-payment-callback',
    namespace: 'trade',
    clusterId: 'hz-prod',
    subscriptionMode: 'Push',
    consumeType: 'CLUSTERING',
    onlineInstances: 4,
    totalLag: 128,
    subscribedTopics: ['payment-callback', 'refund-event'],
    subscriptionDataType: 'TRANSACTION',
    retryMaxTimes: 8,
    createdAt: '2026-02-20 14:15:00',
    updatedAt: '2026-06-15 10:45:00',
    delaySeconds: 50,
    instances: [
      {
        clientId: 'payment-cb-0@10.0.2.10',
        protocol: 'GRPC',
        address: '10.0.2.10:50100',
        subscribedTopics: ['payment-callback', 'refund-event'],
        lastHeartbeat: '2026-07-01 18:32:00',
        topicLag: { 'payment-callback': 35, 'refund-event': 12 },
      },
      {
        clientId: 'payment-cb-1@10.0.2.11',
        protocol: 'GRPC',
        address: '10.0.2.11:50200',
        subscribedTopics: ['payment-callback'],
        lastHeartbeat: '2026-07-01 18:31:58',
        topicLag: { 'payment-callback': 28 },
      },
      {
        clientId: 'payment-cb-2@10.0.2.12',
        protocol: 'GRPC',
        address: '10.0.2.12:50300',
        subscribedTopics: ['refund-event'],
        lastHeartbeat: '2026-07-01 18:32:02',
        topicLag: { 'refund-event': 22 },
      },
      {
        clientId: 'payment-cb-3@10.0.2.13',
        protocol: 'REMOTING',
        address: '10.0.2.13:50400',
        subscribedTopics: ['payment-callback', 'refund-event'],
        lastHeartbeat: '2026-07-01 18:31:55',
        topicLag: { 'payment-callback': 18, 'refund-event': 13 },
      },
    ],
  },
  {
    name: 'cg-user-activity',
    namespace: 'user',
    clusterId: 'hz-prod',
    subscriptionMode: 'Push',
    consumeType: 'BROADCASTING',
    onlineInstances: 3,
    totalLag: 56700,
    subscribedTopics: ['user-activity-log', 'user-profile-change'],
    subscriptionDataType: 'NORMAL',
    retryMaxTimes: 16,
    createdAt: '2026-01-10 09:00:00',
    updatedAt: '2026-05-20 16:30:00',
    delaySeconds: 3725,
    instances: [
      {
        clientId: 'user-act-0@10.0.3.20',
        protocol: 'GRPC',
        address: '10.0.3.20:51100',
        subscribedTopics: ['user-activity-log', 'user-profile-change'],
        lastHeartbeat: '2026-07-01 18:31:50',
        topicLag: { 'user-activity-log': 18500, 'user-profile-change': 420 },
      },
      {
        clientId: 'user-act-1@10.0.3.21',
        protocol: 'GRPC',
        address: '10.0.3.21:51200',
        subscribedTopics: ['user-activity-log'],
        lastHeartbeat: '2026-07-01 18:31:48',
        topicLag: { 'user-activity-log': 19200 },
      },
      {
        clientId: 'user-act-2@10.0.3.22',
        protocol: 'REMOTING',
        address: '10.0.3.22:51300',
        subscribedTopics: ['user-activity-log', 'user-profile-change'],
        lastHeartbeat: '2026-07-01 18:31:52',
        topicLag: { 'user-activity-log': 17800, 'user-profile-change': 780 },
      },
    ],
  },
  {
    name: 'cg-inventory-sync',
    namespace: 'supply',
    clusterId: 'sh-prod',
    subscriptionMode: 'Push',
    consumeType: 'CLUSTERING',
    onlineInstances: 2,
    totalLag: 890,
    subscribedTopics: ['inventory-sync', 'stock-alert'],
    subscriptionDataType: 'FIFO',
    deliveryOrderType: 'PARTITON_ORDER',
    retryMaxTimes: 3,
    createdAt: '2026-04-05 11:20:00',
    updatedAt: '2026-06-30 08:10:00',
    delaySeconds: 720,
    instances: [
      {
        clientId: 'inv-sync-0@10.0.4.30',
        protocol: 'GRPC',
        address: '10.0.4.30:52100',
        subscribedTopics: ['inventory-sync', 'stock-alert'],
        lastHeartbeat: '2026-07-01 18:31:45',
        topicLag: { 'inventory-sync': 520, 'stock-alert': 85 },
      },
      {
        clientId: 'inv-sync-1@10.0.4.31',
        protocol: 'GRPC',
        address: '10.0.4.31:52200',
        subscribedTopics: ['inventory-sync'],
        lastHeartbeat: '2026-07-01 18:31:42',
        topicLag: { 'inventory-sync': 285 },
      },
    ],
  },
  {
    name: 'cg-log-collector',
    namespace: 'infra',
    clusterId: 'hz-prod',
    subscriptionMode: 'Pop',
    consumeType: 'CLUSTERING',
    onlineInstances: 8,
    totalLag: 12400,
    subscribedTopics: ['app-log', 'access-log', 'error-log'],
    subscriptionDataType: 'NORMAL',
    retryMaxTimes: 5,
    createdAt: '2026-05-12 16:00:00',
    updatedAt: '2026-07-01 12:00:00',
    delaySeconds: 1850,
    instances: [
      {
        clientId: 'log-col-0@10.0.5.40',
        protocol: 'GRPC',
        address: '10.0.5.40:53100',
        subscribedTopics: ['app-log', 'access-log'],
        lastHeartbeat: '2026-07-01 18:32:08',
        topicLag: { 'app-log': 1850, 'access-log': 620 },
      },
      {
        clientId: 'log-col-1@10.0.5.41',
        protocol: 'GRPC',
        address: '10.0.5.41:53200',
        subscribedTopics: ['app-log', 'error-log'],
        lastHeartbeat: '2026-07-01 18:32:06',
        topicLag: { 'app-log': 1720, 'error-log': 310 },
      },
      {
        clientId: 'log-col-2@10.0.5.42',
        protocol: 'GRPC',
        address: '10.0.5.42:53300',
        subscribedTopics: ['access-log', 'error-log'],
        lastHeartbeat: '2026-07-01 18:32:04',
        topicLag: { 'access-log': 1450, 'error-log': 280 },
      },
      {
        clientId: 'log-col-3@10.0.5.43',
        protocol: 'GRPC',
        address: '10.0.5.43:53400',
        subscribedTopics: ['app-log'],
        lastHeartbeat: '2026-07-01 18:32:02',
        topicLag: { 'app-log': 1980 },
      },
      {
        clientId: 'log-col-4@10.0.5.44',
        protocol: 'GRPC',
        address: '10.0.5.44:53500',
        subscribedTopics: ['access-log'],
        lastHeartbeat: '2026-07-01 18:32:00',
        topicLag: { 'access-log': 1100 },
      },
      {
        clientId: 'log-col-5@10.0.5.45',
        protocol: 'GRPC',
        address: '10.0.5.45:53600',
        subscribedTopics: ['error-log'],
        lastHeartbeat: '2026-07-01 18:31:58',
        topicLag: { 'error-log': 420 },
      },
      {
        clientId: 'log-col-6@10.0.5.46',
        protocol: 'REMOTING',
        address: '10.0.5.46:53700',
        subscribedTopics: ['app-log', 'access-log', 'error-log'],
        lastHeartbeat: '2026-07-01 18:31:55',
        topicLag: { 'app-log': 950, 'access-log': 580, 'error-log': 190 },
      },
      {
        clientId: 'log-col-7@10.0.5.47',
        protocol: 'GRPC',
        address: '10.0.5.47:53800',
        subscribedTopics: ['app-log', 'error-log'],
        lastHeartbeat: '2026-07-01 18:31:52',
        topicLag: { 'app-log': 820, 'error-log': 130 },
      },
    ],
  },
  {
    name: 'cg-notification-push',
    namespace: 'message',
    clusterId: 'hz-prod',
    subscriptionMode: 'Push',
    consumeType: 'CLUSTERING',
    onlineInstances: 3,
    totalLag: 45,
    subscribedTopics: ['notification-push', 'sms-gateway'],
    subscriptionDataType: 'DELAY',
    retryMaxTimes: 8,
    createdAt: '2026-03-28 08:45:00',
    updatedAt: '2026-06-22 14:30:00',
    delaySeconds: 8,
    instances: [
      {
        clientId: 'notif-push-0@10.0.6.50',
        protocol: 'GRPC',
        address: '10.0.6.50:54100',
        subscribedTopics: ['notification-push'],
        lastHeartbeat: '2026-07-01 18:32:10',
        topicLag: { 'notification-push': 12 },
      },
      {
        clientId: 'notif-push-1@10.0.6.51',
        protocol: 'GRPC',
        address: '10.0.6.51:54200',
        subscribedTopics: ['notification-push', 'sms-gateway'],
        lastHeartbeat: '2026-07-01 18:32:08',
        topicLag: { 'notification-push': 8, 'sms-gateway': 15 },
      },
      {
        clientId: 'notif-push-2@10.0.6.52',
        protocol: 'GRPC',
        address: '10.0.6.52:54300',
        subscribedTopics: ['sms-gateway'],
        lastHeartbeat: '2026-07-01 18:32:06',
        topicLag: { 'sms-gateway': 10 },
      },
    ],
  },
  {
    name: 'cg-ai-task-worker',
    namespace: 'ai',
    clusterId: 'hz-prod',
    subscriptionMode: 'Pop',
    consumeType: 'CLUSTERING',
    onlineInstances: 4,
    totalLag: 3200,
    subscribedTopics: ['ai-task-dispatch', 'model-inference-request'],
    subscriptionDataType: 'NORMAL',
    retryMaxTimes: 3,
    createdAt: '2026-06-01 13:30:00',
    updatedAt: '2026-07-01 09:15:00',
    delaySeconds: 4800,
    instances: [
      {
        clientId: 'ai-worker-0@10.0.7.60',
        protocol: 'GRPC',
        address: '10.0.7.60:55100',
        subscribedTopics: ['ai-task-dispatch', 'model-inference-request'],
        lastHeartbeat: '2026-07-01 18:32:12',
        topicLag: { 'ai-task-dispatch': 450, 'model-inference-request': 380 },
      },
      {
        clientId: 'ai-worker-1@10.0.7.61',
        protocol: 'GRPC',
        address: '10.0.7.61:55200',
        subscribedTopics: ['ai-task-dispatch'],
        lastHeartbeat: '2026-07-01 18:32:10',
        topicLag: { 'ai-task-dispatch': 620 },
      },
      {
        clientId: 'ai-worker-2@10.0.7.62',
        protocol: 'GRPC',
        address: '10.0.7.62:55300',
        subscribedTopics: ['model-inference-request'],
        lastHeartbeat: '2026-07-01 18:32:08',
        topicLag: { 'model-inference-request': 850 },
      },
      {
        clientId: 'ai-worker-3@10.0.7.63',
        protocol: 'GRPC',
        address: '10.0.7.63:55400',
        subscribedTopics: ['ai-task-dispatch', 'model-inference-request'],
        lastHeartbeat: '2026-07-01 18:32:06',
        topicLag: { 'ai-task-dispatch': 380, 'model-inference-request': 520 },
      },
    ],
  },
  {
    name: 'cg-metrics-aggregator',
    namespace: 'infra',
    clusterId: 'sh-prod',
    subscriptionMode: 'Push',
    consumeType: 'CLUSTERING',
    onlineInstances: 2,
    totalLag: 15800,
    subscribedTopics: ['metrics-raw', 'trace-span'],
    subscriptionDataType: 'NORMAL',
    retryMaxTimes: 16,
    createdAt: '2026-02-08 07:00:00',
    updatedAt: '2026-06-10 11:45:00',
    delaySeconds: 82500,
    instances: [
      {
        clientId: 'metrics-agg-0@10.0.8.70',
        protocol: 'GRPC',
        address: '10.0.8.70:56100',
        subscribedTopics: ['metrics-raw', 'trace-span'],
        lastHeartbeat: '2026-07-01 18:31:40',
        topicLag: { 'metrics-raw': 8500, 'trace-span': 3200 },
      },
      {
        clientId: 'metrics-agg-1@10.0.8.71',
        protocol: 'REMOTING',
        address: '10.0.8.71:56200',
        subscribedTopics: ['metrics-raw', 'trace-span'],
        lastHeartbeat: '2026-07-01 18:31:38',
        topicLag: { 'metrics-raw': 2800, 'trace-span': 1300 },
      },
    ],
  },
  {
    name: 'cg-risk-control',
    namespace: 'risk',
    clusterId: 'hz-prod',
    subscriptionMode: 'Push',
    consumeType: 'CLUSTERING',
    onlineInstances: 5,
    totalLag: 4560,
    subscribedTopics: ['transaction-event', 'login-event', 'payment-callback'],
    subscriptionDataType: 'NORMAL',
    retryMaxTimes: 8,
    createdAt: '2025-11-20 10:00:00',
    updatedAt: '2026-06-05 17:20:00',
    delaySeconds: 1520,
    instances: [
      {
        clientId: 'risk-ctrl-0@10.0.9.80',
        protocol: 'GRPC',
        address: '10.0.9.80:57100',
        subscribedTopics: ['transaction-event', 'login-event'],
        lastHeartbeat: '2026-07-01 18:32:15',
        topicLag: { 'transaction-event': 520, 'login-event': 380 },
      },
      {
        clientId: 'risk-ctrl-1@10.0.9.81',
        protocol: 'GRPC',
        address: '10.0.9.81:57200',
        subscribedTopics: ['transaction-event', 'payment-callback'],
        lastHeartbeat: '2026-07-01 18:32:13',
        topicLag: { 'transaction-event': 480, 'payment-callback': 290 },
      },
      {
        clientId: 'risk-ctrl-2@10.0.9.82',
        protocol: 'GRPC',
        address: '10.0.9.82:57300',
        subscribedTopics: ['login-event', 'payment-callback'],
        lastHeartbeat: '2026-07-01 18:32:11',
        topicLag: { 'login-event': 410, 'payment-callback': 350 },
      },
      {
        clientId: 'risk-ctrl-3@10.0.9.83',
        protocol: 'GRPC',
        address: '10.0.9.83:57400',
        subscribedTopics: ['transaction-event', 'login-event', 'payment-callback'],
        lastHeartbeat: '2026-07-01 18:32:09',
        topicLag: { 'transaction-event': 600, 'login-event': 420, 'payment-callback': 310 },
      },
      {
        clientId: 'risk-ctrl-4@10.0.9.84',
        protocol: 'REMOTING',
        address: '10.0.9.84:57500',
        subscribedTopics: ['transaction-event'],
        lastHeartbeat: '2026-07-01 18:32:07',
        topicLag: { 'transaction-event': 800 },
      },
    ],
  },
  {
    name: 'cg-data-sync',
    namespace: 'data',
    clusterId: 'sh-prod',
    subscriptionMode: 'Push',
    consumeType: 'CLUSTERING',
    onlineInstances: 3,
    totalLag: 18700,
    subscribedTopics: ['binlog-event', 'schema-change'],
    subscriptionDataType: 'FIFO',
    deliveryOrderType: 'PARTITON_ORDER',
    retryMaxTimes: 5,
    createdAt: '2025-09-15 08:30:00',
    updatedAt: '2026-06-28 20:00:00',
    delaySeconds: 12600,
    instances: [
      {
        clientId: 'data-sync-0@10.0.10.90',
        protocol: 'GRPC',
        address: '10.0.10.90:58100',
        subscribedTopics: ['binlog-event', 'schema-change'],
        lastHeartbeat: '2026-07-01 18:31:55',
        topicLag: { 'binlog-event': 8500, 'schema-change': 120 },
      },
      {
        clientId: 'data-sync-1@10.0.10.91',
        protocol: 'GRPC',
        address: '10.0.10.91:58200',
        subscribedTopics: ['binlog-event'],
        lastHeartbeat: '2026-07-01 18:31:53',
        topicLag: { 'binlog-event': 9200 },
      },
      {
        clientId: 'data-sync-2@10.0.10.92',
        protocol: 'GRPC',
        address: '10.0.10.92:58300',
        subscribedTopics: ['binlog-event', 'schema-change'],
        lastHeartbeat: '2026-07-01 18:31:50',
        topicLag: { 'binlog-event': 7800, 'schema-change': 80 },
      },
    ],
  },
];

/* ─── Queue Progress (per group) ─── */

export const mockQueueProgress: Record<string, QueueProgress[]> = {
  'cg-order-notify': [
    {
      broker: 'broker-hz-0',
      queueId: 0,
      brokerOffset: 1_580_420,
      consumerOffset: 1_579_850,
      diffTotal: 570,
    },
    {
      broker: 'broker-hz-0',
      queueId: 1,
      brokerOffset: 1_579_200,
      consumerOffset: 1_578_900,
      diffTotal: 300,
    },
    {
      broker: 'broker-hz-0',
      queueId: 2,
      brokerOffset: 1_578_800,
      consumerOffset: 1_578_380,
      diffTotal: 420,
    },
    {
      broker: 'broker-hz-1',
      queueId: 0,
      brokerOffset: 1_420_300,
      consumerOffset: 1_419_950,
      diffTotal: 350,
    },
    {
      broker: 'broker-hz-1',
      queueId: 1,
      brokerOffset: 1_419_800,
      consumerOffset: 1_419_450,
      diffTotal: 350,
    },
    {
      broker: 'broker-hz-1',
      queueId: 2,
      brokerOffset: 1_418_500,
      consumerOffset: 1_418_150,
      diffTotal: 350,
    },
  ],
  'cg-payment-callback': [
    {
      broker: 'broker-hz-0',
      queueId: 0,
      brokerOffset: 892_100,
      consumerOffset: 892_068,
      diffTotal: 32,
    },
    {
      broker: 'broker-hz-0',
      queueId: 1,
      brokerOffset: 891_500,
      consumerOffset: 891_468,
      diffTotal: 32,
    },
    {
      broker: 'broker-hz-1',
      queueId: 0,
      brokerOffset: 780_200,
      consumerOffset: 780_168,
      diffTotal: 32,
    },
    {
      broker: 'broker-hz-1',
      queueId: 1,
      brokerOffset: 779_800,
      consumerOffset: 779_768,
      diffTotal: 32,
    },
  ],
  'cg-user-activity': [
    {
      broker: 'broker-hz-0',
      queueId: 0,
      brokerOffset: 5_680_000,
      consumerOffset: 5_661_200,
      diffTotal: 18800,
    },
    {
      broker: 'broker-hz-0',
      queueId: 1,
      brokerOffset: 5_675_000,
      consumerOffset: 5_655_500,
      diffTotal: 19500,
    },
    {
      broker: 'broker-hz-1',
      queueId: 0,
      brokerOffset: 4_920_000,
      consumerOffset: 4_910_700,
      diffTotal: 9300,
    },
    {
      broker: 'broker-hz-1',
      queueId: 1,
      brokerOffset: 4_915_000,
      consumerOffset: 4_905_900,
      diffTotal: 9100,
    },
  ],
  'cg-inventory-sync': [
    {
      broker: 'broker-sh-0',
      queueId: 0,
      brokerOffset: 320_800,
      consumerOffset: 320_355,
      diffTotal: 445,
    },
    {
      broker: 'broker-sh-0',
      queueId: 1,
      brokerOffset: 320_100,
      consumerOffset: 319_655,
      diffTotal: 445,
    },
  ],
  'cg-log-collector': [
    {
      broker: 'broker-hz-0',
      queueId: 0,
      brokerOffset: 12_800_000,
      consumerOffset: 12_798_450,
      diffTotal: 1550,
    },
    {
      broker: 'broker-hz-0',
      queueId: 1,
      brokerOffset: 12_790_000,
      consumerOffset: 12_788_300,
      diffTotal: 1700,
    },
    {
      broker: 'broker-hz-0',
      queueId: 2,
      brokerOffset: 12_780_000,
      consumerOffset: 12_778_600,
      diffTotal: 1400,
    },
    {
      broker: 'broker-hz-0',
      queueId: 3,
      brokerOffset: 12_770_000,
      consumerOffset: 12_768_700,
      diffTotal: 1300,
    },
    {
      broker: 'broker-hz-1',
      queueId: 0,
      brokerOffset: 11_200_000,
      consumerOffset: 11_198_450,
      diffTotal: 1550,
    },
    {
      broker: 'broker-hz-1',
      queueId: 1,
      brokerOffset: 11_190_000,
      consumerOffset: 11_188_450,
      diffTotal: 1550,
    },
    {
      broker: 'broker-hz-2',
      queueId: 0,
      brokerOffset: 9_800_000,
      consumerOffset: 9_798_200,
      diffTotal: 1800,
    },
    {
      broker: 'broker-hz-2',
      queueId: 1,
      brokerOffset: 9_790_000,
      consumerOffset: 9_788_450,
      diffTotal: 1550,
    },
  ],
  'cg-notification-push': [
    {
      broker: 'broker-hz-0',
      queueId: 0,
      brokerOffset: 450_200,
      consumerOffset: 450_185,
      diffTotal: 15,
    },
    {
      broker: 'broker-hz-0',
      queueId: 1,
      brokerOffset: 449_800,
      consumerOffset: 449_785,
      diffTotal: 15,
    },
    {
      broker: 'broker-hz-1',
      queueId: 0,
      brokerOffset: 380_100,
      consumerOffset: 380_092,
      diffTotal: 8,
    },
    {
      broker: 'broker-hz-1',
      queueId: 1,
      brokerOffset: 379_800,
      consumerOffset: 379_793,
      diffTotal: 7,
    },
  ],
  'cg-ai-task-worker': [
    {
      broker: 'broker-hz-0',
      queueId: 0,
      brokerOffset: 240_800,
      consumerOffset: 240_000,
      diffTotal: 800,
    },
    {
      broker: 'broker-hz-0',
      queueId: 1,
      brokerOffset: 240_200,
      consumerOffset: 239_400,
      diffTotal: 800,
    },
    {
      broker: 'broker-hz-1',
      queueId: 0,
      brokerOffset: 210_600,
      consumerOffset: 209_800,
      diffTotal: 800,
    },
    {
      broker: 'broker-hz-1',
      queueId: 1,
      brokerOffset: 210_000,
      consumerOffset: 209_200,
      diffTotal: 800,
    },
  ],
  'cg-metrics-aggregator': [
    {
      broker: 'broker-sh-0',
      queueId: 0,
      brokerOffset: 8_900_000,
      consumerOffset: 8_892_100,
      diffTotal: 7900,
    },
    {
      broker: 'broker-sh-0',
      queueId: 1,
      brokerOffset: 8_890_000,
      consumerOffset: 8_882_100,
      diffTotal: 7900,
    },
  ],
  'cg-risk-control': [
    {
      broker: 'broker-hz-0',
      queueId: 0,
      brokerOffset: 3_200_000,
      consumerOffset: 3_199_100,
      diffTotal: 900,
    },
    {
      broker: 'broker-hz-0',
      queueId: 1,
      brokerOffset: 3_198_000,
      consumerOffset: 3_197_200,
      diffTotal: 800,
    },
    {
      broker: 'broker-hz-1',
      queueId: 0,
      brokerOffset: 2_800_000,
      consumerOffset: 2_799_060,
      diffTotal: 940,
    },
    {
      broker: 'broker-hz-1',
      queueId: 1,
      brokerOffset: 2_795_000,
      consumerOffset: 2_794_080,
      diffTotal: 920,
    },
    {
      broker: 'broker-hz-2',
      queueId: 0,
      brokerOffset: 2_400_000,
      consumerOffset: 2_399_000,
      diffTotal: 1000,
    },
  ],
  'cg-data-sync': [
    {
      broker: 'broker-sh-0',
      queueId: 0,
      brokerOffset: 6_500_000,
      consumerOffset: 6_493_500,
      diffTotal: 6500,
    },
    {
      broker: 'broker-sh-0',
      queueId: 1,
      brokerOffset: 6_490_000,
      consumerOffset: 6_483_800,
      diffTotal: 6200,
    },
    {
      broker: 'broker-sh-1',
      queueId: 0,
      brokerOffset: 5_800_000,
      consumerOffset: 5_797_000,
      diffTotal: 3000,
    },
    {
      broker: 'broker-sh-1',
      queueId: 1,
      brokerOffset: 5_795_000,
      consumerOffset: 5_792_000,
      diffTotal: 3000,
    },
  ],
};

/* ─── Subscription Details (per group) ─── */

export const mockSubscriptions: Record<string, SubscriptionEntry[]> = {
  'cg-order-notify': [
    {
      topic: 'order-create',
      expression: '*',
      type: 'NORMAL',
      filterMode: '全量',
      consistency: '一致',
    },
    {
      topic: 'order-status-change',
      expression: 'STATUS_PAID || STATUS_SHIPPED',
      type: 'NORMAL',
      filterMode: 'Tag 过滤',
      consistency: '一致',
    },
    {
      topic: 'payment-callback',
      expression: '*',
      type: 'TRANSACTION',
      filterMode: '全量',
      consistency: '不一致',
    },
  ],
  'cg-payment-callback': [
    {
      topic: 'payment-callback',
      expression: '*',
      type: 'TRANSACTION',
      filterMode: '全量',
      consistency: '一致',
    },
    {
      topic: 'refund-event',
      expression: 'AMOUNT > 100',
      type: 'TRANSACTION',
      filterMode: 'SQL92 过滤',
      consistency: '一致',
    },
  ],
  'cg-user-activity': [
    {
      topic: 'user-activity-log',
      expression: '*',
      type: 'NORMAL',
      filterMode: '全量',
      consistency: '一致',
    },
    {
      topic: 'user-profile-change',
      expression: '*',
      type: 'NORMAL',
      filterMode: '全量',
      consistency: '一致',
    },
  ],
  'cg-inventory-sync': [
    {
      topic: 'inventory-sync',
      expression: '*',
      type: 'FIFO',
      filterMode: '全量',
      consistency: '一致',
    },
    {
      topic: 'stock-alert',
      expression: 'LEVEL < 10',
      type: 'FIFO',
      filterMode: 'SQL92 过滤',
      consistency: '一致',
    },
  ],
  'cg-log-collector': [
    {
      topic: 'app-log',
      expression: 'LEVEL in (ERROR, WARN)',
      type: 'NORMAL',
      filterMode: 'SQL92 过滤',
      consistency: '一致',
    },
    {
      topic: 'access-log',
      expression: '*',
      type: 'NORMAL',
      filterMode: '全量',
      consistency: '不一致',
    },
    {
      topic: 'error-log',
      expression: '*',
      type: 'NORMAL',
      filterMode: '全量',
      consistency: '一致',
    },
  ],
  'cg-notification-push': [
    {
      topic: 'notification-push',
      expression: 'PRIORITY >= 3',
      type: 'DELAY',
      filterMode: 'SQL92 过滤',
      consistency: '一致',
    },
    {
      topic: 'sms-gateway',
      expression: '*',
      type: 'DELAY',
      filterMode: '全量',
      consistency: '一致',
    },
  ],
  'cg-ai-task-worker': [
    {
      topic: 'ai-task-dispatch',
      expression: 'MODEL in (GPT, CLAUDE)',
      type: 'NORMAL',
      filterMode: 'SQL92 过滤',
      consistency: '一致',
    },
    {
      topic: 'model-inference-request',
      expression: '*',
      type: 'NORMAL',
      filterMode: '全量',
      consistency: '一致',
    },
  ],
  'cg-metrics-aggregator': [
    {
      topic: 'metrics-raw',
      expression: '*',
      type: 'NORMAL',
      filterMode: '全量',
      consistency: '一致',
    },
    {
      topic: 'trace-span',
      expression: 'DURATION > 500',
      type: 'NORMAL',
      filterMode: 'SQL92 过滤',
      consistency: '不一致',
    },
  ],
  'cg-risk-control': [
    {
      topic: 'transaction-event',
      expression: 'AMOUNT > 5000',
      type: 'NORMAL',
      filterMode: 'SQL92 过滤',
      consistency: '一致',
    },
    {
      topic: 'login-event',
      expression: '*',
      type: 'NORMAL',
      filterMode: '全量',
      consistency: '一致',
    },
    {
      topic: 'payment-callback',
      expression: 'RISK_LEVEL >= 2',
      type: 'NORMAL',
      filterMode: 'SQL92 过滤',
      consistency: '一致',
    },
  ],
  'cg-data-sync': [
    {
      topic: 'binlog-event',
      expression: '*',
      type: 'FIFO',
      filterMode: '全量',
      consistency: '一致',
    },
    {
      topic: 'schema-change',
      expression: 'DDL',
      type: 'FIFO',
      filterMode: 'Tag 过滤',
      consistency: '一致',
    },
  ],
};
