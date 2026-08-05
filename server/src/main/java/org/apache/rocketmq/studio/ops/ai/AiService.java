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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final LlmGateway llmGateway;
    private final McpServerRegistry mcpServerRegistry;


    public SseEmitter chat(ChatDTO request) {
        if (request == null) {
            throw new BusinessException(400, "AI chat request is required");
        }
        log.info("Chat request received: mode={}, conversationId={}", request.getMode(), request.getConversationId());
        return llmGateway.chat(request);
    }


    public AiExecuteResultVO execute(AiCommandDTO command) {
        if (command == null) {
            throw new BusinessException(400, "AI command request is required");
        }
        validateContext(command.getContext());
        log.info("Executing AI command: {}", command.getCommand());
        String result = llmGateway.execute(command);
        return AiExecuteResultVO.builder()
                .success(true)
                .result(result)
                .build();
    }


    public List<AiToolVO> listTools() {
        log.debug("Listing available AI tools");
        return mcpServerRegistry.listTools();
    }

    public List<AiToolVO> listTools(String clusterId) {
        log.debug("Listing available AI tools for cluster: {}", clusterId);
        return mcpServerRegistry.listTools(clusterId);
    }

    public Object executeTool(String name, Map<String, Object> input) {
        log.info("Executing registered AI tool: {}", name);
        return mcpServerRegistry.execute(name, input);
    }

    public String catalogVersion() {
        return mcpServerRegistry.catalogVersion();
    }

    public String catalogDigest() {
        return mcpServerRegistry.catalogDigest();
    }

    public String minimumClientVersion() {
        return mcpServerRegistry.minimumClientVersion();
    }

    private void validateContext(Map<String, Object> context) {
        if (context == null) {
            return;
        }
        if (context.size() > 32) {
            throw new BusinessException(400, "context must not contain more than 32 entries");
        }
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() > 128) {
                throw new BusinessException(400, "context keys must not exceed 128 characters");
            }
            if (String.valueOf(entry.getValue()).length() > 4096) {
                throw new BusinessException(400, "context values must not exceed 4096 characters");
            }
        }
    }
}
