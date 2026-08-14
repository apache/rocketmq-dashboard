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
  listInstances: vi.fn(),
  updateInstance: vi.fn(),
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

const instance = (
  id: string,
  name: string,
  type: Instance['type'] = 'PROXY',
  remark: Instance['remark'] = '',
): Instance => ({
  id,
  name,
  remark,
  type,
  endpoint: `${name}:8080`,
  topicCount: 1,
  consumerGroupCount: 1,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
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
    vi.mocked(cloudCredentialApi.listCloudCredentials).mockResolvedValue([]);
    vi.mocked(aliyunCatalogApi.listAliyunRegions).mockResolvedValue([]);
    vi.mocked(aliyunCatalogApi.listAliyunInstances).mockResolvedValue([]);
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      instance('proxy-1', 'production-proxy'),
      instance('direct-1', 'development-direct', 'DIRECT'),
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
        ...instance('unavailable', 'unavailable-instance'),
        topicCount: 0,
        resourceCountsAvailable: false,
      },
      { ...instance('zero', 'zero-instance'), topicCount: 0 },
      { ...instance('many', 'many-instance'), topicCount: 10 },
    ]);
    const { container } = renderPage();

    await screen.findByText('unavailable-instance');
    const topicHeader = screen.getByRole('columnheader', { name: 'Topic' });
    const rowNames = () =>
      Array.from(container.querySelectorAll('tbody tr')).map(
        (row) => row.querySelector('td')?.textContent,
      );

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
      .mockResolvedValueOnce([instance('initial', 'initial-instance')])
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

    await act(async () => resolveLatestSearch([instance('latest', 'latest-instance')]));
    expect(await screen.findByText('latest-instance')).toBeInTheDocument();

    await act(async () => resolveOldSearch([instance('old', 'old-instance')]));
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
    const proxyOptions = await screen.findAllByText('Proxy 模式', {
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
    vi.mocked(instanceService.createInstance).mockResolvedValue(instance('created', 'new-proxy'));
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
    const proxyOptions = await screen.findAllByText('Proxy 模式', {
      selector: '.ant-select-item-option-content',
    });
    await user.click(proxyOptions[proxyOptions.length - 1]);
    await user.type(within(dialog).getByLabelText('接入地址'), 'proxy-new:8080');
    await user.click(within(dialog).getByRole('button', { name: /连\s*接/ }));

    await waitFor(() =>
      expect(instanceService.createInstance).toHaveBeenCalledWith({
        name: 'new-proxy',
        type: 'PROXY',
        endpoint: 'proxy-new:8080',
      }),
    );
    expect(instanceService.listInstances).toHaveBeenLastCalledWith({ type: 'DIRECT' });
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
    await waitFor(() => expect(instanceService.deleteInstance).toHaveBeenCalledWith('proxy-1'));

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

  it('ignores a stale region response after the cloud credential changes', async () => {
    const user = userEvent.setup();
    const oldRegions = deferred<Array<{ regionId: string; regionName: string }>>();
    const latestRegions = deferred<Array<{ regionId: string; regionName: string }>>();
    vi.mocked(cloudCredentialApi.listCloudCredentials).mockResolvedValue([
      {
        id: 'cred-old',
        name: 'old-account',
        vendor: 'ALIYUN',
        accessKey: 'LTAI-old',
        createdAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'cred-latest',
        name: 'latest-account',
        vendor: 'ALIYUN',
        accessKey: 'LTAI-latest',
        createdAt: '2026-01-01T00:00:00Z',
      },
    ]);
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
    await waitFor(() =>
      expect(aliyunCatalogApi.listAliyunRegions).toHaveBeenCalledWith('cred-old'),
    );

    fireEvent.mouseDown(credentialSelect.parentElement!);
    await user.click(
      await screen.findByText(/latest-account/, { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() =>
      expect(aliyunCatalogApi.listAliyunRegions).toHaveBeenCalledWith('cred-latest'),
    );

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
    vi.mocked(cloudCredentialApi.listCloudCredentials).mockResolvedValue([
      {
        id: 'cred-1',
        name: 'cloud-account',
        vendor: 'ALIYUN',
        accessKey: 'LTAI-one',
        createdAt: '2026-01-01T00:00:00Z',
      },
    ]);
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
    await waitFor(() => expect(aliyunCatalogApi.listAliyunRegions).toHaveBeenCalledWith('cred-1'));

    fireEvent.mouseDown(selects[1].parentElement!);
    await user.click(
      await screen.findByText(/Beijing/, { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() =>
      expect(aliyunCatalogApi.listAliyunInstances).toHaveBeenCalledWith('cred-1', 'cn-beijing'),
    );

    fireEvent.mouseDown(selects[1].parentElement!);
    await user.click(
      await screen.findByText(/Shanghai/, { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() =>
      expect(aliyunCatalogApi.listAliyunInstances).toHaveBeenCalledWith('cred-1', 'cn-shanghai'),
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
    vi.mocked(cloudCredentialApi.listCloudCredentials).mockResolvedValue([
      {
        id: 'cred-aliyun',
        name: 'aliyun-account',
        vendor: 'ALIYUN',
        accessKey: 'LTAI-one',
        createdAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'cred-tencent',
        name: 'tencent-account',
        vendor: 'TENCENT',
        accessKey: 'AKID-one',
        createdAt: '2026-01-01T00:00:00Z',
      },
    ]);
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
    await waitFor(() =>
      expect(aliyunCatalogApi.listAliyunRegions).toHaveBeenCalledWith('cred-aliyun'),
    );
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
    vi.mocked(cloudCredentialApi.listCloudCredentials).mockResolvedValue([
      {
        id: 'cred-aliyun',
        name: 'aliyun-account',
        vendor: 'ALIYUN',
        accessKey: 'LTAI-one',
        createdAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'cred-tencent',
        name: 'tencent-account',
        vendor: 'TENCENT',
        accessKey: 'AKID-one',
        createdAt: '2026-01-01T00:00:00Z',
      },
    ]);
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
      expect(aliyunCatalogApi.listAliyunInstances).toHaveBeenCalledWith(
        'cred-aliyun',
        'cn-beijing',
      ),
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
      instance('no-remark', 'instance-without-remark', 'PROXY', null),
      instance('with-remark', 'instance-with-remark', 'PROXY', 'production'),
    ]);
    renderPage();

    const name = await screen.findByText('instance-without-remark');
    expect(within(name.closest('tr')!).getByText('-')).toBeInTheDocument();

    await user.click(screen.getByRole('columnheader', { name: /备注/ }));
    expect(screen.getByText('instance-without-remark')).toBeInTheDocument();
    expect(screen.getByText('instance-with-remark')).toBeInTheDocument();
  });
});
