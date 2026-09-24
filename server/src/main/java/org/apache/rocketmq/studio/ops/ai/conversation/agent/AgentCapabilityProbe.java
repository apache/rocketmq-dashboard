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

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.ops.ai.CliProcessEnvironment;
import org.apache.rocketmq.studio.ops.ai.conversation.AiConversationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Answers "what can this server actually run?" for {@code GET /api/ai/agent-capabilities}.
 *
 * <p>Three {@link CliBinaryProbe} lookups plus two configuration flags. The probe forks
 * {@code sh -c "command -v <binary>"} with a five second budget, so answering a UI question with it
 * on every page load would be three subprocesses per request — which is why the three results are
 * cached as one unit for {@value #CACHE_TTL_SECONDS} s. A cached answer can be at most that stale,
 * and the alternative (probing per request) is a fork bomb one browser refresh away.
 *
 * <p>{@code rmqctl} reports unavailable both when the binary is missing and when
 * {@code studio.ai.conversation.rmqctl-enabled=false}: the question the UI asks is "will this
 * conversation have RocketMQ tools?", and {@link RmqctlWorkspace#prepare} answers no in both cases.
 * Reporting a present-but-disabled binary as available would make the composer offer an instance
 * picker for tools that are switched off, which is the mystery this endpoint exists to remove.
 *
 * <p>The two flags are read once at construction rather than per request. Both are startup
 * configuration: {@code spring.ai.mcp.server.enabled} decides whether the MCP endpoint bean exists
 * at all, and {@code studio.ai.allow-l3-tools} is a constructor argument of
 * {@code ToolMutationFilter}, so neither can change without a restart, and pretending otherwise here
 * would only add a property lookup per call.
 */
@Slf4j
@Component
public class AgentCapabilityProbe {

    /** {@code ClaudeCodeAgentProvider.BINARY}; private there, so named again here. */
    static final String CLAUDE_BINARY = "claude";

    /** {@code QoderAgentProvider#binaryName()}; private there, so named again here. */
    static final String QODER_BINARY = "qodercli";

    private static final int CACHE_TTL_SECONDS = 60;
    private static final Duration CACHE_TTL = Duration.ofSeconds(CACHE_TTL_SECONDS);

    private final CliBinaryProbe binaryProbe;
    private final AiConversationProperties properties;
    private final boolean mcpEnabled;
    private final boolean l3ToolsAllowed;
    private final Clock clock;
    private final AtomicReference<Binaries> cached = new AtomicReference<>();

    @Autowired
    public AgentCapabilityProbe(CliProcessEnvironment processEnvironment,
                               AiConversationProperties properties,
                               @Value("${spring.ai.mcp.server.enabled:false}") boolean mcpEnabled,
                               @Value("${studio.ai.allow-l3-tools:false}") boolean l3ToolsAllowed) {
        this(new CliBinaryProbe(processEnvironment), properties, mcpEnabled, l3ToolsAllowed, Clock.systemUTC());
    }

    /** Visible for tests: a probe that does not fork, and a clock that makes the cache window assertable. */
    AgentCapabilityProbe(CliBinaryProbe binaryProbe,
                         AiConversationProperties properties,
                         boolean mcpEnabled,
                         boolean l3ToolsAllowed,
                         Clock clock) {
        this.binaryProbe = binaryProbe;
        this.properties = properties;
        this.mcpEnabled = mcpEnabled;
        this.l3ToolsAllowed = l3ToolsAllowed;
        this.clock = clock;
    }

    /** The three CLI probes, cached together so one request costs at most one round of forks. */
    public Binaries binaries() {
        Instant now = clock.instant();
        Binaries current = cached.get();
        if (current != null && Duration.between(current.probedAt(), now).compareTo(CACHE_TTL) < 0) {
            return current;
        }
        Binaries probed = new Binaries(
                properties.isRmqctlEnabled() && binaryProbe.isAvailable(RmqctlWorkspace.RMQCTL_BINARY),
                binaryProbe.isAvailable(CLAUDE_BINARY),
                binaryProbe.isAvailable(QODER_BINARY),
                now);
        // Last writer wins and every writer wrote a fresh probe, so a racing refresh is harmless.
        cached.set(probed);
        log.debug("probed agent binaries: rmqctl={} claude={} qoder={} (cached for {}s)",
                probed.rmqctl(), probed.claude(), probed.qoder(), CACHE_TTL_SECONDS);
        return probed;
    }

    /** {@code spring.ai.mcp.server.enabled}: without the MCP server no agent tool can execute. */
    public boolean mcpEnabled() {
        return mcpEnabled;
    }

    /** {@code studio.ai.allow-l3-tools}: whether destructive tools are reachable at all. */
    public boolean l3ToolsAllowed() {
        return l3ToolsAllowed;
    }

    /**
     * One cached probe round.
     *
     * @param rmqctl the {@code rmqctl} MCP transport is present <em>and</em> enabled
     * @param claude the {@code claude} CLI is present
     * @param qoder the {@code qodercli} CLI is present
     * @param probedAt when this round ran, so the cache window is observable in a test
     */
    public record Binaries(boolean rmqctl, boolean claude, boolean qoder, Instant probedAt) {
    }
}
