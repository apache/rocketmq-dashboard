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

import { useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Drawer,
  Empty,
  Flex,
  Input,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { BarChartOutlined, DownloadOutlined } from '@ant-design/icons';
import { listAlertDeliveriesPage } from '../services/opsService';
import { useLang } from '../i18n/LangContext';
import { buildCsv, downloadCsv, type CsvColumn } from '../utils/download';
import { formatUtcDateTime } from '../utils/format';
import { tableScrollX } from '../utils/table';
import {
  analyzeNotificationDeliveries,
  filterDeliveryAnalyticsRows,
  type DeliveryAnalyticsRow,
  type DeliveryChannelAnalytics,
  type DeliveryErrorAnalytics,
  type DeliveryHealth,
  type NotificationDeliveryAnalytics,
} from '../utils/notificationDeliveryAnalytics';

interface NotificationDeliveryAnalyticsDrawerProps {
  open: boolean;
  onClose: () => void;
}

const PAGE_SIZE = 100;
const MAX_RECORDS = 10_000;

const CSV_COLUMNS: CsvColumn<DeliveryAnalyticsRow>[] = [
  { header: 'Delivery ID', value: (row) => row.id },
  { header: 'Alert ID', value: (row) => row.alertId },
  { header: 'Alert', value: (row) => row.alertTitle },
  { header: 'Instance', value: (row) => row.instanceId },
  { header: 'Channel', value: (row) => row.channel },
  { header: 'Status', value: (row) => row.status },
  { header: 'Health', value: (row) => row.health },
  { header: 'Attempts', value: (row) => row.attemptCount },
  { header: 'Created At (UTC)', value: (row) => row.createdAt },
  { header: 'Delivered At (UTC)', value: (row) => row.deliveredAt },
  { header: 'Latency (ms)', value: (row) => row.latencyMs },
  { header: 'Error Signature', value: (row) => row.errorSignature },
];

const duration = (milliseconds: number | null): string => {
  if (milliseconds === null) return '-';
  if (milliseconds < 1000) return `${milliseconds} ms`;
  if (milliseconds < 60_000) return `${Math.round(milliseconds / 100) / 10} s`;
  return `${Math.round(milliseconds / 6000) / 10} min`;
};

const loadCompleteInventory = async () => {
  const first = await listAlertDeliveriesPage({ page: 1, pageSize: PAGE_SIZE });
  const records = [...first.items];
  const target = Math.min(first.total, MAX_RECORDS);
  for (let page = 2; records.length < target; page += 1) {
    const result = await listAlertDeliveriesPage({ page, pageSize: PAGE_SIZE });
    records.push(...result.items);
    if (result.items.length === 0) break;
  }
  return { records: records.slice(0, MAX_RECORDS), total: first.total };
};

const healthColor: Record<DeliveryHealth, string> = {
  HEALTHY: 'success',
  IN_FLIGHT: 'processing',
  DEGRADED: 'warning',
  CRITICAL: 'error',
};

const NotificationDeliveryAnalyticsDrawer = ({
  open,
  onClose,
}: NotificationDeliveryAnalyticsDrawerProps) => {
  const { t } = useLang();
  const [analytics, setAnalytics] = useState<NotificationDeliveryAnalytics | null>(null);
  const [sourceTotal, setSourceTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [search, setSearch] = useState('');
  const [channel, setChannel] = useState<string | 'ALL'>('ALL');
  const [health, setHealth] = useState<DeliveryHealth | 'ALL'>('ALL');
  const [retriedOnly, setRetriedOnly] = useState(false);
  const requestIdRef = useRef(0);

  const visibleRows = useMemo(
    () =>
      filterDeliveryAnalyticsRows(analytics?.rows ?? [], {
        search,
        channel,
        health,
        retriedOnly,
      }),
    [analytics, channel, health, retriedOnly, search],
  );

  const loadAnalytics = async () => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setLoadError(false);
    try {
      const result = await loadCompleteInventory();
      if (requestId !== requestIdRef.current) return;
      setAnalytics(analyzeNotificationDeliveries(result.records, Date.now()));
      setSourceTotal(result.total);
      setSearch('');
      setChannel('ALL');
      setHealth('ALL');
      setRetriedOnly(false);
    } catch {
      if (requestId === requestIdRef.current) setLoadError(true);
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  };

  const exportRows = () => {
    downloadCsv('rocketmq-notification-delivery-analysis.csv', buildCsv(CSV_COLUMNS, visibleRows));
    message.success(t('deliveryAnalytics.exported', { count: visibleRows.length }));
  };

  const channelColumns: TableColumnsType<DeliveryChannelAnalytics> = [
    { title: t('deliveryAnalytics.channel'), dataIndex: 'channel', width: 130 },
    { title: t('deliveryAnalytics.total'), dataIndex: 'total', width: 100 },
    { title: t('deliveryAnalytics.delivered'), dataIndex: 'delivered', width: 110 },
    { title: t('deliveryAnalytics.failed'), dataIndex: 'failed', width: 100 },
    { title: t('deliveryAnalytics.inFlight'), dataIndex: 'inFlight', width: 110 },
    { title: t('deliveryAnalytics.retried'), dataIndex: 'retried', width: 100 },
    {
      title: t('deliveryAnalytics.successRate'),
      dataIndex: 'successRate',
      width: 130,
      sorter: (left, right) => left.successRate - right.successRate,
      render: (value: number) => `${value}%`,
    },
    {
      title: t('deliveryAnalytics.averageLatency'),
      dataIndex: 'averageLatencyMs',
      width: 150,
      render: duration,
    },
    {
      title: t('deliveryAnalytics.p95Latency'),
      dataIndex: 'p95LatencyMs',
      width: 140,
      render: duration,
    },
  ];

  const errorColumns: TableColumnsType<DeliveryErrorAnalytics> = [
    { title: t('deliveryAnalytics.channel'), dataIndex: 'channel', width: 120 },
    {
      title: t('deliveryAnalytics.errorSignature'),
      dataIndex: 'signature',
      ellipsis: true,
      render: (value: string) => <span title={value}>{value}</span>,
    },
    { title: t('deliveryAnalytics.occurrences'), dataIndex: 'count', width: 110 },
    { title: t('deliveryAnalytics.affectedAlerts'), dataIndex: 'affectedAlerts', width: 130 },
    {
      title: t('deliveryAnalytics.latestAt'),
      dataIndex: 'latestAt',
      width: 180,
      render: (value: string) => formatUtcDateTime(value),
    },
  ];

  const rowColumns: TableColumnsType<DeliveryAnalyticsRow> = [
    {
      title: t('deliveryAnalytics.alert'),
      dataIndex: 'alertTitle',
      width: 260,
      fixed: 'left',
      ellipsis: true,
      render: (value: string, row) => (
        <span title={value}>
          {value} · #{row.alertId}
        </span>
      ),
    },
    { title: t('deliveryAnalytics.instance'), dataIndex: 'instanceId', width: 150 },
    { title: t('deliveryAnalytics.channel'), dataIndex: 'channel', width: 110 },
    { title: t('deliveryAnalytics.status'), dataIndex: 'status', width: 120 },
    {
      title: t('deliveryAnalytics.health'),
      dataIndex: 'health',
      width: 120,
      render: (value: DeliveryHealth) => (
        <Tag color={healthColor[value]}>{t(`deliveryAnalytics.health.${value}`)}</Tag>
      ),
    },
    { title: t('deliveryAnalytics.attempts'), dataIndex: 'attemptCount', width: 100 },
    {
      title: t('deliveryAnalytics.latency'),
      dataIndex: 'latencyMs',
      width: 120,
      render: duration,
    },
    {
      title: t('deliveryAnalytics.createdAt'),
      dataIndex: 'createdAt',
      width: 180,
      render: (value: string) => formatUtcDateTime(value),
    },
    {
      title: t('deliveryAnalytics.errorSignature'),
      dataIndex: 'errorSignature',
      width: 260,
      ellipsis: true,
      render: (value: string) => <span title={value}>{value || '-'}</span>,
    },
  ];

  return (
    <Drawer
      title={t('deliveryAnalytics.title')}
      open={open}
      width={1200}
      destroyOnHidden
      onClose={() => {
        requestIdRef.current += 1;
        onClose();
      }}
    >
      <Flex vertical gap={16}>
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          {t('deliveryAnalytics.description')}
        </Typography.Paragraph>
        <Flex justify="space-between" gap={12} wrap="wrap">
          <Alert
            type="info"
            showIcon
            message={t('deliveryAnalytics.readOnly')}
            style={{ flex: 1 }}
          />
          <Button
            type="primary"
            icon={<BarChartOutlined />}
            aria-label={t('deliveryAnalytics.load')}
            loading={loading}
            onClick={() => void loadAnalytics()}
          >
            {t('deliveryAnalytics.load')}
          </Button>
        </Flex>
        {loadError && <Alert type="error" showIcon message={t('deliveryAnalytics.loadFailed')} />}
        {!analytics && !loading && !loadError && (
          <Empty description={t('deliveryAnalytics.empty')} />
        )}
        {analytics && (
          <>
            {sourceTotal > analytics.rows.length && (
              <Alert
                type="warning"
                showIcon
                message={t('deliveryAnalytics.truncated', {
                  loaded: analytics.rows.length,
                  total: sourceTotal,
                })}
              />
            )}
            <Flex gap={12} wrap="wrap">
              <Card size="small" style={{ flex: 1, minWidth: 125 }}>
                <Statistic title={t('deliveryAnalytics.total')} value={analytics.summary.total} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 125 }}>
                <Statistic
                  title={t('deliveryAnalytics.successRate')}
                  value={analytics.summary.successRate}
                  suffix="%"
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 125 }}>
                <Statistic title={t('deliveryAnalytics.failed')} value={analytics.summary.failed} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 125 }}>
                <Statistic title={t('deliveryAnalytics.stuck')} value={analytics.summary.stuck} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 125 }}>
                <Statistic
                  title={t('deliveryAnalytics.retried')}
                  value={analytics.summary.retried}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 125 }}>
                <Statistic
                  title={t('deliveryAnalytics.p95Latency')}
                  value={duration(analytics.summary.p95LatencyMs)}
                />
              </Card>
            </Flex>
            <Flex justify="space-between" gap={8} wrap="wrap">
              <Space wrap>
                <Input.Search
                  aria-label={t('deliveryAnalytics.search')}
                  placeholder={t('deliveryAnalytics.search')}
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  style={{ width: 250 }}
                  allowClear
                />
                <Select
                  aria-label={t('deliveryAnalytics.channelFilter')}
                  value={channel}
                  onChange={setChannel}
                  style={{ width: 150 }}
                  options={[
                    { value: 'ALL', label: t('deliveryAnalytics.allChannels') },
                    ...analytics.channels.map((item) => ({
                      value: item.channel,
                      label: item.channel,
                    })),
                  ]}
                />
                <Select
                  aria-label={t('deliveryAnalytics.healthFilter')}
                  value={health}
                  onChange={setHealth}
                  style={{ width: 160 }}
                  options={[
                    { value: 'ALL', label: t('deliveryAnalytics.allHealth') },
                    ...(['HEALTHY', 'IN_FLIGHT', 'DEGRADED', 'CRITICAL'] as DeliveryHealth[]).map(
                      (value) => ({ value, label: t(`deliveryAnalytics.health.${value}`) }),
                    ),
                  ]}
                />
                <Checkbox
                  checked={retriedOnly}
                  onChange={(event) => setRetriedOnly(event.target.checked)}
                >
                  {t('deliveryAnalytics.retriedOnly')}
                </Checkbox>
              </Space>
              <Button
                icon={<DownloadOutlined />}
                aria-label={t('deliveryAnalytics.export')}
                disabled={visibleRows.length === 0}
                onClick={exportRows}
              >
                {t('deliveryAnalytics.export')}
              </Button>
            </Flex>
            <Typography.Text type="secondary">
              {t('deliveryAnalytics.visible', { count: visibleRows.length })}
            </Typography.Text>
            <Tabs
              items={[
                {
                  key: 'channels',
                  label: t('deliveryAnalytics.channelTab'),
                  children: (
                    <Table
                      rowKey="key"
                      size="small"
                      columns={channelColumns}
                      dataSource={analytics.channels}
                      pagination={false}
                      scroll={{ x: tableScrollX(channelColumns) }}
                    />
                  ),
                },
                {
                  key: 'errors',
                  label: t('deliveryAnalytics.errorTab', { count: analytics.errors.length }),
                  children: (
                    <Table
                      rowKey="key"
                      size="small"
                      columns={errorColumns}
                      dataSource={analytics.errors}
                      pagination={{ pageSize: 10 }}
                      scroll={{ x: tableScrollX(errorColumns) }}
                    />
                  ),
                },
                {
                  key: 'deliveries',
                  label: t('deliveryAnalytics.deliveryTab', { count: visibleRows.length }),
                  children: (
                    <Table
                      rowKey="id"
                      size="small"
                      columns={rowColumns}
                      dataSource={visibleRows}
                      pagination={{ pageSize: 20, showSizeChanger: true }}
                      scroll={{ x: tableScrollX(rowColumns) }}
                    />
                  ),
                },
              ]}
            />
          </>
        )}
      </Flex>
    </Drawer>
  );
};

export default NotificationDeliveryAnalyticsDrawer;
