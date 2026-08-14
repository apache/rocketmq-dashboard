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

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { logout } from '../api/auth';
import { LangProvider } from '../i18n/LangContext';
import useAuthStore from '../stores/authStore';
import { ThemeProvider } from '../theme/ThemeProvider';
import MainLayout from './MainLayout';

vi.mock('../api/auth', () => ({ logout: vi.fn() }));

vi.mock('antd', async () => {
  const React = await import('react');
  const Container = ({ children }: { children?: React.ReactNode }) =>
    React.createElement(React.Fragment, null, children);
  const Layout = Object.assign(Container, { Sider: Container, Content: Container });
  type DropdownMenu = {
    items: Array<{ key?: string; label?: React.ReactNode }>;
    onClick: (info: { key: string }) => void;
  };

  return {
    Layout,
    Menu: () => null,
    Breadcrumb: ({ items }: { items: Array<{ key: string; title: React.ReactNode }> }) =>
      React.createElement(
        'nav',
        null,
        items.map((item) => React.createElement(React.Fragment, { key: item.key }, item.title)),
      ),
    Avatar: () => React.createElement('span', null, 'avatar'),
    Dropdown: ({ children, menu }: { children?: React.ReactNode; menu: DropdownMenu }) =>
      React.createElement(
        'div',
        null,
        children,
        menu.items.map((item) =>
          item.key
            ? React.createElement(
                'button',
                { key: item.key, onClick: () => menu.onClick({ key: item.key! }) },
                item.label,
              )
            : null,
        ),
      ),
    Empty: () => null,
    Input: () => null,
    Modal: ({ open, children }: { open?: boolean; children?: React.ReactNode }) =>
      open ? React.createElement('div', null, children) : null,
    message: { warning: vi.fn() },
  };
});

describe('MainLayout authentication navigation', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(logout).mockReset().mockResolvedValue(undefined);
    useAuthStore.getState().login('admin', 'user-1', true);
  });

  it('replaces a protected route with the login page after logout', async () => {
    render(
      <LangProvider>
        <ThemeProvider>
          <MemoryRouter initialEntries={['/instance/topic']}>
            <Routes>
              <Route path="/login" element={<div>login page</div>} />
              <Route path="/" element={<MainLayout />}>
                <Route index element={<div>protected home</div>} />
                <Route path="instance/topic" element={<div>protected topic</div>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </ThemeProvider>
      </LangProvider>,
    );

    expect(screen.getByText('protected topic')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '退出登录' }));

    await waitFor(() => expect(screen.getByText('login page')).toBeInTheDocument());
    expect(screen.queryByText('protected home')).not.toBeInTheDocument();
    expect(logout).toHaveBeenCalledOnce();
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('exposes global layout commands as localized semantic buttons', () => {
    render(
      <LangProvider>
        <ThemeProvider>
          <MemoryRouter initialEntries={['/']}>
            <Routes>
              <Route path="/" element={<MainLayout />}>
                <Route index element={<div>protected home</div>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </ThemeProvider>
      </LangProvider>,
    );

    expect(screen.getByRole('button', { name: '返回首页' })).toBeInTheDocument();
    const searchButton = screen.getByRole('button', { name: '打开导航搜索' });
    expect(searchButton).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '切换到模拟数据' })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
    expect(screen.getByRole('button', { name: '切换到英语' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '切换到深色主题' })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
    expect(screen.getByRole('button', { name: '打开用户菜单' })).toBeInTheDocument();

    fireEvent.click(searchButton);
    expect(screen.getByRole('button', { name: '首页' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '切换到英语' }));
    expect(screen.getByRole('button', { name: 'Switch to Chinese' })).toBeInTheDocument();
  });
});
