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
    id: 'instance-direct-1',
    name: 'instance-direct-1',
    remark: '直连实例 1，交易核心链路（NameServer 直连）',
    type: 'DIRECT',
    endpoint: '10.0.1.11:9876',
    topicCount: 4,
    consumerGroupCount: 4,
    createdAt: '2026-08-03 10:00:00',
    updatedAt: '2026-08-03 10:00:00',
  },
  {
    id: 'instance-direct-2',
    name: 'instance-direct-2',
    remark: '直连实例 2，风控与审计链路（NameServer 直连）',
    type: 'DIRECT',
    endpoint: '10.0.1.12:9876',
    topicCount: 4,
    consumerGroupCount: 4,
    createdAt: '2026-08-03 10:00:00',
    updatedAt: '2026-08-03 10:00:00',
  },
  {
    id: 'instance-proxy-1',
    name: 'instance-proxy-1',
    remark: 'Proxy 实例 1，电商交易主链路',
    type: 'PROXY',
    endpoint: '10.0.2.21:8080',
    topicCount: 8,
    consumerGroupCount: 6,
    createdAt: '2026-08-03 10:00:00',
    updatedAt: '2026-08-03 10:00:00',
  },
  {
    id: 'instance-proxy-2',
    name: 'instance-proxy-2',
    remark: 'Proxy 实例 2，营销与会员链路',
    type: 'PROXY',
    endpoint: '10.0.2.22:8080',
    topicCount: 6,
    consumerGroupCount: 5,
    createdAt: '2026-08-03 10:00:00',
    updatedAt: '2026-08-03 10:00:00',
  },
  {
    id: 'instance-proxy-3',
    name: 'instance-proxy-3',
    remark: 'Proxy 实例 3，物流与大数据链路',
    type: 'PROXY',
    endpoint: '10.0.2.23:8080',
    topicCount: 6,
    consumerGroupCount: 6,
    createdAt: '2026-08-03 10:00:00',
    updatedAt: '2026-08-03 10:00:00',
  },
];
