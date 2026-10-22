/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.ops.ai.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.conversation.event.LiveEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AgentStreamSessionLifecycleTest {

    @Test
    void replayBufferShouldBoundFramesAndResumeLiveDeliveryTest() {
        AiRunTestSupport.RecordingSseEmitter emitter = new AiRunTestSupport.RecordingSseEmitter(60_000);
        AgentStreamSession session = new AgentStreamSession(41, emitter, new ObjectMapper(), ignored -> { }, null);
        for (int index = 0; index < 1025; index++) {
            assertThat(session.deliver(0, new LiveEvent.TextDelta("frame-" + index + ";"))).isTrue();
        }
        assertThat(emitter.eventText()).isEmpty();
        session.finishReplay();
        assertThat(emitter.eventCount("event:agent")).isEqualTo(1024);
        assertThat(emitter.eventText()).contains("frame-0;", "frame-1023;").doesNotContain("frame-1024;");
        assertThat(session.deliver(0, new LiveEvent.TextDelta("after-replay"))).isTrue();
        assertThat(emitter.eventText()).contains("after-replay");
    }

    @Test
    void detachShouldDiscardReplayBufferAndNotifyOnlyOnceTest() {
        AiRunTestSupport.RecordingSseEmitter emitter = new AiRunTestSupport.RecordingSseEmitter(60_000);
        AtomicInteger detached = new AtomicInteger();
        AgentStreamSession session = new AgentStreamSession(41, emitter, new ObjectMapper(),
                ignored -> detached.incrementAndGet(), null);
        session.deliver(0, new LiveEvent.TextDelta("buffered"));
        session.detach();
        session.detach();
        session.finishReplay();
        session.complete();
        assertThat(session.deliver(0, new LiveEvent.TextDelta("late"))).isFalse();
        assertThat(emitter.eventText()).isEmpty();
        assertThat(detached.get()).isEqualTo(1);
    }

    @Test
    void competingTerminalCallbacksShouldCompleteAndDetachOnlyOnceTest() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicInteger completions = new AtomicInteger();
        AtomicInteger detached = new AtomicInteger();
        doAnswer(invocation -> {
            completions.incrementAndGet();
            return null;
        }).when(emitter).complete();
        doAnswer(invocation -> {
            completions.incrementAndGet();
            return null;
        }).when(emitter).completeWithError(any());
        AgentStreamSession session = new AgentStreamSession(41, emitter, new ObjectMapper(),
                ignored -> detached.incrementAndGet(), null);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var success = executor.submit(() -> {
                start.await();
                session.complete();
                return null;
            });
            var failure = executor.submit(() -> {
                start.await();
                session.completeWithError(new IOException("disconnected"));
                return null;
            });
            start.countDown();
            success.get(5, TimeUnit.SECONDS);
            failure.get(5, TimeUnit.SECONDS);
        }
        session.complete();
        session.completeWithError(new IOException("late"));
        assertThat(completions.get()).isEqualTo(1);
        assertThat(detached.get()).isEqualTo(1);
        assertThat(session.isClosed()).isTrue();
    }

    @Test
    void heartbeatAfterDetachShouldNotSendAndShouldCancelItsScheduleTest() throws Exception {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(scheduler).scheduleAtFixedRate(heartbeat.capture(), anyLong(), anyLong(),
                eq(TimeUnit.MILLISECONDS));
        SseEmitter emitter = mock(SseEmitter.class);
        AgentStreamSession session = new AgentStreamSession(41, emitter, new ObjectMapper(), ignored -> { }, scheduler);
        heartbeat.getValue().run();
        session.detach();
        heartbeat.getValue().run();
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(future).cancel(false);
    }

    @Test
    void heartbeatSendFailureShouldDetachAndSuppressLaterFramesTest() throws Exception {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(scheduler).scheduleAtFixedRate(heartbeat.capture(), anyLong(), anyLong(),
                eq(TimeUnit.MILLISECONDS));
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        AtomicInteger detached = new AtomicInteger();
        AgentStreamSession session = new AgentStreamSession(41, emitter, new ObjectMapper(),
                ignored -> detached.incrementAndGet(), scheduler);
        heartbeat.getValue().run();
        heartbeat.getValue().run();
        assertThat(session.isClosed()).isTrue();
        assertThat(session.deliver(0, new LiveEvent.TextDelta("late"))).isFalse();
        assertThat(detached.get()).isEqualTo(1);
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(future).cancel(false);
    }
}
