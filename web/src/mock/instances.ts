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

import type { Instance } from '../api/instance';

export const mockInstances: Instance[] = [
  {
    id: '1',
    name: 'rocketmq-trade',
    remark: '核心交易链路，承载订单、支付等主要业务',
    type: 'PROXY',
    endpoint: 'proxy-hz.rocketmq.internal:8080',
    topicCount: 128,
    consumerGroupCount: 56,
    createdAt: '2024-03-15 08:30:00',
    updatedAt: '2026-06-20 14:15:00',
  },
  {
    id: '2',
    name: 'rocketmq-dr',
    remark: '灾备集群，与 trade 集群互为双活',
    type: 'PROXY',
    endpoint: 'proxy-sh.rocketmq.internal:8080',
    topicCount: 96,
    consumerGroupCount: 42,
    createdAt: '2024-05-10 10:00:00',
    updatedAt: '2026-06-18 09:30:00',
  },
  {
    id: '3',
    name: 'rocketmq-debug',
    remark: '开发测试环境，仅供内部调试使用',
    type: 'PROXY',
    endpoint: 'localhost:8081',
    topicCount: 15,
    consumerGroupCount: 8,
    createdAt: '2025-01-20 14:00:00',
    updatedAt: '2026-07-01 11:45:00',
  },
  {
    id: '4',
    name: 'rocketmq-legacy',
    remark: '旧版集群，计划 Q3 完成迁移后下线',
    type: 'DIRECT',
    endpoint: 'namesrv-legacy:9876',
    topicCount: 64,
    consumerGroupCount: 30,
    createdAt: '2022-08-01 09:00:00',
    updatedAt: '2025-12-10 16:20:00',
  },
  {
    id: '5',
    name: 'rocketmq-staging',
    remark: '预发布验证环境，与生产配置一致',
    type: 'PROXY',
    endpoint: 'proxy-staging:8080',
    topicCount: 32,
    consumerGroupCount: 18,
    createdAt: '2024-11-05 11:30:00',
    updatedAt: '2026-06-25 08:00:00',
  },
];
