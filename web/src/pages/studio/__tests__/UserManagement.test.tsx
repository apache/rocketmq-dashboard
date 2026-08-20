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
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import {
  createStudioUser,
  listStudioUsers,
  setStudioUserEnabled,
  type StudioUser,
} from '../../../api/studioUsers';
import useAuthStore from '../../../stores/authStore';
import UserManagementPage from '../UserManagement';

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }));
vi.mock('../../../api/studioUsers', () => ({
  createStudioUser: vi.fn(),
  listStudioUsers: vi.fn(),
  resetStudioUserPassword: vi.fn(),
  setStudioUserEnabled: vi.fn(),
}));
vi.mock('../../../api/auth', () => ({ changePassword: vi.fn() }));

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

const studioUser: StudioUser = {
  id: 2,
  username: 'operator',
  admin: false,
  enabled: true,
  passwordChangedAt: '',
  gmtCreate: '',
  gmtModified: '',
};

const renderPage = () =>
  render(
    <App>
      <UserManagementPage />
    </App>,
  );

describe('UserManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ admin: true, userId: 1, user: 'admin' });
    vi.mocked(listStudioUsers).mockResolvedValue([studioUser]);
  });

  it('deduplicates create submissions before loading state is rendered', async () => {
    vi.mocked(createStudioUser).mockImplementation(() => new Promise(() => {}));
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('operator');
    await user.click(screen.getByRole('button', { name: '新建用户' }));
    await user.type(screen.getByLabelText('用户名'), 'new-user');
    await user.type(screen.getByLabelText('初始密码'), 'password1');
    const confirm = screen.getByRole('button', { name: /OK|确\s*定/ });

    fireEvent.click(confirm);
    fireEvent.click(confirm);

    await waitFor(() => expect(createStudioUser).toHaveBeenCalledTimes(1));
  });

  it('deduplicates repeated status changes for the same user', async () => {
    vi.mocked(setStudioUserEnabled).mockImplementation(() => new Promise(() => {}));
    renderPage();

    await screen.findByText('operator');
    const enabledSwitch = screen.getByRole('switch');
    fireEvent.click(enabledSwitch);
    fireEvent.click(enabledSwitch);

    await waitFor(() => expect(setStudioUserEnabled).toHaveBeenCalledTimes(1));
    expect(setStudioUserEnabled).toHaveBeenCalledWith(2, false);
  });
});
