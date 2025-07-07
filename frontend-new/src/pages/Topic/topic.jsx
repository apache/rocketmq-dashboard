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
import {Button, Checkbox, Form, Input, message, Popconfirm, Space, Table} from 'antd';
import {useLanguage} from '../../i18n/LanguageContext';
import {remoteApi} from '../../api/remoteApi/remoteApi';
import ResetOffsetResultDialog from "../../components/topic/ResetOffsetResultDialog";
import SendResultDialog from "../../components/topic/SendResultDialog";
import TopicModifyDialog from "../../components/topic/TopicModifyDialog";
import ConsumerViewDialog from "../../components/topic/ConsumerViewDialog";
import ConsumerResetOffsetDialog from "../../components/topic/ConsumerResetOffsetDialog";
import SkipMessageAccumulateDialog from "../../components/topic/SkipMessageAccumulateDialog";
import StatsViewDialog from "../../components/topic/StatsViewDialog";
import RouterViewDialog from "../../components/topic/RouterViewDialog";
import SendTopicMessageDialog from "../../components/topic/SendTopicMessageDialog";


const DeployHistoryList = () => {
    const {t} = useLanguage();
    const [filterStr, setFilterStr] = useState('');
    const [filterNormal, setFilterNormal] = useState(true);
    const [filterDelay, setFilterDelay] = useState(false);
    const [filterFifo, setFilterFifo] = useState(false);
    const [filterTransaction, setFilterTransaction] = useState(false);
    const [filterUnspecified, setFilterUnspecified] = useState(false);
    const [filterRetry, setFilterRetry] = useState(false);
    const [filterDLQ, setFilterDLQ] = useState(false);
    const [filterSystem, setFilterSystem] = useState(false);
    const [rmqVersion, setRmqVersion] = useState(true);
    const [writeOperationEnabled, setWriteOperationEnabled] = useState(true);

    const [allTopicList, setAllTopicList] = useState([]);
    const [allMessageTypeList, setAllMessageTypeList] = useState([]);
    const [topicShowList, setTopicShowList] = useState([]);
    const [loading, setLoading] = useState(false);

    // Dialog visibility states
    const [isAddUpdateTopicModalVisible, setIsAddUpdateTopicModalVisible] = useState(false);
    const [isResetOffsetResultModalVisible, setIsResetOffsetResultModalVisible] = useState(false);
    const [isSendResultModalVisible, setIsSendResultModalVisible] = useState(false);
    const [isConsumerViewModalVisible, setIsConsumerViewModalVisible] = useState(false);
    const [isConsumerResetOffsetModalVisible, setIsConsumerResetOffsetModalVisible] = useState(false);
    const [isSkipMessageAccumulateModalVisible, setIsSkipMessageAccumulateModalVisible] = useState(false);
    const [isStatsViewModalVisible, setIsStatsViewModalVisible] = useState(false);
    const [isRouterViewModalVisible, setIsRouterViewModalVisible] = useState(false);
    const [isSendTopicMessageModalVisible, setIsSendTopicMessageModalVisible] = useState(false);

    // Data for dialogs
    const [currentTopicForDialogs, setCurrentTopicForDialogs] = useState('');
    const [isUpdateMode, setIsUpdateMode] = useState(false);
    const [resetOffsetResultData, setResetOffsetResultData] = useState(null);
    const [sendResultData, setSendResultData] = useState(null);
    const [consumerData, setConsumerData] = useState(null);
    const [allConsumerGroupList, setAllConsumerGroupList] = useState([]);
    const [statsData, setStatsData] = useState(null);
    const [routeData, setRouteData] = useState(null);
    const [topicModifyData, setTopicModifyData] = useState([]);
    const [sendTopicMessageData, setSendTopicMessageData] = useState({
        topic: '',
        tag: '',
        key: '',
        messageBody: '',
        traceEnabled: false,
    });
    const [selectedConsumerGroups, setSelectedConsumerGroups] = useState([]);
    const [resetOffsetTime, setResetOffsetTime] = useState(new Date());

    const [allClusterNameList, setAllClusterNameList] = useState([]);
    const [allBrokerNameList, setAllBrokerNameList] = useState([]);
    const [messageApi, msgContextHolder] = message.useMessage();
    // Pagination config
    const [paginationConf, setPaginationConf] = useState({
        current: 1,
        pageSize: 10,
        total: 0,
    });

    useEffect(() => {
        getTopicList();
    }, []);

    useEffect(() => {
        filterList(paginationConf.current);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [filterStr, filterNormal, filterDelay, filterFifo, filterTransaction,
        filterUnspecified, filterRetry, filterDLQ, filterSystem, allTopicList]);

    useEffect(() => {
        const userPermission = localStorage.getItem('userrole');
        console.log(userPermission);
        if (userPermission == 2) {
            setWriteOperationEnabled(false);
        } else {
            setWriteOperationEnabled(true);
        }
    }, []);

    // Close functions for Modals
    const closeAddUpdateDialog = () => {
        setIsAddUpdateTopicModalVisible(false);
        setTopicModifyData([]);
    };

    const closeResetOffsetResultDialog = () => {
        setIsResetOffsetResultModalVisible(false);
        setResetOffsetResultData(null);
    };

    const closeSendResultDialog = () => {
        setIsSendResultModalVisible(false);
        setSendResultData(null);
    };

    const closeConsumerViewDialog = () => {
        setIsConsumerViewModalVisible(false);
        setConsumerData(null);
        setAllConsumerGroupList([]);
    };

    const closeConsumerResetOffsetDialog = () => {
        setIsConsumerResetOffsetModalVisible(false);
        setSelectedConsumerGroups([]);
        setResetOffsetTime(new Date());
        setAllConsumerGroupList([]);
    };

    const closeSkipMessageAccumulateDialog = () => {
        setIsSkipMessageAccumulateModalVisible(false);
        setSelectedConsumerGroups([]);
        setAllConsumerGroupList([]);
    };

    const closeStatsViewDialog = () => {
        setIsStatsViewModalVisible(false);
        setStatsData(null);
    };

    const closeRouterViewDialog = () => {
        setIsRouterViewModalVisible(false);
        setRouteData(null);
    };

    const closeSendTopicMessageDialog = () => {
        setIsSendTopicMessageModalVisible(false);
        setSendTopicMessageData({topic: '', tag: '', key: '', messageBody: '', traceEnabled: false});
    };

    const getTopicList = async () => {
        setLoading(true);
        try {
            const result = await remoteApi.queryTopicList();
            if (result.status === 0) {
                setAllTopicList(result.data.topicNameList);
                setAllMessageTypeList(result.data.messageTypeList);
                setPaginationConf(prev => ({
                    ...prev,
                    total: result.data.topicNameList.length
                }));
            } else {
                messageApi.error(result.errMsg);
            }
        } catch (error) {
            console.error("Error fetching topic list:", error);
            messageApi.error("Failed to fetch topic list");
        } finally {
            setLoading(false);
        }
    };


    const filterList = (currentPage) => {
        const lowExceptStr = filterStr.toLowerCase();
        const canShowList = allTopicList.filter((topic, index) => {
            if (filterStr && !topic.toLowerCase().includes(lowExceptStr)) {
                return false;
            }
            return filterByType(topic, allMessageTypeList[index]);
        });

        const perPage = paginationConf.pageSize;
        const from = (currentPage - 1) * perPage;
        const to = (from + perPage) > canShowList.length ? canShowList.length : from + perPage;

        setTopicShowList(canShowList.slice(from, to));
        setPaginationConf(prev => ({
            ...prev,
            current: currentPage,
            total: canShowList.length
        }));
    };

    const filterByType = (topic, type) => {
        if (filterRetry && type.includes("RETRY")) return true;
        if (filterDLQ && type.includes("DLQ")) return true;
        if (filterSystem && type.includes("SYSTEM")) return true;
        if (rmqVersion && filterUnspecified && type.includes("UNSPECIFIED")) return true;
        if (filterNormal && type.includes("NORMAL")) return true;
        if (!rmqVersion && filterNormal && type.includes("UNSPECIFIED")) return true;
        if (rmqVersion && filterDelay && type.includes("DELAY")) return true;
        if (rmqVersion && filterFifo && type.includes("FIFO")) return true;
        if (rmqVersion && filterTransaction && type.includes("TRANSACTION")) return true;

        return false;
    };

    const handleTableChange = (pagination) => {
        setPaginationConf(pagination);
        filterList(pagination.current);
    };

    const openAddUpdateDialog = async (topic, isSys) => {

        setCurrentTopicForDialogs(typeof topic === 'string' ? topic : (topic && topic.name) || '');
        const isUpdate = typeof topic === 'string' && !!topic; // 如果 topic 是非空字符串，则认为是更新

        setIsUpdateMode(isUpdate);

        try {
            if (isUpdate) {
                // topic 已经是字符串
                const configResult = await remoteApi.getTopicConfig(topic);
                if (configResult.status === 0) {
                    const dataToSet = Array.isArray(configResult.data) ? configResult.data : [configResult.data];
                    setTopicModifyData(dataToSet.map(item => ({
                        clusterNameList: [],
                        brokerNameList: item.brokerNameList || [],
                        topicName: item.topicName,
                        messageType: item.messageType || 'NORMAL',
                        writeQueueNums: item.writeQueueNums || 8,
                        readQueueNums: item.readQueueNums || 8,
                        perm: item.perm || 7,
                    })));
                } else {
                    messageApi.error(configResult.errMsg);
                    return;
                }
            } else {
                setTopicModifyData([{
                    clusterNameList: [],
                    brokerNameList: [],
                    topicName: '',
                    messageType: 'NORMAL',
                    writeQueueNums: 8,
                    readQueueNums: 8,
                    perm: 7,
                }]);
            }
        } catch (error) {
            console.error("Error opening add/update dialog:", error);
            messageApi.error("Failed to open dialog");
            return;
        }

        if (!isUpdate) {
            const clusterResult = await remoteApi.getClusterList();
            if (clusterResult.status === 0) {
                setAllClusterNameList(Object.keys(clusterResult.data.clusterInfo.clusterAddrTable));
                setAllBrokerNameList(Object.keys(clusterResult.data.brokerServer));
            } else {
                messageApi.error(clusterResult.errMsg);
            }
        }
        setIsAddUpdateTopicModalVisible(true);
    };

    // Post Topic Request (Add/Update)
    const postTopicRequest = async (values) => {
        try {
            const result = await remoteApi.createOrUpdateTopic(values);
            if (result.status === 0) {
                messageApi.success(t.TOPIC_OPERATION_SUCCESS);
                closeAddUpdateDialog();
                if (!isUpdateMode) {
                    await getTopicList()
                }
            } else {
                messageApi.error(result.errMsg);
            }
        } catch (error) {
            console.error("Error creating/updating topic:", error);
            messageApi.error("Failed to create/update topic");
        }
    };

    // Delete Topic
    const deleteTopic = async (topicToDelete) => {
        try {
            const result = await remoteApi.deleteTopic(topicToDelete);
            if (result.status === 0) {
                messageApi.success(`${t.TOPIC} [${topicToDelete}] ${t.DELETED_SUCCESSFULLY}`);
                setAllTopicList(allTopicList.filter(topic => topic !== topicToDelete));
                await getTopicList()
            } else {
                messageApi.error(result.errMsg);
            }
        } catch (error) {
            console.error("Error deleting topic:", error);
            messageApi.error("Failed to delete topic");
        }
    };

    // Open Stats View Dialog
    const statsView = async (topic) => {
        setCurrentTopicForDialogs(topic);
        try {
            const result = await remoteApi.getTopicStats(topic);
            if (result.status === 0) {
                setStatsData(result.data);
                setIsStatsViewModalVisible(true);
            } else {
                messageApi.error(result.errMsg);
            }
        } catch (error) {
            console.error("Error fetching stats:", error);
            messageApi.error("Failed to fetch stats");
        }
    };

    // Open Router View Dialog
    const routerView = async (topic) => {
        setCurrentTopicForDialogs(topic);
        try {
            const result = await remoteApi.getTopicRoute(topic);
            if (result.status === 0) {
                setRouteData(result.data);
                setIsRouterViewModalVisible(true);
            } else {
                messageApi.error(result.errMsg);
            }
        } catch (error) {
            console.error("Error fetching route:", error);
            messageApi.error("Failed to fetch route");
        }
    };

    // Open Consumer View Dialog
    const consumerView = async (topic) => {
        setCurrentTopicForDialogs(topic);
        try {
            const result = await remoteApi.getTopicConsumers(topic);
            if (result.status === 0) {
                setConsumerData(result.data);
                setAllConsumerGroupList(Object.keys(result.data));
                setIsConsumerViewModalVisible(true);
            } else {
                messageApi.error(result.errMsg);
            }
        } catch (error) {
            console.error("Error fetching consumers:", error);
            messageApi.error("Failed to fetch consumers");
        }
    };

    // Open Consumer Reset Offset Dialog
    const openConsumerResetOffsetDialog = async (topic) => {
        setCurrentTopicForDialogs(topic);
        try {
            const result = await remoteApi.getTopicConsumerGroups(topic);
            if (result.status === 0) {
                if (!result.data.groupList) {
                    messageApi.error("No consumer groups found");
                    return;
                }
                setAllConsumerGroupList(result.data.groupList);
                setIsConsumerResetOffsetModalVisible(true);
            } else {
                messageApi.error(result.errMsg);
            }
        } catch (error) {
            console.error("Error fetching consumer groups:", error);
            messageApi.error("Failed to fetch consumer groups");
        }
    };

    // Open Skip Message Accumulate Dialog
    const openSkipMessageAccumulateDialog = async (topic) => {
        setCurrentTopicForDialogs(topic);
        try {
            const result = await remoteApi.getTopicConsumerGroups(topic);
            if (result.status === 0) {
                if (!result.data.groupList) {
                    messageApi.error("No consumer groups found");
                    return;
                }
                setAllConsumerGroupList(result.data.groupList);
                setIsSkipMessageAccumulateModalVisible(true);
            } else {
                messageApi.error(result.errMsg);
            }
        } catch (error) {
            console.error("Error fetching consumer groups:", error);
            messageApi.error("Failed to fetch consumer groups");
        }
    };

    // Open Send Topic Message Dialog
    const openSendTopicMessageDialog = (topic) => {
        setCurrentTopicForDialogs(topic);
        setSendTopicMessageData(prev => ({...prev, topic}));
        setIsSendTopicMessageModalVisible(true);
    };

    const handleInputChange = (e) => {
        const {name, value} = e.target;
        setSendTopicMessageData(prevData => ({
            ...prevData,
            [name]: value,
        }));
    };

    const handleResetOffset = async (consumerGroupList, resetTime) => {
        try {
            const result = await remoteApi.resetConsumerOffset({
                resetTime: resetTime, // 使用传递过来的 resetTime
                consumerGroupList: consumerGroupList, // 使用传递过来的 consumerGroupList
                topic: currentTopicForDialogs,
                force: true
            });
            if (result.status === 0) {
                setResetOffsetResultData(result.data);
                setIsResetOffsetResultModalVisible(true);
                setIsConsumerResetOffsetModalVisible(false);
            } else {
                messageApi.error(result.errMsg);
            }
        } catch (error) {
            console.error("Error resetting offset:", error);
            messageApi.error("Failed to reset offset");
        }
    };

    const handleSkipMessageAccumulate = async (consumerGroupListFromDialog) => {
        try {
            const result = await remoteApi.skipMessageAccumulate({
                resetTime: -1,
                consumerGroupList: consumerGroupListFromDialog, // 使用子组件传递的 consumerGroupList
                topic: currentTopicForDialogs, // 使用父组件中管理的 topic
                force: true
            });
            if (result.status === 0) {
                setResetOffsetResultData(result.data); // 注意这里使用了 setResetOffsetResultData，确认这是你期望的
                setIsResetOffsetResultModalVisible(true); // 注意这里使用了 setIsResetOffsetResultModalVisible，确认这是你期望的
                setIsSkipMessageAccumulateModalVisible(false);
            } else {
                messageApi.error(result.errMsg);
            }
        } catch (error) {
            console.error("Error skipping message accumulate:", error);
            messageApi.error("Failed to skip message accumulate");
        }
    };

    const columns = [
        {
            title: t.TOPIC,
            dataIndex: 'topic',
            key: 'topic',
            align: 'center',
            render: (text) => {
                const sysFlag = text.startsWith('%SYS%');
                const topic = sysFlag ? text.substring(5) : text;
                return <span style={{color: sysFlag ? 'red' : ''}}>{topic}</span>;
            },
        },
        {
            title: t.OPERATION,
            key: 'operation',
            align: 'left',
            render: (_, record) => {
                const sysFlag = record.topic.startsWith('%SYS%');
                const topicName = sysFlag ? record.topic.substring(5) : record.topic;
                return (
                    <Space size="small">
                        <Button type="primary" size="small" onClick={() => statsView(topicName)}>
                            {t.STATUS}
                        </Button>
                        <Button type="primary" size="small" onClick={() => routerView(topicName)}>
                            {t.ROUTER}
                        </Button>
                        <Button type="primary" size="small" onClick={() => consumerView(topicName)}>
                            Consumer {t.MANAGE}
                        </Button>
                        <Button type="primary" size="small" onClick={() => openAddUpdateDialog(topicName, sysFlag)}>
                            Topic {t.CONFIG}
                        </Button>
                        {!sysFlag && (
                            <Button type="primary" size="small" onClick={() => openSendTopicMessageDialog(topicName)}>
                                {t.SEND_MSG}
                            </Button>
                        )}
                        {!sysFlag && writeOperationEnabled && (
                            <Button type="primary" danger size="small"
                                    onClick={() => openConsumerResetOffsetDialog(topicName)}>
                                {t.RESET_CUS_OFFSET}
                            </Button>
                        )}
                        {!sysFlag && writeOperationEnabled && (
                            <Button type="primary" danger size="small"
                                    onClick={() => openSkipMessageAccumulateDialog(topicName)}>
                                {t.SKIP_MESSAGE_ACCUMULATE}
                            </Button>
                        )}
                        {!sysFlag && writeOperationEnabled && (
                            <Popconfirm
                                title={`${t.ARE_YOU_SURE_TO_DELETE}`}
                                onConfirm={() => deleteTopic(topicName)}
                                okText={t.YES}
                                cancelText={t.NOT}
                            >
                                <Button type="primary" danger size="small">
                                    {t.DELETE}
                                </Button>
                            </Popconfirm>
                        )}
                    </Space>
                );
            },
        },
    ];

    return (
        <>
            {msgContextHolder}
            <div className="container-fluid" id="deployHistoryList">
                <div className="modal-body">
                    <div className="row">
                        <Form layout="inline" className="pull-left col-sm-12">
                            <Form.Item label={t.TOPIC}>
                                <Input
                                    value={filterStr}
                                    onChange={(e) => setFilterStr(e.target.value)}
                                />
                            </Form.Item>
                            <Form.Item>
                                <Checkbox checked={filterNormal} onChange={(e) => setFilterNormal(e.target.checked)}>
                                    {t.NORMAL}
                                </Checkbox>
                            </Form.Item>
                            {rmqVersion && (
                                <>
                                    <Form.Item>
                                        <Checkbox checked={filterDelay}
                                                  onChange={(e) => setFilterDelay(e.target.checked)}>
                                            {t.DELAY}
                                        </Checkbox>
                                    </Form.Item>
                                    <Form.Item>
                                        <Checkbox checked={filterFifo}
                                                  onChange={(e) => setFilterFifo(e.target.checked)}>
                                            {t.FIFO}
                                        </Checkbox>
                                    </Form.Item>
                                    <Form.Item>
                                        <Checkbox checked={filterTransaction}
                                                  onChange={(e) => setFilterTransaction(e.target.checked)}>
                                            {t.TRANSACTION}
                                        </Checkbox>
                                    </Form.Item>
                                    <Form.Item>
                                        <Checkbox checked={filterUnspecified}
                                                  onChange={(e) => setFilterUnspecified(e.target.checked)}>
                                            {t.UNSPECIFIED}
                                        </Checkbox>
                                    </Form.Item>
                                </>
                            )}
                            <Form.Item>
                                <Checkbox checked={filterRetry} onChange={(e) => setFilterRetry(e.target.checked)}>
                                    {t.RETRY}
                                </Checkbox>
                            </Form.Item>
                            <Form.Item>
                                <Checkbox checked={filterDLQ} onChange={(e) => setFilterDLQ(e.target.checked)}>
                                    {t.DLQ}
                                </Checkbox>
                            </Form.Item>
                            <Form.Item>
                                <Checkbox checked={filterSystem} onChange={(e) => setFilterSystem(e.target.checked)}>
                                    {t.SYSTEM}
                                </Checkbox>
                            </Form.Item>
                            {writeOperationEnabled && (
                                <Form.Item>
                                    <Button type="primary" onClick={openAddUpdateDialog}>
                                        {t.ADD} / {t.UPDATE}
                                    </Button>
                                </Form.Item>
                            )}
                            <Form.Item>
                                <Button type="primary" onClick={getTopicList}>
                                    {t.REFRESH}
                                </Button>
                            </Form.Item>
                        </Form>
                    </div>
                    <br/>
                    <div>
                        <div className="row">
                            <Table
                                bordered
                                loading={loading}
                                dataSource={topicShowList.map((topic, index) => ({key: index, topic}))}
                                columns={columns}
                                pagination={paginationConf}
                                onChange={handleTableChange}
                            />
                        </div>
                    </div>
                </div>

                {/* Modals/Dialogs - 传递 visible 和 onClose prop */}
                <ResetOffsetResultDialog
                    visible={isResetOffsetResultModalVisible}
                    onClose={closeResetOffsetResultDialog} // 传递关闭函数
                    result={resetOffsetResultData}
                    t={t}
                />

                <SendResultDialog
                    visible={isSendResultModalVisible}
                    onClose={closeSendResultDialog} // 传递关闭函数
                    result={sendResultData}
                    t={t}
                />

                <TopicModifyDialog
                    visible={isAddUpdateTopicModalVisible}
                    onClose={closeAddUpdateDialog}
                    initialData={topicModifyData}
                    bIsUpdate={isUpdateMode}
                    writeOperationEnabled={writeOperationEnabled}
                    allClusterNameList={allClusterNameList || []}
                    allBrokerNameList={allBrokerNameList || []}
                    onSubmit={postTopicRequest}
                    onInputChange={handleInputChange}
                    t={t}
                />

                <ConsumerViewDialog
                    visible={isConsumerViewModalVisible}
                    onClose={closeConsumerViewDialog} // 传递关闭函数
                    topic={currentTopicForDialogs}
                    consumerData={consumerData}
                    consumerGroupCount={allConsumerGroupList.length}
                    t={t}
                />

                <ConsumerResetOffsetDialog
                    visible={isConsumerResetOffsetModalVisible}
                    onClose={closeConsumerResetOffsetDialog} // 传递关闭函数
                    topic={currentTopicForDialogs}
                    allConsumerGroupList={allConsumerGroupList}
                    handleResetOffset={handleResetOffset}
                    t={t}
                />

                <SkipMessageAccumulateDialog
                    visible={isSkipMessageAccumulateModalVisible}
                    onClose={closeSkipMessageAccumulateDialog} // 传递关闭函数
                    topic={currentTopicForDialogs}
                    allConsumerGroupList={allConsumerGroupList}
                    handleSkipMessageAccumulate={handleSkipMessageAccumulate}
                    t={t}
                />

                <StatsViewDialog
                    visible={isStatsViewModalVisible}
                    onClose={closeStatsViewDialog} // 传递关闭函数
                    topic={currentTopicForDialogs}
                    statsData={statsData}
                    t={t}
                />

                <RouterViewDialog
                    visible={isRouterViewModalVisible}
                    onClose={closeRouterViewDialog} // 传递关闭函数
                    topic={currentTopicForDialogs}
                    routeData={routeData}
                    t={t}
                />

                <SendTopicMessageDialog
                    visible={isSendTopicMessageModalVisible}
                    onClose={closeSendTopicMessageDialog} // 传递关闭函数
                    topic={currentTopicForDialogs}
                    setSendResultData={setSendResultData}
                    setIsSendResultModalVisible={setIsSendResultModalVisible}
                    setIsSendTopicMessageModalVisible={setIsSendTopicMessageModalVisible}
                    sendTopicMessageData={sendTopicMessageData}
                    message={messageApi}
                    t={t}
                />
            </div>
        </>

    );
};

export default DeployHistoryList;
