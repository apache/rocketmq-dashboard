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
import { listConnections } from './connections';
import type { ClientConnection } from './connections';

const mock = new MockAdapter(client);
const connection: ClientConnection = {
  clientId: 'producer-001',
  type: 'Producer',
  groupOrTopic: 'orders',
  protocol: 'gRPC',
  address: '127.0.0.1:8081',
  language: 'Java',
  version: '5.1.0',
  connectedAt: '2026-07-17T00:00:00',
  clusterName: 'production-cluster',
};

describe('client connections API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('uses the query parameters supported by the backend', async () => {
    mock.onGet('/clients').reply((config) => {
      expect(config.params).toEqual({ clusterId: 'production-cluster', type: 'Producer' });
      return [200, { code: 200, data: [connection] }];
    });

    await expect(
      listConnections({ clusterId: 'production-cluster', type: 'Producer' }),
    ).resolves.toEqual([connection]);
  });
});
