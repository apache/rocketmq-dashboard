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

import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ClusterInfo } from '../api/cluster';
import { listClusters } from '../services/clusterService';
import useClusterStore from './clusterStore';

vi.mock('../services/clusterService', () => ({
  listClusters: vi.fn(),
}));

const cluster: ClusterInfo = {
  id: 'cluster-prod',
  name: 'rocketmq-prod',
  nsClusterName: 'ns-prod',
  type: 'V5_PROXY_CLUSTER',
  endpoint: '10.101.2.1:9876',
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
    msgTraceTopicName: 'RMQ_SYS_TRACE_TOPIC4',
    fileReservedTime: 72,
    writeQueueNums: 16,
    readQueueNums: 16,
    brokerPermission: 6,
    deleteWhen: '04',
  },
  topicCount: 256,
  groupCount: 128,
  tpsHistory: [100, 120],
};

const newerCluster: ClusterInfo = {
  ...cluster,
  id: 'cluster-staging',
  name: 'rocketmq-staging',
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });

  return { promise, resolve, reject };
}

describe('clusterStore', () => {
  afterEach(() => {
    vi.mocked(listClusters).mockReset();
    useClusterStore.setState({ clusters: [], loading: false });
  });

  it('loads clusters from the cluster service', async () => {
    vi.mocked(listClusters).mockResolvedValue([cluster]);

    await useClusterStore.getState().fetchClusters();

    expect(listClusters).toHaveBeenCalledTimes(1);
    expect(useClusterStore.getState()).toMatchObject({
      clusters: [cluster],
      loading: false,
    });
  });

  it('keeps the newest cluster list when overlapping loads finish out of order', async () => {
    const firstLoad = deferred<ClusterInfo[]>();
    const secondLoad = deferred<ClusterInfo[]>();
    vi.mocked(listClusters)
      .mockReturnValueOnce(firstLoad.promise)
      .mockReturnValueOnce(secondLoad.promise);

    const firstFetch = useClusterStore.getState().fetchClusters();
    const secondFetch = useClusterStore.getState().fetchClusters();

    secondLoad.resolve([newerCluster]);
    await secondFetch;

    expect(useClusterStore.getState()).toMatchObject({
      clusters: [newerCluster],
      loading: false,
    });

    firstLoad.resolve([cluster]);
    await firstFetch;

    expect(useClusterStore.getState()).toMatchObject({
      clusters: [newerCluster],
      loading: false,
    });
  });

  it('resets loading when loading clusters fails', async () => {
    const error = new Error('failed to load clusters');
    vi.mocked(listClusters).mockRejectedValue(error);

    await expect(useClusterStore.getState().fetchClusters()).rejects.toThrow(error);

    expect(useClusterStore.getState()).toMatchObject({
      clusters: [],
      loading: false,
    });
  });
});
