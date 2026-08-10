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
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ClusterInfo } from '../../../api/cluster';
import { LangProvider } from '../../../i18n/LangContext';

const clusterServiceMocks = vi.hoisted(() => ({
  createNameServer: vi.fn(),
  listClusters: vi.fn(),
  restartProxy: vi.fn(),
  testClusterConnection: vi.fn(),
  updateClusterConfig: vi.fn(),
  updateNameServer: vi.fn(),
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
      <LangProvider>{ui}</LangProvider>
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
  beforeEach(() => {
    instanceServiceMocks.listInstances.mockReset().mockResolvedValue([
      {
        id: 'instance-1',
        name: 'Instance 1',
        endpoint: 'namesrv-1:9876',
        type: 'DIRECT',
        vendor: 'APACHE',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        createdAt: '',
        updatedAt: '',
      },
    ]);
    clusterServiceMocks.createNameServer.mockReset().mockResolvedValue(undefined);
    clusterServiceMocks.listClusters.mockReset().mockResolvedValue([buildCluster()]);
    clusterServiceMocks.restartProxy.mockReset().mockResolvedValue(undefined);
    clusterServiceMocks.testClusterConnection.mockReset();
    clusterServiceMocks.updateClusterConfig.mockReset().mockImplementation(async () => {
      const cluster = buildCluster();
      return {
        cluster,
        status: 'SUCCESS',
        successfulBrokers: cluster.brokers.map((broker) => broker.addr),
        failedBrokers: [],
      };
    });
    clusterServiceMocks.updateNameServer.mockReset().mockResolvedValue(undefined);
  });

  afterEach(() => {
    Modal.destroyAll();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('surfaces instance bootstrap failures and retries without querying a default cluster', async () => {
    instanceServiceMocks.listInstances
      .mockRejectedValueOnce(new Error('managed instances unavailable'))
      .mockResolvedValueOnce([
        {
          id: 'instance-1',
          name: 'Instance 1',
          endpoint: 'namesrv-1:9876',
          type: 'DIRECT',
          vendor: 'APACHE',
          remark: '',
          topicCount: 0,
          consumerGroupCount: 0,
          createdAt: '',
          updatedAt: '',
        },
      ]);
    const user = userEvent.setup();
    renderWithProviders(<ClusterPage />);

    const alert = await screen.findByRole('alert');
    expect(clusterServiceMocks.listClusters).not.toHaveBeenCalled();

    await user.click(within(alert).getByRole('button', { name: /重\s*试/ }));
    expect(await screen.findByText('rocketmq-prod-0')).toBeInTheDocument();
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledWith('instance-1');
  });

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

    await submitSearch('搜索 Broker 名称或地址', 'not-found');
    expect(screen.queryByText('rocketmq-prod-0')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: /NameServer 管理/ }));
    await submitSearch('搜索地址', 'not-found');
    expect(screen.getByPlaceholderText('搜索地址')).toHaveValue('not-found');

    await user.click(screen.getByRole('tab', { name: /Proxy 管理/ }));
    await submitSearch('搜索 Proxy 地址', 'not-found');
    expect(screen.getByPlaceholderText('搜索 Proxy 地址')).toHaveValue('not-found');
  });

  it('polls the API after two seconds and renders only returned metrics', async () => {
    vi.useFakeTimers();
    const randomSpy = vi.spyOn(Math, 'random');
    clusterServiceMocks.listClusters
      .mockResolvedValueOnce([buildCluster({ tpsIn: 101, tpsOut: 201, connections: 501 })])
      .mockResolvedValueOnce([buildCluster({ tpsIn: 102, tpsOut: 202, connections: 502 })]);

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
    expect(within(proxyDialog).getByText('502')).toBeInTheDocument();
    expect(randomSpy).not.toHaveBeenCalled();
    const proxyTabPanel = screen.getByRole('tabpanel', { name: /Proxy 管理/ });
    const proxyTable = within(proxyTabPanel).getByRole('table');
    expect(within(proxyTable).getByRole('row', { name: /10\.101\.2\.21:8081/ })).toHaveTextContent(
      '502',
    );
    fireEvent.click(screen.getByRole('tab', { name: /Broker 管理/ }));
    expect(screen.getByRole('row', { name: /10\.101\.2\.11:10911/ })).toHaveTextContent('102');
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
      '603',
    );
    expect(successSpy).toHaveBeenCalledTimes(1);
  });

  it('keeps the last snapshot and silently retries after a background failure', async () => {
    vi.useFakeTimers();
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(vi.fn());
    clusterServiceMocks.listClusters
      .mockResolvedValueOnce([buildCluster({ tpsIn: 301 })])
      .mockRejectedValueOnce(new Error('temporary failure'))
      .mockResolvedValueOnce([buildCluster({ tpsIn: 302 })]);

    renderWithProviders(<ClusterPage />);
    await flushPromises();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    expect(screen.getByRole('row', { name: /10\.101\.2\.11:10911/ })).toHaveTextContent('301');
    expect(screen.getByText('刷新失败')).toBeInTheDocument();
    expect(errorSpy).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(clusterServiceMocks.listClusters).toHaveBeenCalledTimes(3);
    expect(screen.getByRole('row', { name: /10\.101\.2\.11:10911/ })).toHaveTextContent('302');
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
      .mockResolvedValueOnce([buildCluster({ tpsIn: 401 })])
      .mockResolvedValueOnce([]);

    renderWithProviders(<ClusterPage />);
    await flushPromises();
    expect(screen.getByRole('row', { name: /10\.101\.2\.11:10911/ })).toHaveTextContent('401');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(screen.queryByRole('row', { name: /10\.101\.2\.11:10911/ })).not.toBeInTheDocument();
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
