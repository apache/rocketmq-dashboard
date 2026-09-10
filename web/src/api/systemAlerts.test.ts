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
  acknowledgeAlert,
  clearAcknowledgedAlerts,
  getCollectorStatus,
  listRelatedSystemAlerts,
  listSystemAlerts,
  listSystemAlertsPage,
} from './ops';

const mock = new MockAdapter(client);
const alert = {
  id: 3,
  level: 'WARNING',
  title: 'Broker offline',
  instanceId: 'instance-a',
  acknowledged: false,
};

describe('system alerts API', () => {
  beforeEach(() => mock.reset());
  afterEach(() => mock.reset());

  it('lists alerts with the provided query filters', async () => {
    mock.onGet('/system-alerts').reply((config) => {
      expect(config.params).toEqual({ instanceId: 'instance-a', level: 'WARNING' });
      return [200, { code: 200, data: [alert] }];
    });

    await expect(
      listSystemAlerts({ instanceId: 'instance-a', level: 'WARNING' }),
    ).resolves.toEqual([alert]);
  });

  it('pages system alerts with the default paging', async () => {
    mock.onGet('/system-alerts/page').reply((config) => {
      expect(config.params).toEqual({});
      return [200, { code: 200, data: { items: [alert], total: 1, page: 1, size: 20 } }];
    });

    await expect(listSystemAlertsPage()).resolves.toMatchObject({ total: 1 });
  });

  it('loads related alerts and acknowledges an alert', async () => {
    mock.onGet('/system-alerts/3/related').reply(200, { code: 200, data: [alert] });
    mock.onPost('/system-alerts/acknowledge').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: 3 });
      return [200, { code: 200, data: null }];
    });

    await expect(listRelatedSystemAlerts(3)).resolves.toEqual([alert]);
    await expect(acknowledgeAlert(3)).resolves.toBeUndefined();
  });

  it('clears acknowledged alerts and reports the count', async () => {
    mock.onPost('/system-alerts/clear-acknowledged').reply(200, {
      code: 200,
      data: { cleared: 2 },
    });

    await expect(clearAcknowledgedAlerts()).resolves.toEqual({ cleared: 2 });
  });

  it('loads the collector status', async () => {
    mock.onGet('/alert-collector-status').reply(200, {
      code: 200,
      data: { collectionInterval: 'PT1M', clusterCollectorCount: 2, businessCollectorCount: 3 },
    });

    await expect(getCollectorStatus()).resolves.toMatchObject({ clusterCollectorCount: 2 });
  });
});
