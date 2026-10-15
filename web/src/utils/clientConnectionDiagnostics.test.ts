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

const row = (overrides: Partial<ClientConnection>): ClientConnection => ({
  clientId: 'direct',
  type: 'Consumer',
  groupOrTopic: 'group-a',
  protocol: 'Remoting',
  address: '10.0.0.1:40000',
  language: 'Java',
  version: 'V5_0_0',
  clusterName: 'cluster-a',
  ...overrides,
});
const codes = (rows: ClientConnection[]) =>
  analyzeClientConnections(rows).issues.map((x) => x.code);
describe('observed connection evidence', () => {
  it('does not infer a mixed protocol or version from the proxy fields intentionally absent on the wire', () => {
    const d = analyzeClientConnections([
      row({}),
      row({ clientId: 'proxy', address: '10.0.0.3:40000', protocol: null, version: null }),
    ]);
    expect(d.issues.map((x) => x.code)).not.toContain('MIXED_PROTOCOL_RESOURCE');
    expect(d.issues.map((x) => x.code)).not.toContain('MIXED_VERSION_RESOURCE');
    expect(d.summary.mixedProtocolResourceCount).toBe(0);
    expect(d.summary.mixedVersionResourceCount).toBe(0);
    expect(d.resources[0].protocols).toContain('unknown');
    expect(d.issues.map((x) => x.code)).not.toContain('UNKNOWN_PROTOCOL');
    expect(d.issues.map((x) => x.code)).not.toContain('UNKNOWN_VERSION');
  });
  it('does not diagnose multi-language multi-version from missing metadata', () => {
    const d = analyzeClientConnections([
      row({}),
      row({ clientId: 'proxy', address: '10.0.0.3:40000', language: null, version: null }),
    ]);
    expect(d.issues.filter((x) => x.code === 'MIXED_VERSION_RESOURCE')).toEqual([]);
    expect(d.issues.map((x) => x.code)).toContain('UNKNOWN_LANGUAGE');
  });
  it('does not assert a single online consumer from an incomplete scan', () => {
    const d = analyzeClientConnections([row({ partial: true })]);
    expect(d.issues.map((x) => x.code)).not.toContain('SINGLE_CONSUMER_INSTANCE');
    expect(d.summary.singleConsumerGroupCount).toBe(0);
    expect(d.issues.map((x) => x.code)).toContain('PARTIAL_CONNECTION_SCAN');
  });
  it('still reports genuinely observed mixed protocol/version clients', () => {
    expect(
      codes([
        row({}),
        row({ clientId: 'grpc', address: '10.0.0.3:40000', protocol: 'gRPC', version: 'V5_1_0' }),
      ]),
    ).toEqual(expect.arrayContaining(['MIXED_PROTOCOL_RESOURCE', 'MIXED_VERSION_RESOURCE']));
  });
  it('still warns about a confirmed complete single consumer', () => {
    expect(codes([row({})])).toContain('SINGLE_CONSUMER_INSTANCE');
  });
});

it.each([null, undefined, '', '   ', '-', 'unknown'])(
  'keeps unreported metadata out of mixed dimensions (%s) Test',
  (missing) => {
    const diagnostics = analyzeClientConnections([
      row({}),
      row({
        clientId: 'proxy',
        address: '10.0.0.3:40000',
        protocol: missing,
        language: missing,
        version: missing,
      }),
    ]);
    expect(
      diagnostics.issues.filter(
        (issue) =>
          issue.code === 'MIXED_PROTOCOL_RESOURCE' || issue.code === 'MIXED_VERSION_RESOURCE',
      ),
    ).toEqual([]);
    expect(diagnostics.summary).toMatchObject({
      mixedProtocolResourceCount: 0,
      mixedVersionResourceCount: 0,
    });
  },
);
