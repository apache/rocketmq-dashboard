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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.conversation.event.LiveEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one ordering guarantee {@link AgentStreamSession#finishReplay()} owes a reconnecting client:
 * the frames buffered during the replay come out before every frame the run publishes afterwards.
 *
 * <p>The transcript a reconnecting browser renders is the arrival order of the frames — the deltas
 * are appended to the trailing text block, not sorted by anything — so a frame that overtakes two
 * buffered ones is a visibly shuffled answer, which is the very failure the buffering exists to
 * prevent.
 *
 * <p>The race is made deterministic instead of timing-dependent by parking the drain thread inside
 * the serialisation of its second buffered frame ({@link StepwiseMapper}) and then publishing a
 * frame from another thread. Serialisation happens in {@code sendAgent} <em>before</em> it takes
 * {@code sendLock}, so this pause is exactly the window a fix that drains outside the lock leaves
 * open: the publishing thread needs no lock to serialise and finds the lock free, because the drain
 * released it after flipping the state to LIVE.
 */
class AgentStreamSessionReplayOrderTest {

    private static final long RUN_ID = 42L;

    @Test
    void aFramePublishedDuringTheDrainShouldNotOvertakeTheBufferedFrames() throws Exception {
        StepwiseMapper mapper = new StepwiseMapper();
        RecordingEmitter emitter = new RecordingEmitter();
        AgentStreamSession session = new AgentStreamSession(RUN_ID, emitter, mapper, detached -> {
        }, null);

        // Buffered while the replay was still in flight: the run's thread published them after the
        // observer registered, before the HTTP thread called finishReplay.
        session.deliver(0, new LiveEvent.TextDelta("replay-one"));
        session.deliver(0, new LiveEvent.TextDelta("replay-two"));

        Thread drain = new Thread(session::finishReplay, "replay-drain");
        drain.start();
        assertThat(mapper.awaitSecondFrame()).as("the drain reached its second buffered frame")
                .isTrue();

        Thread publisher = new Thread(
                () -> session.deliver(0, new LiveEvent.TextDelta("live-one")), "live-publisher");
        publisher.start();
        // Bounded, and deliberately not asserted: it only gives the publishing thread time to reach
        // the socket while the drain is parked. A drain that holds the lock for the whole of itself
        // simply keeps this wait unanswered, and the frame order asserted below decides either way.
        emitter.awaitLiveFrame(500, TimeUnit.MILLISECONDS);
        mapper.releaseSecondFrame();

        drain.join(TimeUnit.SECONDS.toMillis(5));
        publisher.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(drain.isAlive()).isFalse();
        assertThat(publisher.isAlive()).isFalse();

        List<String> frames = emitter.orderedFrames();
        assertThat(frames).hasSize(3);
        assertThat(frames.get(0)).contains("replay-one");
        assertThat(frames.get(1)).contains("replay-two");
        assertThat(frames.get(2)).contains("live-one");
    }

    /**
     * An {@link ObjectMapper} that parks the second serialisation until the test releases it, so the
     * drain can be held still between two buffered frames.
     */
    private static final class StepwiseMapper extends ObjectMapper {

        private final AtomicInteger serialisations = new AtomicInteger();
        private final CountDownLatch secondFrame = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        @Override
        public String writeValueAsString(Object value) throws JsonProcessingException {
            if (serialisations.incrementAndGet() == 2) {
                secondFrame.countDown();
                try {
                    released.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.writeValueAsString(value);
        }

        boolean awaitSecondFrame() throws InterruptedException {
            return secondFrame.await(5, TimeUnit.SECONDS);
        }

        void releaseSecondFrame() {
            released.countDown();
        }
    }

    /** Records the payloads in the order they were written, and flags the live one. */
    private static final class RecordingEmitter extends SseEmitter {

        private final List<String> frames = new CopyOnWriteArrayList<>();
        private final CountDownLatch liveFrame = new CountDownLatch(1);

        private RecordingEmitter() {
            super(300_000L);
        }

        @Override
        public void send(SseEventBuilder builder) {
            Set<ResponseBodyEmitter.DataWithMediaType> event = builder.build();
            for (ResponseBodyEmitter.DataWithMediaType item : event) {
                String payload = String.valueOf(item.getData());
                // One SSE write is the frame's JSON plus its `event:`/`data:` scaffolding, which is
                // recorded as separate items; only the JSON is a frame.
                if (!payload.startsWith("{")) {
                    continue;
                }
                frames.add(payload);
                if (payload.contains("live-one")) {
                    liveFrame.countDown();
                }
            }
        }

        boolean awaitLiveFrame(long timeout, TimeUnit unit) throws InterruptedException {
            return liveFrame.await(timeout, unit);
        }

        List<String> orderedFrames() {
            return frames;
        }
    }
}
