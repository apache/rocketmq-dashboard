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

import React, { useEffect } from 'react';
import { useLlm } from '../../store/context/LlmContext';
import { useOperationEvent, OperationEvents } from '../../store/context/OperationEventContext';

/**
 * OperationEventBridge - Connects OperationEventBus with LlmContext.
 *
 * Chat → Web: When a tool execution succeeds in the chat, the bridge
 * ensures the emit function is available to LlmContext for broadcasting.
 *
 * Web → Chat: When a web page emits an operation event, the bridge
 * adds it as a system message in the chat history.
 *
 * This component renders nothing and acts as a side-effect bridge.
 */
function OperationEventBridge() {
    const { setOperationEmit, addOperationRecord, isPanelOpen } = useLlm();
    const { emit, subscribe } = useOperationEvent();

    // Connect the emit function to LlmContext so confirmAction can emit events
    useEffect(() => {
        setOperationEmit(emit);
        return () => setOperationEmit(null);
    }, [emit, setOperationEmit]);

    // Web → Chat: Subscribe to all write operation events and add to chat history
    useEffect(() => {
        const writeEvents = [
            OperationEvents.TOPIC_CREATED,
            OperationEvents.TOPIC_UPDATED,
            OperationEvents.TOPIC_DELETED,
            OperationEvents.CONSUMER_CREATED,
            OperationEvents.CONSUMER_UPDATED,
            OperationEvents.CONSUMER_DELETED,
            OperationEvents.CONSUMER_OFFSET_RESET,
            OperationEvents.BROKER_CONFIG_UPDATED,
            OperationEvents.ACL_CREATED,
            OperationEvents.ACL_UPDATED,
            OperationEvents.ACL_DELETED,
            OperationEvents.MESSAGE_RESENT,
        ];

        const unsubs = writeEvents.map(eventType =>
            subscribe(eventType, (event) => {
                // Only add to chat if the event came from web (not from chat tool execution)
                // Chat-originated events have toolCallId in payload
                if (!event.payload?.toolCallId) {
                    addOperationRecord(event);
                }
            })
        );

        return () => {
            unsubs.forEach(unsub => unsub());
        };
    }, [subscribe, addOperationRecord]);

    // This component renders nothing
    return null;
}

export default OperationEventBridge;