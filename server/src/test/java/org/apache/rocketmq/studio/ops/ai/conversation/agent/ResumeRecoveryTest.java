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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ResumeRecovery}: the recognition of the one failure mode a retry can fix — the
 * session the conversation remembers no longer exists on disk. Both signals are checked because
 * either alone is fragile, and the retry is gated on the run actually having passed --resume,
 * because retrying a run that never resumed anything just repeats it.
 */
class ResumeRecoveryTest {

    private static final String NOT_FOUND =
            "No conversation found with session ID: 3f9c2a1b\n";

    @Test
    void aZeroExitIsNeverALostResume() {
        assertThat(ResumeRecovery.isLostResumeSignal(
                0, ResumeRecovery.ERROR_DURING_EXECUTION, NOT_FOUND)).isFalse();
        assertThat(ResumeRecovery.isLostResumeSignal(0, null, NOT_FOUND)).isFalse();
    }

    @Test
    void theErrorDuringExecutionSubtypeAloneCarriesTheSignal() {
        assertThat(ResumeRecovery.isLostResumeSignal(
                1, ResumeRecovery.ERROR_DURING_EXECUTION, null)).isTrue();
        assertThat(ResumeRecovery.isLostResumeSignal(
                1, ResumeRecovery.ERROR_DURING_EXECUTION, "unrelated stderr")).isTrue();
    }

    @Test
    void theSessionNotFoundStderrLineAloneCarriesTheSignal() {
        assertThat(ResumeRecovery.isLostResumeSignal(1, null, NOT_FOUND)).isTrue();
        // leading whitespace before the line is tolerated
        assertThat(ResumeRecovery.isLostResumeSignal(1, "ok", "  \n" + NOT_FOUND)).isTrue();
    }

    @Test
    void theStderrLineMustBeTheFirstNonBlankContent() {
        // the prefix match is anchored: a log line mentioning the phrase later
        // in the stderr is not the CLI's session-not-found line
        assertThat(ResumeRecovery.isLostResumeSignal(
                1, "ok", "wrapper: something failed\n" + NOT_FOUND)).isFalse();
    }

    @Test
    void anUnrelatedFailureIsNotALostResume() {
        assertThat(ResumeRecovery.isLostResumeSignal(
                1, "success", "Usage: claude [options]")).isFalse();
        assertThat(ResumeRecovery.isLostResumeSignal(1, "ok", "")).isFalse();
        assertThat(ResumeRecovery.isLostResumeSignal(1, null, null)).isFalse();
    }

    @Test
    void theRetryOnlyAppliesToARunThatActuallyResumed() {
        assertThat(ResumeRecovery.shouldRetryWithoutResume(
                true, 1, ResumeRecovery.ERROR_DURING_EXECUTION, null)).isTrue();
        assertThat(ResumeRecovery.shouldRetryWithoutResume(
                true, 1, null, NOT_FOUND)).isTrue();
        // a fresh run that never passed --resume must not be retried
        assertThat(ResumeRecovery.shouldRetryWithoutResume(
                false, 1, ResumeRecovery.ERROR_DURING_EXECUTION, NOT_FOUND)).isFalse();
    }

    @Test
    void theWireContractConstantsAreStable() {
        assertThat(ResumeRecovery.RESUME_LOST_CODE).isEqualTo("llm.provider.resume_lost");
        assertThat(ResumeRecovery.SESSION_NOT_FOUND_PREFIX)
                .isEqualTo("No conversation found with session ID:");
        assertThat(ResumeRecovery.ERROR_DURING_EXECUTION).isEqualTo("error_during_execution");
    }
}
