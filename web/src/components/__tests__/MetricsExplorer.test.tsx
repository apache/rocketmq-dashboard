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

import { App } from 'antd';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { listDataSources } from '../../api/settings';
import { listMetricProfiles, queryByDataSource, queryMetrics } from '../../api/metrics';
import { LangProvider } from '../../i18n/LangContext';
import { downloadCsv } from '../../utils/download';
import {
  METRICS_QUERY_HISTORY_STORAGE_KEY,
  type MetricsQueryHistoryEntry,
} from '../metricsExplorerDiagnostics';
import MetricsExplorer from '../MetricsExplorer';

vi.mock('../../api/settings', () => ({
  listDataSources: vi.fn(),
}));

vi.mock('../../api/metrics', () => ({
  listMetricProfiles: vi.fn(),
  queryByDataSource: vi.fn(),
  queryMetrics: vi.fn(),
}));

vi.mock('../../utils/download', async () => {
  const actual =
    await vi.importActual<typeof import('../../utils/download')>('../../utils/download');
  return {
    ...actual,
    downloadCsv: vi.fn(),
  };
});

const profiles = [
  {
    id: 'rocketmq5-native',
    name: 'RocketMQ 5.x Native',
    description: 'RocketMQ 5.x native metrics',
    metrics: [
      {
        semanticMetric: 'message_in_tps',
        name: 'Message In TPS',
        unit: 'messages/s',
        prometheusMetric: 'rocketmq_messages_in_total',
        promql: 'sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)',
        labels: ['cluster', 'node_id'],
      },
    ],
  },
  {
    id: 'rocketmq4-exporter',
    name: 'RocketMQ 4.x Exporter',
    description: 'RocketMQ 4.x exporter metrics',
    metrics: [
      {
        semanticMetric: 'consumer_lag_messages',
        name: 'Consumer Lag Messages',
        unit: 'messages',
        prometheusMetric: 'rocketmq_message_accumulation',
        promql: 'sum(rocketmq_message_accumulation) by (cluster, group, topic)',
        labels: ['cluster', 'group', 'topic'],
      },
    ],
  },
];

const metricData = {
  resultType: 'matrix',
  series: [
    {
      labels: { cluster: 'prod', node_id: 'broker-a' },
      values: [
        { timestamp: 1_799_996_400, value: '40' },
        { timestamp: 1_800_000_000, value: '42' },
      ],
      histograms: [],
    },
  ],
  warnings: [],
};

const histogramOnlyData = {
  resultType: 'matrix',
  series: [
    {
      labels: { cluster: 'prod', node_id: 'broker-a' },
      values: [],
      histograms: [
        {
          timestamp: 1_799_996_400,
          histogram: { count: '10', sum: '250', buckets: [] },
        },
        {
          timestamp: 1_800_000_000,
          histogram: { count: '20', sum: '600', buckets: [] },
        },
      ],
    },
  ],
  warnings: [],
};

const createHistoryEntry = (
  overrides: Partial<MetricsQueryHistoryEntry> = {},
): MetricsQueryHistoryEntry => ({
  id: 'history-consumer-lag',
  profileId: 'rocketmq4-exporter',
  profileName: 'RocketMQ 4.x Exporter',
  metricId: 'consumer_lag_messages',
  metricName: 'Consumer Lag Messages',
  metricUnit: 'messages',
  rangeId: '6h',
  rangeLabel: '6h',
  step: '2m',
  dataSourceKey: '',
  dataSourceName: '',
  promql: 'sum(rocketmq_message_accumulation) by (cluster, group, topic)',
  start: 1_799_978_400,
  end: 1_800_000_000,
  queriedAt: 1_800_000_000_000,
  summary: {
    seriesCount: 1,
    visibleSeriesCount: 1,
    sampleCount: 2,
    scalarSampleCount: 2,
    histogramSampleCount: 0,
    warningCount: 0,
    earliestTimestamp: 1_799_978_400,
    latestTimestamp: 1_800_000_000,
  },
  ...overrides,
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

beforeEach(() => {
  vi.spyOn(Date, 'now').mockReturnValue(1_800_000_000_000);
  localStorage.clear();
  vi.mocked(listDataSources).mockResolvedValue([]);
  vi.mocked(listMetricProfiles).mockResolvedValue(profiles);
  vi.mocked(queryByDataSource).mockResolvedValue(metricData);
  vi.mocked(queryMetrics).mockResolvedValue(metricData);
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.clearAllMocks();
});

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );

const createDeferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
};

describe('MetricsExplorer', () => {
  it('loads a metric profile and renders its Prometheus series', async () => {
    renderWithProviders(<MetricsExplorer />);

    expect(await screen.findByText('RocketMQ 5.x Native')).toBeInTheDocument();
    await waitFor(() =>
      expect(queryMetrics).toHaveBeenCalledWith({
        metric: 'sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)',
        start: 1_799_996_400,
        end: 1_800_000_000,
        step: '30s',
      }),
    );

    expect(screen.getByRole('img', { name: 'Message In TPS time series' })).toBeInTheDocument();
    expect(screen.getByText('cluster=prod / node_id=broker-a')).toBeInTheDocument();
    expect(screen.getByText('42 messages/s')).toBeInTheDocument();
  });

  it('sorts provider samples before drawing and selecting the latest value', async () => {
    vi.mocked(queryMetrics).mockResolvedValue({
      ...metricData,
      series: [
        {
          ...metricData.series[0],
          values: [
            { timestamp: 1_800_000_000, value: '42' },
            { timestamp: 1_799_996_400, value: '40' },
          ],
        },
      ],
    });

    renderWithProviders(<MetricsExplorer />);

    expect(await screen.findByText('42 messages/s')).toBeInTheDocument();
    const chart = screen.getByRole('img', { name: 'Message In TPS time series' });
    const points = chart.querySelector('polyline')?.getAttribute('points')?.split(' ') ?? [];
    const xCoordinates = points.map((point) => Number(point.split(',')[0]));
    expect(xCoordinates).toEqual([...xCoordinates].sort((left, right) => left - right));
  });

  it('updates the query window when the range changes', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MetricsExplorer />);

    await screen.findByRole('img', { name: 'Message In TPS time series' });
    await user.click(screen.getByText('6h'));

    await waitFor(() =>
      expect(queryMetrics).toHaveBeenLastCalledWith({
        metric: 'sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)',
        start: 1_799_978_400,
        end: 1_800_000_000,
        step: '2m',
      }),
    );
  });

  it('renders one panel per metric in the selected profile', async () => {
    vi.mocked(listMetricProfiles).mockResolvedValue([
      {
        id: 'rocketmq5-native',
        name: 'RocketMQ 5.x Native',
        description: 'RocketMQ 5.x native metrics',
        metrics: [
          {
            semanticMetric: 'message_in_tps',
            name: 'Message In TPS',
            unit: 'messages/s',
            prometheusMetric: 'rocketmq_messages_in_total',
            promql: 'sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)',
            labels: ['cluster'],
          },
          {
            semanticMetric: 'consumer_lag_messages',
            name: 'Consumer Lag Messages',
            unit: 'messages',
            prometheusMetric: 'rocketmq_consumer_lag_messages',
            promql: 'sum(rocketmq_consumer_lag_messages) by (cluster)',
            labels: ['cluster'],
          },
        ],
      },
    ]);

    renderWithProviders(<MetricsExplorer />);

    await waitFor(() => expect(queryMetrics).toHaveBeenCalledTimes(2));
    expect(screen.getByText('Message In TPS')).toBeInTheDocument();
    expect(screen.getByText('Consumer Lag Messages')).toBeInTheDocument();
    expect(queryMetrics).toHaveBeenCalledWith({
      metric: 'sum(rocketmq_consumer_lag_messages) by (cluster)',
      start: 1_799_996_400,
      end: 1_800_000_000,
      step: '30s',
    });
  });

  it('caps the plotted series and reports the hidden count', async () => {
    const manySeries = Array.from({ length: 14 }, (_, index) => ({
      labels: { cluster: 'prod', node_id: `broker-${index}` },
      values: [
        { timestamp: 1_799_996_400, value: String(index) },
        { timestamp: 1_800_000_000, value: String(index + 1) },
      ],
      histograms: [],
    }));
    vi.mocked(queryMetrics).mockResolvedValue({ ...metricData, series: manySeries });

    renderWithProviders(<MetricsExplorer />);

    expect(await screen.findByText(/另有 4 条序列未显示/)).toBeInTheDocument();
    const chart = screen.getByRole('img', { name: 'Message In TPS time series' });
    expect(chart.querySelectorAll('polyline')).toHaveLength(10);
  });

  it('runs a custom PromQL expression from the query box', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MetricsExplorer />);
    await screen.findByRole('img', { name: 'Message In TPS time series' });

    await user.type(screen.getByLabelText('自定义查询'), 'sum(rocketmq_topic_number)');
    await user.click(screen.getByRole('button', { name: '查询' }));

    await waitFor(() =>
      expect(queryMetrics).toHaveBeenCalledWith({
        metric: 'sum(rocketmq_topic_number)',
        start: 1_799_996_400,
        end: 1_800_000_000,
        step: '30s',
      }),
    );
    expect(await screen.findAllByText('cluster=prod / node_id=broker-a')).not.toHaveLength(0);
  });

  it('refreshes profile panels and the custom query independently', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MetricsExplorer />);
    await screen.findByText('42 messages/s');

    await user.type(screen.getByLabelText('自定义查询'), 'sum(rocketmq_topic_number)');
    await user.click(screen.getByRole('button', { name: '查询' }));

    await waitFor(() =>
      expect(queryMetrics).toHaveBeenCalledWith({
        metric: 'sum(rocketmq_topic_number)',
        start: 1_799_996_400,
        end: 1_800_000_000,
        step: '30s',
      }),
    );

    const refreshedProfileData = {
      ...metricData,
      series: [
        {
          ...metricData.series[0],
          values: [{ timestamp: 1_800_000_000, value: '77' }],
        },
      ],
    };
    const refreshedCustomData = {
      ...metricData,
      series: [
        {
          ...metricData.series[0],
          labels: { cluster: 'prod', query: 'custom' },
          values: [{ timestamp: 1_800_000_000, value: '9' }],
        },
      ],
    };
    vi.mocked(queryMetrics).mockImplementation((query) =>
      Promise.resolve(
        query.metric === 'sum(rocketmq_topic_number)' ? refreshedCustomData : refreshedProfileData,
      ),
    );

    await user.click(screen.getByRole('button', { name: '刷新全部面板' }));

    expect(await screen.findByText('77 messages/s')).toBeInTheDocument();
    expect(screen.getByText('cluster=prod / query=custom')).toBeInTheDocument();
  });

  it('queries the first metric when the version profile changes', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MetricsExplorer />);

    await screen.findByRole('img', { name: 'Message In TPS time series' });
    await user.click(screen.getByRole('combobox', { name: '指标模板' }));
    await user.click(
      await screen.findByText('RocketMQ 4.x Exporter', {
        selector: '.ant-select-item-option-content',
      }),
    );

    await waitFor(() =>
      expect(queryMetrics).toHaveBeenLastCalledWith({
        metric: 'sum(rocketmq_message_accumulation) by (cluster, group, topic)',
        start: 1_799_996_400,
        end: 1_800_000_000,
        step: '30s',
      }),
    );
    expect(screen.getByText('Consumer Lag Messages')).toBeInTheDocument();
  });

  it('keeps the newest range result when an older query finishes later', async () => {
    const initialQuery = createDeferred<typeof metricData>();
    const newerData = {
      ...metricData,
      series: [
        {
          ...metricData.series[0],
          values: [{ timestamp: 1_800_000_000, value: '84' }],
        },
      ],
    };
    vi.mocked(queryMetrics)
      .mockReturnValueOnce(initialQuery.promise)
      .mockResolvedValueOnce(newerData);

    const user = userEvent.setup();
    renderWithProviders(<MetricsExplorer />);

    await waitFor(() => expect(queryMetrics).toHaveBeenCalledTimes(1));
    await user.click(screen.getByText('6h'));
    expect(await screen.findByText('84 messages/s')).toBeInTheDocument();

    initialQuery.resolve(metricData);
    await waitFor(() => expect(screen.queryByText('42 messages/s')).not.toBeInTheDocument());
    expect(screen.getByText('84 messages/s')).toBeInTheDocument();
  });

  it('shows a query failure without replacing the metric controls', async () => {
    vi.mocked(queryMetrics).mockRejectedValue(new Error('Prometheus unavailable'));

    renderWithProviders(<MetricsExplorer />);

    expect(await screen.findByText('Prometheus 查询失败')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: '指标模板' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '刷新全部面板' })).toBeInTheDocument();
  });

  it('shows the actionable message returned by the metrics API', async () => {
    vi.mocked(queryMetrics).mockRejectedValue({
      response: { data: { message: 'Prometheus base URL is not configured' } },
    });

    renderWithProviders(<MetricsExplorer />);

    expect(await screen.findByText('Prometheus base URL is not configured')).toBeInTheDocument();
  });

  it('shows an empty state when Prometheus returns no samples at all', async () => {
    vi.mocked(queryMetrics).mockResolvedValue({ ...metricData, series: [] });

    renderWithProviders(<MetricsExplorer />);

    expect(await screen.findByText('暂无数据')).toBeInTheDocument();
  });

  it('renders histogram-only series from observed sums instead of an empty state', async () => {
    vi.mocked(queryMetrics).mockResolvedValue(histogramOnlyData);

    renderWithProviders(<MetricsExplorer />);

    expect(
      await screen.findByRole('img', { name: 'Message In TPS time series' }),
    ).toBeInTheDocument();
    expect(screen.getByText('600 messages/s')).toBeInTheDocument();
    expect(screen.getByText('直方图')).toBeInTheDocument();
    expect(screen.queryByText('暂无数据')).not.toBeInTheDocument();
  });

  it('falls back to the observation count when the histogram sum is missing', async () => {
    vi.mocked(queryMetrics).mockResolvedValue({
      ...histogramOnlyData,
      series: [
        {
          ...histogramOnlyData.series[0],
          histograms: [
            { timestamp: 1_800_000_000, histogram: { count: '20', sum: '', buckets: [] } },
          ],
        },
      ],
    });

    renderWithProviders(<MetricsExplorer />);

    expect(await screen.findByText('20 messages/s')).toBeInTheDocument();
  });

  it('prefers scalar samples when a series has both values and histograms', async () => {
    vi.mocked(queryMetrics).mockResolvedValue({
      ...metricData,
      series: [
        {
          ...metricData.series[0],
          histograms: [
            { timestamp: 1_800_000_000, histogram: { count: '99', sum: '999', buckets: [] } },
          ],
        },
      ],
    });

    renderWithProviders(<MetricsExplorer />);

    expect(await screen.findByText('42 messages/s')).toBeInTheDocument();
    expect(screen.queryByText('999 messages/s')).not.toBeInTheDocument();
    expect(screen.queryByText('直方图')).not.toBeInTheDocument();
  });

  it('queries the selected data source through the datasource endpoint', async () => {
    const user = userEvent.setup();
    vi.mocked(listDataSources).mockResolvedValue([
      {
        key: 'ds-prom-1',
        name: 'Prometheus 生产',
        type: 'Prometheus',
        url: '',
        auth: 'None',
        status: 'healthy',
        instanceIds: ['instance-1'],
      },
    ]);
    vi.mocked(queryByDataSource).mockResolvedValue(metricData);

    renderWithProviders(<MetricsExplorer instanceId="instance-1" />);

    await screen.findByRole('combobox', { name: '数据源' });
    await user.click(screen.getByRole('combobox', { name: '数据源' }));
    await user.click(
      await screen.findByText('Prometheus 生产', { selector: '.ant-select-item-option-content' }),
    );

    const queryMetricsCallsBefore = vi.mocked(queryMetrics).mock.calls.length;

    await waitFor(() =>
      expect(queryByDataSource).toHaveBeenCalledWith({
        key: 'ds-prom-1',
        instanceId: 'instance-1',
        query: {
          metric: 'sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)',
          start: 1_799_996_400,
          end: 1_800_000_000,
          step: '30s',
        },
      }),
    );
    expect(vi.mocked(queryMetrics).mock.calls.length).toBe(queryMetricsCallsBefore);
  });

  it('prompts for basic credentials and supplies them only to the selected data source query', async () => {
    const user = userEvent.setup();
    vi.mocked(listDataSources).mockResolvedValue([
      {
        key: 'ds-basic',
        name: 'Protected Prometheus',
        type: 'Prometheus',
        url: '',
        auth: 'Basic Auth',
        status: 'healthy',
      },
    ]);

    renderWithProviders(<MetricsExplorer />);

    await user.click(await screen.findByRole('combobox', { name: '数据源' }));
    await user.click(
      await screen.findByText('Protected Prometheus', {
        selector: '.ant-select-item-option-content',
      }),
    );

    expect(await screen.findByRole('dialog', { name: '数据源认证' })).toBeInTheDocument();
    expect(queryByDataSource).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText('用户名'), 'metrics-reader');
    await user.type(screen.getByLabelText('密码'), 'secret-value');
    await user.click(screen.getByRole('button', { name: /连\s*接/ }));

    await waitFor(() =>
      expect(queryByDataSource).toHaveBeenCalledWith({
        key: 'ds-basic',
        username: 'metrics-reader',
        password: 'secret-value',
        instanceId: undefined,
        query: {
          metric: 'sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)',
          start: 1_799_996_400,
          end: 1_800_000_000,
          step: '30s',
        },
      }),
    );
  });

  it('prompts for a bearer token without persisting it when the source is left', async () => {
    const user = userEvent.setup();
    vi.mocked(listDataSources).mockResolvedValue([
      {
        key: 'ds-bearer',
        name: 'Bearer Prometheus',
        type: 'Prometheus',
        url: '',
        auth: 'Bearer Token',
        status: 'healthy',
      },
    ]);

    renderWithProviders(<MetricsExplorer />);

    const sourceSelect = await screen.findByRole('combobox', { name: '数据源' });
    await user.click(sourceSelect);
    await user.click(
      await screen.findByText('Bearer Prometheus', {
        selector: '.ant-select-item-option-content',
      }),
    );
    await user.type(await screen.findByLabelText('令牌'), 'ephemeral-token');
    await user.click(screen.getByRole('button', { name: /连\s*接/ }));

    await waitFor(() =>
      expect(queryByDataSource).toHaveBeenCalledWith(
        expect.objectContaining({ key: 'ds-bearer', bearerToken: 'ephemeral-token' }),
      ),
    );

    await user.click(sourceSelect);
    await user.click(
      await screen.findByText('默认数据源', { selector: '.ant-select-item-option-content' }),
    );
    await user.click(sourceSelect);
    await user.click(
      await screen.findByText('Bearer Prometheus', {
        selector: '.ant-select-item-option-content',
      }),
    );

    expect(await screen.findByRole('dialog', { name: '数据源认证' })).toBeInTheDocument();
    expect(screen.getByLabelText('令牌')).toHaveValue('');
  });

  it('only offers data sources bound to the selected instance or globally available', async () => {
    vi.mocked(listDataSources).mockResolvedValue([
      {
        key: 'ds-global',
        name: 'Global Prometheus',
        type: 'Prometheus',
        url: '',
        auth: 'None',
        status: 'healthy',
      },
      {
        key: 'ds-instance-a',
        name: 'Instance A Prometheus',
        type: 'Prometheus',
        url: '',
        auth: 'None',
        status: 'healthy',
        instanceIds: ['instance-1'],
      },
      {
        key: 'ds-instance-b',
        name: 'Instance B Prometheus',
        type: 'Prometheus',
        url: '',
        auth: 'None',
        status: 'healthy',
        instanceIds: ['instance-2'],
      },
    ]);

    const user = userEvent.setup();
    renderWithProviders(<MetricsExplorer instanceId="instance-1" />);

    await screen.findByRole('combobox', { name: '数据源' });
    await user.click(screen.getByRole('combobox', { name: '数据源' }));

    expect(await screen.findByText('Global Prometheus')).toBeInTheDocument();
    expect(screen.getByText('Instance A Prometheus')).toBeInTheDocument();
    expect(screen.queryByText('Instance B Prometheus')).not.toBeInTheDocument();
  });

  it('falls back to the default source when the selected data source leaves the instance scope', async () => {
    const user = userEvent.setup();
    vi.mocked(listDataSources).mockResolvedValue([
      {
        key: 'ds-instance-a',
        name: 'Instance A Prometheus',
        type: 'Prometheus',
        url: '',
        auth: 'None',
        status: 'healthy',
        instanceIds: ['instance-1'],
      },
    ]);

    const view = renderWithProviders(<MetricsExplorer instanceId="instance-1" />);

    await screen.findByRole('combobox', { name: '数据源' });
    await user.click(screen.getByRole('combobox', { name: '数据源' }));
    await user.click(
      await screen.findByText('Instance A Prometheus', {
        selector: '.ant-select-item-option-content',
      }),
    );

    await waitFor(() =>
      expect(queryByDataSource).toHaveBeenCalledWith(
        expect.objectContaining({
          key: 'ds-instance-a',
          instanceId: 'instance-1',
        }),
      ),
    );
    const dataSourceQueriesBeforeScopeChange = vi.mocked(queryByDataSource).mock.calls.length;
    const defaultQueriesBeforeScopeChange = vi.mocked(queryMetrics).mock.calls.length;

    view.rerender(
      <App>
        <LangProvider>
          <MetricsExplorer instanceId="instance-2" />
        </LangProvider>
      </App>,
    );

    await waitFor(() =>
      expect(vi.mocked(queryMetrics).mock.calls.length).toBeGreaterThan(
        defaultQueriesBeforeScopeChange,
      ),
    );
    expect(vi.mocked(queryByDataSource).mock.calls).toHaveLength(
      dataSourceQueriesBeforeScopeChange,
    );
    expect(screen.getAllByTitle('默认数据源').length).toBeGreaterThan(0);
  });

  it('exports the clicked metric panel as CSV', async () => {
    const user = userEvent.setup();
    const consumerLagData = {
      resultType: 'matrix',
      series: [
        {
          labels: { cluster: 'prod', group: 'group-a', topic: 'topic-a' },
          values: [{ timestamp: 1_800_000_000, value: '101' }],
          histograms: [],
        },
      ],
      warnings: [],
    };
    vi.mocked(listMetricProfiles).mockResolvedValue([
      {
        ...profiles[0],
        metrics: [
          profiles[0].metrics[0],
          {
            semanticMetric: 'consumer_lag_messages',
            name: 'Consumer Lag Messages',
            unit: 'messages',
            prometheusMetric: 'rocketmq_consumer_lag_messages',
            promql: 'sum(rocketmq_consumer_lag_messages) by (cluster, group, topic)',
            labels: ['cluster', 'group', 'topic'],
          },
        ],
      },
    ]);
    vi.mocked(queryMetrics).mockImplementation((query) =>
      Promise.resolve(
        query.metric.includes('rocketmq_consumer_lag_messages') ? consumerLagData : metricData,
      ),
    );

    renderWithProviders(<MetricsExplorer />);

    await screen.findByText('101 messages');
    await user.click(screen.getByRole('button', { name: 'Consumer Lag Messages 导出 CSV' }));

    expect(downloadCsv).toHaveBeenCalledTimes(1);
    const [filename, csv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(filename).toMatch(/^rocketmq-studio-metrics-consumer-lag-messages-/);
    expect(csv).toContain('"Consumer Lag Messages"');
    expect(csv).toContain('"cluster=prod / group=group-a / topic=topic-a"');
    expect(csv).not.toContain('"Message In TPS"');
  });

  it('opens series details from the clicked metric panel', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MetricsExplorer />);

    await screen.findByRole('img', { name: 'Message In TPS time series' });
    await user.click(screen.getByRole('button', { name: 'Message In TPS 序列明细' }));

    const detailsDialog = await screen.findByRole('dialog', { name: /指标序列明细/ });
    expect(within(detailsDialog).getByText('scalar')).toBeInTheDocument();
    expect(
      within(detailsDialog).getByText('{"cluster":"prod","node_id":"broker-a"}'),
    ).toBeInTheDocument();
    expect(within(detailsDialog).getByText('42')).toBeInTheDocument();
  });

  it('restores profile, range, and source from query history', async () => {
    const user = userEvent.setup();
    localStorage.setItem(METRICS_QUERY_HISTORY_STORAGE_KEY, JSON.stringify([createHistoryEntry()]));

    renderWithProviders(<MetricsExplorer />);

    await screen.findByRole('img', { name: 'Message In TPS time series' });
    await user.click(screen.getByRole('button', { name: '查询历史' }));

    const historyDialog = await screen.findByRole('dialog', { name: '指标查询历史' });
    const historyItem = within(historyDialog)
      .getByText('Consumer Lag Messages')
      .closest('.ant-list-item');
    expect(historyItem).not.toBeNull();
    await user.click(within(historyItem as HTMLElement).getByRole('button', { name: '恢复' }));

    await waitFor(() =>
      expect(queryMetrics).toHaveBeenLastCalledWith({
        metric: 'sum(rocketmq_message_accumulation) by (cluster, group, topic)',
        start: 1_799_978_400,
        end: 1_800_000_000,
        step: '2m',
      }),
    );
    expect(screen.getByText('Consumer Lag Messages')).toBeInTheDocument();
  });

  it('filters query history from other instances and shows the current instance context', async () => {
    const user = userEvent.setup();
    localStorage.setItem(
      METRICS_QUERY_HISTORY_STORAGE_KEY,
      JSON.stringify([
        createHistoryEntry({
          id: 'history-instance-a',
          metricName: 'Instance A Lag',
          instanceId: 'instance-a',
        }),
        createHistoryEntry({
          id: 'history-instance-b',
          metricName: 'Instance B Lag',
          instanceId: 'instance-b',
        }),
      ]),
    );

    renderWithProviders(<MetricsExplorer instanceId="instance-a" />);

    await screen.findByRole('img', { name: 'Message In TPS time series' });
    await user.click(screen.getByRole('button', { name: '查询历史' }));

    const historyDialog = await screen.findByRole('dialog', { name: '指标查询历史' });
    expect(within(historyDialog).getByText('Instance A Lag')).toBeInTheDocument();
    expect(within(historyDialog).getAllByText('实例: instance-a').length).toBeGreaterThan(0);
    expect(within(historyDialog).queryByText('Instance B Lag')).not.toBeInTheDocument();
  });

  it('requires credentials again when restoring a protected data source history item', async () => {
    const user = userEvent.setup();
    vi.mocked(listDataSources).mockResolvedValue([
      {
        key: 'ds-basic',
        name: 'Protected Prometheus',
        type: 'Prometheus',
        url: '',
        auth: 'Basic Auth',
        status: 'healthy',
      },
    ]);
    localStorage.setItem(
      METRICS_QUERY_HISTORY_STORAGE_KEY,
      JSON.stringify([
        createHistoryEntry({
          id: 'history-protected-source',
          profileId: 'rocketmq5-native',
          profileName: 'RocketMQ 5.x Native',
          metricId: 'message_in_tps',
          metricName: 'Message In TPS',
          metricUnit: 'messages/s',
          rangeId: '1h',
          rangeLabel: '1h',
          step: '30s',
          dataSourceKey: 'ds-basic',
          dataSourceName: 'Protected Prometheus',
          promql: 'sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)',
          start: 1_799_996_400,
        }),
      ]),
    );

    renderWithProviders(<MetricsExplorer />);

    const sourceSelect = await screen.findByRole('combobox', { name: '数据源' });
    await user.click(sourceSelect);
    await screen.findByText('Protected Prometheus', {
      selector: '.ant-select-item-option-content',
    });
    await user.keyboard('{Escape}');
    await user.click(screen.getByRole('button', { name: '查询历史' }));

    const historyDialog = await screen.findByRole('dialog', { name: '指标查询历史' });
    const historyItem = within(historyDialog)
      .getByText('Protected Prometheus')
      .closest('.ant-list-item');
    expect(historyItem).not.toBeNull();
    await user.click(within(historyItem as HTMLElement).getByRole('button', { name: '恢复' }));

    const authDialog = await screen.findByRole('dialog', { name: '数据源认证' });
    await user.type(within(authDialog).getByLabelText('用户名'), 'metrics-reader');
    await user.type(within(authDialog).getByLabelText('密码'), 'secret-value');
    await user.click(within(authDialog).getByRole('button', { name: /连\s*接/ }));

    await waitFor(() =>
      expect(queryByDataSource).toHaveBeenCalledWith({
        key: 'ds-basic',
        username: 'metrics-reader',
        password: 'secret-value',
        instanceId: undefined,
        query: {
          metric: 'sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)',
          start: 1_799_996_400,
          end: 1_800_000_000,
          step: '30s',
        },
      }),
    );
    expect(localStorage.getItem(METRICS_QUERY_HISTORY_STORAGE_KEY)).not.toContain('secret-value');
  });
});
