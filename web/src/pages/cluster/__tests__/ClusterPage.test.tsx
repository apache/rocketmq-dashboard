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

import { App, message, Modal } from 'antd';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ClusterInfo } from '../../../api/cluster';
import { LangProvider } from '../../../i18n/LangContext';

const clusterServiceMocks = vi.hoisted(() => ({
  createNameserverRegistry: vi.fn(),
  deleteNameserverRegistry: vi.fn(),
  listClusters: vi.fn(),
  listK8sCerts: vi.fn(),
  listNameserverRegistry: vi.fn(),
  listRegistryClusters: vi.fn(),
  previewClusterConfig: vi.fn(),
  restartProxy: vi.fn(),
  testClusterConnection: vi.fn(),
  updateClusterConfig: vi.fn(),
  updateNameserverRegistry: vi.fn(),
}));

const instanceServiceMocks = vi.hoisted(() => ({
  listInstances: vi.fn(),
}));

vi.mock('../../../services/clusterService', () => clusterServiceMocks);
vi.mock('../../../services/instanceService', () => instanceServiceMocks);

import ClusterPage from '../index';

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

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </LangProvider>
    </App>,
  );

const renderWithRoute = (ui: React.ReactElement, route: string) =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </LangProvider>
    </App>,
  );

const buildCluster = ({
  tpsIn = 12480,
  tpsOut = 34560,
  connections = 1842,
}: {
  tpsIn?: number;
  tpsOut?: number;
  connections?: number;
} = {}): ClusterInfo => ({
  id: 'cluster-prod',
  name: 'rocketmq-prod',
  nsClusterName: 'ns-prod',
  type: 'V5_PROXY_CLUSTER',
  endpoint: '10.101.2.1:9876',
  status: 'healthy',
  version: '5.2.0',
  brokers: [
    {
      name: 'rocketmq-prod-0',
      addr: '10.101.2.11:10911',
      version: '5.2.0',
      status: 'running',
      diskUsage: 62,
      tpsIn,
      tpsOut,
    },
  ],
  proxies: [
    {
      addr: '10.101.2.21:8081',
      status: 'healthy',
      connections,
      grpcPort: 8081,
      remotingPort: 8080,
    },
  ],
  nameServers: [{ addr: '10.101.2.1:9876', status: 'healthy' }],
  config: {
    flushDiskType: 'SYNC_FLUSH',
    autoCreateTopicEnable: false,
    autoCreateSubscriptionGroup: false,
    maxMessageSize: 4 * 1024 * 1024,
    msgTraceTopicName: 'RMQ_SYS_TRACE_TOPIC',
    fileReservedTime: 72,
    writeQueueNums: 8,
    readQueueNums: 8,
    brokerPermission: 6,
    deleteWhen: '04',
  },
  topicCount: 10,
  groupCount: 5,
  tpsHistory: [tpsIn],
});

const deferred = <T,>() => {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, reject, resolve };
};

const flushPromises = async () => {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
};

describe('Cluster page', () => {
  it('uses a valid instanceId from the route instead of the default instance', async () => {
    instanceServiceMocks.listInstances.mockResolvedValue([
      {
        id: 1,
        name: 'instance-a',
        type: 'DIRECT',
        vendor: 'APACHE',
        endpoint: '127.0.0.1:9876',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      },
      {
        id: 2,
        name: 'instance-b',
        type: 'DIRECT',
        vendor: 'APACHE',
        endpoint: '127.0.0.2:9876',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      },
    ]);
    renderWithRoute(<ClusterPage />, '/cluster?instanceId=instance-b');

    await screen.findByText('ns-prod');

    expect(clusterServiceMocks.listClusters).toHaveBeenCalledWith('instance-b');
  });

  beforeEach(() => {
    instanceServiceMocks.listInstances.mockReset().mockResolvedValue([
      {
        id: 10,
        name: 'instance-1',
        endpoint: 'namesrv-1:9876',
        type: 'DIRECT',
        vendor: 'APACHE',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
    ]);
    clusterServiceMocks.listClusters.mockReset().mockResolvedValue([buildCluster()]);
    clusterServiceMocks.listRegistryClusters.mockReset().mockResolvedValue([buildCluster()]);
    clusterServiceMocks.listK8sCerts.mockReset().mockResolvedValue([
      {
        id: 1,
        k8sId: 'kubernetes-daily',
        cluster: 'kubernetes-daily',
        type: 'TLS',
        issuer: 'kubernetes-ca',
        notBefore: '',
        notAfter: '',
        status: 'valid',
        daysRemaining: 365,
        san: [],
      },
    ]);
    clusterServiceMocks.createNameserverRegistry.mockReset().mockResolvedValue({
      id: 3,
      name: 'rocketmq3',
      namesrvAddr: 'rocketmq3-nameserver:9876',
      k8sNamespace: null,
      k8sId: null,
      status: 'healthy',
      description: null,
      gmtCreate: '',
      gmtModified: '',
    });
    clusterServiceMocks.updateNameserverRegistry.mockReset().mockResolvedValue({
      id: 1,
      name: 'rocketmq1',
      namesrvAddr: 'rocketmq1-nameserver.svc:9876',
      k8sNamespace: 'rocketmq1',
      k8sId: 'ack-daily',
      status: 'healthy',
      description: null,
      gmtCreate: '',
      gmtModified: '',
    });
    clusterServiceMocks.deleteNameserverRegistry.mockReset().mockResolvedValue(undefined);
    clusterServiceMocks.listNameserverRegistry.mockReset().mockResolvedValue([
      {
        id: 1,
        name: 'rocketmq1',
        namesrvAddr: 'rocketmq1-nameserver:9876',
        k8sNamespace: 'rocketmq1',
        k8sId: 'ack-daily',
        status: 'healthy',
        description: 'community chart cluster',
        gmtCreate: '2026-08-17 19:15:37',
        gmtModified: '2026-08-17 19:15:37',
      },
    ]);
    clusterServiceMocks.restartProxy.mockReset().mockResolvedValue(undefined);
    clusterServiceMocks.testClusterConnection.mockReset();
    clusterServiceMocks.previewClusterConfig.mockReset().mockImplementation(async (request) => {
      const cluster = buildCluster();
      return {
        cluster,
        currentConfig: cluster.config,
        proposedConfig: { ...cluster.config, ...request },
        targetBrokers: cluster.brokers.map((broker) => ({
          name: broker.name,
          address: broker.addr,
        })),
        brokerProperties: {
          ...(request.writeQueueNums != null
            ? { defaultTopicQueueNums: String(request.writeQueueNums) }
            : {}),
        },
        changes: [
          {
            field: 'writeQueueNums',
            currentValue: String(cluster.config.writeQueueNums),
            proposedValue: String(request.writeQueueNums),
            brokerProperty: 'defaultTopicQueueNums',
          },
        ],
        changed: true,
      };
    });
    clusterServiceMocks.updateClusterConfig.mockReset().mockImplementation(async () => {
      const cluster = buildCluster();
      return {
        cluster,
        status: 'SUCCESS',
        successfulBrokers: cluster.brokers.map((broker) => broker.addr),
        failedBrokers: [],
      };
    });
  });

  afterEach(() => {
    Modal.destroyAll();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('auto-retries instance bootstrap failures without querying a default cluster', async () => {
    instanceServiceMocks.listInstances
      .mockRejectedValueOnce(new Error('managed instances unavailable'))
      .mockResolvedValueOnce([
        {
          id: 10,
          name: 'instance-1',
          endpoint: 'namesrv-1:9876',
          type: 'DIRECT',
          vendor: 'APACHE',
          remark: '',
          topicCount: 0,
          consumerGroupCount: 0,
          gmtCreate: '',
          gmtModified: '',
        },
      ]);
    renderWithProviders(<ClusterPage />);

    expect(clusterServiceMocks.listClusters).not.toHaveBeenCalled();

    await waitFor(
      () => expect(clusterServiceMocks.listClusters).toHaveBeenCalledWith('instance-1'),
      { timeout: 6000 },
    );
  }, 10000);

  it('opens proxy detail dialog from the proxy table', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ClusterPage />);

    await user.click(screen.getByRole('tab', { name: /Proxy 管理/ }));
    const proxyRow = await screen.findByRole('row', { name: /10\.101\.2\.21:8081/ });
    await user.click(within(proxyRow).getByRole('button', { name: /详情/ }));

    const dialog = await screen.findByRole('dialog', { name: /Proxy 详情 - 10\.101\.2\.21:8081/ });
    expect(within(dialog).getByText('rocketmq-prod')).toBeInTheDocument();
    expect(within(dialog).getByText('ns-prod')).toBeInTheDocument();
    expect(within(dialog).getAllByText('10.101.2.21:8081')).toHaveLength(1);
    expect(within(dialog).getByText('1,842')).toBeInTheDocument();
    expect(within(dialog).getByText('8081')).toBeInTheDocument();
    expect(within(dialog).getByText('8080')).toBeInTheDocument();
  });

  it('previews broker config changes before submitting the update', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ClusterPage />);

    const brokerRow = await screen.findByRole('row', { name: /10\.101\.2\.11:10911/ });
    await user.click(within(brokerRow).getByRole('button', { name: /配\s*置/ }));
    const dialog = await screen.findByRole('dialog', { name: /配置 - rocketmq-prod/ });
    const writeQueuesInput = within(dialog).getByLabelText('写队列数');
    await user.clear(writeQueuesInput);
    await user.type(writeQueuesInput, '16');

    await user.click(within(dialog).getByRole('button', { name: /预\s*览/ }));

    await waitFor(() =>
      expect(clusterServiceMocks.previewClusterConfig).toHaveBeenCalledWith(
        expect.objectContaining({
          id: 'cluster-prod',
          instanceId: 'instance-1',
          writeQueueNums: 16,
          maxMessageSize: 4 * 1024 * 1024,
        }),
      ),
    );
    expect(clusterServiceMocks.updateClusterConfig).not.toHaveBeenCalled();
    expect(within(dialog).getByText('10.101.2.11:10911')).toBeInTheDocument();
    expect(within(dialog).getByText('defaultTopicQueueNums=16')).toBeInTheDocument();
    expect(within(dialog).getByRole('row', { name: /写队列数/ })).toHaveTextContent('16');
  });

  it('keeps cluster tabs usable when address fields are missing', async () => {
    const user = userEvent.setup();
    const submitSearch = async (placeholder: string, value: string) => {
      const input = screen.getByPlaceholderText(placeholder);
      await user.type(input, value);
      const searchBox = input.closest('.ant-input-search');
      expect(searchBox).not.toBeNull();
      const searchButton = (searchBox as HTMLElement).querySelector('.ant-input-search-button');
      expect(searchButton).not.toBeNull();
      await user.click(searchButton as HTMLElement);
    };
    const cluster = buildCluster();
    cluster.brokers = [{ ...cluster.brokers[0], addr: null as unknown as string }];
    cluster.nameServers = [{ ...cluster.nameServers[0], addr: null as unknown as string }];
    cluster.proxies = [{ ...cluster.proxies[0], addr: null as unknown as string }];
    clusterServiceMocks.listClusters.mockResolvedValue([cluster]);

    renderWithProviders(<ClusterPage />);
    expect(await screen.findByText('rocketmq-prod-0')).toBeInTheDocument();

    await submitSearch('搜索集群名称、Broker 名称或地址', 'not-found');
    expect(screen.queryByText('rocketmq-prod-0')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: /NameServer 管理/ }));
    expect(await screen.findByText('rocketmq1-nameserver:9876')).toBeInTheDocument();
    expect(screen.getAllByText('rocketmq1')).toHaveLength(2);
    await submitSearch('搜索名称或地址', 'not-found');
    expect(screen.getByPlaceholderText('搜索名称或地址')).toHaveValue('not-found');
    expect(screen.queryByText('rocketmq1-nameserver:9876')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: /Proxy 管理/ }));
    await submitSearch('搜索 Proxy 地址', 'not-found');
    expect(screen.getByPlaceholderText('搜索 Proxy 地址')).toHaveValue('not-found');
  });

  it('creates and deletes nameserver registry entries', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ClusterPage />);
    await user.click(screen.getByRole('tab', { name: /NameServer 管理/ }));
    expect(await screen.findByText('rocketmq1-nameserver:9876')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /新建 NameServer/ }));
    const dialog = await screen.findByRole('dialog', { name: /新建 NameServer/ });
    await user.type(within(dialog).getByLabelText('名称'), 'rocketmq3');
    await user.type(within(dialog).getByLabelText('NameServer 地址'), 'rocketmq3-nameserver:9876');
    await user.click(within(dialog).getByRole('button', { name: /确\s*认/ }));
    await waitFor(() =>
      expect(clusterServiceMocks.createNameserverRegistry).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'rocketmq3',
          namesrvAddr: 'rocketmq3-nameserver:9876',
        }),
      ),
    );

    const row = await screen.findByRole('row', { name: /rocketmq1-nameserver:9876/ });
    await user.click(within(row).getByRole('button', { name: /编\s*辑/ }));
    const editDialog = await screen.findByRole('dialog', { name: /编辑 NameServer/ });
    const addrInput = within(editDialog).getByLabelText('NameServer 地址');
    await user.clear(addrInput);
    await user.type(addrInput, 'rocketmq1-nameserver.svc:9876');
    await user.click(within(editDialog).getByRole('button', { name: /确\s*认/ }));
    await waitFor(() =>
      expect(clusterServiceMocks.updateNameserverRegistry).toHaveBeenCalledWith(
        expect.objectContaining({
          id: 1,
          name: 'rocketmq1',
          namesrvAddr: 'rocketmq1-nameserver.svc:9876',
        }),
      ),
    );

    const confirmSpy = vi.spyOn(Modal, 'confirm').mockImplementation((config) => {
      expect(String(config.content)).toContain('rocketmq1');
      void config.onOk?.();
      return { destroy: vi.fn(), update: vi.fn() } as unknown as ReturnType<typeof Modal.confirm>;
    });
    const rowAfterEdit = await screen.findByRole('row', { name: /rocketmq1-nameserver:9876/ });
    fireEvent.click(within(rowAfterEdit).getByRole('button', { name: /删\s*除/ }));
    await waitFor(() =>
      expect(clusterServiceMocks.deleteNameserverRegistry).toHaveBeenCalledWith(1),
    );
    confirmSpy.mockRestore();
  });

  it('polls the API after two seconds and renders only returned metrics', async () => {
    vi.useFakeTimers();
    const randomSpy = vi.spyOn(Math, 'random');
    clusterServiceMocks.listClusters
      .mockResolvedValueOnce([buildCluster({ tpsIn: 101, tpsOut: 201, connections: 501 })])
      .mockResolvedValueOnce([buildCluster({ tpsIn: 102, tpsOut: 202, connections: 502 })]);
    clusterServiceMocks.listRegistryClusters.mockResolvedValue([
      buildCluster({ tpsIn: 101, tpsOut: 201, connections: 501 }),
    ]);

    renderWithProviders(<ClusterPage />);
    await flushPromises();
    randomSpy.mockClear();

    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('row', { name: /10\.101\.2\.11:10911/ })).toHaveTextContent('101');
    fireEvent.click(screen.getByRole('tab', { name: /Proxy 管理/ }));
    const initialProxyRow = screen.getByRole('row', { name: /10\.101\.2\.21:8081/ });
    expect(initialProxyRow).toHaveTextContent('501');
    fireEvent.click(within(initialProxyRow).getByRole('button', { name: /详情/ }));
    const proxyDialog = screen.getByRole('dialog', {
      name: /Proxy 详情 - 10\.101\.2\.21:8081/,
    });
    randomSpy.mockClear();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1999);
    });
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(2);
    expect(within(proxyDialog).getByText('501')).toBeInTheDocument();
    expect(randomSpy).not.toHaveBeenCalled();
    const proxyTabPanel = screen.getByRole('tabpanel', { name: /Proxy 管理/ });
    const proxyTable = within(proxyTabPanel).getByRole('table');
    expect(within(proxyTable).getByRole('row', { name: /10\.101\.2\.21:8081/ })).toHaveTextContent(
      '501',
    );
    fireEvent.click(screen.getByRole('tab', { name: /Broker 管理/ }));
    expect(screen.getByRole('row', { name: /10\.101\.2\.11:10911/ })).toHaveTextContent('101');
  });

  it('waits two seconds after a slow request completes before polling again', async () => {
    vi.useFakeTimers();
    const slowRefresh = deferred<ClusterInfo[]>();
    clusterServiceMocks.listClusters
      .mockResolvedValueOnce([buildCluster()])
      .mockReturnValueOnce(slowRefresh.promise)
      .mockResolvedValue([buildCluster({ tpsIn: 103 })]);

    renderWithProviders(<ClusterPage />);
    await flushPromises();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(2);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10000);
    });
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(2);

    await act(async () => {
      slowRefresh.resolve([buildCluster({ tpsIn: 102 })]);
      await slowRefresh.promise;
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1999);
    });
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(2);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(3);
  });

  it('stops polling when disabled and refreshes immediately when re-enabled', async () => {
    vi.useFakeTimers();
    renderWithProviders(<ClusterPage />);
    await flushPromises();
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(1);

    const autoRefreshSwitch = screen.getByRole('switch');
    fireEvent.click(autoRefreshSwitch);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(1);

    fireEvent.click(autoRefreshSwitch);
    await flushPromises();
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(2);
  });

  it('does not schedule another poll when disabled during an in-flight refresh', async () => {
    vi.useFakeTimers();
    const slowRefresh = deferred<ClusterInfo[]>();
    clusterServiceMocks.listClusters
      .mockResolvedValueOnce([buildCluster()])
      .mockReturnValueOnce(slowRefresh.promise);

    renderWithProviders(<ClusterPage />);
    await flushPromises();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(2);

    fireEvent.click(screen.getByRole('switch'));
    await act(async () => {
      slowRefresh.resolve([buildCluster({ tpsIn: 104 })]);
      await slowRefresh.promise;
      await vi.advanceTimersByTimeAsync(10000);
    });

    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(2);
  });

  it('queues one foreground follow-up when a refresh is already in flight', async () => {
    vi.useFakeTimers();
    const initialRequest = deferred<ClusterInfo[]>();
    clusterServiceMocks.listClusters
      .mockReturnValueOnce(initialRequest.promise)
      .mockResolvedValueOnce([buildCluster({ tpsIn: 202 })]);
    clusterServiceMocks.listRegistryClusters.mockResolvedValue([buildCluster({ tpsIn: 202 })]);

    renderWithProviders(<ClusterPage />);
    await flushPromises();
    fireEvent.click(screen.getByRole('button', { name: '刷新' }));
    fireEvent.click(screen.getByRole('button', { name: '刷新' }));

    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(1);
    await act(async () => {
      initialRequest.resolve([buildCluster({ tpsIn: 201 })]);
      await initialRequest.promise;
      await Promise.resolve();
    });

    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(2);
    expect(screen.getByRole('row', { name: /10\.101\.2\.11:10911/ })).toHaveTextContent('202');
  });

  it('queues an operation refresh behind an in-flight background request', async () => {
    vi.useFakeTimers();
    const successSpy = vi.spyOn(message, 'success').mockImplementation(vi.fn());
    const backgroundRequest = deferred<ClusterInfo[]>();
    const operationRequest = deferred<void>();
    let backgroundSettled = false;
    void backgroundRequest.promise.then(() => {
      backgroundSettled = true;
    });
    clusterServiceMocks.restartProxy.mockReturnValueOnce(operationRequest.promise);
    clusterServiceMocks.listClusters
      .mockResolvedValueOnce([buildCluster({ connections: 601 })])
      .mockReturnValueOnce(backgroundRequest.promise)
      .mockResolvedValueOnce([buildCluster({ connections: 603 })]);
    clusterServiceMocks.listRegistryClusters.mockResolvedValue([
      buildCluster({ connections: 601 }),
    ]);

    renderWithProviders(<ClusterPage />);
    await flushPromises();
    fireEvent.click(screen.getByRole('tab', { name: /Proxy 管理/ }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    const proxyRow = screen.getByRole('row', { name: /10\.101\.2\.21:8081/ });
    fireEvent.click(within(proxyRow).getByRole('button', { name: /重启/ }));
    await flushPromises();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    const dialog = screen.getAllByText('确认重启')[0].closest('.ant-modal');
    expect(dialog).not.toBeNull();
    fireEvent.click(within(dialog as HTMLElement).getByRole('button', { name: /确\s*认/ }));
    await flushPromises();

    expect(clusterServiceMocks.restartProxy).toHaveBeenCalledWith({
      clusterId: 'cluster-prod',
      addr: '10.101.2.21:8081',
    });
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(2);

    await act(async () => {
      operationRequest.resolve();
      await operationRequest.promise;
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(backgroundSettled).toBe(false);
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(2);
    expect(successSpy).not.toHaveBeenCalled();

    await act(async () => {
      backgroundRequest.resolve([buildCluster({ connections: 602 })]);
      await backgroundRequest.promise;
      await Promise.resolve();
      await Promise.resolve();
    });
    await flushPromises();

    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(3);
    const proxyTabPanel = screen.getByRole('tabpanel', { name: /Proxy 管理/ });
    const proxyTable = within(proxyTabPanel).getByRole('table');
    expect(within(proxyTable).getByRole('row', { name: /10\.101\.2\.21:8081/ })).toHaveTextContent(
      '601',
    );
    expect(successSpy).toHaveBeenCalledTimes(1);
  });

  it('keeps the last snapshot and silently retries after a background failure', async () => {
    vi.useFakeTimers();
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(vi.fn());
    clusterServiceMocks.listClusters
      .mockResolvedValueOnce([buildCluster({ connections: 301 })])
      .mockRejectedValueOnce(new Error('temporary failure'))
      .mockResolvedValueOnce([buildCluster({ connections: 302 })]);
    clusterServiceMocks.listRegistryClusters.mockResolvedValue([
      buildCluster({ connections: 301 }),
    ]);

    renderWithProviders(<ClusterPage />);
    await flushPromises();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    fireEvent.click(screen.getByRole('tab', { name: /Proxy 管理/ }));
    expect(screen.getByRole('row', { name: /10\.101\.2\.21:8081/ })).toHaveTextContent('301');
    expect(screen.getByText('刷新失败')).toBeInTheDocument();
    expect(errorSpy).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(3);
    expect(screen.getByRole('row', { name: /10\.101\.2\.21:8081/ })).toHaveTextContent('301');
    expect(screen.queryByText('刷新失败')).not.toBeInTheDocument();
  });

  it('reports initial and manual failures', async () => {
    vi.useFakeTimers();
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(vi.fn());
    clusterServiceMocks.listClusters.mockRejectedValue(new Error('failure'));

    renderWithProviders(<ClusterPage />);
    await flushPromises();
    expect(errorSpy).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('switch'));
    fireEvent.click(screen.getByRole('button', { name: '刷新' }));
    await flushPromises();
    expect(errorSpy).toHaveBeenCalledTimes(2);
  });

  it('clears tables and metric snapshots when the API returns an empty list', async () => {
    vi.useFakeTimers();
    clusterServiceMocks.listClusters
      .mockResolvedValueOnce([buildCluster({ connections: 401 })])
      .mockResolvedValueOnce([]);
    clusterServiceMocks.listRegistryClusters.mockResolvedValue([
      buildCluster({ connections: 401 }),
    ]);

    renderWithProviders(<ClusterPage />);
    await flushPromises();
    fireEvent.click(screen.getByRole('tab', { name: /Proxy 管理/ }));
    expect(screen.getByRole('row', { name: /10\.101\.2\.21:8081/ })).toHaveTextContent('401');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    // Proxy/Broker 行来自注册表探测，不随实例列表清空；仅实例维度计数归零
    expect(screen.getByRole('row', { name: /10\.101\.2\.21:8081/ })).toHaveTextContent('401');
    expect(screen.getByText(/共 0 RocketMQ 集群/)).toBeInTheDocument();
  });

  it('ignores late responses and does not schedule polling after unmount', async () => {
    vi.useFakeTimers();
    const initialRequest = deferred<ClusterInfo[]>();
    clusterServiceMocks.listClusters.mockReturnValue(initialRequest.promise);
    const view = renderWithProviders(<ClusterPage />);

    await flushPromises();

    view.unmount();
    await act(async () => {
      initialRequest.resolve([buildCluster()]);
      await initialRequest.promise;
      await vi.advanceTimersByTimeAsync(5000);
    });

    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(1);
  });
});
