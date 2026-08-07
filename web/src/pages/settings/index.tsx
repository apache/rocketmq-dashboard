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
  Checkbox,
  Descriptions,
  Divider,
  Flex,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  PlusOutlined,
  GithubOutlined,
  BookOutlined,
  GlobalOutlined,
  DeleteOutlined,
  EditOutlined,
  ApiOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';

import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import StatusBadge from '../../components/StatusBadge';
import { getGeneralSettings, saveGeneralSettings } from '../../api/settings';
import type { GeneralSettingsUpdate } from '../../api/settings';
import {
  createDataSource,
  deleteDataSource,
  listDataSources,
  testDataSource,
  updateDataSource,
} from '../../api/settings';
import type { DataSource } from '../../api/settings';
import { STATUS_MAP } from '../../constants/theme';

const { Title, Text, Link: TypoLink } = Typography;

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

// ─── General Settings Tab ───────────────────────────────────────────────────

const GeneralSettingsTab = () => {
  const [form] = Form.useForm<GeneralSettingsUpdate>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [apiKeyConfigured, setApiKeyConfigured] = useState(false);
  const clearApiKey = Form.useWatch('clearApiKey', form);

  useEffect(() => {
    let cancelled = false;
    void getGeneralSettings()
      .then((settings) => {
        if (!cancelled) {
          setApiKeyConfigured(settings.apiKeyConfigured);
          form.setFieldsValue({ ...settings, apiKey: undefined, clearApiKey: false });
        }
      })
      .catch(() => {
        if (!cancelled) message.error('通用设置加载失败，请稍后重试');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [form]);

  const handleFinish = async (values: GeneralSettingsUpdate) => {
    setSaving(true);
    try {
      await saveGeneralSettings(values);
      setApiKeyConfigured(
        values.clearApiKey ? false : apiKeyConfigured || Boolean(values.apiKey?.trim()),
      );
      form.setFieldsValue({ apiKey: undefined, clearApiKey: false });
      message.success('设置已保存');
    } catch {
      message.error('设置保存失败，请稍后重试');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Form
      form={form}
      layout="horizontal"
      labelCol={{ span: 4 }}
      wrapperCol={{ span: 14 }}
      onFinish={handleFinish}
      style={{ maxWidth: 800 }}
    >
      {/* ── 外观 ── */}
      <Divider orientation="left">
        <Title level={5} style={{ margin: 0 }}>
          外观
        </Title>
      </Divider>

      <Form.Item label="主题模式" name="theme">
        <Radio.Group>
          <Radio value="light">浅色</Radio>
          <Radio value="dark">深色</Radio>
          <Radio value="system">跟随系统</Radio>
        </Radio.Group>
      </Form.Item>

      <Form.Item label="紧凑模式" name="compact" valuePropName="checked">
        <Switch />
      </Form.Item>

      {/* ── 通知 ── */}
      <Divider orientation="left">
        <Title level={5} style={{ margin: 0 }}>
          通知
        </Title>
      </Divider>

      <Form.Item
        label="桌面通知"
        name="desktopNotify"
        valuePropName="checked"
        extra="启用后将通过浏览器推送告警通知"
      >
        <Switch />
      </Form.Item>

      <Form.Item label="通知声音" name="notifySound" valuePropName="checked">
        <Switch />
      </Form.Item>

      {/* ── 安全 ── */}
      <Divider orientation="left">
        <Title level={5} style={{ margin: 0 }}>
          安全
        </Title>
      </Divider>

      <Form.Item
        label="会话超时"
        name="sessionTimeout"
        extra="应用于新创建的会话，已登录用户保持原到期时间"
      >
        <InputNumber min={5} max={1440} addonAfter="分钟" />
      </Form.Item>

      <Form.Item name="requireLogin" hidden>
        <Input />
      </Form.Item>
      <Text type="secondary">
        登录保护由服务端 STUDIO_AUTH_LOGIN_REQUIRED 配置决定，修改后重启服务生效。
      </Text>

      {/* ── AI 配置 ── */}
      <Divider orientation="left">
        <Title level={5} style={{ margin: 0 }}>
          AI 配置
        </Title>
      </Divider>

      <Form.Item label="LLM 提供商" name="llmProvider">
        <Select
          options={[
            { value: 'openai', label: 'OpenAI' },
            { value: 'azure', label: 'Azure OpenAI' },
            { value: 'ollama', label: 'Ollama' },
            { value: 'qwen', label: '通义千问' },
          ]}
        />
      </Form.Item>

      <Form.Item
        label="API Key"
        name="apiKey"
        extra={apiKeyConfigured ? '已配置；留空将保留现有密钥' : '尚未配置'}
      >
        <Input.Password placeholder="sk-..." disabled={clearApiKey} />
      </Form.Item>

      {apiKeyConfigured && (
        <Form.Item name="clearApiKey" valuePropName="checked" wrapperCol={{ offset: 4, span: 14 }}>
          <Checkbox
            onChange={(event) => {
              if (event.target.checked) form.setFieldValue('apiKey', undefined);
            }}
          >
            清除已保存的 API Key
          </Checkbox>
        </Form.Item>
      )}

      <Form.Item label="模型名称" name="model">
        <Input placeholder="qwen-max" />
      </Form.Item>

      <Form.Item label="Base URL" name="baseUrl">
        <Input placeholder="https://api.example.com/v1" />
      </Form.Item>

      {/* ── Submit ── */}
      <Form.Item wrapperCol={{ offset: 4, span: 14 }}>
        <Button type="primary" htmlType="submit" loading={saving} disabled={loading}>
          保存设置
        </Button>
      </Form.Item>
    </Form>
  );
};

// ─── Data Source Tab ────────────────────────────────────────────────────────

export const DataSourceTab = () => {
  const [dataSources, setDataSources] = useState<DataSource[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingDataSource, setEditingDataSource] = useState<DataSource | null>(null);
  const [dsForm] = Form.useForm();
  const authValue = Form.useWatch('auth', dsForm);
  const [testingKey, setTestingKey] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void listDataSources()
      .then((sources) => {
        if (!cancelled) setDataSources(sources);
      })
      .catch(() => {
        if (!cancelled) message.error('数据源加载失败，请稍后重试');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
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
    setTestingKey(key);
    try {
      const result = await testDataSource(data);
      if (result.success) message.success(result.message);
      else message.error(result.message);
    } catch {
      message.error('连接测试失败，请稍后重试');
    } finally {
      setTestingKey(null);
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
      setDataSources((previous) =>
        editingDataSource
          ? previous.map((dataSource) => (dataSource.key === saved.key ? saved : dataSource))
          : [...previous, saved],
      );
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
      setDataSources((previous) => previous.filter((item) => item.key !== dataSource.key));
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
    { title: '认证方式', dataIndex: 'auth', key: 'auth' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (s: DataSource['status']) => <StatusBadge status={s as keyof typeof STATUS_MAP} />,
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
            loading={testingKey === record.key}
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
          <Button
            type="link"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => void handleDelete(record)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Flex justify="flex-end" style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
          添加数据源
        </Button>
      </Flex>

      <Table<DataSource>
        columns={columns}
        dataSource={dataSources}
        rowKey="key"
        loading={loading}
        pagination={false}
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
        destroyOnClose
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
            loading={testingKey === 'modal'}
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

// ─── About Tab ──────────────────────────────────────────────────────────────

const AboutTab = () => (
  <div style={{ maxWidth: 800 }}>
    <Descriptions column={1} bordered size="small">
      <Descriptions.Item label="版本">0.1.0</Descriptions.Item>
      <Descriptions.Item label="构建时间">2024-01-15 14:30:00</Descriptions.Item>
      <Descriptions.Item label="RocketMQ 支持版本">4.x / 5.x</Descriptions.Item>
      <Descriptions.Item label="前端框架">React 18 + Ant Design 5</Descriptions.Item>
      <Descriptions.Item label="后端框架">Spring Boot 3 + RocketMQ MCP Server</Descriptions.Item>
      <Descriptions.Item label="License">Apache 2.0</Descriptions.Item>
    </Descriptions>

    <Divider />

    <Title level={5}>相关链接</Title>
    <Space size="middle" style={{ marginBottom: 24 }}>
      <TypoLink href="https://github.com/apache/rocketmq" target="_blank" rel="noopener noreferrer">
        <GithubOutlined /> GitHub
      </TypoLink>
      <TypoLink href="https://rocketmq.apache.org/docs/" target="_blank" rel="noopener noreferrer">
        <BookOutlined /> 文档中心
      </TypoLink>
      <TypoLink href="https://rocketmq.apache.org/" target="_blank" rel="noopener noreferrer">
        <GlobalOutlined /> RocketMQ 社区
      </TypoLink>
    </Space>

    <Divider />

    <Text type="secondary">
      Copyright © 2024 Apache Software Foundation. Licensed under the Apache License, Version 2.0.
    </Text>
  </div>
);

// ─── Page ───────────────────────────────────────────────────────────────────

const SettingsPage = () => {
  const { t } = useLang();

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title={t('settings.title')} subtitle="管理应用配置与数据源" />

      <Tabs
        defaultActiveKey="general"
        items={[
          { key: 'general', label: '通用设置', children: <GeneralSettingsTab /> },
          { key: 'datasource', label: '数据源管理', children: <DataSourceTab /> },
          { key: 'about', label: '关于', children: <AboutTab /> },
        ]}
      />
    </div>
  );
};

export default SettingsPage;
