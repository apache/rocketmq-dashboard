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

import { describe, expect, it } from 'vitest';
import type { ClusterInfo } from '../../api/cluster';
import { countClusterComponents } from './clusterStats';

const baseCluster: ClusterInfo = {
  id: 'c1',
  name: 'rmq-prod',
  nsClusterName: 'ns-prod',
  type: 'V5_PROXY_CLUSTER',
  endpoint: '10.0.0.1:9876',
  status: 'healthy',
  version: '5.2.0',
  brokers: [],
  proxies: [],
  nameServers: [],
  config: {
    flushDiskType: 'SYNC_FLUSH',
    autoCreateTopicEnable: false,
    autoCreateSubscriptionGroup: false,
    maxMessageSize: 4194304,
    msgTraceTopicName: 'RMQ_SYS_TRACE_TOPIC',
    fileReservedTime: 72,
    writeQueueNums: 16,
    readQueueNums: 16,
    brokerPermission: 6,
    deleteWhen: '04',
  },
  topicCount: 10,
  groupCount: 5,
  tpsHistory: [1, 2, 3],
};

describe('countClusterComponents', () => {
  it('returns zero counts for an empty cluster list', () => {
    expect(countClusterComponents([])).toEqual({ brokers: 0, nameServers: 0, proxies: 0 });
  });

  it('counts components across clusters', () => {
    const clusters: ClusterInfo[] = [
      {
        ...baseCluster,
        brokers: [
          {
            addr: 'a:10911',
            name: 'b1',
            status: 'running',
            tpsIn: 1,
            tpsOut: 2,
            diskUsage: 3,
            version: '5.2.0',
          },
        ],
        proxies: [{ addr: 'p:8081' } as ClusterInfo['proxies'][number]],
        nameServers: [{ addr: 'ns:9876' } as ClusterInfo['nameServers'][number]],
      },
      {
        ...baseCluster,
        id: 'c2',
        brokers: [
          {
            addr: 'a2:10911',
            name: 'b2',
            status: 'running',
            tpsIn: 1,
            tpsOut: 2,
            diskUsage: 3,
            version: '5.2.0',
          },
          {
            addr: 'a3:10911',
            name: 'b3',
            status: 'running',
            tpsIn: 1,
            tpsOut: 2,
            diskUsage: 3,
            version: '5.2.0',
          },
        ],
      },
    ];
    expect(countClusterComponents(clusters)).toEqual({ brokers: 3, nameServers: 1, proxies: 1 });
  });

  it('treats missing component lists as empty instead of crashing', () => {
    // A cluster without a proxy serializes its component lists as null.
    const clusters = [
      { ...baseCluster, proxies: null as unknown as ClusterInfo['proxies'] },
      { ...baseCluster, id: 'c2', brokers: null as unknown as ClusterInfo['brokers'] },
      { ...baseCluster, id: 'c3', nameServers: undefined as unknown as ClusterInfo['nameServers'] },
    ];
    expect(countClusterComponents(clusters)).toEqual({ brokers: 0, nameServers: 0, proxies: 0 });
  });
});
