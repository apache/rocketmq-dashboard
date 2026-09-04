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

import { App, Modal, message } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ConsumerGroup } from '../../../api/metadata';
import * as instanceService from '../../../services/instanceService';
import { LangProvider } from '../../../i18n/LangContext';
import * as consumerService from '../../../services/consumerService';
import ConsumerPage from '../consumer';

vi.mock('../../../services/consumerService', () => ({
  batchDeleteConsumerGroups: vi.fn(),
  createConsumerGroup: vi.fn(),
  deleteConsumerGroup: vi.fn(),
  exportConsumerGroups: vi.fn(),
  getConsumerGroup: vi.fn(),
  getConsumerProgress: vi.fn(),
  getConsumerStack: vi.fn(),
  getConsumerSubscriptions: vi.fn(),
  getConsumerGroupSettings: vi.fn(),
  importConsumerGroups: vi.fn(),
  listAllConsumerGroups: vi.fn(),
  previewConsumerOffsetReset: vi.fn(),
  updateConsumerGroupSettings: vi.fn(),
  listConsumerGroupPage: vi.fn(),
  refreshConsumerGroup: vi.fn(),
  resetConsumerOffset: vi.fn(),
}));
const instanceServiceMocks = vi.hoisted(() => ({ listInstances: vi.fn() }));

vi.mock('../../../services/instanceService', () => instanceServiceMocks);

const installBrowserMocks = () => {
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
};

const group: ConsumerGroup = {
  name: 'remote-cg',
  namespace: 'remote-ns',
  clusterId: 'cluster-a',
  instanceId: 'instance-1',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 1,
  totalLag: 10,
  subscribedTopics: ['remote-topic'],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  gmtCreate: '2026-07-23T00:00:00Z',
  gmtModified: '2026-07-23T00:00:00Z',
  delaySeconds: 3,
  instances: [],
};

const buildGroups = (count: number): ConsumerGroup[] =>
  Array.from({ length: count }, (_, index) => ({
    ...group,
    name: `remote-cg-${String(index + 1).padStart(2, '0')}`,
  }));

const groupPage = (
  items: ConsumerGroup[],
  overrides: Partial<{ total: number; page: number; size: number }> = {},
) => ({
  items,
  total: items.length,
  page: 1,
  size: 20,
  ...overrides,
});

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => {
    resolve = complete;
  });
  return { promise, resolve };
};

const renderWithProviders = (ui: React.ReactElement, initialEntry = '/instance/consumer') =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter initialEntries={[initialEntry]}>{ui}</MemoryRouter>
      </LangProvider>
    </App>,
  );

beforeAll(installBrowserMocks);

describe('Consumer page', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.resetAllMocks();
    installBrowserMocks();
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      {
        id: 1,
        name: 'instance-1',
        remark: '',
        type: 'PROXY_CLUSTER',
        endpoint: '10.0.0.1:8080',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      },
    ]);
    vi.mocked(consumerService.listConsumerGroupPage).mockResolvedValue(groupPage([group]));
    vi.mocked(consumerService.listAllConsumerGroups).mockResolvedValue([group]);
    vi.mocked(consumerService.exportConsumerGroups).mockResolvedValue('"Name"\n"remote-cg"');
    vi.mocked(consumerService.refreshConsumerGroup).mockResolvedValue({
      ...group,
      totalLag: 42,
      delaySeconds: 7,
    });
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
          gmtCreate: '2026-07-24T00:00:00Z',
          gmtModified: '2026-07-24T00:00:00Z',
        }) as ConsumerGroup,
    );
    vi.mocked(consumerService.importConsumerGroups).mockResolvedValue({
      imported: 1,
      failed: 0,
      groups: [
        {
          ...group,
          name: 'imported-cg',
          namespace: 'default',
          clusterId: 'server-cluster',
          onlineInstances: 0,
          totalLag: 0,
          delaySeconds: 0,
          instances: [],
          subscribedTopics: [],
          gmtCreate: '2026-07-24T00:00:00Z',
          gmtModified: '2026-07-24T00:00:00Z',
        },
      ],
      failures: [],
    });
    vi.mocked(consumerService.getConsumerProgress).mockResolvedValue([
      {
        topic: 'remote-topic',
        broker: 'broker-a',
        queueId: 0,
        brokerOffset: 100,
        consumerOffset: 90,
        diffTotal: 10,
      },
      {
        topic: '%RETRY%remote-cg',
        broker: 'broker-a',
        queueId: 0,
        brokerOffset: 5,
        consumerOffset: 5,
        diffTotal: 0,
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
    vi.mocked(consumerService.previewConsumerOffsetReset).mockResolvedValue({
      instanceId: 'instance-1',
      groupName: 'remote-cg',
      topic: 'remote-topic',
      timestamp: 1784246400000,
      complete: true,
      allowReset: true,
      queueCount: 1,
      warningCount: 1,
      rewindQueueCount: 1,
      fastForwardQueueCount: 0,
      currentTotalLag: 30,
      projectedTotalLag: 40,
      totalOffsetDelta: -10,
      warnings: ['1 queue(s) will move backward and may replay consumed messages'],
      queues: [
        {
          topic: 'remote-topic',
          broker: 'broker-a',
          queueId: 0,
          minOffset: 0,
          maxOffset: 200,
          brokerOffset: 120,
          consumerOffset: 90,
          targetOffset: 80,
          currentLag: 30,
          projectedLag: 40,
          offsetDelta: -10,
          riskLevel: 'WARNING',
          message: 'Replays 10 message(s)',
        },
      ],
    });
    vi.mocked(consumerService.getConsumerStack).mockResolvedValue({
      groupName: 'remote-cg',
      clientId: 'client-1',
      capturedAt: '2026-07-23T00:00:00Z',
      threadCount: 1,
      threads: [
        {
          threadName: 'ConsumeMessageThread_1',
          threadId: 12,
          state: 'RUNNABLE',
          blockedTime: 0,
          waitedTime: 0,
          stackTrace: ['org.apache.demo.OrderListener.consume(OrderListener.java:42)'],
        },
      ],
    });
    instanceServiceMocks.listInstances.mockResolvedValue([
      {
        id: 1,
        name: 'instance-1',
        remark: '',
        type: 'PROXY_CLUSTER',
        endpoint: '10.0.0.1:8080',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      },
    ]);
  });

  it('loads consumer groups through the service layer', async () => {
    renderWithProviders(<ConsumerPage />);

    expect(await screen.findByText('remote-cg')).toBeInTheDocument();
    expect(screen.getByText('Push')).toBeInTheDocument();
    expect(consumerService.listConsumerGroupPage).toHaveBeenCalledTimes(1);
    expect(consumerService.listConsumerGroupPage).toHaveBeenCalledWith({
      instanceId: 'instance-1',
      page: 1,
      pageSize: 20,
      search: undefined,
      subscriptionMode: undefined,
    });
  });

  afterEach(() => {
    cleanup();
    Modal.destroyAll();
    message.destroy();
  });

  it('clamps back to a valid page when the current page becomes empty after a delete', async () => {
    const user = userEvent.setup();
    let call = 0;
    vi.mocked(consumerService.listConsumerGroupPage).mockImplementation(async (params) => {
      call += 1;
      if (params?.page === 2) {
        // Page 2 went out of range (its rows were deleted server-side).
        return groupPage([], { total: 15, page: 2 });
      }
      // First load reports 45 rows (3 pages); the clamp re-fetch reports the shrunk 15.
      return call === 1
        ? groupPage(buildGroups(20), { total: 45, size: 20 })
        : groupPage(buildGroups(15), { total: 15, size: 20 });
    });
    renderWithProviders(<ConsumerPage />);

    expect(await screen.findByText('remote-cg-01')).toBeInTheDocument();

    const secondPage = document.querySelector('.ant-pagination-item-2');
    expect(secondPage).not.toBeNull();
    await user.click(secondPage as HTMLElement);

    // The empty out-of-range page is corrected: the list reloads page 1.
    await waitFor(() =>
      expect(consumerService.listConsumerGroupPage).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 1, pageSize: 20 }),
      ),
    );
  });

  it('reloads the server page after deleting one consumer group', async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(Modal, 'confirm').mockImplementation((config) => {
      void config.onOk?.();
      return { destroy: vi.fn(), update: vi.fn() } as unknown as ReturnType<typeof Modal.confirm>;
    });
    vi.mocked(consumerService.deleteConsumerGroup).mockResolvedValue(undefined);
    vi.mocked(consumerService.listConsumerGroupPage)
      .mockResolvedValueOnce(groupPage([group]))
      .mockResolvedValueOnce(groupPage([]));
    renderWithProviders(<ConsumerPage />);

    const row = await screen.findByRole('row', { name: /remote-cg/ });
    await user.click(within(row).getByRole('button', { name: /删除/ }));

    await waitFor(() =>
      expect(consumerService.deleteConsumerGroup).toHaveBeenCalledWith('remote-cg', 'instance-1'),
    );
    await waitFor(() => expect(consumerService.listConsumerGroupPage).toHaveBeenCalledTimes(2));
    expect(screen.getByText('管理消费者组订阅关系与消费进度，共 0 个 Group')).toBeInTheDocument();
    confirmSpy.mockRestore();
  });

  it('moves back from an emptied last consumer group page after batch deletion', async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(Modal, 'confirm').mockImplementation((config) => {
      void config.onOk?.();
      return { destroy: vi.fn(), update: vi.fn() } as unknown as ReturnType<typeof Modal.confirm>;
    });
    const firstPage = buildGroups(20).map((item, index) => ({
      ...item,
      name: `cg-${String(index + 1).padStart(2, '0')}`,
    }));
    const lastGroup = { ...group, name: 'cg-21' };
    let deletedLastPage = false;
    vi.mocked(consumerService.listConsumerGroupPage).mockImplementation(async (params) => {
      if (params?.page === 2) {
        return groupPage(deletedLastPage ? [] : [lastGroup], {
          total: deletedLastPage ? 20 : 21,
          page: 2,
          size: 20,
        });
      }
      return groupPage(firstPage, { total: deletedLastPage ? 20 : 21, page: 1, size: 20 });
    });
    vi.mocked(consumerService.batchDeleteConsumerGroups).mockImplementation(async () => {
      deletedLastPage = true;
      return {
        deleted: ['cg-21'],
        failed: [],
      };
    });
    renderWithProviders(<ConsumerPage />);

    expect(await screen.findByText('cg-01')).toBeInTheDocument();
    await user.click(document.querySelector('.ant-pagination-item-2') as HTMLElement);
    expect(await screen.findByText('cg-21')).toBeInTheDocument();
    await user.click(screen.getAllByRole('checkbox')[0]);
    await user.click(screen.getByRole('button', { name: /删除 \(1\)$/ }));

    await waitFor(() =>
      expect(consumerService.batchDeleteConsumerGroups).toHaveBeenCalledWith(
        ['cg-21'],
        'instance-1',
      ),
    );
    await waitFor(() => expect(screen.queryByText('cg-21')).not.toBeInTheDocument());
    expect(screen.getByText('cg-01')).toBeInTheDocument();
    expect(consumerService.listConsumerGroupPage).toHaveBeenLastCalledWith({
      instanceId: 'instance-1',
      search: undefined,
      page: 1,
      pageSize: 20,
    });
    confirmSpy.mockRestore();
  });

  it('keeps failed consumer groups selected after a partially successful batch deletion', async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(Modal, 'confirm').mockImplementation((config) => {
      void config.onOk?.();
      return { destroy: vi.fn(), update: vi.fn() } as unknown as ReturnType<typeof Modal.confirm>;
    });
    const groups = ['cg-01', 'cg-02', 'cg-03'].map((name) => ({ ...group, name }));
    vi.mocked(consumerService.listConsumerGroupPage)
      .mockResolvedValueOnce(groupPage(groups))
      .mockResolvedValueOnce(groupPage([{ ...group, name: 'cg-02' }]));
    vi.mocked(consumerService.batchDeleteConsumerGroups).mockResolvedValue({
      deleted: ['cg-01', 'cg-03'],
      failed: ['cg-02'],
    });
    renderWithProviders(<ConsumerPage />);

    expect(await screen.findByText('cg-01')).toBeInTheDocument();
    await user.click(screen.getAllByRole('checkbox')[0]);
    await user.click(screen.getByRole('button', { name: /删除 \(3\)$/ }));

    await waitFor(() =>
      expect(consumerService.batchDeleteConsumerGroups).toHaveBeenCalledWith(
        ['cg-01', 'cg-02', 'cg-03'],
        'instance-1',
      ),
    );
    await waitFor(() => expect(screen.queryByText('cg-01')).not.toBeInTheDocument());
    expect(screen.getByText('cg-02')).toBeInTheDocument();
    expect(screen.queryByText('cg-03')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /删除 \(1\)$/ })).toBeInTheDocument();
    expect(consumerService.listConsumerGroupPage).toHaveBeenLastCalledWith({
      instanceId: 'instance-1',
      search: undefined,
      page: 1,
      pageSize: 20,
    });
    confirmSpy.mockRestore();
  });

  it('submits the canonical global delivery order type', async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(Modal, 'confirm').mockImplementation((config) => {
      void config.onOk?.();
      return { destroy: vi.fn(), update: vi.fn() } as unknown as ReturnType<typeof Modal.confirm>;
    });
    renderWithProviders(<ConsumerPage />);

    await screen.findByText('remote-cg');
    const createButton = screen.getByRole('button', { name: '创建 Group' });
    await waitFor(() => expect(createButton).toBeEnabled());
    await user.click(createButton);
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Group 名称'), 'cg-global-orders');

    const dataTypeSelect = within(dialog).getByRole('combobox', { name: '订阅组类型' });
    fireEvent.mouseDown(dataTypeSelect.parentElement!);
    await user.click(
      await screen.findByText('顺序消息', { selector: '.ant-select-item-option-content' }),
    );

    const orderTypeSelect = within(dialog).getByRole('combobox', { name: '顺序类型' });
    fireEvent.mouseDown(orderTypeSelect.parentElement!);
    await user.click(
      await screen.findByText('全局顺序', { selector: '.ant-select-item-option-content' }),
    );
    await user.click(within(dialog).getByRole('button', { name: /创\s*建/ }));

    await waitFor(() =>
      expect(consumerService.createConsumerGroup).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'cg-global-orders',
          subscriptionDataType: 'FIFO',
          deliveryOrderType: 'MESSAGES_ORDER',
        }),
      ),
    );
    expect(confirmSpy).toHaveBeenCalledTimes(1);
    confirmSpy.mockRestore();
  });

  it('prefills the group search from the ?group= query parameter', async () => {
    renderWithProviders(<ConsumerPage />, '/instance/consumer?group=remote-cg');

    expect(await screen.findByText('remote-cg')).toBeInTheDocument();
    expect(consumerService.listConsumerGroupPage).toHaveBeenCalledWith(
      expect.objectContaining({ search: 'remote-cg' }),
    );
  });

  it('downloads all consumer groups matching the current filters when exporting', async () => {
    const user = userEvent.setup();
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(vi.fn());
    let exportedBlob: Blob | undefined;
    vi.mocked(URL.createObjectURL).mockImplementation((blob) => {
      exportedBlob = blob as Blob;
      return 'blob:consumer-group-export';
    });
    const currentPageGroups = [
      {
        ...group,
        name: 'orders-cg',
        namespace: '\r=formula-risk',
        subscribedTopics: ['orders-topic', 'payments,topic'],
      },
    ];
    const archivedGroup = {
      ...group,
      name: 'orders-cg-archive',
      namespace: '=archive',
      subscribedTopics: ['orders-topic'],
    };
    const exportGroups = [
      ...currentPageGroups,
      archivedGroup,
      {
        ...group,
        name: 'users-cg',
        namespace: '=formula-risk',
        subscribedTopics: ['users-topic'],
      },
    ];
    vi.mocked(consumerService.listConsumerGroupPage).mockImplementation(async (params) => {
      const filtered = params?.search
        ? exportGroups.filter((item) => item.name.includes(params.search ?? ''))
        : exportGroups;
      return groupPage(filtered.slice(0, 1), {
        total: filtered.length,
        page: params?.page ?? 1,
        size: params?.pageSize ?? 20,
      });
    });
    vi.mocked(consumerService.exportConsumerGroups).mockResolvedValue(
      [
        '"Name","Namespace","Subscribed Topics"',
        '"orders-cg","remote-ns","orders-topic;payments,topic"',
        '"orders-cg-archive","\'=archive","orders-topic"',
        '"orders-cg-formula","\'\r=formula-risk","orders-topic"',
      ].join('\n'),
    );
    renderWithProviders(<ConsumerPage />);

    expect(await screen.findByText('orders-cg')).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText('搜索 Group 名称或 Topic'), 'orders');
    await waitFor(() => expect(screen.queryByText('users-cg')).not.toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /导出/ }));

    await waitFor(() =>
      expect(consumerService.exportConsumerGroups).toHaveBeenCalledWith({
        instanceId: 'instance-1',
        search: 'orders',
        subscriptionMode: undefined,
      }),
    );
    expect(consumerService.listAllConsumerGroups).not.toHaveBeenCalled();
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:consumer-group-export');
    expect(
      document.querySelector('a[download^="rocketmq-consumer-groups-"]'),
    ).not.toBeInTheDocument();

    expect(exportedBlob).toBeDefined();
    const csv = await exportedBlob!.text();
    expect(csv).toContain('"orders-cg"');
    expect(csv).toContain('"orders-cg-archive"');
    expect(csv).toContain('"\'\r=formula-risk"');
    expect(csv).toContain('"\'=archive"');
    expect(csv).toContain('"orders-topic;payments,topic"');
    expect(csv).not.toContain('users-cg');
    clickSpy.mockRestore();
  });

  it('loads subscriptions and progress when opening a group', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /详情/ }));

    await waitFor(() =>
      expect(consumerService.getConsumerSubscriptions).toHaveBeenCalledWith(
        'remote-cg',
        'instance-1',
      ),
    );
    await waitFor(() =>
      expect(consumerService.getConsumerProgress).toHaveBeenCalledWith('remote-cg', 'instance-1'),
    );
    expect(consumerService.getConsumerGroup).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getAllByText('remote-topic').length).toBeGreaterThan(0));
  });

  it('shows group health diagnostics from subscriptions, progress and clients', async () => {
    const riskyGroup: ConsumerGroup = {
      ...group,
      totalLag: 1_200,
      delaySeconds: 720,
      onlineInstances: 2,
      instances: [
        {
          clientId: 'remote-cg-0@10.0.0.1',
          protocol: 'GRPC',
          address: '10.0.0.1:49152',
          subscribedTopics: ['remote-topic'],
          lastHeartbeat: '2026-07-23T00:00:00Z',
          topicLag: { 'remote-topic': 10 },
        },
        {
          clientId: 'remote-cg-1@10.0.0.2',
          protocol: 'REMOTING',
          address: '10.0.0.2:49152',
          subscribedTopics: [],
          lastHeartbeat: '2026-07-23T00:00:00Z',
          topicLag: {},
        },
      ],
    };
    vi.mocked(consumerService.listConsumerGroupPage).mockResolvedValue(groupPage([riskyGroup]));
    vi.mocked(consumerService.getConsumerSubscriptions).mockResolvedValue([
      {
        topic: 'remote-topic',
        expression: 'tagA',
        type: 'NORMAL',
        filterMode: 'Tag 过滤',
        consistency: '不一致',
      },
    ]);
    vi.mocked(consumerService.getConsumerProgress).mockResolvedValue([
      {
        topic: 'remote-topic',
        broker: 'broker-a',
        queueId: 0,
        brokerOffset: 100,
        consumerOffset: 90,
        diffTotal: 10,
      },
      {
        topic: 'remote-topic',
        broker: 'broker-b',
        queueId: 1,
        brokerOffset: 1_200,
        consumerOffset: 100,
        diffTotal: 1_100,
      },
    ]);
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /详情/ }));
    await user.click(await screen.findByRole('tab', { name: /健康诊断/ }));
    const panel = await screen.findByRole('tabpanel', { name: /健康诊断/ });

    await waitFor(() => expect(within(panel).getAllByText('消费风险').length).toBeGreaterThan(0));
    expect(within(panel).getByText('订阅表达式不一致')).toBeInTheDocument();
    expect(within(panel).getByText('Queue 堆积分布严重倾斜')).toBeInTheDocument();
    expect(within(panel).getAllByText('客户端心跳过期').length).toBeGreaterThan(0);
    expect(within(panel).getByText('处理建议')).toBeInTheDocument();
  });

  it('filters queue progress to the topic of the clicked distribution button', async () => {
    vi.mocked(consumerService.getConsumerSubscriptions).mockResolvedValue([
      {
        topic: 'remote-topic',
        expression: '*',
        type: 'NORMAL',
        filterMode: '全量',
        consistency: '一致',
      },
      {
        topic: '%RETRY%remote-cg',
        expression: '*',
        type: 'RETRY',
        filterMode: '全量',
        consistency: '一致',
      },
    ]);
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /详情/ }));
    // Click 查看分布 on the retry-topic subscription row.
    await waitFor(() => expect(screen.getAllByText('%RETRY%remote-cg').length).toBeGreaterThan(0));
    const retryRow = screen.getByText('%RETRY%remote-cg').closest('tr') as HTMLElement;
    await user.click(within(retryRow).getByRole('button', { name: /查看分布/ }));

    // The progress tab should now only show the retry topic's queue, not the normal topic.
    const progressPanel = await screen.findByRole('tabpanel', { name: /消费进度/ });
    await waitFor(() =>
      expect(within(progressPanel).getAllByText('%RETRY%remote-cg').length).toBeGreaterThan(0),
    );
    expect(within(progressPanel).queryByText('remote-topic')).not.toBeInTheDocument();
  });

  it('passes the selected instance to group diagnostics', async () => {
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      {
        id: 2,
        name: 'instance-a',
        remark: '',
        type: 'DIRECT',
        endpoint: '127.0.0.1:9876',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '2026-07-23T00:00:00Z',
        gmtModified: '2026-07-23T00:00:00Z',
      },
    ]);
    vi.mocked(consumerService.listConsumerGroupPage).mockResolvedValue(
      groupPage([{ ...group, instanceId: 'instance-a' }]),
    );
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /详情/ }));

    await waitFor(() =>
      expect(consumerService.getConsumerSubscriptions).toHaveBeenCalledWith(
        'remote-cg',
        'instance-a',
      ),
    );
    await waitFor(() =>
      expect(consumerService.getConsumerProgress).toHaveBeenCalledWith('remote-cg', 'instance-a'),
    );
  });

  it('previews queue impact before resetting consumer offsets', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /重置位点/ }));

    await waitFor(() =>
      expect(consumerService.getConsumerSubscriptions).toHaveBeenCalledWith(
        'remote-cg',
        'instance-1',
      ),
    );
    const confirm = screen.getByRole('button', { name: '确认重置' });
    expect(confirm).toBeDisabled();

    const topicSelect = screen.getByRole('combobox', { name: '目标 Topic' });
    await user.click(topicSelect);
    const option = await waitFor(() => {
      const element = screen
        .getAllByText('remote-topic')
        .find((candidate) => candidate.classList.contains('ant-select-item-option-content'));
      if (!element) throw new Error('Missing target Topic option');
      return element;
    });
    await user.click(option);
    expect(confirm).toBeDisabled();

    await user.click(screen.getByRole('button', { name: /预览影响/ }));

    await waitFor(() =>
      expect(consumerService.previewConsumerOffsetReset).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'remote-cg',
          instanceId: 'instance-1',
          topic: 'remote-topic',
          timestamp: expect.any(Number),
        }),
      ),
    );
    expect(await screen.findByText('重置后总堆积')).toBeInTheDocument();
    expect(await screen.findByText('将回放 10 条消息')).toBeInTheDocument();
    await waitFor(() => expect(confirm).toBeEnabled());
    await user.click(confirm);

    await waitFor(() =>
      expect(consumerService.resetConsumerOffset).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'remote-cg',
          instanceId: 'instance-1',
          topic: 'remote-topic',
          timestamp: expect.any(Number),
        }),
      ),
    );
  });

  it('invalidates the reset preview when reset parameters change', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /重置位点/ }));
    const topicSelect = screen.getByRole('combobox', { name: '目标 Topic' });
    await user.click(topicSelect);
    const option = await waitFor(() => {
      const element = screen
        .getAllByText('remote-topic')
        .find((candidate) => candidate.classList.contains('ant-select-item-option-content'));
      if (!element) throw new Error('Missing target Topic option');
      return element;
    });
    await user.click(option);
    await user.click(screen.getByRole('button', { name: /预览影响/ }));

    expect(await screen.findByText('将回放 10 条消息')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: '确认重置' })).toBeEnabled());

    await user.click(screen.getByRole('button', { name: '1 小时前' }));

    expect(screen.queryByText('将回放 10 条消息')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认重置' })).toBeDisabled();
  });

  it('blocks reset confirmation when the preview has failed queues', async () => {
    vi.mocked(consumerService.previewConsumerOffsetReset).mockResolvedValue({
      instanceId: 'instance-1',
      groupName: 'remote-cg',
      topic: 'remote-topic',
      timestamp: 1784246400000,
      complete: false,
      allowReset: false,
      queueCount: 1,
      warningCount: 1,
      rewindQueueCount: 0,
      fastForwardQueueCount: 0,
      currentTotalLag: 30,
      projectedTotalLag: 30,
      totalOffsetDelta: 0,
      warnings: ['Failed to preview 1 queue(s); retry before applying the reset'],
      queues: [
        {
          topic: 'remote-topic',
          broker: 'broker-a',
          queueId: 0,
          minOffset: -1,
          maxOffset: -1,
          brokerOffset: 120,
          consumerOffset: 90,
          targetOffset: 90,
          currentLag: 30,
          projectedLag: 30,
          offsetDelta: 0,
          riskLevel: 'ERROR',
          message: 'Failed to preview queue offset: timeout',
        },
      ],
    });
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /重置位点/ }));
    await user.click(screen.getByRole('combobox', { name: '目标 Topic' }));
    const option = await waitFor(() => {
      const element = screen
        .getAllByText('remote-topic')
        .find((candidate) => candidate.classList.contains('ant-select-item-option-content'));
      if (!element) throw new Error('Missing target Topic option');
      return element;
    });
    await user.click(option);
    await user.click(screen.getByRole('button', { name: /预览影响/ }));

    expect(await screen.findByText('不完整')).toBeInTheDocument();
    expect(await screen.findByText('Failed to preview queue offset: timeout')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认重置' })).toBeDisabled();
    expect(consumerService.resetConsumerOffset).not.toHaveBeenCalled();
  });

  it('reloads same-named group diagnostics after changing the selected instance', async () => {
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      {
        id: 2,
        name: 'instance-a',
        remark: '',
        type: 'DIRECT',
        endpoint: '127.0.0.1:9876',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '2026-07-23T00:00:00Z',
        gmtModified: '2026-07-23T00:00:00Z',
      },
      {
        id: 3,
        name: 'instance-b',
        remark: '',
        type: 'DIRECT',
        endpoint: '127.0.0.2:9876',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '2026-07-23T00:00:00Z',
        gmtModified: '2026-07-23T00:00:00Z',
      },
    ]);
    vi.mocked(consumerService.listConsumerGroupPage).mockImplementation(async (params) =>
      groupPage([{ ...group, instanceId: params?.instanceId ?? 'instance-a' }], {
        page: params?.page ?? 1,
        size: params?.pageSize ?? 20,
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />, '/instance/instance-a/consumer');

    await user.click(await screen.findByRole('button', { name: /详情/ }));
    await waitFor(() =>
      expect(consumerService.getConsumerSubscriptions).toHaveBeenCalledWith(
        'remote-cg',
        'instance-a',
      ),
    );

    await user.click(screen.getByText('instance-a'));
    await user.click(
      await screen.findByText('instance-b', { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() =>
      expect(consumerService.listConsumerGroupPage).toHaveBeenCalledWith({
        instanceId: 'instance-b',
        page: 1,
        pageSize: 20,
        search: undefined,
        subscriptionMode: undefined,
      }),
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: /详情/ }));

    await waitFor(() =>
      expect(consumerService.getConsumerSubscriptions).toHaveBeenCalledWith(
        'remote-cg',
        'instance-b',
      ),
    );
    await waitFor(() =>
      expect(consumerService.getConsumerProgress).toHaveBeenCalledWith('remote-cg', 'instance-b'),
    );
  });

  it('keeps the latest client stack when an older request resolves last', async () => {
    const firstStack = {
      groupName: 'remote-cg',
      clientId: 'client-1',
      capturedAt: '2026-07-23T00:00:00Z',
      threadCount: 1,
      threads: [
        {
          threadName: 'OldClientThread',
          threadId: 1,
          state: 'RUNNABLE',
          blockedTime: 0,
          waitedTime: 0,
          stackTrace: ['old.Stack.run(Old.java:1)'],
        },
      ],
    };
    const secondStack = {
      ...firstStack,
      clientId: 'client-2',
      threads: [
        {
          ...firstStack.threads[0],
          threadName: 'LatestClientThread',
          stackTrace: ['latest.Stack.run(Latest.java:2)'],
        },
      ],
    };
    let resolveFirst!: (value: typeof firstStack) => void;
    let resolveSecond!: (value: typeof secondStack) => void;
    vi.mocked(consumerService.getConsumerStack)
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveFirst = resolve;
        }),
      )
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveSecond = resolve;
        }),
      );
    vi.mocked(consumerService.listConsumerGroupPage).mockResolvedValue(
      groupPage([
        {
          ...group,
          instances: [
            {
              clientId: 'client-1',
              protocol: 'Remoting',
              address: '10.0.0.1:1',
              subscribedTopics: [],
              lastHeartbeat: '',
              topicLag: {},
            },
            {
              clientId: 'client-2',
              protocol: 'Remoting',
              address: '10.0.0.2:2',
              subscribedTopics: [],
              lastHeartbeat: '',
              topicLag: {},
            },
          ],
        },
      ]),
    );
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /详情/ }));
    // The online instance table now lives inside the overview tab, so its 线程栈 buttons are
    // available as soon as the detail dialog opens — no separate tab click.
    const stackButtons = await screen.findAllByRole('button', { name: /线程栈/ });
    await user.click(stackButtons[0]);
    await user.click(stackButtons[1]);

    await act(async () => resolveSecond(secondStack));
    expect(await screen.findByText('LatestClientThread')).toBeInTheDocument();
    await act(async () => resolveFirst(firstStack));
    expect(screen.getByText('LatestClientThread')).toBeInTheDocument();
    expect(screen.queryByText('OldClientThread')).not.toBeInTheDocument();
  });

  it('loads a consumer client stack trace from the selected instance', async () => {
    vi.mocked(consumerService.listConsumerGroupPage).mockResolvedValue(
      groupPage([
        {
          ...group,
          instances: [
            {
              clientId: 'client-1',
              protocol: 'Remoting',
              address: '10.0.0.1:39210',
              subscribedTopics: ['remote-topic'],
              lastHeartbeat: '2026-07-23T00:00:00Z',
              topicLag: {},
            },
          ],
        },
      ]),
    );
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /详情/ }));
    await user.click(await screen.findByRole('button', { name: /线程栈/ }));

    await waitFor(() =>
      expect(consumerService.getConsumerStack).toHaveBeenCalledWith(
        'remote-cg',
        'client-1',
        'instance-1',
      ),
    );
    expect(await screen.findByText('消费者线程栈')).toBeInTheDocument();
    expect(screen.getByText('ConsumeMessageThread_1')).toBeInTheDocument();
    expect(
      screen.getByText('org.apache.demo.OrderListener.consume(OrderListener.java:42)'),
    ).toBeInTheDocument();
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
    vi.mocked(consumerService.listConsumerGroupPage).mockResolvedValue(groupPage([]));
    vi.mocked(consumerService.importConsumerGroups).mockResolvedValue({
      imported: 1,
      failed: 1,
      groups: [
        {
          ...group,
          name: 'cg-ok',
          subscriptionMode: 'Push',
          consumeType: 'CLUSTERING',
          retryMaxTimes: 16,
          subscriptionDataType: 'NORMAL',
          namespace: 'default',
          clusterId: 'server-cluster',
          onlineInstances: 0,
          totalLag: 0,
          delaySeconds: 0,
          instances: [],
          subscribedTopics: [],
          gmtCreate: '2026-07-24T00:00:00Z',
          gmtModified: '2026-07-24T00:00:00Z',
        },
      ],
      failures: [{ index: 1, name: 'cg-fail', message: 'broker rejected group' }],
    });

    const user = userEvent.setup();
    instanceServiceMocks.listInstances.mockResolvedValue([
      {
        id: 4,
        name: 'instance-proxy-1',
        remark: '',
        type: 'PROXY_CLUSTER',
        endpoint: '10.0.2.21:8080',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      },
    ]);
    renderWithProviders(<ConsumerPage />, '/instance/instance-proxy-1/consumer');

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
    expect(await screen.findByText('检测到 2 个 Group，将通过后端批量导入')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '开始导入' }));

    await waitFor(() => expect(consumerService.importConsumerGroups).toHaveBeenCalledTimes(1));
    expect(consumerService.importConsumerGroups).toHaveBeenCalledWith('instance-proxy-1', [
      {
        name: 'cg-ok',
        subscriptionMode: 'Push',
        consumeType: 'CLUSTERING',
        retryMaxTimes: 16,
        subscriptionDataType: 'NORMAL',
        subscribedTopics: [],
        instanceId: 'instance-proxy-1',
      },
      {
        name: 'cg-fail',
        subscriptionMode: 'Pop',
        consumeType: 'BROADCASTING',
        retryMaxTimes: 4,
        subscriptionDataType: 'FIFO',
        deliveryOrderType: 'PARTITON_ORDER',
        subscribedTopics: [],
        instanceId: 'instance-proxy-1',
      },
    ]);
    expect(consumerService.createConsumerGroup).not.toHaveBeenCalled();
    expect(await screen.findByText('已导入 1 个 Group，1 个失败')).toBeInTheDocument();
    expect(screen.getByText('broker rejected group')).toBeInTheDocument();
    expect(screen.getAllByText('cg-ok').length).toBeGreaterThan(0);
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /重试失败项/ })).toBeInTheDocument(),
    );
  });

  it('shows a spinner while the instance list is still resolving', async () => {
    let resolveInstances!: (value: never[]) => void;
    instanceServiceMocks.listInstances.mockReturnValue(
      new Promise((resolve) => {
        resolveInstances = resolve;
      }),
    );
    renderWithProviders(<ConsumerPage />);

    expect(document.querySelector('.ant-spin-spinning')).not.toBeNull();

    resolveInstances([]);
    await waitFor(() => expect(document.querySelector('.ant-spin-spinning')).toBeNull());
  });

  it('disables Consumer Group writes until an instance is available', async () => {
    instanceServiceMocks.listInstances.mockResolvedValue([]);
    renderWithProviders(<ConsumerPage />);

    expect(await screen.findByText('选择实例')).toBeInTheDocument();
    expect(consumerService.listConsumerGroupPage).not.toHaveBeenCalled();
    await waitFor(() => expect(document.querySelector('.ant-spin-spinning')).toBeNull());
    expect(screen.getByRole('button', { name: /导入/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: '创建 Group' })).toBeDisabled();
  });

  it('auto-refreshes the selected group while the detail modal is open', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    const row = await screen.findByRole('row', { name: /remote-cg/ });
    expect(within(row).queryByRole('button', { name: /刷\s*新/ })).not.toBeInTheDocument();
    expect(within(row).getByText('10')).toBeInTheDocument();

    await user.click(within(row).getByRole('button', { name: /详\s*情/ }));
    await screen.findByRole('dialog', { name: /remote-cg/ });

    await waitFor(
      () => {
        expect(consumerService.refreshConsumerGroup).toHaveBeenCalledWith(
          'remote-cg',
          'instance-1',
        );
      },
      { timeout: 4000 },
    );
    expect(await within(row).findByText('42')).toBeInTheDocument();
  });

  it('edits retry settings from the detail modal settings tab', async () => {
    vi.mocked(consumerService.getConsumerGroupSettings).mockResolvedValue({
      groupName: 'remote-cg',
      retryQueueNums: 1,
      retryMaxTimes: 16,
    });
    vi.mocked(consumerService.updateConsumerGroupSettings).mockResolvedValue({
      groupName: 'remote-cg',
      retryQueueNums: 2,
      retryMaxTimes: 8,
    });
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    const row = await screen.findByRole('row', { name: /remote-cg/ });
    expect(within(row).queryByRole('button', { name: /配\s*置/ })).not.toBeInTheDocument();
    await user.click(within(row).getByRole('button', { name: /详\s*情/ }));

    const dialog = await screen.findByRole('dialog', { name: /remote-cg/ });
    await user.click(within(dialog).getByRole('tab', { name: /配\s*置/ }));

    await waitFor(() => {
      expect(consumerService.getConsumerGroupSettings).toHaveBeenCalledWith(
        'remote-cg',
        'instance-1',
      );
    });
    const retryQueueInput = await within(dialog).findByLabelText('重试队列数');
    expect(retryQueueInput).toHaveValue('1');

    await user.clear(retryQueueInput);
    await user.type(retryQueueInput, '2');
    await user.clear(within(dialog).getByLabelText('最大重试次数'));
    await user.type(within(dialog).getByLabelText('最大重试次数'), '8');
    await user.click(within(dialog).getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => {
      expect(consumerService.updateConsumerGroupSettings).toHaveBeenCalledWith({
        instanceId: 'instance-1',
        name: 'remote-cg',
        retryQueueNums: 2,
        retryMaxTimes: 8,
      });
    });
    expect(await screen.findByText('消费组配置已保存')).toBeInTheDocument();
  });

  it('renders an unknown (-1) lag as unavailable in the table and the lag detail', async () => {
    const user = userEvent.setup();
    vi.mocked(consumerService.listConsumerGroupPage).mockResolvedValue(
      groupPage([
        { ...group, name: 'unknown-lag-cg', totalLag: -1 },
        { ...group, name: 'known-lag-cg', totalLag: 15000 },
      ]),
    );
    renderWithProviders(<ConsumerPage />);

    const unknownRow = await screen.findByRole('row', { name: /unknown-lag-cg/ });
    expect(within(unknownRow).getByText('不可用')).toBeInTheDocument();
    const knownRow = await screen.findByRole('row', { name: /\bknown-lag-cg\b/ });
    expect(within(knownRow).queryByText('不可用')).not.toBeInTheDocument();

    await user.click(within(unknownRow).getByRole('button', { name: /详\s*情/ }));
    const dialog = await screen.findByRole('dialog', { name: /unknown-lag-cg/ });
    expect(within(dialog).getByText('不可用')).toBeInTheDocument();
  });

  it('sorts groups with an unknown lag after known backlogs in lag order', async () => {
    const user = userEvent.setup();
    vi.mocked(consumerService.listConsumerGroupPage).mockResolvedValue(
      groupPage([
        { ...group, name: 'unknown-lag-cg', totalLag: -1 },
        { ...group, name: 'known-lag-cg', totalLag: 15000 },
      ]),
    );
    renderWithProviders(<ConsumerPage />);
    await screen.findByRole('row', { name: /unknown-lag-cg/ });

    const [lagHeader] = screen.getAllByText('总堆积量');
    await user.click(lagHeader);
    await waitFor(() => {
      const rows = Array.from(document.querySelectorAll('tbody tr'));
      const order = rows
        .map((row) => row.textContent ?? '')
        .map((text) =>
          text.includes('unknown-lag-cg')
            ? 'unknown'
            : /\bknown-lag-cg\b/.test(text)
              ? 'known'
              : '',
        )
        .filter(Boolean);
      expect(order).toEqual(['known', 'unknown']);
    });
  });

  it('ignores settings responses from a previously closed group modal', async () => {
    const otherGroup = { ...group, name: 'other-cg' };
    const firstSettings = deferred<{
      groupName: string;
      retryQueueNums: number;
      retryMaxTimes: number;
    }>();
    const secondSettings = deferred<{
      groupName: string;
      retryQueueNums: number;
      retryMaxTimes: number;
    }>();
    vi.mocked(consumerService.listConsumerGroupPage).mockResolvedValue(
      groupPage([group, otherGroup]),
    );
    vi.mocked(consumerService.getConsumerGroupSettings)
      .mockImplementationOnce(() => firstSettings.promise)
      .mockImplementationOnce(() => secondSettings.promise);
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    const firstRow = await screen.findByRole('row', { name: /remote-cg/ });
    await user.click(within(firstRow).getByRole('button', { name: '详情' }));
    const firstDialog = await screen.findByRole('dialog', { name: /remote-cg/ });
    await user.click(within(firstDialog).getByRole('tab', { name: '配置' }));
    await waitFor(() => {
      expect(consumerService.getConsumerGroupSettings).toHaveBeenCalledWith(
        'remote-cg',
        'instance-1',
      );
    });

    fireEvent.click(firstDialog.querySelector('.ant-modal-close') as HTMLElement);

    const secondRow = screen.getByRole('row', { name: /other-cg/ });
    await user.click(within(secondRow).getByRole('button', { name: '详情' }));
    const secondDialog = await screen.findByRole('dialog', { name: /other-cg/ });
    await user.click(within(secondDialog).getByRole('tab', { name: '配置' }));
    await waitFor(() => {
      expect(consumerService.getConsumerGroupSettings).toHaveBeenCalledWith(
        'other-cg',
        'instance-1',
      );
    });

    await act(async () => {
      secondSettings.resolve({ groupName: 'other-cg', retryQueueNums: 4, retryMaxTimes: 12 });
    });
    await waitFor(() => {
      expect(within(secondDialog).getByLabelText('重试队列数')).toHaveValue('4');
    });

    await act(async () => {
      firstSettings.resolve({ groupName: 'remote-cg', retryQueueNums: 1, retryMaxTimes: 16 });
    });
    expect(within(secondDialog).getByLabelText('重试队列数')).toHaveValue('4');
    expect(within(secondDialog).getByLabelText('最大重试次数')).toHaveValue('12');
  });
});
