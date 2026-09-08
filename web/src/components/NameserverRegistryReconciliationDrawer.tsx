/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import { useMemo, useState } from 'react';
import {
  Alert,
  Card,
  Col,
  Drawer,
  Input,
  Row,
  Segmented,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { ClusterInfo, NameserverRegistryEntry } from '../api/cluster';
import { useLang } from '../i18n/LangContext';
import {
  reconcileNameserverRegistry,
  type RegistryReconciliationRow,
  type RegistryReconciliationStatus,
} from '../utils/nameserverRegistryReconciliation';

interface Props {
  open: boolean;
  loading: boolean;
  registry: NameserverRegistryEntry[];
  clusters: ClusterInfo[];
  onClose: () => void;
}

type Filter = 'ALL' | 'ATTENTION' | RegistryReconciliationStatus;
const statusColor: Record<RegistryReconciliationStatus, string> = {
  MATCHED: 'green',
  ADDRESS_MISMATCH: 'gold',
  REGISTRY_ONLY: 'red',
  DISCOVERED_ONLY: 'orange',
  AMBIGUOUS: 'purple',
  DUPLICATE_MAPPING: 'magenta',
};

export const NameserverRegistryReconciliationDrawer = ({
  open,
  loading,
  registry,
  clusters,
  onClose,
}: Props) => {
  const { t } = useLang();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [search, setSearch] = useState('');
  const report = useMemo(
    () => reconcileNameserverRegistry(registry, clusters),
    [clusters, registry],
  );
  const rows = useMemo(() => {
    const query = search.trim().toLowerCase();
    return report.rows.filter((row) => {
      const statusMatches =
        filter === 'ALL' ||
        (filter === 'ATTENTION' ? row.status !== 'MATCHED' : row.status === filter);
      const textMatches =
        !query ||
        [
          row.registryName,
          row.clusterName,
          ...row.configuredAddresses,
          ...row.discoveredAddresses,
          ...row.candidateClusters,
        ]
          .join(' ')
          .toLowerCase()
          .includes(query);
      return statusMatches && textMatches;
    });
  }, [filter, report.rows, search]);

  const label = (status: RegistryReconciliationStatus) => t(`cluster.registryReconcile${status}`);
  const addressList = (addresses: string[]) =>
    addresses.length ? (
      <Space size={[0, 4]} wrap>
        {addresses.map((address) => (
          <Tag key={address}>{address}</Tag>
        ))}
      </Space>
    ) : (
      <Typography.Text type="secondary">-</Typography.Text>
    );
  const columns: ColumnsType<RegistryReconciliationRow> = [
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 150,
      render: (status: RegistryReconciliationStatus) => (
        <Tag color={statusColor[status]}>{label(status)}</Tag>
      ),
    },
    {
      title: t('cluster.registryEntry'),
      dataIndex: 'registryName',
      key: 'registryName',
      width: 180,
    },
    {
      title: t('cluster.discoveredCluster'),
      dataIndex: 'clusterName',
      key: 'clusterName',
      width: 180,
    },
    {
      title: t('cluster.configuredAddresses'),
      dataIndex: 'configuredAddresses',
      key: 'configuredAddresses',
      render: addressList,
    },
    {
      title: t('cluster.discoveredAddresses'),
      dataIndex: 'discoveredAddresses',
      key: 'discoveredAddresses',
      render: addressList,
    },
    {
      title: t('cluster.missingAddresses'),
      dataIndex: 'missingAddresses',
      key: 'missingAddresses',
      render: addressList,
    },
    {
      title: t('cluster.unexpectedAddresses'),
      dataIndex: 'unexpectedAddresses',
      key: 'unexpectedAddresses',
      render: addressList,
    },
    {
      title: t('cluster.candidateClusters'),
      dataIndex: 'candidateClusters',
      key: 'candidateClusters',
      render: addressList,
    },
  ];
  const cards = [
    ['cluster.registryEntries', report.summary.registryEntries],
    ['cluster.discoveredClusters', report.summary.discoveredClusters],
    ['cluster.registryMatched', report.summary.matched],
    ['cluster.registryAttention', report.summary.attentionRequired],
  ] as const;

  return (
    <Drawer
      title={t('cluster.registryReconcileTitle')}
      open={open}
      onClose={onClose}
      width={1120}
      destroyOnHidden
    >
      <Alert
        showIcon
        type={report.summary.attentionRequired ? 'warning' : 'success'}
        message={
          report.summary.attentionRequired
            ? t('cluster.registryReconcileDrift')
            : t('cluster.registryReconcileHealthy')
        }
        description={t('cluster.registryReconcileDescription')}
        style={{ marginBottom: 16 }}
      />
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        {cards.map(([key, value]) => (
          <Col xs={12} xl={6} key={key}>
            <Card size="small">
              <Statistic title={t(key)} value={value} />
            </Card>
          </Col>
        ))}
      </Row>
      <Space wrap style={{ marginBottom: 16 }}>
        <Segmented
          value={filter}
          onChange={(value) => setFilter(value as Filter)}
          options={[
            { value: 'ALL', label: t('common.all') },
            { value: 'ATTENTION', label: t('cluster.registryAttention') },
            { value: 'MATCHED', label: label('MATCHED') },
            { value: 'ADDRESS_MISMATCH', label: label('ADDRESS_MISMATCH') },
            { value: 'REGISTRY_ONLY', label: label('REGISTRY_ONLY') },
            { value: 'DISCOVERED_ONLY', label: label('DISCOVERED_ONLY') },
          ]}
        />
        <Input.Search
          allowClear
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t('cluster.registryReconcileSearch')}
          style={{ width: 300 }}
        />
      </Space>
      <Table
        rowKey="key"
        loading={loading}
        columns={columns}
        dataSource={rows}
        size="small"
        pagination={{ pageSize: 20 }}
        scroll={{ x: 1350 }}
        locale={{ emptyText: t('cluster.registryReconcileEmpty') }}
      />
    </Drawer>
  );
};

export default NameserverRegistryReconciliationDrawer;
