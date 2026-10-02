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
import org.apache.rocketmq.studio.ops.ai.conversation.agent.AgentStreamOptions;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.ResumeRecovery;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Claude Code CLI provider ({@code claude -p}). Credentials are passed to the
 * child process exclusively via ANTHROPIC_AUTH_TOKEN / ANTHROPIC_BASE_URL env
 * entries.
 *
 * <p>Two entry points share one piece of process plumbing ({@link #spawn}): {@link #streamEvents} is
 * what a hosted agent run uses and hands every frame to {@link ClaudeCodeStreamParser}, while the
 * deprecated {@link #stream} keeps serving the prompt-enhancement path, which only ever wanted text.
 */
@Slf4j
@Component
public class ClaudeCodeAgentProvider extends CliAgentProvider {

    public static final String ENGINE = "claude-code";
    private static final String BINARY = "claude";
    private static final String COMPATIBLE_MODE_SUFFIX = "/compatible-mode/v1";
    private static final String ANTHROPIC_APP_SUFFIX = "/apps/anthropic";
    private static final long STREAM_TIMEOUT_SECONDS = 300;
    private static final long OUTPUT_DRAIN_TIMEOUT_SECONDS = 10;
    private static final int MAX_STDERR_BYTES = 64 * 1024;
    private static final String STDERR_TRUNCATED_SUFFIX = "\n...[stderr truncated]";

    /**
     * How Claude Code spells "every tool of this MCP server" in {@code --allowedTools}. Without it
     * the CLI silently auto-denies MCP calls in {@code -p} mode: no prompt, no error,
     * {@code is_error:false}, exit code 0, and the only trace is {@code permission_denials} in the
     * result frame — which {@link ClaudeCodeStreamParser} turns into a warning precisely because this
     * failure is otherwise invisible.
     */
    private static final String MCP_TOOL_ALLOW_PREFIX = "mcp__";

    /**
     * Every <em>built-in</em> tool stays disabled: the agent must not run {@code Bash} or read the
     * filesystem inside the server container, and {@code TodoWrite} stays off so the timeline is not
     * filled with tool calls that have nothing to do with RocketMQ. This list is no longer the whole
     * tool policy, though. MCP tools — the {@code rocketmq-studio} server's, allowed separately
     * through {@code --allowedTools} and confined by {@code --strict-mcp-config} — coexist with it:
     * built-ins off, RocketMQ tools on. Manual, human-driven tool execution still goes through
     * {@code /api/ai/tools}.
     */
    private static final List<String> DISABLED_TOOLS = List.of(
            "Bash", "Read", "Write", "Edit", "Glob", "Grep", "Task",
            "WebFetch", "WebSearch", "TodoWrite", "NotebookEdit");

    private final LlmProperties llmProperties;

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
        return BINARY;
    }

    @Override
    protected List<String> buildCommand(LlmConfigVO config, String prompt, String modelOverride) {
        List<String> command = new ArrayList<>(List.of(BINARY, "-p", prompt == null ? "" : prompt));
        String model = resolveModel(modelOverride, config);
        if (StringUtils.hasText(model)) {
            command.add("--model");
            command.add(model);
        }
        command.add("--disallowedTools");
        command.addAll(DISABLED_TOOLS);
        return command;
    }

    /**
     * The command for an event-streaming run: the same prompt and tool policy as
     * {@link #buildCommand}, plus everything a hosted agent needs that a one-shot completion does not.
     *
     * <ul>
     *   <li>{@code --model} is <strong>always</strong> present. Without it the CLI picks its own
     *       default (an opus build) and an OpenAI-compatible gateway answers
     *       {@code 400 "Model not exist."}, which reads like a Studio bug rather than a missing
     *       flag. A run with no resolvable model therefore fails here, before any process is
     *       spawned.</li>
     *   <li>{@code --resume} carries the runtime session id of the previous turn, which is how a
     *       conversation gets multi-turn context.</li>
     *   <li>{@code --mcp-config} plus {@code --strict-mcp-config} load only the generated Studio MCP
     *       config. Strict mode matters: without it the CLI also reads {@code ~/.claude.json} and
     *       user-scope servers, which inside a container is an uncontrolled surface.</li>
     *   <li>{@code --allowedTools mcp__rocketmq-studio} allows the whole server's tools, so an MCP
     *       call is not silently auto-denied (see {@link #MCP_TOOL_ALLOW_PREFIX}).</li>
     *   <li>{@code --append-system-prompt} takes the <em>text</em> of the system prompt, so the file
     *       named by the options is read here.</li>
     * </ul>
     */
    protected List<String> buildStreamCommand(LlmConfigVO config, AgentStreamOptions options) {
        String prompt = options == null ? null : options.getPrompt();
        String model = resolveModel(options == null ? null : options.getModel(), config);
        if (!StringUtils.hasText(model)) {
            throw new LlmGatewayException(400, "llm.config.model_required",
                    "No model is configured for the " + ENGINE + " engine",
                    "Pick a model in the AI settings: " + BINARY
                            + " without --model selects its own default, which the gateway rejects.");
        }
        List<String> command = new ArrayList<>(
                List.of(BINARY, "-p", prompt == null ? "" : prompt, "--model", model));
        if (options != null && StringUtils.hasText(options.getResumeSessionId())) {
            command.add("--resume");
            command.add(options.getResumeSessionId().trim());
        }
        withStreamFormat(command);
        if (options != null && StringUtils.hasText(options.getMcpConfigPath())) {
            command.add("--mcp-config");
            command.add(options.getMcpConfigPath().trim());
            command.add("--strict-mcp-config");
            command.add("--allowedTools");
            command.add(MCP_TOOL_ALLOW_PREFIX + AgentEventProjector.STUDIO_MCP_SERVER);
        }
        String systemPrompt = readSystemPrompt(options == null ? null : options.getSystemPromptPath());
        if (StringUtils.hasText(systemPrompt)) {
            command.add("--append-system-prompt");
            command.add(systemPrompt);
        }
        command.add("--disallowedTools");
        command.addAll(DISABLED_TOOLS);
        return command;
    }

    /**
     * Streams one hosted-agent run as typed events. The subprocess is attached to the run handle
     * immediately after it starts, so a stop kills the real process tree instead of hoping a thread
     * interrupt lands on the thread that happens to own the child.
     *
     * <p>A non-zero exit is only an error when the CLI never produced its terminal {@code result}
     * frame. When it did, the frame already said what happened — {@code error_max_turns}, an
     * {@code api_error_status}, a permission denial — and the parser turned it into events, so
     * throwing here would replace a precise diagnosis with a generic one. The single exception is a
     * {@code --resume} session that no longer exists: see {@link #throwIfTheResumeSessionWasLost}.
     */
    @Override
    public void streamEvents(LlmConfigVO config, AgentStreamOptions options, Consumer<AgentEvent> sink) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(sink, "sink");
        if (!available()) {
            throw new LlmGatewayException(503, "llm.provider.cli_missing",
                    BINARY + " CLI is not installed in the server runtime",
                    "Install the CLI into the rocketmq-server image or switch the engine to HTTP.");
        }
        AiPayloadGuard.validateOutboundPrompt(options.getPrompt(), options.getModel());
        List<String> command = buildStreamCommand(config, options);
        ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser();
        long timeoutSeconds = Math.max(1L,
                options.timeoutOr(Duration.ofSeconds(streamTimeoutSeconds())).toSeconds());
        SpawnResult spawn = spawn(command, childEnv(config, options), timeoutSeconds,
                options.getWorkspaceDir(), line -> parser.parseLine(line).forEach(sink),
                options.getProcessSink());
        throwIfTheResumeSessionWasLost(options, parser, spawn);
        if (spawn.exitCode() != 0 && !parser.resultFrameSeen()) {
            throw new LlmGatewayException(502, "llm.provider.cli_error",
                    BINARY + " CLI failed: " + describeStderr(spawn.stderr()),
                    "Check the provider credentials, base URL, model name and the resume session id.");
        }
    }

    /**
     * Reports the one failure a caller can repair by retrying: the conversation's {@code --resume}
     * session is gone. The frames have already said so, so this adds no diagnosis — what the caller
     * cannot know on its own is that the retry has to drop {@code --resume}, and the command is built
     * here. {@link ResumeRecovery} holds the contract.
     *
     * <p>Both halves of the signal are needed: the stderr line is a human-readable string a CLI
     * upgrade may reword, and {@code error_during_execution} also reports failures no retry can fix.
     */
    private void throwIfTheResumeSessionWasLost(AgentStreamOptions options,
                                                ClaudeCodeStreamParser parser, SpawnResult spawn) {
        if (!ResumeRecovery.shouldRetryWithoutResume(StringUtils.hasText(options.getResumeSessionId()),
                spawn.exitCode(), parser.resultSubtype(), spawn.stderr())) {
            return;
        }
        throw new LlmGatewayException(502, ResumeRecovery.RESUME_LOST_CODE,
                "the session conversation resumed no longer exists: " + describeStderr(spawn.stderr()),
                "The turn is retried once without --resume; the earlier turns' context is lost.");
    }

    /**
     * Streams completion tokens as plain text.
     *
     * @deprecated only the prompt-enhancement rewrite still needs a text-only channel; agent runs use
     *     {@link #streamEvents(LlmConfigVO, AgentStreamOptions, Consumer)}.
     */
    @Deprecated
    @Override
    public void stream(LlmConfigVO config, String prompt, String modelOverride, Consumer<String> tokenConsumer) {
        if (!available()) {
            throw new LlmGatewayException(503, "llm.provider.cli_missing",
                    binaryName() + " CLI is not installed in the server runtime",
                    "Install the CLI into the rocketmq-server image or switch the engine to HTTP.");
        }
        List<String> command = withStreamFormat(buildCommand(config, prompt, modelOverride));
        ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser();
        AtomicBoolean emitted = new AtomicBoolean(false);
        SpawnResult spawn = spawn(command, childEnv(config), streamTimeoutSeconds(), null, line -> {
            for (AgentEvent event : parser.parseLine(line)) {
                if (event instanceof AgentEvent.TextDelta delta && !delta.content().isEmpty()) {
                    emitted.set(true);
                    tokenConsumer.accept(delta.content());
                }
            }
        }, null);
        if (spawn.exitCode() != 0 && !emitted.get()) {
            throw new LlmGatewayException(502, "llm.provider.cli_error",
                    binaryName() + " CLI failed: " + describeStderr(spawn.stderr()),
                    "Check the provider credentials, base URL and model name.");
        }
        if (!emitted.get() && StringUtils.hasText(parser.fallbackText())) {
            tokenConsumer.accept(parser.fallbackText());
        }
    }

    /**
     * Starts the CLI, feeds every stdout line to {@code stdoutLine} and enforces the wall-clock
     * budget.
     *
     * <p>stdout is drained on a virtual thread and stderr on another, both before {@code waitFor}
     * returns: reading them sequentially on the caller thread deadlocks as soon as the child fills a
     * 64 KiB pipe buffer, and a timeout checked only after both reads can never fire while the child
     * stays alive. {@code waitFor} runs first for the same reason, so a hung child is destroyed
     * rather than waiting for output that will never come.
     */
    private SpawnResult spawn(List<String> command, Map<String, String> providerEnvironment,
                              long timeoutSeconds, String workspaceDir, Consumer<String> stdoutLine,
                              AgentStreamOptions.AgentProcessSink processSink) {
        ProcessBuilder builder = new ProcessBuilder(command);
        processEnvironment().apply(builder, providerEnvironment);
        builder.redirectErrorStream(false);
        if (StringUtils.hasText(workspaceDir)) {
            // A stable cwd is what makes --resume work: claude hashes the project directory to find
            // its session state, so a per-run temporary directory would break every resume.
            builder.directory(new File(workspaceDir.trim()));
        }
        // The claude CLI waits 3s for stdin and emits a warning that leaks into the
        // reply unless stdin is explicitly /dev/null; fall back to closing the pipe
        // on platforms without it.
        boolean devNullAvailable = redirectInputFromDevNull(builder);
        Process process = null;
        try {
            process = startProcess(builder);
            if (!devNullAvailable) {
                process.getOutputStream().close();
            }
            if (processSink != null) {
                processSink.attachProcess(process);
            }
            CompletableFuture<Void> stdoutFuture = drainStdout(process.getInputStream(), stdoutLine);
            CompletableFuture<String> stderrFuture = readAsync(process.getErrorStream());
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                destroyProcessTree(process);
                throw new LlmGatewayException(504, "llm.provider.timeout",
                        binaryName() + " CLI stream timed out after " + timeoutSeconds + "s",
                        "Retry with a shorter prompt or check the gateway latency.");
            }
            await(stdoutFuture);
            return new SpawnResult(process.exitValue(), await(stderrFuture));
        } catch (IOException exception) {
            if (process != null) {
                destroyProcessTree(process);
            }
            throw new LlmGatewayException(502, "llm.provider.io_error",
                    "Failed to execute " + binaryName() + " CLI",
                    "Check that the CLI binary is installed and executable.", exception);
        } catch (InterruptedException exception) {
            if (process != null) {
                destroyProcessTree(process);
            }
            Thread.currentThread().interrupt();
            throw new LlmGatewayException(502, "llm.provider.interrupted",
                    binaryName() + " CLI execution was interrupted", "Retry the request.", exception);
        }
    }

    protected Process startProcess(ProcessBuilder builder) throws IOException {
        return builder.start();
    }

    protected boolean redirectInputFromDevNull(ProcessBuilder builder) {
        File devNull = new File("/dev/null");
        if (!devNull.exists()) {
            return false;
        }
        builder.redirectInput(ProcessBuilder.Redirect.from(devNull));
        return true;
    }

    protected long streamTimeoutSeconds() {
        return STREAM_TIMEOUT_SECONDS;
    }

    private void destroyProcessTree(Process process) {
        AgentProcessTree.destroyForcibly(process, binaryName() + " CLI stream");
    }

    /** The Anthropic credentials for this configuration, unchanged from the text-only path. */
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

    /**
     * The child environment: the Anthropic credentials plus whatever this run adds. Per-run entries
     * win, and they bypass the inherited-environment allow-list by design — see
     * {@link CliProcessEnvironment#applyIsolated(ProcessBuilder, Map)}.
     */
    protected Map<String, String> childEnv(LlmConfigVO config, AgentStreamOptions options) {
        Map<String, String> env = new HashMap<>(childEnv(config));
        if (options != null) {
            options.getExtraEnv().forEach((name, value) -> {
                if (StringUtils.hasText(name) && value != null) {
                    env.put(name, value);
                }
            });
        }
        return env;
    }

    private static List<String> withStreamFormat(List<String> command) {
        command.add("--output-format");
        command.add("stream-json");
        command.add("--verbose");
        command.add("--include-partial-messages");
        return command;
    }

    /**
     * Reads the system prompt to append. {@code --append-system-prompt} takes text, not a path, so
     * the file is read here. An unreadable file degrades to no extra prompt with a warning rather
     * than failing the run: the server-side mutation filter still enforces the preview/apply
     * protocol, so losing the prompt cannot turn into an unguarded write.
     */
    private String readSystemPrompt(String systemPromptPath) {
        if (!StringUtils.hasText(systemPromptPath)) {
            return null;
        }
        try {
            String text = Files.readString(Path.of(systemPromptPath.trim()), StandardCharsets.UTF_8);
            return StringUtils.hasText(text) ? text : null;
        } catch (IOException | RuntimeException exception) {
            log.warn("Ignoring unreadable agent system prompt {}: {}", systemPromptPath, exception.toString());
            return null;
        }
    }

    /** The per-request override wins; otherwise the stored configuration decides. */
    private static String resolveModel(String modelOverride, LlmConfigVO config) {
        if (StringUtils.hasText(modelOverride)) {
            return modelOverride.trim();
        }
        String model = config == null ? null : config.getModel();
        return StringUtils.hasText(model) ? model.trim() : null;
    }

    private static String describeStderr(String stderr) {
        return StringUtils.hasText(stderr) ? stderr.trim() : "unknown error";
    }

    private CompletableFuture<Void> drainStdout(InputStream stdout, Consumer<String> stdoutLine) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stdout, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdoutLine.accept(line);
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
                byte[] bytes = stream.readNBytes(MAX_STDERR_BYTES + 1);
                boolean truncated = bytes.length > MAX_STDERR_BYTES;
                int length = Math.min(bytes.length, MAX_STDERR_BYTES);
                String output = new String(bytes, 0, length, StandardCharsets.UTF_8);
                result.complete(truncated ? output + STDERR_TRUNCATED_SUFFIX : output);
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

    /** How the child finished: its exit code and its (already bounded) stderr. */
    private record SpawnResult(int exitCode, String stderr) {
    }
}
