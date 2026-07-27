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
import {Table, Card, Input, Space, Tag, Spin} from 'antd';
import {AuditOutlined, SearchOutlined} from '@ant-design/icons';
import {remoteApi} from '../../api/remoteApi/remoteApi';
import {useLanguage} from '../../i18n/LanguageContext';

const AuditLog = () => {
    const {t} = useLanguage();
    const [loading, setLoading] = useState(false);
    const [logs, setLogs] = useState([]);
    const [pagination, setPagination] = useState({current: 1, pageSize: 20, total: 0});
    const [keyword, setKeyword] = useState('');

    const fetchLogs = useCallback(async (page = 1, size = 20) => {
        setLoading(true);
        try {
            const result = await remoteApi.listAuditLogs({page, size, keyword});
            if (result && result.status === 0 && result.data) {
                setLogs(result.data.list || []);
                setPagination(prev => ({...prev, current: page, pageSize: size, total: result.data.total || 0}));
            }
        } catch (error) {
            console.error('Failed to fetch audit logs:', error);
        } finally {
            setLoading(false);
        }
    }, [keyword]);

    useEffect(() => { fetchLogs(); }, [fetchLogs]);

    const columns = [
        {title: 'Time', dataIndex: 'timestamp', key: 'timestamp', width: 180},
        {title: 'User', dataIndex: 'username', key: 'username', width: 120},
        {title: 'Action', dataIndex: 'action', key: 'action', width: 150, render: (a) => <Tag color="blue">{a}</Tag>},
        {title: 'Resource', dataIndex: 'resource', key: 'resource', width: 150},
        {title: 'Detail', dataIndex: 'detail', key: 'detail', ellipsis: true},
        {title: 'IP', dataIndex: 'ip', key: 'ip', width: 130},
    ];

    return (
        <Card title={<><AuditOutlined /> {t.AUDIT_LOG || 'Audit Log'}</>} extra={
            <Space>
                <Input placeholder={t.AUDIT_SEARCH || 'Search'} prefix={<SearchOutlined />} value={keyword}
                    onChange={e => setKeyword(e.target.value)} onPressEnter={() => fetchLogs(1)} allowClear />
            </Space>
        }>
            <Spin spinning={loading}>
                <Table columns={columns} dataSource={logs} rowKey="id" size="small"
                    pagination={{...pagination, onChange: (p, s) => fetchLogs(p, s), showSizeChanger: true, showTotal: (t) => `Total ${t}`}} />
            </Spin>
        </Card>
    );
};

export default AuditLog;