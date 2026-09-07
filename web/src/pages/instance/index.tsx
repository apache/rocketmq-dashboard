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
import { Link, useNavigate } from 'react-router-dom';
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
  Tooltip,
  message,
} from 'antd';
import { useLang } from '../../i18n/LangContext';
import { Plus, MagnifyingGlass } from '@phosphor-icons/react';
import {
  DatabaseOutlined,
  EditOutlined,
  DeleteOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { SortOrder } from 'antd/es/table/interface';
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
import { tableScrollX } from '../../utils/table';
import {
  createInstance,
  deleteInstance,
  deleteInstancesBatch,
  importCloudInstances,
  listInstances,
  updateInstance,
} from '../../services/instanceService';
import { DEFAULT_VENDOR, VENDOR_OPTIONS, type InstanceVendor } from './vendorOptions';
import FleetResourceInventoryDrawer from '../../components/FleetResourceInventoryDrawer';

const { Text } = Typography;

const DEFAULT_CLOUD_REGION_IDS: Partial<Record<InstanceVendor, string>> = {
  ALIYUN: 'cn-hangzhou',
  TENCENT: 'ap-chengdu',
};

/* ─── Helpers ─── */
const typeLabel: Record<string, { text: string; color: string }> = {
  CLOUD: { text: '云服务', color: 'blue' },
  PROXY_LOCAL: { text: 'Proxy Local', color: 'cyan' },
  PROXY_CLUSTER: { text: 'Proxy Cluster', color: 'blue' },
  DIRECT: { text: 'Direct', color: 'orange' },
};

function describeApiError(error: unknown, fallback: string): string {
  const serverMessage = (error as { response?: { data?: { message?: unknown } } })?.response?.data
    ?.message;
  return typeof serverMessage === 'string' && serverMessage.trim() ? serverMessage : fallback;
}

type InstanceTypeFilter = 'ALL' | Instance['type'];

function compareResourceCounts(
  left: Instance,
  right: Instance,
  field: 'topicCount' | 'consumerGroupCount',
  sortOrder?: SortOrder,
): number {
  const leftUnavailable = left.resourceCountsAvailable === false;
  const rightUnavailable = right.resourceCountsAvailable === false;
  if (leftUnavailable || rightUnavailable) {
    if (leftUnavailable === rightUnavailable) return 0;
    // Ant Design reverses the comparator for descending order, so invert this
    // branch to keep unavailable counts after numeric values in either order.
    const unavailableAfterAvailable = sortOrder === 'descend' ? -1 : 1;
    return leftUnavailable ? unavailableAfterAvailable : -unavailableAfterAvailable;
  }
  return left[field] - right[field];
}

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
  const addInstanceType = Form.useWatch<Instance['type'] | undefined>('type', addForm);
  const addCredentialId = Form.useWatch<number | undefined>('credentialId', addForm);
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
  const editInstanceType = Form.useWatch<Instance['type'] | undefined>('type', editForm);
  const [submitting, setSubmitting] = useState(false);
  const [importing, setImporting] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [inventoryOpen, setInventoryOpen] = useState(false);
  const requestIdRef = useRef(0);
  const mutationInFlightRef = useRef(false);
  const listQueryRef = useRef<InstanceQuery>({});

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const loadInstances = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    const query = listQueryRef.current;

    setLoading(true);
    try {
      const nextInstances = await listInstances(query);
      if (requestId === requestIdRef.current) {
        setInstances(nextInstances);
        const availableNames = new Set(nextInstances.map((instance) => instance.name));
        setSelectedRowKeys((keys) => keys.filter((key) => availableNames.has(String(key))));
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
  }, []);

  useEffect(() => {
    listQueryRef.current = {
      ...(typeFilter === 'ALL' ? {} : { type: typeFilter }),
      ...(debouncedSearch ? { search: debouncedSearch } : {}),
    };
    const timer = window.setTimeout(() => void loadInstances(), 0);

    return () => {
      window.clearTimeout(timer);
      requestIdRef.current += 1;
    };
  }, [debouncedSearch, loadInstances, typeFilter]);

  const cloudVendor = vendor === 'ALIYUN' || vendor === 'TENCENT';

  useEffect(() => {
    if (!cloudVendor || !addModalOpen) {
      const timer = window.setTimeout(() => setCredentialsLoading(false), 0);
      return () => window.clearTimeout(timer);
    }
    let active = true;
    const timer = window.setTimeout(() => {
      setCredentialsLoading(true);
      listCloudCredentials(vendor)
        .then((result) => {
          if (active) {
            setCredentials(result.items);
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
      const timer = window.setTimeout(() => setRegionsLoading(false), 0);
      return () => window.clearTimeout(timer);
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
        .catch((error) => {
          if (active) {
            message.error(describeApiError(error, '云地域列表加载失败'));
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
      const timer = window.setTimeout(() => setCloudInstancesLoading(false), 0);
      return () => window.clearTimeout(timer);
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
        .catch((error) => {
          if (active) {
            message.error(describeApiError(error, '云实例列表加载失败'));
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
    setCredentialsLoading(false);
    setRegionsLoading(false);
    setCloudInstancesLoading(false);
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
    if (mutationInFlightRef.current) return;
    mutationInFlightRef.current = true;
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
      mutationInFlightRef.current = false;
      setSubmitting(false);
    }
  };

  const handleImportAll = async () => {
    if (importing || vendor === 'APACHE') return;
    const credentialId = addForm.getFieldValue('credentialId') as number | undefined;
    if (!credentialId) {
      message.warning('请先选择云凭据');
      return;
    }
    setImporting(true);
    try {
      const result = await importCloudInstances({ vendor, credentialId });
      await loadInstances();
      const failedCount = result.failedCount ?? result.failed.length;
      const summary =
        result.imported > 0
          ? `导入完成：共同步 ${result.imported + result.skipped} 个实例（新导入 ${result.imported}，已存在跳过 ${result.skipped}）`
          : failedCount > 0
            ? `导入未完成：新导入 ${result.imported} 个，已存在跳过 ${result.skipped} 个`
            : `云上实例均已在 Studio 中（共 ${result.skipped} 个），无需重复导入`;
      if (failedCount > 0) {
        const details = result.failed.length > 0 ? `：${result.failed.join('；')}` : '';
        const omitted = result.failureDetailsTruncated
          ? `（仅显示前 ${result.failed.length} 条）`
          : '';
        message.warning(`${summary}，失败 ${failedCount} 个${omitted}${details}`);
      } else {
        message.success(summary);
      }
      setAddModalOpen(false);
      addForm.resetFields();
      setVendor(DEFAULT_VENDOR);
      setRegions([]);
      setCloudInstances([]);
    } catch (error) {
      message.error(describeApiError(error, '一键导入失败，请稍后重试'));
    } finally {
      setImporting(false);
    }
  };

  const handleUpdate = async () => {
    if (!editingInstance || mutationInFlightRef.current) return;
    mutationInFlightRef.current = true;
    try {
      const values = await editForm.validateFields();
      setSubmitting(true);
      const updated = await updateInstance({
        instanceId: editingInstance.name,
        type: values.type,
        endpoint: values.endpoint,
        remark: values.remark || '',
        adminCredentialRef: values.adminCredentialRef,
      });
      await loadInstances();
      message.success(`实例「${updated.name}」已更新`);
      setEditModalOpen(false);
      editForm.resetFields();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return; // validation failure; antd already shows field-level errors
      }
      message.error('更新实例失败，请稍后重试');
    } finally {
      mutationInFlightRef.current = false;
      setSubmitting(false);
    }
  };

  const handleDelete = async (instance: Instance) => {
    try {
      await deleteInstance(instance.name);
      await loadInstances();
      message.success('已删除');
    } catch (error) {
      message.error(describeApiError(error, '删除实例失败，请稍后重试'));
    }
  };

  const handleBatchDelete = () => {
    const selectedNames = new Set(selectedRowKeys.map(String));
    const selected = instances.filter((instance) => selectedNames.has(instance.name));
    const names = selected.map((instance) => instance.name);
    if (names.length === 0) {
      setSelectedRowKeys([]);
      return;
    }
    const hasCloud = selected.some(
      (instance) => instance.vendor === 'ALIYUN' || instance.vendor === 'TENCENT',
    );
    const warning = hasCloud
      ? '云厂商实例仅从 Studio 移除记录，不会释放云上的 RocketMQ 实例；仍有 Topic/Group 的开源实例无法删除。'
      : '仍有 Topic/Group 的开源实例无法删除。';
    Modal.confirm({
      title: `确认删除选中的 ${names.length} 个实例？`,
      content: `将删除：${names.join('、')}。${warning}`,
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          const result = await deleteInstancesBatch(names);
          await loadInstances();
          setSelectedRowKeys([]);
          const summary = `已删除 ${result.deleted} 个`;
          if (result.failed.length > 0) {
            message.warning(
              `${summary}，${result.failed.length} 个未能删除：${result.failed.join('；')}`,
            );
          } else {
            message.success(summary);
          }
        } catch (error) {
          message.error(describeApiError(error, '批量删除失败，请稍后重试'));
        }
      },
    });
  };

  const columns: ColumnsType<Instance> = [
    {
      title: '地域',
      dataIndex: 'regionId',
      key: 'regionId',
      width: 130,
      ellipsis: true,
      onHeaderCell: () => ({ style: { textAlign: 'left' } }),
      sorter: (a, b) => (a.regionId ?? '').localeCompare(b.regionId ?? ''),
      render: (regionId: string | undefined, record: Instance) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
          {!record.vendor || record.vendor === 'APACHE'
            ? '开源版'
            : record.regionName || regionId || '-'}
        </Text>
      ),
    },
    {
      title: '实例 ID',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      onHeaderCell: () => ({ style: { textAlign: 'left' } }),
      sorter: (a, b) => a.name.localeCompare(b.name),
      render: (text: string) => (
        <Text
          strong
          style={{ fontSize: 14, cursor: 'pointer' }}
          onClick={() => navigate(`/instance/${encodeURIComponent(text)}/topic`)}
        >
          {text}
        </Text>
      ),
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      ellipsis: { showTitle: false },
      onHeaderCell: () => ({ style: { textAlign: 'left' } }),
      sorter: (a, b) => (a.remark ?? '').localeCompare(b.remark ?? ''),
      render: (remark: string | null) =>
        remark ? (
          <Tooltip title={remark}>
            <Text type="secondary" style={{ fontSize: 14 }}>
              {remark}
            </Text>
          </Tooltip>
        ) : (
          <Text type="secondary" style={{ fontSize: 14 }}>
            -
          </Text>
        ),
    },
    {
      title: '厂商',
      dataIndex: 'vendor',
      key: 'vendor',
      width: 100,
      align: 'center' as const,
      render: (value?: string) => {
        const option = VENDOR_OPTIONS.find((item) => item.key === (value || 'APACHE'));
        if (!option) {
          return <Text type="secondary">{value || '-'}</Text>;
        }
        return (
          <Space size={6}>
            <img src={option.logo} alt={option.label} style={{ height: 16 }} />
            <Text style={{ fontSize: 14 }}>{option.label}</Text>
          </Space>
        );
      },
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 110,
      align: 'center' as const,
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
      width: 70,
      align: 'center' as const,
      sorter: (a, b, sortOrder) => compareResourceCounts(a, b, 'topicCount', sortOrder),
      render: (count: number, record: Instance) =>
        record.resourceCountsAvailable === false ? '不可用' : count,
    },
    {
      title: 'Group',
      dataIndex: 'consumerGroupCount',
      key: 'consumerGroupCount',
      width: 70,
      align: 'center' as const,
      sorter: (a, b, sortOrder) => compareResourceCounts(a, b, 'consumerGroupCount', sortOrder),
      render: (count: number, record: Instance) =>
        record.resourceCountsAvailable === false ? '不可用' : count,
    },
    {
      title: '创建时间',
      dataIndex: 'gmtCreate',
      key: 'gmtCreate',
      width: 150,
      sorter: (a, b) => a.gmtCreate.localeCompare(b.gmtCreate),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
          {formatDateTime(d)}
        </Text>
      ),
    },
    {
      title: '修改时间',
      dataIndex: 'gmtModified',
      key: 'gmtModified',
      width: 150,
      sorter: (a, b) => a.gmtModified.localeCompare(b.gmtModified),
      render: (d: string) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
          {formatDateTime(d)}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_: unknown, record: Instance) => (
        <Flex gap={6} onClick={(e) => e.stopPropagation()}>
          <Button
            size="small"
            icon={<EditOutlined />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={() => {
              setEditingInstance(record);
              editForm.setFieldsValue({
                type: record.type,
                endpoint: record.endpoint,
                remark: record.remark,
                adminCredentialRef: record.adminCredentialRef,
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
              const isCloudInstance = record.vendor === 'ALIYUN' || record.vendor === 'TENCENT';
              Modal.confirm({
                title: `确认删除 "${record.name}"？`,
                content: isCloudInstance
                  ? '仅从 Studio 移除该实例记录，不会释放云上的 RocketMQ 实例。'
                  : '此操作不可恢复。',
                okText: '删除',
                okButtonProps: { danger: true },
                onOk: () => handleDelete(record),
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
      {/* Header */}
      <div style={{ marginBottom: 20 }}>
        <h2 style={{ margin: 0, fontSize: 20, fontWeight: 600 }}>{t('instance.title')}</h2>
        <div style={{ marginTop: 6, fontSize: 14, color: '#9CA3AF' }}>
          接入并管理 RocketMQ 实例（开源自建 / 阿里云 / 腾讯云），当前显示 {instances.length} 个实例
        </div>
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
            placeholder="搜索实例 ID 或地址"
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
              { value: 'CLOUD', label: '云服务' },
              { value: 'PROXY_LOCAL', label: 'Proxy Local 模式' },
              { value: 'PROXY_CLUSTER', label: 'Proxy Cluster 模式' },
              { value: 'DIRECT', label: 'Direct 模式' },
            ]}
          />
        </Space>
        <Space size={12}>
          <Button icon={<DatabaseOutlined />} onClick={() => setInventoryOpen(true)}>
            {t('fleetInventory.open')}
          </Button>
          <Button
            danger
            icon={<DeleteOutlined />}
            disabled={selectedRowKeys.length === 0}
            onClick={handleBatchDelete}
          >
            删除
          </Button>
          <Button
            type="primary"
            icon={<Plus size={14} weight="bold" />}
            onClick={() => setAddModalOpen(true)}
          >
            添加实例
          </Button>
        </Space>
      </Flex>

      {/* Table */}
      <Card styles={{ body: { padding: 0 } }}>
        <Table
          className="instance-table"
          columns={columns}
          dataSource={instances}
          loading={loading}
          rowKey="name"
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          pagination={false}
          size="small"
          tableLayout="fixed"
          scroll={{ x: tableScrollX(columns, { selection: true }) }}
        />
      </Card>

      {inventoryOpen && (
        <FleetResourceInventoryDrawer
          open
          instances={instances}
          onClose={() => setInventoryOpen(false)}
        />
      )}

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
        width={520}
        footer={
          <Flex justify="flex-end" gap={8}>
            {cloudVendor && (
              <Tooltip title="遍历该凭据下全部地域，将所有云上实例导入（幂等，已存在的自动跳过），备注自动取自云上实例">
                <Button
                  loading={importing}
                  disabled={!addCredentialId}
                  onClick={() => void handleImportAll()}
                >
                  一键导入
                </Button>
              </Tooltip>
            )}
            <Button
              onClick={() => {
                setAddModalOpen(false);
                addForm.resetFields();
                setVendor(DEFAULT_VENDOR);
                setRegions([]);
                setCloudInstances([]);
              }}
            >
              取消
            </Button>
            <Button type="primary" loading={submitting} onClick={() => void handleCreate()}>
              连接
            </Button>
          </Flex>
        }
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
        <Text type="secondary" style={{ display: 'block', fontSize: 14, marginBottom: 12 }}>
          {VENDOR_OPTIONS.find((option) => option.key === vendor)?.description}
        </Text>
        {cloudVendor ? (
          <>
            <Form form={addForm} layout="vertical">
              <Form.Item
                label="云凭据"
                name="credentialId"
                rules={[{ required: true, message: '请选择云凭据' }]}
                extra={
                  <span>
                    凭据为{vendor === 'ALIYUN' ? '阿里云' : '腾讯云'}账号的 AK/SK，
                    <Link to="/settings?tab=credential">前往「设置 - 云凭据管理」添加</Link>
                  </span>
                }
              >
                <Select
                  placeholder="选择已录入的 AK/SK 凭据"
                  loading={credentialsLoading}
                  onChange={handleCredentialChange}
                  notFoundContent={
                    credentialsLoading ? (
                      '加载中…'
                    ) : (
                      <span>
                        暂无{vendor === 'ALIYUN' ? '阿里云' : '腾讯云'}凭据，
                        <Link to="/settings?tab=credential">去设置中添加</Link>
                      </span>
                    )
                  }
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
                    if (selected) {
                      addForm.setFieldsValue({ name: selected.instanceId });
                    }
                  }}
                />
              </Form.Item>
              <Form.Item
                label="实例 ID"
                name="name"
                rules={[
                  { required: true, message: '请输入实例 ID' },
                  { max: 64, message: '实例 ID 不能超过 64 个字符' },
                ]}
              >
                <Input placeholder="默认取云上实例 ID" />
              </Form.Item>
              <Form.Item label="备注" name="remark">
                <Input.TextArea rows={2} placeholder="可选，描述实例用途" />
              </Form.Item>
            </Form>
          </>
        ) : (
          <Form form={addForm} layout="vertical">
            <Form.Item
              label="实例 ID"
              name="name"
              rules={[
                { required: true, message: '请输入实例 ID' },
                { max: 64, message: '实例 ID 不能超过 64 个字符' },
              ]}
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
                  { value: 'PROXY_LOCAL', label: 'Proxy Local 模式' },
                  { value: 'PROXY_CLUSTER', label: 'Proxy Cluster 模式' },
                  { value: 'DIRECT', label: 'Direct 模式' },
                ]}
              />
            </Form.Item>
            <Form.Item
              label={
                <span>
                  接入地址{' '}
                  <Tooltip title="接入地址为客户端访问入口，会展示在 Topic 等页面供客户端配置使用。若客户端环境无法解析该地址（如 K8s 内部 Service 域名），可自行配置 DNS 解析或在客户端 hosts 中映射。">
                    <QuestionCircleOutlined style={{ color: '#9CA3AF', cursor: 'help' }} />
                  </Tooltip>
                </span>
              }
              name="endpoint"
              rules={[{ required: true, message: '请输入接入地址' }]}
              extra={
                addInstanceType === 'DIRECT'
                  ? 'Direct 模式请填写 NameServer SLB 地址（K8s 场景下一般为 NameServer Service 地址，如 namesrv.mq.svc:9876）'
                  : addInstanceType === 'PROXY_LOCAL'
                    ? 'Proxy Local 模式请填写与 Broker 同进程部署的 Proxy 接入地址（如 broker-proxy.mq.svc:8080）'
                    : addInstanceType === 'PROXY_CLUSTER'
                      ? 'Proxy Cluster 模式请填写独立 Proxy 集群的 SLB 内网地址（如 proxy.mq.svc:8080）'
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
            <Form.Item
              label="管理凭据引用"
              name="adminCredentialRef"
              extra="可选。仅保存服务端配置中的凭据引用，不会保存或传输 AK/SK。"
            >
              <Input placeholder="例：production-admin" />
            </Form.Item>
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
          <Form.Item label="实例 ID">
            <Input value={editingInstance?.name} disabled />
          </Form.Item>
          <Form.Item
            label="接入方式"
            name="type"
            rules={[{ required: true, message: '请选择接入方式' }]}
          >
            <Select
              options={
                editingInstance?.vendor && editingInstance.vendor !== 'APACHE'
                  ? [{ value: 'CLOUD', label: '云服务' }]
                  : [
                      { value: 'PROXY_LOCAL', label: 'Proxy Local 模式' },
                      { value: 'PROXY_CLUSTER', label: 'Proxy Cluster 模式' },
                      { value: 'DIRECT', label: 'Direct 模式' },
                    ]
              }
            />
          </Form.Item>
          <Form.Item
            label={
              <span>
                接入地址{' '}
                <Tooltip title="接入地址为客户端访问入口，会展示在 Topic 等页面供客户端配置使用。若客户端环境无法解析该地址（如 K8s 内部 Service 域名），可自行配置 DNS 解析或在客户端 hosts 中映射。">
                  <QuestionCircleOutlined style={{ color: '#9CA3AF', cursor: 'help' }} />
                </Tooltip>
              </span>
            }
            name="endpoint"
            rules={[{ required: true, message: '请输入接入地址' }]}
            extra={
              editInstanceType === 'DIRECT'
                ? 'Direct 模式请填写 NameServer SLB 地址（K8s 场景下一般为 NameServer Service 地址，如 namesrv.mq.svc:9876）'
                : editInstanceType === 'CLOUD'
                  ? '云服务实例接入地址由云厂商目录解析，不支持手动修改'
                  : '请先选择接入方式'
            }
          >
            <Input
              placeholder={
                editInstanceType === 'DIRECT'
                  ? '例：namesrv.mq.svc.cluster.local:9876'
                  : '例：proxy.mq.svc.cluster.local:8080'
              }
            />
          </Form.Item>
          {editingInstance?.vendor === 'APACHE' && (
            <Form.Item
              label="管理凭据引用"
              name="adminCredentialRef"
              extra="仅保存服务端配置中的引用，不会保存或传输 AK/SK。"
            >
              <Input placeholder="例：production-admin" />
            </Form.Item>
          )}
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={3} placeholder="描述实例用途" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default InstancePage;
