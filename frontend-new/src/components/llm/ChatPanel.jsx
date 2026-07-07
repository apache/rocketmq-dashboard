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

import React, { useRef, useEffect, useState, useCallback } from 'react';
import { Button, Tooltip, Typography } from 'antd';
import {
    SendOutlined,
    RobotOutlined,
    DeleteOutlined,
    StopOutlined,
    UserOutlined,
} from '@ant-design/icons';
import { useLlm } from '../../store/context/LlmContext';
import { useClusterCapabilities } from '../../store/context/ClusterCapabilitiesContext';
import ChatMessage from './ChatMessage';
import ClusterChip from './ClusterChip';
import './ChatPanel.css';

const { Text } = Typography;

function ChatPanel() {
    const {
        isConfigured,
        isLoading,
        messages,
        streamingText,
        sendMessage,
        sendMessageStream,
        confirmAction,
        cancelAction,
        clearChat,
        abortStream,
    } = useLlm();
    const { selectedCluster } = useClusterCapabilities();
    const [inputValue, setInputValue] = useState('');
    const [autoScroll, setAutoScroll] = useState(true);
    const messagesEndRef = useRef(null);
    const messagesContainerRef = useRef(null);
    const inputRef = useRef(null);

    // Auto-scroll to bottom when new messages arrive or streaming text updates
    useEffect(() => {
        if (autoScroll && messagesEndRef.current) {
            messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
        }
    }, [messages, streamingText, autoScroll]);

    // Detect user manual scroll to disable auto-scroll
    const handleScroll = useCallback((e) => {
        const container = e.target;
        const isAtBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 60;
        setAutoScroll(isAtBottom);
    }, []);

    // Scroll to bottom button
    const scrollToBottom = useCallback(() => {
        setAutoScroll(true);
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, []);

    const handleSend = useCallback(() => {
        const text = inputValue.trim();
        if (!text || isLoading) return;
        setInputValue('');
        setAutoScroll(true);

        // Try SSE streaming first; fallback handled inside sendMessageStream
        if (isConfigured) {
            sendMessageStream(text, selectedCluster);
        } else {
            sendMessage(text, selectedCluster);
        }
    }, [inputValue, isLoading, isConfigured, sendMessageStream, sendMessage, selectedCluster]);

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    const handleConfirmAction = useCallback((toolCallId) => {
        confirmAction(toolCallId);
    }, [confirmAction]);

    const handleCancelAction = useCallback((toolCallId) => {
        cancelAction(toolCallId);
    }, [cancelAction]);

    const handleStop = useCallback(() => {
        abortStream();
    }, [abortStream]);

    return (
        <div className="chat-panel">
            {/* Header */}
            <div className="chat-panel-header">
                <div className="chat-panel-header-left">
                    <RobotOutlined className="chat-panel-header-icon" />
                    <span className="chat-panel-header-title">RocketMQ AI 助手</span>
                    <ClusterChip />
                </div>
                <div className="chat-panel-header-right">
                    <Tooltip title="清空对话">
                        <Button
                            type="text"
                            size="small"
                            icon={<DeleteOutlined />}
                            onClick={clearChat}
                            disabled={messages.length === 0 || isLoading}
                            className="chat-panel-header-btn"
                        />
                    </Tooltip>
                </div>
            </div>

            {/* Messages area */}
            <div
                className="chat-panel-messages"
                ref={messagesContainerRef}
                onScroll={handleScroll}
            >
                {messages.length === 0 && !streamingText ? (
                    <div className="chat-panel-empty">
                        <RobotOutlined style={{ fontSize: 48, color: '#bfbfbf', marginBottom: 16 }} />
                        <Text type="secondary" style={{ fontSize: 16, display: 'block', marginBottom: 8 }}>
                            RocketMQ AI 助手
                        </Text>
                        <Text type="secondary" style={{ fontSize: 13, textAlign: 'center', lineHeight: 1.8 }}>
                            向我提问关于 RocketMQ 集群的任何问题<br />
                            例如: "列出所有主题"、"查看消费者组延迟"<br />
                            "创建一个FIFO主题"、"查询消息"
                        </Text>
                        {!isConfigured && (
                            <div className="chat-panel-unconfigured">
                                <Text type="warning" style={{ fontSize: 12 }}>
                                    ⚠️ LLM 未配置，将使用本地搜索模式。请前往 LLM Settings 配置。
                                </Text>
                            </div>
                        )}
                    </div>
                ) : (
                    <>
                        {messages.map((msg) => (
                            <ChatMessage
                                key={msg.id}
                                message={msg}
                                onConfirmAction={handleConfirmAction}
                                onCancelAction={handleCancelAction}
                                isLoading={isLoading}
                            />
                        ))}
                        {/* Streaming text display */}
                        {streamingText && (
                            <div className="chat-message chat-message-assistant">
                                <div className="chat-message-avatar chat-message-avatar-assistant">
                                    <RobotOutlined />
                                </div>
                                <div className="chat-message-body">
                                    <div className="chat-message-bubble chat-message-bubble-assistant chat-message-streaming">
                                        <Text style={{ whiteSpace: 'pre-wrap' }}>{streamingText}</Text>
                                        <span className="chat-cursor" />
                                    </div>
                                </div>
                            </div>
                        )}
                        {/* Loading indicator (when not streaming) */}
                        {isLoading && !streamingText && (
                            <div className="chat-panel-loading">
                                <div className="chat-loading-dots">
                                    <span className="chat-loading-dot" />
                                    <span className="chat-loading-dot" />
                                    <span className="chat-loading-dot" />
                                </div>
                                <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>思考中...</Text>
                            </div>
                        )}
                    </>
                )}
                <div ref={messagesEndRef} />
            </div>

            {/* Scroll to bottom button */}
            {!autoScroll && (
                <div className="chat-scroll-bottom" onClick={scrollToBottom}>
                    ↓ 新消息
                </div>
            )}

            {/* Input area */}
            <div className="chat-panel-input-area">
                <div className="chat-panel-input-wrapper">
                    <textarea
                        ref={inputRef}
                        className="chat-panel-textarea"
                        value={inputValue}
                        onChange={(e) => setInputValue(e.target.value)}
                        onKeyDown={handleKeyDown}
                        placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
                        disabled={isLoading}
                        rows={1}
                    />
                    <div className="chat-panel-input-actions">
                        {isLoading ? (
                            <Tooltip title="停止生成">
                                <Button
                                    type="text"
                                    size="small"
                                    icon={<StopOutlined />}
                                    onClick={handleStop}
                                    danger
                                    className="chat-panel-send-btn"
                                />
                            </Tooltip>
                        ) : (
                            <Tooltip title="发送 (Enter)">
                                <Button
                                    type="text"
                                    size="small"
                                    icon={<SendOutlined />}
                                    onClick={handleSend}
                                    disabled={!inputValue.trim()}
                                    className="chat-panel-send-btn"
                                />
                            </Tooltip>
                        )}
                    </div>
                </div>
                <div className="chat-panel-input-footer">
                    <Text type="secondary" style={{ fontSize: 11 }}>
                        {selectedCluster ? `集群: ${selectedCluster}` : '未选择集群'}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 11 }}>
                        {isConfigured ? '🟢 LLM 已连接' : '🟡 本地模式'}
                    </Text>
                </div>
            </div>
        </div>
    );
}

export default ChatPanel;