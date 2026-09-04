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
import { render, screen } from '@testing-library/react';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import type { DashboardData } from '../../../api/metrics';
import { LangProvider } from '../../../i18n/LangContext';
import DashboardTrafficInsights from '../DashboardTrafficInsights';

const dashboard: DashboardData = {
  stats: {
    totalClusters: 3,
    healthyClusters: 2,
    totalBrokers: 8,
    totalProxies: 3,
    totalNameServers: 3,
    totalTopics: 100,
    totalConsumerGroups: 30,
    totalMessagesToday: 1_000_000,
    messagesPerSecond: 3200,
    tpsIn: 1600,
    tpsOut: 1600,
  },
  clusters: [
    {
      id: 'prod',
      name: 'prod',
      type: 'V5_PROXY_CLUSTER',
      status: 'healthy',
      brokers: 4,
      proxies: 2,
      topics: 70,
      groups: 20,
      tpsIn: 1400,
      tpsOut: 1400,
      version: '5.2.0',
      throughput: [800, 900, 1000, 1300, 1400, 1500],
    },
    {
      id: 'canary',
      name: 'canary',
      type: 'V5_PROXY_CLUSTER',
      status: 'warning',
      brokers: 2,
      proxies: 1,
      topics: 20,
      groups: 8,
      tpsIn: 180,
      tpsOut: 180,
      version: '5.2.0',
      throughput: [220, 230, 240, 180, 170, 160],
    },
    {
      id: 'idle',
      name: 'idle',
      type: 'V4_NAMESRV',
      status: 'healthy',
      brokers: 2,
      proxies: null,
      topics: 10,
      groups: 2,
      tpsIn: 0,
      tpsOut: 0,
      version: '4.9.8',
      throughput: [0, 0, 0, 0],
    },
  ],
};

const renderPanel = (data = dashboard) =>
  render(
    <App>
      <LangProvider>
        <DashboardTrafficInsights dashboard={data} />
      </LangProvider>
    </App>,
  );

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

describe('DashboardTrafficInsights', () => {
  it('renders traffic findings and summary cards without duplicating cluster rows', () => {
    renderPanel();

    expect(screen.getByText('流量洞察')).toBeInTheDocument();
    expect(screen.getByText('严重')).toBeInTheDocument();
    expect(screen.getByText('活跃集群')).toBeInTheDocument();
    expect(screen.getByText('2/3')).toBeInTheDocument();
    expect(screen.getByText('最高流量占比')).toBeInTheDocument();
    expect(screen.getAllByText((_, element) => element?.textContent === '88.6%')).not.toHaveLength(
      0,
    );
    expect(screen.getByText('异常流量')).toBeInTheDocument();
    expect(screen.getAllByText((_, element) => element?.textContent === '360/s')).not.toHaveLength(
      0,
    );

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByText(/prod 承载 88.6% 流量/u)).toBeInTheDocument();
    expect(screen.getByText(/canary 在非健康状态下承载流量/u)).toBeInTheDocument();
  });

  it('shows an empty state when the dashboard has no cluster rows', () => {
    renderPanel({
      stats: {
        totalClusters: 0,
        healthyClusters: 0,
        totalBrokers: 0,
        totalProxies: 0,
        totalNameServers: 0,
        totalTopics: 0,
        totalConsumerGroups: 0,
        totalMessagesToday: 0,
        messagesPerSecond: 0,
        tpsIn: 0,
        tpsOut: 0,
      },
      clusters: [],
    });

    expect(screen.getByText('流量洞察')).toBeInTheDocument();
    expect(screen.getByText('暂无集群流量数据')).toBeInTheDocument();
    expect(screen.getByText('未检测到活跃流量')).toBeInTheDocument();
  });
});
