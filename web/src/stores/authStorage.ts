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

import { readLocalStorage, removeLocalStorage, writeLocalStorage } from '../utils/browserStorage';

export const USER_STORAGE_KEY = 'rocketmq-studio-user';
export const USER_ID_STORAGE_KEY = 'rocketmq-studio-user-id';
export const USER_ADMIN_STORAGE_KEY = 'rocketmq-studio-user-admin';

export interface AuthSession {
  user: string | null;
  userId: number | null;
  admin: boolean | null;
}

function parseUserId(raw: string | null): number | null {
  if (raw == null || raw === '') {
    return null;
  }
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : null;
}

export function readAuthSession(): AuthSession {
  const admin = readLocalStorage(USER_ADMIN_STORAGE_KEY);
  return {
    user: readLocalStorage(USER_STORAGE_KEY),
    userId: parseUserId(readLocalStorage(USER_ID_STORAGE_KEY)),
    admin: admin != null ? admin === 'true' : null,
  };
}

export function persistAuthSession(user: string, userId: number | null, admin: boolean): void {
  writeLocalStorage(USER_STORAGE_KEY, user);
  if (userId != null) {
    writeLocalStorage(USER_ID_STORAGE_KEY, String(userId));
  } else {
    removeLocalStorage(USER_ID_STORAGE_KEY);
  }
  writeLocalStorage(USER_ADMIN_STORAGE_KEY, String(admin));
}

export function clearAuthSession(): void {
  removeLocalStorage('token');
  removeLocalStorage(USER_STORAGE_KEY);
  removeLocalStorage(USER_ID_STORAGE_KEY);
  removeLocalStorage(USER_ADMIN_STORAGE_KEY);
}
