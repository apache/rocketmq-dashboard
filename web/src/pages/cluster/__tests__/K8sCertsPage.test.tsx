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
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import type { K8sCertInfo } from '../../../api/cluster';
import {
  createK8sCert,
  deleteK8sCert,
  listK8sCerts,
  renewK8sCert,
  updateK8sCert,
} from '../../../services/clusterService';
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
    namespace: 'platform',
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
    vi.mocked(createK8sCert).mockResolvedValue(certs[0]);
    vi.mocked(updateK8sCert).mockResolvedValue(certs[0]);
    vi.mocked(renewK8sCert).mockResolvedValue(certs[0]);
    vi.mocked(deleteK8sCert).mockResolvedValue();
  });

  const renderPage = () =>
    render(
      <App>
        <K8sCertsPage />
      </App>,
    );

  it('displays certificate namespace and SAN metadata', async () => {
    renderPage();

    await screen.findByText('rocketmq-prod-tls');

    expect(screen.getByText('rocketmq')).toBeInTheDocument();
    expect(screen.getByText('platform')).toBeInTheDocument();
    expect(screen.getByText('broker.prod.example.com')).toBeInTheDocument();
    expect(screen.getByText('-')).toBeInTheDocument();
  });

  it('explains that certificate records are Studio-local metadata', async () => {
    renderPage();

    expect(await screen.findByTestId('k8s-cert-local-metadata-notice')).toHaveTextContent(
      '证书记录保存为 Studio 本地元数据',
    );
  });

  it.each([
    ['platform', 'rocketmq-staging-tls', 'rocketmq-prod-tls'],
    ['broker.prod.example.com', 'rocketmq-prod-tls', 'rocketmq-staging-tls'],
  ])('searches certificate metadata by %s', async (query, expected, hidden) => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('rocketmq-prod-tls');
    await user.type(
      screen.getByPlaceholderText('搜索证书名称、集群、命名空间或 SAN'),
      `${query}{enter}`,
    );

    expect(screen.getByText(expected)).toBeInTheDocument();
    expect(screen.queryByText(hidden)).not.toBeInTheDocument();
  });

  it('filters certificates by namespace', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('rocketmq-prod-tls');
    const namespaceFilter = screen.getByRole('combobox', { name: '按命名空间筛选' });
    const selector = namespaceFilter
      .closest('.ant-select')
      ?.querySelector<HTMLElement>('.ant-select-selector');

    expect(selector).not.toBeNull();
    fireEvent.mouseDown(selector!);
    await user.click(
      await screen.findByText('platform', { selector: '.ant-select-item-option-content' }),
    );

    expect(screen.getByText('rocketmq-staging-tls')).toBeInTheDocument();
    expect(screen.queryByText('rocketmq-prod-tls')).not.toBeInTheDocument();
  });

  it('exposes local metadata create, edit, renew and delete operations', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('rocketmq-prod-tls');

    expect(screen.getByRole('button', { name: /添加证书/ })).toBeInTheDocument();
    const renewButtons = screen.getAllByRole('button', { name: /续期/ });
    await user.click(renewButtons[0]);
    await waitFor(() => expect(renewK8sCert).toHaveBeenCalledWith('cert-prod'));

    await user.click(screen.getAllByRole('button', { name: /编辑/ })[0]);
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText('证书名称')).toHaveValue('rocketmq-prod-tls');
  });

  it('trims certificate search text before filtering', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('rocketmq-prod-tls');
    await user.type(
      screen.getByPlaceholderText('搜索证书名称、集群、命名空间或 SAN'),
      '  prod-cluster  {enter}',
    );

    expect(screen.getByText('rocketmq-prod-tls')).toBeInTheDocument();
    expect(screen.queryByText('rocketmq-staging-tls')).not.toBeInTheDocument();
  });
});
