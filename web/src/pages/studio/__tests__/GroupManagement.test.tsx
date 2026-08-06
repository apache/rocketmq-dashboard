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
import type { ConsumerGroup } from '../../../api/metadata';
import * as consumerService from '../../../services/consumerService';
import GroupManagement from '../GroupManagement';

vi.mock('../../../services/consumerService', () => ({
  listConsumerGroups: vi.fn(),
  getConsumerProgress: vi.fn(),
  getConsumerSubscriptions: vi.fn(),
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

const makeGroup = (overrides: Partial<ConsumerGroup>): ConsumerGroup => ({
  name: 'order-consumer-group',
  namespace: 'default',
  clusterId: 'cluster-production',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 4,
  totalLag: 1280,
  subscribedTopics: ['ORDER_TOPIC'],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  createdAt: '2025-03-15 10:30:00',
  updatedAt: '2025-03-15 10:30:00',
  delaySeconds: 12,
  instances: [],
  ...overrides,
});

const groups: ConsumerGroup[] = [
  makeGroup({ name: 'order-consumer-group' }),
  makeGroup({ name: 'payment-consumer-group', totalLag: 0, onlineInstances: 2 }),
];

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );
};

describe('GroupManagement Page', () => {
  beforeEach(() => {
    vi.mocked(consumerService.listConsumerGroups).mockResolvedValue(groups);
    vi.mocked(consumerService.getConsumerProgress).mockResolvedValue([]);
    vi.mocked(consumerService.getConsumerSubscriptions).mockResolvedValue([]);
  });

  it('should render the page title', () => {
    renderWithProviders(<GroupManagement />);
    expect(screen.getByText('消费组管理')).toBeInTheDocument();
  });

  it('should render search input with placeholder', () => {
    renderWithProviders(<GroupManagement />);
    expect(screen.getByPlaceholderText('搜索消费组')).toBeInTheDocument();
  });

  it('should not render unsupported mutation actions in the global view', async () => {
    renderWithProviders(<GroupManagement />);
    await waitFor(() => {
      expect(screen.getByText('order-consumer-group')).toBeInTheDocument();
    });
    expect(screen.queryByText('创建消费组')).not.toBeInTheDocument();
    expect(screen.queryByText('配置')).not.toBeInTheDocument();
  });

  it('should render reset button', () => {
    renderWithProviders(<GroupManagement />);
    expect(screen.getByText('重置')).toBeInTheDocument();
  });

  it('should display consumer group data from the service in table', async () => {
    renderWithProviders(<GroupManagement />);
    await waitFor(() => {
      expect(screen.getByText('order-consumer-group')).toBeInTheDocument();
    });
    expect(screen.getByText('payment-consumer-group')).toBeInTheDocument();
  });

  it('should render detail action buttons for each row', async () => {
    renderWithProviders(<GroupManagement />);
    await waitFor(() => {
      expect(screen.getByText('order-consumer-group')).toBeInTheDocument();
    });
    const detailButtons = screen.getAllByText('详情');
    expect(detailButtons.length).toBeGreaterThan(0);
  });

  it('should filter groups by search text', async () => {
    const user = userEvent.setup();
    renderWithProviders(<GroupManagement />);
    await waitFor(() => {
      expect(screen.getByText('order-consumer-group')).toBeInTheDocument();
    });
    const searchInput = screen.getByPlaceholderText('搜索消费组');
    await user.type(searchInput, 'ORDER');
    expect(screen.getByText('order-consumer-group')).toBeInTheDocument();
    expect(screen.queryByText('payment-consumer-group')).not.toBeInTheDocument();
  });
});
