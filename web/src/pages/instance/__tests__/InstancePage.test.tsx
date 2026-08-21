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

import { App, Modal } from 'antd';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import * as aliyunCatalogApi from '../../../api/aliyunCatalog';
import * as cloudCredentialApi from '../../../api/cloudCredential';
import type { CloudCredential, CloudCredentialPage } from '../../../api/cloudCredential';
import type { Instance } from '../../../api/instance';
import { LangProvider } from '../../../i18n/LangContext';
import * as instanceService from '../../../services/instanceService';
import InstancePage from '../index';

vi.mock('../../../api/aliyunCatalog', () => ({
  listAliyunInstances: vi.fn(),
  listAliyunRegions: vi.fn(),
}));

vi.mock('../../../api/cloudCredential', () => ({
  listCloudCredentials: vi.fn(),
}));

vi.mock('../../../api/tencentCatalog', () => ({
  listTencentInstances: vi.fn(),
  listTencentRegions: vi.fn(),
}));

vi.mock('../../../services/instanceService', () => ({
  createInstance: vi.fn(),
  deleteInstance: vi.fn(),
  deleteInstancesBatch: vi.fn(),
  importCloudInstances: vi.fn(),
  listInstances: vi.fn(),
  updateInstance: vi.fn(),
}));

const cloudCredentialPage = (items: CloudCredential[]): CloudCredentialPage => ({
  items,
  total: items.length,
  page: 1,
  size: 20,
});

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

const instance = (
  id: number,
  name: string,
  type: Instance['type'] = 'PROXY_CLUSTER',
  remark: Instance['remark'] = '',
): Instance => ({
  id,
  name,
  remark,
  type,
  endpoint: `${name}:8080`,
  topicCount: 1,
  consumerGroupCount: 1,
  gmtCreate: '2026-01-01T00:00:00Z',
  gmtModified: '2026-01-01T00:00:00Z',
});

const renderPage = () =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter>
          <InstancePage />
        </MemoryRouter>
      </LangProvider>
    </App>,
  );

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
};

describe('InstancePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(cloudCredentialApi.listCloudCredentials).mockResolvedValue(cloudCredentialPage([]));
    vi.mocked(aliyunCatalogApi.listAliyunRegions).mockResolvedValue([]);
    vi.mocked(aliyunCatalogApi.listAliyunInstances).mockResolvedValue([]);
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      instance(1, 'production-proxy'),
      instance(2, 'development-direct', 'DIRECT'),
    ]);
  });

  it('loads server-filtered results when the search or type changes', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('production-proxy')).toBeInTheDocument();
    expect(instanceService.listInstances).toHaveBeenCalledWith({});

    fireEvent.change(screen.getByPlaceholderText('搜索实例 ID 或地址'), {
      target: { value: 'proxy-hz' },
    });
    await waitFor(
      () => expect(instanceService.listInstances).toHaveBeenCalledWith({ search: 'proxy-hz' }),
      { timeout: 1000 },
    );

    fireEvent.change(screen.getByPlaceholderText('搜索实例 ID 或地址'), {
      target: { value: '' },
    });
    await waitFor(() => expect(instanceService.listInstances).toHaveBeenLastCalledWith({}), {
      timeout: 1000,
    });

    const typeSelect = screen.getByRole('combobox');
    fireEvent.mouseDown(typeSelect.parentElement!);
    await user.click(
      await screen.findByText('Direct 模式', { selector: '.ant-select-item-option-content' }),
    );

    await waitFor(() =>
      expect(instanceService.listInstances).toHaveBeenLastCalledWith({ type: 'DIRECT' }),
    );
  });

  it('keeps unavailable resource counts after available values in both sort directions', async () => {
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      {
        ...instance(3, 'unavailable-instance'),
        topicCount: 0,
        resourceCountsAvailable: false,
      },
      { ...instance(4, 'zero-instance'), topicCount: 0 },
      { ...instance(5, 'many-instance'), topicCount: 10 },
    ]);
    const { container } = renderPage();

    await screen.findByText('unavailable-instance');
    const topicHeader = screen.getByRole('columnheader', { name: 'Topic' });
    const rowNames = () =>
      Array.from(container.querySelectorAll('tbody tr'))
        .map((row) =>
          ['zero-instance', 'many-instance', 'unavailable-instance'].find((name) =>
            row.textContent?.includes(name),
          ),
        )
        .filter((name): name is string => Boolean(name));

    fireEvent.click(topicHeader);
    await waitFor(() => {
      expect(rowNames()).toEqual(['zero-instance', 'many-instance', 'unavailable-instance']);
    });

    fireEvent.click(topicHeader);
    await waitFor(() => {
      expect(rowNames()).toEqual(['many-instance', 'zero-instance', 'unavailable-instance']);
    });
  });

  it('ignores an older search response that finishes after the latest request', async () => {
    let resolveOldSearch!: (instances: Instance[]) => void;
    let resolveLatestSearch!: (instances: Instance[]) => void;
    const oldSearch = new Promise<Instance[]>((resolve) => {
      resolveOldSearch = resolve;
    });
    const latestSearch = new Promise<Instance[]>((resolve) => {
      resolveLatestSearch = resolve;
    });
    vi.mocked(instanceService.listInstances)
      .mockResolvedValueOnce([instance(6, 'initial-instance')])
      .mockReturnValueOnce(oldSearch)
      .mockReturnValueOnce(latestSearch);
    renderPage();

    expect(await screen.findByText('initial-instance')).toBeInTheDocument();
    const searchInput = screen.getByPlaceholderText('搜索实例 ID 或地址');
    fireEvent.change(searchInput, { target: { value: 'old' } });
    await waitFor(
      () => expect(instanceService.listInstances).toHaveBeenCalledWith({ search: 'old' }),
      {
        timeout: 1000,
      },
    );

    fireEvent.change(searchInput, { target: { value: 'latest' } });
    await waitFor(
      () => expect(instanceService.listInstances).toHaveBeenCalledWith({ search: 'latest' }),
      { timeout: 1000 },
    );

    await act(async () => resolveLatestSearch([instance(7, 'latest-instance')]));
    expect(await screen.findByText('latest-instance')).toBeInTheDocument();

    await act(async () => resolveOldSearch([instance(8, 'old-instance')]));
    expect(screen.queryByText('old-instance')).not.toBeInTheDocument();
    expect(screen.getByText('latest-instance')).toBeInTheDocument();
  });

  it('ignores duplicate create submissions while the first request is pending', async () => {
    const user = userEvent.setup();
    vi.mocked(instanceService.createInstance).mockImplementation(() => new Promise(() => {}));
    renderPage();

    expect(await screen.findByText('production-proxy')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /添加实例/ }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('实例 ID'), 'new-proxy');
    const createTypeSelect = within(dialog).getByRole('combobox');
    fireEvent.mouseDown(createTypeSelect.parentElement!);
    const proxyOptions = await screen.findAllByText('Proxy Cluster 模式', {
      selector: '.ant-select-item-option-content',
    });
    await user.click(proxyOptions[proxyOptions.length - 1]);
    await user.type(within(dialog).getByLabelText('接入地址'), 'proxy-new:8080');
    const connect = within(dialog).getByRole('button', { name: /连\s*接/ });

    fireEvent.click(connect);
    fireEvent.click(connect);

    await waitFor(() => expect(instanceService.createInstance).toHaveBeenCalledTimes(1));
  });

  it('reloads the current filters after creating an instance', async () => {
    const user = userEvent.setup();
    vi.mocked(instanceService.createInstance).mockResolvedValue(instance(9, 'new-proxy'));
    renderPage();

    expect(await screen.findByText('production-proxy')).toBeInTheDocument();
    const typeSelect = screen.getByRole('combobox');
    fireEvent.mouseDown(typeSelect.parentElement!);
    await user.click(
      await screen.findByText('Direct 模式', { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() =>
      expect(instanceService.listInstances).toHaveBeenLastCalledWith({ type: 'DIRECT' }),
    );

    await user.click(screen.getByRole('button', { name: /添加实例/ }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('实例 ID'), 'new-proxy');
    const createTypeSelect = within(dialog).getByRole('combobox');
    fireEvent.mouseDown(createTypeSelect.parentElement!);
    const proxyOptions = await screen.findAllByText('Proxy Cluster 模式', {
      selector: '.ant-select-item-option-content',
    });
    await user.click(proxyOptions[proxyOptions.length - 1]);
    await user.type(within(dialog).getByLabelText('接入地址'), 'proxy-new:8080');
    await user.click(within(dialog).getByRole('button', { name: /连\s*接/ }));

    await waitFor(() =>
      expect(instanceService.createInstance).toHaveBeenCalledWith({
        name: 'new-proxy',
        type: 'PROXY_CLUSTER',
        endpoint: 'proxy-new:8080',
      }),
    );
    expect(instanceService.listInstances).toHaveBeenLastCalledWith({ type: 'DIRECT' });
  });

  it('creates an Apache instance with an explicit Proxy Local deployment type', async () => {
    const user = userEvent.setup();
    vi.mocked(instanceService.createInstance).mockResolvedValue(
      instance(3, 'local-proxy', 'PROXY_LOCAL'),
    );
    renderPage();

    expect(await screen.findByText('production-proxy')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /添加实例/ }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('实例 ID'), 'local-proxy');
    const createTypeSelect = within(dialog).getByRole('combobox');
    fireEvent.mouseDown(createTypeSelect.parentElement!);
    await user.click(
      await screen.findByText('Proxy Local 模式', {
        selector: '.ant-select-item-option-content',
      }),
    );
    await user.type(within(dialog).getByLabelText('接入地址'), 'broker-proxy:8080');
    await user.click(within(dialog).getByRole('button', { name: /连\s*接/ }));

    await waitFor(() =>
      expect(instanceService.createInstance).toHaveBeenCalledWith({
        name: 'local-proxy',
        type: 'PROXY_LOCAL',
        endpoint: 'broker-proxy:8080',
      }),
    );
  });

  it('updates instance type and endpoint through the edit dialog', async () => {
    const user = userEvent.setup();
    vi.mocked(instanceService.updateInstance).mockResolvedValue(
      instance(1, 'production-proxy', 'DIRECT'),
    );
    renderPage();

    expect(await screen.findByText('production-proxy')).toBeInTheDocument();
    const row = screen.getByRole('row', { name: /production-proxy/ });
    await user.click(within(row).getByRole('button', { name: /编\s*辑/ }));
    const dialog = await screen.findByRole('dialog');

    const typeSelect = within(dialog).getByRole('combobox');
    fireEvent.mouseDown(typeSelect.parentElement!);
    await user.click(
      await screen.findByText('Direct 模式', { selector: '.ant-select-item-option-content' }),
    );

    const endpointInput = within(dialog).getByLabelText('接入地址');
    await user.clear(endpointInput);
    await user.type(endpointInput, 'namesrv-new:9876');
    await user.click(within(dialog).getByRole('button', { name: /保\s*存/ }));

    await waitFor(() =>
      expect(instanceService.updateInstance).toHaveBeenCalledWith(
        expect.objectContaining({
          instanceId: 'production-proxy',
          type: 'DIRECT',
          endpoint: 'namesrv-new:9876',
        }),
      ),
    );
  });

  it('reloads the latest filters after a pending instance deletion completes', async () => {
    const user = userEvent.setup();
    const pendingDelete = deferred<void>();
    vi.mocked(instanceService.deleteInstance).mockReturnValue(pendingDelete.promise);
    const confirmSpy = vi.spyOn(Modal, 'confirm').mockImplementation((config) => {
      void config.onOk?.();
      return { destroy: vi.fn(), update: vi.fn() } as unknown as ReturnType<typeof Modal.confirm>;
    });
    renderPage();

    const proxyName = await screen.findByText('production-proxy');
    await user.click(within(proxyName.closest('tr')!).getByRole('button', { name: /删除/ }));
    await waitFor(() =>
      expect(instanceService.deleteInstance).toHaveBeenCalledWith('production-proxy'),
    );

    const typeSelect = screen.getByRole('combobox');
    fireEvent.mouseDown(typeSelect.parentElement!);
    await user.click(
      await screen.findByText('Direct 模式', { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() =>
      expect(instanceService.listInstances).toHaveBeenLastCalledWith({ type: 'DIRECT' }),
    );

    await act(async () => pendingDelete.resolve());

    await waitFor(() => expect(instanceService.listInstances).toHaveBeenCalledTimes(3));
    expect(instanceService.listInstances).toHaveBeenLastCalledWith({ type: 'DIRECT' });
    confirmSpy.mockRestore();
  });

  it('shows vendor tabs in the add instance modal and switches description', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('production-proxy')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /添加实例/ }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByRole('tab', { name: /开源版/ })).toBeInTheDocument();
    expect(within(dialog).getByRole('tab', { name: /Aliyun 版/ })).toBeInTheDocument();
    expect(within(dialog).getByRole('tab', { name: /Tencent 版/ })).toBeInTheDocument();
    expect(within(dialog).getByText(/自建 Apache RocketMQ 开源集群/)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('tab', { name: /Aliyun 版/ }));
    expect(within(dialog).getByText(/云凭据与云上实例完成接入/)).toBeInTheDocument();
  });

  it('imports every Aliyun instance of the credential via one-click import', async () => {
    const user = userEvent.setup();
    vi.mocked(cloudCredentialApi.listCloudCredentials).mockResolvedValue(
      cloudCredentialPage([
        {
          id: 101,
          name: 'prod-account',
          vendor: 'ALIYUN',
          accessKey: 'LTAI-prod',
          gmtCreate: '2026-01-01T00:00:00Z',
        },
      ]),
    );
    vi.mocked(instanceService.importCloudInstances).mockResolvedValue({
      discovered: 3,
      imported: 2,
      skipped: 1,
      failed: [],
    });

    renderPage();
    expect(await screen.findByText('production-proxy')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /添加实例/ }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('tab', { name: /Aliyun 版/ }));
    await waitFor(() => expect(cloudCredentialApi.listCloudCredentials).toHaveBeenCalled());

    const importButton = within(dialog).getByRole('button', { name: /一键导入/ });
    expect(importButton).toBeDisabled();

    const credentialSelect = within(dialog).getAllByRole('combobox')[0];
    fireEvent.mouseDown(credentialSelect.parentElement!);
    await user.click(
      await screen.findByText(/prod-account/, { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(importButton).toBeEnabled());

    await user.click(importButton);

    await waitFor(() =>
      expect(instanceService.importCloudInstances).toHaveBeenCalledWith({
        vendor: 'ALIYUN',
        credentialId: 101,
      }),
    );
    expect(
      await screen.findByText(/导入完成：共同步 3 个实例（新导入 2，已存在跳过 1）/),
    ).toBeInTheDocument();
  });

  it('ignores a stale region response after the cloud credential changes', async () => {
    const user = userEvent.setup();
    const oldRegions = deferred<Array<{ regionId: string; regionName: string }>>();
    const latestRegions = deferred<Array<{ regionId: string; regionName: string }>>();
    vi.mocked(cloudCredentialApi.listCloudCredentials).mockResolvedValue(
      cloudCredentialPage([
        {
          id: 101,
          name: 'old-account',
          vendor: 'ALIYUN',
          accessKey: 'LTAI-old',
          gmtCreate: '2026-01-01T00:00:00Z',
        },
        {
          id: 102,
          name: 'latest-account',
          vendor: 'ALIYUN',
          accessKey: 'LTAI-latest',
          gmtCreate: '2026-01-01T00:00:00Z',
        },
      ]),
    );
    vi.mocked(aliyunCatalogApi.listAliyunRegions)
      .mockReturnValueOnce(oldRegions.promise)
      .mockReturnValueOnce(latestRegions.promise);

    renderPage();
    expect(await screen.findByText('production-proxy')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /添加实例/ }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('tab', { name: /Aliyun 版/ }));
    await waitFor(() => expect(cloudCredentialApi.listCloudCredentials).toHaveBeenCalled());

    const credentialSelect = within(dialog).getAllByRole('combobox')[0];
    fireEvent.mouseDown(credentialSelect.parentElement!);
    await user.click(
      await screen.findByText(/old-account/, { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(aliyunCatalogApi.listAliyunRegions).toHaveBeenCalledWith(101));

    fireEvent.mouseDown(credentialSelect.parentElement!);
    await user.click(
      await screen.findByText(/latest-account/, { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(aliyunCatalogApi.listAliyunRegions).toHaveBeenCalledWith(102));

    await act(async () =>
      latestRegions.resolve([{ regionId: 'cn-shanghai', regionName: 'Shanghai' }]),
    );
    await act(async () =>
      oldRegions.resolve([{ regionId: 'cn-hangzhou-old', regionName: 'Old Hangzhou' }]),
    );

    const regionSelect = within(dialog).getAllByRole('combobox')[1];
    fireEvent.mouseDown(regionSelect.parentElement!);
    expect(
      await screen.findByText(/Shanghai/, { selector: '.ant-select-item-option-content' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/Old Hangzhou/, { selector: '.ant-select-item-option-content' }),
    ).not.toBeInTheDocument();
  });

  it('ignores a stale instance response after the cloud region changes', async () => {
    const user = userEvent.setup();
    const oldInstances =
      deferred<
        Array<{ instanceId: string; instanceName: string; status: string; regionId: string }>
      >();
    const latestInstances =
      deferred<
        Array<{ instanceId: string; instanceName: string; status: string; regionId: string }>
      >();
    vi.mocked(cloudCredentialApi.listCloudCredentials).mockResolvedValue(
      cloudCredentialPage([
        {
          id: 103,
          name: 'cloud-account',
          vendor: 'ALIYUN',
          accessKey: 'LTAI-one',
          gmtCreate: '2026-01-01T00:00:00Z',
        },
      ]),
    );
    vi.mocked(aliyunCatalogApi.listAliyunRegions).mockResolvedValue([
      { regionId: 'cn-beijing', regionName: 'Beijing' },
      { regionId: 'cn-shanghai', regionName: 'Shanghai' },
    ]);
    vi.mocked(aliyunCatalogApi.listAliyunInstances)
      .mockReturnValueOnce(oldInstances.promise)
      .mockReturnValueOnce(latestInstances.promise);

    renderPage();
    expect(await screen.findByText('production-proxy')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /添加实例/ }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('tab', { name: /Aliyun 版/ }));
    await waitFor(() => expect(cloudCredentialApi.listCloudCredentials).toHaveBeenCalled());

    const selects = within(dialog).getAllByRole('combobox');
    fireEvent.mouseDown(selects[0].parentElement!);
    await user.click(
      await screen.findByText(/cloud-account/, { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(aliyunCatalogApi.listAliyunRegions).toHaveBeenCalledWith(103));

    fireEvent.mouseDown(selects[1].parentElement!);
    await user.click(
      await screen.findByText(/Beijing/, { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() =>
      expect(aliyunCatalogApi.listAliyunInstances).toHaveBeenCalledWith(103, 'cn-beijing'),
    );

    fireEvent.mouseDown(selects[1].parentElement!);
    await user.click(
      await screen.findByText(/Shanghai/, { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() =>
      expect(aliyunCatalogApi.listAliyunInstances).toHaveBeenCalledWith(103, 'cn-shanghai'),
    );

    await act(async () =>
      latestInstances.resolve([
        {
          instanceId: 'rmq-latest',
          instanceName: 'latest-instance',
          status: 'RUNNING',
          regionId: 'cn-shanghai',
        },
      ]),
    );
    await act(async () =>
      oldInstances.resolve([
        {
          instanceId: 'rmq-old',
          instanceName: 'old-instance',
          status: 'RUNNING',
          regionId: 'cn-beijing',
        },
      ]),
    );

    fireEvent.mouseDown(selects[2].parentElement!);
    expect(
      await screen.findByText(/latest-instance/, { selector: '.ant-select-item-option-content' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/old-instance/, { selector: '.ant-select-item-option-content' }),
    ).not.toBeInTheDocument();
  });

  it('clears a pending region load when the cloud vendor changes', async () => {
    const user = userEvent.setup();
    const pendingRegions = deferred<Array<{ regionId: string; regionName: string }>>();
    vi.mocked(cloudCredentialApi.listCloudCredentials).mockResolvedValue(
      cloudCredentialPage([
        {
          id: 104,
          name: 'aliyun-account',
          vendor: 'ALIYUN',
          accessKey: 'LTAI-one',
          gmtCreate: '2026-01-01T00:00:00Z',
        },
        {
          id: 105,
          name: 'tencent-account',
          vendor: 'TENCENT',
          accessKey: 'AKID-one',
          gmtCreate: '2026-01-01T00:00:00Z',
        },
      ]),
    );
    vi.mocked(aliyunCatalogApi.listAliyunRegions).mockReturnValue(pendingRegions.promise);

    renderPage();
    expect(await screen.findByText('production-proxy')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /添加实例/ }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('tab', { name: /Aliyun/ }));
    await waitFor(() => expect(cloudCredentialApi.listCloudCredentials).toHaveBeenCalled());

    const credentialSelect = within(dialog).getAllByRole('combobox')[0];
    fireEvent.mouseDown(credentialSelect.parentElement!);
    await user.click(
      await screen.findByText(/aliyun-account/, { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(aliyunCatalogApi.listAliyunRegions).toHaveBeenCalledWith(104));
    await waitFor(() =>
      expect(within(dialog).getAllByRole('combobox')[1].closest('.ant-select')).toHaveClass(
        'ant-select-loading',
      ),
    );

    await user.click(within(dialog).getByRole('tab', { name: /Tencent/ }));

    const switchedSelects = within(dialog).getAllByRole('combobox');
    expect(switchedSelects[0]).toHaveValue('');
    expect(switchedSelects[1]).toHaveValue('');
    expect(switchedSelects[2]).toHaveValue('');
    expect(switchedSelects[1]).toBeDisabled();
    expect(switchedSelects[2]).toBeDisabled();
    expect(switchedSelects[1].closest('.ant-select')).not.toHaveClass('ant-select-loading');

    await act(async () =>
      pendingRegions.resolve([{ regionId: 'cn-hangzhou-old', regionName: 'Old Hangzhou' }]),
    );
    expect(switchedSelects[1]).toBeDisabled();
  });

  it('clears a pending instance load when the cloud vendor changes', async () => {
    const user = userEvent.setup();
    const pendingInstances =
      deferred<
        Array<{ instanceId: string; instanceName: string; status: string; regionId: string }>
      >();
    vi.mocked(cloudCredentialApi.listCloudCredentials).mockResolvedValue(
      cloudCredentialPage([
        {
          id: 104,
          name: 'aliyun-account',
          vendor: 'ALIYUN',
          accessKey: 'LTAI-one',
          gmtCreate: '2026-01-01T00:00:00Z',
        },
        {
          id: 105,
          name: 'tencent-account',
          vendor: 'TENCENT',
          accessKey: 'AKID-one',
          gmtCreate: '2026-01-01T00:00:00Z',
        },
      ]),
    );
    vi.mocked(aliyunCatalogApi.listAliyunRegions).mockResolvedValue([
      { regionId: 'cn-beijing', regionName: 'Beijing' },
    ]);
    vi.mocked(aliyunCatalogApi.listAliyunInstances).mockReturnValue(pendingInstances.promise);

    renderPage();
    expect(await screen.findByText('production-proxy')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /添加实例/ }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('tab', { name: /Aliyun/ }));
    await waitFor(() => expect(cloudCredentialApi.listCloudCredentials).toHaveBeenCalled());

    const selects = within(dialog).getAllByRole('combobox');
    fireEvent.mouseDown(selects[0].parentElement!);
    await user.click(
      await screen.findByText(/aliyun-account/, { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(aliyunCatalogApi.listAliyunRegions).toHaveBeenCalled());
    fireEvent.mouseDown(selects[1].parentElement!);
    await user.click(
      await screen.findByText(/Beijing/, { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() =>
      expect(aliyunCatalogApi.listAliyunInstances).toHaveBeenCalledWith(104, 'cn-beijing'),
    );
    await waitFor(() =>
      expect(selects[2].closest('.ant-select')).toHaveClass('ant-select-loading'),
    );

    await user.click(within(dialog).getByRole('tab', { name: /Tencent/ }));

    const switchedSelects = within(dialog).getAllByRole('combobox');
    expect(switchedSelects[0]).toHaveValue('');
    expect(switchedSelects[1]).toHaveValue('');
    expect(switchedSelects[2]).toHaveValue('');
    expect(switchedSelects[1]).toBeDisabled();
    expect(switchedSelects[2]).toBeDisabled();
    expect(switchedSelects[2].closest('.ant-select')).not.toHaveClass('ant-select-loading');

    await act(async () =>
      pendingInstances.resolve([
        {
          instanceId: 'rmq-old',
          instanceName: 'old-instance',
          status: 'RUNNING',
          regionId: 'cn-beijing',
        },
      ]),
    );
    expect(switchedSelects[2]).toBeDisabled();
  });

  it('sorts and renders instances without remarks', async () => {
    const user = userEvent.setup();
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      instance(10, 'instance-without-remark', 'PROXY_CLUSTER', null),
      instance(11, 'instance-with-remark', 'PROXY_CLUSTER', 'production'),
    ]);
    renderPage();

    const name = await screen.findByText('instance-without-remark');
    expect(within(name.closest('tr')!).getAllByText('-').length).toBeGreaterThan(0);

    await user.click(screen.getByRole('columnheader', { name: /备注/ }));
    expect(screen.getByText('instance-without-remark')).toBeInTheDocument();
    expect(screen.getByText('instance-with-remark')).toBeInTheDocument();
  });

  it('renders the region column with regionId for cloud instances and a dash for open-source ones', async () => {
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      {
        ...instance(12, 'rmq-cloud-1', 'CLOUD', ''),
        vendor: 'ALIYUN',
        regionId: 'cn-hangzhou',
      },
      instance(13, 'open-source-1', 'DIRECT', ''),
    ]);
    renderPage();

    expect(await screen.findByRole('columnheader', { name: '地域' })).toBeInTheDocument();
    const cloudRow = (await screen.findByText('rmq-cloud-1')).closest('tr')!;
    expect(within(cloudRow).getByText('cn-hangzhou')).toBeInTheDocument();
    const apacheRow = screen.getByText('open-source-1').closest('tr')!;
    expect(within(apacheRow).getAllByText('开源版').length).toBeGreaterThan(0);
    expect(within(apacheRow).queryByText('cn-hangzhou')).not.toBeInTheDocument();
  });

  it('deletes selected instances through the toolbar batch delete button', async () => {
    const user = userEvent.setup();
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      instance(20, 'batch-a'),
      instance(21, 'batch-b'),
    ]);
    vi.mocked(instanceService.deleteInstancesBatch).mockResolvedValue({ deleted: 1, failed: [] });
    const confirmSpy = vi.spyOn(Modal, 'confirm').mockImplementation((config) => {
      void config.onOk?.();
      return { destroy: vi.fn(), update: vi.fn() } as unknown as ReturnType<typeof Modal.confirm>;
    });
    renderPage();

    await screen.findByText('batch-a');
    const deleteButton = screen.getAllByRole('button', { name: /删除/ })[0];
    expect(deleteButton).toBeDisabled();

    const checkboxes = screen.getAllByRole('checkbox');
    await user.click(checkboxes[1]);
    await waitFor(() => expect(deleteButton).toBeEnabled());

    await user.click(deleteButton);

    await waitFor(() =>
      expect(instanceService.deleteInstancesBatch).toHaveBeenCalledWith(['batch-a']),
    );
    expect(confirmSpy).toHaveBeenCalled();
    confirmSpy.mockRestore();
  });
});
