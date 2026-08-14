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
  id: string;
  username: string;
  admin: boolean;
  enabled: boolean;
  passwordChangedAt: string;
  createdAt: string;
  updatedAt: string;
}

export async function listStudioUsers() {
  const response = await client.get<{ data: StudioUser[] }>('/studio-users');
  return response.data.data;
}

export async function createStudioUser(request: { username: string; password: string; admin: boolean }) {
  const response = await client.post<{ data: StudioUser }>('/studio-users', request);
  return response.data.data;
}

export async function setStudioUserEnabled(userId: string, enabled: boolean) {
  const response = await client.post<{ data: StudioUser }>(`/studio-users/${userId}/status`, { enabled });
  return response.data.data;
}

export async function resetStudioUserPassword(userId: string, newPassword: string) {
  await client.post(`/studio-users/${userId}/password`, { newPassword });
}
