/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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

  it('clears stale rows and offers retry when a new instance load fails', async () => {
    const view = render(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );
    expect(await screen.findByText('order-1')).toBeInTheDocument();
    vi.mocked(getQueryHistorySummary).mockRejectedValueOnce(new Error('network unavailable'));

    view.rerender(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-b" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('查询历史加载失败')).toBeInTheDocument();
    expect(screen.queryByText('order-1')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /重\s*试/ })).toBeEnabled();
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
    expect(screen.getByText('Query by Message Key')).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText('Search Topic, trace Topic, Message ID, Key or operator'),
    ).toBeInTheDocument();
    expect(screen.queryByText('服务端查询历史')).not.toBeInTheDocument();
  });

  it('names the recorded query mode instead of printing its wire code', async () => {
    render(
      <App>
        <LangProvider>
          <MessageQueryHistoryModal open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    // The search bar above names the same mode; the history row must not fall back to `KEY`.
    expect(await screen.findByText('order-1')).toBeInTheDocument();
    expect(screen.getByText('按 Message Key')).toBeInTheDocument();
    expect(screen.queryByText('KEY')).not.toBeInTheDocument();

    // The column declares room for the longest label ("Query by Message Key"): a narrower one wraps
    // inside the tag and grows the row height of the small table.
    expect(document.querySelector('.ant-table colgroup col')).toHaveStyle({ width: '160px' });
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
