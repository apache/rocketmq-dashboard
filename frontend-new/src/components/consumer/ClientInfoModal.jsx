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
import {Descriptions, Modal, Spin, Table, Tag, Tooltip} from 'antd';
import {remoteApi} from '../../api/remoteApi/remoteApi';
import {useLanguage} from '../../i18n/LanguageContext';


const ClientInfoModal = ({visible, group, address, onCancel, messageApi}) => {
    const {t} = useLanguage();
    const [loading, setLoading] = useState(false);
    const [connectionData, setConnectionData] = useState(null);

    useEffect(() => {
        const fetchData = async () => {
            if (!visible) return;

            setLoading(true);
            try {
                const connResponse = await remoteApi.queryConsumerConnection(group, address);

                if (connResponse.status === 0) {
                    setConnectionData(connResponse.data);
                }else{
                    messageApi.error(connResponse.errMsg);
                    setConnectionData(null);
                }
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [visible, group, address]);

    const connectionColumns = [
        {
            title: t.CLIENTID, dataIndex: 'clientId', key: 'clientId', width: 220, ellipsis: true,
            render: (text) => (
                <Tooltip title={text}>
                    {text}
                </Tooltip>
            )
        },
        {title: t.CLIENTADDR, dataIndex: 'clientAddr', key: 'clientAddr', width: 150, ellipsis: true},
        {title: t.LANGUAGE, dataIndex: 'language', key: 'language', width: 100},
        {title: t.VERSION, dataIndex: 'versionDesc', key: 'versionDesc', width: 100},
    ];

    const subscriptionColumns = [
        {
            title: t.TOPIC, dataIndex: 'topic', key: 'topic', width: 250, ellipsis: true,
            render: (text) => (
                <Tooltip title={text}>
                    {text}
                </Tooltip>
            )
        },
        {title: t.SUBSCRIPTION_EXPRESSION, dataIndex: 'subString', key: 'subString', width: 150, ellipsis: true},
        {
            title: t.EXPRESSION_TYPE, dataIndex: 'expressionType', key: 'expressionType', width: 120,
            render: (text) => <Tag color="blue">{text}</Tag>
        },
        // --- Added Columns for TagsSet and CodeSet ---
        {
            title: t.TAGS_SET, // Ensure t.TAGS_SET is defined in your language file
            dataIndex: 'tagsSet',
            key: 'tagsSet',
            width: 150,
            render: (tags) => (
                tags && tags.length > 0 ? (
                    <Tooltip title={tags.join(', ')}>
                        {tags.map((tag, index) => (
                            <Tag key={index} color="default">{tag}</Tag>
                        ))}
                    </Tooltip>
                ) : 'N/A'
            ),
            ellipsis: true,
        },
        {
            title: t.CODE_SET, // Ensure t.CODE_SET is defined in your language file
            dataIndex: 'codeSet',
            key: 'codeSet',
            width: 150,
            render: (codes) => (
                codes && codes.length > 0 ? (
                    <Tooltip title={codes.join(', ')}>
                        {codes.map((code, index) => (
                            <Tag key={index} color="default">{code}</Tag>
                        ))}
                    </Tooltip>
                ) : 'N/A'
            ),
            ellipsis: true,
        },
        // --- End of Added Columns ---
        {title: t.SUB_VERSION, dataIndex: 'subVersion', key: 'subVersion', width: 150},
    ];

    const formattedSubscriptionData = connectionData?.subscriptionTable
        ? Object.keys(connectionData.subscriptionTable).map(key => ({
            ...connectionData.subscriptionTable[key],
            key: key,
        }))
        : [];

    return (
        <Modal
            title={`[${group}] ${t.CLIENT_INFORMATION}`}
            visible={visible}
            onCancel={onCancel}
            footer={null}
            width={1200} // Increased width to accommodate more columns
        >
            <Spin spinning={loading}>
                {connectionData && (
                    <>
                        <Descriptions bordered column={2} title={t.CONNECTION_OVERVIEW} style={{marginBottom: 20}}>
                            <Descriptions.Item label={t.CONSUME_TYPE}>
                                <Tag color="green">{connectionData.consumeType}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t.MESSAGE_MODEL}>
                                <Tag color="geekblue">{connectionData.messageModel}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t.CONSUME_FROM_WHERE}>
                                <Tag color="purple">{connectionData.consumeFromWhere}</Tag>
                            </Descriptions.Item>
                        </Descriptions>

                        <h3>{t.CLIENT_CONNECTIONS}</h3>
                        <Table
                            columns={connectionColumns}
                            dataSource={connectionData.connectionSet}
                            rowKey="clientId"
                            pagination={false}
                            scroll={{x: 'max-content'}}
                            style={{marginBottom: 20}}
                        />

                        <h3>{t.CLIENT_SUBSCRIPTIONS}</h3>
                        <Table
                            columns={subscriptionColumns}
                            dataSource={formattedSubscriptionData}
                            rowKey="key"
                            pagination={false}
                            scroll={{x: 'max-content'}}
                        />
                    </>
                )}
            </Spin>
        </Modal>
    );
};

export default ClientInfoModal;
