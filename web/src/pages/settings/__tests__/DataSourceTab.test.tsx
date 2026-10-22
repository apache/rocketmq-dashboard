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

import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App, ConfigProvider, message } from 'antd';
import type { DataSource, DataSourcePage } from '../../../api/settings';
import {
  createDataSource,
  deleteDataSource,
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
  afterEach(() => {
    vi.restoreAllMocks();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.removeItem(LANGUAGE_STORAGE_KEY);
    vi.mocked(listDataSourcesPage).mockResolvedValue(sourcePage);
    vi.mocked(listAllDataSources).mockResolvedValue(sources);
  });

  it.each([false, true])(
    'refreshes the current data-source query when a deletion completes after search changes (failed=%s)',
    async (failed) => {
      const pendingDelete = deferred<void>();
      const remaining = { ...sources[0], key: 'prom-stage', name: 'Prometheus stage' };
      let deleted = false;
      vi.mocked(deleteDataSource).mockReturnValueOnce(pendingDelete.promise);
      vi.mocked(listDataSourcesPage).mockImplementation(async (params) => {
        const items =
          params?.search === 'stage'
            ? [remaining]
            : deleted
              ? [sources[1], remaining]
              : [...sources, remaining];
        return { ...sourcePage, items, total: items.length };
      });
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      render(
        <App>
          <LangProvider>
            <DataSourceTab />
          </LangProvider>
        </App>,
      );
      const initialRow = await screen.findByRole('row', { name: /Prometheus prod/ });
      await user.click(within(initialRow).getByRole('button', { name: /删除/ }));
      await user.click(await screen.findByRole('button', { name: /确\s*定/ }));
      await waitFor(() => expect(deleteDataSource).toHaveBeenCalledWith('prom-prod'));
      fireEvent.change(screen.getByPlaceholderText('搜索数据源名称'), {
        target: { value: 'stage' },
      });
      await waitFor(() =>
        expect(listDataSourcesPage).toHaveBeenLastCalledWith(
          expect.objectContaining({ search: 'stage' }),
        ),
      );
      await waitFor(() => expect(screen.queryByText('Thanos DR')).not.toBeInTheDocument());
      deleted = !failed;
      await act(async () => {
        if (failed) pendingDelete.reject(new Error('delete unavailable'));
        else pendingDelete.resolve();
      });
      await waitFor(() => expect(listDataSourcesPage).toHaveBeenCalledTimes(failed ? 2 : 3));
      expect(listDataSourcesPage).toHaveBeenLastCalledWith(
        expect.objectContaining({ search: 'stage' }),
      );
      expect(screen.getByText('Prometheus stage')).toBeInTheDocument();
      expect(screen.queryByText('Thanos DR')).not.toBeInTheDocument();
    },
  );

  it.each([
    { navigated: false, remaining: 40 },
    { navigated: true, remaining: 40 },
    { navigated: false, remaining: 0 },
  ])(
    'refreshes the current page after deleting its last data source (scenario=%j)',
    async ({ navigated, remaining }) => {
      const pendingDelete = deferred<void>();
      const all = Array.from({ length: 41 }, (_, index) => ({
        ...sources[0],
        key: `source-${index + 1}`,
        name: `Source ${index + 1}`,
      }));
      let deleted = false;
      vi.mocked(deleteDataSource).mockReturnValueOnce(pendingDelete.promise);
      vi.mocked(listDataSourcesPage).mockImplementation(async (params) => {
        const page = params?.page ?? 1;
        const size = params?.pageSize ?? 20;
        const rows = deleted ? all.slice(0, remaining) : all;
        return {
          items: rows.slice((page - 1) * size, page * size),
          total: rows.length,
          page,
          size,
        };
      });
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      render(
        <App>
          <LangProvider>
            <DataSourceTab />
          </LangProvider>
        </App>,
      );
      await screen.findByText('Source 1');
      fireEvent.click(document.querySelector('.ant-pagination-item-3')!);
      const lastRow = await screen.findByRole('row', { name: /Source 41/ });
      await user.click(within(lastRow).getByRole('button', { name: /删除/ }));
      await user.click(await screen.findByRole('button', { name: /确\s*定/ }));
      await waitFor(() => expect(deleteDataSource).toHaveBeenCalledWith('source-41'));
      if (navigated) {
        fireEvent.click(document.querySelector('.ant-pagination-item-1')!);
        await screen.findByText('Source 1');
      }
      deleted = true;
      await act(async () => pendingDelete.resolve());
      const expectedPage = navigated || remaining === 0 ? 1 : 2;
      await waitFor(() =>
        expect(listDataSourcesPage).toHaveBeenLastCalledWith(
          expect.objectContaining({ page: expectedPage }),
        ),
      );
      if (remaining > 0)
        expect(await screen.findByText(navigated ? 'Source 1' : 'Source 21')).toBeInTheDocument();
      else expect(screen.queryByText('Source 41')).not.toBeInTheDocument();
    },
  );

  it('does not fetch data sources after a pending deletion finishes on an unmounted tab', async () => {
    const pendingDelete = deferred<void>();
    vi.mocked(deleteDataSource).mockReturnValueOnce(pendingDelete.promise);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    const { unmount } = render(
      <App>
        <LangProvider>
          <DataSourceTab />
        </LangProvider>
      </App>,
    );
    const row = await screen.findByRole('row', { name: /Prometheus prod/ });
    await user.click(within(row).getByRole('button', { name: /删除/ }));
    await user.click(await screen.findByRole('button', { name: /确\s*定/ }));
    await waitFor(() => expect(deleteDataSource).toHaveBeenCalledWith('prom-prod'));
    unmount();
    await act(async () => pendingDelete.resolve());
    expect(listDataSourcesPage).toHaveBeenCalledTimes(1);
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

  it('drops rows from the previous filter when the new list request fails', async () => {
    vi.mocked(listDataSourcesPage)
      .mockResolvedValueOnce({ ...sourcePage, items: [sources[0]], total: 1 })
      .mockRejectedValueOnce(new Error('request failed'));
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    await screen.findByText('Prometheus prod');
    await selectFilterOption(user, '全部类型', 'Thanos');

    await waitFor(() => expect(listDataSourcesPage).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.queryByText('Prometheus prod')).not.toBeInTheDocument());
  });

  it('disables old-row actions and export while a changed search is pending', async () => {
    const nextList = deferred<DataSourcePage>();
    vi.mocked(listDataSourcesPage)
      .mockResolvedValueOnce(sourcePage)
      .mockReturnValueOnce(nextList.promise);
    render(
      <App>
        <LangProvider>
          <DataSourceTab />
        </LangProvider>
      </App>,
    );

    const oldRow = await screen.findByRole('row', { name: /Prometheus prod/ });
    fireEvent.change(screen.getByPlaceholderText('搜索数据源名称'), {
      target: { value: 'stage' },
    });
    expect(within(oldRow).getByRole('button', { name: /编辑/ })).toBeDisabled();
    expect(within(oldRow).getByRole('button', { name: /删除/ })).toBeDisabled();
    expect(within(oldRow).getByRole('button', { name: /测试连接/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /导出/ })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: /导出/ }));
    expect(listAllDataSources).not.toHaveBeenCalled();

    await waitFor(() =>
      expect(listDataSourcesPage).toHaveBeenLastCalledWith(
        expect.objectContaining({ search: 'stage' }),
      ),
    );
    expect(within(oldRow).getByRole('button', { name: /编辑/ })).toBeDisabled();
    await act(async () => nextList.resolve({ ...sourcePage, items: [], total: 0 }));
    await waitFor(() => expect(screen.queryByText('Prometheus prod')).not.toBeInTheDocument());
    expect(testDataSource).not.toHaveBeenCalled();
    expect(deleteDataSource).not.toHaveBeenCalled();
    expect(listAllDataSources).not.toHaveBeenCalled();
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

  it.each(['success', 'failure', 'rejection'] as const)(
    'ignores a previous modal connection test after reopening (%s) Test',
    async (outcome) => {
      const previousTest = deferred<{ success: boolean; message: string }>();
      const currentTest = deferred<{ success: boolean; message: string }>();
      vi.mocked(testDataSource)
        .mockReturnValueOnce(previousTest.promise)
        .mockReturnValueOnce(currentTest.promise);
      const successMessage = vi.spyOn(message, 'success');
      const errorMessage = vi.spyOn(message, 'error');
      localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      render(
        <ConfigProvider theme={{ token: { motion: false } }}>
          <App>
            <LangProvider>
              <DataSourceTab />
            </LangProvider>
          </App>
        </ConfigProvider>,
      );

      const row = await screen.findByRole('row', { name: /Prometheus prod/ });
      await user.click(within(row).getByRole('button', { name: /edit/i }));
      const previousDialog = await screen.findByRole('dialog');
      await user.click(within(previousDialog).getByRole('button', { name: /test connection/i }));
      await waitFor(() => expect(testDataSource).toHaveBeenCalledTimes(1));
      await user.click(within(previousDialog).getByRole('button', { name: /cancel/i }));
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

      await user.click(within(row).getByRole('button', { name: /edit/i }));
      const currentDialog = await screen.findByRole('dialog');
      const testButton = within(currentDialog).getByRole('button', { name: /test connection/i });
      expect(testButton).not.toHaveClass('ant-btn-loading');
      fireEvent.change(within(currentDialog).getByLabelText('URL'), {
        target: { value: 'http://prometheus-new:9090' },
      });
      await user.click(testButton);
      await waitFor(() => expect(testDataSource).toHaveBeenCalledTimes(2));
      expect(testDataSource).toHaveBeenLastCalledWith({
        type: 'Prometheus',
        url: 'http://prometheus-new:9090',
        auth: 'None',
      });
      expect(testButton).toHaveClass('ant-btn-loading');

      await act(async () => {
        if (outcome === 'rejection') previousTest.reject(new Error('old request failed'));
        else previousTest.resolve({ success: outcome === 'success', message: 'Old test result' });
      });
      expect(testButton).toHaveClass('ant-btn-loading');
      expect(successMessage).not.toHaveBeenCalled();
      expect(errorMessage).not.toHaveBeenCalled();

      await act(async () => currentTest.resolve({ success: true, message: 'Current test result' }));
      await waitFor(() => expect(testButton).not.toHaveClass('ant-btn-loading'));
      expect(successMessage).toHaveBeenCalledWith('Current test result');
    },
  );

  it.each(['cancel', 'save', 'unmount'] as const)(
    'ignores a pending modal connection test after %s Test',
    async (action) => {
      const pendingTest = deferred<{ success: boolean; message: string }>();
      vi.mocked(testDataSource).mockReturnValueOnce(pendingTest.promise);
      vi.mocked(updateDataSource).mockResolvedValueOnce(sources[0]);
      const successMessage = vi.spyOn(message, 'success');
      const errorMessage = vi.spyOn(message, 'error');
      localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const { unmount } = render(
        <ConfigProvider theme={{ token: { motion: false } }}>
          <App>
            <LangProvider>
              <DataSourceTab />
            </LangProvider>
          </App>
        </ConfigProvider>,
      );

      const row = await screen.findByRole('row', { name: /Prometheus prod/ });
      await user.click(within(row).getByRole('button', { name: /edit/i }));
      const dialog = await screen.findByRole('dialog');
      await user.click(within(dialog).getByRole('button', { name: /test connection/i }));
      await waitFor(() => expect(testDataSource).toHaveBeenCalledTimes(1));

      if (action === 'unmount') unmount();
      else {
        await user.click(
          within(dialog).getByRole('button', { name: action === 'save' ? 'OK' : /cancel/i }),
        );
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        if (action === 'save') expect(updateDataSource).toHaveBeenCalledTimes(1);
      }
      successMessage.mockClear();
      errorMessage.mockClear();

      await act(async () => {
        if (action === 'unmount') pendingTest.reject(new Error('request failed after unmount'));
        else pendingTest.resolve({ success: true, message: 'Closed modal test result' });
      });
      expect(successMessage).not.toHaveBeenCalled();
      expect(errorMessage).not.toHaveBeenCalled();
    },
  );

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
