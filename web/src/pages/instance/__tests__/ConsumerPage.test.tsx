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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ConsumerGroup } from '../../../api/metadata';
import { LangProvider } from '../../../i18n/LangContext';
import * as consumerService from '../../../services/consumerService';
import ConsumerPage from '../consumer';

vi.mock('../../../services/consumerService', () => ({
  batchDeleteConsumerGroups: vi.fn(),
  createConsumerGroup: vi.fn(),
  deleteConsumerGroup: vi.fn(),
  getConsumerGroup: vi.fn(),
  getConsumerProgress: vi.fn(),
  getConsumerSubscriptions: vi.fn(),
  listConsumerGroups: vi.fn(),
  resetConsumerOffset: vi.fn(),
}));
vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([]),
}));

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
  Object.defineProperty(URL, 'createObjectURL', {
    writable: true,
    value: vi.fn(() => 'blob:consumer-group-export'),
  });
  Object.defineProperty(URL, 'revokeObjectURL', {
    writable: true,
    value: vi.fn(),
  });
});

const group: ConsumerGroup = {
  name: 'remote-cg',
  namespace: 'remote-ns',
  clusterId: 'cluster-a',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 1,
  totalLag: 10,
  subscribedTopics: ['remote-topic'],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  createdAt: '2026-07-23T00:00:00Z',
  updatedAt: '2026-07-23T00:00:00Z',
  delaySeconds: 3,
  instances: [],
};

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </LangProvider>
    </App>,
  );

describe('Consumer page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(consumerService.listConsumerGroups).mockResolvedValue([group]);
    vi.mocked(consumerService.createConsumerGroup).mockImplementation(
      async (data: Partial<ConsumerGroup>) =>
        ({
          ...group,
          ...data,
          namespace: 'default',
          clusterId: 'server-cluster',
          onlineInstances: 0,
          totalLag: 0,
          delaySeconds: 0,
          instances: [],
          subscribedTopics: data.subscribedTopics ?? [],
          createdAt: '2026-07-24T00:00:00Z',
          updatedAt: '2026-07-24T00:00:00Z',
        }) as ConsumerGroup,
    );
    vi.mocked(consumerService.getConsumerProgress).mockResolvedValue([
      {
        broker: 'broker-a',
        queueId: 0,
        brokerOffset: 100,
        consumerOffset: 90,
        diffTotal: 10,
      },
    ]);
    vi.mocked(consumerService.getConsumerSubscriptions).mockResolvedValue([
      {
        topic: 'remote-topic',
        expression: '*',
        type: 'NORMAL',
        filterMode: '全量',
        consistency: '一致',
      },
    ]);
  });

  it('loads consumer groups through the service layer', async () => {
    renderWithProviders(<ConsumerPage />);

    expect(await screen.findByText('remote-cg')).toBeInTheDocument();
    expect(screen.getByText('Push')).toBeInTheDocument();
    expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(1);
  });

  it('downloads the currently filtered consumer groups when exporting', async () => {
    const user = userEvent.setup();
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(vi.fn());
    let exportedBlob: Blob | undefined;
    vi.mocked(URL.createObjectURL).mockImplementation((blob) => {
      exportedBlob = blob as Blob;
      return 'blob:consumer-group-export';
    });
    vi.mocked(consumerService.listConsumerGroups).mockResolvedValue([
      {
        ...group,
        name: 'orders-cg',
        namespace: 'trade',
        subscribedTopics: ['orders-topic', 'payments,topic'],
      },
      {
        ...group,
        name: 'users-cg',
        namespace: '=formula-risk',
        subscribedTopics: ['users-topic'],
      },
    ]);
    renderWithProviders(<ConsumerPage />);

    expect(await screen.findByText('orders-cg')).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText('搜索 Group 名称或 Topic'), 'orders');
    await waitFor(() => expect(screen.queryByText('users-cg')).not.toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /导出/ }));

    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:consumer-group-export');
    expect(
      document.querySelector('a[download^="rocketmq-consumer-groups-"]'),
    ).not.toBeInTheDocument();

    expect(exportedBlob).toBeDefined();
    const csv = await exportedBlob!.text();
    expect(csv).toContain('"orders-cg"');
    expect(csv).toContain('"orders-topic;payments,topic"');
    expect(csv).not.toContain('users-cg');
    clickSpy.mockRestore();
  });

  it('loads subscriptions and progress when opening a group', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /详情/ }));

    await waitFor(() =>
      expect(consumerService.getConsumerSubscriptions).toHaveBeenCalledWith('remote-cg'),
    );
    await waitFor(() =>
      expect(consumerService.getConsumerProgress).toHaveBeenCalledWith('remote-cg'),
    );
    expect(consumerService.getConsumerGroup).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getAllByText('remote-topic').length).toBeGreaterThan(0));
  });

  it('highlights inconsistent subscriptions and refreshes the check result', async () => {
    vi.mocked(consumerService.getConsumerSubscriptions)
      .mockResolvedValueOnce([
        {
          topic: 'remote-topic',
          expression: '*',
          type: 'NORMAL',
          filterMode: '全量',
          consistency: 'consistent',
        },
        {
          topic: 'stale-topic',
          expression: 'important',
          type: 'NORMAL',
          filterMode: 'Tag 过滤',
          consistency: 'inconsistent',
        },
      ])
      .mockResolvedValueOnce([
        {
          topic: 'remote-topic',
          expression: '*',
          type: 'NORMAL',
          filterMode: '全量',
          consistency: 'consistent',
        },
        {
          topic: 'stale-topic',
          expression: 'important',
          type: 'NORMAL',
          filterMode: 'Tag 过滤',
          consistency: 'consistent',
        },
      ]);

    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /详情/ }));

    expect(await screen.findByText('发现 1 个订阅配置不一致')).toBeInTheDocument();
    expect(screen.getByText('consistent').closest('.ant-tag')).toHaveClass('ant-tag-green');
    expect(screen.getByText('inconsistent').closest('.ant-tag')).toHaveClass('ant-tag-orange');
    await user.click(screen.getByRole('checkbox', { name: '仅看不一致' }));
    expect(screen.getByText('stale-topic')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '重新检查' }));

    expect(await screen.findByText('全部 2 个订阅配置一致')).toBeInTheDocument();
    expect(consumerService.getConsumerSubscriptions).toHaveBeenCalledTimes(2);
  });

  it('keeps unknown consistency values separate from mismatches', async () => {
    vi.mocked(consumerService.getConsumerSubscriptions).mockResolvedValue([
      {
        topic: 'unknown-topic',
        expression: '*',
        type: 'NORMAL',
        filterMode: '全量',
        consistency: 'UNKNOWN',
      },
    ]);

    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /详情/ }));

    expect(await screen.findByText('1 个订阅配置状态未知')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: '仅看不一致' })).toBeDisabled();
  });

  it('reports a failed consistency check without presenting stale data as current', async () => {
    vi.mocked(consumerService.getConsumerSubscriptions).mockRejectedValue(
      new Error('request failed'),
    );

    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /详情/ }));

    expect(await screen.findByText('订阅一致性检查失败，当前保留上次检查结果')).toBeInTheDocument();
  });

  it('keeps per-row state when consumer group CSV import partially fails', async () => {
    vi.mocked(consumerService.listConsumerGroups).mockResolvedValue([]);
    vi.mocked(consumerService.createConsumerGroup).mockImplementation(
      async (data: Partial<ConsumerGroup>) => {
        if (data.name === 'cg-fail') throw new Error('broker rejected group');
        return {
          ...group,
          ...data,
          name: data.name ?? '',
          namespace: 'default',
          clusterId: 'server-cluster',
          onlineInstances: 0,
          totalLag: 0,
          delaySeconds: 0,
          instances: [],
          subscribedTopics: data.subscribedTopics ?? [],
          createdAt: '2026-07-24T00:00:00Z',
          updatedAt: '2026-07-24T00:00:00Z',
        } as ConsumerGroup;
      },
    );

    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await screen.findByText(/共 0 个 Group/);
    const csv = [
      '"Name","Subscription Mode","Consume Type","Retry Max Times","Subscription Data Type","Delivery Order Type"',
      '"cg-ok","Push","CLUSTERING","16","NORMAL",""',
      '"cg-fail","Pop","BROADCASTING","4","FIFO","PARTITON_ORDER"',
    ].join('\n');
    await user.upload(
      screen.getByTestId('consumer-group-import-file'),
      new File([csv], 'groups.csv'),
    );
    expect(await screen.findByText('检测到 2 个 Group，将按顺序调用创建接口')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '开始导入' }));

    await waitFor(() => expect(consumerService.createConsumerGroup).toHaveBeenCalledTimes(2));
    expect(consumerService.createConsumerGroup).toHaveBeenNthCalledWith(1, {
      name: 'cg-ok',
      subscriptionMode: 'Push',
      consumeType: 'CLUSTERING',
      retryMaxTimes: 16,
      subscriptionDataType: 'NORMAL',
      subscribedTopics: [],
    });
    expect(await screen.findByText('已导入 1 个 Group，1 个失败')).toBeInTheDocument();
    expect(screen.getByText('broker rejected group')).toBeInTheDocument();
    expect(screen.getAllByText('cg-ok').length).toBeGreaterThan(0);
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /重试失败项/ })).toBeInTheDocument(),
    );
  });
});
