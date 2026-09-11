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
