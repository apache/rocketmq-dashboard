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
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import {
  createStudioUser,
  listAllStudioUsers as downloadStudioUsers,
  listStudioUsers,
  setStudioUserEnabled,
  type StudioUser,
} from '../../../api/studioUsers';
import { downloadCsv } from '../../../utils/download';
import UserManagementPage from '../UserManagement';

type MockAuthState = { admin: boolean; user: string; userId: number; logout: () => void };
vi.mock('../../../api/studioUsers', () => ({
  createStudioUser: vi.fn(),
  listAllStudioUsers: vi.fn(),
  listStudioUsers: vi.fn(),
  resetStudioUserPassword: vi.fn(),
  setStudioUserEnabled: vi.fn(),
}));

vi.mock('../../../stores/authStore', () => ({
  default: (selector: (state: MockAuthState) => unknown) =>
    selector({ admin: true, user: 'admin', userId: 1, logout: vi.fn() }),
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
      passwordChangedAt: '2026-08-22T08:00:00',
      gmtCreate: '2026-08-22T08:00:00',
      gmtModified: '2026-08-22T08:00:00',
    },
  ],
  total: 21,
  page: 1,
  size: 20,
};

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
    vi.mocked(downloadStudioUsers).mockResolvedValue(studioUserPage.items);
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
    expect(screen.getByText('当前页账号安全摘要')).toBeInTheDocument();
    expect(screen.getAllByText('密码状态').length).toBeGreaterThan(0);
    expect(screen.getByText('正常')).toBeInTheDocument();
  });

  it('renders account security risks from the visible user page', async () => {
    vi.mocked(listStudioUsers).mockResolvedValue({
      items: [
        {
          id: 1,
          username: 'root-admin',
          admin: true,
          enabled: true,
          passwordChangedAt: '2025-01-01T00:00:00Z',
          gmtCreate: '2025-01-01T00:00:00Z',
          gmtModified: '2025-01-01T00:00:00Z',
        },
        {
          id: 2,
          username: 'disabled-admin',
          admin: true,
          enabled: false,
          passwordChangedAt: '',
          gmtCreate: '2026-01-01T00:00:00Z',
          gmtModified: '2026-01-01T00:00:00Z',
        },
      ],
      total: 2,
      page: 1,
      size: 20,
    });

    renderPage();

    expect(await screen.findByText('root-admin')).toBeInTheDocument();
    expect(screen.getByText('账号安全存在高风险')).toBeInTheDocument();
    expect(screen.getByText(/管理员密码长期未轮换/)).toBeInTheDocument();
    expect(screen.getByText('需轮换')).toBeInTheDocument();
    expect(screen.getByText('未知')).toBeInTheDocument();
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
  });

  it('shows password strength guidance while creating a user', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    vi.mocked(createStudioUser).mockResolvedValue({
      id: 10,
      username: 'operator',
      admin: false,
      enabled: true,
      passwordChangedAt: '2026-09-01T00:00:00',
      gmtCreate: '2026-09-01T00:00:00',
      gmtModified: '2026-09-01T00:00:00',
    });
    renderPage();
    await screen.findByText('operator');

    await user.click(screen.getByRole('button', { name: '新建用户' }));
    expect(screen.getByTestId('studio-password-strength')).toHaveTextContent('待输入');
    await user.type(screen.getByLabelText('用户名'), 'operator');
    await user.type(screen.getByLabelText('初始密码'), 'operator123');

    expect(screen.getByTestId('studio-password-strength')).toHaveTextContent('弱');
    expect(screen.getByText('不包含用户名')).toBeInTheDocument();
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
