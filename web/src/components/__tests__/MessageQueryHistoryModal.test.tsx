/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import MessageQueryHistoryModal from '../MessageQueryHistoryModal';
import { LangProvider } from '../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/languagePreference';
import { formatUtcDateTime } from '../../utils/format';
import {
  getQueryHistorySummary,
  listMessageQueryHistory,
  listTraceQueryHistory,
} from '../../api/messageHistory';

vi.mock('../../api/messageHistory', () => ({
  getQueryHistorySummary: vi.fn(),
  listMessageQueryHistory: vi.fn(),
  listTraceQueryHistory: vi.fn(),
}));

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    value: vi.fn().mockImplementation(() => ({
      matches: false,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

describe('MessageQueryHistoryModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.mocked(getQueryHistorySummary).mockResolvedValue({ messageQueries: 4, traceQueries: 2 });
    vi.mocked(listMessageQueryHistory).mockResolvedValue({
      items: [
        {
          id: 1,
          queryType: 'KEY',
          topic: 'orders',
          messageKey: 'order-1',
          resultCount: 2,
          queriedBy: 'alice',
          queriedAt: '2026-08-05T12:00:00Z',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
    vi.mocked(listTraceQueryHistory).mockResolvedValue({
      items: [
        {
          id: 2,
          msgId: 'msg-1',
          topic: 'orders',
          traceTopic: 'CUSTOM_TRACE',
          nodeCount: 3,
          consumerCount: 1,
          queriedBy: 'bob',
          queriedAt: '2026-08-05T12:00:00Z',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
  });

  it('renders as a modal instead of a side drawer', async () => {
    render(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('order-1')).toBeInTheDocument();
    expect(document.querySelector('.ant-modal')).not.toBeNull();
    expect(document.querySelector('.ant-drawer')).toBeNull();
  });

  it('keeps the history tables at the small row height every other list uses', async () => {
    render(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    // 默认尺寸表格的单行约 54px，全站列表口径是 small 的约 40px；这两个表漏了 size。
    expect(await screen.findByText('order-1')).toBeInTheDocument();
    expect(document.querySelector('.ant-table')).toHaveClass('ant-table-small');
  });

  it.each(['messages', 'traces'] as const)(
    'recovers an out-of-range %s page after changing instance Test',
    async (tab) => {
      const user = userEvent.setup();
      const row = tab === 'messages'
        ? { id: 1, queryType: 'KEY', topic: 'orders', messageKey: 'remaining-record', resultCount: 1, queriedBy: 'alice', queriedAt: '' }
        : { id: 1, msgId: 'remaining-record', topic: 'orders', nodeCount: 1, consumerCount: 1, queriedBy: 'alice', queriedAt: '' };
      const list = tab === 'messages' ? vi.mocked(listMessageQueryHistory) : vi.mocked(listTraceQueryHistory);
      list.mockImplementation(async ({ clusterId, page = 1 }) => ({
        items: clusterId === 'instance-a' || page === 1 ? [row] : [],
        total: clusterId === 'instance-a' ? 60 : 1,
        page, size: 20,
      }) as never);
      const modal = (clusterId: string) => (
        <App><LangProvider><MessageQueryHistoryModal open clusterId={clusterId} onClose={vi.fn()} /></LangProvider></App>
      );
      const view = render(modal('instance-a'));
      if (tab === 'traces') await user.click(screen.getByRole('tab', { name: '轨迹查询' }));
      expect(await screen.findByText('remaining-record')).toBeInTheDocument();
      await user.click(within(screen.getByRole('tabpanel')).getByTitle('3'));
      await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 3 })));
      view.rerender(modal('instance-b'));
      await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ clusterId: 'instance-b', page: 1 })));
      expect(await screen.findByText('remaining-record')).toBeInTheDocument();
    },
  );

  it('does not offer a page size that fixed-size API requests ignore Test', async () => {
    vi.mocked(listMessageQueryHistory).mockResolvedValue({ items: [], total: 60, page: 1, size: 20 });
    render(<App><LangProvider><MessageQueryHistoryModal open onClose={vi.fn()} /></LangProvider></App>);
    await screen.findByTitle('3');
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });

  it('loads persisted message and trace history by instance', async () => {
    const user = userEvent.setup();
    render(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('order-1')).toBeInTheDocument();
    expect(listMessageQueryHistory).toHaveBeenCalledWith(
      expect.objectContaining({ clusterId: 'instance-a' }),
    );
    await user.click(screen.getByRole('tab', { name: '轨迹查询' }));
    expect(await screen.findByText('msg-1')).toBeInTheDocument();
    expect(screen.getByText('CUSTOM_TRACE')).toBeInTheDocument();
    await waitFor(() => expect(listTraceQueryHistory).toHaveBeenCalled());
  });

  it('keeps the new instance rows when only the summary fails', async () => {
    const view = render(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );
    expect(await screen.findByText('order-1')).toBeInTheDocument();
    vi.mocked(getQueryHistorySummary).mockRejectedValueOnce(new Error('network unavailable'));
    vi.mocked(listMessageQueryHistory).mockResolvedValueOnce({
      items: [
        {
          id: 3,
          queryType: 'KEY',
          topic: 'payments',
          messageKey: 'payment-3',
          resultCount: 1,
          queriedBy: 'bob',
          queriedAt: '2026-08-05T13:00:00Z',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });

    view.rerender(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-b" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('查询历史统计加载失败')).toBeInTheDocument();
    expect(screen.getByText('payment-3')).toBeInTheDocument();
    expect(screen.queryByText('order-1')).not.toBeInTheDocument();
    expect(
      [...document.querySelectorAll('.ant-statistic-content-value')]
        .slice(0, 2)
        .map((element) => element.textContent),
    ).toEqual(['-', '-']);
    expect(screen.getByRole('button', { name: /重\s*试/ })).toBeEnabled();
  });

  it('keeps the summary when only the message rows fail', async () => {
    vi.mocked(listMessageQueryHistory).mockRejectedValueOnce(new Error('rows unavailable'));
    render(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('查询历史加载失败')).toBeInTheDocument();
    expect(screen.queryByText('order-1')).not.toBeInTheDocument();
    expect(
      [...document.querySelectorAll('.ant-statistic-content-value')]
        .slice(0, 2)
        .map((element) => element.textContent),
    ).toEqual(['4', '2']);
  });

  it('shows the loaded rows while the independent summary request is still pending', async () => {
    let resolveSummary: (value: { messageQueries: number; traceQueries: number }) => void =
      () => {};
    vi.mocked(getQueryHistorySummary).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveSummary = resolve;
        }),
    );
    render(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('order-1')).toBeInTheDocument();
    resolveSummary({ messageQueries: 4, traceQueries: 2 });
  });

  it('treats offset-less queriedAt values as UTC', async () => {
    vi.mocked(listMessageQueryHistory).mockResolvedValue({
      items: [
        {
          id: 1,
          queryType: 'KEY',
          topic: 'orders',
          messageKey: 'order-1',
          resultCount: 2,
          queriedBy: 'alice',
          queriedAt: '2026-08-05T12:00:00',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });

    render(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('order-1')).toBeInTheDocument();
    expect(screen.getByText(formatUtcDateTime('2026-08-05T12:00:00'))).toBeInTheDocument();
  });

  it('renders query history modal copy in English mode', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    render(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('order-1')).toBeInTheDocument();
    expect(screen.getByText('Server Query History')).toBeInTheDocument();
    expect(screen.getAllByText('Message Queries').length).toBeGreaterThan(1);
    expect(
      screen.getByPlaceholderText('Search Topic, trace Topic, Message ID, Key or operator'),
    ).toBeInTheDocument();
    expect(screen.queryByText('服务端查询历史')).not.toBeInTheDocument();
  });

  it('keeps the applied search visible in the input after the modal is closed and reopened', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    const user = userEvent.setup();
    const view = render(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('order-1')).toBeInTheDocument();
    const searchInput = screen.getByPlaceholderText(
      'Search Topic, trace Topic, Message ID, Key or operator',
    );
    await user.type(searchInput, 'order-1');
    await user.keyboard('{Enter}');

    await waitFor(() =>
      expect(listMessageQueryHistory).toHaveBeenCalledWith(
        expect.objectContaining({ search: 'order-1' }),
      ),
    );

    view.rerender(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open={false} clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );
    view.rerender(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    // The applied filter is still active after reopening, so the visible input must
    // show it instead of an empty field that contradicts the filtered table below.
    expect(await screen.findByText('order-1')).toBeInTheDocument();
    expect(
      await screen.findByPlaceholderText('Search Topic, trace Topic, Message ID, Key or operator'),
    ).toHaveValue('order-1');
  });

  it('applies a cleared search field as an empty filter', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    const user = userEvent.setup();
    render(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('order-1')).toBeInTheDocument();
    const searchInput = screen.getByPlaceholderText(
      'Search Topic, trace Topic, Message ID, Key or operator',
    );
    await user.type(searchInput, 'order-1');
    await user.keyboard('{Enter}');
    await waitFor(() =>
      expect(listMessageQueryHistory).toHaveBeenCalledWith(
        expect.objectContaining({ search: 'order-1' }),
      ),
    );

    await user.clear(searchInput);
    await user.keyboard('{Enter}');
    await waitFor(() =>
      expect(listMessageQueryHistory).toHaveBeenLastCalledWith(
        expect.objectContaining({ search: undefined }),
      ),
    );
  });
});
