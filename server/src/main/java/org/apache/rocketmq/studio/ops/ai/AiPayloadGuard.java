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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Central byte budgets for user-controlled AI payloads. Character-count validation is not
 * sufficient here because provider requests, CLI arguments and tool payloads are encoded as
 * UTF-8 before leaving Studio.
 */
final class AiPayloadGuard {

    /**
     * The two halves of the outbound prompt budget. No inbound validator reads them any more — the
     * conversation surface validates its request bodies with jakarta {@code @Size} on
     * {@code AiMessageDTO} — but they stay named rather than collapsed into one literal so the number
     * a provider is allowed to receive still explains itself.
     */
    static final int MAX_MESSAGE_BYTES = 64 * 1024;
    static final int MAX_CONTEXT_BYTES = 256 * 1024;
    static final int MAX_TOOL_INPUT_BYTES = 256 * 1024;
    static final int MAX_OUTBOUND_PROMPT_BYTES = MAX_MESSAGE_BYTES + MAX_CONTEXT_BYTES + 1024;
    static final int MAX_MODEL_BYTES = 512;
    static final int MAX_TOOL_NAME_BYTES = 256;

    private AiPayloadGuard() {
    }

    static void validateToolInvocation(String name, Map<String, Object> input, ObjectMapper objectMapper) {
        requireText(name, "Tool name is required");
        requireWithin(name, MAX_TOOL_NAME_BYTES, "Tool name");
        requireJsonWithin(input, MAX_TOOL_INPUT_BYTES, "Tool input", objectMapper);
    }

    static void validateOutboundPrompt(String prompt, String model) {
        if (exceedsUtf8Limit(prompt, MAX_OUTBOUND_PROMPT_BYTES)) {
            throw requestTooLarge("LLM prompt", MAX_OUTBOUND_PROMPT_BYTES);
        }
        if (exceedsUtf8Limit(model, MAX_MODEL_BYTES)) {
            throw requestTooLarge("LLM model", MAX_MODEL_BYTES);
        }
    }

    private static void requireJsonWithin(Object value, int limitBytes, String field, ObjectMapper objectMapper) {
        if (value == null) {
            return;
        }
        final int size;
        try {
            size = objectMapper.writeValueAsBytes(value).length;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(400, field + " must be valid JSON");
        }
        if (size > limitBytes) {
            throw new BusinessException(400, field + " must not exceed " + limitBytes + " UTF-8 bytes");
        }
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, message);
        }
    }

    private static void requireWithin(String value, int limitBytes, String field) {
        if (exceedsUtf8Limit(value, limitBytes)) {
            throw new BusinessException(400, field + " must not exceed " + limitBytes + " UTF-8 bytes");
        }
    }

    private static boolean exceedsUtf8Limit(String value, int limitBytes) {
        if (value == null) {
            return false;
        }
        int bytes = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current <= 0x7f) {
                bytes++;
            } else if (current <= 0x7ff) {
                bytes += 2;
            } else if (Character.isHighSurrogate(current)
                    && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else if (Character.isSurrogate(current)) {
                // The JDK UTF-8 encoder replaces an unpaired surrogate with a one-byte '?'.
                bytes++;
            } else {
                bytes += 3;
            }
            if (bytes > limitBytes) {
                return true;
            }
        }
        return false;
    }

    private static LlmGatewayException requestTooLarge(String field, int limitBytes) {
        return new LlmGatewayException(400, "llm.request.payload_too_large",
                field + " exceeded the maximum of " + limitBytes + " UTF-8 bytes",
                "Reduce the request size and retry.");
    }
}
