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
import {
    Button,
    Checkbox,
    DatePicker,
    Form,
    Input,
    Modal,
    notification,
    Select,
    Spin,
    Table,
    Tabs,
    Typography
} from 'antd';
import moment from 'moment';
import {ExportOutlined, SearchOutlined, SendOutlined} from '@ant-design/icons';
import DlqMessageDetailViewDialog from "../../components/DlqMessageDetailViewDialog"; // Ensure this path is correct
import {useLanguage} from '../../i18n/LanguageContext'; // Ensure this path is correct
import {remoteApi} from '../../api/remoteApi/remoteApi'; // Adjust the path to your remoteApi.js file

const {TabPane} = Tabs;
const {Option} = Select;
const {Text, Paragraph} = Typography;

const SYS_GROUP_TOPIC_PREFIX = "CID_RMQ_SYS_"; // Define this constant as in Angular
const DLQ_GROUP_TOPIC_PREFIX = "%DLQ%"; // Define this constant

const DlqMessageQueryPage = () => {
    const {t} = useLanguage();
    const [activeTab, setActiveTab] = useState('consumer');
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);

    // Consumer 查询状态
    const [allConsumerGroupList, setAllConsumerGroupList] = useState([]);
    const [selectedConsumerGroup, setSelectedConsumerGroup] = useState(null);
    const [timepickerBegin, setTimepickerBegin] = useState(moment().subtract(3, 'hour')); // 默认三小时前
    const [timepickerEnd, setTimepickerEnd] = useState(moment());
    const [messageShowList, setMessageShowList] = useState([]);
    const [paginationConf, setPaginationConf] = useState({
        current: 1,
        pageSize: 20, // Adjusted to 20 as per Angular code
        total: 0,
    });
    const [checkedAll, setCheckedAll] = useState(false);
    const [selectedMessageIds, setSelectedMessageIds] = useState(new Set()); // Stores msgId for selected messages
    const [messageCheckedList, setMessageCheckedList] = useState([]); // Stores full message objects for checked items
    const [taskId, setTaskId] = useState("");


    // Message ID 查询状态
    const [messageId, setMessageId] = useState('');
    const [queryDlqMessageByMessageIdResult, setQueryDlqMessageByMessageIdResult] = useState([]);
    const [modalApi, modalContextHolder] = Modal.useModal();
    const [notificationApi, notificationContextHolder] = notification.useNotification();
    // Fetch consumer group list on component mount
    useEffect(() => {
        const fetchConsumerGroups = async () => {
            setLoading(true);
            const resp = await remoteApi.queryConsumerGroupList(false);
            if (resp.status === 0) {
                const filteredGroups = resp.data
                    .filter(consumerGroup => !consumerGroup.group.startsWith(SYS_GROUP_TOPIC_PREFIX))
                    .map(consumerGroup => consumerGroup.group)
                    .sort();
                setAllConsumerGroupList(filteredGroups);
            } else {
                notificationApi.error({message: t.ERROR, description: resp.errMsg});
            }
            setLoading(false);
        };
        fetchConsumerGroups();
    }, [t]);

    // Effect to manage batch buttons' disabled state
    useEffect(() => {
        const batchResendBtn = document.getElementById('batchResendBtn');
        const batchExportBtn = document.getElementById('batchExportBtn');
        if (selectedMessageIds.size > 0) {
            batchResendBtn?.classList.remove('disabled');
            batchExportBtn?.classList.remove('disabled');
        } else {
            batchResendBtn?.classList.add('disabled');
            batchExportBtn?.classList.add('disabled');
        }
    }, [selectedMessageIds]);

    const onChangeQueryCondition = useCallback(() => {
        // console.log("查询条件改变");
        setTaskId(""); // Reset taskId when query conditions change
        setPaginationConf(prev => ({...prev, currentPage: 1, totalItems: 0}));
    }, []);

    const queryDlqMessageByConsumerGroup = useCallback(async (page = paginationConf.current, pageSize = paginationConf.pageSize) => {
        if (!selectedConsumerGroup) {
            notificationApi.warning({
                message: t.WARNING,
                description: t.PLEASE_SELECT_CONSUMER_GROUP,
            });
            return;
        }
        if (moment(timepickerEnd).valueOf() < moment(timepickerBegin).valueOf()) {
            notificationApi.error({message: t.END_TIME_LATER_THAN_BEGIN_TIME, delay: 2000});
            return;
        }

        setLoading(true);
        // console.log("根据消费者组查询DLQ消息:", { selectedConsumerGroup, timepickerBegin, timepickerEnd, page, pageSize, taskId });
        try {
            const resp = await remoteApi.queryDlqMessageByConsumerGroup(
                selectedConsumerGroup,
                moment(timepickerBegin).valueOf(),
                moment(timepickerEnd).valueOf(),
                page,
                pageSize,
                taskId
            );

            if (resp.status === 0) {
                const fetchedMessages = resp.data.page.content.map(msg => ({...msg, checked: false}));
                setMessageShowList(fetchedMessages);
                if (fetchedMessages.length === 0) {
                    notificationApi.info({
                        message: t.NO_RESULT,
                        description: t.NO_MATCH_RESULT,
                    });
                }
                setPaginationConf(prev => ({
                    ...prev,
                    current: resp.data.page.number + 1,
                    pageSize: pageSize,
                    total: resp.data.page.totalElements,
                }));
                setTaskId(resp.data.taskId);
                setSelectedMessageIds(new Set()); // Reset选中项
                setCheckedAll(false); // Reset全选状态
                setMessageCheckedList([]); // Clear checked list
            } else {
                notificationApi.error({
                    message: t.ERROR,
                    description: resp.errMsg,
                });
            }
        } catch (error) {
            notificationApi.error({
                message: t.ERROR,
                description: t.QUERY_FAILED,
            });
            console.error("查询失败:", error);
        } finally {
            setLoading(false);
        }
    }, [selectedConsumerGroup, timepickerBegin, timepickerEnd, paginationConf.current, paginationConf.pageSize, taskId, t]);

    const queryDlqMessageByMessageId = useCallback(async () => {
        if (!messageId || !selectedConsumerGroup) {
            notificationApi.warning({
                message: t.WARNING,
                description: t.MESSAGE_ID_AND_CONSUMER_GROUP_REQUIRED,
            });
            return;
        }
        setLoading(true);
        try {
            const resp = await remoteApi.viewMessage(messageId, DLQ_GROUP_TOPIC_PREFIX + selectedConsumerGroup);
            if (resp.status === 0) {
                setQueryDlqMessageByMessageIdResult(resp.data ? [resp.data] : []);
                if (!resp.data) {
                    notificationApi.info({
                        message: t.NO_RESULT,
                        description: t.NO_MATCH_RESULT,
                    });
                }
            } else {
                notificationApi.error({
                    message: t.ERROR,
                    description: resp.errMsg,
                });
            }
        } catch (error) {
            notificationApi.error({
                message: t.ERROR,
                description: t.QUERY_FAILED,
            });
            console.error("查询失败:", error);
        } finally {
            setLoading(false);
        }
    }, [messageId, selectedConsumerGroup, t]);

    const queryDlqMessageDetail = useCallback(async (msgId, consumerGroup) => {
        setLoading(true);
        // console.log(`查询DLQ消息详情: ${msgId}, 消费者组: ${consumerGroup}`);
        try {
            const resp = await remoteApi.viewMessage(msgId, DLQ_GROUP_TOPIC_PREFIX + consumerGroup);
            if (resp.status === 0) {
                modalApi.info({
                    title: t.MESSAGE_DETAIL,
                    width: 800,
                    content: (
                        <DlqMessageDetailViewDialog
                            ngDialogData={{messageView: resp.data}}
                        />
                    ),
                    onOk: () => {
                    },
                    okText: t.CLOSE,
                });
            } else {
                notificationApi.error({
                    message: t.ERROR,
                    description: resp.errMsg,
                });
            }
        } catch (error) {
            notificationApi.error({
                message: t.ERROR,
                description: t.QUERY_FAILED,
            });
            console.error("查询失败:", error);
        } finally {
            setLoading(false);
        }
    }, [t]);

    const resendDlqMessage = useCallback(async (messageView, consumerGroup) => {
        setLoading(true);
        const topic = messageView.properties.RETRY_TOPIC;
        const msgId = messageView.properties.ORIGIN_MESSAGE_ID;
        // console.log(`重发DLQ消息: MsgId=${msgId}, Topic=${topic}, 消费者组=${consumerGroup}`);
        try {
            const resp = await remoteApi.resendDlqMessage(msgId, consumerGroup, topic);
            if (resp.status === 0) {
                notificationApi.success({
                    message: t.SUCCESS,
                    description: t.RESEND_SUCCESS,
                });
                modalApi.info({
                    title: t.RESULT,
                    content: resp.data,
                });
                // Refresh list
                queryDlqMessageByConsumerGroup(paginationConf.current, paginationConf.pageSize);
            } else {
                notificationApi.error({
                    message: t.ERROR,
                    description: resp.errMsg,
                });
                modalApi.error({
                    title: t.RESULT,
                    content: resp.errMsg,
                });
            }
        } catch (error) {
            notificationApi.error({
                message: t.ERROR,
                description: t.RESEND_FAILED,
            });
            console.error("重发失败:", error);
        } finally {
            setLoading(false);
        }
    }, [paginationConf.current, paginationConf.pageSize, queryDlqMessageByConsumerGroup, t]);

    const exportDlqMessage = useCallback(async (msgId, consumerGroup) => {
        setLoading(true);
        // console.log(`导出DLQ消息: MsgId=${msgId}, 消费者组=${consumerGroup}`);
        try {
            const resp = await remoteApi.exportDlqMessage(msgId, consumerGroup);
            if (resp.status === 0) {
                notificationApi.success({
                    message: t.SUCCESS,
                    description: t.EXPORT_SUCCESS,
                });
                // The actual file download is handled within remoteApi.js
            } else {
                notificationApi.error({
                    message: t.ERROR,
                    description: resp.errMsg,
                });
            }
        } catch (error) {
            notificationApi.error({
                message: t.ERROR,
                description: t.EXPORT_FAILED,
            });
            console.error("导出失败:", error);
        } finally {
            setLoading(false);
        }
    }, [t]);

    const batchResendDlqMessage = useCallback(async () => {
        if (selectedMessageIds.size === 0) {
            notificationApi.warning({
                message: t.WARNING,
                description: t.PLEASE_SELECT_MESSAGE_TO_RESEND,
            });
            return;
        }
        setLoading(true);
        const messagesToResend = messageCheckedList.map(message => ({
            topic: message.properties.RETRY_TOPIC,
            msgId: message.properties.ORIGIN_MESSAGE_ID,
            consumerGroup: selectedConsumerGroup,
        }));
        try {
            const resp = await remoteApi.batchResendDlqMessage(messagesToResend);
            if (resp.status === 0) {
                notificationApi.success({
                    message: t.SUCCESS,
                    description: t.BATCH_RESEND_SUCCESS,
                });
                modalApi.info({
                    title: t.RESULT,
                    content: resp.data,
                });
                // Refresh list and reset selected state
                queryDlqMessageByConsumerGroup(paginationConf.current, paginationConf.pageSize);
                setSelectedMessageIds(new Set());
                setCheckedAll(false);
                setMessageCheckedList([]);
            } else {
                notificationApi.error({
                    message: t.ERROR,
                    description: resp.errMsg,
                });
                modalApi.error({
                    title: t.RESULT,
                    content: resp.errMsg,
                });
            }
        } catch (error) {
            notificationApi.error({
                message: t.ERROR,
                description: t.BATCH_RESEND_FAILED,
            });
        } finally {
            setLoading(false);
        }
    }, [selectedMessageIds, messageCheckedList, selectedConsumerGroup, paginationConf.current, paginationConf.pageSize, queryDlqMessageByConsumerGroup, t]);

    const batchExportDlqMessage = useCallback(async () => {
        if (selectedMessageIds.size === 0) {
            notificationApi.warning({
                message: t.WARNING,
                description: t.PLEASE_SELECT_MESSAGE_TO_EXPORT,
            });
            return;
        }
        setLoading(true);
        const messagesToExport = messageCheckedList.map(message => ({
            msgId: message.msgId,
            consumerGroup: selectedConsumerGroup,
        }));
        // console.log(`批量导出DLQ消息从 ${selectedConsumerGroup}:`, messagesToExport);
        try {
            const resp = await remoteApi.batchExportDlqMessage(messagesToExport);
            if (resp.status === 0) {
                notificationApi.success({
                    message: t.SUCCESS,
                    description: t.BATCH_EXPORT_SUCCESS,
                });
                // The actual file download is handled within remoteApi.js
                // Refresh list and reset selected state
                queryDlqMessageByConsumerGroup(paginationConf.current, paginationConf.pageSize);
                setSelectedMessageIds(new Set());
                setCheckedAll(false);
                setMessageCheckedList([]);
            } else {
                notificationApi.error({
                    message: t.ERROR,
                    description: resp.errMsg,
                });
            }
        } catch (error) {
            notificationApi.error({
                message: t.ERROR,
                description: t.BATCH_EXPORT_FAILED,
            });
            console.error("批量导出失败:", error);
        } finally {
            setLoading(false);
        }
    }, [selectedMessageIds, messageCheckedList, selectedConsumerGroup, paginationConf.current, paginationConf.pageSize, queryDlqMessageByConsumerGroup, t]);

    const handleSelectAll = (e) => {
        const checked = e.target.checked;
        setCheckedAll(checked);
        const newSelectedIds = new Set();
        const newCheckedList = [];
        const updatedList = messageShowList.map(item => {
            if (checked) {
                newSelectedIds.add(item.msgId);
                newCheckedList.push(item);
            }
            return {...item, checked};
        });
        setMessageShowList(updatedList);
        setSelectedMessageIds(newSelectedIds);
        setMessageCheckedList(newCheckedList);
    };

    const handleSelectItem = (item, checked) => {
        const newSelectedIds = new Set(selectedMessageIds);
        const newCheckedList = [...messageCheckedList];

        if (checked) {
            newSelectedIds.add(item.msgId);
            newCheckedList.push(item);
        } else {
            newSelectedIds.delete(item.msgId);
            const index = newCheckedList.findIndex(msg => msg.msgId === item.msgId);
            if (index > -1) {
                newCheckedList.splice(index, 1);
            }
        }
        setSelectedMessageIds(newSelectedIds);
        setMessageCheckedList(newCheckedList);

        // Update single item checked state in the displayed list
        const updatedList = messageShowList.map(msg =>
            msg.msgId === item.msgId ? {...msg, checked} : msg
        );
        setMessageShowList(updatedList);

        // Check if all are selected
        setCheckedAll(newSelectedIds.size === updatedList.length && updatedList.length > 0);
    };


    const consumerColumns = [
        {
            title: (
                <Checkbox
                    checked={checkedAll}
                    onChange={handleSelectAll}
                    disabled={messageShowList.length === 0}
                />
            ),
            dataIndex: 'checked',
            key: 'checkbox',
            align: 'center',
            render: (checked, record) => (
                <Checkbox
                    checked={checked}
                    onChange={(e) => handleSelectItem(record, e.target.checked)}
                />
            ),
        },
        {title: 'Message ID', dataIndex: 'msgId', key: 'msgId', align: 'center'},
        {
            title: 'Tag', dataIndex: ['properties', 'TAGS'], key: 'tags', align: 'center',
            render: (tags) => tags || '-' // Display '-' if tags are null or undefined
        },
        {
            title: 'Key', dataIndex: ['properties', 'KEYS'], key: 'keys', align: 'center',
            render: (keys) => keys || '-' // Display '-' if keys are null or undefined
        },
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
                <>
                    <Button type="primary" size="small" style={{marginRight: 8, marginBottom: 8}}
                            onClick={() => queryDlqMessageDetail(record.msgId, selectedConsumerGroup)}>
                        {t.MESSAGE_DETAIL}
                    </Button>
                    <Button type="primary" size="small" style={{marginRight: 8, marginBottom: 8}}
                            onClick={() => resendDlqMessage(record, selectedConsumerGroup)}>
                        {t.RESEND_MESSAGE}
                    </Button>
                    <Button type="primary" size="small" style={{marginBottom: 8}}
                            onClick={() => exportDlqMessage(record.msgId, selectedConsumerGroup)}>
                        {t.EXPORT}
                    </Button>
                </>
            ),
        },
    ];

    const messageIdColumns = [
        {title: 'Message ID', dataIndex: 'msgId', key: 'msgId', align: 'center'},
        {
            title: 'Tag', dataIndex: ['properties', 'TAGS'], key: 'tags', align: 'center',
            render: (tags) => tags || '-'
        },
        {
            title: 'Key', dataIndex: ['properties', 'KEYS'], key: 'keys', align: 'center',
            render: (keys) => keys || '-'
        },
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
                <>
                    <Button type="primary" size="small" style={{marginRight: 8, marginBottom: 8}}
                            onClick={() => queryDlqMessageDetail(record.msgId, selectedConsumerGroup)}>
                        {t.MESSAGE_DETAIL}
                    </Button>
                    <Button type="primary" size="small" style={{marginRight: 8, marginBottom: 8}}
                            onClick={() => resendDlqMessage(record, selectedConsumerGroup)}>
                        {t.RESEND_MESSAGE}
                    </Button>
                    <Button type="primary" size="small" style={{marginBottom: 8}}
                            onClick={() => exportDlqMessage(record.msgId, selectedConsumerGroup)}>
                        {t.EXPORT}
                    </Button>
                </>
            ),
        },
    ];

    return (
        <>
            {notificationContextHolder}
            <div style={{padding: '20px'}}>
                <Spin spinning={loading} tip="加载中...">
                    <Tabs activeKey={activeTab} onChange={setActiveTab} centered>
                        <TabPane tab={t.CONSUMER} key="consumer">
                            <h5 style={{margin: '15px 0'}}>{t.TOTAL_MESSAGES}</h5>
                            <div style={{padding: '20px', minHeight: '600px'}}>
                                <Form layout="inline" form={form} style={{marginBottom: '20px'}}>
                                    <Form.Item label={t.CONSUMER}>
                                        <Select
                                            showSearch
                                            style={{width: 300}}
                                            placeholder={t.SELECT_CONSUMER_GROUP_PLACEHOLDER}
                                            value={selectedConsumerGroup}
                                            onChange={(value) => {
                                                setSelectedConsumerGroup(value);
                                                onChangeQueryCondition();
                                            }}
                                            filterOption={(input, option) =>
                                                option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                                            }
                                        >
                                            {allConsumerGroupList.map(group => (
                                                <Option key={group} value={group}>{group}</Option>
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
                                                onClick={() => queryDlqMessageByConsumerGroup()}>
                                            {t.SEARCH}
                                        </Button>
                                    </Form.Item>
                                    <Form.Item>
                                        <Button
                                            id="batchResendBtn"
                                            type="primary"
                                            icon={<SendOutlined/>}
                                            onClick={batchResendDlqMessage}
                                            disabled={selectedMessageIds.size === 0}
                                        >
                                            {t.BATCH_RESEND}
                                        </Button>
                                    </Form.Item>
                                    <Form.Item>
                                        <Button
                                            id="batchExportBtn"
                                            type="primary"
                                            icon={<ExportOutlined/>}
                                            onClick={batchExportDlqMessage}
                                            disabled={selectedMessageIds.size === 0}
                                        >
                                            {t.BATCH_EXPORT}
                                        </Button>
                                    </Form.Item>
                                </Form>
                                <Table
                                    columns={consumerColumns}
                                    dataSource={messageShowList}
                                    rowKey="msgId"
                                    bordered
                                    pagination={{
                                        current: paginationConf.current,
                                        pageSize: paginationConf.pageSize,
                                        total: paginationConf.total,
                                        onChange: (page, pageSize) => queryDlqMessageByConsumerGroup(page, pageSize),
                                        showSizeChanger: true, // Allow changing page size
                                        pageSizeOptions: ['10', '20', '50', '100'], // Customizable page size options
                                    }}
                                    locale={{emptyText: t.NO_MATCH_RESULT}}
                                />
                            </div>
                        </TabPane>
                        <TabPane tab="Message ID" key="messageId">
                            <h5 style={{margin: '15px 0'}}>
                                {t.MESSAGE_ID_CONSUMER_GROUP_HINT}
                            </h5>
                            <div style={{padding: '20px', minHeight: '600px'}}>
                                <Form layout="inline" style={{marginBottom: '20px'}}>
                                    <Form.Item label={t.CONSUMER}>
                                        <Select
                                            showSearch
                                            style={{width: 300}}
                                            placeholder={t.SELECT_CONSUMER_GROUP_PLACEHOLDER}
                                            value={selectedConsumerGroup}
                                            onChange={setSelectedConsumerGroup}
                                            filterOption={(input, option) =>
                                                option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                                            }
                                        >
                                            {allConsumerGroupList.map(group => (
                                                <Option key={group} value={group}>{group}</Option>
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
                                                onClick={queryDlqMessageByMessageId}>
                                            {t.SEARCH}
                                        </Button>
                                    </Form.Item>
                                </Form>
                                <Table
                                    columns={messageIdColumns}
                                    dataSource={queryDlqMessageByMessageIdResult}
                                    rowKey="msgId"
                                    bordered
                                    pagination={false}
                                    locale={{emptyText: t.NO_MATCH_RESULT}}
                                />
                            </div>
                        </TabPane>
                        {modalContextHolder}
                    </Tabs>
                </Spin>
            </div>
        </>

    );
};

export default DlqMessageQueryPage;
