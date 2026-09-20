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

import lombok.Builder;
import lombok.ToString;
import lombok.Value;

import java.time.Duration;
import java.util.Map;

/**
 * Everything one hosted-agent run needs beyond the LLM configuration: the prompt, the already
 * resolved model, the resume and MCP wiring, the working directory, the cancellation handle and the
 * wall-clock budget.
 *
 * <p>Deliberately immutable and deliberately provider-neutral. A provider must be able to spawn its
 * subprocess from this object alone, which is why {@code model} is <em>pre-resolved</em> (the caller
 * already picked the per-request override or fell back to the stored configuration) and why
 * {@code workspaceDir} doubles as the child's working directory.
 *
 * <h2>Why the working directory matters</h2>
 * {@code claude --resume <id>} resolves session state under
 * {@code <HOME>/.claude/projects/<cwd-hash>/<session-id>.jsonl}. If the cwd changes between turns the
 * hash changes and resume silently finds nothing, so the workspace must be stable per conversation
 * rather than per run.
 *
 * <h2>Why {@code extraEnv} bypasses the CLI environment allow-list</h2>
 * {@code CliProcessEnvironment} copies an allow-list out of the server process and then applies
 * provider-supplied entries unconditionally, because credentials and the rmqctl handshake
 * ({@code RMQ_AI_ACCESS_KEY} / {@code RMQ_AI_SECRET_KEY}) are per-run values that exist nowhere in
 * the parent environment. They travel in the child environment only: never in argv, never in a
 * workspace file, never in a database column.
 */
@Value
@Builder(toBuilder = true)
public class AgentStreamOptions {

    /** The user message for this turn, already enhanced if enhancement was requested. */
    String prompt;

    /**
     * The model to run, pre-resolved by the caller (per-request override or the stored
     * configuration). Providers must pass it through explicitly: {@code claude} without
     * {@code --model} picks its own default and an OpenAI-compatible gateway rejects that name.
     */
    String model;

    /** Runtime session id to resume, or null for the first turn of a conversation. */
    String resumeSessionId;

    /**
     * Path of the generated {@code mcp.json}. Null means "no MCP tools this run", i.e. the agent
     * degrades to plain chat and the CLI is started without {@code --mcp-config}.
     */
    String mcpConfigPath;

    /**
     * Path of the system prompt to append. The provider reads the file and passes its text, because
     * {@code --append-system-prompt} takes text rather than a path. Null or unreadable means no
     * extra system prompt.
     */
    String systemPromptPath;

    /** Per-conversation workspace, also used as the child process working directory. */
    String workspaceDir;

    /** Studio instance this conversation is bound to; informational for the provider. */
    String instanceId;

    /**
     * Extra child environment entries, applied on top of the provider's own. Null is normalised to
     * an empty map by {@link #getExtraEnv()}.
     *
     * <p>Excluded from {@code toString()} because this is where the per-run rmqctl credential travels
     * ({@code RMQ_AI_ACCESS_KEY} / {@code RMQ_AI_SECRET_KEY}), mirroring {@code LlmConfigVO.apiKey}.
     * A log line that interpolates the options must not become a credential leak.
     */
    @ToString.Exclude
    @Builder.Default
    Map<String, String> extraEnv = Map.of();

    /**
     * Cancellation handle. The provider attaches the subprocess as soon as it is started so a stop
     * can kill the real process tree instead of relying on a thread interrupt landing in the right
     * place. Null in tests and in any path that cannot be stopped.
     */
    AgentProcessSink processSink;

    /** Wall-clock budget for the whole run. Null means the provider's own default. */
    Duration timeout;

    /** Never null, so callers can iterate without a guard. */
    public Map<String, String> getExtraEnv() {
        return extraEnv == null ? Map.of() : extraEnv;
    }

    /** The run budget, or {@code fallback} when the caller did not set one. */
    public Duration timeoutOr(Duration fallback) {
        return timeout == null ? fallback : timeout;
    }

    /**
     * The narrowest possible view of a run's cancellation handle: "here is the subprocess, own its
     * lifecycle".
     *
     * <p>This interface exists because the real handle ({@code AgentRunHandle}, which also carries
     * the abort reason, the stop grace period and the observer set) is built on top of the run
     * registry and cannot be referenced from here without a circular dependency between the SPI and
     * the run lifecycle. {@code AgentRunHandle} is expected to implement this interface; the provider
     * only ever calls {@link #attachProcess(Process)} and stays agnostic about the rest.
     */
    public interface AgentProcessSink {

        /**
         * Hands the freshly started subprocess to the run handle. Called exactly once per run,
         * immediately after {@code ProcessBuilder.start()} and before any output is read, so a stop
         * that races the start still finds the process.
         */
        void attachProcess(Process process);
    }
}
