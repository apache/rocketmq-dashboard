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
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.LlmConfigService;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.RmqctlWorkspace;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Conversation CRUD, the timeline read, and the two sweeps that keep {@code rmq_ai_run} honest.
 *
 * <p>Three properties dominate. Owner scoping answers 404 for somebody else's id, because a 403 would
 * confirm the id exists. The sweeps must never touch a run this process still owns, or a slow agent turn
 * gets marked dead under its own worker. And retention cascades by hand — this project declares no
 * foreign keys, so deleting a conversation that leaves its events behind would be a silent leak.
 */
class AiConversationServiceTest {

    private static final long CONVERSATION_ID = 7L;
    private static final String OWNER = "tester";
    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");

    private final AiConversationRepository conversationRepository = mock(AiConversationRepository.class);
    private final AiRunRepository runRepository = mock(AiRunRepository.class);
    private final AiEventRepository eventRepository = mock(AiEventRepository.class);
    private final AgentRunRegistry registry = new AgentRunRegistry();
    private final RmqctlWorkspace workspace = mock(RmqctlWorkspace.class);
    private final LlmConfigService llmConfigService = mock(LlmConfigService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiConversationProperties properties = new AiConversationProperties();

    private final List<RmqAiRun> runUpdates = new ArrayList<>();
    private AiConversationService service;

    @BeforeEach
    void setUp() {
        AuthenticatedUserContext.setUsername(OWNER);
        runUpdates.clear();
        properties.setRetentionDays(90);
        properties.setCleanupBatchSize(500);
        properties.setCleanupMaxBatches(20);
        properties.setOrphanRunTimeout(Duration.ofMinutes(10));
        when(llmConfigService.getConfig()).thenReturn(LlmConfigVO.builder()
                .engine(LlmConfigVO.ENGINE_CLAUDE_CODE)
                .model("qwen3.8-max")
                .enabled(true)
                .build());
        when(conversationRepository.insert(any(RmqAiConversation.class)))
                .thenAnswer(invocation -> {
                    RmqAiConversation conversation = invocation.getArgument(0);
                    conversation.setId(CONVERSATION_ID);
                    return conversation;
                });
        doAnswer(invocation -> {
            RmqAiRun updated = invocation.getArgument(0);
            runUpdates.add(AiRunTestSupport.copyOf(updated));
            return null;
        }).when(runRepository).update(any(RmqAiRun.class));
        service = newService();
    }

    @AfterEach
    void tearDown() {
        AuthenticatedUserContext.clear();
        registry.liveRunIds().forEach(registry::unregister);
    }

    // --- CRUD ------------------------------------------------------------------

    @Test
    void createShouldSnapshotTheConfigurationAndStartUnarchivedWithAPlaceholderTitleTest() {
        RmqAiConversation created = service.create(OWNER, " localtest ", "diagnose");

        assertThat(created.getOwner()).isEqualTo(OWNER);
        assertThat(created.getInstanceId()).isEqualTo("localtest");
        assertThat(created.getMode()).isEqualTo("diagnose");
        assertThat(created.getEngine()).isEqualTo(LlmConfigVO.ENGINE_CLAUDE_CODE);
        assertThat(created.getModel()).isEqualTo("qwen3.8-max");
        assertThat(created.getTitle()).isEqualTo(AiConversationService.DEFAULT_TITLE);
        assertThat(created.getLastSeq()).isZero();
        assertThat(created.getArchived()).isFalse();
        assertThat(created.getGmtCreate()).isEqualTo(LocalDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @Test
    void createShouldFallBackToChatForAnUnknownModeTest() {
        // A mode is a UI hint, not a security boundary: an unexpected value degrades instead of failing
        // the request the user just made.
        assertThat(service.create(OWNER, null, "sudo").getMode()).isEqualTo("chat");
        assertThat(service.create(OWNER, null, null).getMode()).isEqualTo("chat");
    }

    @Test
    void listShouldClampPagingAndPassTheSearchThroughUntouchedTest() {
        RmqAiConversation stored = AiRunTestSupport.conversation(CONVERSATION_ID, OWNER);
        when(conversationRepository.findPage(OWNER, "lag", false, 1, 20))
                .thenReturn(PageResult.of(List.of(stored), 1, 1, 20));

        PageResult<RmqAiConversation> page = service.list(OWNER, "lag", false, 0, 0);

        // A page is 1-based and a size is capped, so no caller can ask for page 0 or an unbounded page.
        verify(conversationRepository).findPage(OWNER, "lag", false, 1, 20);
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);

        service.list(OWNER, null, null, 9, 5000);
        verify(conversationRepository).findPage(OWNER, null, null, 9, 100);
        // The search term reaches the repository verbatim: escaping LIKE wildcards is the repository's
        // job, where the QueryWrapper is built, and doing it here too would double-escape.
    }

    @Test
    void requireOwnedShouldAnswerNotFoundForSomebodyElsesConversationTest() {
        when(conversationRepository.findByIdAndOwner(CONVERSATION_ID, OWNER)).thenReturn(Optional.empty());

        // 404 and not 403: an authorisation error tells an attacker the id exists.
        assertThatThrownBy(() -> service.requireOwned(CONVERSATION_ID, OWNER))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(404));
    }

    @Test
    void updateShouldRenameAndArchiveAndSkipAWriteThatChangesNothingTest() {
        RmqAiConversation stored = AiRunTestSupport.conversation(CONVERSATION_ID, OWNER);
        when(conversationRepository.findByIdAndOwner(CONVERSATION_ID, OWNER)).thenReturn(Optional.of(stored));

        service.update(CONVERSATION_ID, OWNER, "  production lag  ", true);

        verify(conversationRepository).update(argThat(update ->
                "production lag".equals(update.getTitle()) && Boolean.TRUE.equals(update.getArchived())));

        // The same values again change nothing, so nothing is written and the list order is untouched.
        stored.setTitle("production lag");
        stored.setArchived(true);
        service.update(CONVERSATION_ID, OWNER, "production lag", true);
        verify(conversationRepository, times(1)).update(any(RmqAiConversation.class));
    }

    @Test
    void deleteShouldCascadeChildrenFirstAndRemoveTheWorkspaceTest() {
        RmqAiConversation stored = AiRunTestSupport.conversation(CONVERSATION_ID, OWNER);
        when(conversationRepository.findByIdAndOwner(CONVERSATION_ID, OWNER)).thenReturn(Optional.of(stored));
        AgentRunHandle handle = new AgentRunHandle(11L, Duration.ofMillis(1));
        registry.register(11L, handle);
        when(runRepository.findActiveByConversationId(CONVERSATION_ID))
                .thenReturn(Optional.of(AiRunTestSupport.run(11L, CONVERSATION_ID, 1, RunStatus.RUNNING)));

        service.delete(CONVERSATION_ID, OWNER);

        // A live run is stopped before its rows and its workspace disappear under it.
        assertThat(handle.isStopRequested()).isTrue();
        InOrder order = inOrder(eventRepository, runRepository, conversationRepository, workspace);
        order.verify(eventRepository).deleteByConversationId(CONVERSATION_ID);
        order.verify(runRepository).deleteByConversationId(CONVERSATION_ID);
        order.verify(conversationRepository).deleteById(CONVERSATION_ID);
        order.verify(workspace).delete(CONVERSATION_ID);
    }

    @Test
    void deriveTitleShouldFoldWhitespaceAndCapTheLengthTest() {
        assertThat(AiConversationService.deriveTitle("why is my consumer lagging"))
                .isEqualTo("why is my consumer lagging");
        assertThat(AiConversationService.deriveTitle("  first line\n\tsecond line  "))
                .isEqualTo("first line second line");
        assertThat(AiConversationService.deriveTitle("x".repeat(120))).hasSize(40);
        // A rule, not a model call: an extra request per conversation would cost latency and a failure
        // mode for something this simple.
        assertThat(AiConversationService.deriveTitle("   ")).isEqualTo(AiConversationService.DEFAULT_TITLE);
        assertThat(AiConversationService.deriveTitle(null)).isEqualTo(AiConversationService.DEFAULT_TITLE);
        assertThat(AiConversationService.capTitle("y".repeat(600))).hasSize(512);
    }

    // --- timeline --------------------------------------------------------------

    @Test
    void timelineShouldDeriveTheCursorFromOneExtraRowTest() {
        RmqAiConversation stored = AiRunTestSupport.conversation(CONVERSATION_ID, OWNER);
        when(conversationRepository.findByIdAndOwner(CONVERSATION_ID, OWNER)).thenReturn(Optional.of(stored));
        // limit + 1 fetched: the extra row is what says "there is more" without a second query.
        when(eventRepository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, 3)).thenReturn(List.of(
                event(1, "user", "{\"type\":\"user\",\"text\":\"hi\"}"),
                event(2, "text", "{\"type\":\"text\",\"text\":\"hello\"}"),
                event(3, "text", "{\"type\":\"text\",\"text\":\"more\"}")));

        AiConversationService.TimelinePage page = service.timeline(CONVERSATION_ID, OWNER, 0, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextAfter()).isEqualTo(2);
        assertThat(page.items().get(1).event()).isEqualTo(new TimelineEvent.Text("hello"));
        assertThat(page.activeRun()).isNull();
    }

    @Test
    void timelineShouldCarryTheRowIdAndTheRunThatProducedItTest() {
        RmqAiConversation stored = AiRunTestSupport.conversation(CONVERSATION_ID, OWNER);
        when(conversationRepository.findByIdAndOwner(CONVERSATION_ID, OWNER)).thenReturn(Optional.of(stored));
        when(eventRepository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, 201)).thenReturn(List.of(
                event(2, "text", "{\"type\":\"text\",\"text\":\"hello\"}")));

        AiConversationService.TimelineItem item =
                service.timeline(CONVERSATION_ID, OWNER, 0, 200).items().get(0);

        // Both are required by the frozen contract (TimelineItem in web/src/api/aiEvents.ts): runId
        // because rmq_ai_event.run_id is NOT NULL, id because the UI keys its rendered rows on it.
        assertThat(item.id()).isEqualTo(902L);
        assertThat(item.seq()).isEqualTo(2);
        assertThat(item.turn()).isEqualTo(1);
        assertThat(item.runId()).isEqualTo(11L);
        assertThat(item.event()).isEqualTo(new TimelineEvent.Text("hello"));
    }

    @Test
    void timelineShouldSkipAnUnreadableRowInsteadOfFailingTheConversationTest() {
        RmqAiConversation stored = AiRunTestSupport.conversation(CONVERSATION_ID, OWNER);
        when(conversationRepository.findByIdAndOwner(CONVERSATION_ID, OWNER)).thenReturn(Optional.of(stored));
        when(eventRepository.findByConversationIdAfterSeq(eq(CONVERSATION_ID), anyInt(), anyInt()))
                .thenReturn(List.of(
                        event(1, "text", "{\"type\":\"text\",\"text\":\"good\"}"),
                        event(2, "text", "{\"type\":\"text\""),
                        event(3, "text", "{\"type\":\"no_such_event\"}")));

        AiConversationService.TimelinePage page = service.timeline(CONVERSATION_ID, OWNER, 0, 200);

        // One row written by an older build, or truncated by a full disk, must not cost the whole
        // conversation. The cursor still advances past the bad rows so paging cannot get stuck.
        assertThat(page.items()).hasSize(1);
        assertThat(page.nextAfter()).isEqualTo(3);
    }

    @Test
    void timelineShouldReportTheRunStillGeneratingSoAClientCanReattachTest() {
        RmqAiConversation stored = AiRunTestSupport.conversation(CONVERSATION_ID, OWNER);
        when(conversationRepository.findByIdAndOwner(CONVERSATION_ID, OWNER)).thenReturn(Optional.of(stored));
        RmqAiRun active = AiRunTestSupport.run(11L, CONVERSATION_ID, 2, RunStatus.RUNNING);
        when(runRepository.findActiveByConversationId(CONVERSATION_ID)).thenReturn(Optional.of(active));
        when(eventRepository.findByConversationIdAfterSeq(eq(CONVERSATION_ID), anyInt(), anyInt()))
                .thenReturn(List.of());

        AiConversationService.TimelinePage page = service.timeline(CONVERSATION_ID, OWNER, 0, 200);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextAfter()).isNull();
        assertThat(page.activeRun()).isSameAs(active);
    }

    // --- the two sweeps --------------------------------------------------------

    @Test
    void theStartupReaperShouldFailEveryRunLeftNonTerminalByThePreviousProcessTest() {
        RmqAiRun queued = AiRunTestSupport.run(11L, CONVERSATION_ID, 1, RunStatus.QUEUED);
        RmqAiRun running = AiRunTestSupport.run(12L, CONVERSATION_ID, 2, RunStatus.RUNNING);
        running.setStartedAt(LocalDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)).minusMinutes(2));
        when(runRepository.findByStatusIn(RunStatus.ACTIVE_STATUSES)).thenReturn(List.of(queued, running));

        service.run(new DefaultApplicationArguments());

        assertThat(runUpdates).hasSize(2);
        assertThat(runUpdates).allSatisfy(update -> {
            assertThat(update.getStatus()).isEqualTo(RunStatus.FAILED.name());
            // Nothing survived to say why, so this is the honest reason: a redeploy is not a user cancel
            // and not an orphan the watchdog found.
            assertThat(update.getStopReason()).isEqualTo(StopReason.SERVER_RESTART.name());
            assertThat(update.getFinishedAt()).isNotNull();
        });
        assertThat(runUpdates.get(1).getDurationMs()).isPositive();
        // No run_status event is written: there is no sink, and guessing a seq would corrupt the
        // timeline. That is exactly why a reload has to read rmq_ai_run.status and not only the events.
        verify(eventRepository, never()).insert(any(RmqAiEvent.class));
    }

    @Test
    void theStartupReaperShouldNotAbortStartupWhenTheTablesAreNotThereYetTest() {
        when(runRepository.findByStatusIn(any())).thenThrow(new RuntimeException("table does not exist"));

        // The schema runner is about to create it; failing the boot would be worse than skipping a sweep.
        service.run(new DefaultApplicationArguments());

        assertThat(runUpdates).isEmpty();
    }

    @Test
    void theOrphanSweepShouldReapAnOwnerlessRunAndLeaveALiveOneAloneTest() {
        RmqAiRun orphaned = AiRunTestSupport.run(11L, CONVERSATION_ID, 1, RunStatus.RUNNING);
        RmqAiRun slow = AiRunTestSupport.run(12L, CONVERSATION_ID, 1, RunStatus.RUNNING);
        registry.register(12L, new AgentRunHandle(12L, Duration.ofMillis(1)));
        when(runRepository.findStaleActive(any(LocalDateTime.class), eq(RunStatus.ACTIVE_STATUSES)))
                .thenReturn(List.of(orphaned, slow));

        service.purgeExpired();

        // A live handle means the run is merely slow — the CLI budget is five minutes and the timeout is
        // ten — so reaping it would kill an answer that is about to arrive.
        assertThat(runUpdates).hasSize(1);
        assertThat(runUpdates.get(0).getId()).isEqualTo(11L);
        assertThat(runUpdates.get(0).getStatus()).isEqualTo(RunStatus.FAILED.name());
        assertThat(runUpdates.get(0).getStopReason()).isEqualTo(StopReason.ORPHANED.name());
    }

    @Test
    void retentionShouldDeleteChildrenBeforeParentsInBatchesTest() {
        when(conversationRepository.findIdsCreatedBefore(any(LocalDateTime.class), eq(500)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(conversationRepository.deleteByIds(anyList())).thenReturn(3);

        service.purgeExpired();

        InOrder order = inOrder(eventRepository, runRepository, conversationRepository);
        order.verify(eventRepository).deleteByConversationIds(List.of(1L, 2L, 3L));
        order.verify(runRepository).deleteByConversationIds(List.of(1L, 2L, 3L));
        order.verify(conversationRepository).deleteByIds(List.of(1L, 2L, 3L));
        // A batch smaller than the limit means the backlog is gone; looping again would just re-query.
        verify(conversationRepository, times(1)).findIdsCreatedBefore(any(), anyInt());
    }

    @Test
    void retentionShouldRemoveTheWorkspaceOfEveryPurgedConversationTest() {
        when(conversationRepository.findIdsCreatedBefore(any(LocalDateTime.class), eq(500)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(conversationRepository.deleteByIds(anyList())).thenReturn(3);

        service.purgeExpired();

        // The workspace holds the child's HOME, so the agent transcript lives there: a purge that
        // only deletes rows keeps it on disk forever, under a conversation the user cannot see any
        // more. It is removed after the rows, the same order the on-request delete uses.
        InOrder order = inOrder(conversationRepository, workspace);
        order.verify(conversationRepository).deleteByIds(List.of(1L, 2L, 3L));
        order.verify(workspace).delete(1L);
        order.verify(workspace).delete(2L);
        order.verify(workspace).delete(3L);
    }

    @Test
    void retentionShouldKeepBatchingUntilTheBacklogIsGoneOrTheCapIsReachedTest() {
        properties.setCleanupBatchSize(2);
        properties.setCleanupMaxBatches(3);
        service = newService();
        when(conversationRepository.findIdsCreatedBefore(any(LocalDateTime.class), eq(2)))
                .thenReturn(List.of(1L, 2L), List.of(3L, 4L), List.of(5L, 6L), List.of(7L));
        when(conversationRepository.deleteByIds(anyList())).thenReturn(2, 2, 2, 1);

        service.purgeExpired();

        // Three batches, then the cap stops it: a retention pass must not run unbounded on a huge backlog.
        verify(conversationRepository, times(3)).findIdsCreatedBefore(any(), anyInt());
        verify(conversationRepository, times(3)).deleteByIds(anyList());
    }

    @Test
    void retentionShouldDoNothingWhenItIsDisabledAndSwallowItsOwnFailuresTest() {
        properties.setRetentionDays(0);
        service = newService();

        service.purgeExpired();

        verify(conversationRepository, never()).findIdsCreatedBefore(any(), anyInt());

        properties.setRetentionDays(90);
        service = newService();
        when(conversationRepository.findIdsCreatedBefore(any(LocalDateTime.class), anyInt()))
                .thenThrow(new RuntimeException("connection refused"));

        // A scheduler thread that dies on a database blip stops every later pass silently, which is how
        // a retention job turns into a full disk. So: log, and stay alive.
        service.purgeExpired();

        verify(eventRepository, never()).deleteByConversationIds(anyList());
    }

    @Test
    void aFailingSweepShouldNotStopTheRetentionPassTest() {
        doThrow(new RuntimeException("connection refused"))
                .when(runRepository).findStaleActive(any(LocalDateTime.class), any());
        when(conversationRepository.findIdsCreatedBefore(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(1L));
        when(conversationRepository.deleteByIds(anyList())).thenReturn(1);

        service.purgeExpired();

        verify(conversationRepository).deleteByIds(List.of(1L));
    }

    // --- harness ---------------------------------------------------------------

    private AiConversationService newService() {
        return new AiConversationService(conversationRepository, runRepository, eventRepository, registry,
                workspace, llmConfigService, properties, objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RmqAiEvent event(int seq, String type, String payload) {
        RmqAiEvent row = new RmqAiEvent();
        // Deliberately NOT the same number as seq: TimelineItem carries both, and an assertion on one
        // must not pass by coincidence when the other is wrong.
        row.setId(900L + seq);
        row.setConversationId(CONVERSATION_ID);
        row.setRunId(11L);
        row.setTurn(1);
        row.setSeq(seq);
        row.setType(type);
        row.setPayload(payload);
        return row;
    }
}
