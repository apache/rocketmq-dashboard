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
  Button,
  Descriptions,
  Flex,
  Form,
  Input,
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

import {
  createCloudCredential,
  deleteCloudCredential,
  listCloudCredentials,
  updateCloudCredential,
} from '../../api/cloudCredential';
import type { CloudCredential } from '../../api/cloudCredential';
import type { InstanceVendor } from '../../api/instance';

const vendorLabel: Record<string, string> = {
  ALIYUN: '阿里云',
  TENCENT: '腾讯云',
};

const vendorTagColor: Record<string, string> = {
  ALIYUN: 'orange',
  TENCENT: 'blue',
};

const VENDOR_OPTIONS = [
  { value: 'ALIYUN', label: '阿里云' },
  { value: 'TENCENT', label: '腾讯云' },
];

interface CredentialFormValues {
  name: string;
  vendor: InstanceVendor;
  accessKey?: string;
  secretKey?: string;
  remark?: string;
}

export const CloudCredentialTab = () => {
  const [credentials, setCredentials] = useState<CloudCredential[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingCredential, setEditingCredential] = useState<CloudCredential | null>(null);
  const [form] = Form.useForm<CredentialFormValues>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void listCloudCredentials()
      .then((list) => {
        if (!cancelled) setCredentials(list);
      })
      .catch(() => {
        if (!cancelled) message.error('云凭据加载失败，请稍后重试');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

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
        message.success('云凭据已更新');
      } else {
        const saved = await createCloudCredential({
          name: values.name,
          vendor: values.vendor,
          accessKey: values.accessKey ?? '',
          secretKey: values.secretKey ?? '',
          remark: values.remark,
        });
        setCredentials((previous) => [...previous, saved]);
        message.success('云凭据已添加');
      }
      closeModal();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return; // validation failure; antd already shows field-level errors
      }
      message.error('保存云凭据失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (credential: CloudCredential) => {
    try {
      await deleteCloudCredential(credential.id);
      setCredentials((previous) => previous.filter((item) => item.id !== credential.id));
      message.success('云凭据已删除');
    } catch {
      message.error('删除云凭据失败（可能仍被实例引用），请稍后重试');
    }
  };

  const columns: ColumnsType<CloudCredential> = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    {
      title: '云厂商',
      dataIndex: 'vendor',
      key: 'vendor',
      render: (vendor: string) => (
        <Tag color={vendorTagColor[vendor]}>{vendorLabel[vendor] ?? vendor}</Tag>
      ),
    },
    { title: 'AccessKey', dataIndex: 'accessKey', key: 'accessKey' },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    { title: '创建时间', dataIndex: 'gmtCreate', key: 'gmtCreate' },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: CloudCredential) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEditModal(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定要删除该云凭据吗？"
            onConfirm={() => void handleDelete(record)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Flex justify="flex-end" style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal} disabled={loading}>
          添加云凭据
        </Button>
      </Flex>

      <Table<CloudCredential>
        columns={columns}
        dataSource={credentials}
        rowKey="id"
        loading={loading}
        pagination={false}
        size="middle"
      />

      <Modal
        title={editingCredential ? '编辑云凭据' : '添加云凭据'}
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
                label: '云厂商',
                children: vendorLabel[editingCredential.vendor] ?? editingCredential.vendor,
              },
              { key: 'accessKey', label: 'AccessKey', children: editingCredential.accessKey },
            ]}
          />
        )}
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            label="名称"
            name="name"
            rules={[{ required: true, message: '请输入凭据名称' }]}
          >
            <Input placeholder="例如：阿里云测试账号" />
          </Form.Item>

          {!editingCredential && (
            <>
              <Form.Item
                label="云厂商"
                name="vendor"
                rules={[{ required: true, message: '请选择云厂商' }]}
              >
                <Select placeholder="请选择" virtual={false} options={VENDOR_OPTIONS} />
              </Form.Item>

              <Form.Item
                label="AccessKey"
                name="accessKey"
                rules={[{ required: true, message: '请输入 AccessKey' }]}
              >
                <Input autoComplete="off" placeholder="LTAI..." />
              </Form.Item>
            </>
          )}

          <Form.Item
            label="SecretKey"
            name="secretKey"
            rules={editingCredential ? [] : [{ required: true, message: '请输入 SecretKey' }]}
            extra={editingCredential ? '留空表示保持原 SecretKey 不变' : undefined}
          >
            <Input.Password autoComplete="off" placeholder="请输入 SecretKey" />
          </Form.Item>

          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export default CloudCredentialTab;
