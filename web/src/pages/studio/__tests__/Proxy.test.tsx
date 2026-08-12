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
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { queryProxyHomePage, reloadProxyConfig } from '../../../api/proxy';
import { listInstances } from '../../../services/instanceService';
import { LangProvider } from '../../../i18n/LangContext';
import ProxyPage from '../Proxy';

vi.mock('../../../api/proxy', () => ({
  queryProxyHomePage: vi.fn(),
  reloadProxyConfig: vi.fn(),
}));

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn(),
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

const proxyHome = {
  proxyAddrList: ['127.0.0.1:8081'],
  currentProxyAddr: '127.0.0.1:8081',
};

function renderPage() {
  return render(
    <App>
      <LangProvider>
        <ProxyPage />
      </LangProvider>
    </App>,
  );
}

const createDeferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
};

describe('ProxyPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listInstances).mockResolvedValue([
      {
        id: 'proxy-a',
        name: 'Proxy A',
        remark: null,
        type: 'PROXY',
        endpoint: '127.0.0.1:8081',
        topicCount: 0,
        consumerGroupCount: 0,
        createdAt: '',
        updatedAt: '',
      },
    ]);
    vi.mocked(queryProxyHomePage).mockResolvedValue(proxyHome);
    vi.mocked(reloadProxyConfig).mockResolvedValue({
      success: true,
    });
  });

  it('loads Proxy nodes once after the page mounts', async () => {
    renderPage();

    await screen.findByText('127.0.0.1:8081');
    expect(queryProxyHomePage).toHaveBeenCalledWith('proxy-a');
    expect(queryProxyHomePage).toHaveBeenCalledTimes(1);
  });

  it('shows success after the proxy list refreshes', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findAllByText('127.0.0.1:8081');

    await user.click(screen.getByRole('button', { name: '刷新' }));

    expect(await screen.findByText('刷新成功')).toBeInTheDocument();
    await waitFor(() => expect(queryProxyHomePage).toHaveBeenCalledTimes(2));
  });

  it('does not show success when the proxy list refresh fails', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findAllByText('127.0.0.1:8081');
    vi.mocked(queryProxyHomePage).mockRejectedValueOnce(new Error('network error'));

    await user.click(screen.getByRole('button', { name: '刷新' }));

    expect(await screen.findByText('获取代理列表失败')).toBeInTheDocument();
    expect(screen.queryByText('刷新成功')).not.toBeInTheDocument();
    await waitFor(() => expect(queryProxyHomePage).toHaveBeenCalledTimes(2));
  });

  it('does not render simulated proxy configuration values', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findAllByText('127.0.0.1:8081');

    await user.click(screen.getByRole('button', { name: '查看配置' }));

    expect(await screen.findByText('配置接口未接入')).toBeInTheDocument();
    expect(
      screen.getByText('当前版本尚未接入真实 Proxy 配置查询接口，已停止展示模拟配置。'),
    ).toBeInTheDocument();
    expect(screen.queryByText('proxy.maxConnections')).not.toBeInTheDocument();
    expect(screen.queryByText('rocketmq.namesrv.addr')).not.toBeInTheDocument();
  });

  it('marks runtime metrics unavailable when proxy API only returns addresses', async () => {
    renderPage();
    await screen.findAllByText('127.0.0.1:8081');

    expect(screen.queryByText('5.3.0')).not.toBeInTheDocument();
    expect(screen.getAllByText('N/A').length).toBeGreaterThanOrEqual(5);
  });
  it('calls reloadProxyConfig when the reload button is clicked', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findAllByText('127.0.0.1:8081');

    await user.click(screen.getByRole('button', { name: '重载配置' }));

    await waitFor(() => expect(reloadProxyConfig).toHaveBeenCalledWith('127.0.0.1:8081'));
    expect(await screen.findByText('配置重载成功')).toBeInTheDocument();
  });

  it('keeps the latest Proxy list when an older refresh resolves last', async () => {
    const older = createDeferred<typeof proxyHome>();
    const latest = createDeferred<typeof proxyHome>();
    vi.mocked(queryProxyHomePage)
      .mockResolvedValueOnce(proxyHome)
      .mockReturnValueOnce(older.promise)
      .mockReturnValueOnce(latest.promise);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('127.0.0.1:8081');

    const refresh = screen.getByRole('button', { name: '刷新' });
    await user.click(refresh);
    await user.click(refresh);
    await act(async () =>
      latest.resolve({
        proxyAddrList: ['127.0.0.2:8081'],
        currentProxyAddr: '127.0.0.2:8081',
      }),
    );
    expect(await screen.findByText('127.0.0.2:8081')).toBeInTheDocument();

    await act(async () => older.resolve(proxyHome));
    expect(screen.getByText('127.0.0.2:8081')).toBeInTheDocument();
    expect(screen.queryByText('127.0.0.1:8081')).not.toBeInTheDocument();
  });

  it('loads the selected Proxy instance endpoint instead of a process-global address', async () => {
    vi.mocked(listInstances).mockResolvedValue([
      {
        id: 'proxy-a',
        name: 'Proxy A',
        remark: null,
        type: 'PROXY',
        endpoint: '127.0.0.1:8081',
        topicCount: 0,
        consumerGroupCount: 0,
        createdAt: '',
        updatedAt: '',
      },
      {
        id: 'proxy-b',
        name: 'Proxy B',
        remark: null,
        type: 'PROXY',
        endpoint: '127.0.0.2:8081',
        topicCount: 0,
        consumerGroupCount: 0,
        createdAt: '',
        updatedAt: '',
      },
    ]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('127.0.0.1:8081');

    await user.click(screen.getByRole('combobox', { name: 'Proxy instance' }));
    await user.click(await screen.findByText('Proxy B'));

    await waitFor(() => expect(queryProxyHomePage).toHaveBeenLastCalledWith('proxy-b'));
  });
});
