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
    void streamUsesTheIsolatedEnvironment() {
        RecordingEnvironment processEnvironment = new RecordingEnvironment();
        TestClaudeCodeAgentProvider provider = new TestClaudeCodeAgentProvider(
                List.of("sh", "-c",
                        "printf eyJ0eXBlIjoicmVzdWx0IiwicmVzdWx0IjoiZG9uZSJ9 | base64 -d"),
                5,
                processEnvironment,
                Map.of("ANTHROPIC_AUTH_TOKEN", "request-token"));

        provider.stream(LlmConfigVO.builder().build(), "prompt", null, ignored -> { });

        assertThat(processEnvironment.childEnvironments).singleElement().satisfies(environment ->
                assertThat(environment)
                        .containsEntry("ANTHROPIC_AUTH_TOKEN", "request-token")
                        .doesNotContainKey("SERVER_SECRET"));
    }

    private static final class RecordingEnvironment extends CliProcessEnvironment {
        private final List<Map<String, String>> childEnvironments = new ArrayList<>();

        RecordingEnvironment() {
            super(List.of());
        }

        @Override
        void apply(ProcessBuilder builder, Map<String, String> providerEnvironment) {
            builder.environment().put("SERVER_SECRET", "must-not-cross-boundary");
            super.apply(builder, providerEnvironment);
            childEnvironments.add(Map.copyOf(builder.environment()));
        }
    }

    private static class TestClaudeCodeAgentProvider extends ClaudeCodeAgentProvider {

        private final List<String> command;
        private final long timeoutSeconds;
        private final Map<String, String> environment;

        TestClaudeCodeAgentProvider(List<String> command, long timeoutSeconds) {
            this(command, timeoutSeconds, new CliProcessEnvironment(List.of()), Map.of());
        }

        TestClaudeCodeAgentProvider(List<String> command, long timeoutSeconds,
                                    CliProcessEnvironment processEnvironment,
                                    Map<String, String> environment) {
            super(null, processEnvironment);
            this.command = command;
            this.timeoutSeconds = timeoutSeconds;
            this.environment = environment;
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
            return environment;
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
