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

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
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
