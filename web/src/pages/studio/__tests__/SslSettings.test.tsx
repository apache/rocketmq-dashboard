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

import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import SslSettings from '../SslSettings';

// Mock matchMedia for antd responsive components
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

// Mock react-router-dom
vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  useParams: () => ({}),
}));

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );
};

describe('SslSettings Page', () => {
  it('should render the page title', () => {
    renderWithProviders(<SslSettings />);
    expect(screen.getByText('SSL/TLS 设置')).toBeInTheDocument();
  });

  it('should render the info alert', () => {
    renderWithProviders(<SslSettings />);
    expect(screen.getByText('SSL/TLS 配置')).toBeInTheDocument();
  });

  it('should render the SSL enable switch label', () => {
    renderWithProviders(<SslSettings />);
    expect(screen.getByText('启用 SSL/TLS')).toBeInTheDocument();
  });

  it('should not show SSL config fields when SSL is disabled', () => {
    renderWithProviders(<SslSettings />);
    expect(screen.queryByText('KeyStore 配置')).not.toBeInTheDocument();
  });

  it('should show SSL config fields after toggling SSL switch', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SslSettings />);
    const switchEl = screen.getByRole('switch');
    await user.click(switchEl);
    // After toggling, SSL config fields should appear (may appear multiple times in labels + options)
    const protocolLabels = screen.getAllByText('SSL 协议');
    expect(protocolLabels.length).toBeGreaterThan(0);
    expect(screen.getByText('客户端认证')).toBeInTheDocument();
    expect(screen.getByText('KeyStore 配置')).toBeInTheDocument();
  });

  it('should show KeyStore fields after enabling SSL', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SslSettings />);
    const switchEl = screen.getByRole('switch');
    await user.click(switchEl);
    expect(screen.getByText('KeyStore 类型')).toBeInTheDocument();
    expect(screen.getByText('KeyStore 路径')).toBeInTheDocument();
    expect(screen.getByText('KeyStore 密码')).toBeInTheDocument();
  });
});
