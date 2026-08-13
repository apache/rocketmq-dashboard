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

import { App, Modal } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MessageRecord } from '../../../api/message';
import { LangProvider } from '../../../i18n/LangContext';

const messageServiceMocks = vi.hoisted(() => ({
  getMessageTrace: vi.fn(),
  queryMessages: vi.fn(),
}));
const topicServiceMocks = vi.hoisted(() => ({
  listTopics: vi.fn(),
}));
const instanceFilterMocks = vi.hoisted(() => ({
  useInstanceFilter: vi.fn(),
}));

const QUERY_HISTORY_STORAGE_KEY = 'rocketmq-studio-message-query-history';

vi.mock('../../../services/messageService', () => messageServiceMocks);
vi.mock('../../../hooks/useInstanceFilter', () => instanceFilterMocks);

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([]),
}));
vi.mock('../../../services/topicService', () => topicServiceMocks);

import MessagePage from '../message';

const createMessage = (msgId: string): MessageRecord => ({
  msgId,
  topic: `topic-${msgId}`,
  tag: 'tag',
  key: `key-${msgId}`,
  body: '{}',
  storeTime: '2026-07-31T00:00:00Z',
  bornHost: '127.0.0.1:1000',
  storeHost: '127.0.0.1:10911',
  properties: {},
  size: 2,
});

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

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </LangProvider>
    </App>,
  );

const lastElement = <T,>(elements: T[]): T => elements[elements.length - 1]!;

describe('Message page query history', () => {
  beforeEach(() => {
    localStorage.clear();
    messageServiceMocks.getMessageTrace.mockReset().mockResolvedValue(null);
    messageServiceMocks.queryMessages.mockReset().mockResolvedValue([]);
    topicServiceMocks.listTopics
      .mockReset()
      .mockResolvedValue([
        { name: 'order-create' },
        { name: 'payment-callback' },
        { name: 'user-activity-log' },
        { name: 'notification-push' },
        { name: 'inventory-sync' },
      ]);
    instanceFilterMocks.useInstanceFilter.mockReturnValue({
      selectedInstanceId: 1,
      selectInstance: vi.fn(),
      instanceOptions: [{ value: 1, label: 'Instance A' }],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requires the active query mode fields and trims submitted identifiers', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MessagePage />);
    const queryButton = screen.getByRole('button', { name: /^search查询$/ });

    expect(queryButton).toBeDisabled();
    await waitFor(() => expect(queryButton).toHaveAttribute('title', '请选择 Topic'));

    await user.click(lastElement(screen.getAllByRole('combobox')));
    await user.click(lastElement(await screen.findAllByText('order-create')));
    expect(queryButton).toBeEnabled();
    expect(queryButton).not.toHaveAttribute('title');

    await user.click(screen.getByText('按 Message Key'));
    expect(queryButton).toBeDisabled();
    expect(queryButton).toHaveAttribute('title', '请输入 Message Key');

    const keyInput = screen.getByPlaceholderText('输入 Message Key');
    await user.type(keyInput, '   ');
    expect(queryButton).toBeDisabled();
    expect(messageServiceMocks.queryMessages).not.toHaveBeenCalled();

    await user.clear(keyInput);
    await user.type(keyInput, '  ORDER-001  ');
    expect(queryButton).toBeEnabled();
    await user.click(queryButton);
    await waitFor(() => {
      expect(messageServiceMocks.queryMessages).toHaveBeenLastCalledWith({
        topic: 'order-create',
        key: 'ORDER-001',
        instanceId: 1,
      });
    });

    await user.click(screen.getByText('按 Message ID'));
    expect(queryButton).toBeDisabled();
    expect(queryButton).toHaveAttribute('title', '请输入 Message ID');

    const messageIdInput = screen.getByPlaceholderText('输入 Message ID');
    await user.type(messageIdInput, '   ');
    expect(queryButton).toBeDisabled();

    await user.clear(messageIdInput);
    await user.type(messageIdInput, '  MID-001  ');
    expect(queryButton).toBeEnabled();
    await user.click(queryButton);
    await waitFor(() => {
      expect(messageServiceMocks.queryMessages).toHaveBeenLastCalledWith({
        topic: 'order-create',
        msgId: 'MID-001',
        instanceId: 1,
      });
    });
  });

  it('surfaces Topic loading failures and retries without changing instance', async () => {
    const user = userEvent.setup();
    topicServiceMocks.listTopics
      .mockReset()
      .mockRejectedValueOnce(new Error('NameServer unavailable'))
      .mockResolvedValueOnce([{ name: 'orders' }]);

    renderWithProviders(<MessagePage />);

    expect(await screen.findByText('Topic 列表加载失败')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^search查询$/ })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: /重\s*试/ }));
    await waitFor(() => expect(topicServiceMocks.listTopics).toHaveBeenCalledTimes(2));
    await user.click(lastElement(screen.getAllByRole('combobox')));
    expect(await screen.findAllByText('orders')).not.toHaveLength(0);
  });

  it('requires a topic even when a key or message ID is present', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MessagePage />);
    const queryButton = screen.getByRole('button', { name: /^search查询$/ });

    await user.click(screen.getByText('按 Message Key'));
    await user.type(screen.getByPlaceholderText('输入 Message Key'), 'ORDER-001');
    expect(queryButton).toBeDisabled();
    expect(queryButton).toHaveAttribute('title', '请选择 Topic');

    await user.click(screen.getByText('按 Message ID'));
    await user.type(screen.getByPlaceholderText('输入 Message ID'), 'MID-001');
    expect(queryButton).toBeDisabled();
    expect(queryButton).toHaveAttribute('title', '请选择 Topic');
    expect(messageServiceMocks.queryMessages).not.toHaveBeenCalled();
  });

  it('ignores stored queries that are missing fields required by their mode', () => {
    localStorage.setItem(
      QUERY_HISTORY_STORAGE_KEY,
      JSON.stringify([
        { mode: 'topic', params: {} },
        { mode: 'key', params: { topic: 'order-create', key: '   ' } },
        { mode: 'msgid', params: { topic: 'order-create', msgId: '   ' } },
      ]),
    );

    renderWithProviders(<MessagePage />);

    expect(screen.getByRole('button', { name: /最近查询/ })).toBeDisabled();
    expect(messageServiceMocks.queryMessages).not.toHaveBeenCalled();
  });

  it('persists successful queries for replay and allows clearing the history', async () => {
    const user = userEvent.setup();
    const firstView = renderWithProviders(<MessagePage />);
    expect(screen.getByRole('button', { name: /最近查询/ })).toBeDisabled();

    await user.click(screen.getByText('按 Message ID'));
    await user.click(lastElement(screen.getAllByRole('combobox')));
    await user.click(lastElement(await screen.findAllByText('order-create')));
    await user.type(screen.getByPlaceholderText('输入 Message ID'), 'MID-001');
    await user.click(screen.getByRole('button', { name: /^search查询$/ }));

    await waitFor(() => {
      expect(messageServiceMocks.queryMessages).toHaveBeenCalledWith({
        topic: 'order-create',
        msgId: 'MID-001',
        instanceId: 1,
      });
      expect(screen.getByRole('button', { name: /最近查询/ })).toBeEnabled();
    });

    firstView.unmount();
    messageServiceMocks.queryMessages.mockClear();
    renderWithProviders(<MessagePage />);

    await user.click(screen.getByRole('button', { name: /最近查询/ }));
    await user.click(await screen.findByText('Message ID: MID-001 · Topic: order-create'));

    expect(screen.getByPlaceholderText('输入 Message ID')).toHaveValue('MID-001');
    await waitFor(() => {
      expect(messageServiceMocks.queryMessages).toHaveBeenCalledWith({
        topic: 'order-create',
        msgId: 'MID-001',
        instanceId: 1,
      });
    });

    await user.click(screen.getByRole('button', { name: /最近查询/ }));
    // Clearing requires confirmation: the dialog is commanded imperatively, so spy on it
    // and drive the confirm callback instead of depending on portal rendering in jsdom.
    const confirmSpy = vi
      .spyOn(Modal, 'confirm')
      .mockImplementation((config) => {
        config.onOk?.();
        return { destroy: vi.fn(), update: vi.fn() } as unknown as ReturnType<
          typeof Modal.confirm
        >;
      });
    await user.click(await screen.findByText('清空历史'));
    expect(confirmSpy).toHaveBeenCalled();
    confirmSpy.mockRestore();
    expect(screen.getByRole('button', { name: /最近查询/ })).toBeDisabled();
    expect(localStorage).toHaveLength(0);
  });

  it('does not save failed queries', async () => {
    const user = userEvent.setup();
    messageServiceMocks.queryMessages.mockRejectedValue(new Error('network error'));
    renderWithProviders(<MessagePage />);

    await user.click(screen.getByText('按 Message ID'));
    await user.click(lastElement(screen.getAllByRole('combobox')));
    await user.click(lastElement(await screen.findAllByText('order-create')));
    await user.type(screen.getByPlaceholderText('输入 Message ID'), 'MID-FAILED');
    await user.click(screen.getByRole('button', { name: /^search查询$/ }));

    await waitFor(() => {
      expect(messageServiceMocks.queryMessages).toHaveBeenCalledWith({
        topic: 'order-create',
        msgId: 'MID-FAILED',
        instanceId: 1,
      });
    });
    expect(screen.getByRole('button', { name: /最近查询/ })).toBeDisabled();
    expect(localStorage).toHaveLength(0);
  });

  it('keeps five unique queries and moves a repeated query to the front', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MessagePage />);
    await user.click(screen.getByText('按 Message ID'));
    await user.click(lastElement(screen.getAllByRole('combobox')));
    await user.click(lastElement(await screen.findAllByText('order-create')));
    const messageIdInput = screen.getByPlaceholderText('输入 Message ID');
    const queryButton = screen.getByRole('button', { name: /^search查询$/ });

    for (let index = 1; index <= 6; index += 1) {
      await user.clear(messageIdInput);
      await user.type(messageIdInput, `MID-${index}`);
      await user.click(queryButton);
      await waitFor(() => {
        expect(messageServiceMocks.queryMessages).toHaveBeenCalledTimes(index);
      });
    }

    const storageKey = localStorage.key(0);
    expect(storageKey).not.toBeNull();
    const firstHistory = JSON.parse(localStorage.getItem(storageKey!) || '[]') as Array<{
      params: { msgId: string };
    }>;
    expect(firstHistory.map((item) => item.params.msgId)).toEqual([
      'MID-6',
      'MID-5',
      'MID-4',
      'MID-3',
      'MID-2',
    ]);

    await user.clear(messageIdInput);
    await user.type(messageIdInput, 'MID-3');
    await user.click(queryButton);
    await waitFor(() => {
      expect(messageServiceMocks.queryMessages).toHaveBeenCalledTimes(7);
    });

    const updatedHistory = JSON.parse(localStorage.getItem(storageKey!) || '[]') as Array<{
      params: { msgId: string };
    }>;
    expect(updatedHistory.map((item) => item.params.msgId)).toEqual([
      'MID-3',
      'MID-6',
      'MID-5',
      'MID-4',
      'MID-2',
    ]);
  });

  it('replays topic and key queries with their saved parameters', async () => {
    const user = userEvent.setup();
    const topicParams = {
      topic: 'order-create',
      tag: 'vip',
      startTime: 1_700_000_000_000,
      endTime: 1_700_003_600_000,
      msgId: 'STALE-MESSAGE-ID',
    };
    const keyParams = {
      topic: 'payment-callback',
      key: 'ORDER-001',
      msgId: 'STALE-MESSAGE-ID',
    };
    localStorage.setItem(
      QUERY_HISTORY_STORAGE_KEY,
      JSON.stringify([
        { mode: 'topic', params: topicParams },
        { mode: 'key', params: keyParams },
      ]),
    );
    renderWithProviders(<MessagePage />);

    await user.click(screen.getByRole('button', { name: /最近查询/ }));
    await user.click(await screen.findByText('Topic: order-create'));
    await waitFor(() => {
      expect(messageServiceMocks.queryMessages).toHaveBeenLastCalledWith({
        topic: 'order-create',
        tag: 'vip',
        startTime: 1_700_000_000_000,
        endTime: 1_700_003_600_000,
        instanceId: 1,
      });
    });

    await user.click(screen.getByRole('button', { name: /最近查询/ }));
    await user.click(await screen.findByText('Key: ORDER-001 · Topic: payment-callback'));
    await waitFor(() => {
      expect(messageServiceMocks.queryMessages).toHaveBeenLastCalledWith({
        topic: 'payment-callback',
        key: 'ORDER-001',
        instanceId: 1,
      });
      expect(screen.getByPlaceholderText('输入 Message Key')).toHaveValue('ORDER-001');
    });
  });

  it('ignores malformed stored queries', async () => {
    const user = userEvent.setup();
    localStorage.setItem(
      QUERY_HISTORY_STORAGE_KEY,
      JSON.stringify([
        { mode: 'topic', params: { topic: ['invalid'] } },
        { mode: 'unknown', params: { topic: 'order-create' } },
        { mode: 'msgid', params: { msgId: 'MID-VALID', topic: 'order-create' } },
      ]),
    );
    renderWithProviders(<MessagePage />);

    await user.click(screen.getByRole('button', { name: /最近查询/ }));
    expect(
      await screen.findByText('Message ID: MID-VALID · Topic: order-create'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/invalid/)).not.toBeInTheDocument();
  });

  it('does not report consume verification success without a backend API', async () => {
    const user = userEvent.setup();
    messageServiceMocks.queryMessages.mockResolvedValue([createMessage('MID-CONSUME-VERIFY-001')]);
    renderWithProviders(<MessagePage />);

    await user.click(screen.getByText('按 Message ID'));
    await user.click(lastElement(screen.getAllByRole('combobox')));
    await user.click(lastElement(await screen.findAllByText('order-create')));
    await user.type(screen.getByPlaceholderText('输入 Message ID'), 'MID-CONSUME-VERIFY-001');
    await user.click(screen.getByRole('button', { name: /^search查询$/ }));

    expect(await screen.findByText('MID-CONSUME-VERIFY-001')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /验证/ }));

    expect(
      await screen.findByText('消费验证接口尚未接入，无法确认该消息的真实消费状态'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/消费验证成功/)).not.toBeInTheDocument();
  });

  it('sorts and renders messages without tags or keys', async () => {
    const user = userEvent.setup();
    messageServiceMocks.queryMessages.mockResolvedValue([
      { ...createMessage('MID-NULL-FIELDS'), tag: null, key: null },
      { ...createMessage('MID-FULL-FIELDS'), tag: 'vip', key: 'order-001' },
    ]);
    renderWithProviders(<MessagePage />);

    await user.click(screen.getByText('按 Message ID'));
    await user.click(lastElement(screen.getAllByRole('combobox')));
    await user.click(lastElement(await screen.findAllByText('order-create')));
    await user.type(screen.getByPlaceholderText('输入 Message ID'), 'MID');
    await user.click(screen.getByRole('button', { name: /^search查询$/ }));

    expect(await screen.findByText('MID-NULL-FIELDS')).toBeInTheDocument();
    expect(screen.getByText('MID-FULL-FIELDS')).toBeInTheDocument();
    expect(screen.getAllByText('-')).toHaveLength(2);

    await user.click(screen.getByRole('columnheader', { name: /Tag/ }));
    expect(screen.getByText('MID-NULL-FIELDS')).toBeInTheDocument();
    await user.click(screen.getByRole('columnheader', { name: /Key/ }));
    expect(screen.getByText('MID-FULL-FIELDS')).toBeInTheDocument();
  });

  it('requires an instance before allowing a message query', async () => {
    instanceFilterMocks.useInstanceFilter.mockReturnValue({
      selectedInstanceId: undefined,
      selectInstance: vi.fn(),
      instanceOptions: [],
    });
    const user = userEvent.setup();
    renderWithProviders(<MessagePage />);

    await user.click(screen.getByText('按 Message ID'));
    await user.type(screen.getByPlaceholderText('输入 Message ID'), 'MID-NO-INSTANCE');

    expect(screen.getByRole('button', { name: /^search查询$/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /最近查询/ })).toBeDisabled();
    expect(messageServiceMocks.queryMessages).not.toHaveBeenCalled();
  });

  it('loads topic options only for the selected instance', async () => {
    instanceFilterMocks.useInstanceFilter.mockReturnValue({
      selectedInstanceId: 1,
      selectInstance: vi.fn(),
      instanceOptions: [{ value: 1, label: 'Instance A' }],
    });
    topicServiceMocks.listTopics.mockResolvedValue([{ name: 'topic-on-instance-a' }]);
    renderWithProviders(<MessagePage />);

    await waitFor(() => {
      expect(topicServiceMocks.listTopics).toHaveBeenCalledWith({ instanceId: 1 });
    });
  });

  it('does not load static topic options without a selected instance', async () => {
    instanceFilterMocks.useInstanceFilter.mockReturnValue({
      selectedInstanceId: undefined,
      selectInstance: vi.fn(),
      instanceOptions: [],
    });
    renderWithProviders(<MessagePage />);

    await waitFor(() => {
      expect(topicServiceMocks.listTopics).not.toHaveBeenCalled();
    });
  });

  it('clears topic options when loading the selected instance topics fails', async () => {
    instanceFilterMocks.useInstanceFilter.mockReturnValue({
      selectedInstanceId: 1,
      selectInstance: vi.fn(),
      instanceOptions: [{ value: 1, label: 'Instance A' }],
    });
    topicServiceMocks.listTopics.mockRejectedValue(new Error('topic lookup failed'));
    const user = userEvent.setup();
    renderWithProviders(<MessagePage />);

    await waitFor(() => expect(topicServiceMocks.listTopics).toHaveBeenCalledTimes(1));
    await user.click(screen.getAllByRole('combobox')[1]);

    expect(screen.queryByText('order-create')).not.toBeInTheDocument();
  });
});
