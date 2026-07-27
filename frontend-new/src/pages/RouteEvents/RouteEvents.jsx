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

import React, {useEffect, useState, useCallback} from 'react';
import {Table, Card, Tag, Spin, Space} from 'antd';
import {ApartmentOutlined} from '@ant-design/icons';
import {remoteApi} from '../../api/remoteApi/remoteApi';
import {useLanguage} from '../../i18n/LanguageContext';

const RouteEvents = () => {
    const {t} = useLanguage();
    const [loading, setLoading] = useState(false);
    const [events, setEvents] = useState([]);

    const fetchEvents = useCallback(async () => {
        setLoading(true);
        try {
            const result = await remoteApi.getRouteEvents(50);
            if (result && result.status === 0 && result.data) {
                setEvents(result.data || []);
            }
        } catch (error) {
            console.error('Failed to fetch route events:', error);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { fetchEvents(); }, [fetchEvents]);

    const columns = [
        {title: 'Time', dataIndex: 'timestamp', key: 'timestamp', width: 180},
        {title: 'Cluster', dataIndex: 'clusterName', key: 'clusterName', width: 150},
        {title: 'Broker', dataIndex: 'brokerName', key: 'brokerName', width: 130},
        {title: 'Event', dataIndex: 'eventType', key: 'eventType', width: 120,
            render: (type) => {
                const color = type === 'ONLINE' ? 'green' : type === 'OFFLINE' ? 'red' : 'blue';
                return <Tag color={color}>{type}</Tag>;
            }
        },
        {title: 'Address', dataIndex: 'address', key: 'address', width: 180},
        {title: 'Detail', dataIndex: 'detail', key: 'detail', ellipsis: true},
    ];

    return (
        <Card title={<><ApartmentOutlined /> {t.ROUTE_EVENTS || 'Route Events'}</>}>
            <Spin spinning={loading}>
                <Table columns={columns} dataSource={events} rowKey={(r, i) => i} size="small"
                    pagination={{pageSize: 20, showSizeChanger: true}} />
            </Spin>
        </Card>
    );
};

export default RouteEvents;