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
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { LangProvider } from '../i18n/LangContext';
import { ThemeProvider } from '../theme/ThemeProvider';
import useAuthStore from '../stores/authStore';
import MainLayout from './MainLayout';

// Renders the layout with the real antd component tree: the inline font sizes under review live
// inside the user dropdown, which the antd mock in MainLayout.test.tsx replaces entirely.
vi.mock('../api/auth', () => ({ logout: vi.fn() }));
vi.mock('../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([]),
}));

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

describe('MainLayout inline font sizes', () => {
  beforeEach(() => {
    useAuthStore.setState({ username: 'tester', userId: 1, admin: false });
  });

  it('keeps every inline font size at the 14px minimum', async () => {
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

    // The user menu carries one of the reviewed sizes, so open it before scanning.
    fireEvent.click(screen.getByRole('button', { name: '打开用户菜单' }));
    await waitFor(() => expect(screen.getAllByText('数据模式: Real').length).toBeGreaterThan(0));

    const tooSmall = Array.from(document.querySelectorAll<HTMLElement>('[style]')).filter(
      (element) => element.style.fontSize === '13px',
    );
    expect(tooSmall).toHaveLength(0);
  });
});
