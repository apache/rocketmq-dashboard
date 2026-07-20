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

import { useState, useRef } from 'react';
import {
  Table,
  Button,
  Input,
  Select,
  Tag,
  Modal,
  Drawer,
  Card,
  Row,
  Col,
  Progress,
  Space,
  Statistic,
  Descriptions,
  Form,
  InputNumber,
  Alert,
  App,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  MagnifyingGlass,
  ArrowClockwise,
  ClockCounterClockwise,
  Eye,
  PencilSimple,
  Gauge,
  Info,
} from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import {
  queryLiteTopicList,
  queryLiteTopicQuota,
  queryLiteTopicSession,
  extendLiteTopicTTL,
  queryLiteTopicCapability,
  type LiteTopicQuota,
  type LiteTopicItem,
  type LiteTopicSession,
} from '../../api/liteTopic';

const formatDuration = (ms: number | undefined | null): string => {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  if (ms < 3600000) return `${(ms / 60000).toFixed(1)}min`;
  return `${(ms / 3600000).toFixed(1)}h`;
};

const formatTime = (timestamp: number | undefined | null): string => {
  if (!timestamp) return '-';
  return new Date(timestamp).toLocaleString();
};

const getProgressStatus = (percent: number): 'exception' | 'active' | 'normal' => {
  if (percent >= 90) return 'exception';
  if (percent >= 70) return 'active';
  return 'normal';
};

const LiteTopicPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();

  const [loading, setLoading] = useState(false);
  const [topicList, setTopicList] = useState<LiteTopicItem[]>([]);
  const [quota, setQuota] = useState<LiteTopicQuota | null>(null);
  const [capabilitySupported, setCapabilitySupported] = useState(true);
  const [patternFilter, setPatternFilter] = useState('');
  const [namespaceFilter, setNamespaceFilter] = useState('');

  // Session drawer
  const [sessionDrawerOpen, setSessionDrawerOpen] = useState(false);
  const [sessionData, setSessionData] = useState<LiteTopicSession | null>(null);
  const [sessionLoading, setSessionLoading] = useState(false);

  // Extend TTL modal
  const [extendTTLModalOpen, setExtendTTLModalOpen] = useState(false);
  const [extendTTLForm, setExtendTTLForm] = useState<{
    topicPattern: string;
    newTTL: number | null;
  }>({ topicPattern: '', newTTL: null });
  const [extendTTLLoading, setExtendTTLLoading] = useState(false);

  const initialized = useRef<boolean | null>(null);
  if (initialized.current == null) {
    initialized.current = true;
    const init = async () => {
      try {
        const cap = await queryLiteTopicCapability();
        if (cap && cap.supported === false) {
          setCapabilitySupported(false);
          return;
        }
      } catch {
        // Assume supported on error
      }
      try {
        const q = await queryLiteTopicQuota();
        setQuota(q);
      } catch {
        // ignore
      }
      setLoading(true);
      try {
        const list = await queryLiteTopicList();
        setTopicList(list);
      } catch {
        message.error(t('liteTopic.fetchListFailed'));
      } finally {
        setLoading(false);
      }
    };
    init();
  }

  const fetchQuota = async () => {
    try {
      const q = await queryLiteTopicQuota(namespaceFilter || undefined);
      setQuota(q);
    } catch {
      // ignore
    }
  };

  const fetchTopicList = async () => {
    setLoading(true);
    try {
      const list = await queryLiteTopicList(
        patternFilter || undefined,
        namespaceFilter || undefined,
      );
      setTopicList(list);
    } catch {
      message.error(t('liteTopic.fetchListFailed'));
      setTopicList([]);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = () => {
    fetchTopicList();
  };

  const handleRefresh = () => {
    fetchQuota();
    fetchTopicList();
  };

  const handleNamespaceChange = (val: string | undefined) => {
    setNamespaceFilter(val || '');
    // Re-fetch with new namespace
    const doFetch = async () => {
      setLoading(true);
      try {
        const ns = val || undefined;
        const [q, list] = await Promise.all([
          queryLiteTopicQuota(ns),
          queryLiteTopicList(patternFilter || undefined, ns),
        ]);
        setQuota(q);
        setTopicList(list);
      } catch {
        message.error(t('liteTopic.fetchListFailed'));
      } finally {
        setLoading(false);
      }
    };
    doFetch();
  };

  const handleViewSessions = async (sessionId: string) => {
    setSessionDrawerOpen(true);
    setSessionLoading(true);
    try {
      const data = await queryLiteTopicSession(sessionId);
      setSessionData(data);
    } catch {
      message.error(t('liteTopic.fetchSessionFailed'));
      setSessionData(null);
    } finally {
      setSessionLoading(false);
    }
  };

  const handleOpenExtendTTL = (record: LiteTopicItem) => {
    setExtendTTLForm({
      topicPattern: record.topicPattern || '',
      newTTL: null,
    });
    setExtendTTLModalOpen(true);
  };

  const handleExtendTTL = async () => {
    if (!extendTTLForm.topicPattern || extendTTLForm.newTTL == null) return;
    setExtendTTLLoading(true);
    try {
      await extendLiteTopicTTL(extendTTLForm.topicPattern, extendTTLForm.newTTL);
      message.success(t('liteTopic.extendTtlSuccess'));
      setExtendTTLModalOpen(false);
      fetchTopicList();
      fetchQuota();
    } catch {
      message.error(t('liteTopic.extendTtlFailed'));
    } finally {
      setExtendTTLLoading(false);
    }
  };

  const getTTLStatusTag = (status: string | undefined) => {
    const map: Record<string, { color: string; label: string }> = {
      ACTIVE: { color: 'success', label: t('liteTopic.active') },
      EXPIRING_SOON: { color: 'warning', label: t('liteTopic.expiringSoon') },
      EXPIRED: { color: 'error', label: t('liteTopic.expired') },
    };
    const cfg = map[status || ''] || { color: 'default', label: t('liteTopic.unknown') };
    return <Tag color={cfg.color}>{cfg.label}</Tag>;
  };

  // ─── Columns ─────────────────────────────────────────────────

  const columns: ColumnsType<LiteTopicItem> = [
    {
      title: t('liteTopic.pattern'),
      dataIndex: 'topicPattern',
      key: 'topicPattern',
      render: (text: string) => <span style={{ fontWeight: 500, color: '#1677ff' }}>{text}</span>,
      ellipsis: true,
    },
    {
      title: t('liteTopic.topicCount'),
      dataIndex: 'topicCount',
      key: 'topicCount',
      render: (val: number) => <span style={{ fontWeight: 500 }}>{val ?? '-'}</span>,
      sorter: (a, b) => (a.topicCount || 0) - (b.topicCount || 0),
    },
    {
      title: t('liteTopic.consumers'),
      dataIndex: 'consumerCount',
      key: 'consumerCount',
      render: (val: number) => <span style={{ fontWeight: 500 }}>{val ?? '-'}</span>,
      sorter: (a, b) => (a.consumerCount || 0) - (b.consumerCount || 0),
    },
    {
      title: t('liteTopic.backlog'),
      dataIndex: 'totalBacklog',
      key: 'totalBacklog',
      render: (val: number) => {
        const num = val ?? 0;
        const color = num > 10000 ? '#ff4d4f' : num > 0 ? '#fa8c16' : '#52c41a';
        return <span style={{ color, fontWeight: 500 }}>{num.toLocaleString()}</span>;
      },
      sorter: (a, b) => (a.totalBacklog || 0) - (b.totalBacklog || 0),
    },
    {
      title: t('liteTopic.avgTtl'),
      dataIndex: 'averageTTL',
      key: 'averageTTL',
      render: (val: number) => formatDuration(val),
      sorter: (a, b) => (a.averageTTL || 0) - (b.averageTTL || 0),
    },
    {
      title: t('liteTopic.status'),
      dataIndex: 'ttlStatus',
      key: 'ttlStatus',
      render: (status: string) => getTTLStatusTag(status),
    },
    {
      title: t('liteTopic.lastActive'),
      dataIndex: 'lastActiveTime',
      key: 'lastActiveTime',
      render: (val: number) => formatTime(val),
      sorter: (a, b) => (a.lastActiveTime || 0) - (b.lastActiveTime || 0),
    },
    {
      title: t('liteTopic.actions'),
      key: 'action',
      render: (_: unknown, record: LiteTopicItem) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<PencilSimple size={14} />}
            onClick={(e) => {
              e.stopPropagation();
              handleOpenExtendTTL(record);
            }}
          >
            {t('liteTopic.extendTtl')}
          </Button>
          {record.sessionIds && record.sessionIds.length > 0 && (
            <Button
              type="link"
              size="small"
              icon={<Eye size={14} />}
              onClick={(e) => {
                e.stopPropagation();
                handleViewSessions(record.sessionIds![0]);
              }}
            >
              {t('liteTopic.viewSessions')}
            </Button>
          )}
        </Space>
      ),
    },
  ];

  // ─── Quota Panel ─────────────────────────────────────────────

  const renderQuotaPanel = () => {
    if (!quota) return null;

    const topicUsagePercent =
      quota.usageRate != null
        ? Math.round(quota.usageRate * 100)
        : quota.maxTopicCount > 0
          ? Math.round((quota.currentTopicCount / quota.maxTopicCount) * 100)
          : 0;
    const sessionUsagePercent =
      quota.sessionUsageRate != null
        ? Math.round(quota.sessionUsageRate * 100)
        : quota.maxSessionCount > 0
          ? Math.round((quota.currentSessionCount / quota.maxSessionCount) * 100)
          : 0;
    const creationRatePercent =
      quota.maxCreationRate > 0
        ? Math.round((quota.currentCreationRate / quota.maxCreationRate) * 100)
        : 0;

    return (
      <Card bordered={false} style={{ marginBottom: 16, borderRadius: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 16 }}>
          <Gauge size={20} weight="bold" style={{ marginRight: 8, color: '#1677ff' }} />
          <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600 }}>
            {t('liteTopic.quotaOverview')}
          </h3>
        </div>
        <Row gutter={24}>
          <Col span={8}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 13, color: '#595959', marginBottom: 8, fontWeight: 500 }}>
                {t('liteTopic.topicUsage')}
              </div>
              <Progress
                percent={topicUsagePercent}
                status={getProgressStatus(topicUsagePercent)}
                strokeColor={
                  topicUsagePercent >= 90
                    ? '#ff4d4f'
                    : topicUsagePercent >= 70
                      ? '#fa8c16'
                      : '#1677ff'
                }
              />
              <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                {quota.currentTopicCount} / {quota.maxTopicCount}
              </div>
            </div>
          </Col>
          <Col span={8}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 13, color: '#595959', marginBottom: 8, fontWeight: 500 }}>
                {t('liteTopic.sessionUsage')}
              </div>
              <Progress
                percent={sessionUsagePercent}
                status={getProgressStatus(sessionUsagePercent)}
                strokeColor={
                  sessionUsagePercent >= 90
                    ? '#ff4d4f'
                    : sessionUsagePercent >= 70
                      ? '#fa8c16'
                      : '#1677ff'
                }
              />
              <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                {quota.currentSessionCount} / {quota.maxSessionCount}
              </div>
            </div>
          </Col>
          <Col span={8}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 13, color: '#595959', marginBottom: 8, fontWeight: 500 }}>
                {t('liteTopic.creationRate')}
              </div>
              <Progress
                percent={creationRatePercent}
                status={getProgressStatus(creationRatePercent)}
                strokeColor={
                  creationRatePercent >= 90
                    ? '#ff4d4f'
                    : creationRatePercent >= 70
                      ? '#fa8c16'
                      : '#1677ff'
                }
              />
              <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                {quota.currentCreationRate} / {quota.maxCreationRate}
              </div>
            </div>
          </Col>
        </Row>
        <Row gutter={16} style={{ marginTop: 16 }}>
          <Col span={6}>
            <Statistic title={t('liteTopic.defaultTtl')} value={formatDuration(quota.defaultTTL)} />
          </Col>
          <Col span={6}>
            <Statistic title={t('liteTopic.maxTtl')} value={formatDuration(quota.maxTTL)} />
          </Col>
          <Col span={6}>
            <Statistic title={t('liteTopic.remainingQuota')} value={quota.remainingQuota ?? '-'} />
          </Col>
          <Col span={6}>
            <Statistic
              title={t('liteTopic.consumerDensity')}
              value={quota.consumerDensity ?? '-'}
            />
          </Col>
        </Row>
      </Card>
    );
  };

  // ─── Session Drawer Content ──────────────────────────────────

  const renderSessionContent = () => {
    if (sessionLoading) {
      return <div style={{ textAlign: 'center', padding: 40 }}>{t('common.loading')}...</div>;
    }
    if (!sessionData) {
      return <div style={{ textAlign: 'center', padding: 40 }}>{t('common.noData')}</div>;
    }

    const consumptionPercent =
      sessionData.totalMessages != null && sessionData.totalMessages > 0
        ? Math.round(((sessionData.consumedMessages ?? 0) / sessionData.totalMessages) * 100)
        : 0;

    return (
      <div>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label={t('liteTopic.sessionId')} span={2}>
            <code style={{ fontSize: 12 }}>{sessionData.sessionId}</code>
          </Descriptions.Item>
          <Descriptions.Item label={t('liteTopic.clientId')}>
            {sessionData.clientId || '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('liteTopic.clientAddress')}>
            <code style={{ fontSize: 12 }}>{sessionData.clientAddress || '-'}</code>
          </Descriptions.Item>
          <Descriptions.Item label={t('liteTopic.parentTopic')}>
            {sessionData.parentTopic || '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('liteTopic.consumerGroup')}>
            {sessionData.consumerGroup || '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('liteTopic.createTime')}>
            {formatTime(sessionData.createTime)}
          </Descriptions.Item>
          <Descriptions.Item label={t('liteTopic.lastActive')}>
            {formatTime(sessionData.lastActiveTime)}
          </Descriptions.Item>
          <Descriptions.Item label={t('liteTopic.ttl')}>
            {formatDuration(sessionData.ttl)}
          </Descriptions.Item>
          <Descriptions.Item label={t('liteTopic.ttlRemaining')}>
            {formatDuration(sessionData.ttlRemaining)}
          </Descriptions.Item>
          <Descriptions.Item label={t('liteTopic.sessionStatus')}>
            <Tag
              color={
                sessionData.status === 'ACTIVE'
                  ? 'success'
                  : sessionData.status === 'EXPIRED'
                    ? 'error'
                    : 'default'
              }
            >
              {sessionData.status || '-'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('liteTopic.creationCount')}>
            {sessionData.liteTopicCreationCount ?? '-'}
          </Descriptions.Item>
        </Descriptions>

        <h4 style={{ marginTop: 20, marginBottom: 12, fontSize: 14, fontWeight: 600 }}>
          {t('liteTopic.consumptionRate')}
        </h4>
        <Row gutter={16}>
          <Col span={8}>
            <Card
              bordered={false}
              style={{ background: '#f6ffed', borderRadius: 8, textAlign: 'center', padding: 12 }}
            >
              <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>
                {t('liteTopic.totalMessages')}
              </div>
              <div style={{ fontSize: 24, fontWeight: 700 }}>{sessionData.totalMessages ?? 0}</div>
            </Card>
          </Col>
          <Col span={8}>
            <Card
              bordered={false}
              style={{ background: '#f6ffed', borderRadius: 8, textAlign: 'center', padding: 12 }}
            >
              <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>
                {t('liteTopic.consumedMessages')}
              </div>
              <div style={{ fontSize: 24, fontWeight: 700 }}>
                {sessionData.consumedMessages ?? 0}
              </div>
            </Card>
          </Col>
          <Col span={8}>
            <Card
              bordered={false}
              style={{ background: '#fffbe6', borderRadius: 8, textAlign: 'center', padding: 12 }}
            >
              <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>
                {t('liteTopic.pendingMessages')}
              </div>
              <div style={{ fontSize: 24, fontWeight: 700, color: '#fa8c16' }}>
                {sessionData.pendingMessages ?? 0}
              </div>
            </Card>
          </Col>
        </Row>

        <div style={{ marginTop: 16 }}>
          <div style={{ marginBottom: 8, fontWeight: 500 }}>{t('liteTopic.consumptionRate')}</div>
          <Progress
            percent={consumptionPercent}
            status={consumptionPercent >= 100 ? 'success' : 'active'}
            strokeColor={consumptionPercent >= 100 ? '#52c41a' : '#1677ff'}
          />
        </div>

        {sessionData.popProgress != null && (
          <div style={{ marginTop: 16 }}>
            <div style={{ marginBottom: 8, fontWeight: 500 }}>{t('liteTopic.popProgress')}</div>
            <Progress
              percent={Math.round(sessionData.popProgress * 100)}
              status="active"
              strokeColor="#722ed1"
            />
          </div>
        )}

        {sessionData.liteTopics && sessionData.liteTopics.length > 0 && (
          <>
            <h4 style={{ marginTop: 20, marginBottom: 12, fontSize: 14, fontWeight: 600 }}>
              {t('liteTopic.liteTopics')}
            </h4>
            <Table
              dataSource={sessionData.liteTopics.map((lt, idx) => ({
                key: idx,
                ...lt,
              }))}
              columns={[
                {
                  title: t('topic.name'),
                  dataIndex: 'topicName',
                  key: 'topicName',
                  render: (text: string) => <span style={{ fontWeight: 500 }}>{text}</span>,
                },
                {
                  title: t('liteTopic.status'),
                  dataIndex: 'status',
                  key: 'status',
                  render: (s: string) => (
                    <Tag color={s === 'ACTIVE' ? 'success' : 'default'}>{s}</Tag>
                  ),
                },
                {
                  title: t('liteTopic.ttlRemaining'),
                  dataIndex: 'ttlRemaining',
                  key: 'ttlRemaining',
                  render: (v: number) => formatDuration(v),
                },
              ]}
              pagination={false}
              size="small"
            />
          </>
        )}
      </div>
    );
  };

  // ─── Graceful Degradation ────────────────────────────────────

  if (!capabilitySupported) {
    return (
      <div style={{ padding: 0 }}>
        <PageHeader title={t('liteTopic.title')} icon={Gauge} />
        <Alert
          message={t('liteTopic.notSupported')}
          type="info"
          showIcon
          icon={<Info size={16} />}
          style={{ marginTop: 16 }}
        />
      </div>
    );
  }

  // ─── Main Render ─────────────────────────────────────────────

  return (
    <div style={{ padding: 0 }}>
      <PageHeader
        title={t('liteTopic.title')}
        icon={Gauge}
        extra={
          <Button icon={<ArrowClockwise size={14} />} size="small" onClick={handleRefresh}>
            {t('common.refresh')}
          </Button>
        }
      />

      {/* Quota Panel */}
      {renderQuotaPanel()}

      {/* Search / Filter Bar */}
      <Card bordered={false} style={{ marginBottom: 16, borderRadius: 8 }}>
        <Space size="middle" wrap>
          <Input
            placeholder={t('liteTopic.searchPlaceholder')}
            prefix={<MagnifyingGlass size={14} />}
            value={patternFilter}
            onChange={(e) => setPatternFilter(e.target.value)}
            onPressEnter={handleSearch}
            style={{ width: 260 }}
            allowClear
          />
          <Select
            placeholder={t('liteTopic.namespacePlaceholder')}
            value={namespaceFilter || undefined}
            onChange={handleNamespaceChange}
            style={{ width: 180 }}
            allowClear
          >
            <Select.Option value="">{t('liteTopic.allNamespaces')}</Select.Option>
          </Select>
          <Button type="primary" icon={<MagnifyingGlass size={14} />} onClick={handleSearch}>
            {t('common.search')}
          </Button>
        </Space>
      </Card>

      {/* Main Table */}
      <Card bordered={false} style={{ borderRadius: 8 }}>
        <Table
          columns={columns}
          dataSource={topicList}
          rowKey={(record) => record.topicPattern || Math.random().toString()}
          loading={loading}
          pagination={{
            pageSize: 10,
            showTotal: (total) => t('liteTopic.total').replace('{total}', String(total)),
            showSizeChanger: true,
          }}
          size="middle"
        />
      </Card>

      {/* Session Detail Drawer */}
      <Drawer
        title={
          <span>
            <ClockCounterClockwise size={16} style={{ marginRight: 8, color: '#1677ff' }} />
            {t('liteTopic.sessionDetail')}
          </span>
        }
        placement="right"
        width={680}
        open={sessionDrawerOpen}
        onClose={() => {
          setSessionDrawerOpen(false);
          setSessionData(null);
        }}
        destroyOnClose
      >
        {renderSessionContent()}
      </Drawer>

      {/* Extend TTL Modal */}
      <Modal
        title={t('liteTopic.extendTtlModalTitle')}
        open={extendTTLModalOpen}
        onOk={handleExtendTTL}
        onCancel={() => setExtendTTLModalOpen(false)}
        confirmLoading={extendTTLLoading}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
        destroyOnClose
      >
        <Form layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label={t('liteTopic.pattern')}>
            <Input
              value={extendTTLForm.topicPattern}
              onChange={(e) => setExtendTTLForm({ ...extendTTLForm, topicPattern: e.target.value })}
              disabled
            />
          </Form.Item>
          <Form.Item label={t('liteTopic.newTtl')} required>
            <InputNumber
              style={{ width: '100%' }}
              placeholder={t('liteTopic.newTtlPlaceholder')}
              value={extendTTLForm.newTTL}
              onChange={(val) => setExtendTTLForm({ ...extendTTLForm, newTTL: val })}
              min={1}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default LiteTopicPage;
