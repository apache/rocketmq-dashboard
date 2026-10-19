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

import { act, fireEvent, render, screen } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../i18n/LangContext';
import translations from '../i18n/translations';
import useAuthStore from '../stores/authStore';
import { ThemeProvider } from '../theme/ThemeProvider';
import MainLayout from './MainLayout';

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

async function renderRoute(pathname: string) {
  await act(async () => {
    render(
      <ConfigProvider theme={{ token: { motion: false } }}>
        <LangProvider>
          <ThemeProvider>
            <MemoryRouter initialEntries={[pathname]}>
              <Routes>
                <Route path="/" element={<MainLayout />}>
                  <Route
                    path="*"
                    element={
                      <>
                        <Link to="/ai/c/7">Open conversation</Link>
                        <Link to="/settings">Open settings</Link>
                      </>
                    }
                  />
                </Route>
              </Routes>
            </MemoryRouter>
          </ThemeProvider>
        </LangProvider>
      </ConfigProvider>,
    );
  });
}

const aiMenuItem = () => screen.getByRole('menuitem', { name: translations['nav.ai'].en });

describe('MainLayout AI route selection', () => {
  beforeEach(() => {
    localStorage.setItem('rocketmq-studio-language', 'en');
    useAuthStore.getState().login('admin', 7, true);
  });

  it.each(['/ai', '/ai/', '/ai/c/7', '/ai/c/7/'])(
    'selectsAiNavigationForConversationRouteTest: %s',
    async (pathname) => {
      await renderRoute(pathname);
      expect(aiMenuItem()).toHaveClass('ant-menu-item-selected');
      expect(document.getElementById('main-content')).toHaveStyle({ overflow: 'hidden' });
    },
  );

  it.each(['/aix', '/ai-tools', '/ai/c', '/ai/settings', '/ai/c/7/extra'])(
    'doesNotSelectAiForOtherRoutesTest: %s',
    async (pathname) => {
      await renderRoute(pathname);
      expect(aiMenuItem()).not.toHaveClass('ant-menu-item-selected');
      expect(document.getElementById('main-content')).toHaveStyle({ overflow: 'auto' });
    },
  );

  it('updatesAiNavigationWhenConversationRouteChangesTest', async () => {
    await renderRoute('/ai');
    expect(aiMenuItem()).toHaveClass('ant-menu-item-selected');

    await act(async () => {
      fireEvent.click(screen.getByRole('link', { name: 'Open conversation' }));
    });
    expect(aiMenuItem()).toHaveClass('ant-menu-item-selected');

    await act(async () => {
      fireEvent.click(screen.getByRole('link', { name: 'Open settings' }));
    });
    expect(aiMenuItem()).not.toHaveClass('ant-menu-item-selected');
    expect(screen.getByRole('menuitem', { name: translations['nav.settings'].en })).toHaveClass(
      'ant-menu-item-selected',
    );
  });
});
