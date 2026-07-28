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

describe('Auth API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', {
      getItem: vi.fn().mockReturnValue(null),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('login should post credentials and return login data', async () => {
    const mockResponse = {
      username: 'admin',
      type: 'ADMIN',
      contextPath: '/',
    };
    mock
      .onPost('/login/login.do', { username: 'admin', password: 'secret' })
      .reply(200, mockResponse);

    const result = await login('admin', 'secret');
    expect(result).toEqual(mockResponse);
    expect(result.username).toBe('admin');
    expect(result.type).toBe('ADMIN');
  });

  it('login should handle error response', async () => {
    mock.onPost('/login/login.do').reply(500, { message: 'Invalid credentials' });

    await expect(login('wrong', 'creds')).rejects.toThrow();
  });

  it('logout should post to logout endpoint', async () => {
    mock.onPost('/login/logout.do').reply(200);

    await expect(logout()).resolves.toBeUndefined();
  });

  it('logout should handle server error', async () => {
    mock.onPost('/login/logout.do').reply(500);

    await expect(logout()).rejects.toThrow();
  });
});
