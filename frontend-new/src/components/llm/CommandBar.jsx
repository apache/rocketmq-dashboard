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

import React, { useState } from 'react';
import { Input } from 'antd';
import { useLlm } from '../../store/context/LlmContext';
import { useClusterCapabilities } from '../../store/context/ClusterCapabilitiesContext';
import ClusterChip from './ClusterChip';
import GlobalSearch from './GlobalSearch';
import ChatPanel from './ChatPanel';

const { Search } = Input;

function CommandBar() {
    const { isConfigured, isLoading, messages, sendMessage } = useLlm();
    const { selectedCluster } = useClusterCapabilities();
    const [showChat, setShowChat] = useState(false);

    const handleSearch = (value) => {
        const text = value.trim();
        if (!text) return;
        if (isConfigured) {
            setShowChat(true);
        }
    };

    const handleSend = (text) => {
        sendMessage(text, selectedCluster);
    };

    return (
        <div style={{ marginBottom: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <div style={{ flex: 1 }}>
                    {isConfigured ? (
                        <Search
                            placeholder="Ask RocketMQ... (Cmd+K)"
                            allowClear
                            size="large"
                            onSearch={handleSearch}
                            enterButton={null}
                        />
                    ) : (
                        <GlobalSearch />
                    )}
                </div>
                <ClusterChip />
            </div>
            {showChat && (
                <div style={{
                    marginTop: '12px',
                    border: '1px solid #e8e8e8',
                    borderRadius: '8px',
                    height: '500px',
                    overflow: 'hidden',
                }}>
                    <ChatPanel
                        messages={messages}
                        isLoading={isLoading}
                        onSend={handleSend}
                    />
                </div>
            )}
        </div>
    );
}

export default CommandBar;
