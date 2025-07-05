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

import {Button, DatePicker, Form, Modal, Select} from "antd";
import React, {useEffect, useState} from "react";

const ConsumerResetOffsetDialog = ({visible, onClose, topic, allConsumerGroupList, handleResetOffset, t}) => {
    const [form] = Form.useForm();
    const [selectedConsumerGroup, setSelectedConsumerGroup] = useState([]);
    const [selectedTime, setSelectedTime] = useState(null);

    useEffect(() => {
        if (!visible) {
            setSelectedConsumerGroup([]);
            setSelectedTime(null);
            form.resetFields();
        }
    }, [visible, form]);

    const handleResetButtonClick = () => {
        handleResetOffset(selectedConsumerGroup, selectedTime ? selectedTime.valueOf() : null);
    };

    return (
        <Modal
            title={`${topic} ${t.RESET_OFFSET}`}
            open={visible}
            onCancel={onClose}
            footer={[
                <Button key="reset" type="primary" onClick={handleResetButtonClick}>
                    {t.RESET}
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
                <Form.Item label={t.TIME} required>
                    <DatePicker
                        showTime
                        format="YYYY-MM-DD HH:mm:ss"
                        value={selectedTime}
                        onChange={setSelectedTime}
                        style={{width: '100%'}}
                    />
                </Form.Item>
            </Form>
        </Modal>
    );
};

export default ConsumerResetOffsetDialog;
