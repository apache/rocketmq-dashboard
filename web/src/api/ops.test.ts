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

import { afterEach, describe, expect, it } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import client from './client';
import { clearAcknowledgedAlerts } from './ops';

const mock = new MockAdapter(client);

afterEach(() => {
  mock.reset();
});

describe('system alert API', () => {
  it('returns the number of cleared alerts from the backend', async () => {
    mock.onPost('/system-alerts/clear-acknowledged').reply(200, {
      code: 200,
      message: 'success',
      data: { cleared: 2 },
    });

    await expect(clearAcknowledgedAlerts()).resolves.toEqual({ cleared: 2 });
  });
});
