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
  Button,
  Card,
  Drawer,
  Empty,
  Flex,
  Input,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { DownloadOutlined, SwapOutlined } from '@ant-design/icons';
import type { Instance } from '../api/instance';
import { listAllConsumerGroups } from '../services/consumerService';
import { useLang } from '../i18n/LangContext';
import { buildCsv, downloadCsv, type CsvColumn } from '../utils/download';
import {
  compareConsumerGroupInventories,
  filterConsumerGroupComparisonRows,
  formatConsumerGroupDifferences,
  type ConsumerGroupComparisonResult,
  type ConsumerGroupComparisonRow,
  type ConsumerGroupComparisonStatus,
  type ConsumerGroupConfigField,
} from '../utils/consumerGroupConfigComparison';

interface ConsumerGroupConfigComparisonDrawerProps {
  open: boolean;
  instances: Instance[];
  currentInstanceId?: string;
  onClose: () => void;
}

const STATUS_COLORS: Record<ConsumerGroupComparisonStatus, string> = {
  MATCH: 'success',
  DRIFT: 'warning',
  ONLY_SOURCE: 'blue',
  ONLY_TARGET: 'purple',
};

const STATUS_LABELS: Record<ConsumerGroupComparisonStatus, string> = {
  MATCH: 'consumerCompare.statusMatch',
  DRIFT: 'consumerCompare.statusDrift',
  ONLY_SOURCE: 'consumerCompare.statusOnlySource',
  ONLY_TARGET: 'consumerCompare.statusOnlyTarget',
};

const FIELD_LABELS: Record<ConsumerGroupConfigField, string> = {
  namespace: 'consumerCompare.fieldNamespace',
  subscriptionMode: 'consumerCompare.fieldSubscriptionMode',
  consumeType: 'consumerCompare.fieldConsumeType',
  subscriptionDataType: 'consumerCompare.fieldSubscriptionDataType',
  deliveryOrderType: 'consumerCompare.fieldDeliveryOrderType',
  retryMaxTimes: 'consumerCompare.fieldRetryMaxTimes',
};

const CSV_COLUMNS: CsvColumn<ConsumerGroupComparisonRow>[] = [
  { header: 'Consumer Group', value: (row) => row.groupName },
  { header: 'Status', value: (row) => row.status },
  { header: 'Differences', value: (row) => formatConsumerGroupDifferences(row.differences) },
  { header: 'Source Namespace', value: (row) => row.source?.namespace },
  { header: 'Target Namespace', value: (row) => row.target?.namespace },
  { header: 'Source Subscription Mode', value: (row) => row.source?.subscriptionMode },
  { header: 'Target Subscription Mode', value: (row) => row.target?.subscriptionMode },
  { header: 'Source Consume Type', value: (row) => row.source?.consumeType },
  { header: 'Target Consume Type', value: (row) => row.target?.consumeType },
  { header: 'Source Subscription Data Type', value: (row) => row.source?.subscriptionDataType },
  { header: 'Target Subscription Data Type', value: (row) => row.target?.subscriptionDataType },
  { header: 'Source Delivery Order Type', value: (row) => row.source?.deliveryOrderType },
  { header: 'Target Delivery Order Type', value: (row) => row.target?.deliveryOrderType },
  { header: 'Source Retry Max Times', value: (row) => row.source?.retryMaxTimes },
  { header: 'Target Retry Max Times', value: (row) => row.target?.retryMaxTimes },
];

const ConsumerGroupConfigComparisonDrawer = ({
  open,
  instances,
  currentInstanceId,
  onClose,
}: ConsumerGroupConfigComparisonDrawerProps) => {
  const { t } = useLang();
  const initialSource =
    currentInstanceId && instances.some((instance) => instance.name === currentInstanceId)
      ? currentInstanceId
      : instances[0]?.name;
  const [sourceInstanceId, setSourceInstanceId] = useState<string | undefined>(initialSource);
  const [targetInstanceId, setTargetInstanceId] = useState<string | undefined>(
    instances.find((instance) => instance.name !== initialSource)?.name,
  );
  const [result, setResult] = useState<ConsumerGroupComparisonResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState<ConsumerGroupComparisonStatus | 'ALL'>('ALL');
  const [search, setSearch] = useState('');
  const requestIdRef = useRef(0);

  const options = instances.map((instance) => ({ value: instance.name, label: instance.name }));
  const visibleRows = useMemo(
    () => filterConsumerGroupComparisonRows(result?.rows ?? [], statusFilter, search),
    [result, search, statusFilter],
  );

  const runComparison = async () => {
    if (!sourceInstanceId || !targetInstanceId || sourceInstanceId === targetInstanceId) {
      message.warning(t('consumerCompare.selectDifferentInstances'));
      return;
    }
    const requestId = ++requestIdRef.current;
    setLoading(true);
    try {
      const [sourceGroups, targetGroups] = await Promise.all([
        listAllConsumerGroups({ instanceId: sourceInstanceId }),
        listAllConsumerGroups({ instanceId: targetInstanceId }),
      ]);
      if (requestId === requestIdRef.current) {
        setResult(compareConsumerGroupInventories(sourceGroups, targetGroups));
        setStatusFilter('ALL');
        setSearch('');
      }
    } catch {
      if (requestId === requestIdRef.current) message.error(t('consumerCompare.loadFailed'));
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  };

  const swapInstances = () => {
    setSourceInstanceId(targetInstanceId);
    setTargetInstanceId(sourceInstanceId);
    setResult(null);
  };

  const exportComparison = () => {
    if (!result || !sourceInstanceId || !targetInstanceId) return;
    downloadCsv(
      `rocketmq-consumer-group-config-${sourceInstanceId}-vs-${targetInstanceId}.csv`,
      buildCsv(CSV_COLUMNS, visibleRows),
    );
    message.success(t('consumerCompare.exported', { count: visibleRows.length }));
  };

  const columns: TableColumnsType<ConsumerGroupComparisonRow> = [
    {
      title: t('consumerCompare.group'),
      dataIndex: 'groupName',
      key: 'groupName',
      ellipsis: true,
      sorter: (left, right) => left.groupName.localeCompare(right.groupName),
      render: (value: string) => <span title={value}>{value}</span>,
    },
    {
      title: t('consumerCompare.status'),
      dataIndex: 'status',
      key: 'status',
      width: 150,
      render: (status: ConsumerGroupComparisonStatus) => (
        <Tag color={STATUS_COLORS[status]}>{t(STATUS_LABELS[status])}</Tag>
      ),
    },
    {
      title: t('consumerCompare.differenceCount'),
      key: 'differenceCount',
      width: 150,
      render: (_, row) => row.differences.length,
    },
  ];

  return (
    <Drawer
      title={t('consumerCompare.title')}
      open={open}
      onClose={onClose}
      width={960}
      destroyOnHidden
    >
      <Flex vertical gap={16}>
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          {t('consumerCompare.description')}
        </Typography.Paragraph>
        <Flex gap={8} align="end" wrap="wrap">
          <label style={{ flex: 1, minWidth: 220 }}>
            <Typography.Text>{t('consumerCompare.sourceInstance')}</Typography.Text>
            <Select
              aria-label={t('consumerCompare.sourceInstance')}
              value={sourceInstanceId}
              options={options}
              style={{ width: '100%', marginTop: 4 }}
              onChange={(value) => {
                setSourceInstanceId(value);
                setResult(null);
              }}
            />
          </label>
          <Button
            aria-label={t('consumerCompare.swap')}
            icon={<SwapOutlined />}
            onClick={swapInstances}
          />
          <label style={{ flex: 1, minWidth: 220 }}>
            <Typography.Text>{t('consumerCompare.targetInstance')}</Typography.Text>
            <Select
              aria-label={t('consumerCompare.targetInstance')}
              value={targetInstanceId}
              options={options}
              style={{ width: '100%', marginTop: 4 }}
              onChange={(value) => {
                setTargetInstanceId(value);
                setResult(null);
              }}
            />
          </label>
          <Button
            type="primary"
            loading={loading}
            disabled={instances.length < 2}
            onClick={() => void runComparison()}
          >
            {t('consumerCompare.compare')}
          </Button>
        </Flex>

        {instances.length < 2 && <Empty description={t('consumerCompare.needTwoInstances')} />}

        {result && (
          <>
            <Flex gap={12} wrap="wrap">
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic title={t('consumerCompare.matches')} value={result.summary.matches} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic title={t('consumerCompare.drifted')} value={result.summary.drifted} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic
                  title={t('consumerCompare.onlySource')}
                  value={result.summary.onlySource}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic
                  title={t('consumerCompare.onlyTarget')}
                  value={result.summary.onlyTarget}
                />
              </Card>
            </Flex>
            <Flex justify="space-between" gap={8} wrap="wrap">
              <Space wrap>
                <Input.Search
                  allowClear
                  aria-label={t('consumerCompare.search')}
                  placeholder={t('consumerCompare.search')}
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  style={{ width: 260 }}
                />
                <Select
                  aria-label={t('consumerCompare.statusFilter')}
                  value={statusFilter}
                  onChange={setStatusFilter}
                  style={{ width: 180 }}
                  options={[
                    { value: 'ALL', label: t('consumerCompare.statusAll') },
                    ...Object.keys(STATUS_LABELS).map((status) => ({
                      value: status,
                      label: t(STATUS_LABELS[status as ConsumerGroupComparisonStatus]),
                    })),
                  ]}
                />
              </Space>
              <Button icon={<DownloadOutlined />} onClick={exportComparison}>
                {t('consumerCompare.export')}
              </Button>
            </Flex>
            <Table<ConsumerGroupComparisonRow>
              rowKey="key"
              columns={columns}
              dataSource={visibleRows}
              pagination={{ pageSize: 20, showSizeChanger: false }}
              expandable={{
                rowExpandable: (row) => row.differences.length > 0,
                expandedRowRender: (row) => (
                  <Table
                    rowKey="field"
                    size="small"
                    pagination={false}
                    dataSource={row.differences}
                    columns={[
                      {
                        title: t('consumerCompare.field'),
                        dataIndex: 'field',
                        render: (field: ConsumerGroupConfigField) => t(FIELD_LABELS[field]),
                      },
                      { title: sourceInstanceId, dataIndex: 'sourceValue' },
                      { title: targetInstanceId, dataIndex: 'targetValue' },
                    ]}
                  />
                ),
              }}
            />
          </>
        )}
      </Flex>
    </Drawer>
  );
};

export default ConsumerGroupConfigComparisonDrawer;
