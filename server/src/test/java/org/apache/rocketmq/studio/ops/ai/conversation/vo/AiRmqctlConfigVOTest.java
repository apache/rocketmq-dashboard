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
 * Pins {@link AiRmqctlConfigVO}, the snippet the AI page offers for pasting into an external
 * agent (Claude Desktop, Cursor, ...). It mirrors the frozen TS interface
 * {@code AiRmqctlConfigVO} in {@code web/src/api/aiEvents.ts}, so the three fields and their
 * names are a cross-language wire contract — all construction paths must agree and the
 * snippet must travel as a single paste-ready string.
 */
class AiRmqctlConfigVOTest {

    @Test
    void theConfigCarriesThePasteReadySnippet() {
        AiRmqctlConfigVO config = AiRmqctlConfigVO.builder()
                .snippet("{\"mcpServers\":{\"rocketmq-studio\":{\"command\":\"rmqctl\","
                        + "\"args\":[\"mcp\",\"stdio\",\"--instance-id\",\"instance-1\"]}}}")
                .instanceId("instance-1")
                .server("https://studio.example.com")
                .build();

        assertThat(config.getSnippet()).startsWith("{\"mcpServers\":");
        assertThat(config.getSnippet()).contains("--instance-id");
        assertThat(config.getInstanceId()).isEqualTo("instance-1");
        assertThat(config.getServer()).isEqualTo("https://studio.example.com");
    }

    @Test
    void allConstructionPathsAgree() {
        AiRmqctlConfigVO allArgs = new AiRmqctlConfigVO("{}", "instance-1", "https://studio.example.com");
        AiRmqctlConfigVO built = AiRmqctlConfigVO.builder()
                .snippet("{}")
                .instanceId("instance-1")
                .server("https://studio.example.com")
                .build();
        AiRmqctlConfigVO defaulted = new AiRmqctlConfigVO();
        defaulted.setSnippet("{}");
        defaulted.setInstanceId("instance-1");
        defaulted.setServer("https://studio.example.com");

        assertThat(allArgs).isEqualTo(built).isEqualTo(defaulted);
    }
}
