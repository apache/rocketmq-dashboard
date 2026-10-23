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

import MockAdapter from 'axios-mock-adapter';
import { afterEach, describe, expect, it, vi } from 'vitest';
import client from '../api/client';
import useAuthStore from '../stores/authStore';
import { clearAuthSession, readAuthSession } from '../stores/authStorage';
import { listInstances } from './instanceService';

vi.mock('./dataMode', () => ({ isMockMode: () => false }));
vi.mock('antd', () => ({ message: { error: vi.fn() } }));

const mock = new MockAdapter(client);
afterEach(() => {
  mock.reset();
  useAuthStore.getState().logout();
  vi.unstubAllGlobals();
});

describe('instance HTTP session ownership', () => {
  it.each(['operator', 'another-operator'])(
    'keepsNewSessionCoalescingAfterOldHttp401Test (%s)',
    async (nextUser) => {
      useAuthStore.getState().login('operator', 7, true);
      let releaseOld!: () => void;
      let releaseFresh!: () => void;
      let startedOld!: () => void;
      let startedFresh!: () => void;
      const oldStarted = new Promise<void>((resolve) => {
        startedOld = resolve;
      });
      const freshStarted = new Promise<void>((resolve) => {
        startedFresh = resolve;
      });
      mock.onGet('/instances').replyOnce(() => {
        startedOld();
        return new Promise((resolve) => {
          releaseOld = () => resolve([401, { code: 401, message: 'expired' }]);
        });
      });
      mock.onGet('/instances').reply(() => {
        startedFresh();
        return new Promise((resolve) => {
          releaseFresh = () => resolve([200, { code: 200, data: [] }]);
        });
      });
      const oldResult = listInstances().then(
        () => 'unexpected success',
        () => 'rejected',
      );
      await oldStarted;
      useAuthStore.getState().logout();
      useAuthStore.getState().login(nextUser, nextUser === 'operator' ? 7 : 8, true);
      const fresh = listInstances();
      await freshStarted;
      const redirect = vi.fn();
      vi.stubGlobal('window', {
        location: {
          origin: window.location.origin,
          set href(path: string) {
            redirect(path);
          },
        },
      });
      releaseOld();
      expect(await oldResult).toBe('rejected');
      const shared = listInstances({ search: '   ' });
      releaseFresh();
      await expect(Promise.all([fresh, shared])).resolves.toEqual([[], []]);
      expect(mock.history.get).toHaveLength(2);
      expect(redirect).not.toHaveBeenCalled();
      expect(readAuthSession().user).toBe(nextUser);
    },
  );

  it('startsFreshInventoryAfterSessionClearedOutsideStoreTest', async () => {
    useAuthStore.getState().login('operator', 7, true);
    let release!: () => void;
    let started!: () => void;
    const dispatched = new Promise<void>((resolve) => {
      started = resolve;
    });
    mock.onGet('/instances').replyOnce(() => {
      started();
      return new Promise((resolve) => {
        release = () => resolve([200, { code: 200, data: [] }]);
      });
    });
    mock.onGet('/instances').reply(200, { code: 200, data: [] });
    const old = listInstances();
    await dispatched;
    // The shared HTTP unauthorized handler clears storage without replacing the Zustand snapshot.
    clearAuthSession();
    const fresh = listInstances();
    release();
    await Promise.all([old, fresh]);
    expect(mock.history.get).toHaveLength(2);
  });
});
