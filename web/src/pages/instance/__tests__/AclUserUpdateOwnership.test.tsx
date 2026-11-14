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

import { App, ConfigProvider } from 'antd';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AclUser } from '../../../api/acl';
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
  pushAclToBroker: vi.fn(),
  updateAclRule: vi.fn(),
  updateAclUser: vi.fn(),
}));
vi.mock('../../../services/instanceService', () => ({ listInstances: vi.fn() }));

const account: AclUser = {
  id: 11,
  username: 'orders-admin',
  admin: true,
  clusters: ['cluster-a'],
  gmtCreate: '2026-07-23T00:00:00Z',
};
const page = (items: AclUser[]) => ({ items, total: items.length, page: 1, size: 20 });
const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((complete, fail) => {
    resolve = complete;
    reject = fail;
  });
  return { promise, resolve, reject };
};
const renderPage = () =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <App>
        <LangProvider>
          <MemoryRouter initialEntries={['/instance/instance-a/acl']}>
            <AclPage />
          </MemoryRouter>
        </LangProvider>
      </App>
    </ConfigProvider>,
  );
async function changeClusters(dialog: HTMLElement, user: ReturnType<typeof userEvent.setup>) {
  const input = within(dialog).getByRole('combobox');
  await user.click(input);
  await user.keyboard('{Backspace}');
  await user.type(input, 'cluster-b{enter}');
  await user.click(within(dialog).getByRole('button', { name: /保\s*存/ }));
}

describe('ACL user update ownership', () => {
  beforeEach(() => {
    vi.resetAllMocks();
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
    vi.mocked(instanceService.listInstances).mockResolvedValue([]);
    vi.mocked(aclService.listAclRules).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 20,
    });
    vi.mocked(aclService.listAclUsers).mockResolvedValue([]);
    vi.mocked(aclService.pageAclUsers).mockResolvedValue(page([account]));
    vi.mocked(aclService.updateAclUser).mockImplementation(async (update) => ({
      ...account,
      ...update,
    }));
  });

  it.each([true, false])(
    'keepsCompletedAdminChangeFrom%sWhenEditingClustersTest',
    async (initialAdmin) => {
      let stored: AclUser = { ...account, admin: initialAdmin };
      const pendingUpdate = deferred<AclUser>();
      vi.mocked(aclService.pageAclUsers).mockImplementation(async () => page([{ ...stored }]));
      vi.mocked(aclService.updateAclUser)
        .mockImplementationOnce((update) => {
          stored = { ...stored, ...update };
          return pendingUpdate.promise;
        })
        .mockImplementationOnce(async (update) => {
          stored = { ...stored, ...update };
          return { ...stored };
        });
      const user = userEvent.setup();
      renderPage();
      const row = await screen.findByRole('row', { name: /orders-admin/ });
      await waitFor(() => expect(getComputedStyle(row).pointerEvents).not.toBe('none'));
      await user.click(within(row).getByRole('switch'));
      await waitFor(() => expect(aclService.updateAclUser).toHaveBeenCalledTimes(1));
      expect(stored.admin).toBe(!initialAdmin);
      // The backend has accepted the change, but its response is delayed. The editor
      // must not capture the old row before the switch update publishes its result.
      await user.click(within(row).getByRole('button', { name: /编辑/ }));
      await act(async () => pendingUpdate.resolve({ ...stored }));
      await waitFor(() =>
        expect(within(row).getByRole('switch')).toHaveAttribute(
          'aria-checked',
          String(!initialAdmin),
        ),
      );
      if (!screen.queryByRole('dialog')) {
        await user.click(within(row).getByRole('button', { name: /编辑/ }));
      }
      await changeClusters(await screen.findByRole('dialog', { name: '编辑用户' }), user);
      await waitFor(() => expect(aclService.updateAclUser).toHaveBeenCalledTimes(2));
      expect(aclService.updateAclUser).toHaveBeenLastCalledWith({
        id: account.id,
        username: account.username,
        admin: !initialAdmin,
        clusters: ['cluster-b'],
        instanceId: 'instance-a',
      });
      expect(stored.admin).toBe(!initialAdmin);
    },
  );

  it('keepsIndependentUserEditableDuringAdminRevocationTest', async () => {
    const other: AclUser = { ...account, id: 12, username: 'other-admin' };
    vi.mocked(aclService.pageAclUsers).mockResolvedValue(page([account, other]));
    const pendingUpdate = deferred<AclUser>();
    vi.mocked(aclService.updateAclUser).mockReturnValueOnce(pendingUpdate.promise);
    const user = userEvent.setup();
    renderPage();
    const row = await screen.findByRole('row', { name: /orders-admin/ });
    await waitFor(() => expect(getComputedStyle(row).pointerEvents).not.toBe('none'));
    await user.click(within(row).getByRole('switch'));
    await waitFor(() => expect(aclService.updateAclUser).toHaveBeenCalledTimes(1));
    const otherRow = screen.getByRole('row', { name: /other-admin/ });
    await user.click(within(otherRow).getByRole('button', { name: /编辑/ }));
    const dialog = await screen.findByRole('dialog', { name: '编辑用户' });
    expect(within(dialog).getByLabelText('用户名')).toHaveValue('other-admin');
    expect(within(dialog).getByRole('switch')).toBeChecked();
    await act(async () => pendingUpdate.resolve({ ...account, admin: false }));
  });

  it('releasesUserEditorAfterFailedAdminUpdateTest', async () => {
    const pendingUpdate = deferred<AclUser>();
    vi.mocked(aclService.updateAclUser).mockReturnValueOnce(pendingUpdate.promise);
    const user = userEvent.setup();
    renderPage();
    const row = await screen.findByRole('row', { name: /orders-admin/ });
    await waitFor(() => expect(getComputedStyle(row).pointerEvents).not.toBe('none'));
    await user.click(within(row).getByRole('switch'));
    await waitFor(() => expect(aclService.updateAclUser).toHaveBeenCalledTimes(1));
    await act(async () => pendingUpdate.reject(new Error('offline')));
    const edit = within(row).getByRole('button', { name: /编辑/ });
    await waitFor(() => expect(edit).toBeEnabled());
    await user.click(edit);
    await changeClusters(await screen.findByRole('dialog', { name: '编辑用户' }), user);
    await waitFor(() => expect(aclService.updateAclUser).toHaveBeenCalledTimes(2));
    expect(aclService.updateAclUser).toHaveBeenLastCalledWith({
      id: account.id,
      username: account.username,
      admin: true,
      clusters: ['cluster-b'],
      instanceId: 'instance-a',
    });
  });

  it('keepsOrdinaryUserEditingWorkingTest', async () => {
    const user = userEvent.setup();
    renderPage();
    const row = await screen.findByRole('row', { name: /orders-admin/ });
    await waitFor(() => expect(getComputedStyle(row).pointerEvents).not.toBe('none'));
    await user.click(within(row).getByRole('button', { name: /编辑/ }));
    await changeClusters(await screen.findByRole('dialog', { name: '编辑用户' }), user);
    await waitFor(() =>
      expect(aclService.updateAclUser).toHaveBeenCalledWith({
        id: account.id,
        username: account.username,
        admin: true,
        clusters: ['cluster-b'],
        instanceId: 'instance-a',
      }),
    );
  });
});
