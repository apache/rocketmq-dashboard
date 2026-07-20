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

import { useState, useRef, useCallback } from 'react';
import {
  Form,
  Input,
  Select,
  Slider,
  Switch,
  Button,
  Card,
  Space,
  Typography,
  Divider,
  Alert,
  Row,
  Col,
  App,
} from 'antd';
import {
  FloppyDisk,
  Lightning,
  Robot,
  Cloud,
  Globe,
  Key,
  ShieldCheck,
  CheckCircle,
  XCircle,
} from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import {
  getLlmConfig,
  saveLlmConfig,
  testLlmConnection,
  getLlmModels,
  type LlmConfig,
} from '../../api/llm';

const { Text } = Typography;

// Fallback models when API fetch fails
const FALLBACK_MODELS: Record<string, string[]> = {
  openai: ['gpt-4o', 'gpt-4-turbo', 'gpt-4', 'gpt-3.5-turbo'],
  azure: ['gpt-4o', 'gpt-4', 'gpt-3.5-turbo'],
  deepseek: ['deepseek-chat', 'deepseek-reasoner'],
  tongyi: ['qwen-max', 'qwen-plus', 'qwen-turbo'],
  ollama: ['llama3', 'mistral', 'gemma2', 'qwen2.5'],
  bedrock: ['anthropic.claude-3-sonnet', 'anthropic.claude-3-haiku', 'meta.llama3-70b'],
};

interface ProviderDef {
  key: string;
  label: string;
  icon: string;
  color: string;
  descKey: string;
  defaultBaseUrl: string;
  defaultModel: string;
  requireApiKey: boolean;
  requireBaseUrl: boolean;
  extraFields?: string[];
}

const PROVIDERS: ProviderDef[] = [
  {
    key: 'openai',
    label: 'OpenAI',
    icon: '🤖',
    color: '#10a37f',
    descKey: 'llm.providerOpenaiDesc',
    defaultBaseUrl: 'https://api.openai.com/v1',
    defaultModel: 'gpt-4o',
    requireApiKey: true,
    requireBaseUrl: false,
  },
  {
    key: 'azure',
    label: 'Azure OpenAI',
    icon: '☁️',
    color: '#0078d4',
    descKey: 'llm.providerAzureDesc',
    defaultBaseUrl: '',
    defaultModel: 'gpt-4o',
    requireApiKey: true,
    requireBaseUrl: true,
    extraFields: ['deploymentName', 'apiVersion'],
  },
  {
    key: 'deepseek',
    label: 'DeepSeek',
    icon: '🔍',
    color: '#4d6bfe',
    descKey: 'llm.providerDeepseekDesc',
    defaultBaseUrl: 'https://api.deepseek.com/v1',
    defaultModel: 'deepseek-chat',
    requireApiKey: true,
    requireBaseUrl: false,
  },
  {
    key: 'tongyi',
    label: '通义千问',
    icon: '🧠',
    color: '#6236ff',
    descKey: 'llm.providerTongyiDesc',
    defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    defaultModel: 'qwen-max',
    requireApiKey: true,
    requireBaseUrl: false,
  },
  {
    key: 'ollama',
    label: 'Ollama',
    icon: '🦙',
    color: '#6e40c9',
    descKey: 'llm.providerOllamaDesc',
    defaultBaseUrl: 'http://localhost:11434/v1',
    defaultModel: 'llama3',
    requireApiKey: false,
    requireBaseUrl: true,
  },
  {
    key: 'bedrock',
    label: 'AWS Bedrock',
    icon: '☁️',
    color: '#ff9900',
    descKey: 'llm.providerBedrockDesc',
    defaultBaseUrl: '',
    defaultModel: 'anthropic.claude-3-sonnet',
    requireApiKey: true,
    requireBaseUrl: false,
    extraFields: ['awsRegion'],
  },
];

interface TestResult {
  success: boolean;
  msg: string;
}

const LlmSettingsPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();

  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [testLoading, setTestLoading] = useState(false);
  const [testResult, setTestResult] = useState<TestResult | null>(null);
  const [enabled, setEnabled] = useState(false);
  const [selectedProvider, setSelectedProvider] = useState('openai');
  const [apiKeyMasked, setApiKeyMasked] = useState(true);
  const [savedApiKey, setSavedApiKey] = useState('');
  const [modelOptions, setModelOptions] = useState<{ value: string; label: string }[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);

  const initialized = useRef<boolean | null>(null);
  if (initialized.current == null) {
    initialized.current = true;
    fetchConfig();
    fetchModels();
  }

  function fetchConfig() {
    setLoading(true);
    getLlmConfig()
      .then((config) => {
        if (config) {
          const provider = config.provider || 'openai';
          setSelectedProvider(provider);
          setEnabled(config.enabled || false);
          if (config.apiKey) {
            setSavedApiKey(config.apiKey);
            form.setFieldsValue({ apiKey: maskApiKey(config.apiKey) });
          }
          form.setFieldsValue({
            provider,
            apiBase: config.apiBase || '',
            model: config.model || '',
            maxTokens: config.maxTokens || 4096,
            temperature: config.temperature !== undefined ? config.temperature : 0.7,
            deploymentName: config.deploymentName || '',
            apiVersion: config.apiVersion || '2024-02-15-preview',
            awsRegion: config.awsRegion || 'us-east-1',
          });
        }
      })
      .catch(() => {
        message.error(t('llm.loadFailed'));
      })
      .finally(() => {
        setLoading(false);
      });
  }

  function fetchModels() {
    setModelsLoading(true);
    getLlmConfig()
      .then((config) => {
        if (!config || !config.enabled) {
          setModelOptions([]);
          return;
        }
        getLlmModels()
          .then((result) => {
            let models: string[] = [];
            if (result && result.status === 0 && result.data) {
              models = result.data.map((m) => m.id || m.name || '').filter(Boolean);
            }
            if (models.length === 0) {
              const provider = config.provider || 'openai';
              models = FALLBACK_MODELS[provider] || [config.model || ''].filter(Boolean);
            }
            setModelOptions(models.map((m) => ({ value: m, label: m })));
          })
          .catch(() => {
            const provider = config.provider || 'openai';
            const fallback = FALLBACK_MODELS[provider] || [config.model || ''].filter(Boolean);
            setModelOptions(fallback.map((m) => ({ value: m, label: m })));
          });
      })
      .catch(() => {
        setModelOptions([]);
      })
      .finally(() => {
        setModelsLoading(false);
      });
  }

  const maskApiKey = (key: string) => {
    if (!key || key.length < 8) return key ? '••••••••' : '';
    return key.slice(0, 4) + '••••••••' + key.slice(-4);
  };

  const currentProvider = PROVIDERS.find((p) => p.key === selectedProvider) || PROVIDERS[0];

  const handleProviderChange = useCallback(
    (value: string) => {
      setSelectedProvider(value);
      setTestResult(null);
      const provider = PROVIDERS.find((p) => p.key === value);
      if (provider) {
        form.setFieldsValue({
          apiBase: provider.defaultBaseUrl,
          model: provider.defaultModel,
        });
        if (!provider.requireApiKey) {
          form.setFieldsValue({ apiKey: '' });
          setSavedApiKey('');
        }
      }
    },
    [form],
  );

  const handleApiKeyFocus = () => {
    if (apiKeyMasked && savedApiKey) {
      form.setFieldsValue({ apiKey: '' });
      setApiKeyMasked(false);
    }
  };

  const handleApiKeyBlur = () => {
    const val = form.getFieldValue('apiKey');
    if (!val && savedApiKey) {
      form.setFieldsValue({ apiKey: maskApiKey(savedApiKey) });
      setApiKeyMasked(true);
    }
  };

  const handleTestConnection = () => {
    setTestLoading(true);
    setTestResult(null);
    form
      .validateFields()
      .then((values) => {
        const testConfig: LlmConfig = {
          ...values,
          apiKey: apiKeyMasked && savedApiKey ? savedApiKey : values.apiKey || '',
          enabled: true,
        };
        testLlmConnection(testConfig)
          .then((result) => {
            if (result && result.status === 0) {
              setTestResult({ success: true, msg: result.msg || t('llm.testSuccessMsg') });
              message.success(t('llm.testSuccess'));
              // Auto-save after successful test
              saveLlmConfig(testConfig)
                .then(() => {
                  if (testConfig.apiKey) {
                    setSavedApiKey(testConfig.apiKey);
                    form.setFieldsValue({ apiKey: maskApiKey(testConfig.apiKey) });
                    setApiKeyMasked(true);
                  }
                })
                .catch(() => {
                  // auto-save failure is non-critical
                });
            } else {
              setTestResult({
                success: false,
                msg: (result && result.errMsg) || t('llm.testFailedMsg'),
              });
              message.error(t('llm.testFailed'));
            }
          })
          .catch((err) => {
            setTestResult({
              success: false,
              msg: t('llm.testError') + (err.message || ''),
            });
            message.error(t('llm.testFailed'));
          })
          .finally(() => {
            setTestLoading(false);
          });
      })
      .catch(() => {
        setTestLoading(false);
      });
  };

  const handleSave = () => {
    setLoading(true);
    form
      .validateFields()
      .then((values) => {
        const config: LlmConfig = {
          ...values,
          apiKey: apiKeyMasked && savedApiKey ? savedApiKey : values.apiKey || '',
          enabled,
        };
        saveLlmConfig(config)
          .then((result) => {
            if (result && result.status === 0) {
              message.success(t('llm.saveSuccess'));
              if (config.apiKey) {
                setSavedApiKey(config.apiKey);
                form.setFieldsValue({ apiKey: maskApiKey(config.apiKey) });
                setApiKeyMasked(true);
              }
            } else {
              message.error((result && result.errMsg) || t('llm.saveFailed'));
            }
          })
          .catch((err) => {
            if (err.errorFields) {
              message.error(t('llm.formIncomplete'));
            } else {
              message.error(t('llm.saveFailed'));
            }
          })
          .finally(() => {
            setLoading(false);
          });
      })
      .catch(() => {
        message.error(t('llm.formIncomplete'));
        setLoading(false);
      });
  };

  // ─── Provider Grid ──────────────────────────────────────────

  const renderProviderGrid = () => (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: 12,
        marginBottom: 4,
      }}
    >
      {PROVIDERS.map((p) => (
        <div
          key={p.key}
          onClick={() => {
            form.setFieldsValue({ provider: p.key });
            handleProviderChange(p.key);
          }}
          style={{
            position: 'relative',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            padding: '14px 16px',
            border: `2px solid ${selectedProvider === p.key ? p.color : '#f0f0f0'}`,
            borderRadius: 10,
            cursor: 'pointer',
            background: selectedProvider === p.key ? '#fafbff' : '#ffffff',
            boxShadow: selectedProvider === p.key ? '0 2px 8px rgba(22, 119, 255, 0.1)' : 'none',
            transition: 'all 0.2s ease',
            userSelect: 'none',
          }}
        >
          <div
            style={{
              flexShrink: 0,
              width: 40,
              height: 40,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 8,
              fontSize: 20,
              fontWeight: 600,
              background: p.color + '15',
              color: p.color,
            }}
          >
            {p.icon.length <= 2 ? p.icon : <Cloud size={20} />}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 14, fontWeight: 600, lineHeight: 1.4 }}>{p.label}</div>
            <div
              style={{
                fontSize: 11,
                color: 'rgba(0,0,0,0.45)',
                lineHeight: 1.4,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {t(p.descKey)}
            </div>
          </div>
          {selectedProvider === p.key && (
            <CheckCircle
              size={16}
              weight="fill"
              style={{ position: 'absolute', top: 8, right: 8, color: p.color }}
            />
          )}
        </div>
      ))}
    </div>
  );

  // ─── Render ──────────────────────────────────────────────────

  return (
    <div style={{ maxWidth: 960, margin: '0 auto', padding: '0 24px 40px' }}>
      <PageHeader
        title={t('llm.title')}
        icon={Robot}
        extra={
          <Space>
            <Text type="secondary" style={{ fontSize: 13, marginRight: 8 }}>
              {t('llm.enable')}
            </Text>
            <Switch
              checked={enabled}
              onChange={setEnabled}
              checkedChildren={t('llm.on')}
              unCheckedChildren={t('llm.off')}
            />
          </Space>
        }
      />

      <Card
        loading={loading}
        style={{ borderRadius: 12, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}
        styles={{ body: { padding: '24px 28px' } }}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            provider: 'openai',
            model: 'gpt-4o',
            apiBase: 'https://api.openai.com/v1',
            maxTokens: 4096,
            temperature: 0.7,
            apiVersion: '2024-02-15-preview',
            awsRegion: 'us-east-1',
          }}
        >
          {/* Provider Selection */}
          <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 16 }}>
            {t('llm.selectProvider')}
          </div>
          {renderProviderGrid()}
          <Form.Item name="provider" hidden>
            <Input />
          </Form.Item>

          <Divider style={{ margin: '20px 0 16px' }} />

          {/* Connection Config */}
          <div
            style={{
              fontSize: 15,
              fontWeight: 600,
              marginBottom: 16,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <Globe size={16} style={{ marginRight: 6 }} />
            {t('llm.connectionConfig')}
          </div>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="apiKey"
                label={
                  <span>
                    <Key size={14} style={{ marginRight: 4, verticalAlign: 'middle' }} />
                    {t('llm.apiKey')}
                    {currentProvider.requireApiKey && <Text type="danger"> *</Text>}
                  </span>
                }
                rules={
                  currentProvider.requireApiKey
                    ? [{ required: true, message: t('llm.apiKeyRequired') }]
                    : []
                }
                extra={t('llm.apiKeyEncrypted')}
              >
                <Input.Password
                  placeholder={
                    currentProvider.requireApiKey
                      ? t('llm.apiKeyPlaceholder')
                      : t('llm.apiKeyNoRequired')
                  }
                  onFocus={handleApiKeyFocus}
                  onBlur={handleApiKeyBlur}
                  visibilityToggle
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="apiBase"
                label={
                  <span>
                    <Globe size={14} style={{ marginRight: 4, verticalAlign: 'middle' }} />
                    {t('llm.apiBase')}
                    {currentProvider.requireBaseUrl && <Text type="danger"> *</Text>}
                  </span>
                }
                rules={
                  currentProvider.requireBaseUrl
                    ? [{ required: true, message: t('llm.apiBaseRequired') }]
                    : []
                }
                extra={
                  currentProvider.requireBaseUrl
                    ? t('llm.apiBaseRequiredHint')
                    : t('llm.apiBaseCustom')
                }
              >
                <Input
                  placeholder={currentProvider.defaultBaseUrl || 'https://api.openai.com/v1'}
                />
              </Form.Item>
            </Col>
          </Row>

          {/* Azure extra fields */}
          {selectedProvider === 'azure' && (
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="deploymentName"
                  label={t('llm.deploymentName')}
                  rules={[{ required: true, message: t('llm.deploymentNameRequired') }]}
                >
                  <Input placeholder="my-gpt4-deployment" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="apiVersion"
                  label={t('llm.apiVersion')}
                  rules={[{ required: true, message: t('llm.apiVersionRequired') }]}
                >
                  <Select placeholder={t('llm.apiVersion')}>
                    <Select.Option value="2024-02-15-preview">2024-02-15-preview</Select.Option>
                    <Select.Option value="2024-08-01-preview">2024-08-01-preview</Select.Option>
                    <Select.Option value="2025-01-01-preview">2025-01-01-preview</Select.Option>
                  </Select>
                </Form.Item>
              </Col>
            </Row>
          )}

          {/* AWS Bedrock extra fields */}
          {selectedProvider === 'bedrock' && (
            <Form.Item
              name="awsRegion"
              label={t('llm.awsRegion')}
              rules={[{ required: true, message: t('llm.awsRegionRequired') }]}
            >
              <Select placeholder={t('llm.awsRegion')}>
                <Select.Option value="us-east-1">us-east-1</Select.Option>
                <Select.Option value="us-west-2">us-west-2</Select.Option>
                <Select.Option value="eu-west-1">eu-west-1</Select.Option>
                <Select.Option value="ap-northeast-1">ap-northeast-1</Select.Option>
                <Select.Option value="ap-southeast-1">ap-southeast-1</Select.Option>
              </Select>
            </Form.Item>
          )}

          <Divider style={{ margin: '20px 0 16px' }} />

          {/* Model Parameters */}
          <div
            style={{
              fontSize: 15,
              fontWeight: 600,
              marginBottom: 16,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <Lightning size={16} style={{ marginRight: 6 }} />
            {t('llm.modelParams')}
          </div>

          <Form.Item
            name="model"
            label={t('llm.model')}
            rules={[{ required: true, message: t('llm.modelRequired') }]}
            extra={t('llm.modelExtra')}
          >
            <Select
              showSearch
              loading={modelsLoading}
              placeholder={modelsLoading ? t('llm.modelsLoading') : currentProvider.defaultModel}
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              notFoundContent={modelsLoading ? t('common.loading') : t('llm.modelsNotFound')}
              options={modelOptions}
            />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item noStyle shouldUpdate={(prev, cur) => prev.maxTokens !== cur.maxTokens}>
                {({ getFieldValue }) => (
                  <Form.Item
                    name="maxTokens"
                    label={
                      <span>
                        {t('llm.maxTokens')}
                        <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                          {getFieldValue('maxTokens') ?? 4096}
                        </Text>
                      </span>
                    }
                  >
                    <Slider
                      min={256}
                      max={128000}
                      step={256}
                      marks={{
                        2048: { label: '2K', style: { fontSize: 11 } },
                        8192: { label: '8K', style: { fontSize: 11 } },
                        32768: { label: '32K', style: { fontSize: 11 } },
                        128000: { label: '128K', style: { fontSize: 11 } },
                      }}
                    />
                  </Form.Item>
                )}
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item noStyle shouldUpdate={(prev, cur) => prev.temperature !== cur.temperature}>
                {({ getFieldValue }) => (
                  <Form.Item
                    name="temperature"
                    label={
                      <span>
                        {t('llm.temperature')}
                        <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                          {getFieldValue('temperature') ?? 0.7}
                        </Text>
                      </span>
                    }
                    extra={t('llm.temperatureExtra')}
                  >
                    <Slider
                      min={0}
                      max={2}
                      step={0.1}
                      marks={{
                        0: { label: '0', style: { fontSize: 11 } },
                        0.7: { label: '0.7', style: { fontSize: 11 } },
                        1: { label: '1', style: { fontSize: 11 } },
                        2: { label: '2', style: { fontSize: 11 } },
                      }}
                    />
                  </Form.Item>
                )}
              </Form.Item>
            </Col>
          </Row>

          <Divider style={{ margin: '20px 0 16px' }} />

          {/* Actions */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: 12,
            }}
          >
            <Space size="middle">
              <Button
                type="primary"
                icon={<FloppyDisk size={14} />}
                onClick={handleSave}
                loading={loading}
                size="large"
              >
                {t('llm.saveConfig')}
              </Button>
              <Button
                icon={<Lightning size={14} />}
                onClick={handleTestConnection}
                loading={testLoading}
                size="large"
              >
                {testLoading ? t('llm.testing') : t('llm.testConnection')}
              </Button>
            </Space>
            <div style={{ display: 'flex', alignItems: 'center' }}>
              <ShieldCheck size={14} style={{ marginRight: 4 }} />
              <Text type="secondary" style={{ fontSize: 12 }}>
                {t('llm.securityNote')}
              </Text>
            </div>
          </div>

          {/* Test Result */}
          {testResult && (
            <Alert
              style={{ marginTop: 16, borderRadius: 8 }}
              type={testResult.success ? 'success' : 'error'}
              showIcon
              icon={
                testResult.success ? (
                  <CheckCircle size={16} weight="fill" />
                ) : (
                  <XCircle size={16} weight="fill" />
                )
              }
              message={testResult.msg}
              closable
              onClose={() => setTestResult(null)}
            />
          )}
        </Form>
      </Card>
    </div>
  );
};

export default LlmSettingsPage;
