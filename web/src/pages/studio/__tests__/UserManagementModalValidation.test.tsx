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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import {
  createStudioUser,
  getStudioUserSessionOverview,
  listStudioUsers,
  resetStudioUserPassword,
  type StudioUser,
} from '../../../api/studioUsers';
import { LangProvider } from '../../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../../i18n/languagePreference';
import UserManagementPage from '../UserManagement';

type MockAuthState = { admin: boolean; userId: number; logout: () => void };
vi.mock('../../../api/studioUsers', () => ({
  createStudioUser: vi.fn(),
  getStudioUserSessionOverview: vi.fn(),
  listAllStudioUsers: vi.fn(),
  listStudioUserSessions: vi.fn(),
  listStudioUsers: vi.fn(),
  resetStudioUserPassword: vi.fn(),
  revokeStudioUserSessions: vi.fn(),
  setStudioUserEnabled: vi.fn(),
}));

vi.mock('../../../stores/authStore', () => ({
  default: (selector: (state: MockAuthState) => unknown) =>
    selector({ admin: true, userId: 1, logout: vi.fn() }),
}));

vi.mock('../../../utils/download', async () => {
  const downloadModule =
    await vi.importActual<typeof import('../../../utils/download')>('../../../utils/download');
  return {
    ...downloadModule,
    downloadCsv: vi.fn(),
  };
});

const studioUserPage = {
  items: [
    {
      id: 7,
      username: 'operator',
      admin: false,
      enabled: true,
      activeSessionCount: 2,
      lastSessionSeenAt: '2026-08-22T09:30:00',
      nearestSessionExpiresAt: '2026-08-22T10:00:00',
      passwordChangedAt: '2026-08-22T08:00:00',
      gmtCreate: '2026-08-22T08:00:00',
      gmtModified: '2026-08-22T08:00:00',
    } satisfies StudioUser,
  ],
  total: 1,
  page: 1,
  size: 20,
};

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

const renderPage = () =>
  render(
    <MemoryRouter>
      <App>
        <LangProvider>
          <UserManagementPage />
        </LangProvider>
      </App>
    </MemoryRouter>,
  );

const collectUnhandledRejections = () => {
  const reasons: unknown[] = [];
  const handler = (reason: unknown) => {
    reasons.push(reason);
  };
  process.on('unhandledRejection', handler);
  return {
    reasons,
    restore: () => {
      process.off('unhandledRejection', handler);
    },
  };
};

const flushMicrotasks = async () => {
  await new Promise((resolve) => {
    setTimeout(resolve, 50);
  });
};

describe('UserManagement modal validation failures', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    vi.mocked(listStudioUsers).mockResolvedValue(studioUserPage);
    vi.mocked(getStudioUserSessionOverview).mockResolvedValue({
      activeSessionCount: 2,
      activeUserCount: 1,
      expiringSoonSessionCount: 0,
      expiringSoonWindowMinutes: 30,
      staleSessionCount: 0,
      staleSessionThresholdMinutes: 120,
    });
  });

  it('does not leak an unhandled rejection when user creation validation fails', async () => {
    const unhandled = collectUnhandledRejections();
    try {
      const user = userEvent.setup();
      renderPage();
      await user.click(await screen.findByRole('button', { name: 'Create User' }));
      await user.click(await screen.findByRole('button', { name: 'OK' }));

      await waitFor(() => {
        expect(document.querySelectorAll('.ant-form-item-explain-error').length).toBeGreaterThan(0);
      });
      expect(createStudioUser).not.toHaveBeenCalled();

      await flushMicrotasks();
      expect(unhandled.reasons).toEqual([]);
    } finally {
      unhandled.restore();
      localStorage.removeItem(LANGUAGE_STORAGE_KEY);
    }
  });

  it('does not leak an unhandled rejection when password reset validation fails', async () => {
    const unhandled = collectUnhandledRejections();
    try {
      const user = userEvent.setup();
      renderPage();
      await user.click(await screen.findByRole('button', { name: 'Password' }));
      await user.click(await screen.findByRole('button', { name: 'OK' }));

      await waitFor(() => {
        expect(document.querySelectorAll('.ant-form-item-explain-error').length).toBeGreaterThan(0);
      });
      expect(resetStudioUserPassword).not.toHaveBeenCalled();

      await flushMicrotasks();
      expect(unhandled.reasons).toEqual([]);
    } finally {
      unhandled.restore();
      localStorage.removeItem(LANGUAGE_STORAGE_KEY);
    }
  });
});
