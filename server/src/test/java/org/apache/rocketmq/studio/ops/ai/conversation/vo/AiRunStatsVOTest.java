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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the run-stat view of {@link AiRunStatsVO}: the client-reported generation speed
 * round-trips, and a never-reported speed stays null instead of degrading to zero - the
 * replayed transcript must not show a fabricated 0 tokens/s.
 */
class AiRunStatsVOTest {

    @Test
    void aReportedSpeedRoundTrips() {
        AiRunStatsVO stats = AiRunStatsVO.builder()
                .id(7L)
                .tokensPerSecond(42.5)
                .build();

        assertThat(stats.getId()).isEqualTo(7L);
        assertThat(stats.getTokensPerSecond()).isEqualTo(42.5);
    }

    @Test
    void aNeverReportedSpeedStaysNull() {
        AiRunStatsVO stats = AiRunStatsVO.builder().id(7L).build();
        assertThat(stats.getTokensPerSecond()).isNull();
    }
}
