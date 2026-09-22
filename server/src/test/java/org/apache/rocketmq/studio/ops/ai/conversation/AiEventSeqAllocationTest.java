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
import org.apache.rocketmq.studio.ops.ai.LlmConfigService;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.RmqctlWorkspace;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * seq allocation: one writer per conversation, seeded from {@code MAX(seq)}.
 *
 * <p>seq is the reconnect cursor and one half of {@code uk_ai_event_conversation_seq}, so a wrong value
 * is not a cosmetic bug — it is a timeline that replays out of order, or two rows fighting over one
 * position. The case that matters most is
 * {@link #admissionShouldSeedFromMaxSeqAndNotFromTheStaleLastSeqCacheTest}: seeding from
 * {@code rmq_ai_conversation.last_seq} looks correct right up until a run dies between an insert and the
 * cache update, and from then on it silently reuses positions that already exist. That test is written
 * so a {@code last_seq + 1} implementation fails it.
 */
class AiEventSeqAllocationTest {

    private static final long CONVERSATION_ID = 7L;
    private static final long RUN_ID = 11L;
    private static final String OWNER = "tester";

    private final AiEventRepository eventRepository = mock(AiEventRepository.class);
    private final AiConversationRepository conversationRepository = mock(AiConversationRepository.class);
    private final AiRunRepository runRepository = mock(AiRunRepository.class);
    private final AiConversationService conversationService = mock(AiConversationService.class);
    private final AiRunExecutor runExecutor = mock(AiRunExecutor.class);
    private final LlmConfigService llmConfigService = mock(LlmConfigService.class);
    private final RmqctlWorkspace workspace = mock(RmqctlWorkspace.class);
    private final AgentRunRegistry registry = new AgentRunRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<RmqAiEvent> inserted = new CopyOnWriteArrayList<>();
    private final List<AiRunTestSupport.RecordingSseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicInteger runIds = new AtomicInteger();

    private RmqAiConversation conversation;

    @BeforeEach
    void setUp() {
        AuthenticatedUserContext.setUsername(OWNER);
        conversation = AiRunTestSupport.conversation(CONVERSATION_ID, OWNER);
        inserted.clear();
        emitters.clear();
        runIds.set(0);
        doAnswer(invocation -> {
            inserted.add(invocation.getArgument(0));
            return null;
        }).when(eventRepository).insert(any(RmqAiEvent.class));
    }

    @AfterEach
    void tearDown() {
        AuthenticatedUserContext.clear();
    }

    // --- the writer on its own -------------------------------------------------

    @Test
    void firstEventOfANewConversationShouldLandOnSeqOneTest() {
        AiEventSink sink = sink(RUN_ID, 1, 0);

        int assigned = sink.writeUser("hello", null);

        assertThat(assigned).isEqualTo(1);
        assertThat(AiRunTestSupport.seqsOf(inserted)).containsExactly(1);
        assertThat(AiRunTestSupport.typesOf(inserted)).containsExactly("user");
    }

    @Test
    void fiveHundredFlushesShouldProduceContiguousSeqsWithoutHolesOrDuplicatesTest() {
        AiEventSink sink = sink(RUN_ID, 1, 0);

        for (int index = 0; index < 500; index++) {
            sink.writeTimeline(new TimelineEvent.Notice(AgentEventProjector.LEVEL_INFO, "n" + index));
        }
        sink.close();

        assertThat(inserted).hasSize(500);
        assertThat(AiRunTestSupport.seqsOf(inserted))
                .isEqualTo(IntStream.rangeClosed(1, 500).boxed().toList());
        assertThat(sink.highWaterSeq()).isEqualTo(500);
    }

    @Test
    void aRunThatContinuesAnExistingConversationShouldNotRestartTheSequenceTest() {
        AiEventSink first = sink(RUN_ID, 1, 0);
        first.writeUser("first turn", null);
        first.writeTimeline(new TimelineEvent.Text("answer"));
        first.writeTimeline(new TimelineEvent.RunStatus(RunStatus.COMPLETED, null));
        first.close();
        assertThat(first.highWaterSeq()).isEqualTo(3);

        // The second run is seeded from what the first one actually wrote, as admission would seed it.
        AiEventSink second = sink(RUN_ID + 1, 2, first.highWaterSeq());
        second.writeUser("second turn", null);
        second.close();

        assertThat(AiRunTestSupport.seqsOf(inserted)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void aDuplicateTimelinePositionShouldSurfaceInsteadOfBeingLoggedAwayTest() {
        AiEventSink sink = sink(RUN_ID, 1, 0);
        sink.writeUser("hello", null);
        // uk_ai_event_conversation_seq is the backstop for a wrongly seeded allocator. Swallowing it
        // would turn a corrupt timeline into a silent one.
        doThrow(new DuplicateKeyException("uk_ai_event_conversation_seq"))
                .when(eventRepository).insert(any(RmqAiEvent.class));

        assertThatThrownBy(() -> sink.writeTimeline(new TimelineEvent.Text("answer")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void aRunThatDiedBeforeFlushingLastSeqShouldStillSeedTheNextOneCorrectlyTest() {
        AiEventSink crashed = sink(RUN_ID, 1, 0);
        crashed.writeUser("hello", null);
        crashed.writeTimeline(new TimelineEvent.Text("partial answer"));
        // The cache write is what a crash loses; the rows themselves are already committed.
        doThrow(new RuntimeException("connection reset"))
                .when(conversationRepository).update(any(RmqAiConversation.class));
        crashed.writeTimeline(new TimelineEvent.ToolUse("tc1", "rmq.topic.list", null));
        assertThat(crashed.highWaterSeq()).isEqualTo(3);

        AiEventSink next = sink(RUN_ID + 1, 2, crashed.highWaterSeq());
        next.writeUser("are you still there", null);

        assertThat(AiRunTestSupport.seqsOf(inserted)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void anOrdinaryPersistenceFailureShouldNotKillTheRunTest() {
        AiEventSink sink = sink(RUN_ID, 1, 0);
        doThrow(new RuntimeException("deadlock found when trying to get lock"))
                .when(eventRepository).insert(any(RmqAiEvent.class));

        // Losing one row must not cost the user the answer they are watching being generated.
        sink.writeUser("hello", null);
        sink.writeTimeline(new TimelineEvent.Text("answer"));
        sink.close();

        assertThat(sink.highWaterSeq()).isEqualTo(2);
        assertThat(inserted).isEmpty();
    }

    // --- admission, which is where the seed is chosen --------------------------

    @Test
    void admissionShouldSeedFromMaxSeqAndNotFromTheStaleLastSeqCacheTest() {
        // A run that died mid-flush left the cache behind: the rows are committed, the column is not.
        conversation.setLastSeq(3);
        when(eventRepository.maxSeq(CONVERSATION_ID)).thenReturn(41);
        AiRunService service = service();

        service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("hello"));

        // last_seq + 1 would be 4 here, reusing a position that has existed since the first turn.
        verify(eventRepository).maxSeq(CONVERSATION_ID);
        assertThat(AiRunTestSupport.seqsOf(inserted)).containsExactly(42);
        assertThat(inserted.get(0).getType()).isEqualTo("user");
        assertThat(inserted.get(0).getRunId()).isEqualTo(RUN_ID);
    }

    @Test
    void twoSequentiallyAdmittedRunsShouldContinueOneSequenceTest() {
        when(eventRepository.maxSeq(CONVERSATION_ID)).thenReturn(0, 3);
        when(runRepository.maxTurn(CONVERSATION_ID)).thenReturn(0, 1);
        AiRunService service = service();

        service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("first"));
        service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("second"));

        assertThat(AiRunTestSupport.seqsOf(inserted)).containsExactly(1, 4);
        assertThat(inserted).extracting(RmqAiEvent::getTurn).containsExactly(1, 2);
        assertThat(inserted).extracting(RmqAiEvent::getRunId).containsExactly(RUN_ID, RUN_ID + 1);
    }

    @Test
    void aSecondConcurrentAdmissionShouldBeRefusedSoThereIsOnlyEverOneWriterTest() {
        RmqAiRun active = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.RUNNING);
        when(runRepository.findActiveByConversationId(CONVERSATION_ID))
                .thenReturn(Optional.empty(), Optional.of(active));
        AiRunService service = service();

        service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("first"));

        assertThatThrownBy(() -> service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("second")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(409);
                    assertThat(exception.getMessage()).isEqualTo(AiRunService.BUSY_MESSAGE);
                });
        // Refusing is what makes "one writer per conversation" a guarantee rather than a hope, and what
        // lets the sink allocate seq from a plain counter with no locking in the database.
        assertThat(inserted).hasSize(1);
        assertThat(registry.liveRunIds()).containsExactly(RUN_ID);
    }

    @Test
    void aRacingDuplicateTurnShouldFailLoudlyOnTheUniqueKeyTest() {
        AiRunService service = service();
        // uk_ai_run_conversation_turn is the backstop against two admissions racing for the same turn.
        when(runRepository.insert(any(RmqAiRun.class)))
                .thenThrow(new DuplicateKeyException("uk_ai_run_conversation_turn"));

        assertThatThrownBy(() -> service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("hello")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(409));
        assertThat(inserted).isEmpty();
    }

    @Test
    void admissionShouldReserveTheRunStartSeqAboveTheSeedTest() {
        when(eventRepository.maxSeq(CONVERSATION_ID)).thenReturn(9);
        AiRunService service = service();

        service.sendMessage(CONVERSATION_ID, AiRunService.RunRequest.of("hello"));

        verify(runRepository).insert(argThat(run ->
                run.getStartSeq() == 10
                        && run.getEndSeq() == 9
                        && RunStatus.QUEUED.name().equals(run.getStatus())));
    }

    // --- harness ---------------------------------------------------------------

    private AiEventSink sink(long runId, int turn, int seedSeq) {
        // No scheduler: the time boundary is AiRunExecutorTest's business, and a seq test that races a
        // 150ms flush would be a test that fails on a loaded machine.
        return new AiEventSink(CONVERSATION_ID, runId, turn, seedSeq, eventRepository,
                conversationRepository, new AgentEventProjector(runId), objectMapper, null, null,
                AiEventSink.DEFAULT_FLUSH_INTERVAL_MILLIS, Clock.systemUTC());
    }

    private AiRunService service() {
        when(conversationService.requireOwned(eq(CONVERSATION_ID), any())).thenReturn(conversation);
        when(llmConfigService.getConfig()).thenReturn(LlmConfigVO.builder()
                .engine(LlmConfigVO.ENGINE_CLAUDE_CODE)
                .model("qwen3.8-max")
                .enabled(true)
                .build());
        // A prepared workspace, so admission writes the user's turn and nothing else: the degraded-mode
        // notice has its own test in AiRunServiceTest.
        when(workspace.prepare(anyLong(), any())).thenReturn(Optional.of(new RmqctlWorkspace.Preparation(
                "/tmp/ws", "/tmp/ws/home", "/tmp/ws/rmqctl.yaml", "/tmp/ws/mcp.json",
                "/tmp/ws/agent-system-prompt.txt", "localtest", Map.of())));
        when(runRepository.insert(any(RmqAiRun.class))).thenAnswer(invocation -> {
            RmqAiRun run = invocation.getArgument(0);
            run.setId(RUN_ID + runIds.getAndIncrement());
            return run;
        });
        when(runExecutor.streamTimeoutMillis(any())).thenReturn(300_000L);
        when(runExecutor.newHandle(anyLong())).thenAnswer(invocation ->
                new AgentRunHandle(invocation.getArgument(0), Duration.ofMillis(1)));
        when(runExecutor.newSink(anyLong(), anyLong(), anyInt(), anyInt())).thenAnswer(invocation ->
                sink(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));
        when(runExecutor.newSession(anyLong(), anyLong())).thenAnswer(invocation -> {
            AiRunTestSupport.RecordingSseEmitter emitter =
                    new AiRunTestSupport.RecordingSseEmitter(invocation.getArgument(1));
            emitters.add(emitter);
            long runId = invocation.getArgument(0);
            return new AgentStreamSession(runId, emitter, objectMapper,
                    session -> registry.detach(runId, session), null);
        });
        return new AiRunService(conversationService, conversationRepository, runRepository,
                eventRepository, registry, runExecutor, llmConfigService, workspace, objectMapper,
                Clock.systemUTC());
    }
}
