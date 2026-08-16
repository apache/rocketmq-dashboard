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
import { Table, Tag, Input, Select, Flex, Space, Typography, Card, Alert, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../../components/PageHeader';
import type { K8sCertInfo } from '../../api/cluster';
import { listK8sCerts } from '../../services/clusterService';
import { formatDateTime } from '../../utils/format';

const { Text } = Typography;

const getErrorMessage = (error: unknown): string =>
  error instanceof Error && error.message ? error.message : '请求失败，请稍后重试';

const K8sCertsPage = () => {
  const [certs, setCerts] = useState<K8sCertInfo[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(true);
  const [certSearch, setCertSearch] = useState('');
  const [certClusterFilter, setCertClusterFilter] = useState('');
  const [certTypeFilter, setCertTypeFilter] = useState<string>('');
  const [certStatusFilter, setCertStatusFilter] = useState<string>('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    listK8sCerts({
      search: certSearch.trim() || undefined,
      cluster: certClusterFilter || undefined,
      type: certTypeFilter || undefined,
      status: certStatusFilter || undefined,
      page,
      pageSize,
    })
      .then((data) => {
        if (active) {
          setCerts(data.items);
          setTotal(data.total);
        }
      })
      .catch((error: unknown) => {
        if (active) message.error(getErrorMessage(error));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [certSearch, certClusterFilter, certTypeFilter, certStatusFilter, page, pageSize]);

  const resetToFirstPage = (setFilter: (value: string) => void) => (value: string) => {
    setFilter(value);
    setPage(1);
  };

  const certColumns: ColumnsType<K8sCertInfo> = [
    {
      title: 'K8s 集群名称',
      dataIndex: 'cluster',
      key: 'cluster',
      width: 160,
      sorter: (a, b) => a.cluster.localeCompare(b.cluster),
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: '证书名称',
      dataIndex: 'name',
      key: 'name',
      width: 280,
      sorter: (a, b) => a.name.localeCompare(b.name),
      render: (name: string) => (
        <Text style={{ fontFamily: 'monospace', fontSize: 13 }}>{name}</Text>
      ),
    },
    {
      title: 'SAN',
      dataIndex: 'san',
      key: 'san',
      width: 260,
      render: (san: string[] | null) =>
        san?.length ? (
          <Flex wrap gap={4}>
            {san.map((value) => (
              <Tag key={value}>{value}</Tag>
            ))}
          </Flex>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 130,
      sorter: (a, b) => a.type.localeCompare(b.type),
      render: (type: string) => {
        const colorMap: Record<string, string> = {
          TLS: 'blue',
          mTLS: 'purple',
          ServiceAccount: 'orange',
        };
        return <Tag color={colorMap[type] ?? 'default'}>{type}</Tag>;
      },
    },
    {
      title: '签发者',
      dataIndex: 'issuer',
      key: 'issuer',
      width: 130,
      sorter: (a, b) => a.issuer.localeCompare(b.issuer),
    },
    {
      title: '到期时间',
      dataIndex: 'notAfter',
      key: 'notAfter',
      width: 170,
      sorter: (a, b) => new Date(a.notAfter).getTime() - new Date(b.notAfter).getTime(),
      render: (iso: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {formatDateTime(iso)}
        </Text>
      ),
    },
    {
      title: '剩余天数',
      dataIndex: 'daysRemaining',
      key: 'daysRemaining',
      width: 100,
      sorter: (a, b) => a.daysRemaining - b.daysRemaining,
      render: (days: number) => (
        <Text
          style={{
            color: days <= 0 ? '#ff4d4f' : days <= 30 ? '#faad14' : '#52c41a',
            fontWeight: 500,
          }}
        >
          {days}
        </Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      sorter: (a, b) => a.status.localeCompare(b.status),
      render: (status: string) => {
        const map: Record<string, { color: string; label: string }> = {
          valid: { color: 'green', label: '有效' },
          expiring: { color: 'orange', label: '即将过期' },
          expired: { color: 'red', label: '已过期' },
        };
        const cfg = map[status] ?? { color: 'default', label: status };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="K8s 证书管理" subtitle={`共 ${total} 个证书`} />
      <Alert
        data-testid="k8s-cert-local-metadata-notice"
        type="warning"
        showIcon
        message="当前证书记录仅保存为 Studio 本地元数据"
        description="创建、续期和删除操作尚不会应用到 Kubernetes 集群或 cert-manager。请在集群侧管理实际证书，直到 Kubernetes Provider 接入完成。"
        style={{ marginBottom: 16 }}
      />
      <Flex justify="space-between" style={{ marginBottom: 16 }}>
        <Space>
          <Input.Search
            placeholder="搜索证书名称、集群或 SAN"
            allowClear
            value={certSearch}
            onChange={(event) => resetToFirstPage(setCertSearch)(event.target.value)}
            style={{ width: 280 }}
          />
          <Input
            aria-label="按 K8s 集群筛选"
            placeholder="K8s 集群名称"
            allowClear
            value={certClusterFilter}
            onChange={(event) => resetToFirstPage(setCertClusterFilter)(event.target.value)}
            style={{ width: 180 }}
          />
          <Select
            aria-label="按证书类型筛选"
            value={certTypeFilter}
            onChange={resetToFirstPage(setCertTypeFilter)}
            style={{ width: 140 }}
            options={[
              { value: '', label: '全部类型' },
              { value: 'TLS', label: 'TLS' },
              { value: 'mTLS', label: 'mTLS' },
              { value: 'ServiceAccount', label: 'ServiceAccount' },
            ]}
          />
          <Select
            aria-label="按状态筛选"
            value={certStatusFilter}
            onChange={resetToFirstPage(setCertStatusFilter)}
            style={{ width: 160 }}
            options={[
              { value: '', label: '全部状态' },
              { value: 'valid', label: '有效' },
              { value: 'expiring', label: '即将过期' },
              { value: 'expired', label: '已过期' },
            ]}
          />
        </Space>
      </Flex>
      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={certColumns}
          dataSource={certs}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: [20, 50, 100],
            onChange: (nextPage, nextPageSize) => {
              setPage(nextPage);
              setPageSize(nextPageSize);
            },
          }}
          size="small"
          scroll={{ x: 1600 }}
        />
      </Card>
    </div>
  );
};

export default K8sCertsPage;
