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

import client from './client';

// ─── Types ──────────────────────────────────────────────────────
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  username: string;
  type: string; // e.g., "ADMIN", "ORDINARY"
  contextPath: string;
  token?: string; // JWT token (optional, for token-based auth)
  role?: string; // User role (optional, derived from type)
}

// ─── Auth API ───────────────────────────────────────────────────
// Backend: LoginController at /login
// POST /login/login.do    → login (session-based, sets cookie)
// POST /login/logout.do   → logout
// GET  /login/check.query → check if logged in

export async function login(username: string, password: string) {
  const res = await client.post<LoginResponse>('/login/login.do', { username, password });
  return res.data;
}

export async function logout() {
  await client.post('/login/logout.do');
}

export async function checkLogin() {
  const res = await client.get<{ logined: boolean; loginRequired: boolean }>('/login/check.query');
  return res.data;
}
