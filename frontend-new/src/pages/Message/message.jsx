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

import React, {useCallback, useEffect, useState} from 'react';
import {Button, DatePicker, Form, Input, notification, Select, Spin, Table, Tabs, Typography} from 'antd';
import moment from 'moment';
import {SearchOutlined} from '@ant-design/icons';
import {useLanguage} from '../../i18n/LanguageContext';
import MessageDetailViewDialog from "../../components/MessageDetailViewDialog"; // Keep this path
import {remoteApi} from '../../api/remoteApi/remoteApi'; // Keep this path

const {TabPane} = Tabs;
const {Option} = Select;
const {Text, Paragraph} = Typography;

const MessageQueryPage = () => {
    const {t} = useLanguage();
    const [activeTab, setActiveTab] = useState('topic');
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);

    const [allTopicList, setAllTopicList] = useState([]);
    const [selectedTopic, setSelectedTopic] = useState(null);
    const [timepickerBegin, setTimepickerBegin] = useState(moment().subtract(1, 'hour'));
    const [timepickerEnd, setTimepickerEnd] = useState(moment());
    const [messageShowList, setMessageShowList] = useState([]);
    const [paginationConf, setPaginationConf] = useState({
        current: 1,
        pageSize: 10,
        total: 0,
    });
    const [taskId, setTaskId] = useState("");

    // Message Key 查询状态
    const [key, setKey] = useState('');
    const [queryMessageByTopicAndKeyResult, setQueryMessageByTopicAndKeyResult] = useState([]);

    // Message ID 查询状态
    const [messageId, setMessageId] = useState('');

    // State for Message Detail Dialog
    const [isMessageDetailModalVisible, setIsMessageDetailModalVisible] = useState(false);
    const [currentMessageIdForDetail, setCurrentMessageIdForDetail] = useState(null);
    const [currentTopicForDetail, setCurrentTopicForDetail] = useState(null);
    const [notificationApi, notificationContextHolder] = notification.useNotification();

    const fetchAllTopics = useCallback(async () => {
        setLoading(true);
        try {
            const resp = await remoteApi.queryTopic(false);
            if (resp.status === 0) {
                setAllTopicList(resp.data.topicList.sort());
            } else {
                notificationApi.error({
                    message: t.ERROR,
                    description: resp.errMsg || t.FETCH_TOPIC_FAILED,
                });
            }
        } catch (error) {
            notificationApi.error({
                message: t.ERROR,
                description: t.FETCH_TOPIC_FAILED,
            });
            console.error("Error fetching topic list:", error);
        } finally {
            setLoading(false);
        }
    }, [t]);

    useEffect(() => {
        fetchAllTopics();
    }, [fetchAllTopics]);

    const onChangeQueryCondition = () => {
        setTaskId("");
        setPaginationConf(prev => ({
            ...prev,
            current: 1,
            total: 0,
        }));
    };

    const queryMessagePageByTopic = async (page = paginationConf.current, pageSize = paginationConf.pageSize) => {
        if (!selectedTopic) {
            notificationApi.warning({
                message: t.WARNING,
                description: t.PLEASE_SELECT_TOPIC,
            });
            return;
        }
        if (timepickerEnd.valueOf() < timepickerBegin.valueOf()) {
            notificationApi.error({message: t.ERROR, description: t.END_TIME_EARLIER_THAN_BEGIN_TIME});
            return;
        }

        setLoading(true);
        try {
            const resp = await remoteApi.queryMessagePageByTopic(
                selectedTopic,
                timepickerBegin.valueOf(),
                timepickerEnd.valueOf(),
                page,
                pageSize,
                taskId
            );

            if (resp.status === 0) {
                setMessageShowList(resp.data.page.content);
                setPaginationConf(prev => ({
                    ...prev,
                    current: resp.data.page.number + 1,
                    total: resp.data.page.totalElements,
                    pageSize: pageSize,
                }));
                setTaskId(resp.data.taskId);

                if (resp.data.page.content.length === 0) {
                    notificationApi.info({
                        message: t.NO_RESULT,
                        description: t.NO_MATCH_RESULT,
                    });
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
                description: t.QUERY_FAILED,
            });
        } finally {
            setLoading(false);
        }
    };

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
            const resp = await remoteApi.queryMessageByTopicAndKey(selectedTopic, key);
            if (resp.status === 0) {
                setQueryMessageByTopicAndKeyResult(resp.data);
                if (resp.data.length === 0) {
                    notificationApi.info({
                        message: t.NO_RESULT,
                        description: t.NO_MATCH_RESULT,
                    });
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
                description: t.QUERY_FAILED,
            });
        } finally {
            setLoading(false);
        }
    };

    // Updated to open the dialog
    const showMessageDetail = (msgIdToQuery, topicToQuery) => {
        if (!msgIdToQuery) {
            notificationApi.warning({
                message: t.WARNING,
                description: t.MESSAGE_ID_REQUIRED,
            });
            return;
        }
        setCurrentMessageIdForDetail(msgIdToQuery);
        setCurrentTopicForDetail(topicToQuery);
        setIsMessageDetailModalVisible(true);
    };

    const handleCloseMessageDetailModal = () => {
        setIsMessageDetailModalVisible(false);
        setCurrentMessageIdForDetail(null);
        setCurrentTopicForDetail(null);
    };

    const handleResendMessage = async (messageView, consumerGroup) => {
        setLoading(true); // Set loading for the main page as well, as the dialog itself can't control it
        let topicToResend = messageView.topic;
        let msgIdToResend = messageView.msgId;


        if (topicToResend.startsWith('%DLQ%')) {
            if (messageView.properties && messageView.properties.hasOwnProperty("RETRY_TOPIC")) {
                topicToResend = messageView.properties.RETRY_TOPIC;
            }
            if (messageView.properties && messageView.properties.hasOwnProperty("ORIGIN_MESSAGE_ID")) {
                msgIdToResend = messageView.properties.ORIGIN_MESSAGE_ID;
            }
        }

        try {
            const resp = await remoteApi.resendMessageDirectly(msgIdToResend, consumerGroup, topicToResend);
            if (resp.status === 0) {
                notificationApi.success({
                    message: t.SUCCESS,
                    description: t.RESEND_SUCCESS,
                });
            } else {
                notificationApi.error({
                    message: t.ERROR,
                    description: resp.errMsg || t.RESEND_FAILED,
                });
            }
        } catch (error) {
            notificationApi.error({
                message: t.ERROR,
                description: t.RESEND_FAILED,
            });
        } finally {
            setLoading(false);
            // Optionally, you might want to refresh the message detail after resend
            // or close the modal if resend was successful and you don't need to see details immediately.
            // For now, we'll keep the modal open and let the user close it.
        }
    };

    const topicColumns = [
        {
            title: 'Message ID', dataIndex: 'msgId', key: 'msgId', align: 'center',
            render: (text) => <Text copyable>{text}</Text>
        },
        {title: 'Tag', dataIndex: ['properties', 'TAGS'], key: 'tags', align: 'center'},
        {title: 'Key', dataIndex: ['properties', 'KEYS'], key: 'keys', align: 'center'},
        {
            title: 'StoreTime',
            dataIndex: 'storeTimestamp',
            key: 'storeTimestamp',
            align: 'center',
            render: (text) => moment(text).format("YYYY-MM-DD HH:mm:ss"),
        },
        {
            title: 'Operation',
            key: 'operation',
            align: 'center',
            render: (_, record) => (
                <Button type="primary" size="small" onClick={() => showMessageDetail(record.msgId, record.topic)}>
                    {t.MESSAGE_DETAIL}
                </Button>
            ),
        },
    ];

    const keyColumns = [
        {
            title: 'Message ID', dataIndex: 'msgId', key: 'msgId', align: 'center',
            render: (text) => <Text copyable>{text}</Text>
        },
        {title: 'Tag', dataIndex: ['properties', 'TAGS'], key: 'tags', align: 'center'},
        {title: 'Key', dataIndex: ['properties', 'KEYS'], key: 'keys', align: 'center'},
        {
            title: 'StoreTime',
            dataIndex: 'storeTimestamp',
            key: 'storeTimestamp',
            align: 'center',
            render: (text) => moment(text).format("YYYY-MM-DD HH:mm:ss"),
        },
        {
            title: 'Operation',
            key: 'operation',
            align: 'center',
            render: (_, record) => (
                <Button type="primary" size="small" onClick={() => showMessageDetail(record.msgId, record.topic)}>
                    {t.MESSAGE_DETAIL}
                </Button>
            ),
        },
    ];

    return (
        <>
            {notificationContextHolder}
            <div style={{padding: '20px'}}>
                <Spin spinning={loading} tip={t.LOADING_DATA}>
                    <Tabs activeKey={activeTab} onChange={setActiveTab} centered>
                        <TabPane tab="Topic" key="topic">
                            <h5 style={{margin: '15px 0'}}>{t.TOTAL_MESSAGES}</h5>
                            <div style={{padding: '20px', minHeight: '600px'}}>
                                <Form layout="inline" form={form} style={{marginBottom: '20px'}}>
                                    <Form.Item label={t.TOPIC}>
                                        <Select
                                            showSearch
                                            style={{width: 300}}
                                            placeholder={t.SELECT_TOPIC_PLACEHOLDER}
                                            value={selectedTopic}
                                            onChange={(value) => {
                                                setSelectedTopic(value);
                                                onChangeQueryCondition();
                                            }}
                                            filterOption={(input, option) =>
                                                option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                                            }
                                        >
                                            {allTopicList.map(topic => (
                                                <Option key={topic} value={topic}>{topic}</Option>
                                            ))}
                                        </Select>
                                    </Form.Item>
                                    <Form.Item label={t.BEGIN}>
                                        <DatePicker
                                            showTime
                                            format="YYYY-MM-DD HH:mm:ss"
                                            value={timepickerBegin}
                                            onChange={(date) => {
                                                setTimepickerBegin(date);
                                                onChangeQueryCondition();
                                            }}
                                        />
                                    </Form.Item>
                                    <Form.Item label={t.END}>
                                        <DatePicker
                                            showTime
                                            format="YYYY-MM-DD HH:mm:ss"
                                            value={timepickerEnd}
                                            onChange={(date) => {
                                                setTimepickerEnd(date);
                                                onChangeQueryCondition();
                                            }}
                                        />
                                    </Form.Item>
                                    <Form.Item>
                                        <Button type="primary" icon={<SearchOutlined/>}
                                                onClick={() => queryMessagePageByTopic()}>
                                            {t.SEARCH}
                                        </Button>
                                    </Form.Item>
                                </Form>
                                <Table
                                    columns={topicColumns}
                                    dataSource={messageShowList}
                                    rowKey="msgId"
                                    bordered
                                    pagination={{
                                        current: paginationConf.current,
                                        pageSize: paginationConf.pageSize,
                                        total: paginationConf.total,
                                        onChange: (page, pageSize) => queryMessagePageByTopic(page, pageSize),
                                    }}
                                    locale={{emptyText: t.NO_MATCH_RESULT}}
                                />
                            </div>
                        </TabPane>
                        <TabPane tab="Message Key" key="messageKey">
                            <h5 style={{margin: '15px 0'}}>{t.ONLY_RETURN_64_MESSAGES}</h5>
                            <div style={{padding: '20px', minHeight: '600px'}}>
                                <Form layout="inline" style={{marginBottom: '20px'}}>
                                    <Form.Item label="Topic:">
                                        <Select
                                            showSearch
                                            style={{width: 300}}
                                            placeholder={t.SELECT_TOPIC_PLACEHOLDER}
                                            value={selectedTopic}
                                            onChange={setSelectedTopic}
                                            filterOption={(input, option) =>
                                                option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                                            }
                                        >
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
                            <h5 style={{margin: '15px 0'}}>
                                {t.MESSAGE_ID_TOPIC_HINT}
                            </h5>
                            <div style={{padding: '20px', minHeight: '600px'}}>
                                <Form layout="inline" style={{marginBottom: '20px'}}>
                                    <Form.Item label="Topic:">
                                        <Select
                                            showSearch
                                            style={{width: 300}}
                                            placeholder={t.SELECT_TOPIC_PLACEHOLDER}
                                            value={selectedTopic}
                                            onChange={setSelectedTopic}
                                            filterOption={(input, option) =>
                                                option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                                            }
                                        >
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
                                        />
                                    </Form.Item>
                                    <Form.Item>
                                        <Button type="primary" icon={<SearchOutlined/>}
                                                onClick={() => showMessageDetail(messageId, selectedTopic)}>
                                            {t.SEARCH}
                                        </Button>
                                    </Form.Item>
                                </Form>
                            </div>
                        </TabPane>
                    </Tabs>
                </Spin>

                {/* Message Detail Dialog Component */}
                <MessageDetailViewDialog
                    visible={isMessageDetailModalVisible}
                    onCancel={handleCloseMessageDetailModal}
                    messageId={currentMessageIdForDetail}
                    topic={currentTopicForDetail}
                    onResendMessage={handleResendMessage} // Pass the resend function
                />
            </div>
        </>

    );
};

export default MessageQueryPage;
