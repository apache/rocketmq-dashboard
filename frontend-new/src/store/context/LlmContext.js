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

import React, { createContext, useContext, useState, useCallback } from 'react';
import { remoteApi } from '../../api/remoteApi/remoteApi';

const LlmContext = createContext(null);

export function LlmProvider({ children }) {
    const [isConfigured, setIsConfigured] = useState(false);
    const [isOpen, setIsOpen] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [messages, setMessages] = useState([]);

    const checkConfig = useCallback(async () => {
        try {
            const config = await remoteApi.getLlmConfig();
            setIsConfigured(config && config.enabled);
        } catch { setIsConfigured(false); }
    }, []);

    const sendMessage = useCallback(async (text, cluster) => {
        const userMsg = { role: 'user', content: text, id: Date.now() };
        setMessages(prev => [...prev, userMsg]);
        setIsLoading(true);
        try {
            const history = messages.map(m => ({ role: m.role, content: m.content }));
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
        } finally {
            setIsLoading(false);
        }
    }, [messages]);

    const confirmAction = useCallback(async (toolCallId) => {
        setIsLoading(true);
        try {
            return await remoteApi.confirmLlmAction(toolCallId);
        } finally { setIsLoading(false); }
    }, []);

    const clearChat = useCallback(() => setMessages([]), []);

    return (
        <LlmContext.Provider value={{
            isConfigured, setIsConfigured, isOpen, setIsOpen,
            isLoading, messages, sendMessage, confirmAction, clearChat, checkConfig
        }}>
            {children}
        </LlmContext.Provider>
    );
}

export function useLlm() { return useContext(LlmContext); }
export default LlmContext;
