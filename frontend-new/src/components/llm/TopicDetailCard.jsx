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

import React from 'react';
import { LinkOutlined } from '@ant-design/icons';

/**
 * TopicDetailCard — 主题详情卡片
 * 使用 DetailCard 风格的 key-value 列表展示
 */
const TOPIC_FIELDS = [
    { key: 'name', label: '主题名称', alt: 'topicName' },
    { key: 'type', label: '类型', alt: 'topicType' },
    { key: 'readQueueNums', label: '读队列数' },
    { key: 'writeQueueNums', label: '写队列数' },
    { key: 'perm', label: '权限' },
    { key: 'status', label: '状态' },
    { key: 'createTime', label: '创建时间', alt: 'createTimestamp' },
    { key: 'brokerName', label: 'Broker' },
];

function TopicDetailCard({ data }) {
    if (!data) {
        return <div className="detail-card-empty">暂无主题数据</div>;
    }

    const topicData = data.topic || data;

    return (
        <div className="detail-card">
            <div className="detail-card-list">
                {TOPIC_FIELDS.map(field => {
                    const value = topicData[field.key] ?? topicData[field.alt];
                    if (value === undefined || value === null) return null;
                    return (
                        <div className="detail-card-row" key={field.key}>
                            <span className="detail-card-key">{field.label}</span>
                            <span className="detail-card-value">{String(value)}</span>
                        </div>
                    );
                })}
            </div>
            <div className="detail-card-footer">
                <a className="result-card-console-link" href="/#/topic">
                    <LinkOutlined style={{ marginRight: 4 }} />
                    在控制台中打开
                </a>
            </div>
        </div>
    );
}

export default TopicDetailCard;