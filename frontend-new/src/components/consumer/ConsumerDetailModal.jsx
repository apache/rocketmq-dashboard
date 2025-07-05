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

const ConsumerDetailModal = ({visible, group, address, onCancel}) => {
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
                }
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [visible, group, address]);

    const queueColumns = [
        {title: 'Broker', dataIndex: 'brokerName'},
        {title: 'Queue', dataIndex: 'queueId'},
        {title: 'BrokerOffset', dataIndex: 'brokerOffset'},
        {title: 'ConsumerOffset', dataIndex: 'consumerOffset'},
        {title: 'DiffTotal', dataIndex: 'diffTotal'},
        {title: 'LastTimestamp', dataIndex: 'lastTimestamp'},
    ];

    return (
        <Modal
            title={`[${group}]${t.CONSUME_DETAIL}`}
            visible={visible}
            onCancel={onCancel}
            footer={null}
            width={1200}
        >
            <Spin spinning={loading}>
                {details.map((consumeDetail, index) => (
                    <div key={index}>
                        <Table
                            columns={queueColumns}
                            dataSource={consumeDetail.queueStatInfoList}
                            rowKey="queueId"
                            pagination={false}
                        />
                    </div>
                ))}
            </Spin>
        </Modal>
    );
};

export default ConsumerDetailModal;
