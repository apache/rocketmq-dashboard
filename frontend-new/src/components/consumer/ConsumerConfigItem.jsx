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

import React, {useEffect, useState} from 'react';
import {Button, Descriptions, Form, Input, message, Select, Switch} from 'antd';
import {remoteApi} from '../../api/remoteApi/remoteApi'; // 确保路径正确

const {Option} = Select;

const ConsumerConfigItem = ({
                                initialConfig,
                                isAddConfig,
                                group,
                                brokerName,
                                allBrokerList,
                                allClusterNames,
                                onCancel,
                                onSuccess,
                                t
                            }) => {
    const [form] = Form.useForm();
    const [currentBrokerName, setCurrentBrokerName] = useState(brokerName);

    useEffect(() => {
        if (initialConfig) {
            if (!isAddConfig && initialConfig.brokerNameList && initialConfig.brokerNameList.length > 0) {
                // 更新模式，设置当前BrokerName为第一个（如果只有一个的话，或者您有其他选择逻辑）
                setCurrentBrokerName(initialConfig.brokerNameList[0]);
            }

            form.setFieldsValue({
                ...initialConfig.subscriptionGroupConfig,
                groupName: isAddConfig ? undefined : initialConfig.subscriptionGroupConfig.groupName, // 添加模式下groupName可编辑
                brokerName: isAddConfig ? [] : initialConfig.brokerNameList, // 更新模式下显示已有的brokerName
                clusterName: isAddConfig ? [] : initialConfig.clusterNameList, // 更新模式下显示已有的clusterName
            });
        } else {
            // Reset form for add mode or when initialConfig is null (e.g., when the modal is closed)
            form.resetFields();
            form.setFieldsValue({
                groupName: undefined,
                autoCommit: true,
                enableAutoCommit: true,
                enableAutoOffsetReset: true,
                groupSysFlag: 0,
                consumeTimeoutMinute: 10,
                consumeEnable: true,
                consumeMessageOrderly: false,
                consumeBroadcastEnable: false,
                retryQueueNums: 1,
                retryMaxTimes: 16,
                brokerId: 0,
                whichBrokerWhenConsumeSlowly: 0,
                brokerName: [],
                clusterName: [],
            });
            setCurrentBrokerName(undefined); // 清空当前brokerName
        }
    }, [initialConfig, isAddConfig, form]);

    const handleSubmit = async () => {
        try {
            const values = await form.validateFields();
            const numericValues = {
                retryQueueNums: Number(values.retryQueueNums),
                retryMaxTimes: Number(values.retryMaxTimes),
                brokerId: Number(values.brokerId),
                whichBrokerWhenConsumeSlowly: Number(values.whichBrokerWhenConsumeSlowly),
            };

            // 确保brokerNameList是数组
            let finalBrokerNameList = Array.isArray(values.brokerName) ? values.brokerName : [values.brokerName];
            // 确保clusterNameList是数组
            let finalClusterNameList = Array.isArray(values.clusterName) ? values.clusterName : [values.clusterName];

            const payload = {
                subscriptionGroupConfig: {
                    ...(initialConfig && initialConfig.subscriptionGroupConfig ? initialConfig.subscriptionGroupConfig : {}), // 保留旧的配置，除非被新值覆盖
                    ...values,
                    ...numericValues,
                    groupName: isAddConfig ? values.groupName : group, // 添加模式使用表单中的groupName，更新模式使用传入的group
                },
                brokerNameList: finalBrokerNameList,
                clusterNameList: isAddConfig ? finalClusterNameList : null, // 更新模式保留原有clusterNameList
            };

            const response = await remoteApi.createOrUpdateConsumer(payload);
            if (response.status === 0) {
                message.success(t.SUCCESS);
                onSuccess();
            } else {
                message.error(`${t.OPERATION_FAILED}: ${response.errMsg}`);
                console.error('Failed to create or update consumer:', response.errMsg);
            }
        } catch (error) {
            console.error('Validation failed or API call error:', error);
            message.error(t.FORM_VALIDATION_FAILED);
        } finally {
            onCancel()
        }
    };

    // Helper function to parse input value to number
    const parseNumber = (event) => {
        const value = event.target.value;
        return value === '' ? undefined : Number(value);
    };

    // 如果是添加模式，并且用户还没有选择brokerName，或者没有clusterName可供选择，则不渲染表单
    if (isAddConfig && (!allBrokerList || allBrokerList.length === 0 || !allClusterNames || allClusterNames.length === 0)) {
        return <p>{t.NO_DATA}</p>;
    }

    return (
        <div style={{border: '1px solid #e8e8e8', padding: 20, marginBottom: 20, borderRadius: 8}}>
            {/* 标题根据当前BrokerName或“添加新配置”显示 */}
            <h3>{isAddConfig ? t.ADD_CONSUMER : `${t.CONFIG_FOR_BROKER}: ${currentBrokerName || 'N/A'}`}</h3>
            {!isAddConfig && initialConfig && (
                <Descriptions bordered column={2} style={{marginBottom: 24}} size="small">
                    <Descriptions.Item label={t.CLUSTER_NAME} span={2}>
                        {initialConfig.clusterNameList?.join(', ') || 'N/A'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t.RETRY_POLICY} span={2}>
                        <pre style={{margin: 0, maxHeight: '100px', overflow: 'auto', fontSize: '12px'}}>
                            {JSON.stringify(
                                initialConfig.subscriptionGroupConfig.groupRetryPolicy,
                                null,
                                2
                            ) || 'N/A'}
                        </pre>
                    </Descriptions.Item>
                    <Descriptions.Item label={t.CONSUME_TIMEOUT}>
                        {`${initialConfig.subscriptionGroupConfig.consumeTimeoutMinute} ${t.MINUTES}` || 'N/A'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t.SYSTEM_FLAG}>
                        {initialConfig.subscriptionGroupConfig.groupSysFlag || 'N/A'}
                    </Descriptions.Item>
                </Descriptions>
            )}

            <Form form={form} layout="vertical">
                <Form.Item
                    name="groupName"
                    label={t.GROUP_NAME}
                    rules={[{required: true, message: t.CANNOT_BE_EMPTY}]}
                >
                    <Input disabled={!isAddConfig}/>
                </Form.Item>

                {isAddConfig && (
                    <Form.Item
                        name="clusterName"
                        label={t.CLUSTER_NAME}
                        rules={[{required: true, message: t.PLEASE_SELECT_CLUSTER_NAME}]}
                    >
                        <Select
                            mode="multiple"
                            placeholder={t.SELECT_CLUSTERS}
                            disabled={!isAddConfig}
                        >
                            {allClusterNames.map((cluster) => (
                                <Option key={cluster} value={cluster}>
                                    {cluster}
                                </Option>
                            ))}
                        </Select>
                    </Form.Item>
                )}

                <Form.Item
                    name="brokerName"
                    label={t.BROKER_NAME}
                    rules={[{required: true, message: t.PLEASE_SELECT_BROKER}]}
                >
                    <Select
                        mode="multiple"
                        placeholder={t.SELECT_BROKERS}
                        disabled={!isAddConfig} // 只有在添加模式下才能选择brokerName
                        onChange={(selectedBrokers) => {
                            if (isAddConfig && selectedBrokers.length > 0) {
                                // 在添加模式下，如果选择了broker，则将第一个选中的broker设置为当前brokerName用于显示
                                setCurrentBrokerName(selectedBrokers[0]);
                            }
                        }}
                    >
                        {allBrokerList.map((broker) => (
                            <Option key={broker} value={broker}>
                                {broker}
                            </Option>
                        ))}
                    </Select>
                </Form.Item>


                <Form.Item name="consumeEnable" label={t.CONSUME_ENABLE} valuePropName="checked">
                    <Switch/>
                </Form.Item>

                <Form.Item name="consumeMessageOrderly" label={t.ORDERLY_CONSUMPTION} valuePropName="checked">
                    <Switch/>
                </Form.Item>

                <Form.Item name="consumeBroadcastEnable" label={t.BROADCAST_CONSUMPTION} valuePropName="checked">
                    <Switch/>
                </Form.Item>

                <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16}}>
                    <Form.Item
                        name="retryQueueNums"
                        label={t.RETRY_QUEUES}
                        rules={[{
                            type: 'number',
                            message: t.PLEASE_INPUT_NUMBER,
                            transform: value => Number(value)
                        }, {
                            required: true,
                            message: t.CANNOT_BE_EMPTY
                        }]}
                        getValueFromEvent={parseNumber}
                    >
                        <Input type="number"/>
                    </Form.Item>

                    <Form.Item
                        name="retryMaxTimes"
                        label={t.MAX_RETRIES}
                        rules={[{
                            type: 'number',
                            message: t.PLEASE_INPUT_NUMBER,
                            transform: value => Number(value)
                        }, {
                            required: true,
                            message: t.CANNOT_BE_EMPTY
                        }]}
                        getValueFromEvent={parseNumber}
                    >
                        <Input type="number"/>
                    </Form.Item>

                    <Form.Item
                        name="brokerId"
                        label={t.BROKER_ID}
                        rules={[{
                            type: 'number',
                            message: t.PLEASE_INPUT_NUMBER,
                            transform: value => Number(value)
                        }, {
                            required: true,
                            message: t.CANNOT_BE_EMPTY
                        }]}
                        getValueFromEvent={parseNumber}
                    >
                        <Input type="number"/>
                    </Form.Item>

                    <Form.Item
                        name="whichBrokerWhenConsumeSlowly"
                        label={t.SLOW_CONSUMPTION_BROKER}
                        rules={[{
                            type: 'number',
                            message: t.PLEASE_INPUT_NUMBER,
                            transform: value => Number(value)
                        }, {
                            required: true,
                            message: t.CANNOT_BE_EMPTY
                        }]}
                        getValueFromEvent={parseNumber}
                    >
                        <Input type="number"/>
                    </Form.Item>
                </div>
                <div style={{textAlign: 'right', marginTop: 20}}>
                    <Button type="primary" onClick={handleSubmit}>
                        {t.COMMIT}
                    </Button>
                </div>
            </Form>
        </div>
    );
};

export default ConsumerConfigItem;

