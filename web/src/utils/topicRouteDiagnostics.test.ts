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
import type { BrokerRoute } from '../api/metadata';
import { analyzeTopicRoutes } from './topicRouteDiagnostics';

const route = (overrides: Partial<BrokerRoute>): BrokerRoute => ({
  brokerName: 'broker-a',
  brokerAddr: '10.0.0.1:10911',
  masterAddr: '10.0.0.1:10911',
  brokerAddrs: {
    '0': '10.0.0.1:10911',
    '1': '10.0.0.2:10911',
  },
  brokerIds: [0, 1],
  replicaCount: 1,
  writeQueues: 8,
  readQueues: 8,
  perm: 'RW',
  permCode: 6,
  readable: true,
  writable: true,
  topicSysFlag: 0,
  ...overrides,
});

describe('topic route diagnostics', () => {
  it('summarizes balanced readable and writable routes as healthy', () => {
    const diagnostics = analyzeTopicRoutes([
      route({ brokerName: 'broker-a' }),
      route({
        brokerName: 'broker-b',
        brokerAddr: '10.0.1.1:10911',
        masterAddr: '10.0.1.1:10911',
        brokerAddrs: {
          '0': '10.0.1.1:10911',
          '1': '10.0.1.2:10911',
        },
      }),
    ]);

    expect(diagnostics.status).toBe('healthy');
    expect(diagnostics.summary).toMatchObject({
      brokerCount: 2,
      addressCount: 4,
      replicaCount: 2,
      writableBrokerCount: 2,
      readableBrokerCount: 2,
      totalWriteQueues: 16,
      totalReadQueues: 16,
    });
    expect(diagnostics.distributions.map((item) => item.writeShare)).toEqual([50, 50]);
    expect(diagnostics.issues).toEqual([]);
    expect(diagnostics.recommendations).toEqual([]);
  });

  it('returns a critical diagnostic when the broker route is missing', () => {
    const diagnostics = analyzeTopicRoutes([]);

    expect(diagnostics.status).toBe('critical');
    expect(diagnostics.summary.brokerCount).toBe(0);
    expect(diagnostics.distributions).toEqual([]);
    expect(diagnostics.issues).toEqual([
      expect.objectContaining({
        code: 'NO_ROUTE',
        severity: 'critical',
      }),
    ]);
    expect(diagnostics.recommendations).toContain('topicRoute.rec.noRoute');
  });

  it('flags route risks from permissions, queues, skew, and stale addresses', () => {
    const diagnostics = analyzeTopicRoutes([
      route({
        brokerName: 'broker-a',
        brokerAddr: '',
        masterAddr: '',
        brokerAddrs: {
          '1': '10.0.0.2:10911',
        },
        brokerIds: [1],
        writeQueues: 12,
        readQueues: 0,
        perm: 'WO',
        permCode: 2,
        readable: false,
        writable: true,
      }),
      route({
        brokerName: 'broker-b',
        brokerAddr: '10.0.0.2:10911',
        masterAddr: '10.0.0.2:10911',
        brokerAddrs: {
          '0': '10.0.0.2:10911',
        },
        brokerIds: [0],
        writeQueues: 2,
        readQueues: 8,
      }),
    ]);

    const issueCodes = diagnostics.issues.map((item) => item.code);

    expect(diagnostics.status).toBe('critical');
    expect(diagnostics.summary.writeSkew).toEqual({ gap: 10, ratio: 1.43 });
    expect(diagnostics.summary.readSkew).toEqual({ gap: 0, ratio: 0 });
    expect(issueCodes).toEqual(
      expect.arrayContaining([
        'MISSING_MASTER_ADDRESS',
        'READ_QUEUE_UNAVAILABLE',
        'PERMISSION_NOT_READABLE',
        'READ_WRITE_QUEUE_MISMATCH',
        'WRITE_QUEUE_SKEW',
        'DUPLICATE_BROKER_ADDRESS',
      ]),
    );
    expect(diagnostics.distributions[0]).toMatchObject({
      brokerName: 'broker-a',
      brokerAddr: '10.0.0.2:10911',
      readable: false,
      writable: true,
      status: 'critical',
    });
    expect(diagnostics.recommendations).toEqual(
      expect.arrayContaining([
        'topicRoute.rec.checkRegistration',
        'topicRoute.rec.alignQueueConfig',
        'topicRoute.rec.balanceQueues',
      ]),
    );
  });

  it('infers read and write permissions from legacy route payloads', () => {
    const diagnostics = analyzeTopicRoutes([
      route({
        brokerName: 'broker-readonly',
        readable: undefined,
        writable: undefined,
        perm: 'RO',
      }),
      route({
        brokerName: 'broker-writeonly',
        readable: undefined,
        writable: undefined,
        perm: 'WO',
      }),
    ]);

    expect(diagnostics.status).toBe('warning');
    expect(diagnostics.distributions[0]).toMatchObject({
      brokerName: 'broker-readonly',
      readable: true,
      writable: false,
    });
    expect(diagnostics.distributions[1]).toMatchObject({
      brokerName: 'broker-writeonly',
      readable: false,
      writable: true,
    });
    expect(diagnostics.issues.map((item) => item.code)).toEqual(
      expect.arrayContaining(['PERMISSION_NOT_WRITABLE', 'PERMISSION_NOT_READABLE']),
    );
  });
});
