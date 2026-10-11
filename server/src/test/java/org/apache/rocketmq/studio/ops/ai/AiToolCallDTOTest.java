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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the tool-call payload of {@link AiToolCallDTO}: the tool name and the argument map
 * round-trip, and a call without arguments carries an absent map rather than an empty one - the
 * two mean different things on the wire.
 */
class AiToolCallDTOTest {

    @Test
    void aNamedCallWithArgumentsRoundTrips() {
        AiToolCallDTO call = AiToolCallDTO.builder()
                .name("rmq.topic.list")
                .arguments(Map.of("page", 1))
                .build();

        assertThat(call.getName()).isEqualTo("rmq.topic.list");
        assertThat(call.getArguments()).containsEntry("page", 1);
    }

    @Test
    void aCallWithoutArgumentsCarriesAnAbsentMap() {
        AiToolCallDTO call = AiToolCallDTO.builder().name("rmq.cluster.list").build();
        assertThat(call.getArguments()).isNull();
    }
}
