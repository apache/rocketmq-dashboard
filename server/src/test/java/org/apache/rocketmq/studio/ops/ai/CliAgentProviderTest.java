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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliAgentProviderTest {

    private static final class FakeCli extends CliAgentProvider {
        private final String script;
        private final int outputLimitBytes;

        FakeCli(String script) {
            this(script, Integer.MAX_VALUE);
        }

        FakeCli(String script, int outputLimitBytes) {
            this.script = script;
            this.outputLimitBytes = outputLimitBytes;
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
            return Map.of();
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
}
