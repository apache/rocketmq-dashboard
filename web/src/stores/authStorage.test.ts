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
import {
  clearAuthSession,
  persistAuthSession,
  readAuthSession,
  USER_ADMIN_STORAGE_KEY,
  USER_ID_STORAGE_KEY,
  USER_STORAGE_KEY,
} from './authStorage';

describe('auth session storage', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('persists display identity without a bearer token', () => {
    persistAuthSession('studio-admin', 7, true);

    expect(readAuthSession()).toEqual({ user: 'studio-admin', userId: 7, admin: true });
    expect(localStorage.getItem(USER_ID_STORAGE_KEY)).toBe('7');
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('restores display identity independently from the HttpOnly session cookie', () => {
    localStorage.setItem(USER_STORAGE_KEY, 'studio-admin');
    localStorage.setItem(USER_ID_STORAGE_KEY, '7');
    localStorage.setItem(USER_ADMIN_STORAGE_KEY, 'true');

    expect(readAuthSession()).toEqual({ user: 'studio-admin', userId: 7, admin: true });
  });

  it('does not infer admin permissions when the display flag is absent', () => {
    localStorage.setItem(USER_STORAGE_KEY, 'studio-admin');

    expect(readAuthSession()).toEqual({ user: 'studio-admin', userId: null, admin: null });
  });

  it('ignores legacy non-numeric stored user ids', () => {
    localStorage.setItem(USER_STORAGE_KEY, 'studio-admin');
    localStorage.setItem(USER_ID_STORAGE_KEY, 'a1b2c3d4-legacy-uuid');
    localStorage.setItem(USER_ADMIN_STORAGE_KEY, 'true');

    expect(readAuthSession()).toEqual({ user: 'studio-admin', userId: null, admin: true });
  });

  it('rejects corrupted identity and permission values', () => {
    localStorage.setItem(USER_ID_STORAGE_KEY, '-1.5');
    localStorage.setItem(USER_ADMIN_STORAGE_KEY, 'yes');

    expect(readAuthSession()).toEqual({ user: null, userId: null, admin: null });
  });

  it('clears every persisted session key', () => {
    localStorage.setItem('token', 'legacy-token');
    persistAuthSession('studio-admin', 7, true);
    clearAuthSession();

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem(USER_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(USER_ID_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(USER_ADMIN_STORAGE_KEY)).toBeNull();
  });
});
