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

import { useRef, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  InputNumber,
  Spin,
  Row,
  Col,
  Statistic,
  Descriptions,
  Popconfirm,
  Tooltip,
  Alert,
  App,
} from 'antd';
import {
  Plus,
  ArrowClockwise,
  Pencil,
  Trash,
  Eye,
  Database,
  CheckCircle,
  XCircle,
  CloudWarning,
} from '@phosphor-icons/react';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import {
  queryNamespaceList,
  queryNamespaceCapability,
  createNamespace,
  updateNamespace,
  deleteNamespace,
  type NamespaceItem,
} from '../../api/namespace';

const NamespacePage: React.FC = () => {
  const { t } = useLang();
  const { message: msg } = App.useApp();
  const [form] = Form.useForm();

  const [loading, setLoading] = useState(false);
  const [namespaces, setNamespaces] = useState<NamespaceItem[]>([]);
  const [namespaceSupported, setNamespaceSupported] = useState(true);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [formModalVisible, setFormModalVisible] = useState(false);
  const [selectedNamespace, setSelectedNamespace] = useState<NamespaceItem | null>(null);
  const [isEdit, setIsEdit] = useState(false);
  const [stats, setStats] = useState({
    totalNamespaces: 0,
    enabledNamespaces: 0,
    totalTopicQuota: 0,
    totalGroupQuota: 0,
  });

  const initialized = useRef<boolean | null>(null);
  if (initialized.current == null) {
    initialized.current = true;
    const loadData = async () => {
      try {
        const cap = await queryNamespaceCapability();
        setNamespaceSupported(cap.namespaceSupported);
      } catch {
        // capability check failed, assume supported
      }
      await loadNamespaces();
    };
    loadData();
  }

  const loadNamespaces = async () => {
    setLoading(true);
    try {
      const list = await queryNamespaceList();
      setNamespaces(list);
      const enabledCount = list.filter((ns) => ns.status === 'ENABLED').length;
      const totalTopicQuota = list.reduce(
        (sum, ns) => sum + (ns.quotaConfig?.maxTopicCount || 0),
        0,
      );
      const totalGroupQuota = list.reduce(
        (sum, ns) => sum + (ns.quotaConfig?.maxConsumerGroupCount || 0),
        0,
      );
      setStats({
        totalNamespaces: list.length,
        enabledNamespaces: enabledCount,
        totalTopicQuota,
        totalGroupQuota,
      });
    } catch {
      msg.error(t('ns.fetchFailed'));
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setIsEdit(false);
    setSelectedNamespace(null);
    form.resetFields();
    setFormModalVisible(true);
  };

  const handleEdit = (record: NamespaceItem) => {
    setIsEdit(true);
    setSelectedNamespace(record);
    form.setFieldsValue({
      namespaceName: record.namespaceName,
      displayName: record.displayName,
      description: record.description,
      clusterName: record.clusterName,
      maxTopicCount: record.quotaConfig?.maxTopicCount,
      maxConsumerGroupCount: record.quotaConfig?.maxConsumerGroupCount,
      storageQuotaGB: record.quotaConfig?.storageQuotaGB,
      qpsLimit: record.quotaConfig?.qpsLimit,
      connectionLimit: record.quotaConfig?.connectionLimit,
    });
    setFormModalVisible(true);
  };

  const handleView = (record: NamespaceItem) => {
    setSelectedNamespace(record);
    setDetailModalVisible(true);
  };

  const handleDelete = async (name: string) => {
    setLoading(true);
    try {
      await deleteNamespace(name);
      msg.success(t('ns.deleteSuccess'));
      await loadNamespaces();
    } catch {
      msg.error(t('ns.deleteFailed'));
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      const payload: Partial<NamespaceItem> = {
        namespaceName: values.namespaceName,
        displayName: values.displayName,
        description: values.description,
        clusterName: values.clusterName,
        status: 'ENABLED',
        quotaConfig: {
          maxTopicCount: values.maxTopicCount,
          maxConsumerGroupCount: values.maxConsumerGroupCount,
          storageQuotaGB: values.storageQuotaGB,
          qpsLimit: values.qpsLimit,
          connectionLimit: values.connectionLimit,
        },
      };

      if (isEdit) {
        await updateNamespace(payload);
        msg.success(t('ns.updateSuccess'));
      } else {
        await createNamespace(payload);
        msg.success(t('ns.createSuccess'));
      }
      setFormModalVisible(false);
      form.resetFields();
      await loadNamespaces();
    } catch {
      msg.error(isEdit ? t('ns.updateFailed') : t('ns.createFailed'));
    } finally {
      setLoading(false);
    }
  };

  const columns: ColumnsType<NamespaceItem> = [
    {
      title: t('ns.name'),
      dataIndex: 'namespaceName',
      key: 'namespaceName',
      render: (text: string, record: NamespaceItem) => (
        <Space>
          <span style={{ fontWeight: 500 }}>{text}</span>
          {record.defaultNamespace && <Tag color="blue">{t('ns.default')}</Tag>}
        </Space>
      ),
    },
    {
      title: t('ns.displayName'),
      dataIndex: 'displayName',
      key: 'displayName',
      render: (text: string) => text || '-',
    },
    {
      title: t('ns.cluster'),
      dataIndex: 'clusterName',
      key: 'clusterName',
      render: (text: string) => text || '-',
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag
          color={status === 'ENABLED' ? 'success' : 'default'}
          icon={status === 'ENABLED' ? <CheckCircle size={12} /> : <XCircle size={12} />}
        >
          {status || 'UNKNOWN'}
        </Tag>
      ),
    },
    {
      title: t('ns.topicLimit'),
      key: 'topicLimit',
      render: (_: unknown, record: NamespaceItem) => record.quotaConfig?.maxTopicCount || '-',
    },
    {
      title: t('ns.groupLimit'),
      key: 'groupLimit',
      render: (_: unknown, record: NamespaceItem) =>
        record.quotaConfig?.maxConsumerGroupCount || '-',
    },
    {
      title: t('ns.storage'),
      key: 'storage',
      render: (_: unknown, record: NamespaceItem) => record.quotaConfig?.storageQuotaGB || '-',
    },
    {
      title: t('common.actions'),
      key: 'operation',
      render: (_: unknown, record: NamespaceItem) => (
        <Space size="small">
          <Tooltip title={t('ns.viewDetail')}>
            <Button
              type="link"
              size="small"
              icon={<Eye size={16} />}
              onClick={() => handleView(record)}
            />
          </Tooltip>
          {!record.defaultNamespace && (
            <>
              <Tooltip title={t('common.edit')}>
                <Button
                  type="link"
                  size="small"
                  icon={<Pencil size={16} />}
                  onClick={() => handleEdit(record)}
                />
              </Tooltip>
              <Popconfirm
                title={t('ns.confirmDelete')}
                onConfirm={() => handleDelete(record.namespaceName)}
                okText={t('common.yes')}
                cancelText={t('common.no')}
              >
                <Tooltip title={t('common.delete')}>
                  <Button type="link" size="small" danger icon={<Trash size={16} />} />
                </Tooltip>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ];

  if (!namespaceSupported) {
    return (
      <div style={{ padding: 24 }}>
        <Alert
          message={t('ns.notSupported')}
          description={t('ns.notSupportedDesc')}
          type="warning"
          showIcon
          icon={<CloudWarning size={24} />}
        />
      </div>
    );
  }

  return (
    <div style={{ padding: 24, minHeight: 'calc(100vh - 120px)' }}>
      <PageHeader title={t('ns.title')} />

      <Spin spinning={loading} tip={t('common.loading')}>
        {/* Stats Cards */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title={t('ns.total')}
                value={stats.totalNamespaces}
                prefix={<Database size={20} />}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title={t('ns.enabled')}
                value={stats.enabledNamespaces}
                suffix={`/ ${stats.totalNamespaces}`}
                valueStyle={{ color: '#3f8600' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title={t('ns.totalTopicQuota')}
                value={stats.totalTopicQuota}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title={t('ns.totalGroupQuota')}
                value={stats.totalGroupQuota}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
        </Row>

        {/* Action Bar */}
        <Card style={{ marginBottom: 16 }}>
          <Space>
            <Button icon={<ArrowClockwise size={16} />} onClick={loadNamespaces}>
              {t('common.refresh')}
            </Button>
            <Button type="primary" icon={<Plus size={16} />} onClick={handleCreate}>
              {t('ns.create')}
            </Button>
          </Space>
        </Card>

        {/* Namespace Table */}
        <Card title={t('ns.list')}>
          <Table
            columns={columns}
            dataSource={namespaces}
            rowKey="namespaceName"
            pagination={false}
            size="middle"
          />
        </Card>
      </Spin>

      {/* Detail Modal */}
      <Modal
        title={`${t('ns.detail')} - ${selectedNamespace?.namespaceName}`}
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            {t('common.close')}
          </Button>,
        ]}
        width={700}
      >
        {selectedNamespace && (
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label={t('ns.name')} span={2}>
              {selectedNamespace.namespaceName}
            </Descriptions.Item>
            <Descriptions.Item label={t('ns.displayName')}>
              {selectedNamespace.displayName || '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('common.status')}>
              <Tag color={selectedNamespace.status === 'ENABLED' ? 'success' : 'default'}>
                {selectedNamespace.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('ns.cluster')} span={2}>
              {selectedNamespace.clusterName || '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('ns.description')} span={2}>
              {selectedNamespace.description || '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('ns.default')}>
              {selectedNamespace.defaultNamespace ? t('common.yes') : t('common.no')}
            </Descriptions.Item>
            <Descriptions.Item label={t('ns.createTime')}>
              {selectedNamespace.createTime
                ? new Date(selectedNamespace.createTime).toLocaleString()
                : '-'}
            </Descriptions.Item>
            {selectedNamespace.quotaConfig && (
              <>
                <Descriptions.Item label={t('ns.quotaSection')} span={2}>
                  <strong>{t('ns.quotaSection')}</strong>
                </Descriptions.Item>
                <Descriptions.Item label={t('ns.topicLimit')}>
                  {selectedNamespace.quotaConfig.maxTopicCount || '-'}
                </Descriptions.Item>
                <Descriptions.Item label={t('ns.groupLimit')}>
                  {selectedNamespace.quotaConfig.maxConsumerGroupCount || '-'}
                </Descriptions.Item>
                <Descriptions.Item label={t('ns.storage')}>
                  {selectedNamespace.quotaConfig.storageQuotaGB || '-'}
                </Descriptions.Item>
                <Descriptions.Item label={t('ns.qpsLimit')}>
                  {selectedNamespace.quotaConfig.qpsLimit || '-'}
                </Descriptions.Item>
                <Descriptions.Item label={t('ns.connLimit')}>
                  {selectedNamespace.quotaConfig.connectionLimit || '-'}
                </Descriptions.Item>
              </>
            )}
          </Descriptions>
        )}
      </Modal>

      {/* Create/Edit Modal */}
      <Modal
        title={isEdit ? t('ns.edit') : t('ns.create')}
        open={formModalVisible}
        onCancel={() => {
          setFormModalVisible(false);
          form.resetFields();
        }}
        onOk={handleSubmit}
        okText={isEdit ? t('common.update') : t('common.create')}
        cancelText={t('common.cancel')}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="namespaceName"
            label={t('ns.name')}
            rules={[
              { required: true, message: t('ns.nameRequired') },
              {
                pattern: /^[a-zA-Z0-9_-]+$/,
                message: t('ns.namePattern'),
              },
            ]}
          >
            <Input placeholder={t('ns.namePlaceholder')} disabled={isEdit} />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="displayName" label={t('ns.displayName')}>
                <Input placeholder={t('ns.displayNamePlaceholder')} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="clusterName" label={t('ns.cluster')}>
                <Input placeholder={t('ns.clusterPlaceholder')} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="description" label={t('ns.description')}>
            <Input.TextArea rows={2} placeholder={t('ns.descPlaceholder')} />
          </Form.Item>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="maxTopicCount" label={t('ns.topicLimit')}>
                <InputNumber min={1} style={{ width: '100%' }} placeholder="1000" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="maxConsumerGroupCount" label={t('ns.groupLimit')}>
                <InputNumber min={1} style={{ width: '100%' }} placeholder="500" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="storageQuotaGB" label={t('ns.storage')}>
                <InputNumber min={1} style={{ width: '100%' }} placeholder="100" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="qpsLimit" label={t('ns.qpsLimit')}>
                <InputNumber min={1} style={{ width: '100%' }} placeholder="10000" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="connectionLimit" label={t('ns.connLimit')}>
                <InputNumber min={1} style={{ width: '100%' }} placeholder="5000" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
};

export default NamespacePage;
