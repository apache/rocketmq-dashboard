/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Drawer,
  Flex,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { listAllDataSources, testDataSource } from '../api/settings';
import { useLang } from '../i18n/LangContext';
import { buildCsv, downloadCsv } from '../utils/download';
import {
  checkDataSourceConnectivity,
  filterDataSourceConnectivity,
  type DataSourceCheckStatus,
  type DataSourceConnectivityBatch,
  type DataSourceConnectivityResult,
} from '../utils/dataSourceConnectivityBatch';

interface Props {
  open: boolean;
  search?: string;
  type?: string;
  onClose: () => void;
}
const statusColor: Record<DataSourceCheckStatus, string> = {
  SUCCESS: 'green',
  FAILED: 'red',
  SKIPPED_AUTH: 'orange',
};

export const DataSourceConnectivityDrawer = ({
  open,
  search: sourceSearch,
  type,
  onClose,
}: Props) => {
  const { t } = useLang();
  const [batch, setBatch] = useState<DataSourceConnectivityBatch>();
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<DataSourceCheckStatus>();
  const filtered = useMemo(
    () => filterDataSourceConnectivity(batch?.results ?? [], search, status),
    [batch?.results, search, status],
  );
  const run = async () => {
    setLoading(true);
    try {
      const sources = await listAllDataSources({ search: sourceSearch?.trim() || undefined, type });
      setBatch(await checkDataSourceConnectivity(sources, testDataSource, 3));
    } catch {
      message.error(t('settings.connectivityBatchFailed'));
    } finally {
      setLoading(false);
    }
  };
  const label = (value: DataSourceCheckStatus) =>
    t(
      value === 'SUCCESS'
        ? 'settings.connectivitySuccess'
        : value === 'FAILED'
          ? 'settings.connectivityFailed'
          : 'settings.connectivitySkipped',
    );
  const columns: ColumnsType<DataSourceConnectivityResult> = [
    { title: t('common.name'), dataIndex: 'name', key: 'name', fixed: 'left', width: 190 },
    { title: t('common.type'), dataIndex: 'type', key: 'type', width: 130 },
    { title: 'URL', dataIndex: 'url', key: 'url', width: 260, ellipsis: true },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (value: DataSourceCheckStatus) => (
        <Tag color={statusColor[value]}>{label(value)}</Tag>
      ),
    },
    {
      title: t('settings.connectivityLatency'),
      dataIndex: 'latencyMs',
      key: 'latencyMs',
      width: 110,
      render: (value: number | null) => (value === null ? '-' : `${value} ms`),
      sorter: (a, b) => (a.latencyMs ?? Infinity) - (b.latencyMs ?? Infinity),
    },
    {
      title: t('settings.connectivityInstances'),
      dataIndex: 'instanceCount',
      key: 'instanceCount',
      width: 100,
    },
    { title: t('settings.connectivityMessage'), dataIndex: 'message', key: 'message', width: 260 },
  ];
  const exportRows = () =>
    downloadCsv(
      `rocketmq-datasource-connectivity-${new Date().toISOString().slice(0, 10)}.csv`,
      buildCsv(
        [
          { header: 'Key', value: (row) => row.key },
          { header: 'Name', value: (row) => row.name },
          { header: 'Type', value: (row) => row.type },
          { header: 'URL', value: (row) => row.url },
          { header: 'Authentication', value: (row) => row.auth },
          { header: 'Status', value: (row) => row.status },
          { header: 'Latency ms', value: (row) => row.latencyMs },
          { header: 'Instances', value: (row) => row.instanceCount },
          { header: 'Message', value: (row) => row.message },
        ],
        filtered,
      ),
    );
  const summary = batch?.summary;
  const cards = [
    ['settings.connectivityTotal', summary?.total ?? 0],
    ['settings.connectivitySuccess', summary?.succeeded ?? 0],
    ['settings.connectivityFailed', summary?.failed ?? 0],
    ['settings.connectivitySkipped', summary?.skippedAuth ?? 0],
  ] as const;
  return (
    <Drawer
      title={t('settings.connectivityTitle')}
      open={open}
      onClose={onClose}
      width={1000}
      destroyOnHidden
      extra={
        <Space>
          <Button icon={<DownloadOutlined />} disabled={!filtered.length} onClick={exportRows}>
            {t('common.export')}
          </Button>
          <Button type="primary" icon={<ReloadOutlined />} loading={loading} onClick={run}>
            {batch ? t('settings.connectivityRerun') : t('settings.connectivityRun')}
          </Button>
        </Space>
      }
    >
      <Alert
        showIcon
        type="info"
        message={t('settings.connectivityDescription')}
        description={t('settings.connectivityScope', {
          search: sourceSearch?.trim() || t('common.all'),
          type: type || t('common.all'),
        })}
        style={{ marginBottom: 16 }}
      />
      {!batch ? (
        <Card>
          <Flex vertical align="center" gap={12}>
            <Typography.Text type="secondary">{t('settings.connectivityEmpty')}</Typography.Text>
            <Button type="primary" loading={loading} onClick={run}>
              {t('settings.connectivityRun')}
            </Button>
          </Flex>
        </Card>
      ) : (
        <>
          <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
            {cards.map(([key, value]) => (
              <Col xs={12} lg={6} key={key}>
                <Card size="small">
                  <Statistic title={t(key)} value={value} />
                </Card>
              </Col>
            ))}
          </Row>
          {summary?.skippedAuth ? (
            <Alert
              type="warning"
              showIcon
              message={t('settings.connectivitySkippedHint', { count: summary.skippedAuth })}
              style={{ marginBottom: 16 }}
            />
          ) : null}
          <Flex gap={12} wrap style={{ marginBottom: 16 }}>
            <Input.Search
              allowClear
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('settings.connectivitySearch')}
              style={{ width: 300 }}
            />
            <Select
              allowClear
              value={status}
              onChange={setStatus}
              placeholder={t('settings.connectivityAllStatuses')}
              style={{ width: 180 }}
              options={(['SUCCESS', 'FAILED', 'SKIPPED_AUTH'] as const).map((value) => ({
                value,
                label: label(value),
              }))}
            />
            <Typography.Text type="secondary">
              {t('settings.connectivityLatencySummary', {
                average: summary?.averageLatencyMs ?? '-',
                slowest: summary?.slowestLatencyMs ?? '-',
              })}
            </Typography.Text>
          </Flex>
          <Table
            rowKey="key"
            size="small"
            columns={columns}
            dataSource={filtered}
            scroll={{ x: 1200 }}
            pagination={{ pageSize: 20 }}
          />
        </>
      )}
    </Drawer>
  );
};
export default DataSourceConnectivityDrawer;
