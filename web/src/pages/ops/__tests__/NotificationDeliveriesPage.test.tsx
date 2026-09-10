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
    const user = userEvent.setup();
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
    const user = userEvent.setup();
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

  it('shows per-status counts for the loaded page and updates them with the active filter', async () => {
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
                id: 1,
                alertId: 1,
                alertTitle: 'Pending notification',
                channel: 'dingtalk',
                status: 'PENDING',
                attemptCount: 0,
                createdAt: '2026-08-23T10:00:00',
              },
              {
                id: 2,
                alertId: 2,
                alertTitle: 'Delivered notification',
                channel: 'email',
                status: 'DELIVERED',
                attemptCount: 1,
                createdAt: '2026-08-23T10:00:00',
                deliveredAt: '2026-08-23T10:01:00',
              },
              {
                id: 3,
                alertId: 3,
                alertTitle: 'First failed notification',
                channel: 'sms',
                status: 'FAILED',
                attemptCount: 5,
                createdAt: '2026-08-23T10:00:00',
                lastError: 'Webhook rejected the request',
              },
              {
                id: 4,
                alertId: 4,
                alertTitle: 'Second failed notification',
                channel: 'sms',
                status: 'FAILED',
                attemptCount: 5,
                createdAt: '2026-08-23T10:00:00',
                lastError: 'Webhook rejected the request',
              },
            ],
            total: 4,
            page: 1,
            size: 20,
          },
    );
    const user = userEvent.setup();
    render(
      <App>
        <LangProvider>
          <NotificationDeliveriesPage />
        </LangProvider>
      </App>,
    );

    await screen.findByText('Pending notification');

    // Counts describe only the loaded page, not the full result set.
    expect(screen.getByText('当前页:')).toBeInTheDocument();
    expect(screen.getByText('PENDING 1')).toBeInTheDocument();
    expect(screen.getByText('DELIVERED 1')).toBeInTheDocument();
    expect(screen.getByText('FAILED 2')).toBeInTheDocument();
    expect(screen.getByText('SENDING 0')).toBeInTheDocument();
    expect(screen.getByText('RETRY_WAIT 0')).toBeInTheDocument();

    // Switching the status filter reloads the page and the counts follow.
    await user.click(screen.getAllByRole('combobox')[1]);
    const deliveredOption = (await screen.findAllByText('DELIVERED')).find((element) =>
      element.closest('.ant-select-item-option'),
    );
    if (!deliveredOption) throw new Error('DELIVERED filter option not found');
    await user.click(deliveredOption);
    expect(await screen.findByText('DELIVERED 1')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText('PENDING 0')).toBeInTheDocument();
      expect(screen.getByText('FAILED 0')).toBeInTheDocument();
    });
  });
});
