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

import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../i18n/LangContext';
import useAuthStore from '../stores/authStore';
import { ThemeProvider } from '../theme/ThemeProvider';
import MainLayout from './MainLayout';

const instanceServiceMocks = vi.hoisted(() => ({
  getInstanceCapabilities: vi.fn(),
}));

vi.mock('../api/auth', () => ({ logout: vi.fn() }));
vi.mock('../services/instanceService', () => instanceServiceMocks);

/**
 * antd's Sider reaches its breakpoint through `window.matchMedia`, so this harness models the
 * viewport width as a media query answer instead of a layout engine jsdom does not have.
 */
const useViewport = (narrow: boolean) => {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: narrow && query.includes('max-width: 991.98px'),
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
};

const renderShell = () =>
  render(
    <LangProvider>
      <ThemeProvider>
        <MemoryRouter initialEntries={['/instance/topic']}>
          <Routes>
            <Route path="*" element={<MainLayout />} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </LangProvider>,
  );

const sider = () => document.querySelector<HTMLElement>('.ant-layout-sider');
const searchControl = () => screen.getByRole('button', { name: '打开导航搜索' });

describe('MainLayout viewport shell', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.getState().login('admin', 7, true);
    instanceServiceMocks.getInstanceCapabilities.mockReset();
  });

  it('gives a narrow viewport the whole width and keeps navigation behind a control', () => {
    useViewport(true);
    renderShell();

    // A 375px viewport used to keep the 220px desktop sidebar and squeeze the content into the
    // rest. Narrow viewports must not reserve any of it.
    expect(sider()).not.toBeNull();
    expect(sider()).toHaveClass('ant-layout-sider-zero-width');
    expect(sider()!.style.width).toBe('0px');

    // The navigation stays reachable through the explicit zero-width control.
    const trigger = document.querySelector<HTMLElement>('.ant-layout-sider-zero-width-trigger');
    expect(trigger).not.toBeNull();
    fireEvent.click(trigger!);

    expect(sider()).not.toHaveClass('ant-layout-sider-zero-width');
    expect(sider()!.style.width).toBe('220px');
  });

  it('keeps the 220px sidebar for a desktop viewport', () => {
    useViewport(false);
    renderShell();

    expect(sider()).not.toHaveClass('ant-layout-sider-zero-width');
    expect(sider()!.style.width).toBe('220px');
    const trigger = document.querySelector<HTMLElement>('.ant-layout-sider-trigger');
    expect(trigger).not.toBeNull();
    expect(document.querySelector('.ant-layout-sider-zero-width-trigger')).toBeNull();
    fireEvent.click(trigger!);
    expect(sider()!.style.width).toBe('64px');
    expect(document.querySelector('.ant-layout-sider-zero-width-trigger')).toBeNull();
  });

  it('declares the header search control as shrinkable instead of fixed at 280px', () => {
    useViewport(true);
    renderShell();

    // jsdom has no layout; pin the controls' shrink behavior and verify actual widths in a browser.
    expect(searchControl().style.width).toBe('280px');
    expect(searchControl().style.flex).toBe('1 1 280px');
    expect(searchControl().style.minWidth).toBe('0px');
    const rightGroup = searchControl().parentElement;
    expect(rightGroup?.style.minWidth).toBe('0px');
    expect(rightGroup?.style.flex).toBe('1 1 auto');
    expect(document.querySelector<HTMLElement>('.ant-breadcrumb')?.style.minWidth).toBe('0px');
  });
});
