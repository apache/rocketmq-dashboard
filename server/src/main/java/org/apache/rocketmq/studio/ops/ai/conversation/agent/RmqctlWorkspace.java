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
package org.apache.rocketmq.studio.ops.ai.conversation.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.CliProcessEnvironment;
import org.apache.rocketmq.studio.ops.ai.conversation.AiConversationProperties;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Owns the per-conversation directory a hosted agent run executes in: the {@code rmqctl} config, the
 * MCP client snippet handed to {@code claude --mcp-config}, the appended system prompt, the child's
 * {@code HOME} and its working directory.
 *
 * <pre>
 * ${studio.ai.conversation.workspace-dir}/conv-&lt;id&gt;/   0700, created lazily at run admission
 *   rmqctl.yaml                 0600, env: references only, never a secret
 *   mcp.json                    0600, byte-for-byte the shape of `rmqctl mcp config`
 *   agent-system-prompt.txt     0600, a copy of the classpath prompt (--append-system-prompt takes text)
 *   home/                       0700, the child's HOME, per conversation and persistent
 * </pre>
 *
 * <h2>Why the directory, HOME and cwd are per conversation rather than per run</h2>
 * {@code claude --resume <id>} resolves state at
 * {@code $HOME/.claude/projects/<cwd with "/" replaced by "-">/<session-id>.jsonl}. Measured against
 * the CLI: the same session id with a different cwd exits 1, and the same session id and cwd with a
 * different HOME also exits 1. A per-run temporary directory would therefore silently break every
 * multi-turn conversation, so all three are keyed by the conversation id and kept for the
 * conversation's lifetime; {@link #delete(long)} removes them with the conversation.
 *
 * <h2>Where the instance binding actually lives</h2>
 * {@code --instance-id} is baked into {@code mcp.json}'s argv. That is the enforcement point: the
 * agent can call tools but cannot edit argv, and {@code rmqctl/cmd/runtime.go} refuses to default the
 * instance id from the context or from anywhere else. {@code --config} is mandatory for the same
 * reason — the container has no {@code ~/.rmqctl/config.yaml}, and omitting it yields
 * {@code {"code":"COMMAND_FAILED","message":"no current context is selected"}}.
 *
 * <h2>Secrets</h2>
 * The access key and secret key are resolved <em>per run</em> (so an operator editing
 * {@code studio.cluster.admin.credentials.*} takes effect on the next message, not the next restart)
 * and handed back in {@link Preparation#childEnv()} only. They are never written to
 * {@code rmqctl.yaml} — that file carries the {@code env:RMQ_AI_ACCESS_KEY} / {@code env:RMQ_AI_SECRET_KEY}
 * references {@code rmqctl}'s {@code config.ValidateContext} requires — never placed in argv, never
 * persisted to a database column and never logged. {@code mcp.json} needs no {@code env} block:
 * variables exported into the {@code claude} process are inherited by the {@code rmqctl} MCP child.
 *
 * <h2>Degraded mode</h2>
 * When {@code rmqctl} is not on PATH or {@code studio.ai.conversation.rmqctl-enabled=false},
 * {@link #prepare(long, String)} returns {@link Optional#empty()} and creates nothing. The caller
 * then omits {@code --mcp-config}, {@code --strict-mcp-config} and {@code --allowedTools}, keeps the
 * full built-in disallow list — i.e. exactly the pre-MCP behaviour — and persists
 * {@link #degradedNotice()} as the run's first event, so the conversation still works as plain chat
 * and the missing tools are discoverable rather than mysterious.
 *
 * <h2>L2/L3 mutations over MCP, and why there is no {@code --yes} here</h2>
 * {@code rmqctl/cmd/catalog.go}'s {@code defaultConfirm} requires a TTY and rejects pipes and
 * redirections outright, and {@code rmqctl/cmd/mcp.go}'s {@code mcp stdio} has no {@code --yes} path
 * at all. Nothing in this workspace can or should change that. Over MCP the two-step handshake is
 * expressed in the tool protocol itself instead: a dry-run returns a {@code confirm_token} and the
 * apply call consumes it. Enforcement is server-side — {@code ToolMutationFilter} plus
 * {@code ToolTokenService} plus {@code studio.ai.allow-l3-tools} (default {@code false}) — so the
 * hosted agent <strong>cannot run L3 tools unless that flag is on</strong>, and an L2 mutation only
 * succeeds if the agent really does preview first.
 *
 * <p>That is documented rather than coded around on purpose. The consequence for the UI is that a
 * refused mutation is a normal, expected outcome: {@code l3ToolsAllowed} is surfaced in the agent
 * capabilities VO so the composer can explain the refusal instead of showing a mystery error, and the
 * system prompt written into this workspace is what tells the agent to run the two steps and to report
 * a refusal verbatim rather than retry around it.
 */
@Slf4j
@Component
public class RmqctlWorkspace {

    /** The MCP transport binary. Probed with the same {@link CliBinaryProbe} the agent CLIs use. */
    static final String RMQCTL_BINARY = "rmqctl";
    static final String CONFIG_FILE_NAME = "rmqctl.yaml";
    static final String MCP_CONFIG_FILE_NAME = "mcp.json";
    static final String SYSTEM_PROMPT_FILE_NAME = "agent-system-prompt.txt";
    static final String HOME_DIR_NAME = "home";
    static final String WORKSPACE_PREFIX = "conv-";
    static final String CONTEXT_NAME = "studio";

    /**
     * Child environment variable names carrying the credential. They must match the {@code env:}
     * references written into {@code rmqctl.yaml}; the two are the same contract seen from two sides.
     * Deliberately <em>not</em> added to {@code CliProcessEnvironment}'s allow-list: provider-supplied
     * entries bypass it by design, and allow-listing them would additionally leak any server-process
     * value of the same name into every child.
     */
    public static final String ACCESS_KEY_ENV = "RMQ_AI_ACCESS_KEY";
    public static final String SECRET_KEY_ENV = "RMQ_AI_SECRET_KEY";
    public static final String HOME_ENV = "HOME";

    /**
     * Persisted as the run's first event when the agent has to do without RocketMQ tools. Spelled
     * with unicode escapes because {@code style/rmq_checkstyle.xml} rejects non-ASCII characters in
     * Java sources; it reads "rmqctl unavailable, RocketMQ tools are disabled for this
     * conversation". {@code RmqctlWorkspaceTest} asserts it against the readable text in
     * {@code src/test/resources/ai/agent-notices.txt}.
     */
    public static final String DEGRADED_NOTICE_MESSAGE =
            "rmqctl \u4E0D\u53EF\u7528\uFF0C\u672C\u6B21\u4F1A\u8BDD\u5DF2\u7981\u7528 RocketMQ \u5DE5\u5177";

    /** Same, for a conversation that was created without an instance to bind tools to. */
    public static final String UNBOUND_NOTICE_MESSAGE =
            "\u672C\u6B21\u4F1A\u8BDD\u672A\u7ED1\u5B9A\u5B9E\u4F8B\uFF0C\u5DF2\u7981\u7528 RocketMQ \u5DE5\u5177";

    private static final String SYSTEM_PROMPT_RESOURCE = "/prompts/agent-system-prompt.txt";
    private static final String SYSTEM_PROMPT = loadSystemPrompt();
    private static final Duration DEFAULT_RMQCTL_TIMEOUT = Duration.ofSeconds(60);
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final long NANOS_PER_MICROSECOND = 1_000L;
    /** Digits Go's fmtFrac keeps for each unit before stripping trailing zeros. */
    private static final int FRACTION_DIGITS_SECONDS = 9;
    private static final int FRACTION_DIGITS_MILLIS = 6;
    private static final int FRACTION_DIGITS_MICROS = 3;
    /** Go spells microseconds with U+00B5, which checkstyle will not accept as a literal here. */
    private static final String MICRO_SECOND_SUFFIX = "\u00B5s";
    private static final Set<PosixFilePermission> OWNER_ONLY_FILE =
            PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY =
            PosixFilePermissions.fromString("rwx------");
    /**
     * A private, non-shared ObjectMapper: this writes two small contract files and must not inherit
     * whatever serialisation features the REST layer configures (pretty printing, inclusion rules),
     * because {@code mcp.json} is compared byte-for-byte against {@code rmqctl mcp config}.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern IPV4_LOOPBACK =
            Pattern.compile("^127\\.(25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])(\\.(25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])){2}$");
    private static final List<String> IPV6_LOOPBACK_FORMS = List.of("::1", "[::1]", "0:0:0:0:0:0:0:1");

    private final AiConversationProperties properties;
    private final InstanceCredentialResolver credentialResolver;
    private final BooleanSupplier rmqctlAvailability;
    private final int serverPort;

    /**
     * Production wiring. The availability probe is built here rather than injected because
     * {@link CliBinaryProbe} is final and its own seam is the process starter; the package-private
     * constructor below takes the already-reduced "is rmqctl there?" question instead, so a degraded
     * mode is testable without faking a {@link Process}.
     */
    @Autowired
    public RmqctlWorkspace(AiConversationProperties properties,
                           InstanceCredentialResolver credentialResolver,
                           CliProcessEnvironment processEnvironment,
                           @Value("${server.port:8888}") int serverPort) {
        this(properties, credentialResolver,
                () -> new CliBinaryProbe(processEnvironment).isAvailable(RMQCTL_BINARY), serverPort);
    }

    RmqctlWorkspace(AiConversationProperties properties,
                    InstanceCredentialResolver credentialResolver,
                    BooleanSupplier rmqctlAvailability,
                    int serverPort) {
        this.properties = properties;
        this.credentialResolver = credentialResolver;
        this.rmqctlAvailability = rmqctlAvailability;
        this.serverPort = serverPort;
    }

    /**
     * Materialises the workspace for one run and resolves the credential to inject.
     *
     * <p>Idempotent: every file is rewritten from scratch, so a run admitted after a container
     * restart (which wipes {@code /tmp}) gets a complete workspace again, while {@code home/} and
     * whatever {@code claude} persisted inside it are left alone.
     *
     * @return empty when the agent must run without RocketMQ tools — rmqctl disabled, rmqctl missing,
     *     or no instance bound to the conversation. Empty is <em>not</em> an error: the conversation
     *     degrades to plain chat and the caller persists {@link #degradedNotice()} or
     *     {@link #unboundNotice()} as the run's first event.
     * @throws BusinessException when the workspace <em>could</em> be built but the configuration is
     *     wrong: an unusable {@code rmqctl-server-url}, an unusable {@code workspace-dir}, or an
     *     instance with no resolvable credential. These fail loudly instead of degrading, because
     *     the operator asked for tools and a silent fallback would look like an agent that refuses
     *     to answer. A filesystem failure, by contrast, degrades: losing the tools is better than
     *     losing the conversation.
     */
    public Optional<Preparation> prepare(long conversationId, String instanceId) {
        if (!properties.isRmqctlEnabled()) {
            log.debug("rmqctl is disabled by configuration; conversation {} runs without tools", conversationId);
            return Optional.empty();
        }
        if (!StringUtils.hasText(instanceId)) {
            log.info("conversation {} is not bound to an instance; running without RocketMQ tools", conversationId);
            return Optional.empty();
        }
        if (!rmqctlAvailability.getAsBoolean()) {
            log.warn("{} is not available in the server runtime; conversation {} runs without tools",
                    RMQCTL_BINARY, conversationId);
            return Optional.empty();
        }
        Path workspace = workspaceDir(conversationId);
        Path home = workspace.resolve(HOME_DIR_NAME);
        // Resolved before anything is written, so a credential failure leaves no half-built workspace
        // behind — the same invariant the degraded paths above keep.
        Map<String, String> childEnv = childEnv(home, instanceId);
        try {
            createOwnerOnlyDirectory(workspaceRoot());
            createOwnerOnlyDirectory(workspace);
            createOwnerOnlyDirectory(home);
            writeOwnerOnly(workspace.resolve(CONFIG_FILE_NAME), rmqctlYaml().getBytes(StandardCharsets.UTF_8));
            writeOwnerOnly(workspace.resolve(MCP_CONFIG_FILE_NAME), mcpJson(workspace, instanceId));
            writeOwnerOnly(workspace.resolve(SYSTEM_PROMPT_FILE_NAME),
                    systemPromptFor(instanceId).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            log.warn("could not prepare the agent workspace {}: {}", workspace, exception.toString());
            return Optional.empty();
        }
        return Optional.of(new Preparation(
                workspace.toString(),
                home.toString(),
                workspace.resolve(CONFIG_FILE_NAME).toString(),
                workspace.resolve(MCP_CONFIG_FILE_NAME).toString(),
                workspace.resolve(SYSTEM_PROMPT_FILE_NAME).toString(),
                instanceId.trim(),
                childEnv));
    }

    /**
     * Removes a conversation's workspace, called by {@code DELETE /api/ai/conversations/{id}} after
     * the rows are gone. Never throws: a stale directory on a deleted volume must not turn a
     * successful delete into a 500, and it holds nothing that is not reproducible except the
     * {@code claude} resume state of a conversation that no longer exists.
     *
     * @return true when the directory is gone afterwards
     */
    public boolean delete(long conversationId) {
        Path workspace = workspaceDir(conversationId);
        if (!Files.exists(workspace)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(workspace)) {
            // Deepest first: a directory cannot be deleted while it still has children.
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.warn("could not delete {}: {}", path, exception.toString());
                }
            });
        } catch (IOException | RuntimeException exception) {
            log.warn("could not walk the agent workspace {}: {}", workspace, exception.toString());
        }
        boolean gone = !Files.exists(workspace);
        if (!gone) {
            log.warn("agent workspace {} survived deletion", workspace);
        }
        return gone;
    }

    /** Root of all conversation workspaces; created on demand. */
    public Path workspaceRoot() {
        String configured = properties.getWorkspaceDir();
        if (!StringUtils.hasText(configured)) {
            throw new BusinessException(500,
                    "studio.ai.conversation.workspace-dir must not be blank");
        }
        return Path.of(configured.trim());
    }

    /** The directory one conversation runs in, also its child process working directory. */
    public Path workspaceDir(long conversationId) {
        if (conversationId <= 0) {
            throw new BusinessException(400, "conversationId must be positive: " + conversationId);
        }
        return workspaceRoot().resolve(WORKSPACE_PREFIX + conversationId);
    }

    /**
     * The child environment for one run: the two credential variables {@code rmqctl.yaml} refers to,
     * plus {@code HOME}.
     *
     * <p>{@code HOME} is set unconditionally rather than only when the parent has none.
     * {@code CliProcessEnvironment} does allow {@code HOME} through inheritance, but a container
     * often does not set it at all, and inheriting a shared {@code /root} would put every
     * conversation's resume state in one directory — which works, but is not the per-conversation
     * isolation the workspace exists for. Provider entries win over inherited ones, so this is also
     * the value {@code claude} actually sees.
     */
    Map<String, String> childEnv(Path home, String instanceId) {
        InstanceCredentialResolver.InstanceCredential credential =
                credentialResolver.resolveByName(instanceId);
        Map<String, String> env = new LinkedHashMap<>();
        env.put(ACCESS_KEY_ENV, credential.accessKey());
        env.put(SECRET_KEY_ENV, credential.secretKey());
        env.put(HOME_ENV, home.toString());
        return Collections.unmodifiableMap(env);
    }

    /**
     * {@code rmqctl.yaml}: the loopback callback URL plus {@code env:} credential references.
     *
     * <p>The file must be mode 0600 — {@code rmqctl/internal/config/permissions_unix.go} rejects
     * anything with group or other bits set — and the server must be loopback or https, because
     * {@code rmqctl/internal/studio/client.go} refuses plain HTTP to a non-loopback host.
     */
    String rmqctlYaml() {
        return "currentContext: " + CONTEXT_NAME + '\n'
                + "contexts:\n"
                + "  " + CONTEXT_NAME + ":\n"
                + "    server: " + yamlDoubleQuoted(serverUrl()) + '\n'
                + "    credential:\n"
                + "      accessKeyRef: env:" + ACCESS_KEY_ENV + '\n'
                + "      secretKeyRef: env:" + SECRET_KEY_ENV + '\n';
    }

    /**
     * {@code mcp.json}, identical in shape to what {@code rmqctl mcp config} prints
     * ({@code rmqctl/cmd/mcp.go}): the same key order of flags — {@code --config}, then
     * {@code --context} (omitted, {@code currentContext} is in the YAML), then {@code --instance-id},
     * then {@code --timeout} — under the same server name the timeline projector expects.
     *
     * <p>Built with Jackson rather than string concatenation so a path or an instance id containing
     * a quote or a backslash cannot produce a malformed file, and pinned from the Go side by
     * {@code rmqctl/cmd/mcp_config_golden_test.go}.
     */
    byte[] mcpJson(Path workspace, String instanceId) throws IOException {
        return mcpSnippet(workspace.resolve(CONFIG_FILE_NAME).toString(), instanceId);
    }

    /**
     * The paste-ready {@code {"mcpServers":{...}}} snippet for an agent Studio does <em>not</em> host —
     * the answer of {@code GET /api/ai/conversations/{id}/rmqctl-config}. This is the endpoint that
     * backs the architectural claim the whole design rests on: the same signed tool gateway, the same
     * per-instance binding and the same risk gates, reached by a Claude Desktop or Cursor the user runs
     * themselves.
     *
     * <p>Same document and same argv order as {@link #mcpJson}, with one deliberate difference: no
     * {@code --config}. The path this server would pass points inside its own container and does not
     * exist on the user's machine; leaving it out makes the external {@code rmqctl} resolve its own
     * config ({@code $RMQCTL_CONFIG}, else {@code ~/.rmqctl/config.yaml} — see
     * {@code rmqctl/internal/config/config.go}'s {@code Store.Path}), which is exactly what
     * {@code rmqctl mcp config --instance-id <id>} prints for that user. {@link #serverUrl()} is
     * returned alongside so the caller can tell them what to put in {@code contexts.studio.server}.
     *
     * <p>Carries no secret, and cannot: the credential lives in the user's own config file as the
     * {@code env:RMQ_AI_ACCESS_KEY} / {@code env:RMQ_AI_SECRET_KEY} references
     * {@link #rmqctlYaml()} writes, and there is no {@code env} block in the snippet either.
     *
     * @throws org.apache.rocketmq.studio.common.exception.BusinessException 400 when no instance is
     *     given, because {@code rmqctl/cmd/runtime.go} refuses to default {@code --instance-id} from
     *     anywhere and a snippet without it would fail at the first tool call
     */
    public String externalMcpSnippet(String instanceId) {
        try {
            return new String(mcpSnippet(null, instanceId), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            // Writing into a byte buffer: the only IOException source is the instance id being
            // unserialisable, which the blank check above already rules out.
            throw new BusinessException(500, "Could not build the rmqctl MCP snippet: " + exception.getMessage());
        }
    }

    /**
     * One {@code {"mcpServers":{...}}} document. {@code configPath} is null for the external snippet,
     * which mirrors {@code rmqctl/cmd/mcp.go}: it appends {@code --config} only when the flag was set.
     * The remaining argv order — {@code --config}, {@code --instance-id}, {@code --timeout} — is the
     * order the Go command prints and {@code rmqctl/cmd/mcp_config_golden_test.go} pins.
     */
    private byte[] mcpSnippet(String configPath, String instanceId) throws IOException {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required for the rmqctl MCP snippet");
        }
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode server = root.putObject("mcpServers")
                .putObject(AgentEventProjector.STUDIO_MCP_SERVER);
        server.put("command", RMQCTL_BINARY);
        ArrayNode args = server.putArray("args");
        args.add("mcp");
        args.add("stdio");
        if (configPath != null) {
            args.add("--config");
            args.add(configPath);
        }
        args.add("--instance-id");
        args.add(instanceId.trim());
        args.add("--timeout");
        args.add(goDuration(properties.getRmqctlTimeout()));
        return MAPPER.writeValueAsBytes(root);
    }

    /**
     * The Studio URL rmqctl calls back into: the explicit override when set, otherwise
     * {@code server.port} on loopback so the port keeps a single source of truth.
     *
     * <p>Public because {@code GET /api/ai/conversations/{id}/rmqctl-config} reports it next to the
     * paste-ready snippet: an operator who wants an agent outside this container to reach Studio sets
     * {@code studio.ai.conversation.rmqctl-server-url} to the https URL, and that one property then
     * feeds both the hosted workspace and the snippet a user copies.
     */
    public String serverUrl() {
        String configured = properties.getRmqctlServerUrl();
        if (!StringUtils.hasText(configured)) {
            if (serverPort <= 0) {
                throw new BusinessException(500, "Cannot derive the rmqctl callback URL because server.port is "
                        + serverPort + "; set studio.ai.conversation.rmqctl-server-url explicitly.");
            }
            return requireCallbackSafe("http://127.0.0.1:" + serverPort, "the derived loopback URL");
        }
        return requireCallbackSafe(configured.trim(), "studio.ai.conversation.rmqctl-server-url");
    }

    /** The run's system prompt, copied from the classpath into the workspace. */
    static String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * The classpath prompt with the bound instance substituted in. The prompt tells the agent to
     * pass exactly this id on every tool call, which is what stops it from burning turns probing
     * candidate instance ids; the placeholder never survives into a prepared workspace.
     */
    static String systemPromptFor(String instanceId) {
        return SYSTEM_PROMPT.replace("{instanceId}", instanceId.trim());
    }

    /**
     * Renders a {@link Duration} exactly the way Go's {@code time.Duration.String()} does.
     *
     * <p>This is not cosmetic. {@code rmqctl mcp config} echoes {@code runtime.options.timeout.String()}
     * into the snippet it prints, and Go canonicalises rather than repeating what the user typed:
     * {@code --timeout 60s} comes back as {@code "1m0s"}. {@code mcp.json} is supposed to be the same
     * snippet, so the same canonicalisation has to happen here; {@code Duration.toString()} would give
     * ISO-8601 ({@code PT60S}), which {@code time.ParseDuration} rejects outright.
     *
     * <p>Ported from {@code time.Duration.format} in the Go 1.27.1 standard library and verified
     * against that runtime for the table below (also asserted in {@code RmqctlWorkspaceTest}):
     *
     * <pre>
     * 0 -&gt; 0s              1ns -&gt; 1ns            1.5us -&gt; 1.5(MICRO SIGN)s
     * 1ms -&gt; 1ms            100ms -&gt; 100ms        999999999ns -&gt; 999.999999ms
     * 1s -&gt; 1s              1.5s -&gt; 1.5s          59.999s -&gt; 59.999s
     * 60s -&gt; 1m0s           61.5s -&gt; 1m1.5s       90s -&gt; 1m30s
     * 3600s -&gt; 1h0m0s       3661.5s -&gt; 1h1m1.5s   90061s -&gt; 25h1m1s
     * </pre>
     *
     * @throws ArithmeticException for a timeout longer than about 292 years, which {@code toNanos()}
     *     cannot represent. That is an absurd configuration, not a case worth a friendlier error.
     */
    static String goDuration(Duration timeout) {
        Duration effective = timeout == null || timeout.isZero() || timeout.isNegative()
                ? DEFAULT_RMQCTL_TIMEOUT
                : timeout;
        long nanos = effective.toNanos();
        if (nanos < NANOS_PER_SECOND) {
            return subSecondDuration(nanos);
        }
        long totalSeconds = nanos / NANOS_PER_SECOND;
        long minutes = totalSeconds / 60;
        StringBuilder text = new StringBuilder();
        if (minutes >= 60) {
            text.append(minutes / 60).append('h');
        }
        if (minutes > 0) {
            text.append(minutes % 60).append('m');
        }
        return text.append(unit(nanos % NANOS_PER_SECOND, totalSeconds % 60, FRACTION_DIGITS_SECONDS))
                .append('s')
                .toString();
    }

    /** Go's sub-second branch: the largest unit that still leaves an integer part. */
    private static String subSecondDuration(long nanos) {
        if (nanos == 0) {
            return "0s";
        }
        if (nanos < NANOS_PER_MICROSECOND) {
            return nanos + "ns";
        }
        if (nanos < NANOS_PER_MILLISECOND) {
            return unit(nanos % NANOS_PER_MICROSECOND, nanos / NANOS_PER_MICROSECOND,
                    FRACTION_DIGITS_MICROS) + MICRO_SECOND_SUFFIX;
        }
        return unit(nanos % NANOS_PER_MILLISECOND, nanos / NANOS_PER_MILLISECOND,
                FRACTION_DIGITS_MILLIS) + "ms";
    }

    /**
     * An integer part plus the fractional digits Go's {@code fmtFrac} would write: zero-padded on the
     * right to {@code digits} and then stripped of trailing zeros, so 500000000 of a second reads
     * ".5" and 999000000 reads ".999". An empty string when there is no fraction at all.
     */
    private static String unit(long fractionNanos, long whole, int digits) {
        if (fractionNanos == 0) {
            return Long.toString(whole);
        }
        String padded = String.format(Locale.ROOT, "%0" + digits + "d", fractionNanos);
        int end = padded.length();
        while (end > 0 && padded.charAt(end - 1) == '0') {
            end--;
        }
        return whole + "." + padded.substring(0, end);
    }

    private static String requireCallbackSafe(String url, String origin) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            throw new BusinessException(500, origin + " is not a valid URL: " + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean loopbackHttp = "http".equals(scheme) && isLoopbackHost(uri.getHost());
        boolean allowed = "https".equals(scheme) || loopbackHttp;
        if (!allowed) {
            throw new BusinessException(500, origin + " must be an https URL or a loopback http URL: " + url
                    + " (rmqctl rejects plain HTTP for a non-loopback host)");
        }
        if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getFragment() != null) {
            throw new BusinessException(500,
                    origin + " must not contain user info, a query or a fragment: " + url);
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Mirrors {@code rmqctl/internal/studio/auth.go}'s {@code isLoopbackHost}: "localhost"
     * case-insensitively, or an IP literal that is loopback. Go uses {@code net.ParseIP}, which is
     * literal-only, so this deliberately does no DNS resolution either — a hostname that happens to
     * resolve to 127.0.0.1 is accepted by neither side.
     */
    static boolean isLoopbackHost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        if (IPV6_LOOPBACK_FORMS.contains(host)) {
            return true;
        }
        return IPV4_LOOPBACK.matcher(host).matches();
    }

    private static String yamlDoubleQuoted(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static void createOwnerOnlyDirectory(Path directory) throws IOException {
        try {
            Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY));
        } catch (UnsupportedOperationException exception) {
            // A filesystem without POSIX permissions (a Windows dev machine) ignores the attribute.
            Files.createDirectories(directory);
        }
        // createDirectories does not touch an existing directory, so tighten one left behind with a
        // looser mode by an earlier version or by a hand-made directory.
        chmod(directory, OWNER_ONLY_DIRECTORY);
    }

    private static void writeOwnerOnly(Path path, byte[] content) throws IOException {
        Files.deleteIfExists(path);
        try {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE));
        } catch (UnsupportedOperationException exception) {
            Files.createFile(path);
        }
        Files.write(path, content);
        // The attribute is masked by the umask on create; 0600 has no group or other bits for it to
        // remove, but re-asserting keeps the guarantee independent of how the file came into being.
        chmod(path, OWNER_ONLY_FILE);
    }

    private static void chmod(Path path, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (RuntimeException | IOException exception) {
            // RuntimeException covers the UnsupportedOperationException of a filesystem without POSIX
            // permissions and a SecurityException from a locked-down JVM; neither is worth failing a
            // run over, because the create-time attribute already asked for the same mode.
            log.debug("cannot set permissions on {}: {}", path, exception.toString());
        }
    }

    private static String loadSystemPrompt() {
        try (InputStream in = RmqctlWorkspace.class.getResourceAsStream(SYSTEM_PROMPT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(SYSTEM_PROMPT_RESOURCE + " is missing on the classpath");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (!StringUtils.hasText(text)) {
                throw new IllegalStateException(SYSTEM_PROMPT_RESOURCE + " is empty");
            }
            return text;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load " + SYSTEM_PROMPT_RESOURCE, exception);
        }
    }

    /** The first event of a run that has to do without RocketMQ tools. */
    public static TimelineEvent.Notice degradedNotice() {
        return new TimelineEvent.Notice(AgentEventProjector.LEVEL_WARN, DEGRADED_NOTICE_MESSAGE);
    }

    /** The first event of a run in a conversation that was never bound to an instance. */
    public static TimelineEvent.Notice unboundNotice() {
        return new TimelineEvent.Notice(AgentEventProjector.LEVEL_WARN, UNBOUND_NOTICE_MESSAGE);
    }

    /**
     * Everything one run needs to spawn an agent with RocketMQ tools: the paths to pass as
     * {@code AgentStreamOptions.workspaceDir} / {@code mcpConfigPath} / {@code systemPromptPath}, and
     * the environment to pass as {@code extraEnv}.
     *
     * <p>A record with an explicit {@code toString()} rather than a Lombok {@code @Value} with
     * {@code @ToString.Exclude}: {@code childEnv} carries the credential, and the only way to be sure
     * it cannot reach a log line is to never render the values. The key <em>names</em> are printed,
     * because "which variables were injected" is exactly what a diagnosis needs.
     */
    public record Preparation(String workspaceDir,
                              String homeDir,
                              String rmqctlConfigPath,
                              String mcpConfigPath,
                              String systemPromptPath,
                              String instanceId,
                              Map<String, String> childEnv) {

        public Preparation {
            childEnv = childEnv == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(childEnv));
        }

        @Override
        public String toString() {
            return "Preparation[workspaceDir=" + workspaceDir
                    + ", instanceId=" + instanceId
                    + ", mcpConfigPath=" + mcpConfigPath
                    + ", systemPromptPath=" + systemPromptPath
                    + ", childEnv=" + childEnv.keySet() + ']';
        }
    }
}
