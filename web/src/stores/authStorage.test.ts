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

import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  clearAuthSession,
  persistAuthSession,
  readAuthSession,
  TOKEN_STORAGE_KEY,
  USER_ADMIN_STORAGE_KEY,
  USER_STORAGE_KEY,
} from './authStorage';

describe('auth session storage', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('persists the token and user together', () => {
    persistAuthSession('token-1', 'studio-admin', true);

    expect(readAuthSession()).toEqual({ token: 'token-1', user: 'studio-admin', admin: true });
  });

  it('rolls back every session key when a later storage write fails', () => {
    persistAuthSession('old-token', 'old-user', false);
    const originalSetItem = Storage.prototype.setItem;
    let writeCount = 0;
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(function (
      this: Storage,
      key: string,
      value: string,
    ) {
      writeCount += 1;
      if (writeCount === 2) {
        throw new DOMException('Storage quota exceeded', 'QuotaExceededError');
      }
      originalSetItem.call(this, key, value);
    });

    try {
      persistAuthSession('new-token', 'new-user', true);
    } finally {
      setItem.mockRestore();
    }

    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(USER_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(USER_ADMIN_STORAGE_KEY)).toBeNull();
  });

  it('does not restore an orphaned user without a token', () => {
    localStorage.setItem(USER_STORAGE_KEY, 'studio-admin');
    localStorage.setItem(USER_ADMIN_STORAGE_KEY, 'true');

    expect(readAuthSession()).toEqual({ token: null, user: null, admin: null });
  });

  it('does not infer admin permissions from legacy sessions', () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, 'token-1');
    localStorage.setItem(USER_STORAGE_KEY, 'studio-admin');

    expect(readAuthSession()).toEqual({ token: 'token-1', user: 'studio-admin', admin: null });
  });

  it('clears every persisted session key', () => {
    persistAuthSession('token-1', 'studio-admin', true);
    clearAuthSession();

    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(USER_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(USER_ADMIN_STORAGE_KEY)).toBeNull();
  });
});
