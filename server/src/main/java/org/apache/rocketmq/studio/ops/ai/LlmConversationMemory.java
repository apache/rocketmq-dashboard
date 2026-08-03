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
package org.apache.rocketmq.studio.ops.ai;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmConversationMemory {

    private static final int MAX_CONVERSATIONS = 200;
    private static final int MAX_MESSAGES_PER_CONVERSATION = 12;
    private static final int MAX_MESSAGE_CHARS = 8_000;

    private final Map<String, Deque<LlmChatMessage>> conversations =
            new LinkedHashMap<>(16, 0.75f, true);

    public synchronized List<LlmChatMessage> appendUserAndSnapshot(String conversationId, String message) {
        LlmChatMessage userMessage = LlmChatMessage.user(normalizeContent(message));
        String id = normalizeConversationId(conversationId);
        if (!StringUtils.hasText(id)) {
            return List.of(userMessage);
        }
        Deque<LlmChatMessage> messages = conversations.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        messages.addLast(userMessage);
        trim(messages);
        evictOldestConversationIfNeeded();
        return new ArrayList<>(messages);
    }

    public synchronized void appendAssistant(String conversationId, String message) {
        String id = normalizeConversationId(conversationId);
        if (!StringUtils.hasText(id) || !StringUtils.hasText(message)) {
            return;
        }
        Deque<LlmChatMessage> messages = conversations.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        messages.addLast(LlmChatMessage.assistant(normalizeContent(message)));
        trim(messages);
        evictOldestConversationIfNeeded();
    }

    private void trim(Deque<LlmChatMessage> messages) {
        while (messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
            messages.removeFirst();
        }
    }

    private void evictOldestConversationIfNeeded() {
        while (conversations.size() > MAX_CONVERSATIONS) {
            String oldestKey = conversations.keySet().iterator().next();
            conversations.remove(oldestKey);
        }
    }

    private String normalizeConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return "";
        }
        String id = conversationId.trim();
        return id.length() <= 128 ? id : id.substring(0, 128);
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.trim();
        return normalized.length() <= MAX_MESSAGE_CHARS ? normalized : normalized.substring(0, MAX_MESSAGE_CHARS);
    }
}
