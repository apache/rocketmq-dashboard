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
  Alert,
  Button,
  Card,
  Drawer,
  Empty,
  Flex,
  Input,
  Progress,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { CheckOutlined, CloseOutlined, DownloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { Instance, InstanceCapability, InstanceType, InstanceVendor } from '../api/instance';
import { getInstanceCapabilities } from '../services/instanceService';
import { useLang } from '../i18n/LangContext';
import { buildCsv, downloadCsv, type CsvColumn } from '../utils/download';
import { tableScrollX } from '../utils/table';
import {
  buildInstanceCapabilityMatrix,
  describeCapabilityGaps,
  filterInstanceCapabilityRows,
  INSTANCE_CAPABILITIES,
  summarizeVisibleCapabilityRows,
  type CapabilityLoadResult,
  type CapabilityLoadStatus,
  type InstanceCapabilityMatrix,
  type InstanceCapabilityMatrixRow,
} from '../utils/instanceCapabilityMatrix';

interface InstanceCapabilityMatrixDrawerProps {
  open: boolean;
  instances: Instance[];
  onClose: () => void;
}

const LOAD_BATCH_SIZE = 4;

const CSV_COLUMNS: CsvColumn<InstanceCapabilityMatrixRow>[] = [
  { header: 'Instance', value: (row) => row.instanceId },
  { header: 'Vendor', value: (row) => row.vendor },
  { header: 'Access Type', value: (row) => row.accessType },
  { header: 'Endpoint', value: (row) => row.endpoint },
  { header: 'Discovery Status', value: (row) => row.status },
  ...INSTANCE_CAPABILITIES.map((capability) => ({
    header: capability,
    value: (row: InstanceCapabilityMatrixRow) =>
      row.status === 'FAILED'
        ? 'UNKNOWN'
        : row.capabilities.includes(capability)
          ? 'SUPPORTED'
          : 'MISSING',
  })),
  { header: 'Supported Count', value: (row) => row.supportedCount },
  { header: 'Missing Capabilities', value: (row) => row.missingCapabilities.join(';') },
  { header: 'Error', value: (row) => row.error },
];

const errorMessage = (error: unknown): string => {
  const serverMessage = (error as { response?: { data?: { message?: unknown } } })?.response?.data
    ?.message;
  if (typeof serverMessage === 'string' && serverMessage.trim()) return serverMessage.trim();
  if (error instanceof Error && error.message.trim()) return error.message.trim();
  return 'Capability discovery failed';
};

const loadCapability = async (instance: Instance): Promise<CapabilityLoadResult> => {
  try {
    return { instance, value: await getInstanceCapabilities(instance.name) };
  } catch (error) {
    return { instance, error: errorMessage(error) };
  }
};

const loadCapabilitiesInBatches = async (
  instances: Instance[],
): Promise<CapabilityLoadResult[]> => {
  const results: CapabilityLoadResult[] = [];
  for (let start = 0; start < instances.length; start += LOAD_BATCH_SIZE) {
    results.push(
      ...(await Promise.all(instances.slice(start, start + LOAD_BATCH_SIZE).map(loadCapability))),
    );
  }
  return results;
};

const InstanceCapabilityMatrixDrawer = ({
  open,
  instances,
  onClose,
}: InstanceCapabilityMatrixDrawerProps) => {
  const { t } = useLang();
  const [selectedInstanceIds, setSelectedInstanceIds] = useState<string[]>(
    instances.map((instance) => instance.name),
  );
  const [matrix, setMatrix] = useState<InstanceCapabilityMatrix | null>(null);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [vendor, setVendor] = useState<InstanceVendor | 'ALL'>('ALL');
  const [accessType, setAccessType] = useState<InstanceType | 'ALL'>('ALL');
  const [capability, setCapability] = useState<InstanceCapability | 'ALL'>('ALL');
  const [support, setSupport] = useState<'ALL' | 'SUPPORTED' | 'MISSING'>('ALL');
  const [status, setStatus] = useState<CapabilityLoadStatus | 'ALL'>('ALL');
  const requestIdRef = useRef(0);

  useEffect(
    () => () => {
      requestIdRef.current += 1;
    },
    [],
  );

  const selectedInstances = useMemo(() => {
    const selected = new Set(selectedInstanceIds);
    return instances.filter((instance) => selected.has(instance.name));
  }, [instances, selectedInstanceIds]);

  const visibleRows = useMemo(
    () =>
      filterInstanceCapabilityRows(matrix?.rows ?? [], {
        search,
        vendor,
        accessType,
        capability,
        support,
        status,
      }),
    [accessType, capability, matrix, search, status, support, vendor],
  );
  const visibleSummary = useMemo(() => summarizeVisibleCapabilityRows(visibleRows), [visibleRows]);

  const loadMatrix = async () => {
    if (selectedInstances.length === 0) {
      message.warning(t('capabilityMatrix.selectRequired'));
      return;
    }
    const requestId = ++requestIdRef.current;
    setLoading(true);
    try {
      const results = await loadCapabilitiesInBatches(selectedInstances);
      if (requestId !== requestIdRef.current) return;
      setMatrix(buildInstanceCapabilityMatrix(results));
      setSearch('');
      setVendor('ALL');
      setAccessType('ALL');
      setCapability('ALL');
      setSupport('ALL');
      setStatus('ALL');
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  };

  const exportMatrix = () => {
    if (!matrix) return;
    downloadCsv('rocketmq-instance-capability-matrix.csv', buildCsv(CSV_COLUMNS, visibleRows));
    message.success(t('capabilityMatrix.exported', { count: visibleRows.length }));
  };

  const capabilityLabel = (value: InstanceCapability) => t(`capabilityMatrix.${value}`);

  const columns: TableColumnsType<InstanceCapabilityMatrixRow> = [
    {
      title: t('capabilityMatrix.instance'),
      dataIndex: 'instanceId',
      key: 'instanceId',
      width: 180,
      fixed: 'left',
      ellipsis: true,
      sorter: (left, right) => left.instanceId.localeCompare(right.instanceId),
      render: (value: string) => <span title={value}>{value}</span>,
    },
    {
      title: t('capabilityMatrix.vendor'),
      dataIndex: 'vendor',
      key: 'vendor',
      width: 105,
      filters: ['APACHE', 'ALIYUN', 'TENCENT'].map((value) => ({ text: value, value })),
      onFilter: (value, row) => row.vendor === value,
    },
    {
      title: t('capabilityMatrix.accessType'),
      dataIndex: 'accessType',
      key: 'accessType',
      width: 135,
      ellipsis: true,
      render: (value: string) => <span title={value}>{value}</span>,
    },
    ...INSTANCE_CAPABILITIES.map((item): TableColumnsType<InstanceCapabilityMatrixRow>[number] => ({
      title: capabilityLabel(item),
      key: item,
      width: 125,
      align: 'center',
      render: (_, row) => {
        if (row.status === 'FAILED') {
          return <Tag>{t('capabilityMatrix.unknown')}</Tag>;
        }
        return row.capabilities.includes(item) ? (
          <Tag icon={<CheckOutlined />} color="success">
            {t('capabilityMatrix.supported')}
          </Tag>
        ) : (
          <Tag icon={<CloseOutlined />} color="default">
            {t('capabilityMatrix.missing')}
          </Tag>
        );
      },
    })),
    {
      title: t('capabilityMatrix.coverage'),
      key: 'coverage',
      width: 120,
      sorter: (left, right) => left.supportedCount - right.supportedCount,
      render: (_, row) =>
        row.status === 'FAILED' ? (
          <Tooltip title={row.error}>
            <Tag color="error">{t('capabilityMatrix.failed')}</Tag>
          </Tooltip>
        ) : (
          <span>{`${row.supportedCount}/${INSTANCE_CAPABILITIES.length}`}</span>
        ),
    },
    {
      title: t('capabilityMatrix.gaps'),
      key: 'gaps',
      width: 230,
      ellipsis: true,
      render: (_, row) => {
        const detail = describeCapabilityGaps(row);
        const display =
          row.status === 'FAILED'
            ? t('capabilityMatrix.discoveryUnavailable')
            : row.missingCapabilities.length === 0
              ? t('capabilityMatrix.noGaps')
              : row.missingCapabilities.map(capabilityLabel).join(', ');
        return <span title={detail || display}>{display}</span>;
      },
    },
  ];

  const failedRows = matrix?.rows.filter((row) => row.status === 'FAILED') ?? [];

  return (
    <Drawer
      title={t('capabilityMatrix.title')}
      open={open}
      onClose={() => {
        requestIdRef.current += 1;
        onClose();
      }}
      width={1280}
      destroyOnHidden
    >
      <Flex vertical gap={16}>
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          {t('capabilityMatrix.description')}
        </Typography.Paragraph>

        <Flex gap={8} align="end" wrap="wrap">
          <label style={{ flex: 1, minWidth: 380 }}>
            <Typography.Text>{t('capabilityMatrix.instances')}</Typography.Text>
            <Select
              mode="multiple"
              allowClear
              maxTagCount="responsive"
              aria-label={t('capabilityMatrix.instances')}
              value={selectedInstanceIds}
              options={instances.map((instance) => ({
                value: instance.name,
                label: `${instance.name} (${instance.vendor ?? 'APACHE'})`,
              }))}
              style={{ width: '100%', marginTop: 4 }}
              onChange={(value) => {
                requestIdRef.current += 1;
                setSelectedInstanceIds(value);
                setMatrix(null);
                setLoading(false);
              }}
            />
          </label>
          <Button
            type="primary"
            icon={<SearchOutlined />}
            aria-label={t('capabilityMatrix.load')}
            loading={loading}
            disabled={selectedInstanceIds.length === 0}
            onClick={() => void loadMatrix()}
          >
            {t('capabilityMatrix.load')}
          </Button>
        </Flex>

        {instances.length === 0 && <Empty description={t('capabilityMatrix.noInstances')} />}

        {failedRows.length > 0 && (
          <Alert
            type="warning"
            showIcon
            message={t('capabilityMatrix.partialFailure', { count: failedRows.length })}
            description={failedRows.map((row) => `${row.instanceId}: ${row.error}`).join('; ')}
          />
        )}

        {matrix && (
          <>
            <Flex gap={12} wrap="wrap">
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic
                  title={t('capabilityMatrix.requested')}
                  value={matrix.summary.requested}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic title={t('capabilityMatrix.loaded')} value={matrix.summary.loaded} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic
                  title={t('capabilityMatrix.fullCoverage')}
                  value={matrix.summary.fullCoverage}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic title={t('capabilityMatrix.limited')} value={matrix.summary.limited} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 140 }}>
                <Statistic title={t('capabilityMatrix.failed')} value={matrix.summary.failed} />
              </Card>
            </Flex>

            <Card size="small" title={t('capabilityMatrix.coverageSummary')}>
              <Flex gap={16} wrap="wrap">
                {matrix.summary.coverage.map((item) => (
                  <div key={item.capability} style={{ width: 170 }}>
                    <Typography.Text title={item.capability} ellipsis style={{ display: 'block' }}>
                      {capabilityLabel(item.capability)}
                    </Typography.Text>
                    <Progress
                      percent={item.percent}
                      size="small"
                      status={item.percent === 100 ? 'success' : 'normal'}
                      format={() => `${item.supported}/${item.loaded}`}
                    />
                  </div>
                ))}
              </Flex>
            </Card>

            <Flex justify="space-between" gap={8} wrap="wrap">
              <Space wrap>
                <Input
                  allowClear
                  prefix={<SearchOutlined />}
                  aria-label={t('capabilityMatrix.search')}
                  placeholder={t('capabilityMatrix.search')}
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  style={{ width: 240 }}
                />
                <Select
                  aria-label={t('capabilityMatrix.vendorFilter')}
                  value={vendor}
                  onChange={setVendor}
                  style={{ width: 150 }}
                  options={[
                    { value: 'ALL', label: t('capabilityMatrix.allVendors') },
                    { value: 'APACHE', label: 'Apache' },
                    { value: 'ALIYUN', label: 'Alibaba Cloud' },
                    { value: 'TENCENT', label: 'Tencent Cloud' },
                  ]}
                />
                <Select
                  aria-label={t('capabilityMatrix.typeFilter')}
                  value={accessType}
                  onChange={setAccessType}
                  style={{ width: 170 }}
                  options={[
                    { value: 'ALL', label: t('capabilityMatrix.allTypes') },
                    ...(['CLOUD', 'PROXY_LOCAL', 'PROXY_CLUSTER', 'DIRECT'] as const).map(
                      (value) => ({
                        value,
                        label: value,
                      }),
                    ),
                  ]}
                />
                <Select
                  aria-label={t('capabilityMatrix.capabilityFilter')}
                  value={capability}
                  onChange={setCapability}
                  style={{ width: 190 }}
                  options={[
                    { value: 'ALL', label: t('capabilityMatrix.allCapabilities') },
                    ...INSTANCE_CAPABILITIES.map((value) => ({
                      value,
                      label: capabilityLabel(value),
                    })),
                  ]}
                />
                <Select
                  aria-label={t('capabilityMatrix.supportFilter')}
                  value={support}
                  onChange={setSupport}
                  disabled={capability === 'ALL'}
                  style={{ width: 145 }}
                  options={[
                    { value: 'ALL', label: t('capabilityMatrix.allSupport') },
                    { value: 'SUPPORTED', label: t('capabilityMatrix.supported') },
                    { value: 'MISSING', label: t('capabilityMatrix.missing') },
                  ]}
                />
                <Select
                  aria-label={t('capabilityMatrix.statusFilter')}
                  value={status}
                  onChange={setStatus}
                  style={{ width: 145 }}
                  options={[
                    { value: 'ALL', label: t('capabilityMatrix.allStatuses') },
                    { value: 'AVAILABLE', label: t('capabilityMatrix.loaded') },
                    { value: 'FAILED', label: t('capabilityMatrix.failed') },
                  ]}
                />
              </Space>
              <Button
                icon={<DownloadOutlined />}
                aria-label={t('capabilityMatrix.export')}
                onClick={exportMatrix}
                disabled={visibleRows.length === 0}
              >
                {t('capabilityMatrix.export')}
              </Button>
            </Flex>

            <Typography.Text type="secondary">
              {t('capabilityMatrix.visibleSummary', {
                count: visibleSummary.instances,
                limited: visibleSummary.limited,
                failed: visibleSummary.failed,
              })}
            </Typography.Text>
            <Table
              rowKey="key"
              size="small"
              columns={columns}
              dataSource={visibleRows}
              pagination={{ pageSize: 20, showSizeChanger: true }}
              scroll={{ x: tableScrollX(columns) }}
            />
          </>
        )}
      </Flex>
    </Drawer>
  );
};

export default InstanceCapabilityMatrixDrawer;
