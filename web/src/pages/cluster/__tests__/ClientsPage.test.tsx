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
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ClientConnection } from '../../../api/connections';
import type { ClusterInfo } from '../../../api/cluster';
import { LangProvider } from '../../../i18n/LangContext';
import * as connectionsService from '../../../services/connectionsService';
import * as clusterService from '../../../services/clusterService';
import ClientsPage from '../clients';

vi.mock('../../../services/connectionsService', () => ({
  listConnections: vi.fn(),
}));
vi.mock('../../../services/clusterService', () => ({
  listRegistryClusters: vi.fn(),
}));

const registryClusters: ClusterInfo[] = [
  {
    id: 'ns-prod',
    name: 'rocketmq1',
    nsClusterName: 'ns-prod',
    type: 'V4_DIRECT',
    endpoint: 'namesrv-1:9876',
    status: 'healthy',
    version: '5.5.0',
    brokers: [],
    proxies: [],
    nameServers: [],
    config: {} as ClusterInfo['config'],
    topicCount: 0,
    groupCount: 0,
    tpsHistory: [],
  },
  {
    id: 'ns-audit',
    name: 'rocketmq2',
    nsClusterName: 'ns-audit',
    type: 'V4_DIRECT',
    endpoint: 'namesrv-2:9876',
    status: 'healthy',
    version: '5.5.0',
    brokers: [],
    proxies: [],
    nameServers: [],
    config: {} as ClusterInfo['config'],
    topicCount: 0,
    groupCount: 0,
    tpsHistory: [],
  },
];

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
  vi.mocked(clusterService.listRegistryClusters).mockResolvedValue(registryClusters);
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

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

describe('Clients page', () => {
  it('returns to the first page when the connection search changes', async () => {
    const pagedConnections = Array.from({ length: 21 }, (_, index) => ({
      ...connection,
      clientId: `client-${String(index).padStart(2, '0')}`,
      address: `10.0.1.${index + 1}:49152`,
    }));
    vi.mocked(connectionsService.listConnections).mockResolvedValue(pagedConnections);
    const user = userEvent.setup();
    const { container } = renderWithProviders(<ClientsPage />);

    await screen.findByText('client-00');
    await user.click(container.querySelector('.ant-pagination-next button')!);
    expect(await screen.findByText('client-20')).toBeInTheDocument();
    expect(screen.queryByText('client-00')).not.toBeInTheDocument();

    await user.type(screen.getByPlaceholderText('搜索 Client ID 或地址'), 'client-00');
    expect(await screen.findByText('client-00')).toBeInTheDocument();
  });

  it('loads connections for the first online broker cluster', async () => {
    renderWithProviders(<ClientsPage />);

    await screen.findByText('order-svc-0@10.0.1.12:49152');
    expect(connectionsService.listConnections).toHaveBeenCalledWith({
      namesrvAddr: 'namesrv-1:9876',
    });
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

  it('updates statistics when the selected cluster filter changes', async () => {
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

  it('keeps incomplete client metadata searchable by address', async () => {
    const user = userEvent.setup();
    vi.mocked(connectionsService.listConnections).mockResolvedValue([
      {
        ...connection,
        clientId: null,
        address: '10.0.1.99:49152',
        partial: true,
      } as unknown as ClientConnection,
    ]);
    renderWithProviders(<ClientsPage />);

    await screen.findByText('10.0.1.99:49152');
    await user.type(screen.getByPlaceholderText('搜索 Client ID 或地址'), '10.0.1.99');

    expect(screen.getByText('10.0.1.99:49152')).toBeInTheDocument();
  });

  it('exports the currently filtered client connections as CSV', async () => {
    const createObjectURL = vi.fn((blob: Blob | MediaSource) => {
      expect(blob).toBeInstanceOf(Blob);
      return 'blob:client-connections';
    });
    const revokeObjectURL = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {
      writable: true,
      value: createObjectURL,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      writable: true,
      value: revokeObjectURL,
    });
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const user = userEvent.setup();
    vi.mocked(connectionsService.listConnections).mockResolvedValue([
      {
        ...connection,
        clientId: '=risky-client',
        groupOrTopic: 'order-create',
      },
      connections[2],
    ]);
    renderWithProviders(<ClientsPage />);

    await screen.findByText('=risky-client');
    await user.type(screen.getByPlaceholderText('搜索 Client ID 或地址'), 'risky');
    await user.click(screen.getByRole('button', { name: /导出/ }));

    expect(clickSpy).toHaveBeenCalledTimes(1);
    const blob = createObjectURL.mock.calls[0][0] as Blob;
    await expect(blob.text()).resolves.toBe(
      [
        '"Cluster","Client ID","Type","Group/Topic","Protocol","Address","Language","Version","Connected At","Partial"',
        '"ns-prod","\'=risky-client","Producer","order-create","gRPC","10.0.1.12:49152","Java","5.0.7","2026-07-01 08:30:00","false"',
      ].join('\n'),
    );
    expect(
      document.querySelector('a[download^="rocketmq-client-connections-"]'),
    ).not.toBeInTheDocument();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:client-connections');
  });

  it('applies table column filters to the exported CSV', async () => {
    const createObjectURL = vi.fn((blob: Blob | MediaSource) => {
      expect(blob).toBeInstanceOf(Blob);
      return 'blob:filtered-client-connections';
    });
    Object.defineProperty(URL, 'createObjectURL', {
      writable: true,
      value: createObjectURL,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      writable: true,
      value: vi.fn(),
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.mocked(connectionsService.listConnections).mockResolvedValue(connections);
    const user = userEvent.setup();
    renderWithProviders(<ClientsPage />);

    await screen.findByText('order-svc-0@10.0.1.12:49152');
    const filterTriggers = document.querySelectorAll<HTMLElement>('.ant-table-filter-trigger');
    await user.click(filterTriggers[1]);
    const filterDropdown = document.querySelector<HTMLElement>('.ant-table-filter-dropdown');
    expect(filterDropdown).not.toBeNull();
    await user.click(within(filterDropdown!).getByText('Consumer'));
    await user.click(within(filterDropdown!).getByRole('button', { name: 'OK' }));

    expect(screen.queryByText('order-svc-0@10.0.1.12:49152')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '导出' }));

    const blob = createObjectURL.mock.calls[0][0] as Blob;
    const csv = await blob.text();
    expect(csv).not.toContain('order-svc-0@10.0.1.12:49152');
    expect(csv).toContain('payment-svc-0@10.0.1.13:49153');
    expect(csv).toContain('audit-svc-0@10.0.2.10:49154');
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
    expect(screen.getByRole('button', { name: /导出/ })).toBeDisabled();
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

  it('retries the current cluster connection query after a runtime failure', async () => {
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
      namesrvAddr: 'namesrv-1:9876',
    });
  });

  it('clears the previous data when the next cluster connection request fails', async () => {
    vi.mocked(connectionsService.listConnections).mockImplementation((query) =>
      query?.namesrvAddr === 'namesrv-1:9876'
        ? Promise.resolve([connection])
        : Promise.reject(new Error('Cluster ns-audit is unavailable')),
    );
    const user = userEvent.setup();
    renderWithProviders(<ClientsPage />);

    await screen.findByText('order-svc-0@10.0.1.12:49152');
    await user.click(screen.getByRole('combobox', { name: 'NameServer' }));
    await user.click(
      await screen.findByText('rocketmq2 (namesrv-2:9876)', {
        selector: '.ant-select-item-option-content',
      }),
    );

    expect(await screen.findByText('Cluster ns-audit is unavailable')).toBeInTheDocument();
    expect(screen.queryByText('order-svc-0@10.0.1.12:49152')).not.toBeInTheDocument();
    expect(within(screen.getByTestId('connection-total')).getByText('0')).toBeInTheDocument();
  });

  it('ignores a stale connection response after switching nameservers', async () => {
    const user = userEvent.setup();
    const stale = deferred<ClientConnection[]>();
    const latest = deferred<ClientConnection[]>();
    vi.mocked(connectionsService.listConnections)
      .mockImplementationOnce(() => stale.promise)
      .mockImplementationOnce(() => latest.promise);

    renderWithProviders(<ClientsPage />);

    await user.click(screen.getByRole('combobox', { name: 'NameServer' }));
    await user.click(
      await screen.findByText('rocketmq2 (namesrv-2:9876)', {
        selector: '.ant-select-item-option-content',
      }),
    );

    await waitFor(() =>
      expect(connectionsService.listConnections).toHaveBeenLastCalledWith({
        namesrvAddr: 'namesrv-2:9876',
      }),
    );

    await act(async () => {
      latest.resolve(connections);
      stale.resolve([{ ...connection, clientId: 'stale-client@10.0.1.99:49160' }]);
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.queryByText('stale-client@10.0.1.99:49160')).not.toBeInTheDocument();
    expect(within(screen.getByTestId('connection-total')).getByText('3')).toBeInTheDocument();
  });

  it('ignores a stale registry response after a retry', async () => {
    const user = userEvent.setup();
    const stale = deferred<ClusterInfo[]>();
    vi.mocked(clusterService.listRegistryClusters)
      .mockRejectedValueOnce(new Error('Unable to load registry clusters'))
      .mockImplementationOnce(() => stale.promise);

    renderWithProviders(<ClientsPage />);

    expect(await screen.findByText('Unable to load registry clusters')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /重\s*试/ }));
    await waitFor(() => expect(clusterService.listRegistryClusters).toHaveBeenNthCalledWith(2));

    await act(async () => {
      stale.resolve([]);
    });

    expect(screen.queryByText('Unable to load registry clusters')).not.toBeInTheDocument();
    expect(connectionsService.listConnections).toHaveBeenCalledTimes(0);
  });

  it('surfaces registry discovery failures and allows retrying', async () => {
    vi.mocked(clusterService.listRegistryClusters)
      .mockRejectedValueOnce(new Error('Unable to load registry clusters'))
      .mockResolvedValueOnce(registryClusters);
    const user = userEvent.setup();
    renderWithProviders(<ClientsPage />);

    expect(await screen.findByText('Unable to load registry clusters')).toBeInTheDocument();
    expect(connectionsService.listConnections).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /重\s*试/ }));
    await screen.findByText('order-svc-0@10.0.1.12:49152');
    expect(connectionsService.listConnections).toHaveBeenCalledWith({
      namesrvAddr: 'namesrv-1:9876',
    });
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
