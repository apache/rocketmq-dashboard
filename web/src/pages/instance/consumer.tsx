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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Alert,
  Table,
  Card,
  Button,
  Checkbox,
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
  Tooltip,
  Spin,
  Progress,
  Switch,
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
  SlidersHorizontal,
} from '@phosphor-icons/react';
import { ImportOutlined, ExportOutlined, DeleteOutlined, SyncOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';

import PageHeader from '../../components/PageHeader';
import { InstanceSelect } from '../../components/InstanceSelect';
import { useLang } from '../../i18n/LangContext';
import { TOPIC_TYPE_MAP, PROTOCOL_MAP } from '../../constants/theme';
import { formatDateTime, formatDelay, formatUtcDateTime } from '../../utils/format';
import type {
  ConsumerGroup,
  ConsumerInstance,
  ConsumerStackTrace,
  QueueProgress,
  ResetConsumerOffsetPreview,
  ResetConsumerOffsetQueuePreview,
  SubscriptionEntry,
} from '../../api/metadata';
import {
  batchDeleteConsumerGroups,
  createConsumerGroup,
  deleteConsumerGroup,
  exportConsumerGroups,
  getConsumerProgress,
  getConsumerStack,
  getConsumerSubscriptions,
  importConsumerGroups,
  listConsumerGroupPage,
  previewConsumerOffsetReset,
  refreshConsumerGroup,
  resetConsumerOffset,
  getConsumerGroupSettings,
  updateConsumerGroupSettings,
} from '../../services/consumerService';
import { useInstanceFilter } from '../../hooks/useInstanceFilter';
import {
  parseCsvTable,
  RESOURCE_NAME_MAX_LENGTH,
  RESOURCE_NAME_PATTERN,
  validateConsumerGroupCsvImport,
  type ResourceImportRow,
} from '../../utils/resourceCsvImport';
import { downloadCsv } from '../../utils/download';
import { formatLag, isLagAvailable, lagSortValue } from '../../utils/consumerLag';
import { formatOnlineInstances, onlineInstancesSortValue } from '../../utils/consumerConnections';
import { tableScrollX } from '../../utils/table';
import {
  analyzeConsumerGroupHealth,
  type ConsumerGroupHealthIssue,
  type ConsumerGroupHealthStatus,
} from '../../utils/consumerGroupDiagnostics';

const { Text } = Typography;

/* ─── Helpers ─── */

const UNKNOWN_LAG_COLOR = '#8c8c8c';
const UNAVAILABLE_LAG_LABEL = 'consumer.lagUnavailable';

const lagColor = (lag: number): string => {
  // The backend reports -1 when the lag cannot be determined; do not color it
  // as healthy (green) or backlogged.
  if (!isLagAvailable(lag)) return UNKNOWN_LAG_COLOR;
  if (lag >= 10_000) return '#ff4d4f';
  if (lag >= 1_000) return '#faad14';
  return '#52c41a';
};

const visibleConsumerGroups = (groups: ConsumerGroup[], modeFilter: string): ConsumerGroup[] => {
  let data = groups;

  if (modeFilter !== 'ALL') {
    data = data.filter((group) => group.subscriptionMode === modeFilter);
  }

  return data;
};

const normalizedConsistency = (value?: string | null): string => value?.trim().toLowerCase() ?? '';

const isConsistentValue = (value?: string | null): boolean =>
  ['consistent', '一致'].includes(normalizedConsistency(value));

const isInconsistentValue = (value?: string | null): boolean =>
  ['inconsistent', '不一致'].includes(normalizedConsistency(value));

const isConsistentSubscription = (subscription: SubscriptionEntry): boolean =>
  isConsistentValue(subscription.consistency);

const isInconsistentSubscription = (subscription: SubscriptionEntry): boolean =>
  isInconsistentValue(subscription.consistency);

const formatOffsetValue = (value: number) =>
  Number.isFinite(value) && value >= 0 ? value.toLocaleString() : '-';

const formatOffsetDelta = (value: number) => {
  if (value > 0) return `+${value.toLocaleString()}`;
  return value.toLocaleString();
};

const resetPreviewRiskColor = (riskLevel: string) => {
  if (riskLevel === 'ERROR') return 'red';
  if (riskLevel === 'WARNING') return 'orange';
  return 'green';
};

const resetPreviewRiskLabel = (riskLevel: string) => {
  if (riskLevel === 'ERROR') return 'consumer.riskFailed';
  if (riskLevel === 'WARNING') return 'consumer.riskNeedsConfirm';
  return 'consumer.riskNormal';
};

const healthStatusTagColor = (status: ConsumerGroupHealthStatus) => {
  if (status === 'critical') return 'red';
  if (status === 'warning') return 'orange';
  return 'green';
};

const issueSeverityTagColor = (severity: ConsumerGroupHealthIssue['severity']) => {
  if (severity === 'critical') return 'red';
  if (severity === 'warning') return 'orange';
  return 'blue';
};

const issueSeverityLabel = (severity: ConsumerGroupHealthIssue['severity']) => {
  if (severity === 'critical') return 'consumer.severityCritical';
  if (severity === 'warning') return 'consumer.severityWarning';
  return 'consumer.severityInfo';
};

type Translate = (key: string, params?: Record<string, string | number>) => string;

const resetPreviewQueueMessage = (queue: ResetConsumerOffsetQueuePreview, t: Translate) => {
  const messages: string[] = [];
  if (queue.riskLevel === 'ERROR') {
    return queue.message || t('consumer.previewFailed');
  }
  if (queue.targetOffset < 0 || queue.consumerOffset < 0) {
    return queue.message || t('consumer.targetOffsetUnavailable');
  }
  if (queue.offsetDelta < 0) {
    messages.push(
      t('consumer.willReplayMessages', { count: Math.abs(queue.offsetDelta).toLocaleString() }),
    );
  } else if (queue.offsetDelta > 0) {
    messages.push(t('consumer.willSkipMessages', { count: queue.offsetDelta.toLocaleString() }));
  } else {
    messages.push(t('consumer.offsetUnchanged'));
  }
  if (queue.minOffset >= 0 && queue.targetOffset === queue.minOffset) {
    messages.push(t('consumer.targetIsMinOffset'));
  }
  if (queue.maxOffset >= 0 && queue.targetOffset === queue.maxOffset) {
    messages.push(t('consumer.targetIsMaxOffset'));
  }
  return messages.join(t('consumer.previewMessageSeparator'));
};

// Shared helper exported alongside the page component; fast-refresh rule waived.
// eslint-disable-next-line react-refresh/only-export-components
export const diagnosticCacheKey = (instanceId: string | undefined, groupName: string) =>
  `${instanceId ?? ''}\u0000${groupName}`;

/* ═══════════════════════════════════════════
   ConsumerPage
   ═══════════════════════════════════════════ */
type ConsumerPageContentProps = ReturnType<typeof useInstanceFilter>;

const ConsumerPageContent = ({
  selectedInstanceId,
  selectedInstance,
  selectInstance,
  instanceOptions,
  instancesLoading,
  instancesFailed,
  reloadInstances,
}: ConsumerPageContentProps) => {
  const { t, lang } = useLang();
  const isCloudInstance =
    selectedInstance?.vendor === 'ALIYUN' || selectedInstance?.vendor === 'TENCENT';
  const hasSelectedInstance = Boolean(selectedInstanceId);
  const [groups, setGroups] = useState<ConsumerGroup[]>([]);
  const [totalGroups, setTotalGroups] = useState(0);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [resetSubmitting, setResetSubmitting] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [searchParams] = useSearchParams();
  const [search, setSearch] = useState(() => searchParams.get('group') ?? '');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [modeFilter, setModeFilter] = useState<string>('ALL');
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedGroup, setSelectedGroup] = useState<ConsumerGroup | null>(null);
  const [settingsGroup, setSettingsGroup] = useState<ConsumerGroup | null>(null);
  const [settingsLoading, setSettingsLoading] = useState(false);
  const [settingsSubmitting, setSettingsSubmitting] = useState(false);
  const [settingsForm] = Form.useForm<{
    retryQueueNums: number;
    retryMaxTimes: number;
    consumeEnable?: boolean;
    consumeMessageOrderly?: boolean;
    consumeBroadcastEnable?: boolean;
  }>();
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [dataTypeValue, setDataTypeValue] = useState<string | undefined>(undefined);
  const [resetModalOpen, setResetModalOpen] = useState(false);
  const [resetGroup, setResetGroup] = useState<ConsumerGroup | null>(null);
  const [resetTopic, setResetTopic] = useState<string>();
  const [resetTime, setResetTime] = useState<Dayjs>(dayjs().subtract(3, 'hour'));
  const [resetPreview, setResetPreview] = useState<ResetConsumerOffsetPreview | null>(null);
  const [resetPreviewKey, setResetPreviewKey] = useState('');
  const [resetPreviewLoading, setResetPreviewLoading] = useState(false);
  const [resetPreviewError, setResetPreviewError] = useState<string | null>(null);
  const [subscriptionsByGroup, setSubscriptionsByGroup] = useState<
    Record<string, SubscriptionEntry[]>
  >({});
  const [subscriptionLoadingByGroup, setSubscriptionLoadingByGroup] = useState<
    Record<string, boolean>
  >({});
  const [subscriptionErrorByGroup, setSubscriptionErrorByGroup] = useState<Record<string, boolean>>(
    {},
  );
  const [showOnlyInconsistent, setShowOnlyInconsistent] = useState(false);
  const [progressByGroup, setProgressByGroup] = useState<Record<string, QueueProgress[]>>({});
  const [progressErrorByGroup, setProgressErrorByGroup] = useState<Record<string, boolean>>({});
  const [stackModalOpen, setStackModalOpen] = useState(false);
  const [stackLoading, setStackLoading] = useState(false);
  const [selectedStack, setSelectedStack] = useState<ConsumerStackTrace | null>(null);
  const [stackError, setStackError] = useState<string | null>(null);
  const [selectedStackClient, setSelectedStackClient] = useState<ConsumerInstance | null>(null);
  const importInputRef = useRef<HTMLInputElement>(null);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importFilename, setImportFilename] = useState('');
  const [importRows, setImportRows] = useState<ResourceImportRow<Partial<ConsumerGroup>>[]>([]);
  const [importErrors, setImportErrors] = useState<string[]>([]);
  const [importing, setImporting] = useState(false);
  const [exporting, setExporting] = useState(false);

  const groupRequestIdRef = useRef(0);
  const progressRequestIdRef = useRef<Record<string, number>>({});
  const subscriptionRequestIdRef = useRef<Record<string, number>>({});
  const stackRequestIdRef = useRef(0);
  const settingsRequestIdRef = useRef(0);
  // Consumption switches as loaded from the broker, used to detect high-risk changes
  // (disabling consumption / toggling ordered consumption) that need a confirm before saving.
  const originalSettingsRef = useRef<{
    consumeEnable?: boolean;
    consumeMessageOrderly?: boolean;
  } | null>(null);

  const [autoRefresh, setAutoRefresh] = useState(false);
  const silentRefreshRef = useRef(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const triggerRefresh = useCallback((silent: boolean) => {
    silentRefreshRef.current = silent;
    setRefreshKey((key) => key + 1);
  }, []);
  const clearResetPreview = useCallback(() => {
    setResetPreview(null);
    setResetPreviewKey('');
    setResetPreviewError(null);
  }, []);
  const selectedGroupName = selectedGroup?.name;

  const loadConsumerGroupPage = useCallback(
    async (pageToLoad: number, pageSizeToLoad: number, silent = false) => {
      if (!selectedInstanceId) return undefined;
      const requestId = ++groupRequestIdRef.current;
      if (!silent) setLoading(true);
      try {
        const result = await listConsumerGroupPage({
          instanceId: selectedInstanceId,
          search: search.trim() || undefined,
          page: pageToLoad,
          pageSize: pageSizeToLoad,
        });
        if (requestId === groupRequestIdRef.current) {
          setGroups(result.items);
          setTotalGroups(result.total);
          if (result.items.length === 0 && result.total > 0 && pageToLoad > 1) {
            setPage(Math.max(1, Math.ceil(result.total / pageSizeToLoad)));
          }
        }
        return requestId === groupRequestIdRef.current ? result : undefined;
      } catch {
        // A silent (auto-refresh) tick that fails must stay quiet: a toast every 2s while the
        // backend is down turns one transient outage into an unbounded error storm. Only
        // user-initiated loads surface the toast.
        if (requestId === groupRequestIdRef.current && !silent) {
          message.error(t('consumer.fetchListFailed'));
        }
        return undefined;
      } finally {
        if (requestId === groupRequestIdRef.current) setLoading(false);
      }
    },
    [t, selectedInstanceId, search],
  );

  const reloadConsumerGroupPage = useCallback(async () => {
    await loadConsumerGroupPage(page, pageSize);
  }, [loadConsumerGroupPage, page, pageSize]);

  useEffect(() => {
    if (!selectedInstanceId) {
      groupRequestIdRef.current += 1;
      const resetTimer = window.setTimeout(() => {
        setGroups([]);
        setTotalGroups(0);
        setSelectedRowKeys([]);
        setLoading(instancesLoading);
      }, 0);
      return () => {
        window.clearTimeout(resetTimer);
      };
    }
    const silent = silentRefreshRef.current;
    silentRefreshRef.current = false;
    const timer = window.setTimeout(() => {
      void loadConsumerGroupPage(page, pageSize, silent);
    }, 0);
    return () => {
      window.clearTimeout(timer);
    };
  }, [selectedInstanceId, page, pageSize, instancesLoading, refreshKey, loadConsumerGroupPage]);

  useEffect(() => {
    if (!autoRefresh || !selectedInstanceId) {
      return undefined;
    }
    const interval = window.setInterval(() => triggerRefresh(true), 2000);
    return () => window.clearInterval(interval);
  }, [autoRefresh, selectedInstanceId, triggerRefresh]);

  const loadSubscriptions = useCallback(
    async (groupName: string, force = false, silent = false) => {
      const cacheKey = diagnosticCacheKey(selectedInstanceId, groupName);
      if (!force && subscriptionsByGroup[cacheKey]) return;
      const requestId = (subscriptionRequestIdRef.current[cacheKey] ?? 0) + 1;
      subscriptionRequestIdRef.current[cacheKey] = requestId;
      if (!silent) {
        setSubscriptionLoadingByGroup((prev) => ({ ...prev, [cacheKey]: true }));
      }
      setSubscriptionErrorByGroup((prev) => ({ ...prev, [cacheKey]: false }));
      try {
        const subscriptions = await getConsumerSubscriptions(
          groupName,
          selectedInstanceId || undefined,
        );
        if (subscriptionRequestIdRef.current[cacheKey] === requestId) {
          setSubscriptionsByGroup((prev) => ({ ...prev, [cacheKey]: subscriptions }));
        }
      } catch {
        if (subscriptionRequestIdRef.current[cacheKey] === requestId) {
          setSubscriptionErrorByGroup((prev) => ({ ...prev, [cacheKey]: true }));
          if (!silent) {
            message.error(t('consumer.fetchSubscriptionsFailed', { name: groupName }));
          }
        }
      } finally {
        // The loading flag belongs to whichever request is current, not to the request that
        // raised it: the modal's 2s auto-refresh can supersede a user-visible check while that
        // check is still in flight, and then only the silent request is left to clear it.
        if (subscriptionRequestIdRef.current[cacheKey] === requestId) {
          setSubscriptionLoadingByGroup((prev) =>
            prev[cacheKey] ? { ...prev, [cacheKey]: false } : prev,
          );
        }
      }
    },
    [subscriptionsByGroup, t, selectedInstanceId],
  );

  const loadProgress = useCallback(
    async (groupName: string, force = false, silent = false) => {
      const cacheKey = diagnosticCacheKey(selectedInstanceId, groupName);
      if (!force && progressByGroup[cacheKey]) return;
      const requestId = (progressRequestIdRef.current[cacheKey] ?? 0) + 1;
      progressRequestIdRef.current[cacheKey] = requestId;
      try {
        const progress = await getConsumerProgress(groupName, selectedInstanceId || undefined);
        if (progressRequestIdRef.current[cacheKey] === requestId) {
          setProgressByGroup((prev) => ({ ...prev, [cacheKey]: progress }));
          setProgressErrorByGroup((prev) => ({ ...prev, [cacheKey]: false }));
        }
      } catch {
        // A failed read is not an empty result: the progress tab and the health
        // diagnosis must not present it as "the group is offline". A stale request
        // must not write the flag either, or it could mark a group as failed after
        // a newer read already succeeded.
        if (progressRequestIdRef.current[cacheKey] === requestId) {
          setProgressErrorByGroup((prev) => ({ ...prev, [cacheKey]: true }));
          if (!silent) message.error(t('consumer.fetchProgressFailed', { name: groupName }));
        }
      }
    },
    [progressByGroup, t, selectedInstanceId],
  );

  useEffect(() => {
    if (!modalOpen || !selectedGroupName || !selectedInstanceId) return undefined;
    const groupName = selectedGroupName;
    let inFlight = false;
    const tick = async () => {
      if (inFlight) return;
      inFlight = true;
      try {
        const refreshed = await refreshConsumerGroup(groupName, selectedInstanceId);
        if (refreshed) {
          setGroups((prev) => prev.map((g) => (g.name === groupName ? refreshed : g)));
          setSelectedGroup((prev) => (prev && prev.name === groupName ? refreshed : prev));
        }
        await loadProgress(groupName, true, true);
        // The modal advertises "每 2s 自动刷新" for the whole diagnostic result, which includes
        // the subscription consistency verdict, not only the progress table. Refresh it silently
        // so a failing backend does not toast every 2s and the check spinner does not flicker.
        await loadSubscriptions(groupName, true, true);
      } catch {
        // 自动刷新失败静默处理，避免每 2s 弹错
      } finally {
        inFlight = false;
      }
    };
    const interval = window.setInterval(() => void tick(), 2000);
    return () => window.clearInterval(interval);
  }, [modalOpen, selectedGroupName, selectedInstanceId, loadProgress, loadSubscriptions]);

  /* ─── Filtered & sorted data ─── */
  const filtered = useMemo(() => {
    return visibleConsumerGroups(groups, modeFilter);
  }, [groups, modeFilter]);

  const handleExport = async () => {
    setExporting(true);
    try {
      const csv = await exportConsumerGroups({
        instanceId: selectedInstanceId || undefined,
        search: search.trim() || undefined,
        subscriptionMode: modeFilter !== 'ALL' ? modeFilter : undefined,
      });
      downloadCsv(`rocketmq-consumer-groups-${new Date().toISOString().slice(0, 10)}.csv`, csv);
      message.success(t('consumer.exportCompleted'));
    } catch {
      message.error(t('consumer.exportFailed'));
    } finally {
      setExporting(false);
    }
  };

  /* ─── Open detail modal ─── */
  const [detailTab, setDetailTab] = useState('overview');
  const [progressTopic, setProgressTopic] = useState<string | undefined>(undefined);
  const openModal = (group: ConsumerGroup, tab = 'overview', topic?: string) => {
    setSelectedGroup(group);
    setShowOnlyInconsistent(false);
    setDetailTab(tab);
    setProgressTopic(topic);
    setModalOpen(true);
    void loadSubscriptions(group.name);
    void loadProgress(group.name);
  };

  const loadGroupSettings = async (group: ConsumerGroup) => {
    if (!selectedInstanceId) return;
    const requestId = ++settingsRequestIdRef.current;
    setSettingsGroup(group);
    setSettingsLoading(true);
    try {
      const settings = await getConsumerGroupSettings(group.name, selectedInstanceId);
      if (requestId === settingsRequestIdRef.current) {
        settingsForm.setFieldsValue(settings);
        originalSettingsRef.current = {
          consumeEnable: settings.consumeEnable,
          consumeMessageOrderly: settings.consumeMessageOrderly,
        };
      }
    } catch {
      if (requestId === settingsRequestIdRef.current) {
        message.error(t('consumer.settingsLoadFailed'));
      }
    } finally {
      if (requestId === settingsRequestIdRef.current) {
        setSettingsLoading(false);
      }
    }
  };

  const handleDetailTabChange = (key: string) => {
    setDetailTab(key);
    if (key === 'settings' && selectedGroup && settingsGroup?.name !== selectedGroup.name) {
      void loadGroupSettings(selectedGroup);
    }
    if (key === 'health' && selectedGroup) {
      void loadSubscriptions(selectedGroup.name);
      void loadProgress(selectedGroup.name);
    }
  };

  const saveSettings = async () => {
    if (!settingsGroup || !selectedInstanceId) return;
    const values = await settingsForm.validateFields();
    const original = originalSettingsRef.current;
    const risks: string[] = [];
    if (original && values.consumeEnable === false && original.consumeEnable !== false) {
      risks.push(t('consumer.riskDisableConsume'));
    }
    if (
      original &&
      values.consumeMessageOrderly !== undefined &&
      values.consumeMessageOrderly !== original.consumeMessageOrderly
    ) {
      risks.push(t('consumer.riskSwitchOrderly'));
    }
    if (risks.length > 0) {
      const confirmed = await new Promise<boolean>((resolve) => {
        Modal.confirm({
          title: t('consumer.confirmRiskySettingsTitle'),
          content: t('consumer.riskySettingsContent', {
            risks: risks.join(t('consumer.previewMessageSeparator')),
          }),
          okText: t('consumer.confirmChange'),
          okButtonProps: { danger: true },
          cancelText: t('common.cancel'),
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      });
      if (!confirmed) return;
    }
    setSettingsSubmitting(true);
    try {
      const saved = await updateConsumerGroupSettings({
        instanceId: selectedInstanceId,
        name: settingsGroup.name,
        ...values,
      });
      setGroups((current) =>
        current.map((group) =>
          group.name === settingsGroup.name
            ? { ...group, retryMaxTimes: saved.retryMaxTimes }
            : group,
        ),
      );
      setSelectedGroup((current) =>
        current && current.name === settingsGroup.name
          ? { ...current, retryMaxTimes: saved.retryMaxTimes }
          : current,
      );
      message.success(t('consumer.settingsSaved'));
    } catch {
      message.error(t('consumer.settingsSaveFailed'));
    } finally {
      setSettingsSubmitting(false);
    }
  };

  const selectedDiagnosticKey = selectedGroupName
    ? diagnosticCacheKey(selectedInstanceId, selectedGroupName)
    : '';
  const resetDiagnosticKey = resetGroup
    ? diagnosticCacheKey(selectedInstanceId, resetGroup.name)
    : '';
  const resetTimestamp = resetTime.valueOf();
  const currentResetPreviewKey =
    resetGroup && resetTopic
      ? [selectedInstanceId ?? '', resetGroup.name, resetTopic, resetTimestamp].join('\u0000')
      : '';
  const hasCurrentResetPreview = Boolean(
    resetPreview && resetPreviewKey === currentResetPreviewKey,
  );
  const resetPreviewQueues = hasCurrentResetPreview ? (resetPreview?.queues ?? []) : [];
  const resetPreviewWarnings = hasCurrentResetPreview ? (resetPreview?.warnings ?? []) : [];
  const resetPreviewCanApply = Boolean(hasCurrentResetPreview && resetPreview?.allowReset);
  const resetTopicOptions = useMemo(() => {
    const topics = new Set(resetGroup?.subscribedTopics ?? []);
    for (const subscription of subscriptionsByGroup[resetDiagnosticKey] ?? []) {
      if (subscription.topic) topics.add(subscription.topic);
    }
    return Array.from(topics).map((topic) => ({ label: topic, value: topic }));
  }, [resetDiagnosticKey, resetGroup, subscriptionsByGroup]);
  const selectedSubscriptions = useMemo(
    () => (selectedGroupName ? (subscriptionsByGroup[selectedDiagnosticKey] ?? []) : []),
    [selectedDiagnosticKey, selectedGroupName, subscriptionsByGroup],
  );
  const inconsistentSubscriptions = selectedSubscriptions.filter(isInconsistentSubscription);
  const unknownSubscriptions = selectedSubscriptions.filter(
    (subscription) =>
      !isConsistentSubscription(subscription) && !isInconsistentSubscription(subscription),
  );
  const visibleSubscriptions = showOnlyInconsistent
    ? inconsistentSubscriptions
    : selectedSubscriptions;
  const selectedProgress = useMemo(
    () => (selectedGroupName ? (progressByGroup[selectedDiagnosticKey] ?? []) : []),
    [progressByGroup, selectedDiagnosticKey, selectedGroupName],
  );
  const selectedProgressFailed = Boolean(progressErrorByGroup[selectedDiagnosticKey]);
  const progressTopicOptions = useMemo(
    () => Array.from(new Set(selectedProgress.map((q) => q.topic).filter(Boolean))).sort(),
    [selectedProgress],
  );
  const visibleProgress = useMemo(() => {
    const base =
      progressTopic && progressTopicOptions.includes(progressTopic)
        ? selectedProgress.filter((q) => q.topic === progressTopic)
        : selectedProgress;
    return [...base].sort((a, b) => {
      const byTopic = (a.topic ?? '').localeCompare(b.topic ?? '');
      if (byTopic !== 0) return byTopic;
      const byBroker = (a.broker ?? '').localeCompare(b.broker ?? '');
      if (byBroker !== 0) return byBroker;
      return (a.queueId ?? 0) - (b.queueId ?? 0);
    });
  }, [selectedProgress, progressTopic, progressTopicOptions]);
  const hasUnknownProgressLag = visibleProgress.some((q) => !isLagAvailable(q.diffTotal));
  const visibleProgressLag = visibleProgress.reduce(
    (sum, q) => sum + (isLagAvailable(q.diffTotal) ? q.diffTotal : 0),
    0,
  );
  const selectedGroupHealth = useMemo(
    () =>
      selectedGroup
        ? analyzeConsumerGroupHealth(selectedGroup, selectedSubscriptions, selectedProgress)
        : null,
    [selectedGroup, selectedProgress, selectedSubscriptions],
  );
  // A failed progress read leaves the queues unknown; the diagnosis stays useful
  // for the loaded data but must never read as "everything is healthy".
  const selectedGroupHealthIsPartial = selectedProgressFailed && selectedSubscriptions.length > 0;

  const handlePreviewResetOffset = async () => {
    if (!resetGroup || !resetTopic) {
      message.warning(t('consumer.selectResetTopicFirst'));
      return;
    }
    const previewKey = currentResetPreviewKey;
    setResetPreviewLoading(true);
    setResetPreviewError(null);
    try {
      const preview = await previewConsumerOffsetReset({
        name: resetGroup.name,
        instanceId: selectedInstanceId || undefined,
        topic: resetTopic,
        timestamp: resetTimestamp,
      });
      setResetPreview(preview);
      setResetPreviewKey(previewKey);
      if (preview.complete && preview.queueCount > 0) {
        message.success(t('consumer.previewedQueues', { count: preview.queueCount }));
      } else {
        message.warning(t('consumer.previewIncomplete'));
      }
    } catch (error) {
      const reason = error instanceof Error ? error.message : t('consumer.previewRequestFailed');
      setResetPreview(null);
      setResetPreviewKey('');
      setResetPreviewError(reason);
      message.error(reason);
    } finally {
      setResetPreviewLoading(false);
    }
  };

  const handleResetOffset = async () => {
    if (!resetGroup || !resetTopic) return;
    if (!resetPreviewCanApply) {
      message.warning(t('consumer.previewBeforeReset'));
      return;
    }
    setResetSubmitting(true);
    try {
      await resetConsumerOffset({
        name: resetGroup.name,
        instanceId: selectedInstanceId || undefined,
        topic: resetTopic,
        timestamp: resetTimestamp,
      });
      message.success(
        t('consumer.resetCompleted', {
          group: resetGroup.name,
          topic: resetTopic,
          time: resetTime.format('YYYY-MM-DD HH:mm:ss'),
        }),
      );
      setProgressByGroup((prev) => {
        const next = { ...prev };
        delete next[resetDiagnosticKey];
        return next;
      });
      triggerRefresh(true);
      setResetModalOpen(false);
      setResetGroup(null);
      setResetTopic(undefined);
      clearResetPreview();
    } catch {
      message.error(t('consumer.resetFailed'));
    } finally {
      setResetSubmitting(false);
    }
  };

  const openStackModal = async (consumerInstance: ConsumerInstance) => {
    if (!selectedGroup) return;
    const requestId = ++stackRequestIdRef.current;
    const groupName = selectedGroup.name;
    setSelectedStackClient(consumerInstance);
    setSelectedStack(null);
    setStackError(null);
    setStackModalOpen(true);
    setStackLoading(true);
    try {
      const stack = await getConsumerStack(
        groupName,
        consumerInstance.clientId,
        selectedInstanceId || undefined,
      );
      if (requestId === stackRequestIdRef.current) setSelectedStack(stack);
    } catch (error) {
      // The API client already surfaces the server message as a toast; keep the reason in the
      // modal so the operator can tell "capture unsupported" apart from "client went offline".
      if (requestId === stackRequestIdRef.current) {
        setStackError(error instanceof Error ? error.message : '');
      }
    } finally {
      if (requestId === stackRequestIdRef.current) setStackLoading(false);
    }
  };

  const handleImportFile = async (file: File) => {
    if (!selectedInstanceId) {
      message.error(t('consumer.selectInstanceFirst'));
      return;
    }
    setImportFilename(file.name);
    setImporting(false);
    setImportModalOpen(true);
    try {
      const records = parseCsvTable(await file.text());
      const validation = validateConsumerGroupCsvImport(records, selectedInstanceId || undefined);
      setImportRows(validation.rows);
      setImportErrors(validation.errors);
    } catch (error) {
      setImportRows([]);
      setImportErrors([error instanceof Error ? error.message : t('consumer.csvParseFailed')]);
    } finally {
      if (importInputRef.current) importInputRef.current.value = '';
    }
  };

  const handleImportConsumerGroups = async () => {
    if (!selectedInstanceId) {
      message.error(t('consumer.selectInstanceFirst'));
      return;
    }
    const targetIndexes = importRows
      .map((row, index) => ({ row, index }))
      .filter(({ row }) => row.status === 'pending' || row.status === 'failed');
    if (targetIndexes.length === 0 || importErrors.length > 0) return;

    setImporting(true);
    const nextRows = importRows.map((row) => ({ ...row }));
    let createdGroups: ConsumerGroup[] = [];

    try {
      const result = await importConsumerGroups(
        selectedInstanceId,
        targetIndexes.map(({ row }) => row.payload),
      );
      createdGroups = result.groups;
      const failureByIndex = new Map(result.failures.map((failure) => [failure.index, failure]));
      targetIndexes.forEach(({ index }, requestIndex) => {
        const failure = failureByIndex.get(requestIndex);
        nextRows[index] = failure
          ? {
              ...nextRows[index],
              status: 'failed',
              message: failure.message || t('consumer.rowCreateFailed'),
            }
          : { ...nextRows[index], status: 'success', message: t('consumer.rowCreated') };
      });
    } catch (error) {
      for (const { index } of targetIndexes) {
        nextRows[index] = {
          ...nextRows[index],
          status: 'failed',
          message: error instanceof Error ? error.message : t('consumer.rowCreateFailed'),
        };
      }
    } finally {
      setImporting(false);
    }
    setImportRows([...nextRows]);

    if (createdGroups.length > 0) {
      await reloadConsumerGroupPage();
    }

    const failedCount = nextRows.filter((row) => row.status === 'failed').length;
    const invalidCount = nextRows.filter((row) => row.status === 'invalid').length;
    if (failedCount === 0) {
      if (invalidCount > 0) {
        message.warning(
          t('consumer.importDoneSkipped', { created: createdGroups.length, invalid: invalidCount }),
        );
      } else {
        message.success(t('consumer.importDone', { created: createdGroups.length }));
      }
    } else if (createdGroups.length > 0) {
      message.warning(
        t('consumer.importDoneFailed', { created: createdGroups.length, failed: failedCount }),
      );
    } else {
      message.error(t('consumer.importFailed', { failed: failedCount }));
    }
  };

  const consumerGroupImportColumns: ColumnsType<ResourceImportRow<Partial<ConsumerGroup>>> = [
    { title: t('consumer.colLineNumber'), dataIndex: 'lineNumber', key: 'lineNumber', width: 80 },
    { title: t('consumer.colGroupName'), dataIndex: 'name', key: 'name' },
    {
      title: t('consumer.colStatus'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: ResourceImportRow<Partial<ConsumerGroup>>['status']) => {
        if (status === 'success') return <Tag color="success">{t('consumer.statusSuccess')}</Tag>;
        if (status === 'failed') return <Tag color="error">{t('consumer.statusFailed')}</Tag>;
        if (status === 'invalid') return <Tag color="warning">{t('consumer.statusInvalid')}</Tag>;
        return <Tag>{t('consumer.statusPending')}</Tag>;
      },
    },
    {
      title: t('consumer.colDescription'),
      dataIndex: 'message',
      key: 'message',
      render: (text?: string) => text || '-',
    },
  ];

  /* ═══════════════════════════════════════════
     Main Table Columns
     ═══════════════════════════════════════════ */
  const columns: ColumnsType<ConsumerGroup> = [
    {
      title: t('consumer.colGroupName'),
      dataIndex: 'name',
      key: 'name',
      // `minWidth` rather than `width`: this is the one column allowed to grow, so a window
      // wider than the table does not inflate every other column by the same proportion.
      // 170 keeps the total at the container width of a 1560px window, so the table fits
      // without a horizontal scrollbar there; on wider windows this column takes the surplus.
      minWidth: 170,
      ellipsis: true,
      sorter: (a, b) => a.name.localeCompare(b.name),
      render: (name: string) => (
        <Tooltip title={t('consumer.copyTooltip', { name })}>
          <Text
            strong
            style={{ fontSize: 14, cursor: 'pointer' }}
            onClick={() => {
              const done = () => message.success(t('consumer.copied', { name }));
              const failed = () => message.error(t('consumer.copyFailed'));
              if (navigator.clipboard?.writeText) {
                navigator.clipboard.writeText(name).then(done, failed);
              } else {
                const textarea = document.createElement('textarea');
                textarea.value = name;
                textarea.style.position = 'fixed';
                textarea.style.opacity = '0';
                document.body.appendChild(textarea);
                textarea.select();
                try {
                  if (document.execCommand('copy')) {
                    done();
                  } else {
                    failed();
                  }
                } catch {
                  failed();
                } finally {
                  document.body.removeChild(textarea);
                }
              }
            }}
          >
            {name}
          </Text>
        </Tooltip>
      ),
    },
    {
      title: t('consumer.colSubscriptionType'),
      dataIndex: 'subscriptionDataType',
      key: 'subscriptionDataType',
      width: 100,
      sorter: (a, b) => (a.subscriptionDataType ?? '').localeCompare(b.subscriptionDataType ?? ''),
      render: (type: string) => {
        const config = TOPIC_TYPE_MAP[type] || { labelKey: type, color: 'default' };
        return <Tag color={config.color}>{t(config.labelKey)}</Tag>;
      },
    },
    {
      title: t('consumer.colSubscriptionMode'),
      dataIndex: 'subscriptionMode',
      key: 'subscriptionMode',
      width: 84,
      sorter: (a, b) => (a.subscriptionMode ?? '').localeCompare(b.subscriptionMode ?? ''),
      render: (mode: string) => <Tag color={mode === 'Push' ? 'blue' : 'green'}>{mode}</Tag>,
    },
    {
      title: t('consumer.colOnlineClients'),
      dataIndex: 'onlineInstances',
      key: 'onlineInstances',
      width: 100,
      align: 'center',
      sorter: (a, b) =>
        onlineInstancesSortValue(a.onlineInstances) - onlineInstancesSortValue(b.onlineInstances),
      render: (value: number) => formatOnlineInstances(value, t(UNAVAILABLE_LAG_LABEL)),
    },
    {
      title: t('consumer.colTotalLag'),
      dataIndex: 'totalLag',
      key: 'totalLag',
      width: 96,
      align: 'right',
      sorter: (a, b) => lagSortValue(a.totalLag) - lagSortValue(b.totalLag),
      render: (lag: number) =>
        isLagAvailable(lag) ? (
          lag.toLocaleString()
        ) : (
          <Text type="secondary">{t(UNAVAILABLE_LAG_LABEL)}</Text>
        ),
    },
    {
      title: t('consumer.colDelaySeconds'),
      dataIndex: 'delaySeconds',
      key: 'delaySeconds',
      width: 100,
      align: 'right',
      sorter: (a, b) => (a.delaySeconds ?? 0) - (b.delaySeconds ?? 0),
      render: (seconds: number) => formatDelay(seconds ?? 0, lang),
    },
    {
      title: t('consumer.colCreatedAt'),
      dataIndex: 'gmtCreate',
      key: 'gmtCreate',
      // 156 = the 140px `YYYY-MM-DD HH:mm:ss` label at 14px plus the small-table cell padding;
      // anything narrower truncates the timestamp.
      width: 156,
      sorter: (a, b) => (a.gmtCreate ?? '').localeCompare(b.gmtCreate ?? ''),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
          {formatDateTime(d)}
        </Text>
      ),
    },
    {
      title: t('consumer.colModifiedAt'),
      dataIndex: 'gmtModified',
      key: 'gmtModified',
      width: 156,
      sorter: (a, b) => (a.gmtModified ?? '').localeCompare(b.gmtModified ?? ''),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
          {formatDateTime(d)}
        </Text>
      ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 248,
      render: (_: unknown, record: ConsumerGroup) => (
        <Flex gap={6} justify="flex-end">
          <Button
            size="small"
            icon={<Eye size={14} />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={(e) => {
              e.stopPropagation();
              openModal(record);
            }}
          >
            {t('consumer.btnDetail')}
          </Button>
          <Button
            size="small"
            icon={<ArrowsCounterClockwise size={14} />}
            style={{ borderColor: '#fa8c16', color: '#fa8c16' }}
            onClick={(e) => {
              e.stopPropagation();
              setResetGroup(record);
              setResetTopic(undefined);
              setResetTime(dayjs().subtract(3, 'hour'));
              clearResetPreview();
              setResetModalOpen(true);
              void loadSubscriptions(record.name);
            }}
          >
            {t('consumer.btnResetOffset')}
          </Button>
          <Button
            size="small"
            icon={<Trash size={14} />}
            style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
            onClick={(e) => {
              e.stopPropagation();
              Modal.confirm({
                title: t('consumer.deleteConfirmTitle', { name: record.name }),
                content: t('consumer.deleteConfirmContent'),
                okText: t('common.delete'),
                okButtonProps: { danger: true },
                cancelText: t('common.cancel'),
                onOk: async () => {
                  await deleteConsumerGroup(record.name, selectedInstanceId || undefined);
                  await reloadConsumerGroupPage();
                  setSelectedRowKeys((prev) => prev.filter((key) => key !== record.name));
                  message.success(t('consumer.deleted', { name: record.name }));
                },
              });
            }}
          >
            {t('common.delete')}
          </Button>
        </Flex>
      ),
    },
  ];

  /* ═══════════════════════════════════════════
     Expandable Sub-table: Subscription Details
     ═══════════════════════════════════════════ */
  const subscriptionSubColumns = (groupName: string): ColumnsType<SubscriptionEntry> => [
    {
      title: t('consumer.colTopic'),
      dataIndex: 'topic',
      key: 'topic',
      width: 200,
      render: (name: string) => (
        <Text strong style={{ fontSize: 14 }}>
          {name}
        </Text>
      ),
    },
    {
      title: t('consumer.colConsistency'),
      dataIndex: 'consistency',
      key: 'consistency',
      width: 110,
      render: (value: string) => (
        <Tag
          color={
            isConsistentValue(value) ? 'green' : isInconsistentValue(value) ? 'orange' : 'default'
          }
        >
          {value}
        </Tag>
      ),
    },
    {
      title: t('consumer.colSubscriptionMode'),
      dataIndex: 'filterMode',
      key: 'filterMode',
      width: 120,
      render: (mode: string) => {
        // The providers normalize the broker expression types to TAG / SQL / CLASS_FILTER
        // (SubscriptionFilterModes.fromExpressionType); map those codes to labels instead of
        // echoing them into the table.
        const meta: Record<string, { color: string; labelKey: string }> = {
          TAG: { color: 'blue', labelKey: 'consumer.filterTag' },
          SQL: { color: 'purple', labelKey: 'consumer.filterSql92' },
          CLASS_FILTER: { color: 'gold', labelKey: 'consumer.filterClassFilter' },
        };
        const entry = meta[mode];
        return <Tag color={entry?.color ?? 'default'}>{entry ? t(entry.labelKey) : mode}</Tag>;
      },
    },
    {
      title: t('consumer.colExpression'),
      dataIndex: 'expression',
      key: 'expression',
      width: 260,
      render: (expr: string) => (
        <Text code style={{ fontSize: 14 }}>
          {expr}
        </Text>
      ),
    },
    {
      title: '',
      key: 'action',
      width: 100,
      render: (_: unknown, record: SubscriptionEntry) => (
        <Button
          size="small"
          icon={<Eye size={14} />}
          title={t('consumer.viewQueueDistribution')}
          style={{ borderColor: '#1677ff', color: '#1677ff' }}
          onClick={() => {
            const group = groups.find((g) => g.name === groupName) ?? selectedGroup;
            if (group) openModal(group, 'progress', record.topic);
          }}
        >
          {t('consumer.btnViewDistribution')}
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
      width: 210,
      render: (id: string) => (
        <Text copyable style={{ fontSize: 14 }}>
          {id}
        </Text>
      ),
    },
    {
      title: t('consumer.colProtocol'),
      dataIndex: 'protocol',
      key: 'protocol',
      width: 80,
      render: (protocol: string) => {
        const config = PROTOCOL_MAP[protocol] || { labelKey: protocol, color: 'default' };
        return <Tag color={config.color}>{t(config.labelKey)}</Tag>;
      },
    },
    {
      title: t('consumer.colAddress'),
      dataIndex: 'address',
      key: 'address',
      width: 150,
      render: (addr: string) => (
        <Text code style={{ fontSize: 14 }}>
          {addr}
        </Text>
      ),
    },
    {
      title: t('consumer.colLastHeartbeat'),
      dataIndex: 'lastHeartbeat',
      key: 'lastHeartbeat',
      width: 150,
      render: (time: string) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
          {formatDateTime(time)}
        </Text>
      ),
    },
    {
      title: t('consumer.colDiagnostics'),
      key: 'diagnostics',
      width: 90,
      render: (_: unknown, record: ConsumerInstance) => (
        <Button
          size="small"
          icon={<ListBullets size={14} />}
          onClick={() => void openStackModal(record)}
        >
          {t('consumer.btnThreadStack')}
        </Button>
      ),
    },
  ];

  const healthIssueColumns: ColumnsType<ConsumerGroupHealthIssue> = [
    {
      title: t('consumer.colSeverity'),
      dataIndex: 'severity',
      key: 'severity',
      width: 84,
      render: (severity: ConsumerGroupHealthIssue['severity']) => (
        <Tag color={issueSeverityTagColor(severity)}>{t(issueSeverityLabel(severity))}</Tag>
      ),
    },
    {
      title: t('consumer.colIssue'),
      dataIndex: 'title',
      key: 'title',
      width: 180,
      render: (title: string, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{title}</Text>
          {record.subject && <Text type="secondary">{record.subject}</Text>}
        </Space>
      ),
    },
    {
      title: t('consumer.colDescription'),
      dataIndex: 'description',
      key: 'description',
      render: (description: string) => <Text>{description}</Text>,
    },
  ];

  /* ═══════════════════════════════════════════
     Modal: Queue Progress Tab
     ═══════════════════════════════════════════ */
  const queueColumns: ColumnsType<QueueProgress> = [
    {
      title: t('consumer.colTopic'),
      dataIndex: 'topic',
      key: 'topic',
      width: 280,
      ellipsis: true,
      render: (topic: string) => (
        <Text strong style={{ fontSize: 14 }} title={topic || '-'}>
          {topic || '-'}
        </Text>
      ),
    },
    {
      title: 'Broker',
      dataIndex: 'broker',
      key: 'broker',
      width: 160,
      render: (name: string) => (
        <Text strong style={{ fontSize: 14 }}>
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
      // Cloud providers report the lag per topic and cannot supply per-queue offsets, so they
      // send the negative sentinel: show it as unavailable instead of a number that would read
      // like a measurement next to the real lag.
      render: (offset: number) => (
        <Text style={{ fontFamily: 'monospace' }}>{formatOffsetValue(offset)}</Text>
      ),
    },
    {
      title: 'Consumer Offset',
      dataIndex: 'consumerOffset',
      key: 'consumerOffset',
      width: 150,
      align: 'right',
      render: (offset: number) => (
        <Text style={{ fontFamily: 'monospace' }}>{formatOffsetValue(offset)}</Text>
      ),
    },
    {
      title: t('consumer.colLag'),
      dataIndex: 'diffTotal',
      key: 'diffTotal',
      width: 120,
      align: 'right',
      render: (diff: number) => {
        if (!isLagAvailable(diff)) {
          return (
            <Text type="secondary" style={{ fontWeight: 600 }}>
              {t(UNAVAILABLE_LAG_LABEL)}
            </Text>
          );
        }
        const color = lagColor(diff);
        return (
          <Text style={{ color, fontWeight: 600, fontFamily: 'monospace' }}>
            {diff.toLocaleString()}
          </Text>
        );
      },
    },
  ];

  const resetPreviewColumns: ColumnsType<ResetConsumerOffsetQueuePreview> = [
    {
      title: 'Broker',
      dataIndex: 'broker',
      key: 'broker',
      width: 140,
      ellipsis: true,
      render: (broker: string) => (
        <Text strong style={{ fontSize: 14 }} title={broker}>
          {broker || '-'}
        </Text>
      ),
    },
    {
      title: 'Queue ID',
      dataIndex: 'queueId',
      key: 'queueId',
      width: 86,
      align: 'center',
      render: (id: number) => <Tag color="blue">Queue {id}</Tag>,
    },
    {
      title: t('consumer.colCurrentOffset'),
      dataIndex: 'consumerOffset',
      key: 'consumerOffset',
      width: 120,
      align: 'right',
      render: (offset: number) => (
        <Text style={{ fontFamily: 'monospace' }}>{formatOffsetValue(offset)}</Text>
      ),
    },
    {
      title: t('consumer.colTargetOffset'),
      dataIndex: 'targetOffset',
      key: 'targetOffset',
      width: 120,
      align: 'right',
      render: (offset: number) => (
        <Text strong style={{ fontFamily: 'monospace' }}>
          {formatOffsetValue(offset)}
        </Text>
      ),
    },
    {
      title: t('consumer.colChange'),
      dataIndex: 'offsetDelta',
      key: 'offsetDelta',
      width: 100,
      align: 'right',
      render: (delta: number, row: ResetConsumerOffsetQueuePreview) => (
        <Text
          style={{
            color: delta > 0 ? '#fa8c16' : delta < 0 ? '#1677ff' : undefined,
            fontFamily: 'monospace',
            fontWeight: 600,
          }}
        >
          {row.targetOffset < 0 || row.consumerOffset < 0 ? '-' : formatOffsetDelta(delta)}
        </Text>
      ),
    },
    {
      title: t('consumer.colCurrentLag'),
      dataIndex: 'currentLag',
      key: 'currentLag',
      width: 110,
      align: 'right',
      render: (lag: number) => (
        <Text style={{ fontFamily: 'monospace' }}>{formatOffsetValue(lag)}</Text>
      ),
    },
    {
      title: t('consumer.colProjectedLag'),
      dataIndex: 'projectedLag',
      key: 'projectedLag',
      width: 124,
      align: 'right',
      render: (lag: number) => (
        <Text style={{ fontFamily: 'monospace', color: lagColor(lag), fontWeight: 600 }}>
          {formatOffsetValue(lag)}
        </Text>
      ),
    },
    {
      title: t('consumer.colRisk'),
      dataIndex: 'riskLevel',
      key: 'riskLevel',
      width: 86,
      render: (riskLevel: string) => (
        <Tag color={resetPreviewRiskColor(riskLevel)}>{t(resetPreviewRiskLabel(riskLevel))}</Tag>
      ),
    },
    {
      title: t('consumer.colDescription'),
      key: 'message',
      width: 240,
      ellipsis: true,
      render: (_: unknown, record: ResetConsumerOffsetQueuePreview) => (
        <Text style={{ fontSize: 14 }} title={resetPreviewQueueMessage(record, t)}>
          {resetPreviewQueueMessage(record, t)}
        </Text>
      ),
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
        subtitle={t('consumer.pageSubtitle', { count: totalGroups })}
      />

      {/* ─── Filter Bar ─── */}
      <Flex justify="space-between" align="center" style={{ marginBottom: 16 }}>
        <Space size={12} wrap>
          <InstanceSelect
            value={selectedInstanceId || undefined}
            onChange={selectInstance}
            options={instanceOptions}
            style={{ width: 220 }}
            failed={instancesFailed}
            onRetry={reloadInstances}
          />
          <Input.Search
            placeholder={t('consumer.searchPlaceholder')}
            allowClear
            value={search}
            onChange={(e) => {
              setSelectedRowKeys([]);
              setSearch(e.target.value);
              setPage(1);
            }}
            onSearch={(value) => {
              setSelectedRowKeys([]);
              setSearch(value);
              setPage(1);
            }}
            style={{ width: 320 }}
            prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
          />
          <Select
            value={modeFilter}
            onChange={(value) => {
              setSelectedRowKeys([]);
              setModeFilter(value);
            }}
            style={{ width: 140 }}
            options={[
              { value: 'ALL', label: t('consumer.allModes') },
              { value: 'Push', label: 'Push' },
              { value: 'Pop', label: 'Pop' },
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
                  title: t('consumer.batchDeleteConfirmTitle'),
                  content: t('consumer.batchDeleteConfirmContent', {
                    count: selectedRowKeys.length,
                  }),
                  okText: t('common.delete'),
                  okButtonProps: { danger: true },
                  cancelText: t('common.cancel'),
                  onOk: async () => {
                    const names = selectedRowKeys.map(String);
                    const { deleted, failed } = await batchDeleteConsumerGroups(
                      names,
                      selectedInstanceId || undefined,
                    );
                    if (deleted.length > 0) await reloadConsumerGroupPage();
                    if (failed.length > 0) {
                      message.warning(
                        t('consumer.batchDeletedWithFailed', {
                          deleted: deleted.length,
                          failed: failed.length,
                          names: failed.join(', '),
                        }),
                      );
                      setSelectedRowKeys(failed);
                    } else {
                      message.success(t('consumer.batchDeleted', { count: deleted.length }));
                      setSelectedRowKeys([]);
                    }
                  },
                });
              }}
            >
              {t('consumer.btnDeleteCount', { count: selectedRowKeys.length })}
            </Button>
          )}
          <input
            ref={importInputRef}
            type="file"
            accept=".csv,text/csv"
            data-testid="consumer-group-import-file"
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
            {t('consumer.btnImport')}
          </Button>
          <Button icon={<ExportOutlined />} loading={exporting} onClick={() => void handleExport()}>
            {t('consumer.btnExport')}
          </Button>
          <Button
            type="primary"
            icon={<Plus size={14} weight="bold" />}
            disabled={!hasSelectedInstance}
            onClick={() => setCreateModalOpen(true)}
          >
            {t('consumer.btnCreateGroup')}
          </Button>
          <Tooltip title={t('consumer.autoRefreshTooltip')}>
            <Button
              icon={<SyncOutlined spin={autoRefresh} />}
              type={autoRefresh ? 'primary' : 'default'}
              ghost={autoRefresh}
              disabled={!hasSelectedInstance}
              onClick={() => {
                const next = !autoRefresh;
                setAutoRefresh(next);
                if (next) triggerRefresh(true);
              }}
            >
              {t('consumer.btnAutoRefresh')}
            </Button>
          </Tooltip>
        </Space>
      </Flex>

      {/* ─── Table with expandable rows ─── */}
      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={filtered}
          loading={loading}
          rowKey="name"
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          pagination={{
            current: page,
            pageSize,
            total: totalGroups,
            showSizeChanger: true,
            showTotal: (total) => t('consumer.totalGroups', { count: total }),
            pageSizeOptions: [10, 20, 50, 100],
            onChange: (nextPage, nextPageSize) => {
              setSelectedRowKeys([]);
              setPage(nextPage);
              setPageSize(nextPageSize);
            },
          }}
          size="small"
          tableLayout="fixed"
          scroll={{ x: tableScrollX(columns, { selection: true, expandable: true }) }}
          expandable={{
            onExpand: (expanded, record) => {
              if (expanded) void loadSubscriptions(record.name);
            },
            expandedRowRender: (record) => (
              <div style={{ padding: '8px 0' }}>
                <Table
                  columns={subscriptionSubColumns(record.name)}
                  dataSource={
                    subscriptionsByGroup[diagnosticCacheKey(selectedInstanceId, record.name)] ?? []
                  }
                  rowKey={(record) => `${record.topic}-${record.filterMode}-${record.expression}`}
                  loading={
                    subscriptionLoadingByGroup[diagnosticCacheKey(selectedInstanceId, record.name)]
                  }
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
            <Flex align="center" justify="space-between">
              <Space>
                <Cube size={18} weight="fill" color="#1677ff" />
                <span style={{ fontWeight: 600 }}>{selectedGroup.name}</span>
              </Space>
              <Text type="secondary" style={{ fontSize: 14, fontWeight: 400, marginRight: 28 }}>
                <SyncOutlined style={{ marginRight: 4 }} />
                {t('consumer.autoRefreshBadge')}
              </Text>
            </Flex>
          ) : (
            t('consumer.groupDetailTitle')
          )
        }
        open={modalOpen}
        onCancel={() => {
          settingsRequestIdRef.current += 1;
          setModalOpen(false);
          setSelectedGroup(null);
          setShowOnlyInconsistent(false);
          setSettingsGroup(null);
          setSettingsLoading(false);
          settingsForm.resetFields();
        }}
        width={detailTab === 'progress' || detailTab === 'health' ? 1080 : 800}
        destroyOnHidden
        footer={null}
      >
        {selectedGroup && (
          <Tabs
            activeKey={detailTab}
            onChange={handleDetailTabChange}
            items={[
              /* ─── 概览 Tab ─── */
              {
                key: 'overview',
                label: (
                  <Space size={4}>
                    <Info size={14} />
                    <span>{t('consumer.tabOverview')}</span>
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
                            title={t('consumer.statOnlineInstances')}
                            value={selectedGroup.onlineInstances}
                            formatter={(value) =>
                              formatOnlineInstances(Number(value), t(UNAVAILABLE_LAG_LABEL))
                            }
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
                            title={t('consumer.statTotalLag')}
                            value={selectedGroup.totalLag}
                            formatter={(value) =>
                              formatLag(Number(value), t(UNAVAILABLE_LAG_LABEL))
                            }
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
                            title={t('consumer.statSubscribedTopics')}
                            value={(selectedGroup.subscribedTopics ?? []).length}
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
                      styles={{ label: { fontWeight: 500, width: 140 } }}
                    >
                      <Descriptions.Item label={t('consumer.colGroupName')}>
                        <Text strong>{selectedGroup.name}</Text>
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.cluster')}>
                        {selectedGroup.clusterId}
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.colSubscriptionMode')}>
                        <Tag color={selectedGroup.subscriptionMode === 'Push' ? 'blue' : 'green'}>
                          {selectedGroup.subscriptionMode}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.consumeType')}>
                        <Tag
                          color={selectedGroup.consumeType === 'CLUSTERING' ? 'geekblue' : 'purple'}
                        >
                          {selectedGroup.consumeType}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.colSubscriptionType')}>
                        <Tag
                          color={
                            TOPIC_TYPE_MAP[selectedGroup.subscriptionDataType]?.color || 'default'
                          }
                        >
                          {TOPIC_TYPE_MAP[selectedGroup.subscriptionDataType]
                            ? t(TOPIC_TYPE_MAP[selectedGroup.subscriptionDataType].labelKey)
                            : selectedGroup.subscriptionDataType}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.colDelaySeconds')}>
                        <Text strong>{formatDelay(selectedGroup.delaySeconds, lang)}</Text>
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.retryMaxTimes')}>
                        <Text strong>{selectedGroup.retryMaxTimes}</Text>
                        {t('consumer.timesUnit')}
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.colCreatedAt')}>
                        <Space size={4}>
                          <Clock size={13} color="#9CA3AF" />
                          <Text type="secondary">{selectedGroup.gmtCreate}</Text>
                        </Space>
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.colModifiedAt')}>
                        <Space size={4}>
                          <Clock size={13} color="#9CA3AF" />
                          <Text type="secondary">{selectedGroup.gmtModified}</Text>
                        </Space>
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.subscribedTopics')} span={2}>
                        <Space size={4} wrap>
                          {(selectedGroup.subscribedTopics ?? []).map((t) => (
                            <Tag key={t} color="blue">
                              {t}
                            </Tag>
                          ))}
                        </Space>
                      </Descriptions.Item>
                    </Descriptions>

                    {/* 在线实例 */}
                    <div style={{ marginTop: 24 }}>
                      <Flex align="center" gap={6} style={{ marginBottom: 12 }}>
                        <Users size={15} color="#52c41a" />
                        <Text strong style={{ fontSize: 14 }}>
                          {t('consumer.onlineInstancesCount', {
                            count: (selectedGroup.instances ?? []).length,
                          })}
                        </Text>
                      </Flex>
                      <Table
                        columns={instanceColumns}
                        dataSource={selectedGroup.instances ?? []}
                        rowKey="clientId"
                        pagination={false}
                        size="small"
                        tableLayout="fixed"
                        scroll={{ x: tableScrollX(instanceColumns) }}
                      />
                    </div>

                    {/* 订阅关系 */}
                    <div style={{ marginTop: 24 }}>
                      <Flex justify="space-between" align="center" style={{ marginBottom: 12 }}>
                        <Flex align="center" gap={6}>
                          <ListBullets size={15} color="#1677ff" />
                          <Text strong style={{ fontSize: 14 }}>
                            {t('consumer.consistencyCheck')}
                          </Text>
                        </Flex>
                        <Button
                          size="small"
                          icon={<ArrowsClockwise size={14} />}
                          loading={subscriptionLoadingByGroup[selectedDiagnosticKey]}
                          onClick={() => {
                            setShowOnlyInconsistent(false);
                            void loadSubscriptions(selectedGroup.name, true);
                          }}
                        >
                          {t('consumer.recheck')}
                        </Button>
                      </Flex>
                      <Alert
                        showIcon
                        type={
                          subscriptionErrorByGroup[selectedDiagnosticKey]
                            ? 'error'
                            : inconsistentSubscriptions.length > 0 ||
                                unknownSubscriptions.length > 0
                              ? 'warning'
                              : selectedSubscriptions.length > 0
                                ? 'success'
                                : 'info'
                        }
                        message={
                          subscriptionErrorByGroup[selectedDiagnosticKey]
                            ? t('consumer.consistencyCheckFailedKeepLast')
                            : subscriptionLoadingByGroup[selectedDiagnosticKey] &&
                                selectedSubscriptions.length === 0
                              ? t('consumer.consistencyChecking')
                              : inconsistentSubscriptions.length > 0
                                ? t('consumer.foundInconsistent', {
                                    count: inconsistentSubscriptions.length,
                                  })
                                : unknownSubscriptions.length > 0
                                  ? t('consumer.foundUnknown', {
                                      count: unknownSubscriptions.length,
                                    })
                                  : selectedSubscriptions.length > 0
                                    ? t('consumer.allConsistent', {
                                        count: selectedSubscriptions.length,
                                      })
                                    : t('consumer.noSubscriptionsToCheck')
                        }
                        action={
                          <Checkbox
                            checked={showOnlyInconsistent}
                            disabled={inconsistentSubscriptions.length === 0}
                            onChange={(event) => setShowOnlyInconsistent(event.target.checked)}
                          >
                            {t('consumer.showOnlyInconsistent')}
                          </Checkbox>
                        }
                        style={{ marginBottom: 12 }}
                      />
                      <Table
                        columns={subscriptionSubColumns(selectedGroup?.name ?? '')}
                        dataSource={visibleSubscriptions}
                        rowKey={(record) =>
                          `${record.topic}-${record.filterMode}-${record.expression}`
                        }
                        loading={subscriptionLoadingByGroup[selectedDiagnosticKey]}
                        pagination={false}
                        size="small"
                      />
                    </div>
                  </div>
                ),
              },
              /* ─── 健康诊断 Tab ─── */
              {
                key: 'health',
                label: (
                  <Space size={4}>
                    <Info size={14} />
                    <span>{t('consumer.tabHealth')}</span>
                  </Space>
                ),
                children: selectedGroupHealth && (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Flex justify="space-between" align="center" gap={12} wrap>
                      <Space direction="vertical" size={2}>
                        <Space>
                          <Tag color={healthStatusTagColor(selectedGroupHealth.status)}>
                            {selectedGroupHealth.statusText}
                          </Tag>
                          <Text type="secondary">{t('consumer.healthSummaryIntro')}</Text>
                        </Space>
                        <Text type="secondary">{t('consumer.healthRefreshNote')}</Text>
                      </Space>
                      <Button
                        size="small"
                        icon={<ArrowsClockwise size={14} />}
                        loading={subscriptionLoadingByGroup[selectedDiagnosticKey]}
                        onClick={() => {
                          void loadSubscriptions(selectedGroup.name, true);
                          void loadProgress(selectedGroup.name, true);
                        }}
                      >
                        {t('consumer.rediagnose')}
                      </Button>
                    </Flex>

                    {subscriptionErrorByGroup[selectedDiagnosticKey] && (
                      <Alert
                        type="warning"
                        showIcon
                        message={t('consumer.consistencyCheckFailedDiagnosis')}
                      />
                    )}

                    {selectedGroupHealthIsPartial && (
                      <Alert
                        type="warning"
                        showIcon
                        message={t('consumer.progressLoadFailedDiagnosis')}
                      />
                    )}

                    <Row gutter={16}>
                      <Col span={6}>
                        <Card size="small" style={{ borderRadius: 8 }}>
                          <Statistic
                            title={t('consumer.statHealthScore')}
                            value={selectedGroupHealth.summary.healthScore}
                            suffix="/ 100"
                            valueStyle={{
                              color:
                                selectedGroupHealth.status === 'critical'
                                  ? '#ff4d4f'
                                  : selectedGroupHealth.status === 'warning'
                                    ? '#faad14'
                                    : '#52c41a',
                            }}
                          />
                          <Progress
                            percent={selectedGroupHealth.summary.healthScore}
                            showInfo={false}
                            status={
                              selectedGroupHealth.status === 'critical'
                                ? 'exception'
                                : selectedGroupHealth.status === 'warning'
                                  ? 'active'
                                  : 'success'
                            }
                          />
                        </Card>
                      </Col>
                      <Col span={6}>
                        <Card size="small" style={{ borderRadius: 8 }}>
                          <Statistic
                            title={t('consumer.statKnownLag')}
                            value={selectedGroupHealth.summary.totalKnownLag}
                            valueStyle={{
                              color: lagColor(selectedGroupHealth.summary.totalKnownLag),
                            }}
                          />
                          <Text type="secondary">
                            {t('consumer.reportedLagLabel')}
                            {selectedGroupHealth.summary.reportedLag === null
                              ? t(UNAVAILABLE_LAG_LABEL)
                              : selectedGroupHealth.summary.reportedLag.toLocaleString()}
                          </Text>
                        </Card>
                      </Col>
                      <Col span={6}>
                        <Card size="small" style={{ borderRadius: 8 }}>
                          <Statistic
                            title={t('consumer.statQueueCoverage')}
                            value={selectedGroupHealth.summary.queueCount}
                            suffix={`/${selectedGroupHealth.summary.subscribedTopicCount} Topic`}
                          />
                          <Text type="secondary">
                            {selectedGroupHealth.summary.unknownQueueCount > 0
                              ? t('consumer.queueLagUnavailableCount', {
                                  count: selectedGroupHealth.summary.unknownQueueCount,
                                })
                              : t('consumer.queueLagAllAvailable')}
                          </Text>
                        </Card>
                      </Col>
                      <Col span={6}>
                        <Card size="small" style={{ borderRadius: 8 }}>
                          <Statistic
                            title={t('consumer.statClients')}
                            value={selectedGroupHealth.summary.onlineInstances}
                            formatter={(value) =>
                              formatOnlineInstances(Number(value), t(UNAVAILABLE_LAG_LABEL))
                            }
                          />
                          <Text type="secondary">
                            {selectedGroupHealth.summary.onlineInstances < 0
                              ? t('consumer.clientInfoUnavailable')
                              : selectedGroupHealth.summary.staleClientCount > 0
                                ? t('consumer.staleHeartbeatCount', {
                                    count: selectedGroupHealth.summary.staleClientCount,
                                  })
                                : t('consumer.heartbeatNormal')}
                          </Text>
                        </Card>
                      </Col>
                    </Row>

                    {selectedGroupHealth.issues.length > 0 ? (
                      <Table
                        columns={healthIssueColumns}
                        dataSource={selectedGroupHealth.issues}
                        rowKey="id"
                        pagination={false}
                        size="small"
                        tableLayout="fixed"
                        scroll={{ x: tableScrollX(healthIssueColumns) }}
                      />
                    ) : (
                      <Alert type="success" showIcon message={t('consumer.noHealthRisks')} />
                    )}

                    {selectedGroupHealth.recommendations.length > 0 && (
                      <Alert
                        type="info"
                        showIcon
                        message={t('consumer.recommendations')}
                        description={
                          <Space direction="vertical" size={4}>
                            {selectedGroupHealth.recommendations.map((recommendation) => (
                              <Text key={recommendation}>{recommendation}</Text>
                            ))}
                          </Space>
                        }
                      />
                    )}
                  </Space>
                ),
              },
              /* ─── 消费进度 Tab ─── */
              {
                key: 'progress',
                label: (
                  <Space size={4}>
                    <ArrowsClockwise size={14} />
                    <span>{t('consumer.tabProgress')}</span>
                  </Space>
                ),
                children: (
                  <div>
                    {selectedProgressFailed && (
                      <Alert
                        type="warning"
                        showIcon
                        style={{ marginBottom: 12 }}
                        message={t('consumer.progressLoadFailedTitle')}
                        description={t('consumer.progressLoadFailedDescription')}
                      />
                    )}
                    {progressTopicOptions.length > 0 && (
                      <Flex align="center" gap={8} style={{ marginBottom: 12 }}>
                        <Text type="secondary">{t('consumer.topicFilterLabel')}</Text>
                        <Select
                          size="small"
                          style={{ minWidth: 240 }}
                          allowClear
                          placeholder={t('consumer.allTopics')}
                          value={
                            progressTopic && progressTopicOptions.includes(progressTopic)
                              ? progressTopic
                              : undefined
                          }
                          onChange={(value) => setProgressTopic(value)}
                          options={progressTopicOptions.map((topic) => ({
                            label: topic,
                            value: topic,
                          }))}
                        />
                      </Flex>
                    )}
                    <Card
                      size="small"
                      style={{
                        marginBottom: 16,
                        background: '#fafafa',
                        borderRadius: 8,
                      }}
                      styles={{ body: { padding: '8px 16px' } }}
                    >
                      <Space size={24}>
                        <Space size={4}>
                          <Text type="secondary">{t('consumer.totalBrokersLabel')}</Text>
                          <Text strong>{new Set(visibleProgress.map((q) => q.broker)).size}</Text>
                        </Space>
                        <Space size={4}>
                          <Text type="secondary">{t('consumer.totalQueuesLabel')}</Text>
                          <Text strong>{visibleProgress.length}</Text>
                        </Space>
                        <Space size={4}>
                          <Text type="secondary">{t('consumer.totalLagLabel')}</Text>
                          {hasUnknownProgressLag ? (
                            <Text strong style={{ color: UNKNOWN_LAG_COLOR }}>
                              {t(UNAVAILABLE_LAG_LABEL)}
                            </Text>
                          ) : (
                            <Text
                              strong
                              style={{
                                color: lagColor(visibleProgressLag),
                              }}
                            >
                              {visibleProgressLag.toLocaleString()}
                            </Text>
                          )}
                        </Space>
                      </Space>
                    </Card>

                    <Table
                      columns={queueColumns}
                      dataSource={visibleProgress}
                      rowKey={(r) => `${r.topic}-${r.broker}-${r.queueId}`}
                      pagination={false}
                      size="small"
                      tableLayout="fixed"
                      scroll={{ x: tableScrollX(queueColumns), y: 380 }}
                      locale={{
                        emptyText: selectedProgressFailed
                          ? // A failed read must not assert that the group is offline.
                            t('consumer.queueProgressUnavailable')
                          : t('consumer.groupOfflineNoProgress'),
                      }}
                    />
                  </div>
                ),
              },
              /* ─── 配置 Tab ─── */
              {
                key: 'settings',
                label: (
                  <Space size={4}>
                    <SlidersHorizontal size={14} />
                    <span>{t('consumer.tabSettings')}</span>
                  </Space>
                ),
                disabled: isCloudInstance,
                children: (
                  <Spin spinning={settingsLoading}>
                    <Form form={settingsForm} layout="vertical" style={{ maxWidth: 480 }}>
                      <Form.Item label={t('consumer.colGroupName')}>
                        <Text strong>{selectedGroup.name}</Text>
                      </Form.Item>
                      <Form.Item
                        label={t('consumer.retryQueueNums')}
                        name="retryQueueNums"
                        rules={[{ required: true, message: t('consumer.retryQueueNumsRequired') }]}
                      >
                        <InputNumber min={1} max={128} style={{ width: '100%' }} />
                      </Form.Item>
                      <Form.Item
                        label={t('consumer.retryMaxTimes')}
                        name="retryMaxTimes"
                        rules={[{ required: true, message: t('consumer.retryMaxTimesRequired') }]}
                      >
                        <InputNumber min={1} max={128} style={{ width: '100%' }} />
                      </Form.Item>
                      <Form.Item
                        label={t('consumer.consumeEnable')}
                        name="consumeEnable"
                        valuePropName="checked"
                      >
                        <Switch />
                      </Form.Item>
                      <Form.Item
                        label={t('consumer.consumeOrderly')}
                        name="consumeMessageOrderly"
                        valuePropName="checked"
                      >
                        <Switch />
                      </Form.Item>
                      <Form.Item
                        label={t('consumer.consumeBroadcast')}
                        name="consumeBroadcastEnable"
                        valuePropName="checked"
                      >
                        <Switch />
                      </Form.Item>
                      <Form.Item style={{ marginBottom: 0 }}>
                        <Button
                          type="primary"
                          loading={settingsSubmitting}
                          onClick={() => void saveSettings()}
                        >
                          {t('consumer.btnSave')}
                        </Button>
                      </Form.Item>
                    </Form>
                  </Spin>
                ),
              },
            ]}
          />
        )}
      </Modal>

      {/* ═══════════════════════════════════════════
         Consumer Stack Modal
         ═══════════════════════════════════════════ */}
      <Modal
        title={
          <Space>
            <ListBullets size={18} color="#1677ff" />
            <span>{t('consumer.stackModalTitle')}</span>
          </Space>
        }
        open={stackModalOpen}
        onCancel={() => {
          stackRequestIdRef.current += 1;
          setStackModalOpen(false);
          setSelectedStack(null);
          setStackError(null);
          setSelectedStackClient(null);
        }}
        footer={null}
        width={900}
        destroyOnHidden
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="Group">
              <Text strong>{selectedStack?.groupName ?? selectedGroup?.name ?? '-'}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Client ID">
              <Text copyable>
                {selectedStack?.clientId ?? selectedStackClient?.clientId ?? '-'}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('consumer.capturedAt')}>
              {selectedStack?.capturedAt ? formatUtcDateTime(selectedStack.capturedAt) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('consumer.threadCount')}>
              {selectedStack?.threadCount ?? 0}
            </Descriptions.Item>
          </Descriptions>

          {stackLoading ? (
            <Table
              loading
              columns={[
                { title: t('consumer.colThread'), dataIndex: 'threadName', key: 'threadName' },
              ]}
              dataSource={[]}
              pagination={false}
              size="small"
            />
          ) : selectedStack && selectedStack.threads.length > 0 ? (
            selectedStack.threads.map((thread) => (
              <Card
                key={`${thread.threadName}-${thread.threadId}`}
                size="small"
                title={
                  <Space>
                    <Text strong>{thread.threadName}</Text>
                    <Tag color="blue">TID {thread.threadId}</Tag>
                    <Tag color={thread.state === 'RUNNABLE' ? 'green' : 'orange'}>
                      {thread.state}
                    </Tag>
                  </Space>
                }
              >
                <pre
                  style={{
                    margin: 0,
                    maxHeight: 240,
                    overflow: 'auto',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    fontSize: 14,
                    lineHeight: 1.6,
                  }}
                >
                  {thread.stackTrace.join('\n')}
                </pre>
              </Card>
            ))
          ) : (
            <Alert
              type="info"
              showIcon
              message={t('consumer.stackNotSupported')}
              description={
                <>
                  <div>{t('consumer.stackNotSupportedDescription')}</div>
                  {stackError && (
                    <div style={{ marginTop: 8, color: 'rgba(0,0,0,0.45)' }}>{stackError}</div>
                  )}
                </>
              }
            />
          )}
        </Space>
      </Modal>

      {/* ═══════════════════════════════════════════
         Create Group Modal
         ═══════════════════════════════════════════ */}
      <Modal
        title={
          <Space>
            <Plus size={18} weight="bold" color="#1677ff" />
            <span>{t('consumer.createModalTitle')}</span>
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
              if (!selectedInstanceId) {
                message.error(t('consumer.selectInstanceFirst'));
                return;
              }
              Modal.confirm({
                title: t('consumer.confirmCreateTitle'),
                content: t('consumer.confirmCreateContent', { name: values.name }),
                okText: t('consumer.confirmCreate'),
                cancelText: t('common.cancel'),
                onOk: async () => {
                  setSubmitting(true);
                  try {
                    await createConsumerGroup({
                      name: values.name,
                      subscriptionMode: values.subscriptionMode,
                      consumeType: values.consumeType,
                      retryMaxTimes: values.retryMaxTimes,
                      subscriptionDataType: values.dataType || 'NORMAL',
                      deliveryOrderType: values.deliveryOrderType,
                      subscribedTopics: [],
                      instanceId: selectedInstanceId,
                    });
                    // The list is server-paginated: refetch the current page so the new
                    // group lands where the server sorts it and the total stays truthful,
                    // mirroring the topic inventory behavior after create.
                    await reloadConsumerGroupPage();
                    message.success(t('consumer.created', { name: values.name }));
                    setCreateModalOpen(false);
                    form.resetFields();
                    setDataTypeValue(undefined);
                  } catch {
                    message.error(t('consumer.createFailed'));
                    throw new Error(t('consumer.createFailed'));
                  } finally {
                    setSubmitting(false);
                  }
                },
              });
            })
            .catch(() => {});
        }}
        confirmLoading={submitting}
        okText={t('consumer.btnCreate')}
        cancelText={t('common.cancel')}
        width={560}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 16 }}
          initialValues={{
            subscriptionMode: 'Push',
            consumeType: 'CLUSTERING',
            retryMaxTimes: 16,
          }}
        >
          <Form.Item
            label={t('consumer.colGroupName')}
            name="name"
            rules={[
              { required: true, message: t('consumer.groupNameRequired') },
              {
                pattern: RESOURCE_NAME_PATTERN,
                message: t('consumer.groupNamePattern'),
              },
              {
                max: RESOURCE_NAME_MAX_LENGTH.group,
                message: t('consumer.groupNameMaxLength', { max: RESOURCE_NAME_MAX_LENGTH.group }),
              },
            ]}
          >
            <Input placeholder={t('consumer.groupNameExample')} />
          </Form.Item>

          {!isCloudInstance && (
            <Form.Item label={t('consumer.colSubscriptionMode')} name="subscriptionMode">
              <Radio.Group>
                <Radio.Button value="Push">Push</Radio.Button>
                <Radio.Button value="Pop">Pop</Radio.Button>
              </Radio.Group>
            </Form.Item>
          )}

          {!isCloudInstance && (
            <Form.Item label={t('consumer.consumeType')} name="consumeType">
              <Radio.Group>
                <Radio.Button value="CLUSTERING">{t('consumer.clustering')}</Radio.Button>
                <Radio.Button value="BROADCASTING">{t('consumer.broadcasting')}</Radio.Button>
              </Radio.Group>
            </Form.Item>
          )}

          <Form.Item label={t('consumer.retryMaxTimes')} name="retryMaxTimes">
            <InputNumber min={0} max={128} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item label={t('consumer.colSubscriptionType')} name="dataType">
            <Select
              placeholder={t('consumer.selectMessageType')}
              options={[
                { value: 'NORMAL', label: t('consumer.typeNormal') },
                { value: 'FIFO', label: t('consumer.typeFifo') },
                { value: 'DELAY', label: t('consumer.typeDelay') },
                { value: 'TRANSACTION', label: t('consumer.typeTransaction') },
              ]}
              onChange={(val) => setDataTypeValue(val)}
            />
          </Form.Item>

          {dataTypeValue === 'FIFO' && (
            <Form.Item
              label={t('consumer.deliveryOrderType')}
              name="deliveryOrderType"
              initialValue="PARTITON_ORDER"
            >
              <Select
                options={[
                  {
                    value: 'PARTITON_ORDER',
                    label: t('consumer.partitionOrder'),
                  },
                  {
                    value: 'MESSAGES_ORDER',
                    label: t('consumer.globalOrder'),
                  },
                ]}
              />
            </Form.Item>
          )}
        </Form>
      </Modal>

      {/* ═══════════════════════════════════════════
         Import Group Modal
         ═══════════════════════════════════════════ */}
      <Modal
        title={
          importFilename
            ? t('consumer.importModalTitleWithFile', { filename: importFilename })
            : t('consumer.importModalTitle')
        }
        open={importModalOpen}
        onCancel={() => {
          if (!importing) setImportModalOpen(false);
        }}
        onOk={() => void handleImportConsumerGroups()}
        okText={
          importRows.some((row) => row.status === 'failed')
            ? t('consumer.retryFailedRows')
            : t('consumer.startImport')
        }
        cancelText={t('consumer.btnClose')}
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
              message={t('consumer.csvNotImportable')}
              description={importErrors.join(t('consumer.previewMessageSeparator'))}
            />
          ) : importRows.some((row) => row.status === 'invalid') ? (
            <Alert
              type="warning"
              showIcon
              message={t('consumer.detectedInvalidRows', {
                count: importRows.filter((row) => row.status === 'invalid').length,
              })}
              description={t('consumer.importFieldsNote')}
            />
          ) : (
            <Alert
              type="info"
              showIcon
              message={t('consumer.detectedGroups', { count: importRows.length })}
              description={t('consumer.importFieldsNote')}
            />
          )}
          <Table<ResourceImportRow<Partial<ConsumerGroup>>>
            columns={consumerGroupImportColumns}
            dataSource={importRows}
            rowKey="key"
            size="small"
            pagination={false}
          />
        </Space>
      </Modal>

      {/* ═══════════════════════════════════════════
         Reset Offset Modal
         ═══════════════════════════════════════════ */}
      <Modal
        title={
          <Space>
            <ArrowsCounterClockwise size={18} color="#fa8c16" />
            <span>{t('consumer.resetModalTitle')}</span>
          </Space>
        }
        open={resetModalOpen}
        onCancel={() => {
          setResetModalOpen(false);
          setResetGroup(null);
          setResetTopic(undefined);
          clearResetPreview();
        }}
        onOk={() => void handleResetOffset()}
        confirmLoading={resetSubmitting}
        okButtonProps={{
          disabled:
            !resetPreviewCanApply ||
            resetPreviewLoading ||
            Boolean(subscriptionLoadingByGroup[resetDiagnosticKey]),
        }}
        okText={t('consumer.confirmReset')}
        cancelText={t('common.cancel')}
        width={1200}
        destroyOnHidden
      >
        {resetGroup && (
          <Space direction="vertical" size={16} style={{ width: '100%', marginTop: 16 }}>
            <Alert
              showIcon
              type="warning"
              message={t('consumer.resetWarningTitle')}
              description={t('consumer.resetWarningDescription')}
            />
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 4 }}>
                {t('consumer.targetGroup')}
              </Text>
              <Text strong style={{ fontSize: 14 }}>
                {resetGroup.name}
              </Text>
            </div>
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 8 }}>
                {t('consumer.targetTopic')}
              </Text>
              <Select
                aria-label={t('consumer.targetTopic')}
                showSearch
                optionFilterProp="label"
                style={{ width: '100%' }}
                value={resetTopic}
                options={resetTopicOptions}
                loading={subscriptionLoadingByGroup[resetDiagnosticKey]}
                placeholder={t('consumer.selectResetTopic')}
                onChange={(value) => {
                  setResetTopic(value);
                  clearResetPreview();
                }}
                notFoundContent={
                  subscriptionErrorByGroup[resetDiagnosticKey]
                    ? t('consumer.subscribedTopicsLoadFailed')
                    : t('consumer.noSubscribedTopics')
                }
              />
            </div>
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 8 }}>
                {t('consumer.resetToTimeLabel')}
              </Text>
              <DatePicker
                showTime
                style={{ width: '100%' }}
                value={resetTime}
                onChange={(val) => {
                  if (val) {
                    setResetTime(val);
                    clearResetPreview();
                  }
                }}
                format="YYYY-MM-DD HH:mm:ss"
                placeholder={t('consumer.selectResetTime')}
              />
            </div>
            <div>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 8 }}>
                {t('consumer.quickSelect')}
              </Text>
              <Space wrap>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs());
                    clearResetPreview();
                  }}
                >
                  {t('consumer.skipBacklog')}
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs().subtract(1, 'hour'));
                    clearResetPreview();
                  }}
                >
                  {t('consumer.hoursAgo', { count: 1 })}
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs().subtract(3, 'hour'));
                    clearResetPreview();
                  }}
                >
                  {t('consumer.hoursAgo', { count: 3 })}
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs().subtract(6, 'hour'));
                    clearResetPreview();
                  }}
                >
                  {t('consumer.hoursAgo', { count: 6 })}
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs().subtract(12, 'hour'));
                    clearResetPreview();
                  }}
                >
                  {t('consumer.hoursAgo', { count: 12 })}
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs().subtract(1, 'day'));
                    clearResetPreview();
                  }}
                >
                  {t('consumer.daysAgo', { count: 1 })}
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs().subtract(3, 'day'));
                    clearResetPreview();
                  }}
                >
                  {t('consumer.daysAgo', { count: 3 })}
                </Button>
              </Space>
            </div>
            <Flex justify="space-between" align="center" gap={12}>
              <Text type="secondary" style={{ fontSize: 14 }}>
                {t('consumer.previewNote')}
              </Text>
              <Button
                icon={<Eye size={14} />}
                loading={resetPreviewLoading}
                disabled={
                  !resetTopic ||
                  Boolean(subscriptionLoadingByGroup[resetDiagnosticKey]) ||
                  resetSubmitting
                }
                onClick={() => void handlePreviewResetOffset()}
              >
                {t('consumer.btnPreviewImpact')}
              </Button>
            </Flex>
            {resetPreviewError && (
              <Alert
                showIcon
                type="error"
                message={t('consumer.previewFailedTitle')}
                description={resetPreviewError}
              />
            )}
            {hasCurrentResetPreview && resetPreview && (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Descriptions bordered size="small" column={4}>
                  <Descriptions.Item label={t('consumer.queueCount')}>
                    {resetPreview.queueCount}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('consumer.currentTotalLag')}>
                    {formatOffsetValue(resetPreview.currentTotalLag)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('consumer.projectedTotalLag')}>
                    {formatOffsetValue(resetPreview.projectedTotalLag)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('consumer.totalOffsetDelta')}>
                    {formatOffsetDelta(resetPreview.totalOffsetDelta)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('consumer.rewindQueues')}>
                    {resetPreview.rewindQueueCount}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('consumer.fastForwardQueues')}>
                    {resetPreview.fastForwardQueueCount}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('consumer.previewStatus')} span={2}>
                    <Tag
                      color={
                        resetPreview.complete ? 'green' : resetPreview.allowReset ? 'orange' : 'red'
                      }
                    >
                      {t(
                        resetPreview.complete
                          ? 'consumer.previewComplete'
                          : resetPreview.allowReset
                            ? 'consumer.previewLimited'
                            : 'consumer.previewIncompleteStatus',
                      )}
                    </Tag>
                  </Descriptions.Item>
                </Descriptions>
                {resetPreviewWarnings.length > 0 && (
                  <Alert
                    showIcon
                    type={resetPreview.complete ? 'warning' : 'error'}
                    message={t('consumer.confirmImpactTitle')}
                    description={resetPreviewWarnings.join(t('consumer.previewMessageSeparator'))}
                  />
                )}
                <Table
                  columns={resetPreviewColumns}
                  dataSource={resetPreviewQueues}
                  rowKey={(row) => `${row.topic}-${row.broker}-${row.queueId}`}
                  pagination={false}
                  size="small"
                  tableLayout="fixed"
                  scroll={{ x: tableScrollX(resetPreviewColumns), y: 260 }}
                  locale={{ emptyText: t('consumer.noPreviewableQueues') }}
                />
              </Space>
            )}
          </Space>
        )}
      </Modal>
    </div>
  );
};

const ConsumerPage = () => {
  const instanceFilter = useInstanceFilter();
  return (
    <ConsumerPageContent
      key={instanceFilter.selectedInstanceId || 'no-selected-instance'}
      {...instanceFilter}
    />
  );
};

export default ConsumerPage;
