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

import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import {
  queryNamespaceList,
  queryNamespaceDetail,
  createNamespace,
  updateNamespace,
  deleteNamespace,
  queryNamespaceCapability,
  type NamespaceItem,
} from './namespace';

const mock = new MockAdapter(client);

const sampleNs: NamespaceItem = {
  namespaceName: 'production',
  displayName: 'Production',
  description: 'Production namespace',
  clusterName: 'prod-cluster',
  status: 'active',
  defaultNamespace: false,
  createTime: Date.now(),
  quotaConfig: { maxTopicCount: 500, maxConsumerGroupCount: 200 },
};

describe('Namespace API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('queries namespace list', async () => {
    mock.onGet('/namespace/list').reply(200, { code: 200, data: [sampleNs] });

    const result = await queryNamespaceList();
    expect(result).toHaveLength(1);
    expect(result[0].namespaceName).toBe('production');
  });

  it('queries namespace detail by name', async () => {
    mock.onGet('/namespace/production').reply(200, { code: 200, data: sampleNs });

    const result = await queryNamespaceDetail('production');
    expect(result.namespaceName).toBe('production');
    expect(result.quotaConfig?.maxTopicCount).toBe(500);
  });

  it('creates a namespace', async () => {
    mock.onPost('/namespace/create').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.namespaceName).toBe('staging');
      expect(body.displayName).toBe('Staging');
      return [200, { code: 200 }];
    });

    await createNamespace({ namespaceName: 'staging', displayName: 'Staging' });
  });

  it('updates a namespace', async () => {
    mock.onPut('/namespace/update').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.namespaceName).toBe('production');
      expect(body.description).toBe('Updated description');
      return [200, { code: 200 }];
    });

    await updateNamespace({ namespaceName: 'production', description: 'Updated description' });
  });

  it('deletes a namespace by name', async () => {
    mock.onDelete('/namespace/staging').reply(200, { code: 200 });

    await deleteNamespace('staging');
  });

  it('queries namespace capability as supported', async () => {
    mock
      .onGet('/namespace/capability')
      .reply(200, { code: 200, data: { namespaceSupported: true } });

    const result = await queryNamespaceCapability();
    expect(result.namespaceSupported).toBe(true);
  });

  it('queries namespace capability as unsupported', async () => {
    mock
      .onGet('/namespace/capability')
      .reply(200, { code: 200, data: { namespaceSupported: false } });

    const result = await queryNamespaceCapability();
    expect(result.namespaceSupported).toBe(false);
  });
});
