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

import { useState, useMemo } from 'react';
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
import { formatDateTime, formatNumber } from '../../utils/format';
import {
  topics as mockTopics,
  topicRoutes,
  defaultRoute,
  topicConsumers,
  defaultConsumers,
} from '../../mock/topics';
import type { Topic, BrokerRoute, ConsumerGroupInfo } from '../../mock/topics';

const { Text } = Typography;

// ─── Cluster name lookup ───────────────────────────────────────────
const CLUSTER_NAME_MAP: Record<string, { name: string; type: string }> = {
  'rmq-cn-v5-prod-01': { name: 'rmq-cn-v5-prod-01', type: 'V5_PROXY_CLUSTER' },
  'rmq-cn-v4-prod-02': { name: 'rmq-cn-v4-prod-02', type: 'V4_DIRECT' },
};

// ─── Namespace options ────────────────────────────────────────────
const NAMESPACE_OPTIONS = [
  { labelKey: 'topic.allNamespaces', value: '' },
  { label: 'trade', value: 'trade' },
  { label: 'user', value: 'user' },
  { label: 'message', value: 'message' },
  { label: 'supply', value: 'supply' },
  { label: 'ai', value: 'ai' },
];

const TYPE_OPTIONS = [
  { labelKey: 'topic.allNamespaces', value: '' },
  { labelKey: 'theme.topicNormal', value: 'NORMAL' },
  { labelKey: 'theme.topicFifo', value: 'FIFO' },
  { labelKey: 'theme.topicDelay', value: 'DELAY' },
  { labelKey: 'theme.topicTransaction', value: 'TRANSACTION' },
  { label: 'LiteTopic', value: 'LITE' },
];

// ─── Perm label ───────────────────────────────────────────────────
const PERM_LABEL_KEY: Record<string, string> = {
  RW: 'topic.permRW',
  RO: 'topic.permRO',
  WO: 'topic.permWO',
};

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
  { labelKey: 'topic.orderEvent', fn: randomOrderBody },
  { labelKey: 'topic.userEvent', fn: randomUserEventBody },
  { labelKey: 'topic.paymentCallback', fn: randomPaymentBody },
  { labelKey: 'topic.inventoryChange', fn: randomInventoryBody },
  { labelKey: 'topic.notification', fn: randomNotificationBody },
  { labelKey: 'topic.metrics', fn: randomMetricsBody },
];

// ─── Format helpers ───────────────────────────────────────────────

// ═══════════════════════════════════════════════════════════════════
const TopicPage = () => {
  const { t } = useLang();

  // ─── State ─────────────────────────────────────────────────────
  const [topics, setTopics] = useState<Topic[]>(mockTopics);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [searchText, setSearchText] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [nsFilter, setNsFilter] = useState('');
  const [viewMode, setViewMode] = useState<string>('list');
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [selectedTopic, setSelectedTopic] = useState<Topic | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [sendModalOpen, setSendModalOpen] = useState(false);
  const [sendTopic, setSendTopic] = useState<Topic | null>(null);
  const [sending, setSending] = useState(false);
  const [sendForm] = Form.useForm();
  const { modal } = App.useApp();

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
  const openDetail = (topic: Topic) => {
    setSelectedTopic(topic);
    setDetailModalOpen(true);
  };

  // ─── Route / consumer helpers ─────────────────────────────────
  const getRoutes = (name: string): BrokerRoute[] => topicRoutes[name] || defaultRoute;
  const getConsumers = (name: string): ConsumerGroupInfo[] =>
    topicConsumers[name] || defaultConsumers;

  const handleAction = (key: string, topic: Topic) => {
    if (key === 'detail') {
      openDetail(topic);
    } else if (key === 'route') {
      openDetail(topic);
    } else if (key === 'send') {
      setSendTopic(topic);
      sendForm.setFieldsValue({ topic: topic.name, tag: '', key: '', body: '', properties: [] });
      setSendModalOpen(true);
    } else if (key === 'delete') {
      modal.confirm({
        title: t('topic.confirmDelete'),
        content: t('topic.deleteConfirm', { name: topic.name }),
        okText: t('common.delete'),
        okType: 'danger',
        cancelText: t('common.cancel'),
        onOk: () => message.success(t('topic.deleted', { name: topic.name })),
      });
    }
  };

  // ─── Table columns ────────────────────────────────────────────
  const columns: TableColumnsType<Topic> = [
    {
      title: t('topic.topicName'),
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
      title: t('topic.remark'),
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
      title: t('topic.type'),
      dataIndex: 'type',
      key: 'type',
      width: 100,
      sorter: (a, b) => a.type.localeCompare(b.type),
      render: (type: string) => {
        const cfg = TOPIC_TYPE_MAP[type];
        return cfg ? <Tag color={cfg.color}>{t(cfg.labelKey)}</Tag> : <Tag>{type}</Tag>;
      },
    },
    {
      title: t('topic.status'),
      key: 'status',
      width: 90,
      render: () => <Tag color="green">{t('topic.serving')}</Tag>,
    },
    {
      title: t('topic.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      sorter: (a, b) => a.createdAt.localeCompare(b.createdAt),
      render: (d: string) => <Text type="secondary">{formatDateTime(d)}</Text>,
    },
    {
      title: t('topic.updatedAt'),
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 170,
      sorter: (a, b) => a.updatedAt.localeCompare(b.updatedAt),
      render: (d: string) => <Text type="secondary">{formatDateTime(d)}</Text>,
    },
    {
      title: t('common.actions'),
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
            {t('common.detail')}
          </Button>
          <Button
            size="small"
            icon={<SendOutlined />}
            style={{ borderColor: '#52c41a', color: '#52c41a' }}
            onClick={() => handleAction('send', record)}
          >
            {t('topic.send')}
          </Button>
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

  // ─── Route table columns ──────────────────────────────────────
  const routeColumns: TableColumnsType<BrokerRoute> = [
    { title: t('topic.brokerName'), dataIndex: 'brokerName', key: 'brokerName' },
    { title: t('topic.brokerAddr'), dataIndex: 'brokerAddr', key: 'brokerAddr' },
    { title: t('topic.writeQueue'), dataIndex: 'writeQueues', key: 'writeQueues' },
    { title: t('topic.readQueue'), dataIndex: 'readQueues', key: 'readQueues' },
    {
      title: t('topic.perm'),
      dataIndex: 'perm',
      key: 'perm',
      render: (p: string) => <Tag>{t(PERM_LABEL_KEY[p] || p)}</Tag>,
    },
  ];

  // ─── Consumer table columns ───────────────────────────────────
  const consumerColumns: TableColumnsType<ConsumerGroupInfo> = [
    { title: t('topic.consumerGroup'), dataIndex: 'group', key: 'group' },
    {
      title: t('topic.consumeMode'),
      dataIndex: 'messageModel',
      key: 'messageModel',
      render: (m: string) => <Tag color={m === t('topic.broadcast') ? 'orange' : 'blue'}>{m}</Tag>,
    },
    {
      title: t('topic.consumeTps'),
      dataIndex: 'consumeTps',
      key: 'consumeTps',
      render: (n: number) => formatNumber(n),
    },
    {
      title: t('topic.backlog'),
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
        <Descriptions.Item label={t('topic.topicName')} span={2}>
          {topic.name}
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.type')}>
          <Tag color={typeInfo?.color}>{typeInfo ? t(typeInfo.labelKey) : topic.type}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.namespace')}>
          <Tag>{topic.namespace}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.cluster')} span={2}>
          <Space>
            <Text>{topic.clusterId}</Text>
            {clusterType && <Tag color={clusterType.color}>{t(clusterType.labelKey)}</Tag>}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.writeQueues')}>{topic.writeQueues}</Descriptions.Item>
        <Descriptions.Item label={t('topic.readQueues')}>{topic.readQueues}</Descriptions.Item>
        <Descriptions.Item label={t('topic.perm')}>
          <Tag>{t(PERM_LABEL_KEY[topic.perm] || topic.perm)}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.messageCount')}>
          {formatNumber(topic.messageCount)}
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.tps')}>{formatNumber(topic.tps)}</Descriptions.Item>
        <Descriptions.Item label={t('topic.consumerGroupCount')}>
          {topic.consumerGroupCount}
        </Descriptions.Item>
        <Descriptions.Item label={t('topic.createdAt')} span={2}>
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
              onClick={() => openDetail(topic)}
              styles={{ body: { padding: '16px 20px' } }}
              style={{ borderRadius: 8, border: '1px solid #f0f0f0' }}
            >
              {/* Header row: name + type badge */}
              <Flex justify="space-between" align="flex-start" style={{ marginBottom: 12 }}>
                <Text strong style={{ fontSize: 15 }}>
                  {topic.name}
                </Text>
                <Tag color={typeInfo?.color}>{typeInfo ? t(typeInfo.labelKey) : topic.type}</Tag>
              </Flex>

              {/* Namespace + cluster tags */}
              <Space size={4} style={{ marginBottom: 16 }}>
                <Tag style={{ fontSize: 11 }}>{topic.namespace}</Tag>
                {clusterType && (
                  <Tag color={clusterType.color} style={{ fontSize: 11 }}>
                    {t(clusterType.labelKey)}
                  </Tag>
                )}
              </Space>

              {/* Key stats */}
              <Row gutter={16}>
                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
                    {t('topic.messageCount')}
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
                    {t('topic.consumerGroup')}
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
  const handleCreate = () => {
    form.validateFields().then((values) => {
      message.success(t('topic.createSuccess', { name: values.name }));
      setModalOpen(false);
      form.resetFields();
    });
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
      // Simulate send (mock always succeeds)
      await new Promise((r) => setTimeout(r, 500));
      const mockMsgId = `7F${Math.random().toString(16).slice(2, 18).toUpperCase()}`;
      message.success(t('topic.sendSuccess', { id: mockMsgId }));
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
      <PageHeader
        title={t('topic.title')}
        subtitle={t('topic.totalCount', { count: filteredTopics.length })}
      />

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
            placeholder={t('topic.searchPlaceholder')}
            allowClear
            style={{ width: 260 }}
            onSearch={setSearchText}
            onChange={(e) => {
              if (!e.target.value) setSearchText('');
            }}
          />
          <Select
            placeholder={t('topic.typeFilter')}
            value={typeFilter}
            onChange={setTypeFilter}
            options={TYPE_OPTIONS.map((o) => ({
              ...o,
              label: o.labelKey ? t(o.labelKey) : o.label,
            }))}
            style={{ width: 140 }}
          />
          <Select
            placeholder={t('topic.namespace')}
            value={nsFilter}
            onChange={setNsFilter}
            options={NAMESPACE_OPTIONS.map((o) => ({
              ...o,
              label: o.labelKey ? t(o.labelKey) : o.label!,
            }))}
            style={{ width: 140 }}
          />
          <Segmented
            value={viewMode}
            onChange={(v) => setViewMode(v as string)}
            options={[
              { label: t('topic.listView'), value: 'list', icon: <UnorderedListOutlined /> },
              { label: t('topic.cardView'), value: 'card', icon: <AppstoreOutlined /> },
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
                  title: t('topic.confirmBatchDelete'),
                  content: t('topic.batchDeleteConfirm', { count: selectedRowKeys.length }),
                  okText: t('topic.delete'),
                  okType: 'danger',
                  cancelText: t('common.cancel'),
                  onOk: () => {
                    setTopics((prev) => prev.filter((t) => !selectedRowKeys.includes(t.name)));
                    message.success(t('topic.batchDeleted', { count: selectedRowKeys.length }));
                    setSelectedRowKeys([]);
                  },
                });
              }}
            >
              {t('topic.delete')} ({selectedRowKeys.length})
            </Button>
          )}
          <Button icon={<ImportOutlined />} onClick={() => message.info(t('topic.importWip'))}>
            {t('common.import')}
          </Button>
          <Button
            icon={<ExportOutlined />}
            onClick={() => message.success(t('topic.exported', { count: filteredTopics.length }))}
          >
            {t('common.export')}
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
            {t('topic.createTopic')}
          </Button>
        </Space>
      </Flex>

      {/* ── Content ───────────────────────────────────────────── */}
      {viewMode === 'list' ? (
        <Card styles={{ body: { padding: 0 } }} style={{ borderRadius: 8 }}>
          <Table<Topic>
            columns={columns}
            dataSource={filteredTopics}
            rowKey="name"
            rowSelection={{
              selectedRowKeys,
              onChange: (keys) => setSelectedRowKeys(keys),
            }}
            pagination={{
              pageSize: 20,
              showSizeChanger: true,
              showTotal: (total) => t('topic.showTotal', { total }),
            }}
            size="small"
            onRow={(record) => ({
              onClick: () => openDetail(record),
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
            {/* Section 1: Basic Info */}
            <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 12 }}>
              {t('topic.basicInfo')}
            </Text>
            {renderDetailTab(selectedTopic)}

            <Divider style={{ margin: '20px 0 16px' }} />

            {/* Section 2: Route Info */}
            <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 12 }}>
              {t('topic.routeInfo')}
            </Text>
            <Table<BrokerRoute>
              columns={routeColumns}
              dataSource={getRoutes(selectedTopic.name)}
              rowKey="brokerName"
              pagination={false}
              size="small"
            />

            <Divider style={{ margin: '20px 0 16px' }} />

            {/* Section 3: Consumers */}
            <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 12 }}>
              {t('topic.consumerInfo')}
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
        title={t('topic.createTopic')}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
        onOk={handleCreate}
        okText={t('common.create')}
        cancelText={t('common.cancel')}
        width={560}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            writeQueueNums: 8,
            readQueueNums: 8,
            perm: 'RW',
            type: 'NORMAL',
            namespace: 'default',
          }}
          style={{ marginTop: 16 }}
        >
          <Form.Item
            label={t('topic.topicName')}
            name="name"
            rules={[
              { required: true, message: t('topic.topicNameRequired') },
              {
                pattern: /^[a-zA-Z0-9_\-/*]+$/,
                message: t('topic.topicNameRule'),
              },
            ]}
          >
            <Input placeholder={t('topic.topicNamePlaceholder')} />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label={t('topic.namespace')} name="namespace" rules={[{ required: true }]}>
                <Select disabled options={[{ label: 'default', value: 'default' }]} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label={t('topic.type')} name="type" rules={[{ required: true }]}>
                <Select options={TYPE_OPTIONS.filter((o) => o.value)} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label={t('topic.writeQueues')}
                name="writeQueueNums"
                rules={[{ required: true }]}
                extra={t('topic.queueExtra')}
              >
                <InputNumber min={1} max={256} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label={t('topic.readQueues')}
                name="readQueueNums"
                rules={[{ required: true }]}
                extra={t('topic.queueExtra')}
              >
                <InputNumber min={1} max={256} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label={t('topic.perm')} name="perm" rules={[{ required: true }]}>
            <Radio.Group>
              <Radio.Button value="RW">{t('topic.permRW')}</Radio.Button>
              <Radio.Button value="RO">{t('topic.permRO')}</Radio.Button>
              <Radio.Button value="WO">{t('topic.permWO')}</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item label={t('topic.remark')} name="remark">
            <Input.TextArea rows={3} placeholder={t('topic.remarkPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Send Message Modal ──────────────────────────────────── */}
      <Modal
        title={
          <Space>
            <SendOutlined />
            <span>{t('topic.sendMsg', { name: sendTopic?.name ?? '' })}</span>
          </Space>
        }
        open={sendModalOpen}
        onCancel={() => {
          setSendModalOpen(false);
          sendForm.resetFields();
        }}
        onOk={handleSend}
        okText={t('topic.send')}
        cancelText={t('common.cancel')}
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
              style={{ fontFamily: 'monospace', fontSize: 13 }}
            />
          </Form.Item>
          <Flex gap={12} style={{ marginTop: -8, marginBottom: 16 }}>
            <Text type="secondary" style={{ fontSize: 12, flexShrink: 0 }}>
              {t('topic.quickFill')}
            </Text>
            <Space size={4} wrap>
              {RANDOM_BODY_GENERATORS.map((gen) => (
                <Button
                  key={gen.labelKey}
                  type="text"
                  size="small"
                  onClick={() => sendForm.setFieldValue('body', gen.fn())}
                  style={{ fontSize: 12, color: '#8c8c8c', height: 22, padding: '0 6px' }}
                >
                  {t(gen.labelKey)}
                </Button>
              ))}
            </Space>
          </Flex>

          <Divider style={{ margin: '8px 0 16px' }} orientation="left" plain>
            {t('topic.customProps')}
          </Divider>

          <Form.List name="properties">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...rest }) => (
                  <Row gutter={8} key={key} align="middle" style={{ marginBottom: 8 }}>
                    <Col span={10}>
                      <Form.Item {...rest} name={[name, 'key']} style={{ marginBottom: 0 }}>
                        <Input placeholder={t('topic.propName')} />
                      </Form.Item>
                    </Col>
                    <Col span={10}>
                      <Form.Item {...rest} name={[name, 'value']} style={{ marginBottom: 0 }}>
                        <Input placeholder={t('topic.propValue')} />
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
        </Form>
      </Modal>
    </div>
  );
};

export default TopicPage;
