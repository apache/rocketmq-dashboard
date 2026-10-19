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
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentStreamEmitterTest {

    @Test
    void aBlockedObserverShouldNotHoldThePublisherOrOtherObserversTest() throws Exception {
        try (AgentStreamEmitter.Factory factory = new AgentStreamEmitter.Factory()) {
            AgentRunRegistry registry = new AgentRunRegistry();
            AgentRunHandle handle = new AgentRunHandle(41, Duration.ofMillis(1));
            registry.register(41, handle);
            Transport slow = new Transport(factory.apply(300_000L), true);
            Transport fast = new Transport(factory.apply(300_000L), false);
            try {
                for (Transport transport : new Transport[] {slow, fast}) {
                    AgentStreamSession session = new AgentStreamSession(41, transport.emitter,
                            new ObjectMapper(), detached -> registry.detach(41, detached), null);
                    registry.attach(41, session);
                    session.finishReplay();
                }
                registry.publish(41, 0, new LiveEvent.TextDelta("answer"));
                assertThat(slow.entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(fast.entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(handle.isStopRequested()).isFalse();
                registry.finish(41);
                assertThat(fast.completed.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(slow.completed.getCount()).isEqualTo(1);
            } finally {
                slow.release.countDown();
            }
            assertThat(slow.completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(slow.text()).contains("answer", "event:done");
        }
    }

    @Test
    void doneShouldFollowAllQueuedFramesAndCompleteOnceTest() throws Exception {
        try (AgentStreamEmitter.Factory factory = new AgentStreamEmitter.Factory()) {
            Transport transport = new Transport(factory.apply(300_000L), true);
            try {
                transport.emitter.send(SseEmitter.event().data("first"));
                assertThat(transport.entered.await(2, TimeUnit.SECONDS)).isTrue();
                transport.emitter.send(SseEmitter.event().data("second"));
                transport.emitter.send(SseEmitter.event().name("done").data(""));
                transport.emitter.complete();
                transport.emitter.complete();
            } finally {
                transport.release.countDown();
            }
            assertThat(transport.completed.await(2, TimeUnit.SECONDS)).isTrue();
            String text = transport.text();
            assertThat(text.indexOf("first")).isLessThan(text.indexOf("second"));
            assertThat(text.indexOf("second")).isLessThan(text.indexOf("event:done"));
            assertThat(transport.completions).hasSize(1);
        }
    }

    @Test
    void overflowingASlowObserverShouldRejectFurtherFramesTest() throws Exception {
        try (AgentStreamEmitter.Factory factory = new AgentStreamEmitter.Factory()) {
            Transport transport = new Transport(factory.apply(300_000L), true);
            transport.emitter.send(SseEmitter.event().data("blocked"));
            assertThat(transport.entered.await(2, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> {
                    for (int i = 0; i <= AgentStreamEmitter.MAX_PENDING_FRAMES; i++) {
                        transport.emitter.send(SseEmitter.event().data("next"));
                    }
                }).isInstanceOf(IOException.class);
                assertThatThrownBy(() -> transport.emitter.send(SseEmitter.event().data("later")))
                        .isInstanceOf(IOException.class);
            } finally {
                transport.release.countDown();
            }
            assertThat(transport.completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(transport.failures).hasSize(1);
        }
    }

    @Test
    void oversizedFramesShouldFailOnlyThatObserverTest() throws Exception {
        try (AgentStreamEmitter.Factory factory = new AgentStreamEmitter.Factory()) {
            Transport oversized = new Transport(factory.apply(300_000L), false);
            assertThatThrownBy(() -> oversized.emitter.send(SseEmitter.event()
                    .data("x".repeat(AgentStreamEmitter.MAX_PENDING_BYTES))))
                    .isInstanceOf(IOException.class);
            assertThat(oversized.completed.await(2, TimeUnit.SECONDS)).isTrue();
            Transport healthy = new Transport(factory.apply(300_000L), false);
            healthy.emitter.send(SseEmitter.event().data("ok"));
            healthy.emitter.complete();
            assertThat(healthy.completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(healthy.text()).contains("ok");
        }
    }

    @Test
    void timeoutShouldDiscardQueuedFramesAndReleaseTheSenderTest() throws Exception {
        try (AgentStreamEmitter.Factory factory = new AgentStreamEmitter.Factory()) {
            Transport transport = new Transport(factory.apply(300_000L), true);
            transport.emitter.send(SseEmitter.event().data("blocked"));
            assertThat(transport.entered.await(2, TimeUnit.SECONDS)).isTrue();
            transport.emitter.send(SseEmitter.event().data("must not arrive"));
            transport.timeout.run();
            transport.release.countDown();
            assertThatThrownBy(() -> transport.emitter.send(SseEmitter.event().data("late")))
                    .isInstanceOf(IOException.class);
            assertThat(transport.completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(transport.text()).doesNotContain("must not arrive");
        }
    }

    @Test
    void shutdownShouldInterruptBlockedSendersAndRejectNewWritesTest() throws Exception {
        AgentStreamEmitter.Factory factory = new AgentStreamEmitter.Factory();
        try {
            Transport transport = new Transport(factory.apply(300_000L), true);
            transport.emitter.send(SseEmitter.event().data("blocked"));
            assertThat(transport.entered.await(2, TimeUnit.SECONDS)).isTrue();
            factory.close();
            assertThat(transport.completed.await(2, TimeUnit.SECONDS)).isTrue();
            Transport refused = new Transport(factory.apply(300_000L), false);
            assertThatThrownBy(() -> refused.emitter.send(SseEmitter.event().data("late")))
                    .isInstanceOf(IOException.class);
            assertThat(refused.completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(refused.failures).hasSize(1);
        } finally {
            factory.close();
        }
    }

    /** Install a real emitter transport, replacing only the socket boundary with a controlled handler. */
    private static final class Transport {
        private final SseEmitter emitter;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private final CopyOnWriteArrayList<String> chunks = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<String> completions = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();
        private Runnable timeout;

        private Transport(SseEmitter emitter, boolean block) throws Exception {
            this.emitter = emitter;
            Class<?> handlerType = Class.forName(ResponseBodyEmitter.class.getName() + "$Handler");
            Object handler = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[] {handlerType},
                    (proxy, method, args) -> {
                        if (method.getName().equals("send")) {
                            entered.countDown();
                            if (block && !release.await(5, TimeUnit.SECONDS)) {
                                throw new IOException("test transport deadline");
                            }
                            if (args[0] instanceof Set<?> parts) {
                                for (Object part : parts) {
                                    chunks.add(String.valueOf(((ResponseBodyEmitter.DataWithMediaType) part).getData()));
                                }
                            } else {
                                chunks.add(String.valueOf(args[0]));
                            }
                        } else if (method.getName().equals("onTimeout")) {
                            timeout = (Runnable) args[0];
                        } else if (method.getName().equals("complete")
                                || method.getName().equals("completeWithError")) {
                            if (args != null && args.length > 0) failures.add((Throwable) args[0]);
                            completions.add(method.getName());
                            completed.countDown();
                        }
                        return null;
                    });
            var initialize = ResponseBodyEmitter.class.getDeclaredMethod("initialize", handlerType);
            initialize.setAccessible(true);
            initialize.invoke(emitter, handler);
        }

        private String text() {
            return String.join("", chunks);
        }
    }
}
