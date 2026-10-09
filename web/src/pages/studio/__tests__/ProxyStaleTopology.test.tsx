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
import {
  getProxyTopology,
  queryProxyHomePage,
  reloadProxyConfig,
  removeProxyAddress,
} from '../../../api/proxy';
import { LangProvider } from '../../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../../i18n/languagePreference';
import ProxyPage from '../Proxy';

vi.mock('../../../api/proxy', () => ({
  addProxyAddress: vi.fn(),
  getProxyTopology: vi.fn(),
  queryProxyHomePage: vi.fn(),
  reloadProxyConfig: vi.fn(),
  removeProxyAddress: vi.fn(),
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

const createDeferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
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

describe('ProxyPage stale health probe', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    vi.mocked(reloadProxyConfig).mockResolvedValue({ success: true });
    vi.mocked(removeProxyAddress).mockResolvedValue({
      proxyAddrList: [],
      currentProxyAddr: '',
    });
  });

  it('keeps the refreshed node list when an older health probe fails', async () => {
    const firstHome = createDeferred<{ proxyAddrList: string[]; currentProxyAddr: string }>();
    const secondHome = createDeferred<{ proxyAddrList: string[]; currentProxyAddr: string }>();
    const firstTopology = createDeferred<unknown>();

    vi.mocked(queryProxyHomePage)
      .mockReturnValueOnce(firstHome.promise)
      .mockReturnValueOnce(secondHome.promise);
    vi.mocked(getProxyTopology)
      .mockReturnValueOnce(firstTopology.promise)
      .mockResolvedValueOnce([]);

    renderPage();

    // The first load resolves its home data and parks on the health probe.
    await act(async () => {
      firstHome.resolve({ proxyAddrList: ['10.0.0.1:8080'], currentProxyAddr: '10.0.0.1:8080' });
    });

    // A refresh supersedes the first load and completes with a different node.
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Refresh' }));
    await act(async () => {
      secondHome.resolve({ proxyAddrList: ['10.0.0.2:8080'], currentProxyAddr: '10.0.0.2:8080' });
    });

    expect(await screen.findByText('10.0.0.2:8080')).toBeInTheDocument();

    // The superseded load's health probe now fails; its stale node list must
    // not overwrite the newer refresh.
    await act(async () => {
      firstTopology.reject(new Error('probe failed'));
    });

    await waitFor(() => {
      expect(screen.getByText('10.0.0.2:8080')).toBeInTheDocument();
    });
    expect(screen.queryByText('10.0.0.1:8080')).not.toBeInTheDocument();
  });
});
