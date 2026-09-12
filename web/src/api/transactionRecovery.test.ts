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
import { inspectTransactionRecovery, recoverTransactionCheck } from './transactionRecovery';

const mock = new MockAdapter(client);
afterEach(() => mock.reset());
const command = { instanceId: 'instance-a', offsetMessageId: '7F00000100002A9F000000000000002A' };

it('sends the instance-bound preview to its read-only endpoint', async () => {
  mock
    .onPost('/messages/transaction-recovery/preview')
    .reply(200, { code: 200, data: { originalTopic: 'orders' } });
  expect(await inspectTransactionRecovery(command)).toEqual({ originalTopic: 'orders' });
  expect(JSON.parse(mock.history.post[0].data)).toEqual(command);
});

it('uses the explicit recovery endpoint only for submission', async () => {
  mock
    .onPost('/messages/transaction-recovery')
    .reply(200, { code: 200, data: { producerGroup: 'payment-producer' } });
  expect(await recoverTransactionCheck(command)).toEqual({ producerGroup: 'payment-producer' });
  expect(JSON.parse(mock.history.post[0].data)).toEqual(command);
});
