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

import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link AbortReason}: the discriminated abort decision. Each constant fixes the terminal
 * status and the persisted stop reason, so the @PreDestroy drain writes SHUTDOWN, the orphan sweep
 * writes ORPHANED, and the stop endpoint writes USER_STOP — each of which is what actually happened.
 */
class AbortReasonTest {

    @Test
    void aUserStopAndAShutdownBothEndStopped() {
        assertThat(AbortReason.USER_STOP.status()).isEqualTo(RunStatus.STOPPED);
        assertThat(AbortReason.SHUTDOWN.status()).isEqualTo(RunStatus.STOPPED);
    }

    @Test
    void aTimeoutAndAnOrphanBothEndFailed() {
        assertThat(AbortReason.TIMEOUT.status()).isEqualTo(RunStatus.FAILED);
        assertThat(AbortReason.ORPHANED.status()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void everyReasonCarriesItsPersistedStopReason() {
        assertThat(AbortReason.USER_STOP.stopReason()).isEqualTo(StopReason.USER_STOP);
        assertThat(AbortReason.SHUTDOWN.stopReason()).isEqualTo(StopReason.SHUTDOWN);
        assertThat(AbortReason.TIMEOUT.stopReason()).isEqualTo(StopReason.TIMEOUT);
        assertThat(AbortReason.ORPHANED.stopReason()).isEqualTo(StopReason.ORPHANED);
    }

    @Test
    void onlyShutdownUsesTheShorterGrace() {
        assertThat(AbortReason.USER_STOP.usesFullGrace()).isTrue();
        assertThat(AbortReason.TIMEOUT.usesFullGrace()).isTrue();
        assertThat(AbortReason.ORPHANED.usesFullGrace()).isTrue();
        assertThat(AbortReason.SHUTDOWN.usesFullGrace()).isFalse();
    }
}
