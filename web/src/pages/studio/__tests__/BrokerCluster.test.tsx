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
import { listClusters } from '../../../services/clusterService';
import type { ClusterInfo } from '../../../api/cluster';
import BrokerCluster from '../BrokerCluster';

vi.mock('../../../services/clusterService', () => ({
  listClusters: vi.fn(),
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
        name: 'broker-a',
        addr: '10.0.1.10:10911',
        version: '5.3.0',
        status: 'running',
        diskUsage: 62,
        tpsIn: 12580,
        tpsOut: 8340,
      },
      {
        name: 'broker-b',
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
    nameServers: [{ addr: 'nameserver-a', status: 'healthy' }],
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
  });

  it('should render the page title', () => {
    renderWithProviders(<BrokerCluster />);
    expect(screen.getByText('Broker 集群')).toBeInTheDocument();
  });

  it('should render create cluster button', () => {
    renderWithProviders(<BrokerCluster />);
    expect(screen.getByText('创建集群')).toBeInTheDocument();
  });

  it('should render reset button', () => {
    renderWithProviders(<BrokerCluster />);
    expect(screen.getByText('重置')).toBeInTheDocument();
  });

  it('should display broker tab with data from the API', async () => {
    renderWithProviders(<BrokerCluster />);
    // Default tab is broker - data is loaded asynchronously from the service
    const brokerA = await screen.findAllByText('broker-a');
    expect(brokerA.length).toBeGreaterThan(0);
    expect(screen.getAllByText('broker-b').length).toBeGreaterThan(0);
  });

  it('should display broker status tags', async () => {
    renderWithProviders(<BrokerCluster />);
    await screen.findAllByText('broker-a');
    const runningTags = screen.getAllByText('运行中');
    expect(runningTags.length).toBeGreaterThan(0);
    const readonlyTags = screen.getAllByText('只读');
    expect(readonlyTags.length).toBeGreaterThan(0);
  });

  it('should switch to NameServer tab on click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    await screen.findByText('broker-a');
    const nsTab = screen.getByText('NameServer 管理');
    await user.click(nsTab);
    // After clicking, NameServer data should be visible (name equals address, so it appears twice)
    expect(screen.getAllByText('nameserver-a').length).toBeGreaterThan(0);
  });

  it('should switch to Proxy tab on click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    await screen.findByText('broker-a');
    const proxyTab = screen.getByText('Proxy 管理');
    await user.click(proxyTab);
    // After clicking, Proxy data should be visible (proxy name equals its address, so it appears twice)
    expect(screen.getAllByText('10.0.1.30:8080').length).toBeGreaterThan(0);
  });

  it('should render config and restart action buttons', async () => {
    renderWithProviders(<BrokerCluster />);
    await screen.findByText('broker-a');
    const configButtons = screen.getAllByText('配置');
    expect(configButtons.length).toBeGreaterThan(0);
    const restartButtons = screen.getAllByText('重启');
    expect(restartButtons.length).toBeGreaterThan(0);
  });

  it('does not show mock infrastructure data when the API fails', async () => {
    vi.mocked(listClusters).mockRejectedValueOnce(new Error('network error'));
    renderWithProviders(<BrokerCluster />);
    await waitFor(() => {
      expect(listClusters).toHaveBeenCalledTimes(1);
    });
    expect(screen.queryByText('broker-a')).not.toBeInTheDocument();
    expect(screen.queryByText('broker-b')).not.toBeInTheDocument();
    expect(screen.queryByText('proxy-a')).not.toBeInTheDocument();
  });
});
