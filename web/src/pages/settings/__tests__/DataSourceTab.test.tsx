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
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import type { DataSource } from '../../../api/settings';
import { createDataSource, listDataSources, testDataSource } from '../../../api/settings';
import { DataSourceTab } from '../index';

vi.mock('../../../api/settings', () => ({
  createDataSource: vi.fn(),
  deleteDataSource: vi.fn(),
  getGeneralSettings: vi.fn(),
  listDataSources: vi.fn(),
  saveGeneralSettings: vi.fn(),
  testDataSource: vi.fn(),
  updateDataSource: vi.fn(),
}));

const sources: DataSource[] = [
  {
    key: 'prom-prod',
    name: 'Prometheus prod',
    type: 'Prometheus',
    url: 'http://prometheus:9090',
    auth: 'None',
    status: 'healthy',
  },
  {
    key: 'thanos-dr',
    name: 'Thanos DR',
    type: 'Thanos',
    url: 'http://thanos:10902',
    auth: 'Bearer Token',
    status: 'healthy',
  },
];

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

describe('DataSourceTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listDataSources).mockResolvedValue(sources);
  });

  it('shows connection test loading only on the clicked row', async () => {
    let resolveTest: (value: { success: boolean; message: string }) => void = () => undefined;
    vi.mocked(testDataSource).mockReturnValue(
      new Promise((resolve) => {
        resolveTest = resolve;
      }),
    );

    const user = userEvent.setup();
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    await screen.findByText('Prometheus prod');
    const buttons = screen.getAllByRole('button', { name: /测试连接/ });
    await user.click(buttons[0]);

    await waitFor(() => {
      expect(buttons[0]).toHaveClass('ant-btn-loading');
      expect(buttons[1]).not.toHaveClass('ant-btn-loading');
    });

    resolveTest({ success: true, message: 'ok' });
  });

  it('submits basic auth credentials when testing from the modal', async () => {
    vi.mocked(testDataSource).mockResolvedValue({ success: true, message: 'ok' });

    const user = userEvent.setup();
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    await screen.findByText('Prometheus prod');
    await user.click(screen.getByRole('button', { name: /添加数据源/ }));
    await selectAntdOption(user, '类型', 'Prometheus');
    await user.type(screen.getByLabelText('URL'), 'http://prometheus:9090');
    await selectAntdOption(user, '认证方式', 'Basic Auth');
    await user.type(screen.getByLabelText('用户名'), 'prom');
    await user.type(screen.getByLabelText('密码'), 'secret');

    const testButtons = screen.getAllByRole('button', { name: /测试连接/ });
    await user.click(testButtons[testButtons.length - 1]);

    await waitFor(() => {
      expect(testDataSource).toHaveBeenCalledWith({
        type: 'Prometheus',
        url: 'http://prometheus:9090',
        auth: 'Basic Auth',
        username: 'prom',
        password: 'secret',
      });
    });
  });

  it('submits bearer token when testing from the modal', async () => {
    vi.mocked(testDataSource).mockResolvedValue({ success: true, message: 'ok' });

    const user = userEvent.setup();
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    await screen.findByText('Prometheus prod');
    await user.click(screen.getByRole('button', { name: /添加数据源/ }));
    await selectAntdOption(user, '类型', 'Thanos');
    await user.type(screen.getByLabelText('URL'), 'http://thanos:10902');
    await selectAntdOption(user, '认证方式', 'Bearer Token');
    await user.type(screen.getByLabelText('Bearer Token'), 'token-1');

    const testButtons = screen.getAllByRole('button', { name: /测试连接/ });
    await user.click(testButtons[testButtons.length - 1]);

    await waitFor(() => {
      expect(testDataSource).toHaveBeenCalledWith({
        type: 'Thanos',
        url: 'http://thanos:10902',
        auth: 'Bearer Token',
        bearerToken: 'token-1',
      });
    });
  });

  it('does not persist modal-only credentials when creating a data source', async () => {
    vi.mocked(createDataSource).mockResolvedValue({
      key: 'prom-secure',
      name: 'Prometheus secure',
      type: 'Prometheus',
      url: 'http://prometheus:9090',
      auth: 'Basic Auth',
      status: 'healthy',
    });

    const user = userEvent.setup();
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    await screen.findByText('Prometheus prod');
    await user.click(screen.getByRole('button', { name: /添加数据源/ }));
    await user.type(screen.getByLabelText('名称'), 'Prometheus secure');
    await selectAntdOption(user, '类型', 'Prometheus');
    await user.type(screen.getByLabelText('URL'), 'http://prometheus:9090');
    await selectAntdOption(user, '认证方式', 'Basic Auth');
    await user.type(screen.getByLabelText('用户名'), 'prom');
    await user.type(screen.getByLabelText('密码'), 'secret');

    await user.click(screen.getByRole('button', { name: 'OK' }));

    await waitFor(() => {
      expect(createDataSource).toHaveBeenCalledWith({
        name: 'Prometheus secure',
        type: 'Prometheus',
        url: 'http://prometheus:9090',
        auth: 'Basic Auth',
      });
    });
  });
});

async function selectAntdOption(user: ReturnType<typeof userEvent.setup>, label: string, option: string) {
  await user.click(screen.getByLabelText(label));
  const popupId = label === '类型' ? 'type_list' : 'auth_list';
  const popup = await waitFor(() => {
    const element = document.getElementById(popupId);
    if (!element) throw new Error(`Missing popup ${popupId}`);
    return element;
  });
  await user.click(within(popup).getByRole('option', { name: option }));
}
