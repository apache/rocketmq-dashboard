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

import { useEffect, useRef, useState } from 'react';
import {
  Table,
  Tag,
  Input,
  Select,
  Flex,
  Space,
  Typography,
  Card,
  Button,
  Modal,
  Form,
  Popconfirm,
  message,
} from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../../components/PageHeader';
import InfoBanner from '../../components/InfoBanner';
import type { K8sCertInfo } from '../../api/cluster';
import {
  listK8sCertsPage,
  createK8sCert,
  deleteK8sCert,
} from '../../services/clusterService';
import { formatDateTime } from '../../utils/format';

const { Text } = Typography;

const getErrorMessage = (error: unknown): string =>
  error instanceof Error && error.message ? error.message : '请求失败，请稍后重试';

interface CreateCertFormValues {
  k8sId: string;
  cluster: string;
  type: string;
  certPem?: string;
  keyPem?: string;
}

const K8sCertsPage = () => {
  const [certs, setCerts] = useState<K8sCertInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [certSearch, setCertSearch] = useState('');
  const [clusterSearch, setClusterSearch] = useState('');
  const [certTypeFilter, setCertTypeFilter] = useState<string>('');
  const [certStatusFilter, setCertStatusFilter] = useState<string>('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [createForm] = Form.useForm<CreateCertFormValues>();
  const requestId = useRef(0);

  const queryKey = `${certSearch}:${clusterSearch}:${certTypeFilter}:${certStatusFilter}:${page}:${pageSize}`;
  const [prevQueryKey, setPrevQueryKey] = useState(queryKey);
  if (prevQueryKey !== queryKey) {
    setPrevQueryKey(queryKey);
    setLoading(true);
  }

  useEffect(() => {
    let active = true;
    const currentRequest = ++requestId.current;
    listK8sCertsPage({
      search: certSearch.trim() || undefined,
      cluster: clusterSearch.trim() || undefined,
      type: certTypeFilter || undefined,
      status: certStatusFilter || undefined,
      page,
      pageSize,
    })
      .then((data) => {
        if (!active || currentRequest !== requestId.current) return;
        setCerts(data.items);
        setTotal(data.total);
        if (data.items.length === 0 && data.total > 0 && data.page > 1) {
          setPage(Math.max(1, Math.ceil(data.total / data.size)));
        }
        setLoading(false);
      })
      .catch((error: unknown) => {
        if (active && currentRequest === requestId.current) {
          message.error(getErrorMessage(error));
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [certSearch, clusterSearch, certTypeFilter, certStatusFilter, page, pageSize]);

  const applyFilterChange = (setter: (value: string) => void, value: string) => {
    setPage(1);
    setter(value);
  };

  const reloadPage = async () => {
    const currentRequest = ++requestId.current;
    setLoading(true);
    try {
      const data = await listK8sCertsPage({
        search: certSearch.trim() || undefined,
        cluster: clusterSearch.trim() || undefined,
        type: certTypeFilter || undefined,
        status: certStatusFilter || undefined,
        page,
        pageSize,
      });
      if (currentRequest !== requestId.current) return;
      setCerts(data.items);
      setTotal(data.total);
      if (data.items.length === 0 && data.total > 0 && data.page > 1) {
        setPage(Math.max(1, Math.ceil(data.total / data.size)));
      }
    } finally {
      if (currentRequest === requestId.current) setLoading(false);
    }
  };

  const handleCreate = async () => {
    let values: CreateCertFormValues;
    try {
      values = await createForm.validateFields();
    } catch {
      return;
    }
    setCreating(true);
    try {
      const created = await createK8sCert({
        k8sId: values.k8sId.trim(),
        cluster: values.cluster.trim(),
        type: values.type,
        certPem: values.certPem?.trim() || undefined,
        keyPem: values.keyPem?.trim() || undefined,
      });
      await reloadPage();
      message.success(`证书「${created.k8sId}」已添加`);
      setCreateModalOpen(false);
      createForm.resetFields();
    } catch (error: unknown) {
      message.error(getErrorMessage(error));
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (cert: K8sCertInfo) => {
    setDeletingId(cert.id);
    try {
      await deleteK8sCert(cert.id);
      await reloadPage();
      message.success(`证书「${cert.k8sId}」已删除`);
    } catch (error: unknown) {
      message.error(getErrorMessage(error));
    } finally {
      setDeletingId(null);
    }
  };

  const certColumns: ColumnsType<K8sCertInfo> = [
    {
      title: 'K8s 集群名称',
      dataIndex: 'cluster',
      key: 'cluster',
      width: 260,
      ellipsis: true,
      sorter: (a, b) => a.cluster.localeCompare(b.cluster),
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: 'k8s ID',
      dataIndex: 'k8sId',
      key: 'k8sId',
      width: 240,
      sorter: (a, b) => a.k8sId.localeCompare(b.k8sId),
      render: (k8sId: string) => (
        <Text style={{ fontFamily: 'monospace', fontSize: 14 }}>{k8sId}</Text>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 110,
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
      width: 180,
      sorter: (a, b) => a.issuer.localeCompare(b.issuer),
      ellipsis: true,
    },
    {
      title: '到期时间',
      dataIndex: 'notAfter',
      key: 'notAfter',
      width: 170,
      sorter: (a, b) => new Date(a.notAfter).getTime() - new Date(b.notAfter).getTime(),
      render: (iso: string) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
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
      key: 'action',
      width: 90,
      fixed: 'right',
      render: (_: unknown, cert: K8sCertInfo) => (
        <Popconfirm
          title={`确定要删除证书「${cert.k8sId}」吗？`}
          onConfirm={() => void handleDelete(cert)}
          okText="删除"
          cancelText="取消"
          okButtonProps={{ danger: true }}
        >
          <Button
            type="link"
            size="small"
            danger
            icon={<DeleteOutlined />}
            loading={deletingId === cert.id}
          >
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="K8s 证书管理" subtitle={`共 ${total} 个证书`} />
      <InfoBanner
        data-testid="k8s-cert-local-metadata-notice"
        title="当前证书记录仅保存为 Studio 本地元数据"
        description="创建、续期和删除操作尚不会应用到 Kubernetes 集群或 cert-manager。请在集群侧管理实际证书，直到 Kubernetes Provider 接入完成。"
      />
      <Flex justify="space-between" style={{ marginBottom: 16 }}>
        <Space>
          <Input.Search
            placeholder="搜索 k8s ID 或集群"
            allowClear
            onSearch={(value) => applyFilterChange(setCertSearch, value)}
            onChange={(e) => !e.target.value && applyFilterChange(setCertSearch, '')}
            style={{ width: 320 }}
          />
          <Input.Search
            placeholder="搜索集群"
            allowClear
            onSearch={(value) => applyFilterChange(setClusterSearch, value)}
            onChange={(e) => !e.target.value && applyFilterChange(setClusterSearch, '')}
            style={{ width: 200 }}
          />
          <Select
            value={certTypeFilter}
            onChange={(value) => applyFilterChange(setCertTypeFilter, value)}
            style={{ width: 160 }}
            options={[
              { value: '', label: '全部' },
              { value: 'TLS', label: 'TLS' },
              { value: 'mTLS', label: 'mTLS' },
              { value: 'ServiceAccount', label: 'ServiceAccount' },
            ]}
          />
          <Select
            value={certStatusFilter}
            onChange={(value) => applyFilterChange(setCertStatusFilter, value)}
            style={{ width: 140 }}
            options={[
              { value: '', label: '全部状态' },
              { value: 'valid', label: '有效' },
              { value: 'expiring', label: '即将过期' },
              { value: 'expired', label: '已过期' },
            ]}
          />
        </Space>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
          新增证书
        </Button>
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
          scroll={{ x: 1400 }}
        />
      </Card>

      <Modal
        title="新增证书"
        open={createModalOpen}
        onCancel={() => {
          setCreateModalOpen(false);
          createForm.resetFields();
        }}
        onOk={() => void handleCreate()}
        confirmLoading={creating}
        okText="添加"
        cancelText="取消"
        width={640}
        destroyOnHidden
      >
        <Form form={createForm} layout="vertical" preserve={false}>
          <Form.Item
            label="k8s ID"
            name="k8sId"
            rules={[{ required: true, message: '请输入 k8s ID' }]}
          >
            <Input placeholder="例如：kubernetes-daily" />
          </Form.Item>
          <Form.Item
            label="K8s 集群名称"
            name="cluster"
            rules={[{ required: true, message: '请输入集群名称' }]}
          >
            <Input placeholder="例如：kubernetes（120.26.99.191:6443）" />
          </Form.Item>
          <Form.Item label="类型" name="type" initialValue="TLS">
            <Select
              virtual={false}
              options={[
                { value: 'TLS', label: 'TLS' },
                { value: 'mTLS', label: 'mTLS' },
                { value: 'ServiceAccount', label: 'ServiceAccount' },
              ]}
            />
          </Form.Item>
          <Form.Item
            label="证书内容（PEM）"
            name="certPem"
            extra="粘贴 PEM 格式证书，签发者、有效期与 SAN 将自动解析；留空时有效期按一年占位"
          >
            <Input.TextArea
              rows={6}
              placeholder="-----BEGIN CERTIFICATE-----..."
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
          <Form.Item label="私钥内容（PEM）" name="keyPem" extra="仅保存，不会在页面展示或返回">
            <Input.TextArea
              rows={6}
              placeholder="-----BEGIN PRIVATE KEY-----..."
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default K8sCertsPage;
