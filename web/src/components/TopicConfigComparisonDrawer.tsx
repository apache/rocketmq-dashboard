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
import { listAllTopics } from '../services/topicService';
import { useLang } from '../i18n/LangContext';
import { buildCsv, downloadCsv, type CsvColumn } from '../utils/download';
import {
  compareTopicInventories,
  filterTopicComparisonRows,
  formatTopicDifferences,
  type TopicComparisonResult,
  type TopicComparisonRow,
  type TopicComparisonStatus,
  type TopicConfigField,
} from '../utils/topicConfigComparison';

interface TopicConfigComparisonDrawerProps {
  open: boolean;
  instances: Instance[];
  currentInstanceId?: string;
  onClose: () => void;
}

const STATUS_COLORS: Record<TopicComparisonStatus, string> = {
  MATCH: 'success',
  DRIFT: 'warning',
  ONLY_SOURCE: 'blue',
  ONLY_TARGET: 'purple',
};

const STATUS_LABELS: Record<TopicComparisonStatus, string> = {
  MATCH: 'topicCompare.statusMatch',
  DRIFT: 'topicCompare.statusDrift',
  ONLY_SOURCE: 'topicCompare.statusOnlySource',
  ONLY_TARGET: 'topicCompare.statusOnlyTarget',
};

const FIELD_LABELS: Record<TopicConfigField, string> = {
  type: 'topicCompare.fieldType',
  namespace: 'topicCompare.fieldNamespace',
  writeQueues: 'topicCompare.fieldWriteQueues',
  readQueues: 'topicCompare.fieldReadQueues',
  perm: 'topicCompare.fieldPermission',
};

const CSV_COLUMNS: CsvColumn<TopicComparisonRow>[] = [
  { header: 'Topic', value: (row) => row.topicName },
  { header: 'Status', value: (row) => row.status },
  { header: 'Differences', value: (row) => formatTopicDifferences(row.differences) },
  { header: 'Source Type', value: (row) => row.source?.type },
  { header: 'Target Type', value: (row) => row.target?.type },
  { header: 'Source Write Queues', value: (row) => row.source?.writeQueues },
  { header: 'Target Write Queues', value: (row) => row.target?.writeQueues },
  { header: 'Source Read Queues', value: (row) => row.source?.readQueues },
  { header: 'Target Read Queues', value: (row) => row.target?.readQueues },
  { header: 'Source Permission', value: (row) => row.source?.perm },
  { header: 'Target Permission', value: (row) => row.target?.perm },
];

const TopicConfigComparisonDrawer = ({
  open,
  instances,
  currentInstanceId,
  onClose,
}: TopicConfigComparisonDrawerProps) => {
  const { t } = useLang();
  const initialSource =
    currentInstanceId && instances.some((instance) => instance.name === currentInstanceId)
      ? currentInstanceId
      : instances[0]?.name;
  const [sourceInstanceId, setSourceInstanceId] = useState<string | undefined>(initialSource);
  const [targetInstanceId, setTargetInstanceId] = useState<string | undefined>(
    instances.find((instance) => instance.name !== initialSource)?.name,
  );
  const [result, setResult] = useState<TopicComparisonResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState<TopicComparisonStatus | 'ALL'>('ALL');
  const [search, setSearch] = useState('');
  const requestIdRef = useRef(0);

  const options = instances.map((instance) => ({
    value: instance.name,
    label: instance.name,
  }));
  const visibleRows = useMemo(
    () => filterTopicComparisonRows(result?.rows ?? [], statusFilter, search),
    [result, search, statusFilter],
  );

  const runComparison = async () => {
    if (!sourceInstanceId || !targetInstanceId || sourceInstanceId === targetInstanceId) {
      message.warning(t('topicCompare.selectDifferentInstances'));
      return;
    }
    const requestId = ++requestIdRef.current;
    setLoading(true);
    try {
      const [sourceTopics, targetTopics] = await Promise.all([
        listAllTopics({ instanceId: sourceInstanceId }),
        listAllTopics({ instanceId: targetInstanceId }),
      ]);
      if (requestId === requestIdRef.current) {
        setResult(compareTopicInventories(sourceTopics, targetTopics));
        setStatusFilter('ALL');
        setSearch('');
      }
    } catch {
      if (requestId === requestIdRef.current) message.error(t('topicCompare.loadFailed'));
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
      `rocketmq-topic-config-${sourceInstanceId}-vs-${targetInstanceId}.csv`,
      buildCsv(CSV_COLUMNS, visibleRows),
    );
    message.success(t('topicCompare.exported', { count: visibleRows.length }));
  };

  const columns: TableColumnsType<TopicComparisonRow> = [
    {
      title: t('topicCompare.topic'),
      dataIndex: 'topicName',
      key: 'topicName',
      sorter: (left, right) => left.topicName.localeCompare(right.topicName),
    },
    {
      title: t('topicCompare.status'),
      dataIndex: 'status',
      key: 'status',
      width: 150,
      render: (status: TopicComparisonStatus) => (
        <Tag color={STATUS_COLORS[status]}>{t(STATUS_LABELS[status])}</Tag>
      ),
    },
    {
      title: t('topicCompare.differenceCount'),
      key: 'differenceCount',
      width: 150,
      render: (_, row) => row.differences.length,
    },
  ];

  return (
    <Drawer
      title={t('topicCompare.title')}
      open={open}
      onClose={onClose}
      width={960}
      destroyOnHidden
    >
      <Flex vertical gap={16}>
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          {t('topicCompare.description')}
        </Typography.Paragraph>
        <Flex gap={8} align="end" wrap="wrap">
          <label style={{ flex: 1, minWidth: 220 }}>
            <Typography.Text>{t('topicCompare.sourceInstance')}</Typography.Text>
            <Select
              aria-label={t('topicCompare.sourceInstance')}
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
            aria-label={t('topicCompare.swap')}
            icon={<SwapOutlined />}
            onClick={swapInstances}
          />
          <label style={{ flex: 1, minWidth: 220 }}>
            <Typography.Text>{t('topicCompare.targetInstance')}</Typography.Text>
            <Select
              aria-label={t('topicCompare.targetInstance')}
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
            {t('topicCompare.compare')}
          </Button>
        </Flex>

        {instances.length < 2 && <Empty description={t('topicCompare.needTwoInstances')} />}

        {result && (
          <>
            <Flex gap={12} wrap="wrap">
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic title={t('topicCompare.matches')} value={result.summary.matches} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic title={t('topicCompare.drifted')} value={result.summary.drifted} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic title={t('topicCompare.onlySource')} value={result.summary.onlySource} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic title={t('topicCompare.onlyTarget')} value={result.summary.onlyTarget} />
              </Card>
            </Flex>
            <Flex justify="space-between" gap={8} wrap="wrap">
              <Space wrap>
                <Input.Search
                  allowClear
                  aria-label={t('topicCompare.search')}
                  placeholder={t('topicCompare.search')}
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  style={{ width: 240 }}
                />
                <Select
                  aria-label={t('topicCompare.statusFilter')}
                  value={statusFilter}
                  onChange={setStatusFilter}
                  style={{ width: 180 }}
                  options={[
                    { value: 'ALL', label: t('topicCompare.statusAll') },
                    ...Object.keys(STATUS_LABELS).map((status) => ({
                      value: status,
                      label: t(STATUS_LABELS[status as TopicComparisonStatus]),
                    })),
                  ]}
                />
              </Space>
              <Button icon={<DownloadOutlined />} onClick={exportComparison}>
                {t('topicCompare.export')}
              </Button>
            </Flex>
            <Table<TopicComparisonRow>
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
                        title: t('topicCompare.field'),
                        dataIndex: 'field',
                        render: (field: TopicConfigField) => t(FIELD_LABELS[field]),
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

export default TopicConfigComparisonDrawer;
