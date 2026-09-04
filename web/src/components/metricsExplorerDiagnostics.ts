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

import type { MetricData, MetricMapping, MetricSeries } from '../api/metrics';
import { buildCsv } from '../utils/download';

export const METRICS_QUERY_HISTORY_STORAGE_KEY = 'rocketmq-studio-metrics-query-history';
export const METRICS_QUERY_HISTORY_LIMIT = 12;

export type MetricSampleKind = 'scalar' | 'histogram';

export interface NumericMetricSample {
  timestamp: number;
  value: number;
  kind: MetricSampleKind;
  index: number;
  histogramCount?: number;
  histogramSum?: number;
}

export interface MetricSeriesSamples {
  samples: NumericMetricSample[];
  fromHistogram: boolean;
}

export interface MetricResultSummary {
  seriesCount: number;
  visibleSeriesCount: number;
  sampleCount: number;
  scalarSampleCount: number;
  histogramSampleCount: number;
  warningCount: number;
  earliestTimestamp?: number;
  latestTimestamp?: number;
}

export interface MetricCsvContext {
  profileName: string;
  sourceName: string;
  queryStart?: number;
  queryEnd?: number;
  queriedAt?: number;
}

export interface MetricCsvRow {
  profileName: string;
  sourceName: string;
  metricName: string;
  prometheusMetric: string;
  promql: string;
  resultType: string;
  unit: string;
  seriesIndex: number;
  seriesLabel: string;
  labels: string;
  sampleType: MetricSampleKind;
  timestamp: number;
  timestampIso: string;
  value: number;
  histogramCount?: number;
  histogramSum?: number;
  queryStart?: number;
  queryEnd?: number;
  queriedAt?: number;
}

export interface MetricSeriesDetailRow {
  key: string;
  seriesIndex: number;
  seriesLabel: string;
  labels: string;
  sampleType: MetricSampleKind;
  sampleCount: number;
  latestTimestamp?: number;
  latestValue?: number;
  histogramCount?: number;
  histogramSum?: number;
}

export interface MetricsQueryHistoryEntry {
  id: string;
  profileId: string;
  profileName: string;
  metricId: string;
  metricName: string;
  metricUnit: string;
  rangeId: string;
  rangeLabel: string;
  step: string;
  dataSourceKey: string;
  dataSourceName: string;
  promql: string;
  start: number;
  end: number;
  queriedAt: number;
  instanceId?: string;
  summary: MetricResultSummary;
}

interface MetricsQueryHistoryInput {
  profileId: string;
  profileName: string;
  metric: MetricMapping;
  rangeId: string;
  rangeLabel: string;
  step: string;
  dataSourceKey: string;
  dataSourceName: string;
  start: number;
  end: number;
  queriedAt: number;
  instanceId?: string;
  summary: MetricResultSummary;
}

interface StorageLike {
  getItem: (key: string) => string | null;
  setItem: (key: string, value: string) => void;
  removeItem?: (key: string) => void;
}

const finiteNumber = (value: unknown): number | undefined => {
  if (typeof value === 'number') return Number.isFinite(value) ? value : undefined;
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
};

const getDefaultStorage = (): StorageLike | null => {
  if (typeof window === 'undefined') return null;
  try {
    return window.localStorage;
  } catch {
    return null;
  }
};

const safeRead = (storage: StorageLike | null, key: string) => {
  if (!storage) return null;
  try {
    return storage.getItem(key);
  } catch {
    return null;
  }
};

const safeWrite = (storage: StorageLike | null, key: string, value: string) => {
  if (!storage) return false;
  try {
    storage.setItem(key, value);
    return true;
  } catch {
    return false;
  }
};

const safeRemove = (storage: StorageLike | null, key: string) => {
  if (!storage?.removeItem) return false;
  try {
    storage.removeItem(key);
    return true;
  } catch {
    return false;
  }
};

const sortedEntries = (labels: Record<string, string>) =>
  Object.entries(labels).sort(([left], [right]) => left.localeCompare(right));

export const stableLabelsText = (labels: Record<string, string>) =>
  JSON.stringify(Object.fromEntries(sortedEntries(labels)));

export const metricSeriesLabel = (series: MetricSeries, fallback: string) => {
  const labels = sortedEntries(series.labels)
    .filter(([key]) => key !== '__name__')
    .slice(0, 3)
    .map(([key, value]) => `${key}=${value}`);
  return labels.length > 0 ? labels.join(' / ') : series.labels.__name__ || fallback;
};

const sortMetricSamples = (samples: NumericMetricSample[]) =>
  samples
    .filter((sample) => Number.isFinite(sample.timestamp) && Number.isFinite(sample.value))
    .sort((left, right) => left.timestamp - right.timestamp || left.index - right.index);

const toScalarSamples = (series: MetricSeries): NumericMetricSample[] =>
  sortMetricSamples(
    series.values.map((sample, index) => ({
      timestamp: sample.timestamp,
      value: Number(sample.value),
      kind: 'scalar',
      index,
    })),
  );

// Native histograms carry no scalar samples. To keep diagnostics usable, derive
// a trend value from the observed sum and fall back to observation count.
const toHistogramSamples = (series: MetricSeries): NumericMetricSample[] =>
  sortMetricSamples(
    series.histograms.map((sample, index) => {
      const sum = finiteNumber(sample.histogram.sum);
      const count = finiteNumber(sample.histogram.count);
      return {
        timestamp: sample.timestamp,
        value: sum ?? count ?? Number.NaN,
        kind: 'histogram',
        index,
        ...(count !== undefined ? { histogramCount: count } : {}),
        ...(sum !== undefined ? { histogramSum: sum } : {}),
      };
    }),
  );

export const toMetricSeriesSamples = (series: MetricSeries): MetricSeriesSamples => {
  const scalar = toScalarSamples(series);
  if (scalar.length > 0) {
    return { samples: scalar, fromHistogram: false };
  }
  return { samples: toHistogramSamples(series), fromHistogram: true };
};

export const summarizeMetricData = (data: MetricData): MetricResultSummary => {
  let scalarSampleCount = 0;
  let histogramSampleCount = 0;
  let visibleSeriesCount = 0;
  let earliestTimestamp: number | undefined;
  let latestTimestamp: number | undefined;

  data.series.forEach((series) => {
    const { samples, fromHistogram } = toMetricSeriesSamples(series);
    if (samples.length === 0) return;
    visibleSeriesCount += 1;
    if (fromHistogram) {
      histogramSampleCount += samples.length;
    } else {
      scalarSampleCount += samples.length;
    }
    samples.forEach((sample) => {
      earliestTimestamp =
        earliestTimestamp === undefined
          ? sample.timestamp
          : Math.min(earliestTimestamp, sample.timestamp);
      latestTimestamp =
        latestTimestamp === undefined
          ? sample.timestamp
          : Math.max(latestTimestamp, sample.timestamp);
    });
  });

  return {
    seriesCount: data.series.length,
    visibleSeriesCount,
    sampleCount: scalarSampleCount + histogramSampleCount,
    scalarSampleCount,
    histogramSampleCount,
    warningCount: data.warnings.length,
    ...(earliestTimestamp !== undefined ? { earliestTimestamp } : {}),
    ...(latestTimestamp !== undefined ? { latestTimestamp } : {}),
  };
};

export const buildMetricCsvRows = (
  data: MetricData,
  metric: MetricMapping,
  context: MetricCsvContext,
): MetricCsvRow[] =>
  data.series.flatMap((series, seriesIndex) => {
    const { samples } = toMetricSeriesSamples(series);
    const seriesLabel = metricSeriesLabel(series, metric.name);
    const labels = stableLabelsText(series.labels);
    return samples.map((sample) => ({
      profileName: context.profileName,
      sourceName: context.sourceName,
      metricName: metric.name,
      prometheusMetric: metric.prometheusMetric,
      promql: metric.promql,
      resultType: data.resultType,
      unit: metric.unit,
      seriesIndex: seriesIndex + 1,
      seriesLabel,
      labels,
      sampleType: sample.kind,
      timestamp: sample.timestamp,
      timestampIso: new Date(sample.timestamp * 1000).toISOString(),
      value: sample.value,
      histogramCount: sample.histogramCount,
      histogramSum: sample.histogramSum,
      queryStart: context.queryStart,
      queryEnd: context.queryEnd,
      queriedAt: context.queriedAt,
    }));
  });

export const buildMetricCsvFromRows = (rows: MetricCsvRow[]) =>
  buildCsv(
    [
      { header: 'Profile', value: (row: MetricCsvRow) => row.profileName },
      { header: 'Source', value: (row: MetricCsvRow) => row.sourceName },
      { header: 'Metric', value: (row: MetricCsvRow) => row.metricName },
      { header: 'Prometheus Metric', value: (row: MetricCsvRow) => row.prometheusMetric },
      { header: 'PromQL', value: (row: MetricCsvRow) => row.promql },
      { header: 'Result Type', value: (row: MetricCsvRow) => row.resultType },
      { header: 'Series Index', value: (row: MetricCsvRow) => row.seriesIndex },
      { header: 'Series', value: (row: MetricCsvRow) => row.seriesLabel },
      { header: 'Labels', value: (row: MetricCsvRow) => row.labels },
      { header: 'Sample Type', value: (row: MetricCsvRow) => row.sampleType },
      { header: 'Timestamp', value: (row: MetricCsvRow) => row.timestamp },
      { header: 'Timestamp ISO', value: (row: MetricCsvRow) => row.timestampIso },
      { header: 'Value', value: (row: MetricCsvRow) => row.value },
      { header: 'Unit', value: (row: MetricCsvRow) => row.unit },
      { header: 'Histogram Count', value: (row: MetricCsvRow) => row.histogramCount },
      { header: 'Histogram Sum', value: (row: MetricCsvRow) => row.histogramSum },
      { header: 'Query Start', value: (row: MetricCsvRow) => row.queryStart },
      { header: 'Query End', value: (row: MetricCsvRow) => row.queryEnd },
      { header: 'Queried At', value: (row: MetricCsvRow) => row.queriedAt },
    ],
    rows,
  );

export const buildMetricCsv = (
  data: MetricData,
  metric: MetricMapping,
  context: MetricCsvContext,
) => buildMetricCsvFromRows(buildMetricCsvRows(data, metric, context));

const slugify = (value: string) =>
  value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60);

export const buildMetricCsvFilename = (metric: MetricMapping, timestamp = Date.now()) => {
  const slug = slugify(metric.semanticMetric || metric.name) || 'metric';
  const suffix = new Date(timestamp).toISOString().replace(/[:.]/g, '-');
  return `rocketmq-studio-metrics-${slug}-${suffix}.csv`;
};

export const buildMetricSeriesDetailRows = (
  data: MetricData,
  metric: MetricMapping,
): MetricSeriesDetailRow[] =>
  data.series.map((series, seriesIndex) => {
    const { samples, fromHistogram } = toMetricSeriesSamples(series);
    const latest = samples[samples.length - 1];
    return {
      key: `${seriesIndex}-${stableLabelsText(series.labels)}`,
      seriesIndex: seriesIndex + 1,
      seriesLabel: metricSeriesLabel(series, metric.name),
      labels: stableLabelsText(series.labels),
      sampleType: fromHistogram ? 'histogram' : 'scalar',
      sampleCount: samples.length,
      latestTimestamp: latest?.timestamp,
      latestValue: latest?.value,
      histogramCount: latest?.histogramCount,
      histogramSum: latest?.histogramSum,
    };
  });

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

const readString = (record: Record<string, unknown>, key: string) =>
  typeof record[key] === 'string' ? record[key] : '';

const readOptionalString = (record: Record<string, unknown>, key: string) =>
  typeof record[key] === 'string' ? record[key] : undefined;

const readNumber = (record: Record<string, unknown>, key: string) => {
  const value = record[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
};

const readOptionalNumber = (record: Record<string, unknown>, key: string) => {
  const value = record[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
};

const restoreSummary = (value: unknown): MetricResultSummary => {
  if (!isRecord(value)) {
    return {
      seriesCount: 0,
      visibleSeriesCount: 0,
      sampleCount: 0,
      scalarSampleCount: 0,
      histogramSampleCount: 0,
      warningCount: 0,
    };
  }
  return {
    seriesCount: readNumber(value, 'seriesCount'),
    visibleSeriesCount: readNumber(value, 'visibleSeriesCount'),
    sampleCount: readNumber(value, 'sampleCount'),
    scalarSampleCount: readNumber(value, 'scalarSampleCount'),
    histogramSampleCount: readNumber(value, 'histogramSampleCount'),
    warningCount: readNumber(value, 'warningCount'),
    earliestTimestamp: readOptionalNumber(value, 'earliestTimestamp'),
    latestTimestamp: readOptionalNumber(value, 'latestTimestamp'),
  };
};

const restoreHistoryEntry = (value: unknown): MetricsQueryHistoryEntry | null => {
  if (!isRecord(value)) return null;
  const metricId = readString(value, 'metricId');
  const profileId = readString(value, 'profileId');
  const promql = readString(value, 'promql');
  if (!metricId || !profileId || !promql) return null;
  return {
    id: readString(value, 'id') || `${profileId}:${metricId}:${readNumber(value, 'queriedAt')}`,
    profileId,
    profileName: readString(value, 'profileName'),
    metricId,
    metricName: readString(value, 'metricName'),
    metricUnit: readString(value, 'metricUnit'),
    rangeId: readString(value, 'rangeId') || '1h',
    rangeLabel: readString(value, 'rangeLabel') || '1h',
    step: readString(value, 'step'),
    dataSourceKey: readString(value, 'dataSourceKey'),
    dataSourceName: readString(value, 'dataSourceName'),
    promql,
    start: readNumber(value, 'start'),
    end: readNumber(value, 'end'),
    queriedAt: readNumber(value, 'queriedAt'),
    instanceId: readOptionalString(value, 'instanceId'),
    summary: restoreSummary(value.summary),
  };
};

export const loadMetricsQueryHistory = (
  storage: StorageLike | null = getDefaultStorage(),
): MetricsQueryHistoryEntry[] => {
  const raw = safeRead(storage, METRICS_QUERY_HISTORY_STORAGE_KEY);
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map(restoreHistoryEntry)
      .filter((entry): entry is MetricsQueryHistoryEntry => entry !== null)
      .sort((left, right) => right.queriedAt - left.queriedAt)
      .slice(0, METRICS_QUERY_HISTORY_LIMIT);
  } catch {
    return [];
  }
};

export const saveMetricsQueryHistory = (
  entries: MetricsQueryHistoryEntry[],
  storage: StorageLike | null = getDefaultStorage(),
) => safeWrite(storage, METRICS_QUERY_HISTORY_STORAGE_KEY, JSON.stringify(entries));

export const clearMetricsQueryHistory = (storage: StorageLike | null = getDefaultStorage()) =>
  safeRemove(storage, METRICS_QUERY_HISTORY_STORAGE_KEY);

export const createMetricsQueryHistoryEntry = ({
  profileId,
  profileName,
  metric,
  rangeId,
  rangeLabel,
  step,
  dataSourceKey,
  dataSourceName,
  start,
  end,
  queriedAt,
  instanceId,
  summary,
}: MetricsQueryHistoryInput): MetricsQueryHistoryEntry => ({
  id: [
    profileId,
    metric.semanticMetric,
    rangeId,
    dataSourceKey || 'default',
    instanceId || 'global',
    queriedAt,
  ].join(':'),
  profileId,
  profileName,
  metricId: metric.semanticMetric,
  metricName: metric.name,
  metricUnit: metric.unit,
  rangeId,
  rangeLabel,
  step,
  dataSourceKey,
  dataSourceName,
  promql: metric.promql,
  start,
  end,
  queriedAt,
  ...(instanceId !== undefined ? { instanceId } : {}),
  summary,
});

const historyIdentity = (entry: MetricsQueryHistoryEntry) =>
  [
    entry.profileId,
    entry.metricId,
    entry.rangeId,
    entry.dataSourceKey || 'default',
    entry.instanceId || 'global',
    entry.promql,
  ].join('\n');

export const mergeMetricsQueryHistory = (
  entries: MetricsQueryHistoryEntry[],
  entry: MetricsQueryHistoryEntry,
  limit = METRICS_QUERY_HISTORY_LIMIT,
) => {
  const duplicateKey = historyIdentity(entry);
  return [entry, ...entries.filter((item) => historyIdentity(item) !== duplicateKey)].slice(
    0,
    limit,
  );
};
