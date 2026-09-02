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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Base class for CLI-based agent providers: spawns the vendor CLI in a
 * subprocess, injects credentials through the child environment and captures
 * stdout as the completion result.
 */
@Slf4j
public abstract class CliAgentProvider implements AgentProvider {

    private static final long TIMEOUT_SECONDS = 180;
    private static final int MAX_OUTPUT_BYTES = 5 * 1024 * 1024;

    private final CliProcessEnvironment processEnvironment;

    protected CliAgentProvider(CliProcessEnvironment processEnvironment) {
        this.processEnvironment = Objects.requireNonNull(processEnvironment, "processEnvironment");
    }

    protected abstract List<String> buildCommand(LlmConfigVO config, String prompt, String modelOverride);

    protected abstract Map<String, String> childEnv(LlmConfigVO config);

    protected abstract String binaryName();

    protected final CliProcessEnvironment processEnvironment() {
        return processEnvironment;
    }

    @Override
    public boolean available() {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", "command -v " + binaryName());
            processEnvironment.apply(builder, Map.of());
            process = startAvailabilityProcess(builder.redirectErrorStream(true));
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    protected Process startAvailabilityProcess(ProcessBuilder builder) throws IOException {
        return builder.start();
    }

    @Override
    public String complete(LlmConfigVO config, String prompt, String modelOverride) {
        String effectiveModel = StringUtils.hasText(modelOverride)
                ? modelOverride
                : config == null ? null : config.getModel();
        AiPayloadGuard.validateOutboundPrompt(prompt, effectiveModel);
        if (!available()) {
            throw new LlmGatewayException(503, "llm.provider.cli_missing",
                    binaryName() + " CLI is not installed in the server runtime",
                    "Install the CLI into the rocketmq-server image or switch the engine to HTTP.");
        }
        List<String> command = buildCommand(config, prompt, modelOverride);
        ProcessBuilder builder = new ProcessBuilder(command);
        processEnvironment.apply(builder, childEnv(config));
        // Merge stderr into stdout and drain the stream on a background thread. Reading stdout
        // then stderr sequentially on the caller thread deadlocks once the child fills a pipe
        // buffer (64 KiB), and a timeout that runs only after both reads can never fire while the
        // child stays alive. With the merged stream drained in the background, waitFor can enforce
        // the timeout and a hung child is destroyed instead of leaking the caller thread.
        builder.redirectErrorStream(true);
        // CLIs wait for piped stdin and emit a warning that leaks into the reply unless
        // stdin is explicitly /dev/null; fall back to closing the pipe where unavailable.
        boolean devNullAvailable = new java.io.File("/dev/null").exists();
        if (devNullAvailable) {
            builder.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));
        }
        Process process;
        try {
            process = builder.start();
            if (!devNullAvailable) {
                process.getOutputStream().close();
            }
        } catch (IOException exception) {
            throw new LlmGatewayException(502, "llm.provider.io_error",
                    "Failed to execute " + binaryName() + " CLI",
                    "Check that the CLI binary is installed and executable.", exception);
        }
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return readOutput(process);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
        boolean finished;
        try {
            finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new LlmGatewayException(502, "llm.provider.interrupted",
                    binaryName() + " CLI execution was interrupted", "Retry the request.", exception);
        }
        String output;
        try {
            output = outputFuture.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof OutputLimitException outputLimitException) {
                throw new LlmGatewayException(502, "llm.provider.output_too_large",
                        binaryName() + " CLI output exceeded the maximum of "
                                + outputLimitException.limitBytes() + " bytes",
                        "Retry with a shorter prompt or reduce the provider response size.", exception);
            }
            output = "";
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new LlmGatewayException(502, "llm.provider.interrupted",
                    binaryName() + " CLI output collection was interrupted", "Retry the request.", exception);
        } catch (TimeoutException exception) {
            output = "";
        }
        if (!finished) {
            process.destroyForcibly();
            throw new LlmGatewayException(504, "llm.provider.timeout",
                    binaryName() + " CLI timed out after " + TIMEOUT_SECONDS + "s",
                    "Retry with a shorter prompt or check the gateway latency.");
        }
        if (process.exitValue() != 0) {
            log.warn("{} CLI failed rc={}, output={}", binaryName(), process.exitValue(), abbreviate(output));
            throw new LlmGatewayException(502, "llm.provider.cli_error",
                    binaryName() + " CLI failed: " + abbreviate(output),
                    "Check the provider credentials, base URL and model name.");
        }
        String result = output.trim();
        if (!StringUtils.hasText(result)) {
            throw new LlmGatewayException(502, "llm.provider.empty_completion",
                    binaryName() + " CLI returned an empty completion",
                    "Check the selected model and provider response.");
        }
        return result;
    }

    private String readOutput(Process process) throws IOException {
        int limitBytes = outputLimitBytes();
        try (InputStream input = process.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limitBytes, 8192))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > limitBytes - output.size()) {
                    process.destroyForcibly();
                    throw new OutputLimitException(limitBytes);
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    int outputLimitBytes() {
        return MAX_OUTPUT_BYTES;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }

    private static final class OutputLimitException extends IOException {
        private final int limitBytes;

        private OutputLimitException(int limitBytes) {
            super("CLI output exceeds " + limitBytes + " bytes");
            this.limitBytes = limitBytes;
        }

        private int limitBytes() {
            return limitBytes;
        }
    }
}
