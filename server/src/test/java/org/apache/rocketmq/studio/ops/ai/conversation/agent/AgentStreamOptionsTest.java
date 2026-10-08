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
package org.apache.rocketmq.studio.ops.ai.conversation.agent;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link AgentStreamOptions}: the provider-neutral bag a hosted-agent run is spawned from.
 * The two load-bearing contracts are the credential isolation of {@code extraEnv} (the per-run
 * rmqctl handshake travels there, so a log line interpolating the options must not leak it) and
 * the never-null normalisation that lets callers iterate without a guard.
 */
class AgentStreamOptionsTest {

    private static final class RecordingSink implements AgentStreamOptions.AgentProcessSink {

        private Process attached;
        private int calls;

        @Override
        public void attachProcess(Process process) {
            attached = process;
            calls++;
        }
    }

    @Test
    void extraEnvDefaultsToAnEmptyMap() {
        AgentStreamOptions options = AgentStreamOptions.builder()
                .prompt("hello").model("claude-3").build();

        assertThat(options.getExtraEnv()).isNotNull().isEmpty();
    }

    @Test
    void anExplicitlyNullExtraEnvIsNormalisedToEmpty() {
        AgentStreamOptions options = AgentStreamOptions.builder()
                .prompt("hello").model("claude-3")
                .extraEnv(null)
                .build();

        assertThat(options.getExtraEnv()).isNotNull().isEmpty();
    }

    /**
     * The credential-leak contract: extraEnv is where RMQ_AI_ACCESS_KEY / RMQ_AI_SECRET_KEY travel,
     * and toString() must never surface them. A log line that interpolates the options must not
     * become a credential leak.
     */
    @Test
    void toStringNeverSurfacesTheCredentialEnvironment() {
        Map<String, String> secrets = new HashMap<>();
        secrets.put("RMQ_AI_ACCESS_KEY", "ak-do-not-leak");
        secrets.put("RMQ_AI_SECRET_KEY", "sk-do-not-leak");
        AgentStreamOptions options = AgentStreamOptions.builder()
                .prompt("hello").model("claude-3")
                .extraEnv(secrets)
                .build();

        String rendered = options.toString();

        assertThat(rendered)
                .doesNotContain("ak-do-not-leak")
                .doesNotContain("sk-do-not-leak")
                .doesNotContain("RMQ_AI_ACCESS_KEY")
                .doesNotContain("RMQ_AI_SECRET_KEY");
        // the values are still there for the provider, just not in the log line
        assertThat(options.getExtraEnv()).containsEntry("RMQ_AI_ACCESS_KEY", "ak-do-not-leak");
    }

    @Test
    void theTimeoutFallsBackOnlyWhenUnset() {
        Duration budget = Duration.ofSeconds(90);
        AgentStreamOptions withTimeout = AgentStreamOptions.builder()
                .prompt("hello").model("m").timeout(budget).build();
        AgentStreamOptions withoutTimeout = AgentStreamOptions.builder()
                .prompt("hello").model("m").build();

        assertThat(withTimeout.timeoutOr(Duration.ofSeconds(30))).isSameAs(budget);
        assertThat(withoutTimeout.timeoutOr(Duration.ofSeconds(30)))
                .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void toBuilderRoundTripsEveryFieldAndAllowsOneOverride() {
        RecordingSink sink = new RecordingSink();
        Map<String, String> env = Map.of("RMQ_AI_ACCESS_KEY", "ak");
        AgentStreamOptions original = AgentStreamOptions.builder()
                .prompt("hello").model("claude-3")
                .resumeSessionId("session-1")
                .mcpConfigPath("/tmp/mcp.json")
                .systemPromptPath("/tmp/system.txt")
                .workspaceDir("/tmp/ws")
                .instanceId("instance-a")
                .extraEnv(env)
                .processSink(sink)
                .timeout(Duration.ofMinutes(5))
                .build();

        AgentStreamOptions resumed = original.toBuilder()
                .resumeSessionId("session-2")
                .build();

        assertThat(resumed.getPrompt()).isEqualTo("hello");
        assertThat(resumed.getModel()).isEqualTo("claude-3");
        assertThat(resumed.getResumeSessionId()).isEqualTo("session-2");
        assertThat(resumed.getMcpConfigPath()).isEqualTo("/tmp/mcp.json");
        assertThat(resumed.getSystemPromptPath()).isEqualTo("/tmp/system.txt");
        assertThat(resumed.getWorkspaceDir()).isEqualTo("/tmp/ws");
        assertThat(resumed.getInstanceId()).isEqualTo("instance-a");
        assertThat(resumed.getExtraEnv()).isEqualTo(env);
        assertThat(resumed.getProcessSink()).isSameAs(sink);
        assertThat(resumed.getTimeout()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void theProcessSinkIsTheNarrowCancellationView() {
        RecordingSink sink = new RecordingSink();
        AgentStreamOptions options = AgentStreamOptions.builder()
                .prompt("hello").model("m").processSink(sink).build();

        assertThat(options.getProcessSink()).isSameAs(sink);

        // the provider hands the subprocess over exactly once, right after start
        Process fake = new Process() {
            @Override
            public OutputStream getOutputStream() {
                return OutputStream.nullOutputStream();
            }

            @Override
            public InputStream getInputStream() {
                return InputStream.nullInputStream();
            }

            @Override
            public InputStream getErrorStream() {
                return InputStream.nullInputStream();
            }

            @Override
            public int waitFor() {
                return 0;
            }

            @Override
            public int exitValue() {
                return 0;
            }

            @Override
            public void destroy() {
            }
        };
        options.getProcessSink().attachProcess(fake);

        assertThat(sink.calls).isEqualTo(1);
        assertThat(sink.attached).isSameAs(fake);
    }
}
