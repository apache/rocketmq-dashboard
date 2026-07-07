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
import TableResult from './TableResult';
import TimeseriesResult from './TimeseriesResult';
import DetailCard from './DetailCard';
import TopicDetailCard from './TopicDetailCard';
import GroupDetailCard from './GroupDetailCard';

/**
 * viewHint → route path mapping for "在控制台中打开" links
 */
const CONSOLE_ROUTES = {
    'topic-detail': '/topic',
    'topic': '/topic',
    'group-detail': '/consumer',
    'group': '/consumer',
    'consumer': '/consumer',
    'client-detail': '/producer',
    'producer': '/producer',
    'message': '/message',
    'message-query': '/message',
    'dlqMessage': '/dlqMessage',
    'messageTrace': '/messageTrace',
    'proxy': '/proxy',
    'cluster': '/cluster',
    'ops': '/ops',
    'acl': '/acl',
};

/**
 * ResultCard — 根据 viewHint 分发不同卡片模板
 *
 * 支持类型:
 *  - table: 表格结果 (Ant Design Table)
 *  - timeseries: 时序数据 (折线图)
 *  - topic-detail: 主题详情卡片
 *  - group-detail: 消费组详情卡片
 *  - detail / key-value: 通用 key-value 详情卡片
 *  - text / 其他: 纯文本 JSON 展示
 */
function ResultCard({ viewHint, data, content, consoleLink }) {
    // Derive console link from viewHint or explicit prop
    const linkPath = consoleLink || CONSOLE_ROUTES[viewHint] || null;

    const renderContent = () => {
        switch (viewHint) {
            case 'table':
                return (
                    <TableResult
                        columns={data?.columns}
                        dataSource={data?.rows || data?.dataSource}
                        title={content}
                    />
                );
            case 'timeseries':
                return <TimeseriesResult data={data} title={content} />;
            case 'topic-detail':
                return <TopicDetailCard data={data} />;
            case 'group-detail':
                return <GroupDetailCard data={data} />;
            case 'detail':
            case 'key-value':
                return <DetailCard data={data} title={content} />;
            default:
                return <DetailCard data={data} title={content} />;
        }
    };

    return (
        <div className="result-card">
            <div className="result-card-body">
                {renderContent()}
            </div>
            {linkPath && (
                <div className="result-card-footer">
                    <a
                        className="result-card-console-link"
                        href={`/#${linkPath}`}
                    >
                        <LinkOutlined style={{ marginRight: 4 }} />
                        在控制台中打开
                    </a>
                </div>
            )}
        </div>
    );
}

export default ResultCard;