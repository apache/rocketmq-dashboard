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
    expect(screen.getByText('RocketMQ 集群')).toBeInTheDocument();
  });

  it('should render create cluster button', () => {
    renderWithProviders(<BrokerCluster />);
    expect(screen.getByText('新建集群')).toBeInTheDocument();
  });

  it('should render reset button', () => {
    renderWithProviders(<BrokerCluster />);
    expect(screen.getByText('重置')).toBeInTheDocument();
  });

  it('should display broker tab with mock data', () => {
    renderWithProviders(<BrokerCluster />);
    // Default tab is broker - check for mock broker data
    expect(screen.getByText('broker-a')).toBeInTheDocument();
    expect(screen.getByText('broker-b')).toBeInTheDocument();
  });

  it('should display broker status tags', () => {
    renderWithProviders(<BrokerCluster />);
    const runningTags = screen.getAllByText('运行中');
    expect(runningTags.length).toBeGreaterThan(0);
    const readonlyTags = screen.getAllByText('只读');
    expect(readonlyTags.length).toBeGreaterThan(0);
  });

  it('should switch to NameServer tab on click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    const nsTab = screen.getByText('NameServer 管理');
    await user.click(nsTab);
    // After clicking, NameServer data should be visible
    expect(screen.getByText('nameserver-a')).toBeInTheDocument();
  });

  it('should switch to Proxy tab on click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BrokerCluster />);
    const proxyTab = screen.getByText('Proxy 管理');
    await user.click(proxyTab);
    // After clicking, Proxy data should be visible
    expect(screen.getByText('proxy-a')).toBeInTheDocument();
  });

  it('should render config and restart action buttons', () => {
    renderWithProviders(<BrokerCluster />);
    const configButtons = screen.getAllByText('配置');
    expect(configButtons.length).toBeGreaterThan(0);
    const restartButtons = screen.getAllByText('重启');
    expect(restartButtons.length).toBeGreaterThan(0);
  });
});
