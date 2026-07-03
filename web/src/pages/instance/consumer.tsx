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
  Button,
  Tag,
  Space,
  Input,
  Select,
  Tabs,
  Modal,
  Form,
  Descriptions,
  Statistic,
  Radio,
  InputNumber,
  Typography,
  Row,
  Col,
  Flex,
  DatePicker,
  message,
} from 'antd';
import {
  Plus,
  MagnifyingGlass,
  Eye,
  ArrowsCounterClockwise,
  Trash,
  Clock,
  Cube,
  Users,
  ListBullets,
  Info,
  ArrowsClockwise,
} from '@phosphor-icons/react';
import { ImportOutlined, ExportOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';

import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import { TOPIC_TYPE_MAP, PROTOCOL_MAP } from '../../constants/theme';
import { mockConsumerGroups, mockQueueProgress, mockSubscriptions } from '../../mock/consumers';
import type {
  ConsumerGroup,
  ConsumerInstance,
  QueueProgress,
  SubscriptionEntry,
} from '../../mock/consumers';

const { Text } = Typography;

/* ─── Helpers ─── */

const lagColor = (lag: number): string => {
  if (lag >= 10_000) return '#ff4d4f';
  if (lag >= 1_000) return '#faad14';
  return '#52c41a';
};

/**
 * Format delay seconds into human-readable Chinese time.
 * Shows at most 3 units: days → hours → minutes → seconds.
 * e.g. 82500 → "22小时55分钟", 3725 → "1小时2分钟5秒"
 */
const formatDelay = (totalSeconds: number): string => {
  if (totalSeconds <= 0) return '0秒';

  const days = Math.floor(totalSeconds / 86400);
  let remaining = totalSeconds % 86400;
  const hours = Math.floor(remaining / 3600);
  remaining %= 3600;
  const minutes = Math.floor(remaining / 60);
  const seconds = remaining % 60;

  const parts: string[] = [];
  if (days > 0) parts.push(`${days}天`);
  if (hours > 0) parts.push(`${hours}小时`);
  if (minutes > 0) parts.push(`${minutes}分钟`);
  if (seconds > 0 && parts.length < 3) parts.push(`${seconds}秒`);

  return parts.length > 0 ? parts.join('') : '0秒';
};

const formatDateTime = (dateStr: string): string => {
  const d = new Date(dateStr);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

/* ═══════════════════════════════════════════
   ConsumerPage
   ═══════════════════════════════════════════ */
const ConsumerPage = () => {
  const { t } = useLang();
  const [groups, setGroups] = useState<ConsumerGroup[]>(mockConsumerGroups);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [search, setSearch] = useState('');
  const [modeFilter, setModeFilter] = useState<string>('ALL');
  const [sortKey, setSortKey] = useState<string>('name_asc');
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedGroup, setSelectedGroup] = useState<ConsumerGroup | null>(null);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [dataTypeValue, setDataTypeValue] = useState<string | undefined>(undefined);
  const [resetModalOpen, setResetModalOpen] = useState(false);
  const [resetGroup, setResetGroup] = useState<ConsumerGroup | null>(null);
  const [resetTime, setResetTime] = useState<Dayjs>(dayjs().subtract(3, 'hour'));

  /* ─── Filtered & sorted data ─── */
  const filtered = useMemo(() => {
    let data = groups.filter(
      (g) =>
        g.name.includes(search) ||
        g.namespace.includes(search) ||
        g.subscribedTopics.some((t) => t.includes(search)),
    );

    if (modeFilter !== 'ALL') {
      data = data.filter((g) => g.subscriptionMode === modeFilter);
    }

    if (sortKey === 'lag_desc') {
      data = [...data].sort((a, b) => b.totalLag - a.totalLag);
    } else if (sortKey === 'name_asc') {
      data = [...data].sort((a, b) => a.name.localeCompare(b.name));
    }

    return data;
  }, [groups, search, modeFilter, sortKey]);

  /* ─── Open detail modal ─── */
  const openModal = (group: ConsumerGroup) => {
    setSelectedGroup(group);
    setModalOpen(true);
  };

  /* ═══════════════════════════════════════════
     Main Table Columns
     ═══════════════════════════════════════════ */
  const columns: ColumnsType<ConsumerGroup> = [
    {
      title: 'Group 名称',
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
      title: '订阅组类型',
      dataIndex: 'subscriptionDataType',
      key: 'subscriptionDataType',
      width: 110,
      sorter: (a, b) => a.subscriptionDataType.localeCompare(b.subscriptionDataType),
      render: (type: string) => {
        const config = TOPIC_TYPE_MAP[type] || { label: type, color: 'default' };
        return <Tag color={config.color}>{config.label}</Tag>;
      },
    },
    {
      title: '订阅模式',
      dataIndex: 'subscriptionMode',
      key: 'subscriptionMode',
      width: 90,
      sorter: (a, b) => a.subscriptionMode.localeCompare(b.subscriptionMode),
      render: (mode: string) => <Tag color={mode === 'Push' ? 'blue' : 'green'}>{mode}</Tag>,
    },
    {
      title: '在线客户端',
      dataIndex: 'onlineInstances',
      key: 'onlineInstances',
      width: 130,
      align: 'center',
      sorter: (a, b) => a.onlineInstances - b.onlineInstances,
    },
    {
      title: '总堆积量',
      dataIndex: 'totalLag',
      key: 'totalLag',
      width: 120,
      sorter: (a, b) => a.totalLag - b.totalLag,
      render: (lag: number) => lag.toLocaleString(),
    },
    {
      title: '消费延迟',
      dataIndex: 'delaySeconds',
      key: 'delaySeconds',
      width: 160,
      sorter: (a, b) => a.delaySeconds - b.delaySeconds,
      render: (seconds: number) => formatDelay(seconds),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      sorter: (a, b) => a.createdAt.localeCompare(b.createdAt),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {d}
        </Text>
      ),
    },
    {
      title: '修改时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 170,
      sorter: (a, b) => a.updatedAt.localeCompare(b.updatedAt),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {d}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      render: (_: unknown, record: ConsumerGroup) => (
        <Flex gap={6}>
          <Button
            size="small"
            icon={<Eye size={14} />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={(e) => {
              e.stopPropagation();
              openModal(record);
            }}
          >
            详情
          </Button>
          <Button
            size="small"
            icon={<ArrowsCounterClockwise size={14} />}
            style={{ borderColor: '#fa8c16', color: '#fa8c16' }}
            onClick={(e) => {
              e.stopPropagation();
              setResetGroup(record);
              setResetTime(dayjs().subtract(3, 'hour'));
              setResetModalOpen(true);
            }}
          >
            重置位点
          </Button>
          <Button
            size="small"
            icon={<Trash size={14} />}
            style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
            onClick={(e) => {
              e.stopPropagation();
              Modal.confirm({
                title: `确认删除消费组 "${record.name}"？`,
                content: '删除后该消费组的所有配置和消费进度将被清除，此操作不可恢复。',
                okText: '删除',
                okButtonProps: { danger: true },
                cancelText: '取消',
                onOk: () => message.success(`消费组 ${record.name} 已删除`),
              });
            }}
          >
            删除
          </Button>
        </Flex>
      ),
    },
  ];

  /* ═══════════════════════════════════════════
     Expandable Sub-table: Subscription Details
     ═══════════════════════════════════════════ */
  const subscriptionSubColumns: ColumnsType<SubscriptionEntry> = [
    {
      title: 'Topic 主题',
      dataIndex: 'topic',
      key: 'topic',
      width: 200,
      render: (name: string) => (
        <Text strong style={{ fontSize: 13 }}>
          {name}
        </Text>
      ),
    },
    {
      title: '订阅一致性',
      dataIndex: 'consistency',
      key: 'consistency',
      width: 110,
      render: (v: string) => <Tag color={v === '一致' ? 'green' : 'orange'}>{v}</Tag>,
    },
    {
      title: '订阅模式',
      dataIndex: 'filterMode',
      key: 'filterMode',
      width: 120,
      render: (mode: string) => {
        const colorMap: Record<string, string> = {
          全量: 'default',
          'Tag 过滤': 'blue',
          'SQL92 过滤': 'purple',
        };
        return <Tag color={colorMap[mode] || 'default'}>{mode}</Tag>;
      },
    },
    {
      title: '订阅表达式',
      dataIndex: 'expression',
      key: 'expression',
      width: 260,
      render: (expr: string) => (
        <Text code style={{ fontSize: 12 }}>
          {expr}
        </Text>
      ),
    },
    {
      title: '',
      key: 'action',
      width: 100,
      render: () => (
        <Button
          size="small"
          icon={<Eye size={14} />}
          style={{ borderColor: '#1677ff', color: '#1677ff' }}
        >
          查看分布
        </Button>
      ),
    },
  ];

  /* ═══════════════════════════════════════════
     Modal: Consumer Instances Tab
     ═══════════════════════════════════════════ */
  const instanceColumns: ColumnsType<ConsumerInstance> = [
    {
      title: 'Client ID',
      dataIndex: 'clientId',
      key: 'clientId',
      width: 220,
      render: (id: string) => (
        <Text copyable style={{ fontSize: 13 }}>
          {id}
        </Text>
      ),
    },
    {
      title: '协议',
      dataIndex: 'protocol',
      key: 'protocol',
      width: 100,
      render: (protocol: string) => {
        const config = PROTOCOL_MAP[protocol] || { label: protocol, color: 'default' };
        return <Tag color={config.color}>{config.label}</Tag>;
      },
    },
    {
      title: '地址',
      dataIndex: 'address',
      key: 'address',
      width: 180,
      render: (addr: string) => (
        <Text code style={{ fontSize: 12 }}>
          {addr}
        </Text>
      ),
    },
    {
      title: '最后心跳',
      dataIndex: 'lastHeartbeat',
      key: 'lastHeartbeat',
      width: 170,
      render: (time: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {formatDateTime(time)}
        </Text>
      ),
    },
  ];

  /* ═══════════════════════════════════════════
     Modal: Queue Progress Tab
     ═══════════════════════════════════════════ */
  const queueColumns: ColumnsType<QueueProgress> = [
    {
      title: 'Broker',
      dataIndex: 'broker',
      key: 'broker',
      width: 160,
      render: (name: string) => (
        <Text strong style={{ fontSize: 13 }}>
          {name}
        </Text>
      ),
    },
    {
      title: 'Queue ID',
      dataIndex: 'queueId',
      key: 'queueId',
      width: 90,
      align: 'center',
      render: (id: number) => <Tag color="blue">Queue {id}</Tag>,
    },
    {
      title: 'Broker Offset',
      dataIndex: 'brokerOffset',
      key: 'brokerOffset',
      width: 140,
      align: 'right',
      render: (offset: number) => (
        <Text style={{ fontFamily: 'monospace' }}>{offset.toLocaleString()}</Text>
      ),
    },
    {
      title: 'Consumer Offset',
      dataIndex: 'consumerOffset',
      key: 'consumerOffset',
      width: 150,
      align: 'right',
      render: (offset: number) => (
        <Text style={{ fontFamily: 'monospace' }}>{offset.toLocaleString()}</Text>
      ),
    },
    {
      title: '堆积量',
      dataIndex: 'diffTotal',
      key: 'diffTotal',
      width: 120,
      align: 'right',
      render: (diff: number) => {
        const color = lagColor(diff);
        return (
          <Text style={{ color, fontWeight: 600, fontFamily: 'monospace' }}>
            {diff.toLocaleString()}
          </Text>
        );
      },
    },
  ];

  /* ═══════════════════════════════════════════
     Render
     ═══════════════════════════════════════════ */
  return (
    <div style={{ padding: 24 }}>
      {/* ─── Header ─── */}
      <PageHeader
        title={t('group.title')}
        subtitle={`管理消费者组订阅关系与消费进度，共 ${groups.length} 个 Group`}
      />

      {/* ─── Filter Bar ─── */}
      <Flex justify="space-between" align="center" style={{ marginBottom: 16 }}>
        <Space size={12} wrap>
          <Input.Search
            placeholder="搜索 Group 名称、命名空间或 Topic"
            allowClear
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={setSearch}
            style={{ width: 320 }}
            prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
          />
          <Select
            value={modeFilter}
            onChange={setModeFilter}
            style={{ width: 140 }}
            options={[
              { value: 'ALL', label: '全部模式' },
              { value: 'Push', label: 'Push' },
              { value: 'Pop', label: 'Pop' },
            ]}
          />
          <Select
            value={sortKey}
            onChange={setSortKey}
            style={{ width: 160 }}
            options={[
              { value: 'lag_desc', label: '堆积量降序' },
              { value: 'name_asc', label: '名称升序' },
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
                  content: `确定要删除选中的 ${selectedRowKeys.length} 个 Group 吗？`,
                  okText: '删除',
                  okButtonProps: { danger: true },
                  cancelText: '取消',
                  onOk: () => {
                    setGroups((prev) => prev.filter((g) => !selectedRowKeys.includes(g.name)));
                    message.success(`已删除 ${selectedRowKeys.length} 个 Group`);
                    setSelectedRowKeys([]);
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
            onClick={() => message.success(`已导出 ${filtered.length} 个 Group`)}
          >
            导出
          </Button>
          <Button
            type="primary"
            icon={<Plus size={14} weight="bold" />}
            onClick={() => setCreateModalOpen(true)}
          >
            创建 Group
          </Button>
        </Space>
      </Flex>

      {/* ─── Table with expandable rows ─── */}
      <Card bodyStyle={{ padding: 0 }}>
        <Table
          columns={columns}
          dataSource={filtered}
          rowKey="name"
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          pagination={{
            pageSize: 20,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 个 Group`,
          }}
          size="small"
          expandable={{
            expandedRowRender: (record) => (
              <div style={{ padding: '8px 0' }}>
                <Table
                  columns={subscriptionSubColumns}
                  dataSource={mockSubscriptions[record.name] || []}
                  rowKey="topic"
                  pagination={false}
                  size="small"
                />
              </div>
            ),
          }}
        />
      </Card>

      {/* ═══════════════════════════════════════════
         Detail Modal
         ═══════════════════════════════════════════ */}
      <Modal
        title={
          selectedGroup ? (
            <Space>
              <Cube size={18} weight="fill" color="#1677ff" />
              <span style={{ fontWeight: 600 }}>{selectedGroup.name}</span>
              <Tag color="default" style={{ fontSize: 11, borderRadius: 4 }}>
                {selectedGroup.namespace}
              </Tag>
            </Space>
          ) : (
            'Group 详情'
          )
        }
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          setSelectedGroup(null);
        }}
        width={800}
        destroyOnClose
        footer={null}
      >
        {selectedGroup && (
          <Tabs
            defaultActiveKey="overview"
            items={[
              /* ─── 概览 Tab ─── */
              {
                key: 'overview',
                label: (
                  <Space size={4}>
                    <Info size={14} />
                    <span>概览</span>
                  </Space>
                ),
                children: (
                  <div>
                    {/* Statistic Cards */}
                    <Row gutter={16} style={{ marginBottom: 24 }}>
                      <Col span={8}>
                        <Card
                          size="small"
                          style={{
                            borderTop: '3px solid #52c41a',
                            borderRadius: 8,
                          }}
                        >
                          <Statistic
                            title="在线实例"
                            value={selectedGroup.onlineInstances}
                            prefix={<Users size={18} color="#52c41a" />}
                            valueStyle={{ color: '#52c41a' }}
                          />
                        </Card>
                      </Col>
                      <Col span={8}>
                        <Card
                          size="small"
                          style={{
                            borderTop: `3px solid ${lagColor(selectedGroup.totalLag)}`,
                            borderRadius: 8,
                          }}
                        >
                          <Statistic
                            title="总堆积"
                            value={selectedGroup.totalLag}
                            prefix={
                              <ArrowsClockwise size={18} color={lagColor(selectedGroup.totalLag)} />
                            }
                            valueStyle={{
                              color: lagColor(selectedGroup.totalLag),
                            }}
                          />
                        </Card>
                      </Col>
                      <Col span={8}>
                        <Card
                          size="small"
                          style={{
                            borderTop: '3px solid #1677ff',
                            borderRadius: 8,
                          }}
                        >
                          <Statistic
                            title="订阅 Topic 数"
                            value={selectedGroup.subscribedTopics.length}
                            prefix={<ListBullets size={18} color="#1677ff" />}
                            valueStyle={{ color: '#1677ff' }}
                          />
                        </Card>
                      </Col>
                    </Row>

                    {/* Descriptions */}
                    <Descriptions
                      bordered
                      column={2}
                      size="small"
                      labelStyle={{ fontWeight: 500, width: 140 }}
                    >
                      <Descriptions.Item label="Group 名称">
                        <Text strong>{selectedGroup.name}</Text>
                      </Descriptions.Item>
                      <Descriptions.Item label="命名空间">
                        <Tag color="default">{selectedGroup.namespace}</Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="所属集群">
                        {selectedGroup.clusterId}
                      </Descriptions.Item>
                      <Descriptions.Item label="订阅模式">
                        <Tag color={selectedGroup.subscriptionMode === 'Push' ? 'blue' : 'green'}>
                          {selectedGroup.subscriptionMode}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="消费类型">
                        <Tag
                          color={selectedGroup.consumeType === 'CLUSTERING' ? 'geekblue' : 'purple'}
                        >
                          {selectedGroup.consumeType}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="订阅组类型">
                        <Tag
                          color={
                            TOPIC_TYPE_MAP[selectedGroup.subscriptionDataType]?.color || 'default'
                          }
                        >
                          {TOPIC_TYPE_MAP[selectedGroup.subscriptionDataType]?.label ||
                            selectedGroup.subscriptionDataType}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="消费延迟">
                        <Text strong>{formatDelay(selectedGroup.delaySeconds)}</Text>
                      </Descriptions.Item>
                      <Descriptions.Item label="最大重试次数">
                        <Text strong>{selectedGroup.retryMaxTimes}</Text> 次
                      </Descriptions.Item>
                      <Descriptions.Item label="创建时间" span={2}>
                        <Space size={4}>
                          <Clock size={13} color="#9CA3AF" />
                          <Text type="secondary">{selectedGroup.createdAt}</Text>
                        </Space>
                      </Descriptions.Item>
                      <Descriptions.Item label="订阅 Topic" span={2}>
                        <Space size={4} wrap>
                          {selectedGroup.subscribedTopics.map((t) => (
                            <Tag key={t} color="blue">
                              {t}
                            </Tag>
                          ))}
                        </Space>
                      </Descriptions.Item>
                    </Descriptions>

                    {/* 订阅关系 */}
                    <div style={{ marginTop: 24 }}>
                      <Flex align="center" gap={6} style={{ marginBottom: 12 }}>
                        <ListBullets size={15} color="#1677ff" />
                        <Text strong style={{ fontSize: 14 }}>
                          订阅关系
                        </Text>
                      </Flex>
                      <Table
                        columns={subscriptionSubColumns}
                        dataSource={mockSubscriptions[selectedGroup.name] || []}
                        rowKey="topic"
                        pagination={false}
                        size="small"
                      />
                    </div>
                  </div>
                ),
              },
              /* ─── 在线实例 Tab ─── */
              {
                key: 'instances',
                label: (
                  <Space size={4}>
                    <Users size={14} />
                    <span>在线实例 ({selectedGroup.instances.length})</span>
                  </Space>
                ),
                children: (
                  <Table
                    columns={instanceColumns}
                    dataSource={selectedGroup.instances}
                    rowKey="clientId"
                    pagination={false}
                    size="small"
                    scroll={{ y: 400 }}
                  />
                ),
              },
              /* ─── 消费进度 Tab ─── */
              {
                key: 'progress',
                label: (
                  <Space size={4}>
                    <ArrowsClockwise size={14} />
                    <span>消费进度</span>
                  </Space>
                ),
                children: (
                  <div>
                    <Card
                      size="small"
                      style={{
                        marginBottom: 16,
                        background: '#fafafa',
                        borderRadius: 8,
                      }}
                      bodyStyle={{ padding: '8px 16px' }}
                    >
                      <Space size={24}>
                        <Space size={4}>
                          <Text type="secondary">总 Broker 数:</Text>
                          <Text strong>
                            {
                              new Set(
                                (mockQueueProgress[selectedGroup.name] || []).map((q) => q.broker),
                              ).size
                            }
                          </Text>
                        </Space>
                        <Space size={4}>
                          <Text type="secondary">总 Queue 数:</Text>
                          <Text strong>{(mockQueueProgress[selectedGroup.name] || []).length}</Text>
                        </Space>
                        <Space size={4}>
                          <Text type="secondary">总堆积:</Text>
                          <Text
                            strong
                            style={{
                              color: lagColor(selectedGroup.totalLag),
                            }}
                          >
                            {selectedGroup.totalLag.toLocaleString()}
                          </Text>
                        </Space>
                      </Space>
                    </Card>

                    <Table
                      columns={queueColumns}
                      dataSource={mockQueueProgress[selectedGroup.name] || []}
                      rowKey={(r) => `${r.broker}-${r.queueId}`}
                      pagination={false}
                      size="small"
                      scroll={{ y: 380 }}
                    />
                  </div>
                ),
              },
            ]}
          />
        )}
      </Modal>

      {/* ═══════════════════════════════════════════
         Create Group Modal
         ═══════════════════════════════════════════ */}
      <Modal
        title={
          <Space>
            <Plus size={18} weight="bold" color="#1677ff" />
            <span>创建 Group</span>
          </Space>
        }
        open={createModalOpen}
        onCancel={() => {
          setCreateModalOpen(false);
          form.resetFields();
          setDataTypeValue(undefined);
        }}
        onOk={() => {
          form
            .validateFields()
            .then((values) => {
              Modal.confirm({
                title: '确认创建',
                content: `将创建消费组 "${values.name}"，命名空间: ${values.namespace || 'default'}`,
                okText: '确认创建',
                cancelText: '取消',
                onOk: () => {
                  message.success(`消费组 ${values.name} 创建成功`);
                  setCreateModalOpen(false);
                  form.resetFields();
                  setDataTypeValue(undefined);
                },
              });
            })
            .catch(() => {});
        }}
        okText="创建"
        cancelText="取消"
        width={560}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 16 }}
          initialValues={{
            namespace: 'default',
            subscriptionMode: 'Push',
            consumeType: 'CLUSTERING',
            retryMaxTimes: 16,
          }}
        >
          <Form.Item
            label="Group 名称"
            name="name"
            rules={[
              { required: true, message: '请输入 Group 名称' },
              {
                pattern: /^[a-zA-Z][a-zA-Z0-9_-]*$/,
                message: '名称以字母开头，仅包含字母、数字、下划线和短横线',
              },
            ]}
          >
            <Input placeholder="例：cg-order-notify" />
          </Form.Item>

          <Form.Item label="命名空间" name="namespace">
            <Input placeholder="例：trade" />
          </Form.Item>

          <Form.Item label="订阅模式" name="subscriptionMode">
            <Radio.Group>
              <Radio.Button value="Push">Push</Radio.Button>
              <Radio.Button value="Pop">Pop</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item label="消费类型" name="consumeType">
            <Radio.Group>
              <Radio.Button value="CLUSTERING">集群消费</Radio.Button>
              <Radio.Button value="BROADCASTING">广播消费</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item label="最大重试次数" name="retryMaxTimes">
            <InputNumber min={0} max={128} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item label="订阅组类型" name="dataType">
            <Select
              placeholder="选择消息类型"
              options={[
                { value: 'NORMAL', label: '普通消息' },
                { value: 'FIFO', label: '顺序消息' },
                { value: 'DELAY', label: '延迟消息' },
                { value: 'TRANSACTION', label: '事务消息' },
              ]}
              onChange={(val) => setDataTypeValue(val)}
            />
          </Form.Item>

          {dataTypeValue === 'FIFO' && (
            <Form.Item label="顺序类型" name="deliveryOrderType" initialValue="PARTITON_ORDER">
              <Select
                options={[
                  {
                    value: 'PARTITON_ORDER',
                    label: '分区顺序',
                  },
                  {
                    value: 'MESSAGES ORDER',
                    label: '全局顺序',
                  },
                ]}
              />
            </Form.Item>
          )}
        </Form>
      </Modal>

      {/* ═══════════════════════════════════════════
         Reset Offset Modal
         ═══════════════════════════════════════════ */}
      <Modal
        title={
          <Space>
            <ArrowsCounterClockwise size={18} color="#fa8c16" />
            <span>重置消费位点</span>
          </Space>
        }
        open={resetModalOpen}
        onCancel={() => {
          setResetModalOpen(false);
          setResetGroup(null);
        }}
        onOk={() => {
          if (resetGroup) {
            message.success(
              `${resetGroup.name} 消费位点已重置到 ${resetTime.format('YYYY-MM-DD HH:mm:ss')}`,
            );
          }
          setResetModalOpen(false);
          setResetGroup(null);
        }}
        okText="确认重置"
        cancelText="取消"
        width={480}
        destroyOnClose
      >
        {resetGroup && (
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
                ⚠️ 此操作将影响消息消费进度，请谨慎操作。重置后消费者将从指定时间点开始重新消费。
              </Text>
            </div>
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 4 }}>
                目标 Group
              </Text>
              <Text strong style={{ fontSize: 14 }}>
                {resetGroup.name}
              </Text>
            </div>
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 8 }}>
                重置到以下时间点
              </Text>
              <DatePicker
                showTime
                style={{ width: '100%' }}
                value={resetTime}
                onChange={(val) => {
                  if (val) setResetTime(val);
                }}
                format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择重置时间点"
              />
            </div>
            <div>
              <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 8 }}>
                快捷选择
              </Text>
              <Space wrap>
                <Button size="small" onClick={() => setResetTime(dayjs().subtract(1, 'hour'))}>
                  1 小时前
                </Button>
                <Button size="small" onClick={() => setResetTime(dayjs().subtract(3, 'hour'))}>
                  3 小时前
                </Button>
                <Button size="small" onClick={() => setResetTime(dayjs().subtract(6, 'hour'))}>
                  6 小时前
                </Button>
                <Button size="small" onClick={() => setResetTime(dayjs().subtract(12, 'hour'))}>
                  12 小时前
                </Button>
                <Button size="small" onClick={() => setResetTime(dayjs().subtract(1, 'day'))}>
                  1 天前
                </Button>
                <Button size="small" onClick={() => setResetTime(dayjs().subtract(3, 'day'))}>
                  3 天前
                </Button>
              </Space>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default ConsumerPage;
