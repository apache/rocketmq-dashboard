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
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.apache.rocketmq.studio.ops.ai.conversation.event.ThinkingSource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns one {@code claude --output-format stream-json --include-partial-messages} run into the
 * provider-neutral {@link AgentEvent} vocabulary. One instance per run: it carries the assembly
 * buffers and the deduplication sets that a stateless line-by-line mapping cannot recover. Not
 * thread-safe, which is fine because a run's stdout is drained by exactly one thread.
 *
 * <p>Everything in here was derived from a real capture
 * ({@code src/test/resources/ai/claude-stream-capture.jsonl}, two runs: one with two successful MCP
 * tool calls, one {@code --resume} run with none) rather than from the Anthropic streaming docs, and
 * {@code ClaudeCodeStreamParserTest} replays that capture line by line. Where the capture and the
 * docs could disagree, the capture won.
 *
 * <h2>Disposition table</h2>
 * <table border="1">
 * <caption>Every upstream shape seen in the capture, and what it becomes</caption>
 * <tr><th>Upstream shape</th><th>Emitted</th></tr>
 * <tr><td>{@code type=system, subtype=init}</td>
 *     <td>{@link AgentEvent.InitMeta} with the session id, the <em>connected</em> MCP servers and
 *     the tool names the CLI can see. This is the first frame of a run, so the session id is known
 *     long before {@code result} — a run that is stopped mid-flight still leaves something to
 *     {@code --resume}.</td></tr>
 * <tr><td>{@code type=system, subtype=status}</td><td>nothing. Claude Code's own progress telemetry
 *     ({@code {"status":"requesting"}}), not an Anthropic stream event.</td></tr>
 * <tr><td>{@code type=system, subtype=thinking_tokens}</td><td>nothing. Token-count telemetry, 51
 *     frames in one captured run: routing it through the unknown-type guard would emit 51 warnings
 *     per run and drown the warnings that matter.</td></tr>
 * <tr><td>{@code type=system}, any other subtype</td>
 *     <td>{@link AgentEvent.UnhandledUpstream} as {@code system/<subtype>}</td></tr>
 * <tr><td>{@code content_block_start} with {@code content_block.type=tool_use}</td>
 *     <td>{@link AgentEvent.ToolStart}, and an assembly buffer is opened against the block
 *     index.</td></tr>
 * <tr><td>{@code content_block_start} with {@code thinking} or {@code text}</td><td>nothing: a block
 *     boundary. Cutting a coalesced block is the projector's decision, not the parser's.</td></tr>
 * <tr><td>{@code content_block_start} with any other block type</td>
 *     <td>{@link AgentEvent.UnhandledUpstream} as {@code content_block_start/<blockType>}</td></tr>
 * <tr><td>{@code content_block_delta} with {@code delta.type=text_delta}</td>
 *     <td>{@link AgentEvent.TextDelta}</td></tr>
 * <tr><td>{@code content_block_delta} with {@code delta.type=thinking_delta}</td>
 *     <td>{@link AgentEvent.ThinkingDelta} with {@link ThinkingSource#MODEL}. This is the chain of
 *     thought the UI used to mislabel as a prompt rewrite.</td></tr>
 * <tr><td>{@code content_block_delta} with {@code delta.type=input_json_delta}</td>
 *     <td>nothing per fragment; {@code partial_json} is appended to the open block's buffer and only
 *     surfaces once, at {@code content_block_stop}.</td></tr>
 * <tr><td>{@code content_block_delta} with {@code delta.type=signature_delta}</td>
 *     <td>nothing, ever. A thinking-block cryptographic signature: it is neither prose nor
 *     reasoning, and it must be neither rendered nor persisted.</td></tr>
 * <tr><td>{@code content_block_delta} with any other delta type</td>
 *     <td>{@link AgentEvent.UnhandledUpstream} as {@code delta/<deltaType>}</td></tr>
 * <tr><td>{@code content_block_stop}</td><td>{@link AgentEvent.ToolInputComplete} when the closed
 *     block was a {@code tool_use}, carrying the assembled arguments; nothing otherwise. Unparseable
 *     arguments become {@code {"_raw":"<fragments>"}} instead of being dropped.</td></tr>
 * <tr><td>{@code message_start} / {@code message_delta} / {@code message_stop} / {@code ping}</td>
 *     <td>nothing. {@code message_delta.usage} is per-turn and partial; the run totals come from the
 *     {@code result} frame, and emitting a second {@link AgentEvent.ResultMeta} would project a
 *     second {@code run_finished}.</td></tr>
 * <tr><td>any other {@code stream_event.event.type}, or a malformed one</td>
 *     <td>{@link AgentEvent.UnhandledUpstream} as {@code stream_event/<eventType>}; a frame with no
 *     inner type at all is skipped with a debug log.</td></tr>
 * <tr><td>{@code type=assistant}, {@code content[].type=text}</td><td>{@link AgentEvent.TextDelta}
 *     with the whole block, but only when no {@code text_delta} was seen: this is the fallback for a
 *     CLI without {@code --include-partial-messages}, and emitting both would double the
 *     prose.</td></tr>
 * <tr><td>{@code type=assistant}, {@code content[].type=thinking}</td>
 *     <td>{@link AgentEvent.ThinkingDelta} under the same no-delta-seen guard.</td></tr>
 * <tr><td>{@code type=assistant}, {@code content[].type=tool_use}</td>
 *     <td>{@link AgentEvent.ToolStart} + {@link AgentEvent.ToolInputComplete}, but only for an id not
 *     already announced by a {@code content_block_start}. In the capture every tool call arrives
 *     <em>both</em> streamed and aggregated, so without that guard each call would render
 *     twice.</td></tr>
 * <tr><td>{@code tool_use_meta} on an assistant frame</td><td>no event of its own; it is the only
 *     place the canonical dotted tool name exists (see below) and is recorded for the events that
 *     follow.</td></tr>
 * <tr><td>{@code type=assistant}, any other content block type</td>
 *     <td>{@link AgentEvent.UnhandledUpstream} as {@code assistant_block/<blockType>}</td></tr>
 * <tr><td>{@code type=user}, {@code message.content[].type=tool_result}</td>
 *     <td>exactly one {@link AgentEvent.ToolDone}. {@code is_error} is <em>absent</em> on success in
 *     the capture, so absent-or-false both mean success. {@code content} is normalised from either a
 *     bare string or a {@code [{type:"text",text:...}]} array. Duration is left null: the projector
 *     measures it against the remembered {@link AgentEvent.ToolStart}.</td></tr>
 * <tr><td>{@code type=user}, top-level {@code tool_use_result}</td><td>nothing. The same result is
 *     carried twice in one frame (once inside {@code message.content}, once as a top-level
 *     {@code tool_use_result} with a {@code structuredContent} twin); reading both would emit two
 *     {@link AgentEvent.ToolDone} events for one call.</td></tr>
 * <tr><td>{@code type=user}, any other content block</td><td>nothing. A {@code text} block there is
 *     the prompt Studio sent, echoed back.</td></tr>
 * <tr><td>{@code type=result}</td><td>{@link AgentEvent.ResultMeta} with the session id, duration and
 *     token totals, <em>last</em>, preceded by a {@link AgentEvent.ProviderNotice} for the frame's own
 *     {@code errors[]} entries, for a non-null {@code api_error_status} and for every non-empty
 *     {@code permission_denials} entry. A subtype other than {@code success} is what turns the run
 *     into an error. A failed frame echoes the <em>requested</em> session id back, so a caller must
 *     not persist the session id of a run that did not succeed.</td></tr>
 * <tr><td>a line that is not JSON, is not an object, or has no {@code type}</td><td>nothing, plus a
 *     debug log. The capture's own run separator is such a line.</td></tr>
 * <tr><td>any other top-level {@code type}</td><td>{@link AgentEvent.UnhandledUpstream}, at most once
 *     per type per run.</td></tr>
 * </table>
 *
 * <h2>Tool names are sanitised upstream, and must not be un-sanitised here</h2>
 * Claude Code exposes an MCP tool as {@code mcp__<server>__<tool>} with every {@code .} and
 * {@code -} in the tool name replaced by {@code _}: all 40 Studio tools lose their dots, so
 * {@code rmq.topic.list} arrives as {@code mcp__rocketmq-studio__rmq_topic_list}. Reversing that by
 * substituting characters is impossible — {@code rmq_message_query_by_topic} could be
 * {@code rmq.message.query.by.topic} or {@code rmq.message_query_by_topic}, and a dash is
 * indistinguishable from an underscore. The canonical name is instead published per call in the
 * assistant frame's {@code tool_use_meta} array
 * ({@code [{"id":"toolu_...","display_name":"rmq.instance.capabilities",...}]}), so this parser
 * correlates by id and only falls back to the sanitised name when no meta was seen.
 *
 * <p>One consequence of the real frame order is worth stating: {@code tool_use_meta} arrives
 * <em>after</em> the {@code content_block_start} that announced the call (capture lines 84, 90 and
 * 91). The {@link AgentEvent.ToolStart} emitted at the block start therefore still carries the
 * sanitised name, and the display name lands on the {@link AgentEvent.ToolInputComplete} that closes
 * the block. That costs nothing: the projector emits no live frame for {@link AgentEvent.ToolStart}
 * at all, it only remembers the id and starts the duration clock, and the remembered label is
 * replaced by the one on {@link AgentEvent.ToolInputComplete}. In the non-streaming fallback
 * (an assistant {@code tool_use} block with no preceding block start) meta and call arrive in the
 * same frame, so there both events do carry the display name.
 *
 * <h2>Deduplication is this class's job</h2>
 * {@link AgentEventProjector} says in so many words that it has no opinion about how chatty a parser
 * is, so an {@link AgentEvent.UnhandledUpstream} is emitted at most once per distinct upstream type
 * per run. Without that, one new Claude Code release emitting a frame per token would push thousands
 * of identical warnings into the timeline.
 */
@Slf4j
final class ClaudeCodeStreamParser {

    /**
     * Key used when tool arguments arrive but cannot be parsed as JSON. Keeping the fragments beats
     * dropping them: an unreadable argument list is still evidence of what the model tried to do.
     */
    static final String RAW_INPUT_KEY = "_raw";

    /** Ceiling for a short human-readable reason: a tool error, or the provider's own message. */
    private static final int MAX_REASON_CHARS = 512;

    /** How many denied tool names a permission-denial notice lists before it says "and N more". */
    private static final int MAX_DENIED_NAMES = 5;

    /** Jackson's tree mapper is thread-safe for reads, and every run parses the same way. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Claude Code telemetry subtypes that are expected and carry nothing worth rendering. */
    private static final Set<String> TOLERATED_SYSTEM_SUBTYPES = Set.of("status", "thinking_tokens");

    private static final String SYSTEM = "system";
    private static final String STREAM_EVENT = "stream_event";
    private static final String ASSISTANT = "assistant";
    private static final String USER = "user";
    private static final String RESULT = "result";
    private static final String SUBTYPE_INIT = "init";
    private static final String SUBTYPE_SUCCESS = "success";
    private static final String SUBTYPE_ERROR = "error";
    private static final String BLOCK_TOOL_USE = "tool_use";
    private static final String BLOCK_THINKING = "thinking";
    private static final String BLOCK_TEXT = "text";
    private static final String BLOCK_TOOL_RESULT = "tool_result";
    private static final String DELTA_TEXT = "text_delta";
    private static final String DELTA_THINKING = "thinking_delta";
    private static final String DELTA_INPUT_JSON = "input_json_delta";
    private static final String DELTA_SIGNATURE = "signature_delta";
    private static final String STATUS_CONNECTED = "connected";
    private static final String UNKNOWN = "unknown";

    /** Block index -> the tool call being assembled. Insertion ordered so a dump reads in order. */
    private final Map<Integer, ToolBlock> openToolBlocks = new LinkedHashMap<>();

    /** tool_use id -> canonical dotted name from {@code tool_use_meta}. */
    private final Map<String, String> toolDisplayNames = new LinkedHashMap<>();

    /** tool_use ids already announced from a {@code content_block_start}. */
    private final Set<String> announcedToolIds = new LinkedHashSet<>();

    /** Upstream types already reported as unhandled, so each is reported at most once per run. */
    private final Set<String> reportedUnhandled = new LinkedHashSet<>();

    /** True once a {@code text_delta} arrived: the aggregated assistant text is then a duplicate. */
    private boolean textDeltaSeen;

    /** Same guard for reasoning. */
    private boolean thinkingDeltaSeen;

    /** True once any {@link AgentEvent.TextDelta} was emitted, from either source. */
    private boolean textEmitted;

    /** True once the terminal {@code result} frame arrived. */
    private boolean resultFrameSeen;

    /** Subtype of that frame, resolved by {@link #resolveSubtype}; null while none has arrived. */
    private String resultSubtype;

    /** Session id from the most recent frame that carried one; the init frame is the first. */
    private String runtimeSessionId;

    /**
     * Final answer text for callers that only want text, i.e. the deprecated
     * {@code AgentProvider.stream} channel. Populated from {@code result.result} only when no
     * {@link AgentEvent.TextDelta} was emitted, so the two can never both reach a caller.
     */
    private String fallbackText;

    /**
     * Parses one newline-delimited frame.
     *
     * @return the events this frame yields, in order; empty for the many frames that yield none.
     *     Never null, and never throws: a malformed frame is skipped with a debug log because one
     *     bad line must not lose the rest of a run.
     */
    List<AgentEvent> parseLine(String line) {
        if (!StringUtils.hasText(line)) {
            return List.of();
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(line);
        } catch (IOException exception) {
            log.debug("Skipping unparseable claude stream line: {}", abbreviate(line, 200));
            return List.of();
        }
        if (node == null || !node.isObject()) {
            log.debug("Skipping non-object claude stream line: {}", abbreviate(line, 200));
            return List.of();
        }
        captureSessionId(node);
        String type = node.path("type").asText("");
        if (!StringUtils.hasText(type)) {
            log.debug("Skipping claude stream frame without a type: {}", abbreviate(line, 200));
            return List.of();
        }
        return switch (type) {
            case SYSTEM -> parseSystem(node);
            case STREAM_EVENT -> parseStreamEvent(node);
            case ASSISTANT -> parseAssistant(node);
            case USER -> parseUser(node);
            case RESULT -> parseResult(node);
            default -> unhandled(type);
        };
    }

    /**
     * The runtime session id seen so far, or null. Captured from the very first frame rather than
     * from {@code result}, because a run that is stopped or killed before its terminal frame must
     * still leave the conversation resumable.
     */
    String runtimeSessionId() {
        return runtimeSessionId;
    }

    /** Final answer text for the text-only channel, or null when the run streamed its own text. */
    String fallbackText() {
        return fallbackText;
    }

    /** Whether the terminal {@code result} frame arrived, i.e. whether the run explained itself. */
    boolean resultFrameSeen() {
        return resultFrameSeen;
    }

    /**
     * The subtype of that frame, or null when none arrived. One half of the lost-resume signal: the
     * caller pairs it with the exit code and the stderr, because a subtype alone cannot tell a
     * vanished {@code --resume} session from a failure no retry can fix.
     */
    String resultSubtype() {
        return resultSubtype;
    }

    /**
     * Every frame of a run carries {@code session_id}, including {@code system/init} and
     * {@code stream_event}, so the id is known before anything interesting happens.
     */
    private void captureSessionId(JsonNode node) {
        String sessionId = node.path("session_id").asText("");
        if (StringUtils.hasText(sessionId)) {
            runtimeSessionId = sessionId;
        }
    }

    private List<AgentEvent> parseSystem(JsonNode node) {
        String subtype = node.path("subtype").asText("");
        if (SUBTYPE_INIT.equals(subtype)) {
            return List.of(initMeta(node));
        }
        if (TOLERATED_SYSTEM_SUBTYPES.contains(subtype)) {
            return List.of();
        }
        return unhandled(SYSTEM + "/" + (StringUtils.hasText(subtype) ? subtype : UNKNOWN));
    }

    /**
     * Only servers whose status is {@code connected} are listed, and a missing status counts as
     * connected: {@link AgentEventProjector} warns when our own server is absent from this list, so
     * reporting a failed server as present would hide exactly the failure that matters.
     */
    private AgentEvent.InitMeta initMeta(JsonNode node) {
        List<String> servers = null;
        JsonNode serversNode = node.path("mcp_servers");
        if (serversNode.isArray()) {
            servers = new ArrayList<>();
            for (JsonNode server : serversNode) {
                String name = server.path("name").asText("");
                String status = server.path("status").asText("");
                if (StringUtils.hasText(name)
                        && (!StringUtils.hasText(status) || STATUS_CONNECTED.equals(status))) {
                    servers.add(name);
                }
            }
        }
        List<String> tools = new ArrayList<>();
        for (JsonNode tool : node.path("tools")) {
            String name = tool.asText("");
            if (StringUtils.hasText(name)) {
                tools.add(name);
            }
        }
        return new AgentEvent.InitMeta(runtimeSessionId, servers, List.copyOf(tools));
    }

    private List<AgentEvent> parseStreamEvent(JsonNode node) {
        JsonNode event = node.path("event");
        String eventType = event.path("type").asText("");
        if (!StringUtils.hasText(eventType)) {
            log.debug("Skipping stream_event without an inner event type");
            return List.of();
        }
        return switch (eventType) {
            case "content_block_start" -> onContentBlockStart(event);
            case "content_block_delta" -> onContentBlockDelta(event);
            case "content_block_stop" -> onContentBlockStop(event);
            case "message_start", "message_delta", "message_stop", "ping" -> List.of();
            default -> unhandled(STREAM_EVENT + "/" + eventType);
        };
    }

    private List<AgentEvent> onContentBlockStart(JsonNode event) {
        JsonNode block = event.path("content_block");
        String blockType = block.path("type").asText("");
        if (BLOCK_TOOL_USE.equals(blockType)) {
            String id = block.path("id").asText("");
            String name = block.path("name").asText("");
            openToolBlocks.put(event.path("index").asInt(-1), new ToolBlock(id, name));
            if (StringUtils.hasText(id)) {
                announcedToolIds.add(id);
            }
            return List.of(new AgentEvent.ToolStart(id, displayName(id, name)));
        }
        if (BLOCK_THINKING.equals(blockType) || BLOCK_TEXT.equals(blockType)) {
            // A block boundary. The projector cuts its coalescing buffer on a kind change, so there
            // is nothing for the parser to say here.
            return List.of();
        }
        return unhandled("content_block_start/" + (StringUtils.hasText(blockType) ? blockType : UNKNOWN));
    }

    private List<AgentEvent> onContentBlockDelta(JsonNode event) {
        JsonNode delta = event.path("delta");
        String deltaType = delta.path("type").asText("");
        return switch (deltaType) {
            case DELTA_TEXT -> {
                textDeltaSeen = true;
                String text = delta.path("text").asText("");
                if (text.isEmpty()) {
                    yield List.of();
                }
                textEmitted = true;
                yield List.of(new AgentEvent.TextDelta(text));
            }
            case DELTA_THINKING -> {
                thinkingDeltaSeen = true;
                String thinking = delta.path("thinking").asText("");
                if (thinking.isEmpty()) {
                    yield List.of();
                }
                yield List.of(new AgentEvent.ThinkingDelta(thinking, ThinkingSource.MODEL));
            }
            case DELTA_INPUT_JSON -> {
                // Too chatty to stream: arguments are assembled and reported once, when the block
                // closes. A fragment for a block that was never opened as a tool call is dropped.
                ToolBlock block = openToolBlocks.get(event.path("index").asInt(-1));
                if (block != null) {
                    block.arguments.append(delta.path("partial_json").asText(""));
                }
                yield List.of();
            }
            case DELTA_SIGNATURE -> List.of();
            default -> unhandled("delta/" + (StringUtils.hasText(deltaType) ? deltaType : UNKNOWN));
        };
    }

    private List<AgentEvent> onContentBlockStop(JsonNode event) {
        ToolBlock block = openToolBlocks.remove(event.path("index").asInt(-1));
        if (block == null) {
            return List.of();
        }
        return List.of(new AgentEvent.ToolInputComplete(block.id, displayName(block.id, block.name),
                assembledArguments(block)));
    }

    /** Empty arguments are a legitimate no-argument call, so they become {@code {}} not {@code _raw}. */
    private Object assembledArguments(ToolBlock block) {
        String raw = block.arguments.toString();
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(raw, Object.class);
        } catch (IOException exception) {
            log.debug("Tool call {} streamed arguments that are not JSON: {}", block.id,
                    abbreviate(raw, 200));
            return Map.of(RAW_INPUT_KEY, raw);
        }
    }

    private List<AgentEvent> parseAssistant(JsonNode node) {
        // Recorded before the blocks are read, so a tool_use in this very frame can be labelled with
        // the canonical dotted name instead of the sanitised one.
        recordToolUseMeta(node);
        List<AgentEvent> events = new ArrayList<>();
        for (JsonNode block : node.path("message").path("content")) {
            String blockType = block.path("type").asText("");
            switch (blockType) {
                case BLOCK_TEXT -> {
                    if (textDeltaSeen) {
                        continue;
                    }
                    String text = block.path("text").asText("");
                    if (!text.isEmpty()) {
                        textEmitted = true;
                        events.add(new AgentEvent.TextDelta(text));
                    }
                }
                case BLOCK_THINKING -> {
                    if (thinkingDeltaSeen) {
                        continue;
                    }
                    String thinking = block.path("thinking").asText("");
                    if (!thinking.isEmpty()) {
                        events.add(new AgentEvent.ThinkingDelta(thinking, ThinkingSource.MODEL));
                    }
                }
                case BLOCK_TOOL_USE -> {
                    String id = block.path("id").asText("");
                    if (StringUtils.hasText(id) && !announcedToolIds.add(id)) {
                        // Already announced by content_block_start: the same call, aggregated.
                        continue;
                    }
                    String tool = displayName(id, block.path("name").asText(""));
                    events.add(new AgentEvent.ToolStart(id, tool));
                    events.add(new AgentEvent.ToolInputComplete(id, tool, toPlainValue(block.path("input"))));
                }
                default -> events.addAll(
                        unhandled("assistant_block/" + (StringUtils.hasText(blockType) ? blockType : UNKNOWN)));
            }
        }
        return events;
    }

    private void recordToolUseMeta(JsonNode node) {
        for (JsonNode meta : node.path("tool_use_meta")) {
            String id = meta.path("id").asText("");
            String displayName = meta.path("display_name").asText("");
            if (StringUtils.hasText(id) && StringUtils.hasText(displayName)) {
                toolDisplayNames.put(id, displayName);
            }
        }
    }

    private List<AgentEvent> parseUser(JsonNode node) {
        JsonNode content = node.path("message").path("content");
        if (!content.isArray()) {
            return List.of();
        }
        List<AgentEvent> events = new ArrayList<>();
        for (JsonNode block : content) {
            if (!BLOCK_TOOL_RESULT.equals(block.path("type").asText(""))) {
                continue;
            }
            String id = block.path("tool_use_id").asText("");
            // is_error is absent on success in the capture, so absent and false are the same thing.
            boolean failed = block.path("is_error").asBoolean(false);
            String output = normaliseToolResult(block.path("content"));
            events.add(new AgentEvent.ToolDone(id, displayName(id, null), output, !failed, null,
                    failed ? abbreviate(output, MAX_REASON_CHARS) : null));
        }
        return events;
    }

    /**
     * Tool results arrive either as a bare string (the capture) or as an array of content blocks
     * (the documented Anthropic shape). Both normalise to one string; a non-text block such as an
     * image is skipped, and an object payload keeps its JSON so nothing is silently lost.
     */
    private String normaliseToolResult(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder normalised = new StringBuilder();
            for (JsonNode part : content) {
                String text = null;
                if (BLOCK_TEXT.equals(part.path("type").asText(""))) {
                    text = part.path("text").asText("");
                } else if (part.isTextual()) {
                    text = part.asText();
                }
                if (text == null) {
                    continue;
                }
                if (!normalised.isEmpty()) {
                    normalised.append('\n');
                }
                normalised.append(text);
            }
            return normalised.toString();
        }
        return content.toString();
    }

    private List<AgentEvent> parseResult(JsonNode node) {
        List<AgentEvent> events = new ArrayList<>(3);
        providerErrors(node).ifPresent(events::add);
        JsonNode apiErrorStatus = node.path("api_error_status");
        if (!apiErrorStatus.isMissingNode() && !apiErrorStatus.isNull()) {
            events.add(new AgentEvent.ProviderNotice(AgentEventProjector.LEVEL_ERROR,
                    "upstream API error status: " + apiErrorStatus.asText()));
        }
        permissionDenials(node).ifPresent(events::add);
        if (!textEmitted) {
            String result = node.path("result").asText("");
            if (StringUtils.hasText(result)) {
                fallbackText = result;
            }
        }
        resultFrameSeen = true;
        resultSubtype = resolveSubtype(node);
        JsonNode usage = node.path("usage");
        events.add(new AgentEvent.ResultMeta(runtimeSessionId, longOrNull(node, "duration_ms"),
                intOrNull(usage, "input_tokens"), intOrNull(usage, "output_tokens"),
                resultSubtype));
        return events;
    }

    /**
     * The result frame's own explanation of a failure, as an {@code errors} array. A run that resumes
     * a session id which no longer exists is the concrete case: the CLI exits 1 and reports
     * {@code subtype:error_during_execution} with
     * {@code errors:["No conversation found with session ID: ..."]}. Without this the caller only
     * sees a subtype, and the one thing the user needs to know — that the conversation's runtime
     * session is gone and has to be started over — is lost.
     *
     * <p>Note the trap this exposes: that frame still carries a {@code session_id}, and it is the
     * <em>requested</em> id echoed back, not a live one. A caller must not persist the session id of a
     * run that did not succeed.
     */
    private Optional<AgentEvent.ProviderNotice> providerErrors(JsonNode node) {
        JsonNode errors = node.path("errors");
        if (!errors.isArray() || errors.isEmpty()) {
            return Optional.empty();
        }
        List<String> messages = new ArrayList<>();
        for (JsonNode error : errors) {
            String message = error.asText("");
            if (StringUtils.hasText(message)) {
                messages.add(message);
            }
        }
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AgentEvent.ProviderNotice(AgentEventProjector.LEVEL_ERROR,
                "provider reported: " + abbreviate(String.join("; ", messages), MAX_REASON_CHARS)));
    }

    /**
     * Without {@code --allowedTools} the CLI silently auto-denies MCP tool calls: no prompt, no
     * error, {@code is_error:false}, exit code 0, and the model simply asks a human to approve
     * something that will never be shown to one. {@code permission_denials} is the only trace of it,
     * so a non-empty list becomes a warning the user cannot miss.
     */
    private Optional<AgentEvent.ProviderNotice> permissionDenials(JsonNode node) {
        JsonNode denials = node.path("permission_denials");
        if (!denials.isArray() || denials.isEmpty()) {
            return Optional.empty();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode denial : denials) {
            String id = denial.path("tool_use_id").asText("");
            String name = denial.path("tool_name").asText("");
            names.add(displayName(id, name));
        }
        List<String> listed = names.stream()
                .filter(StringUtils::hasText)
                .limit(MAX_DENIED_NAMES)
                .toList();
        String suffix = names.size() > listed.size() ? ", and " + (names.size() - listed.size()) + " more" : "";
        return Optional.of(new AgentEvent.ProviderNotice(AgentEventProjector.LEVEL_WARN,
                "provider auto-denied " + names.size() + " tool call(s) without prompting: "
                        + String.join(", ", listed) + suffix
                        + ". RocketMQ tools cannot run in this session; check the MCP allow-list."));
    }

    /**
     * {@code subtype} decides whether the run completed. {@code is_error} is consulted as well, so a
     * frame that claims success while reporting an error can never be projected as a completed run.
     */
    private String resolveSubtype(JsonNode node) {
        String subtype = node.path("subtype").asText("");
        boolean failed = node.path("is_error").asBoolean(false);
        if (!StringUtils.hasText(subtype)) {
            return failed ? SUBTYPE_ERROR : SUBTYPE_SUCCESS;
        }
        if (failed && SUBTYPE_SUCCESS.equals(subtype)) {
            return SUBTYPE_ERROR;
        }
        return subtype;
    }

    /** The canonical dotted name when {@code tool_use_meta} supplied one, else the sanitised name. */
    private String displayName(String id, String sanitisedName) {
        if (StringUtils.hasText(id)) {
            String displayName = toolDisplayNames.get(id);
            if (StringUtils.hasText(displayName)) {
                return displayName;
            }
        }
        return StringUtils.hasText(sanitisedName) ? sanitisedName : null;
    }

    /** Records an upstream shape this parser does not model, at most once per type per run. */
    private List<AgentEvent> unhandled(String upstreamType) {
        if (!reportedUnhandled.add(upstreamType)) {
            return List.of();
        }
        log.debug("Unhandled claude stream frame type: {}", upstreamType);
        return List.of(new AgentEvent.UnhandledUpstream(upstreamType));
    }

    private Object toPlainValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        return MAPPER.convertValue(node, Object.class);
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asLong() : null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static String abbreviate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "...";
    }

    /** A tool call whose arguments are still arriving: its id, its sanitised name, its buffer. */
    private static final class ToolBlock {

        private final String id;
        private final String name;
        private final StringBuilder arguments = new StringBuilder();

        private ToolBlock(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
