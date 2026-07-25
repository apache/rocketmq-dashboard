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

import { useState } from 'react';
import {
  Card,
  Form,
  Input,
  Button,
  Switch,
  Select,
  Upload,
  Descriptions,
  Tag,
  Space,
  Alert,
  Divider,
  Row,
  Col,
  App,
} from 'antd';
import { ShieldCheck, Lock, CheckCircle, XCircle, UploadSimple } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';

// ─── Types ──────────────────────────────────────────────────────
interface SslConfig {
  enabled: boolean;
  protocol: string;
  keyStoreType: string;
  keyStorePath: string;
  keyStorePassword: string;
  trustStoreType: string;
  trustStorePath: string;
  trustStorePassword: string;
  clientAuth: string;
  certificateExpiry: string | null;
  certificateIssuer: string | null;
}

interface FormValues {
  enabled: boolean;
  protocol: string;
  clientAuth: string;
  keyStoreType: string;
  keyStorePath: string;
  keyStorePassword: string;
  trustStoreType?: string;
  trustStorePath?: string;
  trustStorePassword?: string;
}

// ─── Component ──────────────────────────────────────────────────
const SslSettingsPage = () => {
  const [form] = Form.useForm<FormValues>();
  const [loading, setLoading] = useState(false);
  const [sslEnabled, setSslEnabled] = useState(false);
  const [sslConfig, setSslConfig] = useState<SslConfig>({
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
    certificateIssuer: "Let's Encrypt",
  });
  const { t } = useLang();
  const { message } = App.useApp();

  const handleSave = (values: FormValues) => {
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      message.success(t('ssl.saveSuccess'));
      setSslConfig({ ...sslConfig, ...values });
    }, 1000);
  };

  const handleToggleSsl = (checked: boolean) => {
    setSslEnabled(checked);
    form.setFieldsValue({ enabled: checked });
  };

  const uploadProps = {
    name: 'certificate',
    multiple: false,
    beforeUpload: (file: File) => {
      const isCert =
        file.name.endsWith('.pem') ||
        file.name.endsWith('.crt') ||
        file.name.endsWith('.jks') ||
        file.name.endsWith('.p12');
      if (!isCert) {
        message.error(t('ssl.invalidCertFormat'));
        return false;
      }
      return false;
    },
    onChange: (info: { file: { status?: string } }) => {
      if (info.file.status === 'removed') {
        message.info(t('ssl.certRemoved'));
      }
    },
  };

  return (
    <div style={{ padding: 0 }}>
      <Card
        title={
          <Space>
            <ShieldCheck size={18} style={{ color: '#1677ff' }} />
            <span>{t('ssl.title')}</span>
          </Space>
        }
        bordered={false}
        style={{ borderRadius: 8, boxShadow: '0 1px 6px rgba(0,0,0,0.04)' }}
      >
        <Alert
          message={t('ssl.info')}
          description={t('ssl.infoDesc')}
          type="info"
          showIcon
          style={{ marginBottom: 24 }}
        />

        <Form form={form} layout="vertical" onFinish={handleSave} initialValues={sslConfig}>
          <Form.Item name="enabled" label={t('ssl.enabled')} valuePropName="checked">
            <Switch
              checked={sslEnabled}
              onChange={handleToggleSsl}
              checkedChildren={<CheckCircle size={12} />}
              unCheckedChildren={<XCircle size={12} />}
            />
          </Form.Item>

          {sslEnabled && (
            <>
              <Divider />

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item
                    name="protocol"
                    label={t('ssl.protocol')}
                    rules={[{ required: true, message: t('ssl.selectProtocol') }]}
                  >
                    <Select
                      options={[
                        { value: 'TLSv1.3', label: 'TLS 1.3' },
                        { value: 'TLSv1.2', label: 'TLS 1.2' },
                        { value: 'TLSv1.1', label: 'TLS 1.1 (Deprecated)' },
                        { value: 'TLSv1', label: 'TLS 1.0 (Deprecated)' },
                      ]}
                    />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    name="clientAuth"
                    label={t('ssl.clientAuth')}
                    rules={[{ required: true }]}
                  >
                    <Select
                      options={[
                        { value: 'none', label: t('ssl.none') },
                        { value: 'want', label: t('ssl.want') },
                        { value: 'need', label: t('ssl.need') },
                      ]}
                    />
                  </Form.Item>
                </Col>
              </Row>

              <Divider orientation="left">{t('ssl.keystoreConfig')}</Divider>

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item
                    name="keyStoreType"
                    label={t('ssl.keystoreType')}
                    rules={[{ required: true }]}
                  >
                    <Select
                      options={[
                        { value: 'JKS', label: 'JKS' },
                        { value: 'PKCS12', label: 'PKCS12' },
                      ]}
                    />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    name="keyStorePath"
                    label={t('ssl.keystorePath')}
                    rules={[{ required: true, message: t('ssl.keystorePathPlaceholder') }]}
                  >
                    <Input placeholder="/path/to/keystore.jks" />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item
                name="keyStorePassword"
                label={t('ssl.keystorePassword')}
                rules={[{ required: true, message: t('ssl.keystorePasswordPlaceholder') }]}
              >
                <Input.Password placeholder={t('ssl.keystorePasswordPlaceholder')} />
              </Form.Item>

              <Form.Item label={t('ssl.uploadKeystore')}>
                <Upload {...uploadProps}>
                  <Button icon={<UploadSimple size={14} />}>{t('ssl.upload')}</Button>
                </Upload>
              </Form.Item>

              {form.getFieldValue('clientAuth') !== 'none' && (
                <>
                  <Divider orientation="left">{t('ssl.truststoreConfig')}</Divider>

                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item
                        name="trustStoreType"
                        label={t('ssl.truststoreType')}
                        rules={[{ required: true }]}
                      >
                        <Select
                          options={[
                            { value: 'JKS', label: 'JKS' },
                            { value: 'PKCS12', label: 'PKCS12' },
                          ]}
                        />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item
                        name="trustStorePath"
                        label={t('ssl.truststorePath')}
                        rules={[{ required: true, message: t('ssl.truststorePathPlaceholder') }]}
                      >
                        <Input placeholder="/path/to/truststore.jks" />
                      </Form.Item>
                    </Col>
                  </Row>

                  <Form.Item
                    name="trustStorePassword"
                    label={t('ssl.truststorePassword')}
                    rules={[{ required: true, message: t('ssl.truststorePasswordPlaceholder') }]}
                  >
                    <Input.Password placeholder={t('ssl.truststorePasswordPlaceholder')} />
                  </Form.Item>

                  <Form.Item label={t('ssl.uploadTruststore')}>
                    <Upload {...uploadProps}>
                      <Button icon={<UploadSimple size={14} />}>{t('ssl.upload')}</Button>
                    </Upload>
                  </Form.Item>
                </>
              )}

              <Divider />

              <Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" loading={loading}>
                    {t('ssl.save')}
                  </Button>
                  <Button onClick={() => form.resetFields()}>{t('common.reset')}</Button>
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
              <Lock size={18} style={{ color: '#1677ff' }} />
              <span>{t('ssl.certInfo')}</span>
            </Space>
          }
          bordered={false}
          style={{ marginTop: 16, borderRadius: 8, boxShadow: '0 1px 6px rgba(0,0,0,0.04)' }}
        >
          <Descriptions bordered column={2}>
            <Descriptions.Item label={t('ssl.issuer')}>
              <Tag color="blue">{sslConfig.certificateIssuer}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('ssl.expiryDate')}>
              <Tag color="green">{sslConfig.certificateExpiry}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('ssl.protocol')}>
              <Tag>{sslConfig.protocol}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('common.status')}>
              <Tag color="success" icon={<CheckCircle size={12} />}>
                {t('ssl.active')}
              </Tag>
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}
    </div>
  );
};

export default SslSettingsPage;
