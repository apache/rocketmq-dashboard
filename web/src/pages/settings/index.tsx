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
import type { GeneralSettings } from '../../api/settings';

const { Title, Text, Link: TypoLink } = Typography;

// ─── Types ──────────────────────────────────────────────────────────────────

interface DataSource {
  key: string;
  name: string;
  type: 'Prometheus' | 'VictoriaMetrics' | 'Thanos';
  url: string;
  auth: string;
  status: 'healthy' | 'error';
}

// ─── Mock Data ──────────────────────────────────────────────────────────────

const mockDataSources: DataSource[] = [
  {
    key: '1',
    name: 'Prometheus 生产监控',
    type: 'Prometheus',
    url: 'http://prometheus.prod:9090',
    auth: 'Bearer Token',
    status: 'healthy',
  },
  {
    key: '2',
    name: 'VictoriaMetrics 历史数据',
    type: 'VictoriaMetrics',
    url: 'http://vm.prod:8428',
    auth: 'Basic Auth',
    status: 'healthy',
  },
  {
    key: '3',
    name: '测试环境 Prometheus',
    type: 'Prometheus',
    url: 'http://prometheus.test:9090',
    auth: 'None',
    status: 'error',
  },
];

const typeTagColor: Record<string, string> = {
  Prometheus: 'orange',
  VictoriaMetrics: 'blue',
  Thanos: 'purple',
};

// ─── General Settings Tab ───────────────────────────────────────────────────

const GeneralSettingsTab = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void getGeneralSettings()
      .then((settings) => {
        if (!cancelled) form.setFieldsValue(settings);
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

  const handleFinish = async (values: GeneralSettings) => {
    setSaving(true);
    try {
      await saveGeneralSettings(values);
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

      <Form.Item label="会话超时" name="sessionTimeout">
        <InputNumber min={5} max={1440} addonAfter="分钟" />
      </Form.Item>

      <Form.Item label="需要登录" name="requireLogin" valuePropName="checked">
        <Switch />
      </Form.Item>

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

      <Form.Item label="API Key" name="apiKey">
        <Input.Password placeholder="sk-..." />
      </Form.Item>

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

const DataSourceTab = () => {
  const [modalOpen, setModalOpen] = useState(false);
  const [dsForm] = Form.useForm();
  const [testing, setTesting] = useState(false);

  const handleTestConnection = () => {
    setTesting(true);
    setTimeout(() => {
      setTesting(false);
      message.success('连接成功');
    }, 1200);
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
      render: (s: DataSource['status']) => <StatusBadge status={s} />,
    },
    {
      title: '操作',
      key: 'action',
      render: () => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<ApiOutlined />}
            onClick={() => message.info('正在测试连接...')}
          >
            测试连接
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />}>
            编辑
          </Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Flex justify="flex-end" style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          添加数据源
        </Button>
      </Flex>

      <Table<DataSource>
        columns={columns}
        dataSource={mockDataSources}
        pagination={false}
        size="middle"
      />

      <Modal
        title="添加数据源"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => {
          dsForm.validateFields().then(() => {
            message.success('数据源已添加');
            setModalOpen(false);
            dsForm.resetFields();
          });
        }}
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
            <Select
              placeholder="请选择"
              options={[
                { value: 'Prometheus', label: 'Prometheus' },
                { value: 'VictoriaMetrics', label: 'VictoriaMetrics' },
                { value: 'Thanos', label: 'Thanos' },
              ]}
            />
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
              options={[
                { value: 'None', label: 'None' },
                { value: 'Basic Auth', label: 'Basic Auth' },
                { value: 'Bearer Token', label: 'Bearer Token' },
              ]}
            />
          </Form.Item>

          <Button
            icon={<ApiOutlined />}
            loading={testing}
            onClick={handleTestConnection}
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
      <TypoLink href="https://github.com/apache/rocketmq" target="_blank">
        <GithubOutlined /> GitHub
      </TypoLink>
      <TypoLink href="https://rocketmq.apache.org/docs/" target="_blank">
        <BookOutlined /> 文档中心
      </TypoLink>
      <TypoLink href="https://rocketmq.apache.org/" target="_blank">
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
