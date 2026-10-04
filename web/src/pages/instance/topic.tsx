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

import { useCallback, useEffect, useState, useMemo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Table,
  Card,
  Tag,
  Modal,
  Form,
  Select,
  Input,
  Segmented,
  Descriptions,
  Button,
  Space,
  InputNumber,
  Radio,
  Flex,
  Row,
  Col,
  Divider,
  Typography,
  Spin,
  message,
  App,
  Progress,
} from 'antd';
import type { TableColumnsType } from 'antd';
import {
  PlusOutlined,
  SendOutlined,
  DeleteOutlined,
  EyeOutlined,
  EditOutlined,
  ImportOutlined,
  ExportOutlined,
  SyncOutlined,
  PlusCircleOutlined,
  MinusCircleOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  WarningOutlined,
  DiffOutlined,
} from '@ant-design/icons';
import PageHeader from '../../components/PageHeader';
import InfoBanner from '../../components/InfoBanner';
import { InstanceSelect } from '../../components/InstanceSelect';
import TopicConfigComparisonDrawer from '../../components/TopicConfigComparisonDrawer';
import { useLang } from '../../i18n/LangContext';
import { TOPIC_TYPE_MAP, CLUSTER_TYPE_MAP } from '../../constants/theme';
import type { Topic, BrokerRoute, ConsumerGroupInfo, TopicConsumerPage } from '../../api/metadata';
import {
  batchDeleteTopics,
  createTopic,
  deleteTopic,
  exportTopics,
  getTopicConsumerPage,
  getTopicRoutes,
  importTopics,
  listTopicsPage,
  sendTopicMessage,
  updateTopic,
} from '../../services/topicService';
import { useInstanceFilter } from '../../hooks/useInstanceFilter';
import type { Instance } from '../../api/instance';
import {
  parseCsvTable,
  RESOURCE_NAME_MAX_LENGTH,
  RESOURCE_NAME_PATTERN,
  validateTopicCsvImport,
  type ResourceImportRow,
} from '../../utils/resourceCsvImport';
import { isLagAvailable } from '../../utils/consumerLag';
import { downloadCsv } from '../../utils/download';
import { formatBytes, formatDateTime, formatNumber } from '../../utils/format';
import { tableScrollX } from '../../utils/table';
import {
  analyzeTopicRoutes,
  type RouteDiagnosticIssue,
  type RouteDiagnosticStatus,
  type RouteDistribution,
} from '../../utils/topicRouteDiagnostics';
import {
  analyzeMessagePayloadPreview,
  type MessageBodyFormat,
  type MessagePayloadIssue,
  type MessagePayloadPreviewStatus,
  type MessagePropertyInput,
} from '../../utils/messagePayloadPreview';

const { Text } = Typography;

const INSTANCE_ACCESS_LABEL: Record<Instance['type'], string> = {
  CLOUD: 'topic.accessCloud',
  PROXY_LOCAL: 'Proxy Local',
  PROXY_CLUSTER: 'Proxy Cluster',
  DIRECT: 'Direct',
};

const INSTANCE_ACCESS_DESCRIPTION: Record<Instance['type'], string> = {
  CLOUD: 'topic.accessDescCloud',
  PROXY_LOCAL: 'topic.accessDescProxyLocal',
  PROXY_CLUSTER: 'topic.accessDescProxyCluster',
  DIRECT: 'topic.accessDescDirect',
};

// ─── Cluster name lookup ───────────────────────────────────────────
const CLUSTER_NAME_MAP: Record<string, { name: string; type: string }> = {
  'rmq-cn-v5-prod-01': { name: 'rmq-cn-v5-prod-01', type: 'V5_PROXY_CLUSTER' },
  'rmq-cn-v4-prod-02': { name: 'rmq-cn-v4-prod-02', type: 'V4_DIRECT' },
};

const TYPE_OPTIONS = [
  { label: 'topic.filterAllTypes', value: '' },
  { label: 'topic.filterNormal', value: 'NORMAL' },
  { label: 'topic.filterFifo', value: 'FIFO' },
  { label: 'topic.filterDelay', value: 'DELAY' },
  { label: 'topic.filterTransaction', value: 'TRANSACTION' },
  { label: 'LiteTopic', value: 'LITE' },
];

// Topic 类型选项（描述参考阿里云 RocketMQ 消息类型语义），创建弹窗用 Segmented 展示
const TOPIC_TYPE_CARDS = [
  { value: 'NORMAL', label: 'topic.typeNormal', desc: 'topic.typeNormalDesc' },
  { value: 'FIFO', label: 'topic.typeFifo', desc: 'topic.typeFifoDesc' },
  { value: 'DELAY', label: 'topic.typeDelay', desc: 'topic.typeDelayDesc' },
  {
    value: 'TRANSACTION',
    label: 'topic.typeTransaction',
    desc: 'topic.typeTransactionDesc',
  },
  {
    value: 'LITE',
    label: 'LiteTopic',
    desc: 'topic.typeLiteDesc',
  },
];

// ─── Perm label ───────────────────────────────────────────────────
const PERM_LABEL: Record<string, string> = {
  RW: 'topic.permRw',
  RO: 'topic.permRo',
  WO: 'topic.permWo',
};

type SendMessageFormValues = {
  topic: string;
  tag?: string;
  key?: string;
  body: string;
  propsText?: string;
  properties?: MessagePropertyInput[];
};

const visibleTopics = (
  topics: Topic[],
  selectedInstanceId: string | undefined,
  searchText: string,
  typeFilter: string,
) =>
  topics
    .filter((topic) => {
      if (selectedInstanceId && topic.instanceId !== selectedInstanceId) return false;
      if (searchText && !topic.name.toLowerCase().includes(searchText.toLowerCase())) return false;
      if (typeFilter && topic.type !== typeFilter) return false;
      return true;
    })
    .sort((left, right) => left.name.localeCompare(right.name));

// ─── Random message body generators ──────────────────────────────
const randomOrderBody = () =>
  JSON.stringify(
    {
      orderId: `ORD-${Date.now()}-${Math.floor(Math.random() * 9000 + 1000)}`,
      userId: `user_${Math.floor(Math.random() * 90000 + 10000)}`,
      product: ['MacBook Pro 16"', 'iPhone 16 Pro', 'AirPods Max', 'iPad Air', 'Apple Watch Ultra'][
        Math.floor(Math.random() * 5)
      ],
      amount: +(Math.random() * 10000 + 100).toFixed(2),
      quantity: Math.floor(Math.random() * 5 + 1),
      status: 'CREATED',
      timestamp: new Date().toISOString(),
    },
    null,
    2,
  );

const randomUserEventBody = () =>
  JSON.stringify(
    {
      eventType: ['page_view', 'click', 'login', 'logout', 'search', 'add_to_cart'][
        Math.floor(Math.random() * 6)
      ],
      userId: `user_${Math.floor(Math.random() * 90000 + 10000)}`,
      sessionId: `sess_${Math.random().toString(36).slice(2, 14)}`,
      page: ['/home', '/products', '/cart', '/checkout', '/profile'][Math.floor(Math.random() * 5)],
      device: ['Desktop Chrome', 'Mobile Safari', 'iPad Safari', 'Desktop Firefox'][
        Math.floor(Math.random() * 4)
      ],
      ip: `10.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`,
      timestamp: new Date().toISOString(),
    },
    null,
    2,
  );

const randomPaymentBody = () =>
  JSON.stringify(
    {
      paymentId: `PAY-${Math.random().toString(36).slice(2, 10).toUpperCase()}`,
      orderId: `ORD-${Date.now()}`,
      channel: ['Alipay', 'WeChat Pay', 'UnionPay', 'Credit Card'][Math.floor(Math.random() * 4)],
      amount: +(Math.random() * 5000 + 50).toFixed(2),
      currency: 'CNY',
      status: 'SUCCESS',
      paidAt: new Date().toISOString(),
    },
    null,
    2,
  );

const randomInventoryBody = () =>
  JSON.stringify(
    {
      skuId: `SKU-${Math.floor(Math.random() * 900000 + 100000)}`,
      warehouse: ['HZ-01', 'SH-02', 'BJ-03', 'GZ-04'][Math.floor(Math.random() * 4)],
      change: Math.floor(Math.random() * 200 - 50),
      before: Math.floor(Math.random() * 1000),
      after: Math.floor(Math.random() * 1000),
      reason: ['sale', 'restock', 'return', 'adjustment'][Math.floor(Math.random() * 4)],
      timestamp: new Date().toISOString(),
    },
    null,
    2,
  );

const randomNotificationBody = () =>
  JSON.stringify(
    {
      notificationId: `NOTIF-${Math.random().toString(36).slice(2, 10).toUpperCase()}`,
      type: ['email', 'sms', 'push', 'webhook'][Math.floor(Math.random() * 4)],
      recipient: `user_${Math.floor(Math.random() * 90000 + 10000)}@example.com`,
      title: ['订单发货通知', '优惠券到期提醒', '系统维护公告', '安全验证提醒'][
        Math.floor(Math.random() * 4)
      ],
      priority: ['low', 'medium', 'high'][Math.floor(Math.random() * 3)],
      timestamp: new Date().toISOString(),
    },
    null,
    2,
  );

const randomMetricsBody = () =>
  JSON.stringify(
    {
      metric: ['cpu_usage', 'memory_usage', 'disk_io', 'network_throughput', 'gc_pause'][
        Math.floor(Math.random() * 5)
      ],
      host: `broker-${['a', 'b', 'c'][Math.floor(Math.random() * 3)]}-0${Math.floor(Math.random() * 3 + 1)}`,
      value: +(Math.random() * 100).toFixed(2),
      unit: ['%', 'MB', 'MB/s', 'ms'][Math.floor(Math.random() * 4)],
      timestamp: new Date().toISOString(),
    },
    null,
    2,
  );

const RANDOM_BODY_GENERATORS = [
  { label: 'topic.bodyOrderEvent', fn: randomOrderBody },
  { label: 'topic.bodyUserEvent', fn: randomUserEventBody },
  { label: 'topic.bodyPayment', fn: randomPaymentBody },
  { label: 'topic.bodyInventory', fn: randomInventoryBody },
  { label: 'topic.bodyNotification', fn: randomNotificationBody },
  { label: 'topic.bodyMetrics', fn: randomMetricsBody },
];

const ROUTE_STATUS_META: Record<
  RouteDiagnosticStatus,
  { color: string; label: string; icon: React.ReactNode }
> = {
  healthy: { color: 'success', label: 'topic.routeHealthy', icon: <CheckCircleOutlined /> },
  warning: { color: 'warning', label: 'topic.routeWarning', icon: <WarningOutlined /> },
  critical: { color: 'error', label: 'topic.routeCritical', icon: <ExclamationCircleOutlined /> },
};

const ISSUE_SEVERITY_COLOR: Record<RouteDiagnosticIssue['severity'], string> = {
  warning: 'warning',
  critical: 'error',
};

const formatPercent = (value: number) => `${value.toFixed(value % 1 === 0 ? 0 : 1)}%`;

const BODY_FORMAT_LABEL: Record<MessageBodyFormat, string> = {
  empty: 'topic.bodyFormatEmpty',
  'json-object': 'JSON Object',
  'json-array': 'JSON Array',
  'json-scalar': 'topic.bodyFormatJsonScalar',
  'plain-text': 'topic.bodyFormatPlainText',
};

const PAYLOAD_STATUS_META: Record<
  MessagePayloadPreviewStatus,
  { label: string; color: string; alertType: 'success' | 'warning' | 'error' }
> = {
  ready: { label: 'topic.precheckReady', color: 'success', alertType: 'success' },
  warning: { label: 'topic.precheckWarning', color: 'warning', alertType: 'warning' },
  error: { label: 'topic.precheckBlocked', color: 'error', alertType: 'error' },
};

const PAYLOAD_ISSUE_COLOR: Record<MessagePayloadIssue['severity'], string> = {
  info: 'blue',
  warning: 'warning',
  error: 'error',
};

// ═══════════════════════════════════════════════════════════════════
type TopicPageContentProps = ReturnType<typeof useInstanceFilter>;

const TopicPageContent = ({
  selectedInstanceId,
  selectedInstance,
  selectInstance,
  instanceOptions,
  instancesLoading,
  instancesFailed,
  reloadInstances,
  instances,
}: TopicPageContentProps) => {
  const { t } = useLang();
  const navigate = useNavigate();
  const isCloudInstance =
    selectedInstance?.vendor === 'ALIYUN' || selectedInstance?.vendor === 'TENCENT';
  const canSendTestMessage = (topic: Topic) => !isCloudInstance || topic.type === 'NORMAL';
  const hasSelectedInstance = Boolean(selectedInstanceId);

  // ─── State ─────────────────────────────────────────────────────
  const [topics, setTopics] = useState<Topic[]>([]);
  const [totalTopics, setTotalTopics] = useState(0);
  const [loading, setLoading] = useState(true);
  const [routesByTopic, setRoutesByTopic] = useState<Record<string, BrokerRoute[]>>({});
  const [consumersByTopic, setConsumersByTopic] = useState<Record<string, TopicConsumerPage>>({});
  // A failed detail request leaves the per-topic maps empty, which is not the same as "the broker
  // has no route" or "nobody consumes this": without these flags the route diagnosis renders the
  // empty fallback as the critical "Broker 上没有 Topic 路由" verdict and offers the rebuild action
  // for a topic that exists. Kept per request kind so a route failure cannot mislabel the consumer
  // table, and vice versa.
  const [routeLoadFailedTopics, setRouteLoadFailedTopics] = useState<Record<string, boolean>>({});
  const [consumerLoadFailedTopics, setConsumerLoadFailedTopics] = useState<Record<string, boolean>>(
    {},
  );
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [searchText, setSearchText] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [tablePage, setTablePage] = useState(1);
  const [tablePageSize, setTablePageSize] = useState(20);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [rebuilding, setRebuilding] = useState(false);
  const [syncModalOpen, setSyncModalOpen] = useState(false);
  const [syncChecking, setSyncChecking] = useState(false);
  const [syncMissing, setSyncMissing] = useState<Topic[]>([]);
  const [syncCheckedCount, setSyncCheckedCount] = useState(0);
  const [syncFailedCount, setSyncFailedCount] = useState(0);
  const [syncedTopics, setSyncedTopics] = useState<Set<string>>(() => new Set());
  const [syncingKeys, setSyncingKeys] = useState<Set<string>>(() => new Set());
  const [selectedTopic, setSelectedTopic] = useState<Topic | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [editingTopic, setEditingTopic] = useState<Topic | null>(null);
  const [form] = Form.useForm();
  const createTopicType = Form.useWatch('type', form);
  const [sendModalOpen, setSendModalOpen] = useState(false);
  const [sendTopic, setSendTopic] = useState<Topic | null>(null);
  const [sending, setSending] = useState(false);
  const [sendForm] = Form.useForm();
  const [propsMode, setPropsMode] = useState<'form' | 'text'>('form');
  const sendTagValue = Form.useWatch('tag', sendForm);
  const sendKeyValue = Form.useWatch('key', sendForm);
  const sendBodyValue = Form.useWatch('body', sendForm);
  const sendPropsTextValue = Form.useWatch('propsText', sendForm);
  const sendPropertiesValue = Form.useWatch('properties', sendForm) as
    MessagePropertyInput[] | undefined;
  const { modal } = App.useApp();
  const importInputRef = useRef<HTMLInputElement>(null);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importFilename, setImportFilename] = useState('');
  const [importRows, setImportRows] = useState<ResourceImportRow<Partial<Topic>>[]>([]);
  const [importErrors, setImportErrors] = useState<string[]>([]);
  const [importing, setImporting] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [comparisonOpen, setComparisonOpen] = useState(false);

  const topicRequestIdRef = useRef(0);
  const detailRequestIdRef = useRef(0);
  const consumersRequestIdRef = useRef(0);
  const syncRequestIdRef = useRef(0);
  const createInFlightRef = useRef(false);

  useEffect(
    () => () => {
      syncRequestIdRef.current += 1;
    },
    [],
  );

  const sendPayloadPreview = useMemo(
    () =>
      analyzeMessagePayloadPreview({
        topic: sendTopic?.name,
        tag: sendTagValue,
        key: sendKeyValue,
        body: sendBodyValue,
        propsMode,
        propsText: sendPropsTextValue,
        properties: sendPropertiesValue,
      }),
    [
      propsMode,
      sendBodyValue,
      sendKeyValue,
      sendPropertiesValue,
      sendPropsTextValue,
      sendTagValue,
      sendTopic?.name,
    ],
  );

  const loadTopicPage = useCallback(
    async (pageToLoad: number, pageSizeToLoad: number) => {
      if (!selectedInstanceId) return undefined;
      const requestId = ++topicRequestIdRef.current;
      setLoading(true);
      try {
        const result = await listTopicsPage({
          instanceId: selectedInstanceId,
          type: typeFilter || undefined,
          search: searchText.trim() || undefined,
          page: pageToLoad,
          pageSize: pageSizeToLoad,
        });
        if (requestId === topicRequestIdRef.current) {
          setTopics(result.items);
          setTotalTopics(result.total);
          if (result.items.length === 0 && result.total > 0 && pageToLoad > 1) {
            setTablePage(Math.max(1, Math.ceil(result.total / pageSizeToLoad)));
          }
        }
        return requestId === topicRequestIdRef.current ? result : undefined;
      } catch {
        if (requestId === topicRequestIdRef.current) message.error(t('topic.listLoadFailed'));
        return undefined;
      } finally {
        if (requestId === topicRequestIdRef.current) setLoading(false);
      }
    },
    [selectedInstanceId, typeFilter, searchText],
  );

  const reloadTopicPage = useCallback(async () => {
    await loadTopicPage(tablePage, tablePageSize);
  }, [loadTopicPage, tablePage, tablePageSize]);

  useEffect(() => {
    if (!selectedInstanceId) {
      topicRequestIdRef.current += 1;
      const resetTimer = window.setTimeout(() => {
        setTopics([]);
        setTotalTopics(0);
        setSelectedRowKeys([]);
        setLoading(instancesLoading);
      }, 0);
      return () => {
        window.clearTimeout(resetTimer);
      };
    }
    const timer = window.setTimeout(() => {
      void loadTopicPage(tablePage, tablePageSize);
    }, 0);

    return () => {
      window.clearTimeout(timer);
    };
  }, [selectedInstanceId, tablePage, tablePageSize, instancesLoading, loadTopicPage]);

  // ─── Filtered data ─────────────────────────────────────────────
  const filteredTopics = useMemo(
    () => visibleTopics(topics, selectedInstanceId, searchText, typeFilter),
    [topics, selectedInstanceId, searchText, typeFilter],
  );

  const maxTablePage = Math.max(1, Math.ceil(totalTopics / tablePageSize));
  const currentTablePage = Math.min(tablePage, maxTablePage);

  const resetTablePage = () => {
    setTablePage(1);
  };

  const loadTopicConsumers = useCallback(
    async (topic: Topic, page = 1, pageSize = 20) => {
      const requestId = ++consumersRequestIdRef.current;
      const consumers = await getTopicConsumerPage(
        topic.name,
        selectedInstanceId || undefined,
        page,
        pageSize,
      );
      // Guard against a slower earlier page overwriting a newer one when the user pages quickly.
      if (requestId === consumersRequestIdRef.current) {
        setConsumersByTopic((previous) => ({ ...previous, [topic.name]: consumers }));
      }
    },
    [selectedInstanceId],
  );

  // ─── Open detail modal ────────────────────────────────────────
  const openDetail = useCallback(
    async (topic: Topic) => {
      const requestId = detailRequestIdRef.current + 1;
      detailRequestIdRef.current = requestId;
      setSelectedTopic(topic);
      setDetailModalOpen(true);
      setDetailLoading(true);
      setRouteLoadFailedTopics((previous) => ({ ...previous, [topic.name]: false }));
      setConsumerLoadFailedTopics((previous) => ({ ...previous, [topic.name]: false }));
      // Load the two independently: a failing consumer page must not skip the route lookup, or the
      // modal renders "no route" for a topic whose routes were never requested.
      let detailFailed = false;
      try {
        await loadTopicConsumers(topic);
      } catch {
        detailFailed = true;
        if (requestId === detailRequestIdRef.current) {
          setConsumerLoadFailedTopics((previous) => ({ ...previous, [topic.name]: true }));
        }
      }
      if (requestId !== detailRequestIdRef.current) return;
      if (!isCloudInstance) {
        try {
          const routes = await getTopicRoutes(topic.name, selectedInstanceId || undefined);
          if (requestId !== detailRequestIdRef.current) return;
          setRoutesByTopic((previous) => ({ ...previous, [topic.name]: routes }));
        } catch {
          detailFailed = true;
          if (requestId === detailRequestIdRef.current) {
            setRouteLoadFailedTopics((previous) => ({ ...previous, [topic.name]: true }));
          }
        }
      }
      if (requestId !== detailRequestIdRef.current) return;
      if (detailFailed) {
        message.error(t('topic.detailLoadFailed'));
      }
      setDetailLoading(false);
    },
    [loadTopicConsumers, isCloudInstance, selectedInstanceId],
  );

  // Metadata lives in the database, so a record can exist without a broker route.
  const rebuildTopic = async (topic: Topic) => {
    const instanceId = topic.instanceId || selectedInstanceId || undefined;
    setRebuilding(true);
    try {
      await createTopic({
        name: topic.name,
        type: topic.type,
        writeQueues: topic.writeQueues,
        readQueues: topic.readQueues,
        instanceId,
      });
      const routes = await getTopicRoutes(topic.name, instanceId);
      setRoutesByTopic((previous) => ({ ...previous, [topic.name]: routes }));
      message.success(t('topic.rebuildCompleted', { name: topic.name }));
    } catch {
      message.error(t('topic.rebuildFailed'));
    } finally {
      setRebuilding(false);
    }
  };

  // ─── Route / consumer helpers ─────────────────────────────────
  const getRoutes = (name: string): BrokerRoute[] => routesByTopic[name] ?? [];
  const getConsumerPage = (name: string): TopicConsumerPage =>
    consumersByTopic[name] ?? { items: [], total: 0, page: 1, pageSize: 20 };

  // ─── Sync data: find topics without broker routes and sync them ──
  const invalidateSyncRequest = () => {
    syncRequestIdRef.current += 1;
  };

  const closeSyncModal = () => {
    invalidateSyncRequest();
    setSyncModalOpen(false);
  };

  const openSyncModal = async () => {
    const requestId = syncRequestIdRef.current + 1;
    syncRequestIdRef.current = requestId;
    setSyncModalOpen(true);
    setSyncChecking(true);
    setSyncMissing([]);
    setSyncCheckedCount(0);
    setSyncFailedCount(0);
    setSyncedTopics(new Set());
    try {
      // Scan the rendered list rather than the raw page: the verdict below quotes a count, and the
      // table hides rows the client-side filter dropped, so both must cover the same set.
      const results = await Promise.all(
        filteredTopics.map(async (topic) => {
          const instanceId = topic.instanceId || selectedInstanceId || undefined;
          try {
            return { topic, routes: await getTopicRoutes(topic.name, instanceId) };
          } catch {
            return { topic, routes: null as BrokerRoute[] | null };
          }
        }),
      );
      if (syncRequestIdRef.current !== requestId) return;
      const checked = results.filter((r) => r.routes !== null);
      // A failed lookup proves nothing about that topic, so the counts are what the empty state has
      // to quote - iterating the list would report topics as verified that were never resolved.
      setSyncCheckedCount(checked.length);
      setSyncFailedCount(results.length - checked.length);
      if (checked.length < results.length) {
        message.error(t('topic.routeCheckPartialFailed'));
      }
      setRoutesByTopic((previous) => {
        const next = { ...previous };
        checked.forEach(({ topic, routes }) => {
          next[topic.name] = routes as BrokerRoute[];
        });
        return next;
      });
      setSyncMissing(
        checked.filter(({ routes }) => (routes as BrokerRoute[]).length === 0).map((r) => r.topic),
      );
    } finally {
      if (syncRequestIdRef.current === requestId) setSyncChecking(false);
    }
  };

  const renderSyncFailureAlert = () =>
    syncFailedCount === 0 ? null : (
      <Alert
        type="error"
        showIcon
        style={{ marginBottom: 12 }}
        message={`路由校验失败：${syncFailedCount} 个 Topic 未能校验`}
        description="这些 Topic 无法判断是否缺失路由，请重试后再确认是否需要同步。"
        action={
          <Button size="small" onClick={() => void openSyncModal()}>
            重试
          </Button>
        }
      />
    );

  const syncTopicToBroker = async (topic: Topic) => {
    const instanceId = topic.instanceId || selectedInstanceId || undefined;
    setSyncingKeys((previous) => new Set(previous).add(topic.name));
    try {
      await createTopic({
        name: topic.name,
        type: topic.type,
        writeQueues: topic.writeQueues,
        readQueues: topic.readQueues,
        instanceId,
      });
      const routes = await getTopicRoutes(topic.name, instanceId);
      setRoutesByTopic((previous) => ({ ...previous, [topic.name]: routes }));
      setSyncedTopics((previous) => new Set(previous).add(topic.name));
      message.success(t('topic.syncCompleted', { name: topic.name }));
    } catch {
      message.error(t('topic.syncFailed', { name: topic.name }));
    } finally {
      setSyncingKeys((previous) => {
        const next = new Set(previous);
        next.delete(topic.name);
        return next;
      });
    }
  };

  const handleAction = (key: string, topic: Topic) => {
    if (key === 'detail') {
      void openDetail(topic);
    } else if (key === 'route') {
      void openDetail(topic);
    } else if (key === 'config') {
      openEditConfig(topic);
    } else if (key === 'send') {
      setSendTopic(topic);
      setPropsMode('form');
      sendForm.setFieldsValue({ topic: topic.name, tag: '', key: '', body: '', properties: [] });
      setSendModalOpen(true);
    } else if (key === 'delete') {
      modal.confirm({
        title: t('topic.deleteConfirmTitle'),
        content: t('topic.deleteConfirmContent', { name: topic.name }),
        okText: t('common.delete'),
        okType: 'danger',
        cancelText: t('common.cancel'),
        onOk: async () => {
          try {
            await deleteTopic(topic.name, selectedInstanceId || undefined);
            // Drop the deleted row from the selection: a checked row that disappears would
            // otherwise keep the batch delete armed with a name that no longer exists, and its
            // failure path re-seeds that same selection - leaving nothing to uncheck.
            setSelectedRowKeys((previous) => previous.filter((key) => key !== topic.name));
            await reloadTopicPage();
            message.success(t('topic.deleted', { name: topic.name }));
          } catch {
            message.error(t('topic.deleteFailed'));
          }
        },
      });
    }
  };

  const handleExport = () => {
    setExporting(true);

    void exportTopics({
      instanceId: selectedInstanceId || undefined,
      type: typeFilter || undefined,
      search: searchText.trim() || undefined,
    })
      .then((csv) => {
        downloadCsv(`rocketmq-topics-${new Date().toISOString().slice(0, 10)}.csv`, csv);
        message.success(t('topic.exportCompleted'));
      })
      .catch(() => {
        message.error(t('topic.exportFailed'));
      })
      .finally(() => setExporting(false));
  };

  // ─── Table columns ────────────────────────────────────────────
  const columns: TableColumnsType<Topic> = [
    {
      title: t('topic.colName'),
      dataIndex: 'name',
      key: 'name',
      // 唯一可伸展列：容器比表宽时余量集中在此，其余列保持声明宽度
      minWidth: 220,
      ellipsis: true,
      sorter: (a, b) => a.name.localeCompare(b.name),
      render: (name: string) => (
        <Text strong style={{ fontSize: 14, display: 'block' }} ellipsis={{ tooltip: name }}>
          {name}
        </Text>
      ),
    },
    {
      title: t('topic.colRemark'),
      dataIndex: 'remark',
      key: 'remark',
      width: 200,
      ellipsis: true,
      sorter: (a, b) => (a.remark ?? '').localeCompare(b.remark ?? ''),
      render: (remark: string) => (
        <Text
          type="secondary"
          style={{ fontSize: 14, display: 'block' }}
          ellipsis={{ tooltip: remark }}
        >
          {remark}
        </Text>
      ),
    },
    {
      title: t('topic.colType'),
      dataIndex: 'type',
      key: 'type',
      width: 100,
      sorter: (a, b) => (a.type ?? '').localeCompare(b.type ?? ''),
      render: (type: string) => {
        const cfg = TOPIC_TYPE_MAP[type];
        return cfg ? <Tag color={cfg.color}>{t(cfg.labelKey)}</Tag> : <Tag>{type}</Tag>;
      },
    },
    {
      title: t('topic.colStatus'),
      key: 'status',
      width: 90,
      render: () => <Tag color="green">{t('topic.statusServing')}</Tag>,
    },
    {
      title: t('topic.colCreatedAt'),
      dataIndex: 'gmtCreate',
      key: 'gmtCreate',
      width: 170,
      sorter: (a, b) => (a.gmtCreate ?? '').localeCompare(b.gmtCreate ?? ''),
      render: (d: string) => <Text type="secondary">{formatDateTime(d)}</Text>,
    },
    {
      title: t('topic.colModifiedAt'),
      dataIndex: 'gmtModified',
      key: 'gmtModified',
      width: 170,
      sorter: (a, b) => (a.gmtModified ?? '').localeCompare(b.gmtModified ?? ''),
      render: (d: string) => <Text type="secondary">{formatDateTime(d)}</Text>,
    },
    {
      title: t('common.actions'),
      key: 'action',
      // 4 个小按钮实测 274px + 单元格左 padding 8px = 282px；按钮右对齐贴住表格右缘，
      // 与 Group 管理页操作列样式保持一致。勿随意改小：列宽不足时按钮溢出产生横向滚动条。
      // 宽度由 TopicPage.test.tsx 「keeps the action column wide enough」用例守护。
      width: 282,
      render: (_: unknown, record: Topic) => (
        <Flex gap={6} justify="flex-end" onClick={(e) => e.stopPropagation()}>
          <Button
            size="small"
            icon={<EyeOutlined />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={() => handleAction('detail', record)}
          >
            {t('topic.btnDetail')}
          </Button>
          <Button
            size="small"
            icon={<EditOutlined />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={() => handleAction('config', record)}
          >
            {t('topic.btnConfig')}
          </Button>
          {canSendTestMessage(record) && (
            <Button
              size="small"
              icon={<SendOutlined />}
              style={{ borderColor: '#52c41a', color: '#52c41a' }}
              onClick={() => handleAction('send', record)}
            >
              {t('topic.btnSend')}
            </Button>
          )}
          <Button
            size="small"
            icon={<DeleteOutlined />}
            style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
            onClick={() => handleAction('delete', record)}
          >
            {t('common.delete')}
          </Button>
        </Flex>
      ),
    },
  ];

  const renderRouteStatusTag = (status: RouteDiagnosticStatus) => {
    const meta = ROUTE_STATUS_META[status];
    return (
      <Tag color={meta.color} icon={meta.icon}>
        {t(meta.label)}
      </Tag>
    );
  };

  const renderRouteIssueTags = (issues: RouteDiagnosticIssue[]) => {
    if (issues.length === 0) return <Text type="secondary">{t('common.none')}</Text>;
    return (
      <Space size={[4, 4]} wrap>
        {issues.slice(0, 3).map((item) => (
          <Tag key={item.id} color={ISSUE_SEVERITY_COLOR[item.severity]}>
            {item.title}
          </Tag>
        ))}
        {issues.length > 3 && <Tag>+{issues.length - 3}</Tag>}
      </Space>
    );
  };

  // ─── Route table columns ──────────────────────────────────────
  const routeColumns: TableColumnsType<RouteDistribution> = [
    {
      title: 'Broker',
      dataIndex: 'brokerName',
      key: 'brokerName',
      width: 170,
      render: (_: string, record) => (
        <Space direction="vertical" size={2}>
          <Text strong>{record.brokerName}</Text>
          {renderRouteStatusTag(record.status)}
        </Space>
      ),
    },
    {
      title: t('topic.colAddrTopology'),
      key: 'brokerAddr',
      width: 260,
      render: (_: unknown, record) => (
        <Space direction="vertical" size={2} style={{ width: '100%' }}>
          <Text code copyable style={{ fontSize: 14 }}>
            {record.brokerAddr}
          </Text>
          {record.masterAddr && record.masterAddr !== record.brokerAddr && (
            <Text type="secondary" style={{ fontSize: 14 }}>
              Master {record.masterAddr}
            </Text>
          )}
          <Space size={4} wrap>
            {record.brokerIds.length > 0 ? (
              record.brokerIds.map((id) => (
                <Tag key={id} color={id === '0' ? 'blue' : undefined}>
                  {id === '0' ? 'Master' : `Replica ${id}`}
                </Tag>
              ))
            ) : (
              <Tag color="warning">{t('topic.addrUnknown')}</Tag>
            )}
          </Space>
        </Space>
      ),
    },
    {
      title: t('topic.colQueueDistribution'),
      key: 'queues',
      width: 220,
      render: (_: unknown, record) => (
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <div>
            <Flex justify="space-between">
              <Text>{t('topic.writeQueues', { count: record.writeQueues })}</Text>
              <Text type="secondary">{formatPercent(record.writeShare)}</Text>
            </Flex>
            <Progress percent={record.writeShare} showInfo={false} size="small" />
          </div>
          <div>
            <Flex justify="space-between">
              <Text>{t('topic.readQueues', { count: record.readQueues })}</Text>
              <Text type="secondary">{formatPercent(record.readShare)}</Text>
            </Flex>
            <Progress percent={record.readShare} showInfo={false} size="small" />
          </div>
        </Space>
      ),
    },
    {
      title: t('topic.colPerm'),
      dataIndex: 'perm',
      key: 'perm',
      width: 130,
      render: (_: string, record) => (
        <Space direction="vertical" size={4}>
          <Tag>{t(PERM_LABEL[record.perm] ?? '') || record.perm}</Tag>
          <Space size={4}>
            <Tag color={record.readable ? 'success' : 'error'}>{t('topic.permRead')}</Tag>
            <Tag color={record.writable ? 'success' : 'error'}>{t('topic.permWrite')}</Tag>
          </Space>
        </Space>
      ),
    },
    {
      title: t('topic.colDiagnostics'),
      key: 'diagnostics',
      width: 220,
      render: (_: unknown, record) => renderRouteIssueTags(record.issues),
    },
  ];

  // ─── Consumer table columns ───────────────────────────────────
  const consumerColumns: TableColumnsType<ConsumerGroupInfo> = [
    {
      title: t('topic.colConsumerGroup'),
      dataIndex: 'group',
      key: 'group',
      render: (group: string) =>
        selectedInstanceId ? (
          <Typography.Link
            onClick={() =>
              navigate(
                `/instance/${encodeURIComponent(selectedInstanceId)}/consumer?group=${encodeURIComponent(group)}`,
              )
            }
          >
            {group}
          </Typography.Link>
        ) : (
          group
        ),
    },
    {
      title: t('topic.colMessageModel'),
      dataIndex: 'messageModel',
      key: 'messageModel',
      render: (m: string) => {
        // The API sends enum values ("BROADCASTING"/"CLUSTERING" and vendor case
        // variants), never the legacy display strings the old comparison matched.
        const normalized = (m ?? '').trim().toUpperCase();
        const isBroadcast = normalized.includes('BROADCAST');
        const label = isBroadcast
          ? t('topic.broadcast')
          : normalized.includes('CLUSTER')
            ? t('topic.clustering')
            : m;
        return <Tag color={isBroadcast ? 'orange' : 'blue'}>{label}</Tag>;
      },
    },
    {
      title: t('topic.colConsumeTps'),
      dataIndex: 'consumeTps',
      key: 'consumeTps',
      render: (n: number, record) =>
        record.metricsAvailable === false ? (
          <Text type="secondary">{t('common.unavailable')}</Text>
        ) : (
          formatNumber(n)
        ),
    },
    {
      title: t('topic.colLag'),
      dataIndex: 'diffTotal',
      key: 'diffTotal',
      render: (n: number, record) =>
        record.metricsAvailable === false || !isLagAvailable(n) ? (
          <Text type="secondary">{t('common.unavailable')}</Text>
        ) : (
          <Text type={n > 100 ? 'warning' : undefined}>{formatNumber(n)}</Text>
        ),
    },
  ];

  const renderRouteMetric = (label: string, value: React.ReactNode, extra?: React.ReactNode) => (
    <Col xs={12} md={6}>
      <div
        style={{
          border: '1px solid #f0f0f0',
          borderRadius: 6,
          padding: '10px 12px',
          minHeight: 78,
          background: '#fafafa',
        }}
      >
        <Text type="secondary" style={{ display: 'block', fontSize: 14 }}>
          {label}
        </Text>
        <Text strong style={{ fontSize: 20, fontVariantNumeric: 'tabular-nums' }}>
          {value}
        </Text>
        {extra && (
          <div style={{ marginTop: 2 }}>
            <Text type="secondary" style={{ fontSize: 14 }}>
              {extra}
            </Text>
          </div>
        )}
      </div>
    </Col>
  );

  const renderRouteIssues = (issues: RouteDiagnosticIssue[]) => {
    if (issues.length === 0) return null;
    return (
      <div
        data-testid="topic-route-issues"
        style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 12 }}
      >
        <Text strong style={{ display: 'block', marginBottom: 8 }}>
          {t('topic.diagnosticIssues')}
        </Text>
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          {issues.map((item) => (
            <Flex key={item.id} align="flex-start" gap={8}>
              <Tag color={ISSUE_SEVERITY_COLOR[item.severity]} style={{ marginTop: 1 }}>
                {t(
                  item.severity === 'critical' ? 'topic.severityCritical' : 'topic.severityWarning',
                )}
              </Tag>
              <div>
                <Text strong>
                  {item.brokerName ? `${item.brokerName}：${item.title}` : item.title}
                </Text>
                <Text type="secondary" style={{ display: 'block' }}>
                  {item.description}
                </Text>
              </div>
            </Flex>
          ))}
        </Space>
      </div>
    );
  };

  const renderRouteRecommendations = (recommendations: string[]) => {
    if (recommendations.length === 0) return null;
    return (
      <InfoBanner
        title={t('topic.recommendations')}
        description={
          <Space direction="vertical" size={2}>
            {recommendations.map((item) => (
              <Text key={item} style={{ fontSize: 14 }}>
                {item}
              </Text>
            ))}
          </Space>
        }
      />
    );
  };

  const renderRouteSection = (topic: Topic) => {
    const routes = getRoutes(topic.name);
    const diagnostics = analyzeTopicRoutes(routes);
    const summary = diagnostics.summary;
    const routeLoadFailed = routeLoadFailedTopics[topic.name] === true;

    return (
      <>
        <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 12 }}>
          {t('topic.routeInfo')}
        </Text>
        {!detailLoading && (
          <Space direction="vertical" size={12} style={{ width: '100%', marginBottom: 12 }}>
            <Alert
              type={routeLoadFailed ? 'error' : diagnostics.statusColor}
              showIcon
              message={t('topic.routeDiagnostics', {
                status: routeLoadFailed ? t('topic.routeLoadFailedStatus') : diagnostics.statusText,
              })}
              description={
                routeLoadFailed
                  ? t('topic.routeLoadFailedDesc')
                  : diagnostics.status === 'healthy'
                    ? t('topic.routeSummaryHealthy', {
                        brokers: summary.brokerCount,
                        write: summary.totalWriteQueues,
                        read: summary.totalReadQueues,
                      })
                    : t('topic.routeSummaryIssues', { count: diagnostics.issues.length })
              }
              action={
                routeLoadFailed ? (
                  <Button size="small" onClick={() => void openDetail(topic)}>
                    重试
                  </Button>
                ) : routes.length === 0 ? (
                  <Button
                    size="small"
                    type="primary"
                    loading={rebuilding}
                    onClick={() => void rebuildTopic(topic)}
                  >
                    {t('topic.btnRebuildOnBroker')}
                  </Button>
                ) : undefined
              }
            />
            <Row gutter={[12, 12]}>
              {renderRouteMetric(
                t('topic.metricBrokers'),
                summary.brokerCount,
                t('topic.metricAddresses', { count: summary.addressCount }),
              )}
              {renderRouteMetric(
                t('topic.metricWritableBrokers'),
                summary.writableBrokerCount,
                t('topic.metricWriteQueues', { count: summary.totalWriteQueues }),
              )}
              {renderRouteMetric(
                t('topic.metricReadableBrokers'),
                summary.readableBrokerCount,
                t('topic.metricReadQueues', { count: summary.totalReadQueues }),
              )}
              {renderRouteMetric(
                t('topic.metricReplicas'),
                summary.replicaCount,
                summary.writeSkew.gap > 0 || summary.readSkew.gap > 0
                  ? t('topic.queueSkew', {
                      write: summary.writeSkew.gap,
                      read: summary.readSkew.gap,
                    })
                  : t('topic.queuesBalanced'),
              )}
            </Row>
            {renderRouteIssues(diagnostics.issues)}
            {renderRouteRecommendations(diagnostics.recommendations)}
          </Space>
        )}
        <Table<RouteDistribution>
          columns={routeColumns}
          dataSource={detailLoading ? [] : diagnostics.distributions}
          rowKey="key"
          pagination={false}
          size="small"
          loading={detailLoading}
          tableLayout="fixed"
          scroll={{ x: tableScrollX(routeColumns) }}
        />
      </>
    );
  };

  // ─── Modal: detail tab ────────────────────────────────────────
  const renderDetailTab = (topic: Topic) => {
    const cluster = CLUSTER_NAME_MAP[topic.clusterId];
    const clusterType = cluster ? CLUSTER_TYPE_MAP[cluster.type] : null;
    const typeInfo = TOPIC_TYPE_MAP[topic.type];

    return (
      <Descriptions bordered column={2} size="small" styles={{ label: { fontWeight: 500 } }}>
        <Descriptions.Item label={t('topic.colName')} span={2}>
          {topic.name}
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.colType')}>
          <Tag color={typeInfo?.color}>
            {typeInfo?.labelKey ? t(typeInfo.labelKey) : topic.type}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.cluster')} span={2}>
          <Space>
            <Text>{topic.clusterId}</Text>
            {clusterType && <Tag color={clusterType.color}>{t(clusterType.labelKey)}</Tag>}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.writeQueueCount')}>
          {topic.writeQueues}
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.readQueueCount')}>{topic.readQueues}</Descriptions.Item>
        <Descriptions.Item label={t('topic.colPerm')}>
          <Tag>{PERM_LABEL[topic.perm]}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.todayMessages')}>
          {formatNumber(topic.messageCount)}
        </Descriptions.Item>
        <Descriptions.Item label="TPS">{formatNumber(topic.tps)}</Descriptions.Item>
        <Descriptions.Item label={t('topic.consumerGroupCount')}>
          {topic.consumerGroupCount}
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.colCreatedAt')} span={2}>
          {formatDateTime(topic.gmtCreate)}
        </Descriptions.Item>
      </Descriptions>
    );
  };

  // ─── Create / edit modal submit ───────────────────────────────
  const handleCreate = async () => {
    if (createInFlightRef.current) return;
    if (!selectedInstanceId) {
      message.error(t('topic.selectInstanceFirst'));
      return;
    }
    createInFlightRef.current = true;
    setCreating(true);
    try {
      const values = await form.validateFields();
      if (editingTopic) {
        const updated = await updateTopic({
          ...values,
          instanceId: selectedInstanceId,
        });
        await reloadTopicPage();
        message.success(t('topic.updateCompleted', { name: updated.name }));
      } else {
        const created = await createTopic({
          ...values,
          instanceId: selectedInstanceId,
        });
        await reloadTopicPage();
        message.success(t('topic.createCompleted', { name: created.name }));
      }
      setModalOpen(false);
      setEditingTopic(null);
      form.resetFields();
    } catch (error) {
      if (!(error && typeof error === 'object' && 'errorFields' in error)) {
        message.error(t(editingTopic ? 'topic.updateFailed' : 'topic.createFailed'));
      }
    } finally {
      createInFlightRef.current = false;
      setCreating(false);
    }
  };

  // Classic dashboard parity: the topic row's CONFIG action reuses the create
  // dialog in update mode — name/type stay fixed, queue counts and perm change.
  const openEditConfig = (topic: Topic) => {
    setEditingTopic(topic);
    form.setFieldsValue({
      name: topic.name,
      type: topic.type,
      writeQueues: topic.writeQueues,
      readQueues: topic.readQueues,
      perm: topic.perm,
      remark: topic.remark,
    });
    setModalOpen(true);
  };

  const handleImportFile = async (file: File) => {
    if (!selectedInstanceId) {
      message.error(t('topic.selectInstanceFirst'));
      return;
    }
    setImportFilename(file.name);
    setImporting(false);
    setImportModalOpen(true);
    try {
      const records = parseCsvTable(await file.text());
      const validation = validateTopicCsvImport(records, selectedInstanceId || undefined);
      setImportRows(validation.rows);
      setImportErrors(validation.errors);
    } catch (error) {
      setImportRows([]);
      setImportErrors([error instanceof Error ? error.message : t('topic.csvParseFailed')]);
    } finally {
      if (importInputRef.current) importInputRef.current.value = '';
    }
  };

  const handleImportTopics = async () => {
    if (!selectedInstanceId) {
      message.error(t('topic.selectInstanceFirst'));
      return;
    }
    const targetIndexes = importRows
      .map((row, index) => ({ row, index }))
      .filter(({ row }) => row.status === 'pending' || row.status === 'failed');
    if (targetIndexes.length === 0 || importErrors.length > 0) return;

    setImporting(true);
    const nextRows = importRows.map((row) => ({ ...row }));
    let createdTopics: Topic[] = [];

    try {
      const result = await importTopics(
        selectedInstanceId,
        targetIndexes.map(({ row }) => row.payload),
      );
      createdTopics = result.topics;
      const failureByIndex = new Map(result.failures.map((failure) => [failure.index, failure]));
      targetIndexes.forEach(({ index }, requestIndex) => {
        const failure = failureByIndex.get(requestIndex);
        nextRows[index] = failure
          ? {
              ...nextRows[index],
              status: 'failed',
              message: failure.message || t('topic.rowCreateFailed'),
            }
          : { ...nextRows[index], status: 'success', message: t('topic.rowCreated') };
      });
    } catch (error) {
      for (const { index } of targetIndexes) {
        nextRows[index] = {
          ...nextRows[index],
          status: 'failed',
          message: error instanceof Error ? error.message : t('topic.rowCreateFailed'),
        };
      }
    } finally {
      setImporting(false);
    }
    setImportRows([...nextRows]);

    if (createdTopics.length > 0) {
      // The inventory is server-paginated, so a local prepend leaves the rows,
      // the header count and the pagination total disagreeing with the server.
      await reloadTopicPage();
    }

    const failedCount = nextRows.filter((row) => row.status === 'failed').length;
    const invalidCount = nextRows.filter((row) => row.status === 'invalid').length;
    if (failedCount === 0) {
      if (invalidCount > 0) {
        message.warning(
          t('topic.importedWithInvalid', { created: createdTopics.length, invalid: invalidCount }),
        );
      } else {
        message.success(t('topic.imported', { count: createdTopics.length }));
      }
    } else if (createdTopics.length > 0) {
      message.warning(
        t('topic.importedWithFailed', { created: createdTopics.length, failed: failedCount }),
      );
    } else {
      message.error(t('topic.importAllFailed', { count: failedCount }));
    }
  };

  const topicImportColumns: TableColumnsType<ResourceImportRow<Partial<Topic>>> = [
    { title: t('topic.colLineNumber'), dataIndex: 'lineNumber', key: 'lineNumber', width: 80 },
    { title: t('topic.colName'), dataIndex: 'name', key: 'name' },
    {
      title: t('topic.colStatus'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: ResourceImportRow<Partial<Topic>>['status']) => {
        if (status === 'success') return <Tag color="success">{t('topic.importSuccess')}</Tag>;
        if (status === 'failed') return <Tag color="error">{t('topic.importFailed')}</Tag>;
        if (status === 'invalid') return <Tag color="warning">{t('topic.importInvalid')}</Tag>;
        return <Tag>{t('topic.importPending')}</Tag>;
      },
    },
    {
      title: t('topic.colDescription'),
      dataIndex: 'message',
      key: 'message',
      render: (text?: string) => text || '-',
    },
  ];

  const renderPayloadIssues = (issues: MessagePayloadIssue[]) => {
    if (issues.length === 0) {
      return <Text type="secondary">{t('topic.precheckNoIssues')}</Text>;
    }
    return (
      <Space direction="vertical" size={6} style={{ width: '100%' }}>
        {issues.map((item, index) => (
          <Flex
            key={`${item.code}-${item.names?.join(',') ?? index}`}
            align="flex-start"
            gap={8}
            wrap="nowrap"
          >
            <Tag color={PAYLOAD_ISSUE_COLOR[item.severity]} style={{ marginTop: 1 }}>
              {t(
                item.severity === 'error'
                  ? 'topic.severityBlocked'
                  : item.severity === 'warning'
                    ? 'topic.severityWarning'
                    : 'topic.severityInfo',
              )}
            </Tag>
            <div style={{ minWidth: 0 }}>
              <Text strong>{item.title}</Text>
              <Text type="secondary" style={{ display: 'block' }}>
                {item.description}
              </Text>
            </div>
          </Flex>
        ))}
      </Space>
    );
  };

  const renderSendPayloadPreview = () => {
    const statusMeta = PAYLOAD_STATUS_META[sendPayloadPreview.status];
    const propertyPreview = sendPayloadPreview.propertyEntries.slice(0, 6);
    const hiddenPropertyCount = sendPayloadPreview.propertyEntries.length - propertyPreview.length;

    return (
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Alert
          showIcon
          type={statusMeta.alertType}
          message={
            <Flex gap={8} align="center" wrap>
              <span>{t('topic.sendPrecheck')}</span>
              <Tag color={statusMeta.color}>{t(statusMeta.label)}</Tag>
              <Tag>{t(BODY_FORMAT_LABEL[sendPayloadPreview.summary.bodyFormat])}</Tag>
            </Flex>
          }
          description={
            sendPayloadPreview.blockingIssues.length > 0
              ? t('topic.precheckBlockingCount', {
                  count: sendPayloadPreview.blockingIssues.length,
                })
              : t('topic.precheckSummary')
          }
        />

        <Flex gap={8} wrap>
          <Tag>Body {formatBytes(sendPayloadPreview.summary.bodyBytes)}</Tag>
          <Tag>{t('topic.propCount', { count: sendPayloadPreview.summary.propertyCount })}</Tag>
          <Tag>
            {t('topic.propBytes', { size: formatBytes(sendPayloadPreview.summary.propertyBytes) })}
          </Tag>
          <Tag color={sendPayloadPreview.normalized.tag ? 'blue' : undefined}>
            Tag {sendPayloadPreview.normalized.tag || '-'}
          </Tag>
          <Tag color={sendPayloadPreview.normalized.key ? 'blue' : undefined}>
            Key {sendPayloadPreview.normalized.key || '-'}
          </Tag>
        </Flex>

        <Descriptions bordered size="small" column={1}>
          <Descriptions.Item label="Topic">
            <Text code>{sendPayloadPreview.normalized.topic || sendTopic?.name || '-'}</Text>
          </Descriptions.Item>
          <Descriptions.Item label={t('topic.bodyType')}>
            {t(BODY_FORMAT_LABEL[sendPayloadPreview.summary.bodyFormat])} /{' '}
            {formatBytes(sendPayloadPreview.summary.bodyBytes)}
          </Descriptions.Item>
          <Descriptions.Item label={t('topic.customProps')}>
            {propertyPreview.length === 0 ? (
              <Text type="secondary">{t('common.none')}</Text>
            ) : (
              <Space size={[4, 4]} wrap>
                {propertyPreview.map((entry) => (
                  <Tag key={entry.key} color={entry.reserved ? 'warning' : undefined}>
                    {entry.key}={entry.value || '""'}
                  </Tag>
                ))}
                {hiddenPropertyCount > 0 && <Tag>+{hiddenPropertyCount}</Tag>}
              </Space>
            )}
          </Descriptions.Item>
        </Descriptions>

        {renderPayloadIssues(sendPayloadPreview.issues)}
      </Space>
    );
  };

  // ─── Send message modal submit ────────────────────────────────
  const handleSend = async () => {
    let values: SendMessageFormValues;
    try {
      values = await sendForm.validateFields();
    } catch {
      // validation error, keep the modal open
      return;
    }
    setSending(true);
    try {
      const payloadPreview = analyzeMessagePayloadPreview({
        topic: values.topic,
        tag: values.tag,
        key: values.key,
        body: values.body,
        propsMode,
        propsText: values.propsText,
        properties: values.properties,
      });
      if (payloadPreview.blockingIssues.length > 0) {
        message.error(
          t('topic.precheckBlockedToast', {
            issues: payloadPreview.blockingIssues
              .map((item) => item.title)
              .join(t('topic.listSeparator')),
          }),
        );
        return;
      }
      const result = await sendTopicMessage({
        topic: payloadPreview.normalized.topic,
        instanceId: selectedInstanceId || undefined,
        tag: payloadPreview.normalized.tag,
        key: payloadPreview.normalized.key,
        body: payloadPreview.normalized.body,
        properties: payloadPreview.properties,
      });
      // Keep the modal open for consecutive sends
      message.success(t('topic.sendCompleted', { id: result.msgId }));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('topic.sendFailed'));
    } finally {
      setSending(false);
    }
  };

  // ═══════════════════════════════════════════════════════════════
  // RENDER
  // ═══════════════════════════════════════════════════════════════
  return (
    <div style={{ padding: 24 }}>
      {/* ── Header ────────────────────────────────────────────── */}
      <PageHeader
        title={t('topic.title')}
        subtitle={t('topic.subtitleCount', { count: totalTopics })}
      />

      {/* ── Current instance banner ───────────────────────────── */}
      {selectedInstance && (
        <InfoBanner>
          <Flex align="center" wrap="wrap" gap="8px 28px" style={{ fontSize: 14 }}>
            <span>
              <span style={{ color: '#8c8c8c', marginRight: 6 }}>{t('topic.currentInstance')}</span>
              <span>{selectedInstance.name}</span>
            </span>
            <span>
              <span style={{ color: '#8c8c8c', marginRight: 6 }}>{t('topic.accessMode')}</span>
              <span>{t(INSTANCE_ACCESS_LABEL[selectedInstance.type])}</span>
            </span>
            {selectedInstance.vendor === 'ALIYUN' && (
              <span>
                <span style={{ color: '#8c8c8c', marginRight: 6 }}>{t('topic.vendor')}</span>
                <span>{t('topic.vendorAliyun')}</span>
              </span>
            )}
            {selectedInstance.vendor === 'TENCENT' && (
              <span>
                <span style={{ color: '#8c8c8c', marginRight: 6 }}>{t('topic.vendor')}</span>
                <span>{t('topic.vendorTencent')}</span>
              </span>
            )}
            <span>
              <span style={{ color: '#8c8c8c', marginRight: 6 }}>{t('topic.endpoint')}</span>
              <Text code copyable style={{ fontSize: 16 }}>
                {selectedInstance.endpoint}
              </Text>
            </span>
          </Flex>
          <div style={{ marginTop: 10, fontSize: 14, lineHeight: 1.6, color: '#8c8c8c' }}>
            {t(INSTANCE_ACCESS_DESCRIPTION[selectedInstance.type])}
          </div>
        </InfoBanner>
      )}

      {/* ── Filter bar ────────────────────────────────────────── */}
      <Flex
        gap={12}
        wrap="wrap"
        style={{ marginBottom: 20 }}
        align="center"
        justify="space-between"
      >
        <Space size={12} wrap>
          <InstanceSelect
            value={selectedInstanceId || undefined}
            onChange={(value) => {
              closeSyncModal();
              setSelectedRowKeys([]);
              resetTablePage();
              selectInstance(value);
            }}
            options={instanceOptions}
            style={{ width: 220 }}
            failed={instancesFailed}
            onRetry={reloadInstances}
          />
          <Input.Search
            placeholder={t('topic.searchPlaceholder')}
            allowClear
            style={{ width: 260 }}
            onSearch={(value) => {
              // Store the trimmed term so the client-side row filter matches what the
              // server query used; padded input would otherwise filter out every row.
              setSelectedRowKeys([]);
              setSearchText(value.trim());
              resetTablePage();
            }}
            onChange={(e) => {
              if (!e.target.value) {
                setSelectedRowKeys([]);
                setSearchText('');
                resetTablePage();
              }
            }}
          />
          <Select
            placeholder={t('topic.typeFilterPlaceholder')}
            value={typeFilter}
            onChange={(value) => {
              setSelectedRowKeys([]);
              setTypeFilter(value);
              resetTablePage();
            }}
            options={TYPE_OPTIONS.map((o) => ({ ...o, label: t(o.label) }))}
            style={{ width: 140 }}
          />
        </Space>
        <Space>
          {selectedRowKeys.length > 0 && (
            <Button
              danger
              icon={<DeleteOutlined />}
              onClick={() => {
                Modal.confirm({
                  title: t('topic.batchDeleteConfirmTitle'),
                  content: t('topic.batchDeleteConfirmContent', { count: selectedRowKeys.length }),
                  okText: t('common.delete'),
                  okType: 'danger',
                  cancelText: t('common.cancel'),
                  onOk: async () => {
                    try {
                      const names = selectedRowKeys.map(String);
                      const { deleted, failed } = await batchDeleteTopics(
                        names,
                        selectedInstanceId || undefined,
                      );
                      if (deleted.length > 0) await reloadTopicPage();
                      setSelectedRowKeys(failed);

                      if (failed.length === 0) {
                        message.success(t('topic.batchDeleted', { count: deleted.length }));
                      } else if (deleted.length > 0) {
                        message.warning(
                          t('topic.batchDeletedWithFailed', {
                            deleted: deleted.length,
                            failed: failed.length,
                          }),
                        );
                      } else {
                        message.error(t('topic.batchDeleteFailedCount', { count: failed.length }));
                      }
                    } catch {
                      message.error(t('topic.batchDeleteFailed'));
                    }
                  },
                });
              }}
            >
              {t('topic.btnDeleteCount', { count: selectedRowKeys.length })}
            </Button>
          )}
          <input
            ref={importInputRef}
            type="file"
            accept=".csv,text/csv"
            data-testid="topic-import-file"
            style={{ display: 'none' }}
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) void handleImportFile(file);
            }}
          />
          <Button
            icon={<ImportOutlined />}
            disabled={!hasSelectedInstance || importing}
            onClick={() => importInputRef.current?.click()}
          >
            {t('topic.btnImport')}
          </Button>
          <Button icon={<ExportOutlined />} loading={exporting} onClick={() => void handleExport()}>
            {t('topic.btnExport')}
          </Button>
          <Button
            icon={<DiffOutlined />}
            disabled={instances.length < 2}
            onClick={() => setComparisonOpen(true)}
          >
            {t('topicCompare.open')}
          </Button>
          {!isCloudInstance && (
            <Button
              icon={<SyncOutlined />}
              disabled={!hasSelectedInstance || topics.length === 0}
              onClick={() => void openSyncModal()}
            >
              {t('topic.btnSyncData')}
            </Button>
          )}
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={!hasSelectedInstance}
            onClick={() => {
              setEditingTopic(null);
              form.resetFields();
              setModalOpen(true);
            }}
          >
            {t('topic.btnCreate')}
          </Button>
        </Space>
      </Flex>

      {/* ── Content ───────────────────────────────────────────── */}
      <Card styles={{ body: { padding: 0 } }} style={{ borderRadius: 8 }}>
        <Table<Topic>
          columns={columns}
          dataSource={filteredTopics}
          loading={loading}
          rowKey="name"
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          pagination={{
            current: currentTablePage,
            pageSize: tablePageSize,
            total: totalTopics,
            showSizeChanger: true,
            showTotal: (total) => t('topic.totalRows', { total }),
            onChange: (page, pageSize) => {
              setSelectedRowKeys([]);
              setTablePage(page);
              setTablePageSize(pageSize);
            },
          }}
          size="small"
          tableLayout="fixed"
          scroll={{ x: tableScrollX(columns, { selection: true }) }}
          onRow={(record) => ({
            onClick: () => void openDetail(record),
            style: { cursor: 'pointer' },
          })}
        />
      </Card>

      {comparisonOpen && (
        <TopicConfigComparisonDrawer
          open
          instances={instances}
          currentInstanceId={selectedInstanceId}
          onClose={() => setComparisonOpen(false)}
        />
      )}

      {/* ── Detail Modal ──────────────────────────────────────── */}
      <Modal
        title={selectedTopic?.name}
        open={detailModalOpen}
        onCancel={() => setDetailModalOpen(false)}
        width={1080}
        destroyOnHidden
        footer={null}
      >
        {selectedTopic && (
          <>
            {/* Section 1: 基本信息 */}
            <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 12 }}>
              {t('topic.sectionBasic')}
            </Text>
            {renderDetailTab(selectedTopic)}

            {!isCloudInstance && (
              <>
                <Divider style={{ margin: '20px 0 16px' }} />

                {/* Section 2: 路由信息 */}
                {renderRouteSection(selectedTopic)}
              </>
            )}

            <Divider style={{ margin: '20px 0 16px' }} />

            {/* Section 3: 消费者 */}
            <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 12 }}>
              {t('topic.sectionConsumers')}
            </Text>
            <Table<ConsumerGroupInfo>
              columns={consumerColumns}
              dataSource={getConsumerPage(selectedTopic.name).items}
              rowKey="group"
              // An empty table is only "nobody consumes this" after a load that succeeded.
              locale={{
                emptyText:
                  consumerLoadFailedTopics[selectedTopic.name] === true
                    ? '消费者加载失败，请重试'
                    : undefined,
              }}
              pagination={{
                current: getConsumerPage(selectedTopic.name).page,
                pageSize: getConsumerPage(selectedTopic.name).pageSize,
                total: getConsumerPage(selectedTopic.name).total,
                showSizeChanger: true,
                pageSizeOptions: [10, 20, 50, 100],
                onChange: (page, pageSize) => {
                  void loadTopicConsumers(selectedTopic, page, pageSize);
                },
              }}
              size="small"
            />
          </>
        )}
      </Modal>

      {/* ── Create / Edit Topic Modal ─────────────────────────── */}
      <Modal
        title={editingTopic ? t('topic.editTitle') : t('topic.createTitle')}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          setEditingTopic(null);
          form.resetFields();
        }}
        onOk={handleCreate}
        confirmLoading={creating}
        okText={editingTopic ? t('topic.btnSave') : t('topic.btnCreateShort')}
        cancelText={t('common.cancel')}
        width={560}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            writeQueues: 8,
            readQueues: 8,
            perm: 'RW',
            type: 'NORMAL',
          }}
          style={{ marginTop: 16 }}
        >
          <Form.Item
            label={t('topic.colName')}
            name="name"
            rules={[
              { required: true, message: t('topic.nameRequired') },
              {
                pattern: RESOURCE_NAME_PATTERN,
                message: t('topic.namePattern'),
              },
              {
                max: RESOURCE_NAME_MAX_LENGTH.topic,
                message: t('topic.nameMaxLength', { max: RESOURCE_NAME_MAX_LENGTH.topic }),
              },
            ]}
          >
            <Input placeholder={t('topic.namePlaceholder')} disabled={!!editingTopic} />
          </Form.Item>

          <Form.Item
            label={t('topic.colType')}
            name="type"
            rules={[{ required: true }]}
            extra={t(TOPIC_TYPE_CARDS.find((c) => c.value === createTopicType)?.desc ?? '')}
          >
            <Segmented
              disabled={!!editingTopic}
              options={TOPIC_TYPE_CARDS.filter((c) => !isCloudInstance || c.value !== 'LITE').map(
                ({ value, label }) => ({ value, label: t(label) }),
              )}
            />
          </Form.Item>

          {!isCloudInstance && (
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  label={t('topic.writeQueueCount')}
                  name="writeQueues"
                  rules={[{ required: true }]}
                  extra={t('topic.queuesPerBrokerHint')}
                >
                  <InputNumber min={1} max={256} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  label={t('topic.readQueueCount')}
                  name="readQueues"
                  rules={[{ required: true }]}
                  extra={t('topic.queuesPerBrokerHint')}
                >
                  <InputNumber min={1} max={256} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
          )}

          {!isCloudInstance && (
            <Form.Item label={t('topic.colPerm')} name="perm" rules={[{ required: true }]}>
              <Radio.Group>
                <Radio.Button value="RW">{t('topic.permRw')}</Radio.Button>
                <Radio.Button value="RO">{t('topic.permRo')}</Radio.Button>
                <Radio.Button value="WO">{t('topic.permWo')}</Radio.Button>
              </Radio.Group>
            </Form.Item>
          )}

          <Form.Item label={t('topic.colRemark')} name="remark">
            <Input.TextArea rows={3} placeholder={t('topic.remarkPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Import Topic Modal ────────────────────────────────── */}
      <Modal
        title={
          importFilename
            ? t('topic.importModalTitleWithFile', { filename: importFilename })
            : t('topic.importModalTitle')
        }
        open={importModalOpen}
        onCancel={() => {
          if (!importing) setImportModalOpen(false);
        }}
        onOk={() => void handleImportTopics()}
        okText={
          importRows.some((row) => row.status === 'failed')
            ? t('topic.retryFailedRows')
            : t('topic.startImport')
        }
        cancelText={t('topic.btnClose')}
        confirmLoading={importing}
        okButtonProps={{
          disabled:
            importErrors.length > 0 ||
            importRows.length === 0 ||
            importRows.every((row) => row.status === 'success' || row.status === 'invalid'),
        }}
        width={720}
        destroyOnHidden
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          {importErrors.length > 0 ? (
            <Alert
              type="error"
              showIcon
              message={t('topic.csvNotImportable')}
              description={importErrors.join(t('topic.listSeparator'))}
            />
          ) : importRows.some((row) => row.status === 'invalid') ? (
            <Alert
              type="warning"
              showIcon
              message={t('topic.detectedInvalidRows', {
                count: importRows.filter((row) => row.status === 'invalid').length,
              })}
              description={t('topic.importFieldsNote')}
            />
          ) : (
            <Alert
              type="info"
              showIcon
              message={t('topic.detectedTopics', { count: importRows.length })}
              description={t('topic.importFieldsNote')}
            />
          )}
          <Table<ResourceImportRow<Partial<Topic>>>
            columns={topicImportColumns}
            dataSource={importRows}
            rowKey="key"
            size="small"
            pagination={false}
          />
        </Space>
      </Modal>

      {/* ── Send Message Modal ──────────────────────────────────── */}
      <Modal
        title={
          <Space>
            <SendOutlined />
            <span>{t('topic.sendTo', { name: sendTopic?.name ?? '' })}</span>
          </Space>
        }
        open={sendModalOpen}
        onCancel={() => {
          setSendModalOpen(false);
          sendForm.resetFields();
        }}
        onOk={handleSend}
        okText={t('topic.btnSend')}
        cancelText={t('common.cancel')}
        confirmLoading={sending}
        width={640}
        destroyOnHidden
      >
        <Form
          form={sendForm}
          layout="vertical"
          initialValues={{ topic: sendTopic?.name, tag: '', key: '', body: '', properties: [] }}
          style={{ marginTop: 16 }}
        >
          <Form.Item label="Topic" name="topic" rules={[{ required: true }]}>
            <Input disabled />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="Tag" name="tag">
                <Input placeholder={t('topic.tagPlaceholder')} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Key" name="key">
                <Input placeholder={t('topic.keyPlaceholder')} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            label={t('topic.bodyLabel')}
            name="body"
            rules={[{ required: true, message: t('topic.bodyRequired') }]}
          >
            <Input.TextArea
              rows={8}
              placeholder={t('topic.bodyPlaceholder')}
              style={{ fontFamily: 'monospace', fontSize: 14 }}
            />
          </Form.Item>
          <Flex gap={12} style={{ marginTop: -8, marginBottom: 16 }}>
            <Text type="secondary" style={{ fontSize: 14, flexShrink: 0 }}>
              {t('topic.quickFill')}
            </Text>
            <Space size={4} wrap>
              {RANDOM_BODY_GENERATORS.map((gen) => (
                <Button
                  key={gen.label}
                  type="text"
                  size="small"
                  onClick={() => sendForm.setFieldValue('body', gen.fn())}
                  style={{ fontSize: 14, color: '#8c8c8c', height: 22, padding: '0 6px' }}
                >
                  {t(gen.label)}
                </Button>
              ))}
            </Space>
          </Flex>

          <Divider style={{ margin: '8px 0 16px' }} orientation="left" plain>
            {t('topic.customPropsOptional')}
          </Divider>

          <Flex justify="space-between" align="center" style={{ marginBottom: 12 }}>
            <Segmented
              size="small"
              value={propsMode}
              onChange={(value) => setPropsMode(value as 'form' | 'text')}
              options={[
                { label: t('topic.propsModeForm'), value: 'form' },
                { label: t('topic.propsModeText'), value: 'text' },
              ]}
            />
            {propsMode === 'text' && (
              <Text type="secondary" style={{ fontSize: 14 }}>
                {t('topic.propsTextHint')}
              </Text>
            )}
          </Flex>

          {propsMode === 'text' ? (
            <Form.Item name="propsText" style={{ marginBottom: 0 }}>
              <Input.TextArea
                rows={5}
                placeholder={'TAGS=tagA\nKEY1=value1, KEY2=value2'}
                style={{ fontFamily: 'monospace', fontSize: 14 }}
              />
            </Form.Item>
          ) : (
            <Form.List name="properties">
              {(fields, { add, remove }) => (
                <>
                  {fields.map(({ key, name, ...rest }) => (
                    <Row gutter={8} key={key} align="middle" style={{ marginBottom: 8 }}>
                      <Col span={10}>
                        <Form.Item {...rest} name={[name, 'key']} style={{ marginBottom: 0 }}>
                          <Input placeholder={t('topic.propKeyPlaceholder')} />
                        </Form.Item>
                      </Col>
                      <Col span={10}>
                        <Form.Item {...rest} name={[name, 'value']} style={{ marginBottom: 0 }}>
                          <Input placeholder={t('topic.propValuePlaceholder')} />
                        </Form.Item>
                      </Col>
                      <Col span={4}>
                        <MinusCircleOutlined
                          style={{ color: '#ff4d4f', fontSize: 18, cursor: 'pointer' }}
                          onClick={() => remove(name)}
                        />
                      </Col>
                    </Row>
                  ))}
                  <Button type="dashed" onClick={() => add()} block icon={<PlusCircleOutlined />}>
                    {t('topic.addProp')}
                  </Button>
                </>
              )}
            </Form.List>
          )}

          <Divider style={{ margin: '20px 0 16px' }} orientation="left" plain>
            {t('topic.sendPrecheck')}
          </Divider>
          {renderSendPayloadPreview()}
        </Form>
      </Modal>

      <Modal
        title={t('topic.btnSyncData')}
        open={syncModalOpen}
        onCancel={closeSyncModal}
        footer={<Button onClick={closeSyncModal}>{t('topic.btnClose')}</Button>}
        width={680}
        destroyOnHidden
      >
        {syncChecking ? (
          <Flex justify="center" align="center" style={{ padding: 48 }}>
            <Spin tip={t('topic.syncChecking')}>
              <div style={{ width: 200 }} />
            </Spin>
          </Flex>
        ) : syncMissing.length === 0 ? (
          <div style={{ padding: '16px 0' }}>
            {syncFailedCount > 0 ? (
              renderSyncFailureAlert()
            ) : (
              <Text type="secondary">
                {t('topic.syncAllPresent', { count: syncCheckedCount })}
              </Text>
            )}
          </div>
        ) : (
          <>
            {renderSyncFailureAlert()}
            <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
              {t('topic.syncMissingDescription', { count: syncMissing.length })}
            </Text>
            <Table<Topic>
              dataSource={syncMissing}
              rowKey="name"
              size="small"
              pagination={false}
              columns={[
                { title: 'Topic', dataIndex: 'name', key: 'name' },
                {
                  title: t('topic.colQueueCount'),
                  key: 'queues',
                  width: 110,
                  render: (_: unknown, topic: Topic) =>
                    `${topic.writeQueues ?? '-'} / ${topic.readQueues ?? '-'}`,
                },
                {
                  title: t('topic.colStatus'),
                  key: 'status',
                  width: 100,
                  render: (_: unknown, topic: Topic) =>
                    syncedTopics.has(topic.name) ? (
                      <Tag color="green">{t('topic.syncDone')}</Tag>
                    ) : (
                      <Tag color="orange">{t('topic.syncMissingRoute')}</Tag>
                    ),
                },
                {
                  title: t('common.actions'),
                  key: 'action',
                  width: 90,
                  render: (_: unknown, topic: Topic) => (
                    <Button
                      size="small"
                      type="link"
                      loading={syncingKeys.has(topic.name)}
                      disabled={syncedTopics.has(topic.name)}
                      onClick={() => void syncTopicToBroker(topic)}
                    >
                      {t('topic.btnSync')}
                    </Button>
                  ),
                },
              ]}
            />
          </>
        )}
      </Modal>
    </div>
  );
};

const TopicPage = () => {
  const instanceFilter = useInstanceFilter();
  return (
    <TopicPageContent
      key={instanceFilter.selectedInstanceId || 'no-selected-instance'}
      {...instanceFilter}
    />
  );
};

export default TopicPage;
