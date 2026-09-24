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
package org.apache.rocketmq.studio.ops.ai.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.apache.rocketmq.studio.ops.ai.conversation.dto.AiConversationCreateDTO;
import org.apache.rocketmq.studio.ops.ai.conversation.dto.AiConversationUpdateDTO;
import org.apache.rocketmq.studio.ops.ai.conversation.dto.AiMessageDTO;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiActiveRunRef;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiAgentCapabilitiesVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiConversationDetailVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiConversationListItemVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiConversationVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiRmqctlConfigVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiRunVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiTimelineItemVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiTimelineVO;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the entity-to-VO mapping AND the serialised JSON shape of the conversation REST surface.
 *
 * <p>The JSON assertions are the point of this class: the VO field names are a frozen contract with
 * {@code web/src/api/aiEvents.ts} / {@code aiConversations.ts}, so every VO is serialised and its
 * tree compared against the literal JSON the frontend expects — through BOTH mappers the server can
 * hand a response to (Jackson 3, which is the Spring Boot 4 REST default, and the injected Jackson 2
 * {@link LegacyJackson2Config} mapper). A rename, a casing slip or a timestamp format change fails
 * here instead of silently breaking the UI.
 *
 * <p>Trees are compared after re-parsing the written JSON so number nodes have identical types on
 * both sides regardless of which boxed Java type produced them.
 */
class AiConversationVoAssemblerTest {

    private static final ObjectMapper JACKSON2 = new LegacyJackson2Config().jackson2ObjectMapper();
    private static final JsonMapper JACKSON3 = JsonMapper.builder().build();

    private static final String TOOL_RESULT_PAYLOAD =
            "{\"type\":\"tool_result\",\"tcId\":\"tc-9\",\"tool\":\"topic_list\",\"output\":\"{}\","
                    + "\"outputBytes\":2,\"truncated\":false,\"success\":true,\"durationMs\":12}";

    // ─── Expected wire shapes (literal mirrors of the frozen TS interfaces) ───

    private static final String CONVERSATION_JSON = """
            {
              "id": 42,
              "title": "consumer lag investigation",
              "owner": "terrance",
              "engine": "claude",
              "model": "claude-sonnet-4-5",
              "mode": "chat",
              "instanceId": "rmq-local",
              "runtimeSessionId": "session-123",
              "lastSeq": 17,
              "archived": false,
              "createdAt": "2026-09-15T08:30:15",
              "updatedAt": "2026-09-15T09:00:00"
            }
            """;

    private static final String DETAIL_JSON = """
            {
              "id": 42,
              "title": "consumer lag investigation",
              "owner": "terrance",
              "engine": "claude",
              "model": "claude-sonnet-4-5",
              "mode": "chat",
              "instanceId": "rmq-local",
              "runtimeSessionId": "session-123",
              "lastSeq": 17,
              "archived": false,
              "createdAt": "2026-09-15T08:30:15",
              "updatedAt": "2026-09-15T09:00:00",
              "activeRun": { "id": 7, "status": "RUNNING" }
            }
            """;

    private static final String LIST_ITEM_JSON = """
            {
              "id": 42,
              "title": "consumer lag investigation",
              "engine": "claude",
              "model": "claude-sonnet-4-5",
              "mode": "chat",
              "instanceId": "rmq-local",
              "lastRunId": 7,
              "lastRunStatus": "COMPLETED",
              "updatedAt": "2026-09-15T09:00:00",
              "createdAt": "2026-09-15T08:30:15"
            }
            """;

    private static final String RUN_JSON = """
            {
              "id": 7,
              "conversationId": 42,
              "turn": 3,
              "status": "STOPPED",
              "engine": "claude",
              "model": "claude-sonnet-4-5",
              "startedAt": "2026-09-15T08:30:15.123456",
              "finishedAt": "2026-09-15T08:31:20",
              "durationMs": 64877,
              "inputTokens": 1200,
              "outputTokens": 340,
              "stopReason": "USER_STOP",
              "errorCode": "ai.run.stopped",
              "errorMessage": "stopped by user"
            }
            """;

    private static final String TIMELINE_ITEM_JSON = """
            {
              "id": 900,
              "turn": 3,
              "seq": 17,
              "createdAt": "2026-09-15T08:30:15",
              "runId": 7,
              "event": {
                "type": "tool_result",
                "tcId": "tc-9",
                "tool": "topic_list",
                "output": "{}",
                "outputBytes": 2,
                "truncated": false,
                "success": true,
                "durationMs": 12
              }
            }
            """;

    private static final String TIMELINE_JSON = """
            {
              "items": [
                {
                  "id": 900,
                  "turn": 3,
                  "seq": 17,
                  "createdAt": "2026-09-15T08:30:15",
                  "runId": 7,
                  "event": {
                    "type": "tool_result",
                    "tcId": "tc-9",
                    "tool": "topic_list",
                    "output": "{}",
                    "outputBytes": 2,
                    "truncated": false,
                    "success": true,
                    "durationMs": 12
                  }
                }
              ],
              "nextAfter": 17,
              "activeRun": { "id": 7, "status": "RUNNING" },
              "runs": []
            }
            """;

    private static final String CAPABILITIES_JSON = """
            {
              "rmqctlAvailable": true,
              "claudeAvailable": true,
              "qoderAvailable": false,
              "mcpEnabled": true,
              "l3ToolsAllowed": false
            }
            """;

    private static final String RMQCTL_CONFIG_JSON = """
            {
              "snippet": "{\\\"mcpServers\\\": {}}",
              "instanceId": "rmq-local",
              "server": "http://127.0.0.1:8888"
            }
            """;

    // ─── Mapping: conversation ───────────────────────────────────

    @Test
    void conversationVoMapsEveryFieldTest() {
        AiConversationVO vo = AiConversationVoAssembler.toConversationVo(conversation());

        assertThat(vo.getId()).isEqualTo(42L);
        assertThat(vo.getTitle()).isEqualTo("consumer lag investigation");
        assertThat(vo.getOwner()).isEqualTo("terrance");
        assertThat(vo.getEngine()).isEqualTo("claude");
        assertThat(vo.getModel()).isEqualTo("claude-sonnet-4-5");
        assertThat(vo.getMode()).isEqualTo("chat");
        assertThat(vo.getInstanceId()).isEqualTo("rmq-local");
        assertThat(vo.getRuntimeSessionId()).isEqualTo("session-123");
        assertThat(vo.getLastSeq()).isEqualTo(17);
        assertThat(vo.isArchived()).isFalse();
        // the frozen contract renames gmtCreate/gmtModified to createdAt/updatedAt
        assertThat(vo.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 8, 30, 15));
        assertThat(vo.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 9, 0, 0));
    }

    @Test
    void conversationVoDefaultsNullableColumnsTest() {
        RmqAiConversation entity = conversation();
        entity.setInstanceId(null);
        entity.setRuntimeSessionId(null);
        entity.setLastSeq(null);
        entity.setArchived(null);

        AiConversationVO vo = AiConversationVoAssembler.toConversationVo(entity);

        assertThat(vo.getInstanceId()).isNull();
        assertThat(vo.getRuntimeSessionId()).isNull();
        // both are required (non-null) numbers/booleans in the TS contract
        assertThat(vo.getLastSeq()).isZero();
        assertThat(vo.isArchived()).isFalse();
    }

    @Test
    void conversationDetailVoCarriesParentFieldsPlusActiveRunTest() {
        AiConversationDetailVO vo =
                AiConversationVoAssembler.toConversationDetailVo(conversation(), runningRun());

        // the TS type is `interface AiConversationDetailVO extends AiConversationVO` — every parent
        // field must survive the trip through the subclass
        assertThat(vo.getId()).isEqualTo(42L);
        assertThat(vo.getTitle()).isEqualTo("consumer lag investigation");
        assertThat(vo.getOwner()).isEqualTo("terrance");
        assertThat(vo.getEngine()).isEqualTo("claude");
        assertThat(vo.getModel()).isEqualTo("claude-sonnet-4-5");
        assertThat(vo.getMode()).isEqualTo("chat");
        assertThat(vo.getInstanceId()).isEqualTo("rmq-local");
        assertThat(vo.getRuntimeSessionId()).isEqualTo("session-123");
        assertThat(vo.getLastSeq()).isEqualTo(17);
        assertThat(vo.isArchived()).isFalse();
        assertThat(vo.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 8, 30, 15));
        assertThat(vo.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 9, 0, 0));
        assertThat(vo.getActiveRun())
                .isEqualTo(AiActiveRunRef.builder().id(7L).status(RunStatus.RUNNING).build());
    }

    @Test
    void conversationDetailVoWithoutActiveRunTest() {
        AiConversationDetailVO vo =
                AiConversationVoAssembler.toConversationDetailVo(conversation(), null);

        assertThat(vo.getActiveRun()).isNull();
        assertThat(vo.getId()).isEqualTo(42L);
    }

    // ─── Mapping: list item ──────────────────────────────────────

    @Test
    void listItemVoMapsLastRunTest() {
        AiConversationListItemVO vo =
                AiConversationVoAssembler.toListItemVo(conversation(), completedRun());

        assertThat(vo.getId()).isEqualTo(42L);
        assertThat(vo.getTitle()).isEqualTo("consumer lag investigation");
        assertThat(vo.getEngine()).isEqualTo("claude");
        assertThat(vo.getModel()).isEqualTo("claude-sonnet-4-5");
        assertThat(vo.getMode()).isEqualTo("chat");
        assertThat(vo.getInstanceId()).isEqualTo("rmq-local");
        assertThat(vo.getLastRunId()).isEqualTo(7L);
        assertThat(vo.getLastRunStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(vo.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 8, 30, 15));
        assertThat(vo.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 9, 0, 0));
    }

    @Test
    void listItemVoWithoutLastRunTest() {
        AiConversationListItemVO vo = AiConversationVoAssembler.toListItemVo(conversation(), null);

        assertThat(vo.getLastRunId()).isNull();
        assertThat(vo.getLastRunStatus()).isNull();
    }

    @Test
    void listItemVoExposesUnknownLastRunStatusAsNullTest() {
        RmqAiRun corrupted = completedRun();
        corrupted.setStatus("EXPLODED");

        AiConversationListItemVO vo = AiConversationVoAssembler.toListItemVo(conversation(), corrupted);

        // lastRunStatus is optional in the TS contract, so unreadable data degrades instead of failing
        assertThat(vo.getLastRunId()).isEqualTo(7L);
        assertThat(vo.getLastRunStatus()).isNull();
    }

    // ─── Mapping: run ────────────────────────────────────────────

    @Test
    void runVoMapsEveryFieldTest() {
        AiRunVO vo = AiConversationVoAssembler.toRunVo(stoppedRun());

        assertThat(vo.getId()).isEqualTo(7L);
        assertThat(vo.getConversationId()).isEqualTo(42L);
        assertThat(vo.getTurn()).isEqualTo(3);
        assertThat(vo.getStatus()).isEqualTo(RunStatus.STOPPED);
        assertThat(vo.getEngine()).isEqualTo("claude");
        assertThat(vo.getModel()).isEqualTo("claude-sonnet-4-5");
        assertThat(vo.getStartedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 8, 30, 15, 123_456_000));
        assertThat(vo.getFinishedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 8, 31, 20));
        assertThat(vo.getDurationMs()).isEqualTo(64_877L);
        assertThat(vo.getInputTokens()).isEqualTo(1200);
        assertThat(vo.getOutputTokens()).isEqualTo(340);
        assertThat(vo.getStopReason()).isEqualTo(StopReason.USER_STOP);
        assertThat(vo.getErrorCode()).isEqualTo("ai.run.stopped");
        assertThat(vo.getErrorMessage()).isEqualTo("stopped by user");
    }

    @Test
    void runVoKeepsNullableColumnsNullTest() {
        RmqAiRun entity = new RmqAiRun();
        entity.setId(8L);
        entity.setConversationId(42L);
        entity.setTurn(1);
        entity.setStatus("QUEUED");
        entity.setEngine("claude");
        entity.setModel("claude-sonnet-4-5");
        // startedAt, finishedAt, durationMs, tokens, stopReason, error columns all stay null

        AiRunVO vo = AiConversationVoAssembler.toRunVo(entity);

        assertThat(vo.getStatus()).isEqualTo(RunStatus.QUEUED);
        assertThat(vo.getStartedAt()).isNull();
        assertThat(vo.getFinishedAt()).isNull();
        assertThat(vo.getDurationMs()).isNull();
        assertThat(vo.getInputTokens()).isNull();
        assertThat(vo.getOutputTokens()).isNull();
        assertThat(vo.getStopReason()).isNull();
        assertThat(vo.getErrorCode()).isNull();
        assertThat(vo.getErrorMessage()).isNull();
    }

    @Test
    void runVoExposesUnknownStopReasonAsNullTest() {
        RmqAiRun entity = stoppedRun();
        entity.setStopReason("SOLAR_FLARE");

        AiRunVO vo = AiConversationVoAssembler.toRunVo(entity);

        assertThat(vo.getStopReason()).isNull();
    }

    // ─── Mapping: active run ref ─────────────────────────────────

    @Test
    void activeRunRefMappingTest() {
        assertThat(AiConversationVoAssembler.toActiveRunRef(null)).isNull();

        AiActiveRunRef ref = AiConversationVoAssembler.toActiveRunRef(runningRun());

        assertThat(ref.getId()).isEqualTo(7L);
        assertThat(ref.getStatus()).isEqualTo(RunStatus.RUNNING);
    }

    // ─── Mapping: timeline ───────────────────────────────────────

    @Test
    void timelineItemVoDecodesPayloadTest() {
        AiTimelineItemVO vo = AiConversationVoAssembler.toTimelineItemVo(JACKSON2, eventRow());

        assertThat(vo.getId()).isEqualTo(900L);
        assertThat(vo.getTurn()).isEqualTo(3);
        assertThat(vo.getSeq()).isEqualTo(17);
        assertThat(vo.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 8, 30, 15));
        // runId is required by the TS contract (rmq_ai_event.run_id is NOT NULL)
        assertThat(vo.getRunId()).isEqualTo(7L);
        assertThat(vo.getEvent()).isInstanceOf(TimelineEvent.ToolResult.class);
        TimelineEvent.ToolResult result = (TimelineEvent.ToolResult) vo.getEvent();
        assertThat(result.tcId()).isEqualTo("tc-9");
        assertThat(result.tool()).isEqualTo("topic_list");
        assertThat(result.output()).isEqualTo("{}");
        assertThat(result.outputBytes()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
        assertThat(result.success()).isTrue();
        assertThat(result.durationMs()).isEqualTo(12L);
        assertThat(result.error()).isNull();
    }

    @Test
    void timelineItemVoFallsBackOnUnreadablePayloadTest() {
        RmqAiEvent corrupted = eventRow();
        corrupted.setPayload("{not json at all");

        AiTimelineItemVO vo = AiConversationVoAssembler.toTimelineItemVo(JACKSON2, corrupted);

        // one bad row must not cost the user the whole timeline: placeholder notice, envelope intact
        assertThat(vo.getId()).isEqualTo(900L);
        assertThat(vo.getRunId()).isEqualTo(7L);
        assertThat(vo.getEvent()).isInstanceOf(TimelineEvent.Notice.class);
        TimelineEvent.Notice notice = (TimelineEvent.Notice) vo.getEvent();
        assertThat(notice.level()).isEqualTo(AgentEventProjector.LEVEL_WARN);
        assertThat(notice.message()).isEqualTo(AiConversationVoAssembler.UNREADABLE_EVENT_MESSAGE);
    }

    @Test
    void timelineItemVoFallsBackOnMissingPayloadTest() {
        RmqAiEvent empty = eventRow();
        empty.setPayload(null);

        AiTimelineItemVO vo = AiConversationVoAssembler.toTimelineItemVo(JACKSON2, empty);

        assertThat(vo.getEvent()).isInstanceOf(TimelineEvent.Notice.class);
    }

    @Test
    void timelineVoMappingTest() {
        AiTimelineItemVO item = AiConversationVoAssembler.toTimelineItemVo(JACKSON2, eventRow());

        AiTimelineVO vo = AiConversationVoAssembler.toTimelineVo(List.of(item), 17, runningRun());

        assertThat(vo.getItems()).containsExactly(item);
        assertThat(vo.getNextAfter()).isEqualTo(17);
        assertThat(vo.getActiveRun())
                .isEqualTo(AiActiveRunRef.builder().id(7L).status(RunStatus.RUNNING).build());
    }

    @Test
    void timelineVoDefaultsNullInputsTest() {
        AiTimelineVO vo = AiConversationVoAssembler.toTimelineVo(null, null, null);

        assertThat(vo.getItems()).isEmpty();
        assertThat(vo.getNextAfter()).isNull();
        assertThat(vo.getActiveRun()).isNull();
    }

    // --- Mapping: what the REST surface actually hands over ---------------------

    @Test
    void timelineItemVoMapsAnAlreadyDecodedServiceItemTest() {
        AiTimelineItemVO vo = AiConversationVoAssembler.toTimelineItemVo(serviceItem());

        // id and runId are the two components the contract requires and the service record used to drop
        assertThat(vo.getId()).isEqualTo(900L);
        assertThat(vo.getTurn()).isEqualTo(3);
        assertThat(vo.getSeq()).isEqualTo(17);
        assertThat(vo.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 8, 30, 15));
        assertThat(vo.getRunId()).isEqualTo(7L);
        assertThat(vo.getEvent()).isInstanceOf(TimelineEvent.ToolResult.class);
    }

    @Test
    void timelineVoMapsAWholeServicePageTest() {
        AiConversationService.TimelinePage page = new AiConversationService.TimelinePage(
                List.of(serviceItem()), 17, runningRun());

        AiTimelineVO vo = AiConversationVoAssembler.toTimelineVo(page);

        assertThat(vo.getItems())
                .containsExactly(AiConversationVoAssembler.toTimelineItemVo(serviceItem()));
        assertThat(vo.getNextAfter()).isEqualTo(17);
        assertThat(vo.getActiveRun())
                .isEqualTo(AiActiveRunRef.builder().id(7L).status(RunStatus.RUNNING).build());
    }

    @Test
    void timelineVoTreatsANullServicePageAsEmptyTest() {
        AiTimelineVO vo = AiConversationVoAssembler.toTimelineVo((AiConversationService.TimelinePage) null);

        assertThat(vo.getItems()).isEmpty();
        assertThat(vo.getNextAfter()).isNull();
        assertThat(vo.getActiveRun()).isNull();
    }

    // ─── The wire contract itself ────────────────────────────────

    @Test
    void serialisedJsonMatchesTheFrozenTypeScriptContractTest() throws Exception {
        assertJsonMatchesContract(CONVERSATION_JSON,
                AiConversationVoAssembler.toConversationVo(conversation()));
        assertJsonMatchesContract(DETAIL_JSON,
                AiConversationVoAssembler.toConversationDetailVo(conversation(), runningRun()));
        assertJsonMatchesContract(LIST_ITEM_JSON,
                AiConversationVoAssembler.toListItemVo(conversation(), completedRun()));
        assertJsonMatchesContract(RUN_JSON,
                AiConversationVoAssembler.toRunVo(stoppedRun()));
        assertJsonMatchesContract(TIMELINE_ITEM_JSON,
                AiConversationVoAssembler.toTimelineItemVo(JACKSON2, eventRow()));
        assertJsonMatchesContract(TIMELINE_JSON,
                AiConversationVoAssembler.toTimelineVo(
                        List.of(AiConversationVoAssembler.toTimelineItemVo(JACKSON2, eventRow())),
                        17, runningRun()));
        // The same envelope built from the service page, which is the path the controller takes: the two
        // mappings must produce one identical wire shape, not two similar ones.
        assertJsonMatchesContract(TIMELINE_JSON, AiConversationVoAssembler.toTimelineVo(
                new AiConversationService.TimelinePage(List.of(serviceItem()), 17, runningRun())));
        assertJsonMatchesContract(CAPABILITIES_JSON,
                AiAgentCapabilitiesVO.builder()
                        .rmqctlAvailable(true)
                        .claudeAvailable(true)
                        .qoderAvailable(false)
                        .mcpEnabled(true)
                        .l3ToolsAllowed(false)
                        .build());
        assertJsonMatchesContract(RMQCTL_CONFIG_JSON,
                AiRmqctlConfigVO.builder()
                        .snippet("{\"mcpServers\": {}}")
                        .instanceId("rmq-local")
                        .server("http://127.0.0.1:8888")
                        .build());
    }

    @Test
    void requestDtoFieldNamesMatchTheFrozenTypeScriptContractTest() throws Exception {
        AiConversationCreateDTO create = readWithBothMappers(
                "{\"instanceId\": \"rmq-local\", \"mode\": \"chat\"}", AiConversationCreateDTO.class);
        assertThat(create)
                .isEqualTo(AiConversationCreateDTO.builder().instanceId("rmq-local").mode("chat").build());

        AiConversationUpdateDTO update = readWithBothMappers(
                "{\"title\": \"renamed\", \"archived\": true}", AiConversationUpdateDTO.class);
        assertThat(update)
                .isEqualTo(AiConversationUpdateDTO.builder().title("renamed").archived(true).build());

        AiMessageDTO message = readWithBothMappers(
                "{\"message\": \"why is the lag growing\", \"model\": \"claude-sonnet-4-5\", \"engine\": \"claude\","
                        + " \"mode\": \"chat\", \"enhance\": true, \"resume\": false}",
                AiMessageDTO.class);
        assertThat(message).isEqualTo(AiMessageDTO.builder()
                .message("why is the lag growing")
                .model("claude-sonnet-4-5")
                .engine("claude")
                .mode("chat")
                .enhance(true)
                .resume(false)
                .build());
    }

    // ─── Helpers ─────────────────────────────────────────────────

    /**
     * Compares trees, not strings: both the expected literal and the written VO are parsed by the
     * same mapper first, so field order and number node types cannot cause a false failure while a
     * renamed or missing field still does. Runs against both mappers the server may answer with.
     */
    private static void assertJsonMatchesContract(String expectedJson, Object value) throws Exception {
        tools.jackson.databind.JsonNode expected3 = JACKSON3.readTree(expectedJson);
        tools.jackson.databind.JsonNode actual3 = JACKSON3.readTree(JACKSON3.writeValueAsString(value));
        assertThat(actual3).as("jackson3 tree of %s", value.getClass().getSimpleName())
                .isEqualTo(expected3);

        JsonNode expected2 = JACKSON2.readTree(expectedJson);
        JsonNode actual2 = JACKSON2.readTree(JACKSON2.writeValueAsString(value));
        assertThat(actual2).as("jackson2 tree of %s", value.getClass().getSimpleName())
                .isEqualTo(expected2);
    }

    private static <T> T readWithBothMappers(String json, Class<T> type) throws Exception {
        T fromJackson3 = JACKSON3.readValue(json, type);
        T fromJackson2 = JACKSON2.readValue(json, type);
        assertThat(fromJackson2).as("both mappers must bind %s identically", type.getSimpleName())
                .isEqualTo(fromJackson3);
        return fromJackson2;
    }

    private static RmqAiConversation conversation() {
        RmqAiConversation entity = new RmqAiConversation();
        entity.setId(42L);
        entity.setTitle("consumer lag investigation");
        entity.setOwner("terrance");
        entity.setEngine("claude");
        entity.setModel("claude-sonnet-4-5");
        entity.setMode("chat");
        entity.setInstanceId("rmq-local");
        entity.setRuntimeSessionId("session-123");
        entity.setLastSeq(17);
        entity.setArchived(false);
        entity.setGmtCreate(LocalDateTime.of(2026, 9, 15, 8, 30, 15));
        entity.setGmtModified(LocalDateTime.of(2026, 9, 15, 9, 0, 0));
        return entity;
    }

    private static RmqAiRun stoppedRun() {
        RmqAiRun entity = new RmqAiRun();
        entity.setId(7L);
        entity.setConversationId(42L);
        entity.setTurn(3);
        entity.setStatus("STOPPED");
        entity.setEngine("claude");
        entity.setModel("claude-sonnet-4-5");
        entity.setStartedAt(LocalDateTime.of(2026, 9, 15, 8, 30, 15, 123_456_000));
        entity.setFinishedAt(LocalDateTime.of(2026, 9, 15, 8, 31, 20));
        entity.setDurationMs(64_877L);
        entity.setInputTokens(1200);
        entity.setOutputTokens(340);
        entity.setStopReason("USER_STOP");
        entity.setErrorCode("ai.run.stopped");
        entity.setErrorMessage("stopped by user");
        return entity;
    }

    private static RmqAiRun runningRun() {
        RmqAiRun entity = new RmqAiRun();
        entity.setId(7L);
        entity.setConversationId(42L);
        entity.setTurn(3);
        entity.setStatus("RUNNING");
        entity.setEngine("claude");
        entity.setModel("claude-sonnet-4-5");
        return entity;
    }

    private static RmqAiRun completedRun() {
        RmqAiRun entity = new RmqAiRun();
        entity.setId(7L);
        entity.setConversationId(42L);
        entity.setTurn(3);
        entity.setStatus("COMPLETED");
        entity.setEngine("claude");
        entity.setModel("claude-sonnet-4-5");
        return entity;
    }

    private static RmqAiEvent eventRow() {
        RmqAiEvent row = new RmqAiEvent();
        row.setId(900L);
        row.setConversationId(42L);
        row.setRunId(7L);
        row.setTurn(3);
        row.setSeq(17);
        row.setType("tool_result");
        row.setPayload(TOOL_RESULT_PAYLOAD);
        row.setGmtCreate(LocalDateTime.of(2026, 9, 15, 8, 30, 15));
        return row;
    }

    /**
     * The service-side view of {@link #eventRow()}: the same numbers, payload already decoded. Decoded
     * through {@link AiEventCodec} rather than written as a second literal, so this fixture and the row
     * above cannot disagree about what the event is.
     */
    private static AiConversationService.TimelineItem serviceItem() {
        RmqAiEvent row = eventRow();
        TimelineEvent event = AiEventCodec.read(JACKSON2, row).orElseThrow();
        return new AiConversationService.TimelineItem(row.getId(), row.getTurn(), row.getSeq(),
                row.getGmtCreate(), row.getRunId(), event);
    }
}
