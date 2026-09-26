/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.rocketmq.studio.ops.ai;

import org.apache.rocketmq.studio.ops.ai.conversation.agent.CliBinaryProbe;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CliAgentProviderTest {

    private static class FakeCli extends CliAgentProvider {
        private final String script;
        private final int outputLimitBytes;
        private final Map<String, String> environment;
        private final Process process;
        private final long timeoutSeconds;

        FakeCli(String script) {
            this(script, Integer.MAX_VALUE, new CliProcessEnvironment(List.of()), Map.of(), null, 300);
        }

        FakeCli(String script, int outputLimitBytes) {
            this(script, outputLimitBytes, new CliProcessEnvironment(List.of()), Map.of(), null, 300);
        }

        FakeCli(String script, CliProcessEnvironment processEnvironment, Map<String, String> environment) {
            this(script, Integer.MAX_VALUE, processEnvironment, environment, null, 300);
        }

        FakeCli(Process process, int outputLimitBytes, long timeoutSeconds) {
            this("unused", outputLimitBytes, new CliProcessEnvironment(List.of()), Map.of(),
                    process, timeoutSeconds);
        }

        FakeCli(String script, int outputLimitBytes, CliProcessEnvironment processEnvironment,
                Map<String, String> environment, Process process, long timeoutSeconds) {
            super(processEnvironment);
            this.script = script;
            this.outputLimitBytes = outputLimitBytes;
            this.environment = environment;
            this.process = process;
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public String engine() {
            return "fake";
        }

        @Override
        protected List<String> buildCommand(LlmConfigVO config, String prompt, String modelOverride) {
            return List.of("sh", "-c", script);
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
        int outputLimitBytes() {
            return outputLimitBytes;
        }

        @Override
        protected Process startProcess(ProcessBuilder builder) throws java.io.IOException {
            return process == null ? super.startProcess(builder) : process;
        }

        @Override
        protected long completionTimeoutSeconds() {
            return timeoutSeconds;
        }
    }

    private static final class RecordingEnvironment extends CliProcessEnvironment {
        private final List<Map<String, String>> providerEnvironments = new ArrayList<>();
        private final List<Map<String, String>> childEnvironments = new ArrayList<>();

        RecordingEnvironment() {
            super(List.of());
        }

        @Override
        void apply(ProcessBuilder builder, Map<String, String> providerEnvironment) {
            builder.environment().put("SERVER_SECRET", "must-not-cross-boundary");
            super.apply(builder, providerEnvironment);
            providerEnvironments.add(Map.copyOf(providerEnvironment));
            childEnvironments.add(Map.copyOf(builder.environment()));
        }
    }

    private static final class AvailabilityCli extends FakeCli {
        private final Process process;

        AvailabilityCli(Process process) {
            super("echo unused");
            this.process = process;
        }

        @Override
        protected Process startAvailabilityProcess(ProcessBuilder builder) {
            return process;
        }
    }

    @Test
    void completeSurvivesLargeStderrOutput() {
        // Write well over the 64 KiB pipe buffer to stderr, then print the completion on stdout.
        // Before the fix, sequential stdout/stderr reads deadlocked the caller forever.
        String script = "for i in $(seq 1 10000); do "
                + "echo 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx' >&2; done; "
                + "echo DONE";
        FakeCli cli = new FakeCli(script);

        String result = cli.complete(null, "prompt", null);

        assertThat(result).contains("DONE");
        // All stderr bytes were drained as well (merged into the single output stream),
        // well past the 64 KiB pipe buffer that used to deadlock the sequential reads.
        assertThat(result.length()).isGreaterThan(500_000);
    }

    @Test
    void completeRejectsOutputBeyondConfiguredLimit() {
        FakeCli cli = new FakeCli("yes 0123456789abcdef | head -c 4096", 1024);

        assertThatThrownBy(() -> cli.complete(null, "prompt", null))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(502);
                    assertThat(exception.getCode()).isEqualTo("llm.provider.output_too_large");
                    assertThat(exception.getMessage()).contains("1024 bytes");
                });
    }

    @Test
    void completeTimeoutDestroysDescendantsBeforeTheCliProcessTest() throws Exception {
        List<String> terminationOrder = new ArrayList<>();
        Process process = processTree(new byte[0], false, terminationOrder);
        FakeCli cli = new FakeCli(process, Integer.MAX_VALUE, 1);

        assertThatThrownBy(() -> cli.complete(null, "prompt", null))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(504);
                    assertThat(exception.getCode()).isEqualTo("llm.provider.timeout");
                });

        assertThat(terminationOrder).containsExactly("descendant", "root");
    }

    @Test
    void completeOutputLimitDestroysDescendantsBeforeTheCliProcessTest() throws Exception {
        List<String> terminationOrder = new ArrayList<>();
        Process process = processTree(new byte[2048], true, terminationOrder);
        FakeCli cli = new FakeCli(process, 1024, 5);

        assertThatThrownBy(() -> cli.complete(null, "prompt", null))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(502);
                    assertThat(exception.getCode()).isEqualTo("llm.provider.output_too_large");
                });

        assertThat(terminationOrder).containsExactly("descendant", "root");
    }

    @Test
    void completeAllowsOutputAtConfiguredLimit() {
        FakeCli cli = new FakeCli("yes x | head -c 1024", 1024);

        assertThat(cli.complete(null, "prompt", null)).isNotEmpty();
    }

    @Test
    void aFailedCliShouldAbbreviateItsOutputOnCodePointBoundariesTest() {
        // The CLI quotes the prompt back when it fails, and the prompt is raw user text, so an emoji
        // can sit on the 500th char. The script writes exactly that: 499 bytes of 'x', then the
        // 4 UTF-8 bytes of U+1F600, then a tail that the cut drops.
        String script = "head -c 499 /dev/zero | tr '\\000' x; "
                + "printf '\\360\\237\\230\\200'; printf tail; exit 1";
        FakeCli cli = new FakeCli(script);

        assertThatThrownBy(() -> cli.complete(null, "prompt", null))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(502);
                    assertThat(exception.getCode()).isEqualTo("llm.provider.cli_error");
                    // A char-based cut would keep the high surrogate alone and drop the low one.
                    assertThat(exception.getMessage())
                            .isEqualTo("sh CLI failed: " + "x".repeat(499) + "\uD83D\uDE00" + "...");
                });
    }

    @Test
    void completeRejectsOversizedPromptBeforeStartingCli() {
        FakeCli cli = new FakeCli("echo should-not-run");

        assertThatThrownBy(() -> cli.complete(
                null, "x".repeat(AiPayloadGuard.MAX_OUTBOUND_PROMPT_BYTES + 1), null))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(400);
                    assertThat(exception.getCode()).isEqualTo("llm.request.payload_too_large");
                });
    }

    @Test
    void availabilityAndCompletionUseTheIsolatedEnvironment() {
        RecordingEnvironment processEnvironment = new RecordingEnvironment();
        FakeCli cli = new FakeCli(
                "printf '%s' \"$PROVIDER_TOKEN\"",
                processEnvironment,
                Map.of("PROVIDER_TOKEN", "request-token"));

        assertThat(cli.complete(null, "prompt", null)).isEqualTo("request-token");
        assertThat(processEnvironment.providerEnvironments)
                .containsExactly(Map.of(), Map.of("PROVIDER_TOKEN", "request-token"));
        assertThat(processEnvironment.childEnvironments).allSatisfy(environment ->
                assertThat(environment).doesNotContainKey("SERVER_SECRET"));
        assertThat(processEnvironment.childEnvironments.get(1))
                .containsEntry("PROVIDER_TOKEN", "request-token");
    }

    @Test
    void availabilityDestroysProbeWhenItTimesOut() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(java.util.concurrent.TimeUnit.SECONDS))).thenReturn(false);

        assertThat(new AvailabilityCli(process).available()).isFalse();

        verify(process).destroyForcibly();
    }

    @Test
    void availabilityDestroysProbeAndPreservesInterrupt() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(java.util.concurrent.TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("test interrupt"));

        try {
            assertThat(new AvailabilityCli(process).available()).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(process).destroyForcibly();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void probeLooksTheBinaryUpOnThePathTest() {
        AtomicReference<List<String>> probed = new AtomicReference<>();
        CliBinaryProbe probe = new CliBinaryProbe(
                new CliProcessEnvironment(List.of())::applyIsolated,
                builder -> {
                    probed.set(List.copyOf(builder.command()));
                    return builder.start();
                });

        assertThat(probe.isAvailable("rmqctl")).isFalse();
        assertThat(probed.get()).containsExactly("sh", "-c", "command -v rmqctl");
    }

    @Test
    void probeFindsABinaryThatIsReallyInstalledTest() {
        CliBinaryProbe probe = new CliBinaryProbe(new CliProcessEnvironment(List.of()));

        assertThat(probe.isAvailable("sh")).isTrue();
        assertThat(probe.isAvailable("definitely-not-on-path-9f3a")).isFalse();
    }

    @Test
    void probeAppliesAnEmptyProviderEnvironmentTest() {
        List<Map<String, String>> applied = new ArrayList<>();
        CliBinaryProbe probe = new CliBinaryProbe(
                (builder, providerEnvironment) -> applied.add(Map.copyOf(providerEnvironment)),
                ProcessBuilder::start);

        // A probe only asks whether a binary exists, so nothing request-scoped may travel with it.
        assertThat(probe.isAvailable("sh")).isTrue();
        assertThat(applied).containsExactly(Map.of());
    }

    @Test
    void probeRefusesANameTheShellCouldReadAsSyntaxTest() {
        CliBinaryProbe probe = new CliBinaryProbe(
                (builder, providerEnvironment) -> { },
                builder -> {
                    throw new AssertionError("a refused name must never reach the shell");
                });

        // Every case is a bare name plus something "sh -c" would parse, which is exactly what
        // interpolating into "command -v " turns into a second command, an option or a path.
        for (String name : List.of("rmqctl; id", "rmqctl && id", "rmqctl | id", "claude$(id)",
                "claude`id`", "rmqctl\nid", "/bin/sh", "-x", " ", "")) {
            assertThat(probe.isAvailable(name)).as("name=[%s]", name).isFalse();
        }
        assertThat(probe.isAvailable(null)).isFalse();
    }

    @Test
    void probeStillAcceptsTheBareNamesItsCallersPassTest() throws InterruptedException {
        AtomicReference<List<String>> probed = new AtomicReference<>();
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(java.util.concurrent.TimeUnit.SECONDS))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);
        CliBinaryProbe probe = new CliBinaryProbe(
                (builder, providerEnvironment) -> { },
                builder -> {
                    probed.set(List.copyOf(builder.command()));
                    return process;
                });

        // The constants the callers pass today, plus the shapes the identifier pattern allows.
        assertThat(probe.isAvailable("rmqctl")).isTrue();
        assertThat(probed.get()).containsExactly("sh", "-c", "command -v rmqctl");
        assertThat(probe.isAvailable("claude")).isTrue();
        assertThat(probe.isAvailable("qodercli")).isTrue();
        assertThat(probe.isAvailable("definitely-not-on-path-9f3a")).isTrue();
        assertThat(probe.isAvailable("rmqctl.beta_2+x")).isTrue();
    }

    private static Process processTree(byte[] output, boolean finished, List<String> terminationOrder)
            throws Exception {
        Process process = mock(Process.class);
        ProcessHandle descendant = mock(ProcessHandle.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(output));
        when(process.waitFor(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(finished);
        when(process.descendants()).thenReturn(Stream.of(descendant));
        doAnswer(invocation -> {
            terminationOrder.add("descendant");
            return true;
        }).when(descendant).destroyForcibly();
        doAnswer(invocation -> {
            terminationOrder.add("root");
            return process;
        }).when(process).destroyForcibly();
        return process;
    }
}
