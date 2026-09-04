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
import SystemAlertIncidentExplorerDrawer from '../SystemAlertIncidentExplorerDrawer';
import { downloadCsv } from '../../utils/download';

const serviceMocks = vi.hoisted(() => ({ listSystemAlertsPage: vi.fn() }));
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
    level: 'warning',
    title: 'Consumer lag',
    description: 'lagging',
    time: '2026-09-04T00:00:00Z',
    acknowledged: false,
    domain: 'BUSINESS' as const,
    ruleId: 10,
    fingerprint: 'lag-orders',
    transition: 'FIRING' as const,
    instanceId: 'production',
  },
  {
    id: 2,
    level: 'warning',
    title: 'Consumer lag',
    description: 'recovered',
    time: '2026-09-04T00:10:00Z',
    acknowledged: true,
    domain: 'BUSINESS' as const,
    ruleId: 10,
    fingerprint: 'lag-orders',
    transition: 'RESOLVED' as const,
    instanceId: 'production',
  },
];

const renderDrawer = () =>
  render(
    <App>
      <LangProvider>
        <SystemAlertIncidentExplorerDrawer open onClose={vi.fn()} />
      </LangProvider>
    </App>,
  );

describe('SystemAlertIncidentExplorerDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    serviceMocks.listSystemAlertsPage.mockResolvedValue({
      items: records,
      total: 2,
      page: 1,
      size: 100,
    });
  });

  it('loads a complete snapshot and correlates its timeline', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载时间线' }));
    await waitFor(() =>
      expect(serviceMocks.listSystemAlertsPage).toHaveBeenCalledWith({ page: 1, pageSize: 100 }),
    );
    expect(await screen.findByText('最长持续时间')).toBeInTheDocument();
    expect(screen.getAllByText('10 min').length).toBeGreaterThan(0);
  });

  it('loads subsequent pages until the total is reached', async () => {
    serviceMocks.listSystemAlertsPage
      .mockResolvedValueOnce({ items: [records[0]], total: 2, page: 1, size: 100 })
      .mockResolvedValueOnce({ items: [records[1]], total: 2, page: 2, size: 100 });
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载时间线' }));
    await waitFor(() => expect(serviceMocks.listSystemAlertsPage).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.getAllByText('10 min').length).toBeGreaterThan(0));
  });

  it('exports visible incidents after filtering', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载时间线' }));
    await screen.findByText('最长持续时间');
    await user.type(screen.getByLabelText('搜索标题、实例或关联键'), 'consumer');
    await user.click(screen.getByRole('button', { name: '导出事件组' }));
    const [filename, csv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(filename).toBe('rocketmq-system-alert-incidents.csv');
    expect(csv).toContain('Consumer lag');
    expect(csv).toContain('lag-orders');
  });

  it('keeps full-history failures retryable', async () => {
    serviceMocks.listSystemAlertsPage.mockRejectedValue(new Error('offline'));
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载时间线' }));
    expect(await screen.findByText('完整告警历史加载失败，请稍后重试')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '加载时间线' })).toBeEnabled();
  });
});
