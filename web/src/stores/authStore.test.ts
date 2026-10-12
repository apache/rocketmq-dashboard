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

async function loadStore() {
  vi.resetModules();
  return (await import('./authStore')).default;
}

describe('authStore', () => {
  afterEach(() => {
    localStorage.clear();
    vi.resetModules();
  });

  it('starts without a display identity when storage is empty', async () => {
    const store = await loadStore();

    expect(store.getState()).toMatchObject({ user: null, userId: null, admin: null });
  });

  it('updates and persists the display identity on login without storing a bearer token', async () => {
    const store = await loadStore();

    store.getState().login('studio-admin', 7, true);

    expect(store.getState()).toMatchObject({ user: 'studio-admin', userId: 7, admin: true });
    expect(localStorage.getItem('rocketmq-studio-user')).toBe('studio-admin');
    expect(localStorage.getItem('rocketmq-studio-user-id')).toBe('7');
    expect(localStorage.getItem('rocketmq-studio-user-admin')).toBe('true');
    expect(localStorage.getItem('token')).toBeNull();
  });

  it.each([true, false])(
    'restores the persisted display identity with admin=%s on reload',
    async (admin) => {
      const store = await loadStore();
      store.getState().login('studio-user', 7, admin);

      const reloadedStore = await loadStore();

      expect(reloadedStore).not.toBe(store);
      expect(reloadedStore.getState()).toMatchObject({ user: 'studio-user', userId: 7, admin });
    },
  );

  it('clears the display identity and legacy bearer token on logout, including after reload', async () => {
    const store = await loadStore();
    store.getState().login('studio-admin', 7, true);
    localStorage.setItem('token', 'legacy-token');

    store.getState().logout();

    expect(store.getState()).toMatchObject({ user: null, userId: null, admin: null });
    expect(localStorage.getItem('rocketmq-studio-user')).toBeNull();
    expect(localStorage.getItem('rocketmq-studio-user-id')).toBeNull();
    expect(localStorage.getItem('rocketmq-studio-user-admin')).toBeNull();
    expect(localStorage.getItem('token')).toBeNull();
    const reloadedStore = await loadStore();
    expect(reloadedStore.getState()).toMatchObject({ user: null, userId: null, admin: null });
  });
});
