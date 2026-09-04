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

import { describe, expect, it } from 'vitest';

import type { MetricData, MetricMapping } from '../api/metrics';
import {
  METRICS_QUERY_HISTORY_LIMIT,
  METRICS_QUERY_HISTORY_STORAGE_KEY,
  buildMetricCsv,
  buildMetricCsvFilename,
  buildMetricCsvRows,
  buildMetricSeriesDetailRows,
  clearMetricsQueryHistory,
  createMetricsQueryHistoryEntry,
  loadMetricsQueryHistory,
  mergeMetricsQueryHistory,
  metricSeriesLabel,
  saveMetricsQueryHistory,
  stableLabelsText,
  summarizeMetricData,
  toMetricSeriesSamples,
} from './metricsExplorerDiagnostics';

const metric: MetricMapping = {
  semanticMetric: 'message_in_tps',
  name: 'Message In TPS',
  unit: 'messages/s',
  prometheusMetric: 'rocketmq_messages_in_total',
  promql: 'sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)',
  labels: ['cluster', 'node_id'],
};

const metricData: MetricData = {
  resultType: 'matrix',
  series: [
    {
      labels: { node_id: 'broker-a', cluster: 'prod' },
      values: [
        { timestamp: 1_800_000_000, value: '42' },
        { timestamp: 1_799_996_400, value: '40' },
        { timestamp: 1_799_996_401, value: 'NaN' },
      ],
      histograms: [],
    },
    {
      labels: { cluster: 'prod', node_id: 'broker-b' },
      values: [],
      histograms: [
        { timestamp: 1_799_996_400, histogram: { count: '10', sum: '250', buckets: [] } },
        { timestamp: 1_800_000_000, histogram: { count: '20', sum: '600', buckets: [] } },
      ],
    },
  ],
  warnings: ['partial response'],
};

const createStorage = () => {
  const records = new Map<string, string>();
  return {
    records,
    getItem: (key: string) => records.get(key) ?? null,
    setItem: (key: string, value: string) => {
      records.set(key, value);
    },
    removeItem: (key: string) => {
      records.delete(key);
    },
  };
};

const createHistoryEntry = (
  overrides: Partial<ReturnType<typeof createMetricsQueryHistoryEntry>>,
) =>
  createMetricsQueryHistoryEntry({
    profileId: overrides.profileId ?? 'rocketmq5-native',
    profileName: overrides.profileName ?? 'RocketMQ 5.x Native',
    metric,
    rangeId: overrides.rangeId ?? '1h',
    rangeLabel: overrides.rangeLabel ?? '1h',
    step: overrides.step ?? '30s',
    dataSourceKey: overrides.dataSourceKey ?? '',
    dataSourceName: overrides.dataSourceName ?? '',
    start: overrides.start ?? 1_799_996_400,
    end: overrides.end ?? 1_800_000_000,
    queriedAt: overrides.queriedAt ?? 1_800_000_000_000,
    summary: overrides.summary ?? summarizeMetricData(metricData),
  });

describe('metrics explorer diagnostics', () => {
  it('sorts scalar samples and ignores non-numeric values', () => {
    const samples = toMetricSeriesSamples(metricData.series[0]);

    expect(samples.fromHistogram).toBe(false);
    expect(samples.samples.map((sample) => sample.timestamp)).toEqual([
      1_799_996_400, 1_800_000_000,
    ]);
    expect(samples.samples.map((sample) => sample.value)).toEqual([40, 42]);
  });

  it('derives histogram-only samples from sums and falls back to counts', () => {
    const histogramOnly = toMetricSeriesSamples({
      labels: { cluster: 'prod' },
      values: [],
      histograms: [
        { timestamp: 1, histogram: { count: '10', sum: '', buckets: [] } },
        { timestamp: 2, histogram: { count: '20', sum: '600', buckets: [] } },
      ],
    });

    expect(histogramOnly.fromHistogram).toBe(true);
    expect(histogramOnly.samples).toMatchObject([
      { timestamp: 1, value: 10, histogramCount: 10 },
      { timestamp: 2, value: 600, histogramCount: 20, histogramSum: 600 },
    ]);
  });

  it('builds stable labels, readable series labels, summaries, and detail rows', () => {
    expect(stableLabelsText({ node_id: 'broker-a', cluster: 'prod' })).toBe(
      '{"cluster":"prod","node_id":"broker-a"}',
    );
    expect(metricSeriesLabel(metricData.series[0], metric.name)).toBe(
      'cluster=prod / node_id=broker-a',
    );

    expect(summarizeMetricData(metricData)).toEqual({
      seriesCount: 2,
      visibleSeriesCount: 2,
      sampleCount: 4,
      scalarSampleCount: 2,
      histogramSampleCount: 2,
      warningCount: 1,
      earliestTimestamp: 1_799_996_400,
      latestTimestamp: 1_800_000_000,
    });

    expect(buildMetricSeriesDetailRows(metricData, metric)).toMatchObject([
      {
        seriesIndex: 1,
        sampleType: 'scalar',
        sampleCount: 2,
        latestTimestamp: 1_800_000_000,
        latestValue: 42,
      },
      {
        seriesIndex: 2,
        sampleType: 'histogram',
        sampleCount: 2,
        latestTimestamp: 1_800_000_000,
        latestValue: 600,
        histogramCount: 20,
        histogramSum: 600,
      },
    ]);
  });

  it('exports scalar and histogram samples as formula-safe CSV rows', () => {
    const dangerousMetric = { ...metric, name: '=Message In TPS' };
    const rows = buildMetricCsvRows(metricData, dangerousMetric, {
      profileName: 'RocketMQ 5.x Native',
      sourceName: 'Prometheus prod',
      queryStart: 1_799_996_400,
      queryEnd: 1_800_000_000,
      queriedAt: 1_800_000_000_000,
    });
    const csv = buildMetricCsv(metricData, dangerousMetric, {
      profileName: 'RocketMQ 5.x Native',
      sourceName: 'Prometheus prod',
    });

    expect(rows).toHaveLength(4);
    expect(rows[2]).toMatchObject({
      sampleType: 'histogram',
      value: 250,
      histogramCount: 10,
      histogramSum: 250,
    });
    expect(csv).toContain('"\'=Message In TPS"');
    expect(csv).toContain('"histogram"');
    expect(csv).toContain('"2027-01-15T07:00:00.000Z"');
  });

  it('creates deterministic CSV filenames from metric identity', () => {
    expect(buildMetricCsvFilename(metric, 1_800_000_000_000)).toBe(
      'rocketmq-studio-metrics-message-in-tps-2027-01-15T08-00-00-000Z.csv',
    );
  });

  it('loads, saves, clears, deduplicates, and limits query history without credentials', () => {
    const storage = createStorage();
    const first = createHistoryEntry({ queriedAt: 1 });
    const duplicate = createHistoryEntry({ queriedAt: 2 });
    const distinct = Array.from({ length: METRICS_QUERY_HISTORY_LIMIT + 1 }, (_, index) =>
      createHistoryEntry({
        rangeId: `${index + 1}h`,
        rangeLabel: `${index + 1}h`,
        queriedAt: 100 + index,
      }),
    );

    const merged = distinct.reduce(
      (current, entry) => mergeMetricsQueryHistory(current, entry),
      mergeMetricsQueryHistory([first], duplicate),
    );

    expect(merged).toHaveLength(METRICS_QUERY_HISTORY_LIMIT);
    expect(merged.find((entry) => entry.queriedAt === 1)).toBeUndefined();
    expect(saveMetricsQueryHistory(merged, storage)).toBe(true);
    expect(storage.records.get(METRICS_QUERY_HISTORY_STORAGE_KEY)).not.toContain('password');
    expect(loadMetricsQueryHistory(storage)[0].queriedAt).toBe(112);
    expect(clearMetricsQueryHistory(storage)).toBe(true);
    expect(loadMetricsQueryHistory(storage)).toEqual([]);
  });

  it('ignores malformed persisted history entries and unavailable storage', () => {
    const storage = createStorage();
    storage.setItem(METRICS_QUERY_HISTORY_STORAGE_KEY, '{"broken"');

    expect(loadMetricsQueryHistory(storage)).toEqual([]);
    expect(saveMetricsQueryHistory([createHistoryEntry({})], null)).toBe(false);
    expect(clearMetricsQueryHistory(null)).toBe(false);
  });
});
