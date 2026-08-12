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

export const mockAlertRules = [
  {
    id: 1,
    name: 'Broker 磁盘使用率过高',
    metric: 'rocketmq_disk_use_ratio',
    operator: '>' as const,
    threshold: 85,
    thresholdUnit: '%',
    duration: '5m',
    channels: ['dingtalk', 'email'] as ('dingtalk' | 'email' | 'sms')[],
    enabled: true,
    lastTriggered: '2026-07-02 18:32:11',
    description: '当 Broker 磁盘使用率超过 85% 时触发告警',
  },
  {
    id: 2,
    name: '消费堆积量异常',
    metric: 'rocketmq_consumer_lag_messages',
    operator: '>' as const,
    threshold: 10000,
    thresholdUnit: '条',
    duration: '15m',
    channels: ['dingtalk', 'sms'] as ('dingtalk' | 'email' | 'sms')[],
    enabled: true,
    lastTriggered: '2026-07-02 14:05:23',
    description: '消费组堆积消息数超过 10000 条时触发',
  },
  {
    id: 3,
    name: 'TPS 突降告警',
    metric: 'rocketmq_broker_tps',
    operator: '<' as const,
    threshold: 500,
    thresholdUnit: 'TPS',
    duration: '5m',
    channels: ['dingtalk', 'email', 'sms'] as ('dingtalk' | 'email' | 'sms')[],
    enabled: true,
    lastTriggered: '2026-07-01 22:17:45',
    description: 'TPS 低于 500 时触发，可能表示生产端异常',
  },
  {
    id: 4,
    name: 'Broker 离线检测',
    metric: 'up',
    operator: '>=' as const,
    threshold: 1,
    thresholdUnit: '个',
    duration: '1m',
    channels: ['dingtalk', 'sms'] as ('dingtalk' | 'email' | 'sms')[],
    enabled: true,
    lastTriggered: '2026-06-30 09:12:08',
    description: '检测到任意 Broker 节点离线时立即触发',
  },
  {
    id: 5,
    name: 'Proxy 连接数过高',
    metric: 'rocketmq_connection_count',
    operator: '>' as const,
    threshold: 5000,
    thresholdUnit: '个',
    duration: '5m',
    channels: ['dingtalk', 'email'] as ('dingtalk' | 'email' | 'sms')[],
    enabled: false,
    lastTriggered: '2026-06-28 11:45:33',
    description: 'Proxy 连接数超过 5000 时触发告警',
  },
  {
    id: 6,
    name: '磁盘使用率预警',
    metric: 'rocketmq_disk_use_ratio',
    operator: '>=' as const,
    threshold: 70,
    thresholdUnit: '%',
    duration: '30m',
    channels: ['email'] as ('dingtalk' | 'email' | 'sms')[],
    enabled: true,
    lastTriggered: null,
    description: '磁盘使用率超过 70% 的预警级别',
  },
  {
    id: 7,
    name: '消费堆积轻微告警',
    metric: 'rocketmq_consumer_lag_messages',
    operator: '>' as const,
    threshold: 5000,
    thresholdUnit: '条',
    duration: '30m',
    channels: ['dingtalk'] as ('dingtalk' | 'email' | 'sms')[],
    enabled: true,
    lastTriggered: '2026-07-03 08:21:17',
    description: '消费堆积量超过 5000 条时的轻微告警',
  },
  {
    id: 8,
    name: 'TPS 突增告警',
    metric: 'rocketmq_broker_tps',
    operator: '>' as const,
    threshold: 50000,
    thresholdUnit: 'TPS',
    duration: '1m',
    channels: ['dingtalk', 'email'] as ('dingtalk' | 'email' | 'sms')[],
    enabled: false,
    lastTriggered: '2026-06-25 16:08:52',
    description: 'TPS 超过 50000 时触发，防止流量突增压垮集群',
  },
];

export type AlertRule = (typeof mockAlertRules)[number];
