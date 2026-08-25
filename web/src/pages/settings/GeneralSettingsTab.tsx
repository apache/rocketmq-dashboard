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
import {
  App,
  Button,
  Card,
  Flex,
  Form,
  Input,
  InputNumber,
  Segmented,
  Space,
  Switch,
  Typography,
} from 'antd';
import { BellOutlined, SafetyOutlined, SkinOutlined } from '@ant-design/icons';
import { getGeneralSettings, saveGeneralSettings } from '../../api/settings';
import type { GeneralSettings, GeneralSettingsUpdate } from '../../api/settings';
import { useTheme } from '../../theme/useTheme';
import type { ThemeMode } from '../../theme/themePreference';
import { useLang } from '../../i18n/LangContext';

const { Text } = Typography;

const toThemeMode = (value?: string): ThemeMode =>
  value === 'light' || value === 'dark' ? value : 'system';

const buildPayload = (settings: GeneralSettings): GeneralSettingsUpdate => ({
  theme: settings.theme,
  compact: settings.compact,
  desktopNotify: settings.desktopNotify,
  notifySound: settings.notifySound,
  sessionTimeout: settings.sessionTimeout,
  requireLogin: settings.requireLogin,
  llmProvider: settings.llmProvider,
  model: settings.model,
  baseUrl: settings.baseUrl,
  dingtalkWebhook: settings.dingtalkWebhook,
  emailRecipients: settings.emailRecipients,
  smsWebhook: settings.smsWebhook,
});

export const GeneralSettingsTab = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const { setThemeMode, setCompact } = useTheme();
  const [settings, setSettings] = useState<GeneralSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingPreference, setSavingPreference] = useState(false);
  const [savingSecurity, setSavingSecurity] = useState(false);
  const [savingNotification, setSavingNotification] = useState(false);
  const securityInFlightRef = useRef(false);
  const notifyInFlightRef = useRef(false);
  const [securityForm] = Form.useForm();
  const [notifyForm] = Form.useForm();

  useEffect(() => {
    let cancelled = false;
    void getGeneralSettings()
      .then((loaded) => {
        if (cancelled) return;
        setSettings(loaded);
        securityForm.setFieldsValue({ sessionTimeout: loaded.sessionTimeout });
        notifyForm.setFieldsValue({
          dingtalkWebhook: loaded.dingtalkWebhook,
          emailRecipients: loaded.emailRecipients,
          smsWebhook: loaded.smsWebhook,
        });
      })
      .catch(() => {
        if (!cancelled) message.error(t('settings.loadFailed'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [message, notifyForm, securityForm, t]);

  const persistPreference = async (patch: Partial<GeneralSettings>) => {
    if (!settings) return;
    const next = { ...settings, ...patch };
    setSettings(next);
    setSavingPreference(true);
    try {
      await saveGeneralSettings(buildPayload(next));
      message.success(t('settings.saveSuccess'));
    } catch {
      message.error(t('settings.saveFailed'));
    } finally {
      setSavingPreference(false);
    }
  };

  const mergeAndSave = async (patch: Partial<GeneralSettings>) => {
    if (!settings) return false;
    try {
      await saveGeneralSettings(buildPayload({ ...settings, ...patch }));
      setSettings({ ...settings, ...patch });
      return true;
    } catch {
      message.error(t('settings.saveFailed'));
      return false;
    }
  };

  const handleSecurityFinish = async (values: { sessionTimeout: number }) => {
    if (securityInFlightRef.current) return;
    securityInFlightRef.current = true;
    setSavingSecurity(true);
    try {
      if (await mergeAndSave({ sessionTimeout: values.sessionTimeout })) {
        message.success(t('settings.saveSuccess'));
      }
    } finally {
      securityInFlightRef.current = false;
      setSavingSecurity(false);
    }
  };

  const handleNotifyFinish = async (values: {
    dingtalkWebhook?: string;
    emailRecipients?: string;
    smsWebhook?: string;
  }) => {
    if (notifyInFlightRef.current) return;
    notifyInFlightRef.current = true;
    setSavingNotification(true);
    try {
      if (await mergeAndSave(values)) {
        message.success(t('settings.saveSuccess'));
      }
    } finally {
      notifyInFlightRef.current = false;
      setSavingNotification(false);
    }
  };

  return (
    <Flex vertical gap={24} style={{ maxWidth: 860 }}>
      <Card
        title={
          <Space>
            <SkinOutlined />
            {t('settings.appearance')}
          </Space>
        }
        loading={loading}
      >
        <Flex vertical gap={20}>
          <Flex justify="space-between" align="center" gap={16}>
            <div>
              <div>{t('settings.themeMode')}</div>
              <Text type="secondary" style={{ fontSize: 14 }}>
                {t('settings.themeHelp')}
              </Text>
            </div>
            <Segmented
              value={toThemeMode(settings?.theme)}
              options={[
                { value: 'light', label: t('settings.lightTheme') },
                { value: 'dark', label: t('settings.darkTheme') },
                { value: 'system', label: t('settings.systemTheme') },
              ]}
              disabled={savingPreference}
              onChange={(value) => {
                const mode = value as ThemeMode;
                setThemeMode(mode);
                void persistPreference({ theme: mode });
              }}
            />
          </Flex>
          <Flex justify="space-between" align="center" gap={16}>
            <div>
              <div>{t('settings.compactMode')}</div>
              <Text type="secondary" style={{ fontSize: 14 }}>
                {t('settings.compactHelp')}
              </Text>
            </div>
            <Switch
              checked={Boolean(settings?.compact)}
              disabled={savingPreference}
              onChange={(checked) => {
                setCompact(checked);
                void persistPreference({ compact: checked });
              }}
            />
          </Flex>
        </Flex>
      </Card>

      <Card
        title={
          <Space>
            <SafetyOutlined />
            {t('settings.securitySession')}
          </Space>
        }
        loading={loading}
      >
        <Form form={securityForm} layout="vertical" onFinish={handleSecurityFinish}>
          <Form.Item
            label={t('settings.sessionTimeout')}
            extra={t('settings.sessionTimeoutHelp')}
            style={{ marginBottom: 16 }}
          >
            <Space.Compact>
              <Form.Item
                name="sessionTimeout"
                noStyle
                rules={[{ required: true, message: t('settings.sessionTimeoutRequired') }]}
              >
                <InputNumber min={5} max={1440} style={{ width: 120 }} />
              </Form.Item>
              <Input
                aria-label={t('settings.sessionTimeoutUnit')}
                readOnly
                value={t('settings.minutes')}
                style={{ width: 64 }}
              />
            </Space.Compact>
          </Form.Item>

          <Form.Item style={{ marginBottom: 16 }}>
            <Button type="primary" htmlType="submit" loading={savingSecurity} disabled={loading}>
              {t('settings.saveSettings')}
            </Button>
          </Form.Item>

          <Text type="secondary" style={{ fontSize: 14 }}>
            {t('settings.loginProtectionHelp')}
          </Text>
        </Form>
      </Card>

      <Card
        title={
          <Space>
            <BellOutlined />
            {t('settings.notification')}
          </Space>
        }
        loading={loading}
      >
        <Form form={notifyForm} layout="vertical" onFinish={handleNotifyFinish}>
          <Form.Item
            label={t('settings.dingtalkWebhook')}
            name="dingtalkWebhook"
            extra={t('settings.dingtalkWebhookHelp')}
          >
            <Input placeholder="https://oapi.dingtalk.com/robot/send?access_token=..." />
          </Form.Item>

          <Form.Item
            label={t('settings.emailRecipients')}
            name="emailRecipients"
            extra={t('settings.emailRecipientsHelp')}
          >
            <Input.TextArea rows={2} placeholder="ops@example.com, oncall@example.com" />
          </Form.Item>

          <Form.Item
            label={t('settings.smsWebhook')}
            name="smsWebhook"
            extra={t('settings.smsWebhookHelp')}
          >
            <Input placeholder="https://sms-gateway.example.com/notify" />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <Button
              type="primary"
              htmlType="submit"
              loading={savingNotification}
              disabled={loading}
            >
              {t('settings.saveSettings')}
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </Flex>
  );
};

export default GeneralSettingsTab;
