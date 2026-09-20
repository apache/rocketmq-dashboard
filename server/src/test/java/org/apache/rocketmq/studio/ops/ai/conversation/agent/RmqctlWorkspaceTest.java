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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.conversation.AiConversationProperties;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the workspace contract: the two files {@code rmqctl} and {@code claude} read, the modes they
 * must have, the per-conversation HOME that makes {@code --resume} work, where the credential is
 * allowed to appear, and the degraded mode that must still leave a usable chat.
 *
 * <p>{@code mcp.json} is additionally pinned from the Go side by
 * {@code rmqctl/cmd/mcp_config_golden_test.go}, which runs the real {@code rmqctl mcp config} command
 * and compares it against the same snippet literal used here. The two tests are the cross-language
 * contract; changing one without the other is how the agent silently stops finding its tools.
 */
class RmqctlWorkspaceTest {

    private static final long CONVERSATION_ID = 42L;
    private static final String INSTANCE_ID = "instance-a";
    private static final String ACCESS_KEY = "ak-under-test";
    private static final String SECRET_KEY = "sk-must-never-leave-the-child-env";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The snippet {@code rmqctl mcp config --instance-id instance-a --config <ws>/rmqctl.yaml
     * --timeout 60s} prints, in compact form. Keep in sync with {@code studioJavaMcpSnippet} in
     * {@code rmqctl/cmd/mcp_config_golden_test.go}.
     *
     * <p>The timeout reads {@code 1m0s} and not {@code 60s}: {@code mcp.go} echoes
     * {@code runtime.options.timeout.String()}, and Go canonicalises. {@code RmqctlWorkspace}
     * reproduces that so the two snippets are identical.
     */
    private static final String MCP_CONFIG_SHAPE = "{\"mcpServers\":{\"rocketmq-studio\":{"
            + "\"command\":\"rmqctl\",\"args\":[\"mcp\",\"stdio\",\"--config\",\"%s\",\"--instance-id\",\""
            + INSTANCE_ID + "\",\"--timeout\",\"1m0s\"]}}}";

    @TempDir
    Path root;

    private AiConversationProperties properties;
    private InstanceCredentialResolver credentials;
    private AtomicBoolean probeConsulted;

    @BeforeEach
    void setUp() {
        properties = new AiConversationProperties();
        properties.setWorkspaceDir(root.toString());
        properties.setRmqctlEnabled(true);
        properties.setRmqctlTimeout(Duration.ofSeconds(60));
        credentials = mock(InstanceCredentialResolver.class);
        when(credentials.resolveByName(INSTANCE_ID))
                .thenReturn(new InstanceCredentialResolver.InstanceCredential(ACCESS_KEY, SECRET_KEY));
        probeConsulted = new AtomicBoolean();
    }

    private RmqctlWorkspace workspace(boolean rmqctlAvailable, int serverPort) {
        return new RmqctlWorkspace(properties, credentials, () -> {
            probeConsulted.set(true);
            return rmqctlAvailable;
        }, serverPort);
    }

    private RmqctlWorkspace availableWorkspace() {
        return workspace(true, 8888);
    }

    private RmqctlWorkspace.Preparation prepared() {
        return availableWorkspace().prepare(CONVERSATION_ID, INSTANCE_ID).orElseThrow(
                () -> new AssertionError("the workspace should have been prepared"));
    }

    @Test
    void writesRmqctlConfigWithEnvironmentReferencesOnlyTest() throws Exception {
        RmqctlWorkspace.Preparation preparation = prepared();

        String yaml = Files.readString(Path.of(preparation.rmqctlConfigPath()), StandardCharsets.UTF_8);

        assertThat(yaml).isEqualTo("""
                currentContext: studio
                contexts:
                  studio:
                    server: "http://127.0.0.1:8888"
                    credential:
                      accessKeyRef: env:RMQ_AI_ACCESS_KEY
                      secretKeyRef: env:RMQ_AI_SECRET_KEY
                """);
        // The whole point of the env: indirection: rmqctl's config.ValidateContext requires a
        // reference, and a literal here would put a credential in a file on disk.
        assertThat(yaml).doesNotContain(ACCESS_KEY).doesNotContain(SECRET_KEY);
    }

    @Test
    void writesMcpConfigInRmqctlMcpConfigShapeTest() throws Exception {
        RmqctlWorkspace.Preparation preparation = prepared();

        JsonNode snippet = MAPPER.readTree(Path.of(preparation.mcpConfigPath()).toFile());
        JsonNode server = snippet.path("mcpServers").path(AgentEventProjector.STUDIO_MCP_SERVER);

        assertThat(server.path("command").asText()).isEqualTo("rmqctl");
        assertThat(server.path("args").isArray()).isTrue();
        List<String> args = new ArrayList<>();
        server.path("args").forEach(node -> args.add(node.asText()));
        // argv order is the contract: --config first, then the instance binding, then the timeout.
        assertThat(args).containsExactly("mcp", "stdio",
                "--config", preparation.rmqctlConfigPath(),
                "--instance-id", INSTANCE_ID,
                "--timeout", "1m0s");
        // No env block: variables exported into the claude process are inherited by the rmqctl child.
        assertThat(server.has("env")).isFalse();
        assertThat(snippet.path("mcpServers").size()).isEqualTo(1);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void writesMcpConfigAsCompactJsonTest() throws Exception {
        RmqctlWorkspace.Preparation preparation = prepared();

        String raw = Files.readString(Path.of(preparation.mcpConfigPath()), StandardCharsets.UTF_8);

        assertThat(raw).isEqualTo(MCP_CONFIG_SHAPE.formatted(preparation.rmqctlConfigPath()));
        assertThat(raw).doesNotEndWith("\n");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void createsWorkspaceAndConfigWithOwnerOnlyPermissionsTest() throws Exception {
        RmqctlWorkspace.Preparation preparation = prepared();

        // 0700 on the directories and 0600 on rmqctl.yaml are not cosmetic: rmqctl's
        // checkFilePermissions refuses to load a config with any group or other bit set.
        assertThat(permissions(Path.of(preparation.workspaceDir()))).isEqualTo("rwx------");
        assertThat(permissions(Path.of(preparation.homeDir()))).isEqualTo("rwx------");
        assertThat(permissions(Path.of(preparation.rmqctlConfigPath()))).isEqualTo("rw-------");
        assertThat(permissions(Path.of(preparation.mcpConfigPath()))).isEqualTo("rw-------");
        assertThat(permissions(Path.of(preparation.systemPromptPath()))).isEqualTo("rw-------");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void tightensALooseWorkspaceLeftBehindByAnEarlierRunTest() throws Exception {
        Path stale = root.resolve("conv-" + CONVERSATION_ID);
        Files.createDirectories(stale.resolve("home"));
        Files.setPosixFilePermissions(stale, PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.writeString(stale.resolve("rmqctl.yaml"), "currentContext: studio\n");
        Files.setPosixFilePermissions(stale.resolve("rmqctl.yaml"),
                PosixFilePermissions.fromString("rw-r--r--"));

        RmqctlWorkspace.Preparation preparation = prepared();

        assertThat(permissions(Path.of(preparation.workspaceDir()))).isEqualTo("rwx------");
        assertThat(permissions(Path.of(preparation.rmqctlConfigPath()))).isEqualTo("rw-------");
    }

    @Test
    void injectsCredentialsIntoTheChildEnvironmentOnlyTest() throws Exception {
        RmqctlWorkspace.Preparation preparation = prepared();

        assertThat(preparation.childEnv()).containsOnlyKeys(
                        RmqctlWorkspace.ACCESS_KEY_ENV, RmqctlWorkspace.SECRET_KEY_ENV, RmqctlWorkspace.HOME_ENV)
                .containsEntry(RmqctlWorkspace.ACCESS_KEY_ENV, ACCESS_KEY)
                .containsEntry(RmqctlWorkspace.SECRET_KEY_ENV, SECRET_KEY)
                .containsEntry(RmqctlWorkspace.HOME_ENV, preparation.homeDir());
        assertThat(preparation.childEnv()).isUnmodifiable();
        // The variable names are diagnostics; the values must never be rendered.
        assertThat(preparation.toString())
                .contains("RMQ_AI_ACCESS_KEY", "RMQ_AI_SECRET_KEY", INSTANCE_ID)
                .doesNotContain(ACCESS_KEY, SECRET_KEY);
        assertThat(workspaceFilesMentioningSecrets(preparation.workspaceDir())).isEmpty();
    }

    @Test
    void resolvesCredentialsPerRunRatherThanCachingThemPerConversationTest() {
        RmqctlWorkspace workspace = availableWorkspace();
        String first = workspace.prepare(CONVERSATION_ID, INSTANCE_ID).orElseThrow()
                .childEnv().get(RmqctlWorkspace.SECRET_KEY_ENV);
        when(credentials.resolveByName(INSTANCE_ID)).thenReturn(
                new InstanceCredentialResolver.InstanceCredential(ACCESS_KEY, "rotated-secret"));

        String second = workspace.prepare(CONVERSATION_ID, INSTANCE_ID).orElseThrow()
                .childEnv().get(RmqctlWorkspace.SECRET_KEY_ENV);

        assertThat(first).isEqualTo(SECRET_KEY);
        assertThat(second).isEqualTo("rotated-secret");
        verify(credentials, times(2)).resolveByName(INSTANCE_ID);
    }

    @Test
    void keepsHomeAndWorkingDirectoryStableAcrossRunsTest() throws Exception {
        RmqctlWorkspace workspace = availableWorkspace();
        RmqctlWorkspace.Preparation first = workspace.prepare(CONVERSATION_ID, INSTANCE_ID).orElseThrow();
        // What claude writes under HOME between turns; a workspace that recreated home would break
        // --resume exactly as a per-run temporary directory would.
        Path resumeState = Path.of(first.homeDir()).resolve(".claude/projects/-ws/session.jsonl");
        Files.createDirectories(resumeState.getParent());
        Files.writeString(resumeState, "session state");

        RmqctlWorkspace.Preparation second = workspace.prepare(CONVERSATION_ID, INSTANCE_ID).orElseThrow();

        assertThat(second.workspaceDir()).isEqualTo(first.workspaceDir());
        assertThat(second.homeDir()).isEqualTo(first.homeDir());
        assertThat(second.workspaceDir()).endsWith("conv-" + CONVERSATION_ID);
        assertThat(resumeState).exists();
        assertThat(Files.readString(resumeState)).isEqualTo("session state");
    }

    @Test
    void rebuildsTheContractFilesAfterTheVolumeWasWipedTest() throws Exception {
        RmqctlWorkspace workspace = availableWorkspace();
        RmqctlWorkspace.Preparation first = workspace.prepare(CONVERSATION_ID, INSTANCE_ID).orElseThrow();
        // A container restart wipes /tmp but not the runtime_session_id column, which is exactly the
        // situation ResumeRecovery exists for; the workspace itself must simply come back.
        Files.deleteIfExists(Path.of(first.mcpConfigPath()));
        Files.deleteIfExists(Path.of(first.rmqctlConfigPath()));

        RmqctlWorkspace.Preparation second = workspace.prepare(CONVERSATION_ID, INSTANCE_ID).orElseThrow();

        assertThat(Path.of(second.mcpConfigPath())).exists();
        assertThat(Path.of(second.rmqctlConfigPath())).exists();
        assertThat(Files.readString(Path.of(second.rmqctlConfigPath())))
                .isEqualTo(Files.readString(Path.of(first.rmqctlConfigPath())));
    }

    @Test
    void materialisesTheSystemPromptFromTheClasspathTest() throws Exception {
        RmqctlWorkspace.Preparation preparation = prepared();

        String written = Files.readString(Path.of(preparation.systemPromptPath()), StandardCharsets.UTF_8);
        String onClasspath;
        try (InputStream in = RmqctlWorkspaceTest.class.getResourceAsStream("/prompts/agent-system-prompt.txt")) {
            assertThat(in).as("the agent system prompt must ship in the jar").isNotNull();
            onClasspath = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(written).isEqualTo(onClasspath.replace("{instanceId}", INSTANCE_ID));
        // The bound instance is spelled out so the agent never probes candidate ids; the raw
        // placeholder must not survive into a prepared workspace.
        assertThat(written).contains(INSTANCE_ID).doesNotContain("{instanceId}");
        assertThat(written).contains("rmq.instance.capabilities").contains("confirm_token");
        assertThat(preparation.systemPromptPath()).endsWith("agent-system-prompt.txt");
    }

    @Test
    void degradesToPlainChatWhenTheRmqctlBinaryIsMissingTest() {
        RmqctlWorkspace workspace = workspace(false, 8888);

        assertThat(workspace.prepare(CONVERSATION_ID, INSTANCE_ID)).isEmpty();

        assertThat(probeConsulted).isTrue();
        // Degrading must not leave a half-written workspace behind: an empty conv-<id> directory with
        // no mcp.json is what makes a later "why are there no tools?" impossible to answer.
        assertThat(root.resolve("conv-" + CONVERSATION_ID)).doesNotExist();
        verifyNoInteractions(credentials);
        assertThat(RmqctlWorkspace.degradedNotice())
                .isEqualTo(new TimelineEvent.Notice(AgentEventProjector.LEVEL_WARN, notice("degraded")));
    }

    @Test
    void degradesWithoutEvenProbingWhenRmqctlIsDisabledTest() {
        properties.setRmqctlEnabled(false);
        RmqctlWorkspace workspace = workspace(true, 8888);

        assertThat(workspace.prepare(CONVERSATION_ID, INSTANCE_ID)).isEmpty();

        assertThat(probeConsulted).as("a disabled transport must not cost a process fork").isFalse();
        assertThat(root.resolve("conv-" + CONVERSATION_ID)).doesNotExist();
        verifyNoInteractions(credentials);
    }

    @Test
    void degradesWhenTheConversationIsNotBoundToAnInstanceTest() {
        RmqctlWorkspace workspace = availableWorkspace();

        assertThat(workspace.prepare(CONVERSATION_ID, null)).isEmpty();
        assertThat(workspace.prepare(CONVERSATION_ID, "   ")).isEmpty();

        assertThat(probeConsulted).as("an unbound conversation needs no probe either").isFalse();
        assertThat(root.resolve("conv-" + CONVERSATION_ID)).doesNotExist();
        verifyNoInteractions(credentials);
        assertThat(RmqctlWorkspace.unboundNotice())
                .isEqualTo(new TimelineEvent.Notice(AgentEventProjector.LEVEL_WARN, notice("unbound")));
        assertThat(RmqctlWorkspace.DEGRADED_NOTICE_MESSAGE).isNotEqualTo(RmqctlWorkspace.UNBOUND_NOTICE_MESSAGE);
    }

    @Test
    void failsLoudlyWhenTheInstanceCredentialCannotBeResolvedTest() {
        // A configuration problem is not a capability problem: the operator asked for tools on a
        // specific instance, so this fails loudly instead of silently degrading to chat.
        when(credentials.resolveByName(INSTANCE_ID))
                .thenThrow(new BusinessException(422, "Admin credential is not configured for instance: instance-a"));

        assertThatThrownBy(() -> availableWorkspace().prepare(CONVERSATION_ID, INSTANCE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Admin credential is not configured");
    }

    @Test
    void derivesTheLoopbackCallbackUrlFromTheServerPortTest() throws Exception {
        RmqctlWorkspace.Preparation preparation = workspace(true, 9999)
                .prepare(CONVERSATION_ID, INSTANCE_ID).orElseThrow();

        assertThat(Files.readString(Path.of(preparation.rmqctlConfigPath())))
                .contains("server: \"http://127.0.0.1:9999\"");
    }

    @Test
    void acceptsLoopbackAndHttpsCallbackOverridesTest() throws Exception {
        properties.setRmqctlServerUrl("http://localhost:8888/");
        assertThat(serverUrlOf(prepared())).isEqualTo("http://localhost:8888");

        properties.setRmqctlServerUrl("https://studio.example.com");
        assertThat(serverUrlOf(prepared())).isEqualTo("https://studio.example.com");

        properties.setRmqctlServerUrl("http://127.0.0.1:6789");
        assertThat(serverUrlOf(prepared())).isEqualTo("http://127.0.0.1:6789");
    }

    @Test
    void rejectsNonLoopbackPlainHttpCallbackOverridesTest() {
        // rmqctl/internal/studio/client.go allows plain HTTP only for a loopback host, so accepting
        // these here would move the failure into a tool call the agent cannot explain.
        for (String url : List.of("http://10.0.0.5:8888", "http://studio.example.com", "ftp://127.0.0.1")) {
            properties.setRmqctlServerUrl(url);
            assertThatThrownBy(() -> availableWorkspace().prepare(CONVERSATION_ID, INSTANCE_ID))
                    .as(url)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(url)
                    .hasMessageContaining("loopback");
        }
    }

    @Test
    void rejectsCallbackOverridesCarryingAQueryOrCredentialsTest() {
        for (String url : List.of("http://127.0.0.1:8888/?instance=x", "http://user:pw@127.0.0.1:8888")) {
            properties.setRmqctlServerUrl(url);
            assertThatThrownBy(() -> availableWorkspace().prepare(CONVERSATION_ID, INSTANCE_ID))
                    .as(url)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("must not contain user info, a query or a fragment");
        }
    }

    @Test
    void rejectsAMalformedCallbackOverrideTest() {
        properties.setRmqctlServerUrl("http://127.0.0.1:8888/ not a url");

        assertThatThrownBy(() -> availableWorkspace().prepare(CONVERSATION_ID, INSTANCE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not a valid URL");
    }

    @Test
    void rejectsAnUndeterminableServerPortTest() {
        assertThatThrownBy(() -> workspace(true, 0).prepare(CONVERSATION_ID, INSTANCE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("studio.ai.conversation.rmqctl-server-url");
    }

    @Test
    void rejectsAnUnusableWorkspaceRootTest() {
        properties.setWorkspaceDir("   ");

        assertThatThrownBy(() -> availableWorkspace().prepare(CONVERSATION_ID, INSTANCE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("workspace-dir");
        assertThatThrownBy(() -> availableWorkspace().workspaceDir(0))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * The whole point of {@code goDuration}: {@code Duration.toString()} would emit {@code PT60S},
     * which {@code time.ParseDuration} rejects, and a hand-rolled formatter would emit {@code 60s},
     * which differs from the {@code 1m0s} that {@code rmqctl mcp config} prints. Every expected value
     * here was measured from the Go 1.27.1 runtime, not derived from the port.
     */
    @Test
    void formatsTheTimeoutExactlyLikeGoDurationStringTest() {
        // No "0s" case here on purpose: a zero or negative configured timeout is replaced by the 60s
        // default before formatting, so it can never reach the formatter (asserted at the bottom).
        assertThat(RmqctlWorkspace.goDuration(Duration.ofNanos(1))).isEqualTo("1ns");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofNanos(999))).isEqualTo("999ns");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofNanos(1_000))).isEqualTo("1\u00B5s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofNanos(1_500))).isEqualTo("1.5\u00B5s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofNanos(999_999))).isEqualTo("999.999\u00B5s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofNanos(1_000_000))).isEqualTo("1ms");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofNanos(1_500_000))).isEqualTo("1.5ms");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofMillis(100))).isEqualTo("100ms");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofNanos(999_999_999))).isEqualTo("999.999999ms");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofSeconds(1))).isEqualTo("1s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofMillis(1_500))).isEqualTo("1.5s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofSeconds(30))).isEqualTo("30s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofMillis(59_999))).isEqualTo("59.999s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofSeconds(60))).isEqualTo("1m0s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofMillis(61_500))).isEqualTo("1m1.5s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofSeconds(90))).isEqualTo("1m30s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofSeconds(600))).isEqualTo("10m0s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofSeconds(3_600))).isEqualTo("1h0m0s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofSeconds(3_661))).isEqualTo("1h1m1s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofMillis(3_661_500))).isEqualTo("1h1m1.5s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofSeconds(90_061))).isEqualTo("25h1m1s");
        // A missing or non-positive configured timeout falls back to the 60s default.
        assertThat(RmqctlWorkspace.goDuration(null)).isEqualTo("1m0s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ZERO)).isEqualTo("1m0s");
        assertThat(RmqctlWorkspace.goDuration(Duration.ofSeconds(-5))).isEqualTo("1m0s");
    }

    @Test
    void carriesTheConfiguredTimeoutIntoTheMcpSnippetTest() throws Exception {
        properties.setRmqctlTimeout(Duration.ofMillis(1_500));

        RmqctlWorkspace.Preparation preparation = prepared();

        assertThat(Files.readString(Path.of(preparation.mcpConfigPath()))).contains("\"--timeout\",\"1.5s\"");
    }

    @Test
    void acceptsLoopbackHostsExactlyLikeRmqctlDoesTest() {
        assertThat(RmqctlWorkspace.isLoopbackHost("localhost")).isTrue();
        assertThat(RmqctlWorkspace.isLoopbackHost("LocalHost")).isTrue();
        assertThat(RmqctlWorkspace.isLoopbackHost("127.0.0.1")).isTrue();
        assertThat(RmqctlWorkspace.isLoopbackHost("127.1.2.3")).isTrue();
        assertThat(RmqctlWorkspace.isLoopbackHost("::1")).isTrue();
        assertThat(RmqctlWorkspace.isLoopbackHost("[::1]")).isTrue();
        // Go's net.ParseIP is literal-only, so a hostname that resolves to loopback is rejected too.
        assertThat(RmqctlWorkspace.isLoopbackHost("10.0.0.1")).isFalse();
        assertThat(RmqctlWorkspace.isLoopbackHost("loopback.example.com")).isFalse();
        assertThat(RmqctlWorkspace.isLoopbackHost("127.0.0.256")).isFalse();
        assertThat(RmqctlWorkspace.isLoopbackHost(null)).isFalse();
        assertThat(RmqctlWorkspace.isLoopbackHost("  ")).isFalse();
    }

    @Test
    void deleteRemovesTheWholeWorkspaceAndNeverThrowsTest() throws Exception {
        RmqctlWorkspace workspace = availableWorkspace();
        RmqctlWorkspace.Preparation preparation = workspace.prepare(CONVERSATION_ID, INSTANCE_ID).orElseThrow();
        Path nested = Path.of(preparation.homeDir()).resolve(".claude/projects/-ws");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("session.jsonl"), "state");

        assertThat(workspace.delete(CONVERSATION_ID)).isTrue();
        assertThat(Path.of(preparation.workspaceDir())).doesNotExist();
        // Idempotent, because DELETE /conversations/{id} may be retried by the UI.
        assertThat(workspace.delete(CONVERSATION_ID)).isFalse();
        assertThat(workspace.delete(4242L)).isFalse();
    }

    @Test
    void reusesTheSameWorkspaceDirectoryNamingForEveryCallerTest() {
        RmqctlWorkspace workspace = availableWorkspace();

        assertThat(workspace.workspaceRoot()).isEqualTo(root);
        assertThat(workspace.workspaceDir(CONVERSATION_ID)).isEqualTo(root.resolve("conv-42"));
        assertThat(workspace.workspaceDir(7L)).isNotEqualTo(workspace.workspaceDir(8L));
    }

    /**
     * The resume-failure recovery lives beside the workspace tests on purpose: the workspace is what
     * makes {@code --resume} work, so its loss is the same story, and one class covers the whole
     * HOME/cwd/resume behaviour.
     */
    @Nested
    class ResumeFailureRecovery {

        /** Measured stderr of a stale --resume: exactly one line, exit code 1. */
        private static final String MEASURED_STDERR =
                "No conversation found with session ID: 3f0a1c22-0000-0000-0000-000000000000\n";

        @Test
        void retriesOnTheMeasuredSessionNotFoundStderrTest() {
            assertThat(ResumeRecovery.isLostResumeSignal(1, null, MEASURED_STDERR)).isTrue();
            assertThat(ResumeRecovery.shouldRetryWithoutResume(true, 1, null, MEASURED_STDERR)).isTrue();
        }

        @Test
        void retriesOnTheErrorDuringExecutionResultFrameTest() {
            // The same run also emits a single result frame with this subtype and num_turns 0; either
            // signal alone is enough, because either one may change in a CLI upgrade.
            assertThat(ResumeRecovery.isLostResumeSignal(1, "error_during_execution", "")).isTrue();
            assertThat(ResumeRecovery.isLostResumeSignal(1, "error_during_execution", null)).isTrue();
        }

        @Test
        void doesNotRetryWhenTheRunSucceededTest() {
            assertThat(ResumeRecovery.isLostResumeSignal(0, "error_during_execution", MEASURED_STDERR)).isFalse();
            assertThat(ResumeRecovery.isLostResumeSignal(0, null, MEASURED_STDERR)).isFalse();
        }

        @Test
        void doesNotRetryAnUnrelatedFailureTest() {
            assertThat(ResumeRecovery.isLostResumeSignal(1, "error_max_turns", "gateway exploded")).isFalse();
            assertThat(ResumeRecovery.isLostResumeSignal(1, null, "")).isFalse();
            assertThat(ResumeRecovery.isLostResumeSignal(1, null, null)).isFalse();
            // The prefix must be at the start: a message that merely mentions the text is not a
            // session-not-found failure.
            assertThat(ResumeRecovery.isLostResumeSignal(1, null,
                    "warning: something else\n" + MEASURED_STDERR)).isFalse();
        }

        @Test
        void doesNotRetryARunThatNeverResumedAnythingTest() {
            assertThat(ResumeRecovery.shouldRetryWithoutResume(false, 1, null, MEASURED_STDERR)).isFalse();
            assertThat(ResumeRecovery.shouldRetryWithoutResume(false, 1, "error_during_execution", "")).isFalse();
        }
    }

    private static String serverUrlOf(RmqctlWorkspace.Preparation preparation) throws IOException {
        for (String line : Files.readAllLines(Path.of(preparation.rmqctlConfigPath()))) {
            if (line.trim().startsWith("server:")) {
                return line.substring(line.indexOf('"') + 1, line.lastIndexOf('"'));
            }
        }
        throw new AssertionError("rmqctl.yaml has no server line");
    }

    /** Every secret found in any file below the workspace, as "relative path" entries. */
    private static List<String> workspaceFilesMentioningSecrets(String workspaceDir) throws IOException {
        List<String> leaks = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Path.of(workspaceDir))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                if (content.contains(ACCESS_KEY) || content.contains(SECRET_KEY)) {
                    leaks.add(path.getFileName().toString());
                }
            }
        }
        return leaks;
    }

    private static String permissions(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    /**
     * The readable notice texts. They live in a resource because checkstyle rejects non-ASCII
     * characters in Java sources, and a test that repeated the production unicode escapes would
     * assert nothing. The file is a {@code .txt} parsed with {@link Properties} rather than a
     * {@code .properties}, because the checkstyle plugin also scans property resources and would
     * reject the very characters this file exists to hold.
     */
    private static String notice(String key) {
        Properties notices = new Properties();
        try (InputStream in = RmqctlWorkspaceTest.class.getResourceAsStream("/ai/agent-notices.txt")) {
            assertThat(in).as("agent-notices.txt must be on the test classpath").isNotNull();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                notices.load(reader);
            }
        } catch (IOException exception) {
            throw new AssertionError("could not read agent-notices.txt", exception);
        }
        return notices.getProperty(key);
    }
}
