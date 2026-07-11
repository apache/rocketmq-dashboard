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

import React, { useState, useRef, useCallback, useEffect, useMemo } from 'react';
import { Button, Select, Tooltip, Input, Card } from 'antd';
import {
    SearchOutlined,
    DashboardOutlined,
    AppstoreOutlined,
    RobotOutlined,
    ToolOutlined,
    ThunderboltOutlined,
    AudioOutlined,
    SendOutlined,
    LoadingOutlined,
} from '@ant-design/icons';
import { useLlm } from '../../store/context/LlmContext';
import { remoteApi } from '../../api/remoteApi/remoteApi';
import ChatMessage from '../../components/llm/ChatMessage';
import './HomePage.css';

const { TextArea } = Input;

// 静态默认模型（API 获取失败时的回退）
const FALLBACK_MODELS = {
    openai: ['gpt-4o', 'gpt-4-turbo', 'gpt-4', 'gpt-3.5-turbo'],
    azure: ['gpt-4o', 'gpt-4', 'gpt-3.5-turbo'],
    deepseek: ['deepseek-chat', 'deepseek-reasoner'],
    tongyi: ['qwen-max', 'qwen-plus', 'qwen-turbo'],
    ollama: ['llama3', 'mistral', 'gemma2', 'qwen2.5'],
    bedrock: ['anthropic.claude-3-sonnet', 'anthropic.claude-3-haiku', 'meta.llama3-70b'],
};

const HomePage = () => {
    const {
        isConfigured,
        isLoading,
        messages,
        streamingText,
        sendMessage,
        sendMessageStream,
        clearChat,
        abortStream,
    } = useLlm();

    const [inputValue, setInputValue] = useState('');
    const [selectedModel, setSelectedModel] = useState('');
    const [autoScroll, setAutoScroll] = useState(true);
    const [modelOptions, setModelOptions] = useState([]);
    const [modelsLoading, setModelsLoading] = useState(false);
    const messagesEndRef = useRef(null);
    const messagesContainerRef = useRef(null);
    const inputRef = useRef(null);

    // 动态拉取模型列表：从 LLM 提供商 API 实时获取
    useEffect(() => {
        let cancelled = false;

        const fetchModels = async () => {
            setModelsLoading(true);
            try {
                // 1. 检查 AI 是否已配置
                const config = await remoteApi.getLlmConfig();
                if (!config || !config.enabled) return;

                // 2. 测试连接是否可用
                const testResult = await remoteApi.testLlmConnection({
                    ...config,
                    enabled: true,
                });
                if (!testResult || testResult.status !== 0) return;

                // 3. 从 LLM 提供商 API 动态拉取模型列表
                let models = [];
                try {
                    const modelsResult = await remoteApi.getLlmModels();
                    if (modelsResult && modelsResult.status === 0 && modelsResult.data) {
                        models = modelsResult.data.map(m => m.id || m.name).filter(Boolean);
                    }
                } catch {
                    // API 获取失败，使用静态回退
                }

                // 4. 如果 API 返回空或失败，使用静态默认列表作为回退
                if (models.length === 0) {
                    const provider = config.provider || 'openai';
                    models = FALLBACK_MODELS[provider] || [config.model];
                }

                if (!cancelled) {
                    setModelOptions(models.map(m => ({ value: m, label: m })));
                    // 默认选中已配置的模型
                    setSelectedModel(config.model || models[0] || '');
                }
            } catch {
                if (!cancelled) setModelOptions([]);
            } finally {
                if (!cancelled) setModelsLoading(false);
            }
        };

        fetchModels();
        return () => { cancelled = true; };
    }, []);

    // 模型选项可用性
    const showModelSelect = modelOptions.length > 0 && !modelsLoading;

    // 自动滚动到底部
    useEffect(() => {
        if (autoScroll && messagesEndRef.current) {
            messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
        }
    }, [messages, streamingText, autoScroll]);

    // 检测用户手动滚动
    const handleScroll = useCallback((e) => {
        const container = e.target;
        const isAtBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 60;
        setAutoScroll(isAtBottom);
    }, []);

    const handleSend = useCallback(() => {
        const text = inputValue.trim();
        if (!text || isLoading) return;
        setInputValue('');
        setAutoScroll(true);
        if (sendMessageStream) {
            sendMessageStream(text, undefined, selectedModel || undefined);
        } else {
            sendMessage(text);
        }
    }, [inputValue, isLoading, sendMessage, sendMessageStream, selectedModel]);

    const handleKeyDown = useCallback((e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    }, [handleSend]);

    // 当前时间问候语
    const getGreeting = () => {
        const hour = new Date().getHours();
        if (hour < 12) return '上午好';
        if (hour < 18) return '下午好';
        return '晚上好';
    };

    // 快捷操作
    const quickActions = [
        { icon: <SearchOutlined />, label: '消息查询', color: '#1677ff', path: '/message' },
        { icon: <DashboardOutlined />, label: '集群诊断', color: '#52c41a', path: '/cluster' },
        { icon: <AppstoreOutlined />, label: '资源管理', color: '#722ed1', path: '/topic' },
        { icon: <RobotOutlined />, label: 'AI对话', color: '#eb2f96', action: 'ai' },
    ];

    return (
        <div className="home-page">
            {/* 背景渐变装饰 */}
            <div className="home-bg-decoration"></div>

            {/* 欢迎区域 */}
            <div className="home-welcome-section">
                <h1 className="home-greeting">
                    {getGreeting()}，欢迎 🚀来到{' '}
                    <span className="gradient-text">RocketMQ Studio</span>
                </h1>
                <p className="home-subtitle">
                    跨集群·跨架构·跨云，统一管控你的RocketMQ集群
                </p>

                {/* 快捷操作胶囊按钮 */}
                <div className="quick-actions">
                    {quickActions.map((action, index) => (
                        <Button
                            key={index}
                            className="quick-action-btn"
                            icon={action.icon}
                            size="large"
                            onClick={() => {
                                if (action.path) {
                                    window.location.href = action.path;
                                }
                            }}
                            style={{ '--action-color': action.color }}
                        >
                            {action.label}
                        </Button>
                    ))}
                </div>
            </div>

            {/* AI 聊天面板 */}
            <Card className="ai-chat-card" bordered={false}>
                <div className="ai-chat-panel">
                    {/* 聊天消息区域 */}
                    <div
                        className="ai-chat-messages"
                        ref={messagesContainerRef}
                        onScroll={handleScroll}
                    >
                        {messages.length === 0 && !streamingText && (
                            <div className="ai-chat-empty">
                                <RobotOutlined className="empty-icon" />
                                <p>向 RocketMQ Bot 提问，快速诊断集群问题</p>
                                <div className="empty-suggestions">
                                    <div className="suggestion-chip" onClick={() => setInputValue('查看当前集群状态')}>
                                        📊 查看当前集群状态
                                    </div>
                                    <div className="suggestion-chip" onClick={() => setInputValue('分析消息堆积原因')}>
                                        🔍 分析消息堆积原因
                                    </div>
                                    <div className="suggestion-chip" onClick={() => setInputValue('如何优化消费者性能')}>
                                        ⚡ 如何优化消费者性能
                                    </div>
                                </div>
                            </div>
                        )}
                        {messages.map((msg, idx) => (
                            <ChatMessage key={idx} message={msg} />
                        ))}
                        {streamingText && (
                            <ChatMessage
                                message={{ role: 'assistant', content: streamingText }}
                                isStreaming
                            />
                        )}
                        <div ref={messagesEndRef} />
                    </div>

                    {/* 输入区域 */}
                    <div className="ai-chat-input-area">
                        <div className="input-toolbar">
                            {modelsLoading && (
                                <span className="model-loading">
                                    <LoadingOutlined spin style={{ fontSize: 12, marginRight: 4 }} />
                                    检测模型...
                                </span>
                            )}
                            {showModelSelect && (
                                <Select
                                    value={selectedModel}
                                    onChange={setSelectedModel}
                                    size="small"
                                    className="model-select"
                                    options={modelOptions}
                                />
                            )}
                        </div>
                        <div className="input-row">
                            <TextArea
                                ref={inputRef}
                                value={inputValue}
                                onChange={(e) => setInputValue(e.target.value)}
                                onKeyDown={handleKeyDown}
                                placeholder="向RocketMQ Bot提问，全程加密、安全、可信"
                                autoSize={{ minRows: 1, maxRows: 4 }}
                                className="chat-input"
                                disabled={isLoading}
                            />
                            <div className="input-actions">
                                <Tooltip title="工具">
                                    <Button type="text" size="small" icon={<ToolOutlined />} className="input-action-btn" />
                                </Tooltip>
                                <Tooltip title="Prompt增强">
                                    <Button type="text" size="small" icon={<ThunderboltOutlined />} className="input-action-btn" />
                                </Tooltip>
                                <Tooltip title="麦克风">
                                    <Button type="text" size="small" icon={<AudioOutlined />} className="input-action-btn" />
                                </Tooltip>
                                <Button
                                    type="primary"
                                    shape="circle"
                                    icon={<SendOutlined />}
                                    size="small"
                                    className="send-btn"
                                    onClick={handleSend}
                                    disabled={!inputValue.trim() || isLoading}
                                />
                            </div>
                        </div>
                    </div>
                </div>
            </Card>

            {/* 页脚 */}
            <div className="home-footer">
                <a href="#" className="footer-link">文档中心</a>
                <span className="footer-divider">|</span>
                <a href="#" className="footer-link">RocketMQ社区</a>
                <span className="footer-divider">|</span>
                <span className="footer-text">RocketMQ Studio出品</span>
            </div>
        </div>
    );
};

export default HomePage;