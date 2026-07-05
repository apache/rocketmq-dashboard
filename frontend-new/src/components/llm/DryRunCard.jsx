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
import { Card, Button, Space, Tag, Descriptions, Alert } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';

function DryRunCard({ data, onConfirm, onCancel }) {
    if (!data) {
        return <div style={{ color: '#999', padding: '8px' }}>No dry-run data available</div>;
    }

    const operationName = data.operation || data.operationName || 'Unknown Operation';
    const affectedResources = data.affectedResources || data.resources || [];
    const changeDetails = data.changeDetails || data.changes || {};
    const warnings = data.warnings || [];

    return (
        <Card
            size="small"
            style={{
                border: '2px solid #fa8c16',
                backgroundColor: '#fff7e6',
                marginTop: '8px',
            }}
            title={
                <span style={{ color: '#fa8c16' }}>
                    Dry-Run Preview: {operationName}
                </span>
            }
        >
            {warnings.length > 0 && (
                <Alert
                    message="Warnings"
                    description={
                        <ul style={{ margin: 0, paddingLeft: '20px' }}>
                            {warnings.map((w, i) => (
                                <li key={i}>{typeof w === 'string' ? w : (w.message || JSON.stringify(w))}</li>
                            ))}
                        </ul>
                    }
                    type="warning"
                    showIcon
                    style={{ marginBottom: '12px' }}
                />
            )}

            <Descriptions bordered size="small" column={1} style={{ marginBottom: '12px' }}>
                <Descriptions.Item label="Operation">{operationName}</Descriptions.Item>
                <Descriptions.Item label="Affected Resources">
                    {affectedResources.length > 0
                        ? affectedResources.map((res, i) => (
                            <Tag key={i} color="orange" style={{ marginBottom: '4px' }}>
                                {typeof res === 'string' ? res : (res.name || res.id || JSON.stringify(res))}
                            </Tag>
                        ))
                        : 'None'}
                </Descriptions.Item>
                {Object.entries(changeDetails).map(([key, value]) => (
                    <Descriptions.Item key={key} label={key}>
                        {String(value)}
                    </Descriptions.Item>
                ))}
            </Descriptions>

            <Space>
                <Button
                    type="primary"
                    icon={<CheckOutlined />}
                    style={{ backgroundColor: '#52c41a', borderColor: '#52c41a' }}
                    onClick={onConfirm}
                >
                    Confirm Execute
                </Button>
                <Button
                    icon={<CloseOutlined />}
                    onClick={onCancel}
                >
                    Cancel
                </Button>
            </Space>
        </Card>
    );
}

export default DryRunCard;
