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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Claude Code CLI provider ({@code claude -p}). Credentials are passed to the
 * child process exclusively via ANTHROPIC_AUTH_TOKEN / ANTHROPIC_BASE_URL env
 * entries, mirroring the mq-hub adapter's env-injection approach.
 */
@Slf4j
@Component
public class ClaudeCodeAgentProvider extends CliAgentProvider {

    public static final String ENGINE = "claude-code";
    private static final String COMPATIBLE_MODE_SUFFIX = "/compatible-mode/v1";
    private static final String ANTHROPIC_APP_SUFFIX = "/apps/anthropic";
    private static final long STREAM_TIMEOUT_SECONDS = 300;
    private static final long OUTPUT_DRAIN_TIMEOUT_SECONDS = 10;

    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeCodeAgentProvider(LlmProperties llmProperties, CliProcessEnvironment processEnvironment) {
        super(processEnvironment);
        this.llmProperties = llmProperties;
    }

    @Override
    public String engine() {
        return ENGINE;
    }

    @Override
    protected String binaryName() {
        return "claude";
    }

    @Override
    protected List<String> buildCommand(LlmConfigVO config, String prompt, String modelOverride) {
        List<String> command = new ArrayList<>(List.of("claude", "-p", prompt == null ? "" : prompt));
        String model = StringUtils.hasText(modelOverride) ? modelOverride.trim() : config.getModel();
        if (StringUtils.hasText(model)) {
            command.add("--model");
            command.add(model.trim());
        }
        return command;
    }

    @Override
    public void stream(LlmConfigVO config, String prompt, String modelOverride, Consumer<String> tokenConsumer) {
        if (!available()) {
            throw new LlmGatewayException(503, "llm.provider.cli_missing",
                    binaryName() + " CLI is not installed in the server runtime",
                    "Install the CLI into the rocketmq-server image or switch the engine to HTTP.");
        }
        List<String> command = buildCommand(config, prompt, modelOverride);
        command.add("--output-format");
        command.add("stream-json");
        command.add("--verbose");
        command.add("--include-partial-messages");

        ProcessBuilder builder = new ProcessBuilder(command);
        processEnvironment().apply(builder, childEnv(config));
        builder.redirectErrorStream(false);
        try {
            Process process = builder.start();
            AtomicBoolean emitted = new AtomicBoolean(false);
            StringBuilder resultText = new StringBuilder();
            CompletableFuture<Void> stdoutFuture = drainStdout(
                    process.getInputStream(), tokenConsumer, emitted, resultText);
            CompletableFuture<String> stderrFuture = readAsync(process.getErrorStream());
            long timeoutSeconds = streamTimeoutSeconds();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new LlmGatewayException(504, "llm.provider.timeout",
                        binaryName() + " CLI stream timed out after " + timeoutSeconds + "s",
                        "Retry with a shorter prompt or check the gateway latency.");
            }
            await(stdoutFuture);
            String stderr = await(stderrFuture);
            if (process.exitValue() != 0 && !emitted.get()) {
                throw new LlmGatewayException(502, "llm.provider.cli_error",
                        binaryName() + " CLI failed: " + (StringUtils.hasText(stderr) ? stderr.trim() : "unknown error"),
                        "Check the provider credentials, base URL and model name.");
            }
            if (!emitted.get() && resultText.length() > 0) {
                tokenConsumer.accept(resultText.toString());
            }
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

    protected long streamTimeoutSeconds() {
        return STREAM_TIMEOUT_SECONDS;
    }

    private CompletableFuture<Void> drainStdout(InputStream stdout, Consumer<String> tokenConsumer,
                                                AtomicBoolean emitted, StringBuilder resultText) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stdout, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseStreamLine(line, tokenConsumer, emitted, resultText);
                }
                result.complete(null);
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        });
        return result;
    }

    private CompletableFuture<String> readAsync(InputStream stream) {
        CompletableFuture<String> result = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try (stream) {
                result.complete(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        });
        return result;
    }

    private <T> T await(CompletableFuture<T> future) throws IOException, InterruptedException {
        try {
            return future.get(OUTPUT_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            throw new IOException("Failed to drain Claude CLI output", exception.getCause());
        } catch (TimeoutException exception) {
            throw new IOException("Timed out while draining Claude CLI output", exception);
        }
    }

    /** Parses one stream-json line: emits text deltas, records the final result. */
    private void parseStreamLine(String line, Consumer<String> tokenConsumer,
                                 AtomicBoolean emitted, StringBuilder resultText) {
        if (!StringUtils.hasText(line)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(line);
            String type = node.path("type").asText("");
            if ("stream_event".equals(type)) {
                JsonNode event = node.path("event");
                if ("content_block_delta".equals(event.path("type").asText(""))) {
                    String delta = event.path("delta").path("text").asText("");
                    if (!delta.isEmpty()) {
                        emitted.set(true);
                        tokenConsumer.accept(delta);
                    }
                }
            } else if ("assistant".equals(type)) {
                for (JsonNode block : node.path("message").path("content")) {
                    if ("text".equals(block.path("type").asText(""))) {
                        String text = block.path("text").asText("");
                        if (!text.isEmpty() && !emitted.get()) {
                            // No partial messages arrived; use the full assistant text once.
                            resultText.setLength(0);
                            resultText.append(text);
                        }
                    }
                }
            } else if ("result".equals(type)) {
                String result = node.path("result").asText("");
                if (!result.isEmpty() && !emitted.get()) {
                    resultText.setLength(0);
                    resultText.append(result);
                }
            }
        } catch (IOException exception) {
            log.debug("Skipping unparseable claude stream line: {}", line.length() > 200 ? line.substring(0, 200) : line);
        }
    }

    @Override
    protected Map<String, String> childEnv(LlmConfigVO config) {
        Map<String, String> env = new HashMap<>();
        String token = StringUtils.hasText(config.getApiKey()) ? config.getApiKey().trim() : null;
        if (token != null) {
            env.put("ANTHROPIC_AUTH_TOKEN", token);
        }
        String baseUrl = anthropicBase(config);
        if (StringUtils.hasText(baseUrl)) {
            env.put("ANTHROPIC_BASE_URL", baseUrl);
        }
        return env;
    }

    private String anthropicBase(LlmConfigVO config) {
        if (llmProperties != null && StringUtils.hasText(llmProperties.getAnthropicBaseUrl())) {
            return llmProperties.getAnthropicBaseUrl().trim();
        }
        String apiBase = config.getApiBase();
        if (!StringUtils.hasText(apiBase)) {
            return null;
        }
        String normalized = apiBase.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(COMPATIBLE_MODE_SUFFIX)) {
            return normalized.substring(0, normalized.length() - COMPATIBLE_MODE_SUFFIX.length())
                    + ANTHROPIC_APP_SUFFIX;
        }
        return normalized;
    }
}
