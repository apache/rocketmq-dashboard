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
package org.apache.rocketmq.studio.ops.ai.conversation.vo;

import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the active run reference of {@link AiActiveRunRef}: the minimal pair - run id and
 * status - the timeline envelope carries so the UI can attach to a run that is still
 * streaming.
 */
class AiActiveRunRefTest {

    @Test
    void theReferenceRoundTripsThroughTheBuilder() {
        AiActiveRunRef ref = AiActiveRunRef.builder()
                .id(42L)
                .status(RunStatus.RUNNING)
                .build();

        assertThat(ref.getId()).isEqualTo(42L);
        assertThat(ref.getStatus()).isEqualTo(RunStatus.RUNNING);
    }

    @Test
    void aFreshReferenceCarriesNeither() {
        AiActiveRunRef ref = new AiActiveRunRef();
        assertThat(ref.getId()).isNull();
        assertThat(ref.getStatus()).isNull();
    }
}
