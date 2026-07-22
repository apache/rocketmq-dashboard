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
  Card,
  Button,
  Tag,
  Space,
  Input,
  Select,
  Modal,
  Form,
  Flex,
  Typography,
  message,
} from 'antd';
import { useLang } from '../../i18n/LangContext';
import { Plus, MagnifyingGlass } from '@phosphor-icons/react';
import { EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { Instance } from '../../api/instance';
import {
  createInstance,
  deleteInstance,
  listInstances,
  updateInstance,
} from '../../services/instanceService';

const { Text } = Typography;

/* ─── Helpers ─── */
const typeLabel: Record<string, { text: string; color: string }> = {
  PROXY: { text: 'Proxy 模式', color: 'blue' },
  DIRECT: { text: 'Direct 模式', color: 'orange' },
};

/* ═══════════════════════════════════════════
   InstancePage
   ═══════════════════════════════════════════ */
const InstancePage = () => {
  const { t } = useLang();
  const [instances, setInstances] = useState<Instance[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [addForm] = Form.useForm();
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingInstance, setEditingInstance] = useState<Instance | null>(null);
  const [editForm] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void listInstances()
      .then((nextInstances) => {
        if (!cancelled) setInstances(nextInstances);
      })
      .catch(() => {
        if (!cancelled) message.error('实例列表加载失败，请稍后重试');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const handleCreate = async () => {
    try {
      const values = await addForm.validateFields();
      setSubmitting(true);
      const created = await createInstance(values);
      setInstances((previous) => [...previous, created]);
      message.success(`实例「${created.name}」添加成功`);
      setAddModalOpen(false);
      addForm.resetFields();
    } catch {
      message.error('添加实例失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  const handleUpdate = async () => {
    if (!editingInstance) return;
    try {
      const values = await editForm.validateFields();
      setSubmitting(true);
      const updated = await updateInstance({ id: editingInstance.id, remark: values.remark || '' });
      setInstances((previous) =>
        previous.map((instance) => (instance.id === updated.id ? updated : instance)),
      );
      message.success(`实例「${updated.name}」备注已更新`);
      setEditModalOpen(false);
      editForm.resetFields();
    } catch {
      message.error('更新实例失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (instance: Instance) => {
    try {
      await deleteInstance(instance.id);
      setInstances((previous) => previous.filter((item) => item.id !== instance.id));
      message.success('已删除');
    } catch {
      message.error('删除实例失败，请稍后重试');
    }
  };

  const filtered = instances
    .filter((i) => {
      const matchSearch = i.name.includes(search) || i.endpoint.includes(search);
      const matchType = typeFilter === 'ALL' || i.type === typeFilter;
      return matchSearch && matchType;
    })
    .sort((a, b) => a.name.localeCompare(b.name));

  const columns: ColumnsType<Instance> = [
    {
      title: '实例名称',
      dataIndex: 'name',
      key: 'name',
      width: 180,
      sorter: (a, b) => a.name.localeCompare(b.name),
      render: (text: string) => (
        <Text strong style={{ fontSize: 14 }}>
          {text}
        </Text>
      ),
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      width: 240,
      sorter: (a, b) => a.remark.localeCompare(b.remark),
      render: (remark: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {remark}
        </Text>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 130,
      sorter: (a, b) => a.type.localeCompare(b.type),
      render: (type: string) => {
        const t = typeLabel[type] || { text: type, color: 'default' };
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
    {
      title: 'Topic',
      dataIndex: 'topicCount',
      key: 'topicCount',
      width: 80,
      align: 'center' as const,
      sorter: (a, b) => a.topicCount - b.topicCount,
    },
    {
      title: 'Group',
      dataIndex: 'consumerGroupCount',
      key: 'consumerGroupCount',
      width: 80,
      align: 'center' as const,
      sorter: (a, b) => a.consumerGroupCount - b.consumerGroupCount,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      sorter: (a, b) => a.createdAt.localeCompare(b.createdAt),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {d}
        </Text>
      ),
    },
    {
      title: '修改时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 170,
      sorter: (a, b) => a.updatedAt.localeCompare(b.updatedAt),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {d}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_: unknown, record: Instance) => (
        <Flex gap={6}>
          <Button
            size="small"
            icon={<EditOutlined />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={() => {
              setEditingInstance(record);
              editForm.setFieldsValue({ remark: record.remark });
              setEditModalOpen(true);
            }}
          >
            编辑
          </Button>
          <Button
            size="small"
            icon={<DeleteOutlined />}
            style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
            onClick={() =>
              Modal.confirm({
                title: `确认删除 "${record.name}"？`,
                content: '此操作不可恢复。',
                okText: '删除',
                okButtonProps: { danger: true },
                onOk: () => handleDelete(record),
              })
            }
          >
            删除
          </Button>
        </Flex>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      {/* Header */}
      <div style={{ marginBottom: 20 }}>
        <h2 style={{ margin: 0, fontSize: 20, fontWeight: 600 }}>{t('instance.title')}</h2>
        <span style={{ fontSize: 13, color: '#9CA3AF' }}>
          管理 RocketMQ 集群连接，共 {instances.length} 个实例
        </span>
      </div>

      {/* Filter bar */}
      <Flex
        gap={12}
        wrap="wrap"
        style={{ marginBottom: 16 }}
        align="center"
        justify="space-between"
      >
        <Space size={12} wrap>
          <Input
            placeholder="搜索实例名称或地址"
            prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ width: 240 }}
            allowClear
          />
          <Select
            value={typeFilter}
            onChange={setTypeFilter}
            style={{ width: 140 }}
            options={[
              { value: 'ALL', label: '全部架构' },
              { value: 'PROXY', label: 'Proxy 模式' },
              { value: 'DIRECT', label: 'Direct 模式' },
            ]}
          />
        </Space>
        <Button
          type="primary"
          icon={<Plus size={14} weight="bold" />}
          onClick={() => setAddModalOpen(true)}
        >
          添加实例
        </Button>
      </Flex>

      {/* Table */}
      <Card bodyStyle={{ padding: 0 }}>
        <Table
          columns={columns}
          dataSource={filtered}
          loading={loading}
          rowKey="id"
          pagination={false}
          size="small"
          onRow={(record) => ({
            style: { cursor: 'pointer' },
            onClick: () => message.info(`进入 ${record.name}`),
          })}
        />
      </Card>

      {/* Add Instance Modal */}
      <Modal
        title="添加实例"
        open={addModalOpen}
        onCancel={() => {
          setAddModalOpen(false);
          addForm.resetFields();
        }}
        onOk={() => void handleCreate()}
        confirmLoading={submitting}
        okText="连接"
        cancelText="取消"
        width={520}
      >
        <Form form={addForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            label="实例名称"
            name="name"
            rules={[{ required: true, message: '请输入实例名称' }]}
          >
            <Input placeholder="例：rocketmq-production" />
          </Form.Item>
          <Form.Item
            label="接入方式"
            name="type"
            rules={[{ required: true, message: '请选择接入方式' }]}
          >
            <Select
              placeholder="选择接入方式"
              options={[
                { value: 'PROXY', label: 'Proxy 模式' },
                { value: 'DIRECT', label: 'Direct 模式' },
              ]}
            />
          </Form.Item>
          <Form.Item
            label="接入地址"
            name="endpoint"
            rules={[{ required: true, message: '请输入接入地址' }]}
          >
            <Input placeholder="例：proxy.example.com:8080" />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} placeholder="可选，描述实例用途" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Instance Modal */}
      <Modal
        title={`编辑实例 — ${editingInstance?.name || ''}`}
        open={editModalOpen}
        onCancel={() => {
          setEditModalOpen(false);
          editForm.resetFields();
        }}
        onOk={() => void handleUpdate()}
        confirmLoading={submitting}
        okText="保存"
        cancelText="取消"
        width={520}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="实例名称">
            <Input value={editingInstance?.name} disabled />
          </Form.Item>
          <Form.Item label="接入方式">
            <Select
              value={editingInstance?.type}
              disabled
              options={[
                { value: 'PROXY', label: 'Proxy 模式' },
                { value: 'DIRECT', label: 'Direct 模式' },
              ]}
            />
          </Form.Item>
          <Form.Item label="接入地址">
            <Input value={editingInstance?.endpoint} disabled />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={3} placeholder="描述实例用途" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default InstancePage;
