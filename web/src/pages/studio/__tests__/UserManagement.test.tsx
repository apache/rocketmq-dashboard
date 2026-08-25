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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { listStudioUsers } from '../../../api/studioUsers';
import UserManagementPage from '../UserManagement';

vi.mock('../../../api/studioUsers', () => ({
  createStudioUser: vi.fn(),
  listStudioUsers: vi.fn(),
  resetStudioUserPassword: vi.fn(),
  setStudioUserEnabled: vi.fn(),
}));

vi.mock('../../../stores/authStore', () => ({
  default: (
    selector: (state: { admin: boolean; userId: number; logout: () => void }) => unknown,
  ) =>
    selector({ admin: true, userId: 1, logout: vi.fn() }),
}));

const page = {
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
    vi.mocked(listStudioUsers).mockResolvedValue(page);
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
  });

  it('debounces username search and sends role and status filters', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderPage();
    await screen.findByText('operator');

    await user.type(screen.getByPlaceholderText('搜索用户名'), 'ops');
    await user.click(screen.getByRole('combobox', { name: '按权限筛选' }));
    await user.click(await screen.findByText('管理员', { selector: '.ant-select-item-option-content' }));
    await user.click(screen.getByRole('combobox', { name: '按状态筛选' }));
    await user.click(await screen.findByText('已禁用', { selector: '.ant-select-item-option-content' }));

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
});
