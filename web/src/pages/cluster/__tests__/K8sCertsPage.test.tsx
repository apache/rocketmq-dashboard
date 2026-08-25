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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import type { K8sCertInfo } from '../../../api/cluster';
import { listK8sCerts, createK8sCert, deleteK8sCert } from '../../../services/clusterService';
import K8sCertsPage from '../certs';

vi.mock('../../../services/clusterService', () => ({
  listK8sCerts: vi.fn(),
  createK8sCert: vi.fn(),
  deleteK8sCert: vi.fn(),
}));

const certs: K8sCertInfo[] = [
  {
    id: 1,
    k8sId: 'rocketmq-prod-tls',
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
    id: 3,
    k8sId: 'metadata-only-tls',
    cluster: 'legacy-cluster',
    type: null,
    issuer: null,
    notBefore: null,
    notAfter: null,
    status: null,
    daysRemaining: 0,
    san: null,
  },
  {
    id: 2,
    k8sId: 'rocketmq-staging-tls',
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
    vi.mocked(listK8sCerts).mockResolvedValue(certs);
  });

  const renderPage = () =>
    render(
      <App>
        <K8sCertsPage />
      </App>,
    );

  it('displays certificate metadata without SAN or namespace columns', async () => {
    renderPage();

    await screen.findByText('rocketmq-prod-tls');

    expect(screen.getByText('prod-cluster')).toBeInTheDocument();
    expect(screen.queryByText('broker.prod.example.com')).not.toBeInTheDocument();
    expect(screen.queryByText('命名空间')).not.toBeInTheDocument();
    expect(screen.queryByText('SAN')).not.toBeInTheDocument();
  });

  it('renders and sorts incomplete certificate metadata safely', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('metadata-only-tls');
    await user.click(screen.getByRole('columnheader', { name: /签发者/ }));
    await user.click(screen.getByRole('columnheader', { name: /到期时间/ }));

    expect(screen.getByText('metadata-only-tls')).toBeInTheDocument();
    expect(screen.getAllByText('-').length).toBeGreaterThan(0);
  });

  it('explains that certificate records are Studio-local metadata', async () => {
    renderPage();

    expect(await screen.findByTestId('k8s-cert-local-metadata-notice')).toHaveTextContent(
      '当前证书记录仅保存为 Studio 本地元数据',
    );
  });

  it.each([
    ['staging-cluster', 'rocketmq-staging-tls', 'rocketmq-prod-tls'],
    ['prod-tls', 'rocketmq-prod-tls', 'rocketmq-staging-tls'],
  ])('searches certificate metadata by %s', async (query, expected, hidden) => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('rocketmq-prod-tls');
    await user.type(screen.getByPlaceholderText('搜索 k8s ID 或集群'), `${query}{enter}`);

    expect(screen.getByText(expected)).toBeInTheDocument();
    expect(screen.queryByText(hidden)).not.toBeInTheDocument();
  });

  it('creates a certificate with PEM content through the modal', async () => {
    vi.mocked(createK8sCert).mockResolvedValue({
      ...certs[0],
      id: 3,
      k8sId: 'kubernetes-admin-client',
      cluster: 'kubernetes',
    });
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('rocketmq-prod-tls');
    await user.click(screen.getByRole('button', { name: /新增证书/ }));

    await user.type(
      screen.getByPlaceholderText('例如：kubernetes-daily'),
      'kubernetes-admin-client',
    );
    await user.type(
      screen.getByPlaceholderText('例如：kubernetes（120.26.99.191:6443）'),
      'kubernetes',
    );
    await user.click(screen.getByRole('button', { name: /添\s*加/ }));

    await waitFor(() =>
      expect(createK8sCert).toHaveBeenCalledWith(
        expect.objectContaining({
          k8sId: 'kubernetes-admin-client',
          cluster: 'kubernetes',
          type: 'TLS',
        }),
      ),
    );
    expect(await screen.findByText('kubernetes-admin-client')).toBeInTheDocument();
  });

  it('deletes a certificate after confirmation', async () => {
    vi.mocked(deleteK8sCert).mockResolvedValue();
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('rocketmq-prod-tls');
    const deleteButtons = screen.getAllByRole('button', { name: /删\s*除/ });
    await user.click(deleteButtons[0]);
    const confirmButtons = await screen.findAllByRole('button', { name: /删\s*除/ });
    await user.click(confirmButtons[confirmButtons.length - 1]);

    await waitFor(() => expect(deleteK8sCert).toHaveBeenCalledWith(1));
    await waitFor(() => expect(screen.queryByText('rocketmq-prod-tls')).not.toBeInTheDocument());
  });
});
