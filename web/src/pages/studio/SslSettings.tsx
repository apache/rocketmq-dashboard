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
import { Alert, Button, Card, Form, Input, Select, Space, Switch, Typography, message } from 'antd';
import { ShieldCheck } from '@phosphor-icons/react';
import {
  getSslSettings,
  saveSslSettings,
  validateSslSettings,
  type SslSettings,
  type SslSettingsUpdate,
  type SslSettingsValidationResult,
} from '../../api/settings';
import { useLang } from '../../i18n/LangContext';

const { Text } = Typography;

const STORE_TYPE_OPTIONS = [
  { value: 'PKCS12', label: 'PKCS12' },
  { value: 'JKS', label: 'JKS' },
];

const PROTOCOL_OPTIONS = [
  { value: 'TLSv1.3', label: 'TLSv1.3' },
  { value: 'TLSv1.2', label: 'TLSv1.2' },
];

const defaultFormValues: SslSettingsUpdate = {
  enabled: false,
  protocol: 'TLSv1.3',
  clientAuth: 'none',
  keyStoreType: 'PKCS12',
  keyStorePath: '',
  trustStoreType: 'PKCS12',
  trustStorePath: '',
};

const toFormValues = (settings: SslSettings): SslSettingsUpdate => ({
  enabled: settings.enabled,
  protocol: settings.protocol,
  clientAuth: settings.clientAuth,
  keyStoreType: settings.keyStoreType,
  keyStorePath: settings.keyStorePath,
  trustStoreType: settings.trustStoreType,
  trustStorePath: settings.trustStorePath,
});

const SslSettingsPage = () => {
  const { t } = useLang();
  const [form] = Form.useForm<SslSettingsUpdate>();
  const [settings, setSettings] = useState<SslSettings | null>(null);
  const [validation, setValidation] = useState<SslSettingsValidationResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [validating, setValidating] = useState(false);

  useEffect(() => {
    let active = true;
    getSslSettings()
      .then((data) => {
        if (!active) return;
        setSettings(data);
        form.setFieldsValue(toFormValues(data));
      })
      .catch((error: unknown) => {
        if (!active) return;
        message.error(error instanceof Error ? error.message : t('ssl.loadFailed'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [form, t]);

  const currentPayload = async () => ({
    ...defaultFormValues,
    ...(settings ? toFormValues(settings) : {}),
    ...(await form.validateFields()),
  });

  const refreshSavedSettings = (data: SslSettings) => {
    setSettings(data);
    form.setFieldsValue({
      ...toFormValues(data),
      keyStorePassword: '',
      trustStorePassword: '',
      clearKeyStorePassword: false,
      clearTrustStorePassword: false,
    });
  };

  const handleSave = async () => {
    setSaving(true);
    setValidation(null);
    try {
      const saved = await saveSslSettings(await currentPayload());
      refreshSavedSettings(saved);
      message.success(t('ssl.saveSuccess'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('ssl.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const handleValidate = async () => {
    setValidating(true);
    setValidation(null);
    try {
      setValidation(await validateSslSettings(await currentPayload()));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('ssl.validateFailed'));
    } finally {
      setValidating(false);
    }
  };

  return (
    <div style={{ padding: 0 }}>
      <Card
        loading={loading}
        title={
          <Space>
            <ShieldCheck size={18} style={{ color: '#1677ff' }} />
            <span>{t('ssl.title')}</span>
          </Space>
        }
        variant="borderless"
        style={{ borderRadius: 8, boxShadow: '0 1px 6px rgba(0,0,0,0.04)' }}
      >
        <Alert
          message={t('ssl.restartRequired')}
          description={t('ssl.restartRequiredDesc')}
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />
        {validation && (
          <Alert
            data-testid="ssl-validation-result"
            message={validation.message}
            description={validation.warnings?.length ? validation.warnings.join('; ') : undefined}
            type={validation.success ? 'success' : 'error'}
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}
        <Form
          form={form}
          layout="vertical"
          initialValues={defaultFormValues}
          data-testid="ssl-settings-form"
        >
          <Form.Item name="enabled" label={t('ssl.enabled')} valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="protocol" label={t('ssl.protocol')} rules={[{ required: true }]}>
            <Select options={PROTOCOL_OPTIONS} />
          </Form.Item>
          <Form.Item name="clientAuth" label={t('ssl.clientAuth')} rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'none', label: t('ssl.none') },
                { value: 'want', label: t('ssl.want') },
                { value: 'need', label: t('ssl.need') },
              ]}
            />
          </Form.Item>

          <Card size="small" title={t('ssl.keystoreConfig')} style={{ marginBottom: 16 }}>
            <Form.Item
              name="keyStoreType"
              label={t('ssl.keystoreType')}
              rules={[{ required: true }]}
            >
              <Select options={STORE_TYPE_OPTIONS} />
            </Form.Item>
            <Form.Item name="keyStorePath" label={t('ssl.keystorePath')}>
              <Input placeholder={t('ssl.keystorePathPlaceholder')} />
            </Form.Item>
            <Form.Item name="keyStorePassword" label={t('ssl.keystorePassword')}>
              <Input.Password placeholder={t('ssl.passwordPreservePlaceholder')} />
            </Form.Item>
            {settings?.keyStorePasswordConfigured && (
              <Text type="secondary">{t('ssl.passwordConfigured')}</Text>
            )}
          </Card>

          <Card size="small" title={t('ssl.truststoreConfig')} style={{ marginBottom: 16 }}>
            <Form.Item
              name="trustStoreType"
              label={t('ssl.truststoreType')}
              rules={[{ required: true }]}
            >
              <Select options={STORE_TYPE_OPTIONS} />
            </Form.Item>
            <Form.Item name="trustStorePath" label={t('ssl.truststorePath')}>
              <Input placeholder={t('ssl.truststorePathPlaceholder')} />
            </Form.Item>
            <Form.Item name="trustStorePassword" label={t('ssl.truststorePassword')}>
              <Input.Password placeholder={t('ssl.passwordPreservePlaceholder')} />
            </Form.Item>
            {settings?.trustStorePasswordConfigured && (
              <Text type="secondary">{t('ssl.passwordConfigured')}</Text>
            )}
          </Card>

          <Space>
            <Button aria-label={t('ssl.validate')} onClick={handleValidate} loading={validating}>
              {t('ssl.validate')}
            </Button>
            <Button aria-label={t('ssl.save')} type="primary" onClick={handleSave} loading={saving}>
              {t('ssl.save')}
            </Button>
          </Space>
        </Form>
      </Card>
    </div>
  );
};

export default SslSettingsPage;
