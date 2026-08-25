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
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ConsumerGroup } from '../../../api/metadata';
import * as instanceService from '../../../services/instanceService';
import { LangProvider } from '../../../i18n/LangContext';
import * as consumerService from '../../../services/consumerService';
import ConsumerPage from '../consumer';

vi.mock('../../../services/consumerService', () => ({
  batchDeleteConsumerGroups: vi.fn(),
  createConsumerGroup: vi.fn(),
  deleteConsumerGroup: vi.fn(),
  getConsumerGroup: vi.fn(),
  getConsumerProgress: vi.fn(),
  getConsumerStack: vi.fn(),
  getConsumerSubscriptions: vi.fn(),
  getConsumerGroupSettings: vi.fn(),
  listAllConsumerGroups: vi.fn(),
  updateConsumerGroupSettings: vi.fn(),
  listConsumerGroupPage: vi.fn(),
  refreshConsumerGroup: vi.fn(),
  resetConsumerOffset: vi.fn(),
}));
const instanceServiceMocks = vi.hoisted(() => ({ listInstances: vi.fn() }));

vi.mock('../../../services/instanceService', () => instanceServiceMocks);

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

const renderWithProviders = (ui: React.ReactElement, initialEntry = '/instance/consumer') =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter initialEntries={[initialEntry]}>{ui}</MemoryRouter>
      </LangProvider>
    </App>,
  );

describe('Consumer page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
    });
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
    vi.mocked(consumerService.listAllConsumerGroups).mockImplementation(async (params) =>
      params?.search
        ? exportGroups.filter((item) => item.name.includes(params.search ?? ''))
        : exportGroups,
    );
    renderWithProviders(<ConsumerPage />);

    expect(await screen.findByText('orders-cg')).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText('搜索 Group 名称或 Topic'), 'orders');
    await waitFor(() => expect(screen.queryByText('users-cg')).not.toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /导出/ }));

    await waitFor(() =>
      expect(consumerService.listAllConsumerGroups).toHaveBeenCalledWith({
        instanceId: 'instance-1',
        search: 'orders',
      }),
    );
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

  it('requires and submits a target topic when resetting consumer offsets', async () => {
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
          gmtCreate: '2026-07-24T00:00:00Z',
          gmtModified: '2026-07-24T00:00:00Z',
        } as ConsumerGroup;
      },
    );

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
      instanceId: 'instance-proxy-1',
    });
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
});
