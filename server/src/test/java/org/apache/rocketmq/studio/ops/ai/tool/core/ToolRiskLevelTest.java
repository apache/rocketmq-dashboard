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

package org.apache.rocketmq.studio.ops.ai.tool.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ToolRiskLevel}, the three-tier risk classification the AI tool gate keys on:
 * read-only tools pass freely, mutations need a plan and confirmation, high-risk ones need
 * break-glass. The level names and their {@code operationLevel} strings both travel to the
 * client, so each level must keep its own label — a swapped label would let a mutation pass
 * the read-only gate.
 */
class ToolRiskLevelTest {

    @Test
    void theThreeTiersAreStable() {
        Set<String> levels = Arrays.stream(ToolRiskLevel.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(ToolRiskLevel.values()).hasSize(3);
        assertThat(levels).containsExactlyInAnyOrder("L1", "L2", "L3");
    }

    @Test
    void everyTierCarriesItsOwnOperationLevel() {
        assertThat(ToolRiskLevel.L1.operationLevel()).isEqualTo("L1_READ_ONLY");
        assertThat(ToolRiskLevel.L2.operationLevel()).isEqualTo("L2_MUTATION");
        assertThat(ToolRiskLevel.L3.operationLevel()).isEqualTo("L3_HIGH_RISK");

        assertThat(Arrays.stream(ToolRiskLevel.values())
                .map(ToolRiskLevel::operationLevel))
                .doesNotHaveDuplicates();
    }
}
