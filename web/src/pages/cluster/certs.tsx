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

import { useState } from 'react';
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
import { useLang } from '../../i18n/LangContext';
import { mockK8sCerts, type K8sCertInfo } from '../../mock/clusters';
import { formatDateTime } from '../../utils/format';

const { Text } = Typography;

const K8sCertsPage = () => {
  const [certs, setCerts] = useState<K8sCertInfo[]>(mockK8sCerts);
  const { t } = useLang();
  const [certSearch, setCertSearch] = useState('');
  const [certTypeFilter, setCertTypeFilter] = useState<string>('');
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingCert, setEditingCert] = useState<K8sCertInfo | null>(null);
  const [editForm] = Form.useForm();

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
      title: t('cert.clusterName'),
      dataIndex: 'cluster',
      key: 'cluster',
      width: 160,
      sorter: (a, b) => a.cluster.localeCompare(b.cluster),
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: t('cert.certName'),
      dataIndex: 'name',
      key: 'name',
      width: 280,
      sorter: (a, b) => a.name.localeCompare(b.name),
      render: (name: string) => (
        <Text style={{ fontFamily: 'monospace', fontSize: 13 }}>{name}</Text>
      ),
    },
    {
      title: t('common.type'),
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
      title: t('cert.issuer'),
      dataIndex: 'issuer',
      key: 'issuer',
      width: 130,
      sorter: (a, b) => a.issuer.localeCompare(b.issuer),
    },
    {
      title: t('cert.expiryTime'),
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
      sorter: (a, b) => a.status.localeCompare(b.status),
      render: (status: string) => {
        const map: Record<string, { color: string; labelKey: string }> = {
          valid: { color: 'green', labelKey: 'cert.statusValid' },
          expiring: { color: 'orange', labelKey: 'cert.statusExpiring' },
          expired: { color: 'red', labelKey: 'cert.statusExpired' },
        };
        const cfg = map[status] ?? { color: 'default', labelKey: status };
        return <Tag color={cfg.color}>{t(cfg.labelKey)}</Tag>;
      },
    },
    {
      title: t('common.actions'),
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
                title: t('cert.confirmDelete'),
                content: t('cert.deleteConfirm', { name: record.name }),
                okText: t('common.confirm'),
                cancelText: t('common.cancel'),
                okButtonProps: { danger: true },
                onOk: () => {
                  setCerts((prev) => prev.filter((c) => c.id !== record.id));
                  message.success(t('cert.deleted', { name: record.name }));
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
        title={t('nav.certs')}
        subtitle={t('cert.totalCount', { count: filteredCerts.length })}
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => message.info(t('cert.featureWip'))}
          >
            {t('cert.addCert')}
          </Button>
        }
      />
      <Flex justify="space-between" style={{ marginBottom: 16 }}>
        <Space>
          <Input.Search
            placeholder={t('cert.searchPlaceholder')}
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
              { value: '', label: t('common.all') },
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
          pagination={{ pageSize: 20 }}
          size="small"
        />
      </Card>

      {/* Edit Cert Modal */}
      <Modal
        title={t('cert.editCert', { name: editingCert?.name || '' })}
        open={editModalOpen}
        onCancel={() => {
          setEditModalOpen(false);
          editForm.resetFields();
        }}
        onOk={() => {
          editForm.validateFields().then((values) => {
            if (!editingCert) return;
            setCerts((prev) =>
              prev.map((c) =>
                c.id === editingCert.id
                  ? { ...c, issuer: values.issuer, namespace: values.namespace }
                  : c,
              ),
            );
            message.success(t('cert.certUpdated', { name: editingCert.name }));
            setEditModalOpen(false);
            editForm.resetFields();
          });
        }}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        width={520}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label={t('cert.certName')}>
            <Input value={editingCert?.name} disabled />
          </Form.Item>
          <Form.Item label={t('common.type')}>
            <Select
              value={editingCert?.type}
              disabled
              options={[
                { value: 'TLS', label: 'TLS' },
                { value: 'mTLS', label: 'mTLS' },
                { value: 'ServiceAccount', label: 'ServiceAccount' },
              ]}
            />
          </Form.Item>
          <Form.Item label={t('cert.issuer')} name="issuer">
            <Input placeholder={t('cert.issuerPlaceholder')} />
          </Form.Item>
          <Form.Item label={t('cert.namespace')} name="namespace">
            <Input placeholder={t('cert.namespacePlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default K8sCertsPage;
