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

export interface Topic {
  name: string;
  namespace: string;
  type: 'NORMAL' | 'FIFO' | 'DELAY' | 'TRANSACTION' | 'LITE';
  clusterId: string;
  writeQueues: number;
  readQueues: number;
  perm: 'RW' | 'RO' | 'WO';
  messageCount: number;
  tps: number;
  consumerGroupCount: number;
  remark: string;
  createdAt: string;
  updatedAt: string;
}

export const topics: Topic[] = [
  // NORMAL topics (4)
  {
    name: 'order-create',
    namespace: 'trade',
    type: 'NORMAL',
    clusterId: 'rmq-cn-v5-prod-01',
    writeQueues: 16,
    readQueues: 16,
    perm: 'RW',
    messageCount: 1_842_350,
    tps: 1280,
    consumerGroupCount: 8,
    remark: '订单创建事件通知',
    createdAt: '2024-03-15T08:30:00Z',
    updatedAt: '2024-03-15T10:15:00Z',
  },
  {
    name: 'user-activity-log',
    namespace: 'user',
    type: 'NORMAL',
    clusterId: 'rmq-cn-v5-prod-01',
    writeQueues: 16,
    readQueues: 16,
    perm: 'RW',
    messageCount: 5_623_100,
    tps: 3450,
    consumerGroupCount: 5,
    remark: '用户行为日志同步',
    createdAt: '2024-01-20T10:00:00Z',
    updatedAt: '2024-01-20T14:30:00Z',
  },
  {
    name: 'system-log',
    namespace: 'message',
    type: 'NORMAL',
    clusterId: 'rmq-cn-v4-prod-02',
    writeQueues: 8,
    readQueues: 8,
    perm: 'RW',
    messageCount: 12_480_000,
    tps: 8620,
    consumerGroupCount: 3,
    remark: '系统日志收集与分发',
    createdAt: '2023-11-05T14:20:00Z',
    updatedAt: '2023-11-06T09:45:00Z',
  },
  {
    name: 'notification-email',
    namespace: 'message',
    type: 'NORMAL',
    clusterId: 'rmq-cn-v5-prod-01',
    writeQueues: 4,
    readQueues: 4,
    perm: 'RW',
    messageCount: 328_700,
    tps: 215,
    consumerGroupCount: 2,
    remark: '邮件通知触发',
    createdAt: '2024-06-10T09:15:00Z',
    updatedAt: '2024-06-10T11:20:00Z',
  },

  // FIFO topics (2)
  {
    name: 'inventory-sync',
    namespace: 'supply',
    type: 'FIFO',
    clusterId: 'rmq-cn-v5-prod-01',
    writeQueues: 16,
    readQueues: 16,
    perm: 'RW',
    messageCount: 956_400,
    tps: 680,
    consumerGroupCount: 4,
    remark: '库存同步顺序消息',
    createdAt: '2024-04-22T11:00:00Z',
    updatedAt: '2024-04-22T15:30:00Z',
  },
  {
    name: 'payment-sequence',
    namespace: 'trade',
    type: 'FIFO',
    clusterId: 'rmq-cn-v5-prod-01',
    writeQueues: 8,
    readQueues: 8,
    perm: 'RW',
    messageCount: 412_850,
    tps: 320,
    consumerGroupCount: 6,
    remark: '支付流程顺序处理',
    createdAt: '2024-05-18T16:30:00Z',
    updatedAt: '2024-05-19T08:45:00Z',
  },

  // DELAY topics (2)
  {
    name: 'notification-push',
    namespace: 'message',
    type: 'DELAY',
    clusterId: 'rmq-cn-v5-prod-01',
    writeQueues: 16,
    readQueues: 16,
    perm: 'RW',
    messageCount: 2_105_600,
    tps: 1540,
    consumerGroupCount: 3,
    remark: '延迟推送通知调度',
    createdAt: '2024-02-14T13:45:00Z',
    updatedAt: '2024-02-14T16:20:00Z',
  },
  {
    name: 'scheduled-task',
    namespace: 'supply',
    type: 'DELAY',
    clusterId: 'rmq-cn-v4-prod-02',
    writeQueues: 4,
    readQueues: 4,
    perm: 'RW',
    messageCount: 87_300,
    tps: 56,
    consumerGroupCount: 2,
    remark: '定时任务触发器',
    createdAt: '2024-07-01T08:00:00Z',
    updatedAt: '2024-07-01T10:30:00Z',
  },

  // TRANSACTION topics (2)
  {
    name: 'payment-callback',
    namespace: 'trade',
    type: 'TRANSACTION',
    clusterId: 'rmq-cn-v5-prod-01',
    writeQueues: 16,
    readQueues: 16,
    perm: 'RW',
    messageCount: 634_200,
    tps: 420,
    consumerGroupCount: 5,
    remark: '支付回调事务处理',
    createdAt: '2024-03-28T10:30:00Z',
    updatedAt: '2024-03-28T14:15:00Z',
  },
  {
    name: 'order-confirm',
    namespace: 'trade',
    type: 'TRANSACTION',
    clusterId: 'rmq-cn-v5-prod-01',
    writeQueues: 8,
    readQueues: 8,
    perm: 'RW',
    messageCount: 521_800,
    tps: 360,
    consumerGroupCount: 4,
    remark: '订单确认事务消息',
    createdAt: '2024-04-05T15:20:00Z',
    updatedAt: '2024-04-05T18:40:00Z',
  },

  // LITE topics (2)
  {
    name: 'chat-session',
    namespace: 'ai',
    type: 'LITE',
    clusterId: 'rmq-cn-v5-prod-01',
    writeQueues: 16,
    readQueues: 16,
    perm: 'RW',
    messageCount: 8_920_000,
    tps: 6200,
    consumerGroupCount: 1,
    remark: 'AI 对话会话消息分发',
    createdAt: '2025-01-10T09:00:00Z',
    updatedAt: '2025-01-10T11:30:00Z',
  },
  {
    name: 'ai-task-dispatch',
    namespace: 'ai',
    type: 'LITE',
    clusterId: 'rmq-cn-v5-prod-01',
    writeQueues: 16,
    readQueues: 16,
    perm: 'RW',
    messageCount: 3_450_000,
    tps: 2380,
    consumerGroupCount: 2,
    remark: 'AI 任务调度与状态同步',
    createdAt: '2025-02-20T14:00:00Z',
    updatedAt: '2025-02-20T16:45:00Z',
  },
];

// Mock route info for drawer
export interface BrokerRoute {
  brokerName: string;
  brokerAddr: string;
  writeQueues: number;
  readQueues: number;
  perm: string;
}

export const topicRoutes: Record<string, BrokerRoute[]> = {
  'order-create': [
    {
      brokerName: 'broker-a-0',
      brokerAddr: '10.0.1.10:10911',
      writeQueues: 8,
      readQueues: 8,
      perm: 'RW',
    },
    {
      brokerName: 'broker-b-0',
      brokerAddr: '10.0.1.11:10911',
      writeQueues: 8,
      readQueues: 8,
      perm: 'RW',
    },
  ],
  'user-activity-log': [
    {
      brokerName: 'broker-a-0',
      brokerAddr: '10.0.1.10:10911',
      writeQueues: 8,
      readQueues: 8,
      perm: 'RW',
    },
    {
      brokerName: 'broker-b-0',
      brokerAddr: '10.0.1.11:10911',
      writeQueues: 8,
      readQueues: 8,
      perm: 'RW',
    },
  ],
  'system-log': [
    {
      brokerName: 'broker-a-0',
      brokerAddr: '10.0.2.10:10911',
      writeQueues: 4,
      readQueues: 4,
      perm: 'RW',
    },
    {
      brokerName: 'broker-b-0',
      brokerAddr: '10.0.2.11:10911',
      writeQueues: 4,
      readQueues: 4,
      perm: 'RW',
    },
  ],
  'inventory-sync': [
    {
      brokerName: 'broker-a-0',
      brokerAddr: '10.0.1.10:10911',
      writeQueues: 8,
      readQueues: 8,
      perm: 'RW',
    },
    {
      brokerName: 'broker-b-0',
      brokerAddr: '10.0.1.11:10911',
      writeQueues: 8,
      readQueues: 8,
      perm: 'RW',
    },
  ],
  'payment-callback': [
    {
      brokerName: 'broker-a-0',
      brokerAddr: '10.0.1.10:10911',
      writeQueues: 8,
      readQueues: 8,
      perm: 'RW',
    },
    {
      brokerName: 'broker-b-0',
      brokerAddr: '10.0.1.11:10911',
      writeQueues: 8,
      readQueues: 8,
      perm: 'RW',
    },
  ],
};

// Default route for topics not explicitly listed
export const defaultRoute: BrokerRoute[] = [
  {
    brokerName: 'broker-a-0',
    brokerAddr: '10.0.1.10:10911',
    writeQueues: 8,
    readQueues: 8,
    perm: 'RW',
  },
  {
    brokerName: 'broker-b-0',
    brokerAddr: '10.0.1.11:10911',
    writeQueues: 8,
    readQueues: 8,
    perm: 'RW',
  },
];

// Mock consumer groups for topics
export interface ConsumerGroupInfo {
  group: string;
  consumeType: 'CLUSTERING' | 'BROADCASTING';
  messageModel: string;
  consumeTps: number;
  diffTotal: number;
}

export const topicConsumers: Record<string, ConsumerGroupInfo[]> = {
  'order-create': [
    {
      group: 'GID_order_service',
      consumeType: 'CLUSTERING',
      messageModel: '集群消费',
      consumeTps: 640,
      diffTotal: 120,
    },
    {
      group: 'GID_inventory_deduction',
      consumeType: 'CLUSTERING',
      messageModel: '集群消费',
      consumeTps: 320,
      diffTotal: 0,
    },
    {
      group: 'GID_risk_control',
      consumeType: 'BROADCASTING',
      messageModel: '广播消费',
      consumeTps: 180,
      diffTotal: 45,
    },
    {
      group: 'GID_notification_trigger',
      consumeType: 'CLUSTERING',
      messageModel: '集群消费',
      consumeTps: 140,
      diffTotal: 0,
    },
  ],
  'user-activity-log': [
    {
      group: 'GID_user_profile_sync',
      consumeType: 'CLUSTERING',
      messageModel: '集群消费',
      consumeTps: 2100,
      diffTotal: 350,
    },
    {
      group: 'GID_recommendation_engine',
      consumeType: 'CLUSTERING',
      messageModel: '集群消费',
      consumeTps: 1350,
      diffTotal: 0,
    },
  ],
  'payment-callback': [
    {
      group: 'GID_payment_service',
      consumeType: 'CLUSTERING',
      messageModel: '集群消费',
      consumeTps: 280,
      diffTotal: 0,
    },
    {
      group: 'GID_order_status_update',
      consumeType: 'CLUSTERING',
      messageModel: '集群消费',
      consumeTps: 140,
      diffTotal: 12,
    },
  ],
  'inventory-sync': [
    {
      group: 'GID_warehouse_sync',
      consumeType: 'CLUSTERING',
      messageModel: '集群消费',
      consumeTps: 340,
      diffTotal: 0,
    },
    {
      group: 'GID_stock_alert',
      consumeType: 'CLUSTERING',
      messageModel: '集群消费',
      consumeTps: 340,
      diffTotal: 88,
    },
  ],
};

export const defaultConsumers: ConsumerGroupInfo[] = [
  {
    group: 'GID_default_consumer',
    consumeType: 'CLUSTERING',
    messageModel: '集群消费',
    consumeTps: 100,
    diffTotal: 0,
  },
];
