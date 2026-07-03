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

export interface MessageRecord {
  msgId: string;
  topic: string;
  tag: string;
  key: string;
  body: string;
  storeTime: string;
  bornHost: string;
  storeHost: string;
  properties: Record<string, string>;
  size: number;
}

export interface TraceNode {
  title: string;
  timestamp: string;
  status: 'finish' | 'process' | 'wait';
  costTime: number;
  description: string;
}

export interface ConsumerStatus {
  group: string;
  deliveryStatus: 'success' | 'failed' | 'pending';
  consumeTime: string;
  retryCount: number;
}

export interface TraceRecord {
  nodes: TraceNode[];
  consumerStatus: ConsumerStatus[];
}

export const mockMessages: MessageRecord[] = [
  // ── order-create (5 messages) ──
  {
    msgId: 'AC1E0A6400002A9F0000000001A3F2B1',
    topic: 'order-create',
    tag: 'create',
    key: 'order-12345',
    body: '{"orderId":"20240101001","userId":10001,"items":[{"skuId":"SKU001","quantity":2,"price":99.50}],"totalAmount":199.00}',
    storeTime: '2026-07-01T10:23:45.123Z',
    bornHost: '10.0.1.12:54321',
    storeHost: '10.0.2.3:10911',
    properties: {
      KEYS: 'order-12345',
      TAGS: 'create',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A3F2B1',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 3617,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A3F7C2',
    topic: 'order-create',
    tag: 'create',
    key: 'order-12346',
    body: '{"orderId":"20240101002","userId":10042,"items":[{"skuId":"SKU015","quantity":1,"price":59.00}],"totalAmount":59.00}',
    storeTime: '2026-07-01T10:25:12.456Z',
    bornHost: '10.0.1.12:54322',
    storeHost: '10.0.2.3:10911',
    properties: {
      KEYS: 'order-12346',
      TAGS: 'create',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A3F7C2',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 486,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A401D3',
    topic: 'order-create',
    tag: 'create',
    key: 'order-12347',
    body: '{"orderId":"20240101003","userId":10099,"items":[{"skuId":"SKU008","quantity":3,"price":33.00},{"skuId":"SKU012","quantity":1,"price":128.00}],"totalAmount":227.00}',
    storeTime: '2026-07-01T10:30:05.789Z',
    bornHost: '10.0.1.13:54321',
    storeHost: '10.0.2.4:10911',
    properties: {
      KEYS: 'order-12347',
      TAGS: 'create',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A401D3',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 1331200,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A40BE4',
    topic: 'order-create',
    tag: 'create',
    key: 'order-12348',
    body: '{"orderId":"20240101004","userId":10201,"items":[{"skuId":"SKU003","quantity":1,"price":1299.00}],"totalAmount":1299.00}',
    storeTime: '2026-07-01T11:15:33.234Z',
    bornHost: '10.0.1.12:54323',
    storeHost: '10.0.2.3:10911',
    properties: {
      KEYS: 'order-12348',
      TAGS: 'create',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A40BE4',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 456,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A415F5',
    topic: 'order-create',
    tag: 'create',
    key: 'order-12349',
    body: '{"orderId":"20240101005","userId":10077,"items":[{"skuId":"SKU022","quantity":2,"price":45.00}],"totalAmount":90.00}',
    storeTime: '2026-07-01T12:00:18.567Z',
    bornHost: '10.0.1.14:54321',
    storeHost: '10.0.2.5:10911',
    properties: {
      KEYS: 'order-12349',
      TAGS: 'create',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A415F5',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 442,
  },

  // ── payment-callback (5 messages) ──
  {
    msgId: 'AC1E0A6400002A9F0000000001A42006',
    topic: 'payment-callback',
    tag: 'success',
    key: 'pay-67890',
    body: '{"paymentId":"PAY20240101001","orderId":"20240101001","amount":199.00,"channel":"alipay","status":"PAID","paidAt":"2026-07-01T10:24:02.000Z"}',
    storeTime: '2026-07-01T10:24:05.890Z',
    bornHost: '10.0.1.20:54400',
    storeHost: '10.0.2.3:10911',
    properties: {
      KEYS: 'pay-67890',
      TAGS: 'success',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A42006',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
      TRANSACTION_ID: 'TXN20240101001',
    },
    size: 8724,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A42A17',
    topic: 'payment-callback',
    tag: 'success',
    key: 'pay-67891',
    body: '{"paymentId":"PAY20240101002","orderId":"20240101002","amount":59.00,"channel":"wechat","status":"PAID","paidAt":"2026-07-01T10:26:15.000Z"}',
    storeTime: '2026-07-01T10:26:18.123Z',
    bornHost: '10.0.1.20:54401',
    storeHost: '10.0.2.4:10911',
    properties: {
      KEYS: 'pay-67891',
      TAGS: 'success',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A42A17',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 598,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A43428',
    topic: 'payment-callback',
    tag: 'failed',
    key: 'pay-67892',
    body: '{"paymentId":"PAY20240101003","orderId":"20240101003","amount":227.00,"channel":"alipay","status":"FAILED","reason":"insufficient_balance"}',
    storeTime: '2026-07-01T10:32:45.456Z',
    bornHost: '10.0.1.21:54400',
    storeHost: '10.0.2.3:10911',
    properties: {
      KEYS: 'pay-67892',
      TAGS: 'failed',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A43428',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 672,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A43E39',
    topic: 'payment-callback',
    tag: 'success',
    key: 'pay-67893',
    body: '{"paymentId":"PAY20240101004","orderId":"20240101004","amount":1299.00,"channel":"bank_card","status":"PAID","paidAt":"2026-07-01T11:16:05.000Z"}',
    storeTime: '2026-07-01T11:16:08.789Z',
    bornHost: '10.0.1.20:54402',
    storeHost: '10.0.2.5:10911',
    properties: {
      KEYS: 'pay-67893',
      TAGS: 'success',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A43E39',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 2457600,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A4484A',
    topic: 'payment-callback',
    tag: 'refunded',
    key: 'pay-67894',
    body: '{"paymentId":"PAY20240101005","orderId":"20240101005","amount":90.00,"channel":"wechat","status":"REFUNDED","refundAmount":90.00,"reason":"user_request"}',
    storeTime: '2026-07-01T12:05:22.012Z',
    bornHost: '10.0.1.21:54401',
    storeHost: '10.0.2.3:10911',
    properties: {
      KEYS: 'pay-67894',
      TAGS: 'refunded',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A4484A',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 708,
  },

  // ── user-activity-log (4 messages) ──
  {
    msgId: 'AC1E0A6400002A9F0000000001A4525B',
    topic: 'user-activity-log',
    tag: 'login',
    key: 'user-10001',
    body: '{"userId":10001,"action":"login","device":"iOS 17.2","ip":"223.104.3.21","timestamp":"2026-07-01T09:15:00.000Z"}',
    storeTime: '2026-07-01T09:15:01.345Z',
    bornHost: '10.0.1.30:55000',
    storeHost: '10.0.2.4:10911',
    properties: {
      KEYS: 'user-10001',
      TAGS: 'login',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A4525B',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 5248,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A45C6C',
    topic: 'user-activity-log',
    tag: 'browse',
    key: 'user-10042',
    body: '{"userId":10042,"action":"browse","page":"product_detail","productId":"SKU015","duration":45}',
    storeTime: '2026-07-01T09:30:12.678Z',
    bornHost: '10.0.1.31:55001',
    storeHost: '10.0.2.3:10911',
    properties: {
      KEYS: 'user-10042',
      TAGS: 'browse',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A45C6C',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 312,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A4667D',
    topic: 'user-activity-log',
    tag: 'cart',
    key: 'user-10099',
    body: '{"userId":10099,"action":"add_to_cart","skuId":"SKU008","quantity":3,"cartTotal":5}',
    storeTime: '2026-07-01T10:05:33.901Z',
    bornHost: '10.0.1.30:55002',
    storeHost: '10.0.2.5:10911',
    properties: {
      KEYS: 'user-10099',
      TAGS: 'cart',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A4667D',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 288,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A4708E',
    topic: 'user-activity-log',
    tag: 'logout',
    key: 'user-10001',
    body: '{"userId":10001,"action":"logout","sessionDuration":3600}',
    storeTime: '2026-07-01T10:15:45.234Z',
    bornHost: '10.0.1.30:55000',
    storeHost: '10.0.2.4:10911',
    properties: {
      KEYS: 'user-10001',
      TAGS: 'logout',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A4708E',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 214,
  },

  // ── notification-push (4 messages) ──
  {
    msgId: 'AC1E0A6400002A9F0000000001A47A9F',
    topic: 'notification-push',
    tag: 'push',
    key: 'notif-001',
    body: '{"notificationId":"N001","userId":10001,"type":"order_status","title":"订单已发货","content":"您的订单 20240101001 已发货，快递单号 SF1234567890","channel":"app"}',
    storeTime: '2026-07-01T13:00:05.567Z',
    bornHost: '10.0.1.40:55100',
    storeHost: '10.0.2.3:10911',
    properties: {
      KEYS: 'notif-001',
      TAGS: 'push',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A47A9F',
      WAIT: 'true',
      DELAY: '3',
      MSG_REGION: 'DefaultRegion',
    },
    size: 15872,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A484B0',
    topic: 'notification-push',
    tag: 'sms',
    key: 'notif-002',
    body: '{"notificationId":"N002","userId":10042,"type":"verification","phone":"138****5678","code":"892341","expireMinutes":5}',
    storeTime: '2026-07-01T13:10:22.890Z',
    bornHost: '10.0.1.41:55101',
    storeHost: '10.0.2.4:10911',
    properties: {
      KEYS: 'notif-002',
      TAGS: 'sms',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A484B0',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 428,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A48EC1',
    topic: 'notification-push',
    tag: 'email',
    key: 'notif-003',
    body: '{"notificationId":"N003","userId":10099,"type":"weekly_report","email":"user@example.com","subject":"本周消费报告","templateId":"TPL_WEEKLY"}',
    storeTime: '2026-07-01T14:00:45.123Z',
    bornHost: '10.0.1.40:55102',
    storeHost: '10.0.2.5:10911',
    properties: {
      KEYS: 'notif-003',
      TAGS: 'email',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A48EC1',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 512,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A498D2',
    topic: 'notification-push',
    tag: 'push',
    key: 'notif-004',
    body: '{"notificationId":"N004","userId":10201,"type":"promotion","title":"限时优惠","content":"您关注的商品 SKU003 降价 200 元，立即查看","channel":"app"}',
    storeTime: '2026-07-01T15:30:18.456Z',
    bornHost: '10.0.1.41:55100',
    storeHost: '10.0.2.3:10911',
    properties: {
      KEYS: 'notif-004',
      TAGS: 'push',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A498D2',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 1048576,
  },

  // ── inventory-sync (4 messages) ──
  {
    msgId: 'AC1E0A6400002A9F0000000001A4A2E3',
    topic: 'inventory-sync',
    tag: 'sync',
    key: 'inv-sku001',
    body: '{"skuId":"SKU001","warehouse":"HZ-01","before":500,"after":498,"change":-2,"reason":"order_deduction","orderId":"20240101001"}',
    storeTime: '2026-07-01T10:24:10.789Z',
    bornHost: '10.0.1.50:55200',
    storeHost: '10.0.2.4:10911',
    properties: {
      KEYS: 'inv-sku001',
      TAGS: 'sync',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A4A2E3',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 448,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A4ACF4',
    topic: 'inventory-sync',
    tag: 'deduct',
    key: 'inv-sku003',
    body: '{"skuId":"SKU003","warehouse":"SH-02","before":120,"after":119,"change":-1,"reason":"order_deduction","orderId":"20240101004"}',
    storeTime: '2026-07-01T11:16:15.012Z',
    bornHost: '10.0.1.51:55201',
    storeHost: '10.0.2.3:10911',
    properties: {
      KEYS: 'inv-sku003',
      TAGS: 'deduct',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A4ACF4',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 436,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A4B705',
    topic: 'inventory-sync',
    tag: 'restock',
    key: 'inv-sku015',
    body: '{"skuId":"SKU015","warehouse":"HZ-01","before":50,"after":200,"change":150,"reason":"supplier_restock","poId":"PO20240101001"}',
    storeTime: '2026-07-01T14:30:05.345Z',
    bornHost: '10.0.1.50:55202',
    storeHost: '10.0.2.5:10911',
    properties: {
      KEYS: 'inv-sku015',
      TAGS: 'restock',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A4B705',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 464,
  },
  {
    msgId: 'AC1E0A6400002A9F0000000001A4C116',
    topic: 'inventory-sync',
    tag: 'alert',
    key: 'inv-sku022',
    body: '{"skuId":"SKU022","warehouse":"BJ-03","currentStock":3,"threshold":10,"alertType":"low_stock","action":"notify_procurement"}',
    storeTime: '2026-07-01T16:00:22.678Z',
    bornHost: '10.0.1.52:55200',
    storeHost: '10.0.2.3:10911',
    properties: {
      KEYS: 'inv-sku022',
      TAGS: 'alert',
      UNIQ_KEY: 'AC1E0A6400002A9F0000000001A4C116',
      WAIT: 'true',
      MSG_REGION: 'DefaultRegion',
    },
    size: 392,
  },
];

export const mockMessageTraces: Record<string, TraceRecord> = {
  // ── order-create traces ──
  AC1E0A6400002A9F0000000001A3F2B1: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T10:23:45.100Z',
        status: 'finish',
        costTime: 3,
        description: 'order-service (10.0.1.12:54321) → broker-hz-01',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T10:23:45.115Z',
        status: 'finish',
        costTime: 8,
        description: 'broker-hz-01 (10.0.2.3:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T10:23:45.230Z',
        status: 'finish',
        costTime: 107,
        description: 'cg-order-processor → 消费成功 (23ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-order-processor',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:23:45.230Z',
        retryCount: 0,
      },
      {
        group: 'cg-notification',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:23:45.450Z',
        retryCount: 0,
      },
      {
        group: 'cg-analytics',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:23:46.100Z',
        retryCount: 0,
      },
    ],
  },
  AC1E0A6400002A9F0000000001A3F7C2: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T10:25:12.400Z',
        status: 'finish',
        costTime: 2,
        description: 'order-service (10.0.1.12:54322) → broker-hz-01',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T10:25:12.430Z',
        status: 'finish',
        costTime: 6,
        description: 'broker-hz-01 (10.0.2.3:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T10:25:12.556Z',
        status: 'finish',
        costTime: 120,
        description: 'cg-order-processor → 消费成功 (18ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-order-processor',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:25:12.556Z',
        retryCount: 0,
      },
      { group: 'cg-notification', deliveryStatus: 'pending', consumeTime: '-', retryCount: 0 },
    ],
  },
  AC1E0A6400002A9F0000000001A401D3: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T10:30:05.700Z',
        status: 'finish',
        costTime: 4,
        description: 'order-service (10.0.1.13:54321) → broker-hz-02',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T10:30:05.750Z',
        status: 'finish',
        costTime: 12,
        description: 'broker-hz-02 (10.0.2.4:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T10:30:06.500Z',
        status: 'finish',
        costTime: 750,
        description: 'cg-order-processor → 首次超时，重试成功 (156ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-order-processor',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:30:06.500Z',
        retryCount: 1,
      },
      {
        group: 'cg-analytics',
        deliveryStatus: 'failed',
        consumeTime: '2026-07-01T10:30:08.000Z',
        retryCount: 3,
      },
    ],
  },
  AC1E0A6400002A9F0000000001A40BE4: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T11:15:33.200Z',
        status: 'finish',
        costTime: 3,
        description: 'order-service (10.0.1.12:54323) → broker-hz-01',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T11:15:33.220Z',
        status: 'finish',
        costTime: 5,
        description: 'broker-hz-01 (10.0.2.3:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T11:15:33.300Z',
        status: 'finish',
        costTime: 78,
        description: 'cg-order-processor → 消费成功 (15ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-order-processor',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T11:15:33.300Z',
        retryCount: 0,
      },
    ],
  },
  AC1E0A6400002A9F0000000001A415F5: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T12:00:18.500Z',
        status: 'finish',
        costTime: 5,
        description: 'order-service (10.0.1.14:54321) → broker-hz-03',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T12:00:18.540Z',
        status: 'finish',
        costTime: 9,
        description: 'broker-hz-03 (10.0.2.5:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T12:00:18.667Z',
        status: 'finish',
        costTime: 122,
        description: 'cg-order-processor → 消费成功 (32ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-order-processor',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T12:00:18.667Z',
        retryCount: 0,
      },
      {
        group: 'cg-notification',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T12:00:19.100Z',
        retryCount: 0,
      },
    ],
  },

  // ── payment-callback traces ──
  AC1E0A6400002A9F0000000001A42006: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T10:24:05.800Z',
        status: 'finish',
        costTime: 4,
        description: 'payment-service (10.0.1.20:54400) → broker-hz-01',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T10:24:05.850Z',
        status: 'finish',
        costTime: 10,
        description: 'broker-hz-01 (10.0.2.3:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T10:24:06.000Z',
        status: 'finish',
        costTime: 145,
        description: 'cg-payment-handler → 消费成功 (45ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-payment-handler',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:24:06.000Z',
        retryCount: 0,
      },
      {
        group: 'cg-order-processor',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:24:06.200Z',
        retryCount: 0,
      },
      { group: 'cg-analytics', deliveryStatus: 'pending', consumeTime: '-', retryCount: 0 },
    ],
  },
  AC1E0A6400002A9F0000000001A42A17: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T10:26:18.100Z',
        status: 'finish',
        costTime: 3,
        description: 'payment-service (10.0.1.20:54401) → broker-hz-02',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T10:26:18.110Z',
        status: 'finish',
        costTime: 7,
        description: 'broker-hz-02 (10.0.2.4:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T10:26:18.200Z',
        status: 'finish',
        costTime: 88,
        description: 'cg-payment-handler → 消费成功 (22ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-payment-handler',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:26:18.200Z',
        retryCount: 0,
      },
      {
        group: 'cg-order-processor',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:26:18.500Z',
        retryCount: 0,
      },
    ],
  },
  AC1E0A6400002A9F0000000001A43428: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T10:32:45.400Z',
        status: 'finish',
        costTime: 5,
        description: 'payment-service (10.0.1.21:54400) → broker-hz-01',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T10:32:45.440Z',
        status: 'finish',
        costTime: 8,
        description: 'broker-hz-01 (10.0.2.3:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T10:32:46.000Z',
        status: 'finish',
        costTime: 560,
        description: 'cg-payment-handler → 消费失败 (余额不足)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-payment-handler',
        deliveryStatus: 'failed',
        consumeTime: '2026-07-01T10:32:46.000Z',
        retryCount: 3,
      },
      { group: 'cg-order-processor', deliveryStatus: 'pending', consumeTime: '-', retryCount: 0 },
    ],
  },
  AC1E0A6400002A9F0000000001A43E39: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T11:16:08.700Z',
        status: 'finish',
        costTime: 3,
        description: 'payment-service (10.0.1.20:54402) → broker-hz-03',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T11:16:08.760Z',
        status: 'finish',
        costTime: 11,
        description: 'broker-hz-03 (10.0.2.5:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T11:16:09.000Z',
        status: 'finish',
        costTime: 235,
        description: 'cg-payment-handler → 消费成功 (68ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-payment-handler',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T11:16:09.000Z',
        retryCount: 0,
      },
    ],
  },
  AC1E0A6400002A9F0000000001A4484A: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T12:05:22.000Z',
        status: 'finish',
        costTime: 4,
        description: 'payment-service (10.0.1.21:54401) → broker-hz-01',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T12:05:22.008Z',
        status: 'finish',
        costTime: 6,
        description: 'broker-hz-01 (10.0.2.3:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T12:05:22.120Z',
        status: 'finish',
        costTime: 110,
        description: 'cg-payment-handler → 消费成功 (退款处理)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-payment-handler',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T12:05:22.120Z',
        retryCount: 0,
      },
      {
        group: 'cg-notification',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T12:05:22.500Z',
        retryCount: 0,
      },
    ],
  },

  // ── user-activity-log traces ──
  AC1E0A6400002A9F0000000001A4525B: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T09:15:01.300Z',
        status: 'finish',
        costTime: 2,
        description: 'user-service (10.0.1.30:55000) → broker-hz-02',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T09:15:01.330Z',
        status: 'finish',
        costTime: 5,
        description: 'broker-hz-02 (10.0.2.4:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T09:15:01.445Z',
        status: 'finish',
        costTime: 112,
        description: 'cg-user-tracking → 消费成功 (8ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-user-tracking',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T09:15:01.445Z',
        retryCount: 0,
      },
      {
        group: 'cg-risk-control',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T09:15:01.600Z',
        retryCount: 0,
      },
    ],
  },
  AC1E0A6400002A9F0000000001A45C6C: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T09:30:12.600Z',
        status: 'finish',
        costTime: 3,
        description: 'user-service (10.0.1.31:55001) → broker-hz-01',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T09:30:12.650Z',
        status: 'finish',
        costTime: 7,
        description: 'broker-hz-01 (10.0.2.3:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T09:30:12.778Z',
        status: 'finish',
        costTime: 125,
        description: 'cg-user-tracking → 消费成功 (12ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-user-tracking',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T09:30:12.778Z',
        retryCount: 0,
      },
    ],
  },
  AC1E0A6400002A9F0000000001A4667D: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T10:05:33.800Z',
        status: 'finish',
        costTime: 4,
        description: 'user-service (10.0.1.30:55002) → broker-hz-03',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T10:05:33.870Z',
        status: 'finish',
        costTime: 9,
        description: 'broker-hz-03 (10.0.2.5:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T10:05:34.000Z',
        status: 'finish',
        costTime: 125,
        description: 'cg-recommend → 首次失败，重试成功 (35ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-recommend',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:05:34.000Z',
        retryCount: 1,
      },
      {
        group: 'cg-user-tracking',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:05:33.980Z',
        retryCount: 0,
      },
    ],
  },
  AC1E0A6400002A9F0000000001A4708E: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T10:15:45.200Z',
        status: 'finish',
        costTime: 2,
        description: 'user-service (10.0.1.30:55000) → broker-hz-02',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T10:15:45.220Z',
        status: 'finish',
        costTime: 4,
        description: 'broker-hz-02 (10.0.2.4:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T10:15:45.334Z',
        status: 'finish',
        costTime: 112,
        description: 'cg-user-tracking → 消费成功 (6ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-user-tracking',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:15:45.334Z',
        retryCount: 0,
      },
    ],
  },

  // ── notification-push traces ──
  AC1E0A6400002A9F0000000001A47A9F: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T13:00:05.500Z',
        status: 'finish',
        costTime: 3,
        description: 'notification-service (10.0.1.40:55100) → broker-hz-01',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T13:00:05.550Z',
        status: 'finish',
        costTime: 8,
        description: 'broker-hz-01 (10.0.2.3:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T13:00:05.800Z',
        status: 'finish',
        costTime: 248,
        description: 'cg-push-service → 推送成功 (56ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-push-service',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T13:00:05.800Z',
        retryCount: 0,
      },
      { group: 'cg-sms-gateway', deliveryStatus: 'pending', consumeTime: '-', retryCount: 0 },
    ],
  },
  AC1E0A6400002A9F0000000001A484B0: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T13:10:22.800Z',
        status: 'finish',
        costTime: 4,
        description: 'notification-service (10.0.1.41:55101) → broker-hz-02',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T13:10:22.860Z',
        status: 'finish',
        costTime: 6,
        description: 'broker-hz-02 (10.0.2.4:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T13:10:23.500Z',
        status: 'process',
        costTime: 640,
        description: 'cg-sms-gateway → 发送中...',
      },
    ],
    consumerStatus: [
      { group: 'cg-sms-gateway', deliveryStatus: 'pending', consumeTime: '-', retryCount: 0 },
    ],
  },
  AC1E0A6400002A9F0000000001A48EC1: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T14:00:45.100Z',
        status: 'finish',
        costTime: 3,
        description: 'notification-service (10.0.1.40:55102) → broker-hz-03',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T14:00:45.110Z',
        status: 'finish',
        costTime: 5,
        description: 'broker-hz-03 (10.0.2.5:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T14:00:45.500Z',
        status: 'finish',
        costTime: 388,
        description: 'cg-email-service → 发送成功 (120ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-email-service',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T14:00:45.500Z',
        retryCount: 0,
      },
    ],
  },
  AC1E0A6400002A9F0000000001A498D2: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T15:30:18.400Z',
        status: 'finish',
        costTime: 5,
        description: 'notification-service (10.0.1.41:55100) → broker-hz-01',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T15:30:18.440Z',
        status: 'finish',
        costTime: 10,
        description: 'broker-hz-01 (10.0.2.3:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T15:30:19.000Z',
        status: 'finish',
        costTime: 558,
        description: 'cg-push-service → 首次超时，重试成功 (89ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-push-service',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T15:30:19.000Z',
        retryCount: 1,
      },
    ],
  },

  // ── inventory-sync traces ──
  AC1E0A6400002A9F0000000001A4A2E3: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T10:24:10.700Z',
        status: 'finish',
        costTime: 3,
        description: 'inventory-service (10.0.1.50:55200) → broker-hz-02',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T10:24:10.770Z',
        status: 'finish',
        costTime: 12,
        description: 'broker-hz-02 (10.0.2.4:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T10:24:10.889Z',
        status: 'finish',
        costTime: 116,
        description: 'cg-inventory-sync → 同步成功 (15ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-inventory-sync',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:24:10.889Z',
        retryCount: 0,
      },
      {
        group: 'cg-warehouse',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T10:24:11.200Z',
        retryCount: 0,
      },
    ],
  },
  AC1E0A6400002A9F0000000001A4ACF4: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T11:16:15.000Z',
        status: 'finish',
        costTime: 4,
        description: 'inventory-service (10.0.1.51:55201) → broker-hz-01',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T11:16:15.008Z',
        status: 'finish',
        costTime: 6,
        description: 'broker-hz-01 (10.0.2.3:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T11:16:15.200Z',
        status: 'finish',
        costTime: 190,
        description: 'cg-warehouse → 同步成功 (28ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-warehouse',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T11:16:15.200Z',
        retryCount: 0,
      },
    ],
  },
  AC1E0A6400002A9F0000000001A4B705: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T14:30:05.300Z',
        status: 'finish',
        costTime: 3,
        description: 'inventory-service (10.0.1.50:55202) → broker-hz-03',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T14:30:05.330Z',
        status: 'finish',
        costTime: 7,
        description: 'broker-hz-03 (10.0.2.5:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T14:30:05.500Z',
        status: 'finish',
        costTime: 168,
        description: 'cg-procurement → 同步成功 (22ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-procurement',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T14:30:05.500Z',
        retryCount: 0,
      },
      {
        group: 'cg-inventory-sync',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T14:30:05.600Z',
        retryCount: 0,
      },
    ],
  },
  AC1E0A6400002A9F0000000001A4C116: {
    nodes: [
      {
        title: 'Producer 发送',
        timestamp: '2026-07-01T16:00:22.600Z',
        status: 'finish',
        costTime: 5,
        description: 'inventory-service (10.0.1.52:55200) → broker-hz-01',
      },
      {
        title: 'Broker 存储',
        timestamp: '2026-07-01T16:00:22.660Z',
        status: 'finish',
        costTime: 14,
        description: 'broker-hz-01 (10.0.2.3:10911) CommitLog 写入成功',
      },
      {
        title: 'Consumer 消费',
        timestamp: '2026-07-01T16:00:23.000Z',
        status: 'finish',
        costTime: 336,
        description: 'cg-alert-service → 告警已触发 (45ms)',
      },
    ],
    consumerStatus: [
      {
        group: 'cg-alert-service',
        deliveryStatus: 'success',
        consumeTime: '2026-07-01T16:00:23.000Z',
        retryCount: 0,
      },
      {
        group: 'cg-inventory-sync',
        deliveryStatus: 'failed',
        consumeTime: '2026-07-01T16:00:25.000Z',
        retryCount: 2,
      },
    ],
  },
};
