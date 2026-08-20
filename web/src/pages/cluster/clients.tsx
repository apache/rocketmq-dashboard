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

import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Flex,
  Input,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  theme,
} from 'antd';
import { DownloadSimple, Eye, MagnifyingGlass } from '@phosphor-icons/react';
import type { ColumnsType } from 'antd/es/table';
import type { TableProps } from 'antd';

import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import type { ClientConnection } from '../../api/connections';
import { listConnections } from '../../services/connectionsService';
import { listRegistryClusters } from '../../services/clusterService';
import type { ClusterInfo } from '../../api/cluster';
import { formatDateTime } from '../../utils/format';
import { buildCsv, downloadCsv, type CsvColumn } from '../../utils/download';
import { tableScrollX } from '../../utils/table';

const { Text } = Typography;
const DEFAULT_LOAD_ERROR = '客户端连接加载失败，请稍后重试';

/* ─── Helpers ─── */

const typeConfig: Record<string, { color: string; label: string }> = {
  Producer: { color: 'blue', label: 'Producer' },
  Consumer: { color: 'green', label: 'Consumer' },
};

const protocolConfig: Record<string, { color: string; label: string }> = {
  gRPC: { color: 'green', label: 'gRPC' },
  Remoting: { color: 'blue', label: 'Remoting' },
};

const languageConfig: Record<string, { color: string; label: string }> = {
  Java: { color: 'default', label: 'Java' },
  Go: { color: 'cyan', label: 'Go' },
  Python: { color: 'purple', label: 'Python' },
  Rust: { color: 'orange', label: 'Rust' },
  Cpp: { color: 'geekblue', label: 'C++' },
  CSharp: { color: 'magenta', label: 'C#' },
  NodeJS: { color: 'lime', label: 'Node.js' },
  PHP: { color: 'gold', label: 'PHP' },
};

const CLIENT_CONNECTION_EXPORT_COLUMNS: CsvColumn<ClientConnection>[] = [
  { header: 'Cluster', value: (connection) => connection.clusterName },
  { header: 'Client ID', value: (connection) => connection.clientId },
  { header: 'Type', value: (connection) => connection.type },
  { header: 'Group/Topic', value: (connection) => connection.groupOrTopic },
  { header: 'Protocol', value: (connection) => connection.protocol },
  { header: 'Address', value: (connection) => connection.address },
  { header: 'Language', value: (connection) => connection.language },
  { header: 'Version', value: (connection) => connection.version },
  { header: 'Connected At', value: (connection) => connection.connectedAt },
  { header: 'Partial', value: (connection) => (connection.partial ? 'true' : 'false') },
];

type ClientTableFilters = Parameters<NonNullable<TableProps<ClientConnection>['onChange']>>[1];

const countBy = (values: string[]) =>
  [
    ...values.reduce(
      (counts, value) => counts.set(value, (counts.get(value) ?? 0) + 1),
      new Map<string, number>(),
    ),
  ]
    .map(([label, count]) => ({ label, count }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label));

type ApiErrorLike = {
  message?: unknown;
  response?: {
    data?: {
      message?: unknown;
    };
  };
};

function getLoadErrorMessage(error: unknown): string {
  const apiError = error as ApiErrorLike;
  const responseMessage = apiError.response?.data?.message;
  if (typeof responseMessage === 'string' && responseMessage.trim()) {
    return responseMessage;
  }
  if (typeof apiError.message === 'string' && apiError.message.trim()) {
    return apiError.message;
  }
  return DEFAULT_LOAD_ERROR;
}

/* ═══════════════════════════════════════════
   ClientsPage
   ═══════════════════════════════════════════ */
const ClientsPage = () => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const [connections, setConnections] = useState<ClientConnection[]>([]);
  const [registryClusters, setRegistryClusters] = useState<ClusterInfo[]>([]);
  const [selectedEndpoint, setSelectedEndpoint] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [clusterFilter, setClusterFilter] = useState<string>('ALL');
  const [selectedConnection, setSelectedConnection] = useState<ClientConnection | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [registryLoadKey, setRegistryLoadKey] = useState(0);
  const [connectionLoadKey, setConnectionLoadKey] = useState(0);
  const [columnFilters, setColumnFilters] = useState<ClientTableFilters>({});
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const selectedCluster = registryClusters.find((cluster) => cluster.endpoint === selectedEndpoint);

  const nameserverOptions = useMemo(
    () =>
      registryClusters.map((cluster) => ({
        value: cluster.endpoint,
        label: `${cluster.name} (${cluster.endpoint})`,
      })),
    [registryClusters],
  );

  const handleNameserverChange = (endpoint: string) => {
    setCurrentPage(1);
    setSelectedEndpoint(endpoint);
    setConnections([]);
    setClusterFilter('ALL');
    setSelectedConnection(null);
    setLoadError(null);
    setLoading(true);
  };

  useEffect(() => {
    let cancelled = false;

    void listRegistryClusters()
      .then((nextClusters) => {
        if (cancelled) return;
        setRegistryClusters(nextClusters);
        setSelectedEndpoint((current) => {
          if (current && nextClusters.some((cluster) => cluster.endpoint === current)) {
            return current;
          }
          return nextClusters[0]?.endpoint;
        });
        setLoadError(null);
      })
      .catch((error) => {
        if (cancelled) return;
        setRegistryClusters([]);
        setSelectedEndpoint(undefined);
        setConnections([]);
        setLoadError(getLoadErrorMessage(error));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [registryLoadKey]);

  useEffect(() => {
    let cancelled = false;
    if (!selectedEndpoint || !selectedCluster) {
      return () => {
        cancelled = true;
      };
    }

    void listConnections({ namesrvAddr: selectedEndpoint })
      .then((nextConnections) => {
        if (!cancelled) {
          setConnections(nextConnections);
          setLoadError(null);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setConnections([]);
          setClusterFilter('ALL');
          setSelectedConnection(null);
          setLoadError(getLoadErrorMessage(error));
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [connectionLoadKey, selectedEndpoint, selectedCluster]);

  /* ─── Cluster options using nsClusterName ─── */
  const clusterOptions = useMemo(() => {
    const clusterNames = [
      ...new Set(connections.map((connection) => connection.clusterName)),
    ].sort();
    return [
      { value: 'ALL', label: t('clients.allClusters') },
      ...clusterNames.map((name) => ({ value: name, label: name })),
    ];
  }, [connections, t]);

  const clusterConnections = useMemo(
    () =>
      clusterFilter === 'ALL'
        ? connections
        : connections.filter((connection) => connection.clusterName === clusterFilter),
    [connections, clusterFilter],
  );

  const connectionStats = useMemo(() => {
    const instances = Array.from(
      new Map(
        clusterConnections.map((connection) => [
          `${connection.type}:${connection.clientId}`,
          connection,
        ]),
      ).values(),
    );
    return {
      total: instances.length,
      producers: instances.filter((connection) => connection.type === 'Producer').length,
      consumers: instances.filter((connection) => connection.type === 'Consumer').length,
      protocols: countBy(instances.map((connection) => connection.protocol)),
      languageVersions: countBy(
        instances.map((connection) => `${connection.language} ${connection.version}`),
      ),
    };
  }, [clusterConnections]);

  /* ─── Filtered data (search + cluster only, table handles column filters) ─── */
  const filtered = useMemo(() => {
    const normalizedSearch = search.toLowerCase();
    return clusterConnections.filter(
      (connection) =>
        connection.clientId.toLowerCase().includes(normalizedSearch) ||
        connection.address?.toLowerCase().includes(normalizedSearch),
    );
  }, [clusterConnections, search]);

  const exportConnections = useMemo(() => {
    const matches = (key: string, value: string) => {
      const selected = columnFilters[key];
      return !selected?.length || selected.some((filterValue) => String(filterValue) === value);
    };
    return filtered.filter(
      (connection) =>
        matches('clusterName', connection.clusterName) &&
        matches('type', connection.type) &&
        matches('protocol', connection.protocol) &&
        matches('language', connection.language),
    );
  }, [columnFilters, filtered]);

  const lastPage = Math.max(1, Math.ceil(exportConnections.length / pageSize));
  const clampedCurrentPage = Math.min(currentPage, lastPage);

  const handleExport = () => {
    const filename = `rocketmq-client-connections-${new Date().toISOString().slice(0, 10)}.csv`;
    const csv = buildCsv(CLIENT_CONNECTION_EXPORT_COLUMNS, exportConnections);
    downloadCsv(filename, csv);
  };

  /* ═══════════════════════════════════════════
     Table Columns (with built-in filters)
     ═══════════════════════════════════════════ */
  const columns: ColumnsType<ClientConnection> = [
    {
      title: t('clients.cluster'),
      dataIndex: 'clusterName',
      key: 'clusterName',
      width: 130,
      filters: clusterOptions
        .filter((option) => option.value !== 'ALL')
        .map((option) => ({ text: option.label, value: option.value })),
      onFilter: (value, record) => record.clusterName === value,
      render: (name: string) => <Text style={{ fontSize: 14 }}>{name}</Text>,
    },
    {
      title: t('clients.clientId'),
      dataIndex: 'clientId',
      key: 'clientId',
      width: 260,
      ellipsis: true,
      render: (id: string) => (
        <Text
          copyable
          style={{
            fontSize: 14,
            fontFamily: 'monospace',
            whiteSpace: 'nowrap',
          }}
        >
          {id}
        </Text>
      ),
    },
    {
      title: t('common.type'),
      dataIndex: 'type',
      key: 'type',
      width: 100,
      filters: [
        { text: 'Producer', value: 'Producer' },
        { text: 'Consumer', value: 'Consumer' },
      ],
      onFilter: (value, record) => record.type === value,
      render: (type: string) => {
        const cfg = typeConfig[type] ?? { label: type };
        return <Text style={{ fontSize: 14 }}>{cfg.label}</Text>;
      },
    },
    {
      title: t('clients.groupOrTopic'),
      dataIndex: 'groupOrTopic',
      key: 'groupOrTopic',
      width: 180,
      ellipsis: true,
      render: (name: string) => (
        <Text strong style={{ fontSize: 14 }}>
          {name}
        </Text>
      ),
    },
    {
      title: t('clients.protocol'),
      dataIndex: 'protocol',
      key: 'protocol',
      width: 110,
      filters: [
        { text: 'gRPC', value: 'gRPC' },
        { text: 'Remoting', value: 'Remoting' },
      ],
      onFilter: (value, record) => record.protocol === value,
      render: (protocol: string) => {
        const cfg = protocolConfig[protocol] ?? { color: 'default', label: protocol };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: t('common.address'),
      dataIndex: 'address',
      key: 'address',
      width: 180,
      render: (addr: string) => (
        <Text style={{ fontSize: 14, fontFamily: 'monospace' }}>{addr}</Text>
      ),
    },
    {
      title: t('clients.language'),
      dataIndex: 'language',
      key: 'language',
      width: 100,
      filters: Object.entries(languageConfig).map(([value, config]) => ({
        text: config.label,
        value,
      })),
      onFilter: (value, record) => record.language === value,
      render: (lang: string) => {
        const cfg = languageConfig[lang] ?? { color: 'default', label: lang };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: t('common.version'),
      dataIndex: 'version',
      key: 'version',
      width: 90,
    },
    {
      title: t('cluster.heartbeat'),
      dataIndex: 'connectedAt',
      key: 'connectedAt',
      width: 170,
      sorter: (a, b) => (a.connectedAt ?? '').localeCompare(b.connectedAt ?? ''),
      render: (d?: string | null) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
          {d ? formatDateTime(d) : '-'}
        </Text>
      ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 90,
      fixed: 'right',
      render: (_: unknown, record: ClientConnection) => (
        <Button
          size="small"
          icon={<Eye size={14} />}
          style={{ borderColor: '#1677ff', color: '#1677ff' }}
          onClick={() => setSelectedConnection(record)}
        >
          {t('common.detail')}
        </Button>
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
        title={t('clients.title')}
        subtitle={`${t('clients.title')} — ${filtered.length} connections`}
      />

      {loadError && (
        <Alert
          showIcon
          type="warning"
          message={loadError}
          style={{ marginBottom: 16 }}
          action={
            <Button
              size="small"
              onClick={() => {
                setLoading(true);
                setLoadError(null);
                setRegistryLoadKey((key) => key + 1);
                setConnectionLoadKey((key) => key + 1);
              }}
            >
              重试
            </Button>
          }
        />
      )}
      {connections.some((connection) => connection.partial) && (
        <Alert
          showIcon
          type="warning"
          message="Producer connections are sampled because the topic scan limit was reached."
          style={{ marginBottom: 16 }}
        />
      )}

      {/* ─── Filter Bar ─── */}
      <Flex justify="space-between" align="center" style={{ marginBottom: 16 }}>
        <Space size={12} wrap>
          <Select
            aria-label="NameServer"
            value={selectedEndpoint}
            onChange={handleNameserverChange}
            placeholder={t('clients.selectNameserverPlaceholder')}
            style={{ width: 240 }}
            options={nameserverOptions}
          />
          <Select
            aria-label={t('clients.cluster')}
            value={clusterFilter}
            onChange={(value) => {
              setClusterFilter(value);
              setCurrentPage(1);
            }}
            style={{ width: 180 }}
            options={clusterOptions}
          />
          <Input.Search
            placeholder={t('clients.searchPlaceholder')}
            allowClear
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setCurrentPage(1);
            }}
            onSearch={(value) => {
              setSearch(value);
              setCurrentPage(1);
            }}
            style={{ width: 280 }}
            prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
          />
        </Space>
        <Button
          icon={<DownloadSimple size={16} />}
          disabled={exportConnections.length === 0}
          onClick={handleExport}
        >
          {t('common.export')}
        </Button>
      </Flex>

      <Flex
        data-testid="connection-statistics"
        gap={32}
        align="flex-start"
        wrap
        style={{
          marginBottom: 16,
          padding: '12px 16px',
          background: token.colorBgContainer,
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: token.borderRadiusLG,
        }}
      >
        <div data-testid="connection-total">
          <Statistic title={t('clients.title')} value={connectionStats.total} />
        </div>
        <div data-testid="producer-total">
          <Statistic title="Producer" value={connectionStats.producers} />
        </div>
        <div data-testid="consumer-total">
          <Statistic title="Consumer" value={connectionStats.consumers} />
        </div>
        <div data-testid="protocol-distribution" style={{ minWidth: 180 }}>
          <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
            {t('clients.protocol')}
          </Text>
          <Flex gap={4} wrap>
            {connectionStats.protocols.length > 0 ? (
              connectionStats.protocols.map(({ label, count }) => (
                <Tag key={label} color={protocolConfig[label]?.color ?? 'default'}>
                  {label}: {count}
                </Tag>
              ))
            ) : (
              <Text type="secondary">{t('common.noData')}</Text>
            )}
          </Flex>
        </div>
        <div data-testid="language-version-distribution" style={{ minWidth: 220 }}>
          <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
            {t('clients.language')} / {t('common.version')}
          </Text>
          <Flex gap={4} wrap style={{ maxHeight: 76, overflowY: 'auto' }}>
            {connectionStats.languageVersions.length > 0 ? (
              connectionStats.languageVersions.map(({ label, count }) => {
                const [language, ...versionParts] = label.split(' ');
                const version = versionParts.join(' ');
                const config = languageConfig[language] ?? { color: 'default', label: language };
                return (
                  <Tag key={label} color={config.color}>
                    {config.label} {version}: {count}
                  </Tag>
                );
              })
            ) : (
              <Text type="secondary">{t('common.noData')}</Text>
            )}
          </Flex>
        </div>
      </Flex>

      {/* ─── Table ─── */}
      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={filtered}
          rowKey={(connection) =>
            `${connection.type}:${connection.clientId}:${connection.groupOrTopic}`
          }
          loading={loading}
          onChange={(pagination, filters, _sorter, extra) => {
            setColumnFilters(filters);
            if (extra.action === 'filter') {
              setCurrentPage(1);
              return;
            }
            setCurrentPage(pagination.current ?? 1);
            setPageSize(pagination.pageSize ?? 20);
          }}
          scroll={{ x: tableScrollX(columns) }}
          pagination={{
            current: clampedCurrentPage,
            pageSize,
            showSizeChanger: true,
            showTotal: (total) => `${t('common.total')} ${total}`,
          }}
          size="small"
        />
      </Card>

      <Modal
        title={t('clients.detailTitle', { id: selectedConnection?.clientId ?? '' })}
        open={Boolean(selectedConnection)}
        onCancel={() => setSelectedConnection(null)}
        footer={<Button onClick={() => setSelectedConnection(null)}>{t('common.close')}</Button>}
        width={640}
        destroyOnHidden
      >
        {selectedConnection && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label={t('clients.clientId')}>
              <Text copyable style={{ fontFamily: 'monospace' }}>
                {selectedConnection.clientId}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('clients.cluster')}>
              <Tag color="blue">{selectedConnection.clusterName}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('common.type')}>
              {typeConfig[selectedConnection.type]?.label ?? selectedConnection.type}
            </Descriptions.Item>
            <Descriptions.Item label={t('clients.groupOrTopic')}>
              {selectedConnection.groupOrTopic}
            </Descriptions.Item>
            <Descriptions.Item label={t('clients.protocol')}>
              <Tag color={protocolConfig[selectedConnection.protocol]?.color ?? 'default'}>
                {protocolConfig[selectedConnection.protocol]?.label ?? selectedConnection.protocol}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('common.address')}>
              <Text style={{ fontFamily: 'monospace' }}>{selectedConnection.address}</Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('clients.language')}>
              <Tag color={languageConfig[selectedConnection.language]?.color ?? 'default'}>
                {languageConfig[selectedConnection.language]?.label ?? selectedConnection.language}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('common.version')}>
              {selectedConnection.version}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.heartbeat')}>
              {selectedConnection.connectedAt ?? '-'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default ClientsPage;
