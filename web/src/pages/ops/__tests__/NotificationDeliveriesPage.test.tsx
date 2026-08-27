/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { App } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
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
  retryAlertDelivery: vi.fn(),
}));

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
});
