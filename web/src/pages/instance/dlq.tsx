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

import { useEffect, useMemo, useRef, useState, type Key } from 'react';
import {
  Alert,
  Card,
  Table,
  Button,
  Input,
  Space,
  Flex,
  Modal,
  DatePicker,
  Typography,
  message,
} from 'antd';
import { MagnifyingGlass, Eye, ArrowsCounterClockwise, Download } from '@phosphor-icons/react';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import PageHeader from '../../components/PageHeader';
import { InstanceSelect } from '../../components/InstanceSelect';
import { useLang } from '../../i18n/LangContext';
import type { DLQGroup, DLQMessage } from '../../api/message';
import {
  exportDLQMessages,
  listDLQGroups,
  listDLQMessages,
  resendDLQ,
  resendSelectedDLQMessages,
} from '../../services/messageService';
import { useInstanceFilter } from '../../hooks/useInstanceFilter';
import { buildCsv, downloadCsv, type CsvColumn } from '../../utils/download';
import { tableScrollX } from '../../utils/table';

const { Text } = Typography;
const { RangePicker } = DatePicker;
const DEFAULT_LOAD_ERROR = '死信队列加载失败，请稍后重试';
const DEFAULT_RETRY_ERROR = '提交重投任务失败，请稍后重试';

/* ─── Helpers ─── */

type ApiErrorLike = {
  message?: unknown;
  response?: {
    data?: {
      message?: unknown;
    };
  };
};

const getErrorMessage = (error: unknown, fallback: string): string => {
  const apiError = error as ApiErrorLike;
  const responseMessage = apiError.response?.data?.message;
  if (typeof responseMessage === 'string' && responseMessage.trim()) {
    return responseMessage;
  }
  if (typeof apiError.message === 'string' && apiError.message.trim()) {
    return apiError.message;
  }
  return fallback;
};

const formatDateTime = (iso?: string | null): string => {
  if (!iso) return '-';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '-';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

const DLQ_EXPORT_COLUMNS: CsvColumn<DLQGroup>[] = [
  { header: 'Group Name', value: (group) => group.groupName },
  { header: 'DLQ Topic', value: (group) => group.dlqTopic },
  { header: 'Message Count', value: (group) => group.messageCount },
  { header: 'Retry Count', value: (group) => group.retryCount },
  { header: 'Status', value: (group) => group.status },
  { header: 'Last Enqueue Time', value: (group) => group.lastEnqueueTime },
];

const exportDLQGroups = (groups: DLQGroup[], filename: string) => {
  downloadCsv(filename, buildCsv(DLQ_EXPORT_COLUMNS, groups));
};

const messageKey = (message: DLQMessage) => `${message.msgId}:${message.queueId}:${message.offset}`;

const DLQ_MESSAGE_EXPORT_COLUMNS: CsvColumn<DLQMessage>[] = [
  { header: 'Message ID', value: (message) => message.msgId },
  { header: 'Topic', value: (message) => message.topic },
  { header: 'Queue ID', value: (message) => message.queueId },
  { header: 'Offset', value: (message) => message.offset },
  { header: 'Store Time', value: (message) => new Date(message.storeTime).toISOString() },
  { header: 'Keys', value: (message) => message.keys },
  { header: 'Body', value: (message) => message.body },
  { header: 'Body Base64', value: (message) => message.bodyBase64 },
];

/* ═══════════════════════════════════════════
   DLQPage
   ═══════════════════════════════════════════ */
const DLQPage = () => {
  const { t } = useLang();
  const { selectedInstanceId, selectInstance, instanceOptions } = useInstanceFilter();
  const [groups, setGroups] = useState<DLQGroup[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);
  const [search, setSearch] = useState('');
  const [retryModalOpen, setRetryModalOpen] = useState(false);
  const [retryGroup, setRetryGroup] = useState<DLQGroup | null>(null);
  const [retryRange, setRetryRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(1, 'day'),
    dayjs(),
  ]);
  // Time range applied to dead-letter message exports. Kept page-level and
  // visible so users know exactly which window the export covers instead of
  // silently falling back to the backend's last-hour default.
  const [exportRange, setExportRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(1, 'day'),
    dayjs(),
  ]);
  const [retryTargetTopic, setRetryTargetTopic] = useState('');
  const [retrySubmitting, setRetrySubmitting] = useState(false);
  const [detailGroup, setDetailGroup] = useState<DLQGroup | null>(null);
  const [detailMessages, setDetailMessages] = useState<DLQMessage[]>([]);
  const [detailTotal, setDetailTotal] = useState(0);
  const [detailPage, setDetailPage] = useState(1);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailIncomplete, setDetailIncomplete] = useState(false);
  const [detailFailedQueues, setDetailFailedQueues] = useState(0);
  const [selectedMessageKeys, setSelectedMessageKeys] = useState<Key[]>([]);
  const [selectedResendSubmitting, setSelectedResendSubmitting] = useState(false);
  const [selectedGroupNames, setSelectedGroupNames] = useState<string[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [retryError, setRetryError] = useState<string | null>(null);
  const retryRequestIdRef = useRef(0);

  useEffect(
    () => () => {
      retryRequestIdRef.current += 1;
    },
    [],
  );

  // The retry dialog owns a group name that is meaningful only for the
  // currently selected instance. Clear all instance-scoped state before
  // starting the next request so an old group cannot be retried on a new
  // instance while that request is in flight. Done as render-time state
  // adjustment (rather than inside the load effect) so state is reset
  // before the next fetch without cascading effect renders.
  const scopeKey = `${selectedInstanceId}:${refreshKey}`;
  const [prevScopeKey, setPrevScopeKey] = useState(scopeKey);
  if (prevScopeKey !== scopeKey) {
    setPrevScopeKey(scopeKey);
    setGroups([]);
    setTotal(0);
    setPage(1);
    setSelectedGroupNames([]);
    setDetailGroup(null);
    setRetryModalOpen(false);
    setRetryGroup(null);
    setRetryTargetTopic('');
    setRetryError(null);
    setLoadError(null);
    setLoading(true);
  }

  useEffect(() => {
    let cancelled = false;

    if (!selectedInstanceId) {
      void Promise.resolve().then(() => {
        if (cancelled) return;
        setLoading(false);
      });
      return () => {
        cancelled = true;
      };
    }

    // Clear `loading` inside the same callback as the data updates so rows and
    // the cleared spinner commit in one batched render — otherwise rows can be
    // visible for a render while the spin overlay still blocks pointer events.
    void listDLQGroups(selectedInstanceId, search || undefined, page, pageSize)
      .then((result) => {
        if (!cancelled) {
          setGroups(result.items);
          setTotal(result.total);
          setLoadError(null);
          const availableGroups = new Set(
            result.items.filter((group) => group.messageCount > 0).map((group) => group.groupName),
          );
          setSelectedGroupNames((selected) =>
            selected.filter((groupName) => availableGroups.has(groupName)),
          );
          setLoading(false);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setLoadError(getErrorMessage(error, DEFAULT_LOAD_ERROR));
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [refreshKey, selectedInstanceId, search, page, pageSize]);

  const selectedGroups = useMemo(() => {
    const selected = new Set(selectedGroupNames);
    return groups.filter((group) => selected.has(group.groupName));
  }, [groups, selectedGroupNames]);

  /* ─── Handlers ─── */
  const handleInstanceChange = (instanceId: string) => {
    retryRequestIdRef.current += 1;
    setRetrySubmitting(false);
    selectInstance(instanceId);
  };

  const openRetryModal = (group: DLQGroup) => {
    setRetryGroup(group);
    setRetryRange([dayjs().subtract(1, 'day'), dayjs()]);
    setRetryTargetTopic('');
    setRetryError(null);
    setRetryModalOpen(true);
  };

  const loadDetailMessages = async (group: DLQGroup, nextPage = 1) => {
    if (!selectedInstanceId) return;
    setDetailLoading(true);
    try {
      const result = await listDLQMessages({
        instanceId: selectedInstanceId,
        groupName: group.groupName,
        startTime: exportRange[0].valueOf(),
        endTime: exportRange[1].valueOf(),
        page: nextPage,
        pageSize: 20,
      });
      setDetailMessages(result.items);
      setDetailTotal(result.total);
      setDetailPage(result.page);
      setDetailIncomplete(result.scanIncomplete);
      setDetailFailedQueues(result.failedQueueCount);
    } catch (error) {
      message.error(getErrorMessage(error, '加载死信消息失败，请稍后重试'));
    } finally {
      setDetailLoading(false);
    }
  };

  const openDetailModal = (group: DLQGroup) => {
    setDetailGroup(group);
    setDetailMessages([]);
    setDetailTotal(0);
    setDetailPage(1);
    setSelectedMessageKeys([]);
    void loadDetailMessages(group);
  };

  const handleSelectedResend = async () => {
    if (!detailGroup || !selectedInstanceId) return;
    const selected = detailMessages.filter((item) =>
      selectedMessageKeys.includes(messageKey(item)),
    );
    if (selected.length === 0) return;
    setSelectedResendSubmitting(true);
    try {
      const result = await resendSelectedDLQMessages({
        instanceId: selectedInstanceId,
        groupName: detailGroup.groupName,
        startTime: exportRange[0].valueOf(),
        endTime: exportRange[1].valueOf(),
        messages: selected.map(({ msgId, queueId, offset }) => ({ msgId, queueId, offset })),
      });
      message.success(`已直接重投 ${result.resent} 条死信消息`);
      setSelectedMessageKeys([]);
      void loadDetailMessages(detailGroup, detailPage);
      setRefreshKey((key) => key + 1);
    } catch (error) {
      message.error(getErrorMessage(error, '重投选中的死信消息失败，请稍后重试'));
    } finally {
      setSelectedResendSubmitting(false);
    }
  };

  const handleRetry = async () => {
    if (!retryTargetTopic) {
      message.warning('请输入目标 Topic');
      return;
    }
    if (!retryGroup || !selectedInstanceId) return;

    const requestId = retryRequestIdRef.current + 1;
    retryRequestIdRef.current = requestId;
    const groupName = retryGroup.groupName;
    const targetTopic = retryTargetTopic;
    setRetrySubmitting(true);
    setRetryError(null);
    try {
      const result = await resendDLQ({
        instanceId: selectedInstanceId,
        groupName,
        startTime: retryRange[0].valueOf(),
        endTime: retryRange[1].valueOf(),
        targetTopic,
      });
      if (retryRequestIdRef.current !== requestId) return;
      setRefreshKey((key) => key + 1);
      if (result.scanIncomplete) {
        message.warning(
          `重投扫描不完整：${result.failedQueueCount ?? 0} 个队列无法扫描，已重投 ${result.resent} 条`,
        );
      } else if (result.failed > 0) {
        message.warning(`重投部分完成：成功 ${result.resent}，失败 ${result.failed}`);
      } else {
        message.success(`重投完成：${groupName} → ${targetTopic}（${result.resent} 条）`);
      }
      setRetryModalOpen(false);
      setRetryGroup(null);
      setRetryError(null);
    } catch (error) {
      if (retryRequestIdRef.current === requestId) {
        setRetryError(getErrorMessage(error, DEFAULT_RETRY_ERROR));
      }
    } finally {
      if (retryRequestIdRef.current === requestId) {
        setRetrySubmitting(false);
      }
    }
  };

  const handleExport = async (group: DLQGroup) => {
    try {
      const blob = await exportDLQMessages({
        instanceId: selectedInstanceId,
        groupName: group.groupName,
        startTime: exportRange[0].valueOf(),
        endTime: exportRange[1].valueOf(),
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${group.groupName}-dlq-messages.json`;
      link.click();
      URL.revokeObjectURL(url);
      message.success(`已导出 ${group.groupName} 的死信消息（${blob.size} 字节）`);
    } catch (error) {
      message.error(getErrorMessage(error, '导出死信消息失败，请稍后重试'));
    }
  };

  const handleBatchExport = () => {
    if (selectedGroups.length === 0) return;
    exportDLQGroups(selectedGroups, 'dlq-groups.csv');
  };

  /* ─── Table Columns ─── */
  const columns: ColumnsType<DLQGroup> = [
    {
      title: 'Group 名称',
      dataIndex: 'groupName',
      key: 'groupName',
      width: 200,
      sorter: (a, b) => a.groupName.localeCompare(b.groupName),
      render: (name: string) => (
        <Text strong style={{ fontSize: 14 }}>
          {name}
        </Text>
      ),
    },
    {
      title: 'DLQ Topic',
      dataIndex: 'dlqTopic',
      key: 'dlqTopic',
      width: 240,
      render: (topic: string) => (
        <Text style={{ fontSize: 14, fontFamily: 'monospace' }}>{topic}</Text>
      ),
    },
    {
      title: '死信数量',
      dataIndex: 'messageCount',
      key: 'messageCount',
      width: 100,
      align: 'right',
      sorter: (a, b) => a.messageCount - b.messageCount,
      render: (count: number, record: DLQGroup) => (
        <Text
          style={{
            fontFamily: 'monospace',
            fontWeight: 600,
            color:
              record.statsAvailable === false
                ? undefined
                : count > 50
                  ? '#ff4d4f'
                  : count > 0
                    ? '#fa8c16'
                    : undefined,
          }}
        >
          {record.statsAvailable === false ? '不可用' : count.toLocaleString()}
        </Text>
      ),
    },
    {
      title: '最近入队时间',
      dataIndex: 'lastEnqueueTime',
      key: 'lastEnqueueTime',
      width: 180,
      sorter: (a, b) => (a.lastEnqueueTime || '').localeCompare(b.lastEnqueueTime || ''),
      render: (time?: string | null) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
          {formatDateTime(time)}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 280,
      render: (_: unknown, record: DLQGroup) => (
        <Flex gap={6}>
          <Button
            size="small"
            icon={<Eye size={14} />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={() => openDetailModal(record)}
          >
            查看详情
          </Button>
          <Button
            size="small"
            icon={<ArrowsCounterClockwise size={14} />}
            style={{ borderColor: '#fa8c16', color: '#fa8c16' }}
            onClick={() => openRetryModal(record)}
            disabled={record.statsAvailable === false || record.messageCount === 0}
          >
            重投消息
          </Button>
          <Button
            size="small"
            icon={<Download size={14} />}
            style={{ borderColor: '#52c41a', color: '#52c41a' }}
            onClick={() => handleExport(record)}
            disabled={record.statsAvailable === false || record.messageCount === 0}
          >
            导出
          </Button>
        </Flex>
      ),
    },
  ];

  /* ═══════════════════════════════════════════
     Render
     ═══════════════════════════════════════════ */
  return (
    <div style={{ padding: 24 }}>
      <PageHeader title={t('dlq.title')} subtitle="管理消费失败进入死信队列的消息" />

      {/* ── Filter Bar ── */}
      <Flex justify="space-between" align="center" style={{ marginBottom: 16 }}>
        <Space size={12} wrap>
          <InstanceSelect
            value={selectedInstanceId || undefined}
            onChange={handleInstanceChange}
            options={instanceOptions}
            style={{ width: 220 }}
          />
          <Input.Search
            placeholder="搜索 Group 名称或 DLQ Topic"
            allowClear
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(1);
            }}
            onSearch={(value) => {
              setSearch(value);
              setPage(1);
            }}
            style={{ width: 320 }}
            prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
          />
          <Space size={8}>
            <Text type="secondary" style={{ fontSize: 14 }}>
              导出时间范围
            </Text>
            <RangePicker
              showTime
              format="YYYY-MM-DD HH:mm:ss"
              value={exportRange}
              onChange={(vals) => {
                if (vals && vals[0] && vals[1]) {
                  setExportRange([vals[0], vals[1]]);
                }
              }}
              style={{ width: 380 }}
            />
          </Space>
        </Space>
        <Button
          icon={<Download size={16} />}
          disabled={selectedGroups.length === 0}
          onClick={handleBatchExport}
        >
          {t('message.batchExport')}
          {selectedGroups.length > 0 ? ` (${selectedGroups.length})` : ''}
        </Button>
      </Flex>

      {loadError && (
        <Alert showIcon type="warning" message={loadError} style={{ marginBottom: 16 }} />
      )}

      {/* ── Table ── */}
      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={groups}
          rowKey="groupName"
          loading={loading}
          rowSelection={{
            selectedRowKeys: selectedGroupNames,
            preserveSelectedRowKeys: true,
            onChange: (keys) => setSelectedGroupNames(keys.map(String)),
            getCheckboxProps: (record) => ({
              disabled: record.statsAvailable === false || record.messageCount === 0,
            }),
          }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: [20, 50, 100],
            showTotal: (totalCount) => `共 ${totalCount} 个 Group`,
            onChange: (nextPage, nextPageSize) => {
              setPage(nextPage);
              setPageSize(nextPageSize);
              setSelectedGroupNames([]);
            },
          }}
          size="small"
          scroll={{ x: tableScrollX(columns, { selection: true }) }}
        />
      </Card>

      {/* ═══════════════════════════════════════════
         Retry Modal
         ═══════════════════════════════════════════ */}
      <Modal
        title={
          <Space>
            <ArrowsCounterClockwise size={18} color="#fa8c16" />
            <span>重投死信消息</span>
          </Space>
        }
        open={retryModalOpen}
        onCancel={() => {
          setRetryModalOpen(false);
          setRetryGroup(null);
          setRetryError(null);
        }}
        onOk={handleRetry}
        confirmLoading={retrySubmitting}
        okText="确认重投"
        cancelText="取消"
        width={520}
        destroyOnHidden
      >
        {retryGroup && (
          <div style={{ marginTop: 16 }}>
            {retryError && (
              <Alert showIcon type="warning" message={retryError} style={{ marginBottom: 16 }} />
            )}

            <div
              style={{
                marginBottom: 16,
                padding: '12px 16px',
                background: '#fff7e6',
                borderRadius: 8,
                border: '1px solid #ffd591',
              }}
            >
              <Text type="warning" style={{ fontSize: 14 }}>
                ⚠️ 重投操作将把死信消息重新发送到指定 Topic，请确认目标 Topic 正确。
              </Text>
            </div>

            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 4 }}>
                源 Group
              </Text>
              <Text strong style={{ fontSize: 14 }}>
                {retryGroup.groupName}
              </Text>
            </div>

            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 4 }}>
                死信数量
              </Text>
              <Text strong style={{ fontSize: 14, color: '#fa8c16' }}>
                {retryGroup.messageCount.toLocaleString()} 条
              </Text>
            </div>

            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 8 }}>
                重投时间范围
              </Text>
              <RangePicker
                showTime
                style={{ width: '100%' }}
                value={retryRange}
                onChange={(vals) => {
                  if (vals && vals[0] && vals[1]) {
                    setRetryRange([vals[0], vals[1]]);
                  }
                }}
                format="YYYY-MM-DD HH:mm:ss"
              />
            </div>

            <div>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 8 }}>
                目标 Topic
              </Text>
              <Input
                placeholder="输入目标 Topic 名称"
                value={retryTargetTopic}
                onChange={(e) => setRetryTargetTopic(e.target.value)}
              />
            </div>
          </div>
        )}
      </Modal>

      <Modal
        title="死信队列详情"
        open={Boolean(detailGroup)}
        onCancel={() => setDetailGroup(null)}
        footer={null}
        width={1080}
        destroyOnHidden
      >
        {detailGroup && (
          <div style={{ marginTop: 8 }}>
            <Flex justify="space-between" align="center" style={{ marginBottom: 12 }}>
              <Space direction="vertical" size={0}>
                <Text strong copyable style={{ fontFamily: 'monospace' }}>
                  {detailGroup.groupName}
                </Text>
                <Text type="secondary" copyable style={{ fontFamily: 'monospace' }}>
                  {detailGroup.dlqTopic}
                </Text>
                <Space size={4}>
                  <Text type="secondary">状态：</Text>
                  <Text>
                    {detailGroup.statsAvailable === false ? '统计不可用' : detailGroup.status}
                  </Text>
                </Space>
              </Space>
              <Space>
                <Button
                  icon={<Download size={14} />}
                  disabled={detailMessages.length === 0}
                  onClick={() =>
                    downloadCsv(
                      `${detailGroup.groupName}-dlq-messages.csv`,
                      buildCsv(DLQ_MESSAGE_EXPORT_COLUMNS, detailMessages),
                    )
                  }
                >
                  导出当前页
                </Button>
                <Button
                  type="primary"
                  icon={<ArrowsCounterClockwise size={14} />}
                  loading={selectedResendSubmitting}
                  disabled={selectedMessageKeys.length === 0}
                  onClick={() => void handleSelectedResend()}
                >
                  重投选中消息{selectedMessageKeys.length ? ` (${selectedMessageKeys.length})` : ''}
                </Button>
              </Space>
            </Flex>
            {detailIncomplete && (
              <Alert
                showIcon
                type="warning"
                message={`扫描不完整，${detailFailedQueues} 个队列无法读取或结果达到上限。`}
                style={{ marginBottom: 12 }}
              />
            )}
            <Table<DLQMessage>
              rowKey={messageKey}
              size="small"
              loading={detailLoading}
              dataSource={detailMessages}
              rowSelection={{
                selectedRowKeys: selectedMessageKeys,
                onChange: setSelectedMessageKeys,
              }}
              columns={[
                {
                  title: '消息 ID',
                  dataIndex: 'msgId',
                  width: 220,
                  render: (value: string) => (
                    <Text copyable style={{ fontFamily: 'monospace' }}>
                      {value}
                    </Text>
                  ),
                },
                { title: '队列', dataIndex: 'queueId', width: 70 },
                { title: 'Offset', dataIndex: 'offset', width: 100 },
                {
                  title: '存储时间',
                  dataIndex: 'storeTime',
                  width: 170,
                  render: (value: number) => formatDateTime(new Date(value).toISOString()),
                },
                { title: 'Keys', dataIndex: 'keys', width: 160, ellipsis: true },
                {
                  title: '消息体',
                  dataIndex: 'body',
                  ellipsis: true,
                  render: (value?: string) => value || '-',
                },
              ]}
              pagination={{
                current: detailPage,
                pageSize: 20,
                total: detailTotal,
                onChange: (nextPage) => void loadDetailMessages(detailGroup, nextPage),
              }}
              scroll={{ x: 1000 }}
            />
          </div>
        )}
      </Modal>
    </div>
  );
};

export default DLQPage;
