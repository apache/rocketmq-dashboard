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
import { Table, Card, Tag, Space, Input, Select, Flex, Typography } from 'antd';
import { MagnifyingGlass } from '@phosphor-icons/react';
import type { ColumnsType } from 'antd/es/table';

import PageHeader from '../../components/PageHeader';
import { mockClients } from '../../mock/clients';
import type { ClientConnection } from '../../mock/clients';
import clusters from '../../mock/clusters';
import { useLang } from '../../i18n/LangContext';

const { Text } = Typography;

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
};

/* ═══════════════════════════════════════════
   ClientsPage
   ═══════════════════════════════════════════ */
const ClientsPage = () => {
  const { t } = useLang();
  const [search, setSearch] = useState('');
  const [clusterFilter, setClusterFilter] = useState<string>('ALL');

  /* ─── Cluster options using nsClusterName ─── */
  const clusterOptions = useMemo(() => {
    return [
      { value: 'ALL', label: t('clients.allClusters') },
      ...clusters.map((c) => ({ value: c.nsClusterName, label: c.nsClusterName })),
    ];
  }, []);

  /* ─── Filtered data (search + cluster only, table handles column filters) ─── */
  const filtered = useMemo(() => {
    let data = mockClients.filter(
      (c) => c.clientId.toLowerCase().includes(search.toLowerCase()) || c.address.includes(search),
    );

    if (clusterFilter !== 'ALL') {
      data = data.filter((c) => c.clusterName === clusterFilter);
    }

    return data;
  }, [search, clusterFilter]);

  /* ═══════════════════════════════════════════
     Table Columns (with built-in filters)
     ═══════════════════════════════════════════ */
  const columns: ColumnsType<ClientConnection> = [
    {
      title: t('clients.cluster'),
      dataIndex: 'clusterName',
      key: 'clusterName',
      width: 130,
      filters: clusters.map((c) => ({ text: c.nsClusterName, value: c.nsClusterName })),
      onFilter: (value, record) => record.clusterName === value,
      render: (name: string) => (
        <Tag color="blue" style={{ fontSize: 12 }}>
          {name}
        </Tag>
      ),
    },
    {
      title: t('clients.clientId'),
      dataIndex: 'clientId',
      key: 'clientId',
      width: 260,
      render: (id: string) => (
        <Text
          copyable
          style={{
            fontSize: 13,
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
        return <Text style={{ fontSize: 13 }}>{cfg.label}</Text>;
      },
    },
    {
      title: t('clients.groupOrTopic'),
      dataIndex: 'groupOrTopic',
      key: 'groupOrTopic',
      width: 180,
      render: (name: string) => (
        <Text strong style={{ fontSize: 13 }}>
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
        <Text style={{ fontSize: 13, fontFamily: 'monospace' }}>{addr}</Text>
      ),
    },
    {
      title: t('clients.language'),
      dataIndex: 'language',
      key: 'language',
      width: 100,
      filters: [
        { text: 'Java', value: 'Java' },
        { text: 'Go', value: 'Go' },
        { text: 'Python', value: 'Python' },
        { text: 'Rust', value: 'Rust' },
      ],
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
      sorter: (a, b) => a.connectedAt.localeCompare(b.connectedAt),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {d}
        </Text>
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

      {/* ─── Filter Bar ─── */}
      <Flex justify="space-between" align="center" style={{ marginBottom: 16 }}>
        <Space size={12} wrap>
          <Select
            value={clusterFilter}
            onChange={setClusterFilter}
            style={{ width: 180 }}
            options={clusterOptions}
          />
          <Input.Search
            placeholder={t('clients.searchPlaceholder')}
            allowClear
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={setSearch}
            style={{ width: 280 }}
            prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
          />
        </Space>
      </Flex>

      {/* ─── Table ─── */}
      <Card bodyStyle={{ padding: 0 }}>
        <Table
          columns={columns}
          dataSource={filtered}
          rowKey="clientId"
          scroll={{ x: 1320 }}
          pagination={{
            pageSize: 20,
            showSizeChanger: true,
            showTotal: (total) => `${t('common.total')} ${total}`,
          }}
          size="small"
        />
      </Card>
    </div>
  );
};

export default ClientsPage;
