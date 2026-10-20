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
import { formatUtcDateTime } from '../../utils/format';
import {
  Table,
  Button,
  Input,
  Select,
  Tag,
  Modal,
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
  DownloadSimple,
} from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import InfoBanner from '../../components/InfoBanner';
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
import { listInstances, supportsApacheRuntime, type Instance } from '../../api/instance';
import { buildCsv, downloadCsv, localExportDateStamp, type CsvColumn } from '../../utils/download';

const formatDuration = (ms: number | undefined | null): string => {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  if (ms < 3600000) return `${(ms / 60000).toFixed(1)}min`;
  return `${(ms / 3600000).toFixed(1)}h`;
};

/* LiteTopic instants (create / last-active) are epoch millis. Render them through the shared
 * formatter like the other timestamp surfaces: a stable `YYYY-MM-DD HH:mm:ss` in the viewer's
 * zone, instead of Date.toLocaleString's locale-dependent browser-local string. */
export const formatTime = (timestamp: number | undefined | null): string => {
  if (timestamp == null || !Number.isFinite(timestamp)) return '-';
  const date = new Date(timestamp);
  return Number.isNaN(date.getTime()) ? '-' : formatUtcDateTime(date);
};

/**
 * Sort key for a column value the provider can leave unset. The cells render `-` in that case
 * (`formatDuration` / `formatTime`), and neither an absent TTL nor an absent last-active instant
 * may sort as a measured zero: ascending, that would place the pattern before every measurable one
 * as if it had expired first or been idle longest. Ant Design negates the comparator for a
 * descending column, so the unset value has to stay last in both directions.
 */
const unmeasuredSortKey = (
  value: number | undefined,
  sortOrder?: 'ascend' | 'descend' | null,
): number => value ?? (sortOrder === 'descend' ? Number.MIN_SAFE_INTEGER : Number.MAX_SAFE_INTEGER);

const getProgressStatus = (percent: number): 'exception' | 'active' | 'normal' => {
  if (percent >= 90) return 'exception';
  if (percent >= 70) return 'active';
  return 'normal';
};

const knownTTLStatuses = new Set(['ACTIVE', 'EXPIRING_SOON', 'EXPIRED']);

interface LiteTopicExportRow extends LiteTopicItem {
  ttlStatusLabel: string;
  sessionCount: number;
}

const LITE_TOPIC_EXPORT_COLUMNS: CsvColumn<LiteTopicExportRow>[] = [
  { header: 'Namespace', value: (item) => item.namespace },
  { header: 'Topic Pattern', value: (item) => item.topicPattern },
  { header: 'Topic Count', value: (item) => item.topicCount },
  { header: 'Consumer Count', value: (item) => item.consumerCount },
  { header: 'Total Backlog', value: (item) => item.totalBacklog },
  { header: 'Average TTL', value: (item) => formatDuration(item.averageTTL) },
  { header: 'TTL Status', value: (item) => item.ttlStatusLabel },
  { header: 'Last Active Time', value: (item) => formatTime(item.lastActiveTime) },
  { header: 'Session Count', value: (item) => item.sessionCount },
  { header: 'Session IDs', value: (item) => item.sessionIds?.join(';') },
];

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
  const [capabilityFailed, setCapabilityFailed] = useState(false);
  const [capabilityRetrying, setCapabilityRetrying] = useState(false);
  const [capabilityRetryVersion, setCapabilityRetryVersion] = useState(0);
  const [patternFilter, setPatternFilter] = useState('');
  const [namespaceFilter, setNamespaceFilter] = useState('');
  const [ttlStatusFilter, setTTLStatusFilter] = useState('');
  const [namespaceOptions, setNamespaceOptions] = useState<string[]>([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Session drawer
  const [sessionModalOpen, setSessionModalOpen] = useState(false);
  const [sessionData, setSessionData] = useState<LiteTopicSession | null>(null);
  const [sessionLoading, setSessionLoading] = useState(false);
  const [availableSessionIds, setAvailableSessionIds] = useState<string[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<string>();

  // Extend TTL modal
  const [extendTTLModalOpen, setExtendTTLModalOpen] = useState(false);
  const [extendTTLForm, setExtendTTLForm] = useState<{
    instanceId: string;
    topicPattern: string;
    newTTL: number | null;
  }>({ instanceId: '', topicPattern: '', newTTL: null });
  const [extendTTLLoading, setExtendTTLLoading] = useState(false);
  // Instances that may own a Lite topic write (the backend's ownership guard
  // only supports Apache-vendor instances for TTL updates).
  const [ttlInstanceOptions, setTtlInstanceOptions] = useState<Instance[]>([]);
  const [ttlInstancesLoading, setTtlInstancesLoading] = useState(false);

  const mountedRef = useRef(false);
  const bootstrapRequestId = useRef(0);
  const displayRequestId = useRef(0);
  const displayQueryRef = useRef<{ pattern?: string; namespace?: string }>({});
  const sessionRequestId = useRef(0);
  const ttlInstanceRequestId = useRef(0);

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
      displayQueryRef.current = { pattern, namespace };

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
      } else {
        setQuota(null);
        messageRef.current.warning(translationRef.current('liteTopic.fetchQuotaFailed'));
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
    const ttlCounter = ttlInstanceRequestId;
    mountedState.current = true;

    const bootstrapId = ++bootstrapCounter.current;
    const initialDisplayRequestId = displayCounter.current;
    const bootstrapIsActive = () =>
      mountedState.current && bootstrapId === bootstrapCounter.current;

    const loadNamespaceOptions = async () => {
      if (!bootstrapIsActive()) return;
      try {
        const list = await queryLiteTopicList();
        if (bootstrapIsActive()) setNamespaceOptions(collectNamespaces(list));
      } catch {
        // The active display request owns user-visible error handling.
      }
    };

    const initialize = async () => {
      try {
        const capability = await queryLiteTopicCapability();
        if (!bootstrapIsActive()) return;
        if (capability && capability.supported === false) {
          ++displayCounter.current;
          setCapabilitySupported(false);
          setCapabilityFailed(false);
          setTopicList([]);
          setQuota(null);
          setLoading(false);
          return;
        }
      } catch {
        if (!bootstrapIsActive()) return;
        ++displayCounter.current;
        setCapabilitySupported(false);
        setCapabilityFailed(true);
        setTopicList([]);
        setQuota(null);
        setLoading(false);
        return;
      }

      setCapabilitySupported(true);
      setCapabilityFailed(false);
      if (displayCounter.current === initialDisplayRequestId) {
        const query = displayQueryRef.current;
        const unfiltered = !query.pattern && !query.namespace;
        await fetchData(query.pattern, query.namespace, { collectNamespaceOptions: unfiltered });
        if (!unfiltered) await loadNamespaceOptions();
      } else {
        await loadNamespaceOptions();
      }
    };

    void initialize().finally(() => {
      if (bootstrapIsActive()) setCapabilityRetrying(false);
    });

    return () => {
      mountedState.current = false;
      ++bootstrapCounter.current;
      ++displayCounter.current;
      ++ttlCounter.current;
    };
  }, [capabilityRetryVersion, fetchData]);

  const handleSearch = () => {
    setCurrentPage(1);
    void fetchData(patternFilter || undefined, namespaceFilter || undefined);
  };

  const handleRefresh = () => {
    void fetchData(patternFilter || undefined, namespaceFilter || undefined);
  };

  const handleNamespaceChange = (val: string | undefined) => {
    const namespace = val || undefined;
    setNamespaceFilter(namespace || '');
    setCurrentPage(1);
    void fetchData(patternFilter || undefined, namespace, { clear: true });
  };

  const handleViewSessions = async (sessionId: string, sessionIds?: string[]) => {
    const requestId = ++sessionRequestId.current;
    if (sessionIds) setAvailableSessionIds(sessionIds);
    setSelectedSessionId(sessionId);
    setSessionModalOpen(true);
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
    const requestId = ++ttlInstanceRequestId.current;
    setExtendTTLForm({
      instanceId: '',
      topicPattern: record.topicPattern || '',
      newTTL: null,
    });
    setExtendTTLModalOpen(true);
    setTtlInstanceOptions([]);
    // The TTL write is ownership-checked against the owning instance, so the
    // modal needs the Apache-vendor instance list; auto-select when only one
    // candidate exists.
    setTtlInstancesLoading(true);
    listInstances()
      .then((instances) => {
        if (requestId !== ttlInstanceRequestId.current) return;
        const candidates = instances.filter(supportsApacheRuntime);
        setTtlInstanceOptions(candidates);
        setExtendTTLForm((form) =>
          form.instanceId === '' && candidates.length === 1
            ? { ...form, instanceId: String(candidates[0].name) }
            : form,
        );
      })
      .catch(() => {
        if (requestId === ttlInstanceRequestId.current) setTtlInstanceOptions([]);
      })
      .finally(() => {
        if (requestId === ttlInstanceRequestId.current) setTtlInstancesLoading(false);
      });
  };

  const closeExtendTTL = () => {
    ++ttlInstanceRequestId.current;
    setExtendTTLModalOpen(false);
    setTtlInstancesLoading(false);
  };

  const handleExtendTTL = async () => {
    if (!extendTTLForm.instanceId || !extendTTLForm.topicPattern || extendTTLForm.newTTL == null)
      return;
    setExtendTTLLoading(true);
    try {
      await extendLiteTopicTTL(
        extendTTLForm.instanceId,
        extendTTLForm.topicPattern,
        extendTTLForm.newTTL,
      );
      message.success(t('liteTopic.extendTtlSuccess'));
      closeExtendTTL();
      // The operator may have submitted another query while this write was pending.
      // Refresh that query, without applying filter text that has not been searched.
      const query = displayQueryRef.current;
      void fetchData(query.pattern, query.namespace);
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

  const getTTLStatusLabel = (status: string | undefined) => {
    const map: Record<string, string> = {
      ACTIVE: t('liteTopic.active'),
      EXPIRING_SOON: t('liteTopic.expiringSoon'),
      EXPIRED: t('liteTopic.expired'),
    };
    return map[status || ''] || t('liteTopic.unknown');
  };

  const filteredTopicList = topicList.filter((item) => {
    if (!ttlStatusFilter) return true;
    if (ttlStatusFilter === 'UNKNOWN') {
      return !item.ttlStatus || !knownTTLStatuses.has(item.ttlStatus);
    }
    return item.ttlStatus === ttlStatusFilter;
  });

  const lastPage = Math.max(1, Math.ceil(filteredTopicList.length / pageSize));
  const clampedCurrentPage = Math.min(currentPage, lastPage);

  const handleExport = () => {
    try {
      const rows = filteredTopicList.map((item) => ({
        ...item,
        ttlStatusLabel: getTTLStatusLabel(item.ttlStatus),
        sessionCount: item.sessionIds?.length ?? 0,
      }));
      const filename = `rocketmq-lite-topics-${localExportDateStamp()}.csv`;
      downloadCsv(filename, buildCsv(LITE_TOPIC_EXPORT_COLUMNS, rows));
      message.success(t('liteTopic.exportSuccess', { total: rows.length }));
    } catch {
      message.error(t('liteTopic.exportFailed'));
    }
  };

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
      sorter: (a, b, sortOrder?: 'ascend' | 'descend' | null) =>
        unmeasuredSortKey(a.averageTTL, sortOrder) - unmeasuredSortKey(b.averageTTL, sortOrder),
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
      sorter: (a, b, sortOrder?: 'ascend' | 'descend' | null) =>
        unmeasuredSortKey(a.lastActiveTime, sortOrder) -
        unmeasuredSortKey(b.lastActiveTime, sortOrder),
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
                void handleViewSessions(record.sessionIds![0], record.sessionIds);
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

  // ─── Session Modal Content ───────────────────────────────────

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
    // The consumed-offset scan is capped, so on a session with more lite topics than one scan
    // covers the consumed and total counts are lower bounds: prefix them and drop the ratio.
    const consumedTruncated = sessionData.consumedScanTruncated === true;

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
              <div style={{ fontSize: 24, fontWeight: 700 }}>
                {consumedTruncated
                  ? `≥ ${sessionData.totalMessages ?? 0}`
                  : (sessionData.totalMessages ?? 0)}
              </div>
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
                {consumedTruncated
                  ? `≥ ${sessionData.consumedMessages ?? 0}`
                  : (sessionData.consumedMessages ?? 0)}
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
          {consumedTruncated ? (
            <InfoBanner
              data-testid="lite-topic-consumed-truncated"
              title={t('liteTopic.consumedTruncated.title')}
              description={t('liteTopic.consumedTruncated.description')}
            />
          ) : (
            <Progress
              percent={consumptionPercent}
              status={consumptionPercent >= 100 ? 'success' : 'active'}
              strokeColor={consumptionPercent >= 100 ? '#52c41a' : '#1677ff'}
            />
          )}
        </div>

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
          message={t(
            capabilityFailed ? 'liteTopic.capabilityCheckFailed' : 'liteTopic.notSupported',
          )}
          type={capabilityFailed ? 'error' : 'info'}
          action={
            capabilityFailed ? (
              <Button
                loading={capabilityRetrying}
                disabled={capabilityRetrying}
                onClick={() => {
                  setCapabilityRetrying(true);
                  setCapabilityRetryVersion((version) => version + 1);
                }}
              >
                {t('common.retry')}
              </Button>
            ) : undefined
          }
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
          <Space>
            <Button
              icon={<DownloadSimple size={14} />}
              size="small"
              disabled={loading || filteredTopicList.length === 0}
              onClick={handleExport}
            >
              {t('common.export')}
            </Button>
            <Button icon={<ArrowClockwise size={14} />} size="small" onClick={handleRefresh}>
              {t('common.refresh')}
            </Button>
          </Space>
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
            onChange={(value) => {
              setTTLStatusFilter(value || '');
              setCurrentPage(1);
            }}
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
            current: clampedCurrentPage,
            pageSize,
            onChange: (page, nextPageSize) => {
              setCurrentPage(page);
              setPageSize(nextPageSize);
            },
            showTotal: (total) => t('liteTopic.total').replace('{total}', String(total)),
            showSizeChanger: true,
          }}
          size="middle"
        />
      </Card>

      {/* Session Detail Modal */}
      <Modal
        title={
          <span>
            <ClockCounterClockwise size={16} style={{ marginRight: 8, color: '#1677ff' }} />
            {t('liteTopic.sessionDetail')}
          </span>
        }
        width="min(680px, calc(100vw - 32px))"
        open={sessionModalOpen}
        onCancel={() => {
          sessionRequestId.current += 1;
          setSessionModalOpen(false);
          setSessionLoading(false);
          setSessionData(null);
          setAvailableSessionIds([]);
          setSelectedSessionId(undefined);
        }}
        footer={null}
        destroyOnHidden
        styles={{
          body: { maxHeight: 'calc(100vh - 240px)', overflowY: 'auto', overflowX: 'hidden' },
        }}
      >
        {availableSessionIds.length > 1 && (
          <Select
            aria-label={t('liteTopic.sessionId')}
            showSearch
            optionFilterProp="label"
            value={selectedSessionId}
            options={availableSessionIds.map((sessionId) => ({
              value: sessionId,
              label: sessionId,
            }))}
            onChange={(sessionId) => void handleViewSessions(sessionId)}
            style={{ width: '100%', marginBottom: 16 }}
          />
        )}
        {renderSessionContent()}
      </Modal>

      {/* Extend TTL Modal */}
      <Modal
        title={t('liteTopic.extendTtlModalTitle')}
        open={extendTTLModalOpen}
        onOk={handleExtendTTL}
        onCancel={closeExtendTTL}
        confirmLoading={extendTTLLoading}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
        destroyOnHidden
      >
        <Form layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            label={t('liteTopic.instance')}
            required
            validateStatus={extendTTLForm.instanceId ? '' : 'error'}
            help={extendTTLForm.instanceId ? undefined : t('liteTopic.instanceRequired')}
          >
            <Select
              value={extendTTLForm.instanceId || undefined}
              onChange={(val) => setExtendTTLForm({ ...extendTTLForm, instanceId: val })}
              loading={ttlInstancesLoading}
              placeholder={t('liteTopic.instancePlaceholder')}
              options={ttlInstanceOptions.map((instance) => ({
                value: String(instance.name),
                label: instance.name,
              }))}
            />
          </Form.Item>
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
