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
// Scout test: the notification deliveries page has no out-of-range page
// clamp. alerts.tsx / audit.tsx / acl.tsx / UserManagement.tsx all re-query
// the last valid page when the current page becomes empty with total > 0;
// notificationDeliveries.tsx renders the empty page and leaves the user
// stuck on it.
import { App } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import { listInstances } from '../../../services/instanceService';
import { listAlertDeliveriesPage } from '../../../services/opsService';
import NotificationDeliveriesPage from '../notificationDeliveries';

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([]),
}));
vi.mock('../../../services/opsService', () => ({
  listAlertDeliveriesPage: vi.fn(),
  retryAlertDeliveries: vi.fn(),
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

const delivery = (id: number) => ({
  id,
  alertId: id,
  alertTitle: `Delivery row ${String(id).padStart(2, '0')}`,
  channel: 'dingtalk',
  status: 'DELIVERED' as const,
  attemptCount: 1,
  createdAt: '2026-08-23T10:00:00',
  deliveredAt: '2026-08-23T10:01:00',
});

describe('NotificationDeliveriesPage out-of-range page clamp', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listInstances).mockResolvedValue([]);
  });

  it('re-queries a valid page when the current page becomes empty but total > 0', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    // Page 1 reports 21 delivered records (2 pages of 20). When the user opens
    // page 2, the server has cleaned the delivered records up: page 2 is now
    // out of range (total shrank to 5, last valid page is 1).
    vi.mocked(listAlertDeliveriesPage).mockImplementation(async (query) =>
      query?.page === 2
        ? { items: [], total: 5, page: 2, size: 20 }
        : {
            items: [delivery(1), delivery(2), delivery(3), delivery(4), delivery(5)],
            total: 5,
            page: 1,
            size: 20,
          },
    );
    // First load: pretend there were 21 records so page 2 exists to click.
    vi.mocked(listAlertDeliveriesPage).mockImplementationOnce(async () => ({
      items: Array.from({ length: 21 }, (_, index) => delivery(index + 1)),
      total: 21,
      page: 1,
      size: 20,
    }));

    render(
      <App>
        <LangProvider>
          <NotificationDeliveriesPage />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('Delivery row 01')).toBeInTheDocument();

    const secondPage = document.querySelector('.ant-pagination-item-2');
    expect(secondPage).not.toBeNull();
    await user.click(secondPage as HTMLElement);

    // Page 2 came back empty with total 5: the page must be clamped back to a
    // valid page and re-queried (alerts.tsx does exactly this). On master no
    // third request is fired and the table stays empty.
    await waitFor(
      () =>
        expect(listAlertDeliveriesPage).toHaveBeenLastCalledWith(
          expect.objectContaining({ page: 1 }),
        ),
      { timeout: 2000 },
    );
    expect(await screen.findByText('Delivery row 01')).toBeInTheDocument();
  });
});
