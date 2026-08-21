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
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
  gmtCreate: '2025-03-15 10:30:00',
  gmtModified: '2025-03-15 10:30:00',
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
    vi.restoreAllMocks();
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

  it('should render refresh button', () => {
    renderWithProviders(<GroupManagement />);
    expect(screen.getByText('刷新')).toBeInTheDocument();
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

  it('keeps subscriptions when progress loading fails', async () => {
    vi.mocked(consumerService.getConsumerSubscriptions).mockResolvedValue([
      {
        topic: 'AVAILABLE_SUBSCRIPTION',
        expression: '*',
        type: 'TAG',
        filterMode: 'TAG',
        consistency: 'consistent',
      },
    ]);
    vi.mocked(consumerService.getConsumerProgress).mockRejectedValue(new Error('unavailable'));

    const user = userEvent.setup();
    renderWithProviders(<GroupManagement />);
    await user.click(await screen.findByText('order-consumer-group'));

    expect(await screen.findByText('AVAILABLE_SUBSCRIPTION')).toBeInTheDocument();
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getAllByRole('tab')[2]);
    expect(document.querySelector('.ant-alert-error')).toBeInTheDocument();
  });

  it('keeps progress when subscription loading fails', async () => {
    vi.mocked(consumerService.getConsumerSubscriptions).mockRejectedValue(new Error('unavailable'));
    vi.mocked(consumerService.getConsumerProgress).mockResolvedValue([
      {
        topic: 'orders',
        broker: 'broker-a',
        queueId: 0,
        brokerOffset: 20,
        consumerOffset: 10,
        diffTotal: 10,
      },
    ]);

    const user = userEvent.setup();
    renderWithProviders(<GroupManagement />);
    await user.click(await screen.findByText('order-consumer-group'));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getAllByRole('tab')[2]);

    expect(await screen.findByText('broker-a')).toBeInTheDocument();
    expect(document.querySelector('.ant-alert-error')).toBeInTheDocument();
  });

  it('queues one refresh instead of overlapping an active group request', async () => {
    const initialGroups = createDeferred<ConsumerGroup[]>();
    const refreshedGroups = createDeferred<ConsumerGroup[]>();
    vi.mocked(consumerService.listConsumerGroups)
      .mockReturnValueOnce(initialGroups.promise)
      .mockReturnValueOnce(refreshedGroups.promise);
    renderWithProviders(<GroupManagement />);

    await waitFor(() => expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByText('刷新'));
    expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(1);

    initialGroups.resolve([makeGroup({ name: 'initial-group' })]);
    await waitFor(() => expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(2));

    refreshedGroups.resolve([makeGroup({ name: 'fresh-group' })]);
    expect(await screen.findByText('fresh-group')).toBeInTheDocument();
    expect(screen.queryByText('initial-group')).not.toBeInTheDocument();
  });

  it('polls only while auto refresh is enabled and the document is visible', async () => {
    const visibilityState = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    renderWithProviders(<GroupManagement />);

    await screen.findByText('order-consumer-group');
    expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(1);
    vi.useFakeTimers();

    const autoRefreshSwitch = screen.getByRole('switch');
    fireEvent.click(autoRefreshSwitch);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(1);

    visibilityState.mockReturnValue('visible');
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(2);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(3);

    fireEvent.click(autoRefreshSwitch);
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
      await vi.advanceTimersByTimeAsync(4000);
    });
    expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(3);
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
  it('scopes global group detail diagnostics to the record instance', async () => {
    vi.mocked(consumerService.listConsumerGroups).mockResolvedValue([
      makeGroup({ name: 'shared-group', instanceId: 'instance-2' }),
    ]);
    const user = userEvent.setup();
    renderWithProviders(<GroupManagement />);
    await screen.findByText('shared-group');
    await user.click(screen.getByText('详情'));

    await waitFor(() => {
      expect(consumerService.getConsumerSubscriptions).toHaveBeenCalledWith(
        'shared-group',
        'instance-2',
      );
      expect(consumerService.getConsumerProgress).toHaveBeenCalledWith(
        'shared-group',
        'instance-2',
      );
    });
  });

  it('shows a stopped status in details when no consumer instance is online', async () => {
    vi.mocked(consumerService.listConsumerGroups).mockResolvedValue([
      makeGroup({ name: 'offline-group', onlineInstances: 0 }),
    ]);
    const user = userEvent.setup();
    renderWithProviders(<GroupManagement />);
    await screen.findByText('offline-group');
    await user.click(screen.getByText('详情'));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('已停止')).toBeInTheDocument();
    expect(within(dialog).queryByText('在线')).not.toBeInTheDocument();
  });

  it('uses unique row keys for same-named groups from different instances', async () => {
    vi.mocked(consumerService.listConsumerGroups).mockResolvedValue([
      makeGroup({ name: 'shared-group', instanceId: 'instance-1' }),
      makeGroup({ name: 'shared-group', instanceId: 'instance-2' }),
    ]);
    const { container } = renderWithProviders(<GroupManagement />);
    await screen.findAllByText('shared-group');

    const rowKeys = Array.from(container.querySelectorAll('tbody tr[data-row-key]')).map((row) =>
      row.getAttribute('data-row-key'),
    );
    expect(rowKeys).toContain('instance-1\0shared-group');
    expect(rowKeys).toContain('instance-2\0shared-group');
    expect(new Set(rowKeys).size).toBe(rowKeys.length);
  });
});
