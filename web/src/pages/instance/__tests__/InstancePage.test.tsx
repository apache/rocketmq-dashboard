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
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import type { Instance } from '../../../api/instance';
import { LangProvider } from '../../../i18n/LangContext';
import * as instanceService from '../../../services/instanceService';
import InstancePage from '../index';

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

const instance = (id: string, name: string, type: Instance['type'] = 'PROXY'): Instance => ({
  id,
  name,
  remark: '',
  type,
  endpoint: `${name}:8080`,
  topicCount: 1,
  consumerGroupCount: 1,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
});

const LocationProbe = () => {
  const location = useLocation();

  return <span data-testid="location-probe">{`${location.pathname}${location.search}`}</span>;
};

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/instance']}>
      <App>
        <LangProvider>
          <LocationProbe />
          <Routes>
            <Route path="/instance" element={<InstancePage />} />
            <Route path="/instance/topic" element={<div>Topic route</div>} />
          </Routes>
        </LangProvider>
      </App>
    </MemoryRouter>,
  );

describe('InstancePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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

    fireEvent.change(screen.getByPlaceholderText('搜索实例名称或地址'), {
      target: { value: 'proxy-hz' },
    });
    await waitFor(
      () => expect(instanceService.listInstances).toHaveBeenCalledWith({ search: 'proxy-hz' }),
      { timeout: 1000 },
    );

    fireEvent.change(screen.getByPlaceholderText('搜索实例名称或地址'), {
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
    const searchInput = screen.getByPlaceholderText('搜索实例名称或地址');
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
    await user.type(within(dialog).getByLabelText('实例名称'), 'new-proxy');
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

  it('navigates to the topic view with the selected instance id when a row is clicked', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByText('production-proxy'));

    expect(await screen.findByText('Topic route')).toBeInTheDocument();
    expect(await screen.findByTestId('location-probe')).toHaveTextContent(
      '/instance/topic?instanceId=proxy-1',
    );
  });

  it('does not navigate when clicking row action buttons', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('production-proxy')).toBeInTheDocument();
    await user.click(screen.getAllByRole('button', { name: /编辑/ })[0]);

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(screen.getByTestId('location-probe')).toHaveTextContent('/instance');
    expect(screen.queryByText('Topic route')).not.toBeInTheDocument();
  });
});
