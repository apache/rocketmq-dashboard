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
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DLQGroup } from '../../../api/message';
import { LangProvider } from '../../../i18n/LangContext';
import * as messageService from '../../../services/messageService';
import DLQPage from '../dlq';

vi.mock('../../../services/messageService', () => ({
  listDLQGroups: vi.fn(),
  resendDLQ: vi.fn(),
}));
vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([
    {
      id: 'instance-1',
      name: 'Instance 1',
      endpoint: 'namesrv-1:9876',
      type: 'DIRECT',
      remark: '',
      topicCount: 0,
      consumerGroupCount: 0,
      createdAt: '',
      updatedAt: '',
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
    vi.mocked(messageService.listDLQGroups).mockResolvedValue([dlqGroup]);
  });

  afterEach(() => {
    clickSpy.mockRestore();
    vi.clearAllMocks();
  });

  it('loads DLQ groups through the service layer', async () => {
    renderWithProviders(<DLQPage />);

    expect(await screen.findByText('cg-order')).toBeInTheDocument();
    expect(screen.getByText('%DLQ%cg-order')).toBeInTheDocument();
    expect(messageService.listDLQGroups).toHaveBeenCalledWith('instance-1');
  });

  it('surfaces unavailable DLQ provider errors when loading groups', async () => {
    vi.mocked(messageService.listDLQGroups).mockRejectedValue(
      new Error('DLQ provider is not configured'),
    );
    renderWithProviders(<DLQPage />);

    expect(await screen.findByText('DLQ provider is not configured')).toBeInTheDocument();
  });

  it('does not present unavailable DLQ statistics as an empty queue', async () => {
    vi.mocked(messageService.listDLQGroups).mockResolvedValue([unavailableDlqGroup]);
    renderWithProviders(<DLQPage />);

    const row = (await screen.findByText('cg-order')).closest('tr');
    if (!row) throw new Error('DLQ group row not found');

    expect(within(row).getByText('不可用')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: '重投消息' })).toBeDisabled();
    expect(within(row).getByRole('button', { name: '导出' })).toBeDisabled();
    expect(within(row).getByRole('checkbox')).toBeDisabled();
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

  it('exports the selected group summary as CSV', async () => {
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    await screen.findByText('cg-order');
    await user.click(screen.getByRole('button', { name: '导出' }));

    expect(createObjectURL).toHaveBeenCalledTimes(1);
    const blob = createObjectURL.mock.calls[0][0] as Blob;
    await expect(blob.text()).resolves.toContain('"cg-order","%DLQ%cg-order","7","3","ACTIVE"');
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:dlq');
  });

  it('exports summaries for the selected groups in one CSV file', async () => {
    vi.mocked(messageService.listDLQGroups).mockResolvedValue([dlqGroup, secondDlqGroup]);
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

  it('clears a selected group when refreshed data shows no dead-letter messages', async () => {
    vi.mocked(messageService.listDLQGroups)
      .mockResolvedValueOnce([dlqGroup])
      .mockResolvedValueOnce([{ ...dlqGroup, messageCount: 0 }]);
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    const orderRow = (await screen.findByText('cg-order')).closest('tr');
    if (!orderRow) throw new Error('DLQ group row not found');

    await user.click(within(orderRow).getByRole('checkbox'));
    await user.click(within(orderRow).getByRole('button', { name: '重投消息' }));
    await user.type(screen.getByPlaceholderText('输入目标 Topic 名称'), 'orders-retry');
    await user.click(screen.getByRole('button', { name: '确认重投' }));

    await waitFor(() => expect(messageService.listDLQGroups).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(within(orderRow).getByRole('checkbox')).toBeDisabled());
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
});
