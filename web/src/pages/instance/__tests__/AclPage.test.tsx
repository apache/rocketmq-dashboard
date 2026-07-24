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
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
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

describe('ACL page', () => {
  beforeEach(() => {
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
        accessKey: 'ak-remote',
        secretKey: 'sk-remote',
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

  it('renders backend users on the user tab', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AclPage />);

    await user.click(await screen.findByText('用户管理'));

    expect(await screen.findByText('remote-admin')).toBeInTheDocument();
    expect(screen.getByText('cluster-a')).toBeInTheDocument();
  });
});
