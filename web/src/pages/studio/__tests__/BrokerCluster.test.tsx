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

import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import BrokerCluster from '../BrokerCluster';

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

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );
};

describe('BrokerCluster Page', () => {
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

  it('should show an explicit unavailable state instead of mock broker data', () => {
    renderWithProviders(<BrokerCluster />);
    expect(screen.getByText('当前版本尚未接入真实集群拓扑接口，已停止展示模拟 Broker / NameServer / Proxy 数据。')).toBeInTheDocument();
    expect(screen.queryByText('broker-a')).not.toBeInTheDocument();
    expect(screen.queryByText('broker-b')).not.toBeInTheDocument();
  });

  it('should not render row status tags without real broker data', () => {
    renderWithProviders(<BrokerCluster />);
    expect(screen.queryByText('运行中')).not.toBeInTheDocument();
    expect(screen.queryByText('只读')).not.toBeInTheDocument();
  });

  it('should switch to NameServer tab on click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    const nsTab = screen.getByText('NameServer 管理');
    await user.click(nsTab);
    expect(
      screen.getAllByText('当前版本尚未接入真实集群拓扑接口，已停止展示模拟 Broker / NameServer / Proxy 数据。').length,
    ).toBeGreaterThan(0);
    expect(screen.queryByText('nameserver-a')).not.toBeInTheDocument();
  });

  it('should switch to Proxy tab on click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    const proxyTab = screen.getByText('Proxy 管理');
    await user.click(proxyTab);
    expect(
      screen.getAllByText('当前版本尚未接入真实集群拓扑接口，已停止展示模拟 Broker / NameServer / Proxy 数据。').length,
    ).toBeGreaterThan(0);
    expect(screen.queryByText('proxy-a')).not.toBeInTheDocument();
  });

  it('should not render row action buttons without real infrastructure data', () => {
    renderWithProviders(<BrokerCluster />);
    expect(screen.queryByText('配置')).not.toBeInTheDocument();
    expect(screen.queryByText('重启')).not.toBeInTheDocument();
  });
});
