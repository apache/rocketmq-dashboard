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

import { isMockMode } from './dataMode';
import * as cloudCredentialApi from '../api/cloudCredential';
import type {
  CloudCredential,
  CreateCloudCredentialRequest,
  UpdateCloudCredentialRequest,
} from '../api/cloudCredential';
import { mockCloudCredentials } from '../mock/cloudCredentials';

let mockCredentialStore = mockCloudCredentials.map(copyCredential);

function copyCredential(credential: CloudCredential): CloudCredential {
  return { ...credential };
}

function nowText(): string {
  return new Date().toISOString().replace('T', ' ').slice(0, 19);
}

function maskAccessKey(accessKey: string): string {
  if (accessKey.length <= 8) return '****';
  return `${accessKey.slice(0, 4)}****${accessKey.slice(-4)}`;
}

export async function listCloudCredentials(): Promise<CloudCredential[]> {
  if (isMockMode()) return mockCredentialStore.map(copyCredential);
  return cloudCredentialApi.listCloudCredentials();
}

export async function createCloudCredential(
  data: CreateCloudCredentialRequest,
): Promise<CloudCredential> {
  if (!isMockMode()) return cloudCredentialApi.createCloudCredential(data);

  const timestamp = nowText();
  const credential: CloudCredential = {
    id: `cred-${Date.now()}`,
    name: data.name,
    vendor: data.vendor,
    accessKey: maskAccessKey(data.accessKey),
    remark: data.remark,
    createdAt: timestamp,
    updatedAt: timestamp,
  };
  mockCredentialStore = [...mockCredentialStore, credential];
  return copyCredential(credential);
}

export async function updateCloudCredential(
  data: UpdateCloudCredentialRequest,
): Promise<CloudCredential> {
  if (!isMockMode()) return cloudCredentialApi.updateCloudCredential(data);

  const index = mockCredentialStore.findIndex((credential) => credential.id === data.id);
  if (index < 0) throw new Error(`Cloud credential not found: ${data.id}`);

  const updated: CloudCredential = {
    ...mockCredentialStore[index],
    ...(data.name !== undefined ? { name: data.name } : {}),
    ...(data.remark !== undefined ? { remark: data.remark } : {}),
    updatedAt: nowText(),
  };
  mockCredentialStore = mockCredentialStore.map((credential) =>
    credential.id === data.id ? updated : credential,
  );
  return copyCredential(updated);
}

export async function deleteCloudCredential(id: string): Promise<void> {
  if (!isMockMode()) return cloudCredentialApi.deleteCloudCredential(id);

  const nextStore = mockCredentialStore.filter((credential) => credential.id !== id);
  if (nextStore.length === mockCredentialStore.length) {
    throw new Error(`Cloud credential not found: ${id}`);
  }
  mockCredentialStore = nextStore;
}
