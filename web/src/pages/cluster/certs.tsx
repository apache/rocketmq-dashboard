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
import {
  Table,
  Tag,
  Button,
  Input,
  Select,
  Modal,
  Form,
  Flex,
  Space,
  Typography,
  Card,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { EditOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import PageHeader from '../../components/PageHeader';
import type { K8sCertInfo } from '../../api/cluster';
import {
  createK8sCert,
  deleteK8sCert,
  listK8sCerts,
  updateK8sCert,
} from '../../services/clusterService';

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
  const [submitting, setSubmitting] = useState(false);
  const [certSearch, setCertSearch] = useState('');
  const [certTypeFilter, setCertTypeFilter] = useState<string>('');
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingCert, setEditingCert] = useState<K8sCertInfo | null>(null);
  const [editForm] = Form.useForm();

  useEffect(() => {
    let active = true;
    listK8sCerts()
      .then((data) => {
        if (active) setCerts(data);
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
  }, []);

  const openCreateModal = () => {
    setEditingCert(null);
    editForm.resetFields();
    editForm.setFieldsValue({ type: 'TLS', namespace: 'default' });
    setEditModalOpen(true);
  };

  const closeEditModal = () => {
    setEditModalOpen(false);
    setEditingCert(null);
    editForm.resetFields();
  };

  const saveCert = async () => {
    const values = await editForm.validateFields();
    const data = {
      name: values.name,
      namespace: values.namespace,
      cluster: values.cluster,
      type: values.type,
      issuer: values.issuer,
      san: values.san
        ? String(values.san)
            .split(',')
            .map((value) => value.trim())
            .filter(Boolean)
        : [],
    };

    setSubmitting(true);
    try {
      if (editingCert) {
        const updated = await updateK8sCert({ id: editingCert.id, ...data });
        setCerts((prev) => prev.map((cert) => (cert.id === updated.id ? updated : cert)));
        message.success(`证书「${updated.name}」已更新`);
      } else {
        const created = await createK8sCert(data);
        setCerts((prev) => [...prev, created]);
        message.success(`证书「${created.name}」已创建`);
      }
      closeEditModal();
    } catch (error) {
      message.error(getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  const filteredCerts = certs.filter((cert) => {
    const matchSearch =
      !certSearch ||
      cert.name.toLowerCase().includes(certSearch.toLowerCase()) ||
      cert.cluster.toLowerCase().includes(certSearch.toLowerCase());
    const matchType = !certTypeFilter || cert.type === certTypeFilter;
    return matchSearch && matchType;
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
      key: 'action',
      width: 200,
      render: (_: unknown, record: K8sCertInfo) => (
        <Flex gap={6}>
          <Button
            size="small"
            icon={<EditOutlined />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={() => {
              setEditingCert(record);
              editForm.setFieldsValue({
                name: record.name,
                type: record.type,
                issuer: record.issuer,
                namespace: record.namespace,
                cluster: record.cluster,
                san: record.san.join(', '),
              });
              setEditModalOpen(true);
            }}
          >
            编辑
          </Button>
          <Button
            size="small"
            icon={<DeleteOutlined />}
            style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
            onClick={() => {
              Modal.confirm({
                title: '确认删除',
                content: `确定要删除证书 "${record.name}" 吗？`,
                okText: '确认',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  try {
                    await deleteK8sCert(record.id);
                    setCerts((prev) => prev.filter((c) => c.id !== record.id));
                    message.success(`证书已删除: ${record.name}`);
                  } catch (error) {
                    message.error(getErrorMessage(error));
                    throw error;
                  }
                },
              });
            }}
          >
            删除
          </Button>
        </Flex>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="K8s 证书管理"
        subtitle={`共 ${filteredCerts.length} 个证书`}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            添加证书
          </Button>
        }
      />
      <Flex justify="space-between" style={{ marginBottom: 16 }}>
        <Space>
          <Input.Search
            placeholder="搜索证书名称或集群"
            allowClear
            onSearch={setCertSearch}
            onChange={(e) => !e.target.value && setCertSearch('')}
            style={{ width: 240 }}
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
      </Flex>
      <Card bodyStyle={{ padding: 0 }}>
        <Table
          columns={certColumns}
          dataSource={filteredCerts}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
          size="small"
        />
      </Card>

      {/* Edit Cert Modal */}
      <Modal
        title={editingCert ? `编辑证书 — ${editingCert.name}` : '添加证书'}
        open={editModalOpen}
        onCancel={closeEditModal}
        onOk={saveCert}
        confirmLoading={submitting}
        okText="保存"
        cancelText="取消"
        width={520}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            label="证书名称"
            name="name"
            rules={[{ required: true, message: '请输入证书名称' }]}
          >
            <Input placeholder="例：rocketmq-tls" disabled={Boolean(editingCert)} />
          </Form.Item>
          <Form.Item
            label="K8s 集群名称"
            name="cluster"
            rules={[{ required: true, message: '请输入集群名称' }]}
          >
            <Input placeholder="例：prod-cluster" />
          </Form.Item>
          <Form.Item
            label="类型"
            name="type"
            rules={[{ required: true, message: '请选择证书类型' }]}
          >
            <Select
              options={[
                { value: 'TLS', label: 'TLS' },
                { value: 'mTLS', label: 'mTLS' },
                { value: 'ServiceAccount', label: 'ServiceAccount' },
              ]}
            />
          </Form.Item>
          <Form.Item
            label="签发者"
            name="issuer"
            rules={[{ required: true, message: '请输入签发者' }]}
          >
            <Input placeholder="例：kubernetes-ca" />
          </Form.Item>
          <Form.Item
            label="命名空间"
            name="namespace"
            rules={[{ required: true, message: '请输入命名空间' }]}
          >
            <Input placeholder="例：kube-system" />
          </Form.Item>
          <Form.Item label="SAN" name="san" tooltip="多个域名或 IP 使用英文逗号分隔">
            <Input placeholder="例：broker.example.com, *.rocketmq.example.com" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default K8sCertsPage;
