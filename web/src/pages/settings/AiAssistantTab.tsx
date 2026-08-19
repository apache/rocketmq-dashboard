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

import { useEffect, useRef, useState } from 'react';
import { CaretDownOutlined, ControlOutlined, KeyOutlined, RobotOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  AutoComplete,
  Button,
  Card,
  Col,
  Flex,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Slider,
  Space,
  Tag,
} from 'antd';
import { useLang } from '../../i18n/LangContext';
import {
  getLlmConfig,
  getLlmModels,
  saveLlmConfig,
  testLlmConnection,
  type LlmConfig,
  type LlmTestResult,
} from '../../api/llm';
import { fallbackModelOptions } from '../studio/llmModelOptions';

const PROVIDER_OPTIONS = [
  { value: 'tongyi', label: '通义千问（DashScope）' },
  { value: 'openai', label: 'OpenAI' },
  { value: 'azure', label: 'Azure OpenAI' },
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'ollama', label: 'Ollama（本地）' },
  { value: 'bedrock', label: 'AWS Bedrock' },
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

const BASE_URL_PRESETS: Record<string, { value: string; label: string }[]> = {
  tongyi: [
    {
      value: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      label: '百炼 DashScope 标准（compatible-mode）',
    },
    {
      value: 'https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1',
      label: 'Token Plan 网关（OpenAI 兼容）',
    },
    {
      value: 'https://token-plan.cn-beijing.maas.aliyuncs.com/apps/anthropic',
      label: 'Token Plan 网关（Anthropic 兼容）',
    },
  ],
  openai: [{ value: 'https://api.openai.com/v1', label: 'OpenAI 官方' }],
  deepseek: [{ value: 'https://api.deepseek.com/v1', label: 'DeepSeek 官方' }],
  ollama: [{ value: 'http://localhost:11434/v1', label: 'Ollama 本地' }],
};

interface TestState {
  success: boolean;
  msg: string;
  hint?: string;
}

export const AiAssistantTab = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const selectedProvider = Form.useWatch('provider', form);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [apiKeyConfigured, setApiKeyConfigured] = useState(false);
  const [modelOptions, setModelOptions] = useState<{ value: string; label: string }[]>([]);
  const [testResult, setTestResult] = useState<TestState | null>(null);
  const testRequestIdRef = useRef(0);

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
      deploymentName: config.deploymentName || undefined,
      apiVersion: config.apiVersion || '2024-02-15-preview',
      awsRegion: config.awsRegion || 'us-east-1',
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
      testRequestIdRef.current += 1;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const invalidateTestRequest = () => {
    testRequestIdRef.current += 1;
    setTesting(false);
    setTestResult(null);
  };

  const handleProviderChange = (nextProvider: string) => {
    invalidateTestRequest();
    setModelOptions(fallbackModelOptions(nextProvider));
    const fallbackModel = fallbackModelOptions(nextProvider)[0]?.value;
    form.setFieldsValue({
      provider: nextProvider,
      model: fallbackModel,
      apiBase: DEFAULT_BASE_URL[nextProvider] || '',
      deploymentName: nextProvider === 'azure' ? undefined : form.getFieldValue('deploymentName'),
      apiVersion:
        nextProvider === 'azure'
          ? form.getFieldValue('apiVersion') || '2024-02-15-preview'
          : form.getFieldValue('apiVersion'),
      awsRegion:
        nextProvider === 'bedrock'
          ? form.getFieldValue('awsRegion') || 'us-east-1'
          : form.getFieldValue('awsRegion'),
    });
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
      ...(values.provider === 'azure'
        ? { deploymentName: values.deploymentName, apiVersion: values.apiVersion }
        : {}),
      ...(values.provider === 'bedrock' ? { awsRegion: values.awsRegion } : {}),
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
    const requestId = testRequestIdRef.current + 1;
    testRequestIdRef.current = requestId;
    setTesting(true);
    setTestResult(null);
    try {
      const result = await testLlmConnection(payload);
      if (testRequestIdRef.current === requestId) {
        applyTestResult(result);
        if (result.status === 0) {
          const remoteModels = result.models?.map((model) => model.id || '').filter(Boolean) ?? [];
          setModelOptions(buildModelOptions(payload.provider, remoteModels, payload.model));
        }
      }
    } catch {
      if (testRequestIdRef.current === requestId) {
        setTestResult({ success: false, msg: '连接测试请求失败，请稍后重试' });
      }
    } finally {
      if (testRequestIdRef.current === requestId) {
        setTesting(false);
      }
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
        try {
          const models = await getLlmModels();
          const remoteModels = models.data?.map((model) => model.id || '').filter(Boolean) ?? [];
          setModelOptions(buildModelOptions(payload.provider, remoteModels, payload.model));
        } catch {
          message.warning(t('ai.modelsRefreshFailedAfterSave'));
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
    <Flex vertical gap={24} style={{ maxWidth: 860 }}>
      <Form
        form={form}
        layout="vertical"
        initialValues={{ provider: 'tongyi', engine: 'claude-code' }}
        onValuesChange={invalidateTestRequest}
      >
        <Flex vertical gap={24}>
          <Card
            loading={loading}
            title={
              <Space>
                <RobotOutlined />
                执行引擎与模型
              </Space>
            }
          >
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  label="执行引擎"
                  name="engine"
                  extra="Claude Code / Qoder 引擎在服务器上以 CLI 子进程方式运行，凭据经环境变量注入；HTTP 引擎直连 OpenAI 兼容接口"
                >
                  <Select options={ENGINE_OPTIONS} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  label="模型服务商"
                  name="provider"
                  rules={[{ required: true, message: '请选择模型服务商' }]}
                >
                  <Select options={PROVIDER_OPTIONS} onChange={handleProviderChange} />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item
              label="模型"
              name="model"
              rules={[{ required: true, message: '请选择或输入模型' }]}
              extra="默认使用 qwen3.8-max"
              style={{ marginBottom: 0 }}
            >
              <Select showSearch options={modelOptions} placeholder="选择模型" />
            </Form.Item>
          </Card>

          <Card
            loading={loading}
            title={
              <Space>
                <KeyOutlined />
                凭据与接入地址
              </Space>
            }
          >
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
              style={{
                marginBottom:
                  selectedProvider === 'azure' || selectedProvider === 'bedrock' ? undefined : 0,
              }}
            >
              <AutoComplete
                options={BASE_URL_PRESETS[selectedProvider ?? 'tongyi'] ?? []}
                placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1"
                suffixIcon={<CaretDownOutlined style={{ color: '#9CA3AF' }} />}
                filterOption={(input, option) =>
                  (option?.value ?? '').toLowerCase().includes(input.toLowerCase()) ||
                  (option?.label ?? '').toString().toLowerCase().includes(input.toLowerCase())
                }
              />
            </Form.Item>

            {selectedProvider === 'azure' && (
              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item
                    label="Azure Deployment Name"
                    name="deploymentName"
                    rules={[{ required: true, message: 'Enter the Azure OpenAI deployment name' }]}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="my-gpt-deployment" />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    label="Azure API Version"
                    name="apiVersion"
                    rules={[{ required: true, message: 'Enter the Azure OpenAI API version' }]}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="2024-02-15-preview" />
                  </Form.Item>
                </Col>
              </Row>
            )}

            {selectedProvider === 'bedrock' && (
              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item
                    label="AWS Region"
                    name="awsRegion"
                    rules={[{ required: true, message: 'Enter the AWS Bedrock region' }]}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="us-east-1" />
                  </Form.Item>
                </Col>
              </Row>
            )}
          </Card>

          <Card
            loading={loading}
            title={
              <Space>
                <ControlOutlined />
                生成参数
              </Space>
            }
          >
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item label="Temperature" name="temperature" style={{ marginBottom: 0 }}>
                  <Slider min={0} max={2} step={0.1} marks={{ 0: '0', 0.7: '0.7', 2: '2' }} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="Max Tokens" name="maxTokens" style={{ marginBottom: 0 }}>
                  <InputNumber min={1} max={200000} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
          </Card>

          {testResult && (
            <Alert
              type={testResult.success ? 'success' : 'error'}
              showIcon
              message={testResult.msg}
              description={testResult.hint}
            />
          )}

          <Space>
            <Button
              type="primary"
              loading={saving}
              disabled={loading}
              onClick={() => void handleSave()}
            >
              保存
            </Button>
            <Button loading={testing} disabled={loading} onClick={() => void handleTest()}>
              测试连接
            </Button>
          </Space>
        </Flex>
      </Form>
    </Flex>
  );
};

export default AiAssistantTab;
