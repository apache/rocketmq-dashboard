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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * Coalesced payload of one {@code rmq_ai_event} row: the conversation timeline as it is stored and
 * replayed. Where {@link LiveEvent} is delta-oriented, this is block-oriented — a whole assistant
 * paragraph is one {@code text} event, not a few hundred deltas.
 *
 * <p>Name shifts against the live vocabulary are intentional and total:
 * {@code text_delta -> text}, {@code tool_start -> tool_use}, {@code tool_done -> tool_result}.
 * {@code thinking} keeps its name but renames {@code content} to {@code text}, because on this side
 * it is a finished block rather than a fragment. Both vocabularies reduce into the same render
 * target, which is the whole point: the UI must not be able to tell a live stream from a replay.
 *
 * <p>{@code run_started} has no counterpart here — the conversation and run rows already carry that
 * fact. {@code run_status} does, so a reloaded conversation can show that it was stopped without
 * joining {@code rmq_ai_run}.
 *
 * <p>{@code @JsonSubTypes} is spelled out for the same reason as on {@link LiveEvent}: Jackson 2 is
 * still on the classpath and cannot discover sealed subtypes by itself.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TimelineEvent.User.class, name = "user"),
    @JsonSubTypes.Type(value = TimelineEvent.Thinking.class, name = "thinking"),
    @JsonSubTypes.Type(value = TimelineEvent.Text.class, name = "text"),
    @JsonSubTypes.Type(value = TimelineEvent.ToolUse.class, name = "tool_use"),
    @JsonSubTypes.Type(value = TimelineEvent.ToolResult.class, name = "tool_result"),
    @JsonSubTypes.Type(value = TimelineEvent.Notice.class, name = "notice"),
    @JsonSubTypes.Type(value = TimelineEvent.Error.class, name = "error"),
    @JsonSubTypes.Type(value = TimelineEvent.RunStatus.class, name = "run_status")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface TimelineEvent
        permits TimelineEvent.User, TimelineEvent.Thinking, TimelineEvent.Text, TimelineEvent.ToolUse,
                TimelineEvent.ToolResult, TimelineEvent.Notice, TimelineEvent.Error,
                TimelineEvent.RunStatus {

    /**
     * The user's turn. {@code enhancedPrompt} is present only when Studio rewrote the prompt before
     * handing it to the agent, so the UI can show both what was typed and what was actually asked.
     */
    @JsonTypeName("user")
    record User(String text, String enhancedPrompt) implements TimelineEvent {
    }

    /** A finished reasoning block. {@code source} keeps model thinking apart from prompt rewriting. */
    @JsonTypeName("thinking")
    record Thinking(String text, ThinkingSource source) implements TimelineEvent {
    }

    /** A finished assistant text block, coalesced from many live deltas. */
    @JsonTypeName("text")
    record Text(String text) implements TimelineEvent {
    }

    /** A tool call with its complete input. Counterpart of the live {@code tool_start}. */
    @JsonTypeName("tool_use")
    record ToolUse(String tcId, String tool, Object input) implements TimelineEvent {
    }

    /**
     * A tool result. {@code output} has inline base64 stripped and is capped, {@code outputBytes}
     * is the true size of the sanitised output and {@code truncated} says whether it was cut, so a
     * replayed conversation shows exactly what the live stream showed.
     */
    @JsonTypeName("tool_result")
    record ToolResult(String tcId, String tool, String output, Integer outputBytes, boolean truncated,
                      boolean success, Long durationMs, String error) implements TimelineEvent {
    }

    /** Non-fatal diagnostic worth keeping in history. {@code level} is {@code info}, {@code warn} or {@code error}. */
    @JsonTypeName("notice")
    record Notice(String level, String message) implements TimelineEvent {
    }

    /** The run failed. {@code code} is stable and machine-readable; {@code hint} is optional. */
    @JsonTypeName("error")
    record Error(String code, String message, String hint) implements TimelineEvent {
    }

    /**
     * Terminal state of the run, denormalised into the timeline. {@code reason} explains a
     * non-success terminal state and is absent for {@link RunStatus#COMPLETED}.
     */
    @JsonTypeName("run_status")
    record RunStatus(org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus status,
                     StopReason reason) implements TimelineEvent {
    }
}
