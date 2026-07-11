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

import React, { createContext, useContext, useState, useCallback, useEffect, useRef, useMemo } from 'react';
import { remoteApi } from '../../api/remoteApi/remoteApi';
import { TOOL_TO_EVENT_MAP, EVENT_LABELS, EVENT_TO_ROUTE } from './OperationEventContext';

const LlmContext = createContext(null);

export function LlmProvider({ children }) {
    const [isConfigured, setIsConfigured] = useState(false);
    const [isOpen, setIsOpen] = useState(false);
    const [isPanelOpen, setIsPanelOpen] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [messages, setMessages] = useState([]);
    const [streamingText, setStreamingText] = useState('');
    const eventSourceRef = useRef(null);
    const operationEmitRef = useRef(null);
    const resourceCacheRef = useRef({ topics: null, consumers: null, brokers: null });

    const isDegraded = useMemo(() => !isConfigured, [isConfigured]);

    useEffect(() => {
        checkConfig();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        return () => {
            if (eventSourceRef.current) {
                eventSourceRef.current.close();
                eventSourceRef.current = null;
            }
        };
    }, []);

    const checkConfig = useCallback(async () => {
        try {
            const config = await remoteApi.getLlmConfig();
            setIsConfigured(config && config.enabled);
        } catch {
            setIsConfigured(false);
        }
    }, []);

    const fetchResources = useCallback(async (type) => {
        const cache = resourceCacheRef.current;
        try {
            if (type === 'topics' && !cache.topics) {
                const res = await remoteApi.queryTopicList();
                const list = res?.data?.topicList || [];
                cache.topics = list.map(t => (typeof t === 'string' ? t : t.topic)).filter(Boolean);
            }
            if (type === 'consumers' && !cache.consumers) {
                const res = await remoteApi.queryConsumerGroupList(true, '');
                const raw = res?.data;
                const list = Array.isArray(raw) ? raw : (raw?.groupList || []);
                cache.consumers = list.map(g => (typeof g === 'string' ? g : g.group)).filter(Boolean);
            }
            if (type === 'brokers' && !cache.brokers) {
                const clusterData = await new Promise((resolve) => {
                    remoteApi.queryClusterList((data) => resolve(data));
                });
                const brokerNames = new Set();
                const clusterList = clusterData?.data || clusterData;
                if (Array.isArray(clusterList)) {
                    clusterList.forEach(c => {
                        if (c.brokerNameList) {
                            c.brokerNameList.forEach(b => brokerNames.add(b));
                        }
                        if (c.clusterName) brokerNames.add(c.clusterName);
                    });
                } else if (clusterList && typeof clusterList === 'object') {
                    Object.values(clusterList).forEach(c => {
                        if (c && c.brokerNameList) {
                            c.brokerNameList.forEach(b => brokerNames.add(b));
                        }
                    });
                }
                cache.brokers = Array.from(brokerNames);
            }
        } catch {
            // Silently ignore fetch errors in degraded mode
        }
        return cache[type] || [];
    }, []);

    const searchResources = useCallback(async (query) => {
        if (!query || query.trim().length < 1) return [];
        const searchTerm = query.trim().toLowerCase();
        const results = [];
        try {
            const [topics, consumers, brokers] = await Promise.all([
                fetchResources('topics'),
                fetchResources('consumers'),
                fetchResources('brokers'),
            ]);
            const matchedTopics = topics.filter(t => t.toLowerCase().includes(searchTerm)).slice(0, 6);
            if (matchedTopics.length > 0) {
                results.push(...matchedTopics.map(name => ({ type: 'topic', name })));
            }
            const matchedConsumers = consumers.filter(c => c.toLowerCase().includes(searchTerm)).slice(0, 6);
            if (matchedConsumers.length > 0) {
                results.push(...matchedConsumers.map(name => ({ type: 'consumer', name })));
            }
            const matchedBrokers = brokers.filter(b => b.toLowerCase().includes(searchTerm)).slice(0, 6);
            if (matchedBrokers.length > 0) {
                results.push(...matchedBrokers.map(name => ({ type: 'broker', name })));
            }
        } catch {
            // Silently ignore errors
        }
        return results.slice(0, 12);
    }, [fetchResources]);

    const prefetchResources = useCallback(() => {
        fetchResources('topics');
        fetchResources('consumers');
        fetchResources('brokers');
    }, [fetchResources]);

    const toggleOpen = useCallback(() => {
        setIsOpen(prev => !prev);
    }, []);

    const togglePanelOpen = useCallback(() => {
        setIsPanelOpen(prev => !prev);
    }, []);

    // Abort any in-progress SSE stream
    const abortStream = useCallback(() => {
        if (eventSourceRef.current) {
            eventSourceRef.current.close();
            eventSourceRef.current = null;
        }
        setStreamingText('');
    }, []);

    // Non-streaming send (fallback)
    const sendMessage = useCallback(async (text, cluster, skipUserMsg) => {
        if (!skipUserMsg) {
            const userMsg = { role: 'user', content: text, id: Date.now() };
            setMessages(prev => [...prev, userMsg]);
        }
        setIsLoading(true);
        try {
            const history = messages.map(m => {
                const h = { role: m.role, content: m.content };
                if (m.role === 'assistant' && m.toolCalls) {
                    h.toolCalls = m.toolCalls;
                }
                return h;
            });
            const result = await remoteApi.sendLlmMessage(text, cluster, history);
            const assistantMsg = {
                role: 'assistant',
                content: result.content,
                toolCalls: result.toolCalls,
                viewHint: result.viewHint,
                degraded: result.degraded,
                id: Date.now() + 1
            };
            setMessages(prev => [...prev, assistantMsg]);
            return assistantMsg;
        } catch (error) {
            const errorMsg = {
                role: 'error',
                content: error.message || 'Request failed, please try again later',
                id: Date.now() + 1
            };
            setMessages(prev => [...prev, errorMsg]);
            return errorMsg;
        } finally {
            setIsLoading(false);
        }
    }, [messages]);

    // SSE streaming send
    const sendMessageStream = useCallback((text, cluster, modelOverride) => {
        // Close any existing stream
        if (eventSourceRef.current) {
            eventSourceRef.current.close();
        }

        console.log('[LLM] sendMessageStream called:', text);
        const userMsg = { role: 'user', content: text, id: Date.now() };
        setMessages(prev => [...prev, userMsg]);
        setIsLoading(true);
        setStreamingText('');

        const assistantMsgId = Date.now() + 1;
        let accumulatedText = '';
        let toolCalls = [];
        let viewHint = null;
        let isCompleted = false;
        let hasFallbacked = false;

        // Dedup helper: prevents any handler from adding the same message twice
        const saveAssistantMessage = (msg) => {
            setMessages(prev => {
                if (prev.some(m => m.id === assistantMsgId)) return prev;
                return [...prev, { ...msg, id: assistantMsgId }];
            });
        };

        // Include toolCalls in history for multi-turn context
        const history = messages.map(m => {
            const h = { role: m.role, content: m.content };
            if (m.role === 'assistant' && m.toolCalls) {
                h.toolCalls = m.toolCalls;
            }
            return h;
        });
        const es = remoteApi.sendLlmMessageStream(text, cluster, history, modelOverride);
        eventSourceRef.current = es;

        // Safety timeout: reset loading after 90s if stream hangs
        const safetyTimer = setTimeout(() => {
            if (isCompleted || hasFallbacked) return;
            console.log('[LLM] Safety timeout — forcing reset');
            isCompleted = true;
            hasFallbacked = true;
            es.close();
            eventSourceRef.current = null;
            setStreamingText('');
            setIsLoading(false);
            if (accumulatedText || toolCalls.length > 0) {
                saveAssistantMessage({
                    role: 'assistant',
                    content: accumulatedText + '\n\n⚠️ 响应超时，数据可能不完整',
                    toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
                    viewHint: viewHint
                });
            } else {
                setMessages(prev => [...prev, {
                    role: 'error',
                    content: '请求超时，请重试',
                    id: assistantMsgId
                }]);
            }
        }, 90000);

        es.addEventListener('token', (event) => {
            try {
                const data = JSON.parse(event.data);
                accumulatedText += data.content || data.text || '';
                setStreamingText(accumulatedText);
            } catch {
                // If not JSON, treat as plain text token
                accumulatedText += event.data;
                setStreamingText(accumulatedText);
            }
        });

        es.addEventListener('tool_call', (event) => {
            try {
                const data = JSON.parse(event.data);
                // Backend sends an array of tool calls
                if (Array.isArray(data)) {
                    toolCalls.push(...data);
                } else {
                    toolCalls.push(data);
                }
            } catch {
                // ignore parse errors
            }
        });

        es.addEventListener('view_hint', (event) => {
            try {
                const data = JSON.parse(event.data);
                viewHint = data.hint || data.viewHint || data;
            } catch {
                viewHint = event.data;
            }
        });

        es.addEventListener('done', () => {
            clearTimeout(safetyTimer);
            console.log('[LLM] SSE done event received, content:', accumulatedText?.length, 'toolCalls:', toolCalls.length);
            // If onerror already saved a partial message, do NOT duplicate
            if (hasFallbacked) {
                console.log('[LLM] SSE done skipped — onerror already handled');
                eventSourceRef.current = null;
                return;
            }
            // Mark completed BEFORE any processing to prevent error/onerror race
            isCompleted = true;
            eventSourceRef.current = null;
            setIsLoading(false);
            setStreamingText('');
            // Close the EventSource after done to prevent auto-reconnect
            try { es.close(); } catch (e) { /* ignore */ }
            // Finalize the assistant message
            saveAssistantMessage({
                role: 'assistant',
                content: accumulatedText,
                toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
                viewHint: viewHint
            });
        });

        es.addEventListener('error', (event) => {
            clearTimeout(safetyTimer);
            // Skip if stream already completed normally
            if (isCompleted || hasFallbacked) return;

            // If we have accumulated text, save it as a partial message
            if (accumulatedText || toolCalls.length > 0) {
                saveAssistantMessage({
                    role: 'assistant',
                    content: accumulatedText,
                    toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
                    viewHint: viewHint
                });
            } else {
                saveAssistantMessage({
                    role: 'error',
                    content: event.data || 'Connection lost, please try again later'
                });
            }
            setStreamingText('');
            setIsLoading(false);
            eventSourceRef.current = null;
        });

        es.onerror = () => {
            clearTimeout(safetyTimer);
            console.log('[LLM] SSE onerror fired, isCompleted:', isCompleted, 'hasFallbacked:', hasFallbacked);
            // Always close to stop EventSource auto-reconnect
            es.close();
            eventSourceRef.current = null;

            // Skip if stream completed normally or already handled
            if (isCompleted || hasFallbacked) return;
            hasFallbacked = true;

            // If we have streaming data or tool calls, save it as a completed message
            if (accumulatedText || toolCalls.length > 0) {
                console.log('[LLM] SSE onerror -> saving partial message');
                setStreamingText('');
                setIsLoading(false);
                saveAssistantMessage({
                    role: 'assistant',
                    content: accumulatedText,
                    toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
                    viewHint: viewHint
                });
                return;
            }

            // Only fallback if we got absolutely nothing — the stream truly failed
            console.log('[LLM] SSE onerror -> triggering fallback sendMessage');
            setStreamingText('');
            setIsLoading(false);
            sendMessage(text, cluster, true);
        };
    }, [messages, sendMessage]);

    // Find the tool name from messages for a given toolCallId
    const findToolName = useCallback((toolCallId) => {
        for (const msg of messages) {
            if (msg.role === 'assistant' && msg.toolCalls) {
                const tc = msg.toolCalls.find(t => (t.id === toolCallId || t.toolCallId === toolCallId));
                if (tc) return tc.name || tc.toolName;
            }
            if (msg.role === 'tool' && (msg.id === toolCallId || msg.toolCallId === toolCallId)) {
                return msg.name || msg.toolName;
            }
        }
        return null;
    }, [messages]);

    const confirmAction = useCallback(async (toolCallId) => {
        setIsLoading(true);
        try {
            // Update the tool call status to 'running' in messages
            setMessages(prev => prev.map(msg => {
                if (msg.role === 'tool' && (msg.id === toolCallId || msg.toolCallId === toolCallId)) {
                    return { ...msg, status: 'running' };
                }
                if (msg.role === 'assistant' && msg.toolCalls) {
                    return {
                        ...msg,
                        toolCalls: msg.toolCalls.map(tc =>
                            (tc.id === toolCallId || tc.toolCallId === toolCallId)
                                ? { ...tc, status: 'running' }
                                : tc
                        )
                    };
                }
                return msg;
            }));
            const result = await remoteApi.confirmLlmAction(toolCallId);
            // Update the tool call status to 'completed' after success
            setMessages(prev => prev.map(msg => {
                if (msg.role === 'tool' && (msg.id === toolCallId || msg.toolCallId === toolCallId)) {
                    return { ...msg, status: 'completed', result: result };
                }
                if (msg.role === 'assistant' && msg.toolCalls) {
                    return {
                        ...msg,
                        toolCalls: msg.toolCalls.map(tc =>
                            (tc.id === toolCallId || tc.toolCallId === toolCallId)
                                ? { ...tc, status: 'completed', result: result }
                                : tc
                        )
                    };
                }
                return msg;
            }));

            // Chat → Web: Emit operation event when tool execution succeeds
            const toolName = findToolName(toolCallId);
            if (toolName && operationEmitRef.current) {
                const eventType = TOOL_TO_EVENT_MAP[toolName];
                if (eventType) {
                    const eventLabel = EVENT_LABELS[eventType] || toolName;
                    operationEmitRef.current(eventType, {
                        toolName,
                        toolCallId,
                        result,
                        label: eventLabel,
                        route: EVENT_TO_ROUTE[eventType],
                    });
                }
            }

            return result;
        } catch (error) {
            // Update the tool call status to 'failed' on error
            setMessages(prev => prev.map(msg => {
                if (msg.role === 'tool' && (msg.id === toolCallId || msg.toolCallId === toolCallId)) {
                    return { ...msg, status: 'failed', result: error.message || 'Execution failed' };
                }
                if (msg.role === 'assistant' && msg.toolCalls) {
                    return {
                        ...msg,
                        toolCalls: msg.toolCalls.map(tc =>
                            (tc.id === toolCallId || tc.toolCallId === toolCallId)
                                ? { ...tc, status: 'failed', result: error.message || 'Execution failed' }
                                : tc
                        )
                    };
                }
                return msg;
            }));
            throw error;
        } finally {
            setIsLoading(false);
        }
    }, [findToolName]);

    const cancelAction = useCallback((toolCallId) => {
        // Update the tool call status to 'rejected' (cancelled by user)
        setMessages(prev => prev.map(msg => {
            if (msg.role === 'tool' && (msg.id === toolCallId || msg.toolCallId === toolCallId)) {
                return { ...msg, status: 'rejected', result: 'Operation cancelled by user' };
            }
            if (msg.role === 'assistant' && msg.toolCalls) {
                return {
                    ...msg,
                    toolCalls: msg.toolCalls.map(tc =>
                        (tc.id === toolCallId || tc.toolCallId === toolCallId)
                            ? { ...tc, status: 'rejected', result: 'Operation cancelled by user' }
                            : tc
                    )
                };
            }
            return msg;
        }));
    }, []);

    const clearChat = useCallback(() => {
        abortStream();
        setMessages([]);
    }, [abortStream]);

    // Allow OperationEventBridge to set the emit function
    const setOperationEmit = useCallback((emitFn) => {
        operationEmitRef.current = emitFn;
    }, []);

    // Web → Chat: add an operation record as a system message
    const addOperationRecord = useCallback((event) => {
        const systemMsg = {
            role: 'system',
            content: `Operation: ${event.payload?.label || event.type}`,
            eventType: event.type,
            eventPayload: event.payload,
            eventTimestamp: event.timestamp,
            id: event.id || Date.now(),
        };
        setMessages(prev => [...prev, systemMsg]);
    }, []);

    return (
        <LlmContext.Provider value={{
            isConfigured, setIsConfigured, isDegraded,
            isOpen, setIsOpen, toggleOpen,
            isPanelOpen, setIsPanelOpen, togglePanelOpen,
            isLoading, messages, streamingText,
            sendMessage, sendMessageStream, confirmAction, cancelAction, clearChat, checkConfig,
            abortStream, setOperationEmit, addOperationRecord,
            fetchResources, searchResources, prefetchResources
        }}>
            {children}
        </LlmContext.Provider>
    );
}

export function useLlm() { return useContext(LlmContext); }
export default LlmContext;