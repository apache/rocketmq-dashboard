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
import { Modal } from 'antd';
import { useLlm } from '../../store/context/LlmContext';
import { useClusterCapabilities } from '../../store/context/ClusterCapabilitiesContext';
import ChatPanel from './ChatPanel';

function CommandBarOverlay() {
    const { isOpen, setIsOpen, messages, isLoading, sendMessage } = useLlm();
    const { selectedCluster } = useClusterCapabilities();

    useEffect(() => {
        const handleKeyDown = (e) => {
            // Cmd+K (Mac) or Ctrl+K (Windows/Linux)
            if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
                e.preventDefault();
                // Don't trigger when typing in other inputs
                const tag = e.target.tagName.toLowerCase();
                const isEditable = tag === 'input' || tag === 'textarea' || tag === 'select' || e.target.isContentEditable;
                if (!isEditable) {
                    setIsOpen(true);
                }
            }
            // Escape to close
            if (e.key === 'Escape' && isOpen) {
                setIsOpen(false);
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => {
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [isOpen, setIsOpen]);

    const handleSend = (text) => {
        sendMessage(text, selectedCluster);
    };

    return (
        <Modal
            title="RocketMQ Assistant"
            open={isOpen}
            onCancel={() => setIsOpen(false)}
            footer={null}
            width="80vw"
            style={{ top: 40 }}
            styles={{
                body: {
                    padding: 0,
                    height: '70vh',
                },
            }}
            destroyOnClose={false}
        >
            <ChatPanel
                messages={messages}
                isLoading={isLoading}
                onSend={handleSend}
            />
        </Modal>
    );
}

export default CommandBarOverlay;
