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
import { Typography, Tag } from 'antd';
import {
    UserOutlined,
    RobotOutlined,
    ToolOutlined,
    WarningOutlined,
    CheckCircleOutlined,
    CloseCircleOutlined,
    LoadingOutlined,
    SyncOutlined,
} from '@ant-design/icons';
import { EVENT_LABELS, EVENT_TO_ROUTE } from '../../store/context/OperationEventContext';
import { useNavigate } from 'react-router-dom';
import ResultCard from './ResultCard';
import DryRunCard from './DryRunCard';

const { Text, Paragraph } = Typography;

/**
 * Risk level config for tool call cards
 */
const RISK_CONFIG = {
    L1: { color: '#52c41a', label: '只读', tagColor: 'green' },
    L2: { color: '#faad14', label: '受控变更', tagColor: 'orange' },
    L3: { color: '#ff4d4f', label: '危险操作', tagColor: 'red' },
};

/**
 * ToolCallCard - Displays a tool invocation with status and result
 */
function ToolCallCard({ toolCall, onConfirm, onCancel, isLoading }) {
    if (!toolCall) return null;

    const name = toolCall.name || toolCall.toolName || 'Unknown Tool';
    const status = toolCall.status || toolCall.state || 'pending';
    const args = toolCall.args || toolCall.arguments || toolCall.params || {};
    const result = toolCall.result || toolCall.output;
    const risk = toolCall.risk || toolCall.riskLevel;
    const description = toolCall.description || '';
    const requiresConfirm = toolCall.requiresConfirm || toolCall.dryRun;
    const targetResource = toolCall.targetResource || toolCall.resource || toolCall.target;
    const warnings = toolCall.warnings || [];

    const riskConfig = RISK_CONFIG[risk] || null;

    // Determine if we should show DryRunCard:
    // - L3: always show rejected card (no confirm button)
    // - L2 + requiresConfirm: show preview card with confirm/cancel
    const showDryRun = risk === 'L3' || (risk === 'L2' && requiresConfirm && (status === 'awaiting_confirm' || status === 'pending'));

    const statusIcon = {
        pending: <LoadingOutlined style={{ color: '#1677ff' }} />,
        running: <LoadingOutlined style={{ color: '#1677ff' }} spin />,
        completed: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
        success: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
        failed: <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
        error: <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
        awaiting_confirm: <WarningOutlined style={{ color: '#faad14' }} />,
        rejected: <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
    };

    const statusLabel = {
        pending: '等待中',
        running: '执行中',
        completed: '已完成',
        success: '成功',
        failed: '失败',
        error: '错误',
        awaiting_confirm: '待确认',
        rejected: '已拒绝',
    };

    return (
        <div className="chat-tool-card">
            <div className="chat-tool-card-header">
                <div className="chat-tool-card-title">
                    <ToolOutlined style={{ marginRight: 6, color: '#1677ff' }} />
                    <Text strong className="chat-tool-name">{name}</Text>
                    {riskConfig && (
                        <Tag color={riskConfig.tagColor} style={{ marginLeft: 8, fontSize: 11 }}>
                            {riskConfig.label}
                        </Tag>
                    )}
                </div>
                <div className="chat-tool-card-status">
                    {statusIcon[status] || statusIcon.pending}
                    <Text type="secondary" style={{ fontSize: 12, marginLeft: 4 }}>
                        {statusLabel[status] || status}
                    </Text>
                </div>
            </div>

            {description && (
                <div className="chat-tool-card-desc">
                    <Text type="secondary" style={{ fontSize: 12 }}>{description}</Text>
                </div>
            )}

            {Object.keys(args).length > 0 && (
                <div className="chat-tool-card-args">
                    <Text type="secondary" style={{ fontSize: 11, display: 'block', marginBottom: 4 }}>
                        参数:
                    </Text>
                    <pre className="chat-tool-card-pre">
                        {typeof args === 'string' ? args : JSON.stringify(args, null, 2)}
                    </pre>
                </div>
            )}

            {result && (status === 'completed' || status === 'success') && (
                <div className="chat-tool-card-result">
                    <Text type="secondary" style={{ fontSize: 11, display: 'block', marginBottom: 4 }}>
                        结果:
                    </Text>
                    <pre className="chat-tool-card-pre">
                        {typeof result === 'string' ? result : JSON.stringify(result, null, 2)}
                    </pre>
                </div>
            )}

            {status === 'failed' && result && (
                <div className="chat-tool-card-error">
                    <Text type="secondary" style={{ fontSize: 11, display: 'block', marginBottom: 4 }}>
                        错误:
                    </Text>
                    <pre className="chat-tool-card-pre">{typeof result === 'string' ? result : JSON.stringify(result, null, 2)}</pre>
                </div>
            )}

            {/* Dry-run / confirmation card for L2 & rejected card for L3 */}
            {showDryRun && (
                <div className="chat-tool-card-confirm">
                    <DryRunCard
                        risk={risk}
                        operationName={name}
                        targetResource={targetResource}
                        params={args}
                        warnings={warnings}
                        onConfirm={() => onConfirm && onConfirm(toolCall.id || toolCall.toolCallId)}
                        onCancel={() => onCancel && onCancel(toolCall.id || toolCall.toolCallId)}
                    />
                </div>
            )}
        </div>
    );
}

/**
 * ChatMessage - Renders a chat message with appropriate styling based on role
 * Supports: 'user', 'assistant', 'tool', 'error'
 */
function ChatMessage({ message, onConfirmAction, onCancelAction, isLoading }) {
    const role = message.role;
    const timestamp = new Date(message.id).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const navigate = useNavigate();

    // System / operation record message
    if (role === 'system') {
        const eventType = message.eventType || '';
        const eventLabel = EVENT_LABELS[eventType] || eventType;
        const eventRoute = EVENT_TO_ROUTE[eventType];
        const eventPayload = message.eventPayload || {};
        const eventTimestamp = message.eventTimestamp;

        return (
            <div className="chat-message chat-message-system">
                <div className="chat-message-bubble chat-message-bubble-system">
                    <SyncOutlined style={{ marginRight: 6, color: '#1677ff' }} />
                    <Text style={{ fontSize: 12, color: '#595959' }}>
                        {eventLabel}
                        {eventPayload && Object.keys(eventPayload).length > 0 && (
                            <span style={{ marginLeft: 4, color: '#8c8c8c' }}>
                                {Object.entries(eventPayload).map(([k, v]) => `${k}: ${v}`).join(', ')}
                            </span>
                        )}
                    </Text>
                    {eventRoute && (
                        <a
                            style={{ marginLeft: 8, fontSize: 11 }}
                            onClick={() => navigate(eventRoute)}
                        >
                            查看详情
                        </a>
                    )}
                </div>
                <div className="chat-message-time">
                    <Text type="secondary" style={{ fontSize: 11 }}>
                        {eventTimestamp ? new Date(eventTimestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : timestamp}
                    </Text>
                </div>
            </div>
        );
    }

    // Error message
    if (role === 'error') {
        return (
            <div className="chat-message chat-message-error">
                <div className="chat-message-bubble chat-message-bubble-error">
                    <WarningOutlined style={{ marginRight: 8, color: '#ff4d4f' }} />
                    <Text style={{ color: '#cf1322' }}>{message.content}</Text>
                </div>
                <div className="chat-message-time">
                    <Text type="secondary" style={{ fontSize: 11 }}>{timestamp}</Text>
                </div>
            </div>
        );
    }

    // User message
    if (role === 'user') {
        return (
            <div className="chat-message chat-message-user">
                <div className="chat-message-avatar chat-message-avatar-user">
                    <UserOutlined />
                </div>
                <div className="chat-message-body">
                    <div className="chat-message-bubble chat-message-bubble-user">
                        <Paragraph style={{ margin: 0, color: 'inherit', whiteSpace: 'pre-wrap' }}>
                            {message.content}
                        </Paragraph>
                    </div>
                    <div className="chat-message-time chat-message-time-user">
                        <Text type="secondary" style={{ fontSize: 11 }}>{timestamp}</Text>
                    </div>
                </div>
            </div>
        );
    }

    // Tool call message
    if (role === 'tool') {
        const toolResult = message.result || message.output;
        const toolViewHint = message.viewHint;
        const toolConsoleLink = message.consoleLink;
        const isCompleted = message.status === 'completed' || message.status === 'success';

        return (
            <div className="chat-message chat-message-tool">
                <div className="chat-message-avatar chat-message-avatar-tool">
                    <ToolOutlined />
                </div>
                <div className="chat-message-body">
                    <ToolCallCard
                        toolCall={message}
                        onConfirm={onConfirmAction}
                        onCancel={onCancelAction}
                        isLoading={isLoading}
                    />
                    {/* Render structured result card for completed tool calls */}
                    {isCompleted && toolResult && toolViewHint && (
                        <ResultCard
                            viewHint={toolViewHint}
                            data={typeof toolResult === 'object' ? toolResult : undefined}
                            content={typeof toolResult === 'string' ? toolResult : undefined}
                            consoleLink={toolConsoleLink}
                        />
                    )}
                    <div className="chat-message-time">
                        <Text type="secondary" style={{ fontSize: 11 }}>{timestamp}</Text>
                    </div>
                </div>
            </div>
        );
    }

    // Assistant message (default)
    return (
        <div className="chat-message chat-message-assistant">
            <div className="chat-message-avatar chat-message-avatar-assistant">
                <RobotOutlined />
            </div>
            <div className="chat-message-body">
                <div className="chat-message-bubble chat-message-bubble-assistant">
                    {message.content && (
                        <Paragraph style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                            {message.content}
                        </Paragraph>
                    )}
                    {/* Tool calls embedded in assistant message */}
                    {message.toolCalls && message.toolCalls.length > 0 && (
                        <div className="chat-message-tool-calls">
                            {message.toolCalls.map((tc, idx) => (
                                <ToolCallCard
                                    key={tc.id || idx}
                                    toolCall={tc}
                                    onConfirm={onConfirmAction}
                                    onCancel={onCancelAction}
                                    isLoading={isLoading}
                                />
                            ))}
                        </div>
                    )}
                    {/* Result card for structured data */}
                    {message.viewHint && (
                        <ResultCard
                            viewHint={message.viewHint}
                            data={message.resultData || message.data || message.toolCalls}
                            content={message.content}
                            consoleLink={message.consoleLink}
                        />
                    )}
                    {message.degraded && (
                        <div className="chat-message-degraded">
                            <Text type="warning" style={{ fontSize: 12 }}>
                                ⚠️ LLM 不可用，结果来自本地搜索
                            </Text>
                        </div>
                    )}
                </div>
                <div className="chat-message-time">
                    <Text type="secondary" style={{ fontSize: 11 }}>{timestamp}</Text>
                </div>
            </div>
        </div>
    );
}

export default ChatMessage;