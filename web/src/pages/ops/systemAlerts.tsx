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

import { useEffect, useRef, useState } from 'react';
import {
  Card,
  DatePicker,
  Tag,
  Flex,
  Typography,
  Alert,
  Badge,
  Button,
  message,
  Pagination,
  Select,
  Spin,
  Modal,
  Form,
  Input,
  Popconfirm,
  Segmented,
  theme,
} from 'antd';
import dayjs from 'dayjs';
import { ArrowClockwise, CheckCircle, Info, DownloadSimple, Trash } from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import useAuthStore from '../../stores/authStore';
import {
  acknowledgeAlert,
  clearAcknowledgedAlerts,
  getCollectorStatus,
  listAlertDeliveries,
  listRelatedSystemAlerts,
  retryAlertDelivery,
  listSystemAlertsPage,
  createAlertSilence,
  deleteAlertSilence,
  listAlertSilencesPage,
} from '../../services/opsService';
import type {
  AlertSilence,
  CollectorStatus,
  CreateAlertSilence,
  NotificationDelivery,
  PageResult,
  SystemAlert,
} from '../../api/ops';
import { formatUtcDateTime, formatNumber } from '../../utils/format';
import { buildCsv, downloadCsv, localExportDateStamp, type CsvColumn } from '../../utils/download';
import { zonedLocalDateTimeToUtc } from '../../utils/timeZone';

const { Text } = Typography;
const { RangePicker } = DatePicker;

const ALERT_EXPORT_PAGE_SIZE = 100;
const ALERT_EXPORT_MAX_PAGES = 10_000;
const DEFAULT_TIME_ZONE = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
const WEEKDAYS = [
  { value: 1, key: 'sysAlerts.monday' },
  { value: 2, key: 'sysAlerts.tuesday' },
  { value: 3, key: 'sysAlerts.wednesday' },
  { value: 4, key: 'sysAlerts.thursday' },
  { value: 5, key: 'sysAlerts.friday' },
  { value: 6, key: 'sysAlerts.saturday' },
  { value: 7, key: 'sysAlerts.sunday' },
] as const;

const normalizeAlertLevel = (level?: string | null) => (level ?? '').toLowerCase();
const formatAlertTransition = (
  transition: SystemAlert['transition'] | undefined,
  firingLabel: string,
  resolvedLabel: string,
) => {
  if (transition === 'FIRING') return firingLabel;
  if (transition === 'RESOLVED') return resolvedLabel;
  return transition;
};

const parseSilenceLabels = (
  value: string | undefined,
  invalidMessage: string,
): Record<string, string> | undefined => {
  if (!value?.trim()) return undefined;
  const labels = new Map<string, string>();
  for (const pair of value.split(',')) {
    const separator = pair.indexOf('=');
    const key = pair.slice(0, separator).trim();
    const labelValue = pair.slice(separator + 1).trim();
    if (separator <= 0 || !key || !labelValue || labels.has(key)) {
      throw new Error(invalidMessage);
    }
    labels.set(key, labelValue);
  }
  return Object.fromEntries(labels);
};

const localDateTimeToUtc = (value: string) => new Date(`${value}:00`).toISOString();
const localDateTimeToUtcDatabaseValue = (value: string) =>
  new Date(`${value}:00`).toISOString().replace('Z', '');

const ALERT_EXPORT_COLUMNS: CsvColumn<SystemAlert>[] = [
  { header: 'ID', value: (alert) => alert.id },
  { header: 'Domain', value: (alert) => alert.domain },
  { header: 'Instance', value: (alert) => alert.instanceId },
  { header: 'Rule ID', value: (alert) => alert.ruleId },
  { header: 'Title', value: (alert) => alert.title },
  { header: 'Level', value: (alert) => alert.level },
  { header: 'Transition', value: (alert) => alert.transition },
  { header: 'Time (UTC)', value: (alert) => alert.time },
  { header: 'Current value', value: (alert) => alert.currentValue },
  {
    header: 'Labels',
    value: (alert) =>
      Object.entries(alert.labels ?? {})
        .map(([key, value]) => `${key}=${value}`)
        .join(', '),
  },
  { header: 'Notification suppressed', value: (alert) => alert.notificationSuppressed === true },
  { header: 'Suppression cause alert ID', value: (alert) => alert.suppressionCauseAlertId },
  { header: 'Suppression reason', value: (alert) => alert.suppressionReason },
  { header: 'Acknowledged', value: (alert) => alert.acknowledged },
  { header: 'Acknowledged by', value: (alert) => alert.acknowledgedBy },
  { header: 'Acknowledged at (UTC)', value: (alert) => alert.acknowledgedAt },
  { header: 'Description', value: (alert) => alert.description },
];

const SystemAlertsPage = () => {
  const { t } = useLang();
  const userId = useAuthStore((state) => state.userId);
  const admin = useAuthStore((state) => state.admin);
  const canManageSilences = !userId || admin === true;
  const { token } = theme.useToken();

  const alertLevelConfig: Record<string, { color: string; bg: string; label: string }> = {
    error: { color: '#ff4d4f', bg: '#fff2f0', label: t('sysAlerts.severe') },
    warning: { color: '#fa8c16', bg: '#fff7e6', label: t('sysAlerts.warning') },
    info: { color: '#1677ff', bg: '#e6f4ff', label: t('sysAlerts.info') },
  };

  const [alerts, setAlerts] = useState<SystemAlert[]>([]);
  const [loadFailed, setLoadFailed] = useState(false);
  const [levelFilter, setLevelFilter] = useState<string>('all');
  const [domainFilter, setDomainFilter] = useState<string>('all');
  const [transitionFilter, setTransitionFilter] = useState<string>('all');
  const [suppressionFilter, setSuppressionFilter] = useState<string>('all');
  const [instanceFilter, setInstanceFilter] = useState('');
  const [labelFilter, setLabelFilter] = useState('');
  const [fromFilter, setFromFilter] = useState('');
  const [toFilter, setToFilter] = useState('');
  const [collectorStatus, setCollectorStatus] = useState<CollectorStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [refreshNonce, setRefreshNonce] = useState(0);
  const alertReadGeneration = useRef(0);
  const pageSize = 20;
  const [acknowledgingIds, setAcknowledgingIds] = useState<Set<number>>(() => new Set());
  const [clearing, setClearing] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [deliveries, setDeliveries] = useState<Record<number, NotificationDelivery[]>>({});
  const [loadingDeliveries, setLoadingDeliveries] = useState<Set<number>>(() => new Set());
  const deliveryRequestIds = useRef<Record<number, number>>({});
  const [retryingDeliveryIds, setRetryingDeliveryIds] = useState<Set<number>>(() => new Set());
  const [relatedAlerts, setRelatedAlerts] = useState<Record<number, SystemAlert[]>>({});
  const [loadingRelatedIds, setLoadingRelatedIds] = useState<Set<number>>(() => new Set());
  const [silencesVisible, setSilencesVisible] = useState(false);
  const [silences, setSilences] = useState<AlertSilence[]>([]);
  const [loadingSilences, setLoadingSilences] = useState(false);
  const [silencePage, setSilencePage] = useState(1);
  const [silenceTotal, setSilenceTotal] = useState(0);
  const [savingSilence, setSavingSilence] = useState(false);
  const [deletingSilenceId, setDeletingSilenceId] = useState<number | null>(null);
  const silenceRequestId = useRef(0);
  const silencePageSize = 10;
  const [silenceForm] = Form.useForm();
  const silenceRecurrence = Form.useWatch('recurrence', silenceForm) ?? 'ONCE';

  const currentQuery = () => {
    const labelSeparator = labelFilter.indexOf('=');
    const labelKey = labelSeparator > 0 ? labelFilter.slice(0, labelSeparator).trim() : undefined;
    const labelValue = labelKey ? labelFilter.slice(labelSeparator + 1).trim() : undefined;
    return {
      level: levelFilter === 'all' ? undefined : levelFilter,
      domain: domainFilter === 'all' ? undefined : (domainFilter as 'BUSINESS' | 'CLUSTER'),
      transition: transitionFilter === 'all' ? undefined : transitionFilter,
      notificationSuppressed:
        suppressionFilter === 'all' ? undefined : suppressionFilter === 'suppressed',
      instanceId: instanceFilter.trim() || undefined,
      labelKey: labelKey && labelValue ? labelKey : undefined,
      labelValue: labelKey && labelValue ? labelValue : undefined,
      from: fromFilter ? localDateTimeToUtcDatabaseValue(fromFilter) : undefined,
      to: toFilter ? localDateTimeToUtcDatabaseValue(toFilter) : undefined,
    };
  };

  useEffect(() => {
    let cancelled = false;
    const generation = ++alertReadGeneration.current;

    void listSystemAlertsPage({
      ...currentQuery(),
      page,
      pageSize,
    })
      .then((data: PageResult<SystemAlert>) => {
        if (!cancelled && generation === alertReadGeneration.current) {
          setAlerts(data.items);
          setTotal(data.total);
          setLoadFailed(false);
        }
      })
      .catch(() => {
        if (!cancelled && generation === alertReadGeneration.current) {
          // A failed read must not leave the page reporting "0 unacknowledged" for a store it
          // never reached, and the alert surfaces below must offer a retry.
          message.error(t('sysAlerts.loadFailed'));
          setLoadFailed(true);
        }
      })
      .finally(() => {
        if (!cancelled && generation === alertReadGeneration.current) setLoading(false);
      });
    void getCollectorStatus()
      .then((status) => {
        if (!cancelled) setCollectorStatus(status);
      })
      .catch(() => undefined);

    return () => {
      cancelled = true;
    };
  }, [
    domainFilter,
    fromFilter,
    instanceFilter,
    labelFilter,
    levelFilter,
    page,
    refreshNonce,
    suppressionFilter,
    toFilter,
    transitionFilter,
  ]);

  const unackCount = alerts.filter((a) => !a.acknowledged).length;

  const handleAck = async (id: number) => {
    setAcknowledgingIds((current) => new Set(current).add(id));
    try {
      await acknowledgeAlert(id);
      // Reject pre-ACK snapshots immediately, including before the refresh effect cleans up.
      alertReadGeneration.current += 1;
      setAlerts((prev) => prev.map((a) => (a.id === id ? { ...a, acknowledged: true } : a)));
      setRefreshNonce((value) => value + 1);
      message.success(t('sysAlerts.acknowledged'));
    } catch {
      message.error(t('sysAlerts.acknowledgeFailed'));
    } finally {
      setAcknowledgingIds((current) => {
        const next = new Set(current);
        next.delete(id);
        return next;
      });
    }
  };

  const handleClearAcked = async () => {
    setClearing(true);
    try {
      await clearAcknowledgedAlerts();
      if (page === 1) setRefreshNonce((value) => value + 1);
      else setPage(1);
      message.success(t('sysAlerts.cleared'));
    } catch {
      message.error(t('sysAlerts.clearFailed'));
    } finally {
      setClearing(false);
    }
  };

  const exportAlerts = async () => {
    setExporting(true);
    try {
      const query = currentQuery();
      const first = await listSystemAlertsPage({
        ...query,
        page: 1,
        pageSize: ALERT_EXPORT_PAGE_SIZE,
      });
      const rows = [...first.items];
      let expectedTotal = first.total;
      let currentPage = 2;
      while (rows.length < expectedTotal) {
        if (currentPage > ALERT_EXPORT_MAX_PAGES) {
          throw new Error('System alert export exceeded the pagination limit');
        }
        const result = await listSystemAlertsPage({
          ...query,
          page: currentPage,
          pageSize: ALERT_EXPORT_PAGE_SIZE,
        });
        expectedTotal = Math.min(expectedTotal, result.total);
        if (result.items.length === 0) {
          if (rows.length < expectedTotal) {
            throw new Error('System alert export returned an incomplete page');
          }
          break;
        }
        rows.push(...result.items);
        currentPage += 1;
      }
      downloadCsv(
        `rocketmq-system-alerts-${localExportDateStamp()}.csv`,
        buildCsv(ALERT_EXPORT_COLUMNS, rows),
      );
      message.success(t('sysAlerts.exportSuccess', { count: rows.length }));
    } catch {
      message.error(t('sysAlerts.exportFailed'));
    } finally {
      setExporting(false);
    }
  };

  const loadDeliveries = async (alertId: number, force = false) => {
    if (!force && (deliveries[alertId] || loadingDeliveries.has(alertId))) return;
    const requestId = (deliveryRequestIds.current[alertId] ?? 0) + 1;
    deliveryRequestIds.current[alertId] = requestId;
    setLoadingDeliveries((current) => new Set(current).add(alertId));
    try {
      const result = await listAlertDeliveries(alertId);
      if (deliveryRequestIds.current[alertId] === requestId) {
        setDeliveries((current) => ({ ...current, [alertId]: result }));
      }
    } catch {
      if (deliveryRequestIds.current[alertId] === requestId) {
        message.error(t('sysAlerts.deliveryLoadFailed'));
      }
    } finally {
      if (deliveryRequestIds.current[alertId] === requestId) {
        setLoadingDeliveries((current) => {
          const next = new Set(current);
          next.delete(alertId);
          return next;
        });
      }
    }
  };

  const loadRelatedAlerts = async (alertId: number) => {
    if (relatedAlerts[alertId] || loadingRelatedIds.has(alertId)) return;
    setLoadingRelatedIds((current) => new Set(current).add(alertId));
    try {
      const result = await listRelatedSystemAlerts(alertId);
      setRelatedAlerts((current) => ({ ...current, [alertId]: result }));
    } catch {
      message.error(t('sysAlerts.relatedLoadFailed'));
    } finally {
      setLoadingRelatedIds((current) => {
        const next = new Set(current);
        next.delete(alertId);
        return next;
      });
    }
  };

  const handleRetryDelivery = async (alertId: number, deliveryId: number) => {
    setRetryingDeliveryIds((current) => new Set(current).add(deliveryId));
    try {
      await retryAlertDelivery(deliveryId);
      await loadDeliveries(alertId, true);
      message.success(t('deliveries.retryQueued'));
    } catch {
      message.error(t('deliveries.retryFailed'));
    } finally {
      setRetryingDeliveryIds((current) => {
        const next = new Set(current);
        next.delete(deliveryId);
        return next;
      });
    }
  };

  const loadSilences = async (nextPage = silencePage) => {
    const requestId = ++silenceRequestId.current;
    setLoadingSilences(true);
    try {
      const result = await listAlertSilencesPage({ page: nextPage, pageSize: silencePageSize });
      if (requestId !== silenceRequestId.current) return;
      setSilences(result.items);
      setSilenceTotal(result.total);
      setSilencePage(result.page);
    } catch {
      if (requestId === silenceRequestId.current) message.error(t('sysAlerts.silenceLoadFailed'));
    } finally {
      if (requestId === silenceRequestId.current) setLoadingSilences(false);
    }
  };

  const openSilences = () => {
    setSilencePage(1);
    setSilencesVisible(true);
    void loadSilences(1);
  };

  const createSilence = async () => {
    let values: {
      domain?: 'BUSINESS' | 'CLUSTER';
      ruleId?: string;
      instanceId?: string;
      startsAt: string;
      endsAt: string;
      reason?: string;
      labelsText?: string;
      recurrence?: 'ONCE' | 'DAILY' | 'WEEKLY';
      timeZone?: string;
      recurrenceDays?: number[];
      recurrenceUntil?: string;
    };
    try {
      values = await silenceForm.validateFields();
    } catch {
      return;
    }
    setSavingSilence(true);
    let request: CreateAlertSilence;
    try {
      const recurrence = values.recurrence ?? 'ONCE';
      const convertTime = (value: string) =>
        recurrence === 'ONCE'
          ? localDateTimeToUtc(value)
          : zonedLocalDateTimeToUtc(value, values.timeZone!);
      request = {
        instanceId: values.instanceId,
        startsAt: convertTime(values.startsAt),
        endsAt: convertTime(values.endsAt),
        reason: values.reason,
        ruleId: values.ruleId ? Number(values.ruleId) : undefined,
        domain: values.domain || undefined,
        labels: parseSilenceLabels(values.labelsText, t('sysAlerts.labelsFormatInvalid')),
        recurrence,
        timeZone: recurrence !== 'ONCE' ? values.timeZone : undefined,
        recurrenceDays: recurrence === 'WEEKLY' ? values.recurrenceDays : undefined,
        recurrenceUntil:
          recurrence !== 'ONCE' && values.recurrenceUntil
            ? convertTime(values.recurrenceUntil)
            : undefined,
      };
    } catch (error) {
      // client-side validation throws carry field-specific messages; no request was
      // made, so the axios interceptor never surfaces them
      message.error(
        error instanceof Error && error.message
          ? error.message
          : t('sysAlerts.silenceCreateFailed'),
      );
      setSavingSilence(false);
      return;
    }
    try {
      await createAlertSilence(request);
      silenceForm.resetFields();
      setSilencePage(1);
      await loadSilences(1);
      message.success(t('sysAlerts.silenceCreated'));
    } catch {
      message.error(t('sysAlerts.silenceCreateFailed'));
    } finally {
      setSavingSilence(false);
    }
  };

  const deleteSilence = async (id: number) => {
    setDeletingSilenceId(id);
    try {
      await deleteAlertSilence(id);
      const nextPage = silences.length === 1 && silencePage > 1 ? silencePage - 1 : silencePage;
      setSilencePage(nextPage);
      await loadSilences(nextPage);
      message.success(t('sysAlerts.silenceEnded'));
    } catch {
      message.error(t('sysAlerts.silenceEndFailed'));
    } finally {
      setDeletingSilenceId(null);
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title={t('sysAlerts.title')}
        subtitle={t('sysAlerts.subtitle', { n: loadFailed ? '-' : unackCount })}
        extra={
          <Flex gap={8} align="center">
            {collectorStatus && (
              // 与筛选控件同高同边框的状态片：Tag 的 success 配色只有 22px 高、绿底绿字，
              // 夹在一排 32px 白底控件里既矮又跳。放在页头而不是筛选条，是因为 1600 视口下
              // 筛选条放下 8 个控件后只剩 40px，容不下它。
              <Flex
                align="center"
                gap={6}
                style={{
                  height: 32,
                  padding: '0 12px',
                  border: `1px solid ${token.colorBorder}`,
                  borderRadius: token.borderRadius,
                  background: token.colorBgContainer,
                }}
              >
                <Info size={14} color={token.colorTextSecondary} aria-hidden="true" />
                <Text style={{ fontSize: 14 }}>
                  {t('sysAlerts.collectorImplementations', {
                    cluster: collectorStatus.clusterCollectorCount,
                    business: collectorStatus.businessCollectorCount,
                  })}
                </Text>
              </Flex>
            )}
            <Button
              icon={<DownloadSimple size={14} />}
              onClick={() => void exportAlerts()}
              loading={exporting}
            >
              {t('sysAlerts.exportCsv')}
            </Button>
            <Button onClick={openSilences}>{t('sysAlerts.maintenanceWindows')}</Button>
            <Popconfirm
              title={t('sysAlerts.clearAckedConfirm')}
              description={t('sysAlerts.clearAckedConfirmDesc')}
              onConfirm={() => void handleClearAcked()}
              okText={t('common.confirm')}
              cancelText={t('common.cancel')}
            >
              <Button
                icon={<Trash size={14} />}
                danger
                disabled={!alerts.some((a) => a.acknowledged)}
                loading={clearing}
              >
                {t('sysAlerts.clearAcked')}
              </Button>
            </Popconfirm>
          </Flex>
        }
      />

      {loadFailed && (
        <Alert
          showIcon
          type="error"
          message={t('common.fetchDataFailed')}
          action={
            <Button
              size="small"
              icon={<ArrowClockwise size={16} />}
              onClick={() => setRefreshNonce((value) => value + 1)}
            >
              {t('common.retry')}
            </Button>
          }
          style={{ marginBottom: 12 }}
        />
      )}

      {/* 筛选条：全部控件统一默认尺寸（32px 高）。时间范围用单个 RangePicker 而不是两个
          datetime-local：368px 是填了值之后两段 `YYYY-MM-DD HH:mm` 加箭头和日历图标的实测宽，
          再窄会裁字。整条约 1292px，在 1600 视口（容器 1332px）下保持一行。 */}
      <Flex gap={8} wrap align="center" style={{ marginBottom: 16 }}>
        <Segmented
          value={levelFilter}
          onChange={(value) => {
            setLevelFilter(String(value));
            setPage(1);
          }}
          options={['all', 'error', 'warning', 'info'].map((level) => ({
            value: level,
            label: (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                {level === 'all' ? t('common.all') : alertLevelConfig[level]?.label}
                {!loadFailed && level !== 'all' && (
                  <Badge
                    count={alerts.filter((a) => normalizeAlertLevel(a.level) === level).length}
                    style={{
                      backgroundColor:
                        level === 'error' ? '#ff4d4f' : level === 'warning' ? '#fa8c16' : '#1677ff',
                    }}
                    size="small"
                  />
                )}
              </span>
            ),
          }))}
        />
        <Select
          value={domainFilter}
          style={{ minWidth: 120 }}
          onChange={(value) => {
            setDomainFilter(value);
            setPage(1);
          }}
          options={[
            { value: 'all', label: t('common.all') },
            { value: 'BUSINESS', label: t('sysAlerts.business') },
            { value: 'CLUSTER', label: t('sysAlerts.cluster') },
          ]}
        />
        <Input
          aria-label={t('sysAlerts.instanceFilter')}
          placeholder={t('sysAlerts.instanceId')}
          style={{ width: 140, flex: 'none' }}
          value={instanceFilter}
          onChange={(event) => {
            setInstanceFilter(event.target.value);
            setPage(1);
          }}
        />
        <Input
          aria-label={t('sysAlerts.labelsFilter')}
          placeholder={t('sysAlerts.labelsPlaceholder')}
          style={{ width: 140, flex: 'none' }}
          value={labelFilter}
          onChange={(event) => {
            setLabelFilter(event.target.value);
            setPage(1);
          }}
        />
        <RangePicker
          allowEmpty={[true, true]}
          aria-label={t('sysAlerts.timeRangeFilter')}
          placeholder={[t('sysAlerts.startTime'), t('sysAlerts.endTime')]}
          showTime={{ format: 'HH:mm' }}
          format="YYYY-MM-DD HH:mm"
          style={{ width: 368, flex: 'none' }}
          value={[fromFilter ? dayjs(fromFilter) : null, toFilter ? dayjs(toFilter) : null]}
          onChange={(values) => {
            setFromFilter(values?.[0] ? values[0].format('YYYY-MM-DDTHH:mm') : '');
            setToFilter(values?.[1] ? values[1].format('YYYY-MM-DDTHH:mm') : '');
            setPage(1);
          }}
        />
        <Select
          value={transitionFilter}
          style={{ minWidth: 120 }}
          onChange={(value) => {
            setTransitionFilter(value);
            setPage(1);
          }}
          options={[
            { value: 'all', label: t('sysAlerts.allStatuses') },
            { value: 'FIRING', label: t('sysAlerts.firing') },
            { value: 'RESOLVED', label: t('sysAlerts.resolved') },
          ]}
        />
        <Select
          aria-label={t('sysAlerts.suppressionFilter')}
          value={suppressionFilter}
          style={{ minWidth: 140 }}
          onChange={(value) => {
            setSuppressionFilter(value);
            setPage(1);
          }}
          options={[
            { value: 'all', label: t('sysAlerts.allDeliveryStatuses') },
            { value: 'suppressed', label: t('sysAlerts.notificationSuppressed') },
            { value: 'delivered', label: t('sysAlerts.notificationNotSuppressed') },
          ]}
        />
      </Flex>

      <Flex vertical gap={12}>
        {loading && <Card loading />}
        {!loading &&
          alerts.map((alert) => {
            const normalizedLevel = normalizeAlertLevel(alert.level);
            const cfg = alertLevelConfig[normalizedLevel] ?? {
              color: '#8c8c8c',
              bg: '#fafafa',
              label: alert.level || t('common.na'),
            };
            return (
              <div
                key={alert.id}
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 12,
                  padding: '12px 16px',
                  borderRadius: 8,
                  background: cfg.bg,
                  borderLeft: `3px solid ${cfg.color}`,
                  opacity: alert.acknowledged ? 0.6 : 1,
                }}
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <Flex align="center" gap={8}>
                    <Text strong style={{ fontSize: 14 }}>
                      {alert.title}
                    </Text>
                    <Tag
                      color={
                        normalizedLevel === 'error'
                          ? 'error'
                          : normalizedLevel === 'warning'
                            ? 'warning'
                            : normalizedLevel === 'info'
                              ? 'processing'
                              : 'default'
                      }
                      style={{ fontSize: 14, lineHeight: '18px', padding: '0 6px' }}
                    >
                      {cfg.label}
                    </Tag>
                    {alert.domain && (
                      <Tag color={alert.domain === 'CLUSTER' ? 'geekblue' : 'green'}>
                        {alert.domain === 'CLUSTER'
                          ? t('sysAlerts.domainCluster')
                          : t('sysAlerts.domainBusiness')}
                      </Tag>
                    )}
                    {alert.transition && (
                      <Tag>
                        {formatAlertTransition(
                          alert.transition,
                          t('sysAlerts.firing'),
                          t('sysAlerts.resolved'),
                        )}
                      </Tag>
                    )}
                    {alert.notificationSuppressed && (
                      <Tag color="gold">{t('sysAlerts.notificationSuppressed')}</Tag>
                    )}
                  </Flex>
                  <Text type="secondary" style={{ fontSize: 14 }}>
                    {alert.description}
                  </Text>
                  {alert.instanceId && (
                    <Text type="secondary" style={{ display: 'block' }}>
                      {alert.instanceId}
                      {alert.currentValue != null ? ` · ${formatNumber(alert.currentValue)}` : ''}
                    </Text>
                  )}
                  {alert.acknowledgedAt && (
                    <Text type="secondary" style={{ display: 'block' }}>
                      {t('sysAlerts.acknowledgedBy', { user: alert.acknowledgedBy ?? 'system' })} ·{' '}
                      {formatUtcDateTime(alert.acknowledgedAt)}
                    </Text>
                  )}
                  {alert.notificationSuppressed && (
                    <Text type="warning" style={{ display: 'block' }}>
                      {alert.suppressionReason || t('sysAlerts.suppressedByUpstream')}
                    </Text>
                  )}
                  {alert.labels && Object.keys(alert.labels).length > 0 && (
                    <Flex gap={4} wrap="wrap" style={{ marginTop: 6 }}>
                      {Object.entries(alert.labels)
                        .sort(([left], [right]) => left.localeCompare(right))
                        .map(([key, value]) => (
                          <Tag key={key}>
                            {key}={value}
                          </Tag>
                        ))}
                    </Flex>
                  )}
                  {loadingRelatedIds.has(alert.id) && <Spin size="small" />}
                  {relatedAlerts[alert.id] && (
                    <Flex vertical gap={4} style={{ marginTop: 8 }}>
                      <Text strong>{t('sysAlerts.rootCauseImpact')}</Text>
                      {relatedAlerts[alert.id].length === 0 && (
                        <Text type="secondary">{t('sysAlerts.noRelatedEvents')}</Text>
                      )}
                      {relatedAlerts[alert.id].map((related) => (
                        <Flex key={related.id} gap={6} align="center" wrap="wrap">
                          <Tag color={related.domain === 'CLUSTER' ? 'geekblue' : 'green'}>
                            {related.domain === 'CLUSTER'
                              ? t('sysAlerts.domainCluster')
                              : t('sysAlerts.domainBusiness')}
                          </Tag>
                          <Text>{related.title}</Text>
                          {related.transition && (
                            <Tag>
                              {formatAlertTransition(
                                related.transition,
                                t('sysAlerts.firing'),
                                t('sysAlerts.resolved'),
                              )}
                            </Tag>
                          )}
                          <Text type="secondary">{formatUtcDateTime(related.time)}</Text>
                        </Flex>
                      ))}
                    </Flex>
                  )}
                  {loadingDeliveries.has(alert.id) && <Spin size="small" />}
                  {deliveries[alert.id] && (
                    <Flex vertical gap={6} style={{ marginTop: 6, minWidth: 0 }}>
                      {deliveries[alert.id].length === 0 && (
                        <Text type="secondary">
                          {alert.notificationSuppressed
                            ? alert.suppressionReason || t('sysAlerts.suppressedByUpstream')
                            : alert.ruleId == null
                              ? t('sysAlerts.noDeliveryRecords')
                              : t('sysAlerts.noChannels')}
                        </Text>
                      )}
                      {deliveries[alert.id].map((delivery) => (
                        <div key={delivery.id} style={{ minWidth: 0 }}>
                          <Flex gap={6} align="center" wrap="wrap">
                            <Tag
                              color={
                                delivery.status === 'DELIVERED'
                                  ? 'success'
                                  : delivery.status === 'FAILED'
                                    ? 'error'
                                    : 'processing'
                              }
                            >
                              {delivery.channel}: {delivery.status} ({delivery.attemptCount})
                            </Tag>
                            {delivery.status === 'FAILED' && (
                              <Button
                                size="small"
                                type="link"
                                onClick={() => void handleRetryDelivery(alert.id, delivery.id)}
                                loading={retryingDeliveryIds.has(delivery.id)}
                              >
                                {t('deliveries.retry')}
                              </Button>
                            )}
                          </Flex>
                          {delivery.lastError && (
                            <Text
                              type="secondary"
                              title={delivery.lastError}
                              style={{
                                display: 'block',
                                marginTop: 2,
                                overflowWrap: 'anywhere',
                                wordBreak: 'break-word',
                              }}
                            >
                              {delivery.lastError}
                            </Text>
                          )}
                        </div>
                      ))}
                    </Flex>
                  )}
                </div>
                <Flex align="center" gap={8} style={{ flexShrink: 0 }}>
                  <Text type="secondary" style={{ fontSize: 14 }}>
                    {formatUtcDateTime(alert.time)}
                  </Text>
                  <Button size="small" type="link" onClick={() => void loadDeliveries(alert.id)}>
                    {t('sysAlerts.deliveryRecords')}
                  </Button>
                  <Button size="small" type="link" onClick={() => void loadRelatedAlerts(alert.id)}>
                    {t('sysAlerts.relatedEvents')}
                  </Button>
                  {alert.suppressionCauseAlertId && (
                    <Button
                      size="small"
                      type="link"
                      onClick={() => void loadRelatedAlerts(alert.id)}
                    >
                      {t('sysAlerts.viewRootCause')}
                    </Button>
                  )}
                  {!alert.acknowledged && alert.transition !== 'RESOLVED' && (
                    <Button
                      size="small"
                      type="link"
                      icon={<CheckCircle size={14} />}
                      onClick={() => handleAck(alert.id)}
                      loading={acknowledgingIds.has(alert.id)}
                    >
                      {t('sysAlerts.acknowledge')}
                    </Button>
                  )}
                </Flex>
              </div>
            );
          })}
        {!loading && alerts.length === 0 && (
          <Card>
            <Flex justify="center" style={{ padding: 40 }}>
              <Text type="secondary">{t('sysAlerts.noAlerts')}</Text>
            </Flex>
          </Card>
        )}
      </Flex>
      {total > pageSize && (
        <Pagination
          current={page}
          pageSize={pageSize}
          total={total}
          showSizeChanger={false}
          style={{ marginTop: 16, textAlign: 'right' }}
          onChange={setPage}
        />
      )}
      <Modal
        title={t('sysAlerts.maintenanceWindows')}
        open={silencesVisible}
        onCancel={() => {
          setSilencesVisible(false);
          silenceForm.resetFields();
        }}
        onOk={() => void createSilence()}
        okText={t('sysAlerts.create')}
        okButtonProps={{ style: { display: canManageSilences ? undefined : 'none' } }}
        confirmLoading={savingSilence}
        width={680}
      >
        {canManageSilences && (
          <Form
            form={silenceForm}
            layout="vertical"
            initialValues={{ domain: 'BUSINESS', recurrence: 'ONCE', timeZone: DEFAULT_TIME_ZONE }}
          >
            <Flex gap={8}>
              <Form.Item name="domain" label={t('sysAlerts.domain')} style={{ flex: 1 }}>
                <Select
                  options={[
                    { value: 'BUSINESS', label: t('sysAlerts.business') },
                    { value: 'CLUSTER', label: t('sysAlerts.cluster') },
                  ]}
                />
              </Form.Item>
              <Form.Item
                name="ruleId"
                label={t('sysAlerts.ruleId')}
                style={{ flex: 1 }}
                rules={[{ pattern: /^\d+$/, message: t('sysAlerts.validRuleIdRequired') }]}
              >
                <Input inputMode="numeric" />
              </Form.Item>
              <Form.Item name="instanceId" label={t('sysAlerts.instanceId')} style={{ flex: 1 }}>
                <Input />
              </Form.Item>
            </Flex>
            <Flex gap={8} align="start">
              <Form.Item name="recurrence" label={t('sysAlerts.recurrence')} style={{ flex: 1 }}>
                <Select
                  options={[
                    { value: 'ONCE', label: t('sysAlerts.recurrenceOnce') },
                    { value: 'DAILY', label: t('sysAlerts.recurrenceDaily') },
                    { value: 'WEEKLY', label: t('sysAlerts.recurrenceWeekly') },
                  ]}
                />
              </Form.Item>
              {silenceRecurrence !== 'ONCE' && (
                <Form.Item
                  name="timeZone"
                  label={t('sysAlerts.timeZone')}
                  style={{ flex: 1 }}
                  rules={[{ required: true, message: t('sysAlerts.timeZoneRequired') }]}
                  extra={t('sysAlerts.timeZoneHelp')}
                >
                  <Input placeholder="Asia/Shanghai" />
                </Form.Item>
              )}
            </Flex>
            {silenceRecurrence !== 'ONCE' && (
              <Flex gap={8} align="start">
                {silenceRecurrence === 'WEEKLY' && (
                  <Form.Item
                    name="recurrenceDays"
                    label={t('sysAlerts.recurrenceDays')}
                    style={{ flex: 1 }}
                    rules={[{ required: true, message: t('sysAlerts.recurrenceDaysRequired') }]}
                  >
                    <Select
                      mode="multiple"
                      options={WEEKDAYS.map((day) => ({
                        value: day.value,
                        label: t(day.key),
                      }))}
                    />
                  </Form.Item>
                )}
                <Form.Item
                  name="recurrenceUntil"
                  label={t('sysAlerts.recurrenceUntil')}
                  style={{ flex: 1 }}
                  rules={[{ required: true, message: t('sysAlerts.recurrenceUntilRequired') }]}
                  extra={t('sysAlerts.recurrenceUntilHelp')}
                >
                  <Input type="datetime-local" />
                </Form.Item>
              </Flex>
            )}
            <Flex gap={8}>
              <Form.Item
                name="startsAt"
                label={t('sysAlerts.startTime')}
                rules={[{ required: true, message: t('sysAlerts.startTimeRequired') }]}
                style={{ flex: 1 }}
              >
                <Input type="datetime-local" />
              </Form.Item>
              <Form.Item
                name="endsAt"
                label={t('sysAlerts.endTime')}
                rules={[{ required: true, message: t('sysAlerts.endTimeRequired') }]}
                style={{ flex: 1 }}
              >
                <Input type="datetime-local" />
              </Form.Item>
            </Flex>
            <Form.Item name="reason" label={t('sysAlerts.reason')}>
              <Input maxLength={512} />
            </Form.Item>
            <Form.Item
              name="labelsText"
              label={t('sysAlerts.labelScope')}
              extra={t('sysAlerts.labelScopeHelp')}
            >
              <Input />
            </Form.Item>
          </Form>
        )}
        <Spin spinning={loadingSilences}>
          <Flex vertical gap={6}>
            {silences.length === 0 && (
              <Text type="secondary">{t('sysAlerts.noMaintenanceWindows')}</Text>
            )}
            {silences.map((silence) => (
              <Flex key={silence.id} justify="space-between" align="center" gap={8}>
                <Text>
                  {silence.recurrence && silence.recurrence !== 'ONCE' && (
                    <Tag color="blue">
                      {silence.recurrence === 'DAILY'
                        ? t('sysAlerts.recurrenceDaily')
                        : t('sysAlerts.recurrenceWeekly')}
                    </Tag>
                  )}
                  {silence.domain ?? t('common.all')} ·{' '}
                  {silence.instanceId ?? t('sysAlerts.allInstances')} ·{' '}
                  {formatUtcDateTime(silence.startsAt)} - {formatUtcDateTime(silence.endsAt)}
                  {silence.labels && Object.keys(silence.labels).length > 0
                    ? ` · ${Object.entries(silence.labels)
                        .map(([key, value]) => `${key}=${value}`)
                        .join(', ')}`
                    : ''}
                  {silence.recurrence && silence.recurrence !== 'ONCE'
                    ? ` · ${silence.timeZone} · ${t('sysAlerts.repeatsUntil', {
                        time: silence.recurrenceUntil
                          ? formatUtcDateTime(silence.recurrenceUntil)
                          : '',
                      })}`
                    : ''}
                </Text>
                {canManageSilences && (
                  <Button
                    size="small"
                    danger
                    loading={deletingSilenceId === silence.id}
                    onClick={() => void deleteSilence(silence.id)}
                  >
                    {t('sysAlerts.end')}
                  </Button>
                )}
              </Flex>
            ))}
            {silenceTotal > silencePageSize && (
              <Pagination
                size="small"
                current={silencePage}
                pageSize={silencePageSize}
                total={silenceTotal}
                showSizeChanger={false}
                style={{ alignSelf: 'flex-end', marginTop: 8 }}
                onChange={(nextPage) => void loadSilences(nextPage)}
              />
            )}
          </Flex>
        </Spin>
      </Modal>
    </div>
  );
};

export default SystemAlertsPage;
