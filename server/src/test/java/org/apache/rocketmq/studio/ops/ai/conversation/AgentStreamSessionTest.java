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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The replay-to-live handover of one observer, at the session level: the frames buffered during a
 * replay must reach the wire before any frame published while they are being drained.
 */
class AgentStreamSessionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Interleavings attempted; one is enough to expose an unordered drain, and the drain side of
     * the contract holds on every one of them under the fix. */
    private static final int ATTEMPTS = 50;

    /**
     * An emitter that records the frames it is handed and can run a callback before a send lands,
     * so a test can hand a live frame over from the exact moment an older frame is being written -
     * the interleaving a concurrent publisher produces while the drain sits between two buffered
     * frames.
     */
    private static final class RecordingEmitter extends SseEmitter {

        private final List<String> wire = new ArrayList<>();
        private final AtomicReference<Runnable> beforeSend = new AtomicReference<>();

        private RecordingEmitter() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            Runnable hook = beforeSend.get();
            if (hook != null) {
                beforeSend.set(null);
                hook.run();
            }
            builder.build().forEach(item -> wire.add(String.valueOf(item.getData())));
        }

        String wire() {
            return String.join("", wire);
        }
    }

    @Test
    void bufferedFramesMustReachTheWireBeforeAFramePublishedDuringTheDrainTest() throws Exception {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            RecordingEmitter emitter = new RecordingEmitter();
            AgentStreamSession session = new AgentStreamSession(1L, emitter, MAPPER, other -> {
            }, null);
            session.noteWatermark(1);

            // Two frames buffered while the session is REPLAYING.
            assertThat(session.deliver(2L, new LiveEvent.TextDelta("older"))).isTrue();
            assertThat(session.deliver(3L, new LiveEvent.TextDelta("newer"))).isTrue();

            // While the FIRST buffered frame is on the wire, a concurrent publisher hands over a
            // frame that was produced later. The drain must finish first: the client folds frames
            // in arrival order, so a later frame that overtakes an older one renders ahead of it
            // (a tool_done before its tool_start, an answer before its reasoning).
            Thread publisher = new Thread(() -> session.deliver(4L,
                    new LiveEvent.TextDelta("published-during-the-drain")), "test-publisher");
            emitter.beforeSend.set(() -> {
                publisher.start();
                // Hold the send lock until the publisher is parked on it, so the two threads are
                // racing for the same lock the moment this send releases it.
                awaitBlocked(publisher);
            });
            session.finishReplay();
            publisher.join(TimeUnit.SECONDS.toMillis(5));
            assertThat(publisher.isAlive()).as("publisher finished after the drain").isFalse();

            String wire = emitter.wire();
            int older = wire.indexOf("older");
            int newer = wire.indexOf("newer");
            int published = wire.indexOf("published-during-the-drain");
            assertThat(older)
                    .as("attempt %d wire %s", attempt, wire)
                    .isGreaterThanOrEqualTo(0);
            assertThat(newer).isGreaterThan(older);
            assertThat(published).isGreaterThan(newer);
        }
    }

    private static void awaitBlocked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (!thread.isAlive() || thread.getState() == Thread.State.BLOCKED
                    || thread.getState() == Thread.State.WAITING) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("publisher never reached the send lock");
    }
}
