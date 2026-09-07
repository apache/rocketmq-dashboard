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

import { App, ConfigProvider, message } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MessageRecord, TraceRecord } from '../../../api/message';
import { LangProvider } from '../../../i18n/LangContext';
import MessagePage from '../message';

const serviceMocks = vi.hoisted(() => ({
  getMessageTrace: vi.fn(),
  getMessageTraceByKey: vi.fn(),
  queryMessages: vi.fn(),
  consumeMessageDirectly: vi.fn(),
}));
const instanceFilterMocks = vi.hoisted(() => ({
  useInstanceFilter: vi.fn(),
}));
const historyMocks = vi.hoisted(() => ({
  getQueryHistorySummary: vi.fn(),
  listMessageQueryHistory: vi.fn(),
  listTraceQueryHistory: vi.fn(),
}));

vi.mock('../../../services/messageService', () => ({
  ...serviceMocks,
  queryMessagePage: ({ page = 1, pageSize = 50, ...params }: Record<string, unknown>) =>
    Promise.resolve(serviceMocks.queryMessages(params)).then((result) =>
      Array.isArray(result)
        ? {
            items: result,
            total: result.length,
            page,
            size: pageSize,
            resultMayBeTruncated: false,
          }
        : result,
    ),
}));
vi.mock('../../../hooks/useInstanceFilter', () => instanceFilterMocks);

vi.mock('../../../api/messageHistory', () => historyMocks);

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([]),
}));
vi.mock('../../../services/topicService', () => ({
  listTopics: vi.fn().mockResolvedValue([{ name: 'topic-a' }]),
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
});

const createDeferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

const createMessage = (msgId: string): MessageRecord => ({
  msgId,
  topic: `topic-${msgId}`,
  tag: 'tag',
  key: `key-${msgId}`,
  brokerName: 'broker-a',
  queueId: 0,
  queueOffset: 0,
  body: '{}',
  storeTime: '2026-07-31T00:00:00Z',
  bornHost: '127.0.0.1:1000',
  storeHost: '127.0.0.1:10911',
  properties: {},
  size: 2,
});

const createTrace = (title: string): TraceRecord => ({
  nodes: [
    {
      title,
      timestamp: '2026-07-31T00:00:00Z',
      costTime: 1,
      status: 'finish',
      description: `${title} description`,
    },
  ],
  consumerStatus: [],
});

const MessagePageWithProviders = () => (
  <ConfigProvider theme={{ token: { motion: false } }}>
    <App>
      <LangProvider>
        <MemoryRouter>
          <MessagePage />
        </MemoryRouter>
      </LangProvider>
    </App>
  </ConfigProvider>
);

const renderPage = () => render(<MessagePageWithProviders />);

const selectTopic = async (user: ReturnType<typeof userEvent.setup>) => {
  const topicSelects = screen.getAllByRole('combobox');
  await user.click(topicSelects[topicSelects.length - 1]!);
  const topicOptions = await screen.findAllByText('topic-a');
  await user.click(topicOptions[topicOptions.length - 1]!);
};

describe('MessagePage async request ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    serviceMocks.getMessageTrace.mockResolvedValue(null);
    serviceMocks.getMessageTraceByKey.mockResolvedValue(null);
    historyMocks.getQueryHistorySummary.mockResolvedValue({ messageQueries: 0, traceQueries: 0 });
    historyMocks.listMessageQueryHistory.mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 20,
    });
    historyMocks.listTraceQueryHistory.mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 20,
    });
    instanceFilterMocks.useInstanceFilter.mockReturnValue({
      selectedInstanceId: 1,
      selectInstance: vi.fn(),
      instanceOptions: [{ value: 1, label: 'Instance A' }],
    });
    vi.spyOn(message, 'success').mockImplementation(vi.fn());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('does not restore query results after the user resets an in-flight query', async () => {
    const query = createDeferred<MessageRecord[]>();
    serviceMocks.queryMessages.mockReturnValue(query.promise);
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    await waitFor(() => expect(serviceMocks.queryMessages).toHaveBeenCalledTimes(1));
    await user.click(screen.getByRole('button', { name: /重置/ }));

    await act(async () => {
      query.resolve([createMessage('late-after-reset')]);
    });

    expect(screen.queryByText('late-after-reset')).not.toBeInTheDocument();
  });

  it('resets pagination and the truncated-result warning', async () => {
    serviceMocks.queryMessages.mockResolvedValue({
      items: [createMessage('message-on-page-two')],
      total: 101,
      page: 2,
      size: 50,
      resultMayBeTruncated: true,
    });
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    expect(await screen.findByText('message-on-page-two')).toBeInTheDocument();
    expect(screen.getByText('共 101 条消息')).toBeInTheDocument();
    expect(screen.getByText(/查询结果达到服务端扫描上限/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /重置/ }));

    expect(screen.queryByText('message-on-page-two')).not.toBeInTheDocument();
    expect(screen.queryByText('共 101 条消息')).not.toBeInTheDocument();
    expect(screen.queryByText(/查询结果达到服务端扫描上限/)).not.toBeInTheDocument();
    expect(document.querySelector('.ant-pagination-item-active')).not.toBeInTheDocument();
  });

  it('clears query state and invalidates an in-flight request when the query mode changes', async () => {
    const lateQuery = createDeferred<MessageRecord[]>();
    serviceMocks.queryMessages
      .mockResolvedValueOnce({
        items: [createMessage('topic-result')],
        total: 101,
        page: 2,
        size: 50,
        resultMayBeTruncated: true,
      })
      .mockReturnValueOnce(lateQuery.promise);
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    const queryButton = screen.getByRole('button', { name: /^search查询$/ });
    await user.click(queryButton);
    expect(await screen.findByText('topic-result')).toBeInTheDocument();
    await user.click(queryButton);
    await waitFor(() => expect(serviceMocks.queryMessages).toHaveBeenCalledTimes(2));

    await user.click(screen.getByText('按 Message Key'));

    expect(screen.queryByText('topic-result')).not.toBeInTheDocument();
    expect(screen.queryByText('共 101 条消息')).not.toBeInTheDocument();
    expect(screen.queryByText(/查询结果达到服务端扫描上限/)).not.toBeInTheDocument();
    expect(document.querySelector('.ant-pagination-item-active')).not.toBeInTheDocument();
    expect(document.querySelector('.ant-table-wrapper .ant-spin-spinning')).not.toBeInTheDocument();

    await act(async () => {
      lateQuery.resolve([createMessage('late-topic-result')]);
    });
    expect(screen.queryByText('late-topic-result')).not.toBeInTheDocument();
  });

  it('clears query results and message details when the selected instance changes', async () => {
    serviceMocks.queryMessages.mockResolvedValue([createMessage('message-from-instance-a')]);
    let currentInstanceId = 1;
    const selectInstance = vi.fn((id: number) => {
      currentInstanceId = id;
    });
    instanceFilterMocks.useInstanceFilter.mockImplementation(() => ({
      selectedInstanceId: currentInstanceId,
      selectInstance,
      instanceOptions: [
        { value: 1, label: 'Instance A' },
        { value: 2, label: 'Instance B' },
      ],
    }));
    const user = userEvent.setup();
    const view = renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const row = await screen.findByRole('row', { name: /message-from-instance-a/ });
    await user.click(within(row).getByRole('button', { name: /详情/ }));
    expect(await screen.findByRole('dialog', { name: '消息详情' })).toBeInTheDocument();

    await user.click(screen.getAllByRole('combobox')[0]!);
    const instanceOptions = await screen.findAllByText('Instance B');
    await user.click(instanceOptions[instanceOptions.length - 1]!);
    view.rerender(<MessagePageWithProviders />);

    await waitFor(() => {
      expect(screen.queryByText('message-from-instance-a')).not.toBeInTheDocument();
      expect(screen.queryByRole('dialog', { name: '消息详情' })).not.toBeInTheDocument();
    });
    expect(selectInstance).toHaveBeenCalledWith(2, expect.anything());
  });
  it('surfaces unavailable message provider errors from query requests', async () => {
    serviceMocks.queryMessages.mockRejectedValue(
      new Error('Message query provider is not configured'),
    );
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));

    expect(await screen.findByText('Message query provider is not configured')).toBeInTheDocument();
  });

  it('surfaces unavailable message provider errors from trace requests', async () => {
    serviceMocks.queryMessages.mockResolvedValue([createMessage('message-a')]);
    serviceMocks.getMessageTrace.mockRejectedValue(
      new Error('Message query provider is not configured'),
    );
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const row = await screen.findByRole('row', { name: /message-a/ });
    await user.click(within(row).getByRole('button', { name: /轨迹/ }));

    const dialog = await screen.findByRole('dialog', { name: '消息详情' });
    expect(
      await within(dialog).findByText('Message query provider is not configured'),
    ).toBeInTheDocument();
  });

  it('loads a message trace lazily and reuses it for the same message', async () => {
    serviceMocks.queryMessages.mockResolvedValue([createMessage('message-a')]);
    serviceMocks.getMessageTrace.mockResolvedValue(createTrace('cached-trace'));
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const row = await screen.findByRole('row', { name: /message-a/ });
    await user.click(within(row).getByRole('button', { name: /详情/ }));
    let dialog = await screen.findByRole('dialog', { name: '消息详情' });
    expect(serviceMocks.getMessageTrace).not.toHaveBeenCalled();

    await user.click(within(dialog).getByText('消息轨迹'));
    expect(await within(dialog).findByText('cached-trace description')).toBeInTheDocument();
    expect(serviceMocks.getMessageTrace).toHaveBeenCalledTimes(1);
    await user.click(within(dialog).getByText('消息内容'));
    await user.click(within(dialog).getByText('消息轨迹'));
    expect(serviceMocks.getMessageTrace).toHaveBeenCalledTimes(1);

    await user.click(within(dialog).getByRole('button', { name: /关\s*闭/ }));
    await user.click(within(row).getByRole('button', { name: /详情/ }));
    dialog = await screen.findByRole('dialog', { name: '消息详情' });
    await user.click(within(dialog).getByText('消息轨迹'));
    expect(await within(dialog).findByText('cached-trace description')).toBeInTheDocument();
    expect(serviceMocks.getMessageTrace).toHaveBeenCalledTimes(1);
  });

  it('requiresGroupAndClientBeforeDirectConsumeTest', async () => {
    serviceMocks.queryMessages.mockResolvedValue([createMessage('message-a')]);
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const row = await screen.findByRole('row', { name: /message-a/ });
    await user.click(within(row).getByRole('button', { name: /详情/ }));

    const dialog = await screen.findByRole('dialog', { name: '消息详情' });
    await user.click(within(dialog).getByRole('button', { name: /直接消费/ }));
    const consumeDialogTitle = await screen.findByText('直接消费消息');
    const consumeDialog = consumeDialogTitle.closest('[role="dialog"]');
    expect(consumeDialog).not.toBeNull();
    expect(
      within(consumeDialog as HTMLElement).getByPlaceholderText('目标消费者组'),
    ).toBeInTheDocument();
    expect(
      within(consumeDialog as HTMLElement).getByPlaceholderText('在线客户端 ID'),
    ).toBeInTheDocument();

    await user.click(within(consumeDialog as HTMLElement).getByRole('button', { name: /执\s*行/ }));
    expect(serviceMocks.consumeMessageDirectly).not.toHaveBeenCalled();
  });

  it('queries trace by key with a custom trace topic from the trace tab', async () => {
    serviceMocks.queryMessages.mockResolvedValue([createMessage('message-a')]);
    serviceMocks.getMessageTraceByKey.mockResolvedValue(createTrace('key-trace'));
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const row = await screen.findByRole('row', { name: /message-a/ });
    await user.click(within(row).getByRole('button', { name: /轨迹/ }));

    const dialog = await screen.findByRole('dialog', { name: '消息详情' });
    await user.click(within(dialog).getByText('按 Message Key'));
    const keyInput = within(dialog).getByPlaceholderText('输入 Message Key');
    await user.clear(keyInput);
    await user.type(keyInput, 'ORDER-001');
    const traceTopicInput = within(dialog).getByPlaceholderText('轨迹 Topic（留空使用默认）');
    await user.type(traceTopicInput, 'CUSTOM_TRACE');
    await user.click(within(dialog).getByRole('button', { name: /查询轨迹/ }));

    await waitFor(() => {
      expect(serviceMocks.getMessageTraceByKey).toHaveBeenCalledWith(
        'ORDER-001',
        1,
        'topic-message-a',
        'CUSTOM_TRACE',
      );
    });
    expect(await within(dialog).findByText('key-trace description')).toBeInTheDocument();
  });

  it('keeps the trace tab responsive when an empty trace query supersedes an in-flight load', async () => {
    const pendingTrace = createDeferred<TraceRecord>();
    serviceMocks.queryMessages.mockResolvedValue([createMessage('message-a')]);
    serviceMocks.getMessageTrace.mockReturnValue(pendingTrace.promise);
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const row = await screen.findByRole('row', { name: /message-a/ });
    await user.click(within(row).getByRole('button', { name: /轨迹/ }));

    const dialog = await screen.findByRole('dialog', { name: '消息详情' });
    expect(await within(dialog).findByText('正在加载轨迹数据…')).toBeInTheDocument();

    await user.clear(within(dialog).getByPlaceholderText('消息 ID（默认当前消息）'));
    await user.click(within(dialog).getByRole('button', { name: /查询轨迹/ }));

    expect(within(dialog).getByText('请输入 Message ID')).toBeInTheDocument();
    expect(within(dialog).queryByText('正在加载轨迹数据…')).not.toBeInTheDocument();

    await act(async () => {
      pendingTrace.resolve(createTrace('late-trace'));
      await pendingTrace.promise;
    });

    expect(within(dialog).queryByText('正在加载轨迹数据…')).not.toBeInTheDocument();
    expect(within(dialog).getByText('请输入 Message ID')).toBeInTheDocument();
  });

  it('shows trace diagnostics for slow and failed delivery paths', async () => {
    serviceMocks.queryMessages.mockResolvedValue([createMessage('message-a')]);
    serviceMocks.getMessageTrace.mockResolvedValue({
      nodes: [
        {
          title: 'Producer 发送',
          timestamp: '2026-07-31T00:00:00.000Z',
          costTime: 5,
          status: 'finish',
          description: 'producer sent the message',
        },
        {
          title: 'Broker 存储',
          timestamp: '2026-07-31T00:00:01.600Z',
          costTime: 720,
          status: 'finish',
          description: 'broker persisted the message',
        },
        {
          title: 'Consumer 消费',
          timestamp: '2026-07-31T00:00:02.100Z',
          costTime: 6200,
          status: 'error',
          description: 'consumer returned failure',
        },
      ],
      consumerStatus: [
        {
          group: 'cg-billing',
          deliveryStatus: 'failed',
          consumeTime: '2026-07-31T00:00:05.000Z',
          retryCount: 2,
        },
        {
          group: 'cg-notification',
          deliveryStatus: 'pending',
          consumeTime: '-',
          retryCount: 0,
        },
      ],
    });
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const row = await screen.findByRole('row', { name: /message-a/ });
    await user.click(within(row).getByRole('button', { name: /轨迹/ }));

    const dialog = await screen.findByRole('dialog', { name: '消息详情' });
    expect(await within(dialog).findByText('轨迹诊断')).toBeInTheDocument();
    expect(within(dialog).getByText('投递异常')).toBeInTheDocument();
    expect(within(dialog).getAllByText('轨迹阶段失败')).not.toHaveLength(0);
    expect(within(dialog).getAllByText('阶段耗时偏高')).not.toHaveLength(0);
    expect(within(dialog).getAllByText('消费投递失败')).not.toHaveLength(0);
    expect(within(dialog).getAllByText(/cg-billing/)).not.toHaveLength(0);
    expect(within(dialog).getByText(/cg-notification/)).toBeInTheDocument();
  });

  it('remembers a custom trace topic per instance across page remounts', async () => {
    serviceMocks.queryMessages.mockResolvedValue([createMessage('remembered-message')]);
    const user = userEvent.setup();
    const firstRender = renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const firstRow = await screen.findByRole('row', { name: /remembered-message/ });
    await user.click(within(firstRow).getByRole('button', { name: /轨迹/ }));
    const firstDialog = await screen.findByRole('dialog', { name: '消息详情' });
    const firstTraceTopicInput =
      within(firstDialog).getByPlaceholderText('轨迹 Topic（留空使用默认）');
    await user.type(firstTraceTopicInput, '  CUSTOM_TRACE  ');

    await waitFor(() => {
      expect(localStorage.getItem('rocketmq-studio-message-trace-topic:1')).toBe('CUSTOM_TRACE');
    });

    firstRender.unmount();
    renderPage();
    await selectTopic(user);
    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const secondRow = await screen.findByRole('row', { name: /remembered-message/ });
    await user.click(within(secondRow).getByRole('button', { name: /轨迹/ }));
    const secondDialog = await screen.findByRole('dialog', { name: '消息详情' });

    expect(within(secondDialog).getByPlaceholderText('轨迹 Topic（留空使用默认）')).toHaveValue(
      'CUSTOM_TRACE',
    );
  });

  it('does not leak a stored custom trace topic between instances', async () => {
    localStorage.setItem('rocketmq-studio-message-trace-topic:1', 'TRACE_A');
    localStorage.setItem('rocketmq-studio-message-trace-topic:2', 'TRACE_B');
    serviceMocks.queryMessages.mockResolvedValue([createMessage('instance-message')]);
    let currentInstanceId = 1;
    instanceFilterMocks.useInstanceFilter.mockImplementation(() => ({
      selectedInstanceId: currentInstanceId,
      selectInstance: vi.fn(),
      instanceOptions: [
        { value: 1, label: 'Instance A' },
        { value: 2, label: 'Instance B' },
      ],
    }));
    const user = userEvent.setup();
    const view = renderPage();
    await selectTopic(user);
    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const firstRow = await screen.findByRole('row', { name: /instance-message/ });
    await user.click(within(firstRow).getByRole('button', { name: /轨迹/ }));
    const firstDialog = await screen.findByRole('dialog', { name: '消息详情' });
    expect(within(firstDialog).getByPlaceholderText('轨迹 Topic（留空使用默认）')).toHaveValue(
      'TRACE_A',
    );

    currentInstanceId = 2;
    view.rerender(<MessagePageWithProviders />);
    await selectTopic(user);
    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const secondRow = await screen.findByRole('row', { name: /instance-message/ });
    await user.click(within(secondRow).getByRole('button', { name: /轨迹/ }));
    const secondDialog = await screen.findByRole('dialog', { name: '消息详情' });
    expect(within(secondDialog).getByPlaceholderText('轨迹 Topic（留空使用默认）')).toHaveValue(
      'TRACE_B',
    );
  });

  it('restores a custom trace topic from history before opening the trace again', async () => {
    historyMocks.listTraceQueryHistory.mockResolvedValue({
      items: [
        {
          id: 9,
          msgId: 'history-message',
          topic: 'orders',
          traceTopic: 'CUSTOM_TRACE',
          nodeCount: 1,
          consumerCount: 0,
          queriedBy: 'alice',
          queriedAt: '2026-08-05T12:00:00Z',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
    serviceMocks.queryMessages.mockResolvedValue([createMessage('history-message')]);
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: /服务端历史/ }));
    await user.click(await screen.findByRole('tab', { name: '轨迹查询' }));
    await user.click(await screen.findByText('history-message'));

    const row = await screen.findByRole('row', { name: /history-message/ });
    await user.click(within(row).getByRole('button', { name: /轨迹/ }));

    await waitFor(() => {
      expect(serviceMocks.getMessageTrace).toHaveBeenLastCalledWith(
        'history-message',
        1,
        'topic-history-message',
        'CUSTOM_TRACE',
      );
    });
  });

  it('keeps the latest query loading and ignores an earlier query result', async () => {
    const firstQuery = createDeferred<MessageRecord[]>();
    const secondQuery = createDeferred<MessageRecord[]>();
    serviceMocks.queryMessages
      .mockReturnValueOnce(firstQuery.promise)
      .mockReturnValueOnce(secondQuery.promise);
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    const queryButton = screen.getByRole('button', { name: /^search查询$/ });
    await user.click(queryButton);
    await user.click(queryButton);
    await waitFor(() => expect(serviceMocks.queryMessages).toHaveBeenCalledTimes(2));

    await act(async () => {
      firstQuery.resolve([createMessage('stale-first-query')]);
    });

    expect(screen.queryByText('stale-first-query')).not.toBeInTheDocument();
    expect(document.querySelector('.ant-table-wrapper .ant-spin-spinning')).toBeInTheDocument();

    await act(async () => {
      secondQuery.resolve([createMessage('latest-second-query')]);
    });

    expect(await screen.findByText('latest-second-query')).toBeInTheDocument();
    await waitFor(() =>
      expect(
        document.querySelector('.ant-table-wrapper .ant-spin-spinning'),
      ).not.toBeInTheDocument(),
    );
  });

  it.each(['resolve', 'reject'] as const)(
    'invalidates a trace request when the detail closes before a late %s',
    async (settlement) => {
      const trace = createDeferred<TraceRecord | null>();
      serviceMocks.queryMessages.mockResolvedValue([createMessage('message-a')]);
      serviceMocks.getMessageTrace.mockReturnValue(trace.promise);
      const errorSpy = vi.spyOn(message, 'error').mockImplementation(vi.fn());
      const user = userEvent.setup();
      renderPage();
      await selectTopic(user);

      await user.click(screen.getByRole('button', { name: /^search查询$/ }));
      const row = await screen.findByRole('row', { name: /message-a/ });
      await user.click(within(row).getByRole('button', { name: /轨迹/ }));
      const dialog = await screen.findByRole('dialog', { name: '消息详情' });
      expect(within(dialog).getByText('正在加载轨迹数据…')).toBeInTheDocument();
      await user.click(within(dialog).getByRole('button', { name: /关\s*闭/ }));
      await waitFor(() =>
        expect(screen.queryByRole('dialog', { name: '消息详情' })).not.toBeInTheDocument(),
      );

      await act(async () => {
        if (settlement === 'resolve') {
          trace.resolve(createTrace('late-closed-trace'));
        } else {
          trace.reject(new Error('late trace failure'));
        }
      });

      expect(screen.queryByText('late-closed-trace')).not.toBeInTheDocument();
      expect(screen.queryByText('正在加载轨迹数据…')).not.toBeInTheDocument();
      expect(errorSpy).not.toHaveBeenCalled();
    },
  );

  it('keeps the current trace loading when an earlier trace finishes first', async () => {
    const firstTrace = createDeferred<TraceRecord | null>();
    const secondTrace = createDeferred<TraceRecord | null>();
    serviceMocks.queryMessages.mockResolvedValue([
      createMessage('message-a'),
      createMessage('message-b'),
    ]);
    serviceMocks.getMessageTrace.mockImplementation((msgId: string) =>
      msgId === 'message-a' ? firstTrace.promise : secondTrace.promise,
    );
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const firstRow = await screen.findByRole('row', { name: /message-a/ });
    await user.click(within(firstRow).getByRole('button', { name: /轨迹/ }));
    const firstDialog = await screen.findByRole('dialog', { name: '消息详情' });
    await user.click(within(firstDialog).getByRole('button', { name: /关\s*闭/ }));

    const secondRow = screen.getByRole('row', { name: /message-b/ });
    await user.click(within(secondRow).getByRole('button', { name: /轨迹/ }));
    const secondDialog = await screen.findByRole('dialog', { name: '消息详情' });
    expect(within(secondDialog).getByText('正在加载轨迹数据…')).toBeInTheDocument();

    await act(async () => {
      firstTrace.resolve(createTrace('message-a stale trace'));
    });

    expect(within(secondDialog).getByText('正在加载轨迹数据…')).toBeInTheDocument();
    expect(screen.queryByText('message-a stale trace')).not.toBeInTheDocument();

    await act(async () => {
      secondTrace.resolve(createTrace('message-b trace'));
    });

    expect(await screen.findAllByText('message-b trace')).not.toHaveLength(0);
    expect(screen.queryByText('正在加载轨迹数据…')).not.toBeInTheDocument();
  });

  it('does not display a late trace from a previously closed message detail', async () => {
    const firstTrace = createDeferred<TraceRecord | null>();
    const secondTrace = createDeferred<TraceRecord | null>();
    serviceMocks.queryMessages.mockResolvedValue([
      createMessage('message-a'),
      createMessage('message-b'),
    ]);
    serviceMocks.getMessageTrace.mockImplementation((msgId: string) =>
      msgId === 'message-a' ? firstTrace.promise : secondTrace.promise,
    );
    const user = userEvent.setup();
    renderPage();
    await selectTopic(user);

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    const firstRow = await screen.findByRole('row', { name: /message-a/ });
    await user.click(within(firstRow).getByRole('button', { name: /轨迹/ }));
    const firstDialog = await screen.findByRole('dialog', { name: '消息详情' });
    await user.click(within(firstDialog).getByRole('button', { name: /关\s*闭/ }));

    const secondRow = screen.getByRole('row', { name: /message-b/ });
    await user.click(within(secondRow).getByRole('button', { name: /轨迹/ }));

    await act(async () => {
      secondTrace.resolve(createTrace('message-b trace'));
    });
    expect(await screen.findAllByText('message-b trace')).not.toHaveLength(0);

    await act(async () => {
      firstTrace.resolve(createTrace('message-a stale trace'));
    });

    expect(screen.getAllByText('message-b trace')).not.toHaveLength(0);
    expect(screen.queryByText('message-a stale trace')).not.toBeInTheDocument();
  });
});
