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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the risk predicates of {@link ToolDefinition}: they gate whether a tool call needs a
 * confirm token, a reason, or neither, so a wrong predicate silently weakens the guardrail.
 */
class ToolDefinitionTest {

    private static ToolDefinition definition(ToolRiskLevel riskLevel, String permission) {
        return new ToolDefinition(
                "rmq.cluster.describe",
                new ToolDefinition.Cli("cluster", "describe"),
                "describes a cluster",
                riskLevel,
                permission,
                List.of(),
                Map.of(),
                Map.of(),
                null,
                false,
                null);
    }

    @Test
    void onlyL1IsReadOnly() {
        assertThat(definition(ToolRiskLevel.L1, "cluster:read").isReadOnly()).isTrue();
        assertThat(definition(ToolRiskLevel.L2, "cluster:read").isReadOnly()).isFalse();
        assertThat(definition(ToolRiskLevel.L3, "cluster:read").isReadOnly()).isFalse();
    }

    @Test
    void onlyL3RequiresAReason() {
        assertThat(definition(ToolRiskLevel.L3, "cluster:delete").requiresReason()).isTrue();
        assertThat(definition(ToolRiskLevel.L1, "cluster:read").requiresReason()).isFalse();
        assertThat(definition(ToolRiskLevel.L2, "cluster:update").requiresReason()).isFalse();
    }

    /**
     * Low-risk-read-only is the fast path that skips the confirmation chain: it must demand both
     * halves — the L1 risk level AND a read permission. Either alone must not qualify.
     */
    @Test
    void lowRiskReadOnlyRequiresBothTheL1LevelAndAReadPermission() {
        assertThat(definition(ToolRiskLevel.L1, "cluster:read").isLowRiskReadOnly()).isTrue();
        assertThat(definition(ToolRiskLevel.L1, "cluster:delete").isLowRiskReadOnly()).isFalse();
        assertThat(definition(ToolRiskLevel.L2, "cluster:read").isLowRiskReadOnly()).isFalse();
        assertThat(definition(ToolRiskLevel.L3, "cluster:read").isLowRiskReadOnly()).isFalse();
    }

    /**
     * The permission check is the exact {@code :read} suffix, not a loose "read" substring: a
     * hyphenated {@code cluster-read} or a {@code :readonly} permission must not pass.
     */
    @Test
    void readPermissionMeansTheExactColonReadSuffix() {
        assertThat(definition(ToolRiskLevel.L1, "cluster:read").isLowRiskReadOnly()).isTrue();
        assertThat(definition(ToolRiskLevel.L1, "cluster-read").isLowRiskReadOnly()).isFalse();
        assertThat(definition(ToolRiskLevel.L1, "cluster:readonly").isLowRiskReadOnly()).isFalse();
        assertThat(definition(ToolRiskLevel.L1, "read").isLowRiskReadOnly()).isFalse();
    }

    @Test
    void cliExposesResourceAndVerb() {
        ToolDefinition.Cli cli = definition(ToolRiskLevel.L1, "cluster:read").cli();
        assertThat(cli.resource()).isEqualTo("cluster");
        assertThat(cli.verb()).isEqualTo("describe");
    }
}
