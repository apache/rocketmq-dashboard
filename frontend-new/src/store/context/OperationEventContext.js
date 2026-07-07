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

import React, { createContext, useContext, useCallback, useRef } from 'react';

const OperationEventContext = createContext(null);

/**
 * Standard operation event types for bidirectional sync.
 * Chat → Web: tool execution success triggers page refresh.
 * Web → Chat: page write operations sync to chat history.
 */
export const OperationEvents = {
    // Topic operations
    TOPIC_CREATED: 'topic:created',
    TOPIC_UPDATED: 'topic:updated',
    TOPIC_DELETED: 'topic:deleted',

    // Consumer group operations
    CONSUMER_CREATED: 'consumer:created',
    CONSUMER_UPDATED: 'consumer:updated',
    CONSUMER_DELETED: 'consumer:deleted',
    CONSUMER_OFFSET_RESET: 'consumer:offset-reset',

    // Broker/Cluster operations
    BROKER_CONFIG_UPDATED: 'broker:config-updated',
    CLUSTER_REFRESHED: 'cluster:refreshed',

    // ACL operations
    ACL_CREATED: 'acl:created',
    ACL_UPDATED: 'acl:updated',
    ACL_DELETED: 'acl:deleted',

    // Message operations
    MESSAGE_RESENT: 'message:resent',
};

/**
 * Resource type mapping from MCP tool names to event types.
 * Used to determine which event to emit when a tool execution succeeds.
 */
export const TOOL_TO_EVENT_MAP = {
    'rmq.topic.create': OperationEvents.TOPIC_CREATED,
    'rmq.topic.update': OperationEvents.TOPIC_UPDATED,
    'rmq.topic.delete': OperationEvents.TOPIC_DELETED,
    'rmq.group.create': OperationEvents.CONSUMER_CREATED,
    'rmq.group.update': OperationEvents.CONSUMER_UPDATED,
    'rmq.group.delete': OperationEvents.CONSUMER_DELETED,
    'rmq.group.reset-offset': OperationEvents.CONSUMER_OFFSET_RESET,
    'rmq.broker.config': OperationEvents.BROKER_CONFIG_UPDATED,
    'rmq.acl.create': OperationEvents.ACL_CREATED,
    'rmq.acl.update': OperationEvents.ACL_UPDATED,
    'rmq.acl.delete': OperationEvents.ACL_DELETED,
    'rmq.message.resend': OperationEvents.MESSAGE_RESENT,
};

/**
 * Event type to display label mapping (Chinese).
 */
export const EVENT_LABELS = {
    [OperationEvents.TOPIC_CREATED]: '创建主题',
    [OperationEvents.TOPIC_UPDATED]: '更新主题配置',
    [OperationEvents.TOPIC_DELETED]: '删除主题',
    [OperationEvents.CONSUMER_CREATED]: '创建消费者组',
    [OperationEvents.CONSUMER_UPDATED]: '更新消费者组',
    [OperationEvents.CONSUMER_DELETED]: '删除消费者组',
    [OperationEvents.CONSUMER_OFFSET_RESET]: '重置消费偏移量',
    [OperationEvents.BROKER_CONFIG_UPDATED]: '更新Broker配置',
    [OperationEvents.CLUSTER_REFRESHED]: '刷新集群',
    [OperationEvents.ACL_CREATED]: '创建ACL策略',
    [OperationEvents.ACL_UPDATED]: '更新ACL策略',
    [OperationEvents.ACL_DELETED]: '删除ACL策略',
    [OperationEvents.MESSAGE_RESENT]: '重新发送消息',
};

/**
 * Resource type mapping from event type to route path.
 */
export const EVENT_TO_ROUTE = {
    [OperationEvents.TOPIC_CREATED]: '/topic',
    [OperationEvents.TOPIC_UPDATED]: '/topic',
    [OperationEvents.TOPIC_DELETED]: '/topic',
    [OperationEvents.CONSUMER_CREATED]: '/consumer',
    [OperationEvents.CONSUMER_UPDATED]: '/consumer',
    [OperationEvents.CONSUMER_DELETED]: '/consumer',
    [OperationEvents.CONSUMER_OFFSET_RESET]: '/consumer',
    [OperationEvents.BROKER_CONFIG_UPDATED]: '/cluster',
    [OperationEvents.CLUSTER_REFRESHED]: '/cluster',
    [OperationEvents.ACL_CREATED]: '/acl',
    [OperationEvents.ACL_UPDATED]: '/acl',
    [OperationEvents.ACL_DELETED]: '/acl',
    [OperationEvents.MESSAGE_RESENT]: '/message',
};

export function OperationEventProvider({ children }) {
    const listenersRef = useRef({});

    const emit = useCallback((eventType, payload = {}) => {
        const event = {
            type: eventType,
            payload,
            timestamp: Date.now(),
            id: Date.now() + Math.random(),
        };
        const listeners = listenersRef.current[eventType] || [];
        listeners.forEach(listener => {
            try {
                listener(event);
            } catch (e) {
                console.error(`OperationEvent listener error for ${eventType}:`, e);
            }
        });
    }, []);

    const subscribe = useCallback((eventType, callback) => {
        if (!listenersRef.current[eventType]) {
            listenersRef.current[eventType] = [];
        }
        listenersRef.current[eventType].push(callback);
        // Return unsubscribe function
        return () => {
            const listeners = listenersRef.current[eventType];
            if (listeners) {
                const index = listeners.indexOf(callback);
                if (index > -1) {
                    listeners.splice(index, 1);
                }
            }
        };
    }, []);

    return (
        <OperationEventContext.Provider value={{ emit, subscribe }}>
            {children}
        </OperationEventContext.Provider>
    );
}

export function useOperationEvent() {
    return useContext(OperationEventContext);
}

/**
 * Convenience hook: subscribe to an operation event and auto-cleanup.
 * @param {string} eventType - The event type from OperationEvents
 * @param {function} callback - Called with the event object when emitted
 */
export function useOnOperationEvent(eventType, callback) {
    const { subscribe } = useOperationEvent();
    const callbackRef = useRef(callback);
    callbackRef.current = callback;

    React.useEffect(() => {
        const unsub = subscribe(eventType, (event) => callbackRef.current(event));
        return unsub;
    }, [eventType, subscribe]);
}

export default OperationEventContext;