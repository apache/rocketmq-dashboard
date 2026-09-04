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
  App,
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Flex,
  Form,
  Input,
  List,
  Modal,
  Segmented,
  Select,
  Skeleton,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ArrowsClockwise, ClockCounterClockwise, DownloadSimple, Eye } from '@phosphor-icons/react';

import { listDataSources } from '../api/settings';
import { listMetricProfiles, queryByDataSource, queryMetrics } from '../api/metrics';
import type { DataSource } from '../api/settings';
import type { MetricData, MetricMapping, MetricProfile } from '../api/metrics';
import { useLang } from '../i18n/LangContext';
import { downloadCsv } from '../utils/download';
import { tableScrollX } from '../utils/table';
import {
  buildMetricCsvFilename,
  buildMetricCsvFromRows,
  buildMetricCsvRows,
  buildMetricSeriesDetailRows,
  clearMetricsQueryHistory,
  createMetricsQueryHistoryEntry,
  loadMetricsQueryHistory,
  mergeMetricsQueryHistory,
  metricSeriesLabel,
  saveMetricsQueryHistory,
  summarizeMetricData,
  toMetricSeriesSamples,
  type MetricCsvContext,
  type MetricSeriesDetailRow,
  type MetricsQueryHistoryEntry,
  type NumericMetricSample,
} from './metricsExplorerDiagnostics';

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
const CUSTOM_HISTORY_PROFILE_ID = '__custom__';
const CUSTOM_HISTORY_METRIC_ID = 'custom';

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
      const { samples, fromHistogram } = toMetricSeriesSamples(series);
      return {
        color: SERIES_COLORS[index % SERIES_COLORS.length],
        label: metricSeriesLabel(series, metric.name),
        samples,
        fromHistogram,
      };
    })
    .filter((series) => series.samples.length > 0);

  if (allSeries.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={noSamples} />;
  }

  const latestValue = (series: { samples: NumericMetricSample[] }) =>
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

interface QueryExecution {
  data: MetricData;
  dataSourceKey: string;
  dataSourceName: string;
  start: number;
  end: number;
  queriedAt: number;
}

interface PanelQueryMeta {
  profileId: string;
  profileName: string;
  metric: MetricMapping;
  range: RangeOption;
  dataSourceKey: string;
  dataSourceName: string;
  start: number;
  end: number;
  queriedAt: number;
  instanceId?: string;
}

interface PanelState {
  loading: boolean;
  data?: MetricData;
  error?: string;
  query?: PanelQueryMeta;
}

interface DetailsPanelState {
  metric: MetricMapping;
  data: MetricData;
  query?: PanelQueryMeta;
}

interface PendingAuthReplay {
  profile: MetricProfile | undefined;
  range: RangeOption;
  customPromql?: string;
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
  const { lang } = useLang();
  const queryErrorFallback = lang === 'zh' ? 'Prometheus 查询失败' : 'Prometheus query failed';
  const copy =
    lang === 'zh'
      ? {
          title: 'Prometheus 指标',
          profile: '指标模板',
          range: '时间范围',
          refresh: '刷新全部面板',
          profileError: '指标模板加载失败',
          noProfiles: '暂无指标模板',
          noSamples: '暂无数据',
          histogram: '直方图',
          histogramTooltip: '无标量样本，趋势由直方图观测值推导',
          hiddenSeries: (count: number) =>
            `另有 ${count} 条序列未显示（按最新值保留前 ${MAX_SERIES} 条）`,
          defaultDataSource: '默认数据源',
          customTitle: '自定义查询',
          customPlaceholder:
            '输入 PromQL，如 sum(rate(rocketmq_messages_in_total[1m])) by (cluster)',
          customRun: '查询',
          customEmpty: '输入 PromQL 后点击查询',
          authTitle: '数据源认证',
          authDescription: '凭据仅用于当前数据源，离开该数据源后会被清除。',
          username: '用户名',
          password: '密码',
          token: '令牌',
          connect: '连接',
          cancel: '取消',
          required: '此项为必填项',
          history: '查询历史',
          historyTitle: '指标查询历史',
          noHistory: '暂无指标查询历史',
          restore: '恢复',
          clearHistory: '清空历史',
          exportCsv: '导出 CSV',
          exportDisabled: '暂无可导出的指标样本',
          details: '序列明细',
          detailsTitle: '指标序列明细',
          unavailableHistory: '历史中的指标模板已不可用',
          series: '序列',
          samples: '样本',
          scalarSamples: '标量样本',
          histogramSamples: '直方图样本',
          warnings: '告警',
          source: '数据源',
          latestSample: '最新样本',
          labels: '标签',
          sampleType: '样本类型',
          value: '值',
          firstSample: '最早样本',
          lastSample: '最新样本',
          queryWindow: '查询窗口',
          queriedAt: '查询时间',
          resultType: '结果类型',
          instance: '实例',
          protectedHistory: '该数据源需要重新认证',
        }
      : {
          title: 'Prometheus Metrics',
          profile: 'Metric profile',
          range: 'Time range',
          refresh: 'Refresh all panels',
          profileError: 'Failed to load metric profiles',
          noProfiles: 'No metric profiles',
          noSamples: 'No samples',
          histogram: 'Histogram',
          histogramTooltip: 'No scalar samples; trend derived from histogram observations',
          hiddenSeries: (count: number) =>
            `${count} more series hidden (showing top ${MAX_SERIES} by latest value)`,
          defaultDataSource: 'Default source',
          customTitle: 'Custom query',
          customPlaceholder:
            'Enter PromQL, e.g. sum(rate(rocketmq_messages_in_total[1m])) by (cluster)',
          customRun: 'Run',
          customEmpty: 'Enter a PromQL expression and run the query',
          authTitle: 'Data source authentication',
          authDescription:
            'Credentials are used only for this source and cleared when you leave it.',
          username: 'Username',
          password: 'Password',
          token: 'Token',
          connect: 'Connect',
          cancel: 'Cancel',
          required: 'This field is required',
          history: 'Query history',
          historyTitle: 'Metric query history',
          noHistory: 'No metric query history',
          restore: 'Restore',
          clearHistory: 'Clear history',
          exportCsv: 'Export CSV',
          exportDisabled: 'No metric samples to export',
          details: 'Series details',
          detailsTitle: 'Metric series details',
          unavailableHistory: 'The metric profile in this history entry is unavailable',
          series: 'Series',
          samples: 'Samples',
          scalarSamples: 'Scalar samples',
          histogramSamples: 'Histogram samples',
          warnings: 'Warnings',
          source: 'Source',
          latestSample: 'Latest sample',
          labels: 'Labels',
          sampleType: 'Sample type',
          value: 'Value',
          firstSample: 'First sample',
          lastSample: 'Last sample',
          queryWindow: 'Query window',
          queriedAt: 'Queried at',
          resultType: 'Result type',
          instance: 'Instance',
          protectedHistory: 'This data source requires authentication again',
        };
  const locale = lang === 'zh' ? 'zh-CN' : 'en-US';
  const { message } = App.useApp();
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
  const [history, setHistory] = useState<MetricsQueryHistoryEntry[]>(() =>
    loadMetricsQueryHistory(),
  );
  const [historyOpen, setHistoryOpen] = useState(false);
  const [detailsPanel, setDetailsPanel] = useState<DetailsPanelState | null>(null);
  const profileRequestId = useRef(0);
  const customRequestId = useRef(0);
  // Keeps the latest data source readable from the stable callbacks so switching
  // the source uses the new key instead of a stale closure value.
  const dataSourceKeyRef = useRef(dataSourceKey);
  const dataSourceCredentialsRef = useRef<DataSourceCredentials | null>(null);
  const dataSourceNamesRef = useRef<Map<string, string>>(new Map());
  const pendingAuthReplayRef = useRef<PendingAuthReplay | null>(null);

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
    dataSourceNamesRef.current = new Map(
      availableDataSources.map((source) => [source.key, source.name]),
    );
  }, [availableDataSources]);

  const runQuery = useCallback(
    async (promql: string, range: RangeOption): Promise<QueryExecution> => {
      const queriedAt = Date.now();
      const end = Math.floor(queriedAt / 1000);
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
      const data = currentDataSourceKey
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
      return {
        data: await data,
        dataSourceKey: currentDataSourceKey,
        dataSourceName:
          dataSourceNamesRef.current.get(currentDataSourceKey) ??
          (currentDataSourceKey || copy.defaultDataSource),
        start: query.start,
        end: query.end,
        queriedAt,
      };
    },
    [copy.defaultDataSource, instanceId],
  );

  const loadAll = useCallback(
    async (profile: MetricProfile | undefined, range: RangeOption) => {
      if (!profile) return;
      const currentRequest = ++profileRequestId.current;
      const loadingPatch = Object.fromEntries(
        profile.metrics.map((metric) => [metric.semanticMetric, { loading: true } as PanelState]),
      );
      setPanels(loadingPatch);
      await Promise.all(
        profile.metrics.map(async (metric) => {
          try {
            const execution = await runQuery(metric.promql, range);
            if (currentRequest === profileRequestId.current) {
              const summary = summarizeMetricData(execution.data);
              const query: PanelQueryMeta = {
                profileId: profile.id,
                profileName: profile.name,
                metric,
                range,
                dataSourceKey: execution.dataSourceKey,
                dataSourceName: execution.dataSourceName,
                start: execution.start,
                end: execution.end,
                queriedAt: execution.queriedAt,
                ...(instanceId !== undefined ? { instanceId } : {}),
              };
              const historyEntry = createMetricsQueryHistoryEntry({
                profileId: profile.id,
                profileName: profile.name,
                metric,
                rangeId: range.value,
                rangeLabel: range.label,
                step: range.step,
                dataSourceKey: execution.dataSourceKey,
                dataSourceName: execution.dataSourceName,
                start: execution.start,
                end: execution.end,
                queriedAt: execution.queriedAt,
                ...(instanceId !== undefined ? { instanceId } : {}),
                summary,
              });
              setPanels((previous) => ({
                ...previous,
                [metric.semanticMetric]: { loading: false, data: execution.data, query },
              }));
              setHistory((currentHistory) => {
                const nextHistory = mergeMetricsQueryHistory(currentHistory, historyEntry);
                saveMetricsQueryHistory(nextHistory);
                return nextHistory;
              });
            }
          } catch (error) {
            if (currentRequest === profileRequestId.current) {
              setPanels((previous) => ({
                ...previous,
                [metric.semanticMetric]: {
                  loading: false,
                  error: getQueryErrorMessage(error, queryErrorFallback),
                },
              }));
            }
          }
        }),
      );
    },
    [instanceId, queryErrorFallback, runQuery],
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
      profileRequestId.current += 1;
      customRequestId.current += 1;
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
      const currentRequest = ++customRequestId.current;
      setCustomPanel({ loading: true });
      setAppliedCustomPromql(trimmed);
      try {
        const execution = await runQuery(trimmed, range);
        if (currentRequest === customRequestId.current) {
          const metric: MetricMapping = {
            semanticMetric: CUSTOM_HISTORY_METRIC_ID,
            name: copy.customTitle,
            unit: '',
            prometheusMetric: '',
            promql: trimmed,
            labels: [],
          };
          const summary = summarizeMetricData(execution.data);
          const query: PanelQueryMeta = {
            profileId: CUSTOM_HISTORY_PROFILE_ID,
            profileName: copy.customTitle,
            metric,
            range,
            dataSourceKey: execution.dataSourceKey,
            dataSourceName: execution.dataSourceName,
            start: execution.start,
            end: execution.end,
            queriedAt: execution.queriedAt,
            ...(instanceId !== undefined ? { instanceId } : {}),
          };
          const historyEntry = createMetricsQueryHistoryEntry({
            profileId: CUSTOM_HISTORY_PROFILE_ID,
            profileName: copy.customTitle,
            metric,
            rangeId: range.value,
            rangeLabel: range.label,
            step: range.step,
            dataSourceKey: execution.dataSourceKey,
            dataSourceName: execution.dataSourceName,
            start: execution.start,
            end: execution.end,
            queriedAt: execution.queriedAt,
            ...(instanceId !== undefined ? { instanceId } : {}),
            summary,
          });
          setCustomPanel({ loading: false, data: execution.data, query });
          setHistory((currentHistory) => {
            const nextHistory = mergeMetricsQueryHistory(currentHistory, historyEntry);
            saveMetricsQueryHistory(nextHistory);
            return nextHistory;
          });
        }
      } catch (error) {
        if (currentRequest === customRequestId.current) {
          setCustomPanel({
            loading: false,
            error: getQueryErrorMessage(error, queryErrorFallback),
          });
        }
      }
    },
    [copy.customTitle, instanceId, queryErrorFallback, runQuery],
  );

  const activateDataSource = (
    nextKey: string,
    credentials?: AuthFormValues,
    profile = selectedProfile,
    range = selectedRange,
    customPromqlToRun = appliedCustomPromql,
  ) => {
    dataSourceCredentialsRef.current = credentials ? { key: nextKey, ...credentials } : null;
    dataSourceKeyRef.current = nextKey;
    setDataSourceKey(nextKey);
    void loadAll(profile, range);
    if (customPromqlToRun) {
      void runCustomQuery(customPromqlToRun, range);
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
    const replay = pendingAuthReplayRef.current;
    activateDataSource(
      pendingDataSource.key,
      credentials,
      replay?.profile,
      replay?.range ?? selectedRange,
      replay?.customPromql ?? appliedCustomPromql,
    );
    pendingAuthReplayRef.current = null;
    setPendingDataSource(null);
    authForm.resetFields();
  };

  const handleAuthCancel = () => {
    pendingAuthReplayRef.current = null;
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
  const visibleHistory = useMemo(
    () =>
      instanceId === undefined
        ? history
        : history.filter(
            (entry) => entry.instanceId === undefined || entry.instanceId === instanceId,
          ),
    [history, instanceId],
  );
  const detailRows = useMemo(
    () => (detailsPanel ? buildMetricSeriesDetailRows(detailsPanel.data, detailsPanel.metric) : []),
    [detailsPanel],
  );

  const formatSeconds = useCallback(
    (timestamp?: number) =>
      timestamp === undefined ? '-' : new Date(timestamp * 1000).toLocaleString(locale),
    [locale],
  );
  const formatMillis = useCallback(
    (timestamp?: number) =>
      timestamp === undefined ? '-' : new Date(timestamp).toLocaleString(locale),
    [locale],
  );
  const formatHistorySource = (entry: MetricsQueryHistoryEntry) =>
    entry.dataSourceKey ? entry.dataSourceName || entry.dataSourceKey : copy.defaultDataSource;

  const getCsvContext = useCallback(
    (query?: PanelQueryMeta): MetricCsvContext => ({
      profileName: query?.profileName ?? '',
      sourceName: query?.dataSourceName || copy.defaultDataSource,
      queryStart: query?.start,
      queryEnd: query?.end,
      queriedAt: query?.queriedAt,
    }),
    [copy.defaultDataSource],
  );

  const detailColumns = useMemo<ColumnsType<MetricSeriesDetailRow>>(
    () => [
      {
        title: copy.series,
        dataIndex: 'seriesLabel',
        key: 'seriesLabel',
        width: 220,
        render: (value: string) => (
          <Text ellipsis={{ tooltip: value }} style={{ maxWidth: 200 }}>
            {value}
          </Text>
        ),
      },
      {
        title: copy.labels,
        dataIndex: 'labels',
        key: 'labels',
        width: 320,
        render: (value: string) => (
          <Text code copyable ellipsis={{ tooltip: value }} style={{ maxWidth: 300 }}>
            {value}
          </Text>
        ),
      },
      {
        title: copy.sampleType,
        dataIndex: 'sampleType',
        key: 'sampleType',
        width: 130,
        render: (value: MetricSeriesDetailRow['sampleType']) => (
          <Tag color={value === 'histogram' ? 'purple' : 'blue'} style={{ marginInlineEnd: 0 }}>
            {value === 'histogram' ? copy.histogram : 'scalar'}
          </Tag>
        ),
      },
      {
        title: copy.samples,
        dataIndex: 'sampleCount',
        key: 'sampleCount',
        width: 100,
        sorter: (left, right) => left.sampleCount - right.sampleCount,
      },
      {
        title: copy.latestSample,
        dataIndex: 'latestTimestamp',
        key: 'latestTimestamp',
        width: 180,
        render: (value?: number) => formatSeconds(value),
      },
      {
        title: copy.value,
        dataIndex: 'latestValue',
        key: 'latestValue',
        width: 120,
        render: (value?: number) => (value === undefined ? '-' : formatMetricValue(value)),
      },
    ],
    [
      copy.histogram,
      copy.labels,
      copy.latestSample,
      copy.sampleType,
      copy.samples,
      copy.series,
      copy.value,
      formatSeconds,
    ],
  );

  const handleExportCsv = (metric: MetricMapping, data: MetricData, query?: PanelQueryMeta) => {
    const rows = buildMetricCsvRows(data, metric, getCsvContext(query));
    if (rows.length === 0) return;
    downloadCsv(buildMetricCsvFilename(metric, query?.queriedAt), buildMetricCsvFromRows(rows));
  };

  const handleOpenDetails = (metric: MetricMapping, data: MetricData, query?: PanelQueryMeta) => {
    setDetailsPanel({ metric, data, query });
  };

  const handleClearHistory = () => {
    clearMetricsQueryHistory();
    setHistory([]);
  };

  const restoreProtectedDataSource = (
    dataSource: DataSource,
    profile: MetricProfile | undefined,
    range: RangeOption,
    customPromqlToRun?: string,
  ) => {
    dataSourceCredentialsRef.current = null;
    dataSourceKeyRef.current = dataSource.key;
    pendingAuthReplayRef.current = { profile, range, customPromql: customPromqlToRun };
    setDataSourceKey(dataSource.key);
    setPendingDataSource(dataSource);
    void message.info(copy.protectedHistory);
  };

  const handleRestoreHistory = (entry: MetricsQueryHistoryEntry) => {
    const nextRange =
      RANGE_OPTIONS.find((range) => range.value === entry.rangeId) ?? RANGE_OPTIONS[0];
    const nextDataSource = entry.dataSourceKey
      ? availableDataSources.find((source) => source.key === entry.dataSourceKey)
      : undefined;
    const nextDataSourceKey = nextDataSource?.key ?? '';
    setRangeId(nextRange.value);
    setHistoryOpen(false);

    if (entry.profileId === CUSTOM_HISTORY_PROFILE_ID) {
      setCustomPromql(entry.promql);
      if (nextDataSource && getDataSourceAuthMode(nextDataSource.auth) !== 'none') {
        restoreProtectedDataSource(nextDataSource, selectedProfile, nextRange, entry.promql);
        return;
      }
      activateDataSource(nextDataSourceKey, undefined, selectedProfile, nextRange, entry.promql);
      return;
    }

    const nextProfile = profiles.find((profile) => profile.id === entry.profileId);
    if (!nextProfile) {
      void message.warning(copy.unavailableHistory);
      return;
    }

    localStorage.setItem(PROFILE_STORAGE_KEY, nextProfile.id);
    setProfileId(nextProfile.id);
    if (nextDataSource && getDataSourceAuthMode(nextDataSource.auth) !== 'none') {
      restoreProtectedDataSource(nextDataSource, nextProfile, nextRange, appliedCustomPromql);
      return;
    }
    activateDataSource(nextDataSourceKey, undefined, nextProfile, nextRange, appliedCustomPromql);
  };

  const renderMetricResult = (metric: MetricMapping, state: PanelState) => {
    if (!state.data) return null;
    const summary = summarizeMetricData(state.data);
    return (
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
        <Flex gap={16} wrap="wrap" style={{ marginBottom: 12 }}>
          <Statistic
            title={copy.series}
            value={`${summary.visibleSeriesCount}/${summary.seriesCount}`}
            style={{ minWidth: 96 }}
          />
          <Statistic title={copy.samples} value={summary.sampleCount} style={{ minWidth: 96 }} />
          <Statistic
            title={copy.scalarSamples}
            value={summary.scalarSampleCount}
            style={{ minWidth: 110 }}
          />
          <Statistic
            title={copy.histogramSamples}
            value={summary.histogramSampleCount}
            style={{ minWidth: 130 }}
          />
          <Statistic title={copy.warnings} value={summary.warningCount} style={{ minWidth: 96 }} />
        </Flex>
        <Descriptions
          size="small"
          column={{ xs: 1, sm: 2, md: 3 }}
          style={{ marginBottom: 12 }}
          items={[
            {
              key: 'source',
              label: copy.source,
              children: state.query?.dataSourceName || copy.defaultDataSource,
            },
            {
              key: 'query-window',
              label: copy.queryWindow,
              children: `${formatSeconds(state.query?.start)} - ${formatSeconds(state.query?.end)}`,
            },
            {
              key: 'queried-at',
              label: copy.queriedAt,
              children: formatMillis(state.query?.queriedAt),
            },
            {
              key: 'first-sample',
              label: copy.firstSample,
              children: formatSeconds(summary.earliestTimestamp),
            },
            {
              key: 'last-sample',
              label: copy.lastSample,
              children: formatSeconds(summary.latestTimestamp),
            },
            {
              key: 'result-type',
              label: copy.resultType,
              children: state.data.resultType,
            },
          ]}
        />
        <MetricChart
          data={state.data}
          metric={metric}
          locale={locale}
          noSamples={copy.noSamples}
          histogramLabel={copy.histogram}
          histogramTooltip={copy.histogramTooltip}
          hiddenSeriesText={copy.hiddenSeries}
        />
      </>
    );
  };

  const renderPanel = (metric: MetricMapping) => {
    const state = panels[metric.semanticMetric];
    const data = state?.data;
    const hasRows =
      data !== undefined &&
      buildMetricSeriesDetailRows(data, metric).some((row) => row.sampleCount > 0);
    const hasCsvRows =
      data !== undefined &&
      buildMetricCsvRows(data, metric, getCsvContext(state?.query)).length > 0;
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
        extra={
          <Space size={4}>
            <Tooltip title={copy.details}>
              <Button
                aria-label={`${metric.name} ${copy.details}`}
                size="small"
                icon={<Eye size={16} />}
                disabled={!data || !hasRows}
                onClick={() => data && handleOpenDetails(metric, data, state?.query)}
              />
            </Tooltip>
            <Tooltip title={hasCsvRows ? copy.exportCsv : copy.exportDisabled}>
              <Button
                aria-label={`${metric.name} ${copy.exportCsv}`}
                size="small"
                icon={<DownloadSimple size={16} />}
                disabled={!data || !hasCsvRows}
                onClick={() => data && handleExportCsv(metric, data, state?.query)}
              />
            </Tooltip>
          </Space>
        }
      >
        {state?.loading ? (
          <Flex justify="center" style={{ minHeight: 200 }} align="center">
            <Spin />
          </Flex>
        ) : state?.error ? (
          <Alert type="error" showIcon message={state.error} />
        ) : state?.data ? (
          renderMetricResult(metric, state)
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={copy.noSamples} />
        )}
      </Card>
    );
  };

  const customMetric: MetricMapping = {
    semanticMetric: CUSTOM_HISTORY_METRIC_ID,
    name: copy.customTitle,
    unit: '',
    prometheusMetric: '',
    promql: appliedCustomPromql,
    labels: [],
  };
  const customData = customPanel?.data;
  const customHasRows =
    customData !== undefined &&
    buildMetricSeriesDetailRows(customData, customMetric).some((row) => row.sampleCount > 0);
  const customHasCsvRows =
    customData !== undefined &&
    buildMetricCsvRows(customData, customMetric, getCsvContext(customPanel?.query)).length > 0;

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
          {copy.title}
        </Title>

        <Flex gap={8} wrap="wrap" align="center" style={{ maxWidth: '100%' }}>
          <Select
            aria-label="数据源"
            value={dataSourceKey}
            loading={dataSourcesLoading}
            onChange={handleDataSourceChange}
            options={[
              { label: copy.defaultDataSource, value: '' },
              ...availableDataSources.map((ds) => ({ label: ds.name, value: ds.key })),
            ]}
            style={{ width: 200, maxWidth: '100%' }}
          />
          <Select
            aria-label={copy.profile}
            value={profileId || undefined}
            loading={profilesLoading}
            onChange={handleProfileChange}
            options={profiles.map((profile) => ({ label: profile.name, value: profile.id }))}
            style={{ width: 210, maxWidth: '100%' }}
          />
          <Segmented
            aria-label={copy.range}
            size="small"
            value={rangeId}
            onChange={(value) => handleRangeChange(value as RangeOption['value'])}
            options={RANGE_OPTIONS.map(({ label, value }) => ({ label, value }))}
          />
          <Tooltip title={copy.history}>
            <Button
              aria-label={copy.history}
              icon={<ClockCounterClockwise size={16} />}
              onClick={() => setHistoryOpen(true)}
            />
          </Tooltip>
          <Tooltip title={copy.refresh}>
            <Button
              aria-label={copy.refresh}
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
        <Alert type="error" showIcon message={copy.profileError} />
      ) : profiles.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={copy.noProfiles} />
      ) : (
        <>
          <Card
            size="small"
            title={copy.customTitle}
            style={{ marginBottom: 16 }}
            extra={
              <Space size={4}>
                <Tooltip title={copy.details}>
                  <Button
                    aria-label={`${copy.customTitle} ${copy.details}`}
                    size="small"
                    icon={<Eye size={16} />}
                    disabled={!customData || !customHasRows}
                    onClick={() =>
                      customData && handleOpenDetails(customMetric, customData, customPanel?.query)
                    }
                  />
                </Tooltip>
                <Tooltip title={customHasCsvRows ? copy.exportCsv : copy.exportDisabled}>
                  <Button
                    aria-label={`${copy.customTitle} ${copy.exportCsv}`}
                    size="small"
                    icon={<DownloadSimple size={16} />}
                    disabled={!customData || !customHasCsvRows}
                    onClick={() =>
                      customData && handleExportCsv(customMetric, customData, customPanel?.query)
                    }
                  />
                </Tooltip>
                <Button
                  aria-label={copy.customRun}
                  type="primary"
                  size="small"
                  loading={Boolean(customPanel?.loading)}
                  disabled={!customPromql.trim()}
                  onClick={() => void runCustomQuery(customPromql, selectedRange)}
                >
                  {copy.customRun}
                </Button>
              </Space>
            }
          >
            <Input.TextArea
              aria-label={copy.customTitle}
              value={customPromql}
              onChange={(event) => setCustomPromql(event.target.value)}
              onPressEnter={(event) => {
                if (!event.shiftKey) {
                  event.preventDefault();
                  void runCustomQuery(customPromql, selectedRange);
                }
              }}
              placeholder={copy.customPlaceholder}
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
                renderMetricResult(customMetric, customPanel)
              ) : (
                <Text type="secondary">{copy.customEmpty}</Text>
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
      <Drawer
        title={copy.historyTitle}
        width={820}
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        destroyOnHidden
        extra={
          <Button size="small" disabled={visibleHistory.length === 0} onClick={handleClearHistory}>
            {copy.clearHistory}
          </Button>
        }
      >
        {visibleHistory.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={copy.noHistory} />
        ) : (
          <List
            dataSource={visibleHistory}
            renderItem={(entry) => (
              <List.Item
                actions={[
                  <Button
                    key="restore"
                    size="small"
                    type="link"
                    onClick={() => handleRestoreHistory(entry)}
                  >
                    {copy.restore}
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={
                    <Flex gap={8} wrap="wrap" align="center">
                      <Text strong>{entry.metricName}</Text>
                      <Tag>{entry.rangeLabel}</Tag>
                      <Tag>{formatHistorySource(entry)}</Tag>
                      {entry.instanceId ? (
                        <Tag>{`${copy.instance}: ${entry.instanceId}`}</Tag>
                      ) : null}
                      <Tag>{`${entry.summary.sampleCount} ${copy.samples}`}</Tag>
                    </Flex>
                  }
                  description={
                    <Space direction="vertical" size={4} style={{ width: '100%' }}>
                      <Text
                        code
                        copyable
                        ellipsis={{ tooltip: entry.promql }}
                        style={{ maxWidth: '100%' }}
                      >
                        {entry.promql}
                      </Text>
                      <Text type="secondary">
                        {`${entry.profileName} · ${formatSeconds(entry.start)} - ${formatSeconds(
                          entry.end,
                        )} · ${formatMillis(entry.queriedAt)}`}
                      </Text>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Drawer>
      <Drawer
        title={
          <Flex gap={8} wrap="wrap" align="center">
            <span>{copy.detailsTitle}</span>
            {detailsPanel?.metric.name ? <Tag>{detailsPanel.metric.name}</Tag> : null}
          </Flex>
        }
        width={1080}
        open={detailsPanel !== null}
        onClose={() => setDetailsPanel(null)}
        destroyOnHidden
      >
        {detailsPanel?.query ? (
          <Descriptions
            size="small"
            column={{ xs: 1, sm: 2, md: 3 }}
            style={{ marginBottom: 12 }}
            items={[
              {
                key: 'source',
                label: copy.source,
                children: detailsPanel.query.dataSourceName || copy.defaultDataSource,
              },
              {
                key: 'query-window',
                label: copy.queryWindow,
                children: `${formatSeconds(detailsPanel.query.start)} - ${formatSeconds(
                  detailsPanel.query.end,
                )}`,
              },
              {
                key: 'queried-at',
                label: copy.queriedAt,
                children: formatMillis(detailsPanel.query.queriedAt),
              },
            ]}
          />
        ) : null}
        <Table<MetricSeriesDetailRow>
          rowKey="key"
          size="small"
          dataSource={detailRows}
          columns={detailColumns}
          pagination={{ pageSize: 8, showSizeChanger: false }}
          scroll={{ x: tableScrollX(detailColumns) }}
        />
      </Drawer>
      <Modal
        title={copy.authTitle}
        open={pendingDataSource !== null}
        okText={copy.connect}
        cancelText={copy.cancel}
        onOk={() => authForm.submit()}
        onCancel={handleAuthCancel}
        afterClose={() => authForm.resetFields()}
      >
        <Text type="secondary">{copy.authDescription}</Text>
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
                label={copy.username}
                rules={[{ required: true, whitespace: true, message: copy.required }]}
              >
                <Input autoComplete="username" />
              </Form.Item>
              <Form.Item
                name="password"
                label={copy.password}
                rules={[{ required: true, whitespace: true, message: copy.required }]}
              >
                <Input.Password autoComplete="current-password" />
              </Form.Item>
            </>
          ) : pendingAuthMode === 'bearer' ? (
            <Form.Item
              name="bearerToken"
              label={copy.token}
              rules={[{ required: true, whitespace: true, message: copy.required }]}
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
