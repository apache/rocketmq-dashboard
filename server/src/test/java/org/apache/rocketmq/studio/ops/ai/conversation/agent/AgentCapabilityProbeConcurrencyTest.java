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
package org.apache.rocketmq.studio.ops.ai.conversation.agent;

import org.apache.rocketmq.studio.ops.ai.conversation.AiConversationProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCapabilityProbeConcurrencyTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentRequestsShareColdOrExpiredProbeRoundTest(boolean expired) throws Exception {
        CliBinaryProbe binaryProbe = mock(CliBinaryProbe.class);
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        when(binaryProbe.isAvailable(anyString())).thenReturn(true);
        AiConversationProperties properties = new AiConversationProperties();
        properties.setRmqctlEnabled(true);
        AgentCapabilityProbe probe = new AgentCapabilityProbe(binaryProbe, properties, true, false, clock);
        if (expired) {
            probe.binaries();
            when(clock.instant()).thenReturn(start.plusSeconds(60));
        }

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(binaryProbe.isAvailable(anyString())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return true;
        });
        FutureTask<AgentCapabilityProbe.Binaries> first = new FutureTask<>(probe::binaries);
        FutureTask<AgentCapabilityProbe.Binaries> second = new FutureTask<>(probe::binaries);
        Thread firstThread = new Thread(first, "first-capability-request");
        Thread secondThread = new Thread(second, "second-capability-request");
        firstThread.start();
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            secondThread.start();
            awaitProbeOrMonitor(secondThread);
        } finally {
            release.countDown();
            firstThread.join(TimeUnit.SECONDS.toMillis(5));
            secondThread.join(TimeUnit.SECONDS.toMillis(5));
        }

        assertThat(second.get(5, TimeUnit.SECONDS)).isSameAs(first.get(5, TimeUnit.SECONDS));
        assertThat(calls).hasValue(3);
        assertThat(probe.binaries()).isSameAs(first.get());
        assertThat(calls).hasValue(3);
    }

    private static void awaitProbeOrMonitor(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            StackTraceElement[] stack = thread.getStackTrace();
            if (state == Thread.State.TIMED_WAITING || state == Thread.State.BLOCKED
                    && stack.length > 0 && stack[0].getClassName().equals(AgentCapabilityProbe.class.getName())) {
                return;
            }
            Thread.yield();
        }
        throw new AssertionError("second request did not reach the probe or refresh monitor");
    }
}
