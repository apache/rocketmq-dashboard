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
import { cleanupAuditLogs, listAuditRecords } from './ops';

const mock = new MockAdapter(client);

afterEach(() => {
  mock.reset();
});

describe('audit log API', () => {
  it('uses the backend PageResult contract for filtered audit queries', async () => {
    mock.onGet('/audit-logs').reply((config) => {
      expect(config.params).toEqual({ page: 2, pageSize: 10, result: 'SUCCESS' });
      return [200, { code: 200, data: { items: [], total: 12, page: 2, size: 10 } }];
    });

    await expect(listAuditRecords({ page: 2, pageSize: 10, result: 'SUCCESS' })).resolves.toEqual({
      items: [],
      total: 12,
      page: 2,
      size: 10,
    });
  });

  it('returns the backend cleanup count', async () => {
    mock.onPost('/audit-logs/cleanup', { beforeDays: 30 }).reply(200, {
      code: 200,
      data: { deleted: 3 },
    });

    await expect(cleanupAuditLogs(30)).resolves.toEqual({ deleted: 3 });
  });
});
