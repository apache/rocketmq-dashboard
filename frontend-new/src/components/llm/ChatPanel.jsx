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

import React, { useRef, useEffect, useState } from 'react';
import { Input, Button, Spin, Empty } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import ChatMessage from './ChatMessage';

const { TextArea } = Input;

function ChatPanel({ messages, isLoading, onSend }) {
    const [inputValue, setInputValue] = useState('');
    const messagesEndRef = useRef(null);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    const handleSend = () => {
        const text = inputValue.trim();
        if (!text || isLoading) return;
        setInputValue('');
        onSend(text);
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    return (
        <div style={{
            display: 'flex',
            flexDirection: 'column',
            height: '100%',
            minHeight: '400px',
        }}>
            <div style={{
                flex: 1,
                overflowY: 'auto',
                padding: '16px',
                backgroundColor: '#fafafa',
                borderRadius: '8px 8px 0 0',
            }}>
                {messages.length === 0 ? (
                    <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        height: '100%',
                        minHeight: '200px',
                    }}>
                        <Empty
                            description={
                                <span>
                                    Ask me anything about your RocketMQ cluster.<br />
                                    Try: 'Show all topics', 'Create a FIFO topic',<br />
                                    'What\'s the lag on my consumer groups?'
                                </span>
                            }
                        />
                    </div>
                ) : (
                    messages.map((msg) => (
                        <ChatMessage key={msg.id} message={msg} />
                    ))
                )}
                {isLoading && (
                    <div style={{ textAlign: 'center', padding: '8px' }}>
                        <Spin size="small" />
                    </div>
                )}
                <div ref={messagesEndRef} />
            </div>
            <div style={{
                padding: '12px 16px',
                borderTop: '1px solid #e8e8e8',
                backgroundColor: '#fff',
                borderRadius: '0 0 8px 8px',
                display: 'flex',
                gap: '8px',
            }}>
                <TextArea
                    value={inputValue}
                    onChange={(e) => setInputValue(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder="Type a message... (Enter to send, Shift+Enter for new line)"
                    autoSize={{ minRows: 1, maxRows: 4 }}
                    disabled={isLoading}
                    style={{ flex: 1 }}
                />
                <Button
                    type="primary"
                    icon={<SendOutlined />}
                    onClick={handleSend}
                    loading={isLoading}
                    disabled={!inputValue.trim()}
                />
            </div>
        </div>
    );
}

export default ChatPanel;
