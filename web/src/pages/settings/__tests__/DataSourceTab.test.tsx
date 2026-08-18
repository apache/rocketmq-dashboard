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
import { createDataSource, listDataSourcePage, testDataSource } from '../../../api/settings';
import { LangProvider } from '../../../i18n/LangContext';
import { DataSourceTab } from '../DataSourceTab';

vi.mock('../../../api/settings', () => ({
  createDataSource: vi.fn(),
  deleteDataSource: vi.fn(),
  getGeneralSettings: vi.fn(),
  listDataSourcePage: vi.fn(),
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

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
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

describe('DataSourceTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listDataSourcePage).mockResolvedValue({
      items: sources,
      total: 2,
      page: 1,
      size: 10,
    });
  });

  it('keeps data source creation disabled until the initial list is ready', async () => {
    const initialList = deferred<{
      items: DataSource[];
      total: number;
      page: number;
      size: number;
    }>();
    vi.mocked(listDataSourcePage).mockReturnValue(initialList.promise);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    const addButton = screen.getByRole('button', { name: /添加数据源/ });
    expect(addButton).toBeDisabled();
    await user.click(addButton);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    initialList.resolve({ items: sources, total: 2, page: 1, size: 10 });

    await waitFor(() => expect(addButton).toBeEnabled());
    await user.click(addButton);
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });

  it('does not report a data source as offline when the backend has not tested it', async () => {
    render(
      <LangProvider>
        <App>
          <DataSourceTab />
        </App>
      </LangProvider>,
    );

    await screen.findByText('Prometheus prod');

    expect(screen.getByText('未检测')).toBeInTheDocument();
    expect(screen.queryByText('离线')).not.toBeInTheDocument();
  });

  it('shows connection test loading only on the clicked row', async () => {
    let resolveTest: (value: { success: boolean; message: string }) => void = () => undefined;
    vi.mocked(testDataSource).mockReturnValue(
      new Promise((resolve) => {
        resolveTest = resolve;
      }),
    );

    const user = userEvent.setup({ pointerEventsCheck: 0 });
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

  it('keeps each row loading until its own connection test finishes', async () => {
    vi.mocked(listDataSourcePage).mockResolvedValue({
      items: sources.map((source) => ({ ...source, auth: 'None' })),
      total: 2,
      page: 1,
      size: 10,
    });
    let resolveFirst: (value: { success: boolean; message: string }) => void = () => undefined;
    let resolveSecond: (value: { success: boolean; message: string }) => void = () => undefined;
    vi.mocked(testDataSource)
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveFirst = resolve;
        }),
      )
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveSecond = resolve;
        }),
      );

    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    await screen.findByText('Prometheus prod');
    const buttons = screen.getAllByRole('button', { name: /测试连接/ });
    await user.click(buttons[0]);
    await user.click(buttons[1]);

    await waitFor(() => {
      expect(buttons[0]).toHaveClass('ant-btn-loading');
      expect(buttons[1]).toHaveClass('ant-btn-loading');
    });

    resolveFirst({ success: true, message: 'ok' });
    await waitFor(() => {
      expect(buttons[0]).not.toHaveClass('ant-btn-loading');
      expect(buttons[1]).toHaveClass('ant-btn-loading');
    });

    resolveSecond({ success: true, message: 'ok' });
    await waitFor(() => {
      expect(buttons[1]).not.toHaveClass('ant-btn-loading');
    });
  });

  it('submits basic auth credentials when testing from the modal', async () => {
    vi.mocked(testDataSource).mockResolvedValue({ success: true, message: 'ok' });

    const user = userEvent.setup({ pointerEventsCheck: 0 });
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

    const user = userEvent.setup({ pointerEventsCheck: 0 });
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

  it('creates and tests a Grafana Mimir data source', async () => {
    const created = {
      key: 'mimir-prod',
      name: 'Mimir prod',
      type: 'Mimir',
      url: 'http://mimir:9009',
      auth: 'None',
      status: 'healthy',
    };
    vi.mocked(listDataSourcePage)
      .mockResolvedValueOnce({ items: sources, total: 2, page: 1, size: 10 })
      .mockResolvedValueOnce({ items: [created, ...sources], total: 3, page: 1, size: 10 });
    vi.mocked(testDataSource).mockResolvedValue({ success: true, message: 'ok' });
    vi.mocked(createDataSource).mockResolvedValue(created);

    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    await screen.findByText('Prometheus prod');
    await user.click(screen.getByRole('button', { name: /添加数据源/ }));
    await user.type(screen.getByLabelText('名称'), 'Mimir prod');
    await selectAntdOption(user, '类型', 'Grafana Mimir');
    await user.type(screen.getByLabelText('URL'), 'http://mimir:9009');

    const testButtons = screen.getAllByRole('button', { name: /测试连接/ });
    await user.click(testButtons[testButtons.length - 1]);

    await waitFor(() => {
      expect(testDataSource).toHaveBeenCalledWith({
        type: 'Mimir',
        url: 'http://mimir:9009',
        auth: 'None',
      });
    });

    await user.click(screen.getByRole('button', { name: 'OK' }));

    await waitFor(() => {
      expect(createDataSource).toHaveBeenCalledWith({
        name: 'Mimir prod',
        type: 'Mimir',
        url: 'http://mimir:9009',
        auth: 'None',
      });
    });
    expect(await screen.findByText('Mimir prod')).toBeInTheDocument();
    expect(screen.getByText('Mimir')).toHaveClass('ant-tag-cyan');
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

    const user = userEvent.setup({ pointerEventsCheck: 0 });
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

  it('offers Cortex and ARMS alongside the other backend types', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    await screen.findByText('Prometheus prod');
    await user.click(screen.getByRole('button', { name: /添加数据源/ }));
    await user.click(screen.getByLabelText('类型'));

    const popup = await waitFor(() => {
      const element = document.getElementById('type_list');
      if (!element) throw new Error('Missing popup type_list');
      return element;
    });

    for (const type of [
      'Prometheus',
      'VictoriaMetrics',
      'Thanos',
      'Grafana Mimir',
      'Cortex',
      'ARMS',
    ]) {
      expect(within(popup).getByRole('option', { name: type })).toBeInTheDocument();
    }
  });

  it('requests the selected page from the backend paginator', async () => {
    vi.mocked(listDataSourcePage)
      .mockResolvedValueOnce({ items: [sources[0]], total: 11, page: 1, size: 10 })
      .mockResolvedValueOnce({ items: [sources[1]], total: 11, page: 2, size: 10 });

    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    await screen.findByText('Prometheus prod');
    await user.click(screen.getByTitle('2'));

    expect(await screen.findByText('Thanos DR')).toBeInTheDocument();
    expect(vi.mocked(listDataSourcePage)).toHaveBeenNthCalledWith(2, { page: 2, pageSize: 10 });
  });

  it('returns to the previous page when the backend reports the current page is out of range', async () => {
    vi.mocked(listDataSourcePage)
      .mockResolvedValueOnce({ items: [sources[0]], total: 11, page: 1, size: 10 })
      .mockResolvedValueOnce({ items: [], total: 10, page: 2, size: 10 })
      .mockResolvedValueOnce({ items: [sources[0]], total: 10, page: 1, size: 10 });

    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    await screen.findByText('Prometheus prod');
    await user.click(screen.getByTitle('2'));

    expect(await screen.findByText('Prometheus prod')).toBeInTheDocument();
    expect(vi.mocked(listDataSourcePage)).toHaveBeenNthCalledWith(3, { page: 1, pageSize: 10 });
  });
});

// antd's Select dropdown keeps `pointer-events: none` while its open animation runs,
// so userEvent refuses to click the option; the interaction itself is valid.
async function selectAntdOption(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  option: string,
) {
  await user.click(screen.getByLabelText(label));
  const popupId = label === '类型' ? 'type_list' : 'auth_list';
  const popup = await waitFor(() => {
    const element = document.getElementById(popupId);
    if (!element) throw new Error(`Missing popup ${popupId}`);
    return element;
  });
  await user.click(within(popup).getByRole('option', { name: option }));
}
