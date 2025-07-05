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

import moment from "moment/moment";
import {Button, Modal, Table} from "antd";
import React from "react";

const ConsumerViewDialog = ({visible, onClose, topic, consumerData, consumerGroupCount, t}) => {
    const columns = [
        {title: t.BROKER, dataIndex: 'brokerName', key: 'brokerName', align: 'center'},
        {title: t.QUEUE, dataIndex: 'queueId', key: 'queueId', align: 'center'},
        {title: t.CONSUMER_CLIENT, dataIndex: 'clientInfo', key: 'clientInfo', align: 'center'},
        {title: t.BROKER_OFFSET, dataIndex: 'brokerOffset', key: 'brokerOffset', align: 'center'},
        {title: t.CONSUMER_OFFSET, dataIndex: 'consumerOffset', key: 'consumerOffset', align: 'center'},
        {
            title: t.DIFF_TOTAL,
            dataIndex: 'diffTotal',
            key: 'diffTotal',
            align: 'center',
            render: (_, record) => record.brokerOffset - record.consumerOffset,
        },
        {
            title: t.LAST_TIME_STAMP,
            dataIndex: 'lastTimestamp',
            key: 'lastTimestamp',
            align: 'center',
            render: (text) => moment(text).format('YYYY-MM-DD HH:mm:ss'),
        },
    ];

    return (
        <Modal
            title={`${topic} ${t.SUBSCRIPTION_GROUP}`}
            open={visible}
            onCancel={onClose}
            width={1000}
            footer={[
                <Button key="close" onClick={onClose}>
                    {t.CLOSE}
                </Button>,
            ]}
        >
            {consumerGroupCount === 0 ? (
                <div>{t.NO_DATA} {t.SUBSCRIPTION_GROUP}</div>
            ) : (
                consumerData && Object.entries(consumerData).map(([consumerGroup, consumeDetail]) => (
                    <div key={consumerGroup} style={{marginBottom: '24px'}}>
                        <Table
                            bordered
                            pagination={false}
                            showHeader={false}
                            dataSource={[{
                                consumerGroup,
                                diffTotal: consumeDetail.diffTotal,
                                lastTimestamp: consumeDetail.lastTimestamp
                            }]}
                            columns={[
                                {title: t.SUBSCRIPTION_GROUP, dataIndex: 'consumerGroup', key: 'consumerGroup'},
                                {title: t.DELAY, dataIndex: 'diffTotal', key: 'diffTotal'},
                                {
                                    title: t.LAST_CONSUME_TIME,
                                    dataIndex: 'lastTimestamp',
                                    key: 'lastTimestamp',
                                    render: (text) => moment(text).format('YYYY-MM-DD HH:mm:ss'),
                                },
                            ]}
                            rowKey="consumerGroup"
                            size="small"
                            style={{marginBottom: '12px'}}
                        />
                        <Table
                            bordered
                            pagination={false}
                            dataSource={consumeDetail.queueStatInfoList}
                            columns={columns}
                            rowKey={(record, index) => `${record.brokerName}-${record.queueId}-${index}`}
                            size="small"
                        />
                    </div>
                ))
            )}
        </Modal>
    );
};

export default ConsumerViewDialog;
