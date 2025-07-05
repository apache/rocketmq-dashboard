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

import {Button, Modal, Table} from "antd";
import React from "react";

const RouterViewDialog = ({visible, onClose, topic, routeData, t}) => {
    const brokerColumns = [
        {
            title: 'Broker',
            dataIndex: 'brokerName',
            key: 'brokerName',
        },
        {
            title: 'Broker Addrs',
            key: 'brokerAddrs',
            render: (_, record) => (
                <Table
                    dataSource={Object.entries(record.brokerAddrs || []).map(([key, value]) => ({
                        key,
                        idx: key,
                        address: value
                    }))}
                    columns={[
                        {title: 'Index', dataIndex: 'idx', key: 'idx'},
                        {title: 'Address', dataIndex: 'address', key: 'address'},
                    ]}
                    pagination={false}
                    bordered
                    size="small"
                />
            ),
        },
    ];

    const queueColumns = [
        {
            title: t.BROKER_NAME,
            dataIndex: 'brokerName',
            key: 'brokerName',
        },
        {
            title: t.READ_QUEUE_NUMS,
            dataIndex: 'readQueueNums',
            key: 'readQueueNums',
        },
        {
            title: t.WRITE_QUEUE_NUMS,
            dataIndex: 'writeQueueNums',
            key: 'writeQueueNums',
        },
        {
            title: t.PERM,
            dataIndex: 'perm',
            key: 'perm',
        },
    ];

    return (
        <Modal
            title={`${topic}${t.ROUTER}`}
            open={visible}
            onCancel={onClose}
            width={800}
            footer={[
                <Button key="close" onClick={onClose}>
                    {t.CLOSE}
                </Button>,
            ]}
        >
            <div className="limit_height">
                <div>
                    <h3>Broker Datas:</h3>
                    {routeData?.brokerDatas?.map((item, index) => (
                        <div key={index} style={{marginBottom: '15px', border: '1px solid #d9d9d9', padding: '10px'}}>
                            <Table
                                dataSource={[item]}
                                columns={brokerColumns}
                                pagination={false}
                                bordered
                                size="small"
                            />
                        </div>
                    ))}
                </div>
                <div style={{marginTop: '20px'}}>
                    <h3>{t.QUEUE_DATAS}:</h3>
                    <Table
                        dataSource={routeData?.queueDatas || []}
                        columns={queueColumns}
                        pagination={false}
                        bordered
                        size="small"
                    />
                </div>
            </div>
        </Modal>
    );
};

export default RouterViewDialog;
