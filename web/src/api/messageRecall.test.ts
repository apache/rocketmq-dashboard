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
import { previewMessageRecall, recallDelayMessage } from './messageRecall';

const mock = new MockAdapter(client);
const request = { instanceId: 'instance-a', topic: 'orders', recallHandle: 'producer-handle' };
afterEach(() => mock.reset());

describe('message recall contract', () => {
  it('keeps handle inspection separate from the recall mutation', async () => {
    const target = {
      topic: 'orders',
      brokerName: 'broker-a',
      messageId: 'original-id',
      deliveryTimestamp: 1888825600000,
    };
    mock.onPost('/messages/recall/preview').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(request);
      return [200, { code: 200, data: target }];
    });
    expect(await previewMessageRecall(request)).toEqual(target);
    expect(mock.history.post.map((config) => config.url)).toEqual(['/messages/recall/preview']);
  });

  it('submits an explicit recall and returns the broker receipt', async () => {
    const receipt = { messageId: 'original-id', acceptedAt: 1788825600000 };
    mock.onPost('/messages/recall').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(request);
      return [200, { code: 200, data: receipt }];
    });
    expect(await recallDelayMessage(request)).toEqual(receipt);
    expect(mock.history.post).toHaveLength(1);
  });
});
