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

import { App, ConfigProvider, message, Modal } from 'antd';
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode, useEffect } from 'react';
import { MemoryRouter, Route, Routes, useNavigate, type NavigateFunction } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Topic } from '../../../api/metadata';
import { LangProvider } from '../../../i18n/LangContext';
import * as topicService from '../../../services/topicService';
import * as instanceService from '../../../services/instanceService';
import TopicPage from '../topic';

vi.mock('../../../services/topicService', () => ({
  batchDeleteTopics: vi.fn(),
  createTopic: vi.fn(),
  deleteTopic: vi.fn(),
  exportTopics: vi.fn(),
  getTopicConsumerPage: vi.fn(),
  getTopicRoutes: vi.fn(),
  importTopics: vi.fn(),
  listTopicsPage: vi.fn(),
  sendTopicMessage: vi.fn(),
  updateTopic: vi.fn(),
}));
vi.mock('../../../services/instanceService', () => ({ listInstances: vi.fn() }));

const topic: Topic = {
  name: 'orders-topic',
  namespace: 'default',
  type: 'NORMAL',
  clusterId: 'cluster-a',
  instanceId: 'instance-a',
  writeQueues: 8,
  readQueues: 8,
  perm: 'RW',
  messageCount: 0,
  tps: 0,
  consumerGroupCount: 0,
  remark: '',
  gmtCreate: '2026-01-01T00:00:00Z',
  gmtModified: '2026-01-01T00:00:00Z',
};
type DeleteKind = 'single' | 'batch';
type Departure = 'instance' | 'navigation';
const deleteKinds = ['single', 'batch'] as const;
const departures = ['instance', 'navigation'] as const;
const outcomes = ['success', 'failure'] as const;
const mutationFor = (kind: DeleteKind) =>
  kind === 'single' ? topicService.deleteTopic : topicService.batchDeleteTopics;
const pageFor = (instanceId: string) => ({
  items: [{ ...topic, instanceId }],
  total: 1,
  page: 1,
  size: 20,
});
const deferred = () => {
  let release!: () => void;
  const promise = new Promise<void>((resolve) => {
    release = resolve;
  });
  return { promise, release };
};
let navigate: NavigateFunction;
const Navigation = () => {
  const routerNavigate = useNavigate();
  useEffect(() => {
    navigate = routerNavigate;
  }, [routerNavigate]);
  return null;
};
const renderPage = () =>
  render(
    <StrictMode>
      <ConfigProvider theme={{ token: { motion: false } }}>
        <App>
          <LangProvider>
            <MemoryRouter initialEntries={['/elsewhere', '/instance/instance-a/topic']}>
              <Navigation />
              <Routes>
                <Route path="/instance/:instanceId/topic" element={<TopicPage />} />
                <Route path="/elsewhere" element={<div>Another page</div>} />
              </Routes>
            </MemoryRouter>
          </LangProvider>
        </App>
      </ConfigProvider>
    </StrictMode>,
  );
async function openDelete(kind: DeleteKind) {
  const user = userEvent.setup({ pointerEventsCheck: 0 });
  const row = await screen.findByRole('row', { name: /orders-topic/ });
  await waitFor(() => expect(getComputedStyle(row).pointerEvents).not.toBe('none'));
  if (kind === 'single') {
    await user.click(within(row).getByRole('button', { name: /删除/ }));
  } else {
    await user.click(within(row).getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /删除 \(1\)$/ }));
  }
  return screen.findByRole('dialog');
}
async function confirm(dialog: HTMLElement) {
  await userEvent.setup().click(within(dialog).getByRole('button', { name: /^删 ?除$/ }));
}
async function leavePage(departure: Departure) {
  await act(async () => {
    if (departure === 'instance') await navigate('/instance/instance-b/topic');
    else await navigate(-1);
  });
  if (departure === 'instance') {
    await waitFor(() =>
      expect(topicService.listTopicsPage).toHaveBeenLastCalledWith(
        expect.objectContaining({ instanceId: 'instance-b' }),
      ),
    );
    await screen.findByRole('row', { name: /orders-topic/ });
  } else {
    expect(screen.getByText('Another page')).toBeInTheDocument();
  }
}
const expectNoNotification = () => {
  expect(message.success).not.toHaveBeenCalled();
  expect(message.warning).not.toHaveBeenCalled();
  expect(message.error).not.toHaveBeenCalled();
};

describe('Topic confirmation ownership', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    ConfigProvider.config({
      holderRender: (children) => (
        <ConfigProvider theme={{ token: { motion: false } }}>{children}</ConfigProvider>
      ),
    });
    vi.spyOn(message, 'success');
    vi.spyOn(message, 'warning');
    vi.spyOn(message, 'error');
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
    vi.mocked(instanceService.listInstances).mockResolvedValue(
      ['instance-a', 'instance-b'].map((name, id) => ({
        id: id + 1,
        name,
        remark: '',
        type: 'PROXY_CLUSTER',
        endpoint: '10.0.0.1:8080',
        topicCount: 1,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      })),
    );
    vi.mocked(topicService.listTopicsPage).mockImplementation(async (params) =>
      pageFor(params?.instanceId || 'instance-a'),
    );
    vi.mocked(topicService.deleteTopic).mockResolvedValue(undefined);
    vi.mocked(topicService.batchDeleteTopics).mockResolvedValue({
      deleted: [topic.name],
      failed: [],
    });
  });
  afterEach(async () => {
    await act(async () => {
      cleanup();
      Modal.destroyAll();
    });
    ConfigProvider.config({ holderRender: undefined });
    vi.restoreAllMocks();
  });

  it.each(deleteKinds)('keeps current %s confirmation working under StrictMode', async (kind) => {
    renderPage();
    const dialog = await openDelete(kind);
    vi.mocked(topicService.listTopicsPage).mockClear();
    await confirm(dialog);
    await waitFor(() =>
      expect(mutationFor(kind)).toHaveBeenCalledExactlyOnceWith(
        kind === 'single' ? topic.name : [topic.name],
        'instance-a',
      ),
    );
    expect(topicService.listTopicsPage).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ instanceId: 'instance-a' }),
    );
    await waitFor(() => expect(message.success).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole('button', { name: /删除 \(1\)$/ })).not.toBeInTheDocument();
  });

  it.each(deleteKinds)('keeps current %s cancellation working', async (kind) => {
    renderPage();
    const dialog = await openDelete(kind);
    await userEvent.setup().click(within(dialog).getByRole('button', { name: /^取 ?消$/ }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(topicService.deleteTopic).not.toHaveBeenCalled();
    expect(topicService.batchDeleteTopics).not.toHaveBeenCalled();
  });

  describe.each(departures)('after %s departure', (departure) => {
    it.each(deleteKinds)(
      'dismisses %s confirmation and prevents abandoned deletion',
      async (kind) => {
        renderPage();
        await openDelete(kind);
        await leavePage(departure);
        const orphanedDialog = screen.queryByRole('dialog');
        if (orphanedDialog) await confirm(orphanedDialog);
        expect(topicService.deleteTopic).not.toHaveBeenCalled();
        expect(topicService.batchDeleteTopics).not.toHaveBeenCalled();
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      },
    );

    describe.each(outcomes)('pending mutation %s', (outcome) => {
      it.each(deleteKinds)('ignores abandoned %s completion', async (kind) => {
        const gate = deferred();
        vi.mocked(topicService.deleteTopic).mockImplementationOnce(async () => {
          await gate.promise;
          if (outcome === 'failure') throw new Error('delete failed');
        });
        vi.mocked(topicService.batchDeleteTopics).mockImplementationOnce(async () => {
          await gate.promise;
          if (outcome === 'failure') throw new Error('batch delete failed');
          return { deleted: [topic.name], failed: [] };
        });
        renderPage();
        await confirm(await openDelete(kind));
        await waitFor(() => expect(mutationFor(kind)).toHaveBeenCalledTimes(1));
        await leavePage(departure);
        vi.mocked(topicService.listTopicsPage).mockClear();
        await act(async () => {
          gate.release();
          await gate.promise;
        });
        expect(topicService.listTopicsPage).not.toHaveBeenCalled();
        expectNoNotification();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
    });

    describe.each(outcomes)('pending refresh %s', (outcome) => {
      it.each(deleteKinds)('ignores abandoned %s refresh completion', async (kind) => {
        renderPage();
        const dialog = await openDelete(kind);
        const gate = deferred();
        vi.mocked(topicService.listTopicsPage).mockImplementationOnce(async () => {
          await gate.promise;
          if (outcome === 'failure') throw new Error('refresh failed');
          return pageFor('instance-a');
        });
        vi.mocked(topicService.listTopicsPage).mockClear();
        await confirm(dialog);
        await waitFor(() => expect(topicService.listTopicsPage).toHaveBeenCalledTimes(1));
        await leavePage(departure);
        await act(async () => {
          gate.release();
          await gate.promise;
        });
        expectNoNotification();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
    });
  });

  it.each([{ deleted: [] }, { deleted: [topic.name] }])(
    'ignores stale batch result with deleted names $deleted and failures',
    async ({ deleted }) => {
      const gate = deferred();
      vi.mocked(topicService.batchDeleteTopics).mockImplementationOnce(async () => {
        await gate.promise;
        return { deleted, failed: ['failed-topic'] };
      });
      renderPage();
      await confirm(await openDelete('batch'));
      await leavePage('instance');
      vi.mocked(topicService.listTopicsPage).mockClear();
      await act(async () => {
        gate.release();
        await gate.promise;
      });
      expect(topicService.listTopicsPage).not.toHaveBeenCalled();
      expectNoNotification();
      expect(screen.queryByRole('button', { name: /删除 \(1\)$/ })).not.toBeInTheDocument();
    },
  );

  it.each(deleteKinds)(
    'keeps a newer %s confirmation after an old mutation completes',
    async (kind) => {
      const gate = deferred();
      vi.mocked(topicService.deleteTopic).mockImplementationOnce(async () => gate.promise);
      vi.mocked(topicService.batchDeleteTopics).mockImplementationOnce(async () => {
        await gate.promise;
        return { deleted: [topic.name], failed: [] };
      });
      renderPage();
      await confirm(await openDelete(kind));
      await leavePage('instance');
      const newDialog = await openDelete(kind);
      vi.mocked(topicService.listTopicsPage).mockClear();
      await act(async () => {
        gate.release();
        await gate.promise;
      });
      expect(topicService.listTopicsPage).not.toHaveBeenCalled();
      expectNoNotification();
      expect(newDialog).toBeInTheDocument();
      expect(mutationFor(kind)).toHaveBeenCalledTimes(1);
      await confirm(newDialog);
      await waitFor(() =>
        expect(mutationFor(kind)).toHaveBeenLastCalledWith(
          kind === 'single' ? topic.name : [topic.name],
          'instance-b',
        ),
      );
      expect(mutationFor(kind)).toHaveBeenCalledTimes(2);
    },
  );

  it.each(deleteKinds)(
    'leaves unrelated confirmations alone when %s owner unmounts',
    async (kind) => {
      renderPage();
      await openDelete(kind);
      await act(async () => {
        Modal.confirm({ title: 'Independent dialog', okText: 'Independent confirm' });
      });
      await screen.findByRole('button', { name: 'Independent confirm' });
      await leavePage('navigation');
      expect(screen.getByRole('button', { name: 'Independent confirm' })).toBeInTheDocument();
      expect(screen.getAllByRole('dialog')).toHaveLength(1);
    },
  );
});
