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

import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageRequest;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupResetOffsetInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryInput;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Argument binding for tool calls. The published input schema declares every numeric argument as
 * an integer, which JSON Schema accepts for an integral number of any magnitude; a value that
 * cannot be represented by the {@code long}/{@code int} component it binds to is a caller error
 * and must be reported as such (merged precedent: #2311).
 */
class ToolExecutionContextTest {

    private static final String INSTANCE_ID = "instance-a";

    private static ToolExecutionContext context(Map<String, Object> input) {
        return ToolExecutionContext.of(INSTANCE_ID, null, input);
    }

    @Test
    void convertInputRejectsAFloatArgumentOutsideTheLongRangeTest() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("instanceId", INSTANCE_ID);
        input.put("topicName", "TopicA");
        input.put("key", "keyA");
        input.put("startTime", 1.0E30);

        assertThatThrownBy(() -> context(input).convertInput(MessageQueryInput.class))
                .isInstanceOfSatisfying(ToolExecutionException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo("INVALID_ARGUMENT");
                    assertThat(error.getCode()).isEqualTo(400);
                    assertThat(error.getMessage()).contains("startTime");
                });
    }

    @Test
    void convertInputRejectsAnIntegerArgumentOutsideTheLongRangeTest() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("instanceId", INSTANCE_ID);
        input.put("groupName", "group-a");
        input.put("topicName", "TopicA");
        input.put("timestamp", new BigInteger("9223372036854775808"));

        assertThatThrownBy(() -> context(input).convertInput(GroupResetOffsetInput.class))
                .isInstanceOfSatisfying(ToolExecutionException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo("INVALID_ARGUMENT");
                    assertThat(error.getMessage()).contains("timestamp");
                });
    }

    @Test
    void convertInputRejectsANestedArgumentOutsideTheIntRangeTest() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("instanceId", INSTANCE_ID);
        input.put("page", Map.of("page", 1, "pageSize", 5_000_000_000L));

        assertThatThrownBy(() -> context(input).convertInput(AclListInput.class))
                .isInstanceOfSatisfying(ToolExecutionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("INVALID_ARGUMENT"));
    }

    @Test
    void convertInputKeepsRepresentableArgumentsTest() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("instanceId", INSTANCE_ID);
        input.put("topicName", "TopicA");
        input.put("key", "keyA");
        input.put("startTime", 1.7E12);
        input.put("endTime", 1700000000000L);

        MessageQueryInput query = context(input).convertInput(MessageQueryInput.class);

        assertThat(query.startTime()).isEqualTo(1700000000000L);
        assertThat(query.endTime()).isEqualTo(1700000000000L);

        AclListInput paged = context(Map.of("instanceId", INSTANCE_ID,
                "page", Map.of("page", 2, "pageSize", 50))).convertInput(AclListInput.class);

        assertThat(paged.page()).isEqualTo(new PageRequest(2, 50));
    }
}