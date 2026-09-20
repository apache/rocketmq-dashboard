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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.WebMvcAuthTestSupport;
import org.apache.rocketmq.studio.auth.LoginVO;
import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.AgentCapabilityProbe;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.RmqctlWorkspace;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.apache.rocketmq.studio.ops.ai.conversation.event.ThinkingSource;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiConversationListItemVO;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The nine JSON endpoints of {@link AiConversationController}.
 *
 * <p>What this class pins, beyond "it answers 200":
 * <ul>
 *   <li>the {@code Result<T>} envelope on every one of them — the frontend unwraps {@code data} and
 *       turns any other {@code code} into a toast, so a controller returning a bare VO would silently
 *       render nothing;</li>
 *   <li>the frozen field names and casing of the VOs the TypeScript contract declares
 *       ({@code createdAt}/{@code updatedAt} rather than the entity's {@code gmtCreate}/
 *       {@code gmtModified}, {@code status} and {@code stopReason} uppercase, {@code ThinkingSource}
 *       lowercase, the timeline item's <em>required</em> {@code runId} and its <em>deserialised</em>
 *       {@code event});</li>
 *   <li>that owner scoping is applied to every id in a path, and that a conversation belonging to
 *       somebody else is a 404 and not a 403 — a 403 would turn the auto-increment primary key into an
 *       enumeration oracle;</li>
 *   <li>jakarta validation on each request DTO, so an oversized title is a 400 carrying the
 *       constraint's message instead of a database error.</li>
 * </ul>
 *
 * <p>The two SSE endpoints live in {@code AiStreamControllerTest}: they are the only ones that do not
 * answer with an envelope.
 */
@WebMvcTest(AiConversationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(LegacyJackson2Config.class)
class AiConversationControllerTest extends WebMvcAuthTestSupport {

    private static final String OWNER = "terrance";
    private static final long CONVERSATION_ID = 7L;
    private static final long RUN_ID = 11L;

    /**
     * Timestamps with non-zero seconds where the exact text matters: the wire format is ISO-8601
     * without an offset, which is what the frontend's {@code formatUtcDateTime} reads.
     */
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 15, 8, 30, 15);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 9, 15, 9, 0, 0);

    private static final String SNIPPET = "{\"mcpServers\":{\"rocketmq-studio\":{\"command\":\"rmqctl\","
            + "\"args\":[\"mcp\",\"stdio\",\"--instance-id\",\"rmq-local\",\"--timeout\",\"1m0s\"]}}}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiConversationService conversationService;

    @MockitoBean
    private AiRunService runService;

    @MockitoBean
    private RmqctlWorkspace rmqctlWorkspace;

    @MockitoBean
    private AgentCapabilityProbe capabilityProbe;

    @BeforeEach
    @Override
    protected void configureControllerSliceAuth() {
        super.configureControllerSliceAuth();
        // AuthWebConfig is a WebMvcConfigurer, so this slice registers AuthInterceptor: its preHandle
        // clears AuthenticatedUserContext before the handler runs, which is why the operator is signed
        // in through the interceptor rather than by setting the thread-local directly. Login has to be
        // required for it to do that at all, and the operator has to be a writer because every write
        // endpoint of this controller is admin-only.
        when(authProperties.isLoginRequired()).thenReturn(true);
        when(authService.getAuthenticatedUser(any())).thenReturn(Optional.of(LoginVO.UserInfo.builder()
                .userId(1L)
                .username(OWNER)
                .admin(true)
                .build()));
    }

    // --- 1. POST /api/ai/conversations -----------------------------------------

    @Test
    void createConversationShouldReturnTheResultEnvelopeTest() throws Exception {
        when(conversationService.create(eq(OWNER), any(), any())).thenReturn(conversation("rmq-local"));

        mockMvc.perform(post("/api/ai/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instanceId\":\"rmq-local\",\"mode\":\"chat\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(CONVERSATION_ID))
                .andExpect(jsonPath("$.data.title").value("consumer lag investigation"))
                .andExpect(jsonPath("$.data.owner").value(OWNER))
                .andExpect(jsonPath("$.data.engine").value("claude-code"))
                .andExpect(jsonPath("$.data.model").value("claude-sonnet-4-5"))
                .andExpect(jsonPath("$.data.mode").value("chat"))
                .andExpect(jsonPath("$.data.instanceId").value("rmq-local"))
                .andExpect(jsonPath("$.data.runtimeSessionId").value("session-123"))
                .andExpect(jsonPath("$.data.lastSeq").value(17))
                .andExpect(jsonPath("$.data.archived").value(false))
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-15T08:30:15"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-09-15T09:00:00"))
                // The entity's gmt_create / gmt_modified must not leak through under their own names.
                .andExpect(jsonPath("$.data.gmtCreate").doesNotExist())
                .andExpect(jsonPath("$.data.gmtModified").doesNotExist());

        verify(conversationService).create(OWNER, "rmq-local", "chat");
    }

    @Test
    void createConversationShouldAcceptARequestWithoutAnyFieldTest() throws Exception {
        when(conversationService.create(eq(OWNER), any(), any())).thenReturn(conversation(null));

        // Both body fields are optional (a conversation can start unbound), and so is the body itself:
        // a caller that posts an empty object gets an empty conversation rather than a 400.
        mockMvc.perform(post("/api/ai/conversations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.instanceId").isEmpty());

        verify(conversationService).create(OWNER, null, null);
    }

    @Test
    void createConversationShouldRejectAnOversizedInstanceIdTest() throws Exception {
        mockMvc.perform(post("/api/ai/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("instanceId", "i".repeat(129), "mode", "chat"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("instanceId must not exceed 128 characters"));
    }

    @Test
    void createConversationShouldRejectAnOversizedModeTest() throws Exception {
        mockMvc.perform(post("/api/ai/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mode", "m".repeat(17)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("mode must not exceed 16 characters"));
    }

    // --- 2. GET /api/ai/conversations ------------------------------------------

    @Test
    void listConversationsShouldReturnThePageEnvelopeTest() throws Exception {
        when(conversationService.listItems(OWNER, "lag", false, 1, 20)).thenReturn(
                PageResult.of(List.of(AiConversationListItemVO.builder()
                        .id(CONVERSATION_ID)
                        .title("consumer lag investigation")
                        .engine("claude-code")
                        .model("claude-sonnet-4-5")
                        .mode("chat")
                        .instanceId("rmq-local")
                        .lastRunId(RUN_ID)
                        .lastRunStatus(RunStatus.COMPLETED)
                        .updatedAt(UPDATED_AT)
                        .createdAt(CREATED_AT)
                        .build()), 1, 1, 20));

        mockMvc.perform(get("/api/ai/conversations")
                        .param("page", "1")
                        .param("size", "20")
                        .param("search", "lag")
                        .param("archived", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items[0].id").value(CONVERSATION_ID))
                .andExpect(jsonPath("$.data.items[0].lastRunId").value(RUN_ID))
                // Uppercase, exactly the RunStatus union the TS contract declares.
                .andExpect(jsonPath("$.data.items[0].lastRunStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items[0].updatedAt").value("2026-09-15T09:00:00"))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-09-15T08:30:15"));
    }

    @Test
    void listConversationsShouldDefaultThePagingAndDropABlankSearchTest() throws Exception {
        when(conversationService.listItems(any(), any(), any(), eq(1), eq(20)))
                .thenReturn(PageResult.empty(1, 20));

        mockMvc.perform(get("/api/ai/conversations").param("search", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));

        // A whitespace-only search is no filter: passed through it would match every title containing a
        // space, which is not what an emptied search box means.
        verify(conversationService).listItems(OWNER, null, null, 1, 20);
    }

    // --- 3. GET /api/ai/conversations/{id} -------------------------------------

    @Test
    void getConversationShouldReturnTheRunStillGeneratingTest() throws Exception {
        when(conversationService.requireOwned(CONVERSATION_ID, OWNER)).thenReturn(conversation("rmq-local"));
        when(conversationService.activeRun(CONVERSATION_ID))
                .thenReturn(Optional.of(AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 2, RunStatus.RUNNING)));

        mockMvc.perform(get("/api/ai/conversations/{id}", CONVERSATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(CONVERSATION_ID))
                .andExpect(jsonPath("$.data.owner").value(OWNER))
                .andExpect(jsonPath("$.data.activeRun.id").value(RUN_ID))
                .andExpect(jsonPath("$.data.activeRun.status").value("RUNNING"));
    }

    @Test
    void getConversationShouldReportAnIdleConversationWithANullActiveRunTest() throws Exception {
        when(conversationService.requireOwned(CONVERSATION_ID, OWNER)).thenReturn(conversation("rmq-local"));
        when(conversationService.activeRun(CONVERSATION_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/ai/conversations/{id}", CONVERSATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activeRun").isEmpty());
    }

    @Test
    void getConversationShouldAnswer404ForAnotherOperatorsConversationTest() throws Exception {
        // requireOwned answers 404 for an id that exists but belongs to somebody else: the two cases are
        // indistinguishable on purpose, so neither may say "forbidden".
        when(conversationService.requireOwned(CONVERSATION_ID, OWNER))
                .thenThrow(new BusinessException(404, "AI conversation not found"));

        mockMvc.perform(get("/api/ai/conversations/{id}", CONVERSATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("AI conversation not found"));
    }

    @Test
    void getConversationShouldAnswer404ForAnUnknownIdTest() throws Exception {
        when(conversationService.requireOwned(404L, OWNER))
                .thenThrow(new BusinessException(404, "AI conversation not found"));

        mockMvc.perform(get("/api/ai/conversations/{id}", 404L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("AI conversation not found"));
    }

    // --- 4. PATCH /api/ai/conversations/{id} -----------------------------------

    @Test
    void updateConversationShouldRenameTest() throws Exception {
        RmqAiConversation renamed = conversation("rmq-local");
        renamed.setTitle("renamed");
        when(conversationService.update(CONVERSATION_ID, OWNER, "renamed", null)).thenReturn(renamed);

        mockMvc.perform(patch("/api/ai/conversations/{id}", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("renamed"))
                .andExpect(jsonPath("$.data.archived").value(false));

        // archived was not in the body, so it must be passed on as untouched.
        verify(conversationService).update(CONVERSATION_ID, OWNER, "renamed", null);
    }

    @Test
    void updateConversationShouldArchiveTest() throws Exception {
        RmqAiConversation archived = conversation("rmq-local");
        archived.setArchived(true);
        when(conversationService.update(CONVERSATION_ID, OWNER, null, true)).thenReturn(archived);

        mockMvc.perform(patch("/api/ai/conversations/{id}", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archived\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.archived").value(true))
                .andExpect(jsonPath("$.data.title").value("consumer lag investigation"));

        verify(conversationService).update(CONVERSATION_ID, OWNER, null, true);
    }

    @Test
    void updateConversationShouldRejectAnOversizedTitleTest() throws Exception {
        mockMvc.perform(patch("/api/ai/conversations/{id}", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "t".repeat(513)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("title must not exceed 512 characters"));
    }

    // --- 5. DELETE /api/ai/conversations/{id} ----------------------------------

    @Test
    void deleteConversationShouldReturnAnEmptyEnvelopeTest() throws Exception {
        mockMvc.perform(delete("/api/ai/conversations/{id}", CONVERSATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isEmpty());

        // The service owns the cascade (events, then runs, then the conversation) and the workspace.
        verify(conversationService).delete(CONVERSATION_ID, OWNER);
    }

    @Test
    void deleteConversationShouldAnswer404ForAnotherOperatorsConversationTest() throws Exception {
        doThrow(new BusinessException(404, "AI conversation not found"))
                .when(conversationService).delete(CONVERSATION_ID, OWNER);

        mockMvc.perform(delete("/api/ai/conversations/{id}", CONVERSATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // --- 6. GET /api/ai/conversations/{id}/events ------------------------------

    @Test
    void conversationEventsShouldReturnTheTimelineItemsWithTheRequiredRunIdTest() throws Exception {
        when(conversationService.timeline(CONVERSATION_ID, OWNER, 0, 200)).thenReturn(
                new AiConversationService.TimelinePage(
                        List.of(new AiConversationService.TimelineItem(901L, 1, 1, CREATED_AT, RUN_ID,
                                        new TimelineEvent.User("why is the lag growing", null)),
                                new AiConversationService.TimelineItem(902L, 1, 2, CREATED_AT, RUN_ID,
                                        new TimelineEvent.Text("because the consumer is offline"))),
                        2,
                        AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.RUNNING)));

        mockMvc.perform(get("/api/ai/conversations/{id}/events", CONVERSATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(901))
                .andExpect(jsonPath("$.data.items[0].turn").value(1))
                .andExpect(jsonPath("$.data.items[0].seq").value(1))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-09-15T08:30:15"))
                // Required by the contract (rmq_ai_event.run_id is NOT NULL), so it must never be absent.
                .andExpect(jsonPath("$.data.items[0].runId").value(RUN_ID))
                // The deserialised event, not the raw type + payload columns.
                .andExpect(jsonPath("$.data.items[0].event.type").value("user"))
                .andExpect(jsonPath("$.data.items[0].event.text").value("why is the lag growing"))
                .andExpect(jsonPath("$.data.items[0].event.enhancedPrompt").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].payload").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].event.type").value("text"))
                .andExpect(jsonPath("$.data.items[1].event.text").value("because the consumer is offline"))
                .andExpect(jsonPath("$.data.nextAfter").value(2))
                .andExpect(jsonPath("$.data.activeRun.id").value(RUN_ID))
                .andExpect(jsonPath("$.data.activeRun.status").value("RUNNING"));
    }

    @Test
    void conversationEventsShouldDefaultTheCursorAndTheLimitTest() throws Exception {
        when(conversationService.timeline(CONVERSATION_ID, OWNER, 0, 200)).thenReturn(
                new AiConversationService.TimelinePage(List.of(), null, null));

        mockMvc.perform(get("/api/ai/conversations/{id}/events", CONVERSATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                // No rows means no cursor: the client stops paging on null, not on an empty page.
                .andExpect(jsonPath("$.data.nextAfter").isEmpty())
                .andExpect(jsonPath("$.data.activeRun").isEmpty());

        verify(conversationService).timeline(CONVERSATION_ID, OWNER, 0, 200);
    }

    @Test
    void conversationEventsShouldPassTheCursorThroughTest() throws Exception {
        when(conversationService.timeline(CONVERSATION_ID, OWNER, 150, 50)).thenReturn(
                new AiConversationService.TimelinePage(
                        List.of(new AiConversationService.TimelineItem(903L, 2, 151, CREATED_AT, RUN_ID,
                                new TimelineEvent.Thinking("let me look at the consumer",
                                        ThinkingSource.MODEL))),
                        151, null));

        mockMvc.perform(get("/api/ai/conversations/{id}/events", CONVERSATION_ID)
                        .param("after", "150")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].seq").value(151))
                .andExpect(jsonPath("$.data.items[0].event.type").value("thinking"))
                // ThinkingSource serialises lowercase, unlike RunStatus: they are different unions.
                .andExpect(jsonPath("$.data.items[0].event.source").value("model"))
                .andExpect(jsonPath("$.data.nextAfter").value(151));

        verify(conversationService).timeline(CONVERSATION_ID, OWNER, 150, 50);
    }

    // --- 9. POST /api/ai/runs/{runId}/stop -------------------------------------

    @Test
    void stopRunShouldReturnTheTerminalRunTest() throws Exception {
        RmqAiRun stopped = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.STOPPED);
        stopped.setStopReason(StopReason.USER_STOP.name());
        stopped.setStartedAt(CREATED_AT);
        stopped.setFinishedAt(UPDATED_AT);
        stopped.setDurationMs(1500L);
        when(runService.stop(RUN_ID)).thenReturn(stopped);

        mockMvc.perform(post("/api/ai/runs/{runId}/stop", RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(RUN_ID))
                .andExpect(jsonPath("$.data.conversationId").value(CONVERSATION_ID))
                .andExpect(jsonPath("$.data.turn").value(1))
                .andExpect(jsonPath("$.data.status").value("STOPPED"))
                .andExpect(jsonPath("$.data.stopReason").value("USER_STOP"))
                .andExpect(jsonPath("$.data.engine").value("claude-code"))
                .andExpect(jsonPath("$.data.model").value("qwen3.8-max"))
                .andExpect(jsonPath("$.data.startedAt").value("2026-09-15T08:30:15"))
                .andExpect(jsonPath("$.data.finishedAt").value("2026-09-15T09:00:00"))
                .andExpect(jsonPath("$.data.durationMs").value(1500));
    }

    @Test
    void stopRunShouldAnswer200ForARunThatIsAlreadyTerminalTest() throws Exception {
        // Idempotent on purpose: this backs a button a user may press twice, and the second press must
        // not look like a failure.
        when(runService.stop(RUN_ID))
                .thenReturn(AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.COMPLETED));

        mockMvc.perform(post("/api/ai/runs/{runId}/stop", RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.stopReason").isEmpty());
    }

    @Test
    void stopRunShouldRefuseAStaleRunWith409Test() throws Exception {
        // The runId the caller holds came from a run_started frame it may have received a whole turn
        // ago; a stale stop must fail closed rather than kill the answer being watched right now.
        when(runService.stop(RUN_ID)).thenThrow(new BusinessException(409, AiRunService.BUSY_MESSAGE));

        mockMvc.perform(post("/api/ai/runs/{runId}/stop", RUN_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(AiRunService.BUSY_MESSAGE));
    }

    @Test
    void stopRunShouldAnswer404ForAnotherOperatorsRunTest() throws Exception {
        when(runService.stop(RUN_ID)).thenThrow(new BusinessException(404, "AI run not found"));

        mockMvc.perform(post("/api/ai/runs/{runId}/stop", RUN_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("AI run not found"));
    }

    // --- 10. GET /api/ai/agent-capabilities ------------------------------------

    @Test
    void agentCapabilitiesShouldReportEveryFlagWithoutNullsTest() throws Exception {
        when(capabilityProbe.binaries()).thenReturn(new AgentCapabilityProbe.Binaries(
                true, false, true, Instant.parse("2026-09-18T03:00:00Z")));
        when(capabilityProbe.mcpEnabled()).thenReturn(false);
        when(capabilityProbe.l3ToolsAllowed()).thenReturn(true);

        mockMvc.perform(get("/api/ai/agent-capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.rmqctlAvailable").value(true))
                .andExpect(jsonPath("$.data.claudeAvailable").value(false))
                .andExpect(jsonPath("$.data.qoderAvailable").value(true))
                .andExpect(jsonPath("$.data.mcpEnabled").value(false))
                .andExpect(jsonPath("$.data.l3ToolsAllowed").value(true));

        // All five are required booleans in the TS contract, so none may be missing from the JSON.
        assertThat(dataOf(get("/api/ai/agent-capabilities")))
                .containsOnlyKeys("rmqctlAvailable", "claudeAvailable", "qoderAvailable",
                        "mcpEnabled", "l3ToolsAllowed");
    }

    // --- 11. GET /api/ai/conversations/{id}/rmqctl-config ----------------------

    @Test
    void rmqctlConfigShouldReturnThePasteReadySnippetTest() throws Exception {
        when(conversationService.requireOwned(CONVERSATION_ID, OWNER)).thenReturn(conversation("rmq-local"));
        when(rmqctlWorkspace.externalMcpSnippet("rmq-local")).thenReturn(SNIPPET);
        when(rmqctlWorkspace.serverUrl()).thenReturn("http://127.0.0.1:8888");

        mockMvc.perform(get("/api/ai/conversations/{id}/rmqctl-config", CONVERSATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.snippet").value(SNIPPET))
                .andExpect(jsonPath("$.data.instanceId").value("rmq-local"))
                .andExpect(jsonPath("$.data.server").value("http://127.0.0.1:8888"));

        // Exactly three fields, and the snippet is byte-for-byte what the workspace built: the "no
        // secret in here" guarantee belongs to RmqctlWorkspace (env: references only) and is asserted
        // there, so what this endpoint must not do is add anything of its own.
        assertThat(dataOf(get("/api/ai/conversations/{id}/rmqctl-config", CONVERSATION_ID)))
                .containsOnlyKeys("snippet", "instanceId", "server");
    }

    @Test
    void rmqctlConfigShouldRefuseAConversationThatIsNotBoundToAnInstanceTest() throws Exception {
        when(conversationService.requireOwned(CONVERSATION_ID, OWNER)).thenReturn(conversation(null));

        // rmqctl refuses to default --instance-id from anywhere, so a snippet without one would fail at
        // the first tool call rather than at configuration time; saying so now is cheaper.
        mockMvc.perform(get("/api/ai/conversations/{id}/rmqctl-config", CONVERSATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message")
                        .value("conversation 7 is not bound to an instance, so it has no rmqctl config"));
    }

    @Test
    void rmqctlConfigShouldAnswer404ForAnotherOperatorsConversationTest() throws Exception {
        when(conversationService.requireOwned(CONVERSATION_ID, OWNER))
                .thenThrow(new BusinessException(404, "AI conversation not found"));

        mockMvc.perform(get("/api/ai/conversations/{id}/rmqctl-config", CONVERSATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // --- owner scoping ----------------------------------------------------------

    @Test
    void everyPathShouldBeScopedToTheAuthenticatedOperatorTest() throws Exception {
        when(conversationService.requireOwned(CONVERSATION_ID, OWNER)).thenReturn(conversation(null));
        when(conversationService.activeRun(CONVERSATION_ID)).thenReturn(Optional.empty());
        when(conversationService.timeline(CONVERSATION_ID, OWNER, 0, 200)).thenReturn(
                new AiConversationService.TimelinePage(List.of(), null, null));

        mockMvc.perform(get("/api/ai/conversations/{id}", CONVERSATION_ID)).andExpect(status().isOk());
        mockMvc.perform(get("/api/ai/conversations/{id}/events", CONVERSATION_ID)).andExpect(status().isOk());
        mockMvc.perform(get("/api/ai/conversations/{id}/rmqctl-config", CONVERSATION_ID))
                .andExpect(status().isBadRequest());

        // Every lookup went through the owner filter with the authenticated operator, never with the
        // "system" fallback that an unauthenticated thread would produce.
        verify(conversationService, times(2)).requireOwned(CONVERSATION_ID, OWNER);
        verify(conversationService).timeline(CONVERSATION_ID, OWNER, 0, 200);
    }

    // --- helpers ---------------------------------------------------------------

    private static RmqAiConversation conversation(String instanceId) {
        RmqAiConversation conversation = new RmqAiConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setTitle("consumer lag investigation");
        conversation.setOwner(OWNER);
        conversation.setEngine("claude-code");
        conversation.setModel("claude-sonnet-4-5");
        conversation.setMode("chat");
        conversation.setInstanceId(instanceId);
        conversation.setRuntimeSessionId("session-123");
        conversation.setLastSeq(17);
        conversation.setArchived(false);
        conversation.setGmtCreate(CREATED_AT);
        conversation.setGmtModified(UPDATED_AT);
        return conversation;
    }

    private Map<String, Object> dataOf(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andReturn().getResponse().getContentAsString();
        Map<String, Object> envelope = readJson(body);
        Object data = envelope.get("data");
        return data instanceof Map ? castMap(data) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String body) throws Exception {
        return objectMapper.readValue(body, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object data) {
        return (Map<String, Object>) data;
    }
}
