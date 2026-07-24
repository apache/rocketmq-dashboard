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

export const TOKEN_STORAGE_KEY = 'token';
export const USER_STORAGE_KEY = 'rocketmq-studio-user';

export interface AuthSession {
  token: string | null;
  user: string | null;
}

export function readAuthSession(): AuthSession {
  try {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    return {
      token,
      user: token ? localStorage.getItem(USER_STORAGE_KEY) : null,
    };
  } catch {
    return { token: null, user: null };
  }
}

export function persistAuthSession(token: string, user: string): void {
  try {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
    localStorage.setItem(USER_STORAGE_KEY, user);
  } catch {
    // The in-memory store remains usable when browser storage is unavailable.
  }
}

export function clearAuthSession(): void {
  try {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
  } catch {
    // The caller still clears the in-memory store.
  }
}

export function handleUnauthorized(
  navigate: (url: string) => void = (url) => window.location.assign(url),
): void {
  clearAuthSession();
  try {
    navigate('/');
  } catch {
    // Authentication cleanup must not be replaced by a routing failure.
  }
}
