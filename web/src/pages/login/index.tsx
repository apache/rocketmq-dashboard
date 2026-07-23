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
import { Button, Form, Input, Typography, App } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useLang } from '../../i18n/LangContext';
import useAuthStore from '../../stores/authStore';
import { login as loginApi } from '../../api/auth';

const { Title } = Typography;

interface LoginFormValues {
  username: string;
  password: string;
}

const LoginPage = () => {
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm<LoginFormValues>();
  const { t } = useLang();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const authLogin = useAuthStore((s) => s.login);

  const onFinish = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      const data = await loginApi(values.username, values.password);
      authLogin(data.token ?? '', data.username);
      localStorage.setItem('userrole', data.role ?? data.type);
      message.success(t('login.success'));
      navigate('/', { replace: true });
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : t('login.failed');
      message.error(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        maxWidth: 400,
        margin: '100px auto',
        padding: 24,
        boxShadow: '0 2px 8px #f0f1f2',
        borderRadius: 8,
      }}
    >
      <Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>
        {t('login.welcome')}
      </Title>
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
          <Input placeholder={t('login.usernamePlaceholder')} />
        </Form.Item>

        <Form.Item
          label={t('login.password')}
          name="password"
          rules={[{ required: true, message: t('login.passwordRequired') }]}
        >
          <Input.Password placeholder={t('login.passwordPlaceholder')} />
        </Form.Item>

        <Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            {t('login.title')}
          </Button>
        </Form.Item>
      </Form>
    </div>
  );
};

export default LoginPage;
