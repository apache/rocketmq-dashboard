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
package org.apache.rocketmq.studio.ops.ai.conversation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the wire vocabulary of {@link ThinkingSource}: this single field is what keeps model
 * reasoning apart from the prompt-enhancement rewrite, and the strict decode is what makes a
 * drift between the two sides loud instead of silently reintroducing the coalescing bug.
 */
class ThinkingSourceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void eachSourceCarriesItsLowercaseWireName() {
        assertThat(ThinkingSource.MODEL.wireName()).isEqualTo("model");
        assertThat(ThinkingSource.ENHANCE.wireName()).isEqualTo("enhance");
    }

    @Test
    void decodesBothWireNames() {
        assertThat(ThinkingSource.fromWireName("model")).isEqualTo(ThinkingSource.MODEL);
        assertThat(ThinkingSource.fromWireName("enhance")).isEqualTo(ThinkingSource.ENHANCE);
    }

    /**
     * Strict on purpose: an unknown source means the two sides drifted, and silently falling back
     * to MODEL would reintroduce the coalescing bug this enum exists to prevent.
     */
    @Test
    void anUnknownWireNameIsRejectedLoudly() {
        assertThatThrownBy(() -> ThinkingSource.fromWireName("rewrite"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown thinking source: rewrite");
    }

    @Test
    void theWireNameMatchIsCaseSensitive() {
        assertThatThrownBy(() -> ThinkingSource.fromWireName("Model")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ThinkingSource.fromWireName("ENHANCE")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jacksonSerialisesTheLowercaseWireNameInBothDirections() throws Exception {
        assertThat(mapper.writeValueAsString(ThinkingSource.MODEL)).isEqualTo("\"model\"");
        assertThat(mapper.writeValueAsString(ThinkingSource.ENHANCE)).isEqualTo("\"enhance\"");
        assertThat(mapper.readValue("\"model\"", ThinkingSource.class)).isEqualTo(ThinkingSource.MODEL);
        assertThat(mapper.readValue("\"enhance\"", ThinkingSource.class)).isEqualTo(ThinkingSource.ENHANCE);
        assertThatThrownBy(() -> mapper.readValue("\"rewrite\"", ThinkingSource.class))
                .isInstanceOf(Exception.class);
    }
}
