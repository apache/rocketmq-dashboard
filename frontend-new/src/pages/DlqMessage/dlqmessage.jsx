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
    AutoComplete,
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
const NOTIFICATION_DURATION_SECONDS = 10; // Duration for success notifications (in seconds)

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
    
    // Message Detail Modal state
    const [messageDetailModalVisible, setMessageDetailModalVisible] = useState(false);
    const [messageDetailData, setMessageDetailData] = useState(null);
    // Fetch consumer group list on component mount
    useEffect(() => {
        const fetchConsumerGroups = async () => {
            setLoading(true);
            try {
                const resp = await remoteApi.queryConsumerGroupList(false);
                if (resp.status === 0) {
                    // Handle both array and object with data property
                    const data = Array.isArray(resp.data) ? resp.data : (resp.data || []);
                    const filteredGroups = data
                        .filter(consumerGroup => consumerGroup && consumerGroup.group && !consumerGroup.group.startsWith(SYS_GROUP_TOPIC_PREFIX))
                        .map(consumerGroup => consumerGroup.group)
                        .sort();
                    setAllConsumerGroupList(filteredGroups);
                    if (filteredGroups.length === 0) {
                    }
                } else {
                    // Don't show error if it's just an empty list - this is valid when no brokers are running
                    if (resp.errMsg && !resp.errMsg.includes("No consumer group") && !resp.errMsg.includes("Failed to fetch")) {
                        // Only show warning for actual errors, not network/connection issues
                        console.warn("Consumer group list fetch returned error:", resp.errMsg);
                    }
                }
            } catch (error) {
                console.error("Error fetching consumer groups:", error);
                // Don't show error notification - allow manual input
                // The Input field will work even without the consumer group list
            }
            setLoading(false);
        };
        fetchConsumerGroups();
    }, [t, notificationApi]);

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

    const queryDlqMessageByConsumerGroup = useCallback(async (page = paginationConf.current, pageSize = paginationConf.pageSize, showNoResultToast = true) => {
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
        // Get timestamps directly from moment objects (same as Message page)
        const beginTimestamp = timepickerBegin ? timepickerBegin.valueOf() : moment().subtract(3, 'hour').valueOf();
        const endTimestamp = timepickerEnd ? timepickerEnd.valueOf() : moment().valueOf();
        
        try {
            const resp = await remoteApi.queryDlqMessageByConsumerGroup(
                selectedConsumerGroup,
                beginTimestamp,
                endTimestamp,
                page,
                pageSize,
                taskId
            );

            if (resp.status === 0) {
                const fetchedMessages = resp.data.page.content.map(msg => ({...msg, checked: false}));
                setMessageShowList(fetchedMessages);
                // Only show "No result" toast for user-initiated searches, not auto-refreshes
                if (fetchedMessages.length === 0 && showNoResultToast) {
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
            if (resp.status === 0 && resp.data) {
                setMessageDetailData(resp.data);
                setMessageDetailModalVisible(true);
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
            console.error("查询失败:", error);
        } finally {
            setLoading(false);
        }
    }, [t, notificationApi]);

    const resendDlqMessage = useCallback(async (messageView, consumerGroup) => {
        setLoading(true);
        const topic = messageView.properties.RETRY_TOPIC;
        // Use the DLQ message ID (not ORIGIN_MESSAGE_ID) to retrieve the message from DLQ
        const dlqMsgId = messageView.msgId;
        // console.log(`重发DLQ消息: DLQ MsgId=${dlqMsgId}, Topic=${topic}, 消费者组=${consumerGroup}`);
        try {
            const resp = await remoteApi.resendDlqMessage(dlqMsgId, consumerGroup, topic);
            if (resp.status === 0) {
                // Use simple success message for security and consistency (don't expose message IDs)
                notificationApi.success({
                    message: t.SUCCESS,
                    description: t.RESEND_SUCCESS,
                });
                // Refresh list without showing "No result" toast
                queryDlqMessageByConsumerGroup(paginationConf.current, paginationConf.pageSize, false);
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
        
        const messagesToResend = messageCheckedList.map((message) => {
            const retryTopic = message.properties?.RETRY_TOPIC;
            return {
                topicName: retryTopic || message.properties?.REAL_TOPIC || '', // Changed from 'topic' to 'topicName' to match backend DlqMessageRequest
                msgId: message.msgId, // Use DLQ message ID (same as single resend)
                consumerGroup: selectedConsumerGroup,
            };
        }).filter(msg => msg.topicName && msg.topicName.trim() !== ''); // Filter out messages without topicName
        
        if (messagesToResend.length === 0) {
            notificationApi.error({
                message: t.ERROR,
                description: "No messages can be resent: all selected messages are missing RETRY_TOPIC property",
            });
            setLoading(false);
            return;
        }
        
        if (messagesToResend.length < messageCheckedList.length) {
            notificationApi.warning({
                message: t.WARNING,
                description: `${messagesToResend.length} of ${messageCheckedList.length} messages can be resent (some are missing RETRY_TOPIC)`,
            });
        }
        
        try {
            const resp = await remoteApi.batchResendDlqMessage(messagesToResend);
            
            // Backend returns array wrapped in JsonResult {status: 0, data: Array, errMsg: null}
            // Check if response is an array (success) or has status field (error)
            if (Array.isArray(resp)) {
                // Success - backend returned list of results
                notificationApi.success({
                    message: t.SUCCESS,
                    description: t.BATCH_RESEND_SUCCESS,
                });
                // Show summary instead of full data to avoid exposing message IDs
                if (resp.length > 0) {
                    // Check for success (CMResult.CR_SUCCESS = 0)
                    // consumeResult can be: 0 (CR_SUCCESS), enum name, or number
                    const successCount = resp.filter(r => {
                        const result = r.consumeResult;
                        // CMResult.CR_SUCCESS = 0
                        return result === 0 || result === 'CR_SUCCESS' || result === 'SUCCESS' || result === 'CR_SUCCESS';
                    }).length;
                    const totalCount = resp.length;
                    modalApi.info({
                        title: t.RESULT,
                        content: `${successCount}/${totalCount} messages resent successfully`,
                    });
                } else {
                    modalApi.info({
                        title: t.RESULT,
                        content: t.BATCH_RESEND_SUCCESS,
                    });
                }
                // Refresh list and reset selected state (without showing "No result" toast)
                queryDlqMessageByConsumerGroup(paginationConf.current, paginationConf.pageSize, false);
                setSelectedMessageIds(new Set());
                setCheckedAll(false);
                setMessageCheckedList([]);
            } else if (resp.status === 0) {
                // Handle wrapped response (JsonResult from GlobalRestfulResponseBodyAdvice)
                notificationApi.success({
                    message: t.SUCCESS,
                    description: t.BATCH_RESEND_SUCCESS,
                });
                if (Array.isArray(resp.data) && resp.data.length > 0) {
                    // Check for success (CMResult.CR_SUCCESS = 0)
                    // consumeResult can be: 0 (number), "CR_SUCCESS" (string), or enum object
                    const successCount = resp.data.filter(r => {
                        const result = r.consumeResult;
                        // Handle different formats: number 0, string "CR_SUCCESS", or enum
                        if (result === 0) return true;
                        if (result === 'CR_SUCCESS') return true;
                        if (result === 'SUCCESS') return true;
                        // Handle enum object (if serialized as object)
                        if (result && typeof result === 'object' && result.name === 'CR_SUCCESS') return true;
                        return false;
                    }).length;
                    const totalCount = resp.data.length;
                    modalApi.info({
                        title: t.RESULT,
                        content: `${successCount}/${totalCount} messages resent successfully`,
                    });
                } else {
                    modalApi.info({
                        title: t.RESULT,
                        content: t.BATCH_RESEND_SUCCESS,
                    });
                }
                // Refresh list and reset selected state (without showing "No result" toast)
                queryDlqMessageByConsumerGroup(paginationConf.current, paginationConf.pageSize, false);
                setSelectedMessageIds(new Set());
                setCheckedAll(false);
                setMessageCheckedList([]);
            } else {
                // Error response
                notificationApi.error({
                    message: t.ERROR,
                    description: resp.errMsg || t.BATCH_RESEND_FAILED,
                });
                modalApi.error({
                    title: t.RESULT,
                    content: resp.errMsg || t.BATCH_RESEND_FAILED,
                });
            }
        } catch (error) {
            console.error("Batch resend error:", error);
            notificationApi.error({
                message: t.ERROR,
                description: t.BATCH_RESEND_FAILED + (error.message ? ": " + error.message : ""),
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
        // Build export list from messageShowList filtered by selectedMessageIds
        // This ensures we get all selected messages, not just what's in messageCheckedList
        const messagesToExport = messageShowList
            .filter(message => selectedMessageIds.has(message.msgId))
            .map(message => ({
                msgId: message.msgId,
                consumerGroup: selectedConsumerGroup,
                topicName: message.properties?.RETRY_TOPIC || message.topic || '',
            }));
        if (messagesToExport.length === 0) {
            notificationApi.warning({
                message: t.WARNING,
                description: t.PLEASE_SELECT_MESSAGE_TO_EXPORT,
            });
            setLoading(false);
            return;
        }
        try {
            const resp = await remoteApi.batchExportDlqMessage(messagesToExport);
            if (resp.status === 0) {
                notificationApi.success({
                    message: t.SUCCESS,
                    description: t.BATCH_EXPORT_SUCCESS,
                });
                // The actual file download is handled within remoteApi.js
                // Refresh list and reset selected state (without showing "No result" toast)
                queryDlqMessageByConsumerGroup(paginationConf.current, paginationConf.pageSize, false);
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
                                    <Form.Item 
                                        label={t.CONSUMER}
                                        required
                                        validateStatus={!selectedConsumerGroup ? 'warning' : ''}
                                        help={!selectedConsumerGroup && allConsumerGroupList.length === 0 ? "Type a consumer group name to search DLQ messages" : ""}
                                    >
                                        {allConsumerGroupList.length === 0 ? (
                                            <Input
                                                style={{width: 300}}
                                                placeholder="Type consumer group name (e.g., my-consumer-group)"
                                                value={selectedConsumerGroup || ''}
                                                onChange={(e) => {
                                                    const value = e.target.value.trim();
                                                    setSelectedConsumerGroup(value || null);
                                                    if (value) {
                                                        onChangeQueryCondition();
                                                    }
                                                }}
                                                onPressEnter={() => {
                                                    if (selectedConsumerGroup) {
                                                        queryDlqMessageByConsumerGroup(1, paginationConf.pageSize);
                                                    }
                                                }}
                                                allowClear
                                            />
                                        ) : (
                                            <AutoComplete
                                                style={{width: 300}}
                                                placeholder={t.SELECT_CONSUMER_GROUP_PLACEHOLDER + " or type a name"}
                                                value={selectedConsumerGroup || ''}
                                                onChange={(value) => {
                                                    setSelectedConsumerGroup(value || null);
                                                    onChangeQueryCondition();
                                                }}
                                                onSelect={(value) => {
                                                    setSelectedConsumerGroup(value);
                                                    onChangeQueryCondition();
                                                }}
                                                allowClear
                                                options={allConsumerGroupList.map(group => ({
                                                    value: group,
                                                    label: group
                                                }))}
                                                filterOption={(inputValue, option) => {
                                                    if (!option || !option.value) return true;
                                                    return option.value.toLowerCase().includes(inputValue.toLowerCase());
                                                }}
                                            />
                                        )}
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
                                        onChange: (page, pageSize) => {
                                            queryDlqMessageByConsumerGroup(page, pageSize || paginationConf.pageSize);
                                        },
                                        onShowSizeChange: (current, size) => {
                                            // When page size changes, reset to page 1
                                            queryDlqMessageByConsumerGroup(1, size);
                                        },
                                        showSizeChanger: true, // Allow changing page size
                                        showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} messages`,
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
                                    <Form.Item 
                                        label={t.CONSUMER}
                                        required
                                        validateStatus={!selectedConsumerGroup ? 'warning' : ''}
                                        help={!selectedConsumerGroup && allConsumerGroupList.length === 0 ? "Type a consumer group name to search DLQ messages" : ""}
                                    >
                                        {allConsumerGroupList.length === 0 ? (
                                            <Input
                                                style={{width: 300}}
                                                placeholder="Type consumer group name (e.g., my-consumer-group)"
                                                value={selectedConsumerGroup || ''}
                                                onChange={(e) => {
                                                    const value = e.target.value.trim();
                                                    setSelectedConsumerGroup(value || null);
                                                }}
                                                onPressEnter={() => {
                                                    if (selectedConsumerGroup && messageId) {
                                                        queryDlqMessageByMessageId();
                                                    }
                                                }}
                                                allowClear
                                            />
                                        ) : (
                                            <AutoComplete
                                                style={{width: 300}}
                                                placeholder={t.SELECT_CONSUMER_GROUP_PLACEHOLDER + " or type a name"}
                                                value={selectedConsumerGroup || ''}
                                                onChange={setSelectedConsumerGroup}
                                                onSelect={setSelectedConsumerGroup}
                                                allowClear
                                                options={allConsumerGroupList.map(group => ({
                                                    value: group,
                                                    label: group
                                                }))}
                                                filterOption={(inputValue, option) => {
                                                    if (!option || !option.value) return true;
                                                    return option.value.toLowerCase().includes(inputValue.toLowerCase());
                                                }}
                                            />
                                        )}
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
            <Modal
                title={t.MESSAGE_DETAIL}
                open={messageDetailModalVisible}
                onCancel={() => setMessageDetailModalVisible(false)}
                onOk={() => setMessageDetailModalVisible(false)}
                okText={t.CLOSE}
                width={800}
                footer={[
                    <Button key="close" type="primary" onClick={() => setMessageDetailModalVisible(false)}>
                        {t.CLOSE}
                    </Button>
                ]}
            >
                {messageDetailData && (
                    <DlqMessageDetailViewDialog
                        ngDialogData={{messageView: messageDetailData}}
                    />
                )}
            </Modal>
        </>

    );
};

export default DlqMessageQueryPage;
