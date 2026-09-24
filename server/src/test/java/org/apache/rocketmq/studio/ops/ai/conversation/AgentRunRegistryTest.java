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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.conversation.event.LiveEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The index of what this JVM is generating: who owns a run, and who is watching it.
 *
 * <p>The interesting cases are the ones created by generation being decoupled from the HTTP connection.
 * An observer may attach long after the run started, so it replays and then tails — and the seam between
 * those two must neither lose a frame nor deliver one twice. A run may also be unregistered while an
 * observer is mid-attach, which is why {@link AgentRunRegistry#attach} re-checks after adding.
 */
class AgentRunRegistryTest {

    private static final long RUN_ID = 11L;

    private final AgentRunRegistry registry = new AgentRunRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registerShouldMakeARunStoppableAndObservableTest() {
        AgentRunHandle handle = handle(RUN_ID);

        registry.register(RUN_ID, handle);

        assertThat(registry.isLive(RUN_ID)).isTrue();
        assertThat(registry.liveRunIds()).containsExactly(RUN_ID);
        assertThat(registry.handle(RUN_ID)).contains(handle);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void attachShouldRefuseAnUnknownRunSoTheCallerClosesTheStreamTest() {
        Watcher watcher = watcher(RUN_ID);

        // Nothing is generating here, so no frame will ever arrive: the caller replays and says done
        // instead of leaving a browser on an open stream forever.
        assertThat(registry.attach(RUN_ID, watcher.session)).isFalse();
        assertThat(registry.stop(RUN_ID, AbortReason.USER_STOP)).isFalse();
        assertThat(registry.handle(RUN_ID)).isEmpty();
    }

    @Test
    void publishShouldFanOutToEveryObserverTest() {
        registry.register(RUN_ID, handle(RUN_ID));
        Watcher first = watcher(RUN_ID);
        Watcher second = watcher(RUN_ID);
        attachLive(first, second);

        registry.publish(RUN_ID, 1L, new LiveEvent.TextDelta("hello"));

        // Two tabs on one conversation is legitimate; neither may miss a frame because of the other.
        assertThat(first.emitter.eventText()).contains("hello");
        assertThat(second.emitter.eventText()).contains("hello");
    }

    @Test
    void detachingOneObserverShouldLeaveTheRunAndTheOthersAloneTest() {
        registry.register(RUN_ID, handle(RUN_ID));
        Watcher leaving = watcher(RUN_ID);
        Watcher staying = watcher(RUN_ID);
        attachLive(leaving, staying);

        registry.detach(RUN_ID, leaving.session);
        registry.publish(RUN_ID, 1L, new LiveEvent.TextDelta("after the disconnect"));

        // This is the whole point of the redesign: a closed tab detaches, it does not stop the answer.
        assertThat(registry.isLive(RUN_ID)).isTrue();
        assertThat(leaving.emitter.eventText()).doesNotContain("after the disconnect");
        assertThat(staying.emitter.eventText()).contains("after the disconnect");
    }

    @Test
    void anObserverShouldDropLiveFramesItAlreadyReplayedTest() {
        registry.register(RUN_ID, handle(RUN_ID));
        Watcher late = watcher(RUN_ID);
        late.session.noteWatermark(5);
        registry.attach(RUN_ID, late.session);
        late.session.finishReplay();

        registry.publish(RUN_ID, 4L, toolStart());
        registry.publish(RUN_ID, 5L, toolDone());
        registry.publish(RUN_ID, 6L, toolStart());
        // A frame with no persisted counterpart carries seq 0 and is never dropped: it cannot be
        // replayed from the database either, so dropping it would lose content outright.
        registry.publish(RUN_ID, 0L, new LiveEvent.TextDelta("live tail"));

        assertThat(late.emitter.eventCount("\"type\":\"tool_start\"")).isEqualTo(1);
        assertThat(late.emitter.eventText()).doesNotContain("tool_done").contains("live tail");
    }

    @Test
    void framesArrivingDuringAReplayShouldBeBufferedUntilTheReplayIsSentTest() {
        registry.register(RUN_ID, handle(RUN_ID));
        Watcher watcher = watcher(RUN_ID);
        // Registered before the replay is flushed, so nothing can slip into the gap between the database
        // read and the tail.
        registry.attach(RUN_ID, watcher.session);
        registry.publish(RUN_ID, 7L, new LiveEvent.TextDelta("live frame"));
        watcher.session.noteWatermark(6);
        watcher.session.sendReplayed(new LiveEvent.TextDelta("replayed frame"));

        watcher.session.finishReplay();

        String text = watcher.emitter.eventText();
        assertThat(text).contains("replayed frame").contains("live frame");
        // Order matters more than delivery: a live frame written before an older replayed row would
        // reorder the transcript the client is rendering.
        assertThat(text.indexOf("replayed frame")).isLessThan(text.indexOf("live frame"));
    }

    @Test
    void aFrameBufferedDuringAReplayShouldStillBeDeduplicatedTest() {
        registry.register(RUN_ID, handle(RUN_ID));
        Watcher watcher = watcher(RUN_ID);
        registry.attach(RUN_ID, watcher.session);
        registry.publish(RUN_ID, 6L, toolStart());
        watcher.session.noteWatermark(6);
        watcher.session.sendReplayed(toolStart());

        watcher.session.finishReplay();

        // The buffered frame's row was inside the replay, so it must not be sent a second time.
        assertThat(watcher.emitter.eventCount("\"type\":\"tool_start\"")).isEqualTo(1);
    }

    @Test
    void stopShouldAbortTheRunItWasGivenTest() {
        AgentRunHandle handle = handle(RUN_ID);
        registry.register(RUN_ID, handle);

        assertThat(registry.stop(RUN_ID, AbortReason.USER_STOP)).isTrue();

        assertThat(handle.abortReason()).contains(AbortReason.USER_STOP);
        // A second stop is a no-op, so a double-clicked button cannot escalate or relabel anything.
        assertThat(registry.stop(RUN_ID, AbortReason.SHUTDOWN)).isFalse();
        assertThat(handle.abortReason()).contains(AbortReason.USER_STOP);
    }

    @Test
    void finishShouldSendDoneToEveryObserverAndDropTheRunTest() {
        registry.register(RUN_ID, handle(RUN_ID));
        Watcher first = watcher(RUN_ID);
        Watcher second = watcher(RUN_ID);
        attachLive(first, second);

        assertThat(registry.finish(RUN_ID)).isEqualTo(2);

        assertThat(first.emitter.eventText()).contains("event:done");
        assertThat(second.emitter.eventText()).contains("event:done");
        assertThat(first.emitter.completed()).isTrue();
        assertThat(registry.isLive(RUN_ID)).isFalse();
        // Idempotent, because finalisation calls it from a finally that can run after an earlier call.
        assertThat(registry.finish(RUN_ID)).isZero();
    }

    @Test
    void aShutdownDrainShouldSignalEveryRunBeforeWaitingForAnyOfThemTest() throws Exception {
        Process first = mock(Process.class);
        Process second = mock(Process.class);
        when(first.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(second.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        AgentRunHandle firstHandle = handle(RUN_ID);
        AgentRunHandle secondHandle = handle(RUN_ID + 1);
        firstHandle.attachProcess(first);
        secondHandle.attachProcess(second);
        registry.register(RUN_ID, firstHandle);
        registry.register(RUN_ID + 1, secondHandle);

        registry.drain(AbortReason.SHUTDOWN);

        // Two phases, so the grace periods overlap: SIGTERM to both, then wait for both. A sequential
        // stop would cost one grace period per run and blow the shutdown budget with a full executor.
        InOrder order = inOrder(first, second);
        order.verify(first).destroy();
        order.verify(second).destroy();
        order.verify(first).waitFor(AgentRunHandle.DEFAULT_SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        order.verify(second).waitFor(AgentRunHandle.DEFAULT_SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        verify(first, never()).destroyForcibly();
        assertThat(firstHandle.abortReason()).contains(AbortReason.SHUTDOWN);
        assertThat(secondHandle.abortReason()).contains(AbortReason.SHUTDOWN);
    }

    @Test
    void unregisterShouldDropTheRunWithoutClosingItsObserversTest() {
        registry.register(RUN_ID, handle(RUN_ID));
        Watcher watcher = watcher(RUN_ID);
        registry.attach(RUN_ID, watcher.session);
        watcher.session.finishReplay();

        registry.unregister(RUN_ID);

        assertThat(registry.isLive(RUN_ID)).isFalse();
        assertThat(watcher.emitter.completed()).isFalse();
    }

    private AgentRunHandle handle(long runId) {
        return new AgentRunHandle(runId, Duration.ofMillis(1));
    }

    private Watcher watcher(long runId) {
        return new Watcher(runId);
    }

    private void attachLive(Watcher... watchers) {
        for (Watcher watcher : watchers) {
            registry.attach(RUN_ID, watcher.session);
            watcher.session.finishReplay();
        }
    }

    private static LiveEvent.ToolStart toolStart() {
        return new LiveEvent.ToolStart("tc1", "rmq.topic.list", Map.of());
    }

    private static LiveEvent.ToolDone toolDone() {
        return new LiveEvent.ToolDone("tc1", "rmq.topic.list", "3 topics", 8, false, 12L, true, null);
    }

    /** One observer: the session the registry holds and the emitter a test asserts against. */
    private final class Watcher {

        private final AgentStreamSession session;
        private final AiRunTestSupport.RecordingSseEmitter emitter;

        private Watcher(long runId) {
            this.emitter = new AiRunTestSupport.RecordingSseEmitter(300_000L);
            this.session = new AgentStreamSession(runId, emitter, objectMapper,
                    detached -> registry.detach(runId, detached), null);
        }
    }
}
