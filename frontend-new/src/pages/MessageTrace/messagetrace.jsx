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
import {Button, Form, Input, notification, Select, Spin, Table, Tabs, Typography} from 'antd';
import moment from 'moment';
import {SearchOutlined} from '@ant-design/icons';
import {useLanguage} from '../../i18n/LanguageContext';
import MessageTraceDetailViewDialog from "../../components/MessageTraceDetailViewDialog";
import {remoteApi} from '../../api/remoteApi/remoteApi'; // Import the remoteApi

const {TabPane} = Tabs;
const {Option} = Select;
const {Text, Paragraph} = Typography;

const MessageTraceQueryPage = () => {
    const {t} = useLanguage();
    const [activeTab, setActiveTab] = useState('messageKey');
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);

    // 轨迹主题选择
    const [allTraceTopicList, setAllTraceTopicList] = useState([]);
    const [selectedTraceTopic, setSelectedTraceTopic] = useState(null); // Initialize as null or a default trace topic if applicable

    // Topic 查询状态
    const [allTopicList, setAllTopicList] = useState([]);
    const [selectedTopic, setSelectedTopic] = useState(null);
    const [key, setKey] = useState('');
    const [queryMessageByTopicAndKeyResult, setQueryMessageByTopicAndKeyResult] = useState([]);

    // Message ID 查询状态
    const [messageId, setMessageId] = useState('');
    const [queryMessageByMessageIdResult, setQueryMessageByMessageIdResult] = useState([]);

    // State for MessageTraceDetailViewDialog
    const [isTraceDetailViewOpen, setIsTraceDetailViewOpen] = useState(false);
    const [traceDetailData, setTraceDetailData] = useState(null);
    const [notificationApi, notificationContextHolder] = notification.useNotification();

    useEffect(() => {
        const fetchTopics = async () => {
            setLoading(true);
            try {
                const resp = await remoteApi.queryTopic(true);

                if (resp.status === 0) {
                    const topics = resp.data.topicList.sort();
                    setAllTopicList(topics);

                    const traceTopics = topics.filter(topic =>
                        !topic.startsWith('%RETRY%') && !topic.startsWith('%DLQ%')
                    );
                    setAllTraceTopicList(traceTopics);
                    // Optionally set a default trace topic if available, e.g., 'RMQ_SYS_TRACE_TOPIC'
                    if (traceTopics.includes('RMQ_SYS_TRACE_TOPIC')) {
                        setSelectedTraceTopic('RMQ_SYS_TRACE_TOPIC');
                    } else if (traceTopics.length > 0) {
                        setSelectedTraceTopic(traceTopics[0]); // Select the first one if no default
                    }
                } else {
                    notificationApi.error({
                        message: t.ERROR,
                        description: resp.errMsg || t.QUERY_FAILED,
                    });
                }
            } catch (error) {
                notificationApi.error({
                    message: t.ERROR,
                    description: error.message || t.QUERY_FAILED,
                });
            } finally {
                setLoading(false);
            }
        };

        fetchTopics();
    }, [t]);

    const queryMessageByTopicAndKey = async () => {
        if (!selectedTopic || !key) {
            notificationApi.warning({
                message: t.WARNING,
                description: t.TOPIC_AND_KEY_REQUIRED,
            });
            return;
        }
        setLoading(true);

        try {
            const data = await remoteApi.queryMessageByTopicAndKey(selectedTopic, key);
            if (data.status === 0) {
                setQueryMessageByTopicAndKeyResult(data.data);
                if (data.data.length === 0) {
                    notificationApi.info({
                        message: t.NO_RESULT,
                        description: t.NO_MATCH_RESULT,
                    });
                }
            } else {
                notificationApi.error({
                    message: t.ERROR,
                    description: data.errMsg || t.QUERY_FAILED,
                });
                setQueryMessageByTopicAndKeyResult([]); // Clear previous results on error
            }
        } catch (error) {
            notificationApi.error({
                message: t.ERROR,
                description: error.message || t.QUERY_FAILED,
            });
            setQueryMessageByTopicAndKeyResult([]); // Clear previous results on error
        } finally {
            setLoading(false);
        }
    };

    const queryMessageByMessageId = async (msgIdToQuery, topicToQuery) => {
        if (!msgIdToQuery) {
            notificationApi.warning({
                message: t.WARNING,
                description: t.MESSAGE_ID_REQUIRED,
            });
            return;
        }
        setLoading(true);

        try {
            const res = await remoteApi.queryMessageByMessageId(msgIdToQuery, topicToQuery);
            if (res.status === 0) {
                // 确保 data.data.messageView 存在，并将其包装成数组
                setQueryMessageByMessageIdResult(res.data && res.data.messageView ? [res.data.messageView] : []);
            } else {
                notificationApi.error({
                    message: t.ERROR,
                    description: res.errMsg || t.QUERY_FAILED,
                });
                setQueryMessageByMessageIdResult([]); // 清除错误时的旧数据
            }
        } catch (error) {
            notificationApi.error({
                message: t.ERROR,
                description: error.message || t.QUERY_FAILED,
            });
            setQueryMessageByMessageIdResult([]); // 清除错误时的旧数据
        } finally {
            setLoading(false);
        }
    };

    const queryMessageTraceByMessageId = async (msgId, traceTopic) => {
        if (!msgId) {
            notificationApi.warning({
                message: t.WARNING,
                description: t.MESSAGE_ID_REQUIRED,
            });
            return;
        }
        setLoading(true);

        try {
            const data = await remoteApi.queryMessageTraceByMessageId(msgId, traceTopic || 'RMQ_SYS_TRACE_TOPIC');
            if (data.status === 0) {
                setTraceDetailData(data.data);
                setIsTraceDetailViewOpen(true);
            } else {
                notificationApi.error({
                    message: t.ERROR,
                    description: data.errMsg || t.QUERY_FAILED,
                });
                setTraceDetailData(null); // Clear previous trace data on error
                setIsTraceDetailViewOpen(false); // Do not open dialog if data is not available
            }
        } catch (error) {
            notificationApi.error({
                message: t.ERROR,
                description: error.message || t.QUERY_FAILED,
            });
            setTraceDetailData(null); // Clear previous trace data on error
            setIsTraceDetailViewOpen(false); // Do not open dialog if data is not available
        } finally {
            setLoading(false);
        }
    };

    const handleCloseTraceDetailView = () => {
        setIsTraceDetailViewOpen(false);
        setTraceDetailData(null);
    };

    const keyColumns = [
        {title: 'Message ID', dataIndex: 'msgId', key: 'msgId', align: 'center'},
        {title: 'Tag', dataIndex: ['properties', 'TAGS'], key: 'tags', align: 'center'},
        {title: 'Message Key', dataIndex: ['properties', 'KEYS'], key: 'keys', align: 'center'},
        {
            title: 'StoreTime',
            dataIndex: 'storeTimestamp',
            key: 'storeTimestamp',
            align: 'center',
            render: (text) => moment(text).format('YYYY-MM-DD HH:mm:ss'),
        },
        {
            title: 'Operation',
            key: 'operation',
            align: 'center',
            render: (_, record) => (
                <Button type="primary" size="small"
                        onClick={() => queryMessageTraceByMessageId(record.msgId, selectedTraceTopic)}>
                    {t.MESSAGE_TRACE_DETAIL}
                </Button>
            ),
        },
    ];

    const messageIdColumns = [
        {title: 'Message ID', dataIndex: 'msgId', key: 'msgId', align: 'center'},
        // 注意：这里的 dataIndex 直接指向了 messageView 内部的属性
        {title: 'Tag', dataIndex: ['properties', 'TAGS'], key: 'tags', align: 'center'},
        {title: 'Message Key', dataIndex: ['properties', 'KEYS'], key: 'keys', align: 'center'},
        {
            title: 'StoreTime',
            dataIndex: 'storeTimestamp',
            key: 'storeTimestamp',
            align: 'center',
            render: (text) => moment(text).format('YYYY-MM-DD HH:mm:ss'),
        },
        {
            title: 'Operation',
            key: 'operation',
            align: 'center',
            render: (_, record) => (
                <Button type="primary" size="small"
                        onClick={() => queryMessageTraceByMessageId(record.msgId, selectedTraceTopic)}>
                    {t.MESSAGE_TRACE_DETAIL}
                </Button>
            ),
        },
    ];

    return (
        <>
            {notificationContextHolder}
            <div style={{padding: '20px'}}>
                <Spin spinning={loading} tip="加载中...">
                    <div style={{marginBottom: '20px', borderBottom: '1px solid #f0f0f0', paddingBottom: '15px'}}>
                        <Form layout="inline">
                            <Form.Item label={<Text strong>{t.TRACE_TOPIC}:</Text>}>
                                <Select
                                    showSearch
                                    style={{minWidth: 300}}
                                    placeholder={t.SELECT_TRACE_TOPIC_PLACEHOLDER}
                                    value={selectedTraceTopic}
                                    onChange={setSelectedTraceTopic}
                                    filterOption={(input, option) =>
                                        option.children && option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                                    }
                                >
                                    {allTraceTopicList.map(topic => (
                                        <Option key={topic} value={topic}>{topic}</Option>
                                    ))}
                                </Select>
                            </Form.Item>
                            <Text type="secondary" style={{marginLeft: 10}}>({t.TRACE_TOPIC_HINT})</Text>
                        </Form>
                    </div>

                    <Tabs activeKey={activeTab} onChange={setActiveTab} centered>
                        <TabPane tab="Message Key" key="messageKey">
                            <h5 style={{margin: '15px 0'}}>{t.ONLY_RETURN_64_MESSAGES}</h5>
                            <div style={{padding: '20px', minHeight: '600px'}}>
                                <Form layout="inline" form={form} style={{marginBottom: '20px'}}>
                                    <Form.Item label="Topic:">
                                        <Select
                                            showSearch
                                            style={{width: 300}}
                                            placeholder={t.SELECT_TOPIC_PLACEHOLDER}
                                            value={selectedTopic}
                                            onChange={setSelectedTopic}
                                            required
                                            filterOption={(input, option) =>
                                                option.children && option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                                            }
                                        >
                                            <Option value="">{t.SELECT_TOPIC_PLACEHOLDER}</Option>
                                            {allTopicList.map(topic => (
                                                <Option key={topic} value={topic}>{topic}</Option>
                                            ))}
                                        </Select>
                                    </Form.Item>
                                    <Form.Item label="Key:">
                                        <Input
                                            style={{width: 450}}
                                            value={key}
                                            onChange={(e) => setKey(e.target.value)}
                                            required
                                        />
                                    </Form.Item>
                                    <Form.Item>
                                        <Button type="primary" icon={<SearchOutlined/>}
                                                onClick={queryMessageByTopicAndKey}>
                                            {t.SEARCH}
                                        </Button>
                                    </Form.Item>
                                </Form>
                                <Table
                                    columns={keyColumns}
                                    dataSource={queryMessageByTopicAndKeyResult}
                                    rowKey="msgId"
                                    bordered
                                    pagination={false}
                                    locale={{emptyText: t.NO_MATCH_RESULT}}
                                />
                            </div>
                        </TabPane>
                        <TabPane tab="Message ID" key="messageId">
                            <h5 style={{margin: '15px 0'}}>{t.MESSAGE_ID_TOPIC_HINT}</h5>
                            <div style={{padding: '20px', minHeight: '600px'}}>
                                <Form layout="inline" style={{marginBottom: '20px'}}>
                                    <Form.Item label="Topic:">
                                        <Select
                                            showSearch
                                            style={{width: 300}}
                                            placeholder={t.SELECT_TOPIC_PLACEHOLDER}
                                            value={selectedTopic}
                                            onChange={setSelectedTopic}
                                            required
                                            filterOption={(input, option) => {
                                                if (option.children && typeof option.children === 'string') {
                                                    return option.children.toLowerCase().includes(input.toLowerCase());
                                                }
                                                return false;
                                            }}
                                        >
                                            <Option value="">{t.SELECT_TOPIC_PLACEHOLDER}</Option>
                                            {allTopicList.map(topic => (
                                                <Option key={topic} value={topic}>{topic}</Option>
                                            ))}
                                        </Select>
                                    </Form.Item>
                                    <Form.Item label="MessageId:">
                                        <Input
                                            style={{width: 450}}
                                            value={messageId}
                                            onChange={(e) => setMessageId(e.target.value)}
                                            required
                                        />
                                    </Form.Item>
                                    <Form.Item>
                                        <Button type="primary" icon={<SearchOutlined/>}
                                                onClick={() => queryMessageByMessageId(messageId, selectedTopic)}>
                                            {t.SEARCH}
                                        </Button>
                                    </Form.Item>
                                </Form>
                                <Table
                                    columns={messageIdColumns}
                                    dataSource={queryMessageByMessageIdResult}
                                    rowKey="msgId"
                                    bordered
                                    pagination={false}
                                    locale={{emptyText: t.NO_MATCH_RESULT}}
                                />
                            </div>
                        </TabPane>
                    </Tabs>
                </Spin>

                {/* MessageTraceDetailViewDialog as a child component */}
                {isTraceDetailViewOpen && traceDetailData && (
                    <div style={{
                        position: 'fixed',
                        top: 0,
                        left: 0,
                        right: 0,
                        bottom: 0,
                        backgroundColor: 'rgba(0, 0, 0, 0.5)',
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        zIndex: 1000,
                    }}>
                        <div style={{
                            backgroundColor: '#fff',
                            padding: '20px',
                            borderRadius: '8px',
                            width: '80%',
                            maxHeight: '90%',
                            overflowY: 'auto',
                            position: 'relative'
                        }}>
                            <Typography.Title level={4}
                                              style={{marginBottom: '20px'}}>{t.MESSAGE_TRACE_DETAIL}</Typography.Title>
                            <Button
                                onClick={handleCloseTraceDetailView}
                                style={{
                                    position: 'absolute',
                                    top: '20px',
                                    right: '20px',
                                }}
                            >
                                {t.CLOSE}
                            </Button>
                            <MessageTraceDetailViewDialog
                                ngDialogData={traceDetailData}
                            />
                        </div>
                    </div>
                )}
            </div>
        </>

    );
};

export default MessageTraceQueryPage;
