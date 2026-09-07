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
import { listAllStudioUsers as loadStudioUsersForExport, listStudioUsers, createStudioUser, setStudioUserEnabled, resetStudioUserPassword } from './studioUsers';

const mock = new MockAdapter(client);
const exportQuery = { search: 'op', admin: false };
const exportRequestParams = [1, 2].map((page) => ({ ...exportQuery, page, pageSize: 100 }));
describe('studio users API', () => {
  beforeEach(() => mock.reset());
  afterEach(() => mock.reset());

  it('requests and returns the filtered server page', async () => {
    mock.onGet('/studio-users').reply((config) => [
      200,
      {
        code: 200,
        data: {
          items: [{ id: 7, username: 'operator', admin: false, enabled: true }],
          total: 21,
          page: 2,
          size: 20,
        },
        receivedParams: config.params,
      },
    ]);

    const page = await listStudioUsers({
      search: 'oper',
      admin: false,
      enabled: true,
      page: 2,
      pageSize: 20,
    });

    expect(page.items[0].username).toBe('operator');
    expect(page.total).toBe(21);
    expect(mock.history.get[0].params).toEqual({
      search: 'oper',
      admin: false,
      enabled: true,
      page: 2,
      pageSize: 20,
    });
  });

  it('loads all export pages with the maximum supported page size', async () => {
    mock.onGet('/studio-users').reply((config) => {
      const currentPage = config.params?.page;
      const pageItems =
        currentPage === 1
          ? [{ id: 7, username: 'operator', admin: false, enabled: true }]
          : [{ id: 8, username: 'admin', admin: true, enabled: false }];
      return [
        200,
        {
          code: 200,
          data: {
            items: pageItems,
            total: 2,
            page: currentPage,
            size: 100,
          },
        },
      ];
    });
    const exportedUsers = await loadStudioUsersForExport(exportQuery);
    expect(exportedUsers.map((user) => user.username)).toEqual(['operator', 'admin']);
    expect(mock.history.get).toHaveLength(2);
    expect(mock.history.get.map((request) => request.params)).toEqual([
      exportRequestParams[0],
      exportRequestParams[1],
    ]);
  });

  it('stops exporting when the first page is empty', async () => {
    mock.onGet('/studio-users').reply(200, {
      code: 200,
      data: { items: [], total: 0, page: 1, size: 100 },
    });

    await expect(loadStudioUsersForExport({ search: 'none' })).resolves.toEqual([]);
    expect(mock.history.get).toHaveLength(1);
  });

  it('creates a studio user with the provided credentials', async () => {
    mock.onPost('/studio-users').reply(200, {
      code: 200,
      data: { id: 9, username: 'new-operator', admin: true, enabled: true },
    });

    const created = await createStudioUser({
      username: 'new-operator',
      password: 'secret-pass-1',
      admin: true,
    });

    expect(created.id).toBe(9);
    expect(JSON.parse(mock.history.post[0].data)).toEqual({
      username: 'new-operator',
      password: 'secret-pass-1',
      admin: true,
    });
  });

  it('toggles the enabled state through the status endpoint', async () => {
    mock.onPost('/studio-users/7/status').reply(200, {
      code: 200,
      data: { id: 7, username: 'operator', admin: false, enabled: false },
    });

    const updated = await setStudioUserEnabled(7, false);

    expect(updated.enabled).toBe(false);
    expect(JSON.parse(mock.history.post[0].data)).toEqual({ enabled: false });
  });

  it('resets the password through the password endpoint', async () => {
    mock.onPost('/studio-users/7/password').reply(200, { code: 200 });

    await resetStudioUserPassword(7, 'new-secret-pass');

    expect(JSON.parse(mock.history.post[0].data)).toEqual({ newPassword: 'new-secret-pass' });
  });
});
