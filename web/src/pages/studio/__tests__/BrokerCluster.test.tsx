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

import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import { listClusters } from '../../../services/clusterService';
import { listInstances } from '../../../services/instanceService';
import type { ClusterInfo } from '../../../api/cluster';
import { downloadCsv } from '../../../utils/download';
import BrokerCluster from '../BrokerCluster';

vi.mock('../../../services/clusterService', () => ({
  listClusters: vi.fn(),
}));

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn(),
}));

vi.mock('../../../utils/download', async () => {
  const actual =
    await vi.importActual<typeof import('../../../utils/download')>('../../../utils/download');
  return {
    ...actual,
    downloadCsv: vi.fn(),
  };
});

// Mock matchMedia for antd responsive components
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

// Mock react-router-dom
vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  useParams: () => ({}),
}));

const clusterFixture: ClusterInfo[] = [
  {
    id: 'cluster-1',
    name: 'prod-cn-east-1',
    nsClusterName: 'prod-cn-east-1',
    type: 'V5_PROXY_CLUSTER',
    endpoint: '10.0.1.20:9876',
    status: 'healthy',
    version: '5.3.0',
    brokers: [
      {
        name: 'broker-api-a',
        addr: '10.0.1.10:10911',
        version: '5.3.0',
        status: 'running',
        diskUsage: 62,
        tpsIn: 12580,
        tpsOut: 8340,
      },
      {
        name: 'broker-api-b',
        addr: '10.0.1.11:10911',
        version: '5.3.0',
        status: 'readonly',
        diskUsage: 89,
        tpsIn: 0,
        tpsOut: 3120,
      },
    ],
    proxies: [
      {
        addr: '10.0.1.30:8080',
        status: 'healthy',
        connections: 2340,
        grpcPort: 8081,
        remotingPort: 8080,
      },
    ],
    nameServers: [{ addr: 'nameserver-api-a', status: 'healthy' }],
    config: {
      flushDiskType: 'SYNC_FLUSH',
      autoCreateTopicEnable: false,
      autoCreateSubscriptionGroup: false,
      maxMessageSize: 4194304,
      msgTraceTopicName: 'RMQ_SYS_TRACE_TOPIC4',
      fileReservedTime: 72,
      writeQueueNums: 16,
      readQueueNums: 16,
      brokerPermission: 6,
      deleteWhen: '04',
    },
    topicCount: 10,
    groupCount: 5,
    tpsHistory: [],
  },
];

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );
};

const createDeferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
};

describe('BrokerCluster Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listInstances).mockResolvedValue([
      {
        id: 1,
        name: 'instance-1',
        remark: '',
        type: 'DIRECT',
        endpoint: '10.0.1.20:9876',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
    ]);
    vi.mocked(listClusters).mockResolvedValue(clusterFixture);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('should render the page title', () => {
    renderWithProviders(<BrokerCluster />);
    expect(screen.getByText('Broker 集群')).toBeInTheDocument();
  });

  it('should render reset button', () => {
    renderWithProviders(<BrokerCluster />);
    expect(screen.getByText('重置')).toBeInTheDocument();
  });

  it('should display broker tab with data from the API', async () => {
    renderWithProviders(<BrokerCluster />);
    // Default tab is broker - data is loaded asynchronously from the service
    const brokerA = await screen.findAllByText('broker-api-a');
    expect(brokerA.length).toBeGreaterThan(0);
    expect(screen.getAllByText('broker-api-b').length).toBeGreaterThan(0);
    expect(screen.queryByText('broker-a')).not.toBeInTheDocument();
    expect(listClusters).toHaveBeenCalledWith('instance-1');
  });

  it('exports only the currently selected topology tab', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    await screen.findByText('broker-api-a');

    await user.click(screen.getByRole('button', { name: '导出' }));

    expect(downloadCsv).toHaveBeenCalledTimes(1);
    const [brokerFilename, brokerCsv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(brokerFilename).toMatch(/^rocketmq-broker-topology-\d{4}-\d{2}-\d{2}\.csv$/);
    expect(brokerCsv).toContain('"broker-api-a"');
    expect(brokerCsv).toContain('"broker-api-b"');
    expect(brokerCsv).not.toContain('"nameserver-api-a"');

    await user.click(screen.getByText('NameServer 管理'));
    await user.click(screen.getByRole('button', { name: '导出' }));

    expect(downloadCsv).toHaveBeenCalledTimes(2);
    const [nameServerFilename, nameServerCsv] = vi.mocked(downloadCsv).mock.calls[1];
    expect(nameServerFilename).toMatch(/^rocketmq-nameserver-topology-\d{4}-\d{2}-\d{2}\.csv$/);
    expect(nameServerCsv).toContain('"nameserver-api-a"');
    expect(nameServerCsv).not.toContain('"broker-api-a"');
  }, 10_000);
  it('does not fall back to an unscoped cluster query when instance discovery fails', async () => {
    vi.mocked(listInstances).mockRejectedValueOnce(new Error('instance discovery failed'));
    renderWithProviders(<BrokerCluster />);

    await waitFor(() => expect(listInstances).toHaveBeenCalledTimes(1));
    expect(listClusters).not.toHaveBeenCalled();
    expect(screen.queryByText('broker-api-a')).not.toBeInTheDocument();
  });

  it('should display broker status tags', async () => {
    renderWithProviders(<BrokerCluster />);
    await screen.findAllByText('broker-api-a');
    const runningTags = screen.getAllByText('运行中');
    expect(runningTags.length).toBeGreaterThan(0);
    const readonlyTags = screen.getAllByText('只读');
    expect(readonlyTags.length).toBeGreaterThan(0);
  });

  it('should switch to NameServer tab on click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    await screen.findByText('broker-api-a');
    const nsTab = screen.getByText('NameServer 管理');
    await user.click(nsTab);
    // After clicking, NameServer data should be visible (name equals address, so it appears twice)
    expect(screen.getAllByText('nameserver-api-a').length).toBeGreaterThan(0);
    expect(screen.queryByText('nameserver-a')).not.toBeInTheDocument();
  });

  it('should switch to Proxy tab on click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    await screen.findByText('broker-api-a');
    const proxyTab = screen.getByText('Proxy 管理');
    await user.click(proxyTab);
    // After clicking, Proxy data should be visible (proxy name equals its address, so it appears twice)
    expect(screen.getAllByText('10.0.1.30:8080').length).toBeGreaterThan(0);
  });

  it('renders Broker runtime data without unavailable mutation actions', async () => {
    renderWithProviders(<BrokerCluster />);
    await screen.findByText('broker-api-a');
    expect(screen.queryByText('创建集群')).not.toBeInTheDocument();
    expect(screen.queryByText('配置')).not.toBeInTheDocument();
    expect(screen.queryByText('重启')).not.toBeInTheDocument();
  });

  it('does not show mock infrastructure data when the API fails', async () => {
    vi.mocked(listClusters).mockRejectedValueOnce(new Error('network error'));
    renderWithProviders(<BrokerCluster />);
    await waitFor(() => {
      expect(listClusters).toHaveBeenCalledTimes(1);
    });
    expect(screen.queryByText('broker-a')).not.toBeInTheDocument();
    expect(screen.queryByText('broker-b')).not.toBeInTheDocument();
    expect(screen.queryByText('nameserver-a')).not.toBeInTheDocument();
    expect(screen.queryByText('proxy-a')).not.toBeInTheDocument();
  });

  it('clears topology from the previous instance when the next instance fails to load', async () => {
    vi.mocked(listInstances).mockResolvedValue([
      {
        id: 1,
        name: 'instance-1',
        remark: '',
        type: 'DIRECT',
        endpoint: '10.0.1.20:9876',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
      {
        id: 2,
        name: 'instance-2',
        remark: '',
        type: 'DIRECT',
        endpoint: '10.0.2.20:9876',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
    ]);
    vi.mocked(listClusters)
      .mockResolvedValueOnce(clusterFixture)
      .mockRejectedValueOnce(new Error('instance-2 unavailable'));
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    await screen.findByText('broker-api-a');

    await user.click(screen.getByRole('combobox', { name: '选择实例' }));
    await user.click(
      await screen.findByText('instance-2', { selector: '.ant-select-item-option-content' }),
    );

    await waitFor(() => expect(listClusters).toHaveBeenLastCalledWith('instance-2'));
    await waitFor(() => expect(screen.queryByText('broker-api-a')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: '导出' })).toBeDisabled();
  });

  it('polls only while live refresh is enabled and the document is visible', async () => {
    const visibilityState = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    renderWithProviders(<BrokerCluster />);

    await screen.findByText('broker-api-a');
    expect(listClusters).toHaveBeenCalledTimes(1);
    vi.useFakeTimers();

    const liveRefreshSwitch = screen.getByRole('switch');
    fireEvent.click(liveRefreshSwitch);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });
    expect(listClusters).toHaveBeenCalledTimes(1);

    visibilityState.mockReturnValue('visible');
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    expect(listClusters).toHaveBeenCalledTimes(2);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(4000);
    });
    expect(listClusters).toHaveBeenCalledTimes(4);

    fireEvent.click(liveRefreshSwitch);
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
      await vi.advanceTimersByTimeAsync(4000);
    });
    expect(listClusters).toHaveBeenCalledTimes(4);
  });
  it('keeps the latest cluster list when an older refresh resolves last', async () => {
    const older = createDeferred<ClusterInfo[]>();
    const latest = createDeferred<ClusterInfo[]>();
    vi.mocked(listClusters)
      .mockResolvedValueOnce(clusterFixture)
      .mockReturnValueOnce(older.promise)
      .mockReturnValueOnce(latest.promise);
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    await screen.findByText('broker-api-a');

    const refreshButton = screen.getByText('重置');
    await user.click(refreshButton);
    await user.click(refreshButton);

    const latestFixture = [
      {
        ...clusterFixture[0],
        brokers: [{ ...clusterFixture[0].brokers[0], name: 'latest-broker' }],
      },
    ];
    await act(async () => latest.resolve(latestFixture));
    expect(await screen.findByText('latest-broker')).toBeInTheDocument();

    await act(async () => older.resolve(clusterFixture));
    expect(screen.getByText('latest-broker')).toBeInTheDocument();
    expect(screen.queryByText('broker-api-a')).not.toBeInTheDocument();
  });

  it('formats bracketed IPv6 proxy gRPC endpoints without truncating the host', async () => {
    vi.mocked(listClusters).mockResolvedValue([
      {
        ...clusterFixture[0],
        proxies: [
          {
            ...clusterFixture[0].proxies[0],
            addr: '[2001:db8::10]:8080',
            grpcPort: 8081,
          },
        ],
      },
    ]);
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    await screen.findByText('broker-api-a');
    await user.click(screen.getByText('Proxy 管理'));

    expect(screen.getByText('[2001:db8::10]:8081')).toBeInTheDocument();
  });
  it('renders unrecognized broker statuses as unavailable instead of running', async () => {
    vi.mocked(listClusters).mockResolvedValue([
      {
        ...clusterFixture[0],
        brokers: [{ ...clusterFixture[0].brokers[0], status: 'mystery' }],
      },
    ]);
    renderWithProviders(<BrokerCluster />);

    await screen.findByText('broker-api-a');
    expect(screen.getByText('N/A')).toBeInTheDocument();
    expect(screen.queryByText('运行中')).not.toBeInTheDocument();
  });
});
