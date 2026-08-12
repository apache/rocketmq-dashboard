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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeCodeAgentProviderTest {

    @Test
    void streamShouldDrainLargeStderrOutputTest() {
        TestClaudeCodeAgentProvider provider = new TestClaudeCodeAgentProvider(List.of(
                "sh", "-c", "yes error | head -c 131072 >&2; "
                        + "printf eyJ0eXBlIjoicmVzdWx0IiwicmVzdWx0IjoiZG9uZSJ9 | base64 -d"), 5);
        List<String> tokens = new ArrayList<>();

        provider.stream(LlmConfigVO.builder().build(), "prompt", null, tokens::add);

        assertThat(tokens).containsExactly("done");
    }

    @Test
    void streamShouldEnforceTimeoutBeforeWaitingForStdoutTest() {
        TestClaudeCodeAgentProvider provider = new TestClaudeCodeAgentProvider(
                List.of("sh", "-c", "sleep 2"), 1);

        assertThatThrownBy(() -> provider.stream(
                LlmConfigVO.builder().build(), "prompt", null, ignored -> { }))
                .isInstanceOf(LlmGatewayException.class)
                .satisfies(exception -> assertThat(((LlmGatewayException) exception).getStatusCode())
                        .isEqualTo(504));
    }

    @Test
    void streamShouldRejectOversizedStdoutTest() {
        TestClaudeCodeAgentProvider provider = new TestClaudeCodeAgentProvider(
                List.of("sh", "-c", "head -c 6291456 /dev/zero | tr '\\0' x"), 5);

        assertOutputTooLarge(provider);
    }

    @Test
    void streamShouldRejectOversizedStderrTest() {
        TestClaudeCodeAgentProvider provider = new TestClaudeCodeAgentProvider(
                List.of("sh", "-c", "head -c 6291456 /dev/zero >&2"), 5);

        assertOutputTooLarge(provider);
    }

    @Test
    void streamShouldApplyLimitAcrossStdoutAndStderrTest() {
        TestClaudeCodeAgentProvider provider = new TestClaudeCodeAgentProvider(List.of(
                "sh", "-c", "head -c 3145728 /dev/zero | tr '\\0' x; "
                        + "head -c 3145728 /dev/zero >&2"), 5);

        assertOutputTooLarge(provider);
    }

    private void assertOutputTooLarge(TestClaudeCodeAgentProvider provider) {
        assertThatThrownBy(() -> provider.stream(
                LlmConfigVO.builder().build(), "prompt", null, ignored -> { }))
                .isInstanceOf(LlmGatewayException.class)
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(502);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.provider.output_too_large");
                });
    }

    private static class TestClaudeCodeAgentProvider extends ClaudeCodeAgentProvider {

        private final List<String> command;
        private final long timeoutSeconds;

        TestClaudeCodeAgentProvider(List<String> command, long timeoutSeconds) {
            super(null);
            this.command = command;
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        protected List<String> buildCommand(LlmConfigVO config, String prompt, String modelOverride) {
            return new ArrayList<>(command);
        }

        @Override
        protected Map<String, String> childEnv(LlmConfigVO config) {
            return Map.of();
        }

        @Override
        protected String binaryName() {
            return "sh";
        }

        @Override
        protected long streamTimeoutSeconds() {
            return timeoutSeconds;
        }
    }
}
