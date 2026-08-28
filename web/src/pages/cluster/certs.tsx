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
import { PlusOutlined, DeleteOutlined, SyncOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../../components/PageHeader';
import InfoBanner from '../../components/InfoBanner';
import type { K8sCertInfo } from '../../api/cluster';
import {
  listK8sCerts,
  createK8sCert,
  deleteK8sCert,
  renewK8sCert,
} from '../../services/clusterService';
import { useLang } from '../../i18n/LangContext';
import { formatDateTime } from '../../utils/format';
import { tableScrollX } from '../../utils/table';

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

interface RenewCertFormValues {
  certPem: string;
  keyPem?: string;
}

const K8sCertsPage = () => {
  const { t } = useLang();
  const [certs, setCerts] = useState<K8sCertInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [certSearch, setCertSearch] = useState('');
  const [certTypeFilter, setCertTypeFilter] = useState<string>('');
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [renewing, setRenewing] = useState(false);
  const [renewingCert, setRenewingCert] = useState<K8sCertInfo | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [createForm] = Form.useForm<CreateCertFormValues>();
  const [renewForm] = Form.useForm<RenewCertFormValues>();

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

  const normalizedCertSearch = certSearch.trim().toLowerCase();
  const filteredCerts = certs.filter((cert) => {
    const matchSearch =
      !normalizedCertSearch ||
      [cert.k8sId, cert.cluster].some((value) =>
        value.toLowerCase().includes(normalizedCertSearch),
      );
    const matchType = !certTypeFilter || cert.type === certTypeFilter;
    return matchSearch && matchType;
  });

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
      setCerts((previous) => [...previous, created]);
      message.success(t('cert.added', { name: created.k8sId }));
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
      setCerts((previous) => previous.filter((item) => item.id !== cert.id));
      message.success(t('cert.deleted', { name: cert.k8sId }));
    } catch (error: unknown) {
      message.error(getErrorMessage(error));
    } finally {
      setDeletingId(null);
    }
  };

  const openRenewModal = (cert: K8sCertInfo) => {
    setRenewingCert(cert);
    renewForm.resetFields();
  };

  const closeRenewModal = () => {
    setRenewingCert(null);
    renewForm.resetFields();
  };

  const handleRenew = async () => {
    if (!renewingCert) return;
    let values: RenewCertFormValues;
    try {
      values = await renewForm.validateFields();
    } catch {
      return;
    }
    setRenewing(true);
    try {
      const renewed = await renewK8sCert({
        id: renewingCert.id,
        certPem: values.certPem.trim(),
        keyPem: values.keyPem?.trim() || undefined,
      });
      setCerts((previous) => previous.map((item) => (item.id === renewed.id ? renewed : item)));
      message.success(t('cert.renewed', { name: renewed.k8sId }));
      closeRenewModal();
    } catch (error: unknown) {
      message.error(getErrorMessage(error));
    } finally {
      setRenewing(false);
    }
  };

  const certColumns: ColumnsType<K8sCertInfo> = [
    {
      title: t('cert.clusterName'),
      dataIndex: 'cluster',
      key: 'cluster',
      width: 260,
      ellipsis: true,
      sorter: (a, b) => a.cluster.localeCompare(b.cluster),
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: t('cert.k8sId'),
      dataIndex: 'k8sId',
      key: 'k8sId',
      width: 240,
      sorter: (a, b) => a.k8sId.localeCompare(b.k8sId),
      render: (k8sId: string) => (
        <Text style={{ fontFamily: 'monospace', fontSize: 14 }}>{k8sId}</Text>
      ),
    },
    {
      title: t('common.type'),
      dataIndex: 'type',
      key: 'type',
      width: 110,
      sorter: (a, b) => (a.type ?? '').localeCompare(b.type ?? ''),
      render: (type: string | null) => {
        const colorMap: Record<string, string> = {
          TLS: 'blue',
          mTLS: 'purple',
          ServiceAccount: 'orange',
        };
        return type ? <Tag color={colorMap[type] ?? 'default'}>{type}</Tag> : '-';
      },
    },
    {
      title: t('cert.issuer'),
      dataIndex: 'issuer',
      key: 'issuer',
      width: 180,
      sorter: (a, b) => (a.issuer ?? '').localeCompare(b.issuer ?? ''),
      render: (issuer: string | null) => issuer || '-',
      ellipsis: true,
    },
    {
      title: t('cert.expiryTime'),
      dataIndex: 'notAfter',
      key: 'notAfter',
      width: 170,
      sorter: (a, b) => (Date.parse(a.notAfter ?? '') || 0) - (Date.parse(b.notAfter ?? '') || 0),
      render: (iso: string | null) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
          {formatDateTime(iso)}
        </Text>
      ),
    },
    {
      title: t('cert.daysRemaining'),
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
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      sorter: (a, b) => (a.status ?? '').localeCompare(b.status ?? ''),
      render: (status: string | null) => {
        const map: Record<string, { color: string; label: string }> = {
          valid: { color: 'green', label: t('cert.statusValid') },
          expiring: { color: 'orange', label: t('cert.statusExpiring') },
          expired: { color: 'red', label: t('cert.statusExpired') },
        };
        const cfg = status ? (map[status] ?? { color: 'default', label: status }) : null;
        return cfg ? <Tag color={cfg.color}>{cfg.label}</Tag> : '-';
      },
    },
    {
      title: t('common.actions'),
      key: 'action',
      width: 170,
      fixed: 'right',
      render: (_: unknown, cert: K8sCertInfo) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            icon={<SyncOutlined />}
            onClick={() => openRenewModal(cert)}
          >
            {t('cert.renew')}
          </Button>
          <Popconfirm
            title={t('cert.deleteConfirm', { name: cert.k8sId })}
            onConfirm={() => void handleDelete(cert)}
            okText={t('common.delete')}
            cancelText={t('common.cancel')}
            okButtonProps={{ danger: true }}
          >
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
              loading={deletingId === cert.id}
            >
              {t('common.delete')}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title={t('cert.title')}
        subtitle={t('cert.totalCount', { count: filteredCerts.length })}
      />
      <InfoBanner
        data-testid="k8s-cert-local-metadata-notice"
        title={t('cert.localMetadataTitle')}
        description={t('cert.localMetadataDescription')}
      />
      <Flex justify="space-between" style={{ marginBottom: 16 }}>
        <Space>
          <Input.Search
            placeholder={t('cert.searchK8sPlaceholder')}
            allowClear
            onSearch={setCertSearch}
            onChange={(e) => !e.target.value && setCertSearch('')}
            style={{ width: 320 }}
          />
          <Select
            value={certTypeFilter}
            onChange={setCertTypeFilter}
            style={{ width: 160 }}
            options={[
              { value: '', label: t('common.all') },
              { value: 'TLS', label: 'TLS' },
              { value: 'mTLS', label: 'mTLS' },
              { value: 'ServiceAccount', label: 'ServiceAccount' },
            ]}
          />
        </Space>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
          {t('cert.createCert')}
        </Button>
      </Flex>
      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={certColumns}
          dataSource={filteredCerts}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
          size="small"
          scroll={{ x: tableScrollX(certColumns) }}
        />
      </Card>

      <Modal
        title={t('cert.createCert')}
        open={createModalOpen}
        onCancel={() => {
          setCreateModalOpen(false);
          createForm.resetFields();
        }}
        onOk={() => void handleCreate()}
        confirmLoading={creating}
        okText={t('common.add')}
        cancelText={t('common.cancel')}
        width={640}
        destroyOnHidden
      >
        <Form form={createForm} layout="vertical" preserve={false}>
          <Form.Item
            label={t('cert.k8sId')}
            name="k8sId"
            rules={[{ required: true, message: t('cert.k8sIdRequired') }]}
          >
            <Input placeholder={t('cert.k8sIdPlaceholder')} />
          </Form.Item>
          <Form.Item
            label={t('cert.clusterName')}
            name="cluster"
            rules={[{ required: true, message: t('cert.clusterRequired') }]}
          >
            <Input placeholder={t('cert.clusterPlaceholder')} />
          </Form.Item>
          <Form.Item label={t('common.type')} name="type" initialValue="TLS">
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
            label={t('cert.certPemLabel')}
            name="certPem"
            extra={t('cert.certPemCreateExtra')}
          >
            <Input.TextArea
              rows={6}
              placeholder="-----BEGIN CERTIFICATE-----..."
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
          <Form.Item label={t('cert.keyPemLabel')} name="keyPem" extra={t('cert.keyPemExtra')}>
            <Input.TextArea
              rows={6}
              placeholder="-----BEGIN PRIVATE KEY-----..."
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('cert.renewCert', { name: renewingCert?.k8sId ?? '' })}
        open={Boolean(renewingCert)}
        onCancel={closeRenewModal}
        onOk={() => void handleRenew()}
        confirmLoading={renewing}
        okText={t('cert.renew')}
        cancelText={t('common.cancel')}
        width={640}
        destroyOnHidden
      >
        <Form form={renewForm} layout="vertical" preserve={false}>
          <Form.Item
            label={t('cert.renewCertPemLabel')}
            name="certPem"
            rules={[{ required: true, message: t('cert.renewCertPemRequired') }]}
            extra={t('cert.renewCertPemExtra')}
          >
            <Input.TextArea
              rows={8}
              placeholder="-----BEGIN CERTIFICATE-----..."
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
          <Form.Item
            label={t('cert.renewKeyPemLabel')}
            name="keyPem"
            extra={t('cert.renewKeyPemExtra')}
          >
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
