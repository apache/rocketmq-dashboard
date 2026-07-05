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

import React, { useState, useCallback } from 'react';
import { AutoComplete, Input } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { remoteApi } from '../../api/remoteApi/remoteApi';

function GlobalSearch() {
    const [options, setOptions] = useState([]);
    const [searchText, setSearchText] = useState('');
    const navigate = useNavigate();

    const handleSearch = useCallback(async (value) => {
        setSearchText(value);
        if (!value || value.trim().length < 2) {
            setOptions([]);
            return;
        }

        const searchTerm = value.trim().toLowerCase();
        const results = [];

        try {
            // Search topics
            const topicRes = await remoteApi.queryTopicList();
            if (topicRes && topicRes.data && topicRes.data.topicList) {
                const matchingTopics = topicRes.data.topicList.filter(
                    (t) => (t.topic || t).toLowerCase().includes(searchTerm)
                );
                if (matchingTopics.length > 0) {
                    results.push({
                        label: 'Topics',
                        options: matchingTopics.slice(0, 5).map((t) => ({
                            value: t.topic || t,
                            label: (
                                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                                    <span>{t.topic || t}</span>
                                    <span style={{ color: '#999', fontSize: '12px' }}>Topic</span>
                                </div>
                            ),
                            type: 'topic',
                            name: t.topic || t,
                        })),
                    });
                }
            }
        } catch (e) { /* ignore */ }

        try {
            // Search consumer groups
            const groupRes = await remoteApi.queryConsumerGroupList(true, '');
            if (groupRes && groupRes.data) {
                const groupList = Array.isArray(groupRes.data)
                    ? groupRes.data
                    : (groupRes.data.groupList || []);
                const matchingGroups = groupList.filter(
                    (g) => (typeof g === 'string' ? g : g.group).toLowerCase().includes(searchTerm)
                );
                if (matchingGroups.length > 0) {
                    results.push({
                        label: 'Consumer Groups',
                        options: matchingGroups.slice(0, 5).map((g) => ({
                            value: typeof g === 'string' ? g : g.group,
                            label: (
                                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                                    <span>{typeof g === 'string' ? g : g.group}</span>
                                    <span style={{ color: '#999', fontSize: '12px' }}>Consumer Group</span>
                                </div>
                            ),
                            type: 'consumer',
                            name: typeof g === 'string' ? g : g.group,
                        })),
                    });
                }
            }
        } catch (e) { /* ignore */ }

        setOptions(results);
    }, []);

    const handleSelect = (value, option) => {
        setSearchText('');
        setOptions([]);
        if (option.type === 'topic') {
            navigate(`/topic?name=${encodeURIComponent(option.name)}`);
        } else if (option.type === 'consumer') {
            navigate(`/consumer?name=${encodeURIComponent(option.name)}`);
        } else if (option.type === 'message') {
            navigate(`/message`);
        } else if (option.type === 'producer') {
            navigate(`/producer`);
        }
    };

    return (
        <AutoComplete
            style={{ width: '100%' }}
            options={options}
            onSearch={handleSearch}
            onSelect={handleSelect}
            value={searchText}
            onChange={setSearchText}
            notFoundContent={
                searchText.length >= 2
                    ? 'No results found'
                    : 'Type at least 2 characters to search'
            }
        >
            <Input
                prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
                placeholder="Search resources, topics, groups..."
                allowClear
                size="large"
            />
        </AutoComplete>
    );
}

export default GlobalSearch;
