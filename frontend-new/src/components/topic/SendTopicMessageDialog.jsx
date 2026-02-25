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

import {Button, Checkbox, Form, Input, Modal} from "antd";
import React, {useEffect} from "react";
import {remoteApi} from "../../api/remoteApi/remoteApi";

const SendTopicMessageDialog = ({
                                    visible,
                                    onClose,
                                    topic,
                                    setSendResultData,
                                    setIsSendResultModalVisible,
                                    setIsSendTopicMessageModalVisible,
                                    message,
                                    t,
                                }) => {
    const [form] = Form.useForm();

    useEffect(() => {
        if (visible) {
            form.setFieldsValue({
                topic: topic,
                tag: '',
                key: '',
                messageBody: '',
                traceEnabled: false,
            });
        } else {
            form.resetFields();
        }
    }, [visible, topic, form]);

    const handleSendTopicMessage = async () => {
        try {
            const values = await form.validateFields();
            const result = await remoteApi.sendTopicMessage(values);
            if (result.status === 0) {
                setSendResultData(result.data);
                setIsSendResultModalVisible(true);
                setIsSendTopicMessageModalVisible(false);
            } else {
                message.error(result.errMsg);
            }
        } catch (error) {
            console.error("Error sending message:", error);
            message.error("Failed to send message");
        }
    };

    return (
        <Modal
            title={`${t.SEND}[${topic}]${t.MESSAGE}`}
            open={visible}
            onCancel={onClose}
            footer={[
                <Button key="commit" type="primary" onClick={handleSendTopicMessage}>
                    {t.COMMIT}
                </Button>,
                <Button key="close" onClick={onClose}>
                    {t.CLOSE}
                </Button>,
            ]}
        >
            <Form form={form} layout="horizontal" labelCol={{span: 6}} wrapperCol={{span: 18}}>
                <Form.Item label={t.TOPIC} name="topic">
                    <Input disabled/>
                </Form.Item>
                <Form.Item label={t.TAG} name="tag">
                    <Input/>
                </Form.Item>
                <Form.Item label={t.KEY} name="key">
                    <Input/>
                </Form.Item>
                <Form.Item label={t.MESSAGE_BODY} name="messageBody" rules={[{required: true, message: t.REQUIRED}]}>
                    <Input.TextArea
                        style={{maxHeight: '200px', minHeight: '200px', resize: 'none'}}
                        rows={8}
                    />
                </Form.Item>
                <Form.Item label={t.ENABLE_MESSAGE_TRACE} name="traceEnabled" valuePropName="checked">
                    <Checkbox/>
                </Form.Item>
            </Form>
        </Modal>
    );
};

export default SendTopicMessageDialog;
