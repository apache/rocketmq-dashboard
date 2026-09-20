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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPayloadGuardTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void toolNameMeasuresUtf8BytesAndAcceptsTheExactBoundary() {
        String boundary = "\u754c".repeat(AiPayloadGuard.MAX_TOOL_NAME_BYTES / 3) + "x";
        String oversized = boundary + "\u754c";

        assertThatCode(() -> AiPayloadGuard.validateToolInvocation(
                boundary, Map.of(), objectMapper)).doesNotThrowAnyException();
        assertThatThrownBy(() -> AiPayloadGuard.validateToolInvocation(
                oversized, Map.of(), objectMapper))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UTF-8 bytes");
    }

    @Test
    void toolInputUsesItsSerializedJsonSize() {
        Map<String, Object> input = Map.of(
                "payload", "x".repeat(AiPayloadGuard.MAX_TOOL_INPUT_BYTES));

        assertThatThrownBy(() -> AiPayloadGuard.validateToolInvocation("rmq.query", input, objectMapper))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tool input must not exceed");
    }

    @Test
    void outboundModelUsesTheProviderErrorContract() {
        assertThatThrownBy(() -> AiPayloadGuard.validateOutboundPrompt(
                "hello", "\u6a21".repeat(AiPayloadGuard.MAX_MODEL_BYTES / 3 + 1)))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(400);
                    assertThat(exception.getCode()).isEqualTo("llm.request.payload_too_large");
                    assertThat(exception.getMessage()).contains("LLM model");
                });
    }
}
