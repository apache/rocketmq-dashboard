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
  metricSeriesFullLabel,
  metricSeriesIdentity,
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
    instanceId: overrides.instanceId,
    summary: overrides.summary ?? summarizeMetricData(metricData),
  });

describe('metrics explorer diagnostics', () => {
  it('doesNotInventZeroMeasurementsForBlankScalarSamplesTest', () => {
    const data: MetricData = {
      resultType: 'matrix',
      warnings: [],
      series: [
        {
          labels: { instance: 'broker-a' },
          histograms: [],
          values: [
            { timestamp: 1, value: '0' },
            { timestamp: 2, value: ' -1.25e2 ' },
            { timestamp: 3, value: '' },
            { timestamp: 4, value: ' \t\n ' },
          ],
        },
      ],
    };
    expect(
      toMetricSeriesSamples(data.series[0]).samples.map(({ timestamp, value }) => ({
        timestamp,
        value,
      })),
    ).toEqual([
      { timestamp: 1, value: 0 },
      { timestamp: 2, value: -125 },
    ]);
    expect(summarizeMetricData(data)).toMatchObject({
      sampleCount: 2,
      scalarSampleCount: 2,
      latestTimestamp: 2,
    });
    expect(buildMetricSeriesDetailRows(data, metric)[0]).toMatchObject({
      sampleCount: 2,
      latestTimestamp: 2,
      latestValue: -125,
    });
    expect(
      buildMetricCsvRows(data, metric, { profileName: 'test', sourceName: 'test' }).map(
        (row) => row.value,
      ),
    ).toEqual([0, -125]);
  });

  it('keepsABlankOnlyScalarSeriesUnavailableTest', () => {
    const data: MetricData = {
      resultType: 'matrix',
      warnings: [],
      series: [{ labels: {}, histograms: [], values: [{ timestamp: 1, value: ' ' }] }],
    };
    expect(toMetricSeriesSamples(data.series[0]).samples).toEqual([]);
    expect(summarizeMetricData(data)).toMatchObject({ visibleSeriesCount: 0, sampleCount: 0 });
    expect(buildMetricSeriesDetailRows(data, metric)[0].latestValue).toBeUndefined();
    expect(buildMetricCsvRows(data, metric, { profileName: 'test', sourceName: 'test' })).toEqual(
      [],
    );
  });

  it('preserves both sample kinds in a mixed Prometheus matrix series', () => {
    const data: MetricData = {
      resultType: 'matrix',
      warnings: [],
      series: [
        {
          labels: { instance: 'broker-a' },
          values: [{ timestamp: 2, value: '12' }],
          histograms: [
            { timestamp: 3, histogram: { count: '4', sum: '20', buckets: [] } },
            { timestamp: 1, histogram: { count: '2', sum: '10', buckets: [] } },
          ],
        },
      ],
    };

    expect(
      toMetricSeriesSamples(data.series[0]).samples.map(({ timestamp, kind }) => ({
        timestamp,
        kind,
      })),
    ).toEqual([
      { timestamp: 1, kind: 'histogram' },
      { timestamp: 2, kind: 'scalar' },
      { timestamp: 3, kind: 'histogram' },
    ]);
    expect(summarizeMetricData(data)).toMatchObject({
      seriesCount: 1,
      visibleSeriesCount: 1,
      sampleCount: 3,
      scalarSampleCount: 1,
      histogramSampleCount: 2,
      earliestTimestamp: 1,
      latestTimestamp: 3,
    });
    expect(buildMetricSeriesDetailRows(data, metric)[0]).toMatchObject({
      sampleType: 'mixed',
      sampleCount: 3,
      latestTimestamp: 3,
      latestValue: 20,
      histogramCount: 4,
      histogramSum: 20,
    });
    const rows = buildMetricCsvRows(data, metric, { profileName: 'test', sourceName: 'test' });
    expect(rows.map((row) => row.sampleType)).toEqual(['histogram', 'scalar', 'histogram']);
    expect(rows.map((row) => row.timestamp)).toEqual([1, 2, 3]);
  });

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

  it('keeps the display label compact but the full label set and identity unique', () => {
    const colliding = ['a', 'b'].map((pod) => ({
      labels: { cluster: 'prod', job: 'rmq', namespace: 'ns', pod },
      values: [],
      histograms: [],
    }));

    // The legend stays compact, so both series render the same visible text.
    expect(colliding.map((series) => metricSeriesLabel(series, metric.name))).toEqual([
      'cluster=prod / job=rmq / namespace=ns',
      'cluster=prod / job=rmq / namespace=ns',
    ]);
    // The tooltip keeps the labels the display dropped.
    expect(colliding.map((series) => metricSeriesFullLabel(series, metric.name))).toEqual([
      'cluster=prod / job=rmq / namespace=ns / pod=a',
      'cluster=prod / job=rmq / namespace=ns / pod=b',
    ]);
    // The rendering identity stays unique per original series.
    const identities = colliding.map((series, index) => metricSeriesIdentity(series, index));
    expect(new Set(identities).size).toBe(2);
  });

  it('falls back to the metric name or __name__ for the full label as well', () => {
    expect(
      metricSeriesFullLabel(
        { labels: { __name__: 'up' }, values: [], histograms: [] },
        metric.name,
      ),
    ).toBe('up');
    expect(metricSeriesFullLabel({ labels: {}, values: [], histograms: [] }, metric.name)).toBe(
      metric.name,
    );
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

  it('keeps every epoch column of a CSV row in the same time unit', () => {
    const rows = buildMetricCsvRows(metricData, metric, {
      profileName: 'RocketMQ 5.x Native',
      sourceName: 'Prometheus prod',
      queryStart: 1_799_996_400,
      queryEnd: 1_800_000_000,
      queriedAt: 1_800_000_000_000,
    });

    // Timestamp / Query Start / Query End are epoch seconds; Queried At arrives from the
    // caller as epoch milliseconds, but one CSV file must not mix the two units.
    for (const row of rows) {
      expect(row.queriedAt).toBe(1_800_000_000);
      expect(row.queryStart).toBe(1_799_996_400);
      expect(row.queryEnd).toBe(1_800_000_000);
    }
  });

  it('creates deterministic CSV filenames from metric identity', () => {
    expect(buildMetricCsvFilename(metric, 1_800_000_000_000)).toBe(
      'rocketmq-studio-metrics-message-in-tps-2027-01-15T08-00-00-000Z.csv',
    );
  });

  it('loads, saves, clears, deduplicates, and limits query history without credentials', () => {
    localStorage.clear();
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
    expect(saveMetricsQueryHistory(merged)).toBe(true);
    expect(localStorage.getItem(METRICS_QUERY_HISTORY_STORAGE_KEY)).not.toContain('password');
    expect(loadMetricsQueryHistory()[0].queriedAt).toBe(112);
    expect(clearMetricsQueryHistory()).toBe(true);
    expect(loadMetricsQueryHistory()).toEqual([]);
  });

  it.each([false, true])(
    'keeps unscoped history separate from an instance named global (reverse=%s) Test',
    (reverse) => {
      const unscoped = createHistoryEntry({ queriedAt: 1 });
      const scoped = createHistoryEntry({ instanceId: 'global', queriedAt: 2 });
      const [existing, incoming] = reverse ? [scoped, unscoped] : [unscoped, scoped];

      const merged = mergeMetricsQueryHistory([existing], incoming);

      // The point of this case is separation: an unscoped query and one scoped to an instance
      // literally named "global" must survive as two entries. Ordering follows queriedAt now,
      // so both iteration orders converge on the same result.
      expect(merged).toHaveLength(2);
      expect(merged.map((entry) => entry.id)).toEqual(
        expect.arrayContaining([unscoped.id, scoped.id]),
      );
      expect(merged).toEqual([scoped, unscoped]);
      localStorage.clear();
      expect(saveMetricsQueryHistory(merged)).toBe(true);
      expect(loadMetricsQueryHistory()).toEqual([scoped, unscoped]);
    },
  );

  it('deduplicates repeated queries within the literal global instance Test', () => {
    const older = createHistoryEntry({ instanceId: 'global', queriedAt: 1 });
    const newer = createHistoryEntry({ instanceId: 'global', queriedAt: 2 });

    expect(mergeMetricsQueryHistory([older], newer)).toEqual([newer]);
  });

  it('deduplicates persisted legacy IDs using query fields Test', () => {
    const legacy = {
      ...createHistoryEntry({ instanceId: 'global', queriedAt: 1 }),
      id: 'rocketmq5-native:message_in_tps:1h:default:global:1',
    };
    localStorage.clear();
    saveMetricsQueryHistory([legacy]);
    const newer = createHistoryEntry({ instanceId: 'global', queriedAt: 2 });

    expect(mergeMetricsQueryHistory(loadMetricsQueryHistory(), newer)).toEqual([newer]);
  });

  it('gives unscoped and literal global queries different IDs at the same timestamp Test', () => {
    const unscoped = createHistoryEntry({});
    const scoped = createHistoryEntry({ instanceId: 'global' });

    expect(scoped.id).not.toBe(unscoped.id);
  });

  it('distinguishes the default source from a source with the literal key default Test', () => {
    const implicit = createHistoryEntry({ dataSourceKey: '' });
    const explicit = createHistoryEntry({ dataSourceKey: 'default' });

    expect(explicit.id).not.toBe(implicit.id);
    expect(mergeMetricsQueryHistory([implicit], explicit)).toEqual([explicit, implicit]);
  });

  it('ordersLateCompletionsByQueryTimeWithoutMutatingInputTest', () => {
    const newest = createHistoryEntry({ rangeId: '3h', queriedAt: 30 });
    const oldest = createHistoryEntry({ rangeId: '1h', queriedAt: 10 });
    const middle = createHistoryEntry({ rangeId: '2h', queriedAt: 20 });
    const entries = [newest, oldest];

    expect(mergeMetricsQueryHistory(entries, middle)).toEqual([newest, middle, oldest]);
    expect(entries).toEqual([newest, oldest]);
  });

  it('capsHistoryAfterOrderingSoAnOlderCompletionCannotEvictANewerQueryTest', () => {
    const entries = Array.from({ length: METRICS_QUERY_HISTORY_LIMIT }, (_, index) =>
      createHistoryEntry({ rangeId: `${index + 1}h`, queriedAt: 100 - index }),
    );
    const late = createHistoryEntry({ rangeId: 'old', queriedAt: 1 });

    expect(mergeMetricsQueryHistory(entries, late)).toEqual(entries);
  });

  it('retainsTheNewestOccurrenceWhenAnOlderDuplicateCompletesTest', () => {
    const newest = createHistoryEntry({ queriedAt: 30 });
    const late = createHistoryEntry({ queriedAt: 10 });

    expect(mergeMetricsQueryHistory([newest], late)).toEqual([newest]);
    expect(mergeMetricsQueryHistory([late], newest)).toEqual([newest]);
  });

  it('keepsEqualTimeOrderingStableAcrossPersistenceReloadTest', () => {
    localStorage.clear();
    const first = createHistoryEntry({ rangeId: '1h', queriedAt: 10 });
    const second = createHistoryEntry({ rangeId: '2h', queriedAt: 10 });
    const incoming = createHistoryEntry({ rangeId: '3h', queriedAt: 10 });
    const merged = mergeMetricsQueryHistory([first, second], incoming);

    expect(merged).toEqual([incoming, first, second]);
    expect(saveMetricsQueryHistory(merged)).toBe(true);
    expect(loadMetricsQueryHistory()).toEqual(merged);
    clearMetricsQueryHistory();
  });

  it('deduplicatesBeforeApplyingACustomHistoryLimitTest', () => {
    const newest = createHistoryEntry({ rangeId: '1h', queriedAt: 30 });
    const duplicate = createHistoryEntry({ rangeId: '1h', queriedAt: 20 });
    const distinct = createHistoryEntry({ rangeId: '2h', queriedAt: 10 });
    const late = createHistoryEntry({ rangeId: '3h', queriedAt: 1 });

    expect(mergeMetricsQueryHistory([newest, duplicate, distinct], late, 2)).toEqual([
      newest,
      distinct,
    ]);
  });

  // The unavailable-storage fallbacks are covered by browserStorage.test.ts, which is where
  // the try/catch now lives, so this only asserts the malformed-payload path.
  it('ignores malformed persisted history entries', () => {
    localStorage.clear();
    localStorage.setItem(METRICS_QUERY_HISTORY_STORAGE_KEY, '{"broken"');

    expect(loadMetricsQueryHistory()).toEqual([]);
  });
});
