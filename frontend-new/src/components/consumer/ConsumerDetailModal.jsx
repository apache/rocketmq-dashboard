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
import {Modal, Spin, Table} from 'antd';
import {remoteApi} from '../../api/remoteApi/remoteApi';
import {useLanguage} from '../../i18n/LanguageContext';

const ConsumerDetailModal = ({visible, group, address, onCancel ,messageApi}) => {
    const {t} = useLanguage();
    const [loading, setLoading] = useState(false);
    const [details, setDetails] = useState([]);

    useEffect(() => {
        const fetchData = async () => {
            if (!visible) return;

            setLoading(true);
            try {
                const response = await remoteApi.queryTopicByConsumer(group, address);
                if (response.status === 0) {
                    setDetails(response.data);
                }else {
                    // Handle error case
                    messageApi.error(response.errMsg);
                    setDetails([]);
                }
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [visible, group, address]);

    // Format timestamp to readable date
    const formatTimestamp = (timestamp) => {
        if (!timestamp || timestamp === 0) return '-';
        return new Date(timestamp).toLocaleString();
    };

    // Group data by topic for better organization
    const groupByTopic = (data) => {
        const grouped = {};
        data.forEach(item => {
            if (!grouped[item.topic]) {
                grouped[item.topic] = [];
            }
            grouped[item.topic].push(item);
        });
        return grouped;
    };

    const groupedDetails = groupByTopic(details);

    const queueColumns = [
        {title: 'Broker', dataIndex: 'brokerName', width: 120},
        {title: 'Queue ID', dataIndex: 'queueId', width: 100},
        {title: 'Broker Offset', dataIndex: 'brokerOffset', width: 120},
        {title: 'Consumer Offset', dataIndex: 'consumerOffset', width: 120},
        {
            title: 'Lag (Diff)', dataIndex: 'diffTotal', width: 100,
            render: (diff) => (
                <span style={{color: diff > 0 ? '#f5222d' : '#52c41a'}}>
                    {diff}
                </span>
            )
        },
        {title: 'Client Info', dataIndex: 'clientInfo', width: 200},
        {
            title: 'Last Consume Time', dataIndex: 'lastTimestamp', width: 180,
            render: (timestamp) => formatTimestamp(timestamp)
        },
    ];

    return (
        <Modal
            title={
                <span>Consumer Details - Group: <strong>{group}</strong> | Address: <strong>{address}</strong></span>}
            visible={visible}
            onCancel={onCancel}
            footer={null}
            width={1400}
            style={{top: 20}}
        >
            <Spin spinning={loading}>
                {Object.entries(groupedDetails).map(([topic, topicDetails]) => (
                    <div key={topic} style={{marginBottom: 24}}>
                        <div style={{
                            background: '#f0f0f0',
                            padding: '8px 16px',
                            marginBottom: 8,
                            borderRadius: 4,
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center'
                        }}>
                            <h3 style={{margin: 0}}>Topic: <strong>{topic}</strong></h3>
                            <div>
                                <span style={{marginRight: 16}}>Total Lag: <strong>{topicDetails[0].diffTotal}</strong></span>
                                <span>Last Consume Time: <strong>{formatTimestamp(topicDetails[0].lastTimestamp)}</strong></span>
                            </div>
                        </div>

                        {topicDetails.map((detail, index) => (
                            <div key={index} style={{marginBottom: 16}}>
                                <Table
                                    columns={queueColumns}
                                    dataSource={detail.queueStatInfoList}
                                    rowKey={(record) => `${record.brokerName}-${record.queueId}`}
                                    pagination={false}
                                    size="small"
                                    bordered
                                    scroll={{x: 'max-content'}}
                                />
                            </div>
                        ))}
                    </div>
                ))}
            </Spin>
        </Modal>
    );
};

export default ConsumerDetailModal;
