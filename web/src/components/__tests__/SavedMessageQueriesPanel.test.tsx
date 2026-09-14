/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { App, message } from 'antd';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../i18n/LangContext';
import SavedMessageQueriesPanel from '../SavedMessageQueriesPanel';

const api = vi.hoisted(() => ({
  loadSavedMessageQueries: vi.fn(),
  addSavedMessageQuery: vi.fn(),
  renameSavedMessageQuery: vi.fn(),
  removeSavedMessageQuery: vi.fn(),
}));
vi.mock('../../utils/savedMessageQueries', async (original) => ({
  ...(await original<typeof import('../../utils/savedMessageQueries')>()),
  ...api,
}));
const draft = { instanceId: 'alpha', mode: 'key' as const, topic: 'orders', key: 'key-1' };
const query = { ...draft, id: 'one', name: 'Orders', createdAt: 1, updatedAt: 1 };
const renderPanel = (props: Partial<React.ComponentProps<typeof SavedMessageQueriesPanel>> = {}) =>
  render(
    <App>
      <LangProvider>
        <SavedMessageQueriesPanel
          open
          instanceId="alpha"
          currentQuery={draft}
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

describe('服务端共享命名查询面板', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.values(api).forEach((mock) => mock.mockReset());
    localStorage.clear();
    api.loadSavedMessageQueries.mockResolvedValue([]);
  });
  it('保存成功后重新读取服务端列表', async () => {
    api.loadSavedMessageQueries.mockResolvedValueOnce([]).mockResolvedValue([query]);
    api.addSavedMessageQuery.mockResolvedValue(undefined);
    renderPanel();
    await waitFor(() => expect(api.loadSavedMessageQueries).toHaveBeenCalledWith('alpha'));
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('查询预设名称'), 'Orders');
    await user.click(screen.getByRole('button', { name: /保存当前条件/ }));
    expect(await screen.findByText('Orders')).toBeInTheDocument();
    expect(api.addSavedMessageQuery).toHaveBeenCalledWith('Orders', draft);
    expect(localStorage.getItem('rocketmq-studio-saved-message-queries')).toBeNull();
  });
  it('应用只调用表单回填回调', async () => {
    api.loadSavedMessageQueries.mockResolvedValue([query]);
    const onApply = vi.fn();
    const onClose = vi.fn();
    renderPanel({ onApply, onClose });
    fireEvent.click(await screen.findByRole('button', { name: /应用/ }));
    expect(onApply).toHaveBeenCalledWith(query);
    expect(onClose).toHaveBeenCalledOnce();
    expect(api.addSavedMessageQuery).not.toHaveBeenCalled();
  });
  it('另一个浏览器保存的查询可以刷新读取', async () => {
    renderPanel();
    await screen.findByText('当前实例暂无已保存查询');
    api.loadSavedMessageQueries.mockResolvedValue([query]);
    fireEvent.click(screen.getByRole('button', { name: /刷.*新/ }));
    expect(await screen.findByText('Orders')).toBeInTheDocument();
  });
  it('失败不会显示虚假保存成功', async () => {
    const success = vi.spyOn(message, 'success');
    api.addSavedMessageQuery.mockRejectedValue(new Error('offline'));
    renderPanel();
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('查询预设名称'), 'Orders');
    await user.click(screen.getByRole('button', { name: /保存当前条件/ }));
    expect(await screen.findByText(/保存失败，请检查名称/)).toBeInTheDocument();
    expect(success).not.toHaveBeenCalled();
  });
  it('卸载后丢弃旧实例的迟到响应', async () => {
    let resolve!: (value: (typeof query)[]) => void;
    api.loadSavedMessageQueries.mockImplementationOnce(
      () =>
        new Promise((r) => {
          resolve = r;
        }),
    );
    const view = renderPanel();
    view.unmount();
    api.loadSavedMessageQueries.mockResolvedValue([{ ...query, instanceId: 'beta', name: 'Beta' }]);
    renderPanel({ instanceId: 'beta' });
    await screen.findByText('Beta');
    await act(async () => resolve([query]));
    expect(screen.queryByText('Orders')).not.toBeInTheDocument();
  });
  it('读取失败提供明确提示', async () => {
    api.loadSavedMessageQueries.mockRejectedValue(new Error('offline'));
    renderPanel();
    expect(await screen.findByText('命名查询加载失败，请刷新重试')).toBeInTheDocument();
  });
});
