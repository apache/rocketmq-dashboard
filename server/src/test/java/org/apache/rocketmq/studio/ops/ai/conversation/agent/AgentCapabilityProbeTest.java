/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentCapabilityProbeTest {

    /** A clock the test walks forward by hand; the cache window is observable without sleeping. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private CliBinaryProbe binaryProbe;
    private AiConversationProperties properties;
    private final MutableClock clock = new MutableClock();

    @BeforeEach
    void setUp() {
        binaryProbe = mock(CliBinaryProbe.class);
        properties = new AiConversationProperties();
        when(binaryProbe.isAvailable(anyString())).thenReturn(true);
    }

    private AgentCapabilityProbe newProbe(boolean mcpEnabled, boolean l3Allowed) {
        return new AgentCapabilityProbe(binaryProbe, properties, mcpEnabled, l3Allowed, clock);
    }

    @Test
    void theProbeRoundCachesForSixtySeconds() {
        AgentCapabilityProbe probe = newProbe(true, true);
        AtomicInteger probeRounds = new AtomicInteger();
        when(binaryProbe.isAvailable(anyString())).thenAnswer(invocation -> {
            probeRounds.incrementAndGet();
            return true;
        });

        probe.binaries();
        clock.advance(Duration.ofSeconds(59));
        probe.binaries();

        // Three binaries per round, one round only inside the TTL.
        assertThat(probeRounds.get()).isEqualTo(3);

        clock.advance(Duration.ofSeconds(2));
        probe.binaries();

        assertThat(probeRounds.get()).isEqualTo(6);
    }

    @Test
    void aCachedAnswerCarriesItsProbeTimestamp() {
        AgentCapabilityProbe probe = newProbe(true, true);

        AgentCapabilityProbe.Binaries first = probe.binaries();
        clock.advance(Duration.ofSeconds(30));

        AgentCapabilityProbe.Binaries second = probe.binaries();

        assertThat(second).isEqualTo(first);
        assertThat(second.probedAt()).isEqualTo(clock.instant().minus(Duration.ofSeconds(30)));
    }

    @Test
    void rmqctlReportsUnavailableWhenTheFlagIsOffEvenIfTheBinaryIsPresent() {
        properties.setRmqctlEnabled(false);

        AgentCapabilityProbe.Binaries binaries = newProbe(true, true).binaries();

        assertThat(binaries.rmqctl()).isFalse();
        // The flag short-circuits: the rmqctl binary is never even probed.
        verify(binaryProbe, times(0)).isAvailable("rmqctl");
        assertThat(binaries.claude()).isTrue();
        assertThat(binaries.qoder()).isTrue();
    }

    @Test
    void rmqctlAvailabilityRequiresBothTheFlagAndTheBinary() {
        properties.setRmqctlEnabled(true);
        when(binaryProbe.isAvailable("rmqctl")).thenReturn(false);

        AgentCapabilityProbe.Binaries binaries = newProbe(true, true).binaries();

        assertThat(binaries.rmqctl()).isFalse();
    }

    @Test
    void claudeAndQoderProbeTheirOwnBinaries() {
        when(binaryProbe.isAvailable("claude")).thenReturn(false);
        when(binaryProbe.isAvailable("qodercli")).thenReturn(true);

        AgentCapabilityProbe.Binaries binaries = newProbe(true, true).binaries();

        assertThat(binaries.claude()).isFalse();
        assertThat(binaries.qoder()).isTrue();
    }

    @Test
    void theConfigurationFlagsRoundTrip() {
        AgentCapabilityProbe enabled = newProbe(true, true);
        AgentCapabilityProbe disabled = newProbe(false, false);

        assertThat(enabled.mcpEnabled()).isTrue();
        assertThat(enabled.l3ToolsAllowed()).isTrue();
        assertThat(disabled.mcpEnabled()).isFalse();
        assertThat(disabled.l3ToolsAllowed()).isFalse();
    }

    @Test
    void eachProbeInstanceCachesIndependently() {
        AgentCapabilityProbe first = newProbe(true, true);
        AgentCapabilityProbe second = newProbe(true, true);

        first.binaries();
        clock.advance(Duration.ofSeconds(30));

        // A second instance must fork its own round: the cache is per instance, not static.
        verify(binaryProbe, times(3)).isAvailable(anyString());
        second.binaries();
        verify(binaryProbe, times(6)).isAvailable(anyString());
    }
}
