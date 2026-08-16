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
  listK8sCerts: vi.fn(),
}));

const certs: K8sCertInfo[] = [
  {
    id: 1,
    name: 'rocketmq-prod-tls',
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
    id: 2,
    name: 'rocketmq-staging-tls',
    cluster: 'staging-cluster',
    type: 'TLS',
    issuer: 'kubernetes-ca',
    notBefore: '2026-01-01T00:00:00Z',
    notAfter: '2027-01-01T00:00:00Z',
    status: 'valid',
    daysRemaining: 365,
    // The backend returns null when SAN is omitted.
    san: null as unknown as string[],
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
    vi.clearAllMocks();
    vi.mocked(listK8sCerts).mockImplementation(async (query = {}) => {
      const search = query.search?.trim().toLowerCase();
      const items = certs.filter(
        (cert) =>
          (!search ||
            [cert.name, cert.cluster, ...(cert.san ?? [])].some((value) =>
              value.toLowerCase().includes(search),
            )) &&
          (!query.cluster || cert.cluster === query.cluster) &&
          (!query.type || cert.type === query.type) &&
          (!query.status || cert.status === query.status),
      );
      return { items, total: items.length, page: query.page ?? 1, size: query.pageSize ?? 20 };
    });
  });

  const renderPage = () =>
    render(
      <App>
        <K8sCertsPage />
      </App>,
    );

  it('displays certificate SAN metadata', async () => {
    renderPage();

    await screen.findByText('rocketmq-prod-tls');

    expect(screen.getByText('broker.prod.example.com')).toBeInTheDocument();
    expect(screen.getByText('-')).toBeInTheDocument();
  });

  it('explains that certificate records are Studio-local metadata', async () => {
    renderPage();

    expect(await screen.findByTestId('k8s-cert-local-metadata-notice')).toHaveTextContent(
      '当前证书记录仅保存为 Studio 本地元数据',
    );
  });

  it.each([
    ['staging-cluster', 'rocketmq-staging-tls', 'rocketmq-prod-tls'],
    ['broker.prod.example.com', 'rocketmq-prod-tls', 'rocketmq-staging-tls'],
  ])('searches certificate metadata by %s', async (query, expected, hidden) => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('rocketmq-prod-tls');
    await user.type(
      screen.getByPlaceholderText('搜索证书名称、集群或 SAN'),
      `${query}{enter}`,
    );

    expect(screen.getByText(expected)).toBeInTheDocument();
    expect(screen.queryByText(hidden)).not.toBeInTheDocument();
  });

  it('does not expose local metadata mutations as Kubernetes certificate operations', async () => {
    renderPage();

    await screen.findByText('rocketmq-prod-tls');

    expect(screen.queryByRole('button', { name: '添加证书' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '续期' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '删除' })).not.toBeInTheDocument();
  });

  it('trims certificate search text before filtering', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('rocketmq-prod-tls');
    await user.type(
      screen.getByPlaceholderText('搜索证书名称、集群或 SAN'),
      '  prod-cluster  {enter}',
    );

    expect(screen.getByText('rocketmq-prod-tls')).toBeInTheDocument();
    expect(screen.queryByText('rocketmq-staging-tls')).not.toBeInTheDocument();
  });
});
