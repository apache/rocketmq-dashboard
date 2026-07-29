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

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getAuthStatus } from './api/auth';
import { AuthGate } from './App';
import { LangProvider } from './i18n/LangContext';

vi.mock('./api/auth', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/auth')>();
  return { ...actual, getAuthStatus: vi.fn() };
});

vi.mock('./config', () => ({ API_BASE_URL: '/api', USE_MOCK: false }));

const mockedGetAuthStatus = vi.mocked(getAuthStatus);

function renderGate() {
  return render(
    <LangProvider>
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route path="/login" element={<div>login page</div>} />
          <Route element={<AuthGate />}>
            <Route path="/protected" element={<div>protected content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </LangProvider>,
  );
}

describe('AuthGate', () => {
  beforeEach(() => {
    mockedGetAuthStatus.mockReset();
    localStorage.setItem('token', 'stale-token');
    localStorage.setItem('rocketmq-studio-user', 'admin');
  });

  afterEach(() => {
    cleanup();
    localStorage.clear();
  });

  it('allows protected routes when login protection is disabled', async () => {
    mockedGetAuthStatus.mockResolvedValue({ loginRequired: false, authenticated: false });

    renderGate();

    expect(await screen.findByText('protected content')).toBeInTheDocument();
  });

  it('allows protected routes for an authenticated session', async () => {
    mockedGetAuthStatus.mockResolvedValue({ loginRequired: true, authenticated: true });

    renderGate();

    expect(await screen.findByText('protected content')).toBeInTheDocument();
  });

  it('clears an invalid session and redirects to login', async () => {
    mockedGetAuthStatus.mockResolvedValue({ loginRequired: true, authenticated: false });

    renderGate();

    expect(await screen.findByText('login page')).toBeInTheDocument();
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('rocketmq-studio-user')).toBeNull();
  });

  it('fails closed and retries the status check', async () => {
    mockedGetAuthStatus
      .mockRejectedValueOnce(new Error('network unavailable'))
      .mockResolvedValueOnce({ loginRequired: false, authenticated: false });

    renderGate();

    expect(await screen.findByText('无法验证登录状态')).toBeInTheDocument();
    expect(screen.queryByText('protected content')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));

    await waitFor(() => expect(mockedGetAuthStatus).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('protected content')).toBeInTheDocument();
  });
});
