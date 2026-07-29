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
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public SseEmitter chat(ChatDTO request) {
        LlmConfigVO config = configService.getConfig();
        if (!hasRunnableConfig(config)) {
            return errorEmitter(incompleteConfigException());
        }
        if (!llmClient.supports(config)) {
            return errorEmitter(unsupportedProviderException());
        }

        SseEmitter emitter = new SseEmitter(60_000L);
        executor.execute(() -> streamChat(request, config, emitter));
        return emitter;
    }

    @Override
    public String execute(AiCommandDTO command) {
        LlmConfigVO config = configService.getConfig();
        if (!hasRunnableConfig(config)) {
            throw incompleteConfigException();
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
        } catch (LlmGatewayException exception) {
            log.warn("LLM chat stream failed: {}", exception.getCode(), exception);
            sendError(emitter, exception);
        } catch (Exception exception) {
            log.error("Failed to stream LLM chat response", exception);
            sendError(emitter, new LlmGatewayException(502, "llm.gateway_error",
                    "Failed to stream LLM chat response", "Check the LLM provider configuration and retry.", exception));
        }
    }

    private void sendMessage(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(Map.of("text", token))));
        } catch (IOException exception) {
            throw new LlmGatewayException(500, "llm.stream.emit_failed",
                    "Failed to send LLM stream event", "Retry the chat request.", exception);
        }
    }

    private SseEmitter errorEmitter(LlmGatewayException exception) {
        SseEmitter emitter = new SseEmitter(60_000L);
        executor.execute(() -> sendError(emitter, exception));
        return emitter;
    }

    private void sendError(SseEmitter emitter, LlmGatewayException exception) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(objectMapper.writeValueAsString(Map.of(
                            "status", exception.getStatusCode(),
                            "code", exception.getCode(),
                            "message", exception.getMessage(),
                            "hint", exception.getHint() == null ? "" : exception.getHint()))));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (IOException ioException) {
            emitter.completeWithError(ioException);
        }
    }

    private boolean hasRunnableConfig(LlmConfigVO config) {
        return config != null && config.isReady();
    }

    private void assertSupported(LlmConfigVO config) {
        if (!llmClient.supports(config)) {
            throw unsupportedProviderException();
        }
    }

    private LlmGatewayException incompleteConfigException() {
        return new LlmGatewayException(400, "llm.config.incomplete",
                "LLM provider is not configured or enabled",
                "Configure and enable an LLM provider in Studio LLM Settings before using AI chat.");
    }

    private LlmGatewayException unsupportedProviderException() {
        return new LlmGatewayException(400, "llm.config.unsupported_provider",
                "LLM provider is not supported by the OpenAI-compatible gateway",
                "Use one of: openai, deepseek, tongyi, ollama.");
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
            throw new LlmGatewayException(500, "llm.command.context_serialize_failed",
                    "Failed to serialize AI command context", "Check the command context payload.", exception);
        }
    }
}
