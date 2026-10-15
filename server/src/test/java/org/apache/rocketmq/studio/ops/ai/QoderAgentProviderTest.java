/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.apache.rocketmq.studio.ops.ai.conversation.agent.AgentStreamOptions;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QoderAgentProviderTest {

    private final QoderAgentProvider provider = new QoderAgentProvider(new CliProcessEnvironment(List.of()));

    @Test
    void spiDefaultStillDegradesToTheTextChannelTest() {
        // Qoder now owns a streaming entry, so the SPI default's degradation contract is pinned on a
        // neutral provider that implements nothing but complete(): one completion, one text delta.
        CliAgentProvider textOnly = new CliAgentProvider(new CliProcessEnvironment(List.of())) {
            @Override
            public String engine() {
                return "text-only";
            }

            @Override
            protected List<String> buildCommand(LlmConfigVO config, String prompt, String modelOverride) {
                return List.of("unused");
            }

            @Override
            protected Map<String, String> childEnv(LlmConfigVO config) {
                return Map.of();
            }

            @Override
            protected String binaryName() {
                return "unused";
            }

            @Override
            public String complete(LlmConfigVO config, String prompt, String modelOverride) {
                return "answered " + prompt + " with " + modelOverride;
            }
        };
        List<AgentEvent> events = new ArrayList<>();

        textOnly.streamEvents(LlmConfigVO.builder().model("configured").build(),
                AgentStreamOptions.builder().prompt("prompt").model("request").build(),
                events::add);

        assertThat(events).containsExactly(
                new AgentEvent.TextDelta("answered prompt with request"));
    }

    @Test
    void streamEventsAttachesTheProcessBeforeReadingOutputTest() throws Exception {
        // A stop that races the start must find the process: the run handle receives it immediately
        // after start and before any output byte is consumed.
        List<String> order = new CopyOnWriteArrayList<>();
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(
                "done".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                order.add("read-stdout");
                return super.read(buffer, offset, length);
            }
        });
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);
        List<AgentEvent> events = new ArrayList<>();

        new SeededProcessQoderAgentProvider(process).streamEvents(
                LlmConfigVO.builder().build(),
                AgentStreamOptions.builder().prompt("prompt").model("model")
                        .processSink(attached -> order.add("attach")).build(),
                events::add);

        assertThat(order).first().isEqualTo("attach");
        assertThat(order).contains("read-stdout");
        assertThat(events).containsExactly(new AgentEvent.TextDelta("done"));
    }

    @Test
    void streamEventsKeepsCliDiagnosticsOutOfTheAnswerTest() {
        TestQoderAgentProvider testProvider = new TestQoderAgentProvider(List.of(
                "sh", "-c", "printf 'SDK warning: config deprecated\\n' >&2; printf 'the answer'"));
        List<AgentEvent> events = new ArrayList<>();

        testProvider.streamEvents(LlmConfigVO.builder().build(),
                AgentStreamOptions.builder().prompt("prompt").model("model").build(), events::add);

        assertThat(events).containsExactly(new AgentEvent.TextDelta("the answer"));
    }

    @Test
    void streamEventsFailsANonZeroExitWithTheStderrDiagnosticsTest() {
        TestQoderAgentProvider testProvider = new TestQoderAgentProvider(List.of(
                "sh", "-c", "printf 'quota exhausted' >&2; exit 3"));

        assertThatThrownBy(() -> testProvider.streamEvents(LlmConfigVO.builder().build(),
                AgentStreamOptions.builder().prompt("prompt").model("model").build(),
                event -> { }))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("llm.provider.cli_error");
                    assertThat(exception.getMessage()).contains("quota exhausted");
                });
    }

    @Test
    void streamEventsRejectsACompletionThatNeverReachedStdoutTest() {
        TestQoderAgentProvider testProvider = new TestQoderAgentProvider(List.of(
                "sh", "-c", "printf 'warnings only' >&2"));

        assertThatThrownBy(() -> testProvider.streamEvents(LlmConfigVO.builder().build(),
                AgentStreamOptions.builder().prompt("prompt").model("model").build(),
                event -> { }))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("llm.provider.empty_completion"));
    }

    @Test
    void streamEventsTimeoutDestroysAndStillReportsTheAttachedProcessTest() throws Exception {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);
        List<Process> attached = new ArrayList<>();

        assertThatThrownBy(() -> new SeededProcessQoderAgentProvider(process).streamEvents(
                LlmConfigVO.builder().build(),
                AgentStreamOptions.builder().prompt("prompt").model("model")
                        .processSink(attached::add).build(),
                event -> { }))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("llm.provider.timeout"));
        verify(process).destroyForcibly();
        assertThat(attached).containsExactly(process);
    }

    @Test
    void buildCommandUsesConfiguredModelWhenRequestDoesNotOverrideIt() {
        LlmConfigVO config = LlmConfigVO.builder().model(" qoder-configured ").build();

        assertThat(provider.buildCommand(config, "prompt", null))
                .containsExactly("qodercli", "-p", "prompt", "-m", "qoder-configured");
    }

    @Test
    void buildCommandPrefersRequestModelOverride() {
        LlmConfigVO config = LlmConfigVO.builder().model("qoder-configured ").build();

        assertThat(provider.buildCommand(config, "prompt", " qoder-request "))
                .containsExactly("qodercli", "-p", "prompt", "-m", "qoder-request");
    }

    @Test
    void buildCommandOmitsBlankModel() {
        LlmConfigVO config = LlmConfigVO.builder().model(" ").build();

        assertThat(provider.buildCommand(config, null, null))
                .containsExactly("qodercli", "-p", "");
    }

    /** Drives the streaming path with a fixed command instead of a real {@code qodercli}. */
    private static class TestQoderAgentProvider extends QoderAgentProvider {

        private final List<String> command;

        TestQoderAgentProvider(List<String> command) {
            super(new CliProcessEnvironment(List.of()));
            this.command = command;
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
    }

    /** Adds the process seam the streaming entry exposes: the run starts a recorded process. */
    private static final class SeededProcessQoderAgentProvider extends TestQoderAgentProvider {

        private final Process process;

        SeededProcessQoderAgentProvider(Process process) {
            super(List.of("sh"));
            this.process = process;
        }

        @Override
        protected Process startProcess(ProcessBuilder builder) throws IOException {
            return process;
        }
    }
}
