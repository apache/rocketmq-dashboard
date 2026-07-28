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
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleLlmGateway implements LlmGateway {

    private final LlmConfigService configService;
    private final OpenAiCompatibleLlmClient llmClient;
    private final LlmGatewayStub fallbackGateway;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public SseEmitter chat(ChatDTO request) {
        LlmConfigVO config = configService.getConfig();
        if (!hasRunnableConfig(config)) {
            log.debug("LLM config is incomplete; falling back to stub chat gateway");
            return fallbackGateway.chat(request);
        }
        assertSupported(config);

        SseEmitter emitter = new SseEmitter(60_000L);
        executor.execute(() -> streamChat(request, config, emitter));
        return emitter;
    }

    @Override
    public String execute(AiCommandDTO command) {
        LlmConfigVO config = configService.getConfig();
        if (!hasRunnableConfig(config)) {
            log.debug("LLM config is incomplete; falling back to stub execute gateway");
            return fallbackGateway.execute(command);
        }
        assertSupported(config);
        return llmClient.complete(config, commandPrompt(command), command == null ? null : command.getModel());
    }

    @PreDestroy
    void destroy() {
        executor.shutdownNow();
    }

    private void streamChat(ChatDTO request, LlmConfigVO config, SseEmitter emitter) {
        try {
            llmClient.stream(config, request == null ? null : request.getMessage(),
                    request == null ? null : request.getModel(),
                    token -> sendMessage(emitter, token));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (Exception exception) {
            log.error("Failed to stream LLM chat response", exception);
            emitter.completeWithError(exception);
        }
    }

    private void sendMessage(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(Map.of("text", token))));
        } catch (IOException exception) {
            throw new LlmGatewayException("Failed to send LLM stream event", exception);
        }
    }

    private boolean hasRunnableConfig(LlmConfigVO config) {
        if (config == null) {
            return false;
        }
        boolean keyRequired = !"ollama".equalsIgnoreCase(config.getProvider());
        return StringUtils.hasText(config.getApiBase())
                && StringUtils.hasText(config.getModel())
                && (!keyRequired || StringUtils.hasText(config.getApiKey()));
    }

    private void assertSupported(LlmConfigVO config) {
        if (!llmClient.supports(config)) {
            throw new LlmGatewayException("LLM provider is not supported by the OpenAI-compatible gateway");
        }
    }

    private String commandPrompt(AiCommandDTO command) {
        if (command == null) {
            return "";
        }
        String prompt = StringUtils.hasText(command.getPrompt()) ? command.getPrompt() : command.getCommand();
        if (command.getContext() == null || command.getContext().isEmpty()) {
            return prompt;
        }
        try {
            return prompt + "\n\nContext:\n" + objectMapper.writeValueAsString(command.getContext());
        } catch (JsonProcessingException exception) {
            throw new LlmGatewayException("Failed to serialize AI command context", exception);
        }
    }
}
