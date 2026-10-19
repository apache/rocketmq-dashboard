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

import { App, ConfigProvider, Form, message, Modal } from 'antd';
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useEffect } from 'react';
import { MemoryRouter, Route, Routes, useNavigate, type NavigateFunction } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ConsumerGroup } from '../../../api/metadata';
import { LangProvider } from '../../../i18n/LangContext';
import * as consumerService from '../../../services/consumerService';
import * as instanceService from '../../../services/instanceService';
import ConsumerPage from '../consumer';

vi.mock('../../../services/consumerService', () => ({
  listConsumerGroupPage: vi.fn(),
  deleteConsumerGroup: vi.fn(),
  batchDeleteConsumerGroups: vi.fn(),
  createConsumerGroup: vi.fn(),
  getConsumerGroupSettings: vi.fn(),
  updateConsumerGroupSettings: vi.fn(),
  getConsumerProgress: vi.fn(),
  getConsumerSubscriptions: vi.fn(),
}));
vi.mock('../../../services/instanceService', () => ({ listInstances: vi.fn() }));

const group: ConsumerGroup = {
  name: 'orders-consumer',
  namespace: 'default',
  clusterId: 'cluster-a',
  instanceId: 'instance-a',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 0,
  totalLag: 0,
  subscribedTopics: [],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  gmtCreate: '2026-07-23T00:00:00Z',
  gmtModified: '2026-07-23T00:00:00Z',
  delaySeconds: 0,
  instances: [],
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
    <ConfigProvider theme={{ token: { motion: false } }}>
      <App>
        <LangProvider>
          <MemoryRouter initialEntries={['/elsewhere', '/instance/instance-a/consumer']}>
            <Navigation />
            <Routes>
              <Route path="/instance/:instanceId/consumer" element={<ConsumerPage />} />
              <Route path="/elsewhere" element={<div>Another page</div>} />
            </Routes>
          </MemoryRouter>
        </LangProvider>
      </App>
    </ConfigProvider>,
  );
async function openDelete(kind: 'single' | 'batch') {
  const user = userEvent.setup({ pointerEventsCheck: 0 });
  const row = await screen.findByRole('row', { name: /orders-consumer/ });
  await waitFor(() => expect(getComputedStyle(row).pointerEvents).not.toBe('none'));
  if (kind === 'single') {
    await user.click(within(row).getByRole('button', { name: /删除/ }));
  } else {
    await user.click(within(row).getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /删除 \(1\)$/ }));
  }
  return screen.findByRole('dialog');
}

async function openFormConfirmation(kind: 'create' | 'settings') {
  const user = userEvent.setup({ pointerEventsCheck: 0 });
  const row = await screen.findByRole('row', { name: /orders-consumer/ });
  if (kind === 'create') {
    await user.click(screen.getByRole('button', { name: '创建 Group' }));
    const dialog = await screen.findByRole('dialog', { name: '创建 Group' });
    await user.type(within(dialog).getByLabelText('Group 名称'), 'new-consumer');
    await user.click(within(dialog).getByRole('button', { name: /^创 ?建$/ }));
    return (await screen.findByRole('button', { name: '确认创建' })).closest(
      '[role="dialog"]',
    ) as HTMLElement;
  }
  await user.click(within(row).getByRole('button', { name: '详情' }));
  const dialog = await screen.findByRole('dialog', { name: /orders-consumer/ });
  await user.click(within(dialog).getByRole('tab', { name: '配置' }));
  await waitFor(() => expect(within(dialog).getByLabelText('重试队列数')).toHaveValue('1'));
  await user.click(within(dialog).getByRole('switch', { name: '启用消费' }));
  await user.click(within(dialog).getByRole('button', { name: /^保 ?存$/ }));
  return (await screen.findByRole('button', { name: '确认修改' })).closest(
    '[role="dialog"]',
  ) as HTMLElement;
}

describe('Consumer confirmation ownership', () => {
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
    vi.mocked(instanceService.listInstances).mockResolvedValue(
      ['instance-a', 'instance-b'].map((name, id) => ({
        id: id + 1,
        name,
        remark: '',
        type: 'PROXY_CLUSTER',
        endpoint: '10.0.0.1:8080',
        topicCount: 0,
        consumerGroupCount: 1,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      })),
    );
    vi.mocked(consumerService.listConsumerGroupPage).mockImplementation(async (params) => ({
      items: [{ ...group, instanceId: params?.instanceId }],
      total: 1,
      page: 1,
      size: 20,
    }));
    vi.mocked(consumerService.getConsumerProgress).mockResolvedValue([]);
    vi.mocked(consumerService.getConsumerSubscriptions).mockResolvedValue([]);
    vi.mocked(consumerService.getConsumerGroupSettings).mockResolvedValue({
      groupName: group.name,
      retryQueueNums: 1,
      retryMaxTimes: 16,
      consumeEnable: true,
      consumeMessageOrderly: false,
    });
    vi.mocked(consumerService.createConsumerGroup).mockResolvedValue(group);
    vi.mocked(consumerService.updateConsumerGroupSettings).mockResolvedValue({
      groupName: group.name,
      retryQueueNums: 1,
      retryMaxTimes: 16,
      consumeEnable: false,
      consumeMessageOrderly: false,
    });
    vi.mocked(consumerService.deleteConsumerGroup).mockResolvedValue(undefined);
    vi.mocked(consumerService.batchDeleteConsumerGroups).mockResolvedValue({
      deleted: [group.name],
      failed: [],
    });
  });
  afterEach(async () => {
    await act(async () => {
      cleanup();
      Modal.destroyAll();
    });
    vi.restoreAllMocks();
  });

  it.each(['single', 'batch'] as const)('keepsCurrent%sDeletionWorkingTest', async (kind) => {
    renderPage();
    const dialog = await openDelete(kind);
    await userEvent.setup().click(within(dialog).getByRole('button', { name: /^删 ?除$/ }));
    const mutation =
      kind === 'single'
        ? consumerService.deleteConsumerGroup
        : consumerService.batchDeleteConsumerGroups;
    await waitFor(() =>
      expect(mutation).toHaveBeenCalledWith(
        kind === 'single' ? group.name : [group.name],
        'instance-a',
      ),
    );
  });

  it.each(['single', 'batch'] as const)(
    'dismisses%sConfirmationAfterLeavingItsPageTest',
    async (kind) => {
      renderPage();
      await openDelete(kind);
      await act(async () => {
        await navigate(-1);
      });
      expect(screen.getByText('Another page')).toBeInTheDocument();
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(consumerService.deleteConsumerGroup).not.toHaveBeenCalled();
      expect(consumerService.batchDeleteConsumerGroups).not.toHaveBeenCalled();
    },
  );

  it.each(['single', 'batch'] as const)(
    'doesNotDispatch%sDeletionFromAnAbandonedInstanceTest',
    async (kind) => {
      renderPage();
      await openDelete(kind);
      await act(async () => {
        await navigate('/instance/instance-b/consumer');
      });
      await waitFor(() =>
        expect(consumerService.listConsumerGroupPage).toHaveBeenLastCalledWith(
          expect.objectContaining({ instanceId: 'instance-b' }),
        ),
      );
      const orphanedDialog = screen.queryByRole('dialog');
      if (orphanedDialog) {
        await userEvent
          .setup()
          .click(within(orphanedDialog).getByRole('button', { name: /^删 ?除$/ }));
      }
      expect(consumerService.deleteConsumerGroup).not.toHaveBeenCalled();
      expect(consumerService.batchDeleteConsumerGroups).not.toHaveBeenCalled();
    },
  );
  it.each(['create', 'settings'] as const)(
    'keepsCurrent%sConfirmationWorkingTest',
    async (kind) => {
      renderPage();
      const dialog = await openFormConfirmation(kind);
      await userEvent
        .setup()
        .click(
          within(dialog).getByRole('button', { name: kind === 'create' ? '确认创建' : '确认修改' }),
        );
      const mutation =
        kind === 'create'
          ? consumerService.createConsumerGroup
          : consumerService.updateConsumerGroupSettings;
      await waitFor(() =>
        expect(mutation).toHaveBeenCalledWith(
          expect.objectContaining({ instanceId: 'instance-a' }),
        ),
      );
    },
  );

  it.each(['create', 'settings'] as const)(
    'doesNotDispatch%sMutationFromAnAbandonedInstanceTest',
    async (kind) => {
      renderPage();
      await openFormConfirmation(kind);
      await act(async () => {
        await navigate('/instance/instance-b/consumer');
      });
      await waitFor(() =>
        expect(consumerService.listConsumerGroupPage).toHaveBeenLastCalledWith(
          expect.objectContaining({ instanceId: 'instance-b' }),
        ),
      );
      const orphanedDialog = screen.queryByRole('dialog');
      if (orphanedDialog) {
        await userEvent.setup().click(
          within(orphanedDialog).getByRole('button', {
            name: kind === 'create' ? '确认创建' : '确认修改',
          }),
        );
      }
      expect(consumerService.createConsumerGroup).not.toHaveBeenCalled();
      expect(consumerService.updateConsumerGroupSettings).not.toHaveBeenCalled();
    },
  );
  it.each(['create', 'settings'] as const)(
    'doesNotOpen%sConfirmationAfterValidationOutlivesPageTest',
    async (kind) => {
      const forms = new Set<ReturnType<typeof Form.useForm>[0]>();
      const useForm = Form.useForm;
      vi.spyOn(Form, 'useForm').mockImplementation(function useTrackedForm(...args) {
        const result = useForm(...args);
        forms.add(result[0]);
        return result;
      });
      renderPage();
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const row = await screen.findByRole('row', { name: /orders-consumer/ });
      let formDialog: HTMLElement;
      if (kind === 'create') {
        await user.click(screen.getByRole('button', { name: '创建 Group' }));
        formDialog = await screen.findByRole('dialog', { name: '创建 Group' });
        await user.type(within(formDialog).getByLabelText('Group 名称'), 'late-consumer');
      } else {
        await user.click(within(row).getByRole('button', { name: '详情' }));
        formDialog = await screen.findByRole('dialog', { name: /orders-consumer/ });
        await user.click(within(formDialog).getByRole('tab', { name: '配置' }));
        await waitFor(() =>
          expect(within(formDialog).getByLabelText('重试队列数')).toHaveValue('1'),
        );
        await user.click(within(formDialog).getByRole('switch', { name: '启用消费' }));
      }
      const form = [...forms].find((candidate) =>
        kind === 'create'
          ? candidate.getFieldValue('name') === 'late-consumer'
          : candidate.getFieldValue('retryQueueNums') === 1,
      );
      expect(form).toBeDefined();
      let release!: () => void;
      const validationGate = new Promise<void>((resolve) => {
        release = resolve;
      });
      const validateFields = form!.validateFields;
      const validation = vi.spyOn(form!, 'validateFields').mockImplementation(async (...args) => {
        const values = await validateFields(...args);
        await validationGate;
        return values;
      });
      await user.click(
        within(formDialog).getByRole('button', {
          name: kind === 'create' ? /^创 ?建$/ : /^保 ?存$/,
        }),
      );
      await waitFor(() => expect(validation).toHaveBeenCalledTimes(1));
      await act(async () => {
        await navigate('/elsewhere');
      });
      await act(async () => {
        release();
        await validationGate;
      });
      // Static confirmation creation is queued; allow AntD's scheduled render to run.
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 20));
      });
      expect(screen.getByText('Another page')).toBeInTheDocument();
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      expect(consumerService.createConsumerGroup).not.toHaveBeenCalled();
      expect(consumerService.updateConsumerGroupSettings).not.toHaveBeenCalled();
    },
  );
  it('leavesUnrelatedConfirmationsOwnedByAnotherPageAloneTest', async () => {
    renderPage();
    await openDelete('single');
    await act(async () => {
      Modal.confirm({ title: 'Independent dialog', okText: 'Independent confirm' });
    });
    await screen.findByRole('button', { name: 'Independent confirm' });
    await act(async () => {
      await navigate('/elsewhere');
    });
    expect(screen.getByText('Another page')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Independent confirm' })).toBeInTheDocument();
    expect(screen.queryByText('确认删除消费组 "orders-consumer"？')).not.toBeInTheDocument();
  });

  it.each(['single', 'batch', 'create'] as const)(
    'doesNotRefreshOldInstanceAfterPending%sMutationCompletesTest',
    async (kind) => {
      let release!: () => void;
      const gate = new Promise<void>((resolve) => {
        release = resolve;
      });
      vi.mocked(consumerService.deleteConsumerGroup).mockImplementationOnce(async () => {
        await gate;
      });
      vi.mocked(consumerService.batchDeleteConsumerGroups).mockImplementationOnce(async () => {
        await gate;
        return { deleted: [group.name], failed: [] };
      });
      vi.mocked(consumerService.createConsumerGroup).mockImplementationOnce(async () => {
        await gate;
        return group;
      });
      renderPage();
      const dialog =
        kind === 'create' ? await openFormConfirmation('create') : await openDelete(kind);
      await userEvent
        .setup()
        .click(
          within(dialog).getByRole('button', { name: kind === 'create' ? '确认创建' : /^删 ?除$/ }),
        );
      const mutation =
        kind === 'create'
          ? consumerService.createConsumerGroup
          : kind === 'single'
            ? consumerService.deleteConsumerGroup
            : consumerService.batchDeleteConsumerGroups;
      await waitFor(() => expect(mutation).toHaveBeenCalledTimes(1));
      await act(async () => {
        await navigate('/instance/instance-b/consumer');
      });
      await waitFor(() =>
        expect(consumerService.listConsumerGroupPage).toHaveBeenLastCalledWith(
          expect.objectContaining({ instanceId: 'instance-b' }),
        ),
      );
      vi.mocked(consumerService.listConsumerGroupPage).mockClear();
      await act(async () => {
        release();
        await gate;
      });
      expect(consumerService.listConsumerGroupPage).not.toHaveBeenCalled();
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      expect(screen.getByRole('row', { name: /orders-consumer/ })).toBeInTheDocument();
    },
  );
  describe.each(['instance', 'navigation'] as const)(
    'after %s departure during a confirmed deletion refresh',
    (departure) => {
      it.each(['single', 'batch'] as const)(
        'does not notify when the abandoned %s refresh rejects',
        async (kind) => {
          const error = vi.spyOn(message, 'error');
          const success = vi.spyOn(message, 'success');
          renderPage();
          const dialog = await openDelete(kind);
          let release!: () => void;
          const gate = new Promise<void>((resolve) => {
            release = resolve;
          });
          vi.mocked(consumerService.listConsumerGroupPage).mockImplementationOnce(async () => {
            await gate;
            throw new Error('refresh failed');
          });
          vi.mocked(consumerService.listConsumerGroupPage).mockClear();
          await userEvent.setup().click(within(dialog).getByRole('button', { name: /^删 ?除$/ }));
          await waitFor(() =>
            expect(consumerService.listConsumerGroupPage).toHaveBeenCalledTimes(1),
          );
          await act(async () => {
            if (departure === 'instance') await navigate('/instance/instance-b/consumer');
            else await navigate(-1);
          });
          if (departure === 'instance') {
            await waitFor(() =>
              expect(consumerService.listConsumerGroupPage).toHaveBeenLastCalledWith(
                expect.objectContaining({ instanceId: 'instance-b' }),
              ),
            );
            await screen.findByRole('row', { name: /orders-consumer/ });
          } else {
            expect(screen.getByText('Another page')).toBeInTheDocument();
          }
          vi.mocked(consumerService.listConsumerGroupPage).mockClear();
          await act(async () => {
            release();
            await gate;
          });
          expect(error).not.toHaveBeenCalled();
          expect(success).not.toHaveBeenCalled();
          expect(consumerService.listConsumerGroupPage).not.toHaveBeenCalled();
          expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        },
      );
    },
  );
});
