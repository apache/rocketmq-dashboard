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

import type { CloudCredential } from '../../../api/cloudCredential';
import {
  createCloudCredential,
  deleteCloudCredential,
  listCloudCredentials,
  updateCloudCredential,
} from '../../../services/cloudCredentialService';
import { CloudCredentialTab } from '../index';

vi.mock('../../../services/cloudCredentialService', () => ({
  createCloudCredential: vi.fn(),
  deleteCloudCredential: vi.fn(),
  listCloudCredentials: vi.fn(),
  updateCloudCredential: vi.fn(),
}));

const credentials: CloudCredential[] = [
  {
    id: 'cred-1',
    name: 'aliyun-prod',
    vendor: 'ALIYUN',
    accessKey: 'LTAI****0001',
    remark: 'production account',
    createdAt: '2026-08-06T00:00:00Z',
    updatedAt: '2026-08-07T00:00:00Z',
  },
];

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

  it('lists masked cloud credentials', async () => {
    render(
      <App>
        <CloudCredentialTab />
      </App>,
    );

    expect(await screen.findByText('aliyun-prod')).toBeInTheDocument();
    expect(screen.getByText('LTAI****0001')).toBeInTheDocument();
    expect(screen.getByText('Alibaba Cloud RocketMQ')).toBeInTheDocument();
    expect(screen.queryByText('plain-secret')).not.toBeInTheDocument();
  });

  it('creates a cloud credential from the settings tab', async () => {
    vi.mocked(createCloudCredential).mockResolvedValue({
      id: 'cred-2',
      name: 'aliyun-dr',
      vendor: 'ALIYUN',
      accessKey: 'LTAI****0002',
      remark: 'dr account',
      createdAt: '2026-08-08T00:00:00Z',
    });

    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <CloudCredentialTab />
      </App>,
    );

    await screen.findByText('aliyun-prod');
    await user.click(screen.getByRole('button', { name: /添加云凭据/ }));
    await user.type(screen.getByLabelText('名称'), 'aliyun-dr');
    await user.type(screen.getByLabelText('AccessKey'), 'LTAI5tUnitTestKey000000002');
    await user.type(screen.getByLabelText('Secret Key'), 'secret-2');
    await user.type(screen.getByLabelText('备注'), 'dr account');
    await user.click(screen.getByRole('button', { name: 'OK' }));

    await waitFor(() => {
      expect(createCloudCredential).toHaveBeenCalledWith({
        name: 'aliyun-dr',
        vendor: 'ALIYUN',
        accessKey: 'LTAI5tUnitTestKey000000002',
        secretKey: 'secret-2',
        remark: 'dr account',
      });
    });
    expect(await screen.findByText('aliyun-dr')).toBeInTheDocument();
  });

  it('updates metadata without overwriting an existing secret when left blank', async () => {
    vi.mocked(updateCloudCredential).mockResolvedValue({
      ...credentials[0],
      name: 'aliyun-renamed',
      remark: '',
      updatedAt: '2026-08-08T00:00:00Z',
    });

    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <CloudCredentialTab />
      </App>,
    );

    await screen.findByText('aliyun-prod');
    await user.click(screen.getByRole('button', { name: /编辑/ }));
    await user.clear(screen.getByLabelText('名称'));
    await user.type(screen.getByLabelText('名称'), 'aliyun-renamed');
    await user.clear(screen.getByLabelText('备注'));
    await user.click(screen.getByRole('button', { name: 'OK' }));

    await waitFor(() => {
      expect(updateCloudCredential).toHaveBeenCalledWith({
        id: 'cred-1',
        name: 'aliyun-renamed',
        remark: '',
      });
    });
    expect(updateCloudCredential).not.toHaveBeenCalledWith(
      expect.objectContaining({ secretKey: expect.any(String) }),
    );
    expect(await screen.findByText('aliyun-renamed')).toBeInTheDocument();
  });

  it('confirms before deleting a cloud credential', async () => {
    vi.mocked(deleteCloudCredential).mockResolvedValue(undefined);

    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <CloudCredentialTab />
      </App>,
    );

    await screen.findByText('aliyun-prod');
    await user.click(screen.getByRole('button', { name: /删除/ }));

    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: /删\s*除/ }));

    await waitFor(() => {
      expect(deleteCloudCredential).toHaveBeenCalledWith('cred-1');
    });
    await waitFor(() => {
      expect(screen.queryByText('aliyun-prod')).not.toBeInTheDocument();
    });
  });
});
