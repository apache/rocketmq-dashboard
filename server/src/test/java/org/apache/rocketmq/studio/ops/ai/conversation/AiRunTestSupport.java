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
package org.apache.rocketmq.studio.ops.ai.conversation;

import org.apache.rocketmq.studio.ops.ai.AgentProvider;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.AgentStreamOptions;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * Shared fixtures for the run-lifecycle tests.
 *
 * <p>Three things every one of them needs and none of them should re-implement: an emitter that records
 * what went out over the wire instead of writing to a socket, an executor that runs a task on the
 * calling thread so a test never has to poll, and the two entity rows a run is made of.
 *
 * <p>The recording emitter is the same seam {@code OpenAiCompatibleLlmGatewayTest} uses, including the
 * package-private {@code timeoutMillis} capture, so a test can assert the stream budget that was handed
 * to the emitter factory without a real request.
 */
final class AiRunTestSupport {

    private AiRunTestSupport() {
    }

    /** An emitter factory that records every emitter it hands out. */
    static LongFunction<SseEmitter> recordingInto(List<RecordingSseEmitter> recorded) {
        return timeout -> {
            RecordingSseEmitter emitter = new RecordingSseEmitter(timeout);
            recorded.add(emitter);
            return emitter;
        };
    }

    /**
     * Runs every task on the thread that submitted it. {@code submit} still returns a real
     * {@link java.util.concurrent.Future}, already completed, so a handle that cancels its worker after
     * the fact behaves as it does in production.
     */
    static ExecutorService directExecutor() {
        return new DirectExecutorService();
    }

    static RmqAiConversation conversation(long id, String owner) {
        RmqAiConversation conversation = new RmqAiConversation();
        conversation.setId(id);
        conversation.setTitle(AiConversationService.DEFAULT_TITLE);
        conversation.setOwner(owner);
        conversation.setEngine("claude-code");
        conversation.setModel("qwen3.8-max");
        conversation.setMode("chat");
        conversation.setLastSeq(0);
        conversation.setArchived(false);
        LocalDateTime now = LocalDateTime.of(2026, 9, 18, 10, 0);
        conversation.setGmtCreate(now);
        conversation.setGmtModified(now);
        return conversation;
    }

    static RmqAiRun run(long id, long conversationId, int turn, RunStatus status) {
        RmqAiRun run = new RmqAiRun();
        run.setId(id);
        run.setConversationId(conversationId);
        run.setTurn(turn);
        run.setStatus(status.name());
        run.setEngine("claude-code");
        run.setModel("qwen3.8-max");
        run.setStartSeq(1);
        run.setEndSeq(0);
        LocalDateTime now = LocalDateTime.of(2026, 9, 18, 10, 0);
        run.setGmtCreate(now);
        run.setGmtModified(now);
        return run;
    }

    /** The seq of every captured row, in capture order. */
    static List<Integer> seqsOf(List<RmqAiEvent> events) {
        return events.stream().map(RmqAiEvent::getSeq).toList();
    }

    /** The type tag of every captured row, in capture order. */
    static List<String> typesOf(List<RmqAiEvent> events) {
        return events.stream().map(RmqAiEvent::getType).toList();
    }

    /** An {@link SseEmitter} that records frames instead of writing them to a response. */
    static final class RecordingSseEmitter extends SseEmitter {

        private final long timeoutMillis;
        private final List<Set<ResponseBodyEmitter.DataWithMediaType>> sentEvents = new CopyOnWriteArrayList<>();
        private final CountDownLatch completedLatch = new CountDownLatch(1);
        private Runnable completionCallback;
        private Runnable timeoutCallback;
        private Consumer<Throwable> errorCallback;
        private boolean completed;
        private Throwable failure;

        RecordingSseEmitter(long timeout) {
            super(timeout);
            this.timeoutMillis = timeout;
        }

        long timeoutMillis() {
            return timeoutMillis;
        }

        boolean completed() {
            return completed;
        }

        Throwable failure() {
            return failure;
        }

        boolean awaitCompleted(long timeout, TimeUnit unit) throws InterruptedException {
            return completedLatch.await(timeout, unit);
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
            failure = throwable;
            completedLatch.countDown();
            if (errorCallback != null) {
                errorCallback.accept(throwable);
            }
        }

        /** Fires the transport timeout, which for a run observer must only detach it. */
        void triggerTimeout() {
            if (timeoutCallback != null) {
                timeoutCallback.run();
            }
        }

        /** Fires the transport completion callback, i.e. the client hung up. */
        void triggerCompletion() {
            if (completionCallback != null) {
                completionCallback.run();
            }
        }

        /** Every frame's wire text, concatenated. Markers such as {@code event:agent} are searchable. */
        String eventText() {
            List<String> values = new ArrayList<>();
            sentEvents.forEach(event -> event.forEach(item -> values.add(String.valueOf(item.getData()))));
            return String.join("", values);
        }

        long eventCount(String marker) {
            return sentEvents.stream()
                    .filter(event -> event.stream()
                            .anyMatch(item -> String.valueOf(item.getData()).contains(marker)))
                    .count();
        }
    }

    /** {@link ExecutorService} that runs a task inline, on the submitting thread. */
    private static final class DirectExecutorService extends AbstractExecutorService {

        private volatile boolean shutdown;

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException("executor is shut down");
            }
            // submit() already wrapped this in a FutureTask, so running it here means the returned
            // Future is complete by the time submit() returns.
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
    /**
     * An {@link AgentProvider} that replays a scripted frame sequence, so a test can produce the frames
     * that only appear in awkward orders — a successful {@code result} frame arriving after a stop, say —
     * and can fail on demand. {@code beforeStream} runs before any frame is emitted, which is how a test
     * stops a run from inside its own worker.
     */
    static final class StubAgentProvider implements AgentProvider {

        static final String ENGINE = "claude-code";

        /** Settable so a test can register the stub under a second engine name. */
        String engineName = ENGINE;

        final List<AgentEvent> scripted = new ArrayList<>();
        Consumer<AgentStreamOptions> beforeStream = options -> {
        };
        RuntimeException failure;
        String lastPrompt;
        AgentStreamOptions lastOptions;
        int calls;

        void emit(AgentEvent event) {
            scripted.add(event);
        }

        boolean wasCalled() {
            return calls > 0;
        }

        @Override
        public String engine() {
            return engineName;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String complete(LlmConfigVO config, String prompt, String modelOverride) {
            throw new UnsupportedOperationException("a run streams events, it does not complete");
        }

        @Override
        public void streamEvents(LlmConfigVO config, AgentStreamOptions options, Consumer<AgentEvent> sink) {
            calls++;
            lastPrompt = options.getPrompt();
            lastOptions = options;
            beforeStream.accept(options);
            scripted.forEach(sink);
            if (failure != null) {
                throw failure;
            }
        }
    }
    /**
     * A snapshot of a run row.
     *
     * <p>Necessary because the entity is mutable and Mockito records arguments <em>by reference</em>: a
     * test that inspects the captured row after the run finished would see the terminal state no matter
     * which write it was looking at, and an {@code InOrder} matcher on {@code status} would silently
     * match nothing.
     */
    static RmqAiRun copyOf(RmqAiRun run) {
        RmqAiRun copy = new RmqAiRun();
        copy.setId(run.getId());
        copy.setConversationId(run.getConversationId());
        copy.setTurn(run.getTurn());
        copy.setStatus(run.getStatus());
        copy.setEngine(run.getEngine());
        copy.setModel(run.getModel());
        copy.setRuntimeSessionId(run.getRuntimeSessionId());
        copy.setResumedFrom(run.getResumedFrom());
        copy.setStartedAt(run.getStartedAt());
        copy.setFinishedAt(run.getFinishedAt());
        copy.setDurationMs(run.getDurationMs());
        copy.setInputTokens(run.getInputTokens());
        copy.setOutputTokens(run.getOutputTokens());
        copy.setStartSeq(run.getStartSeq());
        copy.setEndSeq(run.getEndSeq());
        copy.setStopReason(run.getStopReason());
        copy.setErrorCode(run.getErrorCode());
        copy.setErrorMessage(run.getErrorMessage());
        return copy;
    }

    /** A snapshot of the columns a conversation update touches. */
    static RmqAiConversation copyOf(RmqAiConversation conversation) {
        RmqAiConversation copy = new RmqAiConversation();
        copy.setId(conversation.getId());
        copy.setTitle(conversation.getTitle());
        copy.setOwner(conversation.getOwner());
        copy.setEngine(conversation.getEngine());
        copy.setModel(conversation.getModel());
        copy.setMode(conversation.getMode());
        copy.setInstanceId(conversation.getInstanceId());
        copy.setRuntimeSessionId(conversation.getRuntimeSessionId());
        copy.setLastSeq(conversation.getLastSeq());
        copy.setArchived(conversation.getArchived());
        copy.setGmtCreate(conversation.getGmtCreate());
        copy.setGmtModified(conversation.getGmtModified());
        return copy;
    }
}
