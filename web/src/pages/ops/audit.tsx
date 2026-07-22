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
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import type { AuditRecord } from '../../api/ops';
import { cleanupAuditLogs, listAuditRecords } from '../../services/opsService';

const operationTypeColors: Record<string, string> = {
  创建Topic: 'blue',
  删除Topic: 'red',
  修改配置: 'orange',
  重置位点: 'purple',
  ACL变更: 'cyan',
  重启Broker: 'gold',
  删除消费组: 'red',
};

const operationTypeOptions = [
  '创建Topic',
  '删除Topic',
  '修改配置',
  '重置位点',
  'ACL变更',
  '重启Broker',
  '删除消费组',
];

const AuditPage: React.FC = () => {
  const { t } = useLang();
  const [records, setRecords] = useState<AuditRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);
  const [searchText, setSearchText] = useState('');
  const [selectedType, setSelectedType] = useState<string | undefined>(undefined);
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [resultFilter, setResultFilter] = useState('all');
  const [cleanupModalOpen, setCleanupModalOpen] = useState(false);
  const [cleanupDays, setCleanupDays] = useState(30);

  useEffect(() => {
    let cancelled = false;
    const startDate = dateRange?.[0]?.format('YYYY-MM-DD');
    const endDate = dateRange?.[1]?.format('YYYY-MM-DD');

    void listAuditRecords({
      page,
      pageSize,
      search: searchText || undefined,
      operationType: selectedType,
      startDate,
      endDate,
      result: resultFilter === 'all' ? undefined : resultFilter,
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
  }, [page, pageSize, searchText, selectedType, dateRange, resultFilter, refreshKey]);

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

  const columns: ColumnsType<AuditRecord> = [
    {
      title: t('audit.time'),
      dataIndex: 'timestamp',
      width: 180,
    },
    {
      title: t('audit.operator'),
      dataIndex: 'operator',
      width: 130,
    },
    {
      title: t('audit.opType'),
      dataIndex: 'operationType',
      width: 120,
      render: (type: string) => <Tag color={operationTypeColors[type] || 'default'}>{type}</Tag>,
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
      title: t('audit.ip'),
      dataIndex: 'ipAddress',
      width: 140,
    },
    {
      title: t('audit.result'),
      dataIndex: 'result',
      width: 80,
      render: (result: string) =>
        result.toUpperCase() === 'SUCCESS' ? (
          <Tag color="green">{t('common.success')}</Tag>
        ) : (
          <Tag color="red">{t('common.failure')}</Tag>
        ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      {/* ─── Header ─── */}
      <PageHeader title={t('audit.title')} subtitle={t('audit.subtitle')} />

      {/* ─── Filter Bar ─── */}
      <Flex justify="space-between" align="center" style={{ marginBottom: 16 }}>
        <Flex gap={16} align="center">
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
            placeholder={t('audit.opType')}
            allowClear
            style={{ width: 180 }}
            value={selectedType}
            onChange={(value) => {
              setPage(1);
              setSelectedType(value);
            }}
            options={operationTypeOptions.map((opt) => ({ label: opt, value: opt }))}
          />
          <DatePicker.RangePicker
            value={dateRange as [Dayjs | null, Dayjs | null] | null}
            onChange={(vals) => {
              setPage(1);
              setDateRange(vals as [Dayjs | null, Dayjs | null] | null);
            }}
          />
          <Select
            value={resultFilter}
            onChange={(value) => {
              setPage(1);
              setResultFilter(value);
            }}
            style={{ width: 120 }}
            options={[
              { label: t('common.all'), value: 'all' },
              { label: t('common.success'), value: 'SUCCESS' },
              { label: t('common.failure'), value: 'FAILURE' },
            ]}
          />
        </Flex>
        <Button danger icon={<Trash size={14} />} onClick={() => setCleanupModalOpen(true)}>
          {t('audit.cleanup')}
        </Button>
      </Flex>

      {/* ─── Table ─── */}
      <Card bodyStyle={{ padding: 0 }}>
        <Table
          size="small"
          columns={columns}
          dataSource={records}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (nextPage, nextPageSize) => {
              setPage(nextPage);
              setPageSize(nextPageSize);
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
