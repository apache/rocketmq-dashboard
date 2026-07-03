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

import { useState, useMemo } from 'react';
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
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import PageHeader from '../../components/PageHeader';
import { mockAuditRecords, type AuditRecord } from '../../mock/audit';
import { useLang } from '../../i18n/LangContext';

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
  const [records, setRecords] = useState<AuditRecord[]>([...mockAuditRecords] as AuditRecord[]);
  const [searchText, setSearchText] = useState('');
  const [selectedType, setSelectedType] = useState<string | undefined>(undefined);
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [resultFilter, setResultFilter] = useState('all');
  const [cleanupModalOpen, setCleanupModalOpen] = useState(false);
  const [cleanupDays, setCleanupDays] = useState(30);

  const filteredRecords = useMemo(() => {
    return records.filter((record) => {
      if (searchText) {
        const lower = searchText.toLowerCase();
        if (
          !record.operator.toLowerCase().includes(lower) &&
          !record.target.toLowerCase().includes(lower)
        ) {
          return false;
        }
      }
      if (selectedType && record.operationType !== selectedType) {
        return false;
      }
      if (dateRange && dateRange[0] && dateRange[1]) {
        const recordTime = dayjs(record.timestamp);
        const start = dateRange[0].startOf('day');
        const end = dateRange[1].endOf('day');
        if (recordTime.isBefore(start) || recordTime.isAfter(end)) {
          return false;
        }
      }
      if (resultFilter !== 'all' && record.result !== resultFilter) {
        return false;
      }
      return true;
    });
  }, [records, searchText, selectedType, dateRange, resultFilter]);

  const { Text } = Typography;

  const handleCleanup = () => {
    const cutoff = dayjs().subtract(cleanupDays, 'day');
    setRecords((prev) => prev.filter((r) => dayjs(r.timestamp).isAfter(cutoff)));
    message.success(t('audit.cleanupSuccess', { n: cleanupDays }));
    setCleanupModalOpen(false);
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
        result === 'success' ? (
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
            onChange={(e) => setSearchText(e.target.value)}
            style={{ width: 240 }}
            allowClear
          />
          <Select
            placeholder={t('audit.opType')}
            allowClear
            style={{ width: 180 }}
            value={selectedType}
            onChange={setSelectedType}
            options={operationTypeOptions.map((opt) => ({ label: opt, value: opt }))}
          />
          <DatePicker.RangePicker
            value={dateRange as [Dayjs | null, Dayjs | null] | null}
            onChange={(vals) => setDateRange(vals as [Dayjs | null, Dayjs | null] | null)}
          />
          <Select
            value={resultFilter}
            onChange={setResultFilter}
            style={{ width: 120 }}
            options={[
              { label: t('common.all'), value: 'all' },
              { label: t('common.success'), value: 'success' },
              { label: t('common.failure'), value: 'failure' },
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
          dataSource={filteredRecords}
          rowKey="id"
          pagination={{ pageSize: 20 }}
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
