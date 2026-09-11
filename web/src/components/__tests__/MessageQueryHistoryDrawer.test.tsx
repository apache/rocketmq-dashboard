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
import MessageQueryHistoryDrawer from '../MessageQueryHistoryDrawer';
import { LangProvider } from '../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/languagePreference';
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

describe('MessageQueryHistoryDrawer', () => {
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

  it('loads persisted message and trace history by instance', async () => {
    const user = userEvent.setup();
    render(
      <App>
        <LangProvider>
          <MessageQueryHistoryDrawer open clusterId="instance-a" onClose={vi.fn()} />
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
          <MessageQueryHistoryDrawer open clusterId="instance-a" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );
    expect(await screen.findByText('order-1')).toBeInTheDocument();
    vi.mocked(getQueryHistorySummary).mockRejectedValueOnce(new Error('network unavailable'));

    view.rerender(
      <App>
        <LangProvider>
          <MessageQueryHistoryDrawer open clusterId="instance-b" onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('查询历史加载失败')).toBeInTheDocument();
    expect(screen.queryByText('order-1')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /重\s*试/ })).toBeEnabled();
  });

  it('renders query history drawer copy in English mode', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    render(
      <App>
        <LangProvider>
          <MessageQueryHistoryDrawer open clusterId="instance-a" onClose={vi.fn()} />
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
});
