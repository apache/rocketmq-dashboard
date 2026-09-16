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
import type { ClusterInfo, NameserverRegistryEntry } from '../api/cluster';
import {
  normalizeNameserverAddresses,
  reconcileNameserverRegistry,
} from './nameserverRegistryReconciliation';

const cluster = (overrides: Partial<ClusterInfo> = {}): ClusterInfo => ({
  id: 'cluster-a',
  name: 'orders',
  nsClusterName: 'orders-ns',
  type: 'rocketmq',
  endpoint: 'ns-a.example.test:9876',
  status: 'RUNNING',
  version: '5.3.0',
  brokers: [],
  proxies: [],
  nameServers: [{ addr: 'ns-a.example.test:9876', status: 'RUNNING' }],
  config: {
    flushDiskType: 'ASYNC_FLUSH',
    autoCreateTopicEnable: false,
    autoCreateSubscriptionGroup: false,
    maxMessageSize: 4194304,
    msgTraceTopicName: 'RMQ_SYS_TRACE_TOPIC',
    fileReservedTime: 72,
    writeQueueNums: 8,
    readQueueNums: 8,
    brokerPermission: 6,
    deleteWhen: '04',
  },
  topicCount: 10,
  groupCount: 4,
  tpsHistory: [],
  ...overrides,
});

const registry = (
  id: number,
  name: string,
  namesrvAddr: string,
  overrides: Partial<NameserverRegistryEntry> = {},
): NameserverRegistryEntry => ({
  id,
  name,
  namesrvAddr,
  k8sNamespace: null,
  k8sId: null,
  status: 'healthy',
  description: null,
  gmtCreate: null,
  gmtModified: null,
  ...overrides,
});

describe('normalizeNameserverAddresses', () => {
  it('accepts semicolon, comma, whitespace, and newline-separated addresses', () => {
    expect(
      normalizeNameserverAddresses(
        'ns-b.example.test:9876; ns-a.example.test:9876,\nns-c.example.test:9876',
      ),
    ).toEqual(['ns-a.example.test:9876', 'ns-b.example.test:9876', 'ns-c.example.test:9876']);
  });

  it('removes HTTP protocol prefixes and trailing slashes', () => {
    expect(
      normalizeNameserverAddresses(
        'http://ns-a.example.test:9876/ https://ns-b.example.test:9876///',
      ),
    ).toEqual(['ns-a.example.test:9876', 'ns-b.example.test:9876']);
  });

  it('deduplicates and sorts addresses for deterministic comparisons', () => {
    expect(
      normalizeNameserverAddresses(
        'ns-b.example.test:9876;ns-a.example.test:9876;ns-b.example.test:9876',
      ),
    ).toEqual(['ns-a.example.test:9876', 'ns-b.example.test:9876']);
  });

  it('returns an empty list for blank or absent input', () => {
    expect(normalizeNameserverAddresses()).toEqual([]);
    expect(normalizeNameserverAddresses('   ')).toEqual([]);
    expect(normalizeNameserverAddresses(null)).toEqual([]);
  });
});

describe('reconcileNameserverRegistry', () => {
  it('matches a registry entry and discovered cluster by address', () => {
    const report = reconcileNameserverRegistry(
      [registry(1, 'friendly-name', 'ns-a.example.test:9876')],
      [cluster()],
    );

    expect(report.rows).toHaveLength(1);
    expect(report.rows[0]).toMatchObject({
      status: 'MATCHED',
      registryName: 'friendly-name',
      clusterId: 'cluster-a',
      clusterName: 'orders',
      missingAddresses: [],
      unexpectedAddresses: [],
    });
    expect(report.summary).toMatchObject({ matched: 1, attentionRequired: 0 });
  });

  it('matches names case-insensitively when an endpoint is temporarily unavailable', () => {
    const report = reconcileNameserverRegistry(
      [registry(1, 'ORDERS-NS', '')],
      [cluster({ endpoint: '', nameServers: [] })],
    );

    expect(report.rows[0]).toMatchObject({ status: 'MATCHED', clusterId: 'cluster-a' });
  });

  it('matches a registry entry against a cluster id', () => {
    const report = reconcileNameserverRegistry(
      [registry(1, 'CLUSTER-A', 'ns-a.example.test:9876')],
      [cluster()],
    );

    expect(report.rows[0].status).toBe('MATCHED');
  });

  it('reports configured and discovered address differences in both directions', () => {
    const report = reconcileNameserverRegistry(
      [registry(1, 'orders', 'ns-a.example.test:9876;ns-removed.example.test:9876')],
      [
        cluster({
          nameServers: [
            { addr: 'ns-a.example.test:9876', status: 'RUNNING' },
            { addr: 'ns-new.example.test:9876', status: 'RUNNING' },
          ],
        }),
      ],
    );

    expect(report.rows[0]).toMatchObject({
      status: 'ADDRESS_MISMATCH',
      missingAddresses: ['ns-removed.example.test:9876'],
      unexpectedAddresses: ['ns-new.example.test:9876'],
    });
    expect(report.summary.addressMismatches).toBe(1);
    expect(report.summary.attentionRequired).toBe(1);
  });

  it('does not duplicate an endpoint already present in the NameServer node list', () => {
    const report = reconcileNameserverRegistry(
      [registry(1, 'orders', 'ns-a.example.test:9876')],
      [cluster()],
    );

    expect(report.rows[0].discoveredAddresses).toEqual(['ns-a.example.test:9876']);
  });

  it('reports registry-only entries that cannot be associated with discovery', () => {
    const report = reconcileNameserverRegistry(
      [registry(9, 'retired', 'retired.example.test:9876')],
      [cluster()],
    );

    expect(report.rows.find((row) => row.registryId === 9)).toMatchObject({
      status: 'REGISTRY_ONLY',
      clusterId: null,
      missingAddresses: ['retired.example.test:9876'],
    });
    expect(report.summary.registryOnly).toBe(1);
  });

  it('reports discovered clusters that have no registry entry', () => {
    const report = reconcileNameserverRegistry([], [cluster()]);

    expect(report.rows).toEqual([
      expect.objectContaining({
        key: 'cluster-cluster-a',
        status: 'DISCOVERED_ONLY',
        registryId: null,
        clusterId: 'cluster-a',
      }),
    ]);
    expect(report.summary).toMatchObject({ discoveredOnly: 1, attentionRequired: 1 });
  });

  it('reports ambiguous entries rather than choosing a cluster arbitrarily', () => {
    const clusters = [
      cluster({ id: 'cluster-a', name: 'orders-a' }),
      cluster({ id: 'cluster-b', name: 'orders-b' }),
    ];
    const report = reconcileNameserverRegistry(
      [registry(1, 'shared', 'ns-a.example.test:9876')],
      clusters,
    );

    expect(report.rows.find((row) => row.registryId === 1)).toMatchObject({
      status: 'AMBIGUOUS',
      candidateClusters: ['orders-a', 'orders-b'],
    });
    expect(report.summary.ambiguous).toBe(1);
    expect(report.summary.discoveredOnly).toBe(2);
  });

  it('marks every registry entry that maps to the same discovered cluster', () => {
    const report = reconcileNameserverRegistry(
      [
        registry(1, 'orders', 'ns-a.example.test:9876'),
        registry(2, 'orders-ns', 'ns-a.example.test:9876'),
      ],
      [cluster()],
    );

    expect(report.rows.map((row) => row.status)).toEqual([
      'DUPLICATE_MAPPING',
      'DUPLICATE_MAPPING',
    ]);
    expect(report.summary).toMatchObject({ duplicateMappings: 2, discoveredOnly: 0 });
  });

  it('keeps different clusters independent in one fleet report', () => {
    const clusterB = cluster({
      id: 'cluster-b',
      name: 'payments',
      nsClusterName: 'payments-ns',
      endpoint: 'ns-b.example.test:9876',
      nameServers: [{ addr: 'ns-b.example.test:9876', status: 'RUNNING' }],
    });
    const report = reconcileNameserverRegistry(
      [
        registry(1, 'orders', 'ns-a.example.test:9876'),
        registry(2, 'payments', 'ns-b.example.test:9876'),
      ],
      [cluster(), clusterB],
    );

    expect(report.rows.map((row) => row.status)).toEqual(['MATCHED', 'MATCHED']);
    expect(report.summary).toEqual({
      registryEntries: 2,
      discoveredClusters: 2,
      matched: 2,
      addressMismatches: 0,
      registryOnly: 0,
      discoveredOnly: 0,
      ambiguous: 0,
      duplicateMappings: 0,
      attentionRequired: 0,
    });
  });

  it('does not mutate registry entries, clusters, or their address arrays', () => {
    const entries = [registry(1, 'orders', 'ns-a.example.test:9876')];
    const clusters = [cluster()];
    const entrySnapshot = structuredClone(entries);
    const clusterSnapshot = structuredClone(clusters);

    reconcileNameserverRegistry(entries, clusters);

    expect(entries).toEqual(entrySnapshot);
    expect(clusters).toEqual(clusterSnapshot);
  });

  it('returns an empty healthy report when neither source has records', () => {
    const report = reconcileNameserverRegistry([], []);

    expect(report.rows).toEqual([]);
    expect(report.summary.attentionRequired).toBe(0);
    expect(report.summary.registryEntries).toBe(0);
    expect(report.summary.discoveredClusters).toBe(0);
  });
});
