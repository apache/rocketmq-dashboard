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
import { formatDateTime } from '../../utils/format';
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
import { tableScrollX } from '../../utils/table';

const { Text } = Typography;

/* ─── Helpers ─── */

const UNKNOWN_LAG_COLOR = '#8c8c8c';
const UNAVAILABLE_LAG_LABEL = '不可用';

const lagColor = (lag: number): string => {
  // The backend reports -1 when the lag cannot be determined; do not color it
  // as healthy (green) or backlogged.
  if (!isLagAvailable(lag)) return UNKNOWN_LAG_COLOR;
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
  if (riskLevel === 'ERROR') return '失败';
  if (riskLevel === 'WARNING') return '需确认';
  return '正常';
};

const resetPreviewQueueMessage = (queue: ResetConsumerOffsetQueuePreview) => {
  const messages: string[] = [];
  if (queue.riskLevel === 'ERROR') {
    return queue.message || '预览失败';
  }
  if (queue.targetOffset < 0 || queue.consumerOffset < 0) {
    return queue.message || '目标位点不可用';
  }
  if (queue.offsetDelta < 0) {
    messages.push(`将回放 ${Math.abs(queue.offsetDelta).toLocaleString()} 条消息`);
  } else if (queue.offsetDelta > 0) {
    messages.push(`将跳过 ${queue.offsetDelta.toLocaleString()} 条未消费消息`);
  } else {
    messages.push('位点不变');
  }
  if (queue.minOffset >= 0 && queue.targetOffset === queue.minOffset) {
    messages.push('目标为最小保留位点');
  }
  if (queue.maxOffset >= 0 && queue.targetOffset === queue.maxOffset) {
    messages.push('目标为最新位点');
  }
  return messages.join('；');
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
}: ConsumerPageContentProps) => {
  const { t } = useLang();
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
  const [settingsForm] = Form.useForm<{ retryQueueNums: number; retryMaxTimes: number }>();
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
  const stackRequestIdRef = useRef(0);
  const settingsRequestIdRef = useRef(0);

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
    const requestId = ++groupRequestIdRef.current;
    const timer = window.setTimeout(() => {
      if (!silent) setLoading(true);
      void listConsumerGroupPage({
        instanceId: selectedInstanceId,
        search: search.trim() || undefined,
        page,
        pageSize,
      })
        .then((result) => {
          if (requestId === groupRequestIdRef.current) {
            setGroups(result.items);
            setTotalGroups(result.total);
          }
        })
        .catch(() => {
          if (requestId === groupRequestIdRef.current) message.error(t('consumer.fetchListFailed'));
        })
        .finally(() => {
          if (requestId === groupRequestIdRef.current) setLoading(false);
        });
    }, 0);
    return () => {
      window.clearTimeout(timer);
    };
  }, [t, selectedInstanceId, search, page, pageSize, instancesLoading, refreshKey]);

  useEffect(() => {
    if (!autoRefresh || !selectedInstanceId) {
      return undefined;
    }
    const interval = window.setInterval(() => triggerRefresh(true), 2000);
    return () => window.clearInterval(interval);
  }, [autoRefresh, selectedInstanceId, triggerRefresh]);

  const loadSubscriptions = useCallback(
    async (groupName: string, force = false) => {
      const cacheKey = diagnosticCacheKey(selectedInstanceId, groupName);
      if (!force && subscriptionsByGroup[cacheKey]) return;
      setSubscriptionLoadingByGroup((prev) => ({ ...prev, [cacheKey]: true }));
      setSubscriptionErrorByGroup((prev) => ({ ...prev, [cacheKey]: false }));
      try {
        const subscriptions = await getConsumerSubscriptions(
          groupName,
          selectedInstanceId || undefined,
        );
        setSubscriptionsByGroup((prev) => ({ ...prev, [cacheKey]: subscriptions }));
      } catch {
        setSubscriptionErrorByGroup((prev) => ({ ...prev, [cacheKey]: true }));
        message.error(t('consumer.fetchSubscriptionsFailed', { name: groupName }));
      } finally {
        setSubscriptionLoadingByGroup((prev) => ({ ...prev, [cacheKey]: false }));
      }
    },
    [subscriptionsByGroup, t, selectedInstanceId],
  );

  const loadProgress = useCallback(
    async (groupName: string, force = false, silent = false) => {
      const cacheKey = diagnosticCacheKey(selectedInstanceId, groupName);
      if (!force && progressByGroup[cacheKey]) return;
      try {
        const progress = await getConsumerProgress(groupName, selectedInstanceId || undefined);
        setProgressByGroup((prev) => ({ ...prev, [cacheKey]: progress }));
      } catch {
        if (!silent) message.error(t('consumer.fetchProgressFailed', { name: groupName }));
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
      } catch {
        // 自动刷新失败静默处理，避免每 2s 弹错
      } finally {
        inFlight = false;
      }
    };
    const interval = window.setInterval(() => void tick(), 2000);
    return () => window.clearInterval(interval);
  }, [modalOpen, selectedGroupName, selectedInstanceId, loadProgress]);

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
      message.success('Group 导出完成');
    } catch {
      message.error('导出 Group 失败，请稍后重试');
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
      }
    } catch {
      if (requestId === settingsRequestIdRef.current) {
        message.error('加载消费组配置失败，请稍后重试');
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
  };

  const saveSettings = async () => {
    if (!settingsGroup || !selectedInstanceId) return;
    const values = await settingsForm.validateFields();
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
      message.success('消费组配置已保存');
    } catch {
      message.error('保存消费组配置失败，请稍后重试');
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
  const selectedSubscriptions = selectedGroup
    ? (subscriptionsByGroup[selectedDiagnosticKey] ?? [])
    : [];
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

  const handlePreviewResetOffset = async () => {
    if (!resetGroup || !resetTopic) {
      message.warning('请先选择要重置的 Topic');
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
        message.success(`已预览 ${preview.queueCount} 个 Queue`);
      } else {
        message.warning('预览未覆盖可重置队列，请检查 Group/Topic 状态');
      }
    } catch (error) {
      const reason = error instanceof Error ? error.message : '预览重置影响失败';
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
      message.warning('请先预览并确认位点影响');
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
        `${resetGroup.name} 在 ${resetTopic} 的消费位点已重置到 ${resetTime.format('YYYY-MM-DD HH:mm:ss')}`,
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
      message.error('请先选择实例');
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
      setImportErrors([error instanceof Error ? error.message : 'CSV 解析失败']);
    } finally {
      if (importInputRef.current) importInputRef.current.value = '';
    }
  };

  const handleImportConsumerGroups = async () => {
    if (!selectedInstanceId) {
      message.error('请先选择实例');
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
              message: failure.message || '创建失败',
            }
          : { ...nextRows[index], status: 'success', message: '已创建' };
      });
    } catch (error) {
      for (const { index } of targetIndexes) {
        nextRows[index] = {
          ...nextRows[index],
          status: 'failed',
          message: error instanceof Error ? error.message : '创建失败',
        };
      }
    } finally {
      setImporting(false);
    }
    setImportRows([...nextRows]);

    if (createdGroups.length > 0) {
      setGroups((previous) => {
        const createdNames = new Set(createdGroups.map((group) => group.name));
        return [...createdGroups, ...previous.filter((group) => !createdNames.has(group.name))];
      });
    }

    const failedCount = nextRows.filter((row) => row.status === 'failed').length;
    const invalidCount = nextRows.filter((row) => row.status === 'invalid').length;
    if (failedCount === 0) {
      if (invalidCount > 0) {
        message.warning(`已导入 ${createdGroups.length} 个 Group，${invalidCount} 行无效已跳过`);
      } else {
        message.success(`已导入 ${createdGroups.length} 个 Group`);
      }
    } else if (createdGroups.length > 0) {
      message.warning(`已导入 ${createdGroups.length} 个 Group，${failedCount} 个失败`);
    } else {
      message.error(`${failedCount} 个 Group 导入失败`);
    }
  };

  const consumerGroupImportColumns: ColumnsType<ResourceImportRow<Partial<ConsumerGroup>>> = [
    { title: '行号', dataIndex: 'lineNumber', key: 'lineNumber', width: 80 },
    { title: 'Group 名称', dataIndex: 'name', key: 'name' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: ResourceImportRow<Partial<ConsumerGroup>>['status']) => {
        if (status === 'success') return <Tag color="success">成功</Tag>;
        if (status === 'failed') return <Tag color="error">失败</Tag>;
        if (status === 'invalid') return <Tag color="warning">无效</Tag>;
        return <Tag>待导入</Tag>;
      },
    },
    {
      title: '说明',
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
      title: 'Group 名称',
      dataIndex: 'name',
      key: 'name',
      width: 190,
      sorter: (a, b) => a.name.localeCompare(b.name),
      render: (name: string) => (
        <Tooltip title="点击复制名称">
          <Text
            strong
            style={{ fontSize: 14, cursor: 'pointer' }}
            onClick={() => {
              const done = () => message.success(`已复制：${name}`);
              const failed = () => message.error('复制失败，请手动复制');
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
      title: '订阅组类型',
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
      title: '订阅模式',
      dataIndex: 'subscriptionMode',
      key: 'subscriptionMode',
      width: 84,
      sorter: (a, b) => (a.subscriptionMode ?? '').localeCompare(b.subscriptionMode ?? ''),
      render: (mode: string) => <Tag color={mode === 'Push' ? 'blue' : 'green'}>{mode}</Tag>,
    },
    {
      title: '在线客户端',
      dataIndex: 'onlineInstances',
      key: 'onlineInstances',
      width: 100,
      align: 'center',
      sorter: (a, b) => (a.onlineInstances ?? 0) - (b.onlineInstances ?? 0),
    },
    {
      title: '总堆积量',
      dataIndex: 'totalLag',
      key: 'totalLag',
      width: 96,
      align: 'right',
      sorter: (a, b) => lagSortValue(a.totalLag) - lagSortValue(b.totalLag),
      render: (lag: number) =>
        isLagAvailable(lag) ? (
          lag.toLocaleString()
        ) : (
          <Text type="secondary">{UNAVAILABLE_LAG_LABEL}</Text>
        ),
    },
    {
      title: '消费延迟',
      dataIndex: 'delaySeconds',
      key: 'delaySeconds',
      width: 100,
      align: 'right',
      sorter: (a, b) => (a.delaySeconds ?? 0) - (b.delaySeconds ?? 0),
      render: (seconds: number) => formatDelay(seconds ?? 0),
    },
    {
      title: '创建时间',
      dataIndex: 'gmtCreate',
      key: 'gmtCreate',
      width: 156,
      sorter: (a, b) => (a.gmtCreate ?? '').localeCompare(b.gmtCreate ?? ''),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
          {formatDateTime(d)}
        </Text>
      ),
    },
    {
      title: '修改时间',
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
      title: '操作',
      key: 'actions',
      width: 232,
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
            详情
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
                onOk: async () => {
                  await deleteConsumerGroup(record.name, selectedInstanceId || undefined);
                  setGroups((prev) => prev.filter((group) => group.name !== record.name));
                  setSelectedRowKeys((prev) => prev.filter((key) => key !== record.name));
                  message.success(`消费组 ${record.name} 已删除`);
                },
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
  const subscriptionSubColumns = (groupName: string): ColumnsType<SubscriptionEntry> => [
    {
      title: 'Topic 主题',
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
      title: '订阅一致性',
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
          title="查看该 Topic 的队列分布"
          style={{ borderColor: '#1677ff', color: '#1677ff' }}
          onClick={() => {
            const group = groups.find((g) => g.name === groupName) ?? selectedGroup;
            if (group) openModal(group, 'progress', record.topic);
          }}
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
      width: 210,
      render: (id: string) => (
        <Text copyable style={{ fontSize: 14 }}>
          {id}
        </Text>
      ),
    },
    {
      title: '协议',
      dataIndex: 'protocol',
      key: 'protocol',
      width: 80,
      render: (protocol: string) => {
        const config = PROTOCOL_MAP[protocol] || { labelKey: protocol, color: 'default' };
        return <Tag color={config.color}>{t(config.labelKey)}</Tag>;
      },
    },
    {
      title: '地址',
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
      title: '最后心跳',
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
      title: '诊断',
      key: 'diagnostics',
      width: 90,
      render: (_: unknown, record: ConsumerInstance) => (
        <Button
          size="small"
          icon={<ListBullets size={14} />}
          onClick={() => void openStackModal(record)}
        >
          线程栈
        </Button>
      ),
    },
  ];

  /* ═══════════════════════════════════════════
     Modal: Queue Progress Tab
     ═══════════════════════════════════════════ */
  const queueColumns: ColumnsType<QueueProgress> = [
    {
      title: 'Topic 主题',
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
        if (!isLagAvailable(diff)) {
          return (
            <Text type="secondary" style={{ fontWeight: 600 }}>
              {UNAVAILABLE_LAG_LABEL}
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
      title: '当前位点',
      dataIndex: 'consumerOffset',
      key: 'consumerOffset',
      width: 120,
      align: 'right',
      render: (offset: number) => (
        <Text style={{ fontFamily: 'monospace' }}>{formatOffsetValue(offset)}</Text>
      ),
    },
    {
      title: '目标位点',
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
      title: '变化',
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
      title: '当前堆积',
      dataIndex: 'currentLag',
      key: 'currentLag',
      width: 110,
      align: 'right',
      render: (lag: number) => (
        <Text style={{ fontFamily: 'monospace' }}>{formatOffsetValue(lag)}</Text>
      ),
    },
    {
      title: '重置后堆积',
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
      title: '风险',
      dataIndex: 'riskLevel',
      key: 'riskLevel',
      width: 86,
      render: (riskLevel: string) => (
        <Tag color={resetPreviewRiskColor(riskLevel)}>{resetPreviewRiskLabel(riskLevel)}</Tag>
      ),
    },
    {
      title: '说明',
      key: 'message',
      width: 240,
      ellipsis: true,
      render: (_: unknown, record: ResetConsumerOffsetQueuePreview) => (
        <Text style={{ fontSize: 14 }} title={resetPreviewQueueMessage(record)}>
          {resetPreviewQueueMessage(record)}
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
        subtitle={`管理消费者组订阅关系与消费进度，共 ${totalGroups} 个 Group`}
      />

      {/* ─── Filter Bar ─── */}
      <Flex justify="space-between" align="center" style={{ marginBottom: 16 }}>
        <Space size={12} wrap>
          <InstanceSelect
            value={selectedInstanceId || undefined}
            onChange={selectInstance}
            options={instanceOptions}
            style={{ width: 220 }}
          />
          <Input.Search
            placeholder="搜索 Group 名称或 Topic"
            allowClear
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(1);
            }}
            onSearch={(value) => {
              setSearch(value);
              setPage(1);
            }}
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
                  onOk: async () => {
                    const names = selectedRowKeys.map(String);
                    const { deleted, failed } = await batchDeleteConsumerGroups(
                      names,
                      selectedInstanceId || undefined,
                    );
                    setGroups((prev) => prev.filter((g) => !deleted.includes(g.name)));
                    if (failed.length > 0) {
                      message.warning(
                        `已删除 ${deleted.length} 个，失败 ${failed.length} 个：${failed.join(', ')}`,
                      );
                      setSelectedRowKeys((prev) =>
                        prev.filter((key) => !deleted.includes(String(key))),
                      );
                    } else {
                      message.success(`已删除 ${deleted.length} 个 Group`);
                      setSelectedRowKeys([]);
                    }
                  },
                });
              }}
            >
              删除 ({selectedRowKeys.length})
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
            导入
          </Button>
          <Button icon={<ExportOutlined />} loading={exporting} onClick={() => void handleExport()}>
            导出
          </Button>
          <Button
            type="primary"
            icon={<Plus size={14} weight="bold" />}
            disabled={!hasSelectedInstance}
            onClick={() => setCreateModalOpen(true)}
          >
            创建 Group
          </Button>
          <Tooltip title="开启后每 2 秒自动刷新列表">
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
              自动刷新
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
            showTotal: (total) => `共 ${total} 个 Group`,
            pageSizeOptions: [10, 20, 50, 100],
            onChange: (nextPage, nextPageSize) => {
              setPage(nextPage);
              setPageSize(nextPageSize);
            },
          }}
          size="small"
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
                <SyncOutlined style={{ marginRight: 4 }} />{t('consumer.autoRefresh2s')}
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
        width={detailTab === 'progress' ? 1080 : 800}
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
                    <span>{t('consumer.overviewTab')}</span>
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
                            title={t('consumer.onlineInstances')}
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
                            title={t('consumer.totalLagShort')}
                            value={selectedGroup.totalLag}
                            formatter={(value) => formatLag(Number(value), UNAVAILABLE_LAG_LABEL)}
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
                            title={t('consumer.subTopicCountStat')}
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
                      <Descriptions.Item label={t('consumer.name')}>
                        <Text strong>{selectedGroup.name}</Text>
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.cluster')}>
                        {selectedGroup.clusterId}
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.subMode')}>
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
                      <Descriptions.Item label={t('consumer.subGroupType')}>
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
                      <Descriptions.Item label={t('consumer.delay')}>
                        <Text strong>{formatDelay(selectedGroup.delaySeconds)}</Text>
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.maxRetry')}>
                        <Text strong>{selectedGroup.retryMaxTimes}</Text>{t('consumer.timesSuffix')}
                      </Descriptions.Item>
                      <Descriptions.Item label={t('consumer.createdAt')} span={2}>
                        <Space size={4}>
                          <Clock size={13} color="#9CA3AF" />
                          <Text type="secondary">{selectedGroup.gmtCreate}</Text>
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
                          {t('consumer.onlineInstances')} ({(selectedGroup.instances ?? []).length})
                        </Text>
                      </Flex>
                      <Table
                        columns={instanceColumns}
                        dataSource={selectedGroup.instances ?? []}
                        rowKey="clientId"
                        pagination={false}
                        size="small"
                        scroll={{ x: tableScrollX(instanceColumns) }}
                      />
                    </div>

                    {/* 订阅关系 */}
                    <div style={{ marginTop: 24 }}>
                      <Flex justify="space-between" align="center" style={{ marginBottom: 12 }}>
                        <Flex align="center" gap={6}>
                          <ListBullets size={15} color="#1677ff" />
                          <Text strong style={{ fontSize: 14 }}>
                            订阅一致性检查
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
                          重新检查
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
                            ? '订阅一致性检查失败，当前保留上次检查结果'
                            : subscriptionLoadingByGroup[selectedDiagnosticKey] &&
                                selectedSubscriptions.length === 0
                              ? '正在检查订阅一致性'
                              : inconsistentSubscriptions.length > 0
                                ? `发现 ${inconsistentSubscriptions.length} 个订阅配置不一致`
                                : unknownSubscriptions.length > 0
                                  ? `${unknownSubscriptions.length} 个订阅配置状态未知`
                                  : selectedSubscriptions.length > 0
                                    ? `全部 ${selectedSubscriptions.length} 个订阅配置一致`
                                    : '暂无订阅关系可检查'
                        }
                        action={
                          <Checkbox
                            checked={showOnlyInconsistent}
                            disabled={inconsistentSubscriptions.length === 0}
                            onChange={(event) => setShowOnlyInconsistent(event.target.checked)}
                          >
                            仅看不一致
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
                    {progressTopicOptions.length > 0 && (
                      <Flex align="center" gap={8} style={{ marginBottom: 12 }}>
                        <Text type="secondary">Topic 筛选:</Text>
                        <Select
                          size="small"
                          style={{ minWidth: 240 }}
                          allowClear
                          placeholder="全部 Topic"
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
                          <Text type="secondary">总 Broker 数:</Text>
                          <Text strong>{new Set(visibleProgress.map((q) => q.broker)).size}</Text>
                        </Space>
                        <Space size={4}>
                          <Text type="secondary">总 Queue 数:</Text>
                          <Text strong>{visibleProgress.length}</Text>
                        </Space>
                        <Space size={4}>
                          <Text type="secondary">总堆积:</Text>
                          {hasUnknownProgressLag ? (
                            <Text strong style={{ color: UNKNOWN_LAG_COLOR }}>
                              {UNAVAILABLE_LAG_LABEL}
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
                      scroll={{ x: tableScrollX(queueColumns), y: 380 }}
                      locale={{ emptyText: '消费组不在线，暂无队列进度数据' }}
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
                    <span>配置</span>
                  </Space>
                ),
                disabled: isCloudInstance,
                children: (
                  <Spin spinning={settingsLoading}>
                    <Form form={settingsForm} layout="vertical" style={{ maxWidth: 480 }}>
                      <Form.Item label="Group 名称">
                        <Text strong>{selectedGroup.name}</Text>
                      </Form.Item>
                      <Form.Item
                        label="重试队列数"
                        name="retryQueueNums"
                        rules={[{ required: true, message: '请输入重试队列数' }]}
                      >
                        <InputNumber min={1} max={128} style={{ width: '100%' }} />
                      </Form.Item>
                      <Form.Item
                        label="最大重试次数"
                        name="retryMaxTimes"
                        rules={[{ required: true, message: '请输入最大重试次数' }]}
                      >
                        <InputNumber min={1} max={128} style={{ width: '100%' }} />
                      </Form.Item>
                      <Form.Item style={{ marginBottom: 0 }}>
                        <Button
                          type="primary"
                          loading={settingsSubmitting}
                          onClick={() => void saveSettings()}
                        >
                          保存
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
            <span>消费者线程栈</span>
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
            <Descriptions.Item label="采集时间">
              {selectedStack?.capturedAt ? formatDateTime(selectedStack.capturedAt) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="线程数">{selectedStack?.threadCount ?? 0}</Descriptions.Item>
          </Descriptions>

          {stackLoading ? (
            <Table
              loading
              columns={[{ title: '线程', dataIndex: 'threadName', key: 'threadName' }]}
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
              message="暂不支持采集该客户端的线程栈"
              description={
                <>
                  <div>
                    经 Proxy 接入的客户端（gRPC、经 Proxy 的 Remoting）只在 Proxy 侧保持连接，Broker
                    看不到它们；而 Proxy 目前未开放线程栈采集接口，因此这类客户端暂时无法采集。 直连
                    Broker 的客户端可正常查看。
                  </div>
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
              if (!selectedInstanceId) {
                message.error('请先选择实例');
                return;
              }
              Modal.confirm({
                title: '确认创建',
                content: `将创建消费组 "${values.name}"`,
                okText: '确认创建',
                cancelText: '取消',
                onOk: async () => {
                  setSubmitting(true);
                  try {
                    const created = await createConsumerGroup({
                      name: values.name,
                      subscriptionMode: values.subscriptionMode,
                      consumeType: values.consumeType,
                      retryMaxTimes: values.retryMaxTimes,
                      subscriptionDataType: values.dataType || 'NORMAL',
                      deliveryOrderType: values.deliveryOrderType,
                      subscribedTopics: [],
                      instanceId: selectedInstanceId,
                    });
                    setGroups((prev) => [
                      created,
                      ...prev.filter((group) => group.name !== created.name),
                    ]);
                    message.success(`消费组 ${values.name} 创建成功`);
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
        okText="创建"
        cancelText="取消"
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
            label="Group 名称"
            name="name"
            rules={[
              { required: true, message: '请输入 Group 名称' },
              {
                pattern: RESOURCE_NAME_PATTERN,
                message: '仅支持字母、数字、下划线、短横线、% 和 |',
              },
              {
                max: RESOURCE_NAME_MAX_LENGTH.group,
                message: `名称不能超过 ${RESOURCE_NAME_MAX_LENGTH.group} 个字符`,
              },
            ]}
          >
            <Input placeholder="例：cg-order-notify" />
          </Form.Item>

          {!isCloudInstance && (
            <Form.Item label="订阅模式" name="subscriptionMode">
              <Radio.Group>
                <Radio.Button value="Push">Push</Radio.Button>
                <Radio.Button value="Pop">Pop</Radio.Button>
              </Radio.Group>
            </Form.Item>
          )}

          {!isCloudInstance && (
            <Form.Item label="消费类型" name="consumeType">
              <Radio.Group>
                <Radio.Button value="CLUSTERING">集群消费</Radio.Button>
                <Radio.Button value="BROADCASTING">广播消费</Radio.Button>
              </Radio.Group>
            </Form.Item>
          )}

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
                    value: 'MESSAGES_ORDER',
                    label: '全局顺序',
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
        title={`导入 Group${importFilename ? `：${importFilename}` : ''}`}
        open={importModalOpen}
        onCancel={() => {
          if (!importing) setImportModalOpen(false);
        }}
        onOk={() => void handleImportConsumerGroups()}
        okText={importRows.some((row) => row.status === 'failed') ? '重试失败项' : '开始导入'}
        cancelText="关闭"
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
              message="CSV 无法导入"
              description={importErrors.join('；')}
            />
          ) : importRows.some((row) => row.status === 'invalid') ? (
            <Alert
              type="warning"
              showIcon
              message={`检测到 ${
                importRows.filter((row) => row.status === 'invalid').length
              } 行无效，将跳过这些行`}
              description="仅导入可创建字段；CSV 中的 Namespace、Cluster ID 和运行状态列会被忽略。"
            />
          ) : (
            <Alert
              type="info"
              showIcon
              message={`检测到 ${importRows.length} 个 Group，将通过后端批量导入`}
              description="仅导入可创建字段；CSV 中的 Namespace、Cluster ID 和运行状态列会被忽略。"
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
            <span>重置消费位点</span>
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
        okText="确认重置"
        cancelText="取消"
        width={1200}
        destroyOnHidden
      >
        {resetGroup && (
          <Space direction="vertical" size={16} style={{ width: '100%', marginTop: 16 }}>
            <Alert
              showIcon
              type="warning"
              message="此操作将影响消息消费进度"
              description="请先预览每个 Queue 的目标位点和堆积变化，确认预览结果后再执行重置。预览为时点快照、属页面操作引导（非服务端控制），预览期间消息持续写入，实际效果以执行时 Broker 状态为准。"
            />
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 4 }}>
                目标 Group
              </Text>
              <Text strong style={{ fontSize: 14 }}>
                {resetGroup.name}
              </Text>
            </div>
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 8 }}>
                目标 Topic
              </Text>
              <Select
                aria-label="目标 Topic"
                showSearch
                optionFilterProp="label"
                style={{ width: '100%' }}
                value={resetTopic}
                options={resetTopicOptions}
                loading={subscriptionLoadingByGroup[resetDiagnosticKey]}
                placeholder="选择要重置消费位点的 Topic"
                onChange={(value) => {
                  setResetTopic(value);
                  clearResetPreview();
                }}
                notFoundContent={
                  subscriptionErrorByGroup[resetDiagnosticKey]
                    ? '订阅 Topic 加载失败'
                    : '该 Group 暂无订阅 Topic'
                }
              />
            </div>
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 8 }}>
                重置到以下时间点
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
                placeholder="选择重置时间点"
              />
            </div>
            <div>
              <Text type="secondary" style={{ fontSize: 14, display: 'block', marginBottom: 8 }}>
                快捷选择
              </Text>
              <Space wrap>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs().subtract(1, 'hour'));
                    clearResetPreview();
                  }}
                >
                  1 小时前
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs().subtract(3, 'hour'));
                    clearResetPreview();
                  }}
                >
                  3 小时前
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs().subtract(6, 'hour'));
                    clearResetPreview();
                  }}
                >
                  6 小时前
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs().subtract(12, 'hour'));
                    clearResetPreview();
                  }}
                >
                  12 小时前
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs().subtract(1, 'day'));
                    clearResetPreview();
                  }}
                >
                  1 天前
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setResetTime(dayjs().subtract(3, 'day'));
                    clearResetPreview();
                  }}
                >
                  3 天前
                </Button>
              </Space>
            </div>
            <Flex justify="space-between" align="center" gap={12}>
              <Text type="secondary" style={{ fontSize: 14 }}>
                预览不会修改 broker 位点，仅计算目标时间对应的 Queue offset。
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
                预览影响
              </Button>
            </Flex>
            {resetPreviewError && (
              <Alert showIcon type="error" message="位点预览失败" description={resetPreviewError} />
            )}
            {hasCurrentResetPreview && resetPreview && (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Descriptions bordered size="small" column={4}>
                  <Descriptions.Item label="Queue 数">{resetPreview.queueCount}</Descriptions.Item>
                  <Descriptions.Item label="当前总堆积">
                    {formatOffsetValue(resetPreview.currentTotalLag)}
                  </Descriptions.Item>
                  <Descriptions.Item label="重置后总堆积">
                    {formatOffsetValue(resetPreview.projectedTotalLag)}
                  </Descriptions.Item>
                  <Descriptions.Item label="位点净变化">
                    {formatOffsetDelta(resetPreview.totalOffsetDelta)}
                  </Descriptions.Item>
                  <Descriptions.Item label="回放 Queue">
                    {resetPreview.rewindQueueCount}
                  </Descriptions.Item>
                  <Descriptions.Item label="跳过 Queue">
                    {resetPreview.fastForwardQueueCount}
                  </Descriptions.Item>
                  <Descriptions.Item label="预览状态" span={2}>
                    <Tag
                      color={
                        resetPreview.complete ? 'green' : resetPreview.allowReset ? 'orange' : 'red'
                      }
                    >
                      {resetPreview.complete ? '完整' : resetPreview.allowReset ? '有限' : '不完整'}
                    </Tag>
                  </Descriptions.Item>
                </Descriptions>
                {resetPreviewWarnings.length > 0 && (
                  <Alert
                    showIcon
                    type={resetPreview.complete ? 'warning' : 'error'}
                    message="请确认以下影响"
                    description={resetPreviewWarnings.join('；')}
                  />
                )}
                <Table
                  columns={resetPreviewColumns}
                  dataSource={resetPreviewQueues}
                  rowKey={(row) => `${row.topic}-${row.broker}-${row.queueId}`}
                  pagination={false}
                  size="small"
                  scroll={{ x: tableScrollX(resetPreviewColumns), y: 260 }}
                  locale={{ emptyText: '未找到可预览的 Queue 位点' }}
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
