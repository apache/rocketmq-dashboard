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

import { useEffect, useMemo, useState } from 'react';
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
  Select,
  message,
} from 'antd';
import { MagnifyingGlass, Eye, ArrowsCounterClockwise, Download } from '@phosphor-icons/react';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import type { DLQGroup } from '../../api/message';
import { listDLQGroups, resendDLQ } from '../../services/messageService';
import { useInstanceFilter } from '../../hooks/useInstanceFilter';
import { downloadBlob } from '../../utils/download';

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

const escapeCSVValue = (value: string) => {
  const safeValue = /^[=+\-@\t\r]/.test(value) ? `'${value}` : value;
  return `"${safeValue.replace(/"/g, '""')}"`;
};

const exportDLQGroups = (groups: DLQGroup[], filename: string) => {
  const rows = [
    ['Group Name', 'DLQ Topic', 'Message Count', 'Retry Count', 'Status', 'Last Enqueue Time'],
    ...groups.map((group) => [
      group.groupName,
      group.dlqTopic,
      String(group.messageCount),
      String(group.retryCount),
      group.status,
      group.lastEnqueueTime || '',
    ]),
  ];
  const csv = rows.map((row) => row.map(escapeCSVValue).join(',')).join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  downloadBlob(blob, filename);
};

/* ═══════════════════════════════════════════
   DLQPage
   ═══════════════════════════════════════════ */
const DLQPage = () => {
  const { t } = useLang();
  const { selectedInstanceId, selectInstance, instanceOptions } = useInstanceFilter();
  const [groups, setGroups] = useState<DLQGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);
  const [search, setSearch] = useState('');
  const [retryModalOpen, setRetryModalOpen] = useState(false);
  const [retryGroup, setRetryGroup] = useState<DLQGroup | null>(null);
  const [retryRange, setRetryRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(1, 'day'),
    dayjs(),
  ]);
  const [retryTargetTopic, setRetryTargetTopic] = useState('');
  const [retrySubmitting, setRetrySubmitting] = useState(false);
  const [detailGroup, setDetailGroup] = useState<DLQGroup | null>(null);
  const [selectedGroupNames, setSelectedGroupNames] = useState<string[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [retryError, setRetryError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    if (!selectedInstanceId) {
      void Promise.resolve().then(() => {
        if (cancelled) return;
        setGroups([]);
        setSelectedGroupNames([]);
        setLoadError(null);
        setLoading(false);
      });
      return () => {
        cancelled = true;
      };
    }

    void listDLQGroups(selectedInstanceId)
      .then((nextGroups) => {
        if (!cancelled) {
          setGroups(nextGroups);
          setLoadError(null);
          const availableGroups = new Set(
            nextGroups.filter((group) => group.messageCount > 0).map((group) => group.groupName),
          );
          setSelectedGroupNames((selected) =>
            selected.filter((groupName) => availableGroups.has(groupName)),
          );
        }
      })
      .catch((error) => {
        if (!cancelled) setLoadError(getErrorMessage(error, DEFAULT_LOAD_ERROR));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [refreshKey, selectedInstanceId]);

  /* ─── Filtering ─── */
  const filtered = useMemo(() => {
    if (!search) return groups;
    return groups.filter(
      (g) =>
        g.groupName.includes(search) || g.dlqTopic.toLowerCase().includes(search.toLowerCase()),
    );
  }, [groups, search]);

  const selectedGroups = useMemo(() => {
    const selected = new Set(selectedGroupNames);
    return groups.filter((group) => selected.has(group.groupName));
  }, [groups, selectedGroupNames]);

  /* ─── Handlers ─── */
  const openRetryModal = (group: DLQGroup) => {
    setRetryGroup(group);
    setRetryRange([dayjs().subtract(1, 'day'), dayjs()]);
    setRetryTargetTopic('');
    setRetryError(null);
    setRetryModalOpen(true);
  };

  const handleRetry = async () => {
    if (!retryTargetTopic) {
      message.warning('请输入目标 Topic');
      return;
    }
    if (!retryGroup || !selectedInstanceId) return;

    setRetrySubmitting(true);
    setRetryError(null);
    try {
      const result = await resendDLQ({
        instanceId: selectedInstanceId,
        groupName: retryGroup.groupName,
        startTime: retryRange[0].valueOf(),
        endTime: retryRange[1].valueOf(),
        targetTopic: retryTargetTopic,
      });
      setRefreshKey((key) => key + 1);
      if (result.failed > 0) {
        message.warning(`重投部分完成：成功 ${result.resent}，失败 ${result.failed}`);
      } else {
        message.success(
          `重投完成：${retryGroup.groupName} → ${retryTargetTopic}（${result.resent} 条）`,
        );
      }
      setRetryModalOpen(false);
      setRetryGroup(null);
      setRetryError(null);
    } catch (error) {
      setRetryError(getErrorMessage(error, DEFAULT_RETRY_ERROR));
    } finally {
      setRetrySubmitting(false);
    }
  };

  const handleExport = (group: DLQGroup) => {
    exportDLQGroups([group], `${group.groupName}-dlq.csv`);
    message.success(`已导出 ${group.groupName} 的死信队列摘要`);
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
        <Text style={{ fontSize: 13, fontFamily: 'monospace' }}>{topic}</Text>
      ),
    },
    {
      title: '死信数量',
      dataIndex: 'messageCount',
      key: 'messageCount',
      width: 100,
      align: 'right',
      sorter: (a, b) => a.messageCount - b.messageCount,
      render: (count: number) => (
        <Text
          style={{
            fontFamily: 'monospace',
            fontWeight: 600,
            color: count > 50 ? '#ff4d4f' : count > 0 ? '#fa8c16' : undefined,
          }}
        >
          {count.toLocaleString()}
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
        <Text type="secondary" style={{ fontSize: 13 }}>
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
            onClick={() => setDetailGroup(record)}
          >
            查看详情
          </Button>
          <Button
            size="small"
            icon={<ArrowsCounterClockwise size={14} />}
            style={{ borderColor: '#fa8c16', color: '#fa8c16' }}
            onClick={() => openRetryModal(record)}
            disabled={record.messageCount === 0}
          >
            重投消息
          </Button>
          <Button
            size="small"
            icon={<Download size={14} />}
            style={{ borderColor: '#52c41a', color: '#52c41a' }}
            onClick={() => handleExport(record)}
            disabled={record.messageCount === 0}
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
          <Select
            placeholder="选择实例"
            value={selectedInstanceId || undefined}
            onChange={selectInstance}
            options={instanceOptions}
            style={{ width: 220 }}
            notFoundContent="暂无实例"
          />
          <Input.Search
            placeholder="搜索 Group 名称或 DLQ Topic"
            allowClear
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={setSearch}
            style={{ width: 320 }}
            prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
          />
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
      <Card bodyStyle={{ padding: 0 }}>
        <Table
          columns={columns}
          dataSource={filtered}
          rowKey="groupName"
          loading={loading}
          rowSelection={{
            selectedRowKeys: selectedGroupNames,
            preserveSelectedRowKeys: true,
            onChange: (keys) => setSelectedGroupNames(keys.map(String)),
            getCheckboxProps: (record) => ({ disabled: record.messageCount === 0 }),
          }}
          pagination={{
            pageSize: 20,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 个 Group`,
          }}
          size="small"
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
        destroyOnClose
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
              <Text type="warning" style={{ fontSize: 13 }}>
                ⚠️ 重投操作将把死信消息重新发送到指定 Topic，请确认目标 Topic 正确。
              </Text>
            </div>

            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 4 }}>
                源 Group
              </Text>
              <Text strong style={{ fontSize: 14 }}>
                {retryGroup.groupName}
              </Text>
            </div>

            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 4 }}>
                死信数量
              </Text>
              <Text strong style={{ fontSize: 14, color: '#fa8c16' }}>
                {retryGroup.messageCount.toLocaleString()} 条
              </Text>
            </div>

            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 8 }}>
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
              <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 8 }}>
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
        footer={<Button onClick={() => setDetailGroup(null)}>关闭</Button>}
        width={560}
        destroyOnClose
      >
        {detailGroup && (
          <div style={{ marginTop: 8 }}>
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 4 }}>
                Group 名称
              </Text>
              <Text strong copyable style={{ fontSize: 14, fontFamily: 'monospace' }}>
                {detailGroup.groupName}
              </Text>
            </div>
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 4 }}>
                DLQ Topic
              </Text>
              <Text copyable style={{ fontSize: 14, fontFamily: 'monospace' }}>
                {detailGroup.dlqTopic}
              </Text>
            </div>
            <Flex gap={24} wrap="wrap">
              <div>
                <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 4 }}>
                  死信数量
                </Text>
                <Text
                  strong
                  style={{ color: detailGroup.messageCount > 0 ? '#fa8c16' : undefined }}
                >
                  {detailGroup.messageCount.toLocaleString()}
                </Text>
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 4 }}>
                  重试次数
                </Text>
                <Text>{detailGroup.retryCount.toLocaleString()}</Text>
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 4 }}>
                  状态
                </Text>
                <Text>{detailGroup.status}</Text>
              </div>
            </Flex>
            <div style={{ marginTop: 16 }}>
              <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 4 }}>
                最近入队时间
              </Text>
              <Text style={{ fontFamily: 'monospace' }}>
                {formatDateTime(detailGroup.lastEnqueueTime)}
              </Text>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default DLQPage;
