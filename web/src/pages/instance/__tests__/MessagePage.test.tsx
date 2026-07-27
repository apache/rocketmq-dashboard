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

import { App, message } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import * as messageService from '../../../services/messageService';
import * as topicService from '../../../services/topicService';
import MessagePage from '../message';

vi.mock('../../../services/topicService', () => ({
  listTopics: vi.fn(),
}));

vi.mock('../../../services/messageService', () => ({
  getMessageTrace: vi.fn(),
  queryMessages: vi.fn(),
}));

vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd');
  return {
    ...actual,
    message: {
      ...actual.message,
      error: vi.fn(),
      success: vi.fn(),
      warning: vi.fn(),
    },
  };
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
      <LangProvider>{ui}</LangProvider>
    </App>,
  );

const topic = {
  name: 'production-orders',
  namespace: 'production',
  type: 'NORMAL',
  clusterId: 'cluster-a',
  writeQueues: 8,
  readQueues: 8,
  perm: 'RW',
  messageCount: 0,
  tps: 0,
  consumerGroupCount: 0,
  remark: '',
  createdAt: '2026-07-27T00:00:00Z',
  updatedAt: '2026-07-27T00:00:00Z',
};

const messageRecord = {
  msgId: 'message-id-1',
  topic: 'production-orders',
  tag: 'created',
  key: 'order-key',
  body: '{"orderId":"1"}',
  storeTime: '2026-07-27T00:00:00Z',
  bornHost: '127.0.0.1:1000',
  storeHost: '127.0.0.1:10911',
  properties: {},
  size: 128,
};

describe('Message page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(topicService.listTopics).mockResolvedValue([topic]);
    vi.mocked(messageService.queryMessages).mockResolvedValue([]);
  });

  it('loads Topic options through the service layer', async () => {
    const user = userEvent.setup();

    renderWithProviders(<MessagePage />);

    const topicSelect = await screen.findByRole('combobox');
    await user.click(topicSelect);

    expect((await screen.findAllByText('production-orders')).length).toBeGreaterThan(0);
    expect(screen.queryByText('order-create')).not.toBeInTheDocument();
    expect(topicService.listTopics).toHaveBeenCalledTimes(1);
  });

  it('reports Topic loading failures and leaves the options empty', async () => {
    vi.mocked(topicService.listTopics).mockRejectedValue(new Error('network error'));

    renderWithProviders(<MessagePage />);

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith('Topic 列表加载失败，请稍后重试');
    });
    expect(screen.getByRole('combobox')).not.toBeDisabled();
  });

  it('requires a Topic before querying by Topic', async () => {
    const user = userEvent.setup();

    renderWithProviders(<MessagePage />);
    await waitFor(() => {
      expect(screen.getByRole('combobox')).not.toBeDisabled();
    });

    await user.click(screen.getByRole('button', { name: /查询/ }));

    expect(message.warning).toHaveBeenCalledWith('请选择 Topic');
    expect(messageService.queryMessages).not.toHaveBeenCalled();

    const topicSelect = screen.getByRole('combobox');
    await user.click(topicSelect);
    const topicOptions = await screen.findAllByText('production-orders');
    await user.click(topicOptions[topicOptions.length - 1]);
    await user.click(screen.getByRole('button', { name: /查询/ }));

    expect(messageService.queryMessages).toHaveBeenCalledWith(
      expect.objectContaining({
        topic: 'production-orders',
        startTime: expect.any(Number),
        endTime: expect.any(Number),
      }),
    );
  });

  it('requires a Topic and Key before querying by Message Key', async () => {
    const user = userEvent.setup();

    renderWithProviders(<MessagePage />);
    await user.click(screen.getByText('按 Message Key'));
    await waitFor(() => {
      expect(screen.getByRole('combobox')).not.toBeDisabled();
    });

    await user.click(screen.getByRole('button', { name: /查询/ }));
    expect(message.warning).toHaveBeenLastCalledWith('请选择 Topic');

    const topicSelect = screen.getByRole('combobox');
    await user.click(topicSelect);
    const topicOptions = await screen.findAllByText('production-orders');
    await user.click(topicOptions[topicOptions.length - 1]);
    await user.click(screen.getByRole('button', { name: /查询/ }));
    expect(message.warning).toHaveBeenLastCalledWith('请输入 Message Key');
    expect(messageService.queryMessages).not.toHaveBeenCalled();

    await user.type(screen.getByPlaceholderText('输入 Message Key'), '  order-key  ');
    await user.click(screen.getByRole('button', { name: /查询/ }));

    expect(messageService.queryMessages).toHaveBeenCalledWith({
      topic: 'production-orders',
      key: 'order-key',
    });
  });

  it('requires and trims the Message ID before querying', async () => {
    const user = userEvent.setup();

    renderWithProviders(<MessagePage />);
    await user.click(screen.getByText('按 Message ID'));

    await user.click(screen.getByRole('button', { name: /查询/ }));
    expect(message.warning).toHaveBeenCalledWith('请输入 Message ID');
    expect(messageService.queryMessages).not.toHaveBeenCalled();

    await user.type(screen.getByPlaceholderText('输入 Message ID'), '  message-id-1  ');
    await user.click(screen.getByRole('button', { name: /查询/ }));

    expect(messageService.queryMessages).toHaveBeenCalledWith({
      msgId: 'message-id-1',
    });
  });

  it('loads real consumer status when verifying a message', async () => {
    const user = userEvent.setup();
    vi.mocked(messageService.queryMessages).mockResolvedValue([messageRecord]);
    vi.mocked(messageService.getMessageTrace).mockResolvedValue({
      nodes: [],
      consumerStatus: [
        {
          group: 'production-consumer',
          deliveryStatus: 'success',
          consumeTime: '2026-07-27T00:00:01Z',
          retryCount: 0,
        },
      ],
    });

    renderWithProviders(<MessagePage />);
    await user.click(screen.getByText('按 Message ID'));
    await user.type(screen.getByPlaceholderText('输入 Message ID'), 'message-id-1');
    await user.click(screen.getByRole('button', { name: /查询/ }));

    const verifyButton = await screen.findByRole('button', { name: /验证/ });
    vi.mocked(message.success).mockClear();
    await user.click(verifyButton);

    expect(messageService.getMessageTrace).toHaveBeenCalledWith('message-id-1');
    expect(await screen.findByText('production-consumer')).toBeInTheDocument();
    expect(message.success).not.toHaveBeenCalled();
  });
});
