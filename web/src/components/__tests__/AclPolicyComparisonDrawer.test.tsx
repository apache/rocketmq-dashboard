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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import type { Instance } from '../../api/instance';
import type { AclRule, AclUser } from '../../api/acl';
import { LangProvider } from '../../i18n/LangContext';
import AclPolicyComparisonDrawer from '../AclPolicyComparisonDrawer';
import { downloadCsv } from '../../utils/download';

const aclServiceMocks = vi.hoisted(() => ({ listAclRules: vi.fn(), pageAclUsers: vi.fn() }));
vi.mock('../../services/aclService', () => aclServiceMocks);
vi.mock('../../utils/download', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../utils/download')>();
  return { ...actual, downloadCsv: vi.fn() };
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

const instances: Instance[] = ['production', 'staging'].map((name, index) => ({
  id: index + 1,
  name,
  remark: null,
  type: 'DIRECT',
  endpoint: `${name}:9876`,
  vendor: 'APACHE',
  topicCount: 0,
  consumerGroupCount: 0,
  gmtCreate: '2026-09-01 00:00:00',
  gmtModified: '2026-09-01 00:00:00',
}));

const aclUser = (username: string, admin = false): AclUser => ({
  id: username,
  username,
  admin,
  clusters: ['DefaultCluster'],
  permRead: true,
  permWrite: false,
});

const aclRule = (principal: string, actions: string[]): AclRule => ({
  id: `${principal}-${actions.join('-')}`,
  principal,
  resource: 'orders',
  resourceType: 'Topic',
  resourcePattern: 'LITERAL',
  actions,
  decision: 'ALLOW',
  scope: 'cluster',
  aclVersion: '2.0',
});

const renderDrawer = (
  overrides: Partial<React.ComponentProps<typeof AclPolicyComparisonDrawer>> = {},
) =>
  render(
    <App>
      <LangProvider>
        <AclPolicyComparisonDrawer
          open
          instances={instances}
          currentInstanceId="production"
          onClose={vi.fn()}
          {...overrides}
        />
      </LangProvider>
    </App>,
  );

describe('AclPolicyComparisonDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    aclServiceMocks.pageAclUsers.mockImplementation(({ instanceId }: { instanceId: string }) => {
      const items =
        instanceId === 'production'
          ? [aclUser('matching'), aclUser('drifted'), aclUser('source-only')]
          : [aclUser('matching'), aclUser('drifted', true), aclUser('target-only')];
      return Promise.resolve({ items, total: items.length, page: 1, size: 100 });
    });
    aclServiceMocks.listAclRules.mockImplementation(({ instanceId }: { instanceId: string }) => {
      const items =
        instanceId === 'production'
          ? [aclRule('matching', ['PUB']), aclRule('drifted', ['PUB'])]
          : [aclRule('matching', ['PUB']), aclRule('drifted', ['PUB', 'SUB'])];
      return Promise.resolve({ items, total: items.length, page: 1, size: 100 });
    });
  });

  it('loads users and rules for both instances without requesting credentials', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '开始对比' }));

    await waitFor(() => {
      expect(aclServiceMocks.pageAclUsers).toHaveBeenCalledTimes(2);
      expect(aclServiceMocks.listAclRules).toHaveBeenCalledTimes(2);
    });
    expect(await screen.findByText('source-only')).toBeInTheDocument();
    expect(screen.getByText('target-only')).toBeInTheDocument();
    expect(screen.getByText('凭据密钥不会被读取、比较或导出。')).toBeInTheDocument();
  });

  it('shows field-level policy drift', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '开始对比' }));
    await screen.findAllByText('drifted');

    const expandButtons = screen.getAllByRole('button', { name: 'Expand row' });
    await user.click(expandButtons[0]);
    expect(await screen.findByText(/管理员权限|授权动作/)).toBeInTheDocument();
  });

  it('filters the exported policy inventory', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '开始对比' }));
    await screen.findByText('source-only');
    await user.type(screen.getByLabelText('搜索用户或规则身份'), 'source-only');
    await user.click(screen.getByRole('button', { name: /导出结果/ }));

    const [filename, csv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(filename).toBe('rocketmq-acl-policy-production-vs-staging.csv');
    expect(csv).toContain('source-only');
    expect(csv).not.toContain('target-only');
    expect(csv).not.toContain('secret');
  });

  it('swaps source and target before comparison', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '交换源实例和目标实例' }));
    await user.click(screen.getByRole('button', { name: '开始对比' }));

    await waitFor(() => {
      expect(aclServiceMocks.pageAclUsers.mock.calls[0][0].instanceId).toBe('staging');
      expect(aclServiceMocks.pageAclUsers.mock.calls[1][0].instanceId).toBe('production');
    });
  });

  it('requires two instances', () => {
    renderDrawer({ instances: [instances[0]] });
    expect(screen.getByText('至少需要两个实例才能进行策略对比')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始对比' })).toBeDisabled();
  });
});
