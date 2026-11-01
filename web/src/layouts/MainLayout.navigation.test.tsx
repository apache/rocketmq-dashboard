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

import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App, ConfigProvider } from 'antd';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../i18n/LangContext';
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
  Element.prototype.scrollIntoView = vi.fn();
});

async function openSearch() {
  const user = userEvent.setup();
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <App>
        <LangProvider>
          <ThemeProvider>
            <MemoryRouter initialEntries={['/']}>
              <Routes>
                <Route path="/" element={<MainLayout />}>
                  <Route index element={<div>Home route content</div>} />
                  <Route path="settings" element={<div>Settings route content</div>} />
                  <Route path="instance" element={<div>Instance list route content</div>} />
                  <Route path="instance/topic" element={<div>Topic route content</div>} />
                </Route>
              </Routes>
            </MemoryRouter>
          </ThemeProvider>
        </LangProvider>
      </App>
    </ConfigProvider>,
  );
  await user.click(screen.getByRole('button', { name: '打开导航搜索' }));
  const dialog = await screen.findByRole('dialog');
  const input = within(dialog).getByPlaceholderText('搜索页面、功能...');
  await waitFor(() => expect(input).toBeVisible());
  return { input, dialog };
}

describe('MainLayout navigation search keyboard input', () => {
  beforeEach(() => {
    useAuthStore.setState({ user: null, userId: null, admin: null });
  });

  it.each(['composing', 'process-key'])(
    'keepsNavigationSearchOpenWhileConfirmingCompositionTest: %s',
    async (sequence) => {
      const { input, dialog } = await openSearch();
      fireEvent.compositionStart(input);
      fireEvent.compositionUpdate(input, { data: '设置' });
      fireEvent.change(input, { target: { value: '设置' } });
      expect(within(dialog).getByRole('button', { name: '设置' })).toBeVisible();
      if (sequence === 'process-key') fireEvent.compositionEnd(input, { data: '设置' });

      // Exercise the production DOM handler; this does not emulate an OS input method.
      const confirmingKey = new KeyboardEvent('keydown', {
        key: 'Enter',
        keyCode: sequence === 'process-key' ? 229 : 13,
        isComposing: sequence === 'composing',
        bubbles: true,
        cancelable: true,
      });
      fireEvent(input, confirmingKey);

      expect(screen.queryByText('Settings route content')).not.toBeInTheDocument();
      expect(screen.getByText('Home route content')).toBeInTheDocument();
      expect(dialog).toBeVisible();
      expect(input).toHaveValue('设置');
      expect(confirmingKey.defaultPrevented).toBe(false);
      if (sequence === 'composing') fireEvent.compositionEnd(input, { data: '设置' });
      fireEvent.keyDown(input, { key: 'Enter', keyCode: 13, isComposing: false });
      expect(await screen.findByText('Settings route content')).toBeInTheDocument();
    },
  );

  it('leavesComposingArrowKeysToTheInputMethodTest', async () => {
    const { input } = await openSearch();
    fireEvent.change(input, { target: { value: 'instance' } });
    fireEvent.compositionStart(input);
    const down = new KeyboardEvent('keydown', {
      key: 'ArrowDown',
      isComposing: true,
      bubbles: true,
      cancelable: true,
    });
    fireEvent(input, down);
    fireEvent.compositionEnd(input);
    fireEvent.keyDown(input, { key: 'Enter', keyCode: 13, isComposing: false });
    expect(screen.queryByText('Topic route content')).not.toBeInTheDocument();
    expect(await screen.findByText('Instance list route content')).toBeInTheDocument();
    expect(down.defaultPrevented).toBe(false);
  });

  it('keepsOrdinaryArrowSelectionAndEnterNavigationTest', async () => {
    const { input } = await openSearch();
    fireEvent.change(input, { target: { value: 'instance' } });
    fireEvent.keyDown(input, { key: 'ArrowDown', isComposing: false });
    fireEvent.keyDown(input, { key: 'Enter', keyCode: 13, isComposing: false });
    expect(await screen.findByText('Topic route content')).toBeInTheDocument();
  });
});
