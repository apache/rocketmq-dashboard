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

export const USER_STORAGE_KEY = 'rocketmq-studio-user';
export const USER_ID_STORAGE_KEY = 'rocketmq-studio-user-id';
export const USER_ADMIN_STORAGE_KEY = 'rocketmq-studio-user-admin';

export interface AuthSession {
  user: string | null;
  userId: string | null;
  admin: boolean | null;
}

export function readAuthSession(): AuthSession {
  try {
    const admin = localStorage.getItem(USER_ADMIN_STORAGE_KEY);
    return {
      user: localStorage.getItem(USER_STORAGE_KEY),
      userId: localStorage.getItem(USER_ID_STORAGE_KEY),
      admin: admin != null ? admin === 'true' : null,
    };
  } catch {
    return { user: null, userId: null, admin: null };
  }
}

export function persistAuthSession(user: string, userId: string | null, admin: boolean): void {
  try {
    localStorage.setItem(USER_STORAGE_KEY, user);
    if (userId) {
      localStorage.setItem(USER_ID_STORAGE_KEY, userId);
    } else {
      localStorage.removeItem(USER_ID_STORAGE_KEY);
    }
    localStorage.setItem(USER_ADMIN_STORAGE_KEY, String(admin));
  } catch {
    // The in-memory store remains usable when browser storage is unavailable.
  }
}

export function clearAuthSession(): void {
  try {
    localStorage.removeItem('token');
    localStorage.removeItem(USER_STORAGE_KEY);
    localStorage.removeItem(USER_ID_STORAGE_KEY);
    localStorage.removeItem(USER_ADMIN_STORAGE_KEY);
  } catch {
    // The caller still clears the in-memory store.
  }
}
