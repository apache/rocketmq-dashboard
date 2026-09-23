/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { App } from 'antd';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import { listInstances } from '../../../services/instanceService';
import { listAlertDeliveriesPage, retryAlertDelivery } from '../../../services/opsService';
import NotificationDeliveriesPage from '../notificationDeliveries';

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([]),
}));
vi.mock('../../../services/opsService', () => ({
  listAlertDeliveriesPage: vi.fn(),
  retryAlertDeliveries: vi.fn(),
  retryAlertDelivery: vi.fn(),
}));

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => {
    resolve = complete;
  });
  return { promise, resolve };
};

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation(() => ({
      matches: false,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
});

describe('NotificationDeliveriesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listInstances).mockResolvedValue([]);
    vi.mocked(listAlertDeliveriesPage).mockResolvedValue({
      items: [
        {
          id: 7,
          alertId: 3,
          alertTitle: 'Broker disk usage',
          channel: 'dingtalk',
          status: 'FAILED',
          attemptCount: 5,
          createdAt: '2026-08-23T10:00:00',
          lastError: 'Webhook rejected the request',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
  });

  it('retries a failed delivery from the list and refreshes its status', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <LangProvider>
          <NotificationDeliveriesPage />
        </LangProvider>
      </App>,
    );

    await screen.findByText('Broker disk usage');
    await user.click(screen.getByRole('button', { name: '重新投递' }));

    await waitFor(() => expect(retryAlertDelivery).toHaveBeenCalledWith(7));
    await waitFor(() => expect(listAlertDeliveriesPage).toHaveBeenCalledTimes(2));
  });

  it('queues one retry when the action is clicked twice before rendering', async () => {
    const retry = deferred<void>();
    vi.mocked(retryAlertDelivery).mockImplementation(() => retry.promise);
    render(
      <App>
        <LangProvider>
          <NotificationDeliveriesPage />
        </LangProvider>
      </App>,
    );

    await screen.findByText('Broker disk usage');
    const retryButton = screen.getByRole('button', { name: '重新投递' });
    act(() => {
      retryButton.click();
      retryButton.click();
    });

    expect(retryAlertDelivery).toHaveBeenCalledTimes(1);
    retry.resolve();
  });

  it('refreshes a completed retry with the latest filters', async () => {
    const retry = deferred<void>();
    vi.mocked(retryAlertDelivery).mockImplementation(() => retry.promise);
    vi.mocked(listAlertDeliveriesPage).mockImplementation(async (query) =>
      query?.status === 'DELIVERED'
        ? {
            items: [
              {
                id: 8,
                alertId: 4,
                alertTitle: 'Delivered notification',
                channel: 'email',
                status: 'DELIVERED',
                attemptCount: 1,
                createdAt: '2026-08-23T10:00:00',
                deliveredAt: '2026-08-23T10:01:00',
              },
            ],
            total: 1,
            page: 1,
            size: 20,
          }
        : {
            items: [
              {
                id: 7,
                alertId: 3,
                alertTitle: 'Broker disk usage',
                channel: 'dingtalk',
                status: 'FAILED',
                attemptCount: 5,
                createdAt: '2026-08-23T10:00:00',
                lastError: 'Webhook rejected the request',
              },
            ],
            total: 1,
            page: 1,
            size: 20,
          },
    );
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <LangProvider>
          <NotificationDeliveriesPage />
        </LangProvider>
      </App>,
    );

    await screen.findByText('Broker disk usage');
    await user.click(screen.getByRole('button', { name: '重新投递' }));
    await user.click(screen.getAllByRole('combobox')[1]);
    await user.click(await screen.findByText('DELIVERED'));
    expect(await screen.findByText('Delivered notification')).toBeInTheDocument();

    retry.resolve();

    await waitFor(() => expect(listAlertDeliveriesPage).toHaveBeenCalledTimes(3));
    expect(screen.getByText('Delivered notification')).toBeInTheDocument();
    expect(screen.queryByText('Broker disk usage')).not.toBeInTheDocument();
    expect(listAlertDeliveriesPage).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'DELIVERED' }),
    );
  });

  it('surfaces a failed instance-list load with a retry instead of an empty filter', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    vi.mocked(listInstances)
      .mockRejectedValueOnce(new Error('the instance service is down'))
      .mockResolvedValueOnce([]);
    render(
      <App>
        <LangProvider>
          <NotificationDeliveriesPage />
        </LangProvider>
      </App>,
    );

    await screen.findByText('Broker disk usage');
    // An empty instance filter reads as "this deployment has no instances", which is not something a
    // failed request can establish. The retry is the affordance the silent catch never offered.
    const retry = await screen.findByRole('button', { name: /^重\s*试$/ });

    await user.click(retry);

    await waitFor(() => expect(listInstances).toHaveBeenCalledTimes(2));
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /^重\s*试$/ })).not.toBeInTheDocument(),
    );
  });

  it('searches alert titles and errors and returns to page one when the filter changes', async () => {
    vi.mocked(listAlertDeliveriesPage).mockImplementation(async (query) => ({
      items: [
        {
          id: 7,
          alertId: 3,
          alertTitle: 'Broker disk usage',
          channel: 'dingtalk',
          status: 'FAILED',
          attemptCount: 5,
          createdAt: '2026-08-23T10:00:00',
          lastError: 'Webhook rejected the request',
        },
      ],
      total: 25,
      page: query?.page ?? 1,
      size: 20,
    }));
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <LangProvider>
          <NotificationDeliveriesPage />
        </LangProvider>
      </App>,
    );

    await screen.findByText('Broker disk usage');
    const secondPage = document.querySelector('.ant-pagination-item-2') as HTMLElement;
    await user.click(secondPage);
    await waitFor(() =>
      expect(listAlertDeliveriesPage).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 })),
    );

    await user.type(screen.getByPlaceholderText('搜索告警标题或失败原因'), 'WebHook{Enter}');
    await waitFor(() =>
      expect(listAlertDeliveriesPage).toHaveBeenLastCalledWith(
        expect.objectContaining({ search: 'WebHook', page: 1 }),
      ),
    );

    await user.clear(screen.getByPlaceholderText('搜索告警标题或失败原因'));
    await waitFor(() =>
      expect(listAlertDeliveriesPage).toHaveBeenLastCalledWith(
        expect.objectContaining({ search: undefined, page: 1 }),
      ),
    );
  });

  it('forwards the selected local delivery time range as UTC bounds', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(
      <App>
        <LangProvider>
          <NotificationDeliveriesPage />
        </LangProvider>
      </App>,
    );

    await screen.findByText('Broker disk usage');
    const inputs = screen.getAllByLabelText('投递时间范围');
    await user.type(inputs[0], '2026-09-01 14:00:00');
    await user.keyboard('{Enter}');
    await user.type(inputs[1], '2026-09-01 15:00:00');
    await user.keyboard('{Enter}');

    await waitFor(() =>
      expect(listAlertDeliveriesPage).toHaveBeenLastCalledWith(
        expect.objectContaining({
          from: new Date('2026-09-01T14:00:00').toISOString().replace('Z', ''),
          to: new Date('2026-09-01T15:00:00').toISOString().replace('Z', ''),
          page: 1,
        }),
      ),
    );
  });
});
