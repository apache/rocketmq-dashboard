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
import { afterEach, expect, it } from 'vitest';
import client from './client';
import { inspectBrokerTimer } from './brokerTimer';

const mock = new MockAdapter(client);
afterEach(() => mock.reset());

it('passes instance, broker and cancellation without converting 64-bit counters', async () => {
  const payload = { runtime: { values: { timerOffsetBehind: '9007199254740993' }, error: null } };
  mock.onGet('/brokers/timer').reply(200, { code: 200, data: payload });
  const controller = new AbortController();
  expect(await inspectBrokerTimer('instance-a', 'broker/a', controller.signal)).toEqual(payload);
  expect(mock.history.get[0].params).toEqual({ instanceId: 'instance-a', brokerName: 'broker/a' });
  expect(mock.history.get[0].signal).toBe(controller.signal);
  expect(mock.history.post).toHaveLength(0);
});

it('honors a cancelled inspection', async () => {
  const controller = new AbortController();
  controller.abort();
  await expect(inspectBrokerTimer('instance-a', 'broker-a', controller.signal)).rejects.toThrow();
  expect(mock.history.get).toHaveLength(0);
});
