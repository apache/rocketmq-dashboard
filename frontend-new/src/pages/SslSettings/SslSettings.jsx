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
    Card,
    Form,
    Input,
    Button,
    Switch,
    Select,
    Upload,
    message,
    Descriptions,
    Tag,
    Space,
    Alert,
    Divider,
    Row,
    Col,
} from 'antd';
import {
    UploadOutlined,
    SafetyCertificateOutlined,
    LockOutlined,
    CheckCircleOutlined,
    CloseCircleOutlined,
    KeyOutlined,
} from '@ant-design/icons';
import { useLanguage } from '../../i18n/LanguageContext';
import './SslSettings.css';

const { Option } = Select;
const { TextArea } = Input;

const SslSettings = () => {
    const { t } = useLanguage();
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);
    const [sslEnabled, setSslEnabled] = useState(false);
    const [sslConfig, setSslConfig] = useState({
        enabled: false,
        protocol: 'TLSv1.3',
        keyStoreType: 'JKS',
        keyStorePath: '',
        keyStorePassword: '',
        trustStoreType: 'JKS',
        trustStorePath: '',
        trustStorePassword: '',
        clientAuth: 'none',
        certificateExpiry: null,
        certificateIssuer: null,
    });

    useEffect(() => {
        loadSslConfig();
    }, []);

    const loadSslConfig = () => {
        setLoading(true);
        // Simulate API call
        setTimeout(() => {
            setSslConfig({
                enabled: false,
                protocol: 'TLSv1.3',
                keyStoreType: 'JKS',
                keyStorePath: '',
                keyStorePassword: '',
                trustStoreType: 'JKS',
                trustStorePath: '',
                trustStorePassword: '',
                clientAuth: 'none',
                certificateExpiry: '2025-12-31',
                certificateIssuer: 'Let\'s Encrypt',
            });
            setSslEnabled(false);
            setLoading(false);
        }, 500);
    };

    const handleSave = (values) => {
        setLoading(true);
        // Simulate API call
        setTimeout(() => {
            setLoading(false);
            message.success(t.SSL_SAVE_SUCCESS || 'SSL configuration saved successfully');
            setSslConfig({ ...sslConfig, ...values });
        }, 1000);
    };

    const handleToggleSsl = (checked) => {
        setSslEnabled(checked);
        form.setFieldsValue({ enabled: checked });
    };

    const uploadProps = {
        name: 'certificate',
        multiple: false,
        beforeUpload: (file) => {
            const isCert = file.name.endsWith('.pem') || file.name.endsWith('.crt') || 
                          file.name.endsWith('.jks') || file.name.endsWith('.p12');
            if (!isCert) {
                message.error(t.INVALID_CERT_FORMAT || 'Only certificate files are allowed!');
                return false;
            }
            return false; // Prevent auto upload
        },
        onChange: (info) => {
            if (info.file.status === 'removed') {
                message.info(t.CERT_REMOVED || 'Certificate file removed');
            }
        },
    };

    return (
        <div className="ssl-settings-container">
            <Card
                title={
                    <Space>
                        <SafetyCertificateOutlined />
                        <span>{t.SSL_SETTINGS || 'SSL/TLS Settings'}</span>
                    </Space>
                }
                className="ssl-card"
            >
                <Alert
                    message={t.SSL_INFO || 'SSL/TLS Configuration'}
                    description={t.SSL_INFO_DESC || 'Configure SSL/TLS settings for secure communication. Changes will require server restart.'}
                    type="info"
                    showIcon
                    style={{ marginBottom: 24 }}
                />

                <Form
                    form={form}
                    layout="vertical"
                    onFinish={handleSave}
                    initialValues={sslConfig}
                >
                    <Form.Item
                        name="enabled"
                        label={t.SSL_ENABLED || 'Enable SSL/TLS'}
                        valuePropName="checked"
                    >
                        <Switch
                            checked={sslEnabled}
                            onChange={handleToggleSsl}
                            checkedChildren={<CheckCircleOutlined />}
                            unCheckedChildren={<CloseCircleOutlined />}
                        />
                    </Form.Item>

                    {sslEnabled && (
                        <>
                            <Divider />
                            
                            <Row gutter={16}>
                                <Col span={12}>
                                    <Form.Item
                                        name="protocol"
                                        label={t.SSL_PROTOCOL || 'SSL Protocol'}
                                        rules={[{ required: true, message: t.SELECT_PROTOCOL || 'Please select protocol' }]}
                                    >
                                        <Select>
                                            <Option value="TLSv1.3">TLS 1.3</Option>
                                            <Option value="TLSv1.2">TLS 1.2</Option>
                                            <Option value="TLSv1.1">TLS 1.1 (Deprecated)</Option>
                                            <Option value="TLSv1">TLS 1.0 (Deprecated)</Option>
                                        </Select>
                                    </Form.Item>
                                </Col>
                                <Col span={12}>
                                    <Form.Item
                                        name="clientAuth"
                                        label={t.CLIENT_AUTH || 'Client Authentication'}
                                        rules={[{ required: true }]}
                                    >
                                        <Select>
                                            <Option value="none">{t.NONE || 'None'}</Option>
                                            <Option value="want">{t.WANT || 'Want'}</Option>
                                            <Option value="need">{t.NEED || 'Need'}</Option>
                                        </Select>
                                    </Form.Item>
                                </Col>
                            </Row>

                            <Divider orientation="left">{t.KEYSTORE_CONFIG || 'KeyStore Configuration'}</Divider>

                            <Row gutter={16}>
                                <Col span={12}>
                                    <Form.Item
                                        name="keyStoreType"
                                        label={t.KEYSTORE_TYPE || 'KeyStore Type'}
                                        rules={[{ required: true }]}
                                    >
                                        <Select>
                                            <Option value="JKS">JKS</Option>
                                            <Option value="PKCS12">PKCS12</Option>
                                        </Select>
                                    </Form.Item>
                                </Col>
                                <Col span={12}>
                                    <Form.Item
                                        name="keyStorePath"
                                        label={t.KEYSTORE_PATH || 'KeyStore Path'}
                                        rules={[{ required: true, message: t.INPUT_KEYSTORE_PATH || 'Please input KeyStore path' }]}
                                    >
                                        <Input placeholder="/path/to/keystore.jks" />
                                    </Form.Item>
                                </Col>
                            </Row>

                            <Form.Item
                                name="keyStorePassword"
                                label={t.KEYSTORE_PASSWORD || 'KeyStore Password'}
                                rules={[{ required: true, message: t.INPUT_KEYSTORE_PASSWORD || 'Please input KeyStore password' }]}
                            >
                                <Input.Password placeholder={t.INPUT_KEYSTORE_PASSWORD} />
                            </Form.Item>

                            <Form.Item
                                label={t.UPLOAD_KEYSTORE || 'Upload KeyStore File'}
                            >
                                <Upload {...uploadProps}>
                                    <Button icon={<UploadOutlined />}>{t.UPLOAD || 'Upload'}</Button>
                                </Upload>
                            </Form.Item>

                            {sslConfig.clientAuth !== 'none' && (
                                <>
                                    <Divider orientation="left">{t.TRUSTSTORE_CONFIG || 'TrustStore Configuration'}</Divider>

                                    <Row gutter={16}>
                                        <Col span={12}>
                                            <Form.Item
                                                name="trustStoreType"
                                                label={t.TRUSTSTORE_TYPE || 'TrustStore Type'}
                                                rules={[{ required: true }]}
                                            >
                                                <Select>
                                                    <Option value="JKS">JKS</Option>
                                                    <Option value="PKCS12">PKCS12</Option>
                                                </Select>
                                            </Form.Item>
                                        </Col>
                                        <Col span={12}>
                                            <Form.Item
                                                name="trustStorePath"
                                                label={t.TRUSTSTORE_PATH || 'TrustStore Path'}
                                                rules={[{ required: true, message: t.INPUT_TRUSTSTORE_PATH || 'Please input TrustStore path' }]}
                                            >
                                                <Input placeholder="/path/to/truststore.jks" />
                                            </Form.Item>
                                        </Col>
                                    </Row>

                                    <Form.Item
                                        name="trustStorePassword"
                                        label={t.TRUSTSTORE_PASSWORD || 'TrustStore Password'}
                                        rules={[{ required: true, message: t.INPUT_TRUSTSTORE_PASSWORD || 'Please input TrustStore password' }]}
                                    >
                                        <Input.Password placeholder={t.INPUT_TRUSTSTORE_PASSWORD} />
                                    </Form.Item>

                                    <Form.Item
                                        label={t.UPLOAD_TRUSTSTORE || 'Upload TrustStore File'}
                                    >
                                        <Upload {...uploadProps}>
                                            <Button icon={<UploadOutlined />}>{t.UPLOAD || 'Upload'}</Button>
                                        </Upload>
                                    </Form.Item>
                                </>
                            )}

                            <Divider />

                            <Form.Item>
                                <Space>
                                    <Button type="primary" htmlType="submit" loading={loading}>
                                        {t.SAVE || 'Save'}
                                    </Button>
                                    <Button onClick={() => form.resetFields()}>
                                        {t.RESET || 'Reset'}
                                    </Button>
                                </Space>
                            </Form.Item>
                        </>
                    )}
                </Form>
            </Card>

            {sslEnabled && sslConfig.certificateExpiry && (
                <Card
                    title={
                        <Space>
                            <LockOutlined />
                            <span>{t.CERTIFICATE_INFO || 'Certificate Information'}</span>
                        </Space>
                    }
                    className="ssl-card"
                    style={{ marginTop: 16 }}
                >
                    <Descriptions bordered column={2}>
                        <Descriptions.Item label={t.ISSUER || 'Issuer'}>
                            <Tag color="blue">{sslConfig.certificateIssuer}</Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label={t.EXPIRY_DATE || 'Expiry Date'}>
                            <Tag color="green">{sslConfig.certificateExpiry}</Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label={t.PROTOCOL || 'Protocol'}>
                            <Tag>{sslConfig.protocol}</Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label={t.STATUS || 'Status'}>
                            <Tag color="success" icon={<CheckCircleOutlined />}>
                                {t.ACTIVE || 'Active'}
                            </Tag>
                        </Descriptions.Item>
                    </Descriptions>
                </Card>
            )}
        </div>
    );
};

export default SslSettings;
