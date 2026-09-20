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
package org.apache.rocketmq.studio.ops.ai.conversation.event;

import java.util.List;

/**
 * Provider-neutral output of an agent stream parser. This is the SPI vocabulary only: it is NEVER
 * serialised (hence no Jackson annotations anywhere in this file) and it never reaches the browser
 * or the database as-is. {@link AgentEventProjector} translates each event into an ephemeral
 * {@link LiveEvent} and/or a persisted {@link TimelineEvent}.
 *
 * <p>Keeping this vocabulary separate is what lets a second provider (qoder, a future HTTP agent)
 * be added without touching the wire contract: the parser normalises, the projector projects.
 *
 * <p>Two shapes deserve a word:
 * <ul>
 *   <li>{@link ToolStart} carries no input. Tool arguments arrive as
 *       {@code input_json_delta} fragments and are only complete at
 *       {@link ToolInputComplete}, which is why that event exists separately.</li>
 *   <li>{@link ToolDone#durationMs()} is nullable because the provider reports none. The projector
 *       timestamps {@link ToolStart} and computes the duration itself.</li>
 * </ul>
 */
public sealed interface AgentEvent
        permits AgentEvent.TextDelta, AgentEvent.ThinkingDelta, AgentEvent.ToolStart,
                AgentEvent.ToolInputComplete, AgentEvent.ToolDone, AgentEvent.ResultMeta,
                AgentEvent.InitMeta, AgentEvent.ProviderNotice, AgentEvent.UnhandledUpstream {

    /**
     * A fragment of assistant prose. Emitted live as {@code text_delta}; the persisted
     * {@code text} event is produced by coalescing many deltas at a flush boundary.
     */
    record TextDelta(String content) implements AgentEvent {
    }

    /**
     * A fragment of reasoning. {@code source} says whether the model is thinking or whether this is
     * Studio's prompt-enhancement rewrite; the two must never be merged into one block.
     */
    record ThinkingDelta(String content, ThinkingSource source) implements AgentEvent {
    }

    /**
     * The provider announced a tool call. Arguments are not known yet, so the projector only
     * records {@code tcId -> tool} plus a start timestamp and emits nothing.
     */
    record ToolStart(String tcId, String tool) implements AgentEvent {
    }

    /** The tool arguments finished assembling. Now the call can be shown with its full input. */
    record ToolInputComplete(String tcId, String tool, Object input) implements AgentEvent {
    }

    /**
     * The tool finished. {@code tool} may be null when the provider's result frame only carries the
     * call id, in which case the projector labels it from the remembered {@link ToolStart};
     * {@code durationMs} may be null for the same reason.
     */
    record ToolDone(String tcId, String tool, String output, boolean success, Long durationMs,
                    String error) implements AgentEvent {
    }

    /**
     * The provider's terminal {@code result} frame. {@code runtimeSessionId} and the token counts
     * belong on the run row (usage accounting and {@code --resume}), not on an event;
     * {@code subtype} is what decides success versus failure.
     */
    record ResultMeta(String runtimeSessionId, Long durationMs, Integer inputTokens,
                      Integer outputTokens, String subtype) implements AgentEvent {
    }

    /** The provider's {@code system/init} frame: session id, connected MCP servers, visible tools. */
    record InitMeta(String runtimeSessionId, List<String> connectedMcpServers,
                    List<String> availableTools) implements AgentEvent {
    }

    /** A provider-side diagnostic worth surfacing, with the provider's own level. */
    record ProviderNotice(String level, String message) implements AgentEvent {
    }

    /**
     * An upstream frame shape the parser does not model. Surfaced as a warning instead of being
     * dropped silently, so missing coverage is visible rather than looking like a quiet model.
     */
    record UnhandledUpstream(String upstreamType) implements AgentEvent {
    }
}
