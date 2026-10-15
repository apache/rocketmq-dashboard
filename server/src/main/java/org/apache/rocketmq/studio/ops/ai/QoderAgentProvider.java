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

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.util.TextBounds;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.AgentStreamOptions;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Qoder CLI provider ({@code qodercli -p}). qodercli authenticates through its
 * own login state in the runtime image, so no credentials are injected here.
 */
@Slf4j
@Component
public class QoderAgentProvider extends CliAgentProvider {

    public static final String ENGINE = "qoder";

    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(300);

    public QoderAgentProvider(CliProcessEnvironment processEnvironment) {
        super(processEnvironment);
    }

    @Override
    public String engine() {
        return ENGINE;
    }

    @Override
    protected String binaryName() {
        return "qodercli";
    }

    @Override
    protected List<String> buildCommand(LlmConfigVO config, String prompt, String modelOverride) {
        List<String> command = new ArrayList<>(List.of("qodercli", "-p", prompt == null ? "" : prompt));
        String model = StringUtils.hasText(modelOverride)
                ? modelOverride.trim()
                : config == null ? null : config.getModel();
        if (StringUtils.hasText(model)) {
            command.add("-m");
            command.add(model.trim());
        }
        return command;
    }

    @Override
    protected Map<String, String> childEnv(LlmConfigVO config) {
        return Collections.emptyMap();
    }

    /**
     * Streams one hosted-agent run, owning the subprocess the way the run lifecycle expects.
     *
     * <p>The base class only knows how to {@code complete()}: it runs the CLI and keeps the process
     * private. A hosted Qoder run that fell back to the SPI default therefore streamed through
     * {@code complete()} on a worker thread — and never handed its subprocess to
     * {@link AgentStreamOptions#getProcessSink()}, so a stop could not kill the real process tree:
     * it depended on a thread interrupt reaching the reader while any CLI-spawned child kept
     * running. This override runs the same one-shot completion, but attaches the freshly started
     * process to the run handle immediately after {@code start()} and before any output is read,
     * drains stdout and stderr in the background so a chatty child cannot deadlock a pipe, and
     * takes the wall-clock budget from {@link AgentStreamOptions#timeoutOr(Duration)}.
     */
    @Override
    public void streamEvents(LlmConfigVO config, AgentStreamOptions options, Consumer<AgentEvent> sink) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(sink, "sink");
        if (!available()) {
            throw new LlmGatewayException(503, "llm.provider.cli_missing",
                    binaryName() + " CLI is not installed in the server runtime",
                    "Install the CLI into the rocketmq-server image or switch the engine to HTTP.");
        }
        AiPayloadGuard.validateOutboundPrompt(options.getPrompt(), options.getModel());
        List<String> command = buildCommand(config, options.getPrompt(), options.getModel());
        ProcessBuilder builder = new ProcessBuilder(command);
        Map<String, String> environment = new LinkedHashMap<>(childEnv(config));
        environment.putAll(options.getExtraEnv());
        processEnvironment().apply(builder, environment);
        // stdout is the completion, stderr is diagnostics; both are drained on background threads
        // because reading them sequentially on the caller thread deadlocks once the child fills a
        // pipe buffer (64 KiB).
        builder.redirectErrorStream(false);
        if (StringUtils.hasText(options.getWorkspaceDir())) {
            builder.directory(new File(options.getWorkspaceDir().trim()));
        }
        // CLIs wait for piped stdin and emit a warning that leaks into the reply unless stdin is
        // explicitly /dev/null; fall back to closing the pipe where unavailable.
        File devNull = new File("/dev/null");
        boolean devNullAvailable = devNull.exists();
        if (devNullAvailable) {
            builder.redirectInput(ProcessBuilder.Redirect.from(devNull));
        }
        Process process;
        try {
            process = startProcess(builder);
            if (!devNullAvailable) {
                process.getOutputStream().close();
            }
        } catch (IOException exception) {
            throw new LlmGatewayException(502, "llm.provider.io_error",
                    "Failed to execute " + binaryName() + " CLI",
                    "Check that the CLI binary is installed and executable.", exception);
        }
        if (options.getProcessSink() != null) {
            options.getProcessSink().attachProcess(process);
        }
        CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return readStdout(process.getInputStream());
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
        CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return readStderr(process.getErrorStream());
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
        long timeoutSeconds = Math.max(1L, options.timeoutOr(STREAM_TIMEOUT).toSeconds());
        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new LlmGatewayException(502, "llm.provider.interrupted",
                    binaryName() + " CLI execution was interrupted", "Retry the request.", exception);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new LlmGatewayException(504, "llm.provider.timeout",
                    binaryName() + " CLI stream timed out after " + timeoutSeconds + "s",
                    "Retry with a shorter prompt or check the gateway latency.");
        }
        String stdout = awaitStdout(stdoutFuture, process);
        String stderr;
        try {
            stderr = stderrFuture.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException exception) {
            stderr = "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            stderr = "";
        }
        if (process.exitValue() != 0) {
            String diagnostics = StringUtils.hasText(stderr) ? stderr : stdout;
            log.warn("{} CLI failed rc={}, output={}", binaryName(), process.exitValue(),
                    abbreviate(diagnostics));
            throw new LlmGatewayException(502, "llm.provider.cli_error",
                    binaryName() + " CLI failed: " + abbreviate(diagnostics),
                    "Check the provider credentials, base URL and model name.");
        }
        String result = stdout.trim();
        if (!StringUtils.hasText(result)) {
            throw new LlmGatewayException(502, "llm.provider.empty_completion",
                    binaryName() + " CLI returned an empty completion",
                    "Check the selected model and provider response.");
        }
        sink.accept(new AgentEvent.TextDelta(result));
    }

    /**
     * Seam for tests: the production run starts the built command; tests substitute a recorded or a
     * script-driven process.
     */
    protected Process startProcess(ProcessBuilder builder) throws IOException {
        return builder.start();
    }

    private String readStdout(InputStream input) throws IOException {
        int limitBytes = outputLimitBytes();
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limitBytes, 8192));
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read > limitBytes - output.size()) {
                throw new OutputTooLargeException(limitBytes);
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private String readStderr(InputStream input) throws IOException {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    private String awaitStdout(CompletableFuture<String> future, Process process) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof OutputTooLargeException tooLarge) {
                throw new LlmGatewayException(502, "llm.provider.output_too_large",
                        binaryName() + " CLI output exceeded the maximum of "
                                + tooLarge.limitBytes + " bytes",
                        "Retry with a shorter prompt or reduce the provider response size.", exception);
            }
            return "";
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new LlmGatewayException(502, "llm.provider.interrupted",
                    binaryName() + " CLI output collection was interrupted", "Retry the request.",
                    exception);
        } catch (TimeoutException exception) {
            return "";
        }
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        // The failure text this bounds can carry an emoji: cut on a code point boundary.
        return TextBounds.truncate(value.trim(), 500, "...");
    }

    private static final class OutputTooLargeException extends IOException {
        private final int limitBytes;

        private OutputTooLargeException(int limitBytes) {
            super("CLI output exceeds " + limitBytes + " bytes");
            this.limitBytes = limitBytes;
        }
    }
}
