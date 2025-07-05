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

// TopicSingleModifyForm.js
import React, {useEffect} from "react";
import {Button, Col, Divider, Form, Input, Row, Select} from "antd";

const TopicSingleModifyForm = ({
                                   initialData,
                                   bIsUpdate,
                                   writeOperationEnabled,
                                   allClusterNameList,
                                   allBrokerNameList,
                                   onSubmit,
                                   formIndex,
                                   t,
                               }) => {
    const [form] = Form.useForm();

    useEffect(() => {
        if (initialData) {
            form.setFieldsValue(initialData);
        } else {
            form.resetFields();
        }
    }, [initialData, form, formIndex]);

    const handleFormSubmit = () => {
        form.validateFields()
            .then(values => {
                const updatedValues = {...values};
                // 提交时，如果 clusterNameList 或 brokerNameList 为空，则填充所有可用的名称
                if (!bIsUpdate) {
                    if (!updatedValues.clusterNameList || updatedValues.clusterNameList.length === 0) {
                        updatedValues.clusterNameList = allClusterNameList;
                    }
                    if (!updatedValues.brokerNameList || updatedValues.brokerNameList.length === 0) {
                        updatedValues.brokerNameList = allBrokerNameList;
                    }
                }
                onSubmit(updatedValues, formIndex); // 传递 formIndex
            })
            .catch(info => {
                console.log('Validate Failed:', info);
            });
    };

    const messageTypeOptions = [
        {value: 'TRANSACTION', label: 'TRANSACTION'},
        {value: 'FIFO', label: 'FIFO'},
        {value: 'DELAY', label: 'DELAY'},
        {value: 'NORMAL', label: 'NORMAL'},
    ];

    return (
        <div style={{paddingBottom: 24}}>
            {bIsUpdate && <Divider
                orientation="left">{`${t.TOPIC_CONFIG} - ${initialData.brokerNameList ? initialData.brokerNameList.join(', ') : t.UNKNOWN_BROKER}`}</Divider>}
            <Row justify="center"> {/* 使用 Row 居中内容 */}
                <Col span={16}> {/* 表单内容占据 12 栅格宽度，并自动居中 */}
                    <Form
                        form={form}
                        layout="horizontal"
                        labelCol={{span: 8}}
                        wrapperCol={{span: 16}}
                    >
                        <Form.Item label={t.CLUSTER_NAME} name="clusterNameList">
                            <Select
                                mode="multiple"
                                disabled={bIsUpdate}
                                placeholder={t.SELECT_CLUSTER_NAME}
                                options={allClusterNameList.map(name => ({value: name, label: name}))}
                            />
                        </Form.Item>
                        <Form.Item label="BROKER_NAME" name="brokerNameList">
                            <Select
                                mode="multiple"
                                disabled={bIsUpdate}
                                placeholder={t.SELECT_BROKER_NAME}
                                options={allBrokerNameList.map(name => ({value: name, label: name}))}
                            />
                        </Form.Item>
                        <Form.Item
                            label={t.TOPIC_NAME}
                            name="topicName"
                            rules={[{required: true, message: `${t.TOPIC_NAME}${t.CANNOT_BE_EMPTY}`}]}
                        >
                            <Input disabled={bIsUpdate}/>
                        </Form.Item>
                        <Form.Item label={t.MESSAGE_TYPE} name="messageType">
                            <Select
                                disabled={bIsUpdate}
                                options={messageTypeOptions}
                            />
                        </Form.Item>
                        <Form.Item
                            label={t.WRITE_QUEUE_NUMS}
                            name="writeQueueNums"
                            rules={[{required: true, message: `${t.WRITE_QUEUE_NUMS}${t.CANNOT_BE_EMPTY}`}]}
                        >
                            <Input disabled={!writeOperationEnabled}/>
                        </Form.Item>
                        <Form.Item
                            label={t.READ_QUEUE_NUMS}
                            name="readQueueNums"
                            rules={[{required: true, message: `${t.READ_QUEUE_NUMS}${t.CANNOT_BE_EMPTY}`}]}
                        >
                            <Input disabled={!writeOperationEnabled}/>
                        </Form.Item>
                        <Form.Item
                            label={t.PERM}
                            name="perm"
                            rules={[{required: true, message: `${t.PERM}${t.CANNOT_BE_EMPTY}`}]}
                        >
                            <Input disabled={!writeOperationEnabled}/>
                        </Form.Item>
                        {!initialData.sysFlag && writeOperationEnabled && (
                            <Form.Item wrapperCol={{offset: 8, span: 16}}>
                                <Button type="primary" onClick={handleFormSubmit}>
                                    {t.COMMIT}
                                </Button>
                            </Form.Item>
                        )}
                    </Form>
                </Col>
            </Row>
        </div>
    );
};

export default TopicSingleModifyForm;
