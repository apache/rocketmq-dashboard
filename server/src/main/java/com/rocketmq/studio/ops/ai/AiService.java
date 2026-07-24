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
package com.rocketmq.studio.ops.ai;

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
        log.info("Chat request received: mode={}, conversationId={}", request.getMode(), request.getConversationId());
        return llmGateway.chat(request);
    }


    public AiExecuteResultVO execute(AiCommandDTO command) {
        log.info("Executing AI command: {}", command.getCommand());
        try {
            String result = llmGateway.execute(command);
            return AiExecuteResultVO.builder()
                    .success(true)
                    .result(result)
                    .build();
        } catch (Exception e) {
            log.error("Failed to execute AI command", e);
            return AiExecuteResultVO.builder()
                    .success(false)
                    .result("Error: " + e.getMessage())
                    .build();
        }
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
}
