/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { useCallback, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Flex,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { listCloudCredentials } from '../../api/cloudCredential';
import type { CloudCredential } from '../../api/cloudCredential';
import { listInstances } from '../../api/instance';
import type { InstanceVendor } from '../../api/instance';
import { useLang } from '../../i18n/LangContext';
import { buildCsv, downloadCsv } from '../../utils/download';
import {
  buildCredentialUsageReport,
  credentialUsageCsvRows,
  filterCredentialUsageRows,
} from './cloudCredentialUsage';
import type {
  CredentialUsageRow,
  CredentialUsageStatus,
  OrphanCredentialReference,
} from './cloudCredentialUsage';

const CREDENTIAL_PAGE_SIZE = 100;
const MAX_CREDENTIAL_PAGES = 100;

interface Props {
  open: boolean;
  onClose: () => void;
}

const loadAllCredentials = async () => {
  const credentials: CloudCredential[] = [];
  for (let page = 1; page <= MAX_CREDENTIAL_PAGES; page += 1) {
    const result = await listCloudCredentials(undefined, undefined, page, CREDENTIAL_PAGE_SIZE);
    credentials.push(...result.items);
    if (credentials.length >= result.total || result.items.length < CREDENTIAL_PAGE_SIZE) {
      return credentials;
    }
  }
  throw new Error('credential page limit exceeded');
};

export const CloudCredentialUsageDrawer = ({ open, onClose }: Props) => {
  const { t } = useLang();
  const [credentials, setCredentials] = useState<CloudCredential[]>([]);
  const [instances, setInstances] = useState<Awaited<ReturnType<typeof listInstances>>>([]);
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [vendor, setVendor] = useState<InstanceVendor>();
  const [status, setStatus] = useState<CredentialUsageStatus>();

  const report = useMemo(
    () => buildCredentialUsageReport(credentials, instances),
    [credentials, instances],
  );
  const filteredRows = useMemo(
    () => filterCredentialUsageRows(report.rows, { search, vendor, status }),
    [report.rows, search, status, vendor],
  );

  const loadReport = useCallback(async () => {
    setLoading(true);
    try {
      const [nextCredentials, nextInstances] = await Promise.all([
        loadAllCredentials(),
        listInstances(),
      ]);
      setCredentials(nextCredentials);
      setInstances(nextInstances);
      setLoaded(true);
    } catch {
      message.error(t('settings.credentialUsageLoadFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  const exportReport = () => {
    const csv = buildCsv(
      [
        { header: 'Credential ID', value: (row) => row.credentialId },
        { header: 'Credential name', value: (row) => row.credentialName },
        { header: 'Vendor', value: (row) => row.vendor },
        { header: 'Status', value: (row) => row.status },
        { header: 'Instance count', value: (row) => row.usageCount },
        { header: 'Instances', value: (row) => row.instances },
        { header: 'Regions', value: (row) => row.regions },
        { header: 'Mismatched instances', value: (row) => row.mismatchedInstances },
        { header: 'Created at', value: (row) => row.createdAt },
      ],
      credentialUsageCsvRows(filteredRows),
    );
    downloadCsv(
      `rocketmq-cloud-credential-usage-${new Date().toISOString().slice(0, 10)}.csv`,
      csv,
    );
  };

  const statusLabel = (value: CredentialUsageStatus) =>
    t(
      value === 'USED'
        ? 'settings.credentialUsageUsed'
        : value === 'UNUSED'
          ? 'settings.credentialUsageUnused'
          : 'settings.credentialUsageMismatch',
    );

  const credentialColumns: ColumnsType<CredentialUsageRow> = [
    {
      title: t('common.name'),
      dataIndex: 'credentialName',
      key: 'credentialName',
      sorter: (left, right) => left.credentialName.localeCompare(right.credentialName),
    },
    {
      title: t('settings.cloudVendor'),
      dataIndex: 'vendor',
      key: 'vendor',
      width: 110,
      render: (value: InstanceVendor) => <Tag>{value}</Tag>,
    },
    {
      title: t('settings.credentialUsageStatus'),
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (value: CredentialUsageStatus) => (
        <Tag color={value === 'USED' ? 'green' : value === 'UNUSED' ? 'default' : 'red'}>
          {statusLabel(value)}
        </Tag>
      ),
    },
    {
      title: t('settings.credentialUsageInstances'),
      key: 'instances',
      render: (_, row) =>
        row.instanceNames.length ? (
          <Space size={[4, 4]} wrap>
            {row.instanceNames.map((name) => (
              <Tag key={name}>{name}</Tag>
            ))}
          </Space>
        ) : (
          '-'
        ),
    },
    {
      title: t('settings.credentialUsageRegions'),
      key: 'regions',
      width: 180,
      render: (_, row) => row.regions.join(', ') || '-',
    },
    {
      title: t('settings.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 190,
    },
  ];

  const orphanColumns: ColumnsType<OrphanCredentialReference> = [
    {
      title: t('settings.credentialUsageInstance'),
      dataIndex: 'instanceName',
      key: 'instanceName',
    },
    {
      title: t('settings.cloudVendor'),
      dataIndex: 'instanceVendor',
      key: 'instanceVendor',
      render: (value: InstanceVendor) => <Tag>{value}</Tag>,
    },
    {
      title: t('settings.credentialUsageMissingId'),
      dataIndex: 'credentialId',
      key: 'credentialId',
    },
    { title: t('settings.credentialUsageRegions'), dataIndex: 'region', key: 'region' },
  ];

  const summaryCards = [
    ['settings.credentialUsageCredentials', report.summary.credentials, undefined],
    ['settings.credentialUsageCovered', report.summary.coveredInstances, undefined],
    [
      'settings.credentialUsageUnused',
      report.summary.unused,
      report.summary.unused ? '#d46b08' : undefined,
    ],
    [
      'settings.credentialUsageMismatch',
      report.summary.mismatched + report.summary.orphanReferences,
      report.summary.mismatched + report.summary.orphanReferences ? '#cf1322' : undefined,
    ],
  ] as const;

  return (
    <Drawer
      title={t('settings.credentialUsageTitle')}
      open={open}
      onClose={onClose}
      width={1000}
      destroyOnHidden
      extra={
        <Space>
          <Button
            icon={<DownloadOutlined />}
            disabled={!loaded || filteredRows.length === 0}
            onClick={exportReport}
          >
            {t('settings.credentialUsageExport')}
          </Button>
          <Button type="primary" icon={<ReloadOutlined />} loading={loading} onClick={loadReport}>
            {loaded ? t('common.refresh') : t('settings.credentialUsageLoad')}
          </Button>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        message={t('settings.credentialUsagePrivacyTitle')}
        description={t('settings.credentialUsagePrivacyDescription')}
        style={{ marginBottom: 16 }}
      />

      {!loaded ? (
        <Empty description={t('settings.credentialUsageEmpty')}>
          <Button type="primary" loading={loading} onClick={loadReport}>
            {t('settings.credentialUsageLoad')}
          </Button>
        </Empty>
      ) : (
        <>
          <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
            {summaryCards.map(([key, value, color]) => (
              <Col xs={12} lg={6} key={key}>
                <Card size="small">
                  <Statistic title={t(key)} value={value} valueStyle={{ color }} />
                </Card>
              </Col>
            ))}
          </Row>

          {(report.summary.mismatched > 0 || report.summary.orphanReferences > 0) && (
            <Alert
              type="warning"
              showIcon
              message={t('settings.credentialUsageAttention', {
                mismatch: report.summary.mismatched,
                orphan: report.summary.orphanReferences,
              })}
              style={{ marginBottom: 16 }}
            />
          )}

          <Flex gap={12} wrap style={{ marginBottom: 16 }}>
            <Input.Search
              allowClear
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('settings.credentialUsageSearch')}
              style={{ width: 280 }}
            />
            <Select<InstanceVendor>
              allowClear
              value={vendor}
              onChange={setVendor}
              placeholder={t('settings.allCloudVendors')}
              style={{ width: 160 }}
              options={[
                { value: 'ALIYUN', label: t('settings.aliyun') },
                { value: 'TENCENT', label: t('settings.tencent') },
              ]}
            />
            <Select<CredentialUsageStatus>
              allowClear
              value={status}
              onChange={setStatus}
              placeholder={t('settings.credentialUsageAllStatuses')}
              style={{ width: 180 }}
              options={(['USED', 'UNUSED', 'VENDOR_MISMATCH'] as const).map((value) => ({
                value,
                label: statusLabel(value),
              }))}
            />
            <Typography.Text type="secondary" style={{ alignSelf: 'center' }}>
              {t('settings.credentialUsageFiltered', {
                visible: filteredRows.length,
                total: report.rows.length,
              })}
            </Typography.Text>
          </Flex>

          <Tabs
            items={[
              {
                key: 'credentials',
                label: t('settings.credentialUsageCredentialsTab', {
                  count: filteredRows.length,
                }),
                children: (
                  <Table<CredentialUsageRow>
                    rowKey="credentialId"
                    size="small"
                    columns={credentialColumns}
                    dataSource={filteredRows}
                    pagination={{ pageSize: 20, showSizeChanger: true }}
                    scroll={{ x: 880 }}
                  />
                ),
              },
              {
                key: 'orphans',
                label: t('settings.credentialUsageOrphansTab', {
                  count: report.orphanReferences.length,
                }),
                children: (
                  <Table<OrphanCredentialReference>
                    rowKey={(row) => `${row.instanceId}-${row.credentialId}`}
                    size="small"
                    columns={orphanColumns}
                    dataSource={report.orphanReferences}
                    pagination={false}
                  />
                ),
              },
            ]}
          />
        </>
      )}
    </Drawer>
  );
};

export default CloudCredentialUsageDrawer;
