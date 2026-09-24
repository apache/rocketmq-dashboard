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

import org.apache.rocketmq.studio.ops.ai.conversation.agent.AgentStreamOptions;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.ResumeRecovery;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void streamShouldBoundStderrIncludedInFailuresTest() {
        TestClaudeCodeAgentProvider provider = new TestClaudeCodeAgentProvider(
                List.of("sh", "-c", "yes failure | head -c 131072 >&2; exit 1"), 5);

        assertThatThrownBy(() -> provider.stream(
                LlmConfigVO.builder().build(), "prompt", null, ignored -> { }))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("[stderr truncated]")
                .satisfies(exception -> assertThat(exception.getMessage().length()).isLessThan(70_000));
    }

    @Test
    void buildCommandShouldDisableAgentToolsTest() {
        ClaudeCodeAgentProvider provider = new ClaudeCodeAgentProvider(null, new CliProcessEnvironment(List.of()));
        List<String> command = provider.buildCommand(
                LlmConfigVO.builder().model("qwen3.8-max").build(), "check cluster status", null);

        assertThat(command).containsSubsequence("claude", "-p", "check cluster status");
        assertThat(command).containsSubsequence("--model", "qwen3.8-max");
        // Plain chat disables built-in tools so the agent cannot loop on tools or run commands.
        int index = command.indexOf("--disallowedTools");
        assertThat(index).isGreaterThanOrEqualTo(0);
        assertThat(command).contains("Bash", "Read", "WebSearch");
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

    @Test
    void streamInterruptionDestroysTheChildProcess() throws Exception {
        Process process = mock(Process.class);
        CountDownLatch waitStarted = new CountDownLatch(1);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getOutputStream()).thenReturn(new java.io.ByteArrayOutputStream());
        doAnswer(invocation -> {
            waitStarted.countDown();
            new CountDownLatch(1).await();
            return false;
        }).when(process).waitFor(anyLong(), eq(TimeUnit.SECONDS));
        TestClaudeCodeAgentProvider provider = new TestClaudeCodeAgentProvider(
                List.of("claude"), 300, process);
        AtomicReference<LlmGatewayException> failure = new AtomicReference<>();
        AtomicBoolean interruptPreserved = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                provider.stream(LlmConfigVO.builder().build(), "prompt", null, ignored -> { });
            } catch (LlmGatewayException exception) {
                failure.set(exception);
                interruptPreserved.set(Thread.currentThread().isInterrupted());
            } finally {
                finished.countDown();
            }
        });
        worker.start();
        assertThat(waitStarted.await(5, TimeUnit.SECONDS)).isTrue();

        worker.interrupt();

        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNotNull();
        assertThat(failure.get().getCode()).isEqualTo("llm.provider.interrupted");
        assertThat(interruptPreserved).isTrue();
        verify(process).destroyForcibly();
    }

    @Test
    void buildStreamCommandShouldCarryResumeMcpAndSystemPromptTest(@TempDir Path workspace) throws IOException {
        Path systemPrompt = workspace.resolve("agent-system-prompt.txt");
        Files.writeString(systemPrompt, "You are the RocketMQ Studio hosted agent.");
        ClaudeCodeAgentProvider provider = new ClaudeCodeAgentProvider(null, new CliProcessEnvironment(List.of()));
        AgentStreamOptions options = AgentStreamOptions.builder()
                .prompt("list the topics")
                .model("qwen3.8-max")
                .resumeSessionId("280b366b-a346-440c-b595-bf28c68910a4")
                .mcpConfigPath(workspace.resolve("mcp.json").toString())
                .systemPromptPath(systemPrompt.toString())
                .workspaceDir(workspace.toString())
                .instanceId("localtest")
                .build();

        List<String> command = provider.buildStreamCommand(LlmConfigVO.builder().build(), options);

        assertThat(command).containsSubsequence("claude", "-p", "list the topics");
        // Without --model the CLI picks its own default and the gateway answers "Model not exist."
        assertThat(command).containsSubsequence("--model", "qwen3.8-max");
        assertThat(command).containsSubsequence("--resume", "280b366b-a346-440c-b595-bf28c68910a4");
        assertThat(command).containsSubsequence(
                "--output-format", "stream-json", "--verbose", "--include-partial-messages");
        assertThat(command).containsSubsequence(
                "--mcp-config", workspace.resolve("mcp.json").toString(), "--strict-mcp-config");
        // Server-level allow, because claude sanitises every '.' in an MCP tool name away.
        assertThat(command).containsSubsequence("--allowedTools", "mcp__rocketmq-studio");
        // The flag takes text, not a path, so the file is read here.
        assertThat(command).containsSubsequence(
                "--append-system-prompt", "You are the RocketMQ Studio hosted agent.");
        // Built-in tools stay disabled even though the MCP server is now allowed.
        int index = command.indexOf("--disallowedTools");
        assertThat(index).isGreaterThanOrEqualTo(0);
        assertThat(command.subList(index + 1, command.size()))
                .contains("Bash", "Read", "Write", "Edit", "Glob", "Grep", "Task",
                        "WebFetch", "WebSearch", "TodoWrite", "NotebookEdit");
    }

    @Test
    void buildStreamCommandShouldOmitWhatTheOptionsDoNotCarryTest() {
        ClaudeCodeAgentProvider provider = new ClaudeCodeAgentProvider(null, new CliProcessEnvironment(List.of()));

        List<String> command = provider.buildStreamCommand(
                LlmConfigVO.builder().model("qwen3.8-max").build(),
                AgentStreamOptions.builder().prompt("hi").build());

        assertThat(command).containsSubsequence("--model", "qwen3.8-max");
        assertThat(command).containsSubsequence("--output-format", "stream-json");
        assertThat(command).contains("--disallowedTools", "Bash");
        // No MCP config means plain chat: no strict mode, no allow-list, no resume, no extra prompt.
        assertThat(command).doesNotContain("--mcp-config", "--strict-mcp-config", "--allowedTools",
                "--append-system-prompt", "--resume");
    }

    /**
     * The degraded half of the rmqctl contract, and a hard requirement on its own: when
     * {@code RmqctlWorkspace} cannot find rmqctl it returns {@code Optional.empty()}, the run
     * executor builds options with no {@code mcpConfigPath}, and the conversation must still work as
     * plain chat. That means exactly today's command — the complete built-in disallow list, none of
     * the MCP flags — even for a conversation that is bound to an instance. The workspace half is
     * {@code RmqctlWorkspaceTest.degradesToPlainChatWhenTheRmqctlBinaryIsMissingTest}.
     */
    @Test
    void buildStreamCommandShouldStayPlainChatWithTheFullDisallowListWhenDegradedTest() {
        ClaudeCodeAgentProvider provider = new ClaudeCodeAgentProvider(null, new CliProcessEnvironment(List.of()));

        List<String> command = provider.buildStreamCommand(
                LlmConfigVO.builder().model("qwen3.8-max").build(),
                AgentStreamOptions.builder()
                        .prompt("what is the backlog?")
                        .instanceId("localtest")
                        .build());

        assertThat(command).containsSubsequence(
                "claude", "-p", "what is the backlog?", "--model", "qwen3.8-max");
        assertThat(command).doesNotContain("--mcp-config", "--strict-mcp-config", "--allowedTools",
                "--append-system-prompt", "--resume");
        int index = command.indexOf("--disallowedTools");
        assertThat(index).isGreaterThanOrEqualTo(0);
        // containsExactly, not contains: degrading must not quietly shorten the list of built-in tools
        // the agent is denied while it has no RocketMQ tools to use instead.
        assertThat(command.subList(index + 1, command.size())).containsExactly(
                "Bash", "Read", "Write", "Edit", "Glob", "Grep", "Task",
                "WebFetch", "WebSearch", "TodoWrite", "NotebookEdit");
    }

    @Test
    void buildStreamCommandShouldFailFastWithoutAnyModelTest() {
        ClaudeCodeAgentProvider provider = new ClaudeCodeAgentProvider(null, new CliProcessEnvironment(List.of()));

        assertThatThrownBy(() -> provider.buildStreamCommand(
                LlmConfigVO.builder().build(), AgentStreamOptions.builder().prompt("hi").build()))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(400);
                    assertThat(exception.getCode()).isEqualTo("llm.config.model_required");
                });
    }

    @Test
    void buildStreamCommandShouldIgnoreAnUnreadableSystemPromptTest(@TempDir Path workspace) {
        ClaudeCodeAgentProvider provider = new ClaudeCodeAgentProvider(null, new CliProcessEnvironment(List.of()));
        AgentStreamOptions options = AgentStreamOptions.builder()
                .prompt("hi")
                .model("qwen3.8-max")
                .systemPromptPath(workspace.resolve("missing.txt").toString())
                .build();

        // Degrades to no extra prompt: the server-side mutation filter still guards L2/L3 tools.
        assertThat(provider.buildStreamCommand(LlmConfigVO.builder().build(), options))
                .doesNotContain("--append-system-prompt");
    }

    @Test
    void streamEventsShouldEmitTheCapturedToolLoopTest() throws Exception {
        StreamingTestProvider provider = new StreamingTestProvider(
                List.of("cat", capturePath().toString()), 30);
        List<AgentEvent> events = new ArrayList<>();

        provider.streamEvents(LlmConfigVO.builder().build(),
                AgentStreamOptions.builder().prompt("list the topics").model("qwen3.8-max").build(),
                events::add);

        // The whole capture, both runs, through a real subprocess: two tool calls in run 1, none in
        // run 2, and one terminal result per run.
        assertThat(count(events, AgentEvent.InitMeta.class)).isEqualTo(2);
        assertThat(count(events, AgentEvent.ToolStart.class)).isEqualTo(2);
        assertThat(count(events, AgentEvent.ToolInputComplete.class)).isEqualTo(2);
        assertThat(count(events, AgentEvent.ToolDone.class)).isEqualTo(2);
        assertThat(count(events, AgentEvent.ResultMeta.class)).isEqualTo(2);
        assertThat(count(events, AgentEvent.UnhandledUpstream.class)).isZero();
        assertThat(count(events, AgentEvent.ProviderNotice.class)).isZero();
        assertThat(events).anySatisfy(event -> assertThat(event)
                .isEqualTo(new AgentEvent.ToolInputComplete("toolu_c3fc7fce2b8349b5a03068a9",
                        "rmq.instance.capabilities", Map.of("instanceId", "open-source-local"))));
        assertThat(events).last().isEqualTo(new AgentEvent.ResultMeta(
                "280b366b-a346-440c-b595-bf28c68910a4", 4_047L, 6, 120, "success"));
    }

    @Test
    void streamEventsShouldRunInTheWorkspaceAndAttachTheProcessTest(@TempDir Path workspace) {
        RecordingEnvironment processEnvironment = new RecordingEnvironment();
        AtomicReference<Process> attached = new AtomicReference<>();
        StreamingTestProvider provider = new StreamingTestProvider(
                List.of("sh", "-c", "printf '{}'"), 30, processEnvironment,
                Map.of("ANTHROPIC_AUTH_TOKEN", "request-token"));

        provider.streamEvents(LlmConfigVO.builder().build(),
                AgentStreamOptions.builder()
                        .prompt("hi")
                        .model("qwen3.8-max")
                        .workspaceDir(workspace.toString())
                        .extraEnv(Map.of("RMQ_AI_ACCESS_KEY", "per-run-access-key"))
                        .processSink(attached::set)
                        .build(),
                event -> { });

        // A stable cwd is what makes --resume work: claude hashes the project directory to find its
        // session state, so a per-run temporary directory would break every resume.
        assertThat(provider.workingDir.get()).isEqualTo(workspace.toFile());
        // Attaching happens before any output is read, so a stop that races the start still finds it.
        assertThat(attached.get()).isNotNull();
        assertThat(processEnvironment.childEnvironments).singleElement().satisfies(environment ->
                assertThat(environment)
                        .containsEntry("ANTHROPIC_AUTH_TOKEN", "request-token")
                        .containsEntry("RMQ_AI_ACCESS_KEY", "per-run-access-key")
                        .doesNotContainKey("SERVER_SECRET"));
    }

    @Test
    void streamEventsShouldFailWhenTheCliDiesWithoutAResultFrameTest() {
        StreamingTestProvider provider = new StreamingTestProvider(
                List.of("sh", "-c", "echo credential rejected >&2; exit 3"), 30);

        assertThatThrownBy(() -> provider.streamEvents(LlmConfigVO.builder().build(),
                AgentStreamOptions.builder().prompt("hi").model("qwen3.8-max").build(), event -> { }))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(502);
                    assertThat(exception.getCode()).isEqualTo("llm.provider.cli_error");
                    assertThat(exception.getMessage()).contains("credential rejected");
                });
    }

    @Test
    void streamEventsShouldLetAResultFrameExplainANonZeroExitTest() {
        StreamingTestProvider provider = new StreamingTestProvider(
                List.of("sh", "-c", "printf '%s' "
                        + "'{\"type\":\"result\",\"subtype\":\"error_max_turns\",\"is_error\":true,"
                        + "\"duration_ms\":12,\"session_id\":\"s-1\","
                        + "\"usage\":{\"input_tokens\":3,\"output_tokens\":4}}'; exit 1"), 30);
        List<AgentEvent> events = new ArrayList<>();

        // The frame already says what happened; throwing here would replace a precise diagnosis
        // with a generic one.
        provider.streamEvents(LlmConfigVO.builder().build(),
                AgentStreamOptions.builder().prompt("hi").model("qwen3.8-max").build(), events::add);

        assertThat(events).containsExactly(
                new AgentEvent.ResultMeta("s-1", 12L, 3, 4, "error_max_turns"));
    }

    @Test
    void streamEventsShouldReportALostResumeSessionTest() {
        // The measured shape of a stale --resume: exit 1, the session-not-found line on stderr, and a
        // result frame whose subtype is error_during_execution with the dead id echoed back.
        StreamingTestProvider provider = new StreamingTestProvider(
                List.of("sh", "-c", "printf '%s' "
                        + "'{\"type\":\"result\",\"subtype\":\"error_during_execution\",\"is_error\":true,"
                        + "\"num_turns\":0,\"session_id\":\"gone-session\"}'; "
                        + "echo 'No conversation found with session ID: gone-session' >&2; exit 1"), 30);

        // The caller can only retry correctly if it knows the retry has to drop --resume, and the
        // command is built here. See ResumeRecovery.
        assertThatThrownBy(() -> provider.streamEvents(LlmConfigVO.builder().build(),
                AgentStreamOptions.builder().prompt("hi").model("qwen3.8-max")
                        .resumeSessionId("gone-session").build(), event -> { }))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(502);
                    assertThat(exception.getCode()).isEqualTo(ResumeRecovery.RESUME_LOST_CODE);
                    assertThat(exception.getMessage()).contains("gone-session");
                });
    }

    @Test
    void streamEventsShouldNotReportALostResumeWhenTheRunNeverResumedTest() {
        StreamingTestProvider provider = new StreamingTestProvider(
                List.of("sh", "-c", "printf '%s' "
                        + "'{\"type\":\"result\",\"subtype\":\"error_during_execution\",\"is_error\":true,"
                        + "\"num_turns\":0,\"session_id\":\"s-1\"}'; "
                        + "echo 'No conversation found with session ID: s-1' >&2; exit 1"), 30);
        List<AgentEvent> events = new ArrayList<>();

        // Nothing was resumed, so the signal is not the one a retry repairs: the frames explain the
        // failure and retrying the same command would only repeat it.
        provider.streamEvents(LlmConfigVO.builder().build(),
                AgentStreamOptions.builder().prompt("hi").model("qwen3.8-max").build(), events::add);

        assertThat(events).containsExactly(
                new AgentEvent.ResultMeta("s-1", null, null, null, "error_during_execution"));
    }

    private static int count(List<AgentEvent> events, Class<?> type) {
        return (int) events.stream().filter(type::isInstance).count();
    }

    private static Path capturePath() throws Exception {
        return Path.of(ClaudeCodeAgentProviderTest.class
                .getResource("/ai/claude-stream-capture.jsonl").toURI());
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
        private final Process process;

        TestClaudeCodeAgentProvider(List<String> command, long timeoutSeconds) {
            this(command, timeoutSeconds, new CliProcessEnvironment(List.of()), Map.of(), null);
        }

        TestClaudeCodeAgentProvider(List<String> command, long timeoutSeconds,
                                    CliProcessEnvironment processEnvironment,
                                    Map<String, String> environment) {
            this(command, timeoutSeconds, processEnvironment, environment, null);
        }

        TestClaudeCodeAgentProvider(List<String> command, long timeoutSeconds, Process process) {
            this(command, timeoutSeconds, new CliProcessEnvironment(List.of()), Map.of(), process);
        }

        TestClaudeCodeAgentProvider(List<String> command, long timeoutSeconds,
                                    CliProcessEnvironment processEnvironment,
                                    Map<String, String> environment, Process process) {
            super(null, processEnvironment);
            this.command = command;
            this.timeoutSeconds = timeoutSeconds;
            this.environment = environment;
            this.process = process;
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

        @Override
        protected Process startProcess(ProcessBuilder builder) throws java.io.IOException {
            return process == null ? super.startProcess(builder) : process;
        }
    }

    /**
     * Drives the event-streaming path with a fixed command. {@link TestClaudeCodeAgentProvider} only
     * overrides the three-argument {@code buildCommand} used by the deprecated text channel, while
     * {@code streamEvents} goes through {@code buildStreamCommand} and would otherwise try to exec a
     * real {@code claude} binary.
     */
    private static final class StreamingTestProvider extends TestClaudeCodeAgentProvider {

        private final List<String> streamCommand;
        private final AtomicReference<File> workingDir = new AtomicReference<>();

        StreamingTestProvider(List<String> streamCommand, long timeoutSeconds) {
            this(streamCommand, timeoutSeconds, new CliProcessEnvironment(List.of()), Map.of());
        }

        StreamingTestProvider(List<String> streamCommand, long timeoutSeconds,
                              CliProcessEnvironment processEnvironment, Map<String, String> environment) {
            super(streamCommand, timeoutSeconds, processEnvironment, environment);
            this.streamCommand = streamCommand;
        }

        @Override
        protected List<String> buildStreamCommand(LlmConfigVO config, AgentStreamOptions options) {
            return new ArrayList<>(streamCommand);
        }

        @Override
        protected Process startProcess(ProcessBuilder builder) throws IOException {
            workingDir.set(builder.directory());
            return super.startProcess(builder);
        }
    }
}
