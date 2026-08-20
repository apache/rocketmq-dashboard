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

const { Text } = Typography;

const THEME_OPTIONS = [
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
  { value: 'system', label: '跟随系统' },
];

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
  const { message } = App.useApp();
  const { setThemeMode, setCompact } = useTheme();
  const [settings, setSettings] = useState<GeneralSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingPreference, setSavingPreference] = useState(false);
  const [savingSecurity, setSavingSecurity] = useState(false);
  const [savingNotification, setSavingNotification] = useState(false);
  const settingsRef = useRef<GeneralSettings | null>(null);
  const saveQueueRef = useRef<Promise<void>>(Promise.resolve());
  const securityInFlightRef = useRef(false);
  const notifyInFlightRef = useRef(false);
  const [securityForm] = Form.useForm();
  const [notifyForm] = Form.useForm();

  useEffect(() => {
    let cancelled = false;
    void getGeneralSettings()
      .then((loaded) => {
        if (cancelled) return;
        settingsRef.current = loaded;
        setSettings(loaded);
        securityForm.setFieldsValue({ sessionTimeout: loaded.sessionTimeout });
        notifyForm.setFieldsValue({
          dingtalkWebhook: loaded.dingtalkWebhook,
          emailRecipients: loaded.emailRecipients,
          smsWebhook: loaded.smsWebhook,
        });
      })
      .catch(() => {
        if (!cancelled) message.error('通用设置加载失败，请稍后重试');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const mergeAndSave = (patch: Partial<GeneralSettings>) => {
    const operation = saveQueueRef.current.then(async () => {
      const previous = settingsRef.current;
      if (!previous) return { saved: false, previous: null };

      const next = { ...previous, ...patch };
      try {
        await saveGeneralSettings(buildPayload(next));
        settingsRef.current = next;
        setSettings((current) => (current ? { ...current, ...patch } : next));
        return { saved: true, previous };
      } catch {
        message.error('设置保存失败，请稍后重试');
        return { saved: false, previous };
      }
    });
    saveQueueRef.current = operation.then(() => undefined);
    return operation;
  };

  const persistPreference = async (patch: Partial<GeneralSettings>) => {
    if (!settingsRef.current) return;
    setSettings((current) => (current ? { ...current, ...patch } : current));
    setSavingPreference(true);
    const result = await mergeAndSave(patch);
    if (result.saved) {
      message.success('设置已保存');
    } else if (result.previous) {
      const rollback: Partial<GeneralSettings> = {};
      if (patch.theme !== undefined) {
        rollback.theme = result.previous.theme;
        setThemeMode(toThemeMode(result.previous.theme));
      }
      if (patch.compact !== undefined) {
        rollback.compact = result.previous.compact;
        setCompact(result.previous.compact);
      }
      setSettings((current) => (current ? { ...current, ...rollback } : current));
    }
    setSavingPreference(false);
  };

  const handleSecurityFinish = async (values: { sessionTimeout: number }) => {
    if (securityInFlightRef.current) return;
    securityInFlightRef.current = true;
    setSavingSecurity(true);
    try {
      if ((await mergeAndSave({ sessionTimeout: values.sessionTimeout })).saved) {
        message.success('设置已保存');
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
      if ((await mergeAndSave(values)).saved) {
        message.success('设置已保存');
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
            界面偏好
          </Space>
        }
        loading={loading}
      >
        <Flex vertical gap={20}>
          <Flex justify="space-between" align="center" gap={16}>
            <div>
              <div>主题模式</div>
              <Text type="secondary" style={{ fontSize: 14 }}>
                作用于前端展示，跟随系统时随操作系统外观变化
              </Text>
            </div>
            <Segmented
              value={toThemeMode(settings?.theme)}
              options={THEME_OPTIONS}
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
              <div>紧凑模式</div>
              <Text type="secondary" style={{ fontSize: 14 }}>
                减小组件间距，单屏展示更多内容
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
            安全与会话
          </Space>
        }
        loading={loading}
      >
        <Form form={securityForm} layout="vertical" onFinish={handleSecurityFinish}>
          <Form.Item
            label="会话超时"
            extra="应用于新创建的会话，已登录用户保持原到期时间"
            style={{ marginBottom: 16 }}
          >
            <Space.Compact>
              <Form.Item
                name="sessionTimeout"
                noStyle
                rules={[{ required: true, message: '请输入会话超时时长' }]}
              >
                <InputNumber min={5} max={1440} style={{ width: 120 }} />
              </Form.Item>
              <Input aria-label="会话超时单位" readOnly value="分钟" style={{ width: 64 }} />
            </Space.Compact>
          </Form.Item>

          <Form.Item style={{ marginBottom: 16 }}>
            <Button type="primary" htmlType="submit" loading={savingSecurity} disabled={loading}>
              保存设置
            </Button>
          </Form.Item>

          <Text type="secondary" style={{ fontSize: 14 }}>
            登录保护由服务端 STUDIO_AUTH_LOGIN_REQUIRED 配置决定，修改后重启服务生效。
          </Text>
        </Form>
      </Card>

      <Card
        title={
          <Space>
            <BellOutlined />
            通知设置
          </Space>
        }
        loading={loading}
      >
        <Form form={notifyForm} layout="vertical" onFinish={handleNotifyFinish}>
          <Form.Item
            label="钉钉机器人 Webhook"
            name="dingtalkWebhook"
            extra="告警规则选择钉钉渠道时推送到该机器人"
          >
            <Input placeholder="https://oapi.dingtalk.com/robot/send?access_token=..." />
          </Form.Item>

          <Form.Item
            label="邮件收件人"
            name="emailRecipients"
            extra="多个地址用英文逗号分隔；邮件发送通道后续接入"
          >
            <Input.TextArea rows={2} placeholder="ops@example.com, oncall@example.com" />
          </Form.Item>

          <Form.Item
            label="短信网关 Webhook"
            name="smsWebhook"
            extra="占位配置，短信发送通道后续接入"
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
              保存设置
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </Flex>
  );
};

export default GeneralSettingsTab;
