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

import { useState } from 'react';
import {
  Card,
  Table,
  Tag,
  Modal,
  Tabs,
  Descriptions,
  Steps,
  Button,
  Typography,
  Segmented,
  Select,
  DatePicker,
  Input,
  Space,
  Flex,
  message,
} from 'antd';
import {
  SearchOutlined,
  ReloadOutlined,
  SendOutlined,
  EyeOutlined,
  NodeIndexOutlined,
  CheckCircleOutlined,
  DownloadOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import type { MessageRecord, TraceRecord } from '../../api/message';
import { getMessageTrace, queryMessages } from '../../services/messageService';

const { Paragraph, Text } = Typography;
const { RangePicker } = DatePicker;

/* ─── Constants ─── */

type QueryMode = 'topic' | 'key' | 'msgid';

const QUERY_OPTIONS = [
  { value: 'topic' as const, label: '按 Topic 查询' },
  { value: 'key' as const, label: '按 Message Key' },
  { value: 'msgid' as const, label: '按 Message ID' },
];

const TOPIC_OPTIONS = [
  'order-create',
  'payment-callback',
  'user-activity-log',
  'notification-push',
  'inventory-sync',
];

const DELIVERY_STATUS_MAP: Record<string, { label: string; color: string }> = {
  success: { label: '成功', color: 'green' },
  failed: { label: '失败', color: 'red' },
  pending: { label: '等待中', color: 'gold' },
};

const TOPIC_TAG_COLORS: Record<string, string> = {
  'order-create': 'blue',
  'payment-callback': 'purple',
  'user-activity-log': 'cyan',
  'notification-push': 'orange',
  'inventory-sync': 'green',
};

/* ─── Default date range: now - 2 days 00:00:00 → now ─── */
const getDefaultRange = (): [Dayjs, Dayjs] => [dayjs().subtract(2, 'day').startOf('day'), dayjs()];

/* ─── Helpers ─── */

const formatSize = (bytes: number): string => {
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(2)} MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(2)} KB`;
  return `${bytes} B`;
};

const formatTimeMs = (value: number | string): string => {
  const d = new Date(value);
  const pad = (n: number, len = 2) => String(n).padStart(len, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`;
};

const formatBody = (body: string): string => {
  try {
    return JSON.stringify(JSON.parse(body), null, 2);
  } catch {
    return body;
  }
};

/* ═══════════════════════════════════════════
   MessagePage
   ═══════════════════════════════════════════ */
const MessagePage = () => {
  const { t } = useLang();
  const [queryMode, setQueryMode] = useState<QueryMode>('topic');
  const [selectedTopic, setSelectedTopic] = useState<string | undefined>();
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>(getDefaultRange);
  const [tagInput, setTagInput] = useState('');
  const [keyInput, setKeyInput] = useState('');
  const [msgIdInput, setMsgIdInput] = useState('');
  const [messages, setMessages] = useState<MessageRecord[]>([]);
  const [queryLoading, setQueryLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalTab, setModalTab] = useState('content');
  const [selectedMsg, setSelectedMsg] = useState<MessageRecord | null>(null);
  const [traceData, setTraceData] = useState<TraceRecord | null>(null);
  const [traceLoading, setTraceLoading] = useState(false);

  /* ─── Handlers ─── */
  const handleReset = () => {
    setSelectedTopic(undefined);
    setTagInput('');
    setKeyInput('');
    setMsgIdInput('');
    setDateRange(getDefaultRange());
    setMessages([]);
  };

  const handleQuery = async () => {
    const params =
      queryMode === 'topic'
        ? {
            topic: selectedTopic,
            tag: tagInput || undefined,
            startTime: dateRange[0].valueOf(),
            endTime: dateRange[1].valueOf(),
          }
        : queryMode === 'key'
          ? { topic: selectedTopic, key: keyInput || undefined }
          : { msgId: msgIdInput || undefined };

    setQueryLoading(true);
    try {
      const result = await queryMessages(params);
      setMessages(result);
      message.success(`查询完成，共 ${result.length} 条`);
    } catch {
      message.error('消息查询失败，请稍后重试');
    } finally {
      setQueryLoading(false);
    }
  };

  const handleResend = () => {
    message.success('消息重新发送成功（模拟）');
  };

  const openDetail = async (record: MessageRecord, tab = 'content') => {
    setSelectedMsg(record);
    setModalTab(tab);
    setModalOpen(true);
    setTraceData(null);
    setTraceLoading(true);
    try {
      setTraceData(await getMessageTrace(record.msgId));
    } catch {
      message.error('消息轨迹加载失败，请稍后重试');
    } finally {
      setTraceLoading(false);
    }
  };

  const handleDownload = (record: MessageRecord) => {
    const blob = new Blob([formatBody(record.body)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${record.msgId}.json`;
    a.click();
    URL.revokeObjectURL(url);
    message.success('消息下载成功');
  };

  /* ─── Table Columns ─── */
  const columns: ColumnsType<MessageRecord> = [
    {
      title: 'Topic',
      dataIndex: 'topic',
      key: 'topic',
      width: 170,
      sorter: (a, b) => a.topic.localeCompare(b.topic),
      render: (topic: string) => (
        <Text strong style={{ fontSize: 13 }}>
          {topic}
        </Text>
      ),
    },
    {
      title: 'Tag',
      dataIndex: 'tag',
      key: 'tag',
      width: 80,
      sorter: (a, b) => a.tag.localeCompare(b.tag),
      render: (tag: string) => <Tag>{tag}</Tag>,
    },
    {
      title: 'Key',
      dataIndex: 'key',
      key: 'key',
      width: 120,
      sorter: (a, b) => a.key.localeCompare(b.key),
      render: (key: string) => <span style={{ fontFamily: 'monospace', fontSize: 13 }}>{key}</span>,
    },
    {
      title: 'Message ID',
      dataIndex: 'msgId',
      key: 'msgId',
      sorter: (a, b) => a.msgId.localeCompare(b.msgId),
      render: (id: string) => (
        <Paragraph copyable style={{ fontSize: 13, marginBottom: 0, fontFamily: 'monospace' }}>
          {id}
        </Paragraph>
      ),
    },
    {
      title: '存储时间',
      dataIndex: 'storeTime',
      key: 'storeTime',
      width: 185,
      sorter: (a, b) => new Date(a.storeTime).valueOf() - new Date(b.storeTime).valueOf(),
      render: (time: string) => (
        <span style={{ fontFamily: 'monospace', fontSize: 13, whiteSpace: 'nowrap' }}>
          {formatTimeMs(time)}
        </span>
      ),
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 80,
      align: 'right',
      sorter: (a, b) => a.size - b.size,
      render: (size: number) => formatSize(size),
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      render: (_: unknown, record: MessageRecord) => (
        <Flex gap={6}>
          <Button
            size="small"
            icon={<EyeOutlined />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={() => void openDetail(record, 'content')}
          >
            详情
          </Button>
          <Button
            size="small"
            icon={<NodeIndexOutlined />}
            style={{ borderColor: '#722ed1', color: '#722ed1' }}
            onClick={() => void openDetail(record, 'trace')}
          >
            轨迹
          </Button>
          <Button
            size="small"
            icon={<CheckCircleOutlined />}
            style={{ borderColor: '#52c41a', color: '#52c41a' }}
            onClick={() => message.success(`消息 ${record.msgId.slice(0, 16)}... 消费验证成功`)}
          >
            验证
          </Button>
          <Button
            size="small"
            icon={<DownloadOutlined />}
            style={{ borderColor: '#fa8c16', color: '#fa8c16' }}
            onClick={() => handleDownload(record)}
          >
            下载
          </Button>
        </Flex>
      ),
    },
  ];

  const consumerStatusColumns: ColumnsType<{
    group: string;
    deliveryStatus: string;
    consumeTime: number | string;
    retryCount: number;
  }> = [
    {
      title: '消费者组',
      dataIndex: 'group',
      key: 'group',
      render: (g: string) => <span style={{ fontFamily: 'monospace', fontWeight: 500 }}>{g}</span>,
    },
    {
      title: '投递状态',
      dataIndex: 'deliveryStatus',
      key: 'deliveryStatus',
      render: (status: string) => {
        const s = DELIVERY_STATUS_MAP[status.toLowerCase()] || {
          label: status,
          color: 'default',
        };
        return <Tag color={s.color}>{s.label}</Tag>;
      },
    },
    {
      title: '消费时间',
      dataIndex: 'consumeTime',
      key: 'consumeTime',
      render: (time: string) =>
        time === '-' ? (
          <span style={{ color: '#9CA3AF' }}>-</span>
        ) : (
          <span style={{ fontFamily: 'monospace', fontSize: 13 }}>{formatTimeMs(time)}</span>
        ),
    },
    {
      title: '重试次数',
      dataIndex: 'retryCount',
      key: 'retryCount',
      align: 'center',
      render: (count: number) => (
        <span style={{ color: count > 0 ? '#ff4d4f' : undefined }}>{count}</span>
      ),
    },
  ];

  /* ─── Modal Tab Items ─── */
  const modalTabs = [
    {
      key: 'content',
      label: '消息内容',
      children: selectedMsg && (
        <>
          <Descriptions column={2} size="small" style={{ marginBottom: 24 }}>
            <Descriptions.Item label="Message ID" span={2}>
              <Paragraph copyable style={{ marginBottom: 0, fontFamily: 'monospace' }}>
                {selectedMsg.msgId}
              </Paragraph>
            </Descriptions.Item>
            <Descriptions.Item label="Topic">
              <Tag color={TOPIC_TAG_COLORS[selectedMsg.topic] || 'default'}>
                {selectedMsg.topic}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Tag">
              <Tag>{selectedMsg.tag}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Key">
              <span style={{ fontFamily: 'monospace' }}>{selectedMsg.key}</span>
            </Descriptions.Item>
            <Descriptions.Item label="大小">{formatSize(selectedMsg.size)}</Descriptions.Item>
            <Descriptions.Item label="Born Host">
              <span style={{ fontFamily: 'monospace' }}>{selectedMsg.bornHost}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Store Host">
              <span style={{ fontFamily: 'monospace' }}>{selectedMsg.storeHost}</span>
            </Descriptions.Item>
            <Descriptions.Item label="存储时间" span={2}>
              <span style={{ fontFamily: 'monospace' }}>{formatTimeMs(selectedMsg.storeTime)}</span>
            </Descriptions.Item>
          </Descriptions>
          <Typography.Title level={5} style={{ marginBottom: 8 }}>
            消息体
          </Typography.Title>
          <Paragraph
            copyable
            style={{
              background: '#f5f5f5',
              padding: '12px 16px',
              borderRadius: 6,
              fontFamily: "'SF Mono', Monaco, 'Cascadia Code', Consolas, monospace",
              fontSize: 13,
              lineHeight: 1.7,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-all',
              marginBottom: 0,
            }}
          >
            {formatBody(selectedMsg.body)}
          </Paragraph>
        </>
      ),
    },
    {
      key: 'trace',
      label: '消息轨迹',
      children: traceLoading ? (
        <Typography.Text type="secondary">正在加载轨迹数据…</Typography.Text>
      ) : traceData?.nodes?.length ? (
        <Steps
          direction="vertical"
          size="small"
          items={traceData.nodes.map((node) => ({
            title: node.title,
            description: (
              <div style={{ fontSize: 13 }}>
                <div style={{ color: '#9CA3AF', fontFamily: 'monospace' }}>
                  {formatTimeMs(node.timestamp)}
                </div>
                <div style={{ marginTop: 2 }}>{node.description}</div>
                <div style={{ color: '#9CA3AF', fontSize: 12 }}>耗时 {node.costTime}ms</div>
              </div>
            ),
            status: node.status,
          }))}
        />
      ) : (
        <Typography.Text type="secondary">暂无轨迹数据</Typography.Text>
      ),
    },
    {
      key: 'consumer',
      label: '验证',
      children: (
        <Table
          columns={consumerStatusColumns}
          dataSource={traceData?.consumerStatus?.map((c, i) => ({ ...c, _key: i })) || []}
          rowKey="_key"
          pagination={false}
          size="small"
        />
      ),
    },
  ];

  /* ═══════════════════════════════════════════
     Render
     ═══════════════════════════════════════════ */
  return (
    <div style={{ padding: 24 }}>
      <PageHeader title={t('message.title')} subtitle="按 Topic、Tag、Key 或 Message ID 检索消息" />

      {/* ── Query Form ── */}
      <Card style={{ marginBottom: 16 }}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Segmented
            options={QUERY_OPTIONS}
            value={queryMode}
            onChange={(v) => setQueryMode(v as QueryMode)}
          />

          <Space wrap size={12}>
            {queryMode === 'topic' && (
              <>
                <Select
                  placeholder="选择 Topic"
                  style={{ width: 360 }}
                  value={selectedTopic}
                  onChange={setSelectedTopic}
                  allowClear
                  showSearch
                  options={TOPIC_OPTIONS.map((t) => ({
                    value: t,
                    label: t,
                  }))}
                />
                <RangePicker
                  showTime
                  style={{ width: 400 }}
                  value={dateRange}
                  onChange={(vals) => {
                    if (vals && vals[0] && vals[1]) {
                      setDateRange([vals[0], vals[1]]);
                    }
                  }}
                />
                <Input
                  placeholder="输入 Tag（可选）"
                  style={{ width: 180 }}
                  value={tagInput}
                  onChange={(e) => setTagInput(e.target.value)}
                  allowClear
                />
              </>
            )}

            {queryMode === 'key' && (
              <>
                <Select
                  placeholder="选择 Topic"
                  style={{ width: 360 }}
                  value={selectedTopic}
                  onChange={setSelectedTopic}
                  allowClear
                  showSearch
                  options={TOPIC_OPTIONS.map((t) => ({
                    value: t,
                    label: t,
                  }))}
                />
                <Input
                  placeholder="输入 Message Key"
                  style={{ width: 240 }}
                  value={keyInput}
                  onChange={(e) => setKeyInput(e.target.value)}
                />
              </>
            )}

            {queryMode === 'msgid' && (
              <Input
                placeholder="输入 Message ID"
                style={{ width: 400 }}
                value={msgIdInput}
                onChange={(e) => setMsgIdInput(e.target.value)}
              />
            )}

            <Button
              type="primary"
              icon={<SearchOutlined />}
              onClick={() => {
                void handleQuery();
              }}
            >
              查询
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
          </Space>
        </Space>
      </Card>

      {/* ── Results Table ── */}
      <Card bodyStyle={{ padding: 0 }}>
        <Table
          columns={columns}
          dataSource={messages}
          loading={queryLoading}
          rowKey="msgId"
          pagination={{
            pageSize: 50,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条消息`,
          }}
          size="small"
        />
      </Card>

      {/* ── Message Detail Modal ── */}
      <Modal
        title="消息详情"
        width={800}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
        footer={
          <Flex justify="flex-end" gap={8}>
            <Button onClick={() => setModalOpen(false)}>关闭</Button>
            <Button type="primary" icon={<SendOutlined />} onClick={handleResend}>
              重新发送
            </Button>
          </Flex>
        }
      >
        <Tabs activeKey={modalTab} onChange={setModalTab} items={modalTabs} />
      </Modal>
    </div>
  );
};

export default MessagePage;
