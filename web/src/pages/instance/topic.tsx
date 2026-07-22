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

import { useEffect, useState, useMemo } from 'react';
import {
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
  message,
  App,
} from 'antd';
import type { TableColumnsType } from 'antd';
import {
  PlusOutlined,
  UnorderedListOutlined,
  AppstoreOutlined,
  SendOutlined,
  DeleteOutlined,
  EyeOutlined,
  ImportOutlined,
  ExportOutlined,
  PlusCircleOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import { TOPIC_TYPE_MAP, CLUSTER_TYPE_MAP } from '../../constants/theme';
import type { Topic, BrokerRoute, ConsumerGroupInfo } from '../../api/metadata';
import {
  batchDeleteTopics,
  createTopic,
  deleteTopic,
  getTopicConsumers,
  getTopicRoutes,
  listTopics,
  sendTopicMessage,
} from '../../services/topicService';

const { Text } = Typography;

// ─── Cluster name lookup ───────────────────────────────────────────
const CLUSTER_NAME_MAP: Record<string, { name: string; type: string }> = {
  'rmq-cn-v5-prod-01': { name: 'rmq-cn-v5-prod-01', type: 'V5_PROXY_CLUSTER' },
  'rmq-cn-v4-prod-02': { name: 'rmq-cn-v4-prod-02', type: 'V4_DIRECT' },
};

// ─── Namespace options ────────────────────────────────────────────
const NAMESPACE_OPTIONS = [
  { label: '全部', value: '' },
  { label: 'trade', value: 'trade' },
  { label: 'user', value: 'user' },
  { label: 'message', value: 'message' },
  { label: 'supply', value: 'supply' },
  { label: 'ai', value: 'ai' },
];

const TYPE_OPTIONS = [
  { label: '全部', value: '' },
  { label: '普通', value: 'NORMAL' },
  { label: '顺序', value: 'FIFO' },
  { label: '延迟', value: 'DELAY' },
  { label: '事务', value: 'TRANSACTION' },
  { label: 'LiteTopic', value: 'LITE' },
];

// ─── Perm label ───────────────────────────────────────────────────
const PERM_LABEL: Record<string, string> = { RW: '读写', RO: '只读', WO: '只写' };

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
const formatDateTime = (iso: string): string => {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

// ═══════════════════════════════════════════════════════════════════
const TopicPage = () => {
  const { t } = useLang();

  // ─── State ─────────────────────────────────────────────────────
  const [topics, setTopics] = useState<Topic[]>([]);
  const [loading, setLoading] = useState(true);
  const [routesByTopic, setRoutesByTopic] = useState<Record<string, BrokerRoute[]>>({});
  const [consumersByTopic, setConsumersByTopic] = useState<Record<string, ConsumerGroupInfo[]>>({});
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [searchText, setSearchText] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [nsFilter, setNsFilter] = useState('');
  const [viewMode, setViewMode] = useState<string>('列表');
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [selectedTopic, setSelectedTopic] = useState<Topic | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [sendModalOpen, setSendModalOpen] = useState(false);
  const [sendTopic, setSendTopic] = useState<Topic | null>(null);
  const [sending, setSending] = useState(false);
  const [sendForm] = Form.useForm();
  const { modal } = App.useApp();

  useEffect(() => {
    let cancelled = false;
    void listTopics()
      .then((nextTopics) => {
        if (!cancelled) setTopics(nextTopics);
      })
      .catch(() => {
        if (!cancelled) message.error('Topic 列表加载失败，请稍后重试');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  // ─── Filtered data ─────────────────────────────────────────────
  const filteredTopics = useMemo(
    () =>
      topics
        .filter((t) => {
          if (searchText && !t.name.toLowerCase().includes(searchText.toLowerCase())) return false;
          if (typeFilter && t.type !== typeFilter) return false;
          if (nsFilter && t.namespace !== nsFilter) return false;
          return true;
        })
        .sort((a, b) => a.name.localeCompare(b.name)),
    [topics, searchText, typeFilter, nsFilter],
  );

  // ─── Open detail modal ────────────────────────────────────────
  const openDetail = async (topic: Topic) => {
    setSelectedTopic(topic);
    setDetailModalOpen(true);
    try {
      const [routes, consumers] = await Promise.all([
        getTopicRoutes(topic.name),
        getTopicConsumers(topic.name),
      ]);
      setRoutesByTopic((previous) => ({ ...previous, [topic.name]: routes }));
      setConsumersByTopic((previous) => ({ ...previous, [topic.name]: consumers }));
    } catch {
      message.error('Topic 详情加载失败，请稍后重试');
    }
  };

  // ─── Route / consumer helpers ─────────────────────────────────
  const getRoutes = (name: string): BrokerRoute[] => routesByTopic[name] ?? [];
  const getConsumers = (name: string): ConsumerGroupInfo[] => consumersByTopic[name] ?? [];

  const handleAction = (key: string, topic: Topic) => {
    if (key === 'detail') {
      void openDetail(topic);
    } else if (key === 'route') {
      void openDetail(topic);
    } else if (key === 'send') {
      setSendTopic(topic);
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
            await deleteTopic(topic.name);
            setTopics((previous) => previous.filter((item) => item.name !== topic.name));
            message.success(`Topic「${topic.name}」已删除`);
          } catch {
            message.error('删除 Topic 失败，请稍后重试');
          }
        },
      });
    }
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
        <Text strong style={{ fontSize: 14 }}>
          {name}
        </Text>
      ),
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      width: 200,
      sorter: (a, b) => a.remark.localeCompare(b.remark),
      render: (remark: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {remark}
        </Text>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      sorter: (a, b) => a.type.localeCompare(b.type),
      render: (type: string) => {
        const cfg = TOPIC_TYPE_MAP[type];
        return cfg ? <Tag color={cfg.color}>{cfg.label}</Tag> : <Tag>{type}</Tag>;
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
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      sorter: (a, b) => a.createdAt.localeCompare(b.createdAt),
      render: (d: string) => <Text type="secondary">{formatDateTime(d)}</Text>,
    },
    {
      title: '修改时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 170,
      sorter: (a, b) => a.updatedAt.localeCompare(b.updatedAt),
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
          <Button
            size="small"
            icon={<SendOutlined />}
            style={{ borderColor: '#52c41a', color: '#52c41a' }}
            onClick={() => handleAction('send', record)}
          >
            发送
          </Button>
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

  // ─── Route table columns ──────────────────────────────────────
  const routeColumns: TableColumnsType<BrokerRoute> = [
    { title: 'Broker 名称', dataIndex: 'brokerName', key: 'brokerName' },
    { title: 'Broker 地址', dataIndex: 'brokerAddr', key: 'brokerAddr' },
    { title: '写队列', dataIndex: 'writeQueues', key: 'writeQueues' },
    { title: '读队列', dataIndex: 'readQueues', key: 'readQueues' },
    {
      title: '权限',
      dataIndex: 'perm',
      key: 'perm',
      render: (p: string) => <Tag>{PERM_LABEL[p] || p}</Tag>,
    },
  ];

  // ─── Consumer table columns ───────────────────────────────────
  const consumerColumns: TableColumnsType<ConsumerGroupInfo> = [
    { title: '消费者组', dataIndex: 'group', key: 'group' },
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
      render: (n: number) => formatNumber(n),
    },
    {
      title: '堆积量',
      dataIndex: 'diffTotal',
      key: 'diffTotal',
      render: (n: number) => <Text type={n > 100 ? 'warning' : undefined}>{formatNumber(n)}</Text>,
    },
  ];

  // ─── Modal: detail tab ────────────────────────────────────────
  const renderDetailTab = (topic: Topic) => {
    const cluster = CLUSTER_NAME_MAP[topic.clusterId];
    const clusterType = cluster ? CLUSTER_TYPE_MAP[cluster.type] : null;
    const typeInfo = TOPIC_TYPE_MAP[topic.type];

    return (
      <Descriptions bordered column={2} size="small" labelStyle={{ fontWeight: 500 }}>
        <Descriptions.Item label="Topic 名称" span={2}>
          {topic.name}
        </Descriptions.Item>
        <Descriptions.Item label="类型">
          <Tag color={typeInfo?.color}>{typeInfo?.label}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="命名空间">
          <Tag>{topic.namespace}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="集群" span={2}>
          <Space>
            <Text>{topic.clusterId}</Text>
            {clusterType && <Tag color={clusterType.color}>{clusterType.label}</Tag>}
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
          {formatDateTime(topic.createdAt)}
        </Descriptions.Item>
      </Descriptions>
    );
  };

  // ─── Card view ────────────────────────────────────────────────
  const renderCardView = () => (
    <Row gutter={[16, 16]}>
      {filteredTopics.map((topic) => {
        const typeInfo = TOPIC_TYPE_MAP[topic.type];
        const cluster = CLUSTER_NAME_MAP[topic.clusterId];
        const clusterType = cluster ? CLUSTER_TYPE_MAP[cluster.type] : null;

        return (
          <Col xs={24} sm={12} lg={8} key={topic.name}>
            <Card
              hoverable
              size="small"
              onClick={() => void openDetail(topic)}
              styles={{ body: { padding: '16px 20px' } }}
              style={{ borderRadius: 8, border: '1px solid #f0f0f0' }}
            >
              {/* Header row: name + type badge */}
              <Flex justify="space-between" align="flex-start" style={{ marginBottom: 12 }}>
                <Text strong style={{ fontSize: 15 }}>
                  {topic.name}
                </Text>
                <Tag color={typeInfo?.color}>{typeInfo?.label}</Tag>
              </Flex>

              {/* Namespace + cluster tags */}
              <Space size={4} style={{ marginBottom: 16 }}>
                <Tag style={{ fontSize: 11 }}>{topic.namespace}</Tag>
                {clusterType && (
                  <Tag color={clusterType.color} style={{ fontSize: 11 }}>
                    {clusterType.label}
                  </Tag>
                )}
              </Space>

              {/* Key stats */}
              <Row gutter={16}>
                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
                    今日消息量
                  </Text>
                  <Text strong style={{ fontSize: 16, fontVariantNumeric: 'tabular-nums' }}>
                    {formatNumber(topic.messageCount)}
                  </Text>
                </Col>
                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
                    TPS
                  </Text>
                  <Text strong style={{ fontSize: 16, fontVariantNumeric: 'tabular-nums' }}>
                    {formatNumber(topic.tps)}
                  </Text>
                </Col>
                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
                    消费者组
                  </Text>
                  <Text strong style={{ fontSize: 16, fontVariantNumeric: 'tabular-nums' }}>
                    {topic.consumerGroupCount}
                  </Text>
                </Col>
              </Row>
            </Card>
          </Col>
        );
      })}
    </Row>
  );

  // ─── Create modal submit ──────────────────────────────────────
  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      const created = await createTopic(values);
      setTopics((previous) => [created, ...previous]);
      message.success(`Topic「${created.name}」创建成功`);
      setModalOpen(false);
      form.resetFields();
    } catch {
      message.error('创建 Topic 失败，请稍后重试');
    }
  };

  // ─── Send message modal submit ────────────────────────────────
  const handleSend = async () => {
    try {
      const values = await sendForm.validateFields();
      setSending(true);
      // Build properties from the list
      const props: Record<string, string> = {};
      if (values.properties && Array.isArray(values.properties)) {
        values.properties.forEach((p: { key?: string; value?: string }) => {
          if (p.key) props[p.key] = p.value || '';
        });
      }
      const result = await sendTopicMessage({
        topic: values.topic,
        tag: values.tag || undefined,
        key: values.key || undefined,
        body: values.body,
        properties: props,
      });
      message.success(`消息发送成功！MsgId: ${result.msgId}`);
      setSendModalOpen(false);
      sendForm.resetFields();
    } catch {
      // validation error, do nothing
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
      <PageHeader title={t('topic.title')} subtitle={`共 ${filteredTopics.length} 个 Topic`} />

      {/* ── Filter bar ────────────────────────────────────────── */}
      <Flex
        gap={12}
        wrap="wrap"
        style={{ marginBottom: 20 }}
        align="center"
        justify="space-between"
      >
        <Space size={12} wrap>
          <Input.Search
            placeholder="搜索 Topic 名称"
            allowClear
            style={{ width: 260 }}
            onSearch={setSearchText}
            onChange={(e) => {
              if (!e.target.value) setSearchText('');
            }}
          />
          <Select
            placeholder="类型筛选"
            value={typeFilter}
            onChange={setTypeFilter}
            options={TYPE_OPTIONS}
            style={{ width: 140 }}
          />
          <Select
            placeholder="命名空间"
            value={nsFilter}
            onChange={setNsFilter}
            options={NAMESPACE_OPTIONS}
            style={{ width: 140 }}
          />
          <Segmented
            value={viewMode}
            onChange={(v) => setViewMode(v as string)}
            options={[
              { label: '列表', value: '列表', icon: <UnorderedListOutlined /> },
              { label: '卡片', value: '卡片', icon: <AppstoreOutlined /> },
            ]}
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
                      await batchDeleteTopics(names);
                      setTopics((previous) =>
                        previous.filter((topic) => !names.includes(topic.name)),
                      );
                      message.success(`已删除 ${names.length} 个 Topic`);
                      setSelectedRowKeys([]);
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
          <Button icon={<ImportOutlined />} onClick={() => message.info('导入功能开发中')}>
            导入
          </Button>
          <Button
            icon={<ExportOutlined />}
            onClick={() => message.success(`已导出 ${filteredTopics.length} 个 Topic`)}
          >
            导出
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
            创建 Topic
          </Button>
        </Space>
      </Flex>

      {/* ── Content ───────────────────────────────────────────── */}
      {viewMode === '列表' ? (
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
              pageSize: 20,
              showSizeChanger: true,
              showTotal: (t) => `共 ${t} 条`,
            }}
            size="small"
            onRow={(record) => ({
              onClick: () => void openDetail(record),
              style: { cursor: 'pointer' },
            })}
          />
        </Card>
      ) : (
        renderCardView()
      )}

      {/* ── Detail Modal ──────────────────────────────────────── */}
      <Modal
        title={selectedTopic?.name}
        open={detailModalOpen}
        onCancel={() => setDetailModalOpen(false)}
        width={800}
        destroyOnClose
        footer={null}
      >
        {selectedTopic && (
          <>
            {/* Section 1: 基本信息 */}
            <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 12 }}>
              基本信息
            </Text>
            {renderDetailTab(selectedTopic)}

            <Divider style={{ margin: '20px 0 16px' }} />

            {/* Section 2: 路由信息 */}
            <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 12 }}>
              路由信息
            </Text>
            <Table<BrokerRoute>
              columns={routeColumns}
              dataSource={getRoutes(selectedTopic.name)}
              rowKey="brokerName"
              pagination={false}
              size="small"
            />

            <Divider style={{ margin: '20px 0 16px' }} />

            {/* Section 3: 消费者 */}
            <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 12 }}>
              消费者
            </Text>
            <Table<ConsumerGroupInfo>
              columns={consumerColumns}
              dataSource={getConsumers(selectedTopic.name)}
              rowKey="group"
              pagination={false}
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
        okText="创建"
        cancelText="取消"
        width={560}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            writeQueues: 8,
            readQueues: 8,
            perm: 'RW',
            type: 'NORMAL',
            namespace: 'default',
          }}
          style={{ marginTop: 16 }}
        >
          <Form.Item
            label="Topic 名称"
            name="name"
            rules={[
              { required: true, message: '请输入 Topic 名称' },
              {
                pattern: /^[a-zA-Z0-9_\-/*]+$/,
                message: '仅支持字母、数字、下划线、中划线、斜杠和星号',
              },
            ]}
          >
            <Input placeholder="请输入 Topic 名称" />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="命名空间" name="namespace" rules={[{ required: true }]}>
                <Select disabled options={[{ label: 'default', value: 'default' }]} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="类型" name="type" rules={[{ required: true }]}>
                <Select options={TYPE_OPTIONS.filter((o) => o.value)} />
              </Form.Item>
            </Col>
          </Row>

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

          <Form.Item label="权限" name="perm" rules={[{ required: true }]}>
            <Radio.Group>
              <Radio.Button value="RW">读写</Radio.Button>
              <Radio.Button value="RO">只读</Radio.Button>
              <Radio.Button value="WO">只写</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={3} placeholder="可选，描述 Topic 用途" />
          </Form.Item>
        </Form>
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
        destroyOnClose
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
              style={{ fontFamily: 'monospace', fontSize: 13 }}
            />
          </Form.Item>
          <Flex gap={12} style={{ marginTop: -8, marginBottom: 16 }}>
            <Text type="secondary" style={{ fontSize: 12, flexShrink: 0 }}>
              快速填入:
            </Text>
            <Space size={4} wrap>
              {RANDOM_BODY_GENERATORS.map((gen) => (
                <Button
                  key={gen.label}
                  type="text"
                  size="small"
                  onClick={() => sendForm.setFieldValue('body', gen.fn())}
                  style={{ fontSize: 12, color: '#8c8c8c', height: 22, padding: '0 6px' }}
                >
                  {gen.label}
                </Button>
              ))}
            </Space>
          </Flex>

          <Divider style={{ margin: '8px 0 16px' }} orientation="left" plain>
            自定义属性（可选）
          </Divider>

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
        </Form>
      </Modal>
    </div>
  );
};

export default TopicPage;
