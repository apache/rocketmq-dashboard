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
  getBrokerConfigDiff,
  getNameServerConfigDiff,
  getCluster,
  listK8sCerts,
  previewClusterConfig,
  renewK8sCert,
  restartBroker,
  restartNameServer,
  restartProxy,
  updateClusterConfig,
  updateK8sCert,
  updateNameServer,
  upgradeNameServer,
} from './cluster';
import type { K8sCertInfo } from './cluster';

const mock = new MockAdapter(client);

const cert: K8sCertInfo = {
  id: 5,
  k8sId: 'rocketmq-tls',
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
      expect(JSON.parse(config.data)).toMatchObject({ k8sId: cert.k8sId, cluster: cert.cluster });
      return [200, { code: 200, message: 'success', data: cert }];
    });

    await expect(createK8sCert({ k8sId: cert.k8sId, cluster: cert.cluster })).resolves.toEqual(
      cert,
    );
  });

  it('returns the updated certificate and sends its id', async () => {
    const updated = { ...cert, issuer: 'vault' };
    mock.onPost('/k8s-certs/update').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ id: cert.id, issuer: 'vault' });
      return [200, { code: 200, message: 'success', data: updated }];
    });

    await expect(updateK8sCert({ id: cert.id, issuer: 'vault' })).resolves.toEqual(updated);
  });

  it('renews a certificate using replacement certificate material', async () => {
    const renewed = { ...cert, daysRemaining: 365, status: 'valid' };
    mock.onPost('/k8s-certs/renew').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        id: cert.id,
        certPem: '-----BEGIN CERTIFICATE-----...',
        keyPem: '-----BEGIN PRIVATE KEY-----...',
      });
      return [200, { code: 200, message: 'success', data: renewed }];
    });

    await expect(
      renewK8sCert({
        id: cert.id,
        certPem: '-----BEGIN CERTIFICATE-----...',
        keyPem: '-----BEGIN PRIVATE KEY-----...',
      }),
    ).resolves.toEqual(renewed);
  });

  it('sends the certificate id when deleting', async () => {
    mock.onPost('/k8s-certs/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: cert.id });
      return [200, { code: 200, message: 'success', data: null }];
    });

    await expect(deleteK8sCert(cert.id)).resolves.toBeUndefined();
  });

  it('encodes cluster path parameters', async () => {
    const cluster = {
      id: 'cloud/prod cluster:1',
      name: 'prod',
    };
    mock.onGet('/clusters/cloud%2Fprod%20cluster%3A1').reply(200, {
      code: 200,
      data: cluster,
    });

    await expect(getCluster(cluster.id)).resolves.toEqual(cluster);
  });

  it('encodes broker restart path parameters', async () => {
    mock
      .onPost('/clusters/cloud%2Fprod%20cluster%3A1/brokers/broker%2Fmain%3A10911/restart')
      .reply(200, {
        code: 200,
        data: { success: true, message: 'restarted' },
      });

    await expect(restartBroker('cloud/prod cluster:1', 'broker/main:10911')).resolves.toEqual({
      success: true,
      message: 'restarted',
    });
  });

  it('returns per-broker cluster config update results', async () => {
    const result = {
      cluster: { id: 'cluster-1' },
      status: 'PARTIAL',
      successfulBrokers: ['10.0.0.1:10911'],
      failedBrokers: [{ address: '10.0.0.2:10911', message: 'broker unavailable' }],
    };
    mock.onPost('/clusters/config/update').reply(200, { code: 200, data: result });

    await expect(updateClusterConfig({ id: 'cluster-1', writeQueueNums: 16 })).resolves.toEqual(
      result,
    );
  });

  it('previews effective broker config update changes', async () => {
    const result = {
      cluster: { id: 'cluster-1' },
      currentConfig: { writeQueueNums: 8, readQueueNums: 8 },
      proposedConfig: { writeQueueNums: 16, readQueueNums: 16 },
      targetBrokers: [{ name: 'broker-0', address: '10.0.0.1:10911' }],
      brokerProperties: { defaultTopicQueueNums: '16' },
      changes: [
        {
          field: 'writeQueueNums',
          currentValue: '8',
          proposedValue: '16',
          brokerProperty: 'defaultTopicQueueNums',
        },
      ],
      changed: true,
    };
    mock.onPost('/clusters/config/preview').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ id: 'cluster-1', writeQueueNums: 16 });
      return [200, { code: 200, data: result }];
    });

    await expect(previewClusterConfig({ id: 'cluster-1', writeQueueNums: 16 })).resolves.toEqual(
      result,
    );
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
    await expect(
      updateNameServer({ ...target, newAddr: '127.0.0.2:9876' }),
    ).resolves.toBeUndefined();
    await expect(deleteNameServer(target)).resolves.toBeUndefined();
  });

  it('loads NameServer configuration drift for the selected cluster', async () => {
    const result = {
      cluster: 'cluster-1',
      complete: true,
      driftDetected: true,
      nodeCount: 2,
      reachableNodeCount: 2,
      comparedKeys: ['listenPort'],
      nodes: [
        { address: 'ns-a:9876', reachable: true },
        { address: 'ns-b:9876', reachable: true },
      ],
      differences: [
        {
          key: 'listenPort',
          values: [
            { address: 'ns-a:9876', configured: true, value: '9876' },
            { address: 'ns-b:9876', configured: true, value: '19876' },
          ],
        },
      ],
    };
    mock
      .onGet('/nameservers/config-diff', {
        params: { clusterId: 'cluster-1', instanceId: 'instance-proxy-1' },
      })
      .reply(200, {
        code: 200,
        data: result,
      });

    await expect(getNameServerConfigDiff('cluster-1', 'instance-proxy-1')).resolves.toEqual(result);
  });

  it('loads broker configuration drift for the selected cluster', async () => {
    const result = {
      cluster: 'cluster/prod:1',
      complete: true,
      driftDetected: true,
      brokerCount: 2,
      reachableBrokerCount: 2,
      comparedFields: ['flushDiskType', 'writeQueueNums'],
      brokers: [
        { name: 'broker-a', address: '10.0.0.1:10911', reachable: true },
        { name: 'broker-b', address: '10.0.0.2:10911', reachable: true },
      ],
      differences: [
        {
          field: 'writeQueueNums',
          brokerProperty: 'defaultTopicQueueNums',
          values: [
            { brokerName: 'broker-a', address: '10.0.0.1:10911', configured: true, value: '8' },
            { brokerName: 'broker-b', address: '10.0.0.2:10911', configured: true, value: '16' },
          ],
        },
      ],
    };
    mock
      .onGet('/clusters/cluster%2Fprod%3A1/broker-config-diff', {
        params: { instanceId: 'instance-proxy-1' },
      })
      .reply(200, {
        code: 200,
        data: result,
      });

    await expect(getBrokerConfigDiff('cluster/prod:1', 'instance-proxy-1')).resolves.toEqual(
      result,
    );
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
