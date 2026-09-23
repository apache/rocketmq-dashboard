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
 */ import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import CloudCredentialUsageDrawer from '../CloudCredentialUsageDrawer';

const api = vi.hoisted(() => ({ listCloudCredentials: vi.fn(), listInstances: vi.fn() }));
vi.mock('../../../api/cloudCredential', () => ({ listCloudCredentials: api.listCloudCredentials }));
vi.mock('../../../api/instance', () => ({ listInstances: api.listInstances }));
beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((media) => ({
      matches: false,
      media,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
});
const rows = Array.from({ length: 100 }, (_, id) => ({
  id,
  name: `credential-${id}`,
  vendor: 'ALIYUN',
  gmtCreate: '',
}));
const renderDrawer = () =>
  render(
    <App>
      <LangProvider>
        <CloudCredentialUsageDrawer open onClose={vi.fn()} />
      </LangProvider>
    </App>,
  );
describe('凭据清单加载反馈', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.listCloudCredentials.mockReset();
    api.listInstances.mockResolvedValue([]);
  });
  it('逐页显示已读取记录数量', async () => {
    let finish!: (value: unknown) => void;
    api.listCloudCredentials
      .mockResolvedValueOnce({ items: rows, total: 200 })
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finish = resolve;
          }),
      );
    renderDrawer();
    fireEvent.click(screen.getAllByRole('button', { name: /加载清单/ })[0]);
    expect(await screen.findByText('正在加载凭据：已读取 100 / 200 条')).toBeInTheDocument();
    await act(async () =>
      finish({ items: rows.map((row) => ({ ...row, id: row.id + 100 })), total: 200 }),
    );
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());
    expect(api.listCloudCredentials).toHaveBeenCalledTimes(2);
  });
  it('超出分析上限时展示可理解的提示并停止翻页', async () => {
    api.listCloudCredentials.mockResolvedValue({ items: rows, total: 10001 });
    renderDrawer();
    fireEvent.click(screen.getAllByRole('button', { name: /加载清单/ })[0]);
    expect(
      (await screen.findAllByText(/凭据数量超过单次分析的 10,000 条上限/)).length,
    ).toBeGreaterThan(0);
    expect(api.listCloudCredentials).toHaveBeenCalledTimes(1);
  });
  it('关闭后不再继续请求剩余页面', async () => {
    let finish!: (value: unknown) => void;
    api.listCloudCredentials.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const view = renderDrawer();
    fireEvent.click(screen.getAllByRole('button', { name: /加载清单/ })[0]);
    view.unmount();
    await act(async () => finish({ items: rows, total: 200 }));
    expect(api.listCloudCredentials).toHaveBeenCalledTimes(1);
  });
});
