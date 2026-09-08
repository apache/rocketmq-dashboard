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
import { changeColdRead, inspectColdRead } from './coldRead';

const mock = new MockAdapter(client);
afterEach(() => mock.reset());

it('binds reads to an instance and supports cancellation', async () => {
  mock
    .onGet('/brokers/cold-read')
    .reply(200, { code: 200, data: { globalBytes: '9007199254740993' } });
  const signal = new AbortController().signal;
  expect(await inspectColdRead('instance-a', 'broker-a', signal)).toEqual({
    globalBytes: '9007199254740993',
  });
  expect(mock.history.get[0].params).toEqual({ instanceId: 'instance-a', brokerName: 'broker-a' });
  expect(mock.history.get[0].signal).toBe(signal);
});

it('sends one exact integer threshold as a string', async () => {
  mock.onPost('/brokers/cold-read/config').reply(200, { code: 200, data: { verified: true } });
  const command = {
    instanceId: 'instance-a',
    brokerName: 'broker-a',
    group: 'orders',
    action: 'SET' as const,
    threshold: '9007199254740993',
  };
  expect(await changeColdRead(command)).toEqual({ verified: true });
  expect(JSON.parse(mock.history.post[0].data)).toEqual(command);
});

it('removes only the named group without a threshold', async () => {
  mock.onPost('/brokers/cold-read/config').reply(200, { code: 200, data: { verified: false } });
  const command = {
    instanceId: 'instance-a',
    brokerName: 'broker-a',
    group: 'orders',
    action: 'REMOVE' as const,
  };
  await changeColdRead(command);
  expect(JSON.parse(mock.history.post[0].data)).toEqual(command);
});
