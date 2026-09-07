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

import { App, Modal, message } from 'antd';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AclRule, AclUser } from '../../../api/acl';
import { LangProvider } from '../../../i18n/LangContext';
import * as aclService from '../../../services/aclService';
import * as instanceService from '../../../services/instanceService';
import AclPage from '../acl';

vi.mock('../../../services/aclService', () => ({
  createAclRule: vi.fn(),
  createAclUser: vi.fn(),
  createAndUpdatePlainAccessConfig: vi.fn(),
  deleteAclRule: vi.fn(),
  deleteAclUser: vi.fn(),
  getAclUserCredentials: vi.fn(),
  examineBrokerClusterAclConfig: vi.fn(),
  listAclRules: vi.fn(),
  listAclUsers: vi.fn(),
  pageAclUsers: vi.fn(),
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

const renderWithProviders = (ui: React.ReactElement, initialEntry = '/') =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter initialEntries={[initialEntry]}>{ui}</MemoryRouter>
      </LangProvider>
    </App>,
  );

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

describe('ACL page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(instanceService.listInstances).mockResolvedValue([]);

    vi.mocked(aclService.listAclRules).mockResolvedValue({
      items: [
        {
          id: 1,
          principal: 'remote-user',
          resource: 'remote-topic',
          resourceType: 'Topic',
          resourcePattern: 'LITERAL',
          actions: ['PUB'],
          decision: 'ALLOW',
          scope: 'cluster',
          aclVersion: 2,
          gmtCreate: '2026-07-23T00:00:00Z',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
    vi.mocked(aclService.pageAclUsers).mockResolvedValue({
      items: [
        {
          id: 11,
          username: 'remote-admin',
          accessKey: 'acce****3456',
          secretKey: 'secr****7654',
          admin: true,
          clusters: ['cluster-a'],
          gmtCreate: '2026-07-23T00:00:00Z',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
  });

  it('loads ACL rules and users through the service layer', async () => {
    renderWithProviders(<AclPage />);

    expect(await screen.findByText('remote-user')).toBeInTheDocument();
    expect(screen.getByText('remote-topic')).toBeInTheDocument();
    expect(aclService.listAclRules).toHaveBeenCalledTimes(1);
    expect(aclService.pageAclUsers).toHaveBeenCalledTimes(1);
  });

  it('reloads the server rule page after deleting one ACL rule', async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(Modal, 'confirm').mockImplementation((config) => {
      void config.onOk?.();
      return { destroy: vi.fn(), update: vi.fn() } as unknown as ReturnType<typeof Modal.confirm>;
    });
    vi.mocked(aclService.deleteAclRule).mockResolvedValue(undefined);
    vi.mocked(aclService.listAclRules)
      .mockResolvedValueOnce({
        items: [
          {
            id: 1,
            principal: 'remote-user',
            resource: 'remote-topic',
            resourceType: 'Topic',
            resourcePattern: 'LITERAL',
            actions: ['PUB'],
            decision: 'ALLOW',
            scope: 'cluster',
            aclVersion: 2,
            gmtCreate: '2026-07-23T00:00:00Z',
          },
        ],
        total: 1,
        page: 1,
        size: 20,
      })
      .mockResolvedValueOnce({
        items: [],
        total: 0,
        page: 1,
        size: 20,
      });
    renderWithProviders(<AclPage />);

    const row = await screen.findByRole('row', { name: /remote-topic/ });
    await user.click(within(row).getByRole('button', { name: /删除/ }));

    await waitFor(() => expect(aclService.deleteAclRule).toHaveBeenCalledWith(1, undefined));
    await waitFor(() => expect(aclService.listAclRules).toHaveBeenCalledTimes(2));
    expect(screen.queryByText('remote-topic')).not.toBeInTheDocument();
    confirmSpy.mockRestore();
  });

  it('shows a non-copyable placeholder when an ACL user has no access key', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.pageAclUsers).mockResolvedValue({
      items: [
        {
          id: 12,
          username: 'cloud-role',
          accessKey: null,
          secretKey: null,
          admin: false,
          clusters: ['cluster-a'],
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('用户管理'));
    const row = await screen.findByRole('row', { name: /cloud-role/ });
    const accessKeyCell = row.querySelectorAll('td')[1];

    expect(within(accessKeyCell).getByText('-')).toBeInTheDocument();
    expect(within(accessKeyCell).queryByRole('button')).not.toBeInTheDocument();
  });

  it('clamps rules back to a valid page when the current page becomes empty', async () => {
    const user = userEvent.setup();
    const ruleItems = (count: number) =>
      Array.from({ length: count }, (_, index) => ({
        id: index + 1,
        principal: 'remote-user',
        resource: `acl-topic-${String(index + 1).padStart(2, '0')}`,
        resourceType: 'Topic',
        resourcePattern: 'LITERAL',
        actions: ['PUB'],
        decision: 'ALLOW',
        scope: 'cluster',
        aclVersion: 2,
        gmtCreate: '2026-07-23T00:00:00Z',
      }));
    let call = 0;
    vi.mocked(aclService.listAclRules).mockImplementation(async (params) => {
      call += 1;
      if (params?.page === 2) {
        // Page 2 went out of range (its rules were deleted server-side).
        return { items: [], total: 15, page: 2, size: 20 };
      }
      // First load reports 45 rules (3 pages); the clamp re-fetch reports the shrunk 15.
      return call === 1
        ? { items: ruleItems(20), total: 45, page: 1, size: 20 }
        : { items: ruleItems(15), total: 15, page: 1, size: 20 };
    });
    renderWithProviders(<AclPage />);

    expect(await screen.findByText('acl-topic-01')).toBeInTheDocument();

    const secondPage = document.querySelector('.ant-pagination-item-2');
    expect(secondPage).not.toBeNull();
    await user.click(secondPage as HTMLElement);

    // The empty out-of-range page is corrected: the rules reload page 1.
    await waitFor(() =>
      expect(aclService.listAclRules).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 1, pageSize: 20 }),
      ),
    );
  });

  it('closes an ACL rule dialog when switching to another instance', async () => {
    const user = userEvent.setup();
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      {
        id: 1,
        name: 'Instance A',
        type: 'DIRECT',
        endpoint: '127.0.0.1:9876',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
      {
        id: 2,
        name: 'Instance B',
        type: 'DIRECT',
        endpoint: '127.0.0.2:9876',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
    ]);
    renderWithProviders(<AclPage />, '/instance/1/acl');

    expect(await screen.findByText('remote-user')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /添加规则/ }));
    const dialog = await screen.findByRole('dialog');

    await user.click(screen.getAllByRole('combobox')[0]);
    await user.click(
      await screen.findByText('Instance B', { selector: '.ant-select-item-option-content' }),
    );

    expect(await screen.findByText('remote-user')).toBeInTheDocument();
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
  });

  it('keeps rules available when loading users fails', async () => {
    vi.mocked(aclService.pageAclUsers).mockRejectedValue(new Error('users unavailable'));
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

  it('uses Tencent role names for role-backed ACL rules and users', async () => {
    const user = userEvent.setup();
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      {
        id: 21,
        name: 'tencent-rmq',
        type: 'CLOUD',
        endpoint: 'vpc.tencent:8080',
        vendor: 'TENCENT',
        cloudInstanceId: 'rmq-cloud',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
    ]);
    vi.mocked(aclService.listAclRules).mockResolvedValue({
      items: [
        {
          principal: 'reader-role',
          resource: '*',
          resourceType: 'Cluster',
          resourcePattern: 'LITERAL',
          actions: ['PUB'],
          decision: 'ALLOW',
          scope: 'cluster',
          aclVersion: '1.0',
          gmtCreate: '2026-07-23T00:00:00Z',
        } as AclRule,
      ],
      total: 1,
      page: 1,
      size: 20,
    });
    vi.mocked(aclService.pageAclUsers).mockResolvedValue({
      items: [
        {
          username: 'reader-role',
          accessKey: 'acce****3456',
          secretKey: 'secr****7654',
          admin: false,
          clusters: ['rmq-cloud'],
          gmtCreate: '2026-07-23T00:00:00Z',
        } as AclUser,
      ],
      total: 1,
      page: 1,
      size: 20,
    });
    vi.mocked(aclService.updateAclRule).mockResolvedValue({
      id: 'reader-role',
      principal: 'reader-role',
      resource: '*',
      resourceType: 'Cluster',
      resourcePattern: 'LITERAL',
      actions: ['PUB', 'SUB'],
      decision: 'ALLOW',
      scope: 'cluster',
      aclVersion: '1.0',
      gmtCreate: '2026-07-23T00:00:00Z',
    });
    vi.mocked(aclService.getAclUserCredentials).mockResolvedValue({
      id: 'reader-role',
      username: 'reader-role',
      accessKey: 'full-access-key',
      secretKey: 'full-secret-key',
      admin: false,
      clusters: ['rmq-cloud'],
      gmtCreate: '2026-07-23T00:00:00Z',
    });

    renderWithProviders(<AclPage />, '/instance/tencent-rmq/acl');

    await waitFor(() =>
      expect(screen.getByTestId('acl-local-metadata-notice')).toHaveTextContent(
        'Tencent Cloud Role',
      ),
    );
    const ruleRow = await screen.findByRole('row', { name: /reader-role/ });
    await user.click(within(ruleRow).getByRole('button', { name: /编辑/ }));
    const ruleDialog = await screen.findByRole('dialog');
    expect(within(ruleDialog).getByLabelText('资源名称')).toBeDisabled();
    await user.click(within(ruleDialog).getByLabelText('订阅 (SUB)'));
    await user.click(within(ruleDialog).getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => expect(aclService.updateAclRule).toHaveBeenCalledTimes(1));
    expect(aclService.updateAclRule).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 'reader-role',
        principal: 'reader-role',
        resource: '*',
        resourceType: 'Cluster',
        resourcePattern: 'LITERAL',
        decision: 'ALLOW',
        scope: 'cluster',
        instanceId: 'tencent-rmq',
      }),
    );

    await user.click(screen.getByText('用户管理'));
    const userRow = await screen.findByRole('row', { name: /reader-role/ });
    const secretCell = within(userRow).getByText('••••••••••••').closest('td');
    expect(secretCell).not.toBeNull();
    await user.click(within(secretCell as HTMLElement).getByRole('button'));

    await waitFor(() =>
      expect(aclService.getAclUserCredentials).toHaveBeenCalledWith('reader-role', 'tencent-rmq'),
    );
    expect(await within(userRow).findByText('full-secret-key')).toBeInTheDocument();
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
    vi.mocked(aclService.listAclRules).mockResolvedValue({
      items: [
        {
          id: 2,
          principal: 'no-time-rule',
          resource: 'topic-a',
          resourceType: 'Topic',
          resourcePattern: 'LITERAL',
          actions: ['PUB'],
          decision: 'ALLOW',
          scope: 'cluster',
          aclVersion: 2,
          gmtCreate: null,
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
    vi.mocked(aclService.pageAclUsers).mockResolvedValue({
      items: [
        {
          id: 12,
          username: 'no-time-user',
          accessKey: 'acce****3456',
          secretKey: 'secr****7654',
          admin: false,
          clusters: [],
          gmtCreate: null,
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
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
      id: 11,
      username: 'remote-admin',
      accessKey: 'acce****3456',
      secretKey: 'secr****7654',
      admin: true,
      clusters: ['cluster-a'],
      gmtCreate: '2026-07-23T00:00:00Z',
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
      id: 11,
      username: 'remote-admin',
      admin: true,
      clusters: ['cluster-a'],
      instanceId: undefined,
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
      id: 11,
      username: 'remote-admin',
      accessKey: 'acce****3456',
      secretKey: 'secr****7654',
      admin: false,
      clusters: ['cluster-a'],
      gmtCreate: '2026-07-23T00:00:00Z',
    });
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('用户管理'));
    expect(await screen.findByText('remote-admin')).toBeInTheDocument();
    await user.click(screen.getByRole('switch'));

    await waitFor(() => expect(aclService.updateAclUser).toHaveBeenCalledTimes(1));
    const payload = vi.mocked(aclService.updateAclUser).mock.calls[0][0];
    expect(payload).toEqual({
      id: 11,
      username: 'remote-admin',
      admin: false,
      clusters: ['cluster-a'],
      instanceId: undefined,
    });
    expect(payload).not.toHaveProperty('accessKey');
    expect(payload).not.toHaveProperty('secretKey');
  });

  it('creates a user with the selected cluster scope', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.createAclUser).mockResolvedValue({
      id: 13,
      username: 'orders-service',
      accessKey: 'acce****3456',
      secretKey: 'secr****7654',
      admin: false,
      clusters: ['cluster-a', 'cluster-b'],
      gmtCreate: '2026-08-01T00:00:00Z',
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
      instanceId: undefined,
    });
  });

  it('reloads the server user page after creating an ACL user', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.createAclUser).mockResolvedValue({
      id: 13,
      username: 'orders-service',
      accessKey: 'acce****3456',
      secretKey: 'secr****7654',
      admin: false,
      clusters: ['cluster-a', 'cluster-b'],
      gmtCreate: '2026-08-01T00:00:00Z',
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
    await waitFor(() => expect(aclService.pageAclUsers).toHaveBeenCalledTimes(2));
  });

  it('reloads the server user page after deleting one ACL user', async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(Modal, 'confirm').mockImplementation((config) => {
      void config.onOk?.();
      return { destroy: vi.fn(), update: vi.fn() } as unknown as ReturnType<typeof Modal.confirm>;
    });
    vi.mocked(aclService.deleteAclUser).mockResolvedValue(undefined);
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('用户管理'));
    const userPanel = screen.getByRole('tabpanel', { name: '用户管理' });
    const row = await within(userPanel).findByRole('row', { name: /remote-admin/ });
    await user.click(within(row).getByRole('button', { name: /删除/ }));

    await waitFor(() => expect(aclService.deleteAclUser).toHaveBeenCalledWith(11, undefined));
    await waitFor(() => expect(aclService.pageAclUsers).toHaveBeenCalledTimes(2));
    confirmSpy.mockRestore();
  });

  it('replaces the cluster scope of an existing user', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.updateAclUser).mockResolvedValue({
      id: 11,
      username: 'remote-admin',
      accessKey: 'acce****3456',
      secretKey: 'secr****7654',
      admin: true,
      clusters: ['cluster-b'],
      gmtCreate: '2026-07-23T00:00:00Z',
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
      id: 11,
      username: 'remote-admin',
      admin: true,
      clusters: ['cluster-b'],
      instanceId: undefined,
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

    expect(await screen.findAllByText('rocketmq-admin')).not.toHaveLength(0);
    expect(screen.getByText('ACL 2.0')).toBeInTheDocument();
    expect(aclService.examineBrokerClusterAclConfig).toHaveBeenCalledTimes(1);
  });

  it('keeps cluster config ownership with the latest examine request', async () => {
    const firstExamine =
      deferred<Awaited<ReturnType<typeof aclService.examineBrokerClusterAclConfig>>>();
    const secondExamine =
      deferred<Awaited<ReturnType<typeof aclService.examineBrokerClusterAclConfig>>>();
    const thirdExamine =
      deferred<Awaited<ReturnType<typeof aclService.examineBrokerClusterAclConfig>>>();
    const successSpy = vi.spyOn(message, 'success').mockImplementation(vi.fn());
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(vi.fn());
    const user = userEvent.setup();
    vi.mocked(aclService.examineBrokerClusterAclConfig)
      .mockReturnValueOnce(firstExamine.promise)
      .mockReturnValueOnce(secondExamine.promise)
      .mockReturnValueOnce(thirdExamine.promise);
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('集群 ACL 配置'));
    const clusterInput = screen.getByPlaceholderText('请输入集群 ID');
    const examineButton = screen.getByRole('button', { name: /检\s*查\s*配\s*置/ });

    fireEvent.change(clusterInput, { target: { value: 'cluster-old-1' } });
    await user.click(examineButton);
    await waitFor(() =>
      expect(aclService.examineBrokerClusterAclConfig).toHaveBeenNthCalledWith(1, 'cluster-old-1'),
    );

    await user.clear(clusterInput);
    await user.type(clusterInput, 'cluster-old-2{enter}');
    await waitFor(() =>
      expect(aclService.examineBrokerClusterAclConfig).toHaveBeenNthCalledWith(2, 'cluster-old-2'),
    );

    await user.clear(clusterInput);
    await user.type(clusterInput, 'cluster-latest{enter}');
    await waitFor(() =>
      expect(aclService.examineBrokerClusterAclConfig).toHaveBeenNthCalledWith(3, 'cluster-latest'),
    );

    await act(async () => {
      firstExamine.resolve({
        clusterId: 'cluster-old-1',
        aclEnabled: true,
        aclVersion: 'ACL stale-1',
        globalWhiteRemoteAddresses: [],
        accounts: [
          {
            accessKey: 'stale-account-1',
            admin: true,
            defaultTopicPerm: 'ALL',
            defaultGroupPerm: 'ALL',
            topicPerms: [],
            groupPerms: [],
          },
        ],
        accountCount: 1,
      });
    });

    expect(screen.queryByText('stale-account-1')).not.toBeInTheDocument();
    expect(successSpy).not.toHaveBeenCalled();
    expect(errorSpy).not.toHaveBeenCalled();
    expect(examineButton).toHaveClass('ant-btn-loading');

    await act(async () => {
      secondExamine.reject(new Error('stale failure'));
    });

    expect(screen.queryByText('stale-account-1')).not.toBeInTheDocument();
    expect(successSpy).not.toHaveBeenCalled();
    expect(errorSpy).not.toHaveBeenCalled();
    expect(examineButton).toHaveClass('ant-btn-loading');

    await act(async () => {
      thirdExamine.resolve({
        clusterId: 'cluster-latest',
        aclEnabled: true,
        aclVersion: 'ACL latest',
        globalWhiteRemoteAddresses: ['10.0.0.0/8'],
        accounts: [
          {
            accessKey: 'latest-account',
            admin: false,
            defaultTopicPerm: 'PUB',
            defaultGroupPerm: 'SUB',
            topicPerms: ['topic=PUB'],
            groupPerms: ['group=SUB'],
          },
        ],
        accountCount: 1,
      });
    });

    expect(await screen.findAllByText('latest-account')).not.toHaveLength(0);
    expect(screen.getAllByText('ACL latest')).not.toHaveLength(0);
    await waitFor(() => expect(examineButton).not.toHaveClass('ant-btn-loading'));
    expect(successSpy).toHaveBeenCalledTimes(1);
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it('keeps credential reveal ownership with the latest user generation', async () => {
    type CredentialResponse = Awaited<ReturnType<typeof aclService.getAclUserCredentials>>;
    const firstReveal = deferred<CredentialResponse>();
    const secondReveal = deferred<CredentialResponse>();
    const thirdReveal = deferred<CredentialResponse>();
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(vi.fn());
    const user = userEvent.setup();
    vi.mocked(aclService.getAclUserCredentials)
      .mockReturnValueOnce(firstReveal.promise)
      .mockReturnValueOnce(secondReveal.promise)
      .mockReturnValueOnce(thirdReveal.promise);
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('用户管理'));
    const row = await screen.findByRole('row', { name: /remote-admin/ });
    const secretCell = screen.getByText('••••••••••••').closest('td');
    expect(secretCell).not.toBeNull();
    const revealButton = within(secretCell as HTMLElement).getByRole('button');

    await user.click(revealButton);
    await waitFor(() => expect(aclService.getAclUserCredentials).toHaveBeenCalledTimes(1));
    expect(within(row).getByText('加载中…')).toBeInTheDocument();

    await user.click(revealButton);
    expect(within(row).queryByText('加载中…')).not.toBeInTheDocument();

    await user.click(revealButton);
    await waitFor(() => expect(aclService.getAclUserCredentials).toHaveBeenCalledTimes(2));
    expect(within(row).getByText('加载中…')).toBeInTheDocument();

    await user.click(revealButton);
    expect(within(row).queryByText('加载中…')).not.toBeInTheDocument();

    await user.click(revealButton);
    await waitFor(() => expect(aclService.getAclUserCredentials).toHaveBeenCalledTimes(3));
    expect(within(row).getByText('加载中…')).toBeInTheDocument();

    await act(async () => {
      firstReveal.resolve({
        id: 1,
        username: 'remote-admin',
        accessKey: 'stale-access-key',
        secretKey: 'stale-secret-key',
        admin: true,
        clusters: ['cluster-a'],
        gmtCreate: '2026-07-23T00:00:00Z',
      });
    });

    expect(within(row).getByText('加载中…')).toBeInTheDocument();
    expect(screen.queryByText('stale-secret-key')).not.toBeInTheDocument();
    expect(screen.queryByText('stale-access-key')).not.toBeInTheDocument();
    expect(errorSpy).not.toHaveBeenCalled();

    await act(async () => {
      secondReveal.reject(new Error('stale reveal failure'));
    });

    expect(within(row).getByText('加载中…')).toBeInTheDocument();
    expect(errorSpy).not.toHaveBeenCalled();

    await act(async () => {
      thirdReveal.resolve({
        id: 1,
        username: 'remote-admin',
        accessKey: 'latest-access-key',
        secretKey: 'latest-secret-key',
        admin: true,
        clusters: ['cluster-a'],
        gmtCreate: '2026-07-23T00:00:00Z',
      });
    });

    expect(await within(row).findByText('latest-secret-key')).toBeInTheDocument();
    expect(within(row).getByText('latest-access-key')).toBeInTheDocument();
    expect(screen.queryByText('stale-secret-key')).not.toBeInTheDocument();
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it('renders ACL risk diagnostics for the examined cluster config', async () => {
    const user = userEvent.setup();
    vi.mocked(aclService.examineBrokerClusterAclConfig).mockResolvedValue({
      clusterId: 'DefaultCluster',
      aclEnabled: true,
      aclVersion: 'ACL 2.0',
      globalWhiteRemoteAddresses: ['*'],
      accounts: [
        {
          accessKey: 'admin-ak',
          admin: true,
          whiteRemoteAddress: '0.0.0.0/0',
          defaultTopicPerm: 'ALL',
          defaultGroupPerm: 'ALL',
          topicPerms: ['*=ALL'],
          groupPerms: ['*=SUB'],
        },
      ],
      accountCount: 1,
    });
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('集群 ACL 配置'));
    await user.click(await screen.findByRole('button', { name: /检\s*查\s*配\s*置/ }));

    const diagnostics = await screen.findByTestId('acl-risk-diagnostics');
    expect(within(diagnostics).getByText('ACL 风险诊断')).toBeInTheDocument();
    expect(within(diagnostics).getByText('ACL 配置存在高风险')).toBeInTheDocument();
    expect(within(diagnostics).getByText('全局 IP 白名单范围过大')).toBeInTheDocument();
    expect(within(diagnostics).getByText('管理员账号可从宽网段访问')).toBeInTheDocument();
    expect(
      within(diagnostics).getAllByText(
        '将默认 Topic 权限改为 DENY，并为确需访问的 Topic 配置最小权限。',
      ),
    ).not.toHaveLength(0);
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
      gmtCreate: '2026-08-01T00:00:00Z',
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
