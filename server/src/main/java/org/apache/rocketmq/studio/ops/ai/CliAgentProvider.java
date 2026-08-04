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

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Base class for CLI-based agent providers: spawns the vendor CLI in a
 * subprocess, injects credentials through the child environment and captures
 * stdout as the completion result.
 */
@Slf4j
public abstract class CliAgentProvider implements AgentProvider {

    private static final long TIMEOUT_SECONDS = 180;

    protected abstract List<String> buildCommand(LlmConfigVO config, String prompt, String modelOverride);

    protected abstract Map<String, String> childEnv(LlmConfigVO config);

    protected abstract String binaryName();

    @Override
    public boolean available() {
        try {
            Process process = new ProcessBuilder("sh", "-c", "command -v " + binaryName())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    @Override
    public String complete(LlmConfigVO config, String prompt, String modelOverride) {
        if (!available()) {
            throw new LlmGatewayException(503, "llm.provider.cli_missing",
                    binaryName() + " CLI is not installed in the server runtime",
                    "Install the CLI into the rocketmq-server image or switch the engine to HTTP.");
        }
        List<String> command = buildCommand(config, prompt, modelOverride);
        ProcessBuilder builder = new ProcessBuilder(command);
        Map<String, String> env = builder.environment();
        env.putAll(childEnv(config));
        builder.redirectErrorStream(false);
        try {
            Process process = builder.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new LlmGatewayException(504, "llm.provider.timeout",
                        binaryName() + " CLI timed out after " + TIMEOUT_SECONDS + "s",
                        "Retry with a shorter prompt or check the gateway latency.");
            }
            if (process.exitValue() != 0) {
                log.warn("{} CLI failed rc={}, stderr={}", binaryName(), process.exitValue(), abbreviate(stderr));
                throw new LlmGatewayException(502, "llm.provider.cli_error",
                        binaryName() + " CLI failed: " + abbreviate(
                                StringUtils.hasText(stderr) ? stderr : stdout),
                        "Check the provider credentials, base URL and model name.");
            }
            String result = stdout.trim();
            if (!StringUtils.hasText(result)) {
                throw new LlmGatewayException(502, "llm.provider.empty_completion",
                        binaryName() + " CLI returned an empty completion",
                        "Check the selected model and provider response.");
            }
            return result;
        } catch (IOException exception) {
            throw new LlmGatewayException(502, "llm.provider.io_error",
                    "Failed to execute " + binaryName() + " CLI",
                    "Check that the CLI binary is installed and executable.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmGatewayException(502, "llm.provider.interrupted",
                    binaryName() + " CLI execution was interrupted", "Retry the request.", exception);
        }
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }
}
