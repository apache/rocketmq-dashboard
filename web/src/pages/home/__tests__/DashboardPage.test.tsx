/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { App } from 'antd';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  listMetricProfiles,
  queryMetrics,
  queryByDataSource,
  type DashboardData,
} from '../../../api/metrics';
import { listDataSources } from '../../../api/settings';
import { LangProvider } from '../../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../../i18n/languagePreference';
import * as dashboardService from '../../../services/dashboardService';
import * as instanceService from '../../../services/instanceService';
import DashboardPage from '../dashboard';

vi.mock('../../../api/metrics', () => ({
  listMetricProfiles: vi.fn(),
  queryMetrics: vi.fn(),
  queryByDataSource: vi.fn(),
}));
vi.mock('../../../api/settings', () => ({ listDataSources: vi.fn() }));

vi.mock('../../../services/dashboardService', () => ({ getDashboard: vi.fn() }));
vi.mock('../../../services/instanceService', () => ({ listInstances: vi.fn() }));

const dashboard = (name: string): DashboardData => ({
  stats: {
    totalClusters: 1,
    healthyClusters: 1,
    totalBrokers: 1,
    totalProxies: 0,
    totalNameServers: 1,
    totalTopics: 1,
    totalConsumerGroups: 1,
    totalMessagesToday: 1,
    messagesPerSecond: 1,
    tpsIn: 1,
    tpsOut: 1,
  },
  clusters: [
    {
      id: name,
      name,
      type: 'V4_NAMESRV',
      status: 'healthy',
      brokers: 1,
      proxies: 0,
      topics: 1,
      groups: 1,
      tpsIn: 1,
      tpsOut: 1,
      version: '5.0.0',
      throughput: [1],
    },
  ],
});

const unavailableTopologyDashboard = (): DashboardData => ({
  ...dashboard('proxy-cluster'),
  stats: {
    ...dashboard('proxy-cluster').stats,
    totalProxies: null,
    totalNameServers: null,
  },
  clusters: dashboard('proxy-cluster').clusters.map((cluster) => ({
    ...cluster,
    proxies: null,
  })),
});

const trafficDashboard = (): DashboardData => ({
  ...dashboard('traffic-a'),
  stats: {
    ...dashboard('traffic-a').stats,
    totalClusters: 2,
    totalBrokers: 3,
    totalProxies: 1,
    tpsIn: 125,
    tpsOut: 75,
    messagesPerSecond: 200,
  },
  clusters: [
    {
      ...dashboard('traffic-a').clusters[0],
      brokers: 2,
      tpsIn: 100,
      tpsOut: 50,
      throughput: [10, 20, 30, 80],
    },
    {
      ...dashboard('traffic-b').clusters[0],
      brokers: 1,
      tpsIn: 25,
      tpsOut: 25,
      throughput: [25, 25, 25, 25],
    },
  ],
});

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve;
  });
  return { promise, resolve };
};

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </LangProvider>
    </App>,
  );

const LocationProbe = () => {
  const location = useLocation();
  return (
    <output data-testid="location">
      {location.pathname}
      {location.search}
    </output>
  );
};

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listMetricProfiles).mockReset().mockResolvedValue([]);
  vi.mocked(listDataSources).mockReset().mockResolvedValue([]);
  vi.mocked(queryMetrics)
    .mockReset()
    .mockResolvedValue({ resultType: 'matrix', series: [], warnings: [] });
  vi.mocked(queryByDataSource)
    .mockReset()
    .mockResolvedValue({ resultType: 'matrix', series: [], warnings: [] });
  vi.mocked(instanceService.listInstances).mockResolvedValue([
    {
      id: 1,
      name: 'instance-a',
      endpoint: 'a:9876',
      type: 'DIRECT',
      remark: '',
      topicCount: 0,
      consumerGroupCount: 0,
      gmtCreate: '',
      gmtModified: '',
    },
    {
      id: 2,
      name: 'instance-b',
      endpoint: 'b:9876',
      type: 'DIRECT',
      remark: '',
      topicCount: 0,
      consumerGroupCount: 0,
      gmtCreate: '',
      gmtModified: '',
    },
  ]);
});

afterEach(() => {
  // The English-locale case below writes the language preference; without this the rest of the file
  // (and anything appended later) silently depends on test order.
  localStorage.removeItem(LANGUAGE_STORAGE_KEY);
});

describe('DashboardPage', () => {
  it('renders unavailable Proxy topology counts as N/A instead of zero', async () => {
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(unavailableTopologyDashboard());
    renderWithProviders(<DashboardPage />);

    await screen.findAllByText('proxy-cluster');
    expect(await screen.findByText(/1 Brokers · N\/A Proxy/u)).toBeInTheDocument();
    expect(screen.queryByText('0 Proxy')).not.toBeInTheDocument();
    const clusterHealthCard = screen.getByText('集群健康概览').closest('.ant-card');
    expect(clusterHealthCard).not.toBeNull();
    const row = within(clusterHealthCard as HTMLElement)
      .getByText('proxy-cluster')
      .closest('tr');
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).getAllByText('N/A').length).toBeGreaterThanOrEqual(1);
  });

  it('exposes traffic insight metrics in the expanded cluster health row', async () => {
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(trafficDashboard());
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderWithProviders(<DashboardPage />);

    await screen.findAllByText('traffic-a');
    const clusterHealthCard = screen.getByText('集群健康概览').closest('.ant-card');
    expect(clusterHealthCard).not.toBeNull();
    const clusterHealth = within(clusterHealthCard as HTMLElement);

    // The traffic breakdown lives behind the row expander so the main table stays at eight
    // identity/topology columns instead of scrolling horizontally.
    expect(clusterHealth.queryByText('总 TPS')).toBeNull();
    const row = clusterHealth.getByText('traffic-a').closest('tr');
    expect(row).not.toBeNull();
    await user.click(
      within(row as HTMLElement).getByRole('button', { name: /展开行|Expand row/u }),
    );

    expect(clusterHealth.getAllByText('总 TPS')).not.toHaveLength(0);
    expect(clusterHealth.getAllByText('占比')).not.toHaveLength(0);
    expect(clusterHealth.getAllByText('单 Broker TPS')).not.toHaveLength(0);
    expect(clusterHealth.getAllByText('出入比')).not.toHaveLength(0);
    expect(clusterHealth.getByText('150/s')).toBeInTheDocument();
    expect(clusterHealth.getByText('75%')).toBeInTheDocument();
    expect(clusterHealth.getByText('75/s')).toBeInTheDocument();
    expect(clusterHealth.getByText('0.5:1')).toBeInTheDocument();
    expect(clusterHealth.getByText(/上升/u)).toBeInTheDocument();
  });

  it('does not show dashboard data from the previous instance while loading a new selection', async () => {
    const instanceA = deferred<DashboardData>();
    vi.mocked(dashboardService.getDashboard)
      .mockResolvedValueOnce(dashboard('initial-cluster'))
      .mockReturnValueOnce(instanceA.promise);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderWithProviders(<DashboardPage />);

    await screen.findAllByText('initial-cluster');
    const selector = screen.getByRole('combobox', { name: '实例筛选' });
    await user.click(selector);
    await user.click(
      await screen.findByText('instance-a', { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(dashboardService.getDashboard).toHaveBeenCalledWith('instance-a'));

    expect(screen.queryByText('initial-cluster')).not.toBeInTheDocument();
    instanceA.resolve(dashboard('instance-a-cluster'));
    await screen.findAllByText('instance-a-cluster');
  });

  it('does not let a stale instance response overwrite the latest selection', async () => {
    const instanceA = deferred<DashboardData>();
    const instanceB = deferred<DashboardData>();
    vi.mocked(dashboardService.getDashboard)
      .mockResolvedValueOnce(dashboard('initial-cluster'))
      .mockReturnValueOnce(instanceA.promise)
      .mockReturnValueOnce(instanceB.promise);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderWithProviders(<DashboardPage />);

    await screen.findAllByText('initial-cluster');
    const selector = screen.getByRole('combobox', { name: '实例筛选' });
    await user.click(selector);
    await user.click(
      await screen.findByText('instance-a', { selector: '.ant-select-item-option-content' }),
    );
    await user.click(selector);
    await user.click(
      await screen.findByText('instance-b', { selector: '.ant-select-item-option-content' }),
    );

    instanceB.resolve(dashboard('instance-b-cluster'));
    expect(await screen.findAllByText('instance-b-cluster')).not.toHaveLength(0);

    instanceA.resolve(dashboard('instance-a-cluster'));
    await waitFor(() => {
      expect(screen.queryByText('instance-a-cluster')).not.toBeInTheDocument();
    });
    expect(screen.getAllByText('instance-b-cluster')).not.toHaveLength(0);
  });

  it('preserves the selected instance when navigating to the cluster page', async () => {
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboard('instance-a-cluster'));
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderWithProviders(
      <>
        <DashboardPage />
        <LocationProbe />
      </>,
    );

    await screen.findAllByText('instance-a-cluster');
    const selector = screen.getByRole('combobox', { name: '实例筛选' });
    await user.click(selector);
    await user.click(
      await screen.findByText('instance-b', { selector: '.ant-select-item-option-content' }),
    );
    await user.click(screen.getByText('查看全部'));

    expect(screen.getByTestId('location')).toHaveTextContent('/cluster?instanceId=instance-b');
  });

  it('does not offer cloud instances for MQAdmin runtime diagnostics', async () => {
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      {
        id: 1,
        name: 'apache-instance',
        endpoint: 'apache:9876',
        type: 'DIRECT',
        vendor: 'APACHE',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
      {
        id: 2,
        name: 'cloud-instance',
        endpoint: 'cloud:9876',
        type: 'DIRECT',
        vendor: 'ALIYUN',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
    ]);
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboard('apache-cluster'));
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderWithProviders(<DashboardPage />);

    await screen.findAllByText('apache-cluster');
    await user.click(screen.getByRole('combobox', { name: '实例筛选' }));

    expect(
      await screen.findByText('apache-instance', {
        selector: '.ant-select-item-option-content',
      }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('cloud-instance', { selector: '.ant-select-item-option-content' }),
    ).not.toBeInTheDocument();
  });

  it('localizes the load-error alert for English users', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    vi.mocked(dashboardService.getDashboard).mockRejectedValue(new Error('boom'));
    vi.mocked(instanceService.listInstances).mockResolvedValue([]);

    renderWithProviders(<DashboardPage />);

    expect(await screen.findByText('Failed to load dashboard')).toBeInTheDocument();
    expect(screen.queryByText('仪表盘加载失败')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});

describe('independent metrics during overview failures', () => {
  const metricData = {
    resultType: 'matrix',
    warnings: [],
    series: [
      {
        labels: { cluster: 'prod' },
        histograms: [],
        values: [
          { timestamp: 1_800_000_000, value: '42' },
          { timestamp: 1_800_000_030, value: '43' },
        ],
      },
    ],
  };
  const prepareMetrics = async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    vi.mocked(listMetricProfiles).mockResolvedValue([
      {
        id: 'profile',
        name: 'Profile',
        description: '',
        metrics: [
          {
            semanticMetric: 'tps',
            name: 'TPS',
            unit: '/s',
            prometheusMetric: 'messages_total',
            promql: 'rate(messages_total[1m])',
            labels: ['cluster'],
          },
        ],
      },
    ]);
    vi.mocked(listDataSources).mockResolvedValue([
      {
        key: 'prometheus-a',
        name: 'Prometheus A',
        type: 'Prometheus',
        url: '',
        auth: 'None',
        status: 'healthy',
        instanceIds: ['instance-a'],
      },
    ]);
    vi.mocked(queryMetrics).mockResolvedValue(metricData);
    vi.mocked(queryByDataSource).mockResolvedValue(metricData);
    vi.mocked(dashboardService.getDashboard).mockReset();
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboard('healthy-overview'));
    renderWithProviders(<DashboardPage />);
    await screen.findAllByText('healthy-overview');
    await user.click(screen.getByRole('combobox', { name: '实例筛选' }));
    await user.click(
      await screen.findByText('instance-a', {
        selector: '.ant-select-item-option-content',
      }),
    );
    await waitFor(() => expect(dashboardService.getDashboard).toHaveBeenCalledWith('instance-a'));
    await screen.findByRole('img', { name: 'TPS time series' });
    await user.click(screen.getByRole('combobox', { name: '数据源' }));
    await user.click(
      await screen.findByText('Prometheus A', {
        selector: '.ant-select-item-option-content',
      }),
    );
    await waitFor(() =>
      expect(queryByDataSource).toHaveBeenCalledWith(
        expect.objectContaining({
          key: 'prometheus-a',
          instanceId: 'instance-a',
        }),
      ),
    );
    await user.click(screen.getByText('6h'));
    const input = screen.getByRole('textbox', { name: '自定义查询' });
    fireEvent.change(input, { target: { value: 'sum(messages_total)' } });
    await user.click(screen.getByRole('button', { name: '查询' }));
    await screen.findByRole('img', { name: '自定义查询 time series' });
    fireEvent.change(input, { target: { value: 'sum(rate(messages_total[5m]))' } });
    return { user };
  };

  it.each(['instance-b', undefined])(
    'clearsMetricsWhenInstanceSelectionChanges (%s) Test',
    async (nextInstance) => {
      const { user } = await prepareMetrics();
      const nextOverview = deferred<DashboardData>();
      vi.mocked(dashboardService.getDashboard).mockReturnValueOnce(nextOverview.promise);
      const sourceQueryCount = vi.mocked(queryByDataSource).mock.calls.length;
      const selector = screen.getByRole('combobox', { name: '实例筛选' });
      if (nextInstance) {
        await user.click(selector);
        await user.click(
          await screen.findByText(nextInstance, {
            selector: '.ant-select-item-option-content',
          }),
        );
      } else {
        const select = selector.closest('.ant-select') as HTMLElement;
        await user.hover(select);
        await user.click(select.querySelector('.ant-select-clear') as HTMLElement);
      }
      await waitFor(() =>
        expect(dashboardService.getDashboard).toHaveBeenLastCalledWith(nextInstance),
      );
      expect(screen.queryByRole('img', { name: '自定义查询 time series' })).not.toBeInTheDocument();
      expect(screen.queryByText('healthy-overview')).not.toBeInTheDocument();
      await act(async () => nextOverview.resolve(dashboard('next-overview')));
      await screen.findAllByText('next-overview');
      expect(await screen.findByRole('textbox', { name: '自定义查询' })).toHaveValue('');
      expect(
        screen.getByRole('combobox', { name: '数据源' }).closest('.ant-select'),
      ).toHaveTextContent('默认数据源');
      expect(
        screen.getByLabelText('时间范围').querySelector('.ant-segmented-item-selected'),
      ).toHaveTextContent('1h');
      expect(queryByDataSource).toHaveBeenCalledTimes(sourceQueryCount);
    },
  );

  it('keepsHealthyPrometheusUsableWhenOnlyOverviewRefreshFailsTest', async () => {
    const { user } = await prepareMetrics();
    vi.mocked(dashboardService.getDashboard).mockRejectedValueOnce(
      new Error('overview unavailable'),
    );
    await user.click(screen.getByRole('button', { name: /刷\s*新$/ }));
    await screen.findByText('仪表盘加载失败');
    expect(screen.getByRole('textbox', { name: '自定义查询' })).toHaveValue(
      'sum(rate(messages_total[5m]))',
    );
    await user.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() =>
      expect(queryByDataSource).toHaveBeenLastCalledWith(
        expect.objectContaining({
          key: 'prometheus-a',
          instanceId: 'instance-a',
          query: expect.objectContaining({ metric: 'sum(rate(messages_total[5m]))', step: '2m' }),
        }),
      ),
    );
    await screen.findByRole('img', { name: '自定义查询 time series' });
  });

  it('preservesMetricsDraftSourceAndWindowAfterOverviewRetryTest', async () => {
    const { user } = await prepareMetrics();
    vi.mocked(dashboardService.getDashboard).mockRejectedValueOnce(
      new Error('overview unavailable'),
    );
    await user.click(screen.getByRole('button', { name: /刷\s*新$/ }));
    await screen.findByText('仪表盘加载失败');
    await user.click(screen.getByRole('button', { name: /重\s*试$/ }));
    await screen.findAllByText('healthy-overview');
    const input = await screen.findByRole('textbox', { name: '自定义查询' });
    expect(input).toHaveValue('sum(rate(messages_total[5m]))');
    expect(
      screen.getByRole('combobox', { name: '实例筛选' }).closest('.ant-select'),
    ).toHaveTextContent('instance-a');
    expect(
      screen.getByRole('combobox', { name: '数据源' }).closest('.ant-select'),
    ).toHaveTextContent('Prometheus A');
    expect(
      screen.getByLabelText('时间范围').querySelector('.ant-segmented-item-selected'),
    ).toHaveTextContent('6h');
    await user.click(screen.getByRole('button', { name: '刷新全部面板' }));
    await waitFor(() =>
      expect(queryByDataSource).toHaveBeenLastCalledWith(
        expect.objectContaining({
          key: 'prometheus-a',
          instanceId: 'instance-a',
          query: expect.objectContaining({ metric: 'sum(messages_total)', step: '2m' }),
        }),
      ),
    );
  });
});
