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

import React, { useState, useEffect } from 'react';
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
} from 'antd';
import {
    ApiOutlined,
    CheckCircleOutlined,
    CloseCircleOutlined,
    SaveOutlined,
    ThunderboltOutlined,
} from '@ant-design/icons';
import { remoteApi } from '../../api/remoteApi/remoteApi';

const { Title, Text } = Typography;
const { Option } = Select;

const PROVIDERS = [
    { value: 'openai', label: 'OpenAI' },
    { value: 'azure', label: 'Azure OpenAI' },
    { value: 'deepseek', label: 'DeepSeek' },
    { value: 'tongyi', label: 'Tongyi' },
    { value: 'ollama', label: 'Ollama' },
];

function LlmSettings() {
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);
    const [testLoading, setTestLoading] = useState(false);
    const [testResult, setTestResult] = useState(null);
    const [enabled, setEnabled] = useState(false);

    useEffect(() => {
        fetchConfig();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const fetchConfig = async () => {
        setLoading(true);
        try {
            const config = await remoteApi.getLlmConfig();
            if (config) {
                form.setFieldsValue({
                    provider: config.provider || 'openai',
                    apiKey: config.apiKey || '',
                    apiBaseUrl: config.apiBaseUrl || '',
                    model: config.model || 'gpt-4',
                    maxTokens: config.maxTokens || 4096,
                    temperature: config.temperature || 0,
                });
                setEnabled(config.enabled || false);
            }
        } catch (err) {
            console.error('Failed to load LLM config:', err);
        } finally {
            setLoading(false);
        }
    };

    const handleTestConnection = async () => {
        setTestLoading(true);
        setTestResult(null);
        try {
            const values = await form.validateFields();
            const result = await remoteApi.testLlmConnection(values);
            if (result && result.status === 0) {
                setTestResult({ success: true, msg: result.msg || 'Connection successful!' });
            } else {
                setTestResult({ success: false, msg: (result && result.errMsg) || 'Connection failed' });
            }
        } catch (err) {
            setTestResult({ success: false, msg: 'Failed to test connection: ' + (err.message || 'Unknown error') });
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
                enabled,
            };
            const result = await remoteApi.saveLlmConfig(config);
            if (result && result.status === 0) {
                message.success('LLM configuration saved successfully');
            } else {
                message.error((result && result.errMsg) || 'Failed to save configuration');
            }
        } catch (err) {
            console.error('Failed to save LLM config:', err);
            message.error('Failed to save configuration');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ maxWidth: '800px', margin: '0 auto', padding: '24px' }}>
            <Card
                title={
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <ThunderboltOutlined style={{ fontSize: '20px', color: '#1677ff' }} />
                        <Title level={4} style={{ margin: 0 }}>LLM Settings</Title>
                    </div>
                }
                loading={loading}
            >
                <Form
                    form={form}
                    layout="vertical"
                    initialValues={{
                        provider: 'openai',
                        model: 'gpt-4',
                        maxTokens: 4096,
                        temperature: 0,
                    }}
                >
                    <Form.Item
                        name="provider"
                        label="Provider"
                        rules={[{ required: true, message: 'Please select a provider' }]}
                    >
                        <Select placeholder="Select LLM provider">
                            {PROVIDERS.map((p) => (
                                <Option key={p.value} value={p.value}>{p.label}</Option>
                            ))}
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="apiKey"
                        label="API Key"
                        rules={[{ required: true, message: 'Please enter your API key' }]}
                    >
                        <Input.Password placeholder="Enter your API key" />
                    </Form.Item>

                    <Form.Item
                        name="apiBaseUrl"
                        label="API Base URL"
                        extra="Leave empty for default provider endpoint. Required for custom providers like Ollama."
                    >
                        <Input placeholder="https://api.openai.com/v1" />
                    </Form.Item>

                    <Form.Item
                        name="model"
                        label="Model"
                        rules={[{ required: true, message: 'Please enter a model name' }]}
                    >
                        <Input placeholder="gpt-4" />
                    </Form.Item>

                    <Form.Item
                        name="maxTokens"
                        label={
                            <span>Max Tokens: <Text type="secondary">{form.getFieldValue('maxTokens')}</Text></span>
                        }
                    >
                        <Slider
                            min={256}
                            max={16384}
                            step={256}
                            marks={{ 256: '256', 4096: '4096', 8192: '8192', 16384: '16384' }}
                        />
                    </Form.Item>

                    <Form.Item
                        name="temperature"
                        label={
                            <span>Temperature: <Text type="secondary">{form.getFieldValue('temperature')}</Text></span>
                        }
                    >
                        <Slider
                            min={0}
                            max={2}
                            step={0.1}
                            marks={{ 0: '0', 0.5: '0.5', 1: '1', 1.5: '1.5', 2: '2' }}
                        />
                    </Form.Item>

                    <Divider />

                    <Form.Item label="Enable LLM">
                        <Switch
                            checked={enabled}
                            onChange={setEnabled}
                            checkedChildren="On"
                            unCheckedChildren="Off"
                        />
                    </Form.Item>

                    <Form.Item>
                        <Space>
                            <Button
                                type="primary"
                                icon={<SaveOutlined />}
                                onClick={handleSave}
                                loading={loading}
                            >
                                Save
                            </Button>
                            <Button
                                icon={<ApiOutlined />}
                                onClick={handleTestConnection}
                                loading={testLoading}
                            >
                                Test Connection
                            </Button>
                        </Space>
                    </Form.Item>

                    {testResult && (
                        <Card
                            size="small"
                            style={{
                                backgroundColor: testResult.success ? '#f6ffed' : '#fff2f0',
                                borderColor: testResult.success ? '#b7eb8f' : '#ffccc7',
                            }}
                        >
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                {testResult.success ? (
                                    <CheckCircleOutlined style={{ color: '#52c41a', fontSize: '16px' }} />
                                ) : (
                                    <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: '16px' }} />
                                )}
                                <Text type={testResult.success ? 'success' : 'danger'}>
                                    {testResult.msg}
                                </Text>
                            </div>
                        </Card>
                    )}
                </Form>
            </Card>
        </div>
    );
}

export default LlmSettings;
