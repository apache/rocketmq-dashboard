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
package org.apache.rocketmq.studio.ops.ai.tool.contract.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link MessageRedeliveryOutput}: the redelivery result. The {@code redelivered} flag is
 * always true — the output only exists when a redelivery actually happened — and it travels under
 * the JSON name "redelivered", not the record accessor's default.
 */
class MessageRedeliveryOutputTest {

    @Test
    void theRedeliveredFlagIsAlwaysTrue() {
        MessageRedeliveryOutput output = new MessageRedeliveryOutput("original", "new", "%RETRY%group");

        assertThat(output.originalMsgId()).isEqualTo("original");
        assertThat(output.newMsgId()).isEqualTo("new");
        assertThat(output.targetTopic()).isEqualTo("%RETRY%group");
        assertThat(output.redelivered()).isTrue();
    }

    @Test
    void theFlagSerialisesAsRedelivered() throws Exception {
        String json = new ObjectMapper().writeValueAsString(
                new MessageRedeliveryOutput("original", "new", "%RETRY%group"));

        assertThat(json).contains("\"redelivered\":true");
    }
}
