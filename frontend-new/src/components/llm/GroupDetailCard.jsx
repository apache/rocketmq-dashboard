/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * this License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import { LinkOutlined } from '@ant-design/icons';

/**
 * GroupDetailCard — 消费组详情卡片
 * 使用 DetailCard 风格的 key-value 列表展示
 */
const GROUP_FIELDS = [
    { key: 'name', label: '消费组名称', alt: 'groupName' },
    { key: 'consumeMode', label: '消费模式', alt: 'consumeType' },
    { key: 'status', label: '状态' },
    { key: 'onlineClientCount', label: '在线客户端数', alt: 'clientCount' },
    { key: 'diff', label: '积压量' },
    { key: 'delay', label: '延迟' },
];

function GroupDetailCard({ data }) {
    if (!data) {
        return <div className="detail-card-empty">暂无消费组数据</div>;
    }

    const groupData = data.group || data;
    const subscribedTopics = groupData.subscribedTopics || groupData.subscriptionTopics || [];

    return (
        <div className="detail-card">
            <div className="detail-card-list">
                {GROUP_FIELDS.map(field => {
                    const value = groupData[field.key] ?? groupData[field.alt];
                    if (value === undefined || value === null) return null;
                    return (
                        <div className="detail-card-row" key={field.key}>
                            <span className="detail-card-key">{field.label}</span>
                            <span className="detail-card-value">{String(value)}</span>
                        </div>
                    );
                })}
                {/* Subscribed topics as tags */}
                {subscribedTopics.length > 0 && (
                    <div className="detail-card-row">
                        <span className="detail-card-key">订阅主题</span>
                        <span className="detail-card-value">
                            <div className="detail-card-tags">
                                {subscribedTopics.map((topic, i) => (
                                    <span key={i} className="detail-card-tag">
                                        {typeof topic === 'string' ? topic : (topic.topic || topic.topicName || JSON.stringify(topic))}
                                    </span>
                                ))}
                            </div>
                        </span>
                    </div>
                )}
            </div>
            <div className="detail-card-footer">
                <a className="result-card-console-link" href="/#/consumer">
                    <LinkOutlined style={{ marginRight: 4 }} />
                    在控制台中打开
                </a>
            </div>
        </div>
    );
}

export default GroupDetailCard;