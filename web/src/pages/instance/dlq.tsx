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
import { useLang } from '../../i18n/LangContext';
import type { DLQGroup } from '../../api/message';
import { listDLQGroups, resendDLQ } from '../../services/messageService';

const { Text } = Typography;
const { RangePicker } = DatePicker;

/* ─── Helpers ─── */

const formatDateTime = (iso: string): string => {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

/* ═══════════════════════════════════════════
   DLQPage
   ═══════════════════════════════════════════ */
const DLQPage = () => {
  const { t } = useLang();
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

  useEffect(() => {
    let cancelled = false;

    void listDLQGroups()
      .then((nextGroups) => {
        if (!cancelled) setGroups(nextGroups);
      })
      .catch(() => {
        if (!cancelled) message.error('死信队列加载失败，请稍后重试');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  /* ─── Filtering ─── */
  const filtered = useMemo(() => {
    if (!search) return groups;
    return groups.filter(
      (g) =>
        g.groupName.includes(search) || g.dlqTopic.toLowerCase().includes(search.toLowerCase()),
    );
  }, [groups, search]);

  /* ─── Handlers ─── */
  const openRetryModal = (group: DLQGroup) => {
    setRetryGroup(group);
    setRetryRange([dayjs().subtract(1, 'day'), dayjs()]);
    setRetryTargetTopic('');
    setRetryModalOpen(true);
  };

  const handleRetry = async () => {
    if (!retryTargetTopic) {
      message.warning('请输入目标 Topic');
      return;
    }
    if (!retryGroup) return;

    setRetrySubmitting(true);
    try {
      await resendDLQ({
        groupName: retryGroup.groupName,
        startTime: retryRange[0].valueOf(),
        endTime: retryRange[1].valueOf(),
        targetTopic: retryTargetTopic,
      });
      setRefreshKey((key) => key + 1);
      message.success(`已提交重投任务：${retryGroup.groupName} → ${retryTargetTopic}`);
      setRetryModalOpen(false);
      setRetryGroup(null);
    } catch {
      message.error('提交重投任务失败，请稍后重试');
    } finally {
      setRetrySubmitting(false);
    }
  };

  const handleExport = (group: DLQGroup) => {
    message.success(`已导出 ${group.groupName} 的死信消息（模拟）`);
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
      sorter: (a, b) => a.lastEnqueueTime.localeCompare(b.lastEnqueueTime),
      render: (time: string) => (
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
            onClick={() => message.info(`查看 ${record.groupName} 详情（模拟）`)}
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
        <Input.Search
          placeholder="搜索 Group 名称或 DLQ Topic"
          allowClear
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onSearch={setSearch}
          style={{ width: 320 }}
          prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
        />
      </Flex>

      {/* ── Table ── */}
      <Card bodyStyle={{ padding: 0 }}>
        <Table
          columns={columns}
          dataSource={filtered}
          rowKey="groupName"
          loading={loading}
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
    </div>
  );
};

export default DLQPage;
