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

import React, { useCallback } from 'react';
import { Tooltip } from 'antd';
import { RobotOutlined, CloseOutlined } from '@ant-design/icons';
import { useLlm } from '../../store/context/LlmContext';
import ChatPanel from './ChatPanel';
import './SidebarChat.css';

function SidebarChat() {
    const { isPanelOpen, togglePanelOpen, messages } = useLlm();

    const handleClose = useCallback(() => {
        togglePanelOpen();
    }, [togglePanelOpen]);

    return (
        <>
            <Tooltip title="AI 助手" placement="left">
                <div
                    className={`sidebar-chat-trigger ${isPanelOpen ? 'sidebar-chat-trigger-active' : ''} ${messages.length > 0 ? 'sidebar-chat-trigger-unread' : ''}`}
                    onClick={togglePanelOpen}
                >
                    <RobotOutlined style={{ fontSize: 20 }} />
                    {messages.length > 0 && !isPanelOpen && (
                        <span className="sidebar-chat-badge">{messages.length}</span>
                    )}
                </div>
            </Tooltip>

            <div className={`sidebar-chat-overlay ${isPanelOpen ? 'sidebar-chat-overlay-open' : ''}`}>
                <div className={`sidebar-chat-panel ${isPanelOpen ? 'sidebar-chat-panel-open' : ''}`}>
                    <button className="sidebar-chat-close" onClick={handleClose} title="关闭面板">
                        <CloseOutlined />
                    </button>
                    <ChatPanel />
                </div>
            </div>
        </>
    );
}

export default SidebarChat;