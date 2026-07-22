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
import { getDashboard, queryMetrics } from './metrics';

const mock = new MockAdapter(client);
const dashboard = {
  stats: {
    totalClusters: 1,
    healthyClusters: 1,
    totalBrokers: 2,
    totalProxies: 1,
    totalNameServers: 1,
    totalTopics: 4,
    totalConsumerGroups: 3,
    totalMessagesToday: 100,
    messagesPerSecond: 10,
    tpsIn: 8,
    tpsOut: 7,
  },
  clusters: [],
};

describe('metrics API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('loads dashboard data from the dashboard endpoint', async () => {
    mock.onGet('/dashboard').reply(200, { code: 200, data: dashboard });

    await expect(getDashboard()).resolves.toEqual(dashboard);
  });

  it('posts a metrics query and returns its result', async () => {
    const query = { metric: 'TPS_IN', start: 1, end: 2, step: '1m' };
    mock.onPost('/metrics/query').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(query);
      return [200, { code: 200, data: [{ timestamp: 1, value: 8 }] }];
    });

    await expect(queryMetrics(query)).resolves.toEqual([{ timestamp: 1, value: 8 }]);
  });
});
