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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Empty,
  Flex,
  Form,
  Input,
  Modal,
  Segmented,
  Select,
  Skeleton,
  Spin,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { ArrowsClockwise } from '@phosphor-icons/react';

import { listDataSources } from '../api/settings';
import { listMetricProfiles, queryByDataSource, queryMetrics } from '../api/metrics';
import type { DataSource } from '../api/settings';
import type { MetricData, MetricMapping, MetricProfile, MetricSeries } from '../api/metrics';
import { useLang } from '../i18n/LangContext';

const { Text, Title } = Typography;

const CHART_WIDTH = 840;
const CHART_HEIGHT = 240;
const CHART_PADDING = { top: 18, right: 18, bottom: 36, left: 76 };
const SERIES_COLORS = ['#1677ff', '#52c41a', '#fa8c16', '#722ed1', '#13c2c2', '#eb2f96'];

const RANGE_OPTIONS = [
  { label: '1h', value: '1h', seconds: 60 * 60, step: '30s' },
  { label: '6h', value: '6h', seconds: 6 * 60 * 60, step: '2m' },
  { label: '24h', value: '24h', seconds: 24 * 60 * 60, step: '5m' },
] as const;

// High-cardinality queries (per topic/group) can return dozens of series; keep the
// busiest ones so the panel layout stays readable.
const MAX_SERIES = 10;

type RangeOption = (typeof RANGE_OPTIONS)[number];

const PROFILE_STORAGE_KEY = 'rocketmq-studio.metric-profile';

interface NumericSample {
  timestamp: number;
  value: number;
}

const sortAndStrip = (
  samples: { timestamp: number; value: number; index: number }[],
): NumericSample[] =>
  samples
    .filter((sample) => Number.isFinite(sample.timestamp) && Number.isFinite(sample.value))
    .sort((left, right) => left.timestamp - right.timestamp || left.index - right.index)
    .map(({ timestamp, value }) => ({ timestamp, value }));

const toScalarSamples = (series: MetricSeries): NumericSample[] =>
  sortAndStrip(
    series.values.map((sample, index) => ({
      timestamp: sample.timestamp,
      value: Number(sample.value),
      index,
    })),
  );

// Native histograms carry no scalar samples. To avoid plotting them as "no data", derive a
// trend value per histogram: the observed sum (in the metric's unit), falling back to the
// observation count when the sum is absent or non-finite.
const toHistogramSamples = (series: MetricSeries): NumericSample[] =>
  sortAndStrip(
    series.histograms.map((sample, index) => {
      // An empty string parses to 0, so treat a blank field as missing rather than zero.
      const sumText = sample.histogram.sum?.trim();
      const countText = sample.histogram.count?.trim();
      const sum = sumText ? Number(sumText) : Number.NaN;
      const count = countText ? Number(countText) : Number.NaN;
      const value = Number.isFinite(sum) ? sum : count;
      return { timestamp: sample.timestamp, value, index };
    }),
  );

const toNumericSamples = (
  series: MetricSeries,
): { samples: NumericSample[]; fromHistogram: boolean } => {
  const scalar = toScalarSamples(series);
  if (scalar.length > 0) {
    return { samples: scalar, fromHistogram: false };
  }
  return { samples: toHistogramSamples(series), fromHistogram: true };
};

const seriesLabel = (series: MetricSeries, fallback: string) => {
  const labels = Object.entries(series.labels)
    .filter(([key]) => key !== '__name__')
    .slice(0, 3)
    .map(([key, value]) => `${key}=${value}`);
  return labels.length > 0 ? labels.join(' / ') : series.labels.__name__ || fallback;
};

const formatMetricValue = (value: number) =>
  new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value);

interface MetricChartProps {
  data: MetricData;
  metric: MetricMapping;
  locale: string;
  noSamples: string;
  histogramLabel: string;
  histogramTooltip: string;
  hiddenSeriesText: (count: number) => string;
}

const MetricChart = ({
  data,
  metric,
  locale,
  noSamples,
  histogramLabel,
  histogramTooltip,
  hiddenSeriesText,
}: MetricChartProps) => {
  const allSeries = data.series
    .map((series, index) => {
      const { samples, fromHistogram } = toNumericSamples(series);
      return {
        color: SERIES_COLORS[index % SERIES_COLORS.length],
        label: seriesLabel(series, metric.name),
        samples,
        fromHistogram,
      };
    })
    .filter((series) => series.samples.length > 0);

  if (allSeries.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={noSamples} />;
  }

  const latestValue = (series: { samples: NumericSample[] }) =>
    series.samples[series.samples.length - 1].value;
  const hiddenCount = Math.max(0, allSeries.length - MAX_SERIES);
  const chartSeries =
    hiddenCount === 0
      ? allSeries
      : [...allSeries]
          .sort((left, right) => latestValue(right) - latestValue(left))
          .slice(0, MAX_SERIES);

  const samples = chartSeries.flatMap((series) => series.samples);
  const timestamps = samples.map((sample) => sample.timestamp);
  const values = samples.map((sample) => sample.value);
  let minTime = Math.min(...timestamps);
  let maxTime = Math.max(...timestamps);
  let minValue = Math.min(...values);
  let maxValue = Math.max(...values);

  if (minTime === maxTime) {
    minTime -= 1;
    maxTime += 1;
  }
  if (minValue === maxValue) {
    const padding = Math.max(Math.abs(minValue) * 0.1, 1);
    minValue -= padding;
    maxValue += padding;
  }

  const plotWidth = CHART_WIDTH - CHART_PADDING.left - CHART_PADDING.right;
  const plotHeight = CHART_HEIGHT - CHART_PADDING.top - CHART_PADDING.bottom;
  const x = (timestamp: number) =>
    CHART_PADDING.left + ((timestamp - minTime) / (maxTime - minTime)) * plotWidth;
  const y = (value: number) =>
    CHART_PADDING.top + (1 - (value - minValue) / (maxValue - minValue)) * plotHeight;
  const formatTime = (timestamp: number) =>
    new Date(timestamp * 1000).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' });

  return (
    <div>
      <svg
        role="img"
        aria-label={`${metric.name} time series`}
        viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
        preserveAspectRatio="xMidYMid meet"
        style={{ width: '100%', minHeight: 220, display: 'block' }}
      >
        {[0, 0.5, 1].map((ratio) => {
          const gridY = CHART_PADDING.top + ratio * plotHeight;
          const gridValue = maxValue - ratio * (maxValue - minValue);
          return (
            <g key={ratio}>
              <line
                x1={CHART_PADDING.left}
                x2={CHART_WIDTH - CHART_PADDING.right}
                y1={gridY}
                y2={gridY}
                stroke="#e8e8e8"
                strokeWidth="1"
              />
              <text
                x={CHART_PADDING.left - 8}
                y={gridY + 6}
                textAnchor="end"
                fontSize="20"
                fill="#8c8c8c"
              >
                {formatMetricValue(gridValue)}
              </text>
            </g>
          );
        })}
        {chartSeries.map((series) => (
          <polyline
            key={series.label}
            fill="none"
            stroke={series.color}
            strokeWidth="2.5"
            strokeLinejoin="round"
            strokeLinecap="round"
            points={series.samples
              .map((sample) => `${x(sample.timestamp)},${y(sample.value)}`)
              .join(' ')}
          />
        ))}
        <text
          x={CHART_PADDING.left}
          y={CHART_HEIGHT - 8}
          textAnchor="start"
          fontSize="20"
          fill="#8c8c8c"
        >
          {formatTime(minTime)}
        </text>
        <text
          x={CHART_WIDTH - CHART_PADDING.right}
          y={CHART_HEIGHT - 8}
          textAnchor="end"
          fontSize="20"
          fill="#8c8c8c"
        >
          {formatTime(maxTime)}
        </text>
      </svg>

      <Flex gap="8px 16px" wrap="wrap" style={{ marginTop: 8 }}>
        {chartSeries.map((series) => {
          const latest = series.samples[series.samples.length - 1];
          return (
            <Flex
              key={series.label}
              align="center"
              gap={6}
              style={{ flex: '0 1 auto', minWidth: 0, maxWidth: '100%' }}
            >
              <span
                style={{ width: 14, height: 3, background: series.color, display: 'inline-block' }}
              />
              <Text type="secondary" ellipsis={{ tooltip: series.label }} style={{ maxWidth: 160 }}>
                {series.label}
              </Text>
              {series.fromHistogram ? (
                <Tooltip title={histogramTooltip}>
                  <Tag color="purple" style={{ marginInlineEnd: 0 }}>
                    {histogramLabel}
                  </Tag>
                </Tooltip>
              ) : null}
              <Text strong>
                {formatMetricValue(latest.value)}
                {metric.unit ? ` ${metric.unit}` : ''}
              </Text>
            </Flex>
          );
        })}
        {hiddenCount > 0 ? (
          <Text type="secondary" style={{ flex: '1 1 100%' }}>
            {hiddenSeriesText(hiddenCount)}
          </Text>
        ) : null}
      </Flex>
    </div>
  );
};

interface MetricsExplorerProps {
  instanceId?: string;
}

type DataSourceAuthMode = 'none' | 'basic' | 'bearer';

interface AuthFormValues {
  username?: string;
  password?: string;
  bearerToken?: string;
}

interface DataSourceCredentials extends AuthFormValues {
  key: string;
}

interface PanelState {
  loading: boolean;
  data?: MetricData;
  error?: string;
}

const getQueryErrorMessage = (error: unknown, fallback: string): string => {
  if (typeof error !== 'object' || error === null || !('response' in error)) {
    return fallback;
  }

  const response = error.response;
  if (typeof response !== 'object' || response === null || !('data' in response)) {
    return fallback;
  }

  const data = response.data;
  if (typeof data !== 'object' || data === null || !('message' in data)) {
    return fallback;
  }

  const message = data.message;
  return typeof message === 'string' && message.trim() ? message : fallback;
};

const getDataSourceAuthMode = (auth: string): DataSourceAuthMode => {
  const normalized = auth.trim().toLowerCase();
  if (normalized === 'basic' || normalized === 'basic auth') return 'basic';
  if (normalized === 'bearer' || normalized === 'bearer token') return 'bearer';
  return 'none';
};

const MetricsExplorer = ({ instanceId }: MetricsExplorerProps) => {
  const { t, lang } = useLang();
  const locale = lang === 'zh' ? 'zh-CN' : 'en-US';
  const [authForm] = Form.useForm<AuthFormValues>();
  const [profiles, setProfiles] = useState<MetricProfile[]>([]);
  const [profileId, setProfileId] = useState('');
  const [rangeId, setRangeId] = useState<RangeOption['value']>('1h');
  const [panels, setPanels] = useState<Record<string, PanelState>>({});
  const [profilesLoading, setProfilesLoading] = useState(true);
  const [profileError, setProfileError] = useState(false);
  const [customPromql, setCustomPromql] = useState('');
  const [customPanel, setCustomPanel] = useState<PanelState | null>(null);
  const [appliedCustomPromql, setAppliedCustomPromql] = useState('');
  const [dataSources, setDataSources] = useState<DataSource[]>([]);
  const [dataSourceKey, setDataSourceKey] = useState('');
  const [dataSourcesLoading, setDataSourcesLoading] = useState(true);
  const [pendingDataSource, setPendingDataSource] = useState<DataSource | null>(null);
  const requestId = useRef(0);
  // Keeps the latest data source readable from the stable callbacks so switching
  // the source uses the new key instead of a stale closure value.
  const dataSourceKeyRef = useRef(dataSourceKey);
  const dataSourceCredentialsRef = useRef<DataSourceCredentials | null>(null);

  const selectedProfile = useMemo(
    () => profiles.find((profile) => profile.id === profileId),
    [profileId, profiles],
  );
  const selectedRange = RANGE_OPTIONS.find((range) => range.value === rangeId) ?? RANGE_OPTIONS[0];
  const anyLoading =
    Object.values(panels).some((panel) => panel.loading) || Boolean(customPanel?.loading);
  const availableDataSources = useMemo(
    () =>
      dataSources.filter(
        (source) =>
          !source.instanceIds?.length ||
          (instanceId !== undefined && source.instanceIds.includes(instanceId)),
      ),
    [dataSources, instanceId],
  );
  const availableDataSourceKeysRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    availableDataSourceKeysRef.current = new Set(availableDataSources.map((source) => source.key));
  }, [availableDataSources]);

  const runQuery = useCallback(
    (promql: string, range: RangeOption): Promise<MetricData> => {
      const end = Math.floor(Date.now() / 1000);
      const query = { metric: promql, start: end - range.seconds, end, step: range.step };
      const selectedDataSourceKey = dataSourceKeyRef.current;
      const currentDataSourceKey =
        selectedDataSourceKey && availableDataSourceKeysRef.current.has(selectedDataSourceKey)
          ? selectedDataSourceKey
          : '';
      const credentials =
        dataSourceCredentialsRef.current?.key === currentDataSourceKey
          ? dataSourceCredentialsRef.current
          : null;
      return currentDataSourceKey
        ? queryByDataSource({
            key: currentDataSourceKey,
            query,
            instanceId,
            ...(credentials?.username !== undefined ? { username: credentials.username } : {}),
            ...(credentials?.password !== undefined ? { password: credentials.password } : {}),
            ...(credentials?.bearerToken !== undefined
              ? { bearerToken: credentials.bearerToken }
              : {}),
          })
        : queryMetrics(query);
    },
    [instanceId],
  );

  const loadAll = useCallback(
    async (profile: MetricProfile | undefined, range: RangeOption) => {
      if (!profile) return;
      const currentRequest = ++requestId.current;
      const loadingPatch = Object.fromEntries(
        profile.metrics.map((metric) => [metric.semanticMetric, { loading: true } as PanelState]),
      );
      setPanels(loadingPatch);
      await Promise.all(
        profile.metrics.map(async (metric) => {
          try {
            const result = await runQuery(metric.promql, range);
            if (currentRequest === requestId.current) {
              setPanels((previous) => ({
                ...previous,
                [metric.semanticMetric]: { loading: false, data: result },
              }));
            }
          } catch (error) {
            if (currentRequest === requestId.current) {
              setPanels((previous) => ({
                ...previous,
                [metric.semanticMetric]: {
                  loading: false,
                  error: getQueryErrorMessage(error, t('metricsExplorer.queryErrorFallback')),
                },
              }));
            }
          }
        }),
      );
    },
    [t, runQuery],
  );

  useEffect(() => {
    let cancelled = false;
    void listMetricProfiles()
      .then((nextProfiles) => {
        if (cancelled) return;
        setProfiles(nextProfiles);
        const storedProfileId = localStorage.getItem(PROFILE_STORAGE_KEY);
        const initialProfile =
          nextProfiles.find((profile) => profile.id === storedProfileId) ?? nextProfiles[0];
        setProfileId(initialProfile?.id ?? '');
        void loadAll(initialProfile, RANGE_OPTIONS[0]);
      })
      .catch(() => {
        if (!cancelled) setProfileError(true);
      })
      .finally(() => {
        if (!cancelled) setProfilesLoading(false);
      });
    return () => {
      cancelled = true;
      requestId.current += 1;
    };
  }, [loadAll]);

  const handleProfileChange = (nextProfileId: string) => {
    const nextProfile = profiles.find((profile) => profile.id === nextProfileId);
    localStorage.setItem(PROFILE_STORAGE_KEY, nextProfileId);
    setProfileId(nextProfileId);
    void loadAll(nextProfile, selectedRange);
  };

  const handleRangeChange = (nextRangeId: RangeOption['value']) => {
    const nextRange =
      RANGE_OPTIONS.find((range) => range.value === nextRangeId) ?? RANGE_OPTIONS[0];
    setRangeId(nextRangeId);
    void loadAll(selectedProfile, nextRange);
  };

  const runCustomQuery = useCallback(
    async (promql: string, range: RangeOption) => {
      const trimmed = promql.trim();
      if (!trimmed) return;
      const currentRequest = ++requestId.current;
      setCustomPanel({ loading: true });
      setAppliedCustomPromql(trimmed);
      try {
        const result = await runQuery(trimmed, range);
        if (currentRequest === requestId.current) {
          setCustomPanel({ loading: false, data: result });
        }
      } catch (error) {
        if (currentRequest === requestId.current) {
          setCustomPanel({
            loading: false,
            error: getQueryErrorMessage(error, t('metricsExplorer.queryErrorFallback')),
          });
        }
      }
    },
    [t, runQuery],
  );

  const activateDataSource = (nextKey: string, credentials?: AuthFormValues) => {
    dataSourceCredentialsRef.current = credentials ? { key: nextKey, ...credentials } : null;
    dataSourceKeyRef.current = nextKey;
    setDataSourceKey(nextKey);
    void loadAll(selectedProfile, selectedRange);
    if (appliedCustomPromql) {
      void runCustomQuery(appliedCustomPromql, selectedRange);
    }
  };

  const handleDataSourceChange = (nextKey: string) => {
    const nextSource = availableDataSources.find((source) => source.key === nextKey);
    if (nextSource && getDataSourceAuthMode(nextSource.auth) !== 'none') {
      authForm.resetFields();
      setPendingDataSource(nextSource);
      return;
    }
    activateDataSource(nextKey);
  };

  const handleAuthSubmit = (values: AuthFormValues) => {
    if (!pendingDataSource) return;
    const authMode = getDataSourceAuthMode(pendingDataSource.auth);
    const credentials =
      authMode === 'basic'
        ? { username: values.username, password: values.password }
        : { bearerToken: values.bearerToken };
    activateDataSource(pendingDataSource.key, credentials);
    setPendingDataSource(null);
    authForm.resetFields();
  };

  const handleAuthCancel = () => {
    setPendingDataSource(null);
    authForm.resetFields();
  };

  useEffect(() => {
    let cancelled = false;
    void listDataSources()
      .then((next) => {
        if (!cancelled) setDataSources(next);
      })
      .catch(() => {
        /* data-source list is optional; the default source still works */
      })
      .finally(() => {
        if (!cancelled) setDataSourcesLoading(false);
      });
    return () => {
      cancelled = true;
      dataSourceCredentialsRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (dataSourceKey && !availableDataSources.some((source) => source.key === dataSourceKey)) {
      window.setTimeout(() => {
        // Keep the ref in sync with the state; queries read the ref, so a stale key would
        // keep hitting the de-registered data source while the UI shows the default.
        dataSourceCredentialsRef.current = null;
        dataSourceKeyRef.current = '';
        setDataSourceKey('');
        setPendingDataSource(null);
        void loadAll(selectedProfile, selectedRange);
        if (appliedCustomPromql) {
          void runCustomQuery(appliedCustomPromql, selectedRange);
        }
      }, 0);
    }
  }, [
    availableDataSources,
    dataSourceKey,
    loadAll,
    runCustomQuery,
    selectedProfile,
    selectedRange,
    appliedCustomPromql,
  ]);

  const pendingAuthMode = pendingDataSource
    ? getDataSourceAuthMode(pendingDataSource.auth)
    : 'none';

  const renderPanel = (metric: MetricMapping) => {
    const state = panels[metric.semanticMetric];
    return (
      <Card
        key={metric.semanticMetric}
        size="small"
        title={
          <Flex gap={8} align="center">
            <span>{metric.name}</span>
            {metric.unit ? <Tag style={{ marginInlineEnd: 0 }}>{metric.unit}</Tag> : null}
          </Flex>
        }
      >
        {state?.loading ? (
          <Flex justify="center" style={{ minHeight: 200 }} align="center">
            <Spin />
          </Flex>
        ) : state?.error ? (
          <Alert type="error" showIcon message={state.error} />
        ) : state?.data ? (
          <>
            {state.data.warnings.map((warning) => (
              <Alert
                key={warning}
                type="warning"
                showIcon
                message={warning}
                style={{ marginBottom: 8 }}
              />
            ))}
            <MetricChart
              data={state.data}
              metric={metric}
              locale={locale}
              noSamples={t('metricsExplorer.noSamples')}
              histogramLabel={t('metricsExplorer.histogram')}
              histogramTooltip={t('metricsExplorer.histogramTooltip')}
              hiddenSeriesText={(count: number) => t('metricsExplorer.hiddenSeries', { count, maxSeries: MAX_SERIES })}
            />
          </>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('metricsExplorer.noSamples')} />
        )}
      </Card>
    );
  };

  const customMetric: MetricMapping = {
    semanticMetric: 'custom',
    name: appliedCustomPromql || t('metricsExplorer.customTitle'),
    unit: '',
    prometheusMetric: '',
    promql: appliedCustomPromql,
    labels: [],
  };

  return (
    <section aria-labelledby="metrics-explorer-title" style={{ marginTop: 24 }}>
      <Flex
        justify="space-between"
        align="center"
        gap={16}
        wrap="wrap"
        style={{ marginBottom: 12 }}
      >
        <Title id="metrics-explorer-title" level={4} style={{ margin: 0, fontSize: 16 }}>
          {t('metricsExplorer.title')}
        </Title>

        <Flex gap={8} wrap="wrap" align="center" style={{ maxWidth: '100%' }}>
          <Select
            aria-label={t('metricsExplorer.dataSource')}
            value={dataSourceKey}
            loading={dataSourcesLoading}
            onChange={handleDataSourceChange}
            options={[
              { label: t('metricsExplorer.defaultDataSource'), value: '' },
              ...availableDataSources.map((ds) => ({ label: ds.name, value: ds.key })),
            ]}
            style={{ width: 200, maxWidth: '100%' }}
          />
          <Select
            aria-label={t('metricsExplorer.profile')}
            value={profileId || undefined}
            loading={profilesLoading}
            onChange={handleProfileChange}
            options={profiles.map((profile) => ({ label: profile.name, value: profile.id }))}
            style={{ width: 210, maxWidth: '100%' }}
          />
          <Segmented
            aria-label={t('metricsExplorer.range')}
            size="small"
            value={rangeId}
            onChange={(value) => handleRangeChange(value as RangeOption['value'])}
            options={RANGE_OPTIONS.map(({ label, value }) => ({ label, value }))}
          />
          <Tooltip title={t('metricsExplorer.refresh')}>
            <Button
              aria-label={t('metricsExplorer.refresh')}
              icon={<ArrowsClockwise size={16} />}
              onClick={() => {
                void loadAll(selectedProfile, selectedRange);
                if (appliedCustomPromql) {
                  void runCustomQuery(appliedCustomPromql, selectedRange);
                }
              }}
              loading={anyLoading}
            />
          </Tooltip>
        </Flex>
      </Flex>

      {profilesLoading ? (
        <Skeleton active paragraph={{ rows: 5 }} />
      ) : profileError ? (
        <Alert type="error" showIcon message={t('metricsExplorer.profileError')} />
      ) : profiles.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('metricsExplorer.noProfiles')} />
      ) : (
        <>
          <Card
            size="small"
            title={t('metricsExplorer.customTitle')}
            style={{ marginBottom: 16 }}
            extra={
              <Button
                type="primary"
                size="small"
                loading={Boolean(customPanel?.loading)}
                disabled={!customPromql.trim()}
                onClick={() => void runCustomQuery(customPromql, selectedRange)}
              >
                {t('metricsExplorer.customRun')}
              </Button>
            }
          >
            <Input.TextArea
              aria-label={t('metricsExplorer.customTitle')}
              value={customPromql}
              onChange={(event) => setCustomPromql(event.target.value)}
              onPressEnter={(event) => {
                if (!event.shiftKey) {
                  event.preventDefault();
                  void runCustomQuery(customPromql, selectedRange);
                }
              }}
              placeholder={t('metricsExplorer.customPlaceholder')}
              autoSize={{ minRows: 2, maxRows: 6 }}
              style={{ fontFamily: 'monospace' }}
            />
            <div style={{ marginTop: 12 }}>
              {customPanel?.loading ? (
                <Flex justify="center" style={{ minHeight: 200 }} align="center">
                  <Spin />
                </Flex>
              ) : customPanel?.error ? (
                <Alert type="error" showIcon message={customPanel.error} />
              ) : customPanel?.data ? (
                <>
                  {customPanel.data.warnings.map((warning) => (
                    <Alert
                      key={warning}
                      type="warning"
                      showIcon
                      message={warning}
                      style={{ marginBottom: 8 }}
                    />
                  ))}
                  <MetricChart
                    data={customPanel.data}
                    metric={customMetric}
                    locale={locale}
                    noSamples={t('metricsExplorer.noSamples')}
                    histogramLabel={t('metricsExplorer.histogram')}
                    histogramTooltip={t('metricsExplorer.histogramTooltip')}
                    hiddenSeriesText={(count: number) => t('metricsExplorer.hiddenSeries', { count, maxSeries: MAX_SERIES })}
                  />
                </>
              ) : (
                <Text type="secondary">{t('metricsExplorer.customEmpty')}</Text>
              )}
            </div>
          </Card>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(430px, 1fr))',
              gap: 16,
            }}
          >
            {(selectedProfile?.metrics ?? []).map(renderPanel)}
          </div>
        </>
      )}
      <Modal
        title={t('metricsExplorer.authTitle')}
        open={pendingDataSource !== null}
        okText={t('metricsExplorer.connect')}
        cancelText={t('metricsExplorer.cancel')}
        onOk={() => authForm.submit()}
        onCancel={handleAuthCancel}
        afterClose={() => authForm.resetFields()}
      >
        <Text type="secondary">{t('metricsExplorer.authDescription')}</Text>
        <Form<AuthFormValues>
          form={authForm}
          layout="vertical"
          onFinish={handleAuthSubmit}
          style={{ marginTop: 16 }}
        >
          {pendingAuthMode === 'basic' ? (
            <>
              <Form.Item
                name="username"
                label={t('metricsExplorer.username')}
                rules={[{ required: true, whitespace: true, message: t('metricsExplorer.required') }]}
              >
                <Input autoComplete="username" />
              </Form.Item>
              <Form.Item
                name="password"
                label={t('metricsExplorer.password')}
                rules={[{ required: true, whitespace: true, message: t('metricsExplorer.required') }]}
              >
                <Input.Password autoComplete="current-password" />
              </Form.Item>
            </>
          ) : pendingAuthMode === 'bearer' ? (
            <Form.Item
              name="bearerToken"
              label={t('metricsExplorer.token')}
              rules={[{ required: true, whitespace: true, message: t('metricsExplorer.required') }]}
            >
              <Input.Password autoComplete="off" />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
    </section>
  );
};

export default MetricsExplorer;
