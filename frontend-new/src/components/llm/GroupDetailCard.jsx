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
import { Descriptions, Button, Tag } from 'antd';
import { LinkOutlined } from '@ant-design/icons';

function GroupDetailCard({ data }) {
    if (!data) {
        return <div style={{ color: '#999', padding: '8px' }}>No consumer group data available</div>;
    }

    const groupData = data.group || data;
    const subscribedTopics = groupData.subscribedTopics || groupData.subscriptionTopics || [];

    return (
        <div>
            <Descriptions
                bordered
                size="small"
                column={1}
                style={{ marginBottom: '12px' }}
            >
                <Descriptions.Item label="Name">{groupData.name || groupData.groupName || '-'}</Descriptions.Item>
                <Descriptions.Item label="Consume Mode">{groupData.consumeMode || groupData.consumeType || '-'}</Descriptions.Item>
                <Descriptions.Item label="Status">{groupData.status || '-'}</Descriptions.Item>
                <Descriptions.Item label="Online Client Count">{groupData.onlineClientCount ?? groupData.clientCount ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="Subscribed Topics">
                    {subscribedTopics.length > 0
                        ? subscribedTopics.map((topic, index) => (
                            <Tag key={index} color="blue" style={{ marginBottom: '4px' }}>
                                {typeof topic === 'string' ? topic : (topic.topic || topic.topicName || JSON.stringify(topic))}
                            </Tag>
                        ))
                        : '-'}
                </Descriptions.Item>
            </Descriptions>
            <Button
                type="link"
                icon={<LinkOutlined />}
                href="/#/consumer"
                size="small"
            >
                View in Console
            </Button>
        </div>
    );
}

export default GroupDetailCard;
