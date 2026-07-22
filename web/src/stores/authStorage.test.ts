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
  TOKEN_STORAGE_KEY,
  USER_STORAGE_KEY,
} from './authStorage';

describe('auth session storage', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('persists the token and user together', () => {
    persistAuthSession('token-1', 'studio-admin');

    expect(readAuthSession()).toEqual({ token: 'token-1', user: 'studio-admin' });
  });

  it('does not restore an orphaned user without a token', () => {
    localStorage.setItem(USER_STORAGE_KEY, 'studio-admin');

    expect(readAuthSession()).toEqual({ token: null, user: null });
  });

  it('clears every persisted session key', () => {
    persistAuthSession('token-1', 'studio-admin');
    clearAuthSession();

    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(USER_STORAGE_KEY)).toBeNull();
  });
});
