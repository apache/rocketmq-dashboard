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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link LlmGatewayException}: the typed failure of the LLM gateway path. The convenience
 * constructors must land on the 502 llm.gateway_error defaults - that code is what the AI layer
 * recognises as a gateway-level problem rather than a provider verdict.
 */
class LlmGatewayExceptionTest {

    @Test
    void theMessageOnlyConstructorDefaultsToGateway502() {
        LlmGatewayException exception = new LlmGatewayException("upstream broke");

        assertThat(exception.getStatusCode()).isEqualTo(502);
        assertThat(exception.getCode()).isEqualTo("llm.gateway_error");
        assertThat(exception.getMessage()).isEqualTo("upstream broke");
        assertThat(exception.getHint()).isNull();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void theCauseOverloadKeepsTheGatewayDefaultsAndChains() {
        IllegalStateException cause = new IllegalStateException("connection reset");
        LlmGatewayException exception = new LlmGatewayException("stream failed", cause);

        assertThat(exception.getStatusCode()).isEqualTo(502);
        assertThat(exception.getCode()).isEqualTo("llm.gateway_error");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void aTypedFailureCarriesItsStatusCodeCodeAndHint() {
        LlmGatewayException exception = new LlmGatewayException(
                429, "llm.provider.rate_limited", "too many requests", "back off and retry");

        assertThat(exception.getStatusCode()).isEqualTo(429);
        assertThat(exception.getCode()).isEqualTo("llm.provider.rate_limited");
        assertThat(exception.getMessage()).isEqualTo("too many requests");
        assertThat(exception.getHint()).isEqualTo("back off and retry");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void theFullConstructorChainsTheCauseAlongsideTheCode() {
        RuntimeException cause = new RuntimeException("timeout");
        LlmGatewayException exception = new LlmGatewayException(
                504, "llm.provider.timeout", "read timed out", "check latency", cause);

        assertThat(exception.getStatusCode()).isEqualTo(504);
        assertThat(exception.getCode()).isEqualTo("llm.provider.timeout");
        assertThat(exception.getHint()).isEqualTo("check latency");
        assertThat(exception.getCause()).isSameAs(cause);
    }
}
