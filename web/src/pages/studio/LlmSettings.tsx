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
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Select,
  Slider,
  Space,
  Tag,
  App,
} from 'antd';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import {
  getLlmConfig,
  getLlmModels,
  saveLlmConfig,
  testLlmConnection,
  type LlmConfig,
  type LlmTestResult,
} from '../../api/llm';
import { fallbackModelOptions } from './llmModelOptions';

const PROVIDER_OPTIONS = [
  { value: 'tongyi', label: '通义千问（DashScope）' },
  { value: 'openai', label: 'OpenAI' },
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'ollama', label: 'Ollama（本地）' },
];

const ENGINE_OPTIONS = [
  { value: 'claude-code', label: 'Claude Code（默认）' },
  { value: 'qoder', label: 'Qoder CLI' },
  { value: 'http', label: 'HTTP（OpenAI 兼容）' },
];

const DEFAULT_BASE_URL: Record<string, string> = {
  tongyi: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  openai: 'https://api.openai.com/v1',
  deepseek: 'https://api.deepseek.com/v1',
  ollama: 'http://localhost:11434/v1',
};

interface TestState {
  success: boolean;
  msg: string;
  hint?: string;
}

const LlmSettingsPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const [form] = Form.useForm();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [apiKeyConfigured, setApiKeyConfigured] = useState(false);
  const [modelOptions, setModelOptions] = useState<{ value: string; label: string }[]>([]);
  const [testResult, setTestResult] = useState<TestState | null>(null);

  const buildModelOptions = (
    nextProvider: string,
    remoteModels: string[],
    currentModel?: string,
  ) => {
    const source =
      remoteModels.length > 0
        ? remoteModels.map((id) => ({ value: id, label: id }))
        : fallbackModelOptions(nextProvider);
    if (currentModel && !source.some((option) => option.value === currentModel)) {
      source.unshift({ value: currentModel, label: currentModel });
    }
    return source;
  };

  const applyConfig = (config: LlmConfig, remoteModels: string[]) => {
    const nextProvider = config.provider || 'tongyi';
    setApiKeyConfigured(Boolean(config.apiKeyConfigured));
    setModelOptions(buildModelOptions(nextProvider, remoteModels, config.model));
    form.setFieldsValue({
      engine: config.engine || 'claude-code',
      provider: nextProvider,
      model: config.model || undefined,
      apiBase: config.apiBase || DEFAULT_BASE_URL[nextProvider] || '',
      maxTokens: config.maxTokens || 4096,
      temperature: config.temperature ?? 0.7,
      apiKey: undefined,
    });
  };

  useEffect(() => {
    let cancelled = false;
    Promise.all([getLlmConfig(), getLlmModels().catch(() => null)])
      .then(([config, models]) => {
        if (cancelled) return;
        applyConfig(config, models?.data?.map((m) => m.id || '').filter(Boolean) ?? []);
      })
      .catch(() => {
        if (!cancelled) message.error(t('llm.loadFailed'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleProviderChange = (nextProvider: string) => {
    setModelOptions(fallbackModelOptions(nextProvider));
    const fallbackModel = fallbackModelOptions(nextProvider)[0]?.value;
    form.setFieldsValue({
      provider: nextProvider,
      model: fallbackModel,
      apiBase: DEFAULT_BASE_URL[nextProvider] || form.getFieldValue('apiBase'),
    });
    setTestResult(null);
  };

  const buildPayload = async (): Promise<LlmConfig | null> => {
    let values;
    try {
      values = await form.validateFields();
    } catch {
      return null;
    }
    const apiKey = (values.apiKey as string | undefined)?.trim();
    return {
      provider: values.provider,
      engine: values.engine || 'claude-code',
      apiBase: values.apiBase,
      model: values.model,
      maxTokens: values.maxTokens,
      temperature: values.temperature,
      enabled: true,
      // 留空表示保留服务端已配置的密钥（含环境变量注入的 token）
      ...(apiKey ? { apiKey } : {}),
    };
  };

  const applyTestResult = (result: LlmTestResult) => {
    if (result.status === 0) {
      setTestResult({ success: true, msg: result.msg || '连接成功' });
    } else {
      setTestResult({
        success: false,
        msg: result.errMsg || '连接测试失败',
        hint: result.hint,
      });
    }
  };

  const handleTest = async () => {
    const payload = await buildPayload();
    if (!payload) return;
    setTesting(true);
    setTestResult(null);
    try {
      applyTestResult(await testLlmConnection(payload));
    } catch {
      setTestResult({ success: false, msg: '连接测试请求失败，请稍后重试' });
    } finally {
      setTesting(false);
    }
  };

  const handleSave = async () => {
    const payload = await buildPayload();
    if (!payload) return;
    setSaving(true);
    try {
      const result = await saveLlmConfig(payload);
      if (result.status === 0) {
        message.success('保存成功');
        if (payload.apiKey) {
          setApiKeyConfigured(true);
          form.setFieldValue('apiKey', undefined);
        }
      } else {
        message.error(result.errMsg || '保存失败');
      }
    } catch {
      message.error('保存请求失败，请稍后重试');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title={t('llm.title')} subtitle="配置 AI 助手使用的模型服务" />

      <Card loading={loading} style={{ maxWidth: 720 }}>
        <Form
          form={form}
          layout="vertical"
          initialValues={{ provider: 'tongyi', engine: 'claude-code' }}
        >
          <Form.Item
            label="执行引擎"
            name="engine"
            extra="Claude Code / Qoder 引擎在服务器上以 CLI 子进程方式运行，凭据经环境变量注入；HTTP 引擎直连 OpenAI 兼容接口"
          >
            <Select options={ENGINE_OPTIONS} />
          </Form.Item>

          <Form.Item
            label="模型服务商"
            name="provider"
            rules={[{ required: true, message: '请选择模型服务商' }]}
          >
            <Select options={PROVIDER_OPTIONS} onChange={handleProviderChange} />
          </Form.Item>

          <Form.Item
            label="模型"
            name="model"
            rules={[{ required: true, message: '请选择或输入模型' }]}
            extra="默认使用 qwen3.8-max"
          >
            <Select showSearch options={modelOptions} placeholder="选择模型" />
          </Form.Item>

          <Form.Item
            label="API Key"
            name="apiKey"
            extra={
              apiKeyConfigured
                ? '已配置（可能来自环境变量 RMQ_LLM_TOKEN）；留空将保留现有密钥'
                : '请输入 API Key'
            }
          >
            <Input.Password
              placeholder={apiKeyConfigured ? '••••••••（已配置，留空保留）' : 'sk-...'}
              autoComplete="new-password"
            />
          </Form.Item>
          {apiKeyConfigured && (
            <div style={{ marginTop: -16, marginBottom: 16 }}>
              <Tag color="green">密钥已配置</Tag>
            </div>
          )}

          <Form.Item
            label="API Base URL"
            name="apiBase"
            rules={[
              { required: true, message: '请输入 API Base URL' },
              {
                pattern: /^https?:\/\/.+/,
                message: '需为 http/https 地址',
              },
            ]}
          >
            <Input placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1" />
          </Form.Item>

          <Form.Item label="Temperature" name="temperature">
            <Slider min={0} max={2} step={0.1} marks={{ 0: '0', 0.7: '0.7', 2: '2' }} />
          </Form.Item>

          <Form.Item label="Max Tokens" name="maxTokens">
            <InputNumber min={1} max={200000} style={{ width: 200 }} />
          </Form.Item>

          {testResult && (
            <Alert
              style={{ marginBottom: 16 }}
              type={testResult.success ? 'success' : 'error'}
              showIcon
              message={testResult.msg}
              description={testResult.hint}
            />
          )}

          <Form.Item style={{ marginBottom: 0 }}>
            <Space>
              <Button type="primary" loading={saving} onClick={() => void handleSave()}>
                保存
              </Button>
              <Button loading={testing} onClick={() => void handleTest()}>
                测试连接
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default LlmSettingsPage;
