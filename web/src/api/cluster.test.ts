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
  createK8sCert,
  createNameServer,
  deleteK8sCert,
  deleteNameServer,
  listK8sCerts,
  restartNameServer,
  restartProxy,
  updateK8sCert,
  updateNameServer,
  upgradeNameServer,
} from './cluster';
import type { K8sCertInfo } from './cluster';

const mock = new MockAdapter(client);

const cert: K8sCertInfo = {
  id: 'cert-1',
  name: 'rocketmq-tls',
  namespace: 'rocketmq',
  cluster: 'prod-cluster',
  type: 'TLS',
  issuer: 'kubernetes-ca',
  notBefore: '2026-01-01T00:00:00Z',
  notAfter: '2027-01-01T00:00:00Z',
  status: 'valid',
  daysRemaining: 365,
  san: ['broker.example.com'],
};

describe('K8s certificate API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });
  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('loads and unwraps certificate records', async () => {
    mock.onGet('/k8s-certs').reply(200, { code: 200, message: 'success', data: [cert] });

    await expect(listK8sCerts()).resolves.toEqual([cert]);
  });

  it('returns the certificate created by the backend', async () => {
    mock.onPost('/k8s-certs/create').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ name: cert.name, cluster: cert.cluster });
      return [200, { code: 200, message: 'success', data: cert }];
    });

    await expect(createK8sCert({ name: cert.name, cluster: cert.cluster })).resolves.toEqual(cert);
  });

  it('returns the updated certificate and sends its id', async () => {
    const updated = { ...cert, issuer: 'vault' };
    mock.onPost('/k8s-certs/update').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ id: cert.id, issuer: 'vault' });
      return [200, { code: 200, message: 'success', data: updated }];
    });

    await expect(updateK8sCert({ id: cert.id, issuer: 'vault' })).resolves.toEqual(updated);
  });

  it('sends the certificate id when deleting', async () => {
    mock.onPost('/k8s-certs/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: cert.id });
      return [200, { code: 200, message: 'success', data: null }];
    });

    await expect(deleteK8sCert(cert.id)).resolves.toBeUndefined();
  });

  it('sends NameServer operation payloads to their endpoints', async () => {
    const target = { clusterId: 'cluster-1', addr: '127.0.0.1:9876' };
    const requests = [
      ['/nameservers/restart', target],
      ['/nameservers/upgrade', { ...target, version: '5.4.0' }],
      ['/nameservers/create', target],
      ['/nameservers/update', { ...target, newAddr: '127.0.0.2:9876' }],
      ['/nameservers/delete', target],
    ] as const;
    requests.forEach(([url, body]) => {
      mock.onPost(url).reply((config) => {
        expect(JSON.parse(config.data)).toEqual(body);
        return [200, { code: 200, data: null }];
      });
    });

    await expect(restartNameServer(target)).resolves.toBeUndefined();
    await expect(upgradeNameServer({ ...target, version: '5.4.0' })).resolves.toBeUndefined();
    await expect(createNameServer(target)).resolves.toBeUndefined();
    await expect(updateNameServer({ ...target, newAddr: '127.0.0.2:9876' })).resolves.toBeUndefined();
    await expect(deleteNameServer(target)).resolves.toBeUndefined();
  });

  it('sends the proxy restart target', async () => {
    const target = { clusterId: 'cluster-1', addr: '127.0.0.1:8081' };
    mock.onPost('/proxies/restart').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(target);
      return [200, { code: 200, data: null }];
    });

    await expect(restartProxy(target)).resolves.toBeUndefined();
  });
});
