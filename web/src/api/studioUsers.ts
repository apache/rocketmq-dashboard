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

export interface StudioUser {
  id: number;
  username: string;
  admin: boolean;
  enabled: boolean;
  passwordChangedAt: string;
  gmtCreate: string;
  gmtModified: string;
}

export interface StudioUserPage {
  items: StudioUser[];
  total: number;
  page: number;
  size: number;
}

export interface StudioUserQuery {
  search?: string;
  admin?: boolean;
  enabled?: boolean;
  page?: number;
  pageSize?: number;
}

export async function listStudioUsers(query: StudioUserQuery = {}) {
  const response = await client.get<{ data: StudioUserPage }>('/studio-users', {
    params: query,
  });
  return response.data.data;
}

export async function createStudioUser(request: { username: string; password: string; admin: boolean }) {
  const response = await client.post<{ data: StudioUser }>('/studio-users', request);
  return response.data.data;
}

export async function setStudioUserEnabled(userId: number, enabled: boolean) {
  const response = await client.post<{ data: StudioUser }>(`/studio-users/${userId}/status`, { enabled });
  return response.data.data;
}

export async function resetStudioUserPassword(userId: number, newPassword: string) {
  await client.post(`/studio-users/${userId}/password`, { newPassword });
}
