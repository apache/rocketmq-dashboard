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
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.ops.ai.AgentProviderRegistry;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.LlmGatewayException;
import org.apache.rocketmq.studio.ops.ai.OpenAiCompatibleLlmClient;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.AgentStreamOptions;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.PromptEnhancer;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.ResumeRecovery;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.RmqctlWorkspace;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.apache.rocketmq.studio.ops.ai.conversation.event.LiveEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.apache.rocketmq.studio.ops.ai.conversation.event.ThinkingSource;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * Runs one agent turn on its own thread and owns its terminal state.
 *
 * <h2>Generation belongs to the run, not to the HTTP connection</h2>
 * The emitter a {@code POST …/messages} returns is an <em>observer</em>. When the browser goes away the
 * observer detaches and the run keeps executing here; the client reconnects and replays from the
 * database. That is the whole point of the redesign, and it is why the executor exists as a separate
 * owner rather than as a callback hanging off the response. The honest cost: a run nobody watches any
 * more still runs to completion, bounded by the wall-clock budget below and by the orphan sweep.
 *
 * <h2>Why the executor, and not the projector, writes the terminal state</h2>
 * {@link AgentEventProjector} emits a terminal pair — a live {@code run_finished} and a persisted
 * {@code run_status} — on exactly one path: a {@code result} frame whose subtype is
 * {@link AgentEventProjector#SUCCESS_SUBTYPE}. Every other subtype produces an {@code error} and
 * nothing terminal, and a provider that throws an {@link LlmGatewayException} produces no projection at
 * all. Left to the projector, a stopped or failed run would keep {@code status = RUNNING} in
 * {@code rmq_ai_run} forever and a reloaded conversation would show an answer that never ends. So
 * {@link #finalizeRun} is the single place a run reaches a terminal state, and it is exactly-once.
 *
 * <h2>Finalisation order, which is not interchangeable</h2>
 * <ol>
 *   <li>flush the coalescing buffer and persist what it held — skipping this silently loses the last
 *       chunk of assistant text, i.e. the part the user was waiting for;</li>
 *   <li>persist the terminal {@code run_status}, unless the projector already did for a success frame;</li>
 *   <li>UPDATE {@code rmq_ai_run}: status, stop_reason, finished_at, duration_ms, end_seq, tokens;</li>
 *   <li>refresh the {@code rmq_ai_conversation.last_seq} cache;</li>
 *   <li>emit the live {@code run_finished};</li>
 *   <li>close the SSE.</li>
 * </ol>
 * Events before the row, because a reader that sees a terminal row must be able to read the whole
 * timeline; the row before the wire, because a client that receives {@code run_finished} and
 * immediately re-reads the conversation must find the terminal state already there.
 *
 * <h2>Executor shape</h2>
 * {@code ThreadPoolExecutor(0, 16, 60s, SynchronousQueue, AbortPolicy)} — the same shape and the same
 * saturation semantics the OpenAI-compatible gateway has always used: no queue, so a run either starts
 * immediately or is rejected, and a rejection becomes a visible {@code FAILED/OVERLOADED} rather than a
 * request that silently waits behind fifteen others. It is owned here rather than by the gateway because
 * the gateway's workers die with the response and these outlive it.
 */
@Slf4j
@Service
public class AiRunExecutor {

    /** Kept above the HTTP client's own timeout so a provider timeout still reaches the wire. */
    static final long HTTP_STREAM_TIMEOUT_MILLIS = 125_000L;

    /** The CLI budget. A tool-calling agent turn is far slower than one completion call. */
    static final long CLI_STREAM_TIMEOUT_MILLIS = 300_000L;

    private static final int MAX_CONCURRENT_RUNS = 16;

    /** How long the shutdown drain gives workers to write their terminal state after the kills. */
    private static final long SHUTDOWN_AWAIT_SECONDS = 25L;

    /** {@code rmq_ai_run.error_message} is VARCHAR(1024). */
    private static final int MAX_ERROR_MESSAGE_CHARS = 1000;

    /** {@code rmq_ai_run.error_code} is VARCHAR(64). */
    private static final int MAX_ERROR_CODE_CHARS = 64;

    static final String ERROR_CODE_OVERLOADED = "ai.run.overloaded";
    static final String ERROR_CODE_INTERNAL = "ai.run.internal_error";

    private static final String PROVIDER_TIMEOUT_CODE = "llm.provider.timeout";
    private static final Set<String> OUTPUT_LIMIT_CODES =
            Set.of("llm.provider.output_too_large", "llm.provider.response_too_large");
    private static final String ERROR_MAX_TURNS_SUBTYPE = "error_max_turns";

    private final AgentProviderRegistry agentProviders;
    private final OpenAiCompatibleLlmClient llmClient;
    private final PromptEnhancer promptEnhancer;
    private final AgentRunRegistry registry;
    private final AiRunRepository runRepository;
    private final AiConversationRepository conversationRepository;
    private final AiEventRepository eventRepository;
    private final AiConversationProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final LongFunction<SseEmitter> emitterFactory;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    @Autowired
    public AiRunExecutor(AgentProviderRegistry agentProviders,
                         OpenAiCompatibleLlmClient llmClient,
                         PromptEnhancer promptEnhancer,
                         AgentRunRegistry registry,
                         AiRunRepository runRepository,
                         AiConversationRepository conversationRepository,
                         AiEventRepository eventRepository,
                         AiConversationProperties properties,
                         ObjectMapper objectMapper) {
        this(agentProviders, llmClient, promptEnhancer, registry, runRepository, conversationRepository,
                eventRepository, properties, objectMapper, newRunExecutor(), SseEmitter::new,
                newScheduler(), Clock.systemUTC());
    }

    /**
     * The test seam. {@code executor} and {@code emitterFactory} are the same pair the gateway's
     * package-private constructor injects, so a test can run a turn synchronously and record what went
     * out over the wire; {@code scheduler} and {@code clock} make the coalescing window and the
     * timestamps observable without sleeping.
     */
    AiRunExecutor(AgentProviderRegistry agentProviders,
                  OpenAiCompatibleLlmClient llmClient,
                  PromptEnhancer promptEnhancer,
                  AgentRunRegistry registry,
                  AiRunRepository runRepository,
                  AiConversationRepository conversationRepository,
                  AiEventRepository eventRepository,
                  AiConversationProperties properties,
                  ObjectMapper objectMapper,
                  ExecutorService executor,
                  LongFunction<SseEmitter> emitterFactory,
                  ScheduledExecutorService scheduler,
                  Clock clock) {
        this.agentProviders = agentProviders;
        this.llmClient = llmClient;
        this.promptEnhancer = promptEnhancer;
        this.registry = registry;
        this.runRepository = runRepository;
        this.conversationRepository = conversationRepository;
        this.eventRepository = eventRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.emitterFactory = emitterFactory;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    private static ExecutorService newRunExecutor() {
        return new ThreadPoolExecutor(
                0, MAX_CONCURRENT_RUNS, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Shared by the coalescing flush window and the SSE heartbeats. Neither task may block for long,
     * but both do block: a flush takes its sink's lock and writes rows, a heartbeat writes to a socket.
     * With a full executor that is sixteen runs flushing every 150 ms plus one heartbeat per observer
     * every 15 s, so four threads keep a slow client from delaying another run's flush.
     */
    private static ScheduledExecutorService newScheduler() {
        return new ScheduledThreadPoolExecutor(4, runnable -> {
            Thread thread = new Thread(runnable, "ai-run-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** The wall-clock budget of a run, and of the emitter that observes it. */
    long streamTimeoutMillis(String engine) {
        return isHttpEngine(engine) ? HTTP_STREAM_TIMEOUT_MILLIS : CLI_STREAM_TIMEOUT_MILLIS;
    }

    /** A fresh observer for a run. Its callbacks detach; they never stop the run. */
    AgentStreamSession newSession(long runId, long timeoutMillis) {
        return new AgentStreamSession(runId, emitterFactory.apply(timeoutMillis), objectMapper,
                session -> registry.detach(runId, session), scheduler);
    }

    /** A cancellation handle for a run, with the configured SIGTERM-to-SIGKILL grace. */
    AgentRunHandle newHandle(long runId) {
        return new AgentRunHandle(runId, properties.getStopGrace());
    }

    /**
     * The single writer of one run's timeline, seeded from {@code MAX(seq)} read at admission. The
     * projector is per run because it keeps per-run state (tool start timestamps, the coalescing
     * buffer) that no stateless mapping can recover.
     */
    AiEventSink newSink(long conversationId, long runId, int turn, int seedSeq) {
        return new AiEventSink(conversationId, runId, turn, seedSeq, eventRepository,
                conversationRepository, new AgentEventProjector(runId), objectMapper,
                (seq, event) -> registry.publish(runId, seq, event), scheduler);
    }

    /**
     * Hands the run to the executor.
     *
     * @throws RejectedExecutionException when all {@value #MAX_CONCURRENT_RUNS} slots are busy. The
     *     caller turns that into {@code FAILED/OVERLOADED}: saturation is a fact about this server that
     *     the user has to be told, not a queue to hide behind.
     */
    void submit(RunContext context) {
        Future<?> task = executor.submit(() -> execute(context));
        context.getHandle().attachWorker(task);
        if (context.getHandle().isStopRequested()) {
            // A stop landed between registration and submission. The task may never get to run at all —
            // attachWorker's cancel can beat it to the punch — and then nobody would write the terminal
            // state. finalizeRun is exactly-once, so doing it here as well is safe either way.
            log.info("agent run {} was stopped before its worker started", context.getRun().getId());
            AbortReason reason = context.getHandle().abortReason().orElse(AbortReason.USER_STOP);
            finalizeRun(context, new Terminal(reason.status(), reason.stopReason(), null, null,
                    new Outcome(), false));
        }
    }

    /**
     * Writes a terminal state for a run that has no worker: an admission the executor rejected, or a
     * stop that found the row non-terminal with no handle in this JVM.
     */
    void terminate(RunContext context, RunStatus status, StopReason stopReason,
                   String errorCode, String errorMessage) {
        finalizeRun(context, new Terminal(status, stopReason, errorCode, errorMessage, new Outcome(), false));
    }

    private void execute(RunContext context) {
        RmqAiRun run = context.getRun();
        Outcome outcome = new Outcome();
        try {
            markRunning(context);
            streamWithLostResumeRetry(context, outcome);
        } catch (LlmGatewayException exception) {
            log.warn("agent run {} failed: {} - {}", run.getId(), exception.getCode(), exception.getMessage());
            outcome.gatewayFailure = exception;
        } catch (RuntimeException exception) {
            log.error("agent run {} failed unexpectedly", run.getId(), exception);
            outcome.unexpected = exception;
        } finally {
            try {
                finalizeRun(context, decide(context, outcome));
            } catch (RuntimeException exception) {
                log.error("could not finalise agent run {}", run.getId(), exception);
            }
            // Idempotent, and it is what releases observers whose finalisation threw halfway.
            registry.finish(run.getId());
        }
    }

    private void markRunning(RunContext context) {
        RmqAiRun run = context.getRun();
        LocalDateTime now = LocalDateTime.now(clock);
        run.setStatus(RunStatus.RUNNING.name());
        run.setStartedAt(now);
        run.setGmtModified(now);
        runRepository.update(run);
        log.debug("agent run {} of conversation {} is RUNNING (turn {}, engine {})",
                run.getId(), run.getConversationId(), run.getTurn(), run.getEngine());
    }

    /**
     * Streams the turn, and repairs the one provider failure a retry can repair: the {@code --resume}
     * session the conversation remembers no longer exists on disk. The provider reports it as
     * {@link ResumeRecovery#RESUME_LOST_CODE}, because only the provider knows the retry has to drop
     * {@code --resume} from the command.
     *
     * <p>Exactly one retry, and the dead id is forgotten <em>before</em> it: were it kept, the retry
     * would hit the same wall and so would every turn after it. What the conversation loses is the
     * earlier turns' context, which is the price {@link ResumeRecovery} documents.
     */
    private void streamWithLostResumeRetry(RunContext context, Outcome outcome) {
        // Prepared once, outside the retry: re-running the enhancer would pay for the rewrite twice
        // and put its reasoning in the timeline a second time.
        String prompt = preparePrompt(context, outcome);
        boolean resumeRequested = StringUtils.hasText(context.getResumeSessionId());
        try {
            stream(context, prompt, outcome, resumeRequested);
        } catch (LlmGatewayException exception) {
            if (!resumeRequested || !ResumeRecovery.RESUME_LOST_CODE.equals(exception.getCode())) {
                throw exception;
            }
            log.warn("agent run {} could not resume the session conversation {} remembers; retrying"
                            + " without --resume",
                    context.getRun().getId(), context.getConversation().getId());
            forgetRuntimeSession(context);
            outcome.forgetFirstAttempt();
            context.getSink().emit(new AgentEvent.ProviderNotice(AgentEventProjector.LEVEL_WARN,
                    "The agent session this conversation was resuming no longer exists; the turn was"
                            + " retried without the earlier turns' context."));
            stream(context, prompt, outcome, false);
        }
    }

    /** The prompt as the provider receives it, after the optional rewrite. */
    private String preparePrompt(RunContext context, Outcome outcome) {
        if (!context.isEnhance()) {
            return context.getPrompt();
        }
        return promptEnhancer.enhance(context.getConfig(), context.getEngine(), context.getPrompt(),
                chunk -> onAgentEvent(context, outcome,
                        new AgentEvent.ThinkingDelta(chunk, ThinkingSource.ENHANCE)));
    }

    private void stream(RunContext context, String prompt, Outcome outcome, boolean allowResume) {
        Consumer<AgentEvent> events = event -> onAgentEvent(context, outcome, event);
        if (isHttpEngine(context.getEngine())) {
            streamHttp(context, prompt, events);
            return;
        }
        agentProviders.forEngine(context.getEngine())
                .streamEvents(context.getConfig(), options(context, prompt, allowResume), events);
    }

    /**
     * The {@code http} engine has no agent CLI, so it stays a single-turn completion streamed as text
     * deltas: no reasoning, no tools, no resumable session id. That is a real limitation of the engine
     * and not something to paper over — the run row keeps {@code runtime_session_id} null and the next
     * turn starts from scratch.
     */
    private void streamHttp(RunContext context, String prompt, Consumer<AgentEvent> events) {
        LlmConfigVO config = context.getConfig();
        if (!llmClient.supports(config)) {
            throw new LlmGatewayException(400, "llm.config.unsupported_provider",
                    "LLM provider is not supported by the OpenAI-compatible gateway",
                    "Use one of: openai, deepseek, tongyi, ollama.");
        }
        llmClient.stream(config, prompt, context.getRun().getModel(),
                token -> events.accept(new AgentEvent.TextDelta(token)));
    }

    private AgentStreamOptions options(RunContext context, String prompt, boolean allowResume) {
        RmqctlWorkspace.Preparation preparation = context.getPreparation();
        AgentStreamOptions.AgentStreamOptionsBuilder builder = AgentStreamOptions.builder()
                .prompt(prompt)
                .model(context.getRun().getModel())
                // Dropped for the retry: that is the whole repair.
                .resumeSessionId(allowResume ? context.getResumeSessionId() : null)
                .instanceId(context.getConversation().getInstanceId())
                // Registered so a stop kills the real process tree instead of relying on an interrupt.
                .processSink(context.getHandle())
                .timeout(context.getTimeout());
        if (preparation != null) {
            builder.workspaceDir(preparation.workspaceDir())
                    .mcpConfigPath(preparation.mcpConfigPath())
                    .systemPromptPath(preparation.systemPromptPath())
                    .extraEnv(preparation.childEnv());
        }
        return builder.build();
    }

    /**
     * One provider event on its way to the sink. Two things happen here that the projector deliberately
     * does not do, because neither is a projection question:
     * <ul>
     *   <li>the run-scoped facts are recorded — session id, tokens, subtype — since they belong on the
     *       run row and not on an event;</li>
     *   <li>{@link AgentEvent.UnhandledUpstream} is deduplicated to at most one notice per upstream
     *       type per run. The parser already does this for its own frames; doing it here too is what
     *       keeps a provider that does not deduplicate from filling the timeline with one warning per
     *       frame.</li>
     * </ul>
     */
    private void onAgentEvent(RunContext context, Outcome outcome, AgentEvent event) {
        if (event == null) {
            return;
        }
        record(context, outcome, event);
        if (event instanceof AgentEvent.ResultMeta && outcome.stopRacedSuccess) {
            // claude flushes its result frame on SIGTERM, so a stop that lands while the agent is
            // finishing produces a success frame for a run the user stopped. The honest terminal state
            // is STOPPED, so the projector's COMPLETED terminal is swallowed and finalisation writes it.
            log.debug("agent run {} was stopped while its provider reported success", context.getRun().getId());
            return;
        }
        if (event instanceof AgentEvent.UnhandledUpstream unhandled
                && !context.noteUnhandled(unhandled.upstreamType())) {
            return;
        }
        context.getSink().emit(event);
    }

    private void record(RunContext context, Outcome outcome, AgentEvent event) {
        if (event instanceof AgentEvent.InitMeta init) {
            // Captured as early as possible: a run stopped before its result frame still leaves a
            // session id behind, so the next turn can --resume.
            if (outcome.runtimeSessionId == null && StringUtils.hasText(init.runtimeSessionId())) {
                outcome.runtimeSessionId = init.runtimeSessionId().trim();
            }
            return;
        }
        if (event instanceof AgentEvent.ResultMeta meta) {
            // Only from a successful frame: a failed one echoes the *requested* session id back, so
            // persisting it would point the next turn at a session that does not exist. The init
            // frame's id, taken above, is a real one and survives a later failure.
            if (AgentEventProjector.SUCCESS_SUBTYPE.equals(meta.subtype())
                    && StringUtils.hasText(meta.runtimeSessionId())) {
                outcome.runtimeSessionId = meta.runtimeSessionId().trim();
            }
            if (meta.inputTokens() != null) {
                outcome.inputTokens = meta.inputTokens();
            }
            if (meta.outputTokens() != null) {
                outcome.outputTokens = meta.outputTokens();
            }
            if (meta.durationMs() != null) {
                outcome.providerDurationMs = meta.durationMs();
            }
            outcome.subtype = meta.subtype();
            if (AgentEventProjector.SUCCESS_SUBTYPE.equals(meta.subtype())) {
                outcome.stopRacedSuccess = context.getHandle() != null && context.getHandle().isStopRequested();
                outcome.successTerminalProjected = !outcome.stopRacedSuccess;
            }
        }
    }

    /** Maps what happened onto the terminal state to write. An abort always wins: it is the decision
     * a human or the server made about this run, and a provider frame that arrived afterwards does not
     * unmake it. */
    private Terminal decide(RunContext context, Outcome outcome) {
        AbortReason abort = context.getHandle() == null
                ? null
                : context.getHandle().abortReason().orElse(null);
        if (abort != null) {
            return new Terminal(abort.status(), abort.stopReason(), null, null, outcome, false);
        }
        if (outcome.successTerminalProjected) {
            return new Terminal(RunStatus.COMPLETED, null, null, null, outcome, true);
        }
        if (outcome.gatewayFailure != null) {
            LlmGatewayException failure = outcome.gatewayFailure;
            return new Terminal(RunStatus.FAILED, stopReasonForCode(failure.getCode()), failure.getCode(),
                    failure.getMessage(), outcome, false);
        }
        if (outcome.unexpected != null) {
            return new Terminal(RunStatus.FAILED, StopReason.PROVIDER_ERROR, ERROR_CODE_INTERNAL,
                    outcome.unexpected.toString(), outcome, false);
        }
        if (StringUtils.hasText(outcome.subtype)
                && !AgentEventProjector.SUCCESS_SUBTYPE.equals(outcome.subtype)) {
            // The projector already persisted an error event for this subtype; what it cannot do is
            // decide the run's terminal state, which is why this branch exists.
            return new Terminal(RunStatus.FAILED, stopReasonForSubtype(outcome.subtype),
                    "llm.provider." + outcome.subtype,
                    "agent run ended with subtype: " + outcome.subtype, outcome, false);
        }
        return new Terminal(RunStatus.COMPLETED, null, null, null, outcome, false);
    }

    private void finalizeRun(RunContext context, Terminal terminal) {
        if (!context.beginTerminal()) {
            return;
        }
        // A stop interrupts this worker to unwind its drain loops. That interrupt has done its job by
        // now; leaving the flag set would make the terminal writes below fail on their own waits.
        Thread.interrupted();
        RmqAiRun run = context.getRun();
        AiEventSink sink = context.getSink();
        LocalDateTime finishedAt = LocalDateTime.now(clock);
        long durationMs = durationMs(run, finishedAt);
        try {
            // 1. The last chunk of assistant text is still in the coalescing buffer.
            sink.flushPending();
            // 2. The terminal timeline event, unless a success frame already produced one.
            int endSeq = terminal.terminalAlreadyProjected()
                    ? sink.highWaterSeq()
                    : sink.writeTimeline(new TimelineEvent.RunStatus(terminal.status(), terminal.stopReason()));
            // 3. The run row.
            run.setStatus(terminal.status().name());
            run.setStopReason(terminal.stopReason() == null ? null : terminal.stopReason().name());
            run.setFinishedAt(finishedAt);
            run.setDurationMs(durationMs);
            run.setEndSeq(endSeq);
            run.setRuntimeSessionId(terminal.outcome().runtimeSessionId);
            run.setInputTokens(terminal.outcome().inputTokens);
            run.setOutputTokens(terminal.outcome().outputTokens);
            run.setErrorCode(truncate(terminal.errorCode(), MAX_ERROR_CODE_CHARS));
            run.setErrorMessage(truncate(terminal.errorMessage(), MAX_ERROR_MESSAGE_CHARS));
            run.setGmtModified(finishedAt);
            runRepository.update(run);
            rememberRuntimeSession(context, terminal.outcome().runtimeSessionId);
            // 4. The last_seq cache. close() also drains anything the timed flush left behind.
            sink.close();
            // 5. The live terminal frame.
            if (!terminal.terminalAlreadyProjected()) {
                registry.publish(run.getId(), 0L,
                        new LiveEvent.RunFinished(run.getId(), terminal.status(), durationMs));
            }
            log.info("agent run {} finished: status={} reason={} durationMs={} providerDurationMs={}"
                            + " tokens={}/{} endSeq={}",
                    run.getId(), terminal.status(), terminal.stopReason(), durationMs,
                    terminal.outcome().providerDurationMs, terminal.outcome().inputTokens,
                    terminal.outcome().outputTokens, endSeq);
        } finally {
            context.endTerminal();
            // 6. The observers: done, then close. Also runs when a step above threw, so a browser is
            //    never left waiting on a stream that will not produce another frame.
            registry.finish(run.getId());
        }
    }

    /**
     * Carries the provider's session id up to the conversation so the next turn can resume it. Written
     * even for a stopped run: a graceful SIGTERM lets {@code claude} flush the frame that carries it,
     * which is the entire reason the stop is graceful.
     */
    private void rememberRuntimeSession(RunContext context, String runtimeSessionId) {
        if (!StringUtils.hasText(runtimeSessionId)) {
            return;
        }
        RmqAiConversation conversation = context.getConversation();
        if (runtimeSessionId.equals(conversation.getRuntimeSessionId())) {
            return;
        }
        RmqAiConversation update = new RmqAiConversation();
        update.setId(conversation.getId());
        update.setRuntimeSessionId(runtimeSessionId);
        update.setGmtModified(LocalDateTime.now(clock));
        try {
            conversationRepository.update(update);
            conversation.setRuntimeSessionId(runtimeSessionId);
        } catch (RuntimeException exception) {
            log.warn("could not remember the runtime session id of conversation {}: {}",
                    conversation.getId(), exception.toString());
        }
    }

    /**
     * Drops the {@code --resume} id the conversation remembers, so the retry — and every turn after it
     * — starts a fresh CLI session instead of failing on the same dead one again.
     */
    private void forgetRuntimeSession(RunContext context) {
        RmqAiConversation conversation = context.getConversation();
        if (!StringUtils.hasText(conversation.getRuntimeSessionId())) {
            return;
        }
        try {
            conversationRepository.clearRuntimeSessionId(conversation.getId());
            conversation.setRuntimeSessionId(null);
        } catch (RuntimeException exception) {
            log.warn("could not forget the runtime session id of conversation {}: {}",
                    conversation.getId(), exception.toString());
        }
    }

    private long durationMs(RmqAiRun run, LocalDateTime finishedAt) {
        LocalDateTime startedAt = run.getStartedAt() != null ? run.getStartedAt() : run.getGmtCreate();
        if (startedAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(startedAt, finishedAt).toMillis());
    }

    private static StopReason stopReasonForCode(String code) {
        if (PROVIDER_TIMEOUT_CODE.equals(code)) {
            return StopReason.TIMEOUT;
        }
        if (code != null && OUTPUT_LIMIT_CODES.contains(code)) {
            return StopReason.OUTPUT_LIMIT;
        }
        return StopReason.PROVIDER_ERROR;
    }

    private static StopReason stopReasonForSubtype(String subtype) {
        return ERROR_MAX_TURNS_SUBTYPE.equals(subtype) ? StopReason.OUTPUT_LIMIT : StopReason.PROVIDER_ERROR;
    }

    private static String truncate(String value, int maxChars) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, maxChars);
    }

    static boolean isHttpEngine(String engine) {
        return !StringUtils.hasText(engine)
                || LlmConfigVO.ENGINE_HTTP.equals(engine.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Shuts the runs down before the JVM does.
     *
     * <p>The reason is {@link AbortReason#SHUTDOWN} and not {@link AbortReason#USER_STOP}: a redeploy is
     * not a user cancel, and labelling it as one would put a lie in sixteen run rows. The drain is
     * two-phase — SIGTERM to every subprocess first, then wait for each — so the grace periods overlap
     * instead of adding up, and workers are then given {@value #SHUTDOWN_AWAIT_SECONDS}s to write their
     * terminal state before the pool is torn down.
     */
    @PreDestroy
    void destroy() {
        int live = registry.size();
        if (live > 0) {
            log.info("shutting down {} in-flight agent run(s)", live);
        }
        try {
            registry.drain(AbortReason.SHUTDOWN);
        } catch (RuntimeException exception) {
            log.warn("could not drain the in-flight agent runs: {}", exception.toString());
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("{} agent run(s) were still executing {}s into the shutdown drain",
                        registry.size(), SHUTDOWN_AWAIT_SECONDS);
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        scheduler.shutdownNow();
    }

    /**
     * Everything one admitted run carries into execution, plus the two pieces of mutable state that make
     * finalisation exactly-once.
     *
     * <p>The {@code AtomicReference<State>} + {@code compareAndSet} + {@code beginTerminal()} idiom is
     * the one the replaced SSE session used, and it is load-bearing here for the same reason: a stop, a
     * provider failure, the worker's own safety net and a shutdown drain can all reach
     * {@link AiRunExecutor#finalizeRun} for the same run, and only the first may write.
     */
    @Getter
    @RequiredArgsConstructor
    static final class RunContext {

        private enum State {
            ACTIVE, TERMINATING, TERMINATED
        }

        private final RmqAiConversation conversation;
        private final RmqAiRun run;
        private final AiEventSink sink;
        /** Null only for a detached terminal write, which has no subprocess to kill. */
        private final AgentRunHandle handle;
        private final LlmConfigVO config;
        private final String engine;
        private final String prompt;
        private final String resumeSessionId;
        private final boolean enhance;
        private final Duration timeout;
        /** Null when the conversation runs without RocketMQ tools. */
        private final RmqctlWorkspace.Preparation preparation;

        private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);

        /** Upstream frame types already warned about, so a chatty parser cannot fill the timeline. */
        private final Set<String> unhandledUpstream = ConcurrentHashMap.newKeySet();

        /** @return true for the caller that gets to write the terminal state, false for every other */
        boolean beginTerminal() {
            return state.compareAndSet(State.ACTIVE, State.TERMINATING);
        }

        void endTerminal() {
            state.set(State.TERMINATED);
        }

        boolean isTerminal() {
            return state.get() != State.ACTIVE;
        }

        /** @return true the first time this upstream type is seen in this run */
        boolean noteUnhandled(String upstreamType) {
            return unhandledUpstream.add(upstreamType == null ? "" : upstreamType);
        }
    }

    /** What the provider reported over the whole run. Mutable because it is filled in as frames arrive. */
    private static final class Outcome {

        private String runtimeSessionId;
        private String subtype;
        private Integer inputTokens;
        private Integer outputTokens;
        private Long providerDurationMs;
        private boolean successTerminalProjected;
        private boolean stopRacedSuccess;
        private LlmGatewayException gatewayFailure;
        private RuntimeException unexpected;

        /**
         * Drops everything the attempt that asked for the missing session reported, so the retry's own
         * frames decide the terminal state. Its session id in particular must not survive: it is the
         * dead one, echoed back.
         */
        void forgetFirstAttempt() {
            runtimeSessionId = null;
            subtype = null;
            inputTokens = null;
            outputTokens = null;
            providerDurationMs = null;
            successTerminalProjected = false;
            stopRacedSuccess = false;
        }
    }

    /** The terminal state to write, and whether the projector already wrote one. */
    private record Terminal(RunStatus status, StopReason stopReason, String errorCode, String errorMessage,
                            Outcome outcome, boolean terminalAlreadyProjected) {
    }
}
