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
import {Button, Modal, notification, Select, Spin, Table} from 'antd';
import {useLanguage} from "../../i18n/LanguageContext";
import {remoteApi, tools} from "../../api/remoteApi/remoteApi"; // 确保路径正确

const {Option} = Select;

const Cluster = () => {
    const {t} = useLanguage();

    const [loading, setLoading] = useState(false);
    const [clusterNames, setClusterNames] = useState([]);
    const [selectedCluster, setSelectedCluster] = useState('');
    const [instances, setInstances] = useState([]);
    const [allBrokersData, setAllBrokersData] = useState({});

    const [detailModalVisible, setDetailModalVisible] = useState(false);
    const [configModalVisible, setConfigModalVisible] = useState(false);
    const [currentDetail, setCurrentDetail] = useState({});
    const [currentConfig, setCurrentConfig] = useState({});
    const [currentBrokerName, setCurrentBrokerName] = useState('');
    const [currentIndex, setCurrentIndex] = useState(null); // 对应 brokerId
    const [currentBrokerAddress, setCurrentBrokerAddress] = useState('');
    const [api, contextHolder] = notification.useNotification();

    const switchCluster = useCallback((clusterName) => {
        if (allBrokersData[clusterName]) {
            setInstances(allBrokersData[clusterName]);
        } else {
            setInstances([]);
        }
    }, [allBrokersData]);

    const handleChangeCluster = (value) => {
        setSelectedCluster(value);
        switchCluster(value);
    };

    useEffect(() => {
        setLoading(true);
        remoteApi.queryClusterList((resp) => {
            setLoading(false);
            if (resp.status === 0) {
                const {clusterInfo, brokerServer} = resp.data;
                const {clusterAddrTable, brokerAddrTable} = clusterInfo;

                const generatedBrokers = tools.generateBrokerMap(brokerServer, clusterAddrTable, brokerAddrTable);
                setAllBrokersData(generatedBrokers);

                const names = Object.keys(clusterAddrTable);
                setClusterNames(names);

                if (names.length > 0) {
                    const defaultCluster = names[0];
                    setSelectedCluster(defaultCluster);
                    if (generatedBrokers[defaultCluster]) {
                        setInstances(generatedBrokers[defaultCluster]);
                    } else {
                        setInstances([]);
                    }
                }

            } else {
                api.error({message: resp.errMsg || t.QUERY_CLUSTER_LIST_FAILED, duration: 2});
            }
        });
    }, []);

    const showDetail = (brokerName, brokerId, record) => { // 传入 record 整个对象，方便直接显示
        setCurrentBrokerName(brokerName);
        setCurrentIndex(brokerId);
        setCurrentDetail(record); // 直接使用 record 作为详情
        setDetailModalVisible(true);
    };

    const showConfig = (brokerAddress, brokerName, brokerId) => { // 保持一致，传入 brokerId
        setCurrentBrokerName(brokerName);
        setCurrentIndex(brokerId);
        setCurrentBrokerAddress(brokerAddress);

        setLoading(true);
        remoteApi.queryBrokerConfig(brokerAddress, (resp) => {
            setLoading(false);
            if (resp.status === 0) {
                // ✨ 确保 resp.data 是一个对象，如果后端返回的不是对象，这里需要处理
                if (typeof resp.data === 'object' && resp.data !== null) {
                    setCurrentConfig(resp.data);
                    setConfigModalVisible(true);
                } else {
                    api.error({message: t.INVALID_CONFIG_DATA || 'Invalid config data received', duration: 2});
                    setCurrentConfig({}); // 清空配置，避免显示错误
                }
            } else {
                api.error({message: resp.errMsg || t.QUERY_BROKER_CONFIG_FAILED, duration: 2});
            }
        });
    };

    const columns = [
        {
            title: t.SPLIT,
            dataIndex: 'brokerName', // 直接使用 brokerId
            key: 'split',
            align: 'center'
        },
        {
            title: t.NO,
            dataIndex: 'brokerId', // 直接使用 brokerId
            key: 'no',
            align: 'center',
            render: (brokerId) => `${brokerId}${brokerId === 0 ? `(${t.MASTER})` : `(${t.SLAVE})`}`,
        },
        {
            title: t.ADDRESS,
            dataIndex: 'address', // 确保 generateBrokerMap 返回的数据有 address 字段
            key: 'address',
            align: 'center',
        },
        {
            title: t.VERSION,
            dataIndex: 'brokerVersionDesc',
            key: 'version',
            align: 'center',
        },
        {
            title: t.PRO_MSG_TPS,
            dataIndex: 'putTps',
            key: 'putTps',
            align: 'center',
            render: (text) => {
                const tpsValue = text ? Number(String(text).split(' ')[0]) : 0; // 确保text是字符串
                return tpsValue.toFixed(2);
            },
        },
        {
            title: t.CUS_MSG_TPS,
            key: 'cusMsgTps',
            align: 'center',
            render: (_, record) => {
                // 根据你提供的数据结构，这里可能是 getTransferredTps
                const val = record.getTransferedTps?.trim() ? record.getTransferedTps : record.getTransferredTps;
                const tpsValue = val ? Number(String(val).split(' ')[0]) : 0; // 确保val是字符串
                return tpsValue.toFixed(2);
            },
        },
        {
            title: t.YESTERDAY_PRO_COUNT,
            key: 'yesterdayProCount',
            align: 'center',
            render: (_, record) => {
                const putTotalTodayMorning = parseFloat(record.msgPutTotalTodayMorning || 0);
                const putTotalYesterdayMorning = parseFloat(record.msgPutTotalYesterdayMorning || 0);
                return (putTotalTodayMorning - putTotalYesterdayMorning).toLocaleString();
            }
        },
        {
            title: t.YESTERDAY_CUS_COUNT,
            key: 'yesterdayCusCount',
            align: 'center',
            render: (_, record) => {
                const getTotalTodayMorning = parseFloat(record.msgGetTotalTodayMorning || 0);
                const getTotalYesterdayMorning = parseFloat(record.msgGetTotalYesterdayMorning || 0);
                return (getTotalTodayMorning - getTotalYesterdayMorning).toLocaleString();
            }
        },
        {
            title: t.TODAY_PRO_COUNT,
            key: 'todayProCount',
            align: 'center',
            render: (_, record) => {
                const putTotalTodayNow = parseFloat(record.msgPutTotalTodayNow || 0);
                const putTotalTodayMorning = parseFloat(record.msgPutTotalTodayMorning || 0);
                return (putTotalTodayNow - putTotalTodayMorning).toLocaleString();
            }
        },
        {
            title: t.TODAY_CUS_COUNT,
            key: 'todayCusCount',
            align: 'center',
            render: (_, record) => {
                const getTotalTodayNow = parseFloat(record.msgGetTotalTodayNow || 0);
                const getTotalTodayMorning = parseFloat(record.msgGetTotalTodayMorning || 0);
                return (getTotalTodayNow - getTotalTodayMorning).toLocaleString();
            }
        },
        {
            title: t.OPERATION,
            key: 'operation',
            align: 'center',
            render: (_, record) => (
                <>
                    <Button size="small" type="primary"
                            onClick={() => showDetail(record.brokerName, record.brokerId, record)}
                            style={{marginRight: 8}}>
                        {t.STATUS}
                    </Button>
                    {/* 传入 record.address */}
                    <Button size="small" type="primary"
                            onClick={() => showConfig(record.address, record.brokerName, record.brokerId)}>
                        {t.CONFIG}
                    </Button>
                </>
            ),
        },
    ];

    return (
        <>
            {contextHolder}
            <Spin spinning={loading} tip={t.LOADING}>
                <div style={{padding: 24}}>
                    <div style={{marginBottom: 16, display: 'flex', alignItems: 'center'}}>
                        <label style={{marginRight: 8}}>{t.CLUSTER}:</label>
                        <Select
                            style={{width: 300}}
                            placeholder={t.SELECT_CLUSTER || "Please select a cluster"}
                            value={selectedCluster}
                            onChange={handleChangeCluster}
                            allowClear
                        >
                            {clusterNames.map((name) => (
                                <Option key={name} value={name}>
                                    {name}
                                </Option>
                            ))}
                        </Select>
                    </div>

                    <Table
                        dataSource={instances}
                        columns={columns}
                        rowKey={(record) => `${record.brokerName}-${record.brokerId}`}
                        pagination={false}
                        bordered
                        size="middle"
                    />

                    <Modal
                        title={`${t.BROKER} [${currentBrokerName}][${currentIndex}]`}
                        open={detailModalVisible}
                        footer={null}
                        onCancel={() => setDetailModalVisible(false)}
                        width={800}
                        bodyStyle={{maxHeight: '60vh', overflowY: 'auto'}}
                    >
                        <Table
                            dataSource={Object.entries(currentDetail).map(([key, value]) => ({key, value}))}
                            columns={[
                                {title: t.KEY || 'Key', dataIndex: 'key', key: 'key'},
                                {title: t.VALUE || 'Value', dataIndex: 'value', key: 'value'},
                            ]}
                            pagination={false}
                            size="small"
                            bordered
                            rowKey="key"
                        />
                    </Modal>

                    <Modal
                        title={`${t.BROKER} [${currentBrokerName}][${currentIndex}]`}
                        open={configModalVisible}
                        footer={null}
                        onCancel={() => setConfigModalVisible(false)}
                        width={800}
                        bodyStyle={{maxHeight: '60vh', overflowY: 'auto'}}
                    >
                        <Table
                            dataSource={Object.entries(currentConfig).map(([key, value]) => ({key, value}))}
                            columns={[
                                {title: t.KEY || 'Key', dataIndex: 'key', key: 'key'},
                                {title: t.VALUE || 'Value', dataIndex: 'value', key: 'value'},
                            ]}
                            pagination={false}
                            size="small"
                            bordered
                            rowKey="key"
                        />
                    </Modal>
                </div>
            </Spin>
        </>

    );
};

export default Cluster;
