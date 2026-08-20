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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongFunction;

@Slf4j
@Primary
@Component
public class OpenAiCompatibleLlmGateway implements LlmGateway {

    // Keep this above the client timeout so provider timeouts can be sent as SSE errors.
    private static final long HTTP_STREAM_TIMEOUT_MILLIS = 125_000L;
    private static final long CLI_STREAM_TIMEOUT_MILLIS = 300_000L;
    private static final int MAX_CONCURRENT_CHATS = 16;

    private final LlmConfigService configService;
    private final OpenAiCompatibleLlmClient llmClient;
    private final AgentProviderRegistry agentProviders;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final LongFunction<SseEmitter> emitterFactory;
    private final Set<LlmSseSession> activeSessions = ConcurrentHashMap.newKeySet();

    @Autowired
    public OpenAiCompatibleLlmGateway(LlmConfigService configService,
                                      OpenAiCompatibleLlmClient llmClient,
                                      AgentProviderRegistry agentProviders,
                                      ObjectMapper objectMapper) {
        this(configService, llmClient, agentProviders, objectMapper, newChatExecutor(), SseEmitter::new);
    }

    OpenAiCompatibleLlmGateway(LlmConfigService configService,
                               OpenAiCompatibleLlmClient llmClient,
                               AgentProviderRegistry agentProviders,
                               ObjectMapper objectMapper,
                               ExecutorService executor,
                               LongFunction<SseEmitter> emitterFactory) {
        this.configService = configService;
        this.llmClient = llmClient;
        this.agentProviders = agentProviders;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.emitterFactory = emitterFactory;
    }

    private static ExecutorService newChatExecutor() {
        return new ThreadPoolExecutor(
                0, MAX_CONCURRENT_CHATS, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public SseEmitter chat(ChatDTO request) {
        LlmConfigVO config = configService.getConfig();
        if (!hasRunnableConfig(config)) {
            return errorEmitter(incompleteConfigException());
        }
        String engine = resolveEngine(request == null ? null : request.getEngine(), config);
        log.info("Starting AI chat: engine={}, model={}", engine,
                request == null ? null : request.getModel());
        if (isCliEngine(engine)) {
            return submitChat(CLI_STREAM_TIMEOUT_MILLIS,
                    session -> runCliChat(request, config, engine, session));
        }
        if (!llmClient.supports(config)) {
            return errorEmitter(unsupportedProviderException());
        }

        return submitChat(HTTP_STREAM_TIMEOUT_MILLIS,
                session -> streamChat(request, config, session));
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

    private void runCliChat(ChatDTO request, LlmConfigVO config, String engine, LlmSseSession session) {
        try {
            AgentProvider provider = agentProviders.forEngine(engine);
            String prompt = request == null ? null : request.getMessage();
            if (request != null && request.isEnhance() && StringUtils.hasText(prompt)) {
                prompt = enhanceAndEmit(config, provider, prompt, session);
            }
            String result = provider.complete(config, prompt, request == null ? null : request.getModel());
            sendMessage(session, result);
            finishSuccess(session);
        } catch (LlmGatewayException exception) {
            if (session.isCancelled()) {
                return;
            }
            log.warn("Agent CLI chat failed: {}", exception.getCode(), exception);
            sendError(session, exception);
        } catch (Exception exception) {
            if (session.isCancelled()) {
                return;
            }
            log.error("Failed to run agent CLI chat", exception);
            sendError(session, new LlmGatewayException(502, "llm.gateway_error",
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
    private String enhanceAndEmit(LlmConfigVO config, AgentProvider provider, String rawPrompt, LlmSseSession session)
            throws IOException {
        String metaPrompt = ENHANCE_PROMPT_TEMPLATE.formatted(rawPrompt);
        StringBuilder accumulated = new StringBuilder();
        provider.stream(config, metaPrompt, null, chunk -> {
            accumulated.append(chunk);
            emitEnhanceChunk(session, chunk);
        });
        String enhanced = cleanEnhancedPrompt(accumulated.toString());
        return StringUtils.hasText(enhanced) ? enhanced : rawPrompt;
    }

    private String enhanceAndEmitHttp(LlmConfigVO config, String rawPrompt, LlmSseSession session)
            throws IOException {
        String metaPrompt = ENHANCE_PROMPT_TEMPLATE.formatted(rawPrompt);
        StringBuilder accumulated = new StringBuilder();
        llmClient.stream(config, metaPrompt, null, chunk -> {
            accumulated.append(chunk);
            emitEnhanceChunk(session, chunk);
        });
        String enhanced = cleanEnhancedPrompt(accumulated.toString());
        return StringUtils.hasText(enhanced) ? enhanced : rawPrompt;
    }

    private void emitEnhanceChunk(LlmSseSession session, String chunk) {
        if (!StringUtils.hasText(chunk)) {
            return;
        }
        try {
            session.send(SseEmitter.event()
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
        activeSessions.forEach(LlmSseSession::cancel);
        executor.shutdownNow();
    }

    private void streamChat(ChatDTO request, LlmConfigVO config, LlmSseSession session) {
        try {
            String prompt = request == null ? null : request.getMessage();
            if (request != null && request.isEnhance() && StringUtils.hasText(prompt)) {
                prompt = enhanceAndEmitHttp(config, prompt, session);
            }
            llmClient.stream(config, prompt,
                    request == null ? null : request.getModel(),
                    token -> sendMessage(session, token));
            finishSuccess(session);
        } catch (LlmGatewayException exception) {
            if (session.isCancelled()) {
                return;
            }
            log.warn("LLM chat stream failed: {}", exception.getCode(), exception);
            sendError(session, exception);
        } catch (Exception exception) {
            if (session.isCancelled()) {
                return;
            }
            log.error("Failed to stream LLM chat response", exception);
            sendError(session, new LlmGatewayException(502, "llm.gateway_error",
                    "Failed to stream LLM chat response", "Check the LLM provider configuration and retry.", exception));
        }
    }

    private void sendMessage(LlmSseSession session, String token) {
        try {
            session.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(Map.of("text", token))));
        } catch (IOException exception) {
            throw new LlmGatewayException(500, "llm.stream.emit_failed",
                    "Failed to send LLM stream event", "Retry the chat request.", exception);
        }
    }

    private SseEmitter submitChat(long timeoutMillis, Consumer<LlmSseSession> work) {
        LlmSseSession session = newSession(timeoutMillis);
        try {
            Future<?> task = executor.submit(() -> work.accept(session));
            session.attach(task);
        } catch (RejectedExecutionException exception) {
            sendError(session, overloadedException());
        }
        return session.emitter();
    }

    private LlmSseSession newSession(long timeoutMillis) {
        LlmSseSession session = new LlmSseSession(
                emitterFactory.apply(timeoutMillis), activeSessions::remove);
        activeSessions.add(session);
        return session;
    }

    private void finishSuccess(LlmSseSession session) {
        if (!session.beginTerminal()) {
            return;
        }
        try {
            session.send(SseEmitter.event().name("done").data("[DONE]"));
            session.complete();
        } catch (IOException exception) {
            session.completeWithError(exception);
        }
    }

    private SseEmitter errorEmitter(LlmGatewayException exception) {
        LlmSseSession session = newSession(HTTP_STREAM_TIMEOUT_MILLIS);
        sendError(session, exception);
        return session.emitter();
    }

    private void sendError(LlmSseSession session, LlmGatewayException exception) {
        if (!session.beginTerminal()) {
            return;
        }
        try {
            session.send(SseEmitter.event()
                    .name("error")
                    .data(objectMapper.writeValueAsString(Map.of(
                            "status", exception.getStatusCode(),
                            "code", exception.getCode(),
                            "message", exception.getMessage(),
                            "hint", exception.getHint() == null ? "" : exception.getHint()))));
            session.send(SseEmitter.event().name("done").data("[DONE]"));
            session.complete();
        } catch (IOException ioException) {
            session.completeWithError(ioException);
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

    private LlmGatewayException overloadedException() {
        return new LlmGatewayException(503, "llm.gateway.overloaded",
                "AI chat capacity is temporarily exhausted",
                "Wait for an active chat to finish, then retry.");
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
