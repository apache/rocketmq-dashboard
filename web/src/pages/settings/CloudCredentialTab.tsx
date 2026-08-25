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

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Button,
  Input,
  Descriptions,
  Flex,
  Form,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { MagnifyingGlass } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';

import {
  createCloudCredential,
  deleteCloudCredential,
  listCloudCredentials,
  updateCloudCredential,
} from '../../api/cloudCredential';
import type { CloudCredential } from '../../api/cloudCredential';
import type { InstanceVendor } from '../../api/instance';

const vendorTagColor: Record<string, string> = {
  ALIYUN: 'orange',
  TENCENT: 'blue',
};

const PAGE_SIZE_OPTIONS = [20, 50, 100];

interface CredentialFormValues {
  name: string;
  vendor: InstanceVendor;
  accessKey?: string;
  secretKey?: string;
  remark?: string;
}

export const CloudCredentialTab = () => {
  const { t } = useLang();
  const [credentials, setCredentials] = useState<CloudCredential[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [vendorFilter, setVendorFilter] = useState<InstanceVendor | undefined>();
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingCredential, setEditingCredential] = useState<CloudCredential | null>(null);
  const [form] = Form.useForm<CredentialFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const requestSeqRef = useRef(0);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const loadCredentials = useCallback(() => {
    const requestId = ++requestSeqRef.current;
    Promise.resolve().then(() => {
      if (requestId === requestSeqRef.current) {
        setLoading(true);
      }
    });
    return (async () => {
      try {
        const result = await listCloudCredentials(vendorFilter, debouncedSearch, page, pageSize);
        if (requestId !== requestSeqRef.current) return;
        if (result.items.length === 0 && result.total > 0 && page > 1) {
          const lastPage = Math.max(1, Math.ceil(result.total / result.size));
          if (page > lastPage) {
            setPage(lastPage);
            return;
          }
        }
        setCredentials(result.items);
        setTotal(result.total);
      } catch {
        if (requestId === requestSeqRef.current) {
          message.error(t('settings.credentialLoadFailed'));
        }
      } finally {
        if (requestId === requestSeqRef.current) {
          setLoading(false);
        }
      }
    })();
  }, [debouncedSearch, page, pageSize, t, vendorFilter]);

  useEffect(() => {
    void loadCredentials();
  }, [loadCredentials]);

  useEffect(
    () => () => {
      requestSeqRef.current += 1;
    },
    [],
  );

  const changeVendorFilter = (value?: InstanceVendor) => {
    setVendorFilter(value);
    setPage(1);
  };

  const changeSearch = (value: string) => {
    setSearch(value);
    setPage(1);
  };

  const closeModal = () => {
    setModalOpen(false);
    setEditingCredential(null);
    form.resetFields();
  };

  const openCreateModal = () => {
    setEditingCredential(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEditModal = (credential: CloudCredential) => {
    setEditingCredential(credential);
    form.setFieldsValue({
      name: credential.name,
      vendor: credential.vendor,
      remark: credential.remark,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (editingCredential) {
        const saved = await updateCloudCredential({
          id: editingCredential.id,
          name: values.name,
          secretKey: values.secretKey,
          remark: values.remark,
        });
        setCredentials((previous) => previous.map((item) => (item.id === saved.id ? saved : item)));
        message.success(t('settings.credentialUpdated'));
      } else {
        await createCloudCredential({
          name: values.name,
          vendor: values.vendor,
          accessKey: values.accessKey ?? '',
          secretKey: values.secretKey ?? '',
          remark: values.remark,
        });
        setPage(1);
        message.success(t('settings.credentialAdded'));
      }
      await loadCredentials();
      closeModal();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return; // validation failure; antd already shows field-level errors
      }
      message.error(t('settings.credentialSaveFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (credential: CloudCredential) => {
    try {
      await deleteCloudCredential(credential.id);
      const remainingOnPage = credentials.length - 1;
      if (remainingOnPage === 0 && page > 1) {
        setPage(page - 1);
      } else {
        await loadCredentials();
      }
      message.success(t('settings.credentialDeleted'));
    } catch {
      message.error(t('settings.credentialDeleteFailed'));
    }
  };

  const columns: ColumnsType<CloudCredential> = [
    { title: t('common.name'), dataIndex: 'name', key: 'name' },
    {
      title: t('settings.cloudVendor'),
      dataIndex: 'vendor',
      key: 'vendor',
      render: (vendor: string) => (
        <Tag color={vendorTagColor[vendor]}>
          {vendor === 'ALIYUN'
            ? t('settings.aliyun')
            : vendor === 'TENCENT'
              ? t('settings.tencent')
              : vendor}
        </Tag>
      ),
    },
    { title: 'AccessKey', dataIndex: 'accessKey', key: 'accessKey' },
    { title: t('settings.remark'), dataIndex: 'remark', key: 'remark' },
    { title: t('settings.createdAt'), dataIndex: 'gmtCreate', key: 'gmtCreate' },
    {
      title: t('common.actions'),
      key: 'action',
      render: (_: unknown, record: CloudCredential) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEditModal(record)}
          >
            {t('common.edit')}
          </Button>
          <Popconfirm
            title={t('settings.deleteCredentialConfirm')}
            onConfirm={() => void handleDelete(record)}
            okText={t('settings.confirmAction')}
            cancelText={t('common.cancel')}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              {t('common.delete')}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Flex justify="space-between" align="center" gap={12} wrap style={{ marginBottom: 16 }}>
        <Flex gap={12} align="center" wrap>
          <Input
            allowClear
            prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
            placeholder={t('settings.searchCredentials')}
            style={{ width: 240 }}
            value={search}
            onChange={(event) => changeSearch(event.target.value)}
          />
          <Select<InstanceVendor>
            allowClear
            placeholder={t('settings.allCloudVendors')}
            style={{ width: 160 }}
            value={vendorFilter}
            onChange={changeVendorFilter}
            options={[
              { value: 'ALIYUN', label: t('settings.aliyun') },
              { value: 'TENCENT', label: t('settings.tencent') },
            ]}
          />
        </Flex>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal} disabled={loading}>
          {t('settings.addCredential')}
        </Button>
      </Flex>

      <Table<CloudCredential>
        columns={columns}
        dataSource={credentials}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: PAGE_SIZE_OPTIONS.map(String),
          showTotal: (count) => t('settings.totalRecords', { total: count }),
          onChange: (nextPage, nextPageSize) => {
            if (nextPageSize !== pageSize) {
              setPage(1);
              setPageSize(nextPageSize);
            } else {
              setPage(nextPage);
            }
          },
        }}
        size="middle"
      />

      <Modal
        title={t(editingCredential ? 'settings.editCredential' : 'settings.addCredential')}
        open={modalOpen}
        onCancel={closeModal}
        onOk={() => void handleSubmit()}
        confirmLoading={submitting}
        destroyOnHidden
      >
        {editingCredential && (
          <Descriptions
            size="small"
            column={1}
            style={{ marginBottom: 16 }}
            items={[
              {
                key: 'vendor',
                label: t('settings.cloudVendor'),
                children:
                  editingCredential.vendor === 'ALIYUN'
                    ? t('settings.aliyun')
                    : editingCredential.vendor === 'TENCENT'
                      ? t('settings.tencent')
                      : editingCredential.vendor,
              },
              { key: 'accessKey', label: 'AccessKey', children: editingCredential.accessKey },
            ]}
          />
        )}
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            label={t('common.name')}
            name="name"
            rules={[{ required: true, message: t('settings.credentialNameRequired') }]}
          >
            <Input placeholder={t('settings.credentialNameExample')} />
          </Form.Item>

          {!editingCredential && (
            <>
              <Form.Item
                label={t('settings.cloudVendor')}
                name="vendor"
                rules={[{ required: true, message: t('settings.selectCloudVendor') }]}
              >
                <Select
                  placeholder={t('settings.selectCloudVendor')}
                  virtual={false}
                  options={[
                    { value: 'ALIYUN', label: t('settings.aliyun') },
                    { value: 'TENCENT', label: t('settings.tencent') },
                  ]}
                />
              </Form.Item>

              <Form.Item
                label="AccessKey"
                name="accessKey"
                rules={[{ required: true, message: t('settings.accessKeyRequired') }]}
              >
                <Input autoComplete="off" placeholder="LTAI..." />
              </Form.Item>
            </>
          )}

          <Form.Item
            label="SecretKey"
            name="secretKey"
            rules={
              editingCredential
                ? []
                : [{ required: true, message: t('settings.secretKeyRequired') }]
            }
            extra={editingCredential ? t('settings.keepSecretKey') : undefined}
          >
            <Input.Password autoComplete="off" placeholder={t('settings.enterSecretKey')} />
          </Form.Item>

          <Form.Item label={t('settings.remark')} name="remark">
            <Input.TextArea rows={2} placeholder={t('settings.optional')} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export default CloudCredentialTab;
