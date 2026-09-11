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

import { App } from 'antd';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import {
  getStudioUserSessionOverview,
  listAllStudioUsers as downloadStudioUsers,
  listStudioUserSessions,
  listStudioUsers,
  revokeStudioUserSessions,
  setStudioUserEnabled,
  type StudioUser,
  type StudioUserSessionDetail,
} from '../../../api/studioUsers';
import { downloadCsv } from '../../../utils/download';
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
    },
  ],
  total: 21,
  page: 1,
  size: 20,
};

const sessionDetails: StudioUserSessionDetail[] = [
  {
    id: 19,
    userId: 7,
    lastSeenAt: '2026-08-22T09:45:00',
    expiresAt: '2026-08-22T09:50:00',
    gmtCreate: '2026-08-22T09:15:00',
    remainingSeconds: 300,
    idleSeconds: 60,
    expiringSoon: true,
    stale: false,
  },
  {
    id: 20,
    userId: 7,
    lastSeenAt: '2026-08-22T09:20:00',
    expiresAt: '2026-08-22T10:30:00',
    gmtCreate: '2026-08-22T09:00:00',
    remainingSeconds: 4200,
    idleSeconds: 1500,
    expiringSoon: false,
    stale: true,
  },
];

const renderPage = () =>
  render(
    <MemoryRouter>
      <App>
        <UserManagementPage />
      </App>
    </MemoryRouter>,
  );

const selectOption = async (user: UserEvent, comboboxName: string, optionText: string) => {
  await user.click(screen.getByRole('combobox', { name: comboboxName }));
  const option = await screen.findByText(optionText, {
    selector: '.ant-select-item-option-content',
  });
  await user.click(option);
};
const applyAdminDisabledFilter = async (user: UserEvent, keyword = 'ops') => {
  await user.type(screen.getByPlaceholderText('搜索用户名'), keyword);
  await selectOption(user, '按权限筛选', '管理员');
  await selectOption(user, '按状态筛选', '已禁用');
};
const confirmRevokePopover = async (user: UserEvent) => {
  await waitFor(() => expect(document.querySelector('.ant-popover')).toBeTruthy());
  const popover = document.querySelector('.ant-popover') as HTMLElement;
  await user.click(within(popover).getByRole('button', { name: /注\s*销/ }));
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

describe('UserManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listStudioUsers).mockResolvedValue(studioUserPage);
    vi.mocked(getStudioUserSessionOverview).mockResolvedValue({
      activeSessionCount: 5,
      activeUserCount: 3,
      expiringSoonSessionCount: 1,
      staleSessionCount: 2,
      expiringSoonWindowMinutes: 5,
      staleSessionThresholdMinutes: 15,
    });
    vi.mocked(downloadStudioUsers).mockResolvedValue(studioUserPage.items);
    vi.mocked(listStudioUserSessions).mockResolvedValue(sessionDetails);
    vi.mocked(revokeStudioUserSessions).mockResolvedValue({
      userId: 7,
      revokedSessionCount: 2,
    });
  });

  it('loads a bounded first page and renders the server total', async () => {
    renderPage();

    await screen.findByText('operator');
    expect(listStudioUsers).toHaveBeenCalledWith({
      search: undefined,
      admin: undefined,
      enabled: undefined,
      page: 1,
      pageSize: 20,
    });
    expect(screen.getByText('共 21 个用户')).toBeInTheDocument();
    expect(getStudioUserSessionOverview).toHaveBeenCalledTimes(1);
    expect(screen.getAllByText('活跃会话').length).toBeGreaterThan(0);
    expect(screen.getByText('未来 5 分钟过期')).toBeInTheDocument();
  });

  it('debounces username search and sends role and status filters', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderPage();
    await screen.findByText('operator');

    await applyAdminDisabledFilter(user);

    await waitFor(() =>
      expect(listStudioUsers).toHaveBeenLastCalledWith({
        search: 'ops',
        admin: true,
        enabled: false,
        page: 1,
        pageSize: 20,
      }),
    );
  });

  it('exports all users that match the active filters', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderPage();
    await screen.findByText('operator');
    await applyAdminDisabledFilter(user, 'ops');
    await user.click(screen.getByRole('button', { name: '导出' }));
    const expectedExportQuery = { search: 'ops', admin: true, enabled: false };
    await waitFor(() => expect(downloadStudioUsers).toHaveBeenCalledWith(expectedExportQuery));
    expect(downloadCsv).toHaveBeenCalledTimes(1);
    const [exportFilename, exportedCsv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(exportFilename).toMatch(/^rocketmq-studio-users-\d{4}-\d{2}-\d{2}\.csv$/);
    expect(exportedCsv).toContain('"operator"');
    expect(exportedCsv).toContain('"User"');
    expect(exportedCsv).toContain('"Enabled"');
    expect(exportedCsv).toContain('Active Sessions');
    expect(exportedCsv).toContain('"2"');
  });

  it('opens the active session detail drawer for a user', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderPage();

    await screen.findByText('operator');
    await user.click(screen.getByRole('button', { name: '会话' }));

    const drawer = await screen.findByRole('dialog', { name: 'operator 的会话' });
    expect(listStudioUserSessions).toHaveBeenCalledWith(7);
    expect(within(drawer).getAllByText('会话 ID').length).toBeGreaterThan(0);
    expect(within(drawer).getByText('19')).toBeInTheDocument();
    expect(within(drawer).getByText('20')).toBeInTheDocument();
    expect(within(drawer).getByText('即将过期')).toBeInTheDocument();
    expect(within(drawer).getByText('长时间未活跃')).toBeInTheDocument();
    expect(within(drawer).getByText('5分钟')).toBeInTheDocument();
    expect(within(drawer).getByText('1分钟')).toBeInTheDocument();
    expect(within(drawer).queryByText(/token/i)).not.toBeInTheDocument();
  });

  it('revokes sessions after row confirmation', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderPage();

    await screen.findByText('operator');
    expect(screen.getAllByText('2').length).toBeGreaterThan(0);
    expect(screen.getByText(new Date('2026-08-22T09:30:00').toLocaleString())).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '注销' }));
    await screen.findByText('注销 operator 的活跃会话？');
    await confirmRevokePopover(user);

    await waitFor(() => expect(revokeStudioUserSessions).toHaveBeenCalledWith(7));
    expect(listStudioUsers).toHaveBeenCalledTimes(2);
  });

  it('revokes sessions from the detail drawer and refreshes the detail list', async () => {
    vi.mocked(listStudioUserSessions)
      .mockResolvedValueOnce(sessionDetails)
      .mockResolvedValueOnce([]);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderPage();

    await screen.findByText('operator');
    await user.click(screen.getByRole('button', { name: '会话' }));
    const drawer = await screen.findByRole('dialog', { name: 'operator 的会话' });
    await user.click(within(drawer).getByRole('button', { name: '注销全部' }));
    await screen.findByText('注销 operator 的活跃会话？');
    await confirmRevokePopover(user);

    await waitFor(() => expect(revokeStudioUserSessions).toHaveBeenCalledWith(7));
    await waitFor(() => expect(listStudioUserSessions).toHaveBeenCalledTimes(2));
    expect(within(drawer).getByText('暂无活跃会话')).toBeInTheDocument();
  });

  it('refreshes the open session detail drawer', async () => {
    vi.mocked(listStudioUserSessions)
      .mockResolvedValueOnce(sessionDetails)
      .mockResolvedValueOnce([sessionDetails[0]]);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderPage();

    await screen.findByText('operator');
    await user.click(screen.getByRole('button', { name: '会话' }));
    const drawer = await screen.findByRole('dialog', { name: 'operator 的会话' });
    expect(within(drawer).getByText('20')).toBeInTheDocument();

    await user.click(within(drawer).getByRole('button', { name: '刷新' }));

    await waitFor(() => expect(listStudioUserSessions).toHaveBeenCalledTimes(2));
    expect(within(drawer).getByText('19')).toBeInTheDocument();
    expect(within(drawer).queryByText('20')).not.toBeInTheDocument();
  });

  it('blocks the row status switch while the same user revocation is in flight', async () => {
    let resolveRevoke!: () => void;
    vi.mocked(revokeStudioUserSessions).mockImplementationOnce(
      () =>
        new Promise<{ userId: number; revokedSessionCount: number }>((resolve) => {
          resolveRevoke = () => resolve({ userId: 7, revokedSessionCount: 2 });
        }),
    );
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderPage();
    await screen.findByText('operator');

    await user.click(screen.getByRole('button', { name: '注销' }));
    await screen.findByText('注销 operator 的活跃会话？');
    await confirmRevokePopover(user);
    await waitFor(() => expect(revokeStudioUserSessions).toHaveBeenCalledWith(7));

    // Revocation and status updates share one in-flight guard, so the row is blocked meanwhile.
    const toggle = screen.getByRole('switch');
    expect(toggle).toBeDisabled();
    fireEvent.click(toggle);
    expect(setStudioUserEnabled).not.toHaveBeenCalled();

    await act(async () => resolveRevoke());
    await waitFor(() => expect(screen.getByRole('switch')).not.toBeDisabled());
    expect(setStudioUserEnabled).not.toHaveBeenCalled();
  });

  it('does not overlap status updates for the same user', async () => {
    let resolveUpdate!: () => void;
    vi.mocked(setStudioUserEnabled).mockImplementationOnce(
      () =>
        new Promise<StudioUser>((resolve) => {
          resolveUpdate = () => resolve({ ...studioUserPage.items[0], enabled: false });
        }),
    );
    renderPage();

    const toggle = await screen.findByRole('switch');
    fireEvent.click(toggle);
    fireEvent.click(toggle);

    expect(setStudioUserEnabled).toHaveBeenCalledTimes(1);
    expect(setStudioUserEnabled).toHaveBeenCalledWith(7, false);
    await act(async () => resolveUpdate());
  });
});
