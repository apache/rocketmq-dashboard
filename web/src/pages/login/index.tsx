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

import { useRef, useState } from 'react';
import {
  LockOutlined,
  MoonOutlined,
  RocketOutlined,
  SunOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { App, Button, Form, Input, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useLang } from '../../i18n/LangContext';
import useAuthStore from '../../stores/authStore';
import { login as loginApi } from '../../api/auth';
import { useTheme } from '../../theme/useTheme';
import './index.css';

const { Title } = Typography;

interface LoginFormValues {
  username: string;
  password: string;
}

const LoginPage = () => {
  const [loading, setLoading] = useState(false);
  const loginInFlightRef = useRef(false);
  const [form] = Form.useForm<LoginFormValues>();
  const { t } = useLang();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const authLogin = useAuthStore((s) => s.login);
  const { darkMode, toggleTheme } = useTheme();

  const onFinish = async (values: LoginFormValues) => {
    // React state is applied on the next render, so it cannot prevent two submit
    // events in the same tick. Own the request synchronously before awaiting it.
    if (loginInFlightRef.current) return;
    loginInFlightRef.current = true;
    setLoading(true);
    try {
      const data = await loginApi(values.username, values.password);
      authLogin(data.user.username, data.user.userId, data.user.admin);
      message.success(t('login.success'));
      navigate('/', { replace: true });
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : t('login.failed');
      message.error(errorMsg);
    } finally {
      loginInFlightRef.current = false;
      setLoading(false);
    }
  };

  return (
    <main className="login-page" data-theme={darkMode ? 'dark' : 'light'}>
      <section className="login-brand-panel" aria-labelledby="login-brand-title">
        <div className="login-brand-lockup">
          <span className="login-brand-mark" aria-hidden="true">
            <RocketOutlined />
          </span>
          <span>RocketMQ Studio</span>
        </div>
        <div className="login-brand-copy">
          <span className="login-brand-eyebrow">{t('login.brandEyebrow')}</span>
          <Title id="login-brand-title" level={1} className="login-brand-title">
            {t('login.brandTitle')}
          </Title>
          <Typography.Paragraph className="login-brand-description">
            {t('login.brandDescription')}
          </Typography.Paragraph>
        </div>
        <div className="login-brand-status" aria-label={t('login.statusLabel')}>
          <span className="login-status-dot" aria-hidden="true" />
          {t('login.statusLabel')}
        </div>
      </section>

      <section className="login-form-panel" aria-labelledby="login-form-title">
        <div className="login-theme-action">
          <Button
            type="text"
            shape="circle"
            aria-label={darkMode ? t('login.switchToLight') : t('login.switchToDark')}
            icon={darkMode ? <SunOutlined /> : <MoonOutlined />}
            onClick={toggleTheme}
          />
        </div>
        <div className="login-form-shell">
          <div className="login-form-heading">
            <span className="login-form-mark" aria-hidden="true">
              <RocketOutlined />
            </span>
            <Title id="login-form-title" level={2}>
              {t('login.welcome')}
            </Title>
            <Typography.Paragraph>{t('login.formDescription')}</Typography.Paragraph>
          </div>
          <Form
            form={form}
            name="login_form"
            layout="vertical"
            onFinish={onFinish}
            initialValues={{ username: '', password: '' }}
          >
            <Form.Item
              label={t('login.username')}
              name="username"
              rules={[{ required: true, message: t('login.usernameRequired') }]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder={t('login.usernamePlaceholder')}
                autoComplete="username"
              />
            </Form.Item>

            <Form.Item
              label={t('login.password')}
              name="password"
              rules={[{ required: true, message: t('login.passwordRequired') }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder={t('login.passwordPlaceholder')}
                autoComplete="current-password"
              />
            </Form.Item>

            <Form.Item className="login-submit-item">
              <Button type="primary" htmlType="submit" block loading={loading}>
                {t('login.title')}
              </Button>
            </Form.Item>
          </Form>
          <p className="login-form-footer">Apache RocketMQ Studio</p>
        </div>
      </section>
    </main>
  );
};

export default LoginPage;
