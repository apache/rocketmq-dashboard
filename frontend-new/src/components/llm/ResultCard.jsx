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
import { Card, Button } from 'antd';
import { LinkOutlined } from '@ant-design/icons';
import TableResult from './TableResult';
import TimeseriesResult from './TimeseriesResult';
import TopicDetailCard from './TopicDetailCard';
import GroupDetailCard from './GroupDetailCard';

function ResultCard({ viewHint, data, content }) {
    const renderContent = () => {
        switch (viewHint) {
            case 'table':
                return <TableResult columns={data?.columns} dataSource={data?.rows} title={content} />;
            case 'timeseries':
                return <TimeseriesResult data={data} title={content} />;
            case 'topic-detail':
                return <TopicDetailCard data={data} />;
            case 'group-detail':
                return <GroupDetailCard data={data} />;
            case 'client-detail':
                return (
                    <Card size="small">
                        <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>
                            {JSON.stringify(data, null, 2)}
                        </pre>
                    </Card>
                );
            case 'capability-detail':
                return (
                    <Card size="small">
                        <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>
                            {JSON.stringify(data, null, 2)}
                        </pre>
                    </Card>
                );
            default:
                return (
                    <Card size="small">
                        <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>
                            {JSON.stringify(data, null, 2)}
                        </pre>
                    </Card>
                );
        }
    };

    const getLinkPath = () => {
        switch (viewHint) {
            case 'topic-detail': return '/#/topic';
            case 'group-detail': return '/#/consumer';
            case 'client-detail': return '/#/producer';
            default: return null;
        }
    };

    const linkPath = getLinkPath();

    return (
        <Card
            size="small"
            style={{ marginTop: '8px' }}
            extra={
                linkPath && (
                    <Button
                        type="link"
                        size="small"
                        icon={<LinkOutlined />}
                        href={linkPath}
                    >
                        Open in Console
                    </Button>
                )
            }
        >
            {renderContent()}
        </Card>
    );
}

export default ResultCard;
