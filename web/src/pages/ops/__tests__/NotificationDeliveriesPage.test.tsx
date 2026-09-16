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
import { downloadCsv } from '../../../utils/download';
import NotificationDeliveriesPage from '../notificationDeliveries';

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([]),
}));
vi.mock('../../../services/opsService', () => ({
  listAlertDeliveriesPage: vi.fn(),
  retryAlertDeliveries: vi.fn(),
  retryAlertDelivery: vi.fn(),
}));
vi.mock('../../../utils/download', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../utils/download')>();
  return {
    ...actual,
    downloadCsv: vi.fn(),
  };
});

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

  it('exports the filtered deliveries across pages as CSV', async () => {
    vi.mocked(listAlertDeliveriesPage).mockImplementation(async (query) => {
      if (query?.page === 1 && query.pageSize === 100) {
        return {
          items: Array.from({ length: 100 }, (_, index) => ({
            id: index + 1,
            alertId: 1,
            alertTitle: `Alert ${index + 1}`,
            channel: 'email',
            status: 'FAILED',
            attemptCount: 1,
            createdAt: '2026-08-23T10:00:00',
            lastError: 'boom',
          })),
          total: 120,
          page: 1,
          size: 100,
        };
      }
      if (query?.page === 2 && query.pageSize === 100) {
        return {
          items: Array.from({ length: 20 }, (_, index) => ({
            id: 101 + index,
            alertId: 1,
            alertTitle: `Alert ${101 + index}`,
            channel: 'email',
            status: 'FAILED',
            attemptCount: 1,
            createdAt: '2026-08-23T10:00:00',
            lastError: 'boom',
          })),
          total: 120,
          page: 2,
          size: 100,
        };
      }
      return {
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
      };
    });
    const user = userEvent.setup();
    render(
      <App>
        <LangProvider>
          <NotificationDeliveriesPage />
        </LangProvider>
      </App>,
    );

    await screen.findByText('Broker disk usage');
    await user.click(screen.getByRole('button', { name: '导出 CSV' }));

    await waitFor(() => expect(downloadCsv).toHaveBeenCalledTimes(1));
    const [filename, csv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(filename).toMatch(/^rocketmq-notification-deliveries-\d{4}-\d{2}-\d{2}\.csv$/);
    expect(csv).toContain('Delivery ID');
    expect(csv).toContain('Alert 1');
    expect(csv).toContain('Alert 120');
    expect(csv).not.toContain('messageContent');
    expect(listAlertDeliveriesPage).toHaveBeenCalledWith(
      expect.objectContaining({ page: 2, pageSize: 100 }),
    );
  });
});
