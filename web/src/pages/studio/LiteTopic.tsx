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

const knownTTLStatuses = new Set(['ACTIVE', 'EXPIRING_SOON', 'EXPIRED']);

const collectNamespaces = (items: LiteTopicItem[]): string[] => {
  const namespaces = new Map<string, string>();

  items.forEach((item) => {
    const namespace = item.namespace?.trim() || '';
    if (!namespace) return;

    const normalized = namespace.toLowerCase();
    if (!namespaces.has(normalized)) {
      namespaces.set(normalized, namespace);
    }
  });

  return Array.from(namespaces.values()).sort((left, right) => {
    const normalizedOrder = left.toLowerCase().localeCompare(right.toLowerCase());
    return normalizedOrder || left.localeCompare(right);
  });
};

const LiteTopicPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const messageRef = useRef(message);
  const translationRef = useRef(t);

  const [loading, setLoading] = useState(false);
  const [topicList, setTopicList] = useState<LiteTopicItem[]>([]);
  const [quota, setQuota] = useState<LiteTopicQuota | null>(null);
  const [capabilitySupported, setCapabilitySupported] = useState(true);
  const [patternFilter, setPatternFilter] = useState('');
  const [namespaceFilter, setNamespaceFilter] = useState('');
  const [ttlStatusFilter, setTTLStatusFilter] = useState('');
  const [namespaceOptions, setNamespaceOptions] = useState<string[]>([]);

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

  const mountedRef = useRef(false);
  const bootstrapRequestId = useRef(0);
  const displayRequestId = useRef(0);
  const sessionRequestId = useRef(0);

  useEffect(() => {
    messageRef.current = message;
    translationRef.current = t;
  }, [message, t]);

  const fetchData = useCallback(
    async (
      pattern: string | undefined,
      namespace: string | undefined,
      options: { clear?: boolean; collectNamespaceOptions?: boolean } = {},
    ) => {
      const requestId = ++displayRequestId.current;

      if (options.clear) {
        setTopicList([]);
        setQuota(null);
      }
      setLoading(true);

      const [quotaResult, listResult] = await Promise.allSettled([
        queryLiteTopicQuota(namespace),
        queryLiteTopicList(pattern, namespace),
      ]);

      if (!mountedRef.current) return;

      if (listResult.status === 'fulfilled' && options.collectNamespaceOptions) {
        setNamespaceOptions(collectNamespaces(listResult.value));
      }

      if (requestId !== displayRequestId.current) return;

      if (quotaResult.status === 'fulfilled') {
        setQuota(quotaResult.value);
      }

      if (listResult.status === 'fulfilled') {
        setTopicList(listResult.value);
      } else {
        setTopicList([]);
        messageRef.current.error(translationRef.current('liteTopic.fetchListFailed'));
      }

      setLoading(false);
    },
    [],
  );

  useEffect(() => {
    const mountedState = mountedRef;
    const bootstrapCounter = bootstrapRequestId;
    const displayCounter = displayRequestId;
    mountedState.current = true;

    const bootstrapId = ++bootstrapCounter.current;
    const initialDisplayRequestId = displayCounter.current;
    const bootstrapIsActive = () =>
      mountedState.current && bootstrapId === bootstrapCounter.current;

    const initialize = async () => {
      try {
        const capability = await queryLiteTopicCapability();
        if (!bootstrapIsActive()) return;
        if (capability && capability.supported === false) {
          ++displayCounter.current;
          setCapabilitySupported(false);
          setTopicList([]);
          setQuota(null);
          setLoading(false);
          return;
        }
      } catch {
        if (!bootstrapIsActive()) return;
        ++displayCounter.current;
        setCapabilitySupported(false);
        setTopicList([]);
        setQuota(null);
        setLoading(false);
        return;
      }

      if (displayCounter.current === initialDisplayRequestId) {
        await fetchData(undefined, undefined, { collectNamespaceOptions: true });
      } else {
        try {
          const list = await queryLiteTopicList();
          if (bootstrapIsActive()) {
            setNamespaceOptions(collectNamespaces(list));
          }
        } catch {
          // The active display request owns user-visible error handling.
        }
      }
    };

    void initialize();

    return () => {
      mountedState.current = false;
      ++bootstrapCounter.current;
      ++displayCounter.current;
    };
  }, [fetchData]);

  const handleSearch = () => {
    void fetchData(patternFilter || undefined, namespaceFilter || undefined);
  };

  const handleRefresh = () => {
    void fetchData(patternFilter || undefined, namespaceFilter || undefined);
  };

  const handleNamespaceChange = (val: string | undefined) => {
    const namespace = val || undefined;
    setNamespaceFilter(namespace || '');
    void fetchData(patternFilter || undefined, namespace, { clear: true });
  };

  const handleViewSessions = async (sessionId: string) => {
    const requestId = ++sessionRequestId.current;
    setSessionDrawerOpen(true);
    setSessionLoading(true);
    setSessionData(null);
    try {
      const data = await queryLiteTopicSession(sessionId);
      if (requestId !== sessionRequestId.current) return;
      setSessionData(data);
    } catch {
      if (requestId !== sessionRequestId.current) return;
      message.error(t('liteTopic.fetchSessionFailed'));
      setSessionData(null);
    } finally {
      if (requestId === sessionRequestId.current) {
        setSessionLoading(false);
      }
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
      void fetchData(patternFilter || undefined, namespaceFilter || undefined);
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

  const filteredTopicList = topicList.filter((item) => {
    if (!ttlStatusFilter) return true;
    if (ttlStatusFilter === 'UNKNOWN') {
      return !item.ttlStatus || !knownTTLStatuses.has(item.ttlStatus);
    }
    return item.ttlStatus === ttlStatusFilter;
  });

  // ─── Columns ─────────────────────────────────────────────────

  const columns: ColumnsType<LiteTopicItem> = [
    {
      title: t('liteTopic.namespace'),
      dataIndex: 'namespace',
      key: 'namespace',
      render: (text: string) => text || '-',
      ellipsis: true,
    },
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
      <Card variant="borderless" style={{ marginBottom: 16, borderRadius: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 16 }}>
          <Gauge size={20} weight="bold" style={{ marginRight: 8, color: '#1677ff' }} />
          <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600 }}>
            {t('liteTopic.quotaOverview')}
          </h3>
        </div>
        <Row gutter={24}>
          <Col span={8}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 14, color: '#595959', marginBottom: 8, fontWeight: 500 }}>
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
              <div style={{ fontSize: 14, color: '#8c8c8c', marginTop: 4 }}>
                {quota.currentTopicCount} / {quota.maxTopicCount}
              </div>
            </div>
          </Col>
          <Col span={8}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 14, color: '#595959', marginBottom: 8, fontWeight: 500 }}>
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
              <div style={{ fontSize: 14, color: '#8c8c8c', marginTop: 4 }}>
                {quota.currentSessionCount} / {quota.maxSessionCount}
              </div>
            </div>
          </Col>
          <Col span={8}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 14, color: '#595959', marginBottom: 8, fontWeight: 500 }}>
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
              <div style={{ fontSize: 14, color: '#8c8c8c', marginTop: 4 }}>
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
            <code style={{ fontSize: 14 }}>{sessionData.sessionId}</code>
          </Descriptions.Item>
          <Descriptions.Item label={t('liteTopic.clientId')}>
            {sessionData.clientId || '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('liteTopic.clientAddress')}>
            <code style={{ fontSize: 14 }}>{sessionData.clientAddress || '-'}</code>
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
              variant="borderless"
              style={{ background: '#f6ffed', borderRadius: 8, textAlign: 'center', padding: 12 }}
            >
              <div style={{ fontSize: 14, color: '#8c8c8c', marginBottom: 4 }}>
                {t('liteTopic.totalMessages')}
              </div>
              <div style={{ fontSize: 24, fontWeight: 700 }}>{sessionData.totalMessages ?? 0}</div>
            </Card>
          </Col>
          <Col span={8}>
            <Card
              variant="borderless"
              style={{ background: '#f6ffed', borderRadius: 8, textAlign: 'center', padding: 12 }}
            >
              <div style={{ fontSize: 14, color: '#8c8c8c', marginBottom: 4 }}>
                {t('liteTopic.consumedMessages')}
              </div>
              <div style={{ fontSize: 24, fontWeight: 700 }}>
                {sessionData.consumedMessages ?? 0}
              </div>
            </Card>
          </Col>
          <Col span={8}>
            <Card
              variant="borderless"
              style={{ background: '#fffbe6', borderRadius: 8, textAlign: 'center', padding: 12 }}
            >
              <div style={{ fontSize: 14, color: '#8c8c8c', marginBottom: 4 }}>
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
              percent={Math.round(sessionData.popProgress)}
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
        <PageHeader title={t('liteTopic.title')} />
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
        extra={
          <Button icon={<ArrowClockwise size={14} />} size="small" onClick={handleRefresh}>
            {t('common.refresh')}
          </Button>
        }
      />

      {/* Quota Panel */}
      {renderQuotaPanel()}

      {/* Search / Filter Bar */}
      <Card variant="borderless" style={{ marginBottom: 16, borderRadius: 8 }}>
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
            {namespaceOptions.map((namespace) => (
              <Select.Option key={namespace.toLowerCase()} value={namespace}>
                {namespace}
              </Select.Option>
            ))}
          </Select>
          <Select
            aria-label={t('liteTopic.status')}
            placeholder={t('liteTopic.status')}
            value={ttlStatusFilter || undefined}
            onChange={(value) => setTTLStatusFilter(value || '')}
            style={{ width: 160 }}
            allowClear
            options={[
              { value: 'ACTIVE', label: t('liteTopic.active') },
              { value: 'EXPIRING_SOON', label: t('liteTopic.expiringSoon') },
              { value: 'EXPIRED', label: t('liteTopic.expired') },
              { value: 'UNKNOWN', label: t('liteTopic.unknown') },
            ]}
          />
          <Button type="primary" icon={<MagnifyingGlass size={14} />} onClick={handleSearch}>
            {t('common.search')}
          </Button>
        </Space>
      </Card>

      {/* Main Table */}
      <Card variant="borderless" style={{ borderRadius: 8 }}>
        <Table
          columns={columns}
          dataSource={filteredTopicList}
          rowKey={(record) => JSON.stringify([record.namespace, record.topicPattern])}
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
        destroyOnHidden
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
        destroyOnHidden
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
