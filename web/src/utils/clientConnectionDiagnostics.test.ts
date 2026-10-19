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
import type { ClientConnection } from '../api/connections';
import { analyzeClientConnections } from './clientConnectionDiagnostics';

const connection = (overrides: Partial<ClientConnection>): ClientConnection => ({
  clientId: 'order-svc-0@10.0.1.10:49152',
  type: 'Producer',
  groupOrTopic: 'order-events',
  protocol: 'gRPC',
  address: '10.0.1.10:49152',
  language: 'Java',
  version: '5.0.7',
  connectedAt: '2026-07-01 08:30:00',
  clusterName: 'ns-prod',
  ...overrides,
});

describe('client connection diagnostics', () => {
  it('marks a consistent multi-client inventory as healthy', () => {
    const diagnostics = analyzeClientConnections([
      connection({ clientId: 'producer-a', address: '10.0.1.10:49152' }),
      connection({ clientId: 'producer-b', address: '10.0.1.11:49152' }),
      connection({
        clientId: 'consumer-a',
        type: 'Consumer',
        groupOrTopic: 'cg-order',
        address: '10.0.2.10:49152',
      }),
      connection({
        clientId: 'consumer-b',
        type: 'Consumer',
        groupOrTopic: 'cg-order',
        address: '10.0.2.11:49152',
      }),
    ]);

    expect(diagnostics.status).toBe('healthy');
    expect(diagnostics.score).toBe(100);
    expect(diagnostics.summary).toMatchObject({
      totalConnections: 4,
      uniqueClientCount: 4,
      uniqueAddressCount: 4,
      resourceCount: 2,
      partialConnectionCount: 0,
      mixedProtocolResourceCount: 0,
      mixedVersionResourceCount: 0,
      singleConsumerGroupCount: 0,
    });
    expect(diagnostics.issues).toEqual([]);
    expect(diagnostics.recommendations).toEqual([
      '保持客户端连接清单按集群定期巡检，重点关注协议和 SDK 版本收敛。',
    ]);
  });

  it('reports an empty client inventory as critical', () => {
    const diagnostics = analyzeClientConnections([]);

    expect(diagnostics.status).toBe('critical');
    expect(diagnostics.summary.totalConnections).toBe(0);
    expect(diagnostics.resources).toEqual([]);
    expect(diagnostics.issues).toEqual([
      expect.objectContaining({
        code: 'NO_CONNECTIONS',
        severity: 'critical',
      }),
    ]);
  });

  it('detects partial scans, client ID collisions, and duplicated records', () => {
    const diagnostics = analyzeClientConnections([
      connection({
        clientId: 'shared-client',
        groupOrTopic: 'order-events',
        address: '10.0.1.10:49152',
        partial: true,
      }),
      connection({
        clientId: 'shared-client',
        groupOrTopic: 'order-events',
        address: '10.0.1.11:49152',
      }),
      connection({
        clientId: 'dup-client',
        groupOrTopic: 'payment-events',
        address: '10.0.1.12:49152',
      }),
      connection({
        clientId: 'dup-client',
        groupOrTopic: 'payment-events',
        address: '10.0.1.12:49152',
      }),
    ]);

    expect(diagnostics.status).toBe('critical');
    expect(diagnostics.summary.partialConnectionCount).toBe(1);
    expect(diagnostics.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'PARTIAL_CONNECTION_SCAN',
          severity: 'warning',
          evidence: ['partial=1'],
        }),
        expect.objectContaining({
          code: 'CLIENT_ID_COLLISION',
          severity: 'critical',
          clientId: 'shared-client',
        }),
        expect.objectContaining({
          code: 'EXACT_DUPLICATE_CONNECTION',
          severity: 'info',
          clientId: 'dup-client',
        }),
      ]),
    );
  });

  it('keeps distinct identities containing pipe characters separate', () => {
    const diagnostics = analyzeClientConnections([
      connection({ clientId: 'client|partition', groupOrTopic: 'orders' }),
      connection({ clientId: 'client', groupOrTopic: 'partition|orders' }),
    ]);

    expect(diagnostics.issues.filter((item) => item.code === 'EXACT_DUPLICATE_CONNECTION')).toEqual(
      [],
    );
  });

  it('preserves complete identity fields in duplicate evidence', () => {
    const original = connection({ clientId: 'client|partition', groupOrTopic: 'orders|events' });
    const diagnostics = analyzeClientConnections([original, { ...original }]);
    const duplicates = diagnostics.issues.filter(
      (item) => item.code === 'EXACT_DUPLICATE_CONNECTION',
    );

    expect(duplicates).toHaveLength(1);
    expect(duplicates[0]).toMatchObject({
      clientId: 'client|partition',
      resource: 'orders|events',
      evidence: ['address=10.0.1.10:49152', 'count=2'],
    });
  });

  it('flags mixed protocols, mixed versions, and single consumer instances per resource', () => {
    const diagnostics = analyzeClientConnections([
      connection({
        type: 'Consumer',
        clientId: 'consumer-a',
        groupOrTopic: 'cg-order',
        protocol: 'gRPC',
        language: 'Java',
        version: '5.0.7',
      }),
      connection({
        type: 'Consumer',
        clientId: 'consumer-b',
        groupOrTopic: 'cg-order',
        protocol: 'Remoting',
        language: 'Java',
        version: '4.9.8',
        address: '10.0.1.11:49152',
      }),
      connection({
        type: 'Consumer',
        clientId: 'consumer-single',
        groupOrTopic: 'cg-payment',
        address: '10.0.2.10:49152',
      }),
    ]);

    expect(diagnostics.status).toBe('warning');
    expect(diagnostics.summary).toMatchObject({
      mixedProtocolResourceCount: 1,
      mixedVersionResourceCount: 1,
      singleConsumerGroupCount: 1,
    });
    expect(diagnostics.resources[0]).toMatchObject({
      type: 'Consumer',
      resource: 'cg-order',
      status: 'warning',
      issueCount: 2,
    });
    expect(diagnostics.issues.map((item) => item.code)).toEqual(
      expect.arrayContaining([
        'MIXED_PROTOCOL_RESOURCE',
        'MIXED_VERSION_RESOURCE',
        'SINGLE_CONSUMER_INSTANCE',
      ]),
    );
  });

  it('does not report proxy rows for the protocol and version the Proxy response cannot carry', () => {
    // Proxy-derived rows have no protocol/version by design (the server comments "The Proxy
    // response has no protocol"), and the clients page even gives them a dedicated
    // unknown-protocol filter sentinel. Scoring them as anomalies cost 18 points per row:
    // three gRPC consumers behind a proxy flipped the panel to "high risk" while nothing
    // was wrong.
    const proxyRow = (clientId: string, address: string): ClientConnection =>
      connection({
        clientId,
        address,
        type: 'Consumer',
        groupOrTopic: 'cg-order',
        protocol: null,
        version: null,
      });

    const diagnostics = analyzeClientConnections([
      proxyRow('consumer-a', '10.0.2.10:49152'),
      proxyRow('consumer-b', '10.0.2.11:49152'),
      proxyRow('consumer-c', '10.0.2.12:49152'),
    ]);

    expect(diagnostics.issues.map((item) => item.code)).not.toContain('UNKNOWN_PROTOCOL');
    expect(diagnostics.issues.map((item) => item.code)).not.toContain('UNKNOWN_VERSION');
    expect(diagnostics.status).toBe('healthy');
    expect(diagnostics.score).toBe(100);
  });

  it('detects unknown fields, invalid timestamps, and address concentration', () => {
    const diagnostics = analyzeClientConnections([
      connection({
        clientId: 'client-a',
        protocol: 'Custom',
        language: 'Ruby',
        version: '-',
        connectedAt: 'not-a-date',
      }),
      connection({ clientId: 'client-b' }),
      connection({ clientId: 'client-c' }),
      connection({ clientId: 'client-d' }),
    ]);

    expect(diagnostics.status).toBe('critical');
    expect(diagnostics.summary.concentratedAddressCount).toBe(1);
    expect(diagnostics.issues.map((item) => item.code)).toEqual(
      expect.arrayContaining([
        'UNKNOWN_PROTOCOL',
        'UNKNOWN_LANGUAGE',
        'UNKNOWN_VERSION',
        'INVALID_CONNECTION_TIME',
        'ADDRESS_CONCENTRATION',
      ]),
    );
  });
});

describe('client connection diagnostics across physical clusters', () => {
  it('keeps same-named resources from independent clusters scoped per cluster', () => {
    const diagnostics = analyzeClientConnections([
      connection({ clusterName: 'cluster-a', clientId: 'producer-a', version: '5.0.7' }),
      connection({
        clusterName: 'cluster-b',
        clientId: 'producer-b',
        version: '4.9.8',
        address: '10.0.2.10:49152',
      }),
    ]);

    expect(diagnostics.summary.resourceCount).toBe(2);
    expect(diagnostics.summary.mixedVersionResourceCount).toBe(0);
    expect(diagnostics.resources).toEqual([
      expect.objectContaining({ cluster: 'cluster-a', resource: 'order-events', status: 'healthy' }),
      expect.objectContaining({ cluster: 'cluster-b', resource: 'order-events', status: 'healthy' }),
    ]);
    expect(diagnostics.issues).toEqual([]);
  });

  it('does not flag a client ID shared across clusters as a collision', () => {
    const diagnostics = analyzeClientConnections([
      connection({ clusterName: 'cluster-a', clientId: 'shared-client' }),
      connection({
        clusterName: 'cluster-b',
        clientId: 'shared-client',
        address: '10.0.2.10:49152',
      }),
    ]);

    expect(diagnostics.issues.filter((item) => item.code === 'CLIENT_ID_COLLISION')).toEqual([]);
  });

  it('does not flag identical connection records from different clusters as duplicates', () => {
    const diagnostics = analyzeClientConnections([
      connection({ clusterName: 'cluster-a', clientId: 'same-client' }),
      connection({ clusterName: 'cluster-b', clientId: 'same-client' }),
    ]);

    expect(
      diagnostics.issues.filter((item) => item.code === 'EXACT_DUPLICATE_CONNECTION'),
    ).toEqual([]);
  });

  it('counts distinct clients and addresses within their cluster scope', () => {
    const diagnostics = analyzeClientConnections([
      connection({ clusterName: 'cluster-a', clientId: 'same-client' }),
      connection({
        clusterName: 'cluster-b',
        clientId: 'same-client',
        address: '10.0.2.10:49152',
      }),
    ]);

    expect(diagnostics.summary.uniqueClientCount).toBe(2);
    expect(diagnostics.summary.uniqueAddressCount).toBe(2);
  });

  it('bases address concentration on each cluster scope instead of pooled inventories', () => {
    // All five rows share one address string; pooled they trip the concentration
    // threshold, but no single physical cluster holds enough rows to judge it.
    const diagnostics = analyzeClientConnections([
      connection({ clusterName: 'cluster-a', clientId: 'a-1' }),
      connection({ clusterName: 'cluster-a', clientId: 'a-2' }),
      connection({ clusterName: 'cluster-b', clientId: 'b-1' }),
      connection({ clusterName: 'cluster-b', clientId: 'b-2' }),
      connection({ clusterName: 'cluster-b', clientId: 'b-3' }),
    ]);

    expect(diagnostics.summary.concentratedAddressCount).toBe(0);
  });

  it('still flags concentration inside one cluster and identifies the cluster', () => {
    const diagnostics = analyzeClientConnections([
      connection({ clusterName: 'cluster-a', clientId: 'a-1' }),
      connection({ clusterName: 'cluster-a', clientId: 'a-2' }),
      connection({ clusterName: 'cluster-a', clientId: 'a-3' }),
      connection({ clusterName: 'cluster-a', clientId: 'a-4' }),
    ]);

    expect(diagnostics.summary.concentratedAddressCount).toBe(1);
    expect(diagnostics.issues.find((item) => item.code === 'ADDRESS_CONCENTRATION')).toMatchObject({
      cluster: 'cluster-a',
      evidence: ['10.0.1.10:49152: 4/4'],
    });
  });

  it('preserves same-cluster mixed-version and duplicate diagnoses with cluster identity', () => {
    const diagnostics = analyzeClientConnections([
      connection({ clusterName: 'cluster-a', clientId: 'producer-a', version: '5.0.7' }),
      connection({
        clusterName: 'cluster-a',
        clientId: 'producer-b',
        version: '4.9.8',
        address: '10.0.1.11:49152',
      }),
      connection({ clusterName: 'cluster-b', clientId: 'dup-client' }),
      connection({ clusterName: 'cluster-b', clientId: 'dup-client' }),
    ]);

    expect(diagnostics.summary.mixedVersionResourceCount).toBe(1);
    expect(diagnostics.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'MIXED_VERSION_RESOURCE',
          cluster: 'cluster-a',
          resource: 'Producer:order-events',
        }),
        expect.objectContaining({
          code: 'EXACT_DUPLICATE_CONNECTION',
          cluster: 'cluster-b',
          clientId: 'dup-client',
        }),
      ]),
    );
  });
});

describe('client connection diagnostics with unknown provider results', () => {
  it('does not infer mixed protocol and version metadata from unknown proxy rows', () => {
    // Mirrors RocketMQClientProvider.toConnectionVO: Proxy-derived rows carry no
    // protocol/version by design, so an unknown dimension is not an observed one.
    const diagnostics = analyzeClientConnections([
      connection({
        type: 'Consumer',
        groupOrTopic: 'cg-order',
        clientId: 'direct-consumer',
        protocol: 'Remoting',
        version: 'V5_0_0',
      }),
      connection({
        type: 'Consumer',
        groupOrTopic: 'cg-order',
        clientId: 'proxy-consumer',
        protocol: null,
        version: null,
        address: '10.0.1.11:49152',
      }),
    ]);

    expect(diagnostics.issues.map((item) => item.code)).not.toContain('MIXED_PROTOCOL_RESOURCE');
    expect(diagnostics.issues.map((item) => item.code)).not.toContain('MIXED_VERSION_RESOURCE');
    expect(diagnostics.summary.mixedProtocolResourceCount).toBe(0);
    expect(diagnostics.summary.mixedVersionResourceCount).toBe(0);
    expect(diagnostics.status).toBe('healthy');
  });

  it('does not infer mixed language or version dimensions from unknown metadata', () => {
    const diagnostics = analyzeClientConnections([
      connection({
        type: 'Consumer',
        groupOrTopic: 'cg-order',
        clientId: 'direct-consumer',
        protocol: 'Remoting',
        language: 'Java',
        version: 'V5_0_0',
      }),
      connection({
        type: 'Consumer',
        groupOrTopic: 'cg-order',
        clientId: 'proxy-consumer',
        protocol: null,
        language: null,
        version: null,
        address: '10.0.1.11:49152',
      }),
    ]);

    expect(
      diagnostics.issues.filter((item) => item.code === 'MIXED_VERSION_RESOURCE'),
    ).toEqual([]);
  });

  it('does not assert a single online consumer from an incomplete scan', () => {
    const diagnostics = analyzeClientConnections([
      connection({
        type: 'Consumer',
        groupOrTopic: 'cg-order',
        clientId: 'consumer-a',
        partial: true,
      }),
    ]);

    expect(
      diagnostics.issues.filter((item) => item.code === 'SINGLE_CONSUMER_INSTANCE'),
    ).toEqual([]);
    expect(diagnostics.summary.singleConsumerGroupCount).toBe(0);
  });

  it('describes partial scans for both producer and consumer queries', () => {
    const diagnostics = analyzeClientConnections([
      connection({
        type: 'Consumer',
        groupOrTopic: 'cg-order',
        clientId: 'consumer-a',
        partial: true,
      }),
    ]);

    const partialIssue = diagnostics.issues.find(
      (item) => item.code === 'PARTIAL_CONNECTION_SCAN',
    );
    expect(partialIssue).toBeDefined();
    expect(partialIssue?.description).toContain('Producer');
    expect(partialIssue?.description).toContain('Consumer');
  });

  it('keeps genuine mixed dimensions and custom reported metadata diagnosed', () => {
    const diagnostics = analyzeClientConnections([
      connection({
        type: 'Consumer',
        groupOrTopic: 'cg-order',
        clientId: 'direct-consumer',
        protocol: 'Remoting',
        version: 'V5_0_0',
      }),
      connection({
        type: 'Consumer',
        groupOrTopic: 'cg-order',
        clientId: 'custom-consumer',
        protocol: 'Custom',
        version: 'V4_9_8',
        address: '10.0.1.11:49152',
      }),
    ]);

    expect(diagnostics.issues.map((item) => item.code)).toContain('MIXED_PROTOCOL_RESOURCE');
    expect(diagnostics.issues.map((item) => item.code)).toContain('MIXED_VERSION_RESOURCE');
    expect(diagnostics.issues.map((item) => item.code)).toContain('UNKNOWN_PROTOCOL');
    expect(diagnostics.summary.mixedProtocolResourceCount).toBe(1);
    expect(diagnostics.summary.mixedVersionResourceCount).toBe(1);
  });

  it('still flags a single online consumer when the scan is complete', () => {
    const diagnostics = analyzeClientConnections([
      connection({ type: 'Consumer', groupOrTopic: 'cg-order', clientId: 'consumer-a' }),
    ]);

    expect(diagnostics.summary.singleConsumerGroupCount).toBe(1);
    expect(diagnostics.issues.map((item) => item.code)).toContain('SINGLE_CONSUMER_INSTANCE');
  });
});
