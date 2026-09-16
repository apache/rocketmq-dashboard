/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import { afterEach, describe, expect, it } from 'vitest';
import client from './client';
import { inspectBrokerHa } from './brokerHa';

const mock = new MockAdapter(client);
afterEach(() => mock.reset());

describe('HA API contract', () => {
  it('sends the selected instance and broker as query parameters and preserves the envelope', async () => {
    const snapshot = { brokerName: 'broker/a', sampledAt: 10, complete: true, nodes: [] };
    const signal = new AbortController().signal;
    mock.onGet('/brokers/ha').reply((request) => {
      expect(request.params).toEqual({ instanceId: 'instance-a', brokerName: 'broker/a' });
      expect(request.signal).toBe(signal);
      return [200, { code: 200, data: snapshot }];
    });
    expect(await inspectBrokerHa('instance-a', 'broker/a', signal)).toEqual(snapshot);
    expect(mock.history.post).toHaveLength(0);
  });

  it('honors cancellation before sending the request', async () => {
    const controller = new AbortController();
    controller.abort();
    await expect(inspectBrokerHa('instance-a', 'broker-a', controller.signal)).rejects.toThrow();
    expect(mock.history.get).toHaveLength(0);
  });
});
