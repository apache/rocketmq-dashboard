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
import { DownloadSimple, MagnifyingGlass } from '@phosphor-icons/react';
import { ApiOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';

import { useLang } from '../../i18n/LangContext';
import StatusBadge from '../../components/StatusBadge';
import {
  createDataSource,
  deleteDataSource,
  listAllDataSources,
  listDataSourcesPage,
  testDataSource,
  updateDataSource,
} from '../../api/settings';
import type { DataSource } from '../../api/settings';
import { STATUS_MAP } from '../../constants/theme';
import { listInstances } from '../../services/instanceService';
import type { Instance } from '../../api/instance';
import { buildCsv, downloadCsv, type CsvColumn } from '../../utils/download';

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

interface DataSourceExportRow extends DataSource {
  instanceNames: string;
  statusLabel: string;
}

const DATA_SOURCE_EXPORT_COLUMNS: CsvColumn<DataSourceExportRow>[] = [
  { header: 'Name', value: (source) => source.name },
  { header: 'Type', value: (source) => source.type },
  { header: 'URL', value: (source) => source.url },
  { header: 'Applicable Instances', value: (source) => source.instanceNames },
  { header: 'Authentication', value: (source) => source.auth },
  { header: 'Status', value: (source) => source.statusLabel },
];

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
  const [exporting, setExporting] = useState(false);
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
          message.error(t('settings.dataSourceLoadFailed'));
        }
      } finally {
        if (requestId === requestSeqRef.current) {
          setLoading(false);
        }
      }
    })();
  }, [debouncedSearch, page, pageSize, t, typeFilter]);

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
      message.warning(t('settings.dataSourceAuthTestHint'));
      return;
    }
    setTestingKeys((previous) => new Set(previous).add(key));
    try {
      const result = await testDataSource(data);
      if (result.success) message.success(result.message);
      else message.error(result.message);
    } catch {
      message.error(t('settings.connectionTestFailed'));
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
      message.success(
        t(editingDataSource ? 'settings.dataSourceUpdated' : 'settings.dataSourceAdded'),
      );
      setModalOpen(false);
      dsForm.resetFields();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return; // validation failure; antd already shows field-level errors
      }
      message.error(t('settings.dataSourceSaveFailed'));
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
      message.success(t('settings.dataSourceDeleted'));
    } catch {
      message.error(t('settings.dataSourceDeleteFailed'));
    }
  };

  const formatInstanceIds = (instanceIds: string[] | undefined) => {
    if (!instanceIds?.length) return t('settings.global');
    return instanceIds
      .map(
        (instanceId) =>
          instances.find(
            (instance) => instance.name === instanceId || String(instance.id) === instanceId,
          )?.name ?? instanceId,
      )
      .join('、');
  };

  const formatStatus = (status: DataSource['status']) => {
    if (!status || !STATUS_MAP[status]) return t('settings.dataSourceNotTested');
    return t(STATUS_MAP[status].labelKey);
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const exported = await listAllDataSources({
        search: debouncedSearch,
        type: typeFilter,
      });
      const rows = exported.map((source) => ({
        ...source,
        instanceNames: formatInstanceIds(source.instanceIds),
        statusLabel: formatStatus(source.status),
      }));
      const filename = `rocketmq-data-sources-${new Date().toISOString().slice(0, 10)}.csv`;
      downloadCsv(filename, buildCsv(DATA_SOURCE_EXPORT_COLUMNS, rows));
      message.success(t('settings.dataSourceExported', { total: rows.length }));
    } catch {
      message.error(t('settings.dataSourceExportFailed'));
    } finally {
      setExporting(false);
    }
  };

  const columns: ColumnsType<DataSource> = [
    { title: t('common.name'), dataIndex: 'name', key: 'name' },
    {
      title: t('common.type'),
      dataIndex: 'type',
      key: 'type',
      render: (t: string) => <Tag color={typeTagColor[t]}>{t}</Tag>,
    },
    { title: 'URL', dataIndex: 'url', key: 'url' },
    {
      title: t('settings.instances'),
      dataIndex: 'instanceIds',
      key: 'instanceIds',
      render: formatInstanceIds,
    },
    { title: t('settings.authentication'), dataIndex: 'auth', key: 'auth' },
    {
      title: t('common.status'),
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
      title: t('common.actions'),
      key: 'action',
      render: (_: unknown, record: DataSource) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<ApiOutlined />}
            loading={testingKeys.has(record.key)}
            disabled={authNeedsSecret(record.auth)}
            title={authNeedsSecret(record.auth) ? t('settings.dataSourceAuthTestHint') : undefined}
            onClick={() => void handleTestConnection(record, record.key)}
          >
            {t('settings.testConnection')}
          </Button>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEditModal(record)}
          >
            {t('common.edit')}
          </Button>
          <Popconfirm
            title={t('settings.deleteDataSourceConfirm')}
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
            placeholder={t('settings.searchDataSources')}
            style={{ width: 240 }}
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(1);
            }}
          />
          <Select
            allowClear
            placeholder={t('settings.allTypes')}
            style={{ width: 160 }}
            value={typeFilter}
            onChange={(value) => {
              setTypeFilter(value);
              setPage(1);
            }}
            options={DATA_SOURCE_TYPE_OPTIONS}
          />
        </Flex>
        <Space>
          <Button
            icon={<DownloadSimple size={14} />}
            onClick={() => void handleExport()}
            loading={exporting}
            disabled={loading || total === 0}
          >
            {t('common.export')}
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={openCreateModal}
            disabled={loading}
          >
            {t('settings.addDataSource')}
          </Button>
        </Space>
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
        title={t(editingDataSource ? 'settings.editDataSource' : 'settings.addDataSource')}
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
            label={t('common.name')}
            name="name"
            rules={[{ required: true, message: t('settings.dataSourceNameRequired') }]}
          >
            <Input placeholder={t('settings.dataSourceNameExample')} />
          </Form.Item>

          <Form.Item
            label={t('common.type')}
            name="type"
            rules={[{ required: true, message: t('settings.selectType') }]}
          >
            <Select
              placeholder={t('settings.selectType')}
              virtual={false}
              options={DATA_SOURCE_TYPE_OPTIONS}
            />
          </Form.Item>

          <Form.Item
            label="URL"
            name="url"
            rules={[{ required: true, message: t('settings.dataSourceUrlRequired') }]}
          >
            <Input placeholder="http://localhost:9090" />
          </Form.Item>

          <Form.Item
            label={t('settings.instances')}
            name="instanceIds"
            extra={t('settings.instancesHelp')}
          >
            <Select
              mode="multiple"
              allowClear
              placeholder={t('settings.selectDataSourceInstances')}
              options={instances.map((instance) => ({
                value: instance.name,
                label: instance.name,
              }))}
            />
          </Form.Item>

          <Form.Item label={t('settings.authentication')} name="auth" initialValue="None">
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
                label={t('settings.username')}
                name="username"
                rules={[{ required: true, message: t('settings.usernameRequired') }]}
              >
                <Input autoComplete="username" placeholder="prometheus" />
              </Form.Item>
              <Form.Item
                label={t('settings.password')}
                name="password"
                rules={[{ required: true, message: t('settings.passwordRequired') }]}
              >
                <Input.Password
                  autoComplete="current-password"
                  placeholder={t('settings.passwordRequired')}
                />
              </Form.Item>
            </>
          )}

          {authValue === 'Bearer Token' && (
            <Form.Item
              label="Bearer Token"
              name="bearerToken"
              rules={[{ required: true, message: t('settings.bearerTokenRequired') }]}
            >
              <Input.Password autoComplete="off" placeholder={t('settings.enterToken')} />
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
            {t('settings.testConnection')}
          </Button>
        </Form>
      </Modal>
    </>
  );
};

export default DataSourceTab;
