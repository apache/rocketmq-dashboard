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

import { describe, it, expect, vi, beforeAll, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import { listClusters, restartBroker } from '../../../services/clusterService';
import type { ClusterInfo } from '../../../api/cluster';
import BrokerCluster from '../BrokerCluster';

vi.mock('../../../services/clusterService', () => ({
  listClusters: vi.fn(),
  restartBroker: vi.fn(),
}));

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

describe('BrokerCluster Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listClusters).mockResolvedValue(clusterFixture);
    vi.mocked(restartBroker).mockResolvedValue({ success: true, message: 'restarted' });
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

  it('should render only supported broker restart actions', async () => {
    renderWithProviders(<BrokerCluster />);
    await screen.findByText('broker-api-a');
    expect(screen.queryByText('创建集群')).not.toBeInTheDocument();
    expect(screen.queryByText('配置')).not.toBeInTheDocument();
    const restartButtons = screen.getAllByText('重启');
    expect(restartButtons).toHaveLength(2);
  });

  it('restarts a broker after confirmation and refreshes the cluster data', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    await screen.findByText('broker-api-a');

    await user.click(screen.getAllByText('重启')[0]);
    expect(await screen.findByText('确定要重启 Broker "broker-api-a" 吗？')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /确\s*认/ }));

    await waitFor(() => {
      expect(restartBroker).toHaveBeenCalledWith('cluster-1', 'broker-api-a');
    });
    await waitFor(() => {
      expect(listClusters).toHaveBeenCalledTimes(2);
    });
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
});
