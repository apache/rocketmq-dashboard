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

import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Card,
  Table,
  Tag,
  Input,
  Select,
  DatePicker,
  Flex,
  Button,
  Modal,
  InputNumber,
  Typography,
  message,
  Space,
  Tooltip,
} from 'antd';
import { Trash } from '@phosphor-icons/react';
import { DownloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import type { AuditFilter, AuditFilterOptions, AuditSummary } from '../../api/audit';
import type { AuditRecord } from '../../api/ops';
import {
  cleanupAuditLogs,
  exportAuditLogs,
  getAuditFilterOptions,
  getAuditSummary,
  listAuditRecords,
} from '../../services/opsService';
import { downloadBlob } from '../../utils/download';
import { formatDateTime } from '../../utils/format';
import { tableScrollX } from '../../utils/table';
import {
  describeAuditRecord,
  getAuditOperationPresentation,
  getAuditResourcePresentation,
  getAuditResultPresentation,
  parseAuditDetail,
} from './auditPresentation';
import AuditSummaryCards from './AuditSummaryCards';

const emptyFilterOptions: AuditFilterOptions = {
  operationTypes: [],
  resourceTypes: [],
  clusterIds: [],
  results: [],
};

const buildAuditFilter = (
  searchText: string,
  selectedType: string | undefined,
  selectedResourceType: string | undefined,
  selectedClusterId: string | undefined,
  dateRange: [Dayjs | null, Dayjs | null] | null,
  resultFilter: string,
): AuditFilter => ({
  search: searchText || undefined,
  operationType: selectedType,
  resourceType: selectedResourceType,
  clusterId: selectedClusterId,
  startDate: dateRange?.[0]?.format('YYYY-MM-DD'),
  endDate: dateRange?.[1]?.format('YYYY-MM-DD'),
  result: resultFilter === 'all' ? undefined : resultFilter,
});

const AuditPage: React.FC = () => {
  const { t } = useLang();
  const [records, setRecords] = useState<AuditRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);
  const [searchText, setSearchText] = useState('');
  const [debouncedSearchText, setDebouncedSearchText] = useState('');
  const [selectedType, setSelectedType] = useState<string | undefined>(undefined);
  const [selectedResourceType, setSelectedResourceType] = useState<string | undefined>(undefined);
  const [selectedClusterId, setSelectedClusterId] = useState<string | undefined>(undefined);
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [resultFilter, setResultFilter] = useState('all');
  const [filterOptions, setFilterOptions] = useState<AuditFilterOptions>(emptyFilterOptions);
  const [cleanupModalOpen, setCleanupModalOpen] = useState(false);
  const [cleanupDays, setCleanupDays] = useState(30);
  const [exporting, setExporting] = useState(false);
  const [summary, setSummary] = useState<AuditSummary | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const recordsRequestRef = useRef(0);
  const filterOptionsRequestRef = useRef(0);

  useEffect(() => {
    const requestId = ++filterOptionsRequestRef.current;

    void getAuditFilterOptions()
      .then((options) => {
        if (filterOptionsRequestRef.current === requestId) setFilterOptions(options);
      })
      .catch(() => {
        if (filterOptionsRequestRef.current === requestId) {
          setFilterOptions(emptyFilterOptions);
        }
      });
  }, [refreshKey]);

  // Debounce free-text search so the record list is not re-fetched on every
  // keystroke; typing pauses for 300ms before the query hits the server.
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearchText(searchText), 300);
    return () => window.clearTimeout(timer);
  }, [searchText]);

  useEffect(() => {
    const requestId = ++recordsRequestRef.current;
    void Promise.resolve().then(() => {
      if (recordsRequestRef.current === requestId) setLoading(true);
    });

    void listAuditRecords({
      page,
      pageSize,
      ...buildAuditFilter(
        debouncedSearchText,
        selectedType,
        selectedResourceType,
        selectedClusterId,
        dateRange,
        resultFilter,
      ),
    })
      .then((result) => {
        if (recordsRequestRef.current !== requestId) return;
        setRecords(result.items);
        setTotal(result.total);
        if (result.items.length === 0 && result.total > 0 && page > 1) {
          setPage(Math.max(1, Math.ceil(result.total / pageSize)));
          return;
        }
      })
      .catch(() => {
        if (recordsRequestRef.current === requestId) {
          message.error('审计日志加载失败，请稍后重试');
        }
      })
      .finally(() => {
        if (recordsRequestRef.current === requestId) setLoading(false);
      });
  }, [
    page,
    pageSize,
    debouncedSearchText,
    selectedType,
    selectedResourceType,
    selectedClusterId,
    dateRange,
    resultFilter,
    refreshKey,
  ]);

  useEffect(
    () => () => {
      recordsRequestRef.current += 1;
      filterOptionsRequestRef.current += 1;
    },
    [],
  );

  const activeFilter = useMemo(
    () =>
      buildAuditFilter(
        debouncedSearchText,
        selectedType,
        selectedResourceType,
        selectedClusterId,
        dateRange,
        resultFilter,
      ),
    [
      debouncedSearchText,
      selectedType,
      selectedResourceType,
      selectedClusterId,
      dateRange,
      resultFilter,
    ],
  );

  useEffect(() => {
    let cancelled = false;
    // Reset the loading flag whenever the filters change so a stale summary is
    // not shown while the refreshed aggregate is still in flight. The microtask
    // mirrors the record-list effect so the flag update is not applied synchronously.
    void Promise.resolve().then(() => {
      if (!cancelled) setSummaryLoading(true);
    });
    void getAuditSummary(activeFilter)
      .then((value) => {
        if (!cancelled) setSummary(value);
      })
      .catch(() => {
        if (!cancelled) message.error('审计概览加载失败，请稍后重试');
      })
      .finally(() => {
        if (!cancelled) setSummaryLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeFilter, refreshKey]);

  const { Text } = Typography;

  const renderOperationType = (type: string) => {
    const presentation = getAuditOperationPresentation(type);
    return (
      <Tooltip title={type}>
        <Tag color={presentation.color}>
          {presentation.labelKey ? t(presentation.labelKey) : presentation.label}
        </Tag>
      </Tooltip>
    );
  };

  const renderResourceType = (type: string) => {
    const presentation = getAuditResourcePresentation(type);
    return (
      <Tooltip title={type}>
        <Tag color={presentation.color}>
          {presentation.labelKey ? t(presentation.labelKey) : presentation.label}
        </Tag>
      </Tooltip>
    );
  };

  const renderResult = (result: string) => {
    const presentation = getAuditResultPresentation(result);
    return (
      <Tooltip title={result}>
        <Tag color={presentation.color}>
          {presentation.labelKey ? t(presentation.labelKey) : presentation.label}
        </Tag>
      </Tooltip>
    );
  };

  const renderDetail = (detail: string | null | undefined) => {
    const tokens = parseAuditDetail(detail);
    if (tokens.length === 0) return <Text type="secondary">-</Text>;
    if (tokens.length === 1 && !tokens[0].label) {
      return (
        <Text ellipsis={{ tooltip: tokens[0].value }} style={{ maxWidth: 420 }}>
          {tokens[0].value}
        </Text>
      );
    }
    return (
      <Space size={[4, 4]} wrap>
        {tokens.map((token) => (
          <Tag key={`${token.label}:${token.value}`} style={{ marginInlineEnd: 0 }}>
            {token.label}: {token.value}
          </Tag>
        ))}
      </Space>
    );
  };

  const handleCleanup = async () => {
    try {
      await cleanupAuditLogs(cleanupDays);
      setPage(1);
      setRefreshKey((key) => key + 1);
      message.success(t('audit.cleanupSuccess', { n: cleanupDays }));
      setCleanupModalOpen(false);
    } catch {
      message.error('清理审计日志失败，请稍后重试');
    }
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const csv = await exportAuditLogs(activeFilter);
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
      downloadBlob(blob, `rocketmq-audit-logs-${dayjs().format('YYYY-MM-DD')}.csv`);
    } catch {
      message.error('导出审计日志失败，请稍后重试');
    } finally {
      setExporting(false);
    }
  };

  const columns: ColumnsType<AuditRecord> = [
    {
      title: t('audit.time'),
      dataIndex: 'timestamp',
      width: 180,
      sorter: (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime(),
      defaultSortOrder: 'descend',
      render: (timestamp: string) => formatDateTime(timestamp),
    },
    {
      title: t('audit.operator'),
      dataIndex: 'operator',
      width: 130,
      align: 'center',
      sorter: (a, b) => (a.operator ?? '').localeCompare(b.operator ?? ''),
    },
    {
      title: t('audit.opType'),
      dataIndex: 'operationType',
      width: 190,
      align: 'center',
      sorter: (a, b) => (a.operationType ?? '').localeCompare(b.operationType ?? ''),
      render: renderOperationType,
    },
    {
      title: t('audit.resourceType'),
      dataIndex: 'resourceType',
      width: 150,
      ellipsis: true,
      align: 'right',
      sorter: (a, b) => (a.resourceType ?? '').localeCompare(b.resourceType ?? ''),
      render: renderResourceType,
    },
    {
      title: t('audit.cluster'),
      dataIndex: 'clusterId',
      width: 140,
      ellipsis: true,
      sorter: (a, b) => (a.clusterId ?? '').localeCompare(b.clusterId ?? ''),
    },
    {
      title: t('audit.target'),
      dataIndex: 'target',
      width: 200,
      ellipsis: true,
      align: 'center',
      render: (_: string, record) => (
        <Tooltip title={describeAuditRecord(record, t)}>
          <span>{record.target || '-'}</span>
        </Tooltip>
      ),
    },
    {
      title: t('audit.detail'),
      dataIndex: 'detail',
      ellipsis: true,
      render: renderDetail,
    },
    {
      title: t('audit.result'),
      dataIndex: 'result',
      width: 80,
      align: 'center',
      sorter: (a, b) => (a.result ?? '').localeCompare(b.result ?? ''),
      render: renderResult,
    },
    {
      title: t('audit.error'),
      dataIndex: 'errorMessage',
      ellipsis: true,
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      {/* ─── Header ─── */}
      <PageHeader title={t('audit.title')} subtitle={t('audit.subtitle')} />

      {/* ─── Filter Bar ─── */}
      <Flex justify="space-between" align="center" gap={12} wrap style={{ marginBottom: 16 }}>
        <Flex gap={12} align="center" wrap>
          <Input.Search
            placeholder={t('audit.searchPlaceholder')}
            value={searchText}
            onChange={(e) => {
              setPage(1);
              setSearchText(e.target.value);
            }}
            style={{ width: 240 }}
            allowClear
          />
          <Select
            aria-label={t('audit.opType')}
            placeholder={t('audit.opType')}
            allowClear
            style={{ width: 180 }}
            value={selectedType}
            onChange={(value) => {
              setPage(1);
              setSelectedType(value);
            }}
            options={filterOptions.operationTypes.map((value) => {
              const presentation = getAuditOperationPresentation(value);
              return {
                label: presentation.labelKey ? t(presentation.labelKey) : presentation.label,
                value,
              };
            })}
          />
          <Select
            aria-label={t('audit.resourceType')}
            placeholder={t('audit.resourceType')}
            allowClear
            style={{ width: 150 }}
            value={selectedResourceType}
            onChange={(value) => {
              setPage(1);
              setSelectedResourceType(value);
            }}
            options={filterOptions.resourceTypes.map((value) => {
              const presentation = getAuditResourcePresentation(value);
              return {
                label: presentation.labelKey ? t(presentation.labelKey) : presentation.label,
                value,
              };
            })}
          />
          <Select
            aria-label={t('audit.cluster')}
            placeholder={t('audit.cluster')}
            allowClear
            style={{ width: 150 }}
            value={selectedClusterId}
            onChange={(value) => {
              setPage(1);
              setSelectedClusterId(value);
            }}
            options={filterOptions.clusterIds.map((value) => ({ label: value, value }))}
          />
          <DatePicker.RangePicker
            value={dateRange as [Dayjs | null, Dayjs | null] | null}
            onChange={(vals) => {
              setPage(1);
              setDateRange(vals as [Dayjs | null, Dayjs | null] | null);
            }}
          />
          <Select
            aria-label={t('audit.result')}
            value={resultFilter}
            onChange={(value) => {
              setPage(1);
              setResultFilter(value);
            }}
            style={{ width: 120 }}
            options={[
              { label: t('common.all'), value: 'all' },
              ...filterOptions.results.map((value) => {
                const presentation = getAuditResultPresentation(value);
                return {
                  label: presentation.labelKey ? t(presentation.labelKey) : presentation.label,
                  value,
                };
              }),
            ]}
          />
        </Flex>
        <Flex gap={8}>
          <Button
            icon={<DownloadOutlined />}
            loading={exporting}
            onClick={() => void handleExport()}
          >
            {t('common.export')}
          </Button>
          <Button danger icon={<Trash size={14} />} onClick={() => setCleanupModalOpen(true)}>
            {t('audit.cleanup')}
          </Button>
        </Flex>
      </Flex>

      <AuditSummaryCards summary={summary} loading={summaryLoading} />

      {/* ─── Table ─── */}
      <Card styles={{ body: { padding: 0 } }}>
        <Table
          size="small"
          columns={columns}
          dataSource={records}
          rowKey="id"
          loading={loading}
          scroll={{ x: tableScrollX(columns) }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (nextPage, nextPageSize) => {
              if (nextPageSize !== pageSize) {
                // A larger page size can make the current page exceed the new total page count.
                setPage(1);
                setPageSize(nextPageSize);
              } else {
                setPage(nextPage);
              }
            },
          }}
        />
      </Card>

      {/* ─── Cleanup Modal ─── */}
      <Modal
        title={t('audit.cleanupTitle')}
        open={cleanupModalOpen}
        onOk={handleCleanup}
        onCancel={() => setCleanupModalOpen(false)}
        okText={t('audit.cleanupConfirm')}
        cancelText={t('common.cancel')}
        okButtonProps={{ danger: true }}
      >
        <Flex vertical gap={12}>
          <Text>{t('audit.cleanupDesc')}</Text>
          <Flex align="center" gap={8}>
            <span>{t('audit.cleanup')}</span>
            <InputNumber
              min={1}
              max={365}
              value={cleanupDays}
              onChange={(v) => setCleanupDays(v ?? 30)}
            />
            <span>天之前的日志</span>
          </Flex>
        </Flex>
      </Modal>
    </div>
  );
};

export default AuditPage;
