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
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { LangProvider } from '../i18n/LangContext';
import { ThemeProvider } from '../theme/ThemeProvider';
import useAuthStore from '../stores/authStore';
import MainLayout from './MainLayout';

// Renders the layout with the real antd component tree: the inline font sizes under review live
// inside the user menu and the navigation search panel, both of which the antd mock in
// MainLayout.test.tsx replaces entirely (its Avatar drops the style prop and its Input is null).
const instanceServiceMocks = vi.hoisted(() => ({
  getInstanceCapabilities: vi.fn(),
}));

vi.mock('../api/auth', () => ({ logout: vi.fn() }));
vi.mock('../services/instanceService', () => instanceServiceMocks);

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
    // `login`, not `setState`: AuthState keeps `user`, so the raw setState with `username`
    // was a TS2353 that failed `tsc -b` in the build step.
    useAuthStore.getState().login('tester', 1, false);
    instanceServiceMocks.getInstanceCapabilities.mockReset().mockResolvedValue({
      instanceId: 'apache-1',
      vendor: 'APACHE',
      accessType: 'DIRECT',
      capabilities: [
        'TOPIC_MANAGEMENT',
        'CONSUMER_GROUP_MANAGEMENT',
        'MESSAGE_QUERY',
        'MESSAGE_TRACE',
        'ACL_MANAGEMENT',
        'DLQ_MANAGEMENT',
      ],
    });
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

    // Both surfaces carry reviewed sizes and must be on screen for the scan to see them: the user
    // menu (avatar fallback letter) and the navigation search (kbd caps, section titles, result
    // rows, footer hints).
    fireEvent.click(screen.getByRole('button', { name: '打开用户菜单' }));
    await waitFor(() => expect(screen.getAllByText('数据模式: Real').length).toBeGreaterThan(0));
    fireEvent.click(screen.getByRole('button', { name: '打开导航搜索' }));
    await waitFor(() => expect(screen.getAllByText('常规').length).toBeGreaterThan(0));

    // A lower bound, not equality with 13px: a regression to 12px (or any 13-and-under size the
    // convention forbids) has to fail here too. Non-px values are left to the CSS review.
    const tooSmall = Array.from(document.querySelectorAll<HTMLElement>('[style]')).filter(
      (element) => element.style.fontSize.endsWith('px') && parseFloat(element.style.fontSize) < 14,
    );
    expect(
      tooSmall.map(
        (element) => `${element.tagName} ${element.style.fontSize}: ${element.textContent}`,
      ),
    ).toEqual([]);
  });
});
