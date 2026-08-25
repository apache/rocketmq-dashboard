/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 * You may obtain a copy of the License at
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
const downloadMocks = vi.hoisted(() => ({
  downloadBlob: vi.fn(),
}));

vi.mock('../../../services/messageService', () => ({
  ...messageServiceMocks,
  queryMessagePage: (params: Record<string, unknown>) =>
    Promise.resolve(messageServiceMocks.queryMessages(params)).then((items: MessageRecord[]) => ({
      items,
      total: items.length,
      page: 1,
      size: 50,
      resultMayBeTruncated: false,
    })),
}));
vi.mock('../../../hooks/useInstanceFilter', () => instanceFilterMocks);
vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([]),
}));
vi.mock('../../../services/topicService', () => topicServiceMocks);
vi.mock('../../../utils/download', () => downloadMocks);

import MessagePage from '../message';

const baseRecord: Omit<MessageRecord, 'msgId' | 'topic' | 'body' | 'bodyEncoding'> = {
  tag: 'tag',
  key: 'key-1',
  brokerName: 'broker-a',
  queueId: 0,
  queueOffset: 0,
  bodyTruncated: false,
  storeTime: '2026-07-31T00:00:00Z',
  bornHost: '127.0.0.1:1000',
  storeHost: '127.0.0.1:10911',
  properties: {},
  propertiesTruncated: false,
  size: 64,
};

const binaryMessage: MessageRecord = {
  ...baseRecord,
  msgId: 'binary-msg',
  topic: 'image-upload',
  body: 'AAEC AQIBAA==',
  bodyEncoding: 'BASE64',
  bodyTruncated: true,
  propertiesTruncated: true,
};

const textMessage: MessageRecord = {
  ...baseRecord,
  msgId: 'text-msg',
  topic: 'order-create',
  body: '{"orderId":42}',
  bodyEncoding: 'UTF-8',
};

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

describe('Message body encoding and truncation metadata', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    messageServiceMocks.getMessageTrace.mockResolvedValue(null);
    messageServiceMocks.queryMessages.mockResolvedValue([binaryMessage, textMessage]);
    topicServiceMocks.listTopics.mockResolvedValue([
      { name: 'image-upload' },
      { name: 'order-create' },
    ]);
    instanceFilterMocks.useInstanceFilter.mockReturnValue({
      selectedInstanceId: 1,
      selectInstance: vi.fn(),
      instanceOptions: [{ value: 1, label: 'Instance A' }],
    });
  });

  const lastElement = <T,>(elements: T[]): T => elements[elements.length - 1]!;

  const queryCurrentInstance = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.click(lastElement(screen.getAllByRole('combobox')));
    await user.click(lastElement(await screen.findAllByText('image-upload')));
    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    await screen.findByText('binary-msg');
    await screen.findByText('text-msg');
  };

  it('warns about truncated bodies and properties and marks Base64 bodies in the detail view', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MessagePage />);
    await queryCurrentInstance(user);

    const detailButtons = await screen.findAllByRole('button', { name: /详情/ });
    await user.click(detailButtons[0]);

    expect(await screen.findByText('Base64')).toBeInTheDocument();
    expect(screen.getByText('消息体超过展示上限，下方内容已被截断')).toBeInTheDocument();
    expect(screen.getByText('消息属性数量或长度超限，部分属性未完整展示')).toBeInTheDocument();
    expect(screen.getByText('AAEC AQIBAA==')).toBeInTheDocument();
  });

  it('keeps plain text bodies free of encoding warnings', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MessagePage />);
    await queryCurrentInstance(user);

    const detailButtons = await screen.findAllByRole('button', { name: /详情/ });
    await user.click(detailButtons[1]);

    await screen.findByText(/orderId/);
    expect(screen.queryByText('Base64')).not.toBeInTheDocument();
    expect(screen.queryByText('消息体超过展示上限，下方内容已被截断')).not.toBeInTheDocument();
    expect(
      screen.queryByText('消息属性数量或长度超限，部分属性未完整展示'),
    ).not.toBeInTheDocument();
  });

  it('downloads Base64 bodies as plain text and UTF-8 bodies as JSON', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MessagePage />);
    await queryCurrentInstance(user);

    const downloadButtons = await screen.findAllByRole('button', { name: /下载/ });
    await user.click(downloadButtons[0]);
    await waitFor(() => expect(downloadMocks.downloadBlob).toHaveBeenCalledTimes(1));
    const binaryCall = downloadMocks.downloadBlob.mock.calls[0];
    expect(binaryCall[1]).toBe('binary-msg.txt');
    expect(binaryCall[0].type).toBe('text/plain');
    await expect(binaryCall[0].text()).resolves.toBe('AAEC AQIBAA==');

    await user.click(downloadButtons[1]);
    await waitFor(() => expect(downloadMocks.downloadBlob).toHaveBeenCalledTimes(2));
    const textCall = downloadMocks.downloadBlob.mock.calls[1];
    expect(textCall[1]).toBe('text-msg.json');
    expect(textCall[0].type).toBe('application/json');
    await expect(textCall[0].text()).resolves.toContain('"orderId": 42');
  });
});
