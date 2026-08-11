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

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Table,
  Tag,
  Input,
  Modal,
  Select,
  Flex,
  Space,
  Typography,
  Card,
  Alert,
  message,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../../components/PageHeader';
import type { K8sCertInfo } from '../../api/cluster';
import {
  createK8sCert,
  deleteK8sCert,
  listK8sCerts,
  renewK8sCert,
  updateK8sCert,
} from '../../services/clusterService';
import K8sCertEditor from './K8sCertEditor';

const { Text } = Typography;

const formatDateTime = (iso: string): string => {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

const getErrorMessage = (error: unknown): string =>
  error instanceof Error && error.message ? error.message : '请求失败，请稍后重试';

const K8sCertsPage = () => {
  const [certs, setCerts] = useState<K8sCertInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [certSearch, setCertSearch] = useState('');
  const [certTypeFilter, setCertTypeFilter] = useState<string>('');
  const [certNamespaceFilter, setCertNamespaceFilter] = useState<string>('');
  const [selectedIds, setSelectedIds] = useState<React.Key[]>([]);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingCert, setEditingCert] = useState<K8sCertInfo | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [bulkLoading, setBulkLoading] = useState(false);

  const loadCerts = useCallback(async () => {
    setLoading(true);
    try {
      setCerts(await listK8sCerts());
    } catch (error) {
      message.error(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadCerts(), 0);
    return () => window.clearTimeout(timer);
  }, [loadCerts]);

  const saveCert = async (data: Partial<K8sCertInfo>) => {
    setSubmitting(true);
    try {
      if (data.id) {
        await updateK8sCert(data);
        message.success('证书信息已更新');
      } else {
        await createK8sCert(data);
        message.success('证书已创建');
      }
      setEditorOpen(false);
      setEditingCert(null);
      await loadCerts();
    } catch (error) {
      message.error(getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  const renewOne = async (cert: K8sCertInfo) => {
    try {
      await renewK8sCert(cert.id);
      message.success(`证书「${cert.name}」已续期一年`);
      await loadCerts();
    } catch (error) {
      message.error(getErrorMessage(error));
    }
  };

  const deleteOne = async (cert: K8sCertInfo) => {
    try {
      await deleteK8sCert(cert.id);
      setSelectedIds((ids) => ids.filter((id) => id !== cert.id));
      message.success(`证书「${cert.name}」已删除`);
      await loadCerts();
    } catch (error) {
      message.error(getErrorMessage(error));
    }
  };

  const runBulkAction = async (action: 'renew' | 'delete') => {
    setBulkLoading(true);
    try {
      const operation = action === 'renew' ? renewK8sCert : deleteK8sCert;
      const results = await Promise.allSettled(selectedIds.map((id) => operation(String(id))));
      const succeeded = results.filter((result) => result.status === 'fulfilled').length;
      const failed = results.length - succeeded;
      if (failed) message.warning(`已完成 ${succeeded} 个，失败 ${failed} 个`);
      else message.success(`已${action === 'renew' ? '续期' : '删除'} ${succeeded} 个证书`);
      setSelectedIds([]);
      await loadCerts();
    } finally {
      setBulkLoading(false);
    }
  };

  const normalizedCertSearch = certSearch.trim().toLowerCase();
  const namespaceOptions = Array.from(new Set(certs.map((cert) => cert.namespace)))
    .sort((a, b) => a.localeCompare(b))
    .map((namespace) => ({ value: namespace, label: namespace }));
  const filteredCerts = certs.filter((cert) => {
    const matchSearch =
      !normalizedCertSearch ||
      [cert.name, cert.cluster, cert.namespace, ...(cert.san ?? [])].some((value) =>
        value.toLowerCase().includes(normalizedCertSearch),
      );
    const matchType = !certTypeFilter || cert.type === certTypeFilter;
    const matchNamespace = !certNamespaceFilter || cert.namespace === certNamespaceFilter;
    return matchSearch && matchType && matchNamespace;
  });

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
      title: '命名空间',
      dataIndex: 'namespace',
      key: 'namespace',
      width: 150,
      sorter: (a, b) => a.namespace.localeCompare(b.namespace),
      render: (namespace: string) => <Text code>{namespace}</Text>,
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
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 210,
      render: (_, cert) => (
        <Space size={4}>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingCert(cert);
              setEditorOpen(true);
            }}
          >
            编辑
          </Button>
          <Button size="small" icon={<ReloadOutlined />} onClick={() => void renewOne(cert)}>
            续期
          </Button>
          <Button
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() =>
              Modal.confirm({
                title: `删除证书「${cert.name}」？`,
                content: '此操作仅删除 Studio 中保存的证书元数据，且不可恢复。',
                okText: '删除',
                okButtonProps: { danger: true },
                onOk: () => deleteOne(cert),
              })
            }
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="K8s 证书管理" subtitle={`共 ${filteredCerts.length} 个证书`} />
      <Alert
        data-testid="k8s-cert-local-metadata-notice"
        type="warning"
        showIcon
        message="证书记录保存为 Studio 本地元数据"
        description="本页的创建、编辑、续期和删除不会直接修改 Kubernetes Secret 或 cert-manager 资源；实际证书仍需在集群侧同步管理。"
        style={{ marginBottom: 16 }}
      />
      <Flex justify="space-between" gap={12} wrap="wrap" style={{ marginBottom: 16 }}>
        <Space>
          <Input.Search
            placeholder="搜索证书名称、集群、命名空间或 SAN"
            allowClear
            onSearch={setCertSearch}
            onChange={(e) => !e.target.value && setCertSearch('')}
            style={{ width: 320 }}
          />
          <Select
            aria-label="按命名空间筛选"
            value={certNamespaceFilter}
            onChange={setCertNamespaceFilter}
            style={{ width: 180 }}
            options={[{ value: '', label: '全部命名空间' }, ...namespaceOptions]}
          />
          <Select
            value={certTypeFilter}
            onChange={setCertTypeFilter}
            style={{ width: 160 }}
            options={[
              { value: '', label: '全部' },
              { value: 'TLS', label: 'TLS' },
              { value: 'mTLS', label: 'mTLS' },
              { value: 'ServiceAccount', label: 'ServiceAccount' },
            ]}
          />
        </Space>
        <Space>
          {selectedIds.length > 0 && (
            <>
              <Button
                icon={<ReloadOutlined />}
                loading={bulkLoading}
                onClick={() => void runBulkAction('renew')}
              >
                批量续期 ({selectedIds.length})
              </Button>
              <Button
                danger
                icon={<DeleteOutlined />}
                loading={bulkLoading}
                onClick={() =>
                  Modal.confirm({
                    title: `删除选中的 ${selectedIds.length} 个证书？`,
                    content: '删除后无法恢复。',
                    okText: '批量删除',
                    okButtonProps: { danger: true },
                    onOk: () => runBulkAction('delete'),
                  })
                }
              >
                批量删除
              </Button>
            </>
          )}
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingCert(null);
              setEditorOpen(true);
            }}
          >
            添加证书
          </Button>
        </Space>
      </Flex>
      <Card bodyStyle={{ padding: 0 }}>
        <Table
          columns={certColumns}
          dataSource={filteredCerts}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
          size="small"
          scroll={{ x: 1750 }}
          rowSelection={{ selectedRowKeys: selectedIds, onChange: setSelectedIds }}
        />
      </Card>
      <K8sCertEditor
        open={editorOpen}
        certificate={editingCert}
        loading={submitting}
        onCancel={() => {
          setEditorOpen(false);
          setEditingCert(null);
        }}
        onSubmit={saveCert}
      />
    </div>
  );
};

export default K8sCertsPage;
