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
import { cleanupAuditLogs, listAuditRecords } from './ops';

const mock = new MockAdapter(client);

describe('audit log API', () => {
  beforeEach(() => mock.reset());
  afterEach(() => mock.reset());

  it('lists audit records with the provided filters', async () => {
    mock.onGet('/audit-logs').reply((config) => {
      expect(config.params).toEqual({ action: 'cluster.update' });
      return [
        200,
        { code: 200, data: { items: [{ id: 1, action: 'cluster.update' }], total: 1, page: 1, size: 20 } },
      ];
    });

    await expect(listAuditRecords({ action: 'cluster.update' })).resolves.toMatchObject({
      total: 1,
    });
  });

  it('cleans up audit logs older than the cutoff days', async () => {
    mock.onPost('/audit-logs/cleanup').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ beforeDays: 30 });
      return [200, { code: 200, data: { deleted: 5 } }];
    });

    await expect(cleanupAuditLogs(30)).resolves.toEqual({ deleted: 5 });
  });
});
