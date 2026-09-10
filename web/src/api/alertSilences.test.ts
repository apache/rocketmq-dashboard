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
  createAlertSilence,
  deleteAlertSilence,
  listAlertSilences,
  listAlertSilencesPage,
} from './ops';
import type { CreateAlertSilence } from './ops';

const mock = new MockAdapter(client);
const silence = {
  id: 12,
  domain: 'CLUSTER',
  startsAt: '2026-08-22T09:00:00',
  endsAt: '2026-08-22T10:00:00',
  createdBy: 'admin',
};

describe('alert silences API', () => {
  beforeEach(() => mock.reset());
  afterEach(() => mock.reset());

  it('lists and pages alert silences', async () => {
    mock.onGet('/alert-silences').reply(200, { code: 200, data: [silence] });
    mock.onGet('/alert-silences/page').reply((config) => {
      expect(config.params).toEqual({ page: 2, pageSize: 10 });
      return [200, { code: 200, data: { items: [silence], total: 1, page: 2, size: 10 } }];
    });

    await expect(listAlertSilences()).resolves.toEqual([silence]);
    await expect(listAlertSilencesPage({ page: 2, pageSize: 10 })).resolves.toMatchObject({
      total: 1,
    });
  });

  it('creates a silence and deletes by id', async () => {
    mock.onPost('/alert-silences').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ domain: 'CLUSTER' });
      return [200, { code: 200, data: silence }];
    });
    mock.onDelete('/alert-silences/12').reply(200, { code: 200, data: null });

    await expect(
      createAlertSilence({ domain: 'CLUSTER', startsAt: '2026-08-22T09:00:00', endsAt: '2026-08-22T10:00:00' } as CreateAlertSilence),
    ).resolves.toEqual(silence);
    await expect(deleteAlertSilence(12)).resolves.toBeUndefined();
  });
});
