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
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DLQGroup, DLQGroupPage, DLQResendResult } from '../../../api/message';
import { LangProvider } from '../../../i18n/LangContext';
import * as messageService from '../../../services/messageService';
import DLQPage from '../dlq';

vi.mock('../../../services/messageService', () => ({
  listDLQGroups: vi.fn(),
  resendDLQ: vi.fn(),
  exportDLQMessages: vi.fn(),
}));
vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([
    {
      id: 1,
      name: 'instance-1',
      endpoint: 'namesrv-1:9876',
      type: 'DIRECT',
      remark: '',
      topicCount: 0,
      consumerGroupCount: 0,
      gmtCreate: '',
      gmtModified: '',
    },
    {
      id: 2,
      name: 'instance-2',
      endpoint: 'namesrv-2:9876',
      type: 'DIRECT',
      remark: '',
      topicCount: 0,
      consumerGroupCount: 0,
      gmtCreate: '',
      gmtModified: '',
    },
  ]),
}));

const dlqGroup: DLQGroup = {
  groupName: 'cg-order',
  dlqTopic: '%DLQ%cg-order',
  messageCount: 7,
  lastEnqueueTime: '2026-07-24T10:00:00Z',
  retryCount: 3,
  status: 'ACTIVE',
};

const unavailableDlqGroup: DLQGroup = {
  ...dlqGroup,
  messageCount: 0,
  statsAvailable: false,
  status: 'UNAVAILABLE',
};

const secondDlqGroup: DLQGroup = {
  groupName: '-cg-"payment"',
  dlqTopic: '%DLQ%cg-payment',
  messageCount: 2,
  lastEnqueueTime: '2026-07-24T11:00:00Z',
  retryCount: 1,
  status: 'ACTIVE',
};

const pageOf = (items: DLQGroup[]): DLQGroupPage => ({
  items,
  total: items.length,
  page: 1,
  size: 20,
});

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter initialEntries={['/instance/instance-1/dlq']}>{ui}</MemoryRouter>
      </LangProvider>
    </App>,
  );

describe('DLQ page', () => {
  let createObjectURL: ReturnType<typeof vi.fn>;
  let revokeObjectURL: ReturnType<typeof vi.fn>;
  let clickSpy: ReturnType<typeof vi.spyOn>;

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

  beforeEach(() => {
    vi.mocked(messageService.listDLQGroups).mockReset();
    vi.mocked(messageService.resendDLQ).mockReset();
    vi.mocked(messageService.exportDLQMessages).mockReset();
    createObjectURL = vi.fn().mockReturnValue('blob:dlq');
    revokeObjectURL = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: createObjectURL,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectURL,
    });
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.mocked(messageService.listDLQGroups).mockResolvedValue(pageOf([dlqGroup]));
  });

  afterEach(() => {
    clickSpy.mockRestore();
    vi.clearAllMocks();
  });

  it('loads DLQ groups through the service layer', async () => {
    renderWithProviders(<DLQPage />);

    expect(await screen.findByText('cg-order')).toBeInTheDocument();
    expect(screen.getByText('%DLQ%cg-order')).toBeInTheDocument();
    expect(messageService.listDLQGroups).toHaveBeenCalledWith('instance-1', undefined, 1, 20);
  });

  it('resets pagination to the first page when the search term changes', async () => {
    vi.mocked(messageService.listDLQGroups)
      .mockResolvedValueOnce({
        items: [dlqGroup],
        total: 40,
        page: 1,
        size: 20,
      })
      .mockResolvedValueOnce({
        items: [dlqGroup],
        total: 40,
        page: 2,
        size: 20,
      })
      .mockResolvedValueOnce({
        items: [dlqGroup],
        total: 1,
        page: 1,
        size: 20,
      });
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    await screen.findByText('cg-order');
    await user.click(screen.getByTitle('2'));
    await waitFor(() =>
      expect(messageService.listDLQGroups).toHaveBeenLastCalledWith('instance-1', undefined, 2, 20),
    );

    const searchInput = screen.getByPlaceholderText('搜索 Group 名称或 DLQ Topic');
    await user.type(searchInput, 'ord');

    await waitFor(() =>
      expect(messageService.listDLQGroups).toHaveBeenLastCalledWith('instance-1', 'ord', 1, 20),
    );
  });

  it('surfaces unavailable DLQ provider errors when loading groups', async () => {
    vi.mocked(messageService.listDLQGroups).mockRejectedValue(
      new Error('DLQ provider is not configured'),
    );
    renderWithProviders(<DLQPage />);

    expect(await screen.findByText('DLQ provider is not configured')).toBeInTheDocument();
  });

  it('does not present unavailable DLQ statistics as an empty queue', async () => {
    vi.mocked(messageService.listDLQGroups).mockResolvedValue(pageOf([unavailableDlqGroup]));
    renderWithProviders(<DLQPage />);

    const row = (await screen.findByText('cg-order')).closest('tr');
    if (!row) throw new Error('DLQ group row not found');

    expect(within(row).getByText('不可用')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: '重投消息' })).toBeDisabled();
    expect(within(row).getByRole('button', { name: '导出' })).toBeDisabled();
    expect(within(row).getByRole('checkbox')).toBeDisabled();
  });

  it('sorts DLQ rows with missing enqueue timestamps', async () => {
    vi.mocked(messageService.listDLQGroups).mockResolvedValue(
      pageOf([dlqGroup, { ...secondDlqGroup, lastEnqueueTime: null }]),
    );
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    await screen.findByText('cg-order');
    await user.click(screen.getByText('最近入队时间'));

    expect(screen.getByText('-')).toBeInTheDocument();
  });

  it('opens a detail dialog with the selected group metadata', async () => {
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    await screen.findByText('cg-order');
    await user.click(screen.getByRole('button', { name: /查看详情/ }));

    expect(await screen.findByText('死信队列详情')).toBeInTheDocument();
    expect(screen.getAllByText('cg-order')).toHaveLength(2);
    expect(screen.getAllByText('%DLQ%cg-order')).toHaveLength(2);
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
  });

  it('exports the dead-letter messages of a group as JSON', async () => {
    vi.mocked(messageService.exportDLQMessages).mockResolvedValue(
      new Blob(['[{"msgId":"m1","topic":"%DLQ%cg-order","queueId":0,"offset":5}]'], {
        type: 'application/json',
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    await screen.findByText('cg-order');
    await user.click(screen.getByRole('button', { name: '导出' }));

    expect(messageService.exportDLQMessages).toHaveBeenCalledTimes(1);
    const exportParams = vi.mocked(messageService.exportDLQMessages).mock.calls[0][0];
    expect(exportParams.instanceId).toBe('instance-1');
    expect(exportParams.groupName).toBe('cg-order');
    expect(typeof exportParams.startTime).toBe('number');
    expect(typeof exportParams.endTime).toBe('number');
    // Default export window is the last day, mirroring the visible range picker.
    expect(exportParams.endTime! - exportParams.startTime!).toBeGreaterThan(23 * 3600_000);
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    const blob = createObjectURL.mock.calls[0][0] as Blob;
    await expect(blob.text()).resolves.toContain('"msgId":"m1"');
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:dlq');
  });

  it('exports summaries for the selected groups in one CSV file', async () => {
    vi.mocked(messageService.listDLQGroups).mockResolvedValue(pageOf([dlqGroup, secondDlqGroup]));
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    const batchExport = screen.getByRole('button', { name: /批量导出/ });
    expect(batchExport).toBeDisabled();

    const orderRow = (await screen.findByText('cg-order')).closest('tr');
    const paymentRow = screen.getByText('-cg-"payment"').closest('tr');
    if (!orderRow || !paymentRow) throw new Error('DLQ group row not found');

    await user.click(within(orderRow).getByRole('checkbox'));
    await user.click(within(paymentRow).getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /批量导出/ }));

    expect(createObjectURL).toHaveBeenCalledTimes(1);
    const blob = createObjectURL.mock.calls[0][0] as Blob;
    const csv = await blob.text();
    expect(csv).toContain('"cg-order","%DLQ%cg-order","7","3","ACTIVE"');
    expect(csv).toContain('"\'-cg-""payment""","%DLQ%cg-payment","2","1","ACTIVE"');
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:dlq');
  });

  it('neutralizes formulas hidden behind a leading line feed in CSV summary exports', async () => {
    vi.mocked(messageService.listDLQGroups).mockResolvedValue(
      pageOf([
        {
          ...dlqGroup,
          groupName: '\n=1+1',
          dlqTopic: '%DLQ%formula',
        },
      ]),
    );
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    const row = (await screen.findByText('%DLQ%formula')).closest('tr');
    if (!row) throw new Error('DLQ group row not found');
    await user.click(within(row).getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /批量导出/ }));

    const blob = createObjectURL.mock.calls[0][0] as Blob;
    await expect(blob.text()).resolves.toContain('"\'\n=1+1","%DLQ%formula"');
  });

  it('clears a selected group when refreshed data shows no dead-letter messages', async () => {
    vi.mocked(messageService.listDLQGroups)
      .mockResolvedValueOnce(pageOf([dlqGroup]))
      .mockResolvedValueOnce(pageOf([{ ...dlqGroup, messageCount: 0 }]));
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    const orderRow = (await screen.findByText('cg-order')).closest('tr');
    if (!orderRow) throw new Error('DLQ group row not found');

    await user.click(within(orderRow).getByRole('checkbox'));
    await user.click(within(orderRow).getByRole('button', { name: '重投消息' }));
    await user.type(screen.getByPlaceholderText('输入目标 Topic 名称'), 'orders-retry');
    await user.click(screen.getByRole('button', { name: '确认重投' }));

    await waitFor(() => expect(messageService.listDLQGroups).toHaveBeenCalledTimes(2));
    await waitFor(() => {
      // The refresh clears the group list before repopulating it, remounting
      // the row; look the row up freshly instead of reusing the pre-refresh
      // reference, which may point at a detached node.
      const refreshedRow = screen.getAllByText('cg-order')[0]?.closest('tr');
      if (!refreshedRow) throw new Error('DLQ group row not found after refresh');
      expect(within(refreshedRow).getByRole('checkbox')).toBeDisabled();
    });
    expect(screen.getByRole('button', { name: /批量导出/ })).toBeDisabled();
  });

  it('surfaces unavailable DLQ provider errors when retry submission fails', async () => {
    vi.mocked(messageService.resendDLQ).mockRejectedValue(
      new Error('DLQ provider is not configured'),
    );
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    const orderRow = (await screen.findByText('cg-order')).closest('tr');
    if (!orderRow) throw new Error('DLQ group row not found');

    await user.click(within(orderRow).getByRole('button', { name: '重投消息' }));
    await user.type(screen.getByPlaceholderText('输入目标 Topic 名称'), 'orders-retry');
    await user.click(screen.getByRole('button', { name: '确认重投' }));

    expect(await screen.findByText('DLQ provider is not configured')).toBeInTheDocument();
  });

  it('warns when DLQ resend scans only part of the available queues', async () => {
    vi.mocked(messageService.resendDLQ).mockResolvedValue({
      matched: 3,
      resent: 3,
      failed: 0,
      outcome: 'PARTIAL',
      scanIncomplete: true,
      failedQueueCount: 1,
    });
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    const orderRow = (await screen.findByText('cg-order')).closest('tr');
    if (!orderRow) throw new Error('DLQ group row not found');

    await user.click(within(orderRow).getByRole('button', { name: '重投消息' }));
    await user.type(screen.getByPlaceholderText('输入目标 Topic 名称'), 'orders-retry');
    await user.click(screen.getByRole('button', { name: '确认重投' }));

    expect(
      await screen.findByText('重投扫描不完整：1 个队列无法扫描，已重投 3 条'),
    ).toBeInTheDocument();
  });

  it('clears retry state before loading groups for a newly selected instance', async () => {
    let resolveSecondInstance!: (page: DLQGroupPage) => void;
    vi.mocked(messageService.listDLQGroups)
      .mockResolvedValueOnce(pageOf([dlqGroup]))
      .mockImplementationOnce(
        () =>
          new Promise<DLQGroupPage>((resolve) => {
            resolveSecondInstance = resolve;
          }),
      );
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    const orderRow = (await screen.findByText('cg-order')).closest('tr');
    if (!orderRow) throw new Error('DLQ group row not found');

    await user.click(within(orderRow).getByRole('button', { name: '重投消息' }));
    expect(await screen.findByText('重投死信消息')).toBeInTheDocument();

    await user.click(screen.getAllByRole('combobox')[0]);
    await user.click(
      await screen.findByText('instance-2', { selector: '.ant-select-item-option-content' }),
    );

    await waitFor(() => {
      expect(messageService.listDLQGroups).toHaveBeenLastCalledWith('instance-2', undefined, 1, 20);
    });
    await waitFor(() => {
      expect(screen.queryByRole('row', { name: /cg-order/ })).not.toBeInTheDocument();
    });

    resolveSecondInstance(pageOf([secondDlqGroup]));
    expect(await screen.findByText('-cg-"payment"')).toBeInTheDocument();
  });

  it('ignores a retry completion from the previously selected instance', async () => {
    let resolveRetry!: (result: DLQResendResult) => void;
    vi.mocked(messageService.resendDLQ).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveRetry = resolve;
        }),
    );
    vi.mocked(messageService.listDLQGroups)
      .mockResolvedValueOnce(pageOf([dlqGroup]))
      .mockResolvedValueOnce(pageOf([secondDlqGroup]));
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    const firstRow = (await screen.findByText('cg-order')).closest('tr');
    if (!firstRow) throw new Error('DLQ group row not found');
    await user.click(within(firstRow).getByRole('button', { name: '重投消息' }));
    await user.type(screen.getByPlaceholderText('输入目标 Topic 名称'), 'orders-retry');
    await user.click(screen.getByRole('button', { name: '确认重投' }));

    await user.click(screen.getAllByRole('combobox')[0]);
    await user.click(
      await screen.findByText('instance-2', { selector: '.ant-select-item-option-content' }),
    );
    const secondRow = (await screen.findByText('-cg-"payment"')).closest('tr');
    if (!secondRow) throw new Error('second DLQ group row not found');
    await user.click(within(secondRow).getByRole('button', { name: '重投消息' }));
    let retryDialog = screen.getByText('重投死信消息').closest('.ant-modal');
    if (!retryDialog) throw new Error('retry dialog not found');
    expect(within(retryDialog as HTMLElement).getByText('-cg-"payment"')).toBeInTheDocument();

    await act(async () => resolveRetry({ matched: 7, resent: 7, failed: 0, outcome: 'SUCCESS' }));

    retryDialog = screen.getByText('重投死信消息').closest('.ant-modal');
    if (!retryDialog) throw new Error('retry dialog was closed by the stale request');
    expect(within(retryDialog as HTMLElement).getByText('-cg-"payment"')).toBeInTheDocument();
    expect(messageService.listDLQGroups).toHaveBeenCalledTimes(2);
  });
});
