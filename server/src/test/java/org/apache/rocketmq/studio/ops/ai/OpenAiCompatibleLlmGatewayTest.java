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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiCompatibleLlmGatewayTest {

    private final LlmConfigService configService = mock(LlmConfigService.class);
    private final OpenAiCompatibleLlmClient llmClient = mock(OpenAiCompatibleLlmClient.class);
    private final OpenAiCompatibleLlmGateway gateway = new OpenAiCompatibleLlmGateway(
            configService, llmClient, new AgentProviderRegistry(java.util.List.of()), new ObjectMapper());

    @Test
    void chatShouldRejectIncompleteConfigWithoutCallingProvider() {
        ChatDTO request = ChatDTO.builder().message("hello").build();
        when(configService.getConfig()).thenReturn(config("openai", ""));

        SseEmitter result = gateway.chat(request);

        assertThat(result).isNotNull();
        verify(llmClient, never()).supports(any(LlmConfigVO.class));
    }

    @Test
    void httpChatAllowsSlowProvidersToStartStreamingTest() throws Exception {
        ExecutorService executor = singleChatExecutor();
        List<RecordingSseEmitter> emitters = new CopyOnWriteArrayList<>();
        OpenAiCompatibleLlmGateway testedGateway = gateway(executor, emitters);
        LlmConfigVO config = config("openai", "sk-test");
        when(configService.getConfig()).thenReturn(config);
        when(llmClient.supports(config)).thenReturn(true);
        try {
            testedGateway.chat(ChatDTO.builder().message("hello").build());

            assertThat(emitters).hasSize(1);
            assertThat(emitters.get(0).timeoutMillis).isEqualTo(TimeUnit.SECONDS.toMillis(125));
        } finally {
            testedGateway.destroy();
        }
    }

    @Test
    void executeShouldRejectIncompleteConfig() {
        when(configService.getConfig()).thenReturn(config("openai", ""));

        assertThatThrownBy(() -> gateway.execute(AiCommandDTO.builder().command("hello").build()))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider is not configured or enabled")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(400);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.config.incomplete");
                    assertThat(gatewayException.getHint()).contains("LLM Settings");
                });
        verify(llmClient, never()).complete(any(LlmConfigVO.class), any(String.class), any());
    }

    @Test
    void executeShouldUseRealClientWhenConfigIsComplete() {
        LlmConfigVO config = config("openai", "sk-test");
        AiCommandDTO command = AiCommandDTO.builder()
                .command("list topics")
                .prompt("diagnose")
                .model("gpt-4o-mini")
                .context(Map.of("cluster", "prod"))
                .build();
        when(configService.getConfig()).thenReturn(config);
        when(llmClient.supports(config)).thenReturn(true);
        when(llmClient.complete(eq(config), any(String.class), eq("gpt-4o-mini"))).thenReturn("done");

        String result = gateway.execute(command);

        assertThat(result).isEqualTo("done");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(eq(config), promptCaptor.capture(), eq("gpt-4o-mini"));
        assertThat(promptCaptor.getValue()).contains("diagnose");
        assertThat(promptCaptor.getValue()).contains("\"cluster\":\"prod\"");
    }

    @Test
    void executeShouldRejectConfiguredUnsupportedProvider() {
        LlmConfigVO config = config("bedrock", "aws-key");
        when(configService.getConfig()).thenReturn(config);
        when(llmClient.supports(config)).thenReturn(false);

        assertThatThrownBy(() -> gateway.execute(AiCommandDTO.builder().command("hello").build()))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider is not supported by the OpenAI-compatible gateway")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(400);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.config.unsupported_provider");
                    assertThat(gatewayException.getHint()).contains("openai");
                });
    }

    @Test
    void saturatedGatewayReturnsStructuredOverloadWithoutRunningProviderOnCaller() throws Exception {
        ExecutorService executor = singleChatExecutor();
        List<RecordingSseEmitter> emitters = new CopyOnWriteArrayList<>();
        OpenAiCompatibleLlmGateway testedGateway = gateway(executor, emitters);
        LlmConfigVO config = config("openai", "sk-test");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(configService.getConfig()).thenReturn(config);
        when(llmClient.supports(config)).thenReturn(true);
        doAnswer(invocation -> {
            started.countDown();
            release.await();
            return null;
        }).when(llmClient).stream(any(), any(), any(), any());
        try {
            testedGateway.chat(ChatDTO.builder().message("first").build());
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            CompletableFuture<SseEmitter> overloaded = CompletableFuture.supplyAsync(
                    () -> testedGateway.chat(ChatDTO.builder().message("second").build()));
            SseEmitter result = overloaded.get(1, TimeUnit.SECONDS);

            assertThat(result).isSameAs(emitters.get(1));
            assertThat(emitters.get(1).eventText())
                    .contains("event:error", "llm.gateway.overloaded", "503", "event:done", "[DONE]");
            assertThat(emitters.get(1).completed).isTrue();
        } finally {
            release.countDown();
            testedGateway.destroy();
        }
    }

    @Test
    void downstreamTimeoutInterruptsTheRunningProviderWithoutSendingAnotherTerminalEvent() throws Exception {
        ExecutorService executor = singleChatExecutor();
        List<RecordingSseEmitter> emitters = new CopyOnWriteArrayList<>();
        OpenAiCompatibleLlmGateway testedGateway = gateway(executor, emitters);
        LlmConfigVO config = config("openai", "sk-test");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(configService.getConfig()).thenReturn(config);
        when(llmClient.supports(config)).thenReturn(true);
        doAnswer(invocation -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(llmClient).stream(any(), any(), any(), any());
        try {
            testedGateway.chat(ChatDTO.builder().message("hello").build());
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            emitters.get(0).triggerTimeout();

            assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(emitters.get(0).sentEvents).isEmpty();
        } finally {
            testedGateway.destroy();
        }
    }

    @Test
    void gatewayShutdownInterruptsActiveProviderWork() throws Exception {
        ExecutorService executor = singleChatExecutor();
        List<RecordingSseEmitter> emitters = new CopyOnWriteArrayList<>();
        OpenAiCompatibleLlmGateway testedGateway = gateway(executor, emitters);
        LlmConfigVO config = config("openai", "sk-test");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(configService.getConfig()).thenReturn(config);
        when(llmClient.supports(config)).thenReturn(true);
        doAnswer(invocation -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(llmClient).stream(any(), any(), any(), any());

        testedGateway.chat(ChatDTO.builder().message("hello").build());
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        testedGateway.destroy();

        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(emitters.get(0).sentEvents).isEmpty();
    }

    @Test
    void successfulAndFailedStreamsEmitOneTerminalSequence() throws Exception {
        ExecutorService executor = singleChatExecutor();
        List<RecordingSseEmitter> emitters = new CopyOnWriteArrayList<>();
        OpenAiCompatibleLlmGateway testedGateway = gateway(executor, emitters);
        LlmConfigVO config = config("openai", "sk-test");
        when(configService.getConfig()).thenReturn(config);
        when(llmClient.supports(config)).thenReturn(true);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> consumer = invocation.getArgument(3, Consumer.class);
            consumer.accept("hello");
            return null;
        }).doThrow(new LlmGatewayException(502, "llm.provider.failed", "provider failed", "retry"))
                .when(llmClient).stream(any(), any(), any(), any());
        try {
            testedGateway.chat(ChatDTO.builder().message("success").build());
            assertThat(emitters.get(0).completedLatch.await(5, TimeUnit.SECONDS)).isTrue();
            testedGateway.chat(ChatDTO.builder().message("failure").build());
            assertThat(emitters.get(1).completedLatch.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(emitters.get(0).eventText())
                    .contains("event:message", "hello", "event:done", "[DONE]")
                    .doesNotContain("event:error");
            assertThat(emitters.get(1).eventText())
                    .contains("event:error", "llm.provider.failed", "event:done", "[DONE]");
            assertThat(emitters.get(0).eventCount("event:done")).isEqualTo(1);
            assertThat(emitters.get(1).eventCount("event:done")).isEqualTo(1);
        } finally {
            testedGateway.destroy();
        }
    }

    private OpenAiCompatibleLlmGateway gateway(ExecutorService executor,
                                                List<RecordingSseEmitter> emitters) {
        return new OpenAiCompatibleLlmGateway(
                configService, llmClient, new AgentProviderRegistry(List.of()), new ObjectMapper(),
                executor, timeout -> {
                    RecordingSseEmitter emitter = new RecordingSseEmitter(timeout);
                    emitters.add(emitter);
                    return emitter;
                });
    }

    private ExecutorService singleChatExecutor() {
        return new ThreadPoolExecutor(
                0, 1, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private LlmConfigVO config(String provider, String apiKey) {
        return LlmConfigVO.builder()
                .provider(provider)
                .apiKey(apiKey)
                .apiBase("https://example.com/v1")
                .model("gpt-test")
                .maxTokens(256)
                .temperature(0.2)
                .enabled(true)
                .build();
    }

    private static final class RecordingSseEmitter extends SseEmitter {
        private final long timeoutMillis;
        private final List<Set<ResponseBodyEmitter.DataWithMediaType>> sentEvents = new CopyOnWriteArrayList<>();
        private final CountDownLatch completedLatch = new CountDownLatch(1);
        private Runnable completionCallback;
        private Runnable timeoutCallback;
        private Consumer<Throwable> errorCallback;
        private boolean completed;

        RecordingSseEmitter(long timeout) {
            super(timeout);
            timeoutMillis = timeout;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sentEvents.add(builder.build());
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            completionCallback = callback;
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            timeoutCallback = callback;
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
            errorCallback = callback;
        }

        @Override
        public synchronized void complete() {
            completed = true;
            completedLatch.countDown();
            if (completionCallback != null) {
                completionCallback.run();
            }
        }

        @Override
        public synchronized void completeWithError(Throwable throwable) {
            completedLatch.countDown();
            if (errorCallback != null) {
                errorCallback.accept(throwable);
            }
        }

        void triggerTimeout() {
            timeoutCallback.run();
        }

        String eventText() {
            List<String> values = new ArrayList<>();
            sentEvents.forEach(event -> event.forEach(item -> values.add(String.valueOf(item.getData()))));
            return String.join("", values);
        }

        long eventCount(String marker) {
            return sentEvents.stream()
                    .filter(event -> event.stream().anyMatch(item -> String.valueOf(item.getData()).contains(marker)))
                    .count();
        }
    }
}
