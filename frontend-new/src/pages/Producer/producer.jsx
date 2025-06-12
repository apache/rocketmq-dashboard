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
import {Button, Form, Input, message, Select, Table} from 'antd';
import {remoteApi} from '../../api/remoteApi/remoteApi';

const {Option} = Select;

const ProducerConnectionList = () => {
    const [form] = Form.useForm();
    const [allTopicList, setAllTopicList] = useState([]);
    const [connectionList, setConnectionList] = useState([]);
    const [loading, setLoading] = useState(false);
    const [messageApi, msgContextHolder] = message.useMessage();
    useEffect(() => {
        const fetchTopicList = async () => {
            setLoading(true);
            try {
                const resp = await remoteApi.queryTopic(true);
                if (!resp) {
                    messageApi.error("Failed to fetch topic list - no response");
                    return;
                }
                if (resp.status === 0) {
                    setAllTopicList(resp.data.topicList.sort());
                } else {
                    messageApi.error(resp.errMsg || "Failed to fetch topic list");
                }
            } catch (error) {
                messageApi.error("An error occurred while fetching topic list");
                console.error("Fetch error:", error);
            } finally {
                setLoading(false);
            }
        };
        fetchTopicList();
    }, []);

    const onFinish = (values) => {
        setLoading(true);
        const {selectedTopic, producerGroup} = values;
        remoteApi.queryProducerConnection(selectedTopic, producerGroup, (resp) => {
            if (resp.status === 0) {
                setConnectionList(resp.data.connectionSet);
            } else {
                messageApi.error(resp.errMsg || "Failed to fetch producer connection list");
            }
            setLoading(false);
        });
    };

    const columns = [
        {
            title: 'clientId',
            dataIndex: 'clientId',
            key: 'clientId',
            align: 'center',
        },
        {
            title: 'clientAddr',
            dataIndex: 'clientAddr',
            key: 'clientAddr',
            align: 'center',
        },
        {
            title: 'language',
            dataIndex: 'language',
            key: 'language',
            align: 'center',
        },
        {
            title: 'version',
            dataIndex: 'versionDesc',
            key: 'versionDesc',
            align: 'center',
        },
    ];

    return (
        <>
            {msgContextHolder}
            <div className="container-fluid" id="deployHistoryList">
                <Form
                    form={form}
                    layout="inline"
                    onFinish={onFinish}
                    style={{marginBottom: 20}}
                >
                    <Form.Item label="TOPIC" name="selectedTopic"
                               rules={[{required: true, message: 'Please select a topic!'}]}>
                        <Select
                            showSearch
                            placeholder="Select a topic"
                            style={{width: 300}}
                            optionFilterProp="children"
                            filterOption={(input, option) =>
                                option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                            }
                        >
                            {allTopicList.map((topic) => (
                                <Option key={topic} value={topic}>{topic}</Option>
                            ))}
                        </Select>
                    </Form.Item>
                    <Form.Item label="PRODUCER_GROUP" name="producerGroup"
                               rules={[{required: true, message: 'Please input producer group!'}]}>
                        <Input style={{width: 300}}/>
                    </Form.Item>
                    <Form.Item>
                        <Button type="primary" htmlType="submit" loading={loading}>
                            <span className="glyphicon glyphicon-search"></span> SEARCH
                        </Button>
                    </Form.Item>
                </Form>
                <Table
                    dataSource={connectionList}
                    columns={columns}
                    rowKey="clientId"
                    pagination={false}
                    bordered
                />
            </div>
        </>

    );
};

export default ProducerConnectionList;
