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

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { downloadBlob } from '../../../utils/download';
import { AboutTab } from '../AboutTab';

vi.mock('../../../services/dataMode', () => ({
  isMockMode: vi.fn(() => false),
}));

vi.mock('../../../utils/download', async () => {
  const actual =
    await vi.importActual<typeof import('../../../utils/download')>('../../../utils/download');
  return {
    ...actual,
    downloadBlob: vi.fn(),
  };
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

describe('AboutTab', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
    localStorage.clear();
    window.history.pushState({}, '', '/settings');
    if (!window.navigator.clipboard?.writeText) {
      Object.defineProperty(window.navigator, 'clipboard', {
        configurable: true,
        writable: true,
        value: {
          writeText: vi.fn().mockResolvedValue(undefined),
        },
      });
    }
  });

  it('renders the injected build metadata and build-year copyright', () => {
    render(<AboutTab />);

    expect(screen.getByText(__BUILD_COMMIT__)).toBeInTheDocument();
    expect(screen.getByText(__BUILD_TIME__)).toBeInTheDocument();
    expect(
      screen.getByText(
        `Copyright © ${__BUILD_TIME__.slice(0, 4)} Apache Software Foundation. Licensed under the Apache License, Version 2.0.`,
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText('2024-01-15 14:30:00')).not.toBeInTheDocument();
  });

  it('renders support bundle summary metadata', () => {
    localStorage.setItem('rocketmq-studio-language', 'en');
    localStorage.setItem('rocketmq-studio-theme', 'dark');
    localStorage.setItem('rocketmq-studio-compact', 'true');

    render(<AboutTab />);

    expect(screen.getByText('支持信息包')).toBeInTheDocument();
    expect(screen.getByText('API 前缀')).toBeInTheDocument();
    expect(screen.getByText('/api')).toBeInTheDocument();
    expect(screen.getByText('当前路径')).toBeInTheDocument();
    expect(screen.getByText('/settings')).toBeInTheDocument();
    expect(screen.getByText('界面偏好')).toBeInTheDocument();
    expect(screen.getByText('en / dark / compact=yes')).toBeInTheDocument();
  });

  it('copies the support bundle JSON snapshot', async () => {
    localStorage.setItem('token', 'legacy-token');
    localStorage.setItem('rocketmq-studio-secret', 'secret-value');
    localStorage.setItem('password', 'password-value');
    localStorage.setItem('rocketmq-studio-language', 'zh');
    window.history.pushState({}, '', '/settings?token=abc#unsafe');
    const user = userEvent.setup();

    render(
      <App>
        <AboutTab />
      </App>,
    );

    await user.click(screen.getByRole('button', { name: /复制 JSON/ }));

    expect(await screen.findByText('支持信息包已复制')).toBeInTheDocument();
  });

  it('downloads the support bundle as JSON', async () => {
    localStorage.setItem('token', 'legacy-token');
    localStorage.setItem('rocketmq-studio-secret', 'secret-value');
    localStorage.setItem('password', 'password-value');
    window.history.pushState({}, '', '/settings?token=abc#unsafe');
    const user = userEvent.setup();
    render(
      <App>
        <AboutTab />
      </App>,
    );

    await user.click(screen.getByRole('button', { name: /下载 JSON/ }));

    await waitFor(() => expect(downloadBlob).toHaveBeenCalledTimes(1));
    const [blob, filename] = vi.mocked(downloadBlob).mock.calls[0];
    expect(filename).toMatch(/^rocketmq-studio-support-.*\.json$/);
    expect(blob.type).toBe('application/json;charset=utf-8');
    const text = await blob.text();
    const bundle = JSON.parse(text);
    expect(bundle.product.buildCommit).toBe(__BUILD_COMMIT__);
    expect(bundle.runtime.pathname).toBe('/settings');
    expect(bundle.runtime.hasQueryString).toBe(true);
    expect(bundle.redaction.unsafeLocalStorageKeysOmitted).toBe(3);
    expect(text).not.toContain('legacy-token');
    expect(text).not.toContain('secret-value');
    expect(text).not.toContain('password-value');
    expect(text).not.toContain('token=abc');
    expect(text).not.toContain('#unsafe');
  });
});
