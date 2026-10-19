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
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.apache.rocketmq.studio.ops.ai.conversation.event.LiveEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.event.ThinkingSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The disposition table of {@link ClaudeCodeStreamParser}, replayed against a real capture.
 *
 * <p>{@code src/test/resources/ai/claude-stream-capture.jsonl} is 278 lines of actual
 * {@code claude --output-format stream-json --include-partial-messages} output: run 1 (lines 1-216)
 * lists the topics of an instance through two successful MCP tool calls, line 217 is the capture's own
 * run separator, and run 2 (lines 218-278) is a {@code --resume} turn that answers from context and
 * therefore calls no tool at all. Every case below feeds real lines by line number rather than
 * hand-written JSON, so the parser is tested against what the CLI emits and not against what its
 * author remembered. The handful of shapes the capture happens not to contain (an errored tool
 * result, an array-shaped tool result, a non-success result subtype, a permission denial, unknown
 * frame types) are marked as synthetic below and are the only invented JSON in this file.
 *
 * <p>Each case asserts the complete ordered event list, not a {@code contains}: an event that must
 * not be emitted is as much a contract as one that must be.
 *
 * <p>{@code claude-stream-capture-mcp-failed.jsonl} is a second, 196-line capture of the failure mode
 * the first one cannot show: {@code claude} started, its {@code system/init} frame reported
 * {@code mcp_servers[0].status == "failed"}, no {@code mcp__} tool was ever offered, the model answered
 * from its own head, and the run still ended {@code subtype:"success"} with {@code is_error:false}, an
 * empty {@code permission_denials} and exit code 0. Nothing upstream considers that an error, so the
 * init frame is the only evidence that RocketMQ tools were unavailable — which is what
 * {@link #anMcpServerThatFailedToConnectShouldBeReportedAsNotConnectedTest()} pins down.
 */
class ClaudeCodeStreamParserTest {

    private static final String CAPTURE = "/ai/claude-stream-capture.jsonl";
    private static final String SESSION = "280b366b-a346-440c-b595-bf28c68910a4";
    private static final String TOOL_CAPABILITIES = "toolu_c3fc7fce2b8349b5a03068a9";
    private static final String TOOL_TOPIC_LIST = "toolu_f7cb89e3a9f941c8b354eea7";
    private static final String SANITISED_CAPABILITIES = "mcp__rocketmq-studio__rmq_instance_capabilities";
    private static final String SANITISED_TOPIC_LIST = "mcp__rocketmq-studio__rmq_topic_list";
    private static final String SANITISED_GROUP_LIST = "mcp__rocketmq-studio__rmq_group_list";

    private static final int RUN1_FIRST = 1;
    private static final int RUN1_LAST = 216;
    private static final int RUN2_FIRST = 218;
    private static final int RUN2_LAST = 278;

    /** Frames counted in run 1, straight off the capture. */
    private static final int RUN1_THINKING_DELTAS = 51;
    private static final int RUN1_TEXT_DELTAS = 64;
    private static final int RUN1_SIGNATURE_DELTAS = 3;
    private static final int RUN1_THINKING_TOKEN_FRAMES = 51;
    private static final int RUN1_STATUS_FRAMES = 3;

    private static final int RUN2_THINKING_DELTAS = 16;
    private static final int RUN2_TEXT_DELTAS = 16;

    /**
     * The second capture: a whole run in which the Studio MCP server never connected. One thinking
     * block and one text block, no tool call, and a result frame that claims success.
     */
    private static final String MCP_FAILED_CAPTURE = "/ai/claude-stream-capture-mcp-failed.jsonl";
    private static final String MCP_FAILED_SESSION = "4e943b51-9e53-42d9-b2e1-0cb2cc839ac4";
    private static final int MCP_FAILED_LINES_COUNT = 196;
    private static final int MCP_FAILED_INIT_LINE = 1;
    private static final int MCP_FAILED_RESULT_LINE = 196;
    private static final int MCP_FAILED_THINKING_DELTAS = 62;
    private static final int MCP_FAILED_TEXT_DELTAS = 59;
    private static final int MCP_FAILED_VISIBLE_TOOLS = 17;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> LINES = loadLines(CAPTURE);
    private static final List<String> MCP_FAILED = loadLines(MCP_FAILED_CAPTURE);

    /** One code point, two UTF-16 chars: the case the abbreviation caps have to survive. */
    private static final String EMOJI = "\uD83D\uDE00";

    // Synthetic frames: shapes the capture does not contain.
    private static final String TOOL_RESULT_ARRAY_FRAME = """
            {"type":"user","session_id":"s-1","message":{"role":"user","content":[\
            {"type":"tool_result","tool_use_id":"toolu_arr","content":[\
            {"type":"text","text":"first part"},{"type":"text","text":"second part"}]}]}}""";
    private static final String TOOL_RESULT_ERROR_FRAME = """
            {"type":"user","session_id":"s-1","message":{"role":"user","content":[\
            {"type":"tool_result","tool_use_id":"toolu_err","is_error":true,"content":"boom"}]}}""";
    private static final String RESULT_MAX_TURNS_FRAME = """
            {"type":"result","subtype":"error_max_turns","is_error":true,"duration_ms":1200,\
            "session_id":"s-1","usage":{"input_tokens":10,"output_tokens":20}}""";
    private static final String RESULT_DENIED_FRAME = """
            {"type":"result","subtype":"success","is_error":false,"duration_ms":5,"session_id":"s-1",\
            "usage":{"input_tokens":1,"output_tokens":2},"permission_denials":[\
            {"tool_name":"mcp__rocketmq-studio__rmq_topic_list","tool_use_id":"toolu_denied","tool_input":{}}]}""";
    private static final String RESULT_API_ERROR_FRAME = """
            {"type":"result","subtype":"error_during_execution","is_error":true,"duration_ms":7,\
            "session_id":"s-1","api_error_status":429,"permission_denials":[],\
            "usage":{"input_tokens":3,"output_tokens":4}}""";
    private static final String UNKNOWN_TYPE_FRAME = """
            {"type":"compact_boundary","session_id":"s-1","compact_metadata":{"trigger":"auto"}}""";
    private static final String UNKNOWN_SYSTEM_SUBTYPE_FRAME = """
            {"type":"system","subtype":"compact_boundary","session_id":"s-1"}""";
    private static final String UNKNOWN_DELTA_TYPE_FRAME = """
            {"type":"stream_event","session_id":"s-1","event":{"type":"content_block_delta","index":0,\
            "delta":{"type":"citations_delta","citation":{"type":"char_location"}}}}""";
    private static final String UNKNOWN_STREAM_EVENT_FRAME = """
            {"type":"stream_event","session_id":"s-1","event":{"type":"content_block_replace","index":0}}""";
    private static final String UNKNOWN_ASSISTANT_BLOCK_FRAME = """
            {"type":"assistant","session_id":"s-1","message":{"role":"assistant","content":[\
            {"type":"redacted_thinking","data":"EmwKAhgBEgy"}]}}""";
    private static final String UNKNOWN_CONTENT_BLOCK_FRAME = """
            {"type":"stream_event","session_id":"s-1","event":{"type":"content_block_start","index":0,\
            "content_block":{"type":"server_tool_use","id":"srvtoolu_1","name":"web_search"}}}""";
    private static final String TOOL_START_FRAME = """
            {"type":"stream_event","session_id":"s-1","event":{"type":"content_block_start","index":1,\
            "content_block":{"type":"tool_use","id":"toolu_args","name":"mcp__rocketmq-studio__rmq_group_list","input":{}}}}""";
    private static final String BROKEN_ARGUMENT_DELTA_FRAME = """
            {"type":"stream_event","session_id":"s-1","event":{"type":"content_block_delta","index":1,\
            "delta":{"type":"input_json_delta","partial_json":"{not json"}}}""";
    /**
     * A real failed-resume frame, captured live on 2026-09-17 with claude 2.1.273 by running the exact
     * argv this provider builds with a {@code --resume} id that does not exist and
     * {@code ANTHROPIC_BASE_URL} pointed at a closed port, so no request could leave the machine. The
     * CLI exits 1 and puts the reason in {@code errors[]}, not in the subtype — which is why the parser
     * reads that array. Note it still echoes the requested session id back.
     */
    private static final String REAL_RESUME_FAILURE_FRAME = """
            {"type":"result","subtype":"error_during_execution","duration_ms":0,"duration_api_ms":0,\
            "is_error":true,"num_turns":0,"stop_reason":null,\
            "session_id":"280b366b-a346-440c-b595-bf28c68910a4","total_cost_usd":0,\
            "usage":{"output_tokens_details":{"thinking_tokens":0},"input_tokens":0,\
            "cache_creation_input_tokens":0,"cache_read_input_tokens":0,"output_tokens":0,\
            "server_tool_use":{"web_search_requests":0,"web_fetch_requests":0},\
            "service_tier":"standard","cache_creation":{"ephemeral_1h_input_tokens":0,\
            "ephemeral_5m_input_tokens":0},"inference_geo":"","iterations":[],"speed":"standard"},\
            "modelUsage":{},"permission_denials":[],"uuid":"186dd90d-62bd-4674-ac59-c9434f59856f",\
            "errors":["No conversation found with session ID: \
            280b366b-a346-440c-b595-bf28c68910a4"],"result_index":0}""";
    private static final String BLOCK_STOP_FRAME = """
            {"type":"stream_event","session_id":"s-1","event":{"type":"content_block_stop","index":1}}""";

    static Stream<Arguments> dispositions() {
        return Stream.of(
                // ---- system frames -----------------------------------------------------------
                Arguments.of("system/init captures the session id, the connected MCP servers and the visible tools",
                        List.of(line(1)),
                        List.of(new AgentEvent.InitMeta(SESSION, List.of("rocketmq-studio"), toolsOf(line(1))))),
                Arguments.of("system/status is CLI telemetry, tolerated without a warning",
                        List.of(line(2)), List.of()),
                Arguments.of("system/thinking_tokens is CLI telemetry, tolerated without a warning",
                        List.of(line(5)), List.of()),
                // ---- stream_event frames -----------------------------------------------------
                Arguments.of("message_start emits nothing", List.of(line(3)), List.of()),
                Arguments.of("message_delta and message_stop emit nothing: the result frame owns usage",
                        List.of(line(92), line(93)), List.of()),
                Arguments.of("content_block_start/thinking is a block boundary and emits nothing",
                        List.of(line(4)), List.of()),
                Arguments.of("content_block_start/text is a block boundary and emits nothing",
                        List.of(line(113)), List.of()),
                Arguments.of("content_block_stop of a thinking block emits nothing",
                        List.of(line(83)), List.of()),
                Arguments.of("thinking_delta becomes a model ThinkingDelta",
                        List.of(line(6)),
                        List.of(new AgentEvent.ThinkingDelta("The", ThinkingSource.MODEL))),
                Arguments.of("signature_delta is never rendered and never persisted",
                        List.of(line(81)), List.of()),
                Arguments.of("text_delta becomes a TextDelta",
                        List.of(line(114)), List.of(new AgentEvent.TextDelta("The instance has"))),
                // ---- fallbacks for a CLI without partial messages ----------------------------
                Arguments.of("assistant thinking is suppressed once thinking deltas streamed",
                        List.of(line(6), line(82)),
                        List.of(new AgentEvent.ThinkingDelta("The", ThinkingSource.MODEL))),
                Arguments.of("assistant text is suppressed once text deltas streamed",
                        List.of(line(114), line(119)),
                        List.of(new AgentEvent.TextDelta("The instance has"))),
                Arguments.of("assistant thinking without deltas is the reasoning fallback",
                        List.of(line(82)),
                        List.of(new AgentEvent.ThinkingDelta(thinkingOf(line(82)), ThinkingSource.MODEL))),
                Arguments.of("assistant text without deltas is the prose fallback",
                        List.of(line(119)), List.of(new AgentEvent.TextDelta(textOf(line(119))))),
                Arguments.of("assistant tool_use without a streamed start yields both tool events, "
                                + "labelled with the display name from the same frame's tool_use_meta",
                        List.of(line(127)),
                        List.of(new AgentEvent.ToolStart(TOOL_TOPIC_LIST, "rmq.topic.list"),
                                new AgentEvent.ToolInputComplete(TOOL_TOPIC_LIST, "rmq.topic.list",
                                        Map.of("instanceId", "open-source-local")))),
                // ---- a streamed tool call ----------------------------------------------------
                Arguments.of("content_block_start/tool_use announces the call, still with the sanitised name "
                                + "because tool_use_meta has not arrived yet",
                        List.of(line(84)),
                        List.of(new AgentEvent.ToolStart(TOOL_CAPABILITIES, SANITISED_CAPABILITIES))),
                Arguments.of("input_json_delta fragments assemble into one ToolInputComplete carrying the "
                                + "canonical dotted name from tool_use_meta",
                        lines(84, 91),
                        List.of(new AgentEvent.ToolStart(TOOL_CAPABILITIES, SANITISED_CAPABILITIES),
                                new AgentEvent.ToolInputComplete(TOOL_CAPABILITIES, "rmq.instance.capabilities",
                                        Map.of("instanceId", "open-source-local")))),
                Arguments.of("an assistant tool_use already announced by content_block_start is not re-emitted",
                        lines(121, 128),
                        List.of(new AgentEvent.ToolStart(TOOL_TOPIC_LIST, SANITISED_TOPIC_LIST),
                                new AgentEvent.ToolInputComplete(TOOL_TOPIC_LIST, "rmq.topic.list",
                                        Map.of("instanceId", "open-source-local")))),
                Arguments.of("tool_result yields exactly one ToolDone even though the frame carries the "
                                + "result twice, and the dotted name comes from the earlier tool_use_meta",
                        lines(90, 94),
                        List.of(new AgentEvent.ToolStart(TOOL_CAPABILITIES, "rmq.instance.capabilities"),
                                new AgentEvent.ToolInputComplete(TOOL_CAPABILITIES, "rmq.instance.capabilities",
                                        Map.of("instanceId", "open-source-local")),
                                new AgentEvent.ToolDone(TOOL_CAPABILITIES, "rmq.instance.capabilities",
                                        toolResultOf(line(94)), true, null, null))),
                Arguments.of("a tool_result on its own leaves the tool name to the projector",
                        List.of(line(94)),
                        List.of(new AgentEvent.ToolDone(TOOL_CAPABILITIES, null,
                                toolResultOf(line(94)), true, null, null))),
                Arguments.of("is_error absent means success, and no duration is invented",
                        List.of(line(131)),
                        List.of(new AgentEvent.ToolDone(TOOL_TOPIC_LIST, null,
                                toolResultOf(line(131)), true, null, null))),
                // ---- result frames -----------------------------------------------------------
                Arguments.of("result/success yields ResultMeta with the session id, duration and token totals",
                        List.of(line(216)),
                        List.of(new AgentEvent.ResultMeta(SESSION, 15_092L, 1_487, 485, "success"))),
                Arguments.of("a resumed run re-reports the same session id in its own init frame",
                        List.of(line(RUN2_FIRST)),
                        List.of(new AgentEvent.InitMeta(SESSION, List.of("rocketmq-studio"),
                                toolsOf(line(RUN2_FIRST))))),
                Arguments.of("the resumed run's result carries its own duration and usage",
                        List.of(line(RUN2_LAST)),
                        List.of(new AgentEvent.ResultMeta(SESSION, 4_047L, 6, 120, "success"))),
                // ---- lines that are not frames -----------------------------------------------
                Arguments.of("the capture's run separator has no type and is skipped, not reported unhandled",
                        List.of(line(217)), List.of()),
                Arguments.of("an unparseable line is skipped", List.of("not json at all"), List.of()),
                Arguments.of("a blank line is skipped", List.of("   "), List.of()),
                Arguments.of("a JSON scalar is skipped", List.of("\"done\""), List.of()),
                // ---- synthetic shapes the capture does not contain ----------------------------
                Arguments.of("synthetic: a tool_result whose content is an array of text blocks is normalised",
                        List.of(TOOL_RESULT_ARRAY_FRAME),
                        List.of(new AgentEvent.ToolDone("toolu_arr", null, "first part\nsecond part",
                                true, null, null))),
                Arguments.of("synthetic: is_error true flips success and carries a short reason",
                        List.of(TOOL_RESULT_ERROR_FRAME),
                        List.of(new AgentEvent.ToolDone("toolu_err", null, "boom", false, null, "boom"))),
                Arguments.of("synthetic: result/error_max_turns keeps its subtype instead of looking like success",
                        List.of(RESULT_MAX_TURNS_FRAME),
                        List.of(new AgentEvent.ResultMeta("s-1", 1_200L, 10, 20, "error_max_turns"))),
                Arguments.of("synthetic: an unknown top-level type is reported at most once per run",
                        List.of(UNKNOWN_TYPE_FRAME, UNKNOWN_TYPE_FRAME, UNKNOWN_TYPE_FRAME),
                        List.of(new AgentEvent.UnhandledUpstream("compact_boundary"))),
                Arguments.of("synthetic: an unknown system subtype is reported as system/<subtype>",
                        List.of(UNKNOWN_SYSTEM_SUBTYPE_FRAME),
                        List.of(new AgentEvent.UnhandledUpstream("system/compact_boundary"))),
                Arguments.of("synthetic: an unknown delta type is reported as delta/<type>",
                        List.of(UNKNOWN_DELTA_TYPE_FRAME),
                        List.of(new AgentEvent.UnhandledUpstream("delta/citations_delta"))),
                Arguments.of("synthetic: an unknown stream_event type is reported as stream_event/<type>",
                        List.of(UNKNOWN_STREAM_EVENT_FRAME),
                        List.of(new AgentEvent.UnhandledUpstream("stream_event/content_block_replace"))),
                Arguments.of("synthetic: an unknown assistant block type is reported as assistant_block/<type>",
                        List.of(UNKNOWN_ASSISTANT_BLOCK_FRAME),
                        List.of(new AgentEvent.UnhandledUpstream("assistant_block/redacted_thinking"))),
                Arguments.of("synthetic: an unknown content block type is reported",
                        List.of(UNKNOWN_CONTENT_BLOCK_FRAME),
                        List.of(new AgentEvent.UnhandledUpstream("content_block_start/server_tool_use"))),
                Arguments.of("synthetic: unparseable streamed arguments are kept as _raw rather than dropped",
                        List.of(TOOL_START_FRAME, BROKEN_ARGUMENT_DELTA_FRAME, BLOCK_STOP_FRAME),
                        List.of(new AgentEvent.ToolStart("toolu_args", SANITISED_GROUP_LIST),
                                new AgentEvent.ToolInputComplete("toolu_args", SANITISED_GROUP_LIST,
                                        Map.of(ClaudeCodeStreamParser.RAW_INPUT_KEY, "{not json")))),
                Arguments.of("synthetic: a tool call with no arguments at all assembles to an empty object",
                        List.of(TOOL_START_FRAME, BLOCK_STOP_FRAME),
                        List.of(new AgentEvent.ToolStart("toolu_args", SANITISED_GROUP_LIST),
                                new AgentEvent.ToolInputComplete("toolu_args", SANITISED_GROUP_LIST, Map.of()))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dispositions")
    void parseLineShouldFollowTheDispositionTableTest(String label, List<String> frames,
                                                      List<AgentEvent> expected) {
        ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser();

        List<AgentEvent> actual = new ArrayList<>();
        for (String frame : frames) {
            actual.addAll(parser.parseLine(frame));
        }

        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void runOneShouldYieldTheWholeToolLoopInOrderTest() {
        List<AgentEvent> events = parse(lines(RUN1_FIRST, RUN1_LAST));

        // The structural skeleton, in order: two complete tool calls bracketed by the run's init and
        // result frames. Nothing else in the run may produce a non-delta event.
        assertThat(events.stream()
                .filter(event -> !(event instanceof AgentEvent.TextDelta)
                        && !(event instanceof AgentEvent.ThinkingDelta))
                .toList())
                .containsExactly(
                        new AgentEvent.InitMeta(SESSION, List.of("rocketmq-studio"), toolsOf(line(1))),
                        new AgentEvent.ToolStart(TOOL_CAPABILITIES, SANITISED_CAPABILITIES),
                        new AgentEvent.ToolInputComplete(TOOL_CAPABILITIES, "rmq.instance.capabilities",
                                Map.of("instanceId", "open-source-local")),
                        new AgentEvent.ToolDone(TOOL_CAPABILITIES, "rmq.instance.capabilities",
                                toolResultOf(line(94)), true, null, null),
                        new AgentEvent.ToolStart(TOOL_TOPIC_LIST, SANITISED_TOPIC_LIST),
                        new AgentEvent.ToolInputComplete(TOOL_TOPIC_LIST, "rmq.topic.list",
                                Map.of("instanceId", "open-source-local")),
                        new AgentEvent.ToolDone(TOOL_TOPIC_LIST, "rmq.topic.list",
                                toolResultOf(line(131)), true, null, null),
                        new AgentEvent.ResultMeta(SESSION, 15_092L, 1_487, 485, "success"));

        // One event per delta frame, and nothing for the frames that must stay silent: the three
        // signature deltas, the three aggregated assistant thinking blocks, the two aggregated
        // assistant text blocks, the two aggregated assistant tool_use blocks and the 51
        // thinking_tokens telemetry frames.
        assertThat(count(events, AgentEvent.ThinkingDelta.class)).isEqualTo(RUN1_THINKING_DELTAS);
        assertThat(count(events, AgentEvent.TextDelta.class)).isEqualTo(RUN1_TEXT_DELTAS);
        assertThat(events).hasSize(RUN1_THINKING_DELTAS + RUN1_TEXT_DELTAS + 8);
        assertThat(count(events, AgentEvent.UnhandledUpstream.class)).isZero();
        assertThat(count(events, AgentEvent.ProviderNotice.class)).isZero();
    }

    @Test
    void runTwoShouldProduceNoToolEventsTest() {
        List<AgentEvent> events = parse(lines(RUN2_FIRST, RUN2_LAST));

        assertThat(count(events, AgentEvent.ToolStart.class)).isZero();
        assertThat(count(events, AgentEvent.ToolInputComplete.class)).isZero();
        assertThat(count(events, AgentEvent.ToolDone.class)).isZero();
        assertThat(count(events, AgentEvent.ThinkingDelta.class)).isEqualTo(RUN2_THINKING_DELTAS);
        assertThat(count(events, AgentEvent.TextDelta.class)).isEqualTo(RUN2_TEXT_DELTAS);
        assertThat(count(events, AgentEvent.UnhandledUpstream.class)).isZero();
        assertThat(events).last()
                .isEqualTo(new AgentEvent.ResultMeta(SESSION, 4_047L, 6, 120, "success"));
    }

    @Test
    void sessionIdShouldBeKnownLongBeforeTheResultFrameTest() {
        ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser();

        // The init frame is the first line of a run, so a stop anywhere after it still leaves a
        // resumable session id even though the terminal result frame never arrives.
        parser.parseLine(line(1));

        assertThat(parser.runtimeSessionId()).isEqualTo(SESSION);
        assertThat(parser.resultFrameSeen()).isFalse();
        assertThat(parser.fallbackText()).isNull();
    }

    @Test
    void fallbackTextShouldServeTheTextOnlyChannelTest() {
        ClaudeCodeStreamParser silent = new ClaudeCodeStreamParser();
        silent.parseLine(line(RUN1_LAST));

        assertThat(silent.fallbackText()).isEqualTo(resultTextOf(line(RUN1_LAST)));

        ClaudeCodeStreamParser streamed = new ClaudeCodeStreamParser();
        List<AgentEvent> events = new ArrayList<>();
        for (String frame : lines(RUN1_FIRST, RUN1_LAST)) {
            events.addAll(streamed.parseLine(frame));
        }

        // The run streamed its own prose, so the text-only channel must not repeat it at the end.
        assertThat(count(events, AgentEvent.TextDelta.class)).isEqualTo(RUN1_TEXT_DELTAS);
        assertThat(streamed.fallbackText()).isNull();
        assertThat(streamed.resultFrameSeen()).isTrue();
    }

    @Test
    void suppressingTheAggregatedFramesShouldLoseNoContentTest() {
        List<AgentEvent> events = parse(lines(RUN1_FIRST, RUN1_LAST));

        // Every thinking block, text block and tool call in run 1 arrives twice: streamed as
        // content_block frames and again aggregated in an assistant frame. Exactly one copy survives,
        // and it is the streamed one — so the concatenation of the deltas that got through must equal
        // the concatenation of the aggregated blocks that were suppressed.
        assertThat(joined(events, AgentEvent.ThinkingDelta.class))
                .isEqualTo(aggregatedContent("thinking", 82, 111, 150));
        assertThat(joined(events, AgentEvent.TextDelta.class))
                .isEqualTo(aggregatedContent("text", 119, 212));
        assertThat(count(events, AgentEvent.ToolStart.class)).isEqualTo(2);
        assertThat(count(events, AgentEvent.ToolInputComplete.class)).isEqualTo(2);
        assertThat(count(events, AgentEvent.ToolDone.class)).isEqualTo(2);
    }

    /** The content of every event of one type, concatenated in arrival order. */
    private static String joined(List<AgentEvent> events, Class<?> type) {
        StringBuilder joined = new StringBuilder();
        for (AgentEvent event : events) {
            if (type == AgentEvent.ThinkingDelta.class && event instanceof AgentEvent.ThinkingDelta delta) {
                joined.append(delta.content());
            } else if (type == AgentEvent.TextDelta.class && event instanceof AgentEvent.TextDelta delta) {
                joined.append(delta.content());
            }
        }
        return joined.toString();
    }

    /** The same field of every listed aggregated assistant block, concatenated. */
    private static String aggregatedContent(String field, int... lineNumbers) {
        StringBuilder content = new StringBuilder();
        for (int lineNumber : lineNumbers) {
            content.append(read(line(lineNumber)).path("message").path("content").get(0).path(field).asText());
        }
        return content.toString();
    }

    @Test
    void cliTelemetryAndSignatureFramesShouldProduceNothingTest() {
        List<String> frames = telemetryFrames();

        // Run 1 alone carries 51 thinking_tokens frames, 3 status frames and 3 signature deltas.
        // Routing any of them through the unknown-type guard would push 57 identical warnings into
        // one timeline and bury the unhandled shape that actually matters.
        assertThat(frames).hasSize(RUN1_THINKING_TOKEN_FRAMES + RUN1_STATUS_FRAMES + RUN1_SIGNATURE_DELTAS);
        assertThat(parse(frames)).isEmpty();
    }

    /** Every frame of run 1 that is CLI telemetry or a thinking-block signature. */
    private static List<String> telemetryFrames() {
        List<String> frames = new ArrayList<>();
        for (String frame : lines(RUN1_FIRST, RUN1_LAST)) {
            JsonNode node = read(frame);
            String subtype = node.path("subtype").asText("");
            String deltaType = node.path("event").path("delta").path("type").asText("");
            if ("thinking_tokens".equals(subtype) || "status".equals(subtype)
                    || "signature_delta".equals(deltaType)) {
                frames.add(frame);
            }
        }
        return frames;
    }

    @Test
    void aFailedResumeShouldSayTheSessionIsGoneTest() {
        List<AgentEvent> events = parse(List.of(REAL_RESUME_FAILURE_FRAME));

        // The subtype on its own would render as a generic "agent run ended with subtype" error. The
        // reason the user actually needs is in errors[]: this conversation's runtime session is gone
        // and the next turn must start a new one instead of resuming.
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOfSatisfying(AgentEvent.ProviderNotice.class, notice -> {
            assertThat(notice.level()).isEqualTo(AgentEventProjector.LEVEL_ERROR);
            assertThat(notice.message())
                    .contains("No conversation found with session ID: " + SESSION);
        });
        assertThat(events.get(1)).isEqualTo(
                new AgentEvent.ResultMeta(SESSION, 0L, 0, 0, "error_during_execution"));
    }

    @Test
    void aLongFailedToolResultShouldBeAbbreviatedOnCodePointBoundariesTest() {
        // A tool result quotes a message body, which is end-user text, so an emoji can sit on the
        // 512th char of the reason the user is shown. 511 chars put U+1F600's high surrogate exactly
        // on that cut, and the tail is what the cap has to drop whole.
        String payload = "x".repeat(511) + EMOJI + "tail";
        String frame = "{\"type\":\"user\",\"session_id\":\"s-1\",\"message\":{\"role\":\"user\","
                + "\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"toolu_long\","
                + "\"is_error\":true,\"content\":\"" + payload + "\"}]}}";

        List<AgentEvent> events = parse(List.of(frame));

        assertThat(events).singleElement().isInstanceOfSatisfying(AgentEvent.ToolDone.class, done -> {
            assertThat(done.success()).isFalse();
            // A char-based cut would keep the high surrogate on its own and turn it into a
            // replacement character once the event reaches the timeline.
            assertThat(done.error()).isEqualTo("x".repeat(511) + EMOJI + "...");
        });
    }

    @Test
    void permissionDenialsShouldSurfaceAsAWarningTest() {
        List<AgentEvent> events = parse(List.of(RESULT_DENIED_FRAME));

        // Without --allowedTools the CLI silently auto-denies MCP calls: is_error stays false, the
        // subtype stays success and the exit code stays 0. permission_denials is the only trace, so
        // it must become a notice the user cannot miss, ahead of the ResultMeta that ends the run.
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOfSatisfying(AgentEvent.ProviderNotice.class, notice -> {
            assertThat(notice.level()).isEqualTo(AgentEventProjector.LEVEL_WARN);
            assertThat(notice.message())
                    .contains("auto-denied 1 tool call")
                    .contains("mcp__rocketmq-studio__rmq_topic_list");
        });
        assertThat(events.get(1)).isEqualTo(new AgentEvent.ResultMeta("s-1", 5L, 1, 2, "success"));
    }

    @Test
    void apiErrorStatusShouldSurfaceAsAnErrorNoticeTest() {
        List<AgentEvent> events = parse(List.of(RESULT_API_ERROR_FRAME));

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOfSatisfying(AgentEvent.ProviderNotice.class, notice -> {
            assertThat(notice.level()).isEqualTo(AgentEventProjector.LEVEL_ERROR);
            assertThat(notice.message()).contains("429");
        });
        assertThat(events.get(1)).isEqualTo(
                new AgentEvent.ResultMeta("s-1", 7L, 3, 4, "error_during_execution"));
    }

    @Test
    void anEmptyPermissionDenialsListShouldStaySilentTest() {
        // The real capture reports an empty list on both runs; that is the normal case, not a warning.
        List<AgentEvent> events = parse(List.of(line(RUN1_LAST), line(RUN2_LAST)));

        assertThat(count(events, AgentEvent.ProviderNotice.class)).isZero();
    }

    /**
     * The second silent-failure mode, and the reason a second capture exists.
     *
     * <p>{@code permission_denials} covers a CLI that refused to call a tool it had. This covers the
     * worse case: rmqctl was not reachable, so {@code claude} started with
     * {@code mcp_servers[0].status == "failed"}, was never offered a single {@code mcp__} tool, answered
     * from the model alone, and then reported {@code subtype:"success"} with {@code is_error:false}, an
     * empty {@code permission_denials} and exit code 0. Every signal a caller would normally watch says
     * the run was fine. The {@code system/init} frame is the only place the truth appears, so the parser
     * must not list a server that failed to connect as connected.
     */
    @Test
    void anMcpServerThatFailedToConnectShouldBeReportedAsNotConnectedTest() {
        // Guards on the fixture itself, for the same reason as everyCapturedLineShouldBeAccountedForTest:
        // if this capture is ever re-recorded, the line numbers below move with it.
        assertThat(MCP_FAILED).hasSize(MCP_FAILED_LINES_COUNT);
        JsonNode init = read(mcpFailedLine(MCP_FAILED_INIT_LINE));
        assertThat(init.path("subtype").asText()).isEqualTo("init");
        assertThat(init.path("mcp_servers").get(0).path("name").asText())
                .isEqualTo(AgentEventProjector.STUDIO_MCP_SERVER);
        assertThat(init.path("mcp_servers").get(0).path("status").asText()).isEqualTo("failed");
        JsonNode result = read(mcpFailedLine(MCP_FAILED_RESULT_LINE));
        assertThat(result.path("subtype").asText()).isEqualTo("success");
        assertThat(result.path("is_error").asBoolean()).isFalse();
        assertThat(result.path("permission_denials").size()).isZero();

        List<AgentEvent> events = parse(MCP_FAILED);

        assertThat(events).first().isInstanceOfSatisfying(AgentEvent.InitMeta.class, meta -> {
            assertThat(meta.runtimeSessionId()).isEqualTo(MCP_FAILED_SESSION);
            // Empty, not null: the projector warns on a list that lacks our server and stays quiet on a
            // null list, so an empty list is the parser saying "the CLI answered, and it was not there".
            assertThat(meta.connectedMcpServers()).isEmpty();
            assertThat(meta.availableTools())
                    .hasSize(MCP_FAILED_VISIBLE_TOOLS)
                    .noneMatch(tool -> tool.startsWith("mcp__"));
        });

        // Everything else about the run looks healthy, which is exactly what makes the failure silent.
        assertThat(events).last().isEqualTo(
                new AgentEvent.ResultMeta(MCP_FAILED_SESSION, 10_902L, 1_075, 414, "success"));
        assertThat(count(events, AgentEvent.ProviderNotice.class)).isZero();
        assertThat(count(events, AgentEvent.UnhandledUpstream.class)).isZero();
        assertThat(count(events, AgentEvent.ToolStart.class)).isZero();
        assertThat(count(events, AgentEvent.ToolDone.class)).isZero();
        assertThat(count(events, AgentEvent.ThinkingDelta.class)).isEqualTo(MCP_FAILED_THINKING_DELTAS);
        assertThat(count(events, AgentEvent.TextDelta.class)).isEqualTo(MCP_FAILED_TEXT_DELTAS);
        assertThat(events).hasSize(MCP_FAILED_THINKING_DELTAS + MCP_FAILED_TEXT_DELTAS + 2);

        // And the projector, given only what the parser produced, tells the user the tools were not there.
        AgentEventProjector.Projection projection = new AgentEventProjector(1L).project(events.get(0));
        assertThat(projection.live()).isEqualTo(new LiveEvent.Notice(AgentEventProjector.LEVEL_WARN,
                "MCP server " + AgentEventProjector.STUDIO_MCP_SERVER + " did not connect"));
    }

    @Test
    void everyCapturedLineShouldBeAccountedForTest() {
        // A guard on the fixture itself: if the capture is ever re-recorded, the line numbers this
        // test suite is built on move, and every case above would silently test the wrong frame.
        assertThat(LINES).hasSize(278);
        assertThat(read(line(1)).path("subtype").asText()).isEqualTo("init");
        assertThat(read(line(217)).has("type")).isFalse();
        assertThat(read(line(RUN2_LAST)).path("type").asText()).isEqualTo("result");
    }

    private static List<AgentEvent> parse(List<String> frames) {
        ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser();
        List<AgentEvent> events = new ArrayList<>();
        for (String frame : frames) {
            events.addAll(parser.parseLine(frame));
        }
        return events;
    }

    private static int count(List<AgentEvent> events, Class<?> type) {
        return (int) events.stream().filter(type::isInstance).count();
    }

    private static String line(int number) {
        return LINES.get(number - 1);
    }

    private static String mcpFailedLine(int number) {
        return MCP_FAILED.get(number - 1);
    }

    private static List<String> lines(int from, int toInclusive) {
        return LINES.subList(from - 1, toInclusive);
    }

    private static JsonNode read(String frame) {
        try {
            return JSON.readTree(frame);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<String> toolsOf(String frame) {
        List<String> tools = new ArrayList<>();
        read(frame).path("tools").forEach(tool -> tools.add(tool.asText()));
        return List.copyOf(tools);
    }

    private static String toolResultOf(String frame) {
        return read(frame).path("message").path("content").get(0).path("content").asText();
    }

    private static String thinkingOf(String frame) {
        return read(frame).path("message").path("content").get(0).path("thinking").asText();
    }

    private static String textOf(String frame) {
        return read(frame).path("message").path("content").get(0).path("text").asText();
    }

    private static String resultTextOf(String frame) {
        return read(frame).path("result").asText();
    }

    private static List<String> loadLines(String resource) {
        try (InputStream in = ClaudeCodeStreamParserTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource " + resource);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
                lines.remove(lines.size() - 1);
            }
            return List.copyOf(lines);
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot read " + resource, exception);
        }
    }
}
