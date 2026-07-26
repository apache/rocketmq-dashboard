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

import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import type { K8sCertInfo } from '../../../api/cluster';
import { listK8sCerts } from '../../../services/clusterService';
import K8sCertsPage from '../certs';

vi.mock('../../../services/clusterService', () => ({
  createK8sCert: vi.fn(),
  deleteK8sCert: vi.fn(),
  listK8sCerts: vi.fn(),
  renewK8sCert: vi.fn(),
  updateK8sCert: vi.fn(),
}));

const certs: K8sCertInfo[] = [
  {
    id: 'cert-prod',
    name: 'rocketmq-prod-tls',
    namespace: 'rocketmq',
    cluster: 'prod-cluster',
    type: 'TLS',
    issuer: 'kubernetes-ca',
    notBefore: '2026-01-01T00:00:00Z',
    notAfter: '2027-01-01T00:00:00Z',
    status: 'valid',
    daysRemaining: 365,
    san: ['broker.prod.example.com'],
  },
  {
    id: 'cert-staging',
    name: 'rocketmq-staging-tls',
    namespace: 'rocketmq',
    cluster: 'staging-cluster',
    type: 'TLS',
    issuer: 'kubernetes-ca',
    notBefore: '2026-01-01T00:00:00Z',
    notAfter: '2027-01-01T00:00:00Z',
    status: 'valid',
    daysRemaining: 365,
    san: ['broker.staging.example.com'],
  },
];

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

describe('K8sCertsPage', () => {
  beforeEach(() => {
    vi.mocked(listK8sCerts).mockResolvedValue(certs);
  });

  it('trims certificate search text before filtering', async () => {
    const user = userEvent.setup();
    render(
      <App>
        <K8sCertsPage />
      </App>,
    );

    await screen.findByText('rocketmq-prod-tls');
    await user.type(screen.getByPlaceholderText('搜索证书名称或集群'), '  prod-cluster  {enter}');

    expect(screen.getByText('rocketmq-prod-tls')).toBeInTheDocument();
    expect(screen.queryByText('rocketmq-staging-tls')).not.toBeInTheDocument();
  });
});
