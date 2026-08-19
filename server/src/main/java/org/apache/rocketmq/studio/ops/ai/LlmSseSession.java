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

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Couples one downstream SSE response with its asynchronous provider task.
 * Client completion, timeout, and transport errors cancel the task so an
 * abandoned response does not retain a gateway worker until provider timeout.
 */
final class LlmSseSession {

    private enum State {
        ACTIVE,
        TERMINATING,
        TERMINATED,
        CANCELLED
    }

    private final SseEmitter emitter;
    private final Consumer<LlmSseSession> terminationListener;
    private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);
    private final AtomicReference<Future<?>> task = new AtomicReference<>();
    private final AtomicBoolean terminationNotified = new AtomicBoolean();

    LlmSseSession(SseEmitter emitter, Consumer<LlmSseSession> terminationListener) {
        this.emitter = Objects.requireNonNull(emitter, "emitter");
        this.terminationListener = Objects.requireNonNull(terminationListener, "terminationListener");
        emitter.onCompletion(this::cancel);
        emitter.onTimeout(this::cancel);
        emitter.onError(ignored -> cancel());
    }

    SseEmitter emitter() {
        return emitter;
    }

    void attach(Future<?> submittedTask) {
        Objects.requireNonNull(submittedTask, "submittedTask");
        if (!task.compareAndSet(null, submittedTask)) {
            submittedTask.cancel(true);
            throw new IllegalStateException("An SSE session can own only one task");
        }
        if (state.get() == State.CANCELLED) {
            submittedTask.cancel(true);
        }
    }

    boolean beginTerminal() {
        return state.compareAndSet(State.ACTIVE, State.TERMINATING);
    }

    void send(SseEmitter.SseEventBuilder event) throws IOException {
        State current = state.get();
        if (current == State.CANCELLED || current == State.TERMINATED) {
            throw new IOException("SSE client is no longer connected");
        }
        try {
            emitter.send(event);
        } catch (IOException exception) {
            cancel();
            throw exception;
        }
    }

    void complete() {
        if (state.compareAndSet(State.TERMINATING, State.TERMINATED)) {
            notifyTermination();
            emitter.complete();
        }
    }

    void completeWithError(Throwable throwable) {
        State previous = state.getAndUpdate(current -> switch (current) {
            case ACTIVE, TERMINATING -> State.TERMINATED;
            case TERMINATED, CANCELLED -> current;
        });
        if (previous == State.ACTIVE || previous == State.TERMINATING) {
            notifyTermination();
            emitter.completeWithError(throwable);
        }
    }

    void cancel() {
        State previous = state.getAndUpdate(current -> switch (current) {
            case ACTIVE, TERMINATING -> State.CANCELLED;
            case TERMINATED, CANCELLED -> current;
        });
        if (previous == State.ACTIVE || previous == State.TERMINATING) {
            Future<?> submittedTask = task.get();
            if (submittedTask != null) {
                submittedTask.cancel(true);
            }
            notifyTermination();
        }
    }

    boolean isCancelled() {
        return state.get() == State.CANCELLED;
    }

    private void notifyTermination() {
        if (terminationNotified.compareAndSet(false, true)) {
            terminationListener.accept(this);
        }
    }
}
