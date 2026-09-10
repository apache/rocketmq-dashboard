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
import useAuthStore from '../authStore';

describe('authStore', () => {
  afterEach(() => {
    useAuthStore.getState().logout();
    localStorage.clear();
  });

  it('updates the in-memory session through login and logout', () => {
    useAuthStore.getState().login('studio-admin', 7, true);
    expect(useAuthStore.getState()).toMatchObject({
      user: 'studio-admin',
      userId: 7,
      admin: true,
    });

    useAuthStore.getState().logout();
    expect(useAuthStore.getState()).toMatchObject({
      user: null,
      userId: null,
      admin: null,
    });
  });

  it('persists display identity without a bearer token', () => {
    useAuthStore.getState().login('studio-admin', 7, true);

    expect(localStorage.getItem('rocketmq-studio-user')).toBe('studio-admin');
    expect(localStorage.getItem('rocketmq-studio-user-id')).toBe('7');
    expect(localStorage.getItem('rocketmq-studio-user-admin')).toBe('true');
    expect(localStorage.getItem('token')).toBeNull();
  });
});
