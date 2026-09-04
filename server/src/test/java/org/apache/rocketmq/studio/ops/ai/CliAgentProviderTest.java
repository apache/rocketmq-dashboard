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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CliAgentProviderTest {

    private static class FakeCli extends CliAgentProvider {
        private final String script;
        private final int outputLimitBytes;
        private final Map<String, String> environment;

        FakeCli(String script) {
            this(script, Integer.MAX_VALUE);
        }

        FakeCli(String script, int outputLimitBytes) {
            this(script, outputLimitBytes, new CliProcessEnvironment(List.of()), Map.of());
        }

        FakeCli(String script, CliProcessEnvironment processEnvironment, Map<String, String> environment) {
            this(script, Integer.MAX_VALUE, processEnvironment, environment);
        }

        FakeCli(String script, int outputLimitBytes, CliProcessEnvironment processEnvironment,
                Map<String, String> environment) {
            super(processEnvironment);
            this.script = script;
            this.outputLimitBytes = outputLimitBytes;
            this.environment = environment;
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
    void completeAllowsOutputAtConfiguredLimit() {
        FakeCli cli = new FakeCli("yes x | head -c 1024", 1024);

        assertThat(cli.complete(null, "prompt", null)).isNotEmpty();
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
}
