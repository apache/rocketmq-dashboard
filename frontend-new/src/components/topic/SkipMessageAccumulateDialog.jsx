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

import {Button, Form, message, Modal, Select} from "antd";
import React, {useEffect, useState} from "react";

const SkipMessageAccumulateDialog = ({
                                         visible,
                                         onClose,
                                         topic,
                                         allConsumerGroupList,
                                         handleSkipMessageAccumulate,
                                         t
                                     }) => {
    const [form] = Form.useForm();
    const [selectedConsumerGroup, setSelectedConsumerGroup] = useState([]);

    useEffect(() => {
        if (!visible) {
            setSelectedConsumerGroup([]);
            form.resetFields();
        }
    }, [visible, form]);

    const handleCommit = () => {
        if (!selectedConsumerGroup.length) {
            message.error(t.PLEASE_SELECT_GROUP);
            return;
        }
        handleSkipMessageAccumulate(selectedConsumerGroup);
        onClose();
    };

    return (
        <Modal
            title={`${topic} ${t.SKIP_MESSAGE_ACCUMULATE}`}
            open={visible}
            onCancel={onClose}
            footer={[
                <Button key="commit" type="primary" onClick={handleCommit}>
                    {t.COMMIT}
                </Button>,
                <Button key="close" onClick={onClose}>
                    {t.CLOSE}
                </Button>,
            ]}
        >
            <Form form={form} layout="horizontal" labelCol={{span: 6}} wrapperCol={{span: 18}}>
                <Form.Item label={t.SUBSCRIPTION_GROUP} required>
                    <Select
                        mode="multiple"
                        placeholder={t.SELECT_CONSUMER_GROUP}
                        value={selectedConsumerGroup}
                        onChange={setSelectedConsumerGroup}
                        options={allConsumerGroupList.map(group => ({value: group, label: group}))}
                    />
                </Form.Item>
            </Form>
        </Modal>
    );
};

export default SkipMessageAccumulateDialog;
