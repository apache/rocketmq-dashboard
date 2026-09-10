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

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LlmSseSessionTest {

    @Test
    void completionTimeoutAndErrorCancelAttachedTasks() {
        assertLifecycleCallbackCancels(TestEmitter::triggerCompletion);
        assertLifecycleCallbackCancels(TestEmitter::triggerTimeout);
        assertLifecycleCallbackCancels(emitter -> emitter.triggerError(new IllegalStateException("closed")));
    }

    @Test
    void terminationBeforeAttachmentCancelsTheLateTask() {
        TestEmitter emitter = new TestEmitter();
        LlmSseSession session = new LlmSseSession(emitter, ignored -> { });
        Future<?> task = mock(Future.class);

        emitter.triggerTimeout();
        session.attach(task);

        assertThat(session.isCancelled()).isTrue();
        verify(task).cancel(true);
    }

    @Test
    void normalTerminalCompletionDoesNotCancelTheTask() {
        TestEmitter emitter = new TestEmitter();
        AtomicInteger terminations = new AtomicInteger();
        LlmSseSession session = new LlmSseSession(emitter, ignored -> terminations.incrementAndGet());
        Future<?> task = mock(Future.class);
        session.attach(task);

        assertThat(session.beginTerminal()).isTrue();
        session.complete();

        assertThat(emitter.completed).isTrue();
        assertThat(terminations).hasValue(1);
        assertThat(session.isCancelled()).isFalse();
        verify(task, never()).cancel(true);
    }

    @Test
    void attachTwiceShouldRejectTheSecondTask() {
        TestEmitter emitter = new TestEmitter();
        LlmSseSession session = new LlmSseSession(emitter, ignored -> { });
        Future<?> first = mock(Future.class);
        Future<?> second = mock(Future.class);
        session.attach(first);

        assertThatThrownBy(() -> session.attach(second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("An SSE session can own only one task");
        verify(second).cancel(true);
        verify(first, never()).cancel(true);
    }

    @Test
    void sendShouldForwardEventsWhileActive() throws Exception {
        TestEmitter emitter = new TestEmitter();
        LlmSseSession session = new LlmSseSession(emitter, ignored -> { });
        SseEmitter.SseEventBuilder event = SseEmitter.event().data("ping");

        session.send(event);

        assertThat(emitter.lastEvent).isSameAs(event);
        assertThat(emitter.sentEvents).isEqualTo(1);
    }

    @Test
    void sendAfterCancellationShouldFailWithoutTouchingTheEmitter() throws Exception {
        TestEmitter emitter = new TestEmitter();
        LlmSseSession session = new LlmSseSession(emitter, ignored -> { });
        emitter.triggerTimeout();

        assertThatThrownBy(() -> session.send(SseEmitter.event().data("ping")))
                .isInstanceOf(IOException.class)
                .hasMessage("SSE client is no longer connected");
        assertThat(emitter.sentEvents).isZero();
    }

    @Test
    void sendFailureShouldCancelTheSessionAndTask() {
        TestEmitter emitter = new TestEmitter();
        AtomicInteger terminations = new AtomicInteger();
        LlmSseSession session = new LlmSseSession(emitter, ignored -> terminations.incrementAndGet());
        Future<?> task = mock(Future.class);
        session.attach(task);
        emitter.failSends = true;

        assertThatThrownBy(() -> session.send(SseEmitter.event().data("ping")))
                .isInstanceOf(IOException.class);

        assertThat(session.isCancelled()).isTrue();
        assertThat(terminations).hasValue(1);
        verify(task).cancel(true);
    }

    @Test
    void completeWithErrorShouldNotifyOnceAndForwardTheFailure() {
        TestEmitter emitter = new TestEmitter();
        AtomicInteger terminations = new AtomicInteger();
        LlmSseSession session = new LlmSseSession(emitter, ignored -> terminations.incrementAndGet());
        IllegalStateException failure = new IllegalStateException("provider failed");

        session.completeWithError(failure);
        session.completeWithError(failure);

        assertThat(terminations).hasValue(1);
        assertThat(emitter.errorCount).isEqualTo(1);
        assertThat(emitter.lastError).isSameAs(failure);
        assertThat(session.isCancelled()).isFalse();
    }

    @Test
    void completeWithoutBeginTerminalShouldBeANoOp() {
        TestEmitter emitter = new TestEmitter();
        AtomicInteger terminations = new AtomicInteger();
        LlmSseSession session = new LlmSseSession(emitter, ignored -> terminations.incrementAndGet());

        session.complete();

        assertThat(emitter.completed).isFalse();
        assertThat(terminations).hasValue(0);
    }

    @Test
    void cancelAfterTerminalCompletionShouldBeANoOp() {
        TestEmitter emitter = new TestEmitter();
        AtomicInteger terminations = new AtomicInteger();
        LlmSseSession session = new LlmSseSession(emitter, ignored -> terminations.incrementAndGet());
        Future<?> task = mock(Future.class);
        session.attach(task);
        session.beginTerminal();
        session.complete();

        session.cancel();

        assertThat(terminations).hasValue(1);
        verify(task, never()).cancel(true);
    }

    private void assertLifecycleCallbackCancels(Consumer<TestEmitter> callback) {
        TestEmitter emitter = new TestEmitter();
        AtomicInteger terminations = new AtomicInteger();
        LlmSseSession session = new LlmSseSession(emitter, ignored -> terminations.incrementAndGet());
        Future<?> task = mock(Future.class);
        session.attach(task);

        callback.accept(emitter);
        callback.accept(emitter);

        assertThat(session.isCancelled()).isTrue();
        assertThat(terminations).hasValue(1);
        verify(task).cancel(true);
    }

    private static final class TestEmitter extends SseEmitter {
        private Runnable completionCallback;
        private Runnable timeoutCallback;
        private Consumer<Throwable> errorCallback;
        private boolean completed;
        private SseEmitter.SseEventBuilder lastEvent;
        private int sentEvents;
        private boolean failSends;
        private int errorCount;
        private Throwable lastError;

        @Override
        public synchronized void onCompletion(Runnable callback) {
            this.completionCallback = callback;
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            this.timeoutCallback = callback;
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
            this.errorCallback = callback;
        }

        @Override
        public synchronized void complete() {
            completed = true;
            triggerCompletion();
        }

        @Override
        public synchronized void completeWithError(Throwable throwable) {
            errorCount++;
            lastError = throwable;
        }

        @Override
        public synchronized void send(SseEmitter.SseEventBuilder builder) throws java.io.IOException {
            if (failSends) {
                throw new java.io.IOException("transport closed");
            }
            sentEvents++;
            lastEvent = builder;
        }

        void triggerCompletion() {
            if (completionCallback != null) {
                completionCallback.run();
            }
        }

        void triggerTimeout() {
            if (timeoutCallback != null) {
                timeoutCallback.run();
            }
        }

        void triggerError(Throwable throwable) {
            if (errorCallback != null) {
                errorCallback.accept(throwable);
            }
        }
    }
}
