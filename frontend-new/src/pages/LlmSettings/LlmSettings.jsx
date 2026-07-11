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

import React, { useState, useEffect, useCallback } from 'react';
import {
    Form,
    Input,
    Select,
    Slider,
    Switch,
    Button,
    Card,
    message,
    Space,
    Typography,
    Divider,
    Alert,
    Row,
    Col,
} from 'antd';
import {
    ApiOutlined,
    CheckCircleOutlined,
    CloseCircleOutlined,
    SaveOutlined,
    ThunderboltOutlined,
    RobotOutlined,
    CloudOutlined,
    GlobalOutlined,
    KeyOutlined,
    SafetyCertificateOutlined,
} from '@ant-design/icons';
import { remoteApi } from '../../api/remoteApi/remoteApi';
import './AiConfigPage.css';

const { Title, Text } = Typography;
const { Option } = Select;

// 各 LLM 提供商的默认可用模型列表（API 获取失败时的回退）
const FALLBACK_MODELS = {
    openai: ['gpt-4o', 'gpt-4-turbo', 'gpt-4', 'gpt-3.5-turbo'],
    azure: ['gpt-4o', 'gpt-4', 'gpt-3.5-turbo'],
    deepseek: ['deepseek-chat', 'deepseek-reasoner'],
    tongyi: ['qwen-max', 'qwen-plus', 'qwen-turbo'],
    ollama: ['llama3', 'mistral', 'gemma2', 'qwen2.5'],
    bedrock: ['anthropic.claude-3-sonnet', 'anthropic.claude-3-haiku', 'meta.llama3-70b'],
};

/**
 * 提供商配置定义
 * - key: 提供商标识
 * - label: 显示名称
 * - icon: 图标组件
 * - color: 主题色
 * - desc: 简短描述
 * - defaultBaseUrl: 默认API地址
 * - defaultModel: 默认模型
 * - requireApiKey: 是否需要API Key
 * - requireBaseUrl: 是否需要Base URL
 * - extraFields: 额外字段(Azure需要deploymentName等)
 */
const PROVIDERS = [
    {
        key: 'openai',
        label: 'OpenAI',
        icon: '🤖',
        color: '#10a37f',
        desc: 'GPT-4o / GPT-4 / GPT-3.5',
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
        desc: 'Azure 托管的 OpenAI 服务',
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
        desc: 'DeepSeek-V3 / DeepSeek-R1',
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
        desc: 'Qwen-Max / Qwen-Plus / Qwen-Turbo',
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
        desc: '本地部署的开源模型服务',
        defaultBaseUrl: 'http://localhost:11434/v1',
        defaultModel: 'llama3',
        requireApiKey: false,
        requireBaseUrl: true,
    },
    {
        key: 'bedrock',
        label: 'AWS Bedrock',
        icon: '/aws',
        color: '#ff9900',
        desc: 'Claude / Titan / Llama (AWS)',
        defaultBaseUrl: '',
        defaultModel: 'anthropic.claude-3-sonnet',
        requireApiKey: true,
        requireBaseUrl: false,
        extraFields: ['awsRegion'],
    },
];

function LlmSettings() {
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);
    const [testLoading, setTestLoading] = useState(false);
    const [testResult, setTestResult] = useState(null);
    const [enabled, setEnabled] = useState(false);
    const [selectedProvider, setSelectedProvider] = useState('openai');
    const [apiKeyMasked, setApiKeyMasked] = useState(true);
    const [savedApiKey, setSavedApiKey] = useState('');
    const [modelOptions, setModelOptions] = useState([]);
    const [modelsLoading, setModelsLoading] = useState(false);

    useEffect(() => {
        fetchConfig();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // 动态拉取模型列表
    useEffect(() => {
        let cancelled = false;
        const fetchModels = async () => {
            setModelsLoading(true);
            try {
                // 1. 检查 AI 是否已配置
                const config = await remoteApi.getLlmConfig();
                if (!config || !config.enabled) {
                    if (!cancelled) setModelOptions([]);
                    return;
                }

                // 2. 从 LLM 提供商 API 动态拉取模型列表
                let models = [];
                try {
                    const modelsResult = await remoteApi.getLlmModels();
                    if (modelsResult && modelsResult.status === 0 && modelsResult.data) {
                        models = modelsResult.data.map(m => m.id || m.name).filter(Boolean);
                    }
                } catch {
                    // API 获取失败，使用静态回退
                }

                // 3. 如果 API 返回空或失败，使用静态默认列表作为回退
                if (models.length === 0) {
                    const provider = config.provider || 'openai';
                    models = FALLBACK_MODELS[provider] || [config.model || ''].filter(Boolean);
                }

                if (!cancelled) {
                    setModelOptions(models.map(m => ({ value: m, label: m })));
                }
            } catch {
                if (!cancelled) setModelOptions([]);
            } finally {
                if (!cancelled) setModelsLoading(false);
            }
        };
        fetchModels();
        return () => { cancelled = true; };
    }, []);

    const fetchConfig = async () => {
        setLoading(true);
        try {
            const config = await remoteApi.getLlmConfig();
            if (config) {
                const provider = config.provider || 'openai';
                setSelectedProvider(provider);
                setEnabled(config.enabled || false);
                // API Key 掩码显示
                if (config.apiKey) {
                    setSavedApiKey(config.apiKey);
                    form.setFieldsValue({
                        apiKey: maskApiKey(config.apiKey),
                    });
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
        } catch (err) {
            console.error('加载AI配置失败:', err);
        } finally {
            setLoading(false);
        }
    };

    const maskApiKey = (key) => {
        if (!key || key.length < 8) return key ? '••••••••' : '';
        return key.slice(0, 4) + '••••••••' + key.slice(-4);
    };

    const currentProvider = PROVIDERS.find(p => p.key === selectedProvider) || PROVIDERS[0];

    const handleProviderChange = useCallback((value) => {
        setSelectedProvider(value);
        setTestResult(null);
        const provider = PROVIDERS.find(p => p.key === value);
        if (provider) {
            // 自动填充默认值
            form.setFieldsValue({
                apiBase: provider.defaultBaseUrl,
                model: provider.defaultModel,
            });
            // 如果不需要API Key，清空
            if (!provider.requireApiKey) {
                form.setFieldsValue({ apiKey: '' });
                setSavedApiKey('');
            }
        }
    }, [form]);

    const handleApiKeyFocus = () => {
        // 聚焦时如果显示掩码，清空让用户重新输入
        if (apiKeyMasked && savedApiKey) {
            form.setFieldsValue({ apiKey: '' });
            setApiKeyMasked(false);
        }
    };

    const handleApiKeyBlur = () => {
        const val = form.getFieldValue('apiKey');
        if (!val && savedApiKey) {
            // 用户没输入新值，恢复掩码
            form.setFieldsValue({ apiKey: maskApiKey(savedApiKey) });
            setApiKeyMasked(true);
        }
    };

    const handleTestConnection = async () => {
        setTestLoading(true);
        setTestResult(null);
        try {
            const values = await form.validateFields();
            // 构建测试配置，如果API Key是掩码则使用已保存的值
            const testConfig = {
                ...values,
                apiKey: (apiKeyMasked && savedApiKey) ? savedApiKey : (values.apiKey || ''),
                enabled: true,
            };
            const result = await remoteApi.testLlmConnection(testConfig);
            if (result && result.status === 0) {
                setTestResult({ success: true, msg: result.msg || '连接测试成功！API Key 有效。' });
                message.success('连接测试成功');
                // Auto-save config after successful connection test
                try {
                    await remoteApi.saveLlmConfig(testConfig);
                    if (testConfig.apiKey) {
                        setSavedApiKey(testConfig.apiKey);
                        form.setFieldsValue({ apiKey: maskApiKey(testConfig.apiKey) });
                        setApiKeyMasked(true);
                    }
                } catch (saveErr) {
                    console.warn('Auto-save after connection test failed:', saveErr);
                }
            } else {
                setTestResult({
                    success: false,
                    msg: (result && result.errMsg) || '连接测试失败，请检查配置。',
                });
                message.error('连接测试失败');
            }
        } catch (err) {
            setTestResult({
                success: false,
                msg: '连接测试失败: ' + (err.message || '未知错误'),
            });
            message.error('连接测试失败');
        } finally {
            setTestLoading(false);
        }
    };

    const handleSave = async () => {
        setLoading(true);
        try {
            const values = await form.validateFields();
            const config = {
                ...values,
                apiKey: (apiKeyMasked && savedApiKey) ? savedApiKey : (values.apiKey || ''),
                enabled,
            };
            const result = await remoteApi.saveLlmConfig(config);
            if (result && result.status === 0) {
                message.success('AI助手配置已保存');
                // 保存后刷新掩码
                if (config.apiKey) {
                    setSavedApiKey(config.apiKey);
                    form.setFieldsValue({ apiKey: maskApiKey(config.apiKey) });
                    setApiKeyMasked(true);
                }
            } else {
                message.error((result && result.errMsg) || '保存配置失败');
            }
        } catch (err) {
            console.error('保存AI配置失败:', err);
            if (err.errorFields) {
                message.error('请检查表单填写是否完整');
            } else {
                message.error('保存配置失败');
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="ai-config-page">
            <div className="ai-config-header">
                <div className="ai-config-header-left">
                    <RobotOutlined className="ai-config-header-icon" />
                    <div>
                        <Title level={4} style={{ margin: 0 }}>AI助手配置</Title>
                        <Text type="secondary" style={{ fontSize: 13 }}>
                            配置大语言模型提供商和参数，启用后可通过 ⌘K 呼出AI助手
                        </Text>
                    </div>
                </div>
                <div className="ai-config-header-right">
                    <Text type="secondary" style={{ fontSize: 13, marginRight: 8 }}>启用AI助手</Text>
                    <Switch
                        checked={enabled}
                        onChange={setEnabled}
                        checkedChildren="开"
                        unCheckedChildren="关"
                    />
                </div>
            </div>

            <Card className="ai-config-card" loading={loading}>
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
                    {/* 提供商选择 */}
                    <div className="ai-config-section-title">选择大模型提供商</div>
                    <div className="ai-config-provider-grid">
                        {PROVIDERS.map((p) => (
                            <div
                                key={p.key}
                                className={`ai-config-provider-card ${selectedProvider === p.key ? 'ai-config-provider-card-active' : ''}`}
                                onClick={() => {
                                    form.setFieldsValue({ provider: p.key });
                                    handleProviderChange(p.key);
                                }}
                                style={selectedProvider === p.key ? { borderColor: p.color } : {}}
                            >
                                <div className="ai-config-provider-icon" style={{ background: p.color + '15', color: p.color }}>
                                    {p.icon.length <= 2 ? p.icon : <CloudOutlined />}
                                </div>
                                <div className="ai-config-provider-info">
                                    <div className="ai-config-provider-name">{p.label}</div>
                                    <div className="ai-config-provider-desc">{p.desc}</div>
                                </div>
                                {selectedProvider === p.key && (
                                    <CheckCircleOutlined
                                        className="ai-config-provider-check"
                                        style={{ color: p.color }}
                                    />
                                )}
                            </div>
                        ))}
                    </div>
                    <Form.Item name="provider" hidden>
                        <Input />
                    </Form.Item>

                    <Divider style={{ margin: '20px 0 16px' }} />

                    {/* 连接配置 */}
                    <div className="ai-config-section-title">
                        <GlobalOutlined style={{ marginRight: 6 }} />
                        连接配置
                    </div>

                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item
                                name="apiKey"
                                label={
                                    <span>
                                        <KeyOutlined style={{ marginRight: 4 }} />
                                        API Key
                                        {currentProvider.requireApiKey && <Text type="danger"> *</Text>}
                                    </span>
                                }
                                rules={currentProvider.requireApiKey ? [{ required: true, message: '请输入API Key' }] : []}
                                extra="API Key 将加密存储在服务端"
                            >
                                <Input.Password
                                    placeholder={currentProvider.requireApiKey ? 'sk-xxxxxxxxxxxxxxxx' : '本地服务无需API Key'}
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
                                        <GlobalOutlined style={{ marginRight: 4 }} />
                                        API Base URL
                                        {currentProvider.requireBaseUrl && <Text type="danger"> *</Text>}
                                    </span>
                                }
                                rules={currentProvider.requireBaseUrl ? [{ required: true, message: '请输入API地址' }] : []}
                                extra={currentProvider.requireBaseUrl ? '必填：自定义服务地址' : '留空使用默认地址，可自定义'}
                            >
                                <Input placeholder={currentProvider.defaultBaseUrl || 'https://api.openai.com/v1'} />
                            </Form.Item>
                        </Col>
                    </Row>

                    {/* Azure 额外字段 */}
                    {selectedProvider === 'azure' && (
                        <Row gutter={16}>
                            <Col span={12}>
                                <Form.Item
                                    name="deploymentName"
                                    label="部署名称 (Deployment Name)"
                                    rules={[{ required: true, message: '请输入Azure部署名称' }]}
                                >
                                    <Input placeholder="my-gpt4-deployment" />
                                </Form.Item>
                            </Col>
                            <Col span={12}>
                                <Form.Item
                                    name="apiVersion"
                                    label="API 版本"
                                    rules={[{ required: true, message: '请输入API版本' }]}
                                >
                                    <Select placeholder="选择API版本">
                                        <Option value="2024-02-15-preview">2024-02-15-preview</Option>
                                        <Option value="2024-08-01-preview">2024-08-01-preview</Option>
                                        <Option value="2025-01-01-preview">2025-01-01-preview</Option>
                                    </Select>
                                </Form.Item>
                            </Col>
                        </Row>
                    )}

                    {/* AWS Bedrock 额外字段 */}
                    {selectedProvider === 'bedrock' && (
                        <Form.Item
                            name="awsRegion"
                            label="AWS 区域"
                            rules={[{ required: true, message: '请选择AWS区域' }]}
                        >
                            <Select placeholder="选择AWS区域">
                                <Option value="us-east-1">us-east-1 (弗吉尼亚)</Option>
                                <Option value="us-west-2">us-west-2 (俄勒冈)</Option>
                                <Option value="eu-west-1">eu-west-1 (爱尔兰)</Option>
                                <Option value="ap-northeast-1">ap-northeast-1 (东京)</Option>
                                <Option value="ap-southeast-1">ap-southeast-1 (新加坡)</Option>
                            </Select>
                        </Form.Item>
                    )}

                    <Divider style={{ margin: '20px 0 16px' }} />

                    {/* 模型参数 */}
                    <div className="ai-config-section-title">
                        <ThunderboltOutlined style={{ marginRight: 6 }} />
                        模型参数
                    </div>

                    <Form.Item
                        name="model"
                        label="模型名称"
                        rules={[{ required: true, message: '请选择或输入模型名称' }]}
                        extra="实时从 LLM 提供商获取可用模型列表，也可直接输入自定义模型名"
                    >
                        <Select
                            showSearch
                            loading={modelsLoading}
                            placeholder={modelsLoading ? '正在获取模型列表...' : (currentProvider.defaultModel)}
                            filterOption={(input, option) =>
                                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
                            }
                            notFoundContent={modelsLoading ? '加载中...' : '未获取到模型，请确认已保存配置并测试连接'}
                            options={modelOptions}
                        />
                    </Form.Item>

                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item
                                noStyle
                                shouldUpdate={(prev, cur) => prev.maxTokens !== cur.maxTokens}
                            >
                                {({ getFieldValue }) => (
                                    <Form.Item
                                        name="maxTokens"
                                        label={
                                            <span>
                                                最大 Token 数
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
                                                256: '256',
                                                4096: '4K',
                                                8192: '8K',
                                                32768: '32K',
                                                128000: '128K',
                                            }}
                                        />
                                    </Form.Item>
                                )}
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                noStyle
                                shouldUpdate={(prev, cur) => prev.temperature !== cur.temperature}
                            >
                                {({ getFieldValue }) => (
                                    <Form.Item
                                        name="temperature"
                                        label={
                                            <span>
                                                温度 (Temperature)
                                                <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                                                    {getFieldValue('temperature') ?? 0.7}
                                                </Text>
                                            </span>
                                        }
                                        extra="值越低输出越确定，越高越随机"
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

                    {/* 操作按钮 */}
                    <div className="ai-config-actions">
                        <Space size="middle">
                            <Button
                                type="primary"
                                icon={<SaveOutlined />}
                                onClick={handleSave}
                                loading={loading}
                                size="large"
                            >
                                保存配置
                            </Button>
                            <Button
                                icon={<ApiOutlined />}
                                onClick={handleTestConnection}
                                loading={testLoading}
                                size="large"
                            >
                                {testLoading ? '测试中...' : '连接测试'}
                            </Button>
                        </Space>
                        <div className="ai-config-security-note">
                            <SafetyCertificateOutlined style={{ marginRight: 4 }} />
                            <Text type="secondary" style={{ fontSize: 12 }}>
                                API Key 将加密存储，连接测试仅验证Key有效性
                            </Text>
                        </div>
                    </div>

                    {/* 测试结果 */}
                    {testResult && (
                        <Alert
                            className="ai-config-test-result"
                            type={testResult.success ? 'success' : 'error'}
                            showIcon
                            icon={testResult.success ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
                            message={testResult.msg}
                            closable
                            onClose={() => setTestResult(null)}
                        />
                    )}
                </Form>
            </Card>
        </div>
    );
}

export default LlmSettings;