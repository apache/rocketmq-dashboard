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
  Empty,
  Flex,
  Form,
  Input,
  Modal,
  Segmented,
  Select,
  Skeleton,
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
const CHART_PADDING = { top: 18, right: 18, bottom: 32, left: 64 };
const SERIES_COLORS = ['#1677ff', '#52c41a', '#fa8c16', '#722ed1', '#13c2c2', '#eb2f96'];

const RANGE_OPTIONS = [
  { label: '1h', value: '1h', seconds: 60 * 60, step: '30s' },
  { label: '6h', value: '6h', seconds: 6 * 60 * 60, step: '2m' },
  { label: '24h', value: '24h', seconds: 24 * 60 * 60, step: '5m' },
] as const;

interface NumericSample {
  timestamp: number;
  value: number;
}

const toNumericSamples = (series: MetricSeries): NumericSample[] =>
  series.values
    .map((sample) => ({ timestamp: sample.timestamp, value: Number(sample.value) }))
    .filter((sample) => Number.isFinite(sample.timestamp) && Number.isFinite(sample.value))
    .sort((left, right) => left.timestamp - right.timestamp);

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
}

const MetricChart = ({ data, metric, locale, noSamples }: MetricChartProps) => {
  const chartSeries = data.series
    .map((series, index) => ({
      color: SERIES_COLORS[index % SERIES_COLORS.length],
      label: seriesLabel(series, metric.name),
      samples: toNumericSamples(series),
    }))
    .filter((series) => series.samples.length > 0);

  if (chartSeries.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={noSamples} />;
  }

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
                y={gridY + 4}
                textAnchor="end"
                fontSize="11"
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
            strokeWidth="2"
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
          fontSize="11"
          fill="#8c8c8c"
        >
          {formatTime(minTime)}
        </text>
        <text
          x={CHART_WIDTH - CHART_PADDING.right}
          y={CHART_HEIGHT - 8}
          textAnchor="end"
          fontSize="11"
          fill="#8c8c8c"
        >
          {formatTime(maxTime)}
        </text>
      </svg>

      <Flex gap={16} wrap="wrap" style={{ marginTop: 8 }}>
        {chartSeries.map((series) => {
          const latest = series.samples[series.samples.length - 1];
          return (
            <Flex
              key={series.label}
              align="center"
              gap={6}
              style={{ flex: '1 1 220px', minWidth: 0, maxWidth: '100%' }}
            >
              <span
                style={{ width: 18, height: 3, background: series.color, display: 'inline-block' }}
              />
              <Text ellipsis={{ tooltip: series.label }} style={{ maxWidth: 220 }}>
                {series.label}
              </Text>
              <Text strong>
                {formatMetricValue(latest.value)} {metric.unit}
              </Text>
            </Flex>
          );
        })}
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

const getDataSourceAuthMode = (auth: string): DataSourceAuthMode => {
  const normalized = auth.trim().toLowerCase();
  if (normalized === 'basic' || normalized === 'basic auth') return 'basic';
  if (normalized === 'bearer' || normalized === 'bearer token') return 'bearer';
  return 'none';
};

const MetricsExplorer = ({ instanceId }: MetricsExplorerProps) => {
  const { lang } = useLang();
  const copy =
    lang === 'zh'
      ? {
          title: 'Prometheus 指标',
          profile: '指标模板',
          metric: '指标',
          range: '时间范围',
          refresh: '刷新指标',
          profileError: '指标模板加载失败',
          queryError: 'Prometheus 查询失败',
          noProfiles: '暂无指标模板',
          noSamples: '暂无标量数据',
          defaultDataSource: '默认数据源',
          authTitle: '数据源认证',
          authDescription: '凭据仅用于当前数据源，离开该数据源后会被清除。',
          username: '用户名',
          password: '密码',
          token: '令牌',
          connect: '连接',
          cancel: '取消',
          required: '此项为必填项',
        }
      : {
          title: 'Prometheus Metrics',
          profile: 'Metric profile',
          metric: 'Metric',
          range: 'Time range',
          refresh: 'Refresh metrics',
          profileError: 'Failed to load metric profiles',
          queryError: 'Prometheus query failed',
          noProfiles: 'No metric profiles',
          noSamples: 'No scalar samples',
          defaultDataSource: 'Default source',
          authTitle: 'Data source authentication',
          authDescription:
            'Credentials are used only for this source and cleared when you leave it.',
          username: 'Username',
          password: 'Password',
          token: 'Token',
          connect: 'Connect',
          cancel: 'Cancel',
          required: 'This field is required',
        };
  const [authForm] = Form.useForm<AuthFormValues>();
  const [profiles, setProfiles] = useState<MetricProfile[]>([]);
  const [profileId, setProfileId] = useState('');
  const [metricId, setMetricId] = useState('');
  const [rangeId, setRangeId] = useState<(typeof RANGE_OPTIONS)[number]['value']>('1h');
  const [data, setData] = useState<MetricData | null>(null);
  const [profilesLoading, setProfilesLoading] = useState(true);
  const [queryLoading, setQueryLoading] = useState(false);
  const [profileError, setProfileError] = useState(false);
  const [queryError, setQueryError] = useState(false);
  const [dataSources, setDataSources] = useState<DataSource[]>([]);
  const [dataSourceKey, setDataSourceKey] = useState('');
  const [dataSourcesLoading, setDataSourcesLoading] = useState(true);
  const [pendingDataSource, setPendingDataSource] = useState<DataSource | null>(null);
  const requestId = useRef(0);
  // Keeps the latest data source readable from the stable loadMetrics callback so switching
  // the source uses the new key instead of a stale closure value.
  const dataSourceKeyRef = useRef(dataSourceKey);
  const dataSourceCredentialsRef = useRef<DataSourceCredentials | null>(null);

  const selectedProfile = useMemo(
    () => profiles.find((profile) => profile.id === profileId),
    [profileId, profiles],
  );
  const selectedMetric = useMemo(
    () => selectedProfile?.metrics.find((metric) => metric.semanticMetric === metricId),
    [metricId, selectedProfile],
  );
  const selectedRange = RANGE_OPTIONS.find((range) => range.value === rangeId) ?? RANGE_OPTIONS[0];
  const availableDataSources = useMemo(
    () =>
      dataSources.filter(
        (source) =>
          !source.instanceIds?.length ||
          (instanceId !== undefined && source.instanceIds.includes(instanceId)),
      ),
    [dataSources, instanceId],
  );

  const loadMetrics = useCallback(
    async (metric: MetricMapping | undefined, range: (typeof RANGE_OPTIONS)[number]) => {
      if (!metric) return;
      const currentRequest = ++requestId.current;
      const end = Math.floor(Date.now() / 1000);
      const query = {
        metric: metric.promql,
        start: end - range.seconds,
        end,
        step: range.step,
      };
      setQueryLoading(true);
      setQueryError(false);
      try {
        const currentDataSourceKey = dataSourceKeyRef.current;
        const credentials =
          dataSourceCredentialsRef.current?.key === currentDataSourceKey
            ? dataSourceCredentialsRef.current
            : null;
        const result = currentDataSourceKey
          ? await queryByDataSource({
              key: currentDataSourceKey,
              query,
              instanceId,
              ...(credentials?.username !== undefined ? { username: credentials.username } : {}),
              ...(credentials?.password !== undefined ? { password: credentials.password } : {}),
              ...(credentials?.bearerToken !== undefined
                ? { bearerToken: credentials.bearerToken }
                : {}),
            })
          : await queryMetrics(query);
        if (currentRequest === requestId.current) setData(result);
      } catch {
        if (currentRequest === requestId.current) {
          setData(null);
          setQueryError(true);
        }
      } finally {
        if (currentRequest === requestId.current) setQueryLoading(false);
      }
    },
    [instanceId],
  );

  useEffect(() => {
    let cancelled = false;
    void listMetricProfiles()
      .then((nextProfiles) => {
        if (cancelled) return;
        setProfiles(nextProfiles);
        const initialProfile = nextProfiles[0];
        const initialMetric = initialProfile?.metrics[0];
        setProfileId(initialProfile?.id ?? '');
        setMetricId(initialMetric?.semanticMetric ?? '');
        void loadMetrics(initialMetric, RANGE_OPTIONS[0]);
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
  }, [loadMetrics]);

  const handleProfileChange = (nextProfileId: string) => {
    const nextProfile = profiles.find((profile) => profile.id === nextProfileId);
    const nextMetric = nextProfile?.metrics[0];
    setProfileId(nextProfileId);
    setMetricId(nextMetric?.semanticMetric ?? '');
    setData(null);
    void loadMetrics(nextMetric, selectedRange);
  };

  const handleMetricChange = (nextMetricId: string) => {
    const nextMetric = selectedProfile?.metrics.find(
      (metric) => metric.semanticMetric === nextMetricId,
    );
    setMetricId(nextMetricId);
    setData(null);
    void loadMetrics(nextMetric, selectedRange);
  };

  const handleRangeChange = (nextRangeId: (typeof RANGE_OPTIONS)[number]['value']) => {
    const nextRange =
      RANGE_OPTIONS.find((range) => range.value === nextRangeId) ?? RANGE_OPTIONS[0];
    setRangeId(nextRangeId);
    void loadMetrics(selectedMetric, nextRange);
  };

  const activateDataSource = (nextKey: string, credentials?: AuthFormValues) => {
    dataSourceCredentialsRef.current = credentials ? { key: nextKey, ...credentials } : null;
    dataSourceKeyRef.current = nextKey;
    setDataSourceKey(nextKey);
    setData(null);
    void loadMetrics(selectedMetric, selectedRange);
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
        setDataSourceKey('');
        setData(null);
      }, 0);
    }
  }, [availableDataSources, dataSourceKey]);

  const pendingAuthMode = pendingDataSource
    ? getDataSourceAuthMode(pendingDataSource.auth)
    : 'none';

  return (
    <section aria-labelledby="metrics-explorer-title" style={{ marginTop: 24 }}>
      <Flex
        justify="space-between"
        align="center"
        gap={16}
        wrap="wrap"
        style={{ marginBottom: 12 }}
      >
        <Flex gap={8} wrap="wrap" align="center">
          <Title id="metrics-explorer-title" level={4} style={{ margin: 0, fontSize: 16 }}>
            {copy.title}
          </Title>
          {selectedMetric && <Tag>{selectedMetric.unit}</Tag>}
        </Flex>

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
          <Select
            aria-label={copy.metric}
            value={metricId || undefined}
            onChange={handleMetricChange}
            options={(selectedProfile?.metrics ?? []).map((metric) => ({
              label: metric.name,
              value: metric.semanticMetric,
            }))}
            style={{ width: 190, maxWidth: '100%' }}
          />
          <Segmented
            aria-label={copy.range}
            size="small"
            value={rangeId}
            onChange={(value) =>
              handleRangeChange(value as (typeof RANGE_OPTIONS)[number]['value'])
            }
            options={RANGE_OPTIONS.map(({ label, value }) => ({ label, value }))}
          />
          <Tooltip title={copy.refresh}>
            <Button
              aria-label={copy.refresh}
              icon={<ArrowsClockwise size={16} />}
              onClick={() => void loadMetrics(selectedMetric, selectedRange)}
              loading={queryLoading}
            />
          </Tooltip>
        </Flex>
      </Flex>

      {selectedMetric && (
        <Text
          code
          copyable
          style={{ display: 'block', marginBottom: 12, overflowWrap: 'anywhere' }}
        >
          {selectedMetric.promql}
        </Text>
      )}

      {profilesLoading ? (
        <Skeleton active paragraph={{ rows: 5 }} />
      ) : profileError ? (
        <Alert type="error" showIcon message={copy.profileError} />
      ) : profiles.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={copy.noProfiles} />
      ) : queryError ? (
        <Alert type="error" showIcon message={copy.queryError} />
      ) : queryLoading && !data ? (
        <Skeleton active paragraph={{ rows: 5 }} />
      ) : data && selectedMetric ? (
        <>
          {data.warnings.map((warning) => (
            <Alert
              key={warning}
              type="warning"
              showIcon
              message={warning}
              style={{ marginBottom: 8 }}
            />
          ))}
          <MetricChart
            data={data}
            metric={selectedMetric}
            locale={lang === 'zh' ? 'zh-CN' : 'en-US'}
            noSamples={copy.noSamples}
          />
        </>
      ) : null}
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
