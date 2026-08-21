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
  Flex,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { MagnifyingGlass } from '@phosphor-icons/react';
import { ApiOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';

import { useLang } from '../../i18n/LangContext';
import StatusBadge from '../../components/StatusBadge';
import {
  createDataSource,
  deleteDataSource,
  listDataSourcesPage,
  testDataSource,
  updateDataSource,
} from '../../api/settings';
import type { DataSource } from '../../api/settings';
import { STATUS_MAP } from '../../constants/theme';
import { listInstances } from '../../services/instanceService';
import type { Instance } from '../../api/instance';

const { Text } = Typography;

const typeTagColor: Record<string, string> = {
  Prometheus: 'orange',
  VictoriaMetrics: 'blue',
  Thanos: 'purple',
  Mimir: 'cyan',
  Cortex: 'green',
  ARMS: 'red',
};

// Backend types mirror the Prometheus-compatible backends the server accepts
// (see SettingsService.PROMETHEUS_COMPATIBLE_TYPES and MetricsBackendType).
const DATA_SOURCE_TYPE_OPTIONS = [
  { value: 'Prometheus', label: 'Prometheus' },
  { value: 'VictoriaMetrics', label: 'VictoriaMetrics' },
  { value: 'Thanos', label: 'Thanos' },
  { value: 'Mimir', label: 'Grafana Mimir' },
  { value: 'Cortex', label: 'Cortex' },
  { value: 'ARMS', label: 'ARMS' },
];

const PAGE_SIZE_OPTIONS = [20, 50, 100];

type DataSourceFormValues = Partial<DataSource>;

const secretFieldNames = ['username', 'password', 'bearerToken'] as const;
const authNeedsSecret = (auth?: string) => auth === 'Basic Auth' || auth === 'Bearer Token';

const testFieldNames = (auth?: string) => {
  if (auth === 'Basic Auth') return ['type', 'url', 'auth', 'username', 'password'];
  if (auth === 'Bearer Token') return ['type', 'url', 'auth', 'bearerToken'];
  return ['type', 'url', 'auth'];
};

const withoutSecrets = (values: DataSourceFormValues): Partial<DataSource> => {
  const sanitized = { ...values };
  secretFieldNames.forEach((field) => {
    delete sanitized[field];
  });
  return sanitized;
};

export const DataSourceTab = () => {
  const { t } = useLang();
  const [dataSources, setDataSources] = useState<DataSource[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState<string | undefined>();
  const [instances, setInstances] = useState<Instance[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingDataSource, setEditingDataSource] = useState<DataSource | null>(null);
  const [dsForm] = Form.useForm();
  const authValue = Form.useWatch('auth', dsForm);
  const [testingKeys, setTestingKeys] = useState<Set<string>>(() => new Set());
  const [submitting, setSubmitting] = useState(false);
  const requestSeqRef = useRef(0);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const loadDataSources = useCallback(() => {
    const requestId = ++requestSeqRef.current;
    Promise.resolve().then(() => {
      if (requestId === requestSeqRef.current) {
        setLoading(true);
      }
    });
    return (async () => {
      try {
        const result = await listDataSourcesPage({
          search: debouncedSearch,
          type: typeFilter,
          page,
          pageSize,
        });
        if (requestId !== requestSeqRef.current) return;
        if (result.items.length === 0 && result.total > 0 && page > 1) {
          const lastPage = Math.max(1, Math.ceil(result.total / result.size));
          if (page > lastPage) {
            setPage(lastPage);
            return;
          }
        }
        setDataSources(result.items);
        setTotal(result.total);
      } catch {
        if (requestId === requestSeqRef.current) {
          message.error('数据源加载失败，请稍后重试');
        }
      } finally {
        if (requestId === requestSeqRef.current) {
          setLoading(false);
        }
      }
    })();
  }, [debouncedSearch, page, pageSize, typeFilter]);

  useEffect(() => {
    void loadDataSources();
  }, [loadDataSources]);

  useEffect(
    () => () => {
      requestSeqRef.current += 1;
    },
    [],
  );

  useEffect(() => {
    let cancelled = false;
    void listInstances()
      .then((nextInstances) => {
        if (!cancelled) setInstances(nextInstances);
      })
      .catch(() => {
        if (!cancelled) setInstances([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleTestConnection = async (
    data: Pick<DataSource, 'type' | 'url' | 'auth'> & Partial<DataSource>,
    key: string,
  ) => {
    if (key !== 'modal' && authNeedsSecret(data.auth)) {
      message.warning('认证数据源请编辑后输入凭据再测试连接');
      return;
    }
    setTestingKeys((previous) => new Set(previous).add(key));
    try {
      const result = await testDataSource(data);
      if (result.success) message.success(result.message);
      else message.error(result.message);
    } catch {
      message.error('连接测试失败，请稍后重试');
    } finally {
      setTestingKeys((previous) => {
        const next = new Set(previous);
        next.delete(key);
        return next;
      });
    }
  };

  const openCreateModal = () => {
    setEditingDataSource(null);
    dsForm.resetFields();
    dsForm.setFieldValue('auth', 'None');
    setModalOpen(true);
  };

  const openEditModal = (dataSource: DataSource) => {
    setEditingDataSource(dataSource);
    dsForm.setFieldsValue(dataSource);
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await dsForm.validateFields();
      const dataSourceValues = withoutSecrets(values);
      setSubmitting(true);
      const saved = editingDataSource
        ? await updateDataSource({ ...editingDataSource, ...dataSourceValues })
        : await createDataSource(dataSourceValues);
      if (editingDataSource) {
        setDataSources((previous) =>
          previous.map((dataSource) => (dataSource.key === saved.key ? saved : dataSource)),
        );
      } else {
        setPage(1);
      }
      await loadDataSources();
      message.success(editingDataSource ? '数据源已更新' : '数据源已添加');
      setModalOpen(false);
      dsForm.resetFields();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return; // validation failure; antd already shows field-level errors
      }
      message.error('保存数据源失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (dataSource: DataSource) => {
    try {
      await deleteDataSource(dataSource.key);
      if (dataSources.length === 1 && page > 1) {
        setPage(page - 1);
      } else {
        await loadDataSources();
      }
      message.success('数据源已删除');
    } catch {
      message.error('删除数据源失败，请稍后重试');
    }
  };

  const columns: ColumnsType<DataSource> = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (t: string) => <Tag color={typeTagColor[t]}>{t}</Tag>,
    },
    { title: 'URL', dataIndex: 'url', key: 'url' },
    {
      title: '适用实例',
      dataIndex: 'instanceIds',
      key: 'instanceIds',
      render: (instanceIds: string[] | undefined) => {
        if (!instanceIds?.length) return '全局';
        return instanceIds
          .map(
            (instanceId) =>
              instances.find(
                (instance) => instance.name === instanceId || String(instance.id) === instanceId,
              )?.name ?? instanceId,
          )
          .join('、');
      },
    },
    { title: '认证方式', dataIndex: 'auth', key: 'auth' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: DataSource['status']) =>
        status && STATUS_MAP[status] ? (
          <StatusBadge status={status} />
        ) : (
          <Text type="secondary">{t('settings.dataSourceNotTested')}</Text>
        ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: DataSource) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<ApiOutlined />}
            loading={testingKeys.has(record.key)}
            disabled={authNeedsSecret(record.auth)}
            title={
              authNeedsSecret(record.auth) ? '认证数据源请编辑后输入凭据再测试连接' : undefined
            }
            onClick={() => void handleTestConnection(record, record.key)}
          >
            测试连接
          </Button>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEditModal(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定要删除该数据源吗？"
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
      <Flex justify="space-between" align="center" gap={12} wrap style={{ marginBottom: 16 }}>
        <Flex gap={12} align="center" wrap>
          <Input
            allowClear
            prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
            placeholder="搜索数据源名称"
            style={{ width: 240 }}
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(1);
            }}
          />
          <Select
            allowClear
            placeholder="全部类型"
            style={{ width: 160 }}
            value={typeFilter}
            onChange={(value) => {
              setTypeFilter(value);
              setPage(1);
            }}
            options={DATA_SOURCE_TYPE_OPTIONS}
          />
        </Flex>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal} disabled={loading}>
          添加数据源
        </Button>
      </Flex>

      <Table<DataSource>
        columns={columns}
        dataSource={dataSources}
        rowKey="key"
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: PAGE_SIZE_OPTIONS.map(String),
          showTotal: (count) => `共 ${count} 条`,
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
        title={editingDataSource ? '编辑数据源' : '添加数据源'}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          setEditingDataSource(null);
          dsForm.resetFields();
        }}
        onOk={() => void handleSubmit()}
        confirmLoading={submitting}
        destroyOnHidden
      >
        <Form form={dsForm} layout="vertical" preserve={false}>
          <Form.Item
            label="名称"
            name="name"
            rules={[{ required: true, message: '请输入数据源名称' }]}
          >
            <Input placeholder="例如：Prometheus 生产监控" />
          </Form.Item>

          <Form.Item
            label="类型"
            name="type"
            rules={[{ required: true, message: '请选择数据源类型' }]}
          >
            <Select placeholder="请选择" virtual={false} options={DATA_SOURCE_TYPE_OPTIONS} />
          </Form.Item>

          <Form.Item
            label="URL"
            name="url"
            rules={[{ required: true, message: '请输入数据源 URL' }]}
          >
            <Input placeholder="http://localhost:9090" />
          </Form.Item>

          <Form.Item label="适用实例" name="instanceIds" extra="留空时可用于所有实例">
            <Select
              mode="multiple"
              allowClear
              placeholder="选择此数据源对应的实例"
              options={instances.map((instance) => ({
                value: instance.name,
                label: instance.name,
              }))}
            />
          </Form.Item>

          <Form.Item label="认证方式" name="auth" initialValue="None">
            <Select
              virtual={false}
              onChange={() => {
                dsForm.setFieldsValue({
                  username: undefined,
                  password: undefined,
                  bearerToken: undefined,
                });
              }}
              options={[
                { value: 'None', label: 'None' },
                { value: 'Basic Auth', label: 'Basic Auth' },
                { value: 'Bearer Token', label: 'Bearer Token' },
              ]}
            />
          </Form.Item>

          {authValue === 'Basic Auth' && (
            <>
              <Form.Item
                label="用户名"
                name="username"
                rules={[{ required: true, message: '请输入用户名' }]}
              >
                <Input autoComplete="username" placeholder="prometheus" />
              </Form.Item>
              <Form.Item
                label="密码"
                name="password"
                rules={[{ required: true, message: '请输入密码' }]}
              >
                <Input.Password autoComplete="current-password" placeholder="请输入密码" />
              </Form.Item>
            </>
          )}

          {authValue === 'Bearer Token' && (
            <Form.Item
              label="Bearer Token"
              name="bearerToken"
              rules={[{ required: true, message: '请输入 Bearer Token' }]}
            >
              <Input.Password autoComplete="off" placeholder="请输入 Token" />
            </Form.Item>
          )}

          <Button
            icon={<ApiOutlined />}
            loading={testingKeys.has('modal')}
            onClick={() => {
              void dsForm
                .validateFields(testFieldNames(authValue))
                .then((values) => handleTestConnection(values, 'modal'))
                .catch(() => undefined);
            }}
            style={{ marginTop: 8 }}
          >
            测试连接
          </Button>
        </Form>
      </Modal>
    </>
  );
};

export default DataSourceTab;
