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

import React from 'react';
import {Button, Form, Input, message, Typography} from 'antd';
import {remoteApi} from "../../api/remoteApi/remoteApi";
import {useLanguage} from "../../i18n/LanguageContext";

const {Title} = Typography;

const Login = () => {
    const [form] = Form.useForm();
    const [messageApi, msgContextHolder] = message.useMessage();
    const {t} = useLanguage();
    const onFinish = async (values) => {
        const {username, password} = values;
        remoteApi.login(username, password).then((res) => {
            if (res.status === 0) {
                messageApi.success(t.LOGIN_SUCCESS);
                window.localStorage.setItem("username", res.data.loginUserName);
                window.localStorage.setItem("userrole", res.data.loginUserRole);
                window.location.href = '/';
            } else {
                messageApi.error(res.message || t.LOGIN_FAILED);
            }
        })
    };

    return (
        <>
            {msgContextHolder}
            <div style={{
                maxWidth: 400,
                margin: '100px auto',
                padding: 24,
                boxShadow: '0 2px 8px #f0f1f2',
                borderRadius: 8
            }}>
                <Title level={3} style={{textAlign: 'center', marginBottom: 24}}>
                    {t.WELCOME}
                </Title>
                <Form
                    form={form}
                    name="login_form"
                    layout="vertical"
                    onFinish={onFinish}
                    initialValues={{username: '', password: ''}}
                >
                    <Form.Item
                        label={t.USERNAME}
                        name="username"
                        rules={[{required: true, message: t.USERNAME_REQUIRED}]}>
                        <Input placeholder={t.USERNAME_PLACEHOLDER}/>
                    </Form.Item>

                    <Form.Item
                        label={t.PASSWORD}
                        name="password"
                        rules={[{required: true, message: t.PASSWORD_REQUIRED}]}>
                        <Input.Password placeholder={t.PASSWORD_PLACEHOLDER}/>
                    </Form.Item>

                    <Form.Item>
                        <Button type="primary" htmlType="submit" block>
                            {t.LOGIN}
                        </Button>
                    </Form.Item>
                </Form>
            </div>
        </>
    );
};

export default Login;
