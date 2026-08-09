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
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import * as aclService from '../../../services/aclService';
import AclPage from '../acl';

vi.mock('../../../services/aclService', () => ({
  createAclRule: vi.fn(),
  createAclUser: vi.fn(),
  deleteAclRule: vi.fn(),
  deleteAclUser: vi.fn(),
  listAclRules: vi.fn(),
  listAclUsers: vi.fn(),
  updateAclRule: vi.fn(),
  updateAclUser: vi.fn(),
}));
vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([]),
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

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </LangProvider>
    </App>,
  );

describe('ACL page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(aclService.listAclRules).mockResolvedValue([
      {
        id: 'rule-remote',
        principal: 'remote-user',
        resource: 'remote-topic',
        resourceType: 'Topic',
        resourcePattern: 'LITERAL',
        actions: ['PUB'],
        decision: 'ALLOW',
        scope: 'cluster',
        aclVersion: 2,
        createdAt: '2026-07-23T00:00:00Z',
      },
    ]);
    vi.mocked(aclService.listAclUsers).mockResolvedValue([
      {
        id: 'user-remote',
        username: 'remote-admin',
        accessKey: 'acce****3456',
        secretKey: 'secr****7654',
        admin: true,
        clusters: ['cluster-a'],
        createdAt: '2026-07-23T00:00:00Z',
      },
    ]);
  });

  it('loads ACL rules and users through the service layer', async () => {
    renderWithProviders(<AclPage />);

    expect(await screen.findByText('remote-user')).toBeInTheDocument();
    expect(screen.getByText('remote-topic')).toBeInTheDocument();
    expect(aclService.listAclRules).toHaveBeenCalledTimes(1);
    expect(aclService.listAclUsers).toHaveBeenCalledTimes(1);
  });

  it('explains that ACL records are Studio-local metadata', async () => {
    renderWithProviders(<AclPage />);

    expect(await screen.findByTestId('acl-local-metadata-notice')).toHaveTextContent(
      '当前 ACL 规则和用户仅保存为 Studio 本地元数据',
    );
  });

  it('renders backend users on the user tab', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('用户管理'));

    expect(await screen.findByText('remote-admin')).toBeInTheDocument();
    expect(screen.getByText('cluster-a')).toBeInTheDocument();
  });

  it('does not submit masked credentials when editing a user', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.updateAclUser).mockResolvedValue({
      id: 'user-remote',
      username: 'remote-admin',
      accessKey: 'acce****3456',
      secretKey: 'secr****7654',
      admin: true,
      clusters: ['cluster-a'],
      createdAt: '2026-07-23T00:00:00Z',
    });
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('用户管理'));
    expect(await screen.findByText('remote-admin')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /编辑/ }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).queryByText('Access Key')).not.toBeInTheDocument();
    expect(within(dialog).queryByText('Secret Key')).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => expect(aclService.updateAclUser).toHaveBeenCalledTimes(1));
    const payload = vi.mocked(aclService.updateAclUser).mock.calls[0][0];
    expect(payload).toEqual({
      id: 'user-remote',
      username: 'remote-admin',
      admin: true,
      clusters: ['cluster-a'],
    });
    expect(payload).not.toHaveProperty('accessKey');
    expect(payload).not.toHaveProperty('secretKey');
  });

  it('does not submit masked credentials when toggling admin', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.updateAclUser).mockResolvedValue({
      id: 'user-remote',
      username: 'remote-admin',
      accessKey: 'acce****3456',
      secretKey: 'secr****7654',
      admin: false,
      clusters: ['cluster-a'],
      createdAt: '2026-07-23T00:00:00Z',
    });
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('用户管理'));
    expect(await screen.findByText('remote-admin')).toBeInTheDocument();
    await user.click(screen.getByRole('switch'));

    await waitFor(() => expect(aclService.updateAclUser).toHaveBeenCalledTimes(1));
    const payload = vi.mocked(aclService.updateAclUser).mock.calls[0][0];
    expect(payload).toEqual({
      id: 'user-remote',
      username: 'remote-admin',
      admin: false,
      clusters: ['cluster-a'],
    });
    expect(payload).not.toHaveProperty('accessKey');
    expect(payload).not.toHaveProperty('secretKey');
  });

  it('creates a user with the selected cluster scope', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.createAclUser).mockResolvedValue({
      id: 'user-created',
      username: 'orders-service',
      accessKey: 'acce****3456',
      secretKey: 'secr****7654',
      admin: false,
      clusters: ['cluster-a', 'cluster-b'],
      createdAt: '2026-08-01T00:00:00Z',
    });
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('用户管理'));
    const userPanel = screen.getByRole('tabpanel', { name: '用户管理' });
    await user.click(within(userPanel).getByRole('button', { name: /添加用户/ }));
    const dialog = await screen.findByRole('dialog');

    await user.type(
      within(dialog).getByPlaceholderText('例：user-order-service'),
      'orders-service',
    );
    const clusterInput = within(dialog).getByRole('combobox');
    await user.type(clusterInput, 'cluster-a,cluster-b,');
    await user.click(within(dialog).getByRole('button', { name: /添\s*加/ }));

    await waitFor(() => expect(aclService.createAclUser).toHaveBeenCalledTimes(1));
    expect(aclService.createAclUser).toHaveBeenCalledWith({
      username: 'orders-service',
      admin: false,
      clusters: ['cluster-a', 'cluster-b'],
    });
  });

  it('replaces the cluster scope of an existing user', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.updateAclUser).mockResolvedValue({
      id: 'user-remote',
      username: 'remote-admin',
      accessKey: 'acce****3456',
      secretKey: 'secr****7654',
      admin: true,
      clusters: ['cluster-b'],
      createdAt: '2026-07-23T00:00:00Z',
    });
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('用户管理'));
    await user.click(screen.getByRole('button', { name: /编辑/ }));
    const dialog = await screen.findByRole('dialog');

    const clusterInput = within(dialog).getByRole('combobox');
    await user.click(clusterInput);
    await user.keyboard('{Backspace}');
    await user.type(clusterInput, 'cluster-b{enter}');
    await user.click(within(dialog).getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => expect(aclService.updateAclUser).toHaveBeenCalledTimes(1));
    expect(aclService.updateAclUser).toHaveBeenCalledWith({
      id: 'user-remote',
      username: 'remote-admin',
      admin: true,
      clusters: ['cluster-b'],
    });
  });
});
