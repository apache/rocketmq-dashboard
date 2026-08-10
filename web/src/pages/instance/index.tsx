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
import { useNavigate } from 'react-router-dom';
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
  Tabs,
  Typography,
  Alert,
  message,
} from 'antd';
import { useLang } from '../../i18n/LangContext';
import { Plus, MagnifyingGlass } from '@phosphor-icons/react';
import { EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { Instance, InstanceQuery } from '../../api/instance';
import { listCloudCredentials, type CloudCredential } from '../../api/cloudCredential';
import {
  listAliyunInstances,
  listAliyunRegions,
  type CloudInstanceOption,
  type CloudRegion,
} from '../../api/aliyunCatalog';
import { listTencentInstances, listTencentRegions } from '../../api/tencentCatalog';
import { formatDateTime } from '../../utils/format';
import {
  createInstance,
  deleteInstance,
  listInstances,
  updateInstance,
} from '../../services/instanceService';
import { DEFAULT_VENDOR, VENDOR_OPTIONS, type InstanceVendor } from './vendorOptions';

const { Text } = Typography;

const DEFAULT_CLOUD_REGION_IDS: Partial<Record<InstanceVendor, string>> = {
  ALIYUN: 'cn-hangzhou',
  TENCENT: 'ap-chengdu',
};

/* ─── Helpers ─── */
const typeLabel: Record<string, { text: string; color: string }> = {
  PROXY: { text: 'Proxy 模式', color: 'blue' },
  DIRECT: { text: 'Direct 模式', color: 'orange' },
};

type InstanceTypeFilter = 'ALL' | Instance['type'];

/* ═══════════════════════════════════════════
   InstancePage
   ═══════════════════════════════════════════ */
const InstancePage = () => {
  const { t } = useLang();
  const navigate = useNavigate();
  const [instances, setInstances] = useState<Instance[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState<InstanceTypeFilter>('ALL');
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [vendor, setVendor] = useState<InstanceVendor>(DEFAULT_VENDOR);
  const [addForm] = Form.useForm();
  const addInstanceType = Form.useWatch<'PROXY' | 'DIRECT' | undefined>('type', addForm);
  const addCredentialId = Form.useWatch<string | undefined>('credentialId', addForm);
  const addRegionId = Form.useWatch<string | undefined>('regionId', addForm);
  const [credentials, setCredentials] = useState<CloudCredential[]>([]);
  const [credentialsLoading, setCredentialsLoading] = useState(false);
  const [regions, setRegions] = useState<CloudRegion[]>([]);
  const [regionsLoading, setRegionsLoading] = useState(false);
  const [cloudInstances, setCloudInstances] = useState<CloudInstanceOption[]>([]);
  const [cloudInstancesLoading, setCloudInstancesLoading] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingInstance, setEditingInstance] = useState<Instance | null>(null);
  const [editForm] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const requestIdRef = useRef(0);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const loadInstances = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    const query: InstanceQuery = {
      ...(typeFilter === 'ALL' ? {} : { type: typeFilter }),
      ...(debouncedSearch ? { search: debouncedSearch } : {}),
    };

    setLoading(true);
    try {
      const nextInstances = await listInstances(query);
      if (requestId === requestIdRef.current) {
        setInstances(nextInstances);
      }
    } catch {
      if (requestId === requestIdRef.current) {
        message.error('实例列表加载失败，请稍后重试');
      }
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  }, [debouncedSearch, typeFilter]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadInstances(), 0);

    return () => {
      window.clearTimeout(timer);
      requestIdRef.current += 1;
    };
  }, [loadInstances]);

  const cloudVendor = vendor === 'ALIYUN' || vendor === 'TENCENT';

  useEffect(() => {
    if (!cloudVendor || !addModalOpen) {
      return;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      setCredentialsLoading(true);
      listCloudCredentials()
        .then((items) => {
          if (active) {
            setCredentials(items.filter((item) => item.vendor === vendor));
          }
        })
        .catch(() => {
          if (active) {
            message.error('云凭据列表加载失败');
          }
        })
        .finally(() => {
          if (active) {
            setCredentialsLoading(false);
          }
        });
    }, 0);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [vendor, cloudVendor, addModalOpen]);

  useEffect(() => {
    if (!cloudVendor || !addCredentialId) {
      return;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      setRegionsLoading(true);
      const request =
        vendor === 'ALIYUN'
          ? listAliyunRegions(addCredentialId)
          : listTencentRegions(addCredentialId);
      request
        .then((items) => {
          if (!active) {
            return;
          }
          setRegions(items);
          if (!addForm.getFieldValue('regionId')) {
            const preferred = items.find(
              (region) => region.regionId === DEFAULT_CLOUD_REGION_IDS[vendor],
            );
            if (preferred) {
              addForm.setFieldsValue({ regionId: preferred.regionId });
            }
          }
        })
        .catch(() => {
          if (active) {
            message.error('云地域列表加载失败');
          }
        })
        .finally(() => {
          if (active) {
            setRegionsLoading(false);
          }
        });
    }, 0);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [vendor, cloudVendor, addCredentialId, addForm]);

  useEffect(() => {
    if (!cloudVendor || !addCredentialId || !addRegionId) {
      return;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      setCloudInstancesLoading(true);
      const request =
        vendor === 'ALIYUN'
          ? listAliyunInstances(addCredentialId, addRegionId)
          : listTencentInstances(addCredentialId, addRegionId);
      request
        .then((items) => {
          if (active) {
            setCloudInstances(items);
          }
        })
        .catch(() => {
          if (active) {
            message.error('云实例列表加载失败');
          }
        })
        .finally(() => {
          if (active) {
            setCloudInstancesLoading(false);
          }
        });
    }, 0);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [vendor, cloudVendor, addCredentialId, addRegionId]);

  const handleVendorChange = (nextVendor: string) => {
    setVendor(nextVendor as InstanceVendor);
    setCredentials([]);
    setRegions([]);
    setCloudInstances([]);
    addForm.setFieldsValue({
      credentialId: undefined,
      regionId: undefined,
      cloudInstanceId: undefined,
    });
  };

  const handleCredentialChange = () => {
    setRegions([]);
    setCloudInstances([]);
    addForm.setFieldsValue({ regionId: undefined, cloudInstanceId: undefined });
  };

  const handleRegionChange = () => {
    setCloudInstances([]);
    addForm.setFieldsValue({ cloudInstanceId: undefined });
  };

  const handleCreate = async () => {
    try {
      const values = await addForm.validateFields();
      setSubmitting(true);
      const payload = cloudVendor
        ? {
            name: values.name,
            vendor,
            credentialId: values.credentialId,
            cloudInstanceId: values.cloudInstanceId,
            regionId: values.regionId,
            remark: values.remark,
          }
        : values;
      const created = await createInstance(payload);
      await loadInstances();
      message.success(`实例「${created.name}」添加成功`);
      setAddModalOpen(false);
      addForm.resetFields();
      setVendor(DEFAULT_VENDOR);
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return; // validation failure; antd already shows field-level errors
      }
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
      await loadInstances();
      message.success(`实例「${updated.name}」备注已更新`);
      setEditModalOpen(false);
      editForm.resetFields();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return; // validation failure; antd already shows field-level errors
      }
      message.error('更新实例失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (instance: Instance) => {
    try {
      await deleteInstance(instance.id);
      await loadInstances();
      message.success('已删除');
    } catch {
      message.error('删除实例失败，请稍后重试');
    }
  };

  const sortedInstances = [...instances].sort((a, b) => a.name.localeCompare(b.name));

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
      sorter: (a, b) => (a.remark ?? '').localeCompare(b.remark ?? ''),
      render: (remark: string | null) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {remark || '-'}
        </Text>
      ),
    },
    {
      title: '厂商',
      dataIndex: 'vendor',
      key: 'vendor',
      width: 140,
      render: (value?: string) => {
        const option = VENDOR_OPTIONS.find((item) => item.key === (value || 'APACHE'));
        if (!option) {
          return <Text type="secondary">{value || '-'}</Text>;
        }
        return (
          <Space size={6}>
            <img src={option.logo} alt={option.label} style={{ height: 16 }} />
            <Text style={{ fontSize: 13 }}>{option.label}</Text>
          </Space>
        );
      },
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
      render: (count: number, record: Instance) =>
        record.resourceCountsAvailable === false ? '不可用' : count,
    },
    {
      title: 'Group',
      dataIndex: 'consumerGroupCount',
      key: 'consumerGroupCount',
      width: 80,
      align: 'center' as const,
      sorter: (a, b) => a.consumerGroupCount - b.consumerGroupCount,
      render: (count: number, record: Instance) =>
        record.resourceCountsAvailable === false ? '不可用' : count,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      sorter: (a, b) => a.createdAt.localeCompare(b.createdAt),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {formatDateTime(d)}
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
          {formatDateTime(d)}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_: unknown, record: Instance) => (
        <Flex gap={6} onClick={(e) => e.stopPropagation()}>
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
          管理 RocketMQ 集群连接，当前显示 {instances.length} 个实例
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
          <Select<InstanceTypeFilter>
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
          dataSource={sortedInstances}
          loading={loading}
          rowKey="id"
          pagination={false}
          size="small"
          onRow={(record) => ({
            style: { cursor: 'pointer' },
            onClick: () => navigate(`/instance/${record.id}/topic`),
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
          setVendor(DEFAULT_VENDOR);
          setRegions([]);
          setCloudInstances([]);
        }}
        onOk={() => void handleCreate()}
        confirmLoading={submitting}
        okText="连接"
        cancelText="取消"
        width={520}
      >
        <Tabs
          type="card"
          activeKey={vendor}
          onChange={handleVendorChange}
          style={{ marginTop: 8, marginBottom: 4 }}
          items={VENDOR_OPTIONS.map((option) => ({
            key: option.key,
            label: (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <img src={option.logo} alt={option.label} style={{ height: 18, maxWidth: 80 }} />
                {option.label}
              </span>
            ),
          }))}
        />
        <Text type="secondary" style={{ display: 'block', fontSize: 12, marginBottom: 12 }}>
          {VENDOR_OPTIONS.find((option) => option.key === vendor)?.description}
        </Text>
        {cloudVendor ? (
          <Form form={addForm} layout="vertical">
            <Form.Item
              label="云凭据"
              name="credentialId"
              rules={[{ required: true, message: '请选择云凭据' }]}
              extra={`凭据为${vendor === 'ALIYUN' ? '阿里云' : '腾讯云'}账号的 AK/SK，由后端录入`}
            >
              <Select
                placeholder="选择已录入的 AK/SK 凭据"
                loading={credentialsLoading}
                onChange={handleCredentialChange}
                options={credentials.map((item) => ({
                  value: item.id,
                  label: `${item.name}（${item.accessKey}）`,
                }))}
              />
            </Form.Item>
            <Form.Item
              label="地域"
              name="regionId"
              rules={[{ required: true, message: '请选择地域' }]}
            >
              <Select
                placeholder={addCredentialId ? '选择地域' : '请先选择云凭据'}
                disabled={!addCredentialId}
                loading={regionsLoading}
                onChange={handleRegionChange}
                options={regions.map((region) => ({
                  value: region.regionId,
                  label: `${region.regionName}（${region.regionId}）`,
                }))}
              />
            </Form.Item>
            <Form.Item
              label="云上实例"
              name="cloudInstanceId"
              rules={[{ required: true, message: '请选择云上实例' }]}
              extra="商业版实例来自云端目录，无法手工创建"
            >
              <Select
                showSearch
                optionFilterProp="label"
                placeholder={addRegionId ? '选择云上实例' : '请先选择地域'}
                disabled={!addRegionId}
                loading={cloudInstancesLoading}
                options={cloudInstances.map((item) => ({
                  value: item.instanceId,
                  label: `${item.instanceName || item.instanceId}（${item.instanceId}）`,
                }))}
                onChange={(value) => {
                  const selected = cloudInstances.find((item) => item.instanceId === value);
                  if (selected?.instanceName) {
                    addForm.setFieldsValue({ name: selected.instanceName });
                  }
                }}
              />
            </Form.Item>
            <Form.Item
              label="实例名称"
              name="name"
              rules={[{ required: true, message: '请输入实例名称' }]}
            >
              <Input placeholder="默认取云上实例名称" />
            </Form.Item>
            <Form.Item label="备注" name="remark">
              <Input.TextArea rows={2} placeholder="可选，描述实例用途" />
            </Form.Item>
          </Form>
        ) : (
          <Form form={addForm} layout="vertical">
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
              extra={
                addInstanceType === 'DIRECT'
                  ? 'Direct 模式请填写 NameServer SLB 地址（K8s 场景下一般为 NameServer Service 地址，如 namesrv.mq.svc:9876）'
                  : addInstanceType === 'PROXY'
                    ? 'Proxy 模式请填写 Proxy SLB 内网地址（如 proxy.mq.svc:8080）'
                    : '请先选择接入方式'
              }
            >
              <Input
                placeholder={
                  addInstanceType === 'DIRECT'
                    ? '例：namesrv.mq.svc.cluster.local:9876'
                    : '例：proxy.mq.svc.cluster.local:8080'
                }
              />
            </Form.Item>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="接入地址为客户端访问入口"
              description="接入地址会展示在 Topic 等页面供客户端配置使用。若客户端环境无法解析该地址（如 K8s 内部 Service 域名），可自行配置 DNS 解析或在客户端 hosts 中映射。"
            />
            <Form.Item label="备注" name="remark">
              <Input.TextArea rows={2} placeholder="可选，描述实例用途" />
            </Form.Item>
          </Form>
        )}
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
