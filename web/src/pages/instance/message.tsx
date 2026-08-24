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

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
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
  HistoryOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import PageHeader from '../../components/PageHeader';
import { InstanceSelect } from '../../components/InstanceSelect';
import MessageQueryHistoryDrawer from '../../components/MessageQueryHistoryDrawer';
import {
  useQueueBrowser,
  QueueBrowserControls,
  QueueBrowserResults,
} from '../../components/QueueBrowser';
import type { MessageQueryHistory, TraceQueryHistory } from '../../api/messageHistory';
import { useLang } from '../../i18n/LangContext';
import type { MessageQuery, MessageRecord, TraceRecord } from '../../api/message';
import { getMessageTrace, queryMessagePage } from '../../services/messageService';
import { listTopics } from '../../services/topicService';
import { useInstanceFilter } from '../../hooks/useInstanceFilter';
import { downloadBlob } from '../../utils/download';
import { tableScrollX } from '../../utils/table';

const { Paragraph, Text } = Typography;
const { RangePicker } = DatePicker;
const DEFAULT_QUERY_ERROR = '消息查询失败，请稍后重试';
const DEFAULT_TRACE_ERROR = '消息轨迹加载失败，请稍后重试';

/* ─── Constants ─── */

type QueryMode = 'topic' | 'key' | 'msgid' | 'queue';

type ApiErrorLike = {
  message?: unknown;
  response?: {
    data?: {
      message?: unknown;
    };
  };
};

const RESEND_UNAVAILABLE_MESSAGE = '当前版本尚未接入普通消息重新发送接口';

const QUERY_OPTIONS = [
  { value: 'topic' as const, label: '按 Topic 查询' },
  { value: 'key' as const, label: '按 Message Key' },
  { value: 'msgid' as const, label: '按 Message ID' },
  { value: 'queue' as const, label: '按队列浏览' },
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
  if (!value) return '-';
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

const getQueryValidationError = (mode: QueryMode, params: MessageQuery): string | null => {
  if (!params.topic?.trim()) return '请选择 Topic';
  if (mode === 'key' && !params.key?.trim()) return '请输入 Message Key';
  if (mode === 'msgid' && !params.msgId?.trim()) return '请输入 Message ID';
  return null;
};

const normalizedText = (value: string | undefined): string | undefined =>
  value?.trim() || undefined;

const normalizeMessageQuery = (mode: QueryMode, params: MessageQuery): MessageQuery => {
  const topic = normalizedText(params.topic);
  if (mode === 'msgid') {
    const msgId = normalizedText(params.msgId);
    return {
      ...(topic ? { topic } : {}),
      ...(msgId ? { msgId } : {}),
    };
  }

  const tag = normalizedText(params.tag);
  const commonParams = {
    ...(topic ? { topic } : {}),
    ...(tag ? { tag } : {}),
    ...(params.startTime !== undefined ? { startTime: params.startTime } : {}),
    ...(params.endTime !== undefined ? { endTime: params.endTime } : {}),
  };
  if (mode === 'key') {
    const key = normalizedText(params.key);
    return { ...commonParams, ...(key ? { key } : {}) };
  }
  return commonParams;
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

/* ═══════════════════════════════════════════
   MessagePage
   ═══════════════════════════════════════════ */
type InstanceFilterProps = {
  selectedInstanceId: string | undefined;
  selectInstance: (instanceId: string) => void;
  instanceOptions: { value: string; label: string }[];
};

const MessagePage = () => {
  const { selectedInstanceId, selectInstance, instanceOptions } = useInstanceFilter();
  // Keying the content by the selected instance makes React remount it whenever the instance
  // changes — whether from this page's own <Select> or from the shared filter/route elsewhere —
  // so query results, the detail modal and in-flight request ownership all reset cleanly.
  return (
    <MessagePageContent
      key={selectedInstanceId || 'no-instance'}
      selectedInstanceId={selectedInstanceId}
      selectInstance={selectInstance}
      instanceOptions={instanceOptions}
    />
  );
};

/* ═══════════════════════════════════════════
   MessagePageContent
   ═══════════════════════════════════════════ */
const MessagePageContent = ({
  selectedInstanceId,
  selectInstance,
  instanceOptions,
}: InstanceFilterProps) => {
  const { t } = useLang();
  const [topicOptions, setTopicOptions] = useState<string[]>([]);
  const [topicError, setTopicError] = useState<string | null>(null);
  const [topicLoading, setTopicLoading] = useState(false);
  const topicRequestId = useRef(0);

  const loadTopicOptions = useCallback(async () => {
    if (!selectedInstanceId) {
      setTopicOptions([]);
      setTopicError(null);
      setTopicLoading(false);
      return;
    }
    const requestId = ++topicRequestId.current;
    setTopicLoading(true);
    setTopicError(null);
    setTopicOptions([]);
    try {
      const nextTopics = await listTopics({ instanceId: selectedInstanceId });
      if (requestId !== topicRequestId.current) return;
      setTopicOptions(nextTopics.map((topic) => topic.name));
    } catch (error: unknown) {
      if (requestId !== topicRequestId.current) return;
      setTopicError(error instanceof Error ? error.message : '加载 Topic 列表失败');
    } finally {
      if (requestId === topicRequestId.current) setTopicLoading(false);
    }
  }, [selectedInstanceId]);

  useEffect(() => {
    void Promise.resolve().then(loadTopicOptions);
    return () => {
      topicRequestId.current += 1;
    };
  }, [loadTopicOptions]);
  const [queryMode, setQueryMode] = useState<QueryMode>('topic');
  const queueBrowser = useQueueBrowser(selectedInstanceId);
  const [selectedTopic, setSelectedTopic] = useState<string | undefined>();
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>(getDefaultRange);
  const [keyInput, setKeyInput] = useState('');
  const [msgIdInput, setMsgIdInput] = useState('');
  const [messages, setMessages] = useState<MessageRecord[]>([]);
  const [messageTotal, setMessageTotal] = useState(0);
  const [messagePage, setMessagePage] = useState(1);
  const [messagePageSize, setMessagePageSize] = useState(50);
  const [resultMayBeTruncated, setResultMayBeTruncated] = useState(false);
  const [queryLoading, setQueryLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalTab, setModalTab] = useState('content');
  const [selectedMsg, setSelectedMsg] = useState<MessageRecord | null>(null);
  const [traceData, setTraceData] = useState<TraceRecord | null>(null);
  const [traceLoading, setTraceLoading] = useState(false);
  const [queryError, setQueryError] = useState<string | null>(null);
  const [traceError, setTraceError] = useState<string | null>(null);
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const queryGenerationRef = useRef(0);
  const traceGenerationRef = useRef(0);

  useEffect(
    () => () => {
      queryGenerationRef.current += 1;
      traceGenerationRef.current += 1;
    },
    [],
  );

  const currentQueryParams: MessageQuery =
    queryMode === 'topic'
      ? { topic: selectedTopic, startTime: dateRange[0].valueOf(), endTime: dateRange[1].valueOf() }
      : queryMode === 'key'
        ? { topic: selectedTopic, key: keyInput || undefined }
        : { topic: selectedTopic, msgId: msgIdInput || undefined };
  const queryValidationError = getQueryValidationError(queryMode, currentQueryParams);
  const queryDisabledReason = !selectedInstanceId
    ? '请先选择实例'
    : topicLoading
      ? '正在加载 Topic 列表'
      : topicError
        ? 'Topic 列表加载失败，请先重试'
        : queryValidationError;

  /* ─── Handlers ─── */
  const handleReset = () => {
    queryGenerationRef.current += 1;
    setSelectedTopic(undefined);
    setKeyInput('');
    setMsgIdInput('');
    setDateRange(getDefaultRange());
    setMessages([]);
    setQueryError(null);
    setQueryLoading(false);
  };

  const executeQuery = async (
    mode: QueryMode,
    params: MessageQuery,
    page = 1,
    pageSize = messagePageSize,
  ) => {
    const requestGeneration = queryGenerationRef.current + 1;
    queryGenerationRef.current = requestGeneration;
    if (!selectedInstanceId) {
      setQueryError('请先选择实例后再查询消息');
      setQueryLoading(false);
      return;
    }
    const normalizedParams = normalizeMessageQuery(mode, params);
    const validationError = getQueryValidationError(mode, normalizedParams);
    if (validationError) {
      setQueryError(validationError);
      setQueryLoading(false);
      return;
    }
    setQueryLoading(true);
    setQueryError(null);
    try {
      const result = await queryMessagePage({
        ...normalizedParams,
        instanceId: selectedInstanceId,
        page,
        pageSize,
      });
      if (queryGenerationRef.current !== requestGeneration) return;
      setMessages(result.items);
      setMessageTotal(result.total);
      setMessagePage(result.page);
      setMessagePageSize(result.size);
      setResultMayBeTruncated(result.resultMayBeTruncated);
      setQueryError(null);
      message.success(`查询完成，共 ${result.total} 条`);
    } catch (error) {
      if (queryGenerationRef.current === requestGeneration) {
        setQueryError(getErrorMessage(error, DEFAULT_QUERY_ERROR));
      }
    } finally {
      if (queryGenerationRef.current === requestGeneration) {
        setQueryLoading(false);
      }
    }
  };

  const handleQuery = async () => {
    await executeQuery(queryMode, currentQueryParams);
  };

  const replayHistoryRecord = (record: MessageQueryHistory) => {
    const modeMap: Record<string, QueryMode> = { TOPIC: 'topic', KEY: 'key', MSG_ID: 'msgid' };
    const mode = modeMap[record.queryType] || 'topic';
    const params: MessageQuery = {
      topic: record.topic,
      msgId: record.msgId || undefined,
      key: record.messageKey || undefined,
      startTime: record.startTime,
      endTime: record.endTime,
    };
    setQueryMode(mode);
    setSelectedTopic(record.topic);
    setKeyInput(record.messageKey || '');
    setMsgIdInput(record.msgId || '');
    if (mode === 'topic' && record.startTime !== undefined && record.endTime !== undefined) {
      setDateRange([dayjs(record.startTime), dayjs(record.endTime)]);
    }
    setHistoryDrawerOpen(false);
    void executeQuery(mode, params);
  };

  const replayTraceRecord = (record: TraceQueryHistory) => {
    setQueryMode('msgid');
    setSelectedTopic(record.topic);
    setMsgIdInput(record.msgId);
    setHistoryDrawerOpen(false);
    void executeQuery('msgid', { topic: record.topic, msgId: record.msgId });
  };

  const handleVerifyConsume = () => {
    message.warning('消费验证接口尚未接入，无法确认该消息的真实消费状态');
  };
  const openDetail = async (record: MessageRecord, tab = 'content') => {
    const requestGeneration = traceGenerationRef.current + 1;
    traceGenerationRef.current = requestGeneration;
    setSelectedMsg(record);
    setModalTab(tab);
    setModalOpen(true);
    setTraceData(null);
    setTraceLoading(true);
    setTraceError(null);
    try {
      const result = await getMessageTrace(record.msgId, selectedInstanceId, record.topic);
      if (traceGenerationRef.current !== requestGeneration) return;
      setTraceData(result);
      setTraceError(null);
    } catch (error) {
      if (traceGenerationRef.current === requestGeneration) {
        setTraceError(getErrorMessage(error, DEFAULT_TRACE_ERROR));
      }
    } finally {
      if (traceGenerationRef.current === requestGeneration) {
        setTraceLoading(false);
      }
    }
  };

  const closeDetail = () => {
    traceGenerationRef.current += 1;
    setModalOpen(false);
    setTraceLoading(false);
    setTraceError(null);
  };

  const handleDownload = (record: MessageRecord) => {
    const blob = new Blob([formatBody(record.body)], { type: 'application/json' });
    downloadBlob(blob, `${record.msgId}.json`);
    message.success('消息下载成功');
  };

  /* ─── Table Columns ─── */
  const columns: ColumnsType<MessageRecord> = [
    {
      title: 'Topic',
      dataIndex: 'topic',
      key: 'topic',
      width: 170,
      ellipsis: true,
      sorter: (a, b) => a.topic.localeCompare(b.topic),
      render: (topic: string) => (
        <Text strong style={{ fontSize: 14 }}>
          {topic}
        </Text>
      ),
    },
    {
      title: 'Tag',
      dataIndex: 'tag',
      key: 'tag',
      width: 80,
      render: (tag: string | null) => <Tag>{tag || '-'}</Tag>,
    },
    {
      title: 'Key',
      dataIndex: 'key',
      key: 'key',
      width: 120,
      ellipsis: true,
      render: (key: string | null) => (
        <span style={{ fontFamily: 'monospace', fontSize: 14 }}>{key || '-'}</span>
      ),
    },
    {
      title: 'Message ID',
      dataIndex: 'msgId',
      key: 'msgId',
      width: 260,
      render: (id: string) => (
        <Text
          copyable={{ text: id }}
          ellipsis={{ tooltip: id }}
          style={{ fontSize: 14, fontFamily: 'monospace', width: '100%', display: 'block' }}
        >
          {id}
        </Text>
      ),
    },
    {
      title: '存储时间',
      dataIndex: 'storeTime',
      key: 'storeTime',
      width: 185,
      sorter: (a, b) => new Date(a.storeTime).valueOf() - new Date(b.storeTime).valueOf(),
      render: (time: string) => (
        <span style={{ fontFamily: 'monospace', fontSize: 14, whiteSpace: 'nowrap' }}>
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
            onClick={handleVerifyConsume}
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
        const s = DELIVERY_STATUS_MAP[(status ?? '').toLowerCase()] || {
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
          <span style={{ fontFamily: 'monospace', fontSize: 14 }}>{formatTimeMs(time)}</span>
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
              fontSize: 14,
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
      ) : traceError ? (
        <Alert showIcon type="warning" message={traceError} />
      ) : traceData?.nodes?.length ? (
        <Steps
          direction="vertical"
          size="small"
          items={traceData.nodes.map((node) => ({
            title: node.title,
            description: (
              <div style={{ fontSize: 14 }}>
                <div style={{ color: '#9CA3AF', fontFamily: 'monospace' }}>
                  {formatTimeMs(node.timestamp)}
                </div>
                <div style={{ marginTop: 2 }}>{node.description}</div>
                <div style={{ color: '#9CA3AF', fontSize: 14 }}>耗时 {node.costTime}ms</div>
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
      <PageHeader title={t('message.title')} subtitle="按 Topic、Key 或 Message ID 检索消息" />

      {/* ── Query Form ── */}
      <Card style={{ marginBottom: 16 }}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Space size={12}>
            <InstanceSelect
              value={selectedInstanceId || undefined}
              onChange={selectInstance}
              options={instanceOptions}
              style={{ width: 220 }}
            />
            <Segmented
              options={QUERY_OPTIONS}
              value={queryMode}
              onChange={(v) => setQueryMode(v as QueryMode)}
            />
          </Space>

          {queryMode !== 'queue' && (
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
                    loading={topicLoading}
                    disabled={topicLoading || Boolean(topicError)}
                    options={topicOptions.map((t) => ({
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
                    loading={topicLoading}
                    disabled={topicLoading || Boolean(topicError)}
                    options={topicOptions.map((t) => ({
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
                <>
                  <Select
                    placeholder="选择 Topic"
                    style={{ width: 360 }}
                    value={selectedTopic}
                    onChange={setSelectedTopic}
                    allowClear
                    showSearch
                    loading={topicLoading}
                    disabled={topicLoading || Boolean(topicError)}
                    options={topicOptions.map((t) => ({
                      value: t,
                      label: t,
                    }))}
                  />
                  <Input
                    placeholder="输入 Message ID"
                    style={{ width: 400 }}
                    value={msgIdInput}
                    onChange={(e) => setMsgIdInput(e.target.value)}
                  />
                </>
              )}

              <Button
                type="primary"
                icon={<SearchOutlined />}
                disabled={Boolean(queryDisabledReason)}
                title={queryDisabledReason || undefined}
                onClick={() => {
                  void handleQuery();
                }}
              >
                查询
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
              <Button icon={<HistoryOutlined />} onClick={() => setHistoryDrawerOpen(true)}>
                服务端历史
              </Button>
            </Space>
          )}

          {queryMode === 'queue' && (
            <QueueBrowserControls
              instanceId={selectedInstanceId}
              state={queueBrowser}
              topicOptions={topicOptions.map((t) => ({ label: t, value: t }))}
              topicLoading={topicLoading}
            />
          )}
        </Space>
      </Card>

      {queryMode === 'queue' && <QueueBrowserResults state={queueBrowser} />}

      {topicError && (
        <Alert
          showIcon
          type="error"
          message="Topic 列表加载失败"
          description={topicError}
          action={
            <Button size="small" onClick={() => void loadTopicOptions()}>
              重试
            </Button>
          }
          style={{ marginBottom: 16 }}
        />
      )}
      <MessageQueryHistoryDrawer
        open={historyDrawerOpen}
        clusterId={selectedInstanceId}
        onClose={() => setHistoryDrawerOpen(false)}
        onSelectMessage={replayHistoryRecord}
        onSelectTrace={replayTraceRecord}
      />

      {queryError && (
        <Alert showIcon type="warning" message={queryError} style={{ marginBottom: 16 }} />
      )}
      {queryMode !== 'queue' && resultMayBeTruncated && (
        <Alert
          showIcon
          type="warning"
          message="查询结果达到服务端扫描上限，当前总数可能不完整。"
          style={{ marginBottom: 16 }}
        />
      )}

      {/* ── Results Table ── */}
      {queryMode !== 'queue' && (
        <Card styles={{ body: { padding: 0 } }}>
          <Table
            columns={columns}
            dataSource={messages}
            loading={queryLoading}
            rowKey="msgId"
            pagination={{
              current: messagePage,
              pageSize: messagePageSize,
              total: messageTotal,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条消息`,
              onChange: (page, pageSize) =>
                void executeQuery(queryMode, currentQueryParams, page, pageSize),
            }}
            size="small"
            scroll={{ x: tableScrollX(columns) }}
          />
        </Card>
      )}

      {/* ── Message Detail Modal ── */}
      <Modal
        title="消息详情"
        width={800}
        open={modalOpen}
        onCancel={closeDetail}
        destroyOnHidden
        footer={
          <Flex justify="flex-end" gap={8}>
            <Button onClick={closeDetail}>关闭</Button>
            <Button
              type="primary"
              icon={<SendOutlined />}
              disabled
              title={RESEND_UNAVAILABLE_MESSAGE}
            >
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
