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
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import type { CloudCredentialPage } from '../../../api/cloudCredential';
import {
  createCloudCredential,
  deleteCloudCredential,
  listCloudCredentials,
  updateCloudCredential,
} from '../../../api/cloudCredential';
import { LangProvider } from '../../../i18n/LangContext';
import { CloudCredentialTab } from '../CloudCredentialTab';

vi.mock('../../../api/cloudCredential', () => ({
  createCloudCredential: vi.fn(),
  deleteCloudCredential: vi.fn(),
  listCloudCredentials: vi.fn(),
  updateCloudCredential: vi.fn(),
}));

const credentials: CloudCredentialPage = {
  items: [
    {
      id: 1,
      name: 'aliyun-test',
      vendor: 'ALIYUN',
      accessKey: 'LTAI****0001',
      remark: '测试账号',
      gmtCreate: '2026-08-18T10:00:00',
    },
  ],
  total: 1,
  page: 1,
  size: 20,
};

const renderTab = () =>
  render(
    <App>
      <LangProvider>
        <CloudCredentialTab />
      </LangProvider>
    </App>,
  );

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

describe('CloudCredentialTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listCloudCredentials).mockResolvedValue(credentials);
  });

  it('renders masked credentials from the backend', async () => {
    renderTab();

    await waitFor(() => expect(screen.getByText('aliyun-test')).toBeInTheDocument());
    expect(screen.getByText('LTAI****0001')).toBeInTheDocument();
    expect(screen.getByText('阿里云')).toBeInTheDocument();
  });

  it('creates a credential from the modal form', async () => {
    vi.mocked(createCloudCredential).mockResolvedValue({
      id: 2,
      name: 'tencent-prod',
      vendor: 'TENCENT',
      accessKey: 'AKID****9999',
      gmtCreate: '2026-08-18T11:00:00',
    });
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderTab();

    await waitFor(() => expect(screen.getByText('aliyun-test')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /添加云凭据/ }));

    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByPlaceholderText(/请输入凭据名称|例如/), 'tencent-prod');
    // fill name field explicitly (placeholder shared with example text)
    await user.click(within(dialog).getByLabelText('云厂商'));
    await user.click(await screen.findByText('腾讯云'));
    await user.type(screen.getByPlaceholderText('LTAI...'), 'AKID000000009999');
    await user.type(screen.getByPlaceholderText('请输入 SecretKey'), 'secret-9999');
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));

    await waitFor(() =>
      expect(createCloudCredential).toHaveBeenCalledWith({
        name: 'tencent-prod',
        vendor: 'TENCENT',
        accessKey: 'AKID000000009999',
        secretKey: 'secret-9999',
        remark: undefined,
      }),
    );
    await waitFor(() => expect(screen.getByText('tencent-prod')).toBeInTheDocument());
  });

  it('updates name and remark while keeping the secret unchanged when blank', async () => {
    vi.mocked(updateCloudCredential).mockResolvedValue({
      ...credentials.items[0],
      name: 'aliyun-renamed',
      remark: '新备注',
    });
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderTab();

    await waitFor(() => expect(screen.getByText('aliyun-test')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /编辑/ }));

    const dialog = await screen.findByRole('dialog');
    const nameInput = within(dialog).getByDisplayValue('aliyun-test');
    await user.clear(nameInput);
    await user.type(nameInput, 'aliyun-renamed');
    const remarkInput = within(dialog).getByDisplayValue('测试账号');
    await user.clear(remarkInput);
    await user.type(remarkInput, '新备注');
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));

    await waitFor(() =>
      expect(updateCloudCredential).toHaveBeenCalledWith({
        id: 1,
        name: 'aliyun-renamed',
        secretKey: undefined,
        remark: '新备注',
      }),
    );
    await waitFor(() => expect(screen.getByText('aliyun-renamed')).toBeInTheDocument());
  });

  it('deletes a credential after confirmation', async () => {
    vi.mocked(deleteCloudCredential).mockResolvedValue(undefined);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderTab();

    await waitFor(() => expect(screen.getByText('aliyun-test')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /删除/ }));
    await user.click(await screen.findByRole('button', { name: /确\s*定/ }));

    await waitFor(() => expect(deleteCloudCredential).toHaveBeenCalledWith(1));
    await waitFor(() => expect(screen.queryByText('aliyun-test')).not.toBeInTheDocument());
  });
});
