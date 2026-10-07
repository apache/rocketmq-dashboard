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
package org.apache.rocketmq.studio.ops.ai.conversation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the lifecycle vocabulary of {@link RunStatus}: which states are terminal, which two the
 * admission rule and the startup reaper treat as still running, and the uppercase wire shape both
 * the database column and the run_status events carry.
 */
class RunStatusTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void completedStoppedAndFailedAreTerminal() {
        assertThat(RunStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(RunStatus.STOPPED.isTerminal()).isTrue();
        assertThat(RunStatus.FAILED.isTerminal()).isTrue();
    }

    @Test
    void queuedAndRunningAreNotTerminal() {
        assertThat(RunStatus.QUEUED.isTerminal()).isFalse();
        assertThat(RunStatus.RUNNING.isTerminal()).isFalse();
    }

    /**
     * ACTIVE_STATUSES is what the admission query and the startup reaper agree "still running"
     * means: exactly QUEUED and RUNNING. A terminal status creeping in would let two runs of one
     * conversation be admitted at once.
     */
    @Test
    void activeStatusesAreExactlyQueuedAndRunning() {
        assertThat(RunStatus.ACTIVE_STATUSES).containsExactly("QUEUED", "RUNNING");
    }

    @Test
    void serialisesAsTheUppercaseEnumName() throws Exception {
        assertThat(mapper.writeValueAsString(RunStatus.RUNNING)).isEqualTo("\"RUNNING\"");
        assertThat(mapper.writeValueAsString(RunStatus.COMPLETED)).isEqualTo("\"COMPLETED\"");
        assertThat(mapper.readValue("\"FAILED\"", RunStatus.class)).isEqualTo(RunStatus.FAILED);
    }
}
