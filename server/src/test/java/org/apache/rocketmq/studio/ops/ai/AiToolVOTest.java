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
package org.apache.rocketmq.studio.ops.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the tool listing row of {@link AiToolVO}: the catalog entry the tools page renders -
 * name, description, risk and permission levels, required capabilities and the deprecation
 * pair - with a fresh row reading as implemented and not deprecated.
 */
class AiToolVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        AiToolVO tool = AiToolVO.builder()
                .name("rmq.topic.list")
                .version("1.0")
                .cli(java.util.Map.of("resource", "topic", "verb", "list"))
                .description("Lists topics")
                .parameters(java.util.Map.of())
                .riskLevel("L1")
                .operationLevel("READ")
                .permission("READ")
                .requiredCapabilities(List.of("TOPIC_READ"))
                .outputSchema(java.util.Map.of())
                .viewHint("table")
                .deprecated(false)
                .replacement("rmq.topic.listV2")
                .implemented(true)
                .build();

        assertThat(tool.getName()).isEqualTo("rmq.topic.list");
        assertThat(tool.getVersion()).isEqualTo("1.0");
        assertThat(tool.getDescription()).isEqualTo("Lists topics");
        assertThat(tool.getRiskLevel()).isEqualTo("L1");
        assertThat(tool.getOperationLevel()).isEqualTo("READ");
        assertThat(tool.getPermission()).isEqualTo("READ");
        assertThat(tool.getRequiredCapabilities()).containsExactly("TOPIC_READ");
        assertThat(tool.getViewHint()).isEqualTo("table");
        assertThat(tool.isDeprecated()).isFalse();
        assertThat(tool.getReplacement()).isEqualTo("rmq.topic.listV2");
        assertThat(tool.isImplemented()).isTrue();
    }

    @Test
    void aFreshRowIsImplementedAndNotDeprecated() {
        AiToolVO tool = new AiToolVO();
        assertThat(tool.isImplemented()).isFalse();
        assertThat(tool.isDeprecated()).isFalse();
        assertThat(tool.getRequiredCapabilities()).isNull();
    }
}
