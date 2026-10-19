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

package org.apache.rocketmq.studio.ops.ai.tool.contract.group;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ResetOffsetOutput}, the AI tool's answer for a consumer group offset reset:
 * which group and topic were reset, to which timestamp — and the {@code reset} verdict. The
 * three-argument convenience constructor defaults the verdict to true (the "we did it"
 * answer); the four-argument one exists so a skipped reset can say so honestly.
 */
class ResetOffsetOutputTest {

    @Test
    void theConvenienceConstructorAnswersReset() {
        ResetOffsetOutput output = new ResetOffsetOutput("order-consumer-group", "order-topic", 1700000000000L);

        assertThat(output.group()).isEqualTo("order-consumer-group");
        assertThat(output.topic()).isEqualTo("order-topic");
        assertThat(output.timestamp()).isEqualTo(1700000000000L);
        assertThat(output.reset()).isTrue();
    }

    @Test
    void theFullConstructorCanSayTheResetDidNotHappen() {
        ResetOffsetOutput skipped = new ResetOffsetOutput(
                "order-consumer-group", "order-topic", 1700000000000L, false);

        assertThat(skipped.reset()).isFalse();
        assertThat(skipped).isEqualTo(new ResetOffsetOutput(
                "order-consumer-group", "order-topic", 1700000000000L, false));
        assertThat(skipped).isNotEqualTo(new ResetOffsetOutput(
                "order-consumer-group", "order-topic", 1700000000000L, true));
    }
}
