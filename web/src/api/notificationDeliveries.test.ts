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
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import client from './client';
import {
  listAlertDeliveries,
  listAlertDeliveriesPage,
  retryAlertDelivery,
  retryAlertDeliveries,
} from './ops';

const mock = new MockAdapter(client);
const delivery = {
  id: 1,
  alertId: 3,
  channel: 'dingtalk',
  status: 'PENDING',
};

describe('notification deliveries API', () => {
  beforeEach(() => mock.reset());
  afterEach(() => mock.reset());

  it('lists the deliveries for one alert', async () => {
    mock.onGet('/system-alerts/3/deliveries').reply(200, { code: 200, data: [delivery] });

    await expect(listAlertDeliveries(3)).resolves.toEqual([delivery]);
  });

  it('pages deliveries with the provided filters', async () => {
    mock.onGet('/system-alerts/deliveries/page').reply((config) => {
      expect(config.params).toEqual({ channel: 'dingtalk' });
      return [200, { code: 200, data: { items: [delivery], total: 1, page: 1, size: 20 } }];
    });

    await expect(listAlertDeliveriesPage({ channel: 'dingtalk' })).resolves.toMatchObject({
      total: 1,
    });
  });

  it('retries one delivery and a batch of deliveries', async () => {
    mock.onPost('/system-alerts/deliveries/1/retry').reply(200, { code: 200, data: null });
    mock.onPost('/system-alerts/deliveries/retry').reply((config) => {
      expect(JSON.parse(config.data)).toEqual([1, 2]);
      return [200, { code: 200, data: { retried: 2, failed: 0 } }];
    });

    await expect(retryAlertDelivery(1)).resolves.toBeUndefined();
    await expect(retryAlertDeliveries([1, 2])).resolves.toEqual({ retried: 2, failed: 0 });
  });
});
