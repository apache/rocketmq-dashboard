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

import { useCallback, useEffect, useMemo, useState } from 'react';
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
  message,
} from 'antd';
import { MagnifyingGlass, ArrowClockwise, Users, Eye } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';
import type { ConsumerGroup, QueueProgress, SubscriptionEntry } from '../../api/metadata';
import {
  getConsumerProgress,
  getConsumerSubscriptions,
  listConsumerGroups,
} from '../../services/consumerService';

// ─── Helpers ────────────────────────────────────────────────────
type GroupStatus = 'running' | 'warning' | 'stopped';

const BACKLOG_WARNING_THRESHOLD = 10000;

const deriveStatus = (group: ConsumerGroup): GroupStatus => {
  if (group.onlineInstances <= 0) return 'stopped';
  if (group.totalLag > BACKLOG_WARNING_THRESHOLD) return 'warning';
  return 'running';
};

const isConsistent = (consistency: string): boolean =>
  consistency === 'consistent' || consistency === '一致';

// ─── Component ──────────────────────────────────────────────────
const GroupManagementPage = () => {
  const [searchText, setSearchText] = useState('');
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedGroup, setSelectedGroup] = useState<ConsumerGroup | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [groups, setGroups] = useState<ConsumerGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [subscriptions, setSubscriptions] = useState<SubscriptionEntry[]>([]);
  const [progress, setProgress] = useState<QueueProgress[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const { t } = useLang();

  useEffect(() => {
    let cancelled = false;

    const fetchGroups = async () => {
      try {
        const data = await listConsumerGroups();
        if (!cancelled) setGroups(data);
      } catch {
        if (!cancelled) message.error(t('consumer.fetchListFailed'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void fetchGroups();
    return () => {
      cancelled = true;
    };
  }, [t]);

  const handleRefresh = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listConsumerGroups();
      setGroups(data);
    } catch {
      message.error(t('consumer.fetchListFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  const handleViewDetail = useCallback(
    async (group: ConsumerGroup) => {
      setSelectedGroup(group);
      setModalVisible(true);
      setSubscriptions([]);
      setProgress([]);
      setDetailLoading(true);
      try {
        const [subs, prog] = await Promise.all([
          getConsumerSubscriptions(group.name),
          getConsumerProgress(group.name),
        ]);
        setSubscriptions(subs);
        setProgress(prog);
      } catch {
        message.error(t('consumer.fetchProgressFailed', { name: group.name }));
      } finally {
        setDetailLoading(false);
      }
    },
    [t],
  );

  const normalizedSearchText = searchText.trim().toLowerCase();
  const filteredGroupData = useMemo(
    () =>
      groups.filter(
        (record) =>
          !normalizedSearchText || record.name.toLowerCase().includes(normalizedSearchText),
      ),
    [groups, normalizedSearchText],
  );

  const columns = [
    {
      title: t('groupMgmt.groupName'),
      dataIndex: 'name',
      key: 'name',
      render: (text: string, record: ConsumerGroup) => (
        <a
          onClick={() => void handleViewDetail(record)}
          style={{ color: '#1677ff', fontWeight: 500, whiteSpace: 'nowrap' }}
        >
          {text}
        </a>
      ),
    },
    { title: t('groupMgmt.namespace'), dataIndex: 'namespace', key: 'namespace' },
    { title: t('groupMgmt.cluster'), dataIndex: 'clusterId', key: 'clusterId' },
    {
      title: t('groupMgmt.onlineInstances'),
      dataIndex: 'onlineInstances',
      key: 'onlineInstances',
      render: (count: number) => <span style={{ fontWeight: 500 }}>{count}</span>,
    },
    {
      title: t('groupMgmt.consumeMode'),
      dataIndex: 'consumeType',
      key: 'consumeType',
      render: (mode: string) => (
        <Tag color={mode === 'CLUSTERING' ? 'blue' : 'orange'}>
          {mode === 'CLUSTERING' ? t('groupMgmt.clustering') : t('groupMgmt.broadcasting')}
        </Tag>
      ),
    },
    {
      title: t('groupMgmt.diff'),
      dataIndex: 'totalLag',
      key: 'totalLag',
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
      key: 'status',
      render: (_: unknown, record: ConsumerGroup) => {
        const status = deriveStatus(record);
        const config: Record<GroupStatus, { color: string; label: string }> = {
          running: { color: 'success', label: t('brokerCluster.statusRunning') },
          warning: { color: 'warning', label: t('groupMgmt.backlogAlert') },
          stopped: { color: 'error', label: t('groupMgmt.stopped') },
        };
        const { color, label } = config[status];
        return <Tag color={color}>{label}</Tag>;
      },
    },
    {
      title: t('common.actions'),
      key: 'action',
      render: (_: unknown, record: ConsumerGroup) => (
        <Button type="link" size="small" onClick={() => void handleViewDetail(record)}>
          {t('common.detail')}
        </Button>
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
        <Tag color={isConsistent(consistency) ? 'success' : 'warning'}>
          {isConsistent(consistency) ? t('groupMgmt.consistent') : t('groupMgmt.inconsistent')}
        </Tag>
      ),
    },
    { title: t('groupMgmt.subMode'), dataIndex: 'filterMode', key: 'filterMode' },
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
          <Switch
            checked={autoRefresh}
            onChange={setAutoRefresh}
            checkedChildren={t('common.autoRefresh')}
            unCheckedChildren={t('groupMgmt.manual')}
            size="small"
          />
          <Button
            icon={<ArrowClockwise size={14} />}
            size="small"
            onClick={() => void handleRefresh()}
          >
            {t('common.reset')}
          </Button>
        </Space>
      </div>

      <Card bordered={false} style={{ borderRadius: 8, boxShadow: '0 1px 6px rgba(0,0,0,0.04)' }}>
        <Table
          columns={columns}
          dataSource={filteredGroupData}
          rowKey="name"
          loading={loading}
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
            {selectedGroup?.name}
          </h3>
        </div>
        {selectedGroup && (
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
                            {selectedGroup.onlineInstances}{' '}
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
                          <div style={{ fontSize: 24, fontWeight: 600, color: '#ff4d4f' }}>
                            {selectedGroup.totalLag.toLocaleString()}
                          </div>
                        </Card>
                      </Col>
                      <Col span={8}>
                        <Card bordered={false} style={{ background: '#f0f5ff' }}>
                          <div style={{ color: '#666', fontSize: 12 }}>
                            {t('groupMgmt.subscribedTopics')}
                          </div>
                          <div style={{ fontSize: 24, fontWeight: 600 }}>
                            {selectedGroup.subscribedTopics.length}
                          </div>
                        </Card>
                      </Col>
                    </Row>
                    <Descriptions column={2} bordered size="small">
                      <Descriptions.Item label={t('groupMgmt.groupName')}>
                        {selectedGroup.name}
                      </Descriptions.Item>
                      <Descriptions.Item label={t('groupMgmt.namespace')}>
                        {selectedGroup.namespace}
                      </Descriptions.Item>
                      <Descriptions.Item label={t('groupMgmt.cluster')}>
                        {selectedGroup.clusterId}
                      </Descriptions.Item>
                      <Descriptions.Item label={t('groupMgmt.consumeMode')}>
                        <Tag color={selectedGroup.consumeType === 'CLUSTERING' ? 'blue' : 'orange'}>
                          {selectedGroup.consumeType === 'CLUSTERING'
                            ? t('groupMgmt.clustering')
                            : t('groupMgmt.broadcasting')}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label={t('groupMgmt.consumeType')}>
                        {selectedGroup.subscriptionMode}
                      </Descriptions.Item>
                      <Descriptions.Item label={t('groupMgmt.consumeDelay')}>
                        {selectedGroup.delaySeconds.toLocaleString()}s
                      </Descriptions.Item>
                      <Descriptions.Item label={t('groupMgmt.maxRetry')}>
                        {selectedGroup.retryMaxTimes}
                      </Descriptions.Item>
                      <Descriptions.Item label={t('groupMgmt.createdAt')}>
                        {selectedGroup.createdAt}
                      </Descriptions.Item>
                      <Descriptions.Item label={t('groupMgmt.subscribedTopics')} span={2}>
                        {selectedGroup.subscribedTopics.join(', ')}
                      </Descriptions.Item>
                    </Descriptions>
                    <h4 style={{ marginTop: 20, marginBottom: 12 }}>
                      {t('groupMgmt.subscription')}
                    </h4>
                    <Table
                      columns={subscriptionColumns}
                      dataSource={subscriptions}
                      rowKey="topic"
                      loading={detailLoading}
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
                        dataIndex: 'clientId',
                        key: 'clientId',
                      },
                      { title: t('common.address'), dataIndex: 'address', key: 'address' },
                      { title: t('brokerCluster.version'), dataIndex: 'protocol', key: 'protocol' },
                      {
                        title: t('brokerCluster.status'),
                        key: 'status',
                        render: () => <Tag color="success">{t('groupMgmt.online')}</Tag>,
                      },
                    ]}
                    dataSource={selectedGroup.instances}
                    rowKey="clientId"
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
                      { title: 'Broker', dataIndex: 'broker', key: 'broker' },
                      { title: 'QueueId', dataIndex: 'queueId', key: 'queueId' },
                      {
                        title: 'Broker Offset',
                        dataIndex: 'brokerOffset',
                        key: 'brokerOffset',
                        render: (v: number) => v.toLocaleString(),
                      },
                      {
                        title: 'Consumer Offset',
                        dataIndex: 'consumerOffset',
                        key: 'consumerOffset',
                        render: (v: number) => v.toLocaleString(),
                      },
                      {
                        title: 'Diff',
                        dataIndex: 'diffTotal',
                        key: 'diffTotal',
                        render: (v: number) => (
                          <span style={{ color: v > 100 ? '#ff4d4f' : '#52c41a', fontWeight: 500 }}>
                            {v.toLocaleString()}
                          </span>
                        ),
                      },
                    ]}
                    dataSource={progress}
                    rowKey={(record) => `${record.broker}-${record.queueId}`}
                    loading={detailLoading}
                    pagination={false}
                    size="small"
                  />
                ),
              },
            ]}
          />
        )}
      </Modal>
    </div>
  );
};

export default GroupManagementPage;
