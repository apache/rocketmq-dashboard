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

import { useEffect, useState } from 'react';
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
} from 'antd';
import { Trash } from '@phosphor-icons/react';
import { DownloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import type { AuditFilter, AuditFilterOptions } from '../../api/audit';
import type { AuditRecord } from '../../api/ops';
import {
  cleanupAuditLogs,
  exportAuditLogs,
  getAuditFilterOptions,
  listAuditRecords,
} from '../../services/opsService';
import { downloadBlob } from '../../utils/download';

const emptyFilterOptions: AuditFilterOptions = {
  operationTypes: [],
  resourceTypes: [],
  clusterIds: [],
  results: [],
};

const formatFilterLabel = (value: string) => value.trim().replace(/_/g, ' ');

const resultColor = (result: string) => {
  const normalized = result.toUpperCase();
  if (normalized === 'SUCCESS') return 'green';
  if (normalized === 'PARTIAL') return 'orange';
  return 'red';
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

  useEffect(() => {
    let cancelled = false;

    void getAuditFilterOptions()
      .then((options) => {
        if (!cancelled) setFilterOptions(options);
      })
      .catch(() => {
        if (!cancelled) setFilterOptions(emptyFilterOptions);
      });

    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  // Debounce free-text search so the record list is not re-fetched on every
  // keystroke; typing pauses for 300ms before the query hits the server.
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearchText(searchText), 300);
    return () => window.clearTimeout(timer);
  }, [searchText]);

  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => {
      if (!cancelled) setLoading(true);
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
        if (cancelled) return;
        setRecords(result.items);
        setTotal(result.total);
      })
      .catch(() => {
        if (!cancelled) message.error('审计日志加载失败，请稍后重试');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
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

  const { Text } = Typography;

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
      const csv = await exportAuditLogs(
        buildAuditFilter(
          searchText,
          selectedType,
          selectedResourceType,
          selectedClusterId,
          dateRange,
          resultFilter,
        ),
      );
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
    },
    {
      title: t('audit.operator'),
      dataIndex: 'operator',
      width: 130,
      sorter: (a, b) => (a.operator ?? '').localeCompare(b.operator ?? ''),
    },
    {
      title: t('audit.opType'),
      dataIndex: 'operationType',
      width: 190,
      sorter: (a, b) => (a.operationType ?? '').localeCompare(b.operationType ?? ''),
      render: (type: string) => <Tag>{formatFilterLabel(type)}</Tag>,
    },
    {
      title: t('audit.resourceType'),
      dataIndex: 'resourceType',
      width: 120,
      ellipsis: true,
      sorter: (a, b) => (a.resourceType ?? '').localeCompare(b.resourceType ?? ''),
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
    },
    {
      title: t('audit.detail'),
      dataIndex: 'detail',
      ellipsis: true,
    },
    {
      title: t('audit.result'),
      dataIndex: 'result',
      width: 80,
      sorter: (a, b) => (a.result ?? '').localeCompare(b.result ?? ''),
      render: (result: string) => (
        <Tag color={resultColor(result)}>{formatFilterLabel(result)}</Tag>
      ),
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
            options={filterOptions.operationTypes.map((value) => ({
              label: formatFilterLabel(value),
              value,
            }))}
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
            options={filterOptions.resourceTypes.map((value) => ({
              label: formatFilterLabel(value),
              value,
            }))}
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
              ...filterOptions.results.map((value) => ({
                label: formatFilterLabel(value),
                value,
              })),
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

      {/* ─── Table ─── */}
      <Card styles={{ body: { padding: 0 } }}>
        <Table
          size="small"
          columns={columns}
          dataSource={records}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1470 }}
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
