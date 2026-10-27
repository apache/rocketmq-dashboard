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
  Empty,
  Flex,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
  theme,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { DownloadOutlined, SwapOutlined } from '@ant-design/icons';
import type { Instance } from '../api/instance';
import { listAllTopics } from '../services/topicService';
import { useLang } from '../i18n/LangContext';
import InfoBanner from './InfoBanner';
import { buildCsv, downloadCsv, type CsvColumn } from '../utils/download';
import { tableScrollX } from '../utils/table';
import {
  compareTopicInventories,
  filterTopicComparisonRows,
  formatTopicDifferences,
  type TopicComparisonResult,
  type TopicComparisonRow,
  type TopicComparisonStatus,
  type TopicConfigField,
} from '../utils/topicConfigComparison';

interface TopicConfigComparisonModalProps {
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

// Same four hues as the row-action palette elsewhere in the app (antd presets), used where a CSS
// color is needed rather than a Tag color name: the card accent and the topic dot.
const STATUS_ACCENT: Record<TopicComparisonStatus, string> = {
  MATCH: '#52c41a',
  DRIFT: '#faad14',
  ONLY_SOURCE: '#1677ff',
  ONLY_TARGET: '#722ed1',
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

const TopicConfigComparisonModal = ({
  open,
  instances,
  currentInstanceId,
  onClose,
}: TopicConfigComparisonModalProps) => {
  const { t } = useLang();
  const { token } = theme.useToken();
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
    setResult(null);
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

  // Any change of the compared pair invalidates a comparison that is still loading, so a
  // response for the previous pair can never be rendered under the new selection.
  const invalidateComparison = () => {
    requestIdRef.current += 1;
    setResult(null);
    setLoading(false);
  };

  const swapInstances = () => {
    setSourceInstanceId(targetInstanceId);
    setTargetInstanceId(sourceInstanceId);
    invalidateComparison();
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
      minWidth: 240,
      ellipsis: true,
      sorter: (left, right) => left.topicName.localeCompare(right.topicName),
      render: (topicName: string, row) => (
        <Flex align="center" gap={8}>
          <span
            aria-hidden
            style={{
              width: 8,
              height: 8,
              borderRadius: 4,
              flex: 'none',
              background: STATUS_ACCENT[row.status],
            }}
          />
          <Typography.Text style={{ fontFamily: 'monospace', fontSize: 14 }}>
            {topicName}
          </Typography.Text>
        </Flex>
      ),
    },
    {
      title: t('topicCompare.status'),
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: TopicComparisonStatus) => (
        <Tag color={STATUS_COLORS[status]}>{t(STATUS_LABELS[status])}</Tag>
      ),
    },
    {
      title: t('topicCompare.differenceCount'),
      key: 'differenceCount',
      width: 130,
      align: 'right',
      sorter: (left, right) => left.differences.length - right.differences.length,
      render: (_, row) =>
        row.differences.length === 0 ? (
          <Typography.Text type="secondary">—</Typography.Text>
        ) : (
          <Tag color="warning">
            {t('topicCompare.differenceCountValue', { count: row.differences.length })}
          </Tag>
        ),
    },
  ];

  const summaryCards = [
    { status: 'MATCH', label: t('topicCompare.matches'), value: result?.summary.matches ?? 0 },
    { status: 'DRIFT', label: t('topicCompare.drifted'), value: result?.summary.drifted ?? 0 },
    {
      status: 'ONLY_SOURCE',
      label: t('topicCompare.onlySource'),
      value: result?.summary.onlySource ?? 0,
    },
    {
      status: 'ONLY_TARGET',
      label: t('topicCompare.onlyTarget'),
      value: result?.summary.onlyTarget ?? 0,
    },
  ] as const;

  return (
    <Modal
      title={t('topicCompare.title')}
      open={open}
      onCancel={onClose}
      width={960}
      footer={null}
      destroyOnHidden
      styles={{
        // The result is a pair of inventory tables; cap the body so a long comparison scrolls
        // inside the modal instead of growing it past the viewport.
        body: { maxHeight: 'calc(100vh - 240px)', overflowY: 'auto', overflowX: 'hidden' },
      }}
    >
      <Flex vertical gap={16}>
        <InfoBanner description={t('topicCompare.description')} style={{ marginBottom: 0 }} />

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
                invalidateComparison();
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
                invalidateComparison();
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

        {instances.length < 2 ? (
          <Empty description={t('topicCompare.needTwoInstances')} />
        ) : (
          !result &&
          !loading && (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('topicCompare.emptyHint')} />
          )
        )}

        <Spin spinning={loading}>
          {result && (
            <Flex vertical gap={12}>
              {/* 四张状态卡同时是筛选器：点一下按该状态过滤，再点取消，替代原先重复的状态下拉。 */}
              <Flex gap={12} wrap="wrap">
                {summaryCards.map((card) => {
                  const active = statusFilter === card.status;
                  const accent = STATUS_ACCENT[card.status];
                  return (
                    <button
                      key={card.status}
                      type="button"
                      aria-pressed={active}
                      aria-label={t('topicCompare.filterByStatus', { status: card.label })}
                      onClick={() => setStatusFilter(active ? 'ALL' : card.status)}
                      style={{
                        flex: 1,
                        minWidth: 140,
                        padding: 0,
                        border: 'none',
                        background: 'none',
                        cursor: 'pointer',
                        textAlign: 'left',
                      }}
                    >
                      <Card
                        size="small"
                        style={{
                          borderTop: `2px solid ${accent}`,
                          borderColor: active ? accent : undefined,
                          background: active ? token.colorFillTertiary : undefined,
                        }}
                      >
                        <Statistic
                          title={card.label}
                          value={card.value}
                          valueStyle={{ color: accent, fontSize: 22 }}
                        />
                      </Card>
                    </button>
                  );
                })}
              </Flex>

              <Flex justify="space-between" gap={8} align="center" wrap="wrap">
                <Typography.Text type="secondary" style={{ fontSize: 14 }}>
                  {t('topicCompare.resultSummary', {
                    source: sourceInstanceId ?? '-',
                    target: targetInstanceId ?? '-',
                    total: result.rows.length,
                    visible: visibleRows.length,
                  })}
                </Typography.Text>
                <Space wrap>
                  {statusFilter !== 'ALL' && (
                    <Button size="small" onClick={() => setStatusFilter('ALL')}>
                      {t('topicCompare.clearStatusFilter')}
                    </Button>
                  )}
                  <Input.Search
                    allowClear
                    aria-label={t('topicCompare.search')}
                    placeholder={t('topicCompare.search')}
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    style={{ width: 240 }}
                  />
                  <Button icon={<DownloadOutlined />} onClick={exportComparison}>
                    {t('topicCompare.export')}
                  </Button>
                </Space>
              </Flex>

              <Table<TopicComparisonRow>
                rowKey="key"
                size="small"
                sticky
                columns={columns}
                dataSource={visibleRows}
                tableLayout="fixed"
                scroll={{ x: tableScrollX(columns) }}
                pagination={{ pageSize: 20, showSizeChanger: false }}
                locale={{
                  emptyText: (
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description={t('topicCompare.noRowsForFilter')}
                    />
                  ),
                }}
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
                          width: 160,
                          render: (field: TopicConfigField) => (
                            <Typography.Text strong>{t(FIELD_LABELS[field])}</Typography.Text>
                          ),
                        },
                        {
                          title: sourceInstanceId,
                          dataIndex: 'sourceValue',
                          render: (value: string | number | null) => (
                            <Typography.Text type="secondary" style={{ fontFamily: 'monospace' }}>
                              {value ?? '—'}
                            </Typography.Text>
                          ),
                        },
                        {
                          title: targetInstanceId,
                          dataIndex: 'targetValue',
                          render: (value: string | number | null) => (
                            <Typography.Text
                              strong
                              style={{ fontFamily: 'monospace', color: STATUS_ACCENT.DRIFT }}
                            >
                              {value ?? '—'}
                            </Typography.Text>
                          ),
                        },
                      ]}
                    />
                  ),
                }}
              />
            </Flex>
          )}
        </Spin>
      </Flex>
    </Modal>
  );
};

export default TopicConfigComparisonModal;
