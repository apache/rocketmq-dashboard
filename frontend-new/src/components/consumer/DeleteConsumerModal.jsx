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
import {Button, Checkbox, Modal, notification, Spin} from 'antd';
import {remoteApi} from '../../api/remoteApi/remoteApi';

const DeleteConsumerModal = ({visible, group, onCancel, onSuccess, t}) => {
    const [brokerList, setBrokerList] = useState([]);
    const [selectedBrokers, setSelectedBrokers] = useState([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        const fetchBrokers = async () => {
            if (!visible) return;

            setLoading(true);
            try {
                const response = await remoteApi.fetchBrokerNameList(group);
                if (response.status === 0) {
                    setBrokerList(response.data);
                }
            } finally {
                setLoading(false);
            }
        };

        fetchBrokers();
    }, [visible, group]);

    const handleDelete = async () => {
        if (selectedBrokers.length === 0) {
            notification.warning({message: t.PLEASE_SELECT_BROKER});
            return;
        }

        setLoading(true);
        try {
            const response = await remoteApi.deleteConsumerGroup(
                group,
                selectedBrokers
            );

            if (response.status === 0) {
                notification.success({message: t.DELETE_SUCCESS});
                onSuccess();
                onCancel();
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            title={`${t.DELETE_CONSUMER_GROUP} - ${group}`}
            visible={visible}
            onCancel={onCancel}
            footer={[
                <Button key="cancel" onClick={onCancel}>
                    {t.CANCEL}
                </Button>,
                <Button
                    key="delete"
                    type="primary"
                    danger
                    loading={loading}
                    onClick={handleDelete}
                >
                    {t.CONFIRM_DELETE}
                </Button>
            ]}
        >
            <Spin spinning={loading}>
                <div style={{marginBottom: 16}}>{t.SELECT_DELETE_BROKERS}:</div>
                <Checkbox.Group
                    style={{width: '100%'}}
                    value={selectedBrokers}
                    onChange={values => setSelectedBrokers(values)}
                >
                    {brokerList.map(broker => (
                        <div key={broker}>
                            <Checkbox value={broker}>{broker}</Checkbox>
                        </div>
                    ))}
                </Checkbox.Group>
            </Spin>
        </Modal>
    );
};

export default DeleteConsumerModal;
