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
  createDataSource,
  deleteDataSource,
  getSslSettings,
  listDataSources,
  saveSslSettings,
  testDataSource,
  updateDataSource,
  validateSslSettings,
} from './settings';
import type { DataSource, SslSettings } from './settings';

const mock = new MockAdapter(client);
const source: DataSource = {
  key: 'source-1',
  name: 'Prometheus',
  type: 'Prometheus',
  url: 'http://prometheus:9090',
  auth: 'None',
  status: 'healthy',
};

const sslSettings: SslSettings = {
  enabled: true,
  protocol: 'TLSv1.3',
  clientAuth: 'none',
  keyStoreType: 'PKCS12',
  keyStorePath: '/etc/rocketmq/server.p12',
  keyStorePasswordConfigured: true,
  trustStoreType: 'PKCS12',
  trustStorePath: '',
  trustStorePasswordConfigured: false,
  restartRequired: true,
};

describe('data sources API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('loads and returns created or updated data sources', async () => {
    mock.onGet('/settings/datasources').reply(200, { code: 200, data: [source] });
    mock.onPost('/settings/datasources/create').reply(200, { code: 200, data: source });
    mock.onPost('/settings/datasources/update').reply(200, { code: 200, data: source });

    await expect(listDataSources()).resolves.toEqual([source]);
    await expect(createDataSource({ name: source.name })).resolves.toEqual(source);
    await expect(updateDataSource(source)).resolves.toEqual(source);
  });

  it('uses a key query parameter for deletion and sends test auth details', async () => {
    mock.onPost('/settings/datasources/delete').reply((config) => {
      expect(config.params).toEqual({ key: source.key });
      return [200, { code: 200, data: null }];
    });
    mock.onPost('/settings/datasources/test').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        type: source.type,
        url: source.url,
        auth: source.auth,
      });
      return [200, { code: 200, data: { success: true, message: 'Connection successful' } }];
    });

    await expect(deleteDataSource(source.key)).resolves.toBeUndefined();
    await expect(
      testDataSource({ type: source.type, url: source.url, auth: source.auth }),
    ).resolves.toEqual({ success: true, message: 'Connection successful' });
  });
});

describe('SSL settings API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('loads SSL settings and strips blank passwords before saving', async () => {
    mock.onGet('/settings/ssl').reply(200, { code: 200, data: sslSettings });
    mock.onPost('/settings/ssl/save').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        enabled: true,
        protocol: 'TLSv1.3',
        clientAuth: 'none',
        keyStoreType: 'PKCS12',
        keyStorePath: '/etc/rocketmq/server.p12',
        trustStoreType: 'PKCS12',
        trustStorePath: '',
      });
      return [200, { code: 200, data: sslSettings }];
    });

    await expect(getSslSettings()).resolves.toEqual(sslSettings);
    await expect(
      saveSslSettings({
        enabled: true,
        protocol: 'TLSv1.3',
        clientAuth: 'none',
        keyStoreType: 'PKCS12',
        keyStorePath: '/etc/rocketmq/server.p12',
        keyStorePassword: ' ',
        trustStoreType: 'PKCS12',
        trustStorePath: '',
      }),
    ).resolves.toEqual(sslSettings);
  });

  it('keeps explicit SSL password clear flags when saving', async () => {
    mock.onPost('/settings/ssl/save').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        enabled: true,
        protocol: 'TLSv1.3',
        clientAuth: 'none',
        keyStoreType: 'PKCS12',
        keyStorePath: '/etc/rocketmq/server.p12',
        clearKeyStorePassword: true,
        trustStoreType: 'PKCS12',
        trustStorePath: '',
      });
      return [200, { code: 200, data: { ...sslSettings, keyStorePasswordConfigured: false } }];
    });

    await expect(
      saveSslSettings({
        enabled: true,
        protocol: 'TLSv1.3',
        clientAuth: 'none',
        keyStoreType: 'PKCS12',
        keyStorePath: '/etc/rocketmq/server.p12',
        keyStorePassword: ' ',
        clearKeyStorePassword: true,
        trustStoreType: 'PKCS12',
        trustStorePath: '',
      }),
    ).resolves.toMatchObject({ keyStorePasswordConfigured: false });
  });

  it('validates SSL settings through the backend validation endpoint', async () => {
    mock.onPost('/settings/ssl/validate').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({
        enabled: true,
        keyStorePath: '/etc/rocketmq/server.p12',
      });
      return [
        200,
        {
          code: 200,
          data: {
            success: true,
            message: 'SSL/TLS keystore settings are valid',
            warnings: [],
          },
        },
      ];
    });

    await expect(
      validateSslSettings({
        enabled: true,
        protocol: 'TLSv1.3',
        clientAuth: 'none',
        keyStoreType: 'PKCS12',
        keyStorePath: '/etc/rocketmq/server.p12',
        trustStoreType: 'PKCS12',
        trustStorePath: '',
      }),
    ).resolves.toEqual({
      success: true,
      message: 'SSL/TLS keystore settings are valid',
      warnings: [],
    });
  });
});
