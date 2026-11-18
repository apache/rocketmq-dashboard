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
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  NotificationDeliveryBulkRetryResult,
  NotificationDeliveryRecord,
} from '../../../api/ops';
import { LangProvider } from '../../../i18n/LangContext';
import { listInstances } from '../../../services/instanceService';
import {
  listAlertDeliveriesPage,
  retryAlertDeliveries,
  retryAlertDelivery,
} from '../../../services/opsService';
import NotificationDeliveriesPage from '../notificationDeliveries';

vi.mock('../../../services/instanceService', () => ({ listInstances: vi.fn() }));
vi.mock('../../../services/opsService', () => ({
  listAlertDeliveriesPage: vi.fn(),
  retryAlertDeliveries: vi.fn(),
  retryAlertDelivery: vi.fn(),
}));

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((complete, fail) => {
    resolve = complete;
    reject = fail;
  });
  return { promise, resolve, reject };
};

const failedDelivery = (id: number): NotificationDeliveryRecord => ({
  id,
  alertId: id + 100,
  alertTitle: `Failed notification ${id}`,
  channel: 'dingtalk',
  status: 'FAILED',
  attemptCount: 5,
  createdAt: '2026-08-23T10:00:00',
  lastError: `Webhook rejected delivery ${id}`,
});

const pageOf = (items: NotificationDeliveryRecord[]) => ({
  items,
  total: items.length,
  page: 1,
  size: 20,
});

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn(() => ({
      matches: false,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
});

describe('NotificationDeliveriesPage bulk retry details', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listInstances).mockResolvedValue([]);
    vi.mocked(listAlertDeliveriesPage).mockResolvedValue(
      pageOf([failedDelivery(7), failedDelivery(8)]),
    );
  });

  const startBulkAndOpenDetails = async (selectedId: number, filterFailed = true) => {
    const user = userEvent.setup();
    const bulk = deferred<Awaited<ReturnType<typeof retryAlertDeliveries>>>();
    const reload = deferred<Awaited<ReturnType<typeof listAlertDeliveriesPage>>>();
    vi.mocked(retryAlertDeliveries).mockReturnValueOnce(bulk.promise);
    render(
      <App>
        <LangProvider>
          <NotificationDeliveriesPage />
        </LangProvider>
      </App>,
    );

    await screen.findByText('Failed notification 7');
    if (filterFailed) {
      await user.click(screen.getAllByRole('combobox')[1]);
      await user.click(
        await screen.findByText('FAILED', { selector: '.ant-select-item-option-content' }),
      );
      await waitFor(() =>
        expect(listAlertDeliveriesPage).toHaveBeenLastCalledWith(
          expect.objectContaining({ status: 'FAILED' }),
        ),
      );
    }
    const bulkButton = screen.getByRole('button', { name: '重试当前页失败记录' });
    await waitFor(() => expect(bulkButton).toBeEnabled());
    vi.mocked(listAlertDeliveriesPage).mockReturnValueOnce(reload.promise);

    // Start the bulk action before opening the modal. Its still-pending response leaves the eye
    // action enabled, so this sequence never clicks through a modal mask.
    await user.click(bulkButton);
    expect(retryAlertDeliveries).toHaveBeenCalledExactlyOnceWith([7, 8]);
    const row = screen.getByText(`Failed notification ${selectedId}`).closest('tr')!;
    const viewDetails = within(row).getByRole('button', { name: '查看投递详情' });
    expect(viewDetails).toBeEnabled();
    await user.click(viewDetails);
    const dialog = await screen.findByRole('dialog', { name: '投递详情' });
    expect(within(dialog).getByText('FAILED')).toBeInTheDocument();
    return { bulk, reload, dialog };
  };

  const expectQueuedDetails = (dialog: HTMLElement, selectedId: number) => {
    expect(within(dialog).getByText('PENDING')).toBeInTheDocument();
    expect(within(dialog).getByText('0')).toBeInTheDocument();
    expect(within(dialog).queryByText('FAILED')).not.toBeInTheDocument();
    expect(
      within(dialog).queryByText(`Webhook rejected delivery ${selectedId}`),
    ).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: '重新投递' })).not.toBeInTheDocument();
  };

  const expectFailedDetails = (dialog: HTMLElement, selectedId: number) => {
    expect(within(dialog).getByText('FAILED')).toBeInTheDocument();
    expect(within(dialog).getByText('5')).toBeInTheDocument();
    expect(within(dialog).getByText(`Webhook rejected delivery ${selectedId}`)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '重新投递' })).toBeInTheDocument();
    expect(within(dialog).queryByText('PENDING')).not.toBeInTheDocument();
  };

  it('keeps a queued snapshot when the FAILED-filtered reload removes every successful row Test', async () => {
    const { bulk, reload, dialog } = await startBulkAndOpenDetails(7);
    await act(async () => bulk.resolve({ succeededIds: [7, 8], failures: {} }));
    await waitFor(() => expect(listAlertDeliveriesPage).toHaveBeenCalledTimes(3));
    await act(async () => reload.resolve(pageOf([])));

    expectQueuedDetails(dialog, 7);
    expect(retryAlertDelivery).not.toHaveBeenCalled();
    expect(listAlertDeliveriesPage).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'FAILED' }),
    );
  });

  it('updates only the successful delivery while a partial-result reload is pending Test', async () => {
    const { bulk, reload, dialog } = await startBulkAndOpenDetails(7);
    await act(async () =>
      bulk.resolve({ succeededIds: [7], failures: { 8: 'Delivery could not be retried' } }),
    );
    await waitFor(() => expect(listAlertDeliveriesPage).toHaveBeenCalledTimes(3));
    expectQueuedDetails(dialog, 7);

    await act(async () => reload.resolve(pageOf([failedDelivery(8)])));
    expectQueuedDetails(dialog, 7);
    expect(retryAlertDelivery).not.toHaveBeenCalled();
  });

  it.each<NotificationDeliveryBulkRetryResult & { name: string }>([
    {
      name: 'partial failure',
      succeededIds: [7],
      failures: { 8: 'Delivery could not be retried' },
    },
    {
      name: 'all failed',
      succeededIds: [],
      failures: { 7: 'Delivery could not be retried', 8: 'Delivery could not be retried' },
    },
  ])('keeps an unsuccessful delivery retryable for a $name result Test', async (result) => {
    const { bulk, reload, dialog } = await startBulkAndOpenDetails(8);
    await act(async () => bulk.resolve(result));
    await waitFor(() => expect(listAlertDeliveriesPage).toHaveBeenCalledTimes(3));
    // The failed ID must not be optimistically changed, even before the reload can restore it.
    expectFailedDetails(dialog, 8);
    await act(async () =>
      reload.resolve(
        pageOf(
          [failedDelivery(7), failedDelivery(8)].filter(
            (row) => !result.succeededIds.includes(row.id),
          ),
        ),
      ),
    );

    expectFailedDetails(dialog, 8);
    expect(within(dialog).getByRole('button', { name: '重新投递' })).toBeEnabled();
    expect(retryAlertDelivery).not.toHaveBeenCalled();
  });

  it('preserves the failed details when the bulk request is rejected Test', async () => {
    const { bulk, dialog } = await startBulkAndOpenDetails(7);
    await act(async () => bulk.reject(new Error('bulk retry unavailable')));

    expect(await screen.findByText('批量重新投递失败，请稍后重试')).toBeInTheDocument();
    expectFailedDetails(dialog, 7);
    expect(within(dialog).getByRole('button', { name: '重新投递' })).toBeEnabled();
    expect(listAlertDeliveriesPage).toHaveBeenCalledTimes(2);
    expect(retryAlertDelivery).not.toHaveBeenCalled();
  });

  it('prefers the authoritative refreshed delivery over its queued snapshot Test', async () => {
    // An unfiltered page can legitimately include the delivery after dispatch has completed.
    const { bulk, reload, dialog } = await startBulkAndOpenDetails(7, false);
    await act(async () => bulk.resolve({ succeededIds: [7, 8], failures: {} }));
    await waitFor(() => expect(listAlertDeliveriesPage).toHaveBeenCalledTimes(2));
    await act(async () =>
      reload.resolve(
        pageOf([{ ...failedDelivery(7), status: 'DELIVERED', attemptCount: 1, lastError: null }]),
      ),
    );

    expect(within(dialog).getByText('DELIVERED')).toBeInTheDocument();
    expect(within(dialog).getByText('1')).toBeInTheDocument();
    expect(within(dialog).queryByText('PENDING')).not.toBeInTheDocument();
    expect(within(dialog).queryByText('FAILED')).not.toBeInTheDocument();
    expect(within(dialog).queryByText('Webhook rejected delivery 7')).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: '重新投递' })).not.toBeInTheDocument();
  });
});
