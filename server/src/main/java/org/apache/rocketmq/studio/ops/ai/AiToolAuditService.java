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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiToolAuditService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 300;

    private final OperationAuditService operationAuditService;

    public void recordSuccess(String toolName, Map<String, Object> input) {
        record(toolName, input, "SUCCESS", null);
    }

    public void recordFailure(String toolName, Map<String, Object> input, Throwable error) {
        record(toolName, input, "FAILED", error == null ? null : error.getMessage());
    }

    private void record(String toolName, Map<String, Object> input, String result, String errorMessage) {
        try {
            operationAuditService.record(
                    "EXECUTE_AI_TOOL",
                    "AI_TOOL",
                    toolName,
                    clusterId(input),
                    detail(input),
                    result,
                    truncate(errorMessage));
        } catch (RuntimeException exception) {
            log.warn("Failed to record AI tool audit for {}: {}", toolName, exception.getMessage());
        }
    }

    private String detail(Map<String, Object> input) {
        return "inputKeys=" + inputKeys(input);
    }

    private List<String> inputKeys(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        return input.keySet().stream()
                .filter(StringUtils::hasText)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String clusterId(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        for (String key : List.of("cluster", "clusterId", "instanceId")) {
            Object value = input.get(key);
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
