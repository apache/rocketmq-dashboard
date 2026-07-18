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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import { login, logout } from './auth';

const mock = new MockAdapter(client);

describe('auth API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('submits login credentials and returns the authenticated user', async () => {
    mock.onPost('/auth/login').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ username: 'studio', password: 'secret' });
      return [200, { code: 200, data: { token: 'token', username: 'studio', role: 'ADMIN' } }];
    });

    await expect(login('studio', 'secret')).resolves.toEqual({
      token: 'token',
      username: 'studio',
      role: 'ADMIN',
    });
  });

  it('posts to the logout endpoint', async () => {
    mock.onPost('/auth/logout').reply(200, { code: 200, data: null });

    await expect(logout()).resolves.toBeUndefined();
  });
});
