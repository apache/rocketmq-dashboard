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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;


    public SseEmitter chat(ChatDTO request) {
        AiPayloadGuard.validateChat(request);
        log.info("Chat request received: mode={}, conversationId={}", request.getMode(), request.getConversationId());
        return llmGateway.chat(request);
    }


    public AiExecuteResultVO execute(AiCommandDTO command) {
        if (command == null) {
            log.warn("AI command body is missing");
            return AiExecuteResultVO.builder()
                    .success(false)
                    .result("Command request is required")
                    .build();
        }
        try {
            AiPayloadGuard.validateCommand(command, objectMapper);
        } catch (BusinessException exception) {
            return AiExecuteResultVO.builder()
                    .success(false)
                    .result(exception.getMessage())
                    .build();
        }
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
}
