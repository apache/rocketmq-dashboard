/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { App } from 'antd';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../i18n/LangContext';
import {
  SAVED_MESSAGE_QUERIES_STORAGE_KEY,
  persistSavedMessageQueries,
  type SavedMessageQuery,
  type SavedMessageQueryDraft,
} from '../../utils/savedMessageQueries';
import SavedMessageQueriesDrawer from '../SavedMessageQueriesDrawer';

const currentQuery: SavedMessageQueryDraft = {
  instanceId: 'instance-a',
  mode: 'key',
  topic: 'orders',
  key: 'ORDER-100',
};

const query = (overrides: Partial<SavedMessageQuery> = {}): SavedMessageQuery => ({
  ...currentQuery,
  id: 'query-1',
  name: 'Order lookup',
  createdAt: 100,
  updatedAt: 100,
  ...overrides,
});

const renderDrawer = (
  props: Partial<React.ComponentProps<typeof SavedMessageQueriesDrawer>> = {},
) =>
  render(
    <App>
      <LangProvider>
        <SavedMessageQueriesDrawer
          open
          instanceId="instance-a"
          currentQuery={currentQuery}
          onClose={vi.fn()}
          onApply={vi.fn()}
          {...props}
        />
      </LangProvider>
    </App>,
  );

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((value: string) => ({
      matches: false,
      media: value,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

describe('SavedMessageQueriesDrawer', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('saves the current criteria and renders the reusable preset', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderDrawer();

    const saveButton = screen.getByRole('button', { name: /保存当前条件/ });
    expect(saveButton).toBeDisabled();
    await user.type(screen.getByLabelText('查询预设名称'), 'Production orders');
    expect(saveButton).toBeEnabled();
    await user.click(saveButton);

    expect(await screen.findByText('Production orders')).toBeInTheDocument();
    expect(screen.getByText('orders · Key: ORDER-100')).toBeInTheDocument();
    expect(screen.getByText('查询条件已保存')).toBeInTheDocument();
    const envelope = JSON.parse(localStorage.getItem(SAVED_MESSAGE_QUERIES_STORAGE_KEY)!);
    expect(envelope.queries).toEqual([
      expect.objectContaining({
        name: 'Production orders',
        instanceId: 'instance-a',
        topic: 'orders',
        key: 'ORDER-100',
      }),
    ]);
  });

  it('applies a saved query and closes the drawer', async () => {
    persistSavedMessageQueries([query()]);
    const onApply = vi.fn();
    const onClose = vi.fn();
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderDrawer({ onApply, onClose });

    await user.click(await screen.findByRole('button', { name: /应用/ }));

    expect(onApply).toHaveBeenCalledWith(expect.objectContaining({ id: 'query-1' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('renames a query inline and persists the result', async () => {
    persistSavedMessageQueries([query()]);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderDrawer();

    await user.click(await screen.findByRole('button', { name: /编辑/ }));
    const renameInput = screen.getByLabelText('重命名查询预设');
    await user.clear(renameInput);
    await user.type(renameInput, 'Urgent orders{Enter}');

    expect(await screen.findByText('Urgent orders')).toBeInTheDocument();
    expect(screen.getByText('查询预设已重命名')).toBeInTheDocument();
    const envelope = JSON.parse(localStorage.getItem(SAVED_MESSAGE_QUERIES_STORAGE_KEY)!);
    expect(envelope.queries[0].name).toBe('Urgent orders');
  });

  it('deletes a query only after confirmation', async () => {
    persistSavedMessageQueries([query()]);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderDrawer();

    await user.click(await screen.findByRole('button', { name: '删除' }));
    const confirmation = await screen.findByText('确定删除这个查询预设吗？');
    const popup = confirmation.closest<HTMLElement>('.ant-popover')!;
    await user.click(within(popup).getByRole('button', { name: /确\s*认/ }));

    expect(await screen.findByText('查询预设已删除')).toBeInTheDocument();
    expect(screen.queryByText('Order lookup')).not.toBeInTheDocument();
  });

  it('filters by name and query identifiers', async () => {
    persistSavedMessageQueries([
      query(),
      query({ id: 'query-2', name: 'Payment lookup', topic: 'payments', key: 'PAY-7' }),
    ]);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderDrawer();

    expect(await screen.findByText('Order lookup')).toBeInTheDocument();
    await user.type(screen.getByLabelText('搜索已保存查询'), 'PAY-7');

    expect(screen.getByText('Payment lookup')).toBeInTheDocument();
    expect(screen.queryByText('Order lookup')).not.toBeInTheDocument();
  });

  it('isolates presets by instance', async () => {
    persistSavedMessageQueries([
      query(),
      query({ id: 'query-2', name: 'Other instance', instanceId: 'instance-b' }),
    ]);
    const view = renderDrawer();

    expect(await screen.findByText('Order lookup')).toBeInTheDocument();
    expect(screen.queryByText('Other instance')).not.toBeInTheDocument();

    view.rerender(
      <App>
        <LangProvider>
          <SavedMessageQueriesDrawer
            open
            instanceId="instance-b"
            currentQuery={{ ...currentQuery, instanceId: 'instance-b' }}
            onClose={vi.fn()}
            onApply={vi.fn()}
          />
        </LangProvider>
      </App>,
    );
    expect(await screen.findByText('Other instance')).toBeInTheDocument();
    expect(screen.queryByText('Order lookup')).not.toBeInTheDocument();
  });

  it('disables saving when no instance or complete current query is available', () => {
    renderDrawer({ instanceId: undefined, currentQuery: undefined });

    expect(screen.getByText('请先选择实例，再保存或复用查询条件。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /保存当前条件/ })).toBeDisabled();
  });

  it('rejects a duplicate name without changing persisted queries', async () => {
    persistSavedMessageQueries([query()]);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderDrawer();

    await user.type(screen.getByLabelText('查询预设名称'), 'order lookup');
    await user.click(screen.getByRole('button', { name: /保存当前条件/ }));

    expect(await screen.findByText('当前实例已存在同名查询预设')).toBeInTheDocument();
    const envelope = JSON.parse(localStorage.getItem(SAVED_MESSAGE_QUERIES_STORAGE_KEY)!);
    expect(envelope.queries).toHaveLength(1);
  });

  it('reports browser storage failures and keeps the visible list unchanged', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderDrawer();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });

    await user.type(screen.getByLabelText('查询预设名称'), 'Cannot persist');
    await user.click(screen.getByRole('button', { name: /保存当前条件/ }));

    expect(await screen.findAllByText('浏览器存储不可用，无法保存查询预设')).not.toHaveLength(0);
    expect(screen.queryByText('Cannot persist')).not.toBeInTheDocument();
  });

  it('reloads queries when another browser tab updates storage', async () => {
    renderDrawer();
    expect(await screen.findByText('当前实例暂无已保存查询')).toBeInTheDocument();
    persistSavedMessageQueries([query()]);

    act(() => {
      window.dispatchEvent(
        new StorageEvent('storage', {
          key: SAVED_MESSAGE_QUERIES_STORAGE_KEY,
          newValue: localStorage.getItem(SAVED_MESSAGE_QUERIES_STORAGE_KEY),
        }),
      );
    });

    expect(await screen.findByText('Order lookup')).toBeInTheDocument();
  });

  it('uses English labels when the saved language preference is English', async () => {
    localStorage.setItem('rocketmq-studio-language', 'en');
    persistSavedMessageQueries([query()]);
    renderDrawer();

    expect(await screen.findByText('Saved Message Queries')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Save Current Criteria/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Apply/ })).toBeInTheDocument();
  });

  it('accepts storage events only for the saved-query key', async () => {
    renderDrawer();
    persistSavedMessageQueries([query()]);

    fireEvent(
      window,
      new StorageEvent('storage', { key: 'unrelated-key', newValue: JSON.stringify({}) }),
    );
    expect(screen.queryByText('Order lookup')).not.toBeInTheDocument();

    fireEvent(
      window,
      new StorageEvent('storage', {
        key: SAVED_MESSAGE_QUERIES_STORAGE_KEY,
        newValue: localStorage.getItem(SAVED_MESSAGE_QUERIES_STORAGE_KEY),
      }),
    );
    await waitFor(() => expect(screen.getByText('Order lookup')).toBeInTheDocument());
  });
});
