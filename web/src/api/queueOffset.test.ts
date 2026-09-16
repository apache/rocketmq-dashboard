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
import { applyQueueOffset, previewQueueOffset } from './queueOffset';

const mock = new MockAdapter(client);
const target = {
  instanceId: 'instance-a',
  group: 'cg-orders',
  topic: 'orders',
  brokerName: 'broker-a',
  queueId: 1,
};
afterEach(() => mock.reset());

describe('single queue offset contract', () => {
  it('previews only the selected queue with an exact decimal offset', async () => {
    mock.onPost('/groups/queue-offset/preview').reply((request) => {
      expect(JSON.parse(request.data)).toEqual({ ...target, offset: '9007199254740993' });
      return [200, { code: 200, data: { targetOffset: '9007199254740993' } }];
    });
    expect((await previewQueueOffset(target, '9007199254740993')).targetOffset).toBe(
      '9007199254740993',
    );
    expect(mock.history.post).toHaveLength(1);
  });

  it('includes the previously observed position when applying the update', async () => {
    mock.onPost('/groups/queue-offset').reply((request) => {
      expect(JSON.parse(request.data)).toEqual({
        ...target,
        offset: '20',
        expectedCurrentOffset: '40',
      });
      return [200, { code: 200, data: { currentOffset: '40', targetOffset: '20' } }];
    });
    expect((await applyQueueOffset(target, '20', '40')).targetOffset).toBe('20');
    expect(mock.history.post).toHaveLength(1);
  });
});
