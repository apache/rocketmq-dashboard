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
import { formatDateTime } from '../../utils/format';
import type {
  ConsumerGroup,
  ConsumerInstance,
  QueueProgress,
  SubscriptionEntry,
} from '../../api/metadata';
import {
  batchDeleteConsumerGroups,
  createConsumerGroup,
  deleteConsumerGroup,
  getConsumerProgress,
  getConsumerSubscriptions,
  listConsumerGroups,
  resetConsumerOffset,
} from '../../services/consumerService';
import { useInstanceFilter } from '../../hooks/useInstanceFilter';
import {
  parseCsvTable,
  validateConsumerGroupCsvImport,
  type ResourceImportRow,
} from '../../utils/resourceCsvImport';

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

const GROUP_EXPORT_COLUMNS: Array<{ header: string; value: (group: ConsumerGroup) => unknown }> = [
  { header: 'Name', value: (group) => group.name },
  { header: 'Namespace', value: (group) => group.namespace },
  { header: 'Cluster ID', value: (group) => group.clusterId },
  { header: 'Subscription Mode', value: (group) => group.subscriptionMode },
  { header: 'Consume Type', value: (group) => group.consumeType },
  { header: 'Online Instances', value: (group) => group.onlineInstances },
  { header: 'Total Lag', value: (group) => group.totalLag },
  { header: 'Delay Seconds', value: (group) => group.delaySeconds },
  { header: 'Subscription Data Type', value: (group) => group.subscriptionDataType },
  { header: 'Delivery Order Type', value: (group) => group.deliveryOrderType },
  { header: 'Retry Max Times', value: (group) => group.retryMaxTimes },
  { header: 'Subscribed Topics', value: (group) => (group.subscribedTopics ?? []).join(';') },
  { header: 'Created At', value: (group) => group.createdAt },
  { header: 'Updated At', value: (group) => group.updatedAt },
];

const escapeCsvCell = (value: unknown) => {
  const text = value == null ? '' : String(value);
  const formulaSafeText = /^[=+\-@]/.test(text) ? `'${text}` : text;
  return `"${formulaSafeText.replace(/"/g, '""')}"`;
};

const buildConsumerGroupCsv = (groups: ConsumerGroup[]) =>
  [
    GROUP_EXPORT_COLUMNS.map((column) => escapeCsvCell(column.header)).join(','),
    ...groups.map((group) =>
      GROUP_EXPORT_COLUMNS.map((column) => escapeCsvCell(column.value(group))).join(','),
    ),
  ].join('\n');

const downloadCsv = (filename: string, csv: string) => {
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.style.display = 'none';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
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

/* ═══════════════════════════════════════════
   ConsumerPage
   ═══════════════════════════════════════════ */
const ConsumerPage = () => {
  const { t } = useLang();
  const { selectedInstanceId, selectedInstance, selectInstance, instanceOptions } =
    useInstanceFilter();
  const isCloudInstance =
    selectedInstance?.vendor === 'ALIYUN' || selectedInstance?.vendor === 'TENCENT';
  const hasSelectedInstance = Boolean(selectedInstanceId);
  const [groups, setGroups] = useState<ConsumerGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [resetSubmitting, setResetSubmitting] = useState(false);
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
  const importInputRef = useRef<HTMLInputElement>(null);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importFilename, setImportFilename] = useState('');
  const [importRows, setImportRows] = useState<ResourceImportRow<Partial<ConsumerGroup>>[]>([]);
  const [importErrors, setImportErrors] = useState<string[]>([]);
  const [importing, setImporting] = useState(false);

  const groupRequestIdRef = useRef(0);

  useEffect(() => {
    if (!selectedInstanceId) {
      return;
    }
    const requestId = ++groupRequestIdRef.current;
    const timer = window.setTimeout(() => {
      setLoading(true);
      void listConsumerGroups({ instanceId: selectedInstanceId })
        .then((nextGroups) => {
          if (requestId === groupRequestIdRef.current) setGroups(nextGroups);
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
  }, [t, selectedInstanceId]);

  const loadSubscriptions = useCallback(
    async (groupName: string, force = false) => {
      if (!force && subscriptionsByGroup[groupName]) return;
      setSubscriptionLoadingByGroup((prev) => ({ ...prev, [groupName]: true }));
      setSubscriptionErrorByGroup((prev) => ({ ...prev, [groupName]: false }));
      try {
        const subscriptions = await getConsumerSubscriptions(
          groupName,
          selectedInstanceId || undefined,
        );
        setSubscriptionsByGroup((prev) => ({ ...prev, [groupName]: subscriptions }));
      } catch {
        setSubscriptionErrorByGroup((prev) => ({ ...prev, [groupName]: true }));
        message.error(t('consumer.fetchSubscriptionsFailed', { name: groupName }));
      } finally {
        setSubscriptionLoadingByGroup((prev) => ({ ...prev, [groupName]: false }));
      }
    },
    [subscriptionsByGroup, t, selectedInstanceId],
  );

  const loadProgress = useCallback(
    async (groupName: string) => {
      if (progressByGroup[groupName]) return;
      try {
        const progress = await getConsumerProgress(groupName, selectedInstanceId || undefined);
        setProgressByGroup((prev) => ({ ...prev, [groupName]: progress }));
      } catch {
        message.error(t('consumer.fetchProgressFailed', { name: groupName }));
      }
    },
    [progressByGroup, t, selectedInstanceId],
  );

  /* ─── Filtered & sorted data ─── */
  const filtered = useMemo(() => {
    let data = groups.filter(
      (g) => g.name.includes(search) || (g.subscribedTopics ?? []).some((t) => t.includes(search)),
    );

    if (selectedInstanceId) {
      data = data.filter((g) => g.instanceId === selectedInstanceId);
    }

    if (modeFilter !== 'ALL') {
      data = data.filter((g) => g.subscriptionMode === modeFilter);
    }

    if (sortKey === 'lag_desc') {
      data = [...data].sort((a, b) => b.totalLag - a.totalLag);
    } else if (sortKey === 'name_asc') {
      data = [...data].sort((a, b) => a.name.localeCompare(b.name));
    }

    return data;
  }, [groups, search, modeFilter, sortKey, selectedInstanceId]);

  /* ─── Open detail modal ─── */
  const openModal = (group: ConsumerGroup) => {
    setSelectedGroup(group);
    setShowOnlyInconsistent(false);
    setModalOpen(true);
    void loadSubscriptions(group.name);
    void loadProgress(group.name);
  };

  const selectedSubscriptions = selectedGroup
    ? (subscriptionsByGroup[selectedGroup.name] ?? [])
    : [];
  const inconsistentSubscriptions = selectedSubscriptions.filter(isInconsistentSubscription);
  const unknownSubscriptions = selectedSubscriptions.filter(
    (subscription) =>
      !isConsistentSubscription(subscription) && !isInconsistentSubscription(subscription),
  );
  const visibleSubscriptions = showOnlyInconsistent
    ? inconsistentSubscriptions
    : selectedSubscriptions;
  const selectedProgress = selectedGroup ? (progressByGroup[selectedGroup.name] ?? []) : [];

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
    const createdGroups: ConsumerGroup[] = [];

    for (const { row, index } of targetIndexes) {
      try {
        const created = await createConsumerGroup(row.payload);
        createdGroups.push(created);
        nextRows[index] = { ...nextRows[index], status: 'success', message: '已创建' };
      } catch (error) {
        nextRows[index] = {
          ...nextRows[index],
          status: 'failed',
          message: error instanceof Error ? error.message : '创建失败',
        };
      }
      setImportRows([...nextRows]);
    }

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
    setImporting(false);
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
      width: 90,
      sorter: (a, b) => (a.subscriptionMode ?? '').localeCompare(b.subscriptionMode ?? ''),
      render: (mode: string) => <Tag color={mode === 'Push' ? 'blue' : 'green'}>{mode}</Tag>,
    },
    {
      title: '在线客户端',
      dataIndex: 'onlineInstances',
      key: 'onlineInstances',
      width: 130,
      align: 'center',
      sorter: (a, b) => (a.onlineInstances ?? 0) - (b.onlineInstances ?? 0),
    },
    {
      title: '总堆积量',
      dataIndex: 'totalLag',
      key: 'totalLag',
      width: 120,
      sorter: (a, b) => (a.totalLag ?? 0) - (b.totalLag ?? 0),
      render: (lag: number) => (lag ?? 0).toLocaleString(),
    },
    {
      title: '消费延迟',
      dataIndex: 'delaySeconds',
      key: 'delaySeconds',
      width: 160,
      sorter: (a, b) => (a.delaySeconds ?? 0) - (b.delaySeconds ?? 0),
      render: (seconds: number) => formatDelay(seconds ?? 0),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      sorter: (a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? ''),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {formatDateTime(d)}
        </Text>
      ),
    },
    {
      title: '修改时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 170,
      sorter: (a, b) => (a.updatedAt ?? '').localeCompare(b.updatedAt ?? ''),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {formatDateTime(d)}
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
        const config = PROTOCOL_MAP[protocol] || { labelKey: protocol, color: 'default' };
        return <Tag color={config.color}>{t(config.labelKey)}</Tag>;
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
          <Select
            placeholder="选择实例"
            value={selectedInstanceId || undefined}
            onChange={selectInstance}
            options={instanceOptions}
            style={{ width: 220 }}
            notFoundContent="暂无实例"
          />
          <Input.Search
            placeholder="搜索 Group 名称或 Topic"
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
                  onOk: async () => {
                    const names = selectedRowKeys.map(String);
                    await batchDeleteConsumerGroups(names, selectedInstanceId || undefined);
                    setGroups((prev) => prev.filter((g) => !names.includes(g.name)));
                    message.success(`已删除 ${selectedRowKeys.length} 个 Group`);
                    setSelectedRowKeys([]);
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
          <Button
            icon={<ExportOutlined />}
            onClick={() => {
              downloadCsv(
                `rocketmq-consumer-groups-${new Date().toISOString().slice(0, 10)}.csv`,
                buildConsumerGroupCsv(filtered),
              );
              message.success(`已导出 ${filtered.length} 个 Group`);
            }}
          >
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
        </Space>
      </Flex>

      {/* ─── Table with expandable rows ─── */}
      <Card bodyStyle={{ padding: 0 }}>
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
            pageSize: 20,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 个 Group`,
          }}
          size="small"
          expandable={{
            onExpand: (expanded, record) => {
              if (expanded) void loadSubscriptions(record.name);
            },
            expandedRowRender: (record) => (
              <div style={{ padding: '8px 0' }}>
                <Table
                  columns={subscriptionSubColumns}
                  dataSource={subscriptionsByGroup[record.name] ?? []}
                  rowKey="topic"
                  loading={subscriptionLoadingByGroup[record.name]}
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
            </Space>
          ) : (
            'Group 详情'
          )
        }
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          setSelectedGroup(null);
          setShowOnlyInconsistent(false);
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
                          {TOPIC_TYPE_MAP[selectedGroup.subscriptionDataType]
                            ? t(TOPIC_TYPE_MAP[selectedGroup.subscriptionDataType].labelKey)
                            : selectedGroup.subscriptionDataType}
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
                          loading={subscriptionLoadingByGroup[selectedGroup.name]}
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
                          subscriptionErrorByGroup[selectedGroup.name]
                            ? 'error'
                            : inconsistentSubscriptions.length > 0 ||
                                unknownSubscriptions.length > 0
                              ? 'warning'
                              : selectedSubscriptions.length > 0
                                ? 'success'
                                : 'info'
                        }
                        message={
                          subscriptionErrorByGroup[selectedGroup.name]
                            ? '订阅一致性检查失败，当前保留上次检查结果'
                            : subscriptionLoadingByGroup[selectedGroup.name] &&
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
                        columns={subscriptionSubColumns}
                        dataSource={visibleSubscriptions}
                        rowKey="topic"
                        loading={subscriptionLoadingByGroup[selectedGroup.name]}
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
                          <Text strong>{new Set(selectedProgress.map((q) => q.broker)).size}</Text>
                        </Space>
                        <Space size={4}>
                          <Text type="secondary">总 Queue 数:</Text>
                          <Text strong>{selectedProgress.length}</Text>
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
                      dataSource={selectedProgress}
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
        destroyOnClose
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
                pattern: /^[a-zA-Z][a-zA-Z0-9_-]*$/,
                message: '名称以字母开头，仅包含字母、数字、下划线和短横线',
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
        destroyOnClose
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
              message={`检测到 ${importRows.length} 个 Group，将按顺序调用创建接口`}
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
        }}
        onOk={async () => {
          if (resetGroup) {
            setResetSubmitting(true);
            try {
                await resetConsumerOffset({
                  name: resetGroup.name,
                  instanceId: selectedInstanceId || undefined,
                  timestamp: resetTime.valueOf(),
                });
              message.success(
                `${resetGroup.name} 消费位点已重置到 ${resetTime.format('YYYY-MM-DD HH:mm:ss')}`,
              );
            } catch {
              message.error(t('consumer.resetFailed'));
              return;
            } finally {
              setResetSubmitting(false);
            }
          }
          setResetModalOpen(false);
          setResetGroup(null);
        }}
        confirmLoading={resetSubmitting}
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
