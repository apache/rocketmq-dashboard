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

import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../i18n/LangContext';
import NotificationDeliveryAnalyticsDrawer from '../NotificationDeliveryAnalyticsDrawer';
import { downloadCsv } from '../../utils/download';

const serviceMocks = vi.hoisted(() => ({ listAlertDeliveriesPage: vi.fn() }));
vi.mock('../../services/opsService', () => serviceMocks);
vi.mock('../../utils/download', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../utils/download')>();
  return { ...actual, downloadCsv: vi.fn() };
});

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

const records = [
  {
    id: 1,
    alertId: 10,
    alertTitle: 'Broker unavailable',
    channel: 'dingtalk',
    status: 'DELIVERED' as const,
    attemptCount: 2,
    createdAt: '2026-09-04T00:00:00Z',
    deliveredAt: '2026-09-04T00:00:02Z',
    instanceId: 'production',
  },
  {
    id: 2,
    alertId: 11,
    alertTitle: 'Consumer lag',
    channel: 'email',
    status: 'FAILED' as const,
    attemptCount: 3,
    createdAt: '2026-09-04T00:01:00Z',
    lastError: 'SMTP 503 denied',
    instanceId: 'production',
  },
];

const renderDrawer = () =>
  render(
    <App>
      <LangProvider>
        <NotificationDeliveryAnalyticsDrawer open onClose={vi.fn()} />
      </LangProvider>
    </App>,
  );

describe('NotificationDeliveryAnalyticsDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    serviceMocks.listAlertDeliveriesPage.mockResolvedValue({
      items: records,
      total: records.length,
      page: 1,
      size: 100,
    });
  });

  it('loads the complete first page and renders channel analytics', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载分析' }));
    await waitFor(() =>
      expect(serviceMocks.listAlertDeliveriesPage).toHaveBeenCalledWith({ page: 1, pageSize: 100 }),
    );
    expect(await screen.findByText('渠道概览')).toBeInTheDocument();
    expect(screen.getAllByText('终态成功率').length).toBeGreaterThan(0);
  });

  it('loads subsequent pages until the reported total is reached', async () => {
    serviceMocks.listAlertDeliveriesPage
      .mockResolvedValueOnce({ items: [records[0]], total: 2, page: 1, size: 100 })
      .mockResolvedValueOnce({ items: [records[1]], total: 2, page: 2, size: 100 });
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载分析' }));
    await waitFor(() => expect(serviceMocks.listAlertDeliveriesPage).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('渠道概览')).toBeInTheDocument();
  });

  it('exports only records matching the current text filter', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载分析' }));
    await screen.findByText('渠道概览');
    await user.type(screen.getByLabelText('搜索告警、实例或错误'), 'consumer lag');
    await user.click(screen.getByRole('button', { name: '导出分析结果' }));
    const [filename, csv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(filename).toBe('rocketmq-notification-delivery-analysis.csv');
    expect(csv).toContain('Consumer lag');
    expect(csv).not.toContain('Broker unavailable');
  });

  it('keeps a failed full-history load retryable', async () => {
    serviceMocks.listAlertDeliveriesPage.mockRejectedValue(new Error('offline'));
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载分析' }));
    expect(await screen.findByText('投递历史加载失败，请稍后重试')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '加载分析' })).toBeEnabled();
  });
});
