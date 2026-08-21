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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { listDataSources } from '../../api/settings';
import { listMetricProfiles, queryByDataSource, queryMetrics } from '../../api/metrics';
import { LangProvider } from '../../i18n/LangContext';
import MetricsExplorer from '../MetricsExplorer';

vi.mock('../../api/settings', () => ({
  listDataSources: vi.fn(),
}));

vi.mock('../../api/metrics', () => ({
  listMetricProfiles: vi.fn(),
  queryByDataSource: vi.fn(),
  queryMetrics: vi.fn(),
}));

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
    expect(screen.getByRole('button', { name: '刷新指标' })).toBeInTheDocument();
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
});
