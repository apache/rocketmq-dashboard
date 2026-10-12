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
 * Pins the agent capability flags of {@link AiAgentCapabilitiesVO}: the availability booleans
 * that decide which engines the new-conversation picker offers - a fresh view offers nothing,
 * and every flag defaults false so the UI never advertises an engine the runtime probe has not
 * confirmed.
 */
class AiAgentCapabilitiesVOTest {

    @Test
    void aFreshViewOffersNothing() {
        AiAgentCapabilitiesVO capabilities = new AiAgentCapabilitiesVO();
        assertThat(capabilities.isRmqctlAvailable()).isFalse();
        assertThat(capabilities.isClaudeAvailable()).isFalse();
        assertThat(capabilities.isQoderAvailable()).isFalse();
        assertThat(capabilities.isMcpEnabled()).isFalse();
        assertThat(capabilities.isL3ToolsAllowed()).isFalse();
    }

    @Test
    void everyFlagRoundTripsThroughTheBuilder() {
        AiAgentCapabilitiesVO capabilities = AiAgentCapabilitiesVO.builder()
                .rmqctlAvailable(true)
                .claudeAvailable(true)
                .qoderAvailable(false)
                .mcpEnabled(true)
                .l3ToolsAllowed(true)
                .build();

        assertThat(capabilities.isRmqctlAvailable()).isTrue();
        assertThat(capabilities.isClaudeAvailable()).isTrue();
        assertThat(capabilities.isQoderAvailable()).isFalse();
        assertThat(capabilities.isMcpEnabled()).isTrue();
        assertThat(capabilities.isL3ToolsAllowed()).isTrue();
    }
}
