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
import type { ConsumerGroup, QueueProgress, SubscriptionEntry } from '../../../api/metadata';
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

const createDeferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
};

describe('GroupManagement Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(consumerService.listConsumerGroups).mockResolvedValue(groups);
    vi.mocked(consumerService.getConsumerProgress).mockResolvedValue([]);
    vi.mocked(consumerService.getConsumerSubscriptions).mockResolvedValue([]);
  });

  afterEach(() => {
    vi.useRealTimers();
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
    expect(screen.queryByText('查看分布')).not.toBeInTheDocument();
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

  it('keeps the latest group detail when an earlier request resolves last', async () => {
    const firstSubscriptions = createDeferred<SubscriptionEntry[]>();
    const firstProgress = createDeferred<QueueProgress[]>();
    const secondSubscriptions = createDeferred<SubscriptionEntry[]>();
    const secondProgress = createDeferred<QueueProgress[]>();
    vi.mocked(consumerService.getConsumerSubscriptions).mockImplementation((groupName) =>
      groupName === 'order-consumer-group'
        ? firstSubscriptions.promise
        : secondSubscriptions.promise,
    );
    vi.mocked(consumerService.getConsumerProgress).mockImplementation((groupName) =>
      groupName === 'order-consumer-group' ? firstProgress.promise : secondProgress.promise,
    );

    const user = userEvent.setup();
    renderWithProviders(<GroupManagement />);
    await screen.findByText('order-consumer-group');

    const detailButtons = screen.getAllByText('详情');
    await user.click(detailButtons[0]);
    await user.click(detailButtons[1]);

    await act(async () => {
      secondSubscriptions.resolve([
        {
          topic: 'SECOND_GROUP_TOPIC',
          expression: '*',
          type: 'TAG',
          filterMode: 'TAG',
          consistency: 'consistent',
        },
      ]);
      secondProgress.resolve([]);
    });
    expect(await screen.findByText('SECOND_GROUP_TOPIC')).toBeInTheDocument();

    await act(async () => {
      firstSubscriptions.resolve([
        {
          topic: 'FIRST_GROUP_TOPIC',
          expression: '*',
          type: 'TAG',
          filterMode: 'TAG',
          consistency: 'consistent',
        },
      ]);
      firstProgress.resolve([]);
    });
    expect(screen.getByText('SECOND_GROUP_TOPIC')).toBeInTheDocument();
    expect(screen.queryByText('FIRST_GROUP_TOPIC')).not.toBeInTheDocument();
  });

  it('keeps the latest group list when an earlier refresh resolves last', async () => {
    const initialGroups = createDeferred<ConsumerGroup[]>();
    const refreshedGroups = createDeferred<ConsumerGroup[]>();
    vi.mocked(consumerService.listConsumerGroups)
      .mockReturnValueOnce(initialGroups.promise)
      .mockReturnValueOnce(refreshedGroups.promise);
    vi.useFakeTimers();
    renderWithProviders(<GroupManagement />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    fireEvent.click(screen.getByText('重置'));

    await act(async () => {
      refreshedGroups.resolve([makeGroup({ name: 'fresh-group' })]);
      await Promise.resolve();
    });
    expect(screen.getByText('fresh-group')).toBeInTheDocument();

    await act(async () => {
      initialGroups.resolve([makeGroup({ name: 'stale-group' })]);
      await Promise.resolve();
    });
    expect(screen.getByText('fresh-group')).toBeInTheDocument();
    expect(screen.queryByText('stale-group')).not.toBeInTheDocument();
  });

  it('polls only while auto refresh is enabled', async () => {
    vi.useFakeTimers();
    renderWithProviders(<GroupManagement />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(1);

    const autoRefreshSwitch = screen.getByRole('switch');
    fireEvent.click(autoRefreshSwitch);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(2);

    fireEvent.click(autoRefreshSwitch);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(4000);
    });
    expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(2);
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
  it('uses unique row keys for same-named groups from different instances', async () => {
    vi.mocked(consumerService.listConsumerGroups).mockResolvedValue([
      makeGroup({ name: 'shared-group', instanceId: 'instance-a' }),
      makeGroup({ name: 'shared-group', instanceId: 'instance-b' }),
    ]);
    const { container } = renderWithProviders(<GroupManagement />);
    await screen.findAllByText('shared-group');

    const rowKeys = Array.from(container.querySelectorAll('tbody tr[data-row-key]')).map((row) =>
      row.getAttribute('data-row-key'),
    );
    expect(rowKeys).toContain('instance-a\0shared-group');
    expect(rowKeys).toContain('instance-b\0shared-group');
    expect(new Set(rowKeys).size).toBe(rowKeys.length);
  });

});
