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
import {Button, Modal, Spin} from 'antd';
import {remoteApi} from '../../api/remoteApi/remoteApi';
import {useLanguage} from '../../i18n/LanguageContext';
import ConsumerConfigItem from './ConsumerConfigItem'; // 导入子组件

const ConsumerConfigModal = ({visible, isAddConfig, group, onCancel, setIsAddConfig, onSuccess}) => {
    const {t} = useLanguage();
    const [loading, setLoading] = useState(false);
    const [allBrokerList, setAllBrokerList] = useState([]); // 存储所有可用的broker
    const [allClusterNames, setAllClusterNames] = useState([]); // 存储所有可用的cluster names
    const [initialConfigData, setInitialConfigData] = useState({}); // 存储按brokerName分的初始配置数据

    useEffect(() => {
        if (visible) {
            const fetchInitialData = async () => {
                setLoading(true);
                try {
                    // Fetch cluster list for broker names and cluster names
                    if (isAddConfig) {
                        const clusterResponse = await remoteApi.getClusterList();
                        if (clusterResponse.status === 0 && clusterResponse.data) {
                            const clusterInfo = clusterResponse.data.clusterInfo;

                            const brokers = [];
                            const clusterNames = Object.keys(clusterInfo?.clusterAddrTable || {});

                            clusterNames.forEach(clusterName => {
                                const brokersInCluster = clusterInfo?.clusterAddrTable?.[clusterName] || [];
                                brokers.push(...brokersInCluster);
                            });

                            setAllBrokerList([...new Set(brokers)]); // 确保brokerName唯一
                            setAllClusterNames(clusterNames);

                        } else {
                            console.error('Failed to fetch cluster list:', clusterResponse.errMsg);
                        }
                    }
                    if (!isAddConfig) {
                        // Fetch existing consumer config for update mode
                        const consumerConfigResponse = await remoteApi.queryConsumerConfig(group);
                        if (consumerConfigResponse.status === 0 && consumerConfigResponse.data && consumerConfigResponse.data.length > 0) {
                            const configMap = {};
                            consumerConfigResponse.data.forEach(config => {
                                // 假设每个brokerName有一个独立的配置项
                                config.brokerNameList.forEach(brokerName => {
                                    configMap[brokerName] = {
                                        ...config,
                                        // 确保brokerNameList和clusterNameList是数组形式，即使API返回单值
                                        brokerNameList: Array.isArray(config.brokerNameList) ? config.brokerNameList : [config.brokerNameList],
                                        clusterNameList: Array.isArray(config.clusterNameList) ? config.clusterNameList : [config.clusterNameList]
                                    };
                                });
                            });
                            setInitialConfigData(configMap);
                        } else {
                            console.error(`Failed to fetch consumer config for group: ${group}`);
                            onCancel(); // Close modal if config not found
                        }
                    } else {
                        // For add mode, initialize with empty values and allow selecting any broker
                        setInitialConfigData({
                            // 当isAddConfig为true时，我们只提供一个空的配置模板，用户选择broker后会创建新的配置
                            // 在这里，我们将设置一个空的初始配置，供用户选择broker来创建新配置
                            newConfig: {
                                groupName: undefined,
                                subscriptionGroupConfig: {
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
                                },
                                brokerNameList: [],
                                clusterNameList: []
                            }
                        });
                    }
                } catch (error) {
                    console.error('Error in fetching initial data:', error);
                } finally {
                    setLoading(false);
                }
            };

            fetchInitialData();
        } else {
            // Reset state when modal is closed
            setInitialConfigData({});
            setAllBrokerList([]);
            setAllClusterNames([]);
        }
    }, [visible, isAddConfig, group, onCancel]);

    const getBrokersToRender = () => {
        if (isAddConfig) {
            return ['newConfig'];
        } else {
            return Object.keys(initialConfigData);
        }
    }


    return (
        <Modal
            title={isAddConfig ? t.ADD_CONSUMER : `${t.CONFIG} - ${group}`}
            visible={visible}
            onCancel={() => {
                onCancel();
                setIsAddConfig(false); // 确保关闭时重置添加模式
            }}
            width={800}
            footer={[
                <Button key="cancel" onClick={() => {
                    onCancel();
                    setIsAddConfig(false);
                }}>
                    {t.CLOSE}
                </Button>,
            ]}
            style={{top: 20}} // 让弹窗靠上一点，方便内容滚动
            bodyStyle={{maxHeight: 'calc(100vh - 200px)', overflowY: 'auto'}} // 允许内容滚动
        >
            <Spin spinning={loading}>
                {getBrokersToRender().map(brokerOrKey => (
                    <ConsumerConfigItem
                        key={brokerOrKey} // 使用brokerName作为key
                        initialConfig={initialConfigData[brokerOrKey]}
                        isAddConfig={isAddConfig}
                        group={group} // 传递当前group
                        brokerName={isAddConfig ? undefined : brokerOrKey} // 添加模式下brokerName由用户选择，更新模式下是当前遍历的brokerName
                        allBrokerList={allBrokerList}
                        allClusterNames={allClusterNames}
                        onSuccess={onSuccess}
                        onCancel={onCancel}
                        t={t} // 传递i18n函数
                    />
                ))}
            </Spin>
        </Modal>
    );
};

export default ConsumerConfigModal;
