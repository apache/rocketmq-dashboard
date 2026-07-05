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
import { Descriptions, Button } from 'antd';
import { LinkOutlined } from '@ant-design/icons';

function TopicDetailCard({ data }) {
    if (!data) {
        return <div style={{ color: '#999', padding: '8px' }}>No topic data available</div>;
    }

    const topicData = data.topic || data;

    return (
        <div>
            <Descriptions
                bordered
                size="small"
                column={1}
                style={{ marginBottom: '12px' }}
            >
                <Descriptions.Item label="Name">{topicData.name || topicData.topicName || '-'}</Descriptions.Item>
                <Descriptions.Item label="Type">{topicData.type || topicData.topicType || '-'}</Descriptions.Item>
                <Descriptions.Item label="Read Queue Nums">{topicData.readQueueNums ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="Write Queue Nums">{topicData.writeQueueNums ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="Perm">{topicData.perm ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="Status">{topicData.status || '-'}</Descriptions.Item>
                <Descriptions.Item label="Create Time">{topicData.createTime || topicData.createTimestamp || '-'}</Descriptions.Item>
            </Descriptions>
            <Button
                type="link"
                icon={<LinkOutlined />}
                href="/#/topic"
                size="small"
            >
                View in Console
            </Button>
        </div>
    );
}

export default TopicDetailCard;
