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
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';

const messageServiceMocks = vi.hoisted(() => ({
  getMessageTrace: vi.fn(),
  queryMessages: vi.fn(),
}));

const QUERY_HISTORY_STORAGE_KEY = 'rocketmq-studio-message-query-history';

vi.mock('../../../services/messageService', () => messageServiceMocks);

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([]),
}));
vi.mock('../../../services/topicService', () => ({
  listTopics: vi.fn().mockResolvedValue([]),
}));

import MessagePage from '../message';

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

describe('Message page query history', () => {
  beforeEach(() => {
    localStorage.clear();
    messageServiceMocks.getMessageTrace.mockReset().mockResolvedValue(null);
    messageServiceMocks.queryMessages.mockReset().mockResolvedValue([]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('persists successful queries for replay and allows clearing the history', async () => {
    const user = userEvent.setup();
    const firstView = renderWithProviders(<MessagePage />);
    expect(screen.getByRole('button', { name: /最近查询/ })).toBeDisabled();

    await user.click(screen.getByText('按 Message ID'));
    await user.type(screen.getByPlaceholderText('输入 Message ID'), 'MID-001');
    await user.click(screen.getByRole('button', { name: /^search查询$/ }));

    await waitFor(() => {
      expect(messageServiceMocks.queryMessages).toHaveBeenCalledWith({ msgId: 'MID-001' });
      expect(screen.getByRole('button', { name: /最近查询/ })).toBeEnabled();
    });

    firstView.unmount();
    messageServiceMocks.queryMessages.mockClear();
    renderWithProviders(<MessagePage />);

    await user.click(screen.getByRole('button', { name: /最近查询/ }));
    await user.click(await screen.findByText('Message ID: MID-001'));

    expect(screen.getByPlaceholderText('输入 Message ID')).toHaveValue('MID-001');
    await waitFor(() => {
      expect(messageServiceMocks.queryMessages).toHaveBeenCalledWith({ msgId: 'MID-001' });
    });

    await user.click(screen.getByRole('button', { name: /最近查询/ }));
    await user.click(await screen.findByText('清空历史'));
    expect(screen.getByRole('button', { name: /最近查询/ })).toBeDisabled();
    expect(localStorage).toHaveLength(0);
  });

  it('does not save failed queries', async () => {
    const user = userEvent.setup();
    messageServiceMocks.queryMessages.mockRejectedValue(new Error('network error'));
    renderWithProviders(<MessagePage />);

    await user.click(screen.getByText('按 Message ID'));
    await user.type(screen.getByPlaceholderText('输入 Message ID'), 'MID-FAILED');
    await user.click(screen.getByRole('button', { name: /^search查询$/ }));

    await waitFor(() => {
      expect(messageServiceMocks.queryMessages).toHaveBeenCalledWith({ msgId: 'MID-FAILED' });
    });
    expect(screen.getByRole('button', { name: /最近查询/ })).toBeDisabled();
    expect(localStorage).toHaveLength(0);
  });

  it('keeps five unique queries and moves a repeated query to the front', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MessagePage />);
    await user.click(screen.getByText('按 Message ID'));
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
    };
    const keyParams = { topic: 'payment-callback', key: 'ORDER-001' };
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
      expect(messageServiceMocks.queryMessages).toHaveBeenLastCalledWith(topicParams);
    });

    await user.click(screen.getByRole('button', { name: /最近查询/ }));
    await user.click(await screen.findByText('Key: ORDER-001 · Topic: payment-callback'));
    await waitFor(() => {
      expect(messageServiceMocks.queryMessages).toHaveBeenLastCalledWith(keyParams);
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
        { mode: 'msgid', params: { msgId: 'MID-VALID' } },
      ]),
    );
    renderWithProviders(<MessagePage />);

    await user.click(screen.getByRole('button', { name: /最近查询/ }));
    expect(await screen.findByText('Message ID: MID-VALID')).toBeInTheDocument();
    expect(screen.queryByText(/invalid/)).not.toBeInTheDocument();
    expect(screen.queryByText(/order-create/)).not.toBeInTheDocument();
  });
});
