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
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App, message } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import type { Topic } from '../../../api/metadata';
import TopicPage from '../topic';

const topicServiceMocks = vi.hoisted(() => ({
  batchDeleteTopics: vi.fn(),
  createTopic: vi.fn(),
  deleteTopic: vi.fn(),
  getTopicConsumers: vi.fn(),
  getTopicRoutes: vi.fn(),
  listTopics: vi.fn(),
  sendTopicMessage: vi.fn(),
}));

vi.mock('../../../services/topicService', () => topicServiceMocks);

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

const buildTopics = (count: number): Topic[] =>
  Array.from({ length: count }, (_, index) => {
    const suffix = String(index + 1).padStart(2, '0');
    return {
      name: `topic-${suffix}`,
      namespace: 'default',
      type: 'NORMAL',
      clusterId: 'rmq-cn-v5-prod-01',
      writeQueues: 8,
      readQueues: 8,
      perm: 'RW',
      messageCount: index,
      tps: index,
      consumerGroupCount: 0,
      remark: `Topic ${suffix}`,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    };
  });

const renderWithProviders = () =>
  render(
    <App>
      <LangProvider>
        <TopicPage />
      </LangProvider>
    </App>,
  );

const getTableBody = () => {
  const tableBody = document.querySelector('.ant-table-tbody');
  expect(tableBody).not.toBeNull();
  return tableBody as HTMLElement;
};

describe('TopicPage', () => {
  beforeEach(() => {
    topicServiceMocks.listTopics.mockResolvedValue(buildTopics(25));
    topicServiceMocks.batchDeleteTopics.mockResolvedValue({ deleted: [], failed: [] });
    topicServiceMocks.getTopicRoutes.mockResolvedValue([]);
    topicServiceMocks.getTopicConsumers.mockResolvedValue([]);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('keeps the current table page after opening and closing topic details', async () => {
    const user = userEvent.setup();
    renderWithProviders();

    expect(await screen.findByText('topic-01')).toBeInTheDocument();

    const secondPage = document.querySelector('.ant-pagination-item-2');
    expect(secondPage).not.toBeNull();
    await user.click(secondPage as HTMLElement);

    await waitFor(() => expect(within(getTableBody()).getByText('topic-21')).toBeInTheDocument());
    expect(within(getTableBody()).queryByText('topic-01')).not.toBeInTheDocument();

    await user.click(screen.getAllByRole('button', { name: /详情/ })[0]);
    await waitFor(() => expect(topicServiceMocks.getTopicRoutes).toHaveBeenCalledWith('topic-21'));

    const closeButton = document.querySelector('.ant-modal-close');
    expect(closeButton).not.toBeNull();
    await user.click(closeButton as HTMLElement);

    expect(within(getTableBody()).getByText('topic-21')).toBeInTheDocument();
    expect(within(getTableBody()).queryByText('topic-01')).not.toBeInTheDocument();
  });

  it('keeps failed topics selected after a partially successful batch deletion', async () => {
    const user = userEvent.setup();
    topicServiceMocks.listTopics.mockResolvedValue(buildTopics(3));
    topicServiceMocks.batchDeleteTopics.mockResolvedValue({
      deleted: ['topic-01', 'topic-03'],
      failed: ['topic-02'],
    });
    renderWithProviders();

    expect(await screen.findByText('topic-01')).toBeInTheDocument();
    await user.click(screen.getAllByRole('checkbox')[0]);
    await user.click(screen.getByRole('button', { name: /删除 \(3\)$/ }));

    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: /删\s*除/ }));

    await waitFor(() => expect(screen.queryByText('topic-01')).not.toBeInTheDocument());
    expect(screen.getByText('topic-02')).toBeInTheDocument();
    expect(screen.queryByText('topic-03')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /删除 \(1\)$/ })).toBeInTheDocument();
    expect(screen.getByText('已删除 2 个 Topic，1 个删除失败')).toBeInTheDocument();
  });

  it('shows an error when sending a test message fails', async () => {
    const user = userEvent.setup();
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(vi.fn());
    topicServiceMocks.listTopics.mockResolvedValue(buildTopics(1));
    topicServiceMocks.sendTopicMessage.mockRejectedValue(new Error('send failed'));

    renderWithProviders();

    expect(await screen.findByText('topic-01')).toBeInTheDocument();
    const row = within(getTableBody()).getByText('topic-01').closest('tr');
    expect(row).not.toBeNull();
    await user.click(within(row as HTMLElement).getByRole('button', { name: /发送/ }));
    const dialogTitle = await screen.findByText('发送消息到 topic-01');
    const dialog = dialogTitle.closest('[role="dialog"]') as HTMLElement;
    expect(dialog).not.toBeNull();
    await user.type(within(dialog).getByLabelText('消息体 Body'), 'test message');
    await user.click(within(dialog).getByRole('button', { name: /^发\s*送$/ }));

    await waitFor(() => expect(topicServiceMocks.sendTopicMessage).toHaveBeenCalledTimes(1));
    expect(errorSpy).toHaveBeenCalledWith('发送测试消息失败，请稍后重试');
    expect(dialogTitle).toBeInTheDocument();
    errorSpy.mockRestore();
  });
});
