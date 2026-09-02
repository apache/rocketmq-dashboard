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
  ImportOutlined,
  ExportOutlined,
  SyncOutlined,
  PlusCircleOutlined,
  MinusCircleOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import PageHeader from '../../components/PageHeader';
import InfoBanner from '../../components/InfoBanner';
import { InstanceSelect } from '../../components/InstanceSelect';
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
import { downloadCsv } from '../../utils/download';
import { parseMessageProperties } from '../../utils/messageProperties';
import { tableScrollX } from '../../utils/table';
import {
  analyzeTopicRoutes,
  type RouteDiagnosticIssue,
  type RouteDiagnosticStatus,
  type RouteDistribution,
} from '../../utils/topicRouteDiagnostics';

const { Text } = Typography;

const INSTANCE_ACCESS_LABEL: Record<Instance['type'], string> = {
  CLOUD: '云服务',
  PROXY_LOCAL: 'Proxy Local',
  PROXY_CLUSTER: 'Proxy Cluster',
  DIRECT: 'Direct',
};

const INSTANCE_ACCESS_DESCRIPTION: Record<Instance['type'], string> = {
  CLOUD:
    '接入点为云厂商托管实例的接入地址，由云实例目录解析得出。若客户端环境无法解析该地址，请自行配置 DNS 解析或在客户端 hosts 中映射。',
  PROXY_LOCAL:
    '接入点为与 Broker 同进程部署的 Proxy 地址。若客户端环境无法解析该地址，请自行配置 DNS 解析或在客户端 hosts 中映射。',
  PROXY_CLUSTER:
    '接入点为独立 Proxy 集群的 SLB 内网地址。若客户端环境无法解析该地址，请自行配置 DNS 解析或在客户端 hosts 中映射。',
  DIRECT:
    '接入点为 NameServer SLB 地址（K8s 场景下一般为 NameServer Service 地址），Direct 模式客户端通过该地址发现 Broker。若客户端环境无法解析该地址，请自行配置 DNS 解析或在客户端 hosts 中映射。',
};

// ─── Cluster name lookup ───────────────────────────────────────────
const CLUSTER_NAME_MAP: Record<string, { name: string; type: string }> = {
  'rmq-cn-v5-prod-01': { name: 'rmq-cn-v5-prod-01', type: 'V5_PROXY_CLUSTER' },
  'rmq-cn-v4-prod-02': { name: 'rmq-cn-v4-prod-02', type: 'V4_DIRECT' },
};

const TYPE_OPTIONS = [
  { label: '全部', value: '' },
  { label: '普通', value: 'NORMAL' },
  { label: '顺序', value: 'FIFO' },
  { label: '延迟', value: 'DELAY' },
  { label: '事务', value: 'TRANSACTION' },
  { label: 'LiteTopic', value: 'LITE' },
];

// Topic 类型选项（描述参考阿里云 RocketMQ 消息类型语义），创建弹窗用 Segmented 展示
const TOPIC_TYPE_CARDS = [
  { value: 'NORMAL', label: '普通消息', desc: '适用于无特殊顺序要求的常规消息收发场景。' },
  { value: 'FIFO', label: '顺序消息', desc: '严格按照消息发送顺序消费，适用于顺序敏感的业务。' },
  { value: 'DELAY', label: '延迟消息', desc: '消息在指定的延迟时间或定时后才投递给消费者。' },
  {
    value: 'TRANSACTION',
    label: '事务消息',
    desc: '支持分布式事务，保证本地事务与消息发送的最终一致性。',
  },
  {
    value: 'LITE',
    label: 'LiteTopic',
    desc: '轻量级主题，资源开销更低，适用于大规模轻量消息场景。',
  },
];

// ─── Perm label ───────────────────────────────────────────────────
const PERM_LABEL: Record<string, string> = { RW: '读写', RO: '只读', WO: '只写' };

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
  { label: '订单事件', fn: randomOrderBody },
  { label: '用户行为', fn: randomUserEventBody },
  { label: '支付回调', fn: randomPaymentBody },
  { label: '库存变更', fn: randomInventoryBody },
  { label: '通知消息', fn: randomNotificationBody },
  { label: '监控指标', fn: randomMetricsBody },
];

// ─── Format helpers ───────────────────────────────────────────────
const formatNumber = (n: number) => n.toLocaleString('zh-CN');

const formatDateTime = (iso?: string): string => {
  if (!iso) return '-';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '-';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

const ROUTE_STATUS_META: Record<
  RouteDiagnosticStatus,
  { color: string; label: string; icon: React.ReactNode }
> = {
  healthy: { color: 'success', label: '健康', icon: <CheckCircleOutlined /> },
  warning: { color: 'warning', label: '关注', icon: <WarningOutlined /> },
  critical: { color: 'error', label: '异常', icon: <ExclamationCircleOutlined /> },
};

const ISSUE_SEVERITY_COLOR: Record<RouteDiagnosticIssue['severity'], string> = {
  warning: 'warning',
  critical: 'error',
};

const formatPercent = (value: number) => `${value.toFixed(value % 1 === 0 ? 0 : 1)}%`;

// ═══════════════════════════════════════════════════════════════════
const TopicPage = () => {
  const { t } = useLang();
  const navigate = useNavigate();
  const {
    selectedInstanceId,
    selectedInstance,
    selectInstance,
    instanceOptions,
    instancesLoading,
  } = useInstanceFilter();
  const isCloudInstance =
    selectedInstance?.vendor === 'ALIYUN' || selectedInstance?.vendor === 'TENCENT';
  const hasSelectedInstance = Boolean(selectedInstanceId);

  // ─── State ─────────────────────────────────────────────────────
  const [topics, setTopics] = useState<Topic[]>([]);
  const [totalTopics, setTotalTopics] = useState(0);
  const [loading, setLoading] = useState(true);
  const [routesByTopic, setRoutesByTopic] = useState<Record<string, BrokerRoute[]>>({});
  const [consumersByTopic, setConsumersByTopic] = useState<Record<string, TopicConsumerPage>>({});
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
  const [syncedTopics, setSyncedTopics] = useState<Set<string>>(() => new Set());
  const [syncingKeys, setSyncingKeys] = useState<Set<string>>(() => new Set());
  const [selectedTopic, setSelectedTopic] = useState<Topic | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm();
  const createTopicType = Form.useWatch('type', form);
  const [sendModalOpen, setSendModalOpen] = useState(false);
  const [sendTopic, setSendTopic] = useState<Topic | null>(null);
  const [sending, setSending] = useState(false);
  const [sendForm] = Form.useForm();
  const [propsMode, setPropsMode] = useState<'form' | 'text'>('form');
  const { modal } = App.useApp();
  const importInputRef = useRef<HTMLInputElement>(null);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importFilename, setImportFilename] = useState('');
  const [importRows, setImportRows] = useState<ResourceImportRow<Partial<Topic>>[]>([]);
  const [importErrors, setImportErrors] = useState<string[]>([]);
  const [importing, setImporting] = useState(false);
  const [exporting, setExporting] = useState(false);

  const topicRequestIdRef = useRef(0);
  const detailRequestIdRef = useRef(0);
  const consumersRequestIdRef = useRef(0);
  const createInFlightRef = useRef(false);

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
    const requestId = ++topicRequestIdRef.current;
    const timer = window.setTimeout(() => {
      setLoading(true);
      void listTopicsPage({
        instanceId: selectedInstanceId,
        type: typeFilter || undefined,
        search: searchText.trim() || undefined,
        page: tablePage,
        pageSize: tablePageSize,
      })
        .then((result) => {
          if (requestId === topicRequestIdRef.current) {
            setTopics(result.items);
            setTotalTopics(result.total);
          }
        })
        .catch(() => {
          if (requestId === topicRequestIdRef.current)
            message.error('Topic 列表加载失败，请稍后重试');
        })
        .finally(() => {
          if (requestId === topicRequestIdRef.current) setLoading(false);
        });
    }, 0);

    return () => {
      window.clearTimeout(timer);
    };
  }, [selectedInstanceId, typeFilter, searchText, tablePage, tablePageSize, instancesLoading]);

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
      try {
        await loadTopicConsumers(topic);
        if (requestId !== detailRequestIdRef.current) return;
        if (!isCloudInstance) {
          const routes = await getTopicRoutes(topic.name, selectedInstanceId || undefined);
          if (requestId !== detailRequestIdRef.current) return;
          setRoutesByTopic((previous) => ({ ...previous, [topic.name]: routes }));
        }
      } catch {
        if (requestId === detailRequestIdRef.current)
          message.error('Topic 详情加载失败，请稍后重试');
      } finally {
        if (requestId === detailRequestIdRef.current) setDetailLoading(false);
      }
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
      message.success(`Topic「${topic.name}」已在 Broker 上重建`);
    } catch {
      message.error('重建 Topic 失败，请检查 Broker 状态后重试');
    } finally {
      setRebuilding(false);
    }
  };

  // ─── Route / consumer helpers ─────────────────────────────────
  const getRoutes = (name: string): BrokerRoute[] => routesByTopic[name] ?? [];
  const getConsumerPage = (name: string): TopicConsumerPage =>
    consumersByTopic[name] ?? { items: [], total: 0, page: 1, pageSize: 20 };

  // ─── Sync data: find topics without broker routes and sync them ──
  const openSyncModal = async () => {
    setSyncModalOpen(true);
    setSyncChecking(true);
    setSyncMissing([]);
    setSyncedTopics(new Set());
    try {
      const results = await Promise.all(
        topics.map(async (topic) => {
          const instanceId = topic.instanceId || selectedInstanceId || undefined;
          try {
            return { topic, routes: await getTopicRoutes(topic.name, instanceId) };
          } catch {
            return { topic, routes: null as BrokerRoute[] | null };
          }
        }),
      );
      const checked = results.filter((r) => r.routes !== null);
      if (checked.length < results.length) {
        message.error('部分 Topic 路由校验失败，请稍后重试');
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
      setSyncChecking(false);
    }
  };

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
      message.success(`Topic「${topic.name}」已同步到 Broker`);
    } catch {
      message.error(`同步 Topic「${topic.name}」失败，请检查 Broker 状态后重试`);
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
    } else if (key === 'send') {
      setSendTopic(topic);
      setPropsMode('form');
      sendForm.setFieldsValue({ topic: topic.name, tag: '', key: '', body: '', properties: [] });
      setSendModalOpen(true);
    } else if (key === 'delete') {
      modal.confirm({
        title: '确认删除',
        content: `确定要删除 Topic「${topic.name}」吗？此操作不可撤销。`,
        okText: '删除',
        okType: 'danger',
        cancelText: '取消',
        onOk: async () => {
          try {
            await deleteTopic(topic.name, selectedInstanceId || undefined);
            setTopics((previous) => previous.filter((item) => item.name !== topic.name));
            message.success(`Topic「${topic.name}」已删除`);
          } catch {
            message.error('删除 Topic 失败，请稍后重试');
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
        message.success('Topic 导出完成');
      })
      .catch(() => {
        message.error('导出 Topic 失败，请稍后重试');
      })
      .finally(() => setExporting(false));
  };

  // ─── Table columns ────────────────────────────────────────────
  const columns: TableColumnsType<Topic> = [
    {
      title: 'Topic 名称',
      dataIndex: 'name',
      key: 'name',
      width: 220,
      sorter: (a, b) => a.name.localeCompare(b.name),
      render: (name: string) => (
        <Text strong style={{ fontSize: 14, display: 'block' }} ellipsis={{ tooltip: name }}>
          {name}
        </Text>
      ),
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      width: 200,
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
      title: '类型',
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
      title: '状态',
      key: 'status',
      width: 90,
      render: () => <Tag color="green">服务中</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'gmtCreate',
      key: 'gmtCreate',
      width: 170,
      sorter: (a, b) => (a.gmtCreate ?? '').localeCompare(b.gmtCreate ?? ''),
      render: (d: string) => <Text type="secondary">{formatDateTime(d)}</Text>,
    },
    {
      title: '修改时间',
      dataIndex: 'gmtModified',
      key: 'gmtModified',
      width: 170,
      sorter: (a, b) => (a.gmtModified ?? '').localeCompare(b.gmtModified ?? ''),
      render: (d: string) => <Text type="secondary">{formatDateTime(d)}</Text>,
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_: unknown, record: Topic) => (
        <Flex gap={6} onClick={(e) => e.stopPropagation()}>
          <Button
            size="small"
            icon={<EyeOutlined />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={() => handleAction('detail', record)}
          >
            详情
          </Button>
          {!isCloudInstance && (
            <Button
              size="small"
              icon={<SendOutlined />}
              style={{ borderColor: '#52c41a', color: '#52c41a' }}
              onClick={() => handleAction('send', record)}
            >
              发送
            </Button>
          )}
          <Button
            size="small"
            icon={<DeleteOutlined />}
            style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
            onClick={() => handleAction('delete', record)}
          >
            删除
          </Button>
        </Flex>
      ),
    },
  ];

  const renderRouteStatusTag = (status: RouteDiagnosticStatus) => {
    const meta = ROUTE_STATUS_META[status];
    return (
      <Tag color={meta.color} icon={meta.icon}>
        {meta.label}
      </Tag>
    );
  };

  const renderRouteIssueTags = (issues: RouteDiagnosticIssue[]) => {
    if (issues.length === 0) return <Text type="secondary">无</Text>;
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
      title: '地址拓扑',
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
              <Tag color="warning">地址未知</Tag>
            )}
          </Space>
        </Space>
      ),
    },
    {
      title: '队列分布',
      key: 'queues',
      width: 220,
      render: (_: unknown, record) => (
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <div>
            <Flex justify="space-between">
              <Text>写队列 {record.writeQueues}</Text>
              <Text type="secondary">{formatPercent(record.writeShare)}</Text>
            </Flex>
            <Progress percent={record.writeShare} showInfo={false} size="small" />
          </div>
          <div>
            <Flex justify="space-between">
              <Text>读队列 {record.readQueues}</Text>
              <Text type="secondary">{formatPercent(record.readShare)}</Text>
            </Flex>
            <Progress percent={record.readShare} showInfo={false} size="small" />
          </div>
        </Space>
      ),
    },
    {
      title: '权限',
      dataIndex: 'perm',
      key: 'perm',
      width: 130,
      render: (_: string, record) => (
        <Space direction="vertical" size={4}>
          <Tag>{PERM_LABEL[record.perm] || record.perm}</Tag>
          <Space size={4}>
            <Tag color={record.readable ? 'success' : 'error'}>读</Tag>
            <Tag color={record.writable ? 'success' : 'error'}>写</Tag>
          </Space>
        </Space>
      ),
    },
    {
      title: '诊断',
      key: 'diagnostics',
      width: 220,
      render: (_: unknown, record) => renderRouteIssueTags(record.issues),
    },
  ];

  // ─── Consumer table columns ───────────────────────────────────
  const consumerColumns: TableColumnsType<ConsumerGroupInfo> = [
    {
      title: '消费者组',
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
      title: '消费模式',
      dataIndex: 'messageModel',
      key: 'messageModel',
      render: (m: string) => <Tag color={m === '广播消费' ? 'orange' : 'blue'}>{m}</Tag>,
    },
    {
      title: '消费 TPS',
      dataIndex: 'consumeTps',
      key: 'consumeTps',
      render: (n: number, record) =>
        record.metricsAvailable === false ? <Text type="secondary">不可用</Text> : formatNumber(n),
    },
    {
      title: '堆积量',
      dataIndex: 'diffTotal',
      key: 'diffTotal',
      render: (n: number, record) =>
        record.metricsAvailable === false ? (
          <Text type="secondary">不可用</Text>
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
          诊断项
        </Text>
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          {issues.map((item) => (
            <Flex key={item.id} align="flex-start" gap={8}>
              <Tag color={ISSUE_SEVERITY_COLOR[item.severity]} style={{ marginTop: 1 }}>
                {item.severity === 'critical' ? '异常' : '关注'}
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
        title="建议处理"
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

    return (
      <>
        <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 12 }}>
          路由信息
        </Text>
        {!detailLoading && (
          <Space direction="vertical" size={12} style={{ width: '100%', marginBottom: 12 }}>
            <Alert
              type={diagnostics.statusColor}
              showIcon
              message={`路由诊断：${diagnostics.statusText}`}
              description={
                diagnostics.status === 'healthy'
                  ? `共 ${summary.brokerCount} 个 Broker，写队列 ${summary.totalWriteQueues} 个，读队列 ${summary.totalReadQueues} 个。`
                  : `发现 ${diagnostics.issues.length} 个诊断项，优先处理异常标记的 Broker。`
              }
              action={
                routes.length === 0 ? (
                  <Button
                    size="small"
                    type="primary"
                    loading={rebuilding}
                    onClick={() => void rebuildTopic(topic)}
                  >
                    在 Broker 上重建
                  </Button>
                ) : undefined
              }
            />
            <Row gutter={[12, 12]}>
              {renderRouteMetric(
                'Broker 数',
                summary.brokerCount,
                `${summary.addressCount} 个地址`,
              )}
              {renderRouteMetric(
                '可写 Broker',
                summary.writableBrokerCount,
                `${summary.totalWriteQueues} 个写队列`,
              )}
              {renderRouteMetric(
                '可读 Broker',
                summary.readableBrokerCount,
                `${summary.totalReadQueues} 个读队列`,
              )}
              {renderRouteMetric(
                'Replica 数',
                summary.replicaCount,
                summary.writeSkew.gap > 0 || summary.readSkew.gap > 0
                  ? `队列差距 写 ${summary.writeSkew.gap} / 读 ${summary.readSkew.gap}`
                  : '队列均衡',
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
        <Descriptions.Item label="Topic 名称" span={2}>
          {topic.name}
        </Descriptions.Item>
        <Descriptions.Item label="类型">
          <Tag color={typeInfo?.color}>
            {typeInfo?.labelKey ? t(typeInfo.labelKey) : topic.type}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="集群" span={2}>
          <Space>
            <Text>{topic.clusterId}</Text>
            {clusterType && <Tag color={clusterType.color}>{t(clusterType.labelKey)}</Tag>}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="写队列数">{topic.writeQueues}</Descriptions.Item>
        <Descriptions.Item label="读队列数">{topic.readQueues}</Descriptions.Item>
        <Descriptions.Item label="权限">
          <Tag>{PERM_LABEL[topic.perm]}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="今日消息量">{formatNumber(topic.messageCount)}</Descriptions.Item>
        <Descriptions.Item label="TPS">{formatNumber(topic.tps)}</Descriptions.Item>
        <Descriptions.Item label="消费者组数">{topic.consumerGroupCount}</Descriptions.Item>
        <Descriptions.Item label="创建时间" span={2}>
          {formatDateTime(topic.gmtCreate)}
        </Descriptions.Item>
      </Descriptions>
    );
  };

  // ─── Create modal submit ──────────────────────────────────────
  const handleCreate = async () => {
    if (createInFlightRef.current) return;
    if (!selectedInstanceId) {
      message.error('请先选择实例');
      return;
    }
    createInFlightRef.current = true;
    setCreating(true);
    try {
      const values = await form.validateFields();
      const created = await createTopic({
        ...values,
        instanceId: selectedInstanceId,
      });
      setTopics((previous) => [created, ...previous]);
      message.success(`Topic「${created.name}」创建成功`);
      setModalOpen(false);
      form.resetFields();
    } catch (error) {
      if (!(error && typeof error === 'object' && 'errorFields' in error)) {
        message.error('创建 Topic 失败，请稍后重试');
      }
    } finally {
      createInFlightRef.current = false;
      setCreating(false);
    }
  };

  const handleImportFile = async (file: File) => {
    if (!selectedInstanceId) {
      message.error('请先选择实例');
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
      setImportErrors([error instanceof Error ? error.message : 'CSV 解析失败']);
    } finally {
      if (importInputRef.current) importInputRef.current.value = '';
    }
  };

  const handleImportTopics = async () => {
    if (!selectedInstanceId) {
      message.error('请先选择实例');
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
              message: failure.message || '创建失败',
            }
          : { ...nextRows[index], status: 'success', message: '已创建' };
      });
    } catch (error) {
      for (const { index } of targetIndexes) {
        nextRows[index] = {
          ...nextRows[index],
          status: 'failed',
          message: error instanceof Error ? error.message : '创建失败',
        };
      }
    } finally {
      setImporting(false);
    }
    setImportRows([...nextRows]);

    if (createdTopics.length > 0) {
      setTopics((previous) => {
        const createdNames = new Set(createdTopics.map((topic) => topic.name));
        return [...createdTopics, ...previous.filter((topic) => !createdNames.has(topic.name))];
      });
    }

    const failedCount = nextRows.filter((row) => row.status === 'failed').length;
    const invalidCount = nextRows.filter((row) => row.status === 'invalid').length;
    if (failedCount === 0) {
      if (invalidCount > 0) {
        message.warning(`已导入 ${createdTopics.length} 个 Topic，${invalidCount} 行无效已跳过`);
      } else {
        message.success(`已导入 ${createdTopics.length} 个 Topic`);
      }
    } else if (createdTopics.length > 0) {
      message.warning(`已导入 ${createdTopics.length} 个 Topic，${failedCount} 个失败`);
    } else {
      message.error(`${failedCount} 个 Topic 导入失败`);
    }
  };

  const topicImportColumns: TableColumnsType<ResourceImportRow<Partial<Topic>>> = [
    { title: '行号', dataIndex: 'lineNumber', key: 'lineNumber', width: 80 },
    { title: 'Topic 名称', dataIndex: 'name', key: 'name' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: ResourceImportRow<Partial<Topic>>['status']) => {
        if (status === 'success') return <Tag color="success">成功</Tag>;
        if (status === 'failed') return <Tag color="error">失败</Tag>;
        if (status === 'invalid') return <Tag color="warning">无效</Tag>;
        return <Tag>待导入</Tag>;
      },
    },
    {
      title: '说明',
      dataIndex: 'message',
      key: 'message',
      render: (text?: string) => text || '-',
    },
  ];

  // ─── Send message modal submit ────────────────────────────────
  const handleSend = async () => {
    let values;
    try {
      values = await sendForm.validateFields();
    } catch {
      // validation error, keep the modal open
      return;
    }
    setSending(true);
    try {
      // Build properties: batch-paste text mode or key-value form rows
      let props: Record<string, string> = {};
      if (propsMode === 'text') {
        const parsed = parseMessageProperties(values.propsText || '');
        if (parsed.errors.length > 0) {
          message.error(`消息属性格式错误：${parsed.errors.join('；')}`);
          return;
        }
        props = parsed.properties;
      } else if (values.properties && Array.isArray(values.properties)) {
        values.properties.forEach((p: { key?: string; value?: string }) => {
          if (p.key) props[p.key] = p.value || '';
        });
      }
      const result = await sendTopicMessage({
        topic: values.topic,
        instanceId: selectedInstanceId || undefined,
        tag: values.tag || undefined,
        key: values.key || undefined,
        body: values.body,
        properties: props,
      });
      // Keep the modal open for consecutive sends
      message.success(`消息发送成功！MsgId: ${result.msgId}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '消息发送失败，请稍后重试');
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
      <PageHeader title={t('topic.title')} subtitle={`共 ${totalTopics} 个 Topic`} />

      {/* ── Current instance banner ───────────────────────────── */}
      {selectedInstance && (
        <InfoBanner>
          <Flex align="center" wrap="wrap" gap="8px 28px" style={{ fontSize: 14 }}>
            <span>
              <span style={{ color: '#8c8c8c', marginRight: 6 }}>当前实例</span>
              <span>{selectedInstance.name}</span>
            </span>
            <span>
              <span style={{ color: '#8c8c8c', marginRight: 6 }}>接入模式</span>
              <span>{INSTANCE_ACCESS_LABEL[selectedInstance.type]}</span>
            </span>
            {selectedInstance.vendor === 'ALIYUN' && (
              <span>
                <span style={{ color: '#8c8c8c', marginRight: 6 }}>厂商</span>
                <span>阿里云</span>
              </span>
            )}
            {selectedInstance.vendor === 'TENCENT' && (
              <span>
                <span style={{ color: '#8c8c8c', marginRight: 6 }}>厂商</span>
                <span>腾讯云</span>
              </span>
            )}
            <span>
              <span style={{ color: '#8c8c8c', marginRight: 6 }}>接入点</span>
              <Text code copyable style={{ fontSize: 16 }}>
                {selectedInstance.endpoint}
              </Text>
            </span>
          </Flex>
          <div style={{ marginTop: 10, fontSize: 14, lineHeight: 1.6, color: '#8c8c8c' }}>
            {INSTANCE_ACCESS_DESCRIPTION[selectedInstance.type]}
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
              resetTablePage();
              selectInstance(value);
            }}
            options={instanceOptions}
            style={{ width: 220 }}
          />
          <Input.Search
            placeholder="搜索 Topic 名称"
            allowClear
            style={{ width: 260 }}
            onSearch={(value) => {
              setSearchText(value);
              resetTablePage();
            }}
            onChange={(e) => {
              if (!e.target.value) {
                setSearchText('');
                resetTablePage();
              }
            }}
          />
          <Select
            placeholder="类型筛选"
            value={typeFilter}
            onChange={(value) => {
              setTypeFilter(value);
              resetTablePage();
            }}
            options={TYPE_OPTIONS}
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
                  title: '确认批量删除',
                  content: `确定要删除选中的 ${selectedRowKeys.length} 个 Topic 吗？此操作不可撤销。`,
                  okText: '删除',
                  okType: 'danger',
                  cancelText: '取消',
                  onOk: async () => {
                    try {
                      const names = selectedRowKeys.map(String);
                      const { deleted, failed } = await batchDeleteTopics(
                        names,
                        selectedInstanceId || undefined,
                      );
                      if (deleted.length > 0) {
                        const deletedNames = new Set(deleted);
                        setTopics((previous) =>
                          previous.filter((topic) => !deletedNames.has(topic.name)),
                        );
                      }
                      setSelectedRowKeys(failed);

                      if (failed.length === 0) {
                        message.success(`已删除 ${deleted.length} 个 Topic`);
                      } else if (deleted.length > 0) {
                        message.warning(
                          `已删除 ${deleted.length} 个 Topic，${failed.length} 个删除失败`,
                        );
                      } else {
                        message.error(`${failed.length} 个 Topic 删除失败，请稍后重试`);
                      }
                    } catch {
                      message.error('批量删除 Topic 失败，请稍后重试');
                    }
                  },
                });
              }}
            >
              删除 ({selectedRowKeys.length})
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
            导入
          </Button>
          <Button icon={<ExportOutlined />} loading={exporting} onClick={() => void handleExport()}>
            导出
          </Button>
          {!isCloudInstance && (
            <Button
              icon={<SyncOutlined />}
              disabled={!hasSelectedInstance || topics.length === 0}
              onClick={() => void openSyncModal()}
            >
              同步数据
            </Button>
          )}
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={!hasSelectedInstance}
            onClick={() => setModalOpen(true)}
          >
            创建 Topic
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
            showTotal: (count) => t('settings.totalRecords', { total: count }),
            onChange: (page, pageSize) => {
              setTablePage(page);
              setTablePageSize(pageSize);
            },
          }}
          size="small"
          scroll={{ x: tableScrollX(columns, { selection: true }) }}
          onRow={(record) => ({
            onClick: () => void openDetail(record),
            style: { cursor: 'pointer' },
          })}
        />
      </Card>

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
              基本信息
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
              消费者
            </Text>
            <Table<ConsumerGroupInfo>
              columns={consumerColumns}
              dataSource={getConsumerPage(selectedTopic.name).items}
              rowKey="group"
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

      {/* ── Create Topic Modal ────────────────────────────────── */}
      <Modal
        title="创建 Topic"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
        onOk={handleCreate}
        confirmLoading={creating}
        okText="创建"
        cancelText="取消"
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
            label="Topic 名称"
            name="name"
            rules={[
              { required: true, message: '请输入 Topic 名称' },
              {
                pattern: RESOURCE_NAME_PATTERN,
                message: '仅支持字母、数字、下划线、短横线、% 和 |',
              },
              {
                max: RESOURCE_NAME_MAX_LENGTH.topic,
                message: `名称不能超过 ${RESOURCE_NAME_MAX_LENGTH.topic} 个字符`,
              },
            ]}
          >
            <Input placeholder="请输入 Topic 名称" />
          </Form.Item>

          <Form.Item
            label="类型"
            name="type"
            rules={[{ required: true }]}
            extra={TOPIC_TYPE_CARDS.find((c) => c.value === createTopicType)?.desc}
          >
            <Segmented
              options={TOPIC_TYPE_CARDS.filter((c) => !isCloudInstance || c.value !== 'LITE').map(
                ({ value, label }) => ({ value, label }),
              )}
            />
          </Form.Item>

          {!isCloudInstance && (
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  label="写队列数"
                  name="writeQueues"
                  rules={[{ required: true }]}
                  extra="每个 Broker 节点 8 个队列"
                >
                  <InputNumber min={1} max={256} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  label="读队列数"
                  name="readQueues"
                  rules={[{ required: true }]}
                  extra="每个 Broker 节点 8 个队列"
                >
                  <InputNumber min={1} max={256} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
          )}

          {!isCloudInstance && (
            <Form.Item label="权限" name="perm" rules={[{ required: true }]}>
              <Radio.Group>
                <Radio.Button value="RW">读写</Radio.Button>
                <Radio.Button value="RO">只读</Radio.Button>
                <Radio.Button value="WO">只写</Radio.Button>
              </Radio.Group>
            </Form.Item>
          )}

          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={3} placeholder="可选，描述 Topic 用途" />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Import Topic Modal ────────────────────────────────── */}
      <Modal
        title={`导入 Topic${importFilename ? `：${importFilename}` : ''}`}
        open={importModalOpen}
        onCancel={() => {
          if (!importing) setImportModalOpen(false);
        }}
        onOk={() => void handleImportTopics()}
        okText={importRows.some((row) => row.status === 'failed') ? '重试失败项' : '开始导入'}
        cancelText="关闭"
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
              message="CSV 无法导入"
              description={importErrors.join('；')}
            />
          ) : importRows.some((row) => row.status === 'invalid') ? (
            <Alert
              type="warning"
              showIcon
              message={`检测到 ${
                importRows.filter((row) => row.status === 'invalid').length
              } 行无效，将跳过这些行`}
              description="仅导入可创建字段；CSV 中的 Namespace、Cluster ID 和运行状态列会被忽略。"
            />
          ) : (
            <Alert
              type="info"
              showIcon
              message={`检测到 ${importRows.length} 个 Topic，将通过后端批量导入`}
              description="仅导入可创建字段；CSV 中的 Namespace、Cluster ID 和运行状态列会被忽略。"
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
            <span>发送消息到 {sendTopic?.name}</span>
          </Space>
        }
        open={sendModalOpen}
        onCancel={() => {
          setSendModalOpen(false);
          sendForm.resetFields();
        }}
        onOk={handleSend}
        okText="发送"
        cancelText="取消"
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
                <Input placeholder="可选，消息标签" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Key" name="key">
                <Input placeholder="可选，消息 Key（用于查询）" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            label="消息体 Body"
            name="body"
            rules={[{ required: true, message: '请输入消息体' }]}
          >
            <Input.TextArea
              rows={8}
              placeholder="JSON 格式消息体"
              style={{ fontFamily: 'monospace', fontSize: 14 }}
            />
          </Form.Item>
          <Flex gap={12} style={{ marginTop: -8, marginBottom: 16 }}>
            <Text type="secondary" style={{ fontSize: 14, flexShrink: 0 }}>
              快速填入:
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
                  {gen.label}
                </Button>
              ))}
            </Space>
          </Flex>

          <Divider style={{ margin: '8px 0 16px' }} orientation="left" plain>
            自定义属性（可选）
          </Divider>

          <Flex justify="space-between" align="center" style={{ marginBottom: 12 }}>
            <Segmented
              size="small"
              value={propsMode}
              onChange={(value) => setPropsMode(value as 'form' | 'text')}
              options={[
                { label: '逐条录入', value: 'form' },
                { label: '批量粘贴', value: 'text' },
              ]}
            />
            {propsMode === 'text' && (
              <Text type="secondary" style={{ fontSize: 14 }}>
                支持 key=value，多个属性用换行或逗号分隔
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
                          <Input placeholder="属性名" />
                        </Form.Item>
                      </Col>
                      <Col span={10}>
                        <Form.Item {...rest} name={[name, 'value']} style={{ marginBottom: 0 }}>
                          <Input placeholder="属性值" />
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
                    添加属性
                  </Button>
                </>
              )}
            </Form.List>
          )}
        </Form>
      </Modal>

      <Modal
        title="同步数据"
        open={syncModalOpen}
        onCancel={() => setSyncModalOpen(false)}
        footer={<Button onClick={() => setSyncModalOpen(false)}>关闭</Button>}
        width={680}
        destroyOnHidden
      >
        {syncChecking ? (
          <Flex justify="center" align="center" style={{ padding: 48 }}>
            <Spin tip="正在校验 Topic 路由…">
              <div style={{ width: 200 }} />
            </Spin>
          </Flex>
        ) : syncMissing.length === 0 ? (
          <div style={{ padding: '16px 0' }}>
            <Text type="secondary">所有 Topic 在 Broker 上均有路由，无需同步。</Text>
          </div>
        ) : (
          <>
            <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
              以下 {syncMissing.length} 个 Topic 在 Broker 上找不到路由，可同步写入对应集群的
              Broker（按元数据记录的队列数重建）。
            </Text>
            <Table<Topic>
              dataSource={syncMissing}
              rowKey="name"
              size="small"
              pagination={false}
              columns={[
                { title: 'Topic', dataIndex: 'name', key: 'name' },
                {
                  title: '写/读队列数',
                  key: 'queues',
                  width: 110,
                  render: (_: unknown, topic: Topic) =>
                    `${topic.writeQueues ?? '-'} / ${topic.readQueues ?? '-'}`,
                },
                {
                  title: '状态',
                  key: 'status',
                  width: 100,
                  render: (_: unknown, topic: Topic) =>
                    syncedTopics.has(topic.name) ? (
                      <Tag color="green">已同步</Tag>
                    ) : (
                      <Tag color="orange">缺失路由</Tag>
                    ),
                },
                {
                  title: '操作',
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
                      同步
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

export default TopicPage;
