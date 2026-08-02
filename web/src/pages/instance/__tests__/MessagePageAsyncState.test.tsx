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
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MessageRecord, TraceRecord } from '../../../api/message';
import { LangProvider } from '../../../i18n/LangContext';
import MessagePage from '../message';

const serviceMocks = vi.hoisted(() => ({
  getMessageTrace: vi.fn(),
  queryMessages: vi.fn(),
}));

vi.mock('../../../services/messageService', () => serviceMocks);

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

const renderPage = () =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <App>
        <LangProvider>
          <MessagePage />
        </LangProvider>
      </App>
    </ConfigProvider>,
  );

describe('MessagePage async request ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    serviceMocks.getMessageTrace.mockResolvedValue(null);
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

    await user.click(screen.getByRole('button', { name: /^search查询$/ }));
    await waitFor(() => expect(serviceMocks.queryMessages).toHaveBeenCalledTimes(1));
    await user.click(screen.getByRole('button', { name: /重置/ }));

    await act(async () => {
      query.resolve([createMessage('late-after-reset')]);
    });

    expect(screen.queryByText('late-after-reset')).not.toBeInTheDocument();
  });

  it('keeps the latest query loading and ignores an earlier query result', async () => {
    const firstQuery = createDeferred<MessageRecord[]>();
    const secondQuery = createDeferred<MessageRecord[]>();
    serviceMocks.queryMessages
      .mockReturnValueOnce(firstQuery.promise)
      .mockReturnValueOnce(secondQuery.promise);
    const user = userEvent.setup();
    renderPage();

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

    expect(await screen.findByText('message-b trace')).toBeInTheDocument();
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
    expect(await screen.findByText('message-b trace')).toBeInTheDocument();

    await act(async () => {
      firstTrace.resolve(createTrace('message-a stale trace'));
    });

    expect(screen.getByText('message-b trace')).toBeInTheDocument();
    expect(screen.queryByText('message-a stale trace')).not.toBeInTheDocument();
  });
});
