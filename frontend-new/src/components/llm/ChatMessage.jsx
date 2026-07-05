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
import { Typography } from 'antd';
import ResultCard from './ResultCard';

const { Text } = Typography;

function ChatMessage({ message }) {
    const isUser = message.role === 'user';
    const timestamp = new Date(message.id).toLocaleTimeString();

    const bubbleStyle = {
        maxWidth: '80%',
        padding: '10px 16px',
        borderRadius: '12px',
        marginBottom: '4px',
        wordBreak: 'break-word',
    };

    const userBubbleStyle = {
        ...bubbleStyle,
        marginLeft: 'auto',
        backgroundColor: '#1677ff',
        color: '#fff',
        borderBottomRightRadius: '4px',
    };

    const assistantBubbleStyle = {
        ...bubbleStyle,
        marginRight: 'auto',
        backgroundColor: '#ffffff',
        border: '1px solid #e8e8e8',
        borderBottomLeftRadius: '4px',
    };

    return (
        <div style={{ marginBottom: '16px' }}>
            <div style={isUser ? userBubbleStyle : assistantBubbleStyle}>
                {isUser ? (
                    <Text style={{ color: 'inherit' }}>{message.content}</Text>
                ) : (
                    <div>
                        {message.content && (
                            <Text style={{ whiteSpace: 'pre-wrap' }}>{message.content}</Text>
                        )}
                        {message.viewHint && message.content && (
                            <ResultCard
                                viewHint={message.viewHint}
                                data={message.toolCalls}
                                content={message.content}
                            />
                        )}
                    </div>
                )}
            </div>
            <div style={{
                textAlign: isUser ? 'right' : 'left',
                paddingLeft: isUser ? '0' : '16px',
                paddingRight: isUser ? '16px' : '0',
            }}>
                <Text type="secondary" style={{ fontSize: '11px' }}>
                    {timestamp}
                </Text>
            </div>
        </div>
    );
}

export default ChatMessage;
