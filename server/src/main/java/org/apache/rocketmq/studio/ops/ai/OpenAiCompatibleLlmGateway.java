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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
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
    private final AgentProviderRegistry agentProviders;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public SseEmitter chat(ChatDTO request) {
        LlmConfigVO config = configService.getConfig();
        if (!hasRunnableConfig(config)) {
            return errorEmitter(incompleteConfigException());
        }
        String engine = resolveEngine(request == null ? null : request.getEngine(), config);
        if (isCliEngine(engine)) {
            SseEmitter emitter = new SseEmitter(300_000L);
            executor.execute(() -> runCliChat(request, config, engine, emitter));
            return emitter;
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
        String engine = resolveEngine(command == null ? null : command.getEngine(), config);
        if (isCliEngine(engine)) {
            AgentProvider provider = agentProviders.forEngine(engine);
            return provider.complete(config, commandPrompt(command), command == null ? null : command.getModel());
        }
        assertSupported(config);
        return llmClient.complete(config, commandPrompt(command), command == null ? null : command.getModel());
    }

    /** Request-level engine (per-user preference) overrides the global config. */
    private String resolveEngine(String requestEngine, LlmConfigVO config) {
        String engine = StringUtils.hasText(requestEngine)
                ? requestEngine.trim().toLowerCase(Locale.ROOT) : null;
        if (engine == null) {
            return config.normalizeEngine();
        }
        return switch (engine) {
            case LlmConfigVO.ENGINE_HTTP, LlmConfigVO.ENGINE_CLAUDE_CODE, LlmConfigVO.ENGINE_QODER -> engine;
            default -> config.normalizeEngine();
        };
    }

    private boolean isCliEngine(String engine) {
        return !LlmConfigVO.ENGINE_HTTP.equalsIgnoreCase(engine);
    }

    private void runCliChat(ChatDTO request, LlmConfigVO config, String engine, SseEmitter emitter) {
        try {
            AgentProvider provider = agentProviders.forEngine(engine);
            String prompt = request == null ? null : request.getMessage();
            if (request != null && request.isEnhance() && StringUtils.hasText(prompt)) {
                prompt = enhanceAndEmit(config, provider, prompt, emitter);
            }
            String result = provider.complete(config, prompt, request == null ? null : request.getModel());
            sendMessage(emitter, result);
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (LlmGatewayException exception) {
            log.warn("Agent CLI chat failed: {}", exception.getCode(), exception);
            sendError(emitter, exception);
        } catch (Exception exception) {
            log.error("Failed to run agent CLI chat", exception);
            sendError(emitter, new LlmGatewayException(502, "llm.gateway_error",
                    "Failed to run agent CLI chat", "Check the agent provider configuration and retry.", exception));
        }
    }

    private static final String ENHANCE_PROMPT_TEMPLATE = loadEnhancePromptTemplate();

    private static String loadEnhancePromptTemplate() {
        try (InputStream in = OpenAiCompatibleLlmGateway.class
                .getResourceAsStream("/prompts/enhance-prompt.txt")) {
            if (in == null) {
                throw new IllegalStateException("prompts/enhance-prompt.txt is missing on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load prompts/enhance-prompt.txt", exception);
        }
    }

    /** Streams the prompt rewrite via the given provider, emitting per-chunk "enhance" SSE events. */
    private String enhanceAndEmit(LlmConfigVO config, AgentProvider provider, String rawPrompt, SseEmitter emitter)
            throws IOException {
        String metaPrompt = ENHANCE_PROMPT_TEMPLATE.formatted(rawPrompt);
        StringBuilder accumulated = new StringBuilder();
        provider.stream(config, metaPrompt, null, chunk -> {
            accumulated.append(chunk);
            emitEnhanceChunk(emitter, chunk);
        });
        String enhanced = cleanEnhancedPrompt(accumulated.toString());
        return StringUtils.hasText(enhanced) ? enhanced : rawPrompt;
    }

    private String enhanceAndEmitHttp(LlmConfigVO config, String rawPrompt, SseEmitter emitter)
            throws IOException {
        String metaPrompt = ENHANCE_PROMPT_TEMPLATE.formatted(rawPrompt);
        StringBuilder accumulated = new StringBuilder();
        llmClient.stream(config, metaPrompt, null, chunk -> {
            accumulated.append(chunk);
            emitEnhanceChunk(emitter, chunk);
        });
        String enhanced = cleanEnhancedPrompt(accumulated.toString());
        return StringUtils.hasText(enhanced) ? enhanced : rawPrompt;
    }

    private void emitEnhanceChunk(SseEmitter emitter, String chunk) {
        if (!StringUtils.hasText(chunk)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("enhance")
                    .data(objectMapper.writeValueAsString(Map.of("delta", chunk))));
        } catch (IOException exception) {
            throw new LlmGatewayException(500, "llm.stream.emit_failed",
                    "Failed to send enhance event", "Retry the chat request.", exception);
        }
    }

    private String cleanEnhancedPrompt(String enhanced) {
        if (enhanced == null) {
            return "";
        }
        String cleaned = enhanced.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
        }
        return cleaned;
    }

    @PreDestroy
    void destroy() {
        executor.shutdownNow();
    }

    private void streamChat(ChatDTO request, LlmConfigVO config, SseEmitter emitter) {
        try {
            String prompt = request == null ? null : request.getMessage();
            if (request != null && request.isEnhance() && StringUtils.hasText(prompt)) {
                prompt = enhanceAndEmitHttp(config, prompt, emitter);
            }
            llmClient.stream(config, prompt,
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
