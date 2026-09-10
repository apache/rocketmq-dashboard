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
import { DownloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  DatePicker,
  Drawer,
  Empty,
  Flex,
  Pagination,
  Space,
  Spin,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import type { AuditFilter } from '../../api/audit';
import type { AuditRecord } from '../../api/ops';
import { exportAuditLogs, listAuditRecords } from '../../services/opsService';
import { downloadBlob } from '../../utils/download';
import { formatDateTime } from '../../utils/format';
import { useLang } from '../../i18n/LangContext';
import {
  getAuditOperationPresentation,
  getAuditResourcePresentation,
  getAuditResultPresentation,
  parseAuditDetail,
} from './auditPresentation';

export interface AuditTimelineResource {
  resourceType: string;
  target: string;
  clusterId?: string | null;
}

interface ResourceOperationTimelineDrawerProps {
  open: boolean;
  resource: AuditTimelineResource;
  onClose: () => void;
}

const buildTimelineFilter = (
  resource: AuditTimelineResource,
  dateRange: [Dayjs | null, Dayjs | null] | null,
): AuditFilter => ({
  resourceType: resource.resourceType,
  target: resource.target,
  ...(resource.clusterId?.trim() ? { clusterId: resource.clusterId } : { clusterIdMissing: true }),
  ...(dateRange?.[0] ? { startDate: dateRange[0].format('YYYY-MM-DD') } : {}),
  ...(dateRange?.[1] ? { endDate: dateRange[1].format('YYYY-MM-DD') } : {}),
});

const ResourceOperationTimelineDrawer = ({
  open,
  resource,
  onClose,
}: ResourceOperationTimelineDrawerProps) => {
  const { t } = useLang();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [response, setResponse] = useState<{
    queryKey: string;
    records: AuditRecord[];
    total: number;
    error: boolean;
  } | null>(null);
  const [exporting, setExporting] = useState(false);
  const requestVersion = useRef(0);
  const filter = useMemo(() => buildTimelineFilter(resource, dateRange), [dateRange, resource]);
  const queryKey = JSON.stringify({ page, pageSize, filter });
  const currentResponse = response?.queryKey === queryKey ? response : null;
  const loading = open && currentResponse === null;

  useEffect(() => {
    const version = ++requestVersion.current;
    if (!open) return;
    void listAuditRecords({ page, pageSize, ...filter })
      .then((result) => {
        if (requestVersion.current !== version) return;
        setResponse({
          queryKey,
          records: result.items,
          total: result.total,
          error: false,
        });
      })
      .catch(() => {
        if (requestVersion.current !== version) return;
        setResponse({ queryKey, records: [], total: 0, error: true });
      });
    return () => {
      requestVersion.current += 1;
    };
  }, [filter, open, page, pageSize, queryKey]);

  const handleExport = async () => {
    setExporting(true);
    try {
      const csv = await exportAuditLogs(filter);
      downloadBlob(
        new Blob([csv], { type: 'text/csv;charset=utf-8' }),
        `rocketmq-audit-resource-${dayjs().format('YYYY-MM-DD')}.csv`,
      );
    } catch {
      message.error(t('audit.timelineExportFailed'));
    } finally {
      setExporting(false);
    }
  };

  const resourcePresentation = getAuditResourcePresentation(resource.resourceType);
  const resourceLabel = resourcePresentation.labelKey
    ? t(resourcePresentation.labelKey)
    : resourcePresentation.label;

  return (
    <Drawer
      title={t('audit.timelineTitle')}
      open={open}
      width="min(720px, calc(100vw - 16px))"
      onClose={onClose}
      destroyOnHidden
      extra={
        <Button
          icon={<DownloadOutlined />}
          aria-label={t('audit.timelineExport')}
          loading={exporting}
          onClick={() => void handleExport()}
        >
          {t('audit.timelineExport')}
        </Button>
      }
    >
      <Space direction="vertical" size={20} style={{ width: '100%' }}>
        <Flex gap={8} wrap="wrap" align="center">
          <Tag color={resourcePresentation.color}>{resourceLabel}</Tag>
          <Typography.Text strong>{resource.target}</Typography.Text>
          <Typography.Text type="secondary">
            {resource.clusterId?.trim() || t('audit.timelineNoCluster')}
          </Typography.Text>
        </Flex>

        <DatePicker.RangePicker
          aria-label={t('audit.timelineDateRange')}
          style={{ width: '100%', maxWidth: 320 }}
          value={dateRange}
          onChange={(value) => {
            setPage(1);
            setDateRange(value as [Dayjs | null, Dayjs | null] | null);
          }}
        />

        {loading ? (
          <Flex justify="center" style={{ minHeight: 240, paddingTop: 80 }}>
            <Spin />
          </Flex>
        ) : currentResponse?.error ? (
          <Alert type="error" showIcon message={t('audit.timelineLoadFailed')} />
        ) : currentResponse && currentResponse.records.length > 0 ? (
          <>
            <Timeline
              items={currentResponse.records.map((record) => {
                const operation = getAuditOperationPresentation(record.operationType);
                const result = getAuditResultPresentation(record.result);
                const detail = parseAuditDetail(record.detail)
                  .map((token) => (token.label ? `${token.label}: ${token.value}` : token.value))
                  .join(', ');
                return {
                  color:
                    record.result === 'FAILED'
                      ? 'red'
                      : record.result === 'PARTIAL'
                        ? 'orange'
                        : 'green',
                  children: (
                    <Space direction="vertical" size={4} style={{ width: '100%' }}>
                      <Flex gap={8} align="center" wrap="wrap">
                        <Typography.Text strong>
                          {operation.labelKey ? t(operation.labelKey) : operation.label}
                        </Typography.Text>
                        <Tag color={result.color}>
                          {result.labelKey ? t(result.labelKey) : result.label}
                        </Tag>
                      </Flex>
                      <Typography.Text type="secondary">
                        {formatDateTime(record.timestamp)} · {record.operator || '-'}
                      </Typography.Text>
                      {detail && <Typography.Text>{detail}</Typography.Text>}
                      {record.errorMessage && (
                        <Typography.Text type="danger">{record.errorMessage}</Typography.Text>
                      )}
                    </Space>
                  ),
                };
              })}
            />
            <Flex justify="flex-end">
              <Pagination
                current={page}
                pageSize={pageSize}
                total={currentResponse.total}
                showSizeChanger
                responsive
                pageSizeOptions={[10, 20, 50, 100]}
                onChange={(nextPage, nextPageSize) => {
                  setPage(nextPageSize === pageSize ? nextPage : 1);
                  setPageSize(nextPageSize);
                }}
              />
            </Flex>
          </>
        ) : (
          <Empty description={t('audit.timelineEmpty')} />
        )}
      </Space>
    </Drawer>
  );
};

export default ResourceOperationTimelineDrawer;
