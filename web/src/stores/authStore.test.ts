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

import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  USER_ADMIN_STORAGE_KEY,
  USER_ID_STORAGE_KEY,
  USER_STORAGE_KEY,
} from './authStorage';

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetModules();
  });

  it('starts unauthenticated when nothing is stored', async () => {
    const { default: store } = await import('./authStore');

    expect(store.getState().user).toBeNull();
    expect(store.getState().userId).toBeNull();
    expect(store.getState().admin).toBeNull();
  });

  it('restores a persisted session on load', async () => {
    localStorage.setItem(USER_STORAGE_KEY, 'alice');
    localStorage.setItem(USER_ID_STORAGE_KEY, '7');
    localStorage.setItem(USER_ADMIN_STORAGE_KEY, 'true');

    const { default: store } = await import('./authStore');

    expect(store.getState().user).toBe('alice');
    expect(store.getState().userId).toBe(7);
    expect(store.getState().admin).toBe(true);
  });

  it('treats a non-numeric persisted user id as absent', async () => {
    localStorage.setItem(USER_STORAGE_KEY, 'bob');
    localStorage.setItem(USER_ID_STORAGE_KEY, 'not-a-number');
    localStorage.setItem(USER_ADMIN_STORAGE_KEY, 'false');

    const { default: store } = await import('./authStore');

    expect(store.getState().user).toBe('bob');
    expect(store.getState().userId).toBeNull();
    expect(store.getState().admin).toBe(false);
  });

  it('login persists and applies the session', async () => {
    const { default: store } = await import('./authStore');

    store.getState().login('bob', 9, false);

    expect(store.getState().user).toBe('bob');
    expect(store.getState().userId).toBe(9);
    expect(store.getState().admin).toBe(false);
    expect(localStorage.getItem(USER_STORAGE_KEY)).toBe('bob');
    expect(localStorage.getItem(USER_ID_STORAGE_KEY)).toBe('9');
    expect(localStorage.getItem(USER_ADMIN_STORAGE_KEY)).toBe('false');
  });

  it('logout clears the session and the stored keys', async () => {
    localStorage.setItem(USER_STORAGE_KEY, 'alice');
    localStorage.setItem(USER_ID_STORAGE_KEY, '7');
    localStorage.setItem(USER_ADMIN_STORAGE_KEY, 'true');

    const { default: store } = await import('./authStore');
    store.getState().logout();

    expect(store.getState().user).toBeNull();
    expect(store.getState().userId).toBeNull();
    expect(store.getState().admin).toBeNull();
    expect(localStorage.getItem(USER_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(USER_ADMIN_STORAGE_KEY)).toBeNull();
  });
});
