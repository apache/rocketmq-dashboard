/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License");  you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Scout test: the system alerts page has no out-of-range page clamp.
 * alerts.tsx / audit.tsx / acl.tsx / UserManagement.tsx all re-query the
 * last valid page when the current page comes back empty with total > 0;
 * systemAlerts.tsx (load effect at ~line 198) stores the empty page as-is.
 */
import { App } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import {
  getCollectorStatus,
  listAlertSilences,
  listAlertSilencesPage,
  listSystemAlertsPage,
} from '../../../services/opsService';
import SystemAlertsPage from '../systemAlerts';

vi.mock('../../../services/opsService', () => ({
  acknowledgeAlert: vi.fn(),
  clearAcknowledgedAlerts: vi.fn(),
  listSystemAlertsPage: vi.fn(),
  getCollectorStatus: vi.fn().mockResolvedValue({ collectionInterval: 'PT30S' }),
  listAlertDeliveries: vi.fn().mockResolvedValue([]),
  listRelatedSystemAlerts: vi.fn().mockResolvedValue([]),
  retryAlertDelivery: vi.fn(),
  listAlertSilences: vi.fn(),
  listAlertSilencesPage: vi.fn(),
  createAlertSilence: vi.fn(),
  deleteAlertSilence: vi.fn(),
}));

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation(() => ({
      matches: false,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

const alert = (id: number) => ({
  id,
  level: 'error',
  title: `System alert ${String(id).padStart(2, '0')}`,
  description: `alert ${id}`,
  time: '2026-08-10 01:00',
  acknowledged: false,
});

describe('SystemAlertsPage out-of-range page clamp', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getCollectorStatus).mockResolvedValue({ collectionInterval: 'PT30S' });
    vi.mocked(listAlertSilences).mockResolvedValue([]);
    vi.mocked(listAlertSilencesPage).mockResolvedValue({ items: [], total: 0, page: 1, size: 10 });
  });

  it('re-queries a valid page when the current page becomes empty but total > 0', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    // First load reports 21 alerts (2 pages of 20). Opening page 2 hits a
    // shrunk result set: the server now only has 5 alerts, so page 2 is out
    // of range and comes back empty while total > 0.
    vi.mocked(listSystemAlertsPage).mockImplementation(async (query) =>
      query?.page === 2
        ? { items: [], total: 5, page: 2, size: 20 }
        : {
            items: [alert(1), alert(2), alert(3), alert(4), alert(5)],
            total: 5,
            page: 1,
            size: 20,
          },
    );
    vi.mocked(listSystemAlertsPage).mockImplementationOnce(async () => ({
      items: Array.from({ length: 21 }, (_, index) => alert(index + 1)),
      total: 21,
      page: 1,
      size: 20,
    }));

    render(
      <App>
        <LangProvider>
          <SystemAlertsPage />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('System alert 01')).toBeInTheDocument();

    const secondPage = document.querySelector('.ant-pagination-item-2');
    expect(secondPage).not.toBeNull();
    await user.click(secondPage as HTMLElement);

    // Page 2 came back empty with total 5: the page must clamp back to a
    // valid page and re-query (as alerts.tsx / audit.tsx do). On master the
    // list stays empty and no third request is fired.
    await waitFor(
      () =>
        expect(listSystemAlertsPage).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })),
      { timeout: 2000 },
    );
    expect(await screen.findByText('System alert 01')).toBeInTheDocument();
  });
});