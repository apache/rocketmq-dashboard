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
import { EditOutlined, DeleteOutlined, QuestionCircleOutlined } from '@ant-design/icons';
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

const { Text } = Typography;

const DEFAULT_CLOUD_REGION_IDS: Partial<Record<InstanceVendor, string>> = {
  ALIYUN: 'cn-hangzhou',
  TENCENT: 'ap-chengdu',
};

const VENDOR_LABEL_KEYS: Record<InstanceVendor, string> = {
  APACHE: 'instance.openSourceEdition',
  ALIYUN: 'instance.aliyunEdition',
  TENCENT: 'instance.tencentEdition',
};

const VENDOR_DESCRIPTION_KEYS: Record<InstanceVendor, string> = {
  APACHE: 'instance.apacheDescription',
  ALIYUN: 'instance.aliyunDescription',
  TENCENT: 'instance.tencentDescription',
};

/* ─── Helpers ─── */
const typeLabel: Record<string, { labelKey: string; color: string }> = {
  CLOUD: { labelKey: 'instance.cloudType', color: 'blue' },
  PROXY_LOCAL: { labelKey: 'instance.proxyLocalMode', color: 'cyan' },
  PROXY_CLUSTER: { labelKey: 'instance.proxyClusterMode', color: 'blue' },
  DIRECT: { labelKey: 'instance.directMode', color: 'orange' },
};

const APACHE_ACCESS_TYPE_OPTIONS = [
  { value: 'PROXY_LOCAL', labelKey: 'instance.proxyLocalMode' },
  { value: 'PROXY_CLUSTER', labelKey: 'instance.proxyClusterMode' },
  { value: 'DIRECT', labelKey: 'instance.directMode' },
] as const;

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
  const { lang, t } = useLang();
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
        message.error(t('instance.listLoadFailed'));
      }
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  }, [t]);

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
            message.error(t('instance.cloudCredentialLoadFailed'));
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
  }, [vendor, cloudVendor, addModalOpen, t]);

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
            message.error(describeApiError(error, t('instance.cloudRegionLoadFailed')));
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
  }, [vendor, cloudVendor, addCredentialId, addForm, t]);

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
            message.error(describeApiError(error, t('instance.cloudInstanceLoadFailed')));
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
  }, [vendor, cloudVendor, addCredentialId, addRegionId, t]);

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

  const getVendorLabel = (nextVendor: InstanceVendor) => {
    return t(VENDOR_LABEL_KEYS[nextVendor]);
  };

  const getAccessTypeOptions = () =>
    APACHE_ACCESS_TYPE_OPTIONS.map((option) => ({
      value: option.value,
      label: t(option.labelKey),
    }));

  const getEndpointExtra = (type?: Instance['type']) => {
    if (type === 'DIRECT') return t('instance.directEndpointExtra');
    if (type === 'PROXY_LOCAL') return t('instance.proxyLocalEndpointExtra');
    if (type === 'PROXY_CLUSTER') return t('instance.proxyClusterEndpointExtra');
    if (type === 'CLOUD') return t('instance.cloudEndpointExtra');
    return t('instance.selectAccessTypeFirst');
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
      message.success(t('instance.added', { name: created.name }));
      setAddModalOpen(false);
      addForm.resetFields();
      setVendor(DEFAULT_VENDOR);
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return; // validation failure; antd already shows field-level errors
      }
      message.error(t('instance.createFailed'));
    } finally {
      mutationInFlightRef.current = false;
      setSubmitting(false);
    }
  };

  const handleImportAll = async () => {
    if (importing || vendor === 'APACHE') return;
    const credentialId = addForm.getFieldValue('credentialId') as number | undefined;
    if (!credentialId) {
      message.warning(t('instance.selectCloudCredentialFirst'));
      return;
    }
    setImporting(true);
    try {
      const result = await importCloudInstances({ vendor, credentialId });
      await loadInstances();
      const failedCount = result.failedCount ?? result.failed.length;
      const summary =
        result.imported > 0
          ? t('instance.importSuccess', {
              total: result.imported + result.skipped,
              imported: result.imported,
              skipped: result.skipped,
            })
          : failedCount > 0
            ? t('instance.importIncomplete', {
                imported: result.imported,
                skipped: result.skipped,
              })
            : t('instance.importAllSkipped', { skipped: result.skipped });
      if (failedCount > 0) {
        const details =
          result.failed.length > 0
            ? `${lang === 'zh' ? '：' : ': '}${result.failed.join(lang === 'zh' ? '；' : '; ')}`
            : '';
        const omitted = result.failureDetailsTruncated
          ? t('instance.importFailureDetailsTruncated', { count: result.failed.length })
          : '';
        message.warning(
          t('instance.importPartialFailure', {
            summary,
            count: failedCount,
            omitted,
            details,
          }),
        );
      } else {
        message.success(summary);
      }
      setAddModalOpen(false);
      addForm.resetFields();
      setVendor(DEFAULT_VENDOR);
      setRegions([]);
      setCloudInstances([]);
    } catch (error) {
      message.error(describeApiError(error, t('instance.importFailed')));
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
      message.success(t('instance.updated', { name: updated.name }));
      setEditModalOpen(false);
      editForm.resetFields();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return; // validation failure; antd already shows field-level errors
      }
      message.error(t('instance.updateFailed'));
    } finally {
      mutationInFlightRef.current = false;
      setSubmitting(false);
    }
  };

  const handleDelete = async (instance: Instance) => {
    try {
      await deleteInstance(instance.name);
      await loadInstances();
      message.success(t('instance.deleted'));
    } catch (error) {
      message.error(describeApiError(error, t('instance.deleteFailed')));
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
      ? t('instance.cloudBatchDeleteWarning')
      : t('instance.batchDeleteWarning');
    Modal.confirm({
      title: t('instance.confirmBatchDelete', { count: names.length }),
      content: t('instance.batchDeleteContent', {
        names: names.join(lang === 'zh' ? '、' : ', '),
        warning,
      }),
      okText: t('common.delete'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          const result = await deleteInstancesBatch(names);
          await loadInstances();
          setSelectedRowKeys([]);
          const summary = t('instance.deletedCount', { count: result.deleted });
          if (result.failed.length > 0) {
            message.warning(
              t('instance.batchDeletePartialFailure', {
                summary,
                count: result.failed.length,
                failed: result.failed.join(lang === 'zh' ? '；' : '; '),
              }),
            );
          } else {
            message.success(summary);
          }
        } catch (error) {
          message.error(describeApiError(error, t('instance.batchDeleteFailed')));
        }
      },
    });
  };

  const columns: ColumnsType<Instance> = [
    {
      title: t('instance.region'),
      dataIndex: 'regionId',
      key: 'regionId',
      width: 130,
      ellipsis: true,
      onHeaderCell: () => ({ style: { textAlign: 'left' } }),
      sorter: (a, b) => (a.regionId ?? '').localeCompare(b.regionId ?? ''),
      render: (regionId: string | undefined, record: Instance) => (
        <Text type="secondary" style={{ fontSize: 14 }}>
          {!record.vendor || record.vendor === 'APACHE'
            ? t('instance.openSourceEdition')
            : record.regionName || regionId || '-'}
        </Text>
      ),
    },
    {
      title: t('instance.instanceName'),
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
      title: t('instance.remark'),
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
      title: t('instance.vendor'),
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
            <img src={option.logo} alt={getVendorLabel(option.key)} style={{ height: 16 }} />
            <Text style={{ fontSize: 14 }}>{getVendorLabel(option.key)}</Text>
          </Space>
        );
      },
    },
    {
      title: t('common.type'),
      dataIndex: 'type',
      key: 'type',
      width: 110,
      align: 'center' as const,
      sorter: (a, b) => a.type.localeCompare(b.type),
      render: (type: string) => {
        const config = typeLabel[type] || { labelKey: type, color: 'default' };
        return <Tag color={config.color}>{typeLabel[type] ? t(config.labelKey) : type}</Tag>;
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
        record.resourceCountsAvailable === false ? t('common.unavailable') : count,
    },
    {
      title: 'Group',
      dataIndex: 'consumerGroupCount',
      key: 'consumerGroupCount',
      width: 70,
      align: 'center' as const,
      sorter: (a, b, sortOrder) => compareResourceCounts(a, b, 'consumerGroupCount', sortOrder),
      render: (count: number, record: Instance) =>
        record.resourceCountsAvailable === false ? t('common.unavailable') : count,
    },
    {
      title: t('instance.createdAt'),
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
      title: t('instance.updatedAt'),
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
      title: t('common.actions'),
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
            {t('common.edit')}
          </Button>
          <Button
            size="small"
            icon={<DeleteOutlined />}
            style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
            onClick={() => {
              const isCloudInstance = record.vendor === 'ALIYUN' || record.vendor === 'TENCENT';
              Modal.confirm({
                title: t('instance.confirmDelete', { name: record.name }),
                content: isCloudInstance
                  ? t('instance.cloudDeleteWarning')
                  : t('instance.deleteWarning'),
                okText: t('common.delete'),
                okButtonProps: { danger: true },
                onOk: () => handleDelete(record),
              });
            }}
          >
            {t('common.delete')}
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
          {t('instance.managementSubtitle', { count: instances.length })}
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
            placeholder={t('instance.searchPlaceholder')}
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
              { value: 'ALL', label: t('instance.allTypes') },
              { value: 'CLOUD', label: t('instance.cloudType') },
              { value: 'PROXY_LOCAL', label: t('instance.proxyLocalMode') },
              { value: 'PROXY_CLUSTER', label: t('instance.proxyClusterMode') },
              { value: 'DIRECT', label: t('instance.directMode') },
            ]}
          />
        </Space>
        <Space size={12}>
          <Button
            danger
            icon={<DeleteOutlined />}
            disabled={selectedRowKeys.length === 0}
            onClick={handleBatchDelete}
          >
            {t('common.delete')}
          </Button>
          <Button
            type="primary"
            icon={<Plus size={14} weight="bold" />}
            onClick={() => setAddModalOpen(true)}
          >
            {t('instance.addInstance')}
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

      {/* Add Instance Modal */}
      <Modal
        title={t('instance.addInstance')}
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
              <Tooltip title={t('instance.importAllTooltip')}>
                <Button
                  loading={importing}
                  disabled={!addCredentialId}
                  onClick={() => void handleImportAll()}
                >
                  {t('instance.importAll')}
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
              {t('common.cancel')}
            </Button>
            <Button type="primary" loading={submitting} onClick={() => void handleCreate()}>
              {t('instance.connect')}
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
                <img
                  src={option.logo}
                  alt={getVendorLabel(option.key)}
                  style={{ height: 18, maxWidth: 80 }}
                />
                {getVendorLabel(option.key)}
              </span>
            ),
          }))}
        />
        <Text type="secondary" style={{ display: 'block', fontSize: 14, marginBottom: 12 }}>
          {t(VENDOR_DESCRIPTION_KEYS[vendor])}
        </Text>
        {cloudVendor ? (
          <>
            <Form form={addForm} layout="vertical">
              <Form.Item
                label={t('instance.cloudCredential')}
                name="credentialId"
                rules={[{ required: true, message: t('instance.cloudCredentialRequired') }]}
                extra={
                  <span>
                    {t('instance.cloudCredentialExtraPrefix', { vendor: getVendorLabel(vendor) })}
                    <Link to="/settings?tab=credential">
                      {t('instance.cloudCredentialSettingsLink')}
                    </Link>
                  </span>
                }
              >
                <Select
                  placeholder={t('instance.selectStoredCredential')}
                  loading={credentialsLoading}
                  onChange={handleCredentialChange}
                  notFoundContent={
                    credentialsLoading ? (
                      t('instance.loading')
                    ) : (
                      <span>
                        {t('instance.noCloudCredential', { vendor: getVendorLabel(vendor) })}
                        <Link to="/settings?tab=credential">{t('instance.addInSettings')}</Link>
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
                label={t('instance.region')}
                name="regionId"
                rules={[{ required: true, message: t('instance.regionRequired') }]}
              >
                <Select
                  placeholder={
                    addCredentialId
                      ? t('instance.selectRegion')
                      : t('instance.selectCloudCredentialFirst')
                  }
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
                label={t('instance.cloudInstance')}
                name="cloudInstanceId"
                rules={[{ required: true, message: t('instance.cloudInstanceRequired') }]}
                extra={t('instance.cloudInstanceExtra')}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder={
                    addRegionId
                      ? t('instance.selectCloudInstance')
                      : t('instance.selectRegionFirst')
                  }
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
                label={t('instance.instanceName')}
                name="name"
                rules={[
                  { required: true, message: t('instance.nameRequired') },
                  { max: 64, message: t('instance.nameMax') },
                ]}
              >
                <Input placeholder={t('instance.cloudNamePlaceholder')} />
              </Form.Item>
              <Form.Item label={t('instance.remark')} name="remark">
                <Input.TextArea rows={2} placeholder={t('instance.remarkPlaceholder')} />
              </Form.Item>
            </Form>
          </>
        ) : (
          <Form form={addForm} layout="vertical">
            <Form.Item
              label={t('instance.instanceName')}
              name="name"
              rules={[
                { required: true, message: t('instance.nameRequired') },
                { max: 64, message: t('instance.nameMax') },
              ]}
            >
              <Input placeholder={t('instance.namePlaceholder')} />
            </Form.Item>
            <Form.Item
              label={t('instance.accessType')}
              name="type"
              rules={[{ required: true, message: t('instance.accessTypeRequired') }]}
            >
              <Select
                placeholder={t('instance.selectAccessType')}
                options={getAccessTypeOptions()}
              />
            </Form.Item>
            <Form.Item
              label={
                <span>
                  {t('instance.endpoint')}{' '}
                  <Tooltip title={t('instance.endpointHelp')}>
                    <QuestionCircleOutlined style={{ color: '#9CA3AF', cursor: 'help' }} />
                  </Tooltip>
                </span>
              }
              name="endpoint"
              rules={[{ required: true, message: t('instance.endpointRequired') }]}
              extra={getEndpointExtra(addInstanceType)}
            >
              <Input
                placeholder={
                  addInstanceType === 'DIRECT'
                    ? t('instance.directEndpointPlaceholder')
                    : t('instance.proxyEndpointPlaceholder')
                }
              />
            </Form.Item>
            <Form.Item
              label={t('instance.adminCredentialRef')}
              name="adminCredentialRef"
              extra={t('instance.adminCredentialRefExtra')}
            >
              <Input placeholder={t('instance.adminCredentialRefPlaceholder')} />
            </Form.Item>
            <Form.Item label={t('instance.remark')} name="remark">
              <Input.TextArea rows={2} placeholder={t('instance.remarkPlaceholder')} />
            </Form.Item>
          </Form>
        )}
      </Modal>

      {/* Edit Instance Modal */}
      <Modal
        title={t('instance.editInstanceTitle', { name: editingInstance?.name || '' })}
        open={editModalOpen}
        onCancel={() => {
          setEditModalOpen(false);
          editForm.resetFields();
        }}
        onOk={() => void handleUpdate()}
        confirmLoading={submitting}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        width={520}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label={t('instance.instanceName')}>
            <Input value={editingInstance?.name} disabled />
          </Form.Item>
          <Form.Item
            label={t('instance.accessType')}
            name="type"
            rules={[{ required: true, message: t('instance.accessTypeRequired') }]}
          >
            <Select
              options={
                editingInstance?.vendor && editingInstance.vendor !== 'APACHE'
                  ? [{ value: 'CLOUD', label: t('instance.cloudType') }]
                  : getAccessTypeOptions()
              }
            />
          </Form.Item>
          <Form.Item
            label={
              <span>
                {t('instance.endpoint')}{' '}
                <Tooltip title={t('instance.endpointHelp')}>
                  <QuestionCircleOutlined style={{ color: '#9CA3AF', cursor: 'help' }} />
                </Tooltip>
              </span>
            }
            name="endpoint"
            rules={[{ required: true, message: t('instance.endpointRequired') }]}
            extra={getEndpointExtra(editInstanceType)}
          >
            <Input
              placeholder={
                editInstanceType === 'DIRECT'
                  ? t('instance.directEndpointPlaceholder')
                  : t('instance.proxyEndpointPlaceholder')
              }
            />
          </Form.Item>
          {editingInstance?.vendor === 'APACHE' && (
            <Form.Item
              label={t('instance.adminCredentialRef')}
              name="adminCredentialRef"
              extra={t('instance.adminCredentialRefEditExtra')}
            >
              <Input placeholder={t('instance.adminCredentialRefPlaceholder')} />
            </Form.Item>
          )}
          <Form.Item label={t('instance.remark')} name="remark">
            <Input.TextArea rows={3} placeholder={t('instance.remarkEditPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default InstancePage;
