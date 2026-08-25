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

const DEFAULT_BASE_URL: Record<string, string> = {
  tongyi: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  openai: 'https://api.openai.com/v1',
  anthropic: 'https://api.anthropic.com',
  deepseek: 'https://api.deepseek.com/v1',
  ollama: 'http://localhost:11434/v1',
};

const providerOptions = (t: (key: string) => string) => [
  { value: 'tongyi', label: t('settings.tongyi') },
  { value: 'openai', label: 'OpenAI' },
  { value: 'anthropic', label: 'Anthropic (Claude)' },
  { value: 'azure', label: 'Azure OpenAI' },
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'ollama', label: t('settings.ollamaLocal') },
  { value: 'bedrock', label: 'AWS Bedrock' },
];

const engineOptions = (t: (key: string) => string) => [
  { value: 'claude-code', label: t('settings.claudeCodeDefault') },
  { value: 'qoder', label: 'Qoder CLI' },
  { value: 'http', label: t('settings.httpOpenAiCompatible') },
];

const baseUrlPresets = (
  t: (key: string) => string,
): Record<string, { value: string; label: string }[]> => ({
  tongyi: [
    {
      value: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      label: t('settings.dashscopeStandard'),
    },
    {
      value: 'https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1',
      label: t('settings.tokenPlanOpenAi'),
    },
    {
      value: 'https://token-plan.cn-beijing.maas.aliyuncs.com/apps/anthropic',
      label: t('settings.tokenPlanAnthropic'),
    },
  ],
  openai: [{ value: 'https://api.openai.com/v1', label: t('settings.openaiOfficial') }],
  anthropic: [
    { value: 'https://api.anthropic.com', label: t('settings.anthropicOfficial') },
    {
      value: 'https://token-plan.cn-beijing.maas.aliyuncs.com/apps/anthropic',
      label: t('settings.tokenPlanAnthropic'),
    },
  ],
  deepseek: [{ value: 'https://api.deepseek.com/v1', label: t('settings.deepseekOfficial') }],
  ollama: [{ value: 'http://localhost:11434/v1', label: t('settings.ollamaLocal') }],
});

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

  const handleClearApiKey = async () => {
    const payload = await buildPayload();
    if (!payload) return;
    const confirmed = window.confirm(t('settings.clearApiKeyConfirm'));
    if (!confirmed) return;
    setSaving(true);
    try {
      const result = await saveLlmConfig({
        ...payload,
        apiKey: undefined,
        clearApiKey: true,
      });
      if (result.status === 0) {
        message.success(t('settings.clearApiKeySucceeded'));
        setApiKeyConfigured(false);
        form.setFieldValue('apiKey', undefined);
      } else {
        message.error(result.errMsg || t('settings.clearApiKeyFailed'));
      }
    } catch {
      message.error(t('settings.clearApiKeyFailed'));
    } finally {
      setSaving(false);
    }
  };

  const applyTestResult = (result: LlmTestResult) => {
    if (result.status === 0) {
      setTestResult({ success: true, msg: result.msg || t('settings.connectionSucceeded') });
    } else {
      setTestResult({
        success: false,
        msg: result.errMsg || t('settings.connectionTestFailedShort'),
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
        setTestResult({ success: false, msg: t('settings.connectionTestRequestFailed') });
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
        message.success(t('settings.saveSucceeded'));
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
        message.error(result.errMsg || t('settings.saveFailedShort'));
      }
    } catch {
      message.error(t('settings.saveRequestFailed'));
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
                {t('settings.aiEngineModel')}
              </Space>
            }
          >
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  label={t('settings.aiEngine')}
                  name="engine"
                  extra={t('settings.aiEngineHelp')}
                >
                  <Select options={engineOptions(t)} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  label={t('settings.modelProvider')}
                  name="provider"
                  rules={[{ required: true, message: t('settings.modelProviderRequired') }]}
                >
                  <Select options={providerOptions(t)} onChange={handleProviderChange} />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item
              label={t('settings.model')}
              name="model"
              rules={[{ required: true, message: t('settings.modelRequired') }]}
              extra={t('settings.modelHelp')}
              style={{ marginBottom: 0 }}
            >
              <AutoComplete
                options={modelOptions}
                placeholder={t('settings.selectOrEnterModel')}
                filterOption={(input, option) =>
                  String(option?.value ?? '')
                    .toLowerCase()
                    .includes(input.toLowerCase())
                }
                allowClear
              />
            </Form.Item>
          </Card>

          <Card
            loading={loading}
            title={
              <Space>
                <KeyOutlined />
                {t('settings.credentialsEndpoint')}
              </Space>
            }
          >
            <Form.Item
              label="API Key"
              name="apiKey"
              extra={
                apiKeyConfigured ? t('settings.apiKeyConfiguredHelp') : t('settings.enterApiKey')
              }
            >
              <Input.Password
                placeholder={
                  apiKeyConfigured ? t('settings.apiKeyConfiguredPlaceholder') : 'sk-...'
                }
                autoComplete="new-password"
              />
            </Form.Item>
            {apiKeyConfigured && (
              <div style={{ marginTop: -16, marginBottom: 16 }}>
                <Tag color="green">{t('settings.apiKeyConfigured')}</Tag>
                <Button
                  danger
                  size="small"
                  style={{ marginLeft: 8 }}
                  loading={saving}
                  onClick={() => void handleClearApiKey()}
                >
                  {t('settings.clearApiKey')}
                </Button>
              </div>
            )}

            <Form.Item
              label="API Base URL"
              name="apiBase"
              rules={[
                { required: true, message: t('settings.apiBaseRequired') },
                {
                  pattern: /^https?:\/\/.+/,
                  message: t('settings.apiBaseInvalid'),
                },
              ]}
              style={{
                marginBottom:
                  selectedProvider === 'azure' || selectedProvider === 'bedrock' ? undefined : 0,
              }}
            >
              <AutoComplete
                options={baseUrlPresets(t)[selectedProvider ?? 'tongyi'] ?? []}
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
                {t('settings.generationParameters')}
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
              {t('common.save')}
            </Button>
            <Button loading={testing} disabled={loading} onClick={() => void handleTest()}>
              {t('settings.testConnection')}
            </Button>
          </Space>
        </Flex>
      </Form>
    </Flex>
  );
};

export default AiAssistantTab;
