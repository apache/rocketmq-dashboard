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
  MessageOutlined,
  MoonOutlined,
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
      {/* ── Animated Orbs Background (same recipe as the home page) ── */}
      <div
        className="login-orbs"
        aria-hidden="true"
        style={{ animation: '8s ease-in-out infinite oneday-bg-drift' }}
      >
        {/* Top-left blue orb */}
        <div
          className="login-orb"
          style={{
            top: '-14%',
            left: '-7%',
            width: '42%',
            height: '42%',
            background:
              'radial-gradient(circle at 30% 30%, rgb(186, 230, 253) 0%, transparent 65%)',
            opacity: 0.45,
            filter: 'blur(80px)',
            animation: '8s ease-in-out infinite oneday-orb-drift-a',
          }}
        />
        {/* Bottom-right violet orb */}
        <div
          className="login-orb"
          style={{
            bottom: '-18%',
            right: '-10%',
            width: '48%',
            height: '48%',
            background:
              'radial-gradient(circle at 70% 70%, rgb(221, 214, 254) 0%, rgb(233, 213, 255) 40%, transparent 68%)',
            opacity: 0.4,
            filter: 'blur(90px)',
            animation: '10s ease-in-out infinite oneday-orb-drift-b',
          }}
        />
        {/* Top-right warm accent */}
        <div
          className="login-orb"
          style={{
            top: '-8%',
            right: '5%',
            width: '30%',
            height: '30%',
            background:
              'radial-gradient(circle at 60% 40%, rgb(254, 215, 170) 0%, transparent 60%)',
            opacity: 0.3,
            filter: 'blur(70px)',
            animation: '12s ease-in-out infinite oneday-orb-drift-c',
          }}
        />
        {/* Center-bottom subtle blue-green */}
        <div
          className="login-orb"
          style={{
            bottom: '10%',
            left: '20%',
            width: '35%',
            height: '28%',
            background:
              'radial-gradient(ellipse at 50% 80%, rgb(153, 246, 228) 0%, transparent 60%)',
            opacity: 0.2,
            filter: 'blur(80px)',
            animation: '14s ease-in-out infinite oneday-orb-drift-a',
          }}
        />
        {/* Noise texture overlay */}
        <div
          className="login-noise"
          style={{
            backgroundImage: `url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/></filter><rect width='100%' height='100%' filter='url(%23n)' opacity='1'/></svg>")`,
          }}
        />
      </div>

      <div className="login-theme-action">
        <Button
          type="text"
          shape="circle"
          aria-label={darkMode ? t('login.switchToLight') : t('login.switchToDark')}
          icon={darkMode ? <SunOutlined /> : <MoonOutlined />}
          onClick={toggleTheme}
        />
      </div>

      <section className="login-card" aria-labelledby="login-form-title">
        <div className="login-brand-lockup">
          <span className="login-brand-mark" aria-hidden="true">
            <MessageOutlined />
          </span>
          <span className="login-brand-name">RocketMQ Studio</span>
          <span className="login-brand-eyebrow">{t('login.brandEyebrow')}</span>
        </div>

        <div className="login-headline">
          <Title id="login-form-title" level={1} className="login-headline-title">
            {t('login.brandTitle')}
          </Title>
          <Typography.Paragraph className="login-headline-description">
            {t('login.brandDescription')}
          </Typography.Paragraph>
        </div>

        <div className="login-form-shell">
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
        </div>

        <div className="login-card-footer">
          <span className="login-status" aria-label={t('login.statusLabel')}>
            <span className="login-status-dot" aria-hidden="true" />
            {t('login.statusLabel')}
          </span>
          <span className="login-form-footer">Apache RocketMQ Studio</span>
        </div>
      </section>
    </main>
  );
};

export default LoginPage;
