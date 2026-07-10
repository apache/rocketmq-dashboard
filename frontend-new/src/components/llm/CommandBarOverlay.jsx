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

import React, { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import { Spin, Typography, Tooltip } from 'antd';
import {
    SearchOutlined,
    RobotOutlined,
    ThunderboltOutlined,
    CloseOutlined,
    WarningOutlined,
    FileTextOutlined,
    TeamOutlined,
    CloudServerOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useLlm } from '../../store/context/LlmContext';
import { useClusterCapabilities } from '../../store/context/ClusterCapabilitiesContext';
import { remoteApi } from '../../api/remoteApi/remoteApi';
import ChatMessage from './ChatMessage';
import ClusterChip from './ClusterChip';
import './CommandBarOverlay.css';

const { Text } = Typography;

/**
 * All 30 registered MCP tool definitions.
 * Source: org.apache.rocketmq.dashboard.cli.schema.ToolRegistry
 */
const MCP_TOOLS = [
    // cluster
    { name: 'rmq.cluster.list', description: '列出所有集群', risk: 'L1', resource: 'cluster' },
    { name: 'rmq.cluster.describe', description: '获取集群详细信息', risk: 'L1', resource: 'cluster' },
    // namespace
    { name: 'rmq.namespace.list', description: '列出所有命名空间', risk: 'L1', resource: 'namespace' },
    { name: 'rmq.namespace.create', description: '创建命名空间', risk: 'L2', resource: 'namespace' },
    { name: 'rmq.namespace.delete', description: '删除命名空间', risk: 'L3', resource: 'namespace' },
    // topic
    { name: 'rmq.topic.list', description: '列出所有主题', risk: 'L1', resource: 'topic' },
    { name: 'rmq.topic.describe', description: '获取主题详细信息', risk: 'L1', resource: 'topic' },
    { name: 'rmq.topic.create', description: '创建主题', risk: 'L2', resource: 'topic' },
    { name: 'rmq.topic.update', description: '更新主题配置', risk: 'L2', resource: 'topic' },
    { name: 'rmq.topic.delete', description: '删除主题', risk: 'L3', resource: 'topic' },
    // group
    { name: 'rmq.group.list', description: '列出所有消费者组', risk: 'L1', resource: 'group' },
    { name: 'rmq.group.describe', description: '获取消费者组详细信息', risk: 'L1', resource: 'group' },
    { name: 'rmq.group.create', description: '创建消费者组', risk: 'L2', resource: 'group' },
    { name: 'rmq.group.update', description: '更新消费者组配置', risk: 'L2', resource: 'group' },
    { name: 'rmq.group.reset-offset', description: '重置消费者偏移量', risk: 'L2', resource: 'group' },
    { name: 'rmq.group.delete', description: '删除消费者组', risk: 'L3', resource: 'group' },
    // message
    { name: 'rmq.message.query-by-id', description: '按消息ID查询', risk: 'L1', resource: 'message' },
    { name: 'rmq.message.query-by-time', description: '按时间范围查询消息', risk: 'L1', resource: 'message' },
    { name: 'rmq.message.resend', description: '重新发送消息', risk: 'L2', resource: 'message' },
    // client
    { name: 'rmq.client.list', description: '列出所有客户端连接', risk: 'L1', resource: 'client' },
    { name: 'rmq.client.describe', description: '获取客户端详细信息', risk: 'L1', resource: 'client' },
    // acl
    { name: 'rmq.acl.list', description: '列出所有ACL策略', risk: 'L1', resource: 'acl' },
    { name: 'rmq.acl.create', description: '创建ACL策略', risk: 'L2', resource: 'acl' },
    { name: 'rmq.acl.update', description: '更新ACL策略', risk: 'L2', resource: 'acl' },
    { name: 'rmq.acl.delete', description: '删除ACL策略', risk: 'L3', resource: 'acl' },
    // broker
    { name: 'rmq.broker.list', description: '列出所有Broker', risk: 'L1', resource: 'broker' },
    { name: 'rmq.broker.describe', description: '获取Broker详细信息', risk: 'L1', resource: 'broker' },
    { name: 'rmq.broker.config', description: '获取或更新Broker配置', risk: 'L2', resource: 'broker' },
    // metrics
    { name: 'rmq.metrics.query', description: '查询监控指标', risk: 'L1', resource: 'metrics' },
    // capabilities
    { name: 'rmq.capabilities.detect', description: '检测集群支持的功能', risk: 'L1', resource: 'capabilities' },
];

// Risk level badge colors
const RISK_COLORS = {
    L1: '#52c41a',  // green - read-only
    L2: '#faad14',  // orange - controlled mutation
    L3: '#ff4d4f',  // red - dangerous
};

// Predefined quick commands for suggestions
const QUICK_COMMANDS = [
    { icon: '📋', label: '列出所有主题', command: '列出所有主题' },
    { icon: '🔄', label: '创建一个FIFO主题', command: '创建一个FIFO主题' },
    { icon: '📊', label: '查看消费者组延迟', command: '查看消费者组的延迟情况' },
    { icon: '🔍', label: '查询消息', command: '查询消息' },
    { icon: '📈', label: '集群概览', command: '显示集群概览' },
    { icon: '⚙️', label: '查看Broker配置', command: '查看Broker配置' },
];

// Resource type icons and labels for degraded search
const RESOURCE_META = {
    topic: { icon: <FileTextOutlined />, label: 'Topic', route: '/topic', color: '#1677ff' },
    consumer: { icon: <TeamOutlined />, label: 'Consumer Group', route: '/consumer', color: '#52c41a' },
    broker: { icon: <CloudServerOutlined />, label: 'Broker', route: '/cluster', color: '#faad14' },
};

function CommandBarOverlay() {
    const { isOpen, setIsOpen, toggleOpen, isConfigured, isDegraded, messages, isLoading, sendMessage, confirmAction, cancelAction, clearChat, searchResources, prefetchResources } = useLlm();
    const { selectedCluster } = useClusterCapabilities();
    const navigate = useNavigate();
    const [inputValue, setInputValue] = useState('');
    const [mode, setMode] = useState('command');
    const [activeSuggestionIndex, setActiveSuggestionIndex] = useState(-1);
    const [showSuggestions, setShowSuggestions] = useState(false);
    const inputRef = useRef(null);
    const messagesEndRef = useRef(null);
    const panelRef = useRef(null);
    const suggestionsListRef = useRef(null);

    const [degradedResults, setDegradedResults] = useState([]);
    const [degradedLoading, setDegradedLoading] = useState(false);
    const [degradedActiveIndex, setDegradedActiveIndex] = useState(-1);
    const degradedResultsRef = useRef(null);
    const debounceTimerRef = useRef(null);

    const performDegradedSearch = useCallback(async (query) => {
        if (!query || query.trim().length < 1) {
            setDegradedResults([]);
            setDegradedLoading(false);
            return;
        }
        setDegradedLoading(true);
        try {
            const results = await searchResources(query);
            setDegradedResults(results);
        } catch {
            setDegradedResults([]);
        }
        setDegradedLoading(false);
        setDegradedActiveIndex(-1);
    }, [searchResources]);

    const handleDegradedInputChange = useCallback((value) => {
        if (debounceTimerRef.current) {
            clearTimeout(debounceTimerRef.current);
        }
        if (!value || value.trim().length < 1) {
            setDegradedResults([]);
            setDegradedActiveIndex(-1);
            return;
        }
        debounceTimerRef.current = setTimeout(() => {
            performDegradedSearch(value);
        }, 200);
    }, [performDegradedSearch]);

    const handleDegradedResultClick = useCallback((result) => {
        const meta = RESOURCE_META[result.type];
        if (!meta) return;
        if (result.type === 'topic') {
            navigate(`${meta.route}?name=${encodeURIComponent(result.name)}`);
        } else if (result.type === 'consumer') {
            navigate(`${meta.route}?name=${encodeURIComponent(result.name)}`);
        } else if (result.type === 'broker') {
            navigate(meta.route);
        }
        setIsOpen(false);
    }, [navigate, setIsOpen]);

    const handleDegradedKeyDown = useCallback((e) => {
        if (degradedResults.length === 0) return;
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            setDegradedActiveIndex(prev => prev < degradedResults.length - 1 ? prev + 1 : 0);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setDegradedActiveIndex(prev => prev > 0 ? prev - 1 : degradedResults.length - 1);
        } else if (e.key === 'Enter' && degradedActiveIndex >= 0) {
            e.preventDefault();
            handleDegradedResultClick(degradedResults[degradedActiveIndex]);
        }
    }, [degradedResults, degradedActiveIndex, handleDegradedResultClick]);

    // Scroll active degraded result into view
    useEffect(() => {
        if (degradedActiveIndex >= 0 && degradedResultsRef.current) {
            const activeItem = degradedResultsRef.current.children[degradedActiveIndex];
            if (activeItem) {
                activeItem.scrollIntoView({ block: 'nearest' });
            }
        }
    }, [degradedActiveIndex]);

    // Filter MCP tools based on input
    const filteredTools = useMemo(() => {
        const query = inputValue.trim().toLowerCase();
        if (!query) return [];
        return MCP_TOOLS.filter(tool =>
            tool.name.toLowerCase().includes(query) ||
            tool.description.toLowerCase().includes(query) ||
            tool.resource.toLowerCase().includes(query)
        ).slice(0, 8); // Limit to 8 suggestions
    }, [inputValue]);

    // Handle keyboard shortcut: Cmd/Ctrl+K to toggle, Escape to close
    useEffect(() => {
        const handleKeyDown = (e) => {
            if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
                e.preventDefault();
                toggleOpen();
            }
            if (e.key === 'Escape' && isOpen) {
                e.preventDefault();
                // If suggestions are showing, close them first; otherwise close the panel
                if (showSuggestions) {
                    setShowSuggestions(false);
                } else {
                    handleClose();
                }
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => {
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [isOpen, toggleOpen, showSuggestions]);

    // Auto-focus input when opened
    useEffect(() => {
        if (isOpen) {
            setTimeout(() => {
                inputRef.current?.focus();
            }, 100);
            // Pre-fetch resources in degraded mode
            if (!isConfigured) {
                prefetchResources();
            }
        } else {
            setMode('command');
            setInputValue('');
            setActiveSuggestionIndex(-1);
            setShowSuggestions(false);
            setDegradedResults([]);
            setDegradedActiveIndex(-1);
            if (debounceTimerRef.current) {
                clearTimeout(debounceTimerRef.current);
            }
        }
    }, [isOpen, isConfigured, prefetchResources]);

    // Scroll to bottom when new messages arrive
    useEffect(() => {
        if (mode === 'chat') {
            messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        }
    }, [messages, mode]);

    // Show suggestions when input changes in command mode
    useEffect(() => {
        if (mode === 'command' && inputValue.trim().length > 0 && filteredTools.length > 0) {
            setShowSuggestions(true);
            setActiveSuggestionIndex(-1);
        } else {
            setShowSuggestions(false);
            setActiveSuggestionIndex(-1);
        }
    }, [inputValue, filteredTools.length, mode]);

    // Scroll active suggestion into view
    useEffect(() => {
        if (activeSuggestionIndex >= 0 && suggestionsListRef.current) {
            const activeItem = suggestionsListRef.current.children[activeSuggestionIndex];
            if (activeItem) {
                activeItem.scrollIntoView({ block: 'nearest' });
            }
        }
    }, [activeSuggestionIndex]);

    const handleClose = useCallback(() => {
        setIsOpen(false);
        // Clear degraded state
        if (debounceTimerRef.current) {
            clearTimeout(debounceTimerRef.current);
        }
        setDegradedResults([]);
        setDegradedActiveIndex(-1);
    }, [setIsOpen]);

    const handleSend = useCallback((text) => {
        const trimmed = (text || inputValue).trim();
        if (!trimmed || isLoading) return;
        sendMessage(trimmed, selectedCluster);
        setInputValue('');
        setMode('chat');
        setShowSuggestions(false);
        setActiveSuggestionIndex(-1);
    }, [inputValue, isLoading, sendMessage, selectedCluster]);

    const handleInputChange = (e) => {
        const value = e.target.value;
        setInputValue(value);
        // Trigger degraded search if LLM is not configured
        if (!isConfigured) {
            handleDegradedInputChange(value);
        }
    };

    const handleInputKeyDown = (e) => {
        // In degraded mode, handle keyboard navigation for search results
        if (!isConfigured && degradedResults.length > 0) {
            handleDegradedKeyDown(e);
            if (e.defaultPrevented) return;
        }

        // Navigate suggestions with arrow keys
        if (showSuggestions && filteredTools.length > 0) {
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                setActiveSuggestionIndex(prev =>
                    prev < filteredTools.length - 1 ? prev + 1 : 0
                );
                return;
            }
            if (e.key === 'ArrowUp') {
                e.preventDefault();
                setActiveSuggestionIndex(prev =>
                    prev > 0 ? prev - 1 : filteredTools.length - 1
                );
                return;
            }
            // Select active suggestion with Enter or Tab
            if ((e.key === 'Enter' || e.key === 'Tab') && activeSuggestionIndex >= 0) {
                e.preventDefault();
                const selectedTool = filteredTools[activeSuggestionIndex];
                setInputValue(selectedTool.name + ' ');
                setShowSuggestions(false);
                setActiveSuggestionIndex(-1);
                inputRef.current?.focus();
                return;
            }
        }

        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    const handleSuggestionClick = (tool) => {
        setInputValue(tool.name + ' ');
        setShowSuggestions(false);
        setActiveSuggestionIndex(-1);
        inputRef.current?.focus();
    };

    const handleQuickCommand = (command) => {
        handleSend(command);
    };

    const handleBackdropClick = (e) => {
        if (e.target === e.currentTarget) {
            handleClose();
        }
    };

    // Click outside panel to close
    useEffect(() => {
        const handleClickOutside = (e) => {
            if (panelRef.current && !panelRef.current.contains(e.target) && isOpen) {
                handleClose();
            }
        };

        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
            return () => {
                document.removeEventListener('mousedown', handleClickOutside);
            };
        }
    }, [isOpen, handleClose]);

    if (!isOpen) return null;

    return (
        <div className="command-bar-backdrop" onClick={handleBackdropClick}>
            <div className="command-bar-panel" ref={panelRef}>
                {/* Header with input */}
                <div className="command-bar-header">
                    <div className="command-bar-input-wrapper">
                        {!isConfigured ? (
                            <SearchOutlined className="command-bar-input-icon" />
                        ) : mode === 'command' ? (
                            <SearchOutlined className="command-bar-input-icon" />
                        ) : (
                            <RobotOutlined className="command-bar-input-icon" />
                        )}
                        <input
                            ref={inputRef}
                            className="command-bar-input"
                            value={inputValue}
                            onChange={handleInputChange}
                            onKeyDown={handleInputKeyDown}
                            placeholder={
                                !isConfigured
                                    ? '搜索 Topic、Consumer、Broker...'
                                    : mode === 'command'
                                        ? '输入指令或工具名称... (如 "rmq.topic.list" 或 "列出所有主题")'
                                        : '继续对话... (Enter 发送, Shift+Enter 换行)'
                            }
                            disabled={isLoading}
                            autoComplete="off"
                            spellCheck="false"
                        />
                        {inputValue && (
                            <button
                                className="command-bar-clear-btn"
                                onClick={() => {
                                    setInputValue('');
                                    setShowSuggestions(false);
                                    setActiveSuggestionIndex(-1);
                                    inputRef.current?.focus();
                                }}
                                title="清除"
                            >
                                <CloseOutlined />
                            </button>
                        )}
                        {mode === 'chat' && (
                            <Tooltip title="新对话">
                                <button
                                    className="command-bar-new-chat-btn"
                                    onClick={() => {
                                        clearChat();
                                        setMode('command');
                                    }}
                                    title="开始新对话"
                                >
                                    <ThunderboltOutlined />
                                </button>
                            </Tooltip>
                        )}
                        <div className="command-bar-shortcut-hint">
                            <kbd>ESC</kbd>
                        </div>
                    </div>
                    <div className="command-bar-cluster-chip">
                        <ClusterChip />
                    </div>
                </div>

                {/* Autocomplete suggestions dropdown */}
                {showSuggestions && filteredTools.length > 0 && (
                    <div className="command-bar-autocomplete" ref={suggestionsListRef}>
                        <div className="command-bar-autocomplete-header">
                            <span className="command-bar-autocomplete-title">MCP 工具</span>
                            <span className="command-bar-autocomplete-count">
                                {filteredTools.length} 个匹配
                            </span>
                        </div>
                        {filteredTools.map((tool, index) => (
                            <div
                                key={tool.name}
                                className={`command-bar-autocomplete-item ${
                                    index === activeSuggestionIndex ? 'active' : ''
                                }`}
                                onClick={() => handleSuggestionClick(tool)}
                                onMouseEnter={() => setActiveSuggestionIndex(index)}
                            >
                                <div className="command-bar-autocomplete-item-left">
                                    <span
                                        className="command-bar-risk-badge"
                                        style={{ backgroundColor: RISK_COLORS[tool.risk] || '#d9d9d9' }}
                                    >
                                        {tool.risk}
                                    </span>
                                    <span className="command-bar-tool-name">{tool.name}</span>
                                </div>
                                <span className="command-bar-tool-desc">{tool.description}</span>
                            </div>
                        ))}
                        <div className="command-bar-autocomplete-footer">
                            <kbd>↑↓</kbd> 导航 <kbd>Tab</kbd>/<kbd>↵</kbd> 选择 <kbd>ESC</kbd> 关闭
                        </div>
                    </div>
                )}

                {/* Degraded mode banner */}
                {!isConfigured && (
                    <div className="command-bar-degraded-banner">
                        <WarningOutlined className="command-bar-degraded-icon" />
                        <span>AI助手不可用，已切换为全局搜索</span>
                    </div>
                )}

                {/* Degraded search results */}
                {!isConfigured && (degradedResults.length > 0 || degradedLoading) && (
                    <div className="command-bar-degraded-results" ref={degradedResultsRef}>
                        {degradedLoading && degradedResults.length === 0 ? (
                            <div className="command-bar-degraded-loading">
                                <Spin size="small" />
                                <Text type="secondary" style={{ marginLeft: 8, fontSize: 13 }}>搜索中...</Text>
                            </div>
                        ) : (
                            degradedResults.map((result, index) => {
                                const meta = RESOURCE_META[result.type];
                                return (
                                    <div
                                        key={`${result.type}-${result.name}`}
                                        className={`command-bar-degraded-item ${index === degradedActiveIndex ? 'active' : ''}`}
                                        onClick={() => handleDegradedResultClick(result)}
                                        onMouseEnter={() => setDegradedActiveIndex(index)}
                                    >
                                        <div className="command-bar-degraded-item-left">
                                            <span className="command-bar-degraded-type-icon" style={{ color: meta?.color || '#8c8c8c' }}>
                                                {meta?.icon}
                                            </span>
                                            <span className="command-bar-degraded-item-name">{result.name}</span>
                                        </div>
                                        <span className="command-bar-degraded-item-type" style={{ color: meta?.color || '#8c8c8c' }}>
                                            {meta?.label || result.type}
                                        </span>
                                    </div>
                                );
                            })
                        )}
                        {degradedResults.length > 0 && (
                            <div className="command-bar-degraded-footer">
                                <kbd>↑↓</kbd> 导航 <kbd>↵</kbd> 跳转
                            </div>
                        )}
                    </div>
                )}

                {/* Content area */}
                <div className="command-bar-content">
                    {!isConfigured ? (
                        /* Degraded mode: show help text when no results */
                        <div className="command-bar-suggestions">
                            <div className="command-bar-suggestions-title">
                                <SearchOutlined style={{ marginRight: 6 }} />
                                全局搜索
                            </div>
                            <div className="command-bar-degraded-help">
                                <p>输入关键词搜索以下资源：</p>
                                <div className="command-bar-degraded-resource-tags">
                                    <span className="command-bar-degraded-resource-tag" style={{ borderColor: '#1677ff', color: '#1677ff' }}>
                                        <FileTextOutlined /> Topic
                                    </span>
                                    <span className="command-bar-degraded-resource-tag" style={{ borderColor: '#52c41a', color: '#52c41a' }}>
                                        <TeamOutlined /> Consumer Group
                                    </span>
                                    <span className="command-bar-degraded-resource-tag" style={{ borderColor: '#faad14', color: '#faad14' }}>
                                        <CloudServerOutlined /> Broker / Cluster
                                    </span>
                                </div>
                                <p className="command-bar-degraded-hint">
                                    前往 <a onClick={() => { navigate('/llm-settings'); setIsOpen(false); }}>AI助手配置</a> 页面启用智能助手
                                </p>
                            </div>
                        </div>
                    ) : mode === 'command' && messages.length === 0 && !showSuggestions ? (
                        /* Quick commands suggestion area */
                        <div className="command-bar-suggestions">
                            <div className="command-bar-suggestions-title">
                                <RobotOutlined style={{ marginRight: 6 }} />
                                快捷指令
                            </div>
                            <div className="command-bar-suggestions-grid">
                                {QUICK_COMMANDS.map((cmd, index) => (
                                    <button
                                        key={index}
                                        className="command-bar-suggestion-item"
                                        onClick={() => handleQuickCommand(cmd.command)}
                                    >
                                        <span className="command-bar-suggestion-icon">{cmd.icon}</span>
                                        <span className="command-bar-suggestion-label">{cmd.label}</span>
                                    </button>
                                ))}
                            </div>
                            {/* MCP tools reference */}
                            <div className="command-bar-tools-ref">
                                <div className="command-bar-suggestions-title" style={{ marginTop: 16 }}>
                                    <SearchOutlined style={{ marginRight: 6 }} />
                                    可用工具 ({MCP_TOOLS.length})
                                </div>
                                <div className="command-bar-tools-list">
                                    {MCP_TOOLS.map(tool => (
                                        <span
                                            key={tool.name}
                                            className="command-bar-tool-tag"
                                            onClick={() => {
                                                setInputValue(tool.name + ' ');
                                                inputRef.current?.focus();
                                            }}
                                        >
                                            <span
                                                className="command-bar-risk-dot"
                                                style={{ backgroundColor: RISK_COLORS[tool.risk] }}
                                            />
                                            {tool.name}
                                        </span>
                                    ))}
                                </div>
                            </div>

                        </div>
                    ) : (
                        /* Chat messages area */
                        <div className="command-bar-messages">
                            {messages.length === 0 && mode === 'chat' ? (
                                <div className="command-bar-empty">
                                    <RobotOutlined style={{ fontSize: 32, color: '#bfbfbf', marginBottom: 12 }} />
                                    <Text type="secondary">
                                        向 RocketMQ 助手提问任何问题
                                    </Text>
                                </div>
                            ) : (
                                messages.map((msg) => (
                                    <ChatMessage key={msg.id} message={msg} onConfirmAction={confirmAction} onCancelAction={cancelAction} isLoading={isLoading} />
                                ))
                            )}
                            {isLoading && (
                                <div className="command-bar-loading">
                                    <Spin size="small" />
                                    <Text type="secondary" style={{ marginLeft: 8 }}>思考中...</Text>
                                </div>
                            )}
                            <div ref={messagesEndRef} />
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="command-bar-footer">
                    <div className="command-bar-footer-left">
                        <span className="command-bar-footer-hint">
                            {!isConfigured ? (
                                <>
                                    <SearchOutlined /> 全局搜索模式
                                </>
                            ) : (
                                <>
                                    <RobotOutlined /> RocketMQ AI Assistant
                                </>
                            )}
                        </span>
                    </div>
                    <div className="command-bar-footer-right">
                        <span className="command-bar-footer-hint">
                            <kbd>↵</kbd> 发送
                        </span>
                        <span className="command-bar-footer-hint">
                            <kbd>⌘K</kbd> 切换
                        </span>
                        <span className="command-bar-footer-hint">
                            <kbd>ESC</kbd> 关闭
                        </span>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default CommandBarOverlay;