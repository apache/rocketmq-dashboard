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
import org.apache.rocketmq.studio.ops.ai.AgentProviderRegistry;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.LlmGatewayException;
import org.apache.rocketmq.studio.ops.ai.OpenAiCompatibleLlmClient;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.PromptEnhancer;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.apache.rocketmq.studio.ops.ai.conversation.event.ThinkingSource;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The run worker: what it writes, in what order, and what it does when the provider misbehaves.
 *
 * <p>Two properties dominate. The first is that the executor <em>owns</em> the terminal state — the
 * projector only produces one for a successful {@code result} frame, so without this a stopped or failed
 * run would stay {@code RUNNING} in the database forever and a reloaded conversation would show an answer
 * that never ends. The second is that persisted writes are coalesced: a run that streams five thousand
 * characters of prose must produce three rows, not five hundred, while the live stream stays per token.
 *
 * <p>The provider is a stub rather than a mock so a test can script the exact frame sequence, including
 * the ones that only appear when a stop races a successful result. The scheduler is a mock that never
 * fires, so the coalescing window cannot make a row-count assertion depend on how loaded the machine is;
 * the window itself has its own test with a real scheduler.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiRunExecutorTest {

    private static final long CONVERSATION_ID = 7L;
    private static final long RUN_ID = 11L;
    private static final String ENGINE = LlmConfigVO.ENGINE_CLAUDE_CODE;

    @Mock
    private AiRunRepository runRepository;

    @Mock
    private AiConversationRepository conversationRepository;

    @Mock
    private AiEventRepository eventRepository;

    @Mock
    private PromptEnhancer promptEnhancer;

    @Mock
    private OpenAiCompatibleLlmClient llmClient;

    @Mock
    private ScheduledExecutorService scheduler;

    private final AiRunTestSupport.StubAgentProvider provider =
            new AiRunTestSupport.StubAgentProvider();
    private final AgentRunRegistry registry = new AgentRunRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<RmqAiEvent> inserted = new CopyOnWriteArrayList<>();
    private final List<RmqAiRun> runUpdates = new CopyOnWriteArrayList<>();

    /**
     * Every persistence call in the order it happened, labelled. The run entity is mutable and Mockito
     * records arguments by reference, so an {@code InOrder} verification with a status matcher would be
     * matching the state the row had by the time the test asserts — always the terminal one. An explicit
     * log is both correct and a more readable statement of the required order.
     */
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<AiRunTestSupport.RecordingSseEmitter> emitters = new CopyOnWriteArrayList<>();

    private AiRunExecutor executor;
    private RmqAiConversation conversation;
    private RmqAiRun run;
    private AgentStreamSession session;

    @BeforeEach
    void setUp() {
        inserted.clear();
        runUpdates.clear();
        calls.clear();
        emitters.clear();
        doAnswer(invocation -> {
            RmqAiEvent row = invocation.getArgument(0);
            inserted.add(row);
            calls.add("event:" + row.getType() + "@" + row.getSeq());
            return null;
        }).when(eventRepository).insert(any(RmqAiEvent.class));
        doAnswer(invocation -> {
            RmqAiRun updated = invocation.getArgument(0);
            RmqAiRun copy = AiRunTestSupport.copyOf(updated);
            runUpdates.add(copy);
            calls.add("run:" + copy.getStatus());
            return null;
        }).when(runRepository).update(any(RmqAiRun.class));
        doAnswer(invocation -> {
            RmqAiConversation update = invocation.getArgument(0);
            calls.add(update.getLastSeq() == null
                    ? "conversation:runtime_session_id=" + update.getRuntimeSessionId()
                    : "conversation:last_seq=" + update.getLastSeq());
            return null;
        }).when(conversationRepository).update(any(RmqAiConversation.class));
        AiConversationProperties properties = new AiConversationProperties();
        properties.setStopGrace(Duration.ofMillis(1));
        executor = new AiRunExecutor(new AgentProviderRegistry(List.of(provider)), llmClient, promptEnhancer,
                registry, runRepository, conversationRepository, eventRepository, properties, objectMapper,
                AiRunTestSupport.directExecutor(), AiRunTestSupport.recordingInto(emitters), scheduler,
                Clock.systemUTC());
        conversation = AiRunTestSupport.conversation(CONVERSATION_ID, "tester");
        run = AiRunTestSupport.run(RUN_ID, CONVERSATION_ID, 1, RunStatus.QUEUED);
    }

    @AfterEach
    void tearDown() {
        registry.liveRunIds().forEach(registry::unregister);
    }

    @Test
    void textDeltasShouldBeCoalescedIntoBlocksWhileTheStreamStaysPerTokenTest() {
        // 5000 characters in 500 deltas: 2048-character cuts leave two rows, and the finalisation flush
        // turns the remainder into a third. Without coalescing this is 500 rows for one paragraph.
        for (int index = 0; index < 500; index++) {
            provider.emit(new AgentEvent.TextDelta("0123456789"));
        }

        startAndRun();

        assertThat(types()).containsExactly("user", "text", "text", "text", "run_status");
        // The buffer is cut after the delta that crosses the ceiling is appended, so 10-character
        // deltas give 2050 rather than 2048 — the same rule coalesceLiveToTimeline models on the client.
        assertThat(blockLengths()).containsExactly(2050, 2050, 900);
        // Live deltas are not buffered: the browser gets every token as it arrives.
        assertThat(emitters.get(0).eventCount("\"type\":\"text_delta\"")).isEqualTo(500);
    }

    @Test
    void toolCallShouldFlushPendingTextFirstBecauseItIsADurabilityBoundaryTest() {
        provider.emit(new AgentEvent.TextDelta("let me look that up"));
        provider.emit(new AgentEvent.ToolStart("tc1", "rmq.topic.list"));
        provider.emit(new AgentEvent.ToolInputComplete("tc1", "rmq.topic.list", Map.of()));
        provider.emit(new AgentEvent.ToolDone("tc1", "rmq.topic.list", "3 topics", true, 12L, null));
        provider.emit(new AgentEvent.TextDelta("here they are"));

        startAndRun();

        // The prose that arrived before the tool call is persisted before it, so a reconnecting client
        // reads the same order the live client saw.
        assertThat(types()).containsExactly("user", "text", "tool_use", "tool_result", "text", "run_status");
    }

    @Test
    void aSuccessfulResultFrameShouldBeTheOnlyTerminalTheProjectorWritesTest() {
        provider.emit(new AgentEvent.TextDelta("done"));
        provider.emit(new AgentEvent.ResultMeta("session-9", 1200L, 30, 90,
                AgentEventProjector.SUCCESS_SUBTYPE));

        startAndRun();

        assertThat(types()).containsExactly("user", "text", "run_status");
        assertThat(runRow().getStatus()).isEqualTo(RunStatus.COMPLETED.name());
        assertThat(runRow().getStopReason()).isNull();
        // The session id and the token counts belong on the row, not on an event.
        assertThat(runRow().getRuntimeSessionId()).isEqualTo("session-9");
        assertThat(runRow().getInputTokens()).isEqualTo(30);
        assertThat(runRow().getOutputTokens()).isEqualTo(90);
        assertThat(conversation.getRuntimeSessionId()).isEqualTo("session-9");
        // Exactly one terminal reached the wire: the projector's, not a second one from finalisation.
        assertThat(emitters.get(0).eventCount("\"type\":\"run_finished\"")).isEqualTo(1);
        assertThat(registry.isLive(RUN_ID)).isFalse();
    }

    @Test
    void aStopDuringTheRunShouldWriteStoppedEvenWhenTheProviderReportsSuccessTest() {
        // claude flushes its result frame on SIGTERM, so this is the normal shape of a stop that lands
        // while the agent is finishing. The user stopped it; COMPLETED would be a lie.
        provider.beforeStream = options -> ((AgentRunHandle) options.getProcessSink())
                .stop(AbortReason.USER_STOP);
        provider.emit(new AgentEvent.TextDelta("almost finished"));
        provider.emit(new AgentEvent.ResultMeta("session-9", 1200L, 30, 90,
                AgentEventProjector.SUCCESS_SUBTYPE));

        startAndRun();

        assertThat(runRow().getStatus()).isEqualTo(RunStatus.STOPPED.name());
        assertThat(runRow().getStopReason()).isEqualTo(StopReason.USER_STOP.name());
        // One terminal event, and it is the stopped one: the swallowed success frame must not leave a
        // second run_status behind it.
        assertThat(types()).containsExactly("user", "text", "run_status");
        assertThat(payloads().get(2)).contains(RunStatus.STOPPED.name());
        assertThat(emitters.get(0).eventText()).contains("\"type\":\"run_finished\"");
        // The graceful stop still bought the next turn its resume id.
        assertThat(runRow().getRuntimeSessionId()).isEqualTo("session-9");
    }

    @Test
    void aProviderTimeoutShouldWriteFailedWithTheTimeoutStopReasonTest() {
        provider.failure = new LlmGatewayException(504, "llm.provider.timeout",
                "claude CLI stream timed out after 300s", "Retry with a shorter prompt.");
        provider.emit(new AgentEvent.TextDelta("partial"));

        startAndRun();

        assertThat(runRow().getStatus()).isEqualTo(RunStatus.FAILED.name());
        assertThat(runRow().getStopReason()).isEqualTo(StopReason.TIMEOUT.name());
        assertThat(runRow().getErrorCode()).isEqualTo("llm.provider.timeout");
        assertThat(types()).containsExactly("user", "text", "run_status");
        assertThat(emitters.get(0).eventText())
                .contains("\"status\":\"FAILED\"")
                .contains("event:done");
        assertThat(emitters.get(0).completed()).isTrue();
    }

    @Test
    void aNonSuccessResultSubtypeShouldWriteFailedWithTheSubtypeTest() {
        // The projector persists the error and stops there: it cannot decide the run's terminal state,
        // which is exactly why the executor has to.
        provider.emit(new AgentEvent.ResultMeta(null, null, null, null, "error_max_turns"));

        startAndRun();

        assertThat(types()).containsExactly("user", "error", "run_status");
        assertThat(runRow().getStatus()).isEqualTo(RunStatus.FAILED.name());
        assertThat(runRow().getStopReason()).isEqualTo(StopReason.OUTPUT_LIMIT.name());
        assertThat(runRow().getErrorCode()).isEqualTo("llm.provider.error_max_turns");
    }

    @Test
    void anUnexpectedProviderFailureShouldNotLeaveTheRunNonTerminalTest() {
        provider.failure = new IllegalStateException("provider exploded");

        startAndRun();

        assertThat(runRow().getStatus()).isEqualTo(RunStatus.FAILED.name());
        assertThat(runRow().getStopReason()).isEqualTo(StopReason.PROVIDER_ERROR.name());
        assertThat(runRow().getErrorCode()).isEqualTo(AiRunExecutor.ERROR_CODE_INTERNAL);
        assertThat(emitters.get(0).completed()).isTrue();
    }

    @Test
    void finalisationShouldPersistInTheFixedOrderTest() {
        // A provider failure, so the terminal event is the one the EXECUTOR writes: the projector only
        // produces a terminal for a successful result frame, and this is the path that used to leave the
        // run row saying RUNNING forever.
        provider.emit(new AgentEvent.TextDelta("done"));
        provider.failure = new LlmGatewayException(504, "llm.provider.timeout",
                "claude CLI stream timed out after 300s", "Retry with a shorter prompt.");

        startAndRun();

        // The order is not interchangeable. The pending flush comes first because it is the tail of the
        // answer; events before the row, because a reader that sees a terminal row must be able to read
        // the whole timeline; the row before the wire, because a client that receives run_finished and
        // immediately re-reads the conversation must find it already terminal; last_seq after each flush
        // and never ahead of the rows it summarises, because it is only a cache.
        assertThat(calls).containsExactly(
                "event:user@1",
                "conversation:last_seq=1",
                "run:RUNNING",
                "event:text@2",
                "conversation:last_seq=2",
                "event:run_status@3",
                "conversation:last_seq=3",
                "run:FAILED");
        assertThat(runRow().getEndSeq()).isEqualTo(3);
        assertThat(runRow().getStartSeq()).isEqualTo(1);
        assertThat(runRow().getFinishedAt()).isNotNull();
        assertThat(runRow().getDurationMs()).isNotNegative();
        // The wire order follows the persistence order: the terminal frame, then done.
        assertThat(emitters.get(0).eventText())
                .contains("\"type\":\"text_delta\"")
                .contains("\"type\":\"run_finished\"")
                .contains("event:done");
    }

    @Test
    void aDatabaseFailureInsideAFlushShouldNotKillTheRunTest() {
        doThrow(new RuntimeException("deadlock found when trying to get lock"))
                .when(eventRepository).insert(any(RmqAiEvent.class));
        provider.emit(new AgentEvent.TextDelta("answer"));

        startAndRun();

        // The user still gets their answer and the run still reaches a terminal state; only the timeline
        // is short a few rows. This is the retention-purge discipline applied to the hot path.
        assertThat(runUpdates).isNotEmpty();
        assertThat(runRow().getStatus()).isEqualTo(RunStatus.COMPLETED.name());
        assertThat(emitters.get(0).eventText()).contains("\"type\":\"text_delta\"");
        assertThat(emitters.get(0).completed()).isTrue();
    }

    @Test
    void anUnhandledUpstreamFrameShouldBeWarnedAboutOncePerTypePerRunTest() {
        provider.emit(new AgentEvent.UnhandledUpstream("plan_update"));
        provider.emit(new AgentEvent.UnhandledUpstream("plan_update"));
        provider.emit(new AgentEvent.UnhandledUpstream("plan_update"));
        provider.emit(new AgentEvent.UnhandledUpstream("checkpoint"));

        startAndRun();

        // A provider that does not deduplicate its own frames must not be able to fill the timeline with
        // one warning per frame; the coverage gap stays visible, once per type.
        assertThat(types()).containsExactly("user", "notice", "notice", "run_status");
        assertThat(payloads().get(1)).contains("plan_update");
        assertThat(payloads().get(2)).contains("checkpoint");
    }

    @Test
    void httpEngineShouldStreamTokensAsTextDeltasWithoutAnAgentProviderTest() {
        doAnswer(invocation -> {
            Consumer<String> tokens = invocation.getArgument(3);
            tokens.accept("hello ");
            tokens.accept("world");
            return null;
        }).when(llmClient).stream(any(), any(), any(), any());
        when(llmClient.supports(any())).thenReturn(true);

        AiRunExecutor.RunContext context = context(LlmConfigVO.ENGINE_HTTP);
        executor.submit(context);

        assertThat(types()).containsExactly("user", "text", "run_status");
        assertThat(payloads().get(1)).contains("hello world");
        assertThat(runRow().getStatus()).isEqualTo(RunStatus.COMPLETED.name());
        // The http engine has no agent CLI, so it has no resumable session and never asks for one.
        assertThat(runRow().getRuntimeSessionId()).isNull();
        assertThat(provider.wasCalled()).isFalse();
    }

    @Test
    void enhancementShouldBePersistedAsEnhanceThinkingAndNotAsModelReasoningTest() {
        when(promptEnhancer.enhance(any(), any(), eq("hello"), any())).thenAnswer(invocation -> {
            Consumer<String> deltas = invocation.getArgument(3);
            deltas.accept("You are a RocketMQ expert. ");
            deltas.accept("Explain hello.");
            return "You are a RocketMQ expert. Explain hello.";
        });
        provider.emit(new AgentEvent.ThinkingDelta("let me think", ThinkingSource.MODEL));
        provider.emit(new AgentEvent.TextDelta("answer"));

        startAndRun(true);

        assertThat(types()).containsExactly("user", "thinking", "thinking", "text", "run_status");
        // The rewrite and the model's reasoning are separate blocks: merging them is the bug the
        // ThinkingSource field exists to prevent.
        assertThat(payloads().get(1)).contains("\"source\":\"enhance\"");
        assertThat(payloads().get(2)).contains("\"source\":\"model\"");
        assertThat(provider.lastPrompt).isEqualTo("You are a RocketMQ expert. Explain hello.");
    }

    @Test
    void bufferedContentShouldBePersistedOnceTheCoalescingWindowElapsesTest() throws Exception {
        // A real scheduler and a short window: the durability of a half-finished paragraph must not
        // depend on more tokens arriving, or a reconnect finds nothing to replay.
        ScheduledExecutorService realScheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            AiEventSink sink = new AiEventSink(CONVERSATION_ID, RUN_ID, 1, 0, eventRepository,
                    conversationRepository, new AgentEventProjector(RUN_ID), objectMapper, null,
                    realScheduler, 20L, Clock.systemUTC());
            sink.emit(new AgentEvent.TextDelta("half an answer"));

            assertThat(inserted).isEmpty();
            assertThat(awaitRows(1, Duration.ofSeconds(5))).isTrue();
            assertThat(types()).containsExactly("text");
            assertThat(payloads().get(0)).contains("half an answer");

            // The window rearms: the next block is its own row, not an append to the first.
            sink.emit(new AgentEvent.TextDelta(" and the rest"));
            assertThat(awaitRows(2, Duration.ofSeconds(5))).isTrue();
            sink.close();
            assertThat(types()).containsExactly("text", "text");
        } finally {
            realScheduler.shutdownNow();
        }
    }

    @Test
    void closeShouldPersistWhateverTheBufferStillHoldsTest() {
        AiEventSink sink = new AiEventSink(CONVERSATION_ID, RUN_ID, 1, 0, eventRepository,
                conversationRepository, new AgentEventProjector(RUN_ID), objectMapper, null, null,
                AiEventSink.DEFAULT_FLUSH_INTERVAL_MILLIS, Clock.systemUTC());
        sink.emit(new AgentEvent.TextDelta("the last chunk"));
        assertThat(inserted).isEmpty();

        sink.close();

        // Skipping this step silently loses the tail of the answer, which is the part the user waited for.
        assertThat(types()).containsExactly("text");
        assertThat(sink.hasPending()).isFalse();
        // close() is the last step that touches last_seq, and it leaves the cache at the high-water mark.
        verify(conversationRepository).update(argThat(update -> Integer.valueOf(1).equals(update.getLastSeq())));
    }

    @Test
    void shutdownDrainShouldStopEveryRunWithShutdownAndNotWithUserStopTest() throws Exception {
        Process first = mock(Process.class);
        Process second = mock(Process.class);
        when(first.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(second.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        AiRunExecutor.RunContext context = context(ENGINE);
        registry.handle(RUN_ID).orElseThrow().attachProcess(first);
        AgentRunHandle other = executor.newHandle(RUN_ID + 1);
        other.attachProcess(second);
        registry.register(RUN_ID + 1, other);

        executor.destroy();

        // A redeploy is not a user cancel; mislabelling it puts a lie in every run row it drains.
        assertThat(context.getHandle().abortReason()).contains(AbortReason.SHUTDOWN);
        assertThat(other.abortReason()).contains(AbortReason.SHUTDOWN);
        // Both were signalled with the shutdown grace, which is shorter than a user stop's.
        verify(first).waitFor(AgentRunHandle.DEFAULT_SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        verify(second).waitFor(AgentRunHandle.DEFAULT_SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS);
    }

    // --- harness ---------------------------------------------------------------

    private void startAndRun() {
        startAndRun(false);
    }

    private void startAndRun(boolean enhance) {
        executor.submit(context(ENGINE, enhance));
    }

    private AiRunExecutor.RunContext context(String engine) {
        return context(engine, false);
    }

    private AiRunExecutor.RunContext context(String engine, boolean enhance) {
        AgentRunHandle handle = executor.newHandle(RUN_ID);
        AiEventSink sink = executor.newSink(CONVERSATION_ID, RUN_ID, 1, 0);
        session = executor.newSession(RUN_ID, executor.streamTimeoutMillis(engine));
        registry.register(RUN_ID, handle);
        sink.writeUser("hello", null);
        registry.attach(RUN_ID, session);
        session.finishReplay();
        return new AiRunExecutor.RunContext(conversation, run, sink, handle,
                LlmConfigVO.builder().engine(engine).model("qwen3.8-max").enabled(true).build(),
                engine, "hello", null, enhance, Duration.ofSeconds(300), null);
    }

    private List<String> types() {
        return AiRunTestSupport.typesOf(inserted);
    }

    private List<String> payloads() {
        return inserted.stream().map(RmqAiEvent::getPayload).toList();
    }

    /**
     * The length of each persisted {@code text} block, decoded from the payload. Asserting on the raw
     * JSON length would bake the envelope into a test that is about coalescing.
     */
    private List<Integer> blockLengths() {
        List<Integer> lengths = new ArrayList<>();
        inserted.stream()
                .filter(row -> "text".equals(row.getType()))
                .forEach(row -> lengths.add(textOf(row)));
        return lengths;
    }

    private int textOf(RmqAiEvent row) {
        try {
            return objectMapper.readTree(row.getPayload()).path("text").asText().length();
        } catch (Exception exception) {
            throw new AssertionError("unreadable payload: " + row.getPayload(), exception);
        }
    }

    /** The last terminal write to the run row, i.e. the state the database ended up with. */
    private RmqAiRun runRow() {
        assertThat(runUpdates).isNotEmpty();
        return runUpdates.get(runUpdates.size() - 1);
    }

    private boolean awaitRows(int expected, Duration budget) throws InterruptedException {
        long deadline = System.nanoTime() + budget.toNanos();
        while (System.nanoTime() < deadline) {
            if (inserted.size() >= expected) {
                return true;
            }
            Thread.sleep(10L);
        }
        return inserted.size() >= expected;
    }
}
