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
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.AgentProviderRegistry;
import org.apache.rocketmq.studio.ops.ai.LlmConfigService;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.OpenAiCompatibleLlmClient;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.PromptEnhancer;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.RmqctlWorkspace;
import org.apache.rocketmq.studio.ops.ai.conversation.event.LiveEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Admission, the run state machine and the stop guards.
 *
 * <p>These are the decisions, as opposed to {@link AiRunExecutorTest}, which is the work. The three that
 * matter most are the guards around stopping: a stop names a run, and the one unrecoverable mistake
 * available is killing a different, newer run because a client held a stale id. Everything here is
 * written so that mistake fails closed.
 */
class AiRunServiceTest {

    private static final long CONVERSATION_ID = 7L;
    private static final long RUN_ID = 11L;
    private static final String OWNER = "tester";

    private final AiConversationRepository conversationRepository = mock(AiConversationRepository.class);
    private final AiRunRepository runRepository = mock(AiRunRepository.class);
    private final AiEventRepository eventRepository = mock(AiEventRepository.class);
    private final AiConversationService conversationService = mock(AiConversationService.class);
    private final LlmConfigService llmConfigService = mock(LlmConfigService.class);
    private final RmqctlWorkspace workspace = mock(RmqctlWorkspace.class);
    private final PromptEnhancer promptEnhancer = mock(PromptEnhancer.class);
    private final OpenAiCompatibleLlmClient llmClient = mock(OpenAiCompatibleLlmClient.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final AgentRunRegistry registry = new AgentRunRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiRunTestSupport.StubAgentProvider provider = new AiRunTestSupport.StubAgentProvider();

    private final List<RmqAiEvent> inserted = new CopyOnWriteArrayList<>();
    private final List<RmqAiRun> runUpdates = new CopyOnWriteArrayList<>();
    private final List<RmqAiRun> runInserts = new CopyOnWriteArrayList<>();
    private final List<RmqAiConversation> conversationUpdates = new CopyOnWriteArrayList<>();
    private final List<AiRunTestSupport.RecordingSseEmitter> emitters = new CopyOnWriteArrayList<>();

    private RmqAiConversation conversation;
    private RmqAiRun admitted;
    private AiRunExecutor executor;
    private AiRunService service;

    @BeforeEach
    void setUp() {
        AuthenticatedUserContext.setUsername(OWNER);
        inserted.clear();
        runUpdates.clear();
        runInserts.clear();
        conversationUpdates.clear();
        emitters.clear();
        admitted = null;
        conversation = AiRunTestSupport.conversation(CONVERSATION_ID, OWNER);
        conversation.setInstanceId("localtest");
        doAnswer(invocation -> {
            inserted.add(invocation.getArgument(0));
            return null;
        }).when(eventRepository).insert(any(RmqAiEvent.class));
        doAnswer(invocation -> {
            RmqAiRun updated = invocation.getArgument(0);
            runUpdates.add(AiRunTestSupport.copyOf(updated));
            return null;
        }).when(runRepository).update(any(RmqAiRun.class));
        doAnswer(invocation -> {
            RmqAiConversation updated = invocation.getArgument(0);
            conversationUpdates.add(AiRunTestSupport.copyOf(updated));
            return null;
        }).when(conversationRepository).update(any(RmqAiConversation.class));
        when(runRepository.insert(any(RmqAiRun.class))).thenAnswer(invocation -> {
            RmqAiRun run = invocation.getArgument(0);
            run.setId(RUN_ID);
            admitted = run;
            runInserts.add(AiRunTestSupport.copyOf(run));
            return run;
        });
        // The admitted row is the one a later findById or stop resolves, unless a test says otherwise.
        when(runRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(admitted));
        when(conversationService.requireOwned(CONVERSATION_ID, OWNER)).thenReturn(conversation);
        when(llmConfigService.getConfig()).thenReturn(LlmConfigVO.builder()
                .engine(LlmConfigVO.ENGINE_CLAUDE_CODE)
                .model("configured-model")
                .enabled(true)
                .build());
        when(workspace.prepare(anyLong(), any())).thenReturn(Optional.of(preparation()));
        service = serviceWith(AiRunTestSupport.directExecutor());
    }

    @AfterEach
    void tearDown() {
        AuthenticatedUserContext.clear();
        registry.liveRunIds().forEach(registry::unregister);
    }

    // --- admission -------------------------------------------------------------

    @Test
    void admissionShouldQueueTheRunWithASnapshotOfTheEngineAndModelTest() {
        provider.engineName = LlmConfigVO.ENGINE_QODER;
        service = serviceWith(AiRunTestSupport.directExecutor());

        service.sendMessage(CONVERSATION_ID, new AiRunService.RunRequest(
                "what is lagging", "requested-model", LlmConfigVO.ENGINE_QODER, null, false, null));

        // The snapshot is taken at admission, so changing the settings later cannot rewrite history.
        assertThat(runInserts).hasSize(1);
        assertThat(runInserts.get(0).getStatus()).isEqualTo(RunStatus.QUEUED.name());
        assertThat(runInserts.get(0).getEngine()).isEqualTo(LlmConfigVO.ENGINE_QODER);
        assertThat(runInserts.get(0).getModel()).isEqualTo("requested-model");
        assertThat(runInserts.get(0).getTurn()).isEqualTo(1);
        assertThat(runInserts.get(0).getStartSeq()).isEqualTo(1);
        // The worker picked the run up: QUEUED is a real state, not a label the row keeps.
        assertThat(runUpdates.get(0).getStatus()).isEqualTo(RunStatus.RUNNING.name());
        assertThat(runUpdates.get(0).getStartedAt()).isNotNull();
        // ...and the run really did go on to execute and finish on the queued engine.
        assertThat(lastRun().getStatus()).isEqualTo(RunStatus.COMPLETED.name());
        assertThat(emitters.get(0).timeoutMillis()).isEqualTo(AiRunExecutor.CLI_STREAM_TIMEOUT_MILLIS);
    }

    @Test
    void admissionShouldPersistTheUsersTurnAndAnnounceTheRunTest() {
        service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("what is lagging"));

        // The direct executor runs the turn inline, so the terminal row is already there too.
        assertThat(AiRunTestSupport.typesOf(inserted)).containsExactly("user", "run_status");
        assertThat(inserted.get(0).getPayload()).contains("what is lagging");
        assertThat(inserted.get(0).getSeq()).isEqualTo(1);
        // run_started is published before submit, so a client reading the response cannot miss it.
        assertThat(emitters.get(0).eventText())
                .contains("\"type\":\"run_started\"")
                .contains("\"turn\":1");
        assertThat(registry.isLive(RUN_ID)).isFalse();
    }

    @Test
    void admissionShouldRefuseASecondActiveRunOnTheSameConversationTest() {
        when(runRepository.findActiveByConversationId(CONVERSATION_ID))
                .thenReturn(Optional.of(AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.RUNNING)));

        assertThatThrownBy(() -> service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("again")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(409);
                    assertThat(exception.getMessage()).isEqualTo(AiRunService.BUSY_MESSAGE);
                });
        assertThat(inserted).isEmpty();
        assertThat(runUpdates).isEmpty();
    }

    @Test
    void aConversationWithoutToolsShouldSaySoInItsTimelineTest() {
        when(workspace.prepare(anyLong(), any())).thenReturn(Optional.empty());

        service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("hello"));

        // Degradation must be discoverable in the transcript, not only in a server log, and it comes
        // after the user's turn because that row is what a bubble boundary is made of.
        assertThat(AiRunTestSupport.typesOf(inserted)).startsWith("user", "notice");
        assertThat(inserted.get(1).getPayload()).contains(RmqctlWorkspace.DEGRADED_NOTICE_MESSAGE);
        assertThat(inserted.get(1).getSeq()).isEqualTo(2);
    }

    @Test
    void aConversationWithNoInstanceShouldSayItIsUnboundRatherThanDegradedTest() {
        conversation.setInstanceId(null);
        when(workspace.prepare(anyLong(), any())).thenReturn(Optional.empty());

        service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("hello"));

        assertThat(inserted.get(1).getPayload()).contains(RmqctlWorkspace.UNBOUND_NOTICE_MESSAGE);
    }

    @Test
    void theFirstTurnShouldNameTheConversationAndALaterOneShouldNotRenameItTest() {
        service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("why is my consumer lagging"));

        assertThat(conversation.getTitle()).isEqualTo("why is my consumer lagging");
        assertThat(titlesWritten()).containsExactly("why is my consumer lagging");

        // A title the user chose (or one already derived) survives later turns.
        conversation.setTitle("renamed by the user");
        service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("and now the producer side"));

        assertThat(conversation.getTitle()).isEqualTo("renamed by the user");
        assertThat(titlesWritten()).containsExactly("why is my consumer lagging");
        // Every turn still bumps gmt_modified, because the list is ordered by it: a conversation
        // answered just now has to float to the top.
        assertThat(conversationUpdates.size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void aSaturatedExecutorShouldFailTheRunWithOverloadedInsteadOfQueueingItTest() {
        ExecutorService saturated = AiRunTestSupport.directExecutor();
        saturated.shutdown();
        service = serviceWith(saturated);

        SseEmitter emitter = service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("hello"));

        assertThat(emitter).isSameAs(emitters.get(0));
        assertThat(lastRun().getStatus()).isEqualTo(RunStatus.FAILED.name());
        assertThat(lastRun().getStopReason()).isEqualTo(StopReason.OVERLOADED.name());
        assertThat(lastRun().getErrorCode()).isEqualTo(AiRunExecutor.ERROR_CODE_OVERLOADED);
        assertThat(AiRunTestSupport.typesOf(inserted)).containsExactly("user", "error", "run_status");
        assertThat(emitters.get(0).eventText()).contains("event:error").contains("503");
        assertThat(emitters.get(0).completed()).isTrue();
        assertThat(registry.isLive(RUN_ID)).isFalse();
    }

    @Test
    void aBlankOrOversizedMessageShouldBeRefusedBeforeARowIsWrittenTest() {
        assertThatThrownBy(() -> service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("   ")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service.sendMessage(CONVERSATION_ID,
                AiRunService.RunRequest.of("x".repeat(8193))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(400));
        assertThat(inserted).isEmpty();
    }

    // --- stopping --------------------------------------------------------------

    @Test
    void stopShouldAbortALiveRunAndLeaveItStoppedWithUserStopTest() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            service = serviceWith(worker);
            provider.beforeStream = options -> {
                started.countDown();
                await(release);
            };
            service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("a long answer"));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.isLive(RUN_ID)).isTrue();

            RmqAiRun stopped = service.stop(RUN_ID);

            assertThat(registry.handle(RUN_ID))
                    .get()
                    .satisfies(handle -> assertThat(handle.abortReason()).contains(AbortReason.USER_STOP));
            release.countDown();
            assertThat(stopped.getId()).isEqualTo(RUN_ID);
            assertThat(awaitRunStatus(RunStatus.STOPPED, Duration.ofSeconds(5))).isTrue();
            assertThat(lastRun().getStopReason()).isEqualTo(StopReason.USER_STOP.name());
            assertThat(AiRunTestSupport.typesOf(inserted)).contains("run_status");
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void stopOfAnAlreadyTerminalRunShouldBeANoOpTest() {
        RmqAiRun finished = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.COMPLETED);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(finished));
        when(runRepository.findActiveByConversationId(CONVERSATION_ID)).thenReturn(Optional.empty());

        // Idempotent, so the stop button is safe to press twice and a client that missed the terminal
        // frame can still ask.
        assertThat(service.stop(RUN_ID)).isSameAs(finished);
        assertThat(runUpdates).isEmpty();
        assertThat(inserted).isEmpty();
    }

    @Test
    void aStaleStopShouldFailClosedAndNotTouchTheNewerRunTest() {
        RmqAiRun stale = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.QUEUED);
        RmqAiRun current = AiRunTestSupport.run(RUN_ID + 1, CONVERSATION_ID, 2, RunStatus.RUNNING);
        AgentRunHandle currentHandle = new AgentRunHandle(RUN_ID + 1, Duration.ofMillis(1));
        registry.register(RUN_ID + 1, currentHandle);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(stale));
        when(runRepository.findActiveByConversationId(CONVERSATION_ID)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.stop(RUN_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(409);
                    assertThat(exception.getMessage()).isEqualTo(AiRunService.BUSY_MESSAGE);
                });
        // The client's runId came from a run_started frame it may have received a whole turn ago. Killing
        // the answer the user is watching now is the one unrecoverable mistake available here.
        assertThat(currentHandle.isStopRequested()).isFalse();
        assertThat(runUpdates).isEmpty();
    }

    @Test
    void stopOfARunNobodyOwnsAnyMoreShouldStillWriteTheTerminalStateTest() {
        // The row says active, but nothing in this process owns it: the worker is gone and will never
        // finalise. Writing the terminal state here is what keeps the stop button from spinning until the
        // orphan sweep gets to it ten minutes later.
        RmqAiRun abandoned = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.RUNNING);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(abandoned));
        when(runRepository.findActiveByConversationId(CONVERSATION_ID)).thenReturn(Optional.of(abandoned));
        when(eventRepository.maxSeq(CONVERSATION_ID)).thenReturn(4);

        service.stop(RUN_ID);

        assertThat(lastRun().getStatus()).isEqualTo(RunStatus.STOPPED.name());
        assertThat(lastRun().getStopReason()).isEqualTo(StopReason.USER_STOP.name());
        assertThat(AiRunTestSupport.typesOf(inserted)).containsExactly("run_status");
        assertThat(inserted.get(0).getSeq()).isEqualTo(5);
    }

    @Test
    void stopOfAnUnknownOrForeignRunShouldBeNotFoundTest() {
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.stop(RUN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(404));

        RmqAiRun foreign = AiRunTestSupport.run(RUN_ID, 99L, 1, RunStatus.RUNNING);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(foreign));
        // Somebody else's run is a 404, not a 403: an authorisation error would confirm the id exists.
        when(conversationService.requireOwned(99L, OWNER))
                .thenThrow(new BusinessException(404, "AI conversation not found"));
        assertThatThrownBy(() -> service.stop(RUN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(404));
    }

    @Test
    void onlyOneCallerShouldEverWinTheTerminalWriteTest() {
        AiEventSink sink = executor.newSink(CONVERSATION_ID, RUN_ID, 1, 0);
        AiRunExecutor.RunContext context = new AiRunExecutor.RunContext(conversation,
                AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.RUNNING), sink,
                new AgentRunHandle(RUN_ID, Duration.ofMillis(1)), null, provider.engine(), "hello",
                null, false, Duration.ofSeconds(300), null);

        // A stop, a provider failure, the worker's own safety net and a shutdown drain can all reach
        // finalisation for the same run; the compareAndSet is what makes exactly one of them write.
        assertThat(context.beginTerminal()).isTrue();
        assertThat(context.beginTerminal()).isFalse();
        assertThat(context.isTerminal()).isTrue();
        context.endTerminal();
        assertThat(context.beginTerminal()).isFalse();
    }

    // --- attach ----------------------------------------------------------------

    @Test
    void attachShouldReplayPersistedEventsAndCloseWhenTheRunIsOverTest() {
        RmqAiRun finished = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.COMPLETED);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(finished));
        when(conversationService.requireOwned(CONVERSATION_ID, OWNER)).thenReturn(conversation);
        when(eventRepository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, 200)).thenReturn(List.of(
                event(1, "user", "{\"type\":\"user\",\"text\":\"what is lagging\"}"),
                event(2, "text", "{\"type\":\"text\",\"text\":\"group A is 4000 behind\"}"),
                event(3, "run_status", "{\"type\":\"run_status\",\"status\":\"COMPLETED\"}")));

        service.attach(RUN_ID, 0);

        String text = emitters.get(0).eventText();
        // A replayed block arrives as the frame the live client would have received, so the two paths
        // reduce to the same transcript.
        assertThat(text).contains("\"type\":\"text_delta\"").contains("group A is 4000 behind");
        assertThat(text).contains("\"type\":\"run_finished\"").contains("event:done");
        // The user's own turn has no live counterpart: the client renders it from what it sent.
        assertThat(text).doesNotContain("what is lagging");
        assertThat(emitters.get(0).completed()).isTrue();
    }

    @Test
    void attachShouldHonourTheCursorAndSynthesiseATerminalFrameForAReapedRunTest() {
        // A run reaped at startup or by the orphan sweep has no run_status row at all — there was no sink
        // to write one — so the row is the only record of how it ended and the client must not wait
        // forever for a frame that will never come.
        RmqAiRun reaped = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.FAILED);
        reaped.setStopReason(StopReason.SERVER_RESTART.name());
        reaped.setDurationMs(1234L);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(reaped));
        when(eventRepository.findByConversationIdAfterSeq(CONVERSATION_ID, 2, 200))
                .thenReturn(List.of(event(3, "text", "{\"type\":\"text\",\"text\":\"tail\"}")));

        service.attach(RUN_ID, 2);

        verifyCursor(2);
        String text = emitters.get(0).eventText();
        assertThat(text).contains("tail").contains("\"type\":\"run_finished\"").contains("FAILED");
        assertThat(emitters.get(0).completed()).isTrue();
    }

    @Test
    void attachToARunStillGeneratingShouldStayOpenAndTailLiveFramesTest() {
        RmqAiRun active = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.RUNNING);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(active));
        when(eventRepository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, 200))
                .thenReturn(List.of(event(1, "text", "{\"type\":\"text\",\"text\":\"so far\"}")));
        registry.register(RUN_ID, new AgentRunHandle(RUN_ID, Duration.ofMillis(1)));

        service.attach(RUN_ID, 0);

        assertThat(emitters.get(0).eventText()).contains("so far").doesNotContain("event:done");
        assertThat(emitters.get(0).completed()).isFalse();

        registry.publish(RUN_ID, 2L, new LiveEvent.TextDelta("and since"));

        assertThat(emitters.get(0).eventText()).contains("and since");
    }

    @Test
    void attachAtTheHeadShouldReadOneEmptyPageAndThenCloseTest() {
        // The common reconnect: the client's cursor is the newest row, so there is nothing to replay. The
        // drain has to end on that empty page rather than ask again, and a run that is already over still
        // has to get its terminal frame.
        RmqAiRun finished = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.COMPLETED);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(finished));
        when(conversationService.requireOwned(CONVERSATION_ID, OWNER)).thenReturn(conversation);
        when(eventRepository.findByConversationIdAfterSeq(CONVERSATION_ID, 42, 200)).thenReturn(List.of());

        service.attach(RUN_ID, 42);

        String text = emitters.get(0).eventText();
        assertThat(text).doesNotContain("\"type\":\"text_delta\"");
        assertThat(text).contains("\"type\":\"run_finished\"").contains("event:done");
        assertThat(emitters.get(0).completed()).isTrue();
        verify(eventRepository, times(1))
                .findByConversationIdAfterSeq(CONVERSATION_ID, 42, 200);
    }

    @Test
    void attachShouldReplayABacklogLongerThanOneTimelinePageTest() {
        // A tool-heavy run persists two rows per tool call plus one per coalesced text or thinking block,
        // so a client that was away for a few minutes comes back to more than one page of history. The
        // watermark ends up at the last row read, which means anything past the first page would be
        // neither replayed (it sits behind the client's cursor) nor sent live (it was published before
        // this observer existed) — the reconnected transcript would carry a permanent hole.
        RmqAiRun active = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.RUNNING);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(active));
        List<RmqAiEvent> firstPage = new ArrayList<>();
        for (int seq = 1; seq <= 200; seq++) {
            firstPage.add(event(seq, "text", "{\"type\":\"text\",\"text\":\"block-" + seq + "\"}"));
        }
        when(eventRepository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, 200)).thenReturn(firstPage);
        when(eventRepository.findByConversationIdAfterSeq(CONVERSATION_ID, 200, 200))
                .thenReturn(List.of(event(201, "text", "{\"type\":\"text\",\"text\":\"the newest block\"}")));
        registry.register(RUN_ID, new AgentRunHandle(RUN_ID, Duration.ofMillis(1)));

        service.attach(RUN_ID, 0);

        assertThat(emitters.get(0).eventText())
                .contains("block-1").contains("block-200").contains("the newest block");
        // The second read has to continue after the last seq of the first page. Re-reading the same cursor
        // is what would turn this loop into a stall instead of a drain.
        verify(eventRepository).findByConversationIdAfterSeq(CONVERSATION_ID, 200, 200);
    }

    @Test
    void attachShouldNotLoseAFramePublishedWhileTheReplayIsReadTest() {
        // The registry keeps no backlog, so a frame published before the observer is registered is fanned
        // out to nobody and the tail cannot pick it up afterwards. Registering the observer only after the
        // read therefore drops everything the run publishes while the replay is being loaded.
        RmqAiRun active = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.RUNNING);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(active));
        when(eventRepository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, 200)).thenAnswer(invocation -> {
            registry.publish(RUN_ID, 42L, new LiveEvent.Notice("info", "published mid-replay"));
            return List.of(event(1, "text", "{\"type\":\"text\",\"text\":\"so far\"}"));
        });
        registry.register(RUN_ID, new AgentRunHandle(RUN_ID, Duration.ofMillis(1)));

        service.attach(RUN_ID, 0);

        assertThat(emitters.get(0).eventText()).contains("published mid-replay");
    }

    @Test
    void attachShouldDetachTheObserverWhenTheReplayReadFailsTest() {
        // A half-replayed observer is in the registry but has never sent a frame; nothing else would ever
        // remove it, because the emitter only detaches on a transport callback for a response the client
        // is still waiting for.
        RmqAiRun active = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.RUNNING);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(active));
        when(eventRepository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, 200))
                .thenThrow(new IllegalStateException("connection reset"));
        registry.register(RUN_ID, new AgentRunHandle(RUN_ID, Duration.ofMillis(1)));

        assertThatThrownBy(() -> service.attach(RUN_ID, 0)).isInstanceOf(IllegalStateException.class);

        // Ending the run completes whoever is still subscribed; a stranded observer would show up here.
        assertThat(registry.finish(RUN_ID)).isZero();
    }

    // --- resolution ------------------------------------------------------------

    @Test
    void engineAndModelShouldResolvePerRequestThenConversationThenConfigurationTest() {
        LlmConfigVO config = LlmConfigVO.builder()
                .engine(LlmConfigVO.ENGINE_HTTP).model("configured").enabled(true).build();

        assertThat(AiRunService.resolveEngine("qoder", "claude-code", config)).isEqualTo("qoder");
        assertThat(AiRunService.resolveEngine(null, "claude-code", config)).isEqualTo("claude-code");
        assertThat(AiRunService.resolveEngine(null, null, config)).isEqualTo(LlmConfigVO.ENGINE_HTTP);
        // An unknown engine falls back rather than failing a turn over a typo in a stored preference.
        assertThat(AiRunService.resolveEngine("nope", null, config)).isEqualTo(LlmConfigVO.ENGINE_HTTP);

        assertThat(AiRunService.resolveModel("Requested", "Stored", config)).isEqualTo("Requested");
        assertThat(AiRunService.resolveModel(null, "Stored", config)).isEqualTo("Stored");
        assertThat(AiRunService.resolveModel(null, null, config)).isEqualTo("configured");
    }

    @Test
    void resumeShouldFollowTheRequestAndFallBackToTheConversationTest() {
        conversation.setRuntimeSessionId(" session-9 ");

        assertThat(AiRunService.resolveResume(null, conversation)).isEqualTo("session-9");
        assertThat(AiRunService.resolveResume(Boolean.TRUE, conversation)).isEqualTo("session-9");
        // An explicit false starts a fresh provider session, which is how a user escapes a bad context.
        assertThat(AiRunService.resolveResume(Boolean.FALSE, conversation)).isNull();
        conversation.setRuntimeSessionId(null);
        assertThat(AiRunService.resolveResume(null, conversation)).isNull();
    }

    @Test
    void theHttpEngineShouldGetTheShorterStreamBudgetTest() {
        assertThat(executor.streamTimeoutMillis(LlmConfigVO.ENGINE_HTTP))
                .isEqualTo(AiRunExecutor.HTTP_STREAM_TIMEOUT_MILLIS);
        assertThat(executor.streamTimeoutMillis(LlmConfigVO.ENGINE_CLAUDE_CODE))
                .isEqualTo(AiRunExecutor.CLI_STREAM_TIMEOUT_MILLIS);
        assertThat(executor.streamTimeoutMillis(LlmConfigVO.ENGINE_QODER))
                .isEqualTo(AiRunExecutor.CLI_STREAM_TIMEOUT_MILLIS);
    }

    // --- harness ---------------------------------------------------------------

    private AiRunService serviceWith(ExecutorService executorService) {
        AiConversationProperties properties = new AiConversationProperties();
        properties.setStopGrace(Duration.ofMillis(50));
        executor = new AiRunExecutor(new AgentProviderRegistry(List.of(provider)), llmClient,
                promptEnhancer, registry, runRepository, conversationRepository, eventRepository,
                properties, objectMapper, executorService, AiRunTestSupport.recordingInto(emitters),
                scheduler, Clock.fixed(Instant.parse("2026-09-18T10:00:00Z"), ZoneOffset.UTC));
        return new AiRunService(conversationService, conversationRepository, runRepository,
                eventRepository, registry, executor, llmConfigService, workspace, objectMapper,
                Clock.fixed(Instant.parse("2026-09-18T10:00:00Z"), ZoneOffset.UTC));
    }

    private static RmqctlWorkspace.Preparation preparation() {
        return new RmqctlWorkspace.Preparation("/tmp/ws", "/tmp/ws/home", "/tmp/ws/rmqctl.yaml",
                "/tmp/ws/mcp.json", "/tmp/ws/agent-system-prompt.txt", "localtest", Map.of());
    }

    private static RmqAiEvent event(int seq, String type, String payload) {
        RmqAiEvent row = new RmqAiEvent();
        row.setConversationId(CONVERSATION_ID);
        row.setRunId(RUN_ID);
        row.setTurn(1);
        row.setSeq(seq);
        row.setType(type);
        row.setPayload(payload);
        return row;
    }

    /** The titles carried by conversation updates, i.e. the renames this service actually wrote. */
    private List<String> titlesWritten() {
        return conversationUpdates.stream()
                .map(RmqAiConversation::getTitle)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private RmqAiRun lastRun() {
        assertThat(runUpdates).isNotEmpty();
        return runUpdates.get(runUpdates.size() - 1);
    }

    private boolean awaitRunStatus(RunStatus expected, Duration budget) throws InterruptedException {
        long deadline = System.nanoTime() + budget.toNanos();
        while (System.nanoTime() < deadline) {
            if (!runUpdates.isEmpty() && expected.name().equals(lastRun().getStatus())) {
                return true;
            }
            Thread.sleep(10L);
        }
        return !runUpdates.isEmpty() && expected.name().equals(lastRun().getStatus());
    }

    private void verifyCursor(int afterSeq) {
        verify(eventRepository).findByConversationIdAfterSeq(CONVERSATION_ID, afterSeq, 200);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
