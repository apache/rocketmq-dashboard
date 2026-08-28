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
import type { DataSource, DataSourcePage } from '../../../api/settings';
import {
  createDataSource,
  listAllDataSources,
  listDataSourcesPage,
  testDataSource,
  updateDataSource,
} from '../../../api/settings';
import { LangProvider } from '../../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../../i18n/languagePreference';
import { downloadCsv } from '../../../utils/download';
import { DataSourceTab } from '../DataSourceTab';

vi.mock('../../../api/settings', () => ({
  createDataSource: vi.fn(),
  deleteDataSource: vi.fn(),
  getGeneralSettings: vi.fn(),
  listAllDataSources: vi.fn(),
  listDataSourcesPage: vi.fn(),
  saveGeneralSettings: vi.fn(),
  testDataSource: vi.fn(),
  updateDataSource: vi.fn(),
}));

vi.mock('../../../utils/download', async () => {
  const downloadModule =
    await vi.importActual<typeof import('../../../utils/download')>('../../../utils/download');
  return {
    ...downloadModule,
    downloadCsv: vi.fn(),
  };
});

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

const sourcePage: DataSourcePage = {
  items: sources,
  total: sources.length,
  page: 1,
  size: 20,
};

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
    localStorage.removeItem(LANGUAGE_STORAGE_KEY);
    vi.mocked(listDataSourcesPage).mockResolvedValue(sourcePage);
    vi.mocked(listAllDataSources).mockResolvedValue(sources);
  });

  it('keeps data source creation disabled until the initial list is ready', async () => {
    const initialList = deferred<DataSourcePage>();
    vi.mocked(listDataSourcesPage).mockReturnValue(initialList.promise);
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

    initialList.resolve(sourcePage);

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

  it('renders management controls in English when English is selected', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    render(
      <LangProvider>
        <App>
          <DataSourceTab />
        </App>
      </LangProvider>,
    );

    expect(await screen.findByPlaceholderText('Search data source names')).toBeInTheDocument();
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
    vi.mocked(listDataSourcesPage).mockResolvedValue({
      ...sourcePage,
      items: sources.map((source) => ({ ...source, auth: 'None' })),
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

  it('exports all data sources that match the active filters without secrets', async () => {
    vi.mocked(listAllDataSources).mockResolvedValue([
      {
        ...sources[1],
        instanceIds: ['instance-1'],
        username: 'hidden-user',
        password: 'hidden-password',
        bearerToken: 'hidden-token',
      },
    ]);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <LangProvider>
        <App>
          <DataSourceTab />
        </App>
      </LangProvider>,
    );

    await screen.findByText('Prometheus prod');
    await user.type(screen.getByPlaceholderText('搜索数据源名称'), 'prom');
    await selectFilterOption(user, '全部类型', 'Thanos');
    await waitFor(() =>
      expect(listDataSourcesPage).toHaveBeenLastCalledWith({
        search: 'prom',
        type: 'Thanos',
        page: 1,
        pageSize: 20,
      }),
    );

    await user.click(screen.getByRole('button', { name: '导出' }));

    await waitFor(() =>
      expect(listAllDataSources).toHaveBeenCalledWith({
        search: 'prom',
        type: 'Thanos',
      }),
    );
    const [filename, csv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(filename).toMatch(/^rocketmq-data-sources-\d{4}-\d{2}-\d{2}\.csv$/);
    expect(csv).toContain('"Name","Type","URL","Applicable Instances","Authentication","Status"');
    expect(csv).toContain('"Thanos DR","Thanos","http://thanos:10902"');
    expect(csv).toContain('"Bearer Token"');
    expect(csv).not.toContain('hidden-user');
    expect(csv).not.toContain('hidden-password');
    expect(csv).not.toContain('hidden-token');
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

  it('updates authenticated data source metadata without requiring query credentials', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    vi.mocked(updateDataSource).mockResolvedValue({
      ...sources[1],
      name: 'Thanos primary',
    });
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <DataSourceTab />
      </App>,
    );
    await screen.findByText('Thanos DR');

    const editButtons = screen.getAllByRole('button', { name: /edit/i });
    await user.click(editButtons[1]);
    const dialog = await screen.findByRole('dialog');
    const nameInput = within(dialog).getByDisplayValue('Thanos DR');
    await user.clear(nameInput);
    await user.type(nameInput, 'Thanos primary');
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));

    await waitFor(() =>
      expect(updateDataSource).toHaveBeenCalledWith({
        ...sources[1],
        name: 'Thanos primary',
      }),
    );
  });

  it('creates and tests a Grafana Mimir data source', async () => {
    vi.mocked(testDataSource).mockResolvedValue({ success: true, message: 'ok' });
    vi.mocked(createDataSource).mockResolvedValue({
      key: 'mimir-prod',
      name: 'Mimir prod',
      type: 'Mimir',
      url: 'http://mimir:9009',
      auth: 'None',
      status: 'healthy',
    });
    vi.mocked(listDataSourcesPage).mockResolvedValue({
      ...sourcePage,
      items: [
        {
          key: 'mimir-prod',
          name: 'Mimir prod',
          type: 'Mimir',
          url: 'http://mimir:9009',
          auth: 'None',
          status: 'healthy',
        },
      ],
    });

    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    await waitFor(() => expect(listDataSourcesPage).toHaveBeenCalled());
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

async function selectFilterOption(
  user: ReturnType<typeof userEvent.setup>,
  placeholder: string,
  option: string,
) {
  await user.click(screen.getByText(placeholder));
  await user.click(
    await screen.findByText(option, { selector: '.ant-select-item-option-content' }),
  );
}
