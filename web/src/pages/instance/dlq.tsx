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

import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Card,
  Table,
  Button,
  Input,
  Space,
  Flex,
  Modal,
  Drawer,
  DatePicker,
  Typography,
  message,
} from 'antd';
import { MagnifyingGlass, Eye, ArrowsCounterClockwise, Download } from '@phosphor-icons/react';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import PageHeader from '../../components/PageHeader';
import InfoBanner from '../../components/InfoBanner';
import { InstanceSelect } from '../../components/InstanceSelect';
import { useLang } from '../../i18n/LangContext';
import type { DLQGroup, DLQMessage } from '../../api/message';
import {
  exportDLQExcel,
  listDLQGroups,
  listDLQMessages,
  resendDLQ,
  resendDLQSelected,
} from '../../services/messageService';
import { useInstanceFilter } from '../../hooks/useInstanceFilter';
import { buildCsv, downloadBlob, downloadCsv, type CsvColumn } from '../../utils/download';
import { tableScrollX } from '../../utils/table';

const { Text } = Typography;
const { RangePicker } = DatePicker;

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

export const formatDateTime = (value?: string | number | null): string => {
  if (value === undefined || value === null || value === '') return '-';
  const d = new Date(value);
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
  const [selectedGroupNames, setSelectedGroupNames] = useState<string[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [retryError, setRetryError] = useState<string | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailMessages, setDetailMessages] = useState<DLQMessage[]>([]);
  const [detailTotal, setDetailTotal] = useState(0);
  const [detailPage, setDetailPage] = useState(1);
  const [detailPageSize, setDetailPageSize] = useState(20);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailSelectedMsgIds, setDetailSelectedMsgIds] = useState<string[]>([]);
  const [detailResending, setDetailResending] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const detailRequestIdRef = useRef(0);
  const retryRequestIdRef = useRef(0);
  const groupRequestIdRef = useRef(0);
  const resendInFlightRef = useRef(false);

  useEffect(
    () => () => {
      retryRequestIdRef.current += 1;
      detailRequestIdRef.current += 1;
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
    const requestId = ++groupRequestIdRef.current;

    if (!selectedInstanceId) {
      void Promise.resolve().then(() => {
        if (groupRequestIdRef.current !== requestId) return;
        setLoading(false);
      });
      return;
    }

    // Clear `loading` inside the same callback as the data updates so rows and
    // the cleared spinner commit in one batched render — otherwise rows can be
    // visible for a render while the spin overlay still blocks pointer events.
    void listDLQGroups(selectedInstanceId, search || undefined, page, pageSize)
      .then((result) => {
        if (groupRequestIdRef.current === requestId) {
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
        if (groupRequestIdRef.current === requestId) {
          setLoadError(getErrorMessage(error, t('dlq.loadFailed')));
          setLoading(false);
        }
      });
  }, [refreshKey, selectedInstanceId, search, page, pageSize, t]);

  useEffect(
    () => () => {
      groupRequestIdRef.current += 1;
    },
    [],
  );

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

  const handleRetry = async () => {
    if (!retryTargetTopic) {
      message.warning(t('dlq.enterTargetTopic'));
      return;
    }
    if (!retryGroup || !selectedInstanceId) return;
    if (resendInFlightRef.current) return;

    resendInFlightRef.current = true;
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
          t('dlq.rescanIncomplete', {
            count: String(result.failedQueueCount ?? 0),
            resent: String(result.resent),
          }),
        );
      } else if (result.failed > 0) {
        message.warning(
          t('dlq.retryPartial', {
            succeeded: String(result.resent),
            failed: String(result.failed),
          }),
        );
      } else {
        message.success(
          t('dlq.retryComplete', {
            group: groupName,
            topic: targetTopic,
            count: String(result.resent),
          }),
        );
      }
      setRetryModalOpen(false);
      setRetryGroup(null);
      setRetryError(null);
    } catch (error) {
      if (retryRequestIdRef.current === requestId) {
        setRetryError(getErrorMessage(error, t('dlq.retrySubmitFailed')));
      }
    } finally {
      resendInFlightRef.current = false;
      if (retryRequestIdRef.current === requestId) {
        setRetrySubmitting(false);
      }
    }
  };

  const handleExport = async (group: DLQGroup) => {
    try {
      const { blob, meta } = await exportDLQExcel({
        instanceId: selectedInstanceId,
        groupName: group.groupName,
        startTime: exportRange[0].valueOf(),
        endTime: exportRange[1].valueOf(),
      });
      downloadBlob(blob, `${group.groupName}-dlq-messages.xlsx`);
      if (meta.truncated || meta.failedQueueCount > 0) {
        message.warning(
          t('dlq.exportIncomplete', {
            count: String(meta.failedQueueCount),
            limit: String(meta.limit),
          }),
        );
      } else {
        message.success(t('dlq.exportDone', { group: group.groupName, bytes: String(blob.size) }));
      }
    } catch (error) {
      message.error(getErrorMessage(error, t('dlq.exportFailed')));
    }
  };

  const handleBatchExport = () => {
    if (selectedGroups.length === 0) return;
    exportDLQGroups(selectedGroups, 'dlq-groups.csv');
  };

  /* ─── DLQ Message Details Drawer ─── */
  const openDetailDrawer = (group: DLQGroup) => {
    setDetailGroup(group);
    setDetailOpen(true);
    setDetailPage(1);
    setDetailSelectedMsgIds([]);
    setDetailError(null);
    void loadDetailMessages(group, 1, detailPageSize);
  };

  const loadDetailMessages = async (group: DLQGroup, page: number, pageSize: number) => {
    if (!selectedInstanceId) return;
    const requestId = detailRequestIdRef.current + 1;
    detailRequestIdRef.current = requestId;
    setDetailLoading(true);
    setDetailError(null);
    try {
      const result = await listDLQMessages({
        instanceId: selectedInstanceId,
        groupName: group.groupName,
        startTime: exportRange[0].valueOf(),
        endTime: exportRange[1].valueOf(),
        page,
        pageSize,
      });
      if (detailRequestIdRef.current !== requestId) return;
      setDetailMessages(result.items);
      setDetailTotal(result.total);
      setDetailPage(page);
    } catch (error) {
      if (detailRequestIdRef.current === requestId) {
        setDetailError(getErrorMessage(error, t('dlq.detailLoadFailed')));
      }
    } finally {
      if (detailRequestIdRef.current === requestId) {
        setDetailLoading(false);
      }
    }
  };

  const resendSelectedMessages = async (msgIds: string[]) => {
    if (!selectedInstanceId || !detailGroup || msgIds.length === 0) return;
    if (resendInFlightRef.current) return;
    resendInFlightRef.current = true;
    setDetailResending(true);
    setDetailError(null);
    try {
      const result = await resendDLQSelected({
        instanceId: selectedInstanceId,
        groupName: detailGroup.groupName,
        msgIds,
      });
      if (result.outcome === 'FAILED' && result.failed > 0) {
        message.error(
          t('dlq.resendFailedToast', {
            succeeded: String(result.resent),
            failed: String(result.failed),
          }),
        );
      } else if (result.resent > 0 && result.failed > 0) {
        message.warning(
          t('dlq.resendPartial', {
            succeeded: String(result.resent),
            failed: String(result.failed),
          }),
        );
      } else {
        message.success(t('dlq.resendComplete', { count: String(result.resent) }));
      }
      setDetailSelectedMsgIds([]);
      await loadDetailMessages(detailGroup, detailPage, detailPageSize);
    } catch (error) {
      setDetailError(getErrorMessage(error, t('dlq.resendFailed')));
    } finally {
      resendInFlightRef.current = false;
      setDetailResending(false);
    }
  };

  const exportDetailExcel = async () => {
    if (!selectedInstanceId || !detailGroup) return;
    try {
      const { blob, meta } = await exportDLQExcel({
        instanceId: selectedInstanceId,
        groupName: detailGroup.groupName,
        startTime: exportRange[0].valueOf(),
        endTime: exportRange[1].valueOf(),
        msgIds: detailSelectedMsgIds.length > 0 ? detailSelectedMsgIds : undefined,
      });
      downloadBlob(blob, `${detailGroup.groupName}-dlq-messages.xlsx`);
      if (meta.truncated || meta.failedQueueCount > 0) {
        message.warning(
          t('dlq.exportIncomplete', {
            count: String(meta.failedQueueCount),
            limit: String(meta.limit),
          }),
        );
      } else if (detailSelectedMsgIds.length > 0) {
        message.success(
          t('dlq.exportSelectedDone', {
            count: String(detailSelectedMsgIds.length),
            bytes: String(blob.size),
          }),
        );
      } else {
        message.success(t('dlq.exportAllDone', { bytes: String(blob.size) }));
      }
    } catch (error) {
      message.error(getErrorMessage(error, t('dlq.exportFailed')));
    }
  };

  /* ─── Table Columns ─── */
  const columns: ColumnsType<DLQGroup> = [
    {
      title: t('dlq.groupName'),
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
      title: t('dlq.deadCount'),
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
          {record.statsAvailable === false ? t('dlq.unavailable') : count.toLocaleString()}
        </Text>
      ),
    },
    {
      title: t('dlq.lastEnqueue'),
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
      title: t('common.actions'),
      key: 'actions',
      width: 280,
      render: (_: unknown, record: DLQGroup) => (
        <Flex gap={6}>
          <Button
            size="small"
            icon={<Eye size={14} />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={() => openDetailDrawer(record)}
          >
            {t('dlq.messageDetails')}
          </Button>
          <Button
            size="small"
            icon={<ArrowsCounterClockwise size={14} />}
            style={{ borderColor: '#fa8c16', color: '#fa8c16' }}
            onClick={() => openRetryModal(record)}
            disabled={record.statsAvailable === false || record.messageCount === 0}
          >
            {t('dlq.resendMessages')}
          </Button>
          <Button
            size="small"
            icon={<Download size={14} />}
            style={{ borderColor: '#52c41a', color: '#52c41a' }}
            onClick={() => handleExport(record)}
            disabled={record.statsAvailable === false || record.messageCount === 0}
          >
            {t('dlq.export')}
          </Button>
        </Flex>
      ),
    },
  ];

  /* ─── Detail Drawer Columns ─── */
  const detailColumns: ColumnsType<DLQMessage> = [
    {
      title: 'Message ID',
      dataIndex: 'msgId',
      key: 'msgId',
      width: 240,
      render: (msgId: string) => (
        <Text copyable style={{ fontFamily: 'monospace', fontSize: 14 }}>
          {msgId}
        </Text>
      ),
    },
    {
      title: 'Queue',
      key: 'queue',
      width: 90,
      render: (_: unknown, record: DLQMessage) => (
        <Text style={{ fontFamily: 'monospace' }}>{record.queueId}</Text>
      ),
    },
    {
      title: 'Offset',
      dataIndex: 'offset',
      key: 'offset',
      width: 90,
      render: (offset: number) => <Text style={{ fontFamily: 'monospace' }}>{offset}</Text>,
    },
    {
      title: t('dlq.enqueueTime'),
      dataIndex: 'storeTime',
      key: 'storeTime',
      width: 160,
      render: (storeTime: number) => (
        <Text style={{ fontFamily: 'monospace', fontSize: 14 }}>{formatDateTime(storeTime)}</Text>
      ),
    },
    {
      title: 'Keys',
      dataIndex: 'keys',
      key: 'keys',
      width: 140,
      ellipsis: true,
      render: (keys: string | null) => keys ?? '-',
    },
    {
      title: 'Body',
      dataIndex: 'body',
      key: 'body',
      width: 220,
      ellipsis: true,
      render: (body: string | null) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
          {body && body.length > 80 ? `${body.slice(0, 80)}…` : (body ?? '-')}
        </Text>
      ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 100,
      render: (_: unknown, record: DLQMessage) => (
        <Button
          size="small"
          icon={<ArrowsCounterClockwise size={13} />}
          loading={detailResending}
          onClick={() => void resendSelectedMessages([record.msgId])}
        >
          {t('dlq.resend')}
        </Button>
      ),
    },
  ];

  /* ═══════════════════════════════════════════
     Render
     ═══════════════════════════════════════════ */
  return (
    <div style={{ padding: 24 }}>
      <PageHeader title={t('dlq.title')} subtitle={t('dlq.subtitle')} />

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
            placeholder={t('dlq.searchPlaceholder')}
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
              {t('dlq.exportRange')}
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
            showTotal: (totalCount) => t('dlq.groupsTotal', { count: String(totalCount) }),
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
            <span>{t('dlq.retryTitle')}</span>
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
        okText={t('dlq.confirmRetry')}
        cancelText={t('common.cancel')}
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
                {t('dlq.retryNotice')}
              </Text>
            </div>

            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 4 }}>
                {t('dlq.sourceGroup')}
              </Text>
              <Text strong style={{ fontSize: 14 }}>
                {retryGroup.groupName}
              </Text>
            </div>

            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 4 }}>
                {t('dlq.deadCount')}
              </Text>
              <Text strong style={{ fontSize: 14, color: '#fa8c16' }}>
                {t('dlq.messages', { count: retryGroup.messageCount.toLocaleString() })}
              </Text>
            </div>

            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 8 }}>
                {t('dlq.retryRange')}
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
                {t('dlq.targetTopic')}
              </Text>
              <Input
                placeholder={t('dlq.targetTopicPlaceholder')}
                value={retryTargetTopic}
                onChange={(e) => setRetryTargetTopic(e.target.value)}
              />
            </div>
          </div>
        )}
      </Modal>

      {/* ═══════════════════════════════════════════
         Message Detail Drawer
         ═══════════════════════════════════════════ */}
      <Drawer
        title={
          detailGroup
            ? t('dlq.detailTitle', { group: detailGroup.groupName })
            : t('dlq.detailTitleShort')
        }
        width={1080}
        open={detailOpen}
        onClose={() => {
          detailRequestIdRef.current += 1;
          setDetailOpen(false);
          setDetailGroup(null);
          setDetailMessages([]);
          setDetailSelectedMsgIds([]);
          setDetailError(null);
        }}
        destroyOnHidden
      >
        {detailGroup && (
          <>
            <Flex
              justify="space-between"
              align="flex-start"
              wrap="wrap"
              gap={12}
              style={{ marginBottom: 16 }}
            >
              <Space size={24} wrap>
                <div>
                  <Text
                    type="secondary"
                    style={{ fontSize: 14, display: 'block', marginBottom: 4 }}
                  >
                    DLQ Topic
                  </Text>
                  <Text copyable style={{ fontFamily: 'monospace' }}>
                    {detailGroup.dlqTopic}
                  </Text>
                </div>
                <div>
                  <Text
                    type="secondary"
                    style={{ fontSize: 14, display: 'block', marginBottom: 4 }}
                  >
                    {t('dlq.deadCount')}
                  </Text>
                  <Text
                    strong
                    style={{ color: detailGroup.messageCount > 0 ? '#fa8c16' : undefined }}
                  >
                    {detailGroup.statsAvailable === false
                      ? t('dlq.unavailable')
                      : detailGroup.messageCount.toLocaleString()}
                  </Text>
                </div>
                <div>
                  <Text
                    type="secondary"
                    style={{ fontSize: 14, display: 'block', marginBottom: 4 }}
                  >
                    {t('dlq.lastEnqueue')}
                  </Text>
                  <Text style={{ fontFamily: 'monospace' }}>
                    {formatDateTime(detailGroup.lastEnqueueTime)}
                  </Text>
                </div>
              </Space>
              <Button
                icon={<Download size={15} />}
                disabled={detailTotal === 0}
                onClick={() => void exportDetailExcel()}
              >
                {detailSelectedMsgIds.length > 0
                  ? t('dlq.exportSelected', { count: String(detailSelectedMsgIds.length) })
                  : t('dlq.exportAll')}
              </Button>
            </Flex>

            <InfoBanner
              description={t('dlq.detailRangeNotice', {
                from: exportRange[0].format('YYYY-MM-DD HH:mm:ss'),
                to: exportRange[1].format('YYYY-MM-DD HH:mm:ss'),
              })}
            />

            {detailError && (
              <Alert showIcon type="warning" message={detailError} style={{ marginBottom: 16 }} />
            )}

            <Table<DLQMessage>
              rowKey="msgId"
              size="small"
              loading={detailLoading}
              dataSource={detailMessages}
              rowSelection={{
                selectedRowKeys: detailSelectedMsgIds,
                onChange: (keys) => setDetailSelectedMsgIds(keys.map(String)),
              }}
              pagination={{
                current: detailPage,
                pageSize: detailPageSize,
                total: detailTotal,
                showSizeChanger: true,
                pageSizeOptions: [10, 20, 50],
                showTotal: (totalCount) => t('dlq.messagesTotal', { count: String(totalCount) }),
                onChange: (nextPage, nextPageSize) => {
                  setDetailPage(nextPage);
                  setDetailPageSize(nextPageSize);
                  setDetailSelectedMsgIds([]);
                  if (detailGroup) {
                    void loadDetailMessages(detailGroup, nextPage, nextPageSize);
                  }
                },
              }}
              scroll={{ x: tableScrollX(detailColumns, { selection: true }) }}
              columns={detailColumns}
            />

            {detailSelectedMsgIds.length > 0 && (
              <Flex justify="flex-end" style={{ marginTop: 16 }}>
                <Button
                  type="primary"
                  icon={<ArrowsCounterClockwise size={15} />}
                  loading={detailResending}
                  onClick={() => void resendSelectedMessages(detailSelectedMsgIds)}
                >
                  {t('dlq.batchResend', { count: String(detailSelectedMsgIds.length) })}
                </Button>
              </Flex>
            )}
          </>
        )}
      </Drawer>
    </div>
  );
};

export default DLQPage;
