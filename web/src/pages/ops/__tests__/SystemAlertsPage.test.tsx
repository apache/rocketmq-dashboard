/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { App } from 'antd';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../../i18n/languagePreference';
import { formatUtcDateTime } from '../../../utils/format';
import { downloadCsv } from '../../../utils/download';
import {
  acknowledgeAlert,
  createAlertSilence,
  listAlertDeliveries,
  listRelatedSystemAlerts,
  retryAlertDelivery,
  deleteAlertSilence,
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

vi.mock('../../../utils/download', async () => {
  const actual =
    await vi.importActual<typeof import('../../../utils/download')>('../../../utils/download');
  return { ...actual, downloadCsv: vi.fn() };
});

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

const renderPage = () =>
  render(
    <App>
      <LangProvider>
        <SystemAlertsPage />
      </LangProvider>
    </App>,
  );

describe('SystemAlertsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listSystemAlertsPage).mockResolvedValue({
      items: [
        {
          id: 1,
          level: 'error',
          title: 'Broker unavailable',
          description: 'broker a',
          time: '2026-08-10 01:00',
          acknowledged: false,
        },
        {
          id: 2,
          level: 'warning',
          title: 'Consumer lag',
          description: 'consumer b',
          time: '2026-08-10 01:01',
          acknowledged: false,
        },
      ],
      total: 2,
      page: 1,
      size: 20,
    });
    vi.mocked(listAlertSilences).mockResolvedValue([]);
    vi.mocked(listAlertSilencesPage).mockResolvedValue({ items: [], total: 0, page: 1, size: 10 });
  });

  it('finishes an export when a later page is empty after the result set shrinks', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    vi.mocked(listSystemAlertsPage)
      .mockResolvedValueOnce({
        items: [
          {
            id: 1,
            level: 'error',
            title: 'Broker unavailable',
            description: 'broker a',
            time: '2026-08-10 01:00',
            acknowledged: false,
          },
        ],
        total: 1,
        page: 1,
        size: 20,
      })
      .mockResolvedValueOnce({
        items: [
          {
            id: 1,
            level: 'error',
            title: 'Broker unavailable',
            description: 'broker a',
            time: '2026-08-10 01:00',
            acknowledged: false,
          },
        ],
        total: 200,
        page: 1,
        size: 100,
      })
      .mockResolvedValueOnce({ items: [], total: 200, page: 2, size: 100 });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Broker unavailable');

    await user.click(screen.getByRole('button', { name: 'Export CSV' }));

    await waitFor(() => expect(downloadCsv).toHaveBeenCalledTimes(1));
    expect(listSystemAlertsPage).toHaveBeenCalledTimes(3);
    expect(listSystemAlertsPage).toHaveBeenLastCalledWith({ page: 2, pageSize: 100 });
  });

  it('renders an alert with an unknown backend level', async () => {
    vi.mocked(listSystemAlertsPage).mockReset();
    vi.mocked(listSystemAlertsPage).mockResolvedValue({
      items: [
        {
          id: 3,
          level: 'critical',
          title: 'Critical broker condition',
          description: 'A newer backend emitted this level',
          time: '2026-08-10 01:00',
          acknowledged: false,
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });

    renderPage();

    expect(await screen.findByText('Critical broker condition')).toBeInTheDocument();
    expect(screen.getByText('critical')).toBeInTheDocument();
    expect(screen.getByText('A newer backend emitted this level')).toBeInTheDocument();
  });

  it('formats event timestamps and native transition labels', async () => {
    vi.mocked(listSystemAlertsPage).mockResolvedValue({
      items: [
        {
          id: 9,
          level: 'warning',
          title: 'Disk recovered',
          description: 'disk usage returned to normal',
          time: '2026-08-23T10:35:38.590731',
          transition: 'RESOLVED',
          acknowledged: true,
          acknowledgedBy: 'admin',
          acknowledgedAt: '2026-08-23T10:40:00.000000',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
    renderPage();

    expect(
      await screen.findByText(formatUtcDateTime('2026-08-23T10:35:38.590731')),
    ).toBeInTheDocument();
    expect(screen.getByText('已恢复')).toBeInTheDocument();
    expect(
      screen.getByText(`确认：admin · ${formatUtcDateTime('2026-08-23T10:40:00.000000')}`),
    ).toBeInTheDocument();
  });

  it('filters backend alert levels case-insensitively', async () => {
    vi.mocked(listSystemAlertsPage).mockReset();
    vi.mocked(listSystemAlertsPage)
      .mockResolvedValueOnce({
        items: [
          {
            id: 4,
            level: 'Error',
            title: 'Mixed-case error',
            description: 'error',
            time: '2026-08-10 01:00',
            acknowledged: false,
          },
          {
            id: 5,
            level: 'WARNING',
            title: 'Mixed-case warning',
            description: 'warning',
            time: '2026-08-10 01:01',
            acknowledged: false,
          },
        ],
        total: 2,
        page: 1,
        size: 20,
      })
      .mockResolvedValueOnce({
        items: [
          {
            id: 4,
            level: 'Error',
            title: 'Mixed-case error',
            description: 'error',
            time: '2026-08-10 01:00',
            acknowledged: false,
          },
        ],
        total: 1,
        page: 1,
        size: 20,
      });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Mixed-case error');

    await user.click(screen.getByRole('button', { name: /严重/ }));

    await waitFor(() => {
      expect(screen.getByText('Mixed-case error')).toBeInTheDocument();
      expect(screen.queryByText('Mixed-case warning')).not.toBeInTheDocument();
    });
    await waitFor(() =>
      expect(listSystemAlertsPage).toHaveBeenLastCalledWith({
        level: 'error',
        page: 1,
        pageSize: 20,
      }),
    );
  });

  it('forwards instance, resource label, and time filters to the event feed', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Broker unavailable');

    await user.type(screen.getByLabelText('实例 ID 筛选'), 'local');
    await user.type(screen.getByLabelText('资源标签筛选'), 'brokerName=broker-a');
    fireEvent.change(screen.getByLabelText('开始时间筛选'), {
      target: { value: '2026-08-01T00:00' },
    });
    fireEvent.change(screen.getByLabelText('结束时间筛选'), {
      target: { value: '2026-08-02T00:00' },
    });

    await waitFor(() => {
      expect(listSystemAlertsPage).toHaveBeenLastCalledWith(
        expect.objectContaining({
          instanceId: 'local',
          labelKey: 'brokerName',
          labelValue: 'broker-a',
          from: new Date('2026-08-01T00:00:00').toISOString().replace('Z', ''),
          to: new Date('2026-08-02T00:00:00').toISOString().replace('Z', ''),
        }),
      );
    });
  });

  it('does not offer acknowledgement for resolved alert history', async () => {
    vi.mocked(listSystemAlertsPage).mockResolvedValue({
      items: [
        {
          id: 6,
          level: 'warning',
          title: 'Recovered broker',
          description: 'back to normal',
          time: '2026-08-10 01:02',
          transition: 'RESOLVED',
          acknowledged: false,
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
    renderPage();

    await screen.findByText('Recovered broker');
    expect(screen.queryByRole('button', { name: /^确认$/ })).not.toBeInTheDocument();
  });

  it('distinguishes historical alerts without a rule from rules without notification channels', async () => {
    vi.mocked(listSystemAlertsPage).mockResolvedValue({
      items: [
        {
          id: 7,
          level: 'warning',
          title: 'Historical disk alert',
          description: 'created before native alert rules',
          time: '2026-08-10 01:03',
          acknowledged: false,
        },
        {
          id: 8,
          level: 'warning',
          title: 'Native disk alert',
          description: 'created by a native rule without channels',
          time: '2026-08-10 01:04',
          acknowledged: false,
          ruleId: 42,
        },
      ],
      total: 2,
      page: 1,
      size: 20,
    });
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Historical disk alert');
    const notificationButtons = screen.getAllByRole('button', { name: '投递记录' });
    await user.click(notificationButtons[0]);
    await user.click(notificationButtons[1]);

    expect(await screen.findByText('无通知投递记录')).toBeInTheDocument();
    expect(screen.getByText('未配置通知通道')).toBeInTheDocument();
    expect(listAlertDeliveries).toHaveBeenCalledWith(7);
    expect(listAlertDeliveries).toHaveBeenCalledWith(8);
  });

  it('explains when a business notification was suppressed by a cluster incident', async () => {
    vi.mocked(listSystemAlertsPage).mockResolvedValue({
      items: [
        {
          id: 12,
          level: 'warning',
          title: 'Consumer lag',
          description: 'orders consumer lag is high',
          time: '2026-08-10 01:04',
          acknowledged: false,
          ruleId: 42,
          notificationSuppressed: true,
          suppressionCauseAlertId: 9,
          suppressionReason: '通知已由上游集群故障抑制：Broker unavailable',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
    vi.mocked(listAlertDeliveries).mockResolvedValue([]);
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('通知已抑制')).toBeInTheDocument();
    expect(screen.getByText('通知已由上游集群故障抑制：Broker unavailable')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '投递记录' }));
    expect(await screen.findAllByText('通知已由上游集群故障抑制：Broker unavailable')).toHaveLength(
      2,
    );
    await user.click(screen.getByRole('button', { name: '查看根因' }));
    expect(listRelatedSystemAlerts).toHaveBeenCalledWith(12);
  });

  it('shows cross-domain related events on demand', async () => {
    vi.mocked(listRelatedSystemAlerts).mockResolvedValue([
      {
        id: 9,
        level: 'error',
        title: 'Cluster root event',
        description: 'broker-a is offline',
        time: '2026-08-10 01:00',
        domain: 'CLUSTER',
        transition: 'FIRING',
        acknowledged: false,
      },
    ]);
    const user = userEvent.setup();
    renderPage();

    await user.click((await screen.findAllByRole('button', { name: '关联事件' }))[0]);

    expect(await screen.findByText('Cluster root event')).toBeInTheDocument();
    expect(screen.getByText('可能根因/影响')).toBeInTheDocument();
    expect(listRelatedSystemAlerts).toHaveBeenCalledWith(1);
  });

  it('keeps delivery failures within the alert card and retries only failed deliveries', async () => {
    const longError = 'DingTalk rejected webhook: signing mismatch '.repeat(12);
    vi.mocked(listAlertDeliveries).mockResolvedValue([
      { id: 9, channel: 'dingtalk', status: 'FAILED', attemptCount: 5, lastError: longError },
      { id: 10, channel: 'email', status: 'DELIVERED', attemptCount: 1 },
    ]);
    const user = userEvent.setup();
    renderPage();

    await user.click((await screen.findAllByRole('button', { name: '投递记录' }))[0]);
    const error = await screen.findByText((content) =>
      content.includes('DingTalk rejected webhook: signing mismatch'),
    );
    expect(error).toHaveStyle({ overflowWrap: 'anywhere', wordBreak: 'break-word' });

    await user.click(screen.getByRole('button', { name: '重新投递' }));
    await waitFor(() => {
      expect(retryAlertDelivery).toHaveBeenCalledWith(9);
      expect(listAlertDeliveries).toHaveBeenCalledTimes(2);
    });
  });

  it('tracks simultaneous acknowledgements independently', async () => {
    vi.mocked(acknowledgeAlert).mockImplementation(() => new Promise(() => {}));
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Broker unavailable');
    const acknowledgeButtons = screen.getAllByRole('button', { name: /^确认$/ });
    await user.click(acknowledgeButtons[0]);
    await user.click(acknowledgeButtons[1]);

    await waitFor(() => {
      expect(acknowledgeAlert).toHaveBeenCalledWith(1);
      expect(acknowledgeAlert).toHaveBeenCalledWith(2);
      expect(acknowledgeButtons[0]).toHaveClass('ant-btn-loading');
      expect(acknowledgeButtons[1]).toHaveClass('ant-btn-loading');
    });
  });

  it('shows maintenance windows and creates a scoped silence', async () => {
    vi.mocked(listAlertSilencesPage).mockResolvedValue({
      items: [
        {
          id: 9,
          domain: 'CLUSTER',
          instanceId: 'local',
          startsAt: '2026-08-10T01:00',
          endsAt: '2026-08-10T02:00',
          createdBy: 'admin',
        },
      ],
      total: 11,
      page: 1,
      size: 10,
    });
    vi.mocked(createAlertSilence).mockResolvedValue({
      id: 10,
      domain: 'BUSINESS',
      startsAt: '2026-08-11T01:00',
      endsAt: '2026-08-11T02:00',
      createdBy: 'admin',
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: '维护窗口' }));
    expect(await screen.findByText(/CLUSTER.*local/)).toBeInTheDocument();
    expect(listAlertSilencesPage).toHaveBeenCalledWith({ page: 1, pageSize: 10 });

    await user.type(screen.getByLabelText('规则 ID'), '42');
    await user.type(screen.getByLabelText('标签范围'), 'brokerName=broker-a,topic=orders');
    fireEvent.change(screen.getByLabelText('开始时间'), { target: { value: '2026-08-11T01:00' } });
    fireEvent.change(screen.getByLabelText('结束时间'), { target: { value: '2026-08-11T02:00' } });
    await user.click(screen.getByRole('button', { name: /创\s*建/ }));

    await waitFor(() => {
      expect(createAlertSilence).toHaveBeenCalledWith(
        expect.objectContaining({
          domain: 'BUSINESS',
          ruleId: 42,
          labels: { brokerName: 'broker-a', topic: 'orders' },
          startsAt: new Date('2026-08-11T01:00:00').toISOString(),
          endsAt: new Date('2026-08-11T02:00:00').toISOString(),
        }),
      );
      expect(listAlertSilencesPage).toHaveBeenLastCalledWith({ page: 1, pageSize: 10 });
    });
  });

  it('creates a bounded weekly maintenance schedule in an IANA time zone', async () => {
    vi.mocked(listAlertSilencesPage).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 10,
    });
    vi.mocked(createAlertSilence).mockResolvedValue({
      id: 12,
      startsAt: '2026-09-07T01:00:00Z',
      endsAt: '2026-09-07T02:00:00Z',
      recurrence: 'WEEKLY',
      timeZone: 'Asia/Shanghai',
      recurrenceDays: [1, 3, 5],
      recurrenceUntil: '2026-10-01T00:00:00Z',
      createdBy: 'admin',
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: '维护窗口' }));
    const recurrenceSelect = screen.getByLabelText('重复方式');
    fireEvent.mouseDown(recurrenceSelect.parentElement!);
    await user.click(await screen.findByText('每周'));

    await user.clear(screen.getByLabelText('时区'));
    await user.type(screen.getByLabelText('时区'), 'Asia/Shanghai');
    const weekdaySelect = screen.getByLabelText('重复日期');
    fireEvent.mouseDown(weekdaySelect.parentElement!);
    await user.click(await screen.findByText('周一'));

    fireEvent.change(screen.getByLabelText('开始时间'), { target: { value: '2026-09-07T09:00' } });
    fireEvent.change(screen.getByLabelText('结束时间'), { target: { value: '2026-09-07T10:00' } });
    fireEvent.change(screen.getByLabelText('重复至'), { target: { value: '2026-10-01T08:00' } });
    await user.click(screen.getByRole('button', { name: /创\s*建/ }));

    await waitFor(() => {
      expect(createAlertSilence).toHaveBeenCalledWith(
        expect.objectContaining({
          recurrence: 'WEEKLY',
          timeZone: 'Asia/Shanghai',
          recurrenceDays: [1],
          startsAt: '2026-09-07T01:00:00.000Z',
          endsAt: '2026-09-07T02:00:00.000Z',
          recurrenceUntil: '2026-10-01T00:00:00.000Z',
        }),
      );
    });
  });

  it('loads maintenance windows by page and backs up after deleting the last page item', async () => {
    vi.mocked(listAlertSilencesPage)
      .mockResolvedValueOnce({
        items: [
          {
            id: 9,
            domain: 'CLUSTER',
            instanceId: 'local',
            startsAt: '2026-08-10T01:00',
            endsAt: '2026-08-10T02:00',
            createdBy: 'admin',
          },
        ],
        total: 11,
        page: 1,
        size: 10,
      })
      .mockResolvedValueOnce({
        items: [
          {
            id: 10,
            domain: 'BUSINESS',
            instanceId: 'remote',
            startsAt: '2026-08-11T01:00',
            endsAt: '2026-08-11T02:00',
            createdBy: 'admin',
          },
        ],
        total: 11,
        page: 2,
        size: 10,
      })
      .mockResolvedValueOnce({
        items: [
          {
            id: 9,
            domain: 'CLUSTER',
            instanceId: 'local',
            startsAt: '2026-08-10T01:00',
            endsAt: '2026-08-10T02:00',
            createdBy: 'admin',
          },
        ],
        total: 10,
        page: 1,
        size: 10,
      });
    vi.mocked(deleteAlertSilence).mockResolvedValue();
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: '维护窗口' }));
    await user.click(await screen.findByRole('listitem', { name: '2' }));
    expect(await screen.findByText(/BUSINESS.*remote/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /结\s*束/ }));

    await waitFor(() => {
      expect(deleteAlertSilence).toHaveBeenCalledWith(10);
      expect(listAlertSilencesPage).toHaveBeenLastCalledWith({ page: 1, pageSize: 10 });
    });
  });
});
