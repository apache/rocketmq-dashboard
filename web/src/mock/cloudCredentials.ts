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

import type { CloudCredential } from '../api/cloudCredential';

export const mockCloudCredentials: CloudCredential[] = [
  {
    id: 'cred-aliyun-prod',
    name: '阿里云生产账号',
    vendor: 'ALIYUN',
    accessKey: 'LTAI****0001',
    remark: '用于生产 RocketMQ 实例接入',
    createdAt: '2026-08-01 10:00:00',
    updatedAt: '2026-08-01 10:00:00',
  },
  {
    id: 'cred-aliyun-dr',
    name: '阿里云灾备账号',
    vendor: 'ALIYUN',
    accessKey: 'LTAI****0002',
    remark: '用于灾备环境接入',
    createdAt: '2026-08-02 11:00:00',
    updatedAt: '2026-08-02 11:00:00',
  },
];
