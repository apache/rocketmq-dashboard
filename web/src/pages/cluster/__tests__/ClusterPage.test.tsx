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

import { App } from 'antd';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import ClusterPage from '../index';

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

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );

describe('Cluster page', () => {
  it('opens proxy detail dialog from the proxy table', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ClusterPage />);

    await user.click(screen.getByRole('tab', { name: /Proxy 管理/ }));
    const proxyRow = screen.getByRole('row', { name: /10\.101\.2\.21:8081/ });
    await user.click(within(proxyRow).getByRole('button', { name: /详情/ }));

    const dialog = await screen.findByRole('dialog', { name: /Proxy 详情 - 10\.101\.2\.21:8081/ });
    expect(within(dialog).getByText('rocketmq-prod')).toBeInTheDocument();
    expect(within(dialog).getByText('ns-prod')).toBeInTheDocument();
    expect(within(dialog).getAllByText('10.101.2.21:8081')).toHaveLength(1);
    expect(within(dialog).getByText('1,842')).toBeInTheDocument();
    expect(within(dialog).getByText('8081')).toBeInTheDocument();
    expect(within(dialog).getByText('8080')).toBeInTheDocument();
  });
});
