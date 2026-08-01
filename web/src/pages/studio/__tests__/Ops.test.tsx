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

import type { ReactElement } from 'react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import OpsPage from '../Ops';
import { deleteNameSvrAddr, queryOpsHomePage } from '../../../api/ops';
import useAuthStore from '../../../stores/authStore';

vi.mock('../../../api/ops', () => ({
  addNameSvrAddr: vi.fn(),
  deleteNameSvrAddr: vi.fn(),
  queryOpsHomePage: vi.fn(),
  updateIsVIPChannel: vi.fn(),
  updateNameSvrAddr: vi.fn(),
  updateUseTLS: vi.fn(),
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

const renderWithProviders = (ui: ReactElement) => {
  return render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );
};

describe('OpsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    useAuthStore.setState({ token: null, user: null, admin: null });
    vi.mocked(queryOpsHomePage).mockResolvedValue({
      namesvrAddrList: ['127.0.0.1:9876', '127.0.0.2:9876'],
      useVIPChannel: true,
      useTLS: false,
      currentNamesrv: '127.0.0.1:9876',
    });
  });

  it('loads NameServer and channel settings after mount', async () => {
    renderWithProviders(<OpsPage />);

    await waitFor(() => {
      expect(queryOpsHomePage).toHaveBeenCalledTimes(1);
    });

    expect(await screen.findByText('127.0.0.1:9876')).toBeInTheDocument();
    expect(screen.getAllByRole('switch')[0]).toBeChecked();
    expect(screen.getAllByRole('switch')[1]).not.toBeChecked();
  });

  it('hides write controls for read-only users', async () => {
    useAuthStore.setState({ token: 'token-reader', user: 'reader', admin: false });

    renderWithProviders(<OpsPage />);

    await waitFor(() => {
      expect(queryOpsHomePage).toHaveBeenCalledTimes(1);
    });

    expect(screen.queryByPlaceholderText('NamesrvAddr')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /新增|添加/ })).not.toBeInTheDocument();
  });

  it('keeps write controls visible for admin users', async () => {
    useAuthStore.setState({ token: 'token-admin', user: 'admin', admin: true });

    renderWithProviders(<OpsPage />);

    await waitFor(() => {
      expect(queryOpsHomePage).toHaveBeenCalledTimes(1);
    });

    expect(await screen.findByPlaceholderText('NamesrvAddr')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /新增|添加/ })).toBeInTheDocument();
  });

  it('deletes a non-current NameServer and restores the current selection', async () => {
    const user = userEvent.setup();
    const { container } = renderWithProviders(<OpsPage />);

    await waitFor(() => {
      expect(queryOpsHomePage).toHaveBeenCalledTimes(1);
    });

    const deleteButton = screen.getByRole('button', { name: /删除|Delete/ });
    expect(deleteButton).toBeDisabled();

    fireEvent.mouseDown(container.querySelector('.ant-select-selector') as Element);
    fireEvent.click(
      await screen.findByText('127.0.0.2:9876', {
        selector: '.ant-select-item-option-content',
      }),
    );
    await waitFor(() => {
      expect(deleteButton).toBeEnabled();
    });

    await user.click(deleteButton);
    await user.click(await screen.findByRole('button', { name: /确\s*认|Confirm/ }));

    await waitFor(() => {
      expect(deleteNameSvrAddr).toHaveBeenCalledWith('127.0.0.2:9876');
    });
    expect(container.querySelector('.ant-select-selection-item')).toHaveTextContent(
      '127.0.0.1:9876',
    );
    fireEvent.mouseDown(container.querySelector('.ant-select-selector') as Element);
    await waitFor(() => {
      expect(
        screen.queryAllByText('127.0.0.2:9876', {
          selector: '.ant-select-item-option-content',
        }),
      ).toHaveLength(0);
    });
  });
});
