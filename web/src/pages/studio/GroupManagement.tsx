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
  Table,
  Button,
  Input,
  Tag,
  Modal,
  Tabs,
  Card,
  Row,
  Col,
  Descriptions,
  Space,
  Switch,
} from 'antd';
import { MagnifyingGlass, Plus, ArrowClockwise, Users, Eye } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';

// ─── Types ──────────────────────────────────────────────────────
interface GroupRecord {
  key: string;
  group: string;
  namespace: string;
  cluster: string;
  count: number;
  consumeType: string;
  messageModel: 'CLUSTERING' | 'BROADCASTING';
  diff: number;
  status: 'running' | 'warning' | 'stopped';
}

interface SubscriptionRecord {
  key: string;
  topic: string;
  consistency: 'consistent' | 'inconsistent';
  subMode: string;
  expression: string;
}

interface InstanceRecord {
  key: string;
  instanceId: string;
  address: string;
  version: string;
  status: string;
}

interface ProgressRecord {
  key: string;
  topic: string;
  queueId: string;
  brokerOffset: string;
  consumerOffset: string;
  diff: number;
}

// ─── Mock Data ──────────────────────────────────────────────────
const groupData: GroupRecord[] = [
  {
    key: '1',
    group: 'order-consumer-group',
    namespace: 'default',
    cluster: 'cluster-production',
    count: 4,
    consumeType: 'PUSH',
    messageModel: 'CLUSTERING',
    diff: 1280,
    status: 'running',
  },
  {
    key: '2',
    group: 'payment-consumer-group',
    namespace: 'default',
    cluster: 'cluster-production',
    count: 2,
    consumeType: 'PUSH',
    messageModel: 'CLUSTERING',
    diff: 0,
    status: 'running',
  },
  {
    key: '3',
    group: 'inventory-sync-group',
    namespace: 'ns-warehouse',
    cluster: 'cluster-warehouse',
    count: 3,
    consumeType: 'PUSH',
    messageModel: 'CLUSTERING',
    diff: 56800,
    status: 'warning',
  },
  {
    key: '4',
    group: 'log-processor-group',
    namespace: 'default',
    cluster: 'cluster-production',
    count: 1,
    consumeType: 'PUSH',
    messageModel: 'BROADCASTING',
    diff: 0,
    status: 'running',
  },
  {
    key: '5',
    group: 'notification-group',
    namespace: 'ns-notify',
    cluster: 'cluster-production',
    count: 2,
    consumeType: 'PUSH',
    messageModel: 'CLUSTERING',
    diff: 3,
    status: 'running',
  },
];

const subscriptionData: SubscriptionRecord[] = [
  { key: '1', topic: 'ORDER_TOPIC', consistency: 'consistent', subMode: 'TAG', expression: '*' },
  {
    key: '2',
    topic: 'PAYMENT_TOPIC',
    consistency: 'consistent',
    subMode: 'TAG',
    expression: 'pay_success || pay_fail',
  },
  {
    key: '3',
    topic: 'INVENTORY_TOPIC',
    consistency: 'inconsistent',
    subMode: 'TAG',
    expression: 'stock_update',
  },
];

const instanceData: InstanceRecord[] = [
  {
    key: '1',
    instanceId: 'cid-001',
    address: '10.0.1.10:10911',
    version: '5.3.0',
    status: 'online',
  },
  {
    key: '2',
    instanceId: 'cid-002',
    address: '10.0.1.11:10911',
    version: '5.3.0',
    status: 'online',
  },
  {
    key: '3',
    instanceId: 'cid-003',
    address: '10.0.1.12:10911',
    version: '5.3.0',
    status: 'online',
  },
  {
    key: '4',
    instanceId: 'cid-004',
    address: '10.0.1.13:10911',
    version: '5.3.0',
    status: 'online',
  },
];

const progressData: ProgressRecord[] = [
  {
    key: '1',
    topic: 'ORDER_TOPIC',
    queueId: '0',
    brokerOffset: '125,680',
    consumerOffset: '125,400',
    diff: 280,
  },
  {
    key: '2',
    topic: 'ORDER_TOPIC',
    queueId: '1',
    brokerOffset: '98,200',
    consumerOffset: '98,200',
    diff: 0,
  },
  {
    key: '3',
    topic: 'PAYMENT_TOPIC',
    queueId: '0',
    brokerOffset: '45,300',
    consumerOffset: '45,300',
    diff: 0,
  },
  {
    key: '4',
    topic: 'INVENTORY_TOPIC',
    queueId: '0',
    brokerOffset: '230,100',
    consumerOffset: '173,300',
    diff: 56800,
  },
];

// ─── Component ──────────────────────────────────────────────────
const GroupManagementPage = () => {
  const [searchText, setSearchText] = useState('');
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedGroup, setSelectedGroup] = useState<string | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const { t } = useLang();

  const handleViewDetail = (groupName: string) => {
    setSelectedGroup(groupName);
    setModalVisible(true);
  };

  const columns = [
    {
      title: t('groupMgmt.groupName'),
      dataIndex: 'group',
      key: 'group',
      render: (text: string) => (
        <a onClick={() => handleViewDetail(text)} style={{ color: '#1677ff', fontWeight: 500 }}>
          {text}
        </a>
      ),
    },
    { title: t('groupMgmt.namespace'), dataIndex: 'namespace', key: 'namespace' },
    { title: t('groupMgmt.cluster'), dataIndex: 'cluster', key: 'cluster' },
    {
      title: t('groupMgmt.onlineInstances'),
      dataIndex: 'count',
      key: 'count',
      render: (count: number) => <span style={{ fontWeight: 500 }}>{count}</span>,
    },
    {
      title: t('groupMgmt.consumeMode'),
      dataIndex: 'messageModel',
      key: 'messageModel',
      render: (mode: string) => (
        <Tag color={mode === 'CLUSTERING' ? 'blue' : 'orange'}>
          {mode === 'CLUSTERING' ? t('groupMgmt.clustering') : t('groupMgmt.broadcasting')}
        </Tag>
      ),
    },
    {
      title: t('groupMgmt.diff'),
      dataIndex: 'diff',
      key: 'diff',
      render: (diff: number) => (
        <span
          style={{
            color: diff > 10000 ? '#ff4d4f' : diff > 0 ? '#fa8c16' : '#52c41a',
            fontWeight: 500,
          }}
        >
          {diff.toLocaleString()}
        </span>
      ),
    },
    {
      title: t('brokerCluster.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const config: Record<string, { color: string; label: string }> = {
          running: { color: 'success', label: t('brokerCluster.statusRunning') },
          warning: { color: 'warning', label: t('groupMgmt.backlogAlert') },
          stopped: { color: 'error', label: t('groupMgmt.stopped') },
        };
        const { color, label } = config[status] || config.running;
        return <Tag color={color}>{label}</Tag>;
      },
    },
    {
      title: t('common.actions'),
      key: 'action',
      render: (_: unknown, record: GroupRecord) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => handleViewDetail(record.group)}>
            {t('common.detail')}
          </Button>
          <Button type="link" size="small">
            {t('brokerCluster.config')}
          </Button>
        </Space>
      ),
    },
  ];

  const subscriptionColumns = [
    {
      title: t('groupMgmt.topic'),
      dataIndex: 'topic',
      key: 'topic',
      render: (text: string) => <span style={{ fontWeight: 500 }}>{text}</span>,
    },
    {
      title: t('groupMgmt.consistency'),
      dataIndex: 'consistency',
      key: 'consistency',
      render: (consistency: string) => (
        <Tag color={consistency === 'consistent' ? 'success' : 'warning'}>
          {consistency === 'consistent' ? t('groupMgmt.consistent') : t('groupMgmt.inconsistent')}
        </Tag>
      ),
    },
    { title: t('groupMgmt.subMode'), dataIndex: 'subMode', key: 'subMode' },
    {
      title: t('groupMgmt.expression'),
      dataIndex: 'expression',
      key: 'expression',
      render: (text: string) => (
        <code style={{ background: '#f5f5f5', padding: '2px 6px', borderRadius: 4, fontSize: 12 }}>
          {text}
        </code>
      ),
    },
    {
      title: t('common.actions'),
      key: 'action',
      render: () => (
        <Button type="link" size="small" icon={<Eye size={14} />}>
          {t('groupMgmt.viewDistribution')}
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: 0 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 20,
        }}
      >
        <h2
          style={{
            fontSize: 20,
            fontWeight: 600,
            margin: 0,
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <Users size={22} style={{ marginRight: 8, color: '#1677ff' }} />
          {t('groupMgmt.title')}
        </h2>
        <Space size="middle">
          <Input
            placeholder={t('groupMgmt.searchPlaceholder')}
            prefix={<MagnifyingGlass size={14} />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            style={{ width: 240 }}
            allowClear
          />
          <Button type="primary" icon={<Plus size={14} />}>
            {t('groupMgmt.createGroup')}
          </Button>
          <Switch
            checked={autoRefresh}
            onChange={setAutoRefresh}
            checkedChildren={t('common.autoRefresh')}
            unCheckedChildren={t('groupMgmt.manual')}
            size="small"
          />
          <Button icon={<ArrowClockwise size={14} />} size="small">
            {t('common.reset')}
          </Button>
        </Space>
      </div>

      <Card bordered={false} style={{ borderRadius: 8, boxShadow: '0 1px 6px rgba(0,0,0,0.04)' }}>
        <Table
          columns={columns}
          dataSource={groupData.filter((d) => !searchText || d.group.includes(searchText))}
          pagination={{
            pageSize: 10,
            showTotal: (total) => `${t('common.total')} ${total} Group`,
            showSizeChanger: true,
          }}
          size="middle"
        />
      </Card>

      <Modal
        title={null}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        width={720}
        destroyOnClose
      >
        <div style={{ marginBottom: 16 }}>
          <h3 style={{ margin: 0, display: 'flex', alignItems: 'center' }}>
            <Users size={18} style={{ marginRight: 8, color: '#1677ff' }} />
            {selectedGroup}
          </h3>
        </div>
        <Tabs
          defaultActiveKey="overview"
          items={[
            {
              key: 'overview',
              label: t('groupMgmt.overview'),
              children: (
                <div>
                  <Row gutter={16} style={{ marginBottom: 20 }}>
                    <Col span={8}>
                      <Card bordered={false} style={{ background: '#f6ffed' }}>
                        <div style={{ color: '#666', fontSize: 12 }}>
                          {t('groupMgmt.onlineInstances')}
                        </div>
                        <div style={{ fontSize: 24, fontWeight: 600 }}>
                          4{' '}
                          <Tag color="success" style={{ marginLeft: 8 }}>
                            {t('groupMgmt.online')}
                          </Tag>
                        </div>
                      </Card>
                    </Col>
                    <Col span={8}>
                      <Card bordered={false} style={{ background: '#fff2f0' }}>
                        <div style={{ color: '#666', fontSize: 12 }}>
                          {t('groupMgmt.totalDiff')}
                        </div>
                        <div style={{ fontSize: 24, fontWeight: 600, color: '#ff4d4f' }}>1,280</div>
                      </Card>
                    </Col>
                    <Col span={8}>
                      <Card bordered={false} style={{ background: '#f0f5ff' }}>
                        <div style={{ color: '#666', fontSize: 12 }}>
                          {t('groupMgmt.subscribedTopics')}
                        </div>
                        <div style={{ fontSize: 24, fontWeight: 600 }}>3</div>
                      </Card>
                    </Col>
                  </Row>
                  <Descriptions column={2} bordered size="small">
                    <Descriptions.Item label={t('groupMgmt.groupName')}>
                      order-consumer-group
                    </Descriptions.Item>
                    <Descriptions.Item label={t('groupMgmt.namespace')}>default</Descriptions.Item>
                    <Descriptions.Item label={t('groupMgmt.cluster')}>
                      cluster-production
                    </Descriptions.Item>
                    <Descriptions.Item label={t('groupMgmt.consumeMode')}>
                      <Tag color="blue">{t('groupMgmt.clustering')}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label={t('groupMgmt.consumeType')}>PUSH</Descriptions.Item>
                    <Descriptions.Item label={t('groupMgmt.consumeDelay')}>12ms</Descriptions.Item>
                    <Descriptions.Item label={t('groupMgmt.maxRetry')}>16</Descriptions.Item>
                    <Descriptions.Item label={t('groupMgmt.createdAt')}>
                      2025-03-15 10:30:00
                    </Descriptions.Item>
                    <Descriptions.Item label={t('groupMgmt.subscribedTopics')} span={2}>
                      ORDER_TOPIC, PAYMENT_TOPIC, INVENTORY_TOPIC
                    </Descriptions.Item>
                  </Descriptions>
                  <h4 style={{ marginTop: 20, marginBottom: 12 }}>{t('groupMgmt.subscription')}</h4>
                  <Table
                    columns={subscriptionColumns}
                    dataSource={subscriptionData}
                    pagination={false}
                    size="small"
                  />
                </div>
              ),
            },
            {
              key: 'instances',
              label: t('groupMgmt.onlineInstances'),
              children: (
                <Table
                  columns={[
                    {
                      title: t('groupMgmt.instanceId'),
                      dataIndex: 'instanceId',
                      key: 'instanceId',
                    },
                    { title: t('common.address'), dataIndex: 'address', key: 'address' },
                    { title: t('brokerCluster.version'), dataIndex: 'version', key: 'version' },
                    {
                      title: t('brokerCluster.status'),
                      dataIndex: 'status',
                      key: 'status',
                      render: () => <Tag color="success">{t('groupMgmt.online')}</Tag>,
                    },
                  ]}
                  dataSource={instanceData}
                  pagination={false}
                  size="small"
                />
              ),
            },
            {
              key: 'progress',
              label: t('groupMgmt.consumeProgress'),
              children: (
                <Table
                  columns={[
                    { title: 'Topic', dataIndex: 'topic', key: 'topic' },
                    { title: 'QueueId', dataIndex: 'queueId', key: 'queueId' },
                    { title: 'Broker Offset', dataIndex: 'brokerOffset', key: 'brokerOffset' },
                    {
                      title: 'Consumer Offset',
                      dataIndex: 'consumerOffset',
                      key: 'consumerOffset',
                    },
                    {
                      title: 'Diff',
                      dataIndex: 'diff',
                      key: 'diff',
                      render: (v: number) => (
                        <span style={{ color: v > 100 ? '#ff4d4f' : '#52c41a', fontWeight: 500 }}>
                          {v}
                        </span>
                      ),
                    },
                  ]}
                  dataSource={progressData}
                  pagination={false}
                  size="small"
                />
              ),
            },
          ]}
        />
      </Modal>
    </div>
  );
};

export default GroupManagementPage;
