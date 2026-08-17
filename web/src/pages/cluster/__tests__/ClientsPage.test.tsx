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

import { App } from 'antd';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ClientConnection } from '../../../api/connections';
import { LangProvider } from '../../../i18n/LangContext';
import * as connectionsService from '../../../services/connectionsService';
import * as instanceService from '../../../services/instanceService';
import ClientsPage from '../clients';

vi.mock('../../../services/connectionsService', () => ({
  listConnections: vi.fn(),
}));
vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([
    {
      id: 1,
      name: 'instance-1',
      endpoint: 'namesrv-1:9876',
      type: 'DIRECT',
      remark: '',
      topicCount: 0,
      consumerGroupCount: 0,
      gmtCreate: '',
      gmtModified: '',
    },
  ]),
}));

const connection: ClientConnection = {
  clientId: 'order-svc-0@10.0.1.12:49152',
  type: 'Producer',
  groupOrTopic: 'order-create',
  protocol: 'gRPC',
  address: '10.0.1.12:49152',
  language: 'Java',
  version: '5.0.7',
  connectedAt: '2026-07-01 08:30:00',
  clusterName: 'ns-prod',
};

const connections: ClientConnection[] = [
  connection,
  {
    clientId: 'payment-svc-0@10.0.1.13:49153',
    type: 'Consumer',
    groupOrTopic: 'payment-consumer',
    protocol: 'gRPC',
    address: '10.0.1.13:49153',
    language: 'Go',
    version: '2.1.0',
    connectedAt: '2026-07-01 08:31:00',
    clusterName: 'ns-prod',
  },
  {
    clientId: 'audit-svc-0@10.0.2.10:49154',
    type: 'Consumer',
    groupOrTopic: 'audit-consumer',
    protocol: 'Remoting',
    address: '10.0.2.10:49154',
    language: 'Cpp',
    version: '4.9.8',
    connectedAt: '2026-07-01 08:32:00',
    clusterName: 'ns-audit',
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

beforeEach(() => {
  vi.mocked(connectionsService.listConnections).mockResolvedValue([connection]);
});

afterEach(() => {
  vi.clearAllMocks();
});

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );

describe('Clients page', () => {
  it('loads connections for the selected instance', async () => {
    renderWithProviders(<ClientsPage />);

    await screen.findByText('order-svc-0@10.0.1.12:49152');
    expect(connectionsService.listConnections).toHaveBeenCalledWith({ instanceId: 1 });
  });

  it('summarizes connection types, protocols, and language versions', async () => {
    vi.mocked(connectionsService.listConnections).mockResolvedValue(connections);
    renderWithProviders(<ClientsPage />);

    await waitFor(() => {
      expect(within(screen.getByTestId('connection-total')).getByText('3')).toBeInTheDocument();
    });
    expect(within(screen.getByTestId('producer-total')).getByText('1')).toBeInTheDocument();
    expect(within(screen.getByTestId('consumer-total')).getByText('2')).toBeInTheDocument();

    const protocols = screen.getByTestId('protocol-distribution');
    expect(within(protocols).getByText('gRPC: 2')).toBeInTheDocument();
    expect(within(protocols).getByText('Remoting: 1')).toBeInTheDocument();

    const languageVersions = screen.getByTestId('language-version-distribution');
    expect(within(languageVersions).getByText('Java 5.0.7: 1')).toBeInTheDocument();
    expect(within(languageVersions).getByText('Go 2.1.0: 1')).toBeInTheDocument();
    expect(within(languageVersions).getByText('C++ 4.9.8: 1')).toBeInTheDocument();
  });

  it('updates statistics when the selected cluster changes', async () => {
    const user = userEvent.setup();
    vi.mocked(connectionsService.listConnections).mockResolvedValue(connections);
    renderWithProviders(<ClientsPage />);

    await screen.findByText('audit-svc-0@10.0.2.10:49154');
    await user.click(screen.getByRole('combobox', { name: '所属集群' }));
    await user.click(
      await screen.findByText('ns-prod', { selector: '.ant-select-item-option-content' }),
    );

    await waitFor(() => {
      expect(within(screen.getByTestId('connection-total')).getByText('2')).toBeInTheDocument();
    });
    expect(within(screen.getByTestId('producer-total')).getByText('1')).toBeInTheDocument();
    expect(within(screen.getByTestId('consumer-total')).getByText('1')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('protocol-distribution')).getByText('gRPC: 2'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId('protocol-distribution')).queryByText('Remoting: 1'),
    ).toBeNull();
    expect(
      within(screen.getByTestId('language-version-distribution')).getByText('Java 5.0.7: 1'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId('language-version-distribution')).queryByText('C++ 4.9.8: 1'),
    ).toBeNull();
  });

  it('keeps cluster statistics stable when text search narrows the table', async () => {
    const user = userEvent.setup();
    vi.mocked(connectionsService.listConnections).mockResolvedValue(connections);
    renderWithProviders(<ClientsPage />);

    await screen.findByText('audit-svc-0@10.0.2.10:49154');
    await user.type(screen.getByPlaceholderText('搜索 Client ID 或地址'), 'order-svc');

    expect(within(screen.getByTestId('connection-total')).getByText('3')).toBeInTheDocument();
    expect(within(screen.getByTestId('producer-total')).getByText('1')).toBeInTheDocument();
    expect(within(screen.getByTestId('consumer-total')).getByText('2')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('protocol-distribution')).getByText('Remoting: 1'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId('language-version-distribution')).getByText('C++ 4.9.8: 1'),
    ).toBeInTheDocument();
    expect(screen.getByText('order-svc-0@10.0.1.12:49152')).toBeInTheDocument();
    expect(screen.queryByText('audit-svc-0@10.0.2.10:49154')).toBeNull();
  });

  it('renders empty distributions when no connections are available', async () => {
    vi.mocked(connectionsService.listConnections).mockResolvedValue([]);
    renderWithProviders(<ClientsPage />);

    expect(
      within(await screen.findByTestId('connection-total')).getByText('0'),
    ).toBeInTheDocument();
    expect(within(screen.getByTestId('producer-total')).getByText('0')).toBeInTheDocument();
    expect(within(screen.getByTestId('consumer-total')).getByText('0')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('protocol-distribution')).getByText('暂无数据'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId('language-version-distribution')).getByText('暂无数据'),
    ).toBeInTheDocument();
  });

  it('surfaces unavailable provider errors from the client API', async () => {
    vi.mocked(connectionsService.listConnections).mockRejectedValue(
      new Error('Client connection provider is not configured'),
    );
    renderWithProviders(<ClientsPage />);

    expect(
      await screen.findByText('Client connection provider is not configured'),
    ).toBeInTheDocument();
    expect(within(screen.getByTestId('connection-total')).getByText('0')).toBeInTheDocument();
  });

  it('retries the current instance connection query after a runtime failure', async () => {
    vi.mocked(connectionsService.listConnections)
      .mockRejectedValueOnce(new Error('Client connection provider is not configured'))
      .mockResolvedValueOnce([connection]);
    const user = userEvent.setup();
    renderWithProviders(<ClientsPage />);

    expect(
      await screen.findByText('Client connection provider is not configured'),
    ).toBeInTheDocument();
    expect(connectionsService.listConnections).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: /重\s*试/ }));

    expect(await screen.findByText('order-svc-0@10.0.1.12:49152')).toBeInTheDocument();
    expect(connectionsService.listConnections).toHaveBeenCalledTimes(2);
    expect(connectionsService.listConnections).toHaveBeenLastCalledWith({
      instanceId: 1,
    });
  });

  it('clears the previous instance data when the next instance connection request fails', async () => {
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      {
        id: 1,
        name: 'instance-1',
        endpoint: 'namesrv-1:9876',
        type: 'DIRECT',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
      {
        id: 2,
        name: 'instance-2',
        endpoint: 'namesrv-2:9876',
        type: 'DIRECT',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
    ]);
    vi.mocked(connectionsService.listConnections).mockImplementation((query) =>
      query?.instanceId === 1
        ? Promise.resolve([connection])
        : Promise.reject(new Error('Instance 2 is unavailable')),
    );
    const user = userEvent.setup();
    renderWithProviders(<ClientsPage />);

    await screen.findByText('order-svc-0@10.0.1.12:49152');
    await user.click(screen.getByRole('combobox', { name: 'Instance' }));
    await user.click(
      await screen.findByText('instance-2', { selector: '.ant-select-item-option-content' }),
    );

    expect(await screen.findByText('Instance 2 is unavailable')).toBeInTheDocument();
    expect(screen.queryByText('order-svc-0@10.0.1.12:49152')).not.toBeInTheDocument();
    expect(within(screen.getByTestId('connection-total')).getByText('0')).toBeInTheDocument();
  });

  it('surfaces instance discovery failures and allows retrying', async () => {
    vi.mocked(instanceService.listInstances)
      .mockRejectedValueOnce(new Error('Unable to load managed instances'))
      .mockResolvedValueOnce([
        {
          id: 1,
          name: 'instance-1',
          endpoint: 'namesrv-1:9876',
          type: 'DIRECT',
          remark: '',
          topicCount: 0,
          consumerGroupCount: 0,
          gmtCreate: '',
          gmtModified: '',
        },
      ]);
    const user = userEvent.setup();
    renderWithProviders(<ClientsPage />);

    expect(await screen.findByText('Unable to load managed instances')).toBeInTheDocument();
    expect(connectionsService.listConnections).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /重\s*试/ }));
    await screen.findByText('order-svc-0@10.0.1.12:49152');
    expect(connectionsService.listConnections).toHaveBeenCalledWith({ instanceId: 1 });
  });

  it('opens a client detail dialog from the connection table', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ClientsPage />);

    const row = await screen.findByRole('row', { name: /order-svc-0@10\.0\.1\.12:49152/ });
    await user.click(within(row).getByRole('button', { name: /详情/ }));

    const dialog = await screen.findByRole('dialog', {
      name: /客户端详情 - order-svc-0@10\.0\.1\.12:49152/,
    });
    expect(within(dialog).getAllByText('order-svc-0@10.0.1.12:49152')).toHaveLength(1);
    expect(within(dialog).getByText('ns-prod')).toBeInTheDocument();
    expect(within(dialog).getByText('Producer')).toBeInTheDocument();
    expect(within(dialog).getByText('order-create')).toBeInTheDocument();
    expect(within(dialog).getByText('gRPC')).toBeInTheDocument();
    expect(within(dialog).getByText('10.0.1.12:49152')).toBeInTheDocument();
    expect(within(dialog).getByText('Java')).toBeInTheDocument();
    expect(within(dialog).getByText('5.0.7')).toBeInTheDocument();
    expect(within(dialog).getByText('2026-07-01 08:30:00')).toBeInTheDocument();
  });
});
