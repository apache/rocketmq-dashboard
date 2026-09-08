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
import { App, ConfigProvider } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';

import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import MessagePage from '../message';

const serviceMocks = vi.hoisted(() => ({
  consumeMessageDirectly: vi.fn(),
  getMessageTrace: vi.fn(),
  getMessageTraceByKey: vi.fn(),
  queryMessages: vi.fn(),
}));
const instanceFilterMocks = vi.hoisted(() => ({
  useInstanceFilter: vi.fn(),
}));

vi.mock('../../../services/messageService', () => ({
  ...serviceMocks,
  queryMessagePage: ({ page = 1, pageSize = 50, ...params }: Record<string, unknown>) =>
    Promise.resolve(serviceMocks.queryMessages(params)).then((items) => ({
      items: Array.isArray(items) ? items : [],
      total: Array.isArray(items) ? items.length : 0,
      page,
      size: pageSize,
      resultMayBeTruncated: false,
    })),
}));
vi.mock('../../../hooks/useInstanceFilter', () => instanceFilterMocks);
vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([]),
}));
vi.mock('../../../services/topicService', () => ({
  listTopics: vi.fn().mockResolvedValue([{ name: 'orders' }]),
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

const renderPage = () =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <App>
        <LangProvider>
          <MemoryRouter>
            <MessagePage />
          </MemoryRouter>
        </LangProvider>
      </App>
    </ConfigProvider>,
  );

const selectTopic = async (user: ReturnType<typeof userEvent.setup>) => {
  const topicSelects = screen.getAllByRole('combobox');
  await user.click(topicSelects[topicSelects.length - 1]!);
  const options = await screen.findAllByText('orders');
  await user.click(options[options.length - 1]!);
};

describe('Message page key query', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    serviceMocks.getMessageTrace.mockResolvedValue(null);
    serviceMocks.getMessageTraceByKey.mockResolvedValue(null);
    serviceMocks.queryMessages.mockResolvedValue([]);
    instanceFilterMocks.useInstanceFilter.mockReturnValue({
      selectedInstanceId: 1,
      selectInstance: vi.fn(),
      instanceOptions: [{ value: 1, label: 'Instance A' }],
    });
  });

  it('submits the selected time range and tag with a key query', async () => {
    const user = userEvent.setup();
    renderPage();

    await selectTopic(user);
    await user.click(screen.getByText('按 Message Key'));
    await user.type(screen.getByPlaceholderText('输入 Message Key'), ' ORDER-001 ');
    await user.type(screen.getByPlaceholderText('输入 Tag（可选）'), ' vip ');

    const queryButton = screen.getByRole('button', { name: /^search查询$/ });
    await waitFor(() => expect(queryButton).toBeEnabled());
    await user.click(queryButton);

    await waitFor(() => expect(serviceMocks.queryMessages).toHaveBeenCalled());
    expect(serviceMocks.queryMessages).toHaveBeenLastCalledWith({
      instanceId: 1,
      topic: 'orders',
      key: 'ORDER-001',
      tag: 'vip',
      startTime: expect.any(Number),
      endTime: expect.any(Number),
    });
  });
});
