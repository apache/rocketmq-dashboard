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
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
  createAndUpdatePlainAccessConfig: vi.fn(),
  deleteAclRule: vi.fn(),
  deleteAclUser: vi.fn(),
  examineBrokerClusterAclConfig: vi.fn(),
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

  it('keeps rules available when loading users fails', async () => {
    vi.mocked(aclService.listAclUsers).mockRejectedValue(new Error('users unavailable'));
    renderWithProviders(<AclPage />);

    expect(await screen.findByText('remote-user')).toBeInTheDocument();
    expect(screen.getByText('remote-topic')).toBeInTheDocument();
  });

  it('keeps users available when loading rules fails', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.listAclRules).mockRejectedValue(new Error('rules unavailable'));
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('用户管理'));
    expect(await screen.findByText('remote-admin')).toBeInTheDocument();
    expect(screen.getByText('cluster-a')).toBeInTheDocument();
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

  it('shows missing backend timestamps as unavailable', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.listAclRules).mockResolvedValue([
      {
        id: 'rule-without-time',
        principal: 'no-time-rule',
        resource: 'topic-a',
        resourceType: 'Topic',
        resourcePattern: 'LITERAL',
        actions: ['PUB'],
        decision: 'ALLOW',
        scope: 'cluster',
        aclVersion: 2,
        createdAt: null,
      },
    ]);
    vi.mocked(aclService.listAclUsers).mockResolvedValue([
      {
        id: 'user-without-time',
        username: 'no-time-user',
        accessKey: 'acce****3456',
        secretKey: 'secr****7654',
        admin: false,
        clusters: [],
        createdAt: null,
      },
    ]);
    renderWithProviders(<AclPage />);

    expect(await screen.findByText('no-time-rule')).toBeInTheDocument();
    expect(screen.getByText('-')).toBeInTheDocument();

    await user.click(screen.getByText('用户管理'));
    const userPanel = screen.getByRole('tabpanel', { name: '用户管理' });
    expect(await within(userPanel).findByText('no-time-user')).toBeInTheDocument();
    expect(within(userPanel).getByText('-')).toBeInTheDocument();
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
      instanceId: '',
    });
    expect(payload).not.toHaveProperty('accessKey');
    expect(payload).not.toHaveProperty('secretKey');
  });

  it('ignores duplicate admin toggles while an update is pending', async () => {
    vi.mocked(aclService.updateAclUser).mockImplementation(() => new Promise(() => {}));
    const user = userEvent.setup();
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('用户管理'));
    expect(await screen.findByText('remote-admin')).toBeInTheDocument();
    const adminSwitch = screen.getByRole('switch');
    fireEvent.click(adminSwitch);
    fireEvent.click(adminSwitch);

    await waitFor(() => expect(aclService.updateAclUser).toHaveBeenCalledTimes(1));
    expect(adminSwitch).toBeDisabled();
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
      instanceId: '',
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
      instanceId: '',
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
      instanceId: '',
    });
  });

  it('examines the broker cluster ACL config', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.examineBrokerClusterAclConfig).mockResolvedValue({
      clusterId: 'DefaultCluster',
      aclEnabled: true,
      aclVersion: 'ACL 2.0',
      globalWhiteRemoteAddresses: ['10.0.0.0/8'],
      accounts: [
        {
          accessKey: 'rocketmq-admin',
          admin: true,
          defaultTopicPerm: 'ALL',
          defaultGroupPerm: 'ALL',
          topicPerms: ['*=ALL'],
          groupPerms: ['*=ALL'],
        },
        {
          accessKey: 'user-order-service',
          admin: false,
          defaultTopicPerm: 'PUB',
          defaultGroupPerm: 'SUB',
          topicPerms: ['order-*=PUB'],
          groupPerms: ['cg-order-*=SUB'],
        },
      ],
      accountCount: 2,
    });
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('集群 ACL 配置'));
    const clusterInput = screen.getByPlaceholderText('请输入集群 ID');
    await user.type(clusterInput, 'DefaultCluster');
    await user.click(await screen.findByRole('button', { name: /检\s*查\s*配\s*置/ }));

    expect(await screen.findByText('rocketmq-admin')).toBeInTheDocument();
    expect(screen.getByText('ACL 2.0')).toBeInTheDocument();
    expect(aclService.examineBrokerClusterAclConfig).toHaveBeenCalledTimes(1);
  });

  it('creates a plain access account', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.createAndUpdatePlainAccessConfig).mockResolvedValue({
      accessKey: 'new-svc',
      admin: false,
      defaultTopicPerm: 'DENY',
      defaultGroupPerm: 'DENY',
      topicPerms: [],
      groupPerms: [],
      createdAt: '2026-08-01T00:00:00Z',
    });
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('集群 ACL 配置'));
    await user.click(screen.getByRole('button', { name: /新增/ }));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByPlaceholderText('e.g. user-order-service'), 'new-svc');
    await user.type(within(dialog).getByPlaceholderText('请输入密钥'), 'new-secret');
    await user.click(within(dialog).getByRole('button', { name: /添\s*加/ }));

    await waitFor(() =>
      expect(aclService.createAndUpdatePlainAccessConfig).toHaveBeenCalledTimes(1),
    );
    expect(aclService.createAndUpdatePlainAccessConfig).toHaveBeenCalledWith(
      expect.objectContaining({ accessKey: 'new-svc' }),
    );
  });
});
