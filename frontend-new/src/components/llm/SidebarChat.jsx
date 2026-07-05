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
import { Drawer, FloatButton } from 'antd';
import { RobotOutlined } from '@ant-design/icons';
import { useLlm } from '../../store/context/LlmContext';
import { useClusterCapabilities } from '../../store/context/ClusterCapabilitiesContext';
import ChatPanel from './ChatPanel';

function SidebarChat({ contextPrefix }) {
    const [open, setOpen] = useState(false);
    const { messages, isLoading, sendMessage } = useLlm();
    const { selectedCluster } = useClusterCapabilities();

    const handleSend = (text) => {
        const fullMessage = contextPrefix ? `${contextPrefix}: ${text}` : text;
        sendMessage(fullMessage, selectedCluster);
    };

    return (
        <>
            <FloatButton
                icon={<RobotOutlined />}
                type="primary"
                style={{ right: 24, bottom: 24 }}
                onClick={() => setOpen(true)}
                tooltip="Ask LLM"
            />
            <Drawer
                title="RocketMQ Assistant"
                placement="right"
                onClose={() => setOpen(false)}
                open={open}
                width={480}
                styles={{
                    body: {
                        padding: 0,
                    },
                }}
            >
                <ChatPanel
                    messages={messages}
                    isLoading={isLoading}
                    onSend={handleSend}
                />
            </Drawer>
        </>
    );
}

export default SidebarChat;
