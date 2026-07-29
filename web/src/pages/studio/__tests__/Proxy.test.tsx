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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { queryProxyHomePage } from '../../../api/proxy';
import { LangProvider } from '../../../i18n/LangContext';
import ProxyPage from '../Proxy';

vi.mock('../../../api/proxy', () => ({
  addProxyAddr: vi.fn(),
  queryProxyHomePage: vi.fn(),
  removeProxyAddr: vi.fn(),
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

describe('ProxyPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(queryProxyHomePage).mockResolvedValue(proxyHome);
  });

  it('shows success after the proxy list refreshes', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('127.0.0.1:8081');

    await user.click(screen.getByRole('button', { name: '刷新' }));

    expect(await screen.findByText('刷新成功')).toBeInTheDocument();
    await waitFor(() => expect(queryProxyHomePage).toHaveBeenCalledTimes(2));
  });

  it('does not show success when the proxy list refresh fails', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('127.0.0.1:8081');
    vi.mocked(queryProxyHomePage).mockRejectedValueOnce(new Error('network error'));

    await user.click(screen.getByRole('button', { name: '刷新' }));

    expect(await screen.findByText('获取代理列表失败')).toBeInTheDocument();
    expect(screen.queryByText('刷新成功')).not.toBeInTheDocument();
    await waitFor(() => expect(queryProxyHomePage).toHaveBeenCalledTimes(2));
  });
});
