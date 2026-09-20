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
 * Ephemeral SSE payload of a live run. Delta-oriented and deliberately lossy: {@code run_started}
 * and {@code run_finished} exist only here (the run row is the truth about a run), and assistant
 * prose arrives as many {@code text_delta} frames instead of one {@code text} block.
 *
 * <p>Wire shape is {@code {"type": "<name>", ...}}. The names come from
 * {@code server/src/test/resources/ai/ai-event-contract.json}, which both this type and the web
 * client are tested against; adding, renaming or dropping a subtype means editing that file too.
 *
 * <p>{@code @JsonSubTypes} is spelled out even though this interface is sealed and Jackson 3 can
 * read the permitted subclasses itself: Jackson 2 is still on the classpath (see
 * {@code LegacyJackson2Config}) and cannot, so without the explicit list every Jackson 2
 * deserialisation of a stored or replayed event would fail with "known type ids = []".
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = LiveEvent.RunStarted.class, name = "run_started"),
    @JsonSubTypes.Type(value = LiveEvent.TextDelta.class, name = "text_delta"),
    @JsonSubTypes.Type(value = LiveEvent.Thinking.class, name = "thinking"),
    @JsonSubTypes.Type(value = LiveEvent.ToolStart.class, name = "tool_start"),
    @JsonSubTypes.Type(value = LiveEvent.ToolDone.class, name = "tool_done"),
    @JsonSubTypes.Type(value = LiveEvent.Notice.class, name = "notice"),
    @JsonSubTypes.Type(value = LiveEvent.Error.class, name = "error"),
    @JsonSubTypes.Type(value = LiveEvent.RunFinished.class, name = "run_finished")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface LiveEvent
        permits LiveEvent.RunStarted, LiveEvent.TextDelta, LiveEvent.Thinking, LiveEvent.ToolStart,
                LiveEvent.ToolDone, LiveEvent.Notice, LiveEvent.Error, LiveEvent.RunFinished {

    /** First frame of a stream. Lets the browser bind the SSE connection to a run and a turn. */
    @JsonTypeName("run_started")
    record RunStarted(Long runId, Long conversationId, String title, Integer turn) implements LiveEvent {
    }

    /** One fragment of assistant prose. Concatenate in arrival order. */
    @JsonTypeName("text_delta")
    record TextDelta(String content) implements LiveEvent {
    }

    /** One fragment of reasoning. {@code source} separates model thinking from prompt rewriting. */
    @JsonTypeName("thinking")
    record Thinking(String content, ThinkingSource source) implements LiveEvent {
    }

    /**
     * A tool call about to execute, with its complete input. Emitted when the arguments finished
     * assembling, not when the provider first announced the call, so the card never flickers
     * through an input-less state.
     */
    @JsonTypeName("tool_start")
    record ToolStart(String tcId, String tool, Object input) implements LiveEvent {
    }

    /**
     * A tool call finished. {@code outputBytes} is the true size of the (sanitised) output and
     * {@code truncated} says whether {@code output} was cut to the projector's ceiling, so the UI
     * can say "showing the first 32 KiB of 4.1 MB" instead of implying completeness.
     */
    @JsonTypeName("tool_done")
    record ToolDone(String tcId, String tool, String output, Integer outputBytes, boolean truncated,
                    Long durationMs, boolean success, String error) implements LiveEvent {
    }

    /** Non-fatal, user-visible diagnostic. {@code level} is {@code info}, {@code warn} or {@code error}. */
    @JsonTypeName("notice")
    record Notice(String level, String message) implements LiveEvent {
    }

    /** Fatal for this run. {@code code} is stable and machine-readable; {@code hint} is optional. */
    @JsonTypeName("error")
    record Error(String code, String message, String hint) implements LiveEvent {
    }

    /** Last frame of a stream. The run row remains the authority; this only releases the UI. */
    @JsonTypeName("run_finished")
    record RunFinished(Long runId, RunStatus status, Long durationMs) implements LiveEvent {
    }
}
