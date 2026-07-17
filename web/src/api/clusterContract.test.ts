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

import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import { getCluster, listClusters } from './cluster';
import type { ClusterInfo } from './cluster';

const mock = new MockAdapter(client);
const cluster: ClusterInfo = {
  id: 'cluster-a',
  name: 'cluster-a',
  nsClusterName: 'production',
  type: 'V5_PROXY_CLUSTER',
  endpoint: '127.0.0.1:9876',
  status: 'RUNNING',
  version: '5.3.0',
  brokers: [{ name: 'broker-a', addr: '127.0.0.1:10911', status: 'RUNNING', tpsIn: 1, tpsOut: 2, diskUsage: 10, version: '5.3.0' }],
  proxies: [{ addr: '127.0.0.1:8080', status: 'RUNNING', connections: 3, grpcPort: 8081, remotingPort: 8080 }],
  nameServers: [{ addr: '127.0.0.1:9876', status: 'RUNNING' }],
  config: { flushDiskType: 'ASYNC_FLUSH', autoCreateTopicEnable: true, autoCreateSubscriptionGroup: true, maxMessageSize: 4194304, msgTraceTopicName: 'TRACE_TOPIC', fileReservedTime: 72, writeQueueNums: 8, readQueueNums: 8, brokerPermission: 6, deleteWhen: '04' },
  topicCount: 10,
  groupCount: 4,
  tpsHistory: [1, 2],
};

describe('cluster API contract', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('unwraps the backend ClusterVO shape for list and detail endpoints', async () => {
    mock.onGet('/clusters').reply(200, { code: 200, data: [cluster] });
    mock.onGet('/clusters/cluster-a').reply(200, { code: 200, data: cluster });

    await expect(listClusters()).resolves.toEqual([cluster]);
    await expect(getCluster(cluster.id)).resolves.toEqual(cluster);
  });
});
