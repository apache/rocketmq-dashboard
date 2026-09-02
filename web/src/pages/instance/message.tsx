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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
  Progress,
  Statistic,
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
import { getMessageQueryResults } from '../../api/messageHistory';
import { useLang } from '../../i18n/LangContext';
import type { MessageQuery, MessageRecord, TraceRecord } from '../../api/message';
import {
  consumeMessageDirectly,
  getMessageTrace,
  getMessageTraceByKey,
  queryMessagePage,
} from '../../services/messageService';
import { listTopics } from '../../services/topicService';
import { useInstanceFilter } from '../../hooks/useInstanceFilter';
import { downloadBlob } from '../../utils/download';
import { tableScrollX } from '../../utils/table';
import {
  analyzeMessageTrace,
  type MessageTraceDiagnostics,
  type TraceDiagnosticStatus,
} from '../../utils/messageTraceDiagnostics';

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

const formatDurationMs = (value: number | null): string => {
  if (value == null) return '-';
  if (value >= 60000) return `${(value / 60000).toFixed(1)} min`;
  if (value >= 1000) return `${(value / 1000).toFixed(2)} s`;
  return `${value} ms`;
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

const diagnosticTagColor: Record<TraceDiagnosticStatus, string> = {
  healthy: 'success',
  warning: 'warning',
  critical: 'error',
};

const diagnosticStatusText: Record<TraceDiagnosticStatus, string> = {
  healthy: '健康',
  warning: '关注',
  critical: '异常',
};

const TraceDiagnosticsPanel = ({ diagnostics }: { diagnostics: MessageTraceDiagnostics }) => {
  const issueData = diagnostics.issues.slice(0, 8);

  return (
    <Space direction="vertical" size={12} style={{ width: '100%', marginBottom: 16 }}>
      <Alert
        showIcon
        type={diagnostics.statusColor}
        message={
          <Flex gap={8} align="center" wrap>
            <span>轨迹诊断</span>
            <Tag color={diagnosticTagColor[diagnostics.status]}>{diagnostics.statusText}</Tag>
            {issueData.map((issue) => (
              <Tag key={issue.id} color={diagnosticTagColor[issue.severity]}>
                {issue.title}
              </Tag>
            ))}
          </Flex>
        }
      />
      <Flex gap={16} wrap>
        <div style={{ minWidth: 160 }}>
          <div style={{ color: '#8c8c8c', marginBottom: 6 }}>健康分</div>
          <Progress
            percent={diagnostics.score}
            status={diagnostics.status === 'critical' ? 'exception' : 'normal'}
            strokeColor={diagnostics.status === 'healthy' ? '#52c41a' : undefined}
          />
        </div>
        <Statistic title="轨迹阶段" value={diagnostics.summary.nodeCount} />
        <Statistic
          title="端到端耗时"
          value={formatDurationMs(diagnostics.summary.endToEndLatencyMs)}
        />
        <Statistic
          title="阶段耗时合计"
          value={formatDurationMs(diagnostics.summary.totalNodeCostMs)}
        />
        <Statistic
          title="消费成功率"
          value={
            diagnostics.summary.successfulConsumerRate == null
              ? '-'
              : `${diagnostics.summary.successfulConsumerRate}%`
          }
        />
      </Flex>
      {diagnostics.summary.slowestNode && (
        <Typography.Text type="secondary">
          最慢阶段：{diagnostics.summary.slowestNode.title}，
          {formatDurationMs(diagnostics.summary.slowestNode.valueMs)}
          {diagnostics.summary.slowestGap
            ? `；最大阶段间隔：${diagnostics.summary.slowestGap.title}，${formatDurationMs(
                diagnostics.summary.slowestGap.valueMs,
              )}`
            : ''}
        </Typography.Text>
      )}
      {issueData.length > 0 && (
        <Table
          columns={[
            {
              title: '级别',
              dataIndex: 'severity',
              key: 'severity',
              width: 90,
              render: (severity: TraceDiagnosticStatus) => (
                <Tag color={diagnosticTagColor[severity]}>{diagnosticStatusText[severity]}</Tag>
              ),
            },
            {
              title: '风险',
              dataIndex: 'title',
              key: 'title',
              width: 150,
            },
            {
              title: '说明',
              dataIndex: 'description',
              key: 'description',
            },
          ]}
          dataSource={issueData}
          rowKey="id"
          pagination={false}
          size="small"
        />
      )}
      {diagnostics.recommendations.length > 0 && (
        <Space direction="vertical" size={4}>
          {diagnostics.recommendations.slice(0, 4).map((recommendation) => (
            <Typography.Text key={recommendation} type="secondary">
              {recommendation}
            </Typography.Text>
          ))}
        </Space>
      )}
    </Space>
  );
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
  const [traceQueryMode, setTraceQueryMode] = useState<'msgid' | 'key'>('msgid');
  const [traceQueryValue, setTraceQueryValue] = useState('');
  const [customTraceTopic, setCustomTraceTopic] = useState('');
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [directConsumeOpen, setDirectConsumeOpen] = useState(false);
  const [directConsumeGroup, setDirectConsumeGroup] = useState('');
  const [directConsumeClientId, setDirectConsumeClientId] = useState('');
  const [directConsumeSubmitting, setDirectConsumeSubmitting] = useState(false);
  const queryGenerationRef = useRef(0);
  const traceGenerationRef = useRef(0);
  const traceCacheRef = useRef(new Map<string, Promise<TraceRecord | null>>());
  const traceDiagnostics = useMemo(() => analyzeMessageTrace(traceData), [traceData]);

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
  const clearQueryResults = () => {
    setMessages([]);
    setMessageTotal(0);
    setMessagePage(1);
    setResultMayBeTruncated(false);
    setQueryError(null);
    setQueryLoading(false);
  };

  const handleReset = () => {
    queryGenerationRef.current += 1;
    setSelectedTopic(undefined);
    setKeyInput('');
    setMsgIdInput('');
    setDateRange(getDefaultRange());
    clearQueryResults();
  };

  const handleQueryModeChange = (mode: QueryMode) => {
    if (mode === queryMode) return;
    queryGenerationRef.current += 1;
    setQueryMode(mode);
    clearQueryResults();
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

  const replayHistoryRecord = async (record: MessageQueryHistory) => {
    const modeMap: Record<string, QueryMode> = { TOPIC: 'topic', KEY: 'key', MSG_ID: 'msgid' };
    const mode = modeMap[record.queryType] || 'topic';
    handleQueryModeChange(mode);
    setSelectedTopic(record.topic);
    setKeyInput(record.messageKey || '');
    setMsgIdInput(record.msgId || '');
    if (mode === 'topic' && record.startTime !== undefined && record.endTime !== undefined) {
      setDateRange([dayjs(record.startTime), dayjs(record.endTime)]);
    }
    setHistoryDrawerOpen(false);
    const requestGeneration = queryGenerationRef.current + 1;
    queryGenerationRef.current = requestGeneration;
    setQueryLoading(true);
    setQueryError(null);
    try {
      const results = await getMessageQueryResults(record.id);
      if (queryGenerationRef.current !== requestGeneration) return;
      const mapped: MessageRecord[] = results.map((r) => ({
        msgId: r.msgId,
        topic: r.topic,
        tag: r.tag || null,
        key: r.key || null,
        brokerName: r.brokerName || null,
        queueId: r.queueId,
        queueOffset: r.queueOffset,
        body: '',
        storeTime: r.storeTime,
        bornHost: r.bornHost,
        storeHost: r.storeHost,
        properties: {},
        size: r.size,
      }));
      setMessages(mapped);
      setMessageTotal(mapped.length);
      setMessagePage(1);
      setResultMayBeTruncated(false);
      message.success(`已加载历史查询结果，共 ${mapped.length} 条`);
    } catch (error) {
      if (queryGenerationRef.current === requestGeneration) {
        setQueryError(getErrorMessage(error, '加载历史结果失败'));
      }
    } finally {
      if (queryGenerationRef.current === requestGeneration) {
        setQueryLoading(false);
      }
    }
  };

  const replayTraceRecord = (record: TraceQueryHistory) => {
    handleQueryModeChange('msgid');
    setSelectedTopic(record.topic);
    setMsgIdInput(record.msgId);
    setHistoryDrawerOpen(false);
    void executeQuery('msgid', { topic: record.topic, msgId: record.msgId });
  };

  const handleVerifyConsume = () => {
    message.warning('消费验证接口尚未接入，无法确认该消息的真实消费状态');
  };
  const loadMessageTrace = async (record: MessageRecord) => {
    const requestGeneration = traceGenerationRef.current + 1;
    traceGenerationRef.current = requestGeneration;
    setTraceData(null);
    setTraceLoading(true);
    setTraceError(null);
    setTraceQueryMode('msgid');
    setTraceQueryValue(record.msgId);
    const cacheKey = JSON.stringify([selectedInstanceId, record.topic, record.msgId]);
    let traceRequest = traceCacheRef.current.get(cacheKey);
    if (!traceRequest) {
      traceRequest = getMessageTrace(record.msgId, selectedInstanceId, record.topic).catch(
        (error) => {
          traceCacheRef.current.delete(cacheKey);
          throw error;
        },
      );
      traceCacheRef.current.set(cacheKey, traceRequest);
    }
    try {
      const result = await traceRequest;
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

  const openDetail = (record: MessageRecord, tab = 'content') => {
    traceGenerationRef.current += 1;
    setSelectedMsg(record);
    setModalTab(tab);
    setModalOpen(true);
    setTraceData(null);
    setTraceLoading(false);
    setTraceError(null);
    if (tab === 'trace') void loadMessageTrace(record);
  };

  const handleModalTabChange = (tab: string) => {
    setModalTab(tab);
    if (tab === 'trace' && selectedMsg) void loadMessageTrace(selectedMsg);
  };

  const runTraceQuery = async () => {
    const requestGeneration = traceGenerationRef.current + 1;
    traceGenerationRef.current = requestGeneration;
    const value = traceQueryValue.trim();
    if (!value) {
      setTraceError(traceQueryMode === 'key' ? '请输入 Message Key' : '请输入 Message ID');
      return;
    }
    setTraceData(null);
    setTraceLoading(true);
    setTraceError(null);
    try {
      const result =
        traceQueryMode === 'key'
          ? await getMessageTraceByKey(
              value,
              selectedInstanceId,
              selectedMsg?.topic,
              customTraceTopic,
            )
          : await getMessageTrace(value, selectedInstanceId, selectedMsg?.topic, customTraceTopic);
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

  const openDirectConsume = () => {
    setDirectConsumeGroup('');
    setDirectConsumeClientId('');
    setDirectConsumeOpen(true);
  };

  const handleDirectConsume = async () => {
    if (
      !selectedInstanceId ||
      !selectedMsg ||
      !directConsumeGroup.trim() ||
      !directConsumeClientId.trim()
    ) {
      message.warning('请填写目标消费组和在线客户端 ID');
      return;
    }
    setDirectConsumeSubmitting(true);
    try {
      const result = await consumeMessageDirectly({
        instanceId: selectedInstanceId,
        topic: selectedMsg.topic,
        msgId: selectedMsg.msgId,
        consumerGroup: directConsumeGroup.trim(),
        clientId: directConsumeClientId.trim(),
      });
      const detail = [result.consumeResult, result.remark].filter(Boolean).join('：');
      message.info(`Broker 返回 ${detail || 'UNKNOWN'}，耗时 ${result.spentTimeMillis} ms`);
      setDirectConsumeOpen(false);
    } catch (error) {
      message.error(getErrorMessage(error, '直接消费请求失败，请检查消费组和客户端是否在线'));
    } finally {
      setDirectConsumeSubmitting(false);
    }
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
            {t('message.detail')}
          </Button>
          <Button
            size="small"
            icon={<NodeIndexOutlined />}
            style={{ borderColor: '#722ed1', color: '#722ed1' }}
            onClick={() => void openDetail(record, 'trace')}
          >
            {t('message.trace')}
          </Button>
          <Button
            size="small"
            icon={<CheckCircleOutlined />}
            style={{ borderColor: '#52c41a', color: '#52c41a' }}
            onClick={handleVerifyConsume}
          >
            {t('message.verify')}
          </Button>
          <Button
            size="small"
            icon={<DownloadOutlined />}
            style={{ borderColor: '#fa8c16', color: '#fa8c16' }}
            onClick={() => handleDownload(record)}
          >
            {t('message.download')}
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
      title: t('message.consumerGroup'),
      dataIndex: 'group',
      key: 'group',
      render: (g: string) => <span style={{ fontFamily: 'monospace', fontWeight: 500 }}>{g}</span>,
    },
    {
      title: t('message.deliveryStatus'),
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
      title: t('message.consumeTime'),
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
      title: t('message.retryCount'),
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
      label: t('message.messageContent'),
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
            <Descriptions.Item label={t('message.size')}>{formatSize(selectedMsg.size)}</Descriptions.Item>
            <Descriptions.Item label="Born Host">
              <span style={{ fontFamily: 'monospace' }}>{selectedMsg.bornHost}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Store Host">
              <span style={{ fontFamily: 'monospace' }}>{selectedMsg.storeHost}</span>
            </Descriptions.Item>
            <Descriptions.Item label={t('message.storeTime')} span={2}>
              <span style={{ fontFamily: 'monospace' }}>{formatTimeMs(selectedMsg.storeTime)}</span>
            </Descriptions.Item>
          </Descriptions>
          <Typography.Title level={5} style={{ marginBottom: 8 }}>
            {t('message.messageBody')}
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
      children: (
        <>
          <Space wrap size={8} style={{ marginBottom: 16 }}>
            <Segmented
              size="small"
              options={[
                { value: 'msgid', label: '按 Message ID' },
                { value: 'key', label: '按 Message Key' },
              ]}
              value={traceQueryMode}
              onChange={(value) => setTraceQueryMode(value as 'msgid' | 'key')}
            />
            <Input
              size="small"
              style={{ width: 300 }}
              placeholder={
                traceQueryMode === 'key' ? '输入 Message Key' : '消息 ID（默认当前消息）'
              }
              value={traceQueryValue}
              onChange={(event) => setTraceQueryValue(event.target.value)}
            />
            <Input
              size="small"
              style={{ width: 260 }}
              placeholder="轨迹 Topic（留空使用默认）"
              value={customTraceTopic}
              onChange={(event) => setCustomTraceTopic(event.target.value)}
              allowClear
            />
            <Button
              size="small"
              type="primary"
              icon={<SearchOutlined />}
              onClick={() => void runTraceQuery()}
            >
              查询轨迹
            </Button>
          </Space>
          {traceLoading ? (
            <Typography.Text type="secondary">正在加载轨迹数据…</Typography.Text>
          ) : traceError ? (
            <Alert showIcon type="warning" message={traceError} />
          ) : traceData?.nodes?.length ? (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <TraceDiagnosticsPanel diagnostics={traceDiagnostics} />
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
            </Space>
          ) : (
            <Typography.Text type="secondary">暂无轨迹数据</Typography.Text>
          )}
        </>
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
              onChange={(v) => handleQueryModeChange(v as QueryMode)}
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
              disabled={!selectedInstanceId || !selectedMsg}
              onClick={openDirectConsume}
            >
              直接消费
            </Button>
          </Flex>
        }
      >
        <Tabs activeKey={modalTab} onChange={handleModalTabChange} items={modalTabs} />
      </Modal>

      <Modal
        title="直接消费消息"
        open={directConsumeOpen}
        onCancel={() => setDirectConsumeOpen(false)}
        onOk={() => void handleDirectConsume()}
        confirmLoading={directConsumeSubmitting}
        okText="执行"
        destroyOnHidden
      >
        <Alert
          showIcon
          type="warning"
          message="Broker 会请求指定在线客户端立即消费该消息。"
          description="这不是向 Topic 重新发送消息；Broker 返回的消费结果会原样显示。"
          style={{ marginBottom: 16 }}
        />
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Input value={selectedMsg?.topic} disabled addonBefore="Topic" />
          <Input value={selectedMsg?.msgId} disabled addonBefore="Message ID" />
          <Input
            value={directConsumeGroup}
            onChange={(event) => setDirectConsumeGroup(event.target.value)}
            placeholder="目标消费者组"
            addonBefore="Consumer group"
          />
          <Input
            value={directConsumeClientId}
            onChange={(event) => setDirectConsumeClientId(event.target.value)}
            placeholder="在线客户端 ID"
            addonBefore="Client ID"
          />
        </Space>
      </Modal>
    </div>
  );
};

export default MessagePage;
