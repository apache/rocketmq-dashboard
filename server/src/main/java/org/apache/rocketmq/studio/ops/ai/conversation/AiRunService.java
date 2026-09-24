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
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.LlmConfigService;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.LlmGatewayException;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.RmqctlWorkspace;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.apache.rocketmq.studio.ops.ai.conversation.event.LiveEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

/**
 * Admission, the run state machine and stopping — everything about a run that is a <em>decision</em>,
 * as opposed to {@link AiRunExecutor}, which is everything that is <em>work</em>.
 *
 * <h2>The state machine</h2>
 * {@code QUEUED -> RUNNING -> COMPLETED | STOPPED | FAILED}, with one extra edge: an admission the
 * executor rejects goes straight to {@code FAILED/OVERLOADED}. QUEUED is written by this class at
 * admission, RUNNING by the worker when it starts, and every terminal state by
 * {@link AiRunExecutor#finalizeRun}, which is exactly-once. Nothing here writes a terminal state except
 * through the executor, so there is one place to look when a run ends up in the wrong one.
 *
 * <h2>One active run per conversation</h2>
 * Admission refuses with 409 when the conversation already has a QUEUED or RUNNING run, and
 * {@code uk_ai_run_conversation_turn} is the backstop that turns a racing duplicate admission into a
 * loud failure instead of two writers allocating seq for one timeline. That guarantee is what lets
 * {@link AiEventSink} seed a plain counter from {@code MAX(seq)} with no locking in the database.
 *
 * <h2>Stopping fails closed</h2>
 * A stop names the run it wants stopped. If the conversation's active run is a <em>different</em> one,
 * the request is refused with 409 ({@code ai.run.stale_stop}) and nothing is killed: the caller's
 * {@code runId} came from a {@code run_started} frame it may have received a whole turn ago, and the one
 * unrecoverable mistake available here is killing the answer the user is currently watching. An
 * already-terminal run is a 200 no-op, which is what makes the stop button safe to press twice.
 *
 * <h2>The emitter is an observer</h2>
 * {@link #sendMessage} returns an emitter that is registered as the run's first observer and then
 * handed off. {@link #attach} is the reconnect path: it replays the persisted events after the client's
 * cursor and only then starts tailing live ones, registering the observer <em>before</em> the replay is
 * flushed so a frame cannot slip into the gap. Neither path can stop the run by disconnecting.
 */
@Slf4j
@Service
public class AiRunService {

    /**
     * Answer to both 409s — a second admission and a stale stop — because both mean the same thing to
     * the user: this conversation already has an answer being generated. Spelled with unicode escapes
     * because {@code style/rmq_checkstyle.xml} rejects non-ASCII characters in Java sources; it reads
     * "this conversation already has an answer in progress".
     */
    static final String BUSY_MESSAGE =
            "\u8BE5\u4F1A\u8BDD\u5DF2\u6709\u6B63\u5728\u8FDB\u884C\u7684\u56DE\u7B54";

    /** Identifier an operator can grep for when a stop was refused because it named the wrong run. */
    static final String STALE_STOP_CODE = "ai.run.stale_stop";

    /**
     * Stable codes of a refusal that had to travel <em>inside</em> the event stream rather than as an
     * HTTP status; see {@link #refusalStream(BusinessException)}. Derived from the status because
     * {@link BusinessException} carries no string code of its own, and one greppable token per refusal
     * kind is what an operator needs in a log line and what the UI shows in its error block.
     */
    static final String REFUSED_NOT_FOUND_CODE = "ai.conversation.not_found";
    static final String REFUSED_BUSY_CODE = "ai.run.busy";
    static final String REFUSED_INVALID_CODE = "ai.request.invalid";
    static final String REFUSED_CODE = "ai.run.refused";

    static final String OVERLOADED_MESSAGE = "AI chat capacity is temporarily exhausted";
    static final String OVERLOADED_HINT = "Wait for the active answer to finish, then retry.";

    /** {@code AiMessageDTO.message} is {@code @Size(max = 8192)}; enforced here so no caller bypasses it. */
    private static final int MAX_MESSAGE_CHARS = 8192;

    private final AiConversationService conversationService;
    private final AiConversationRepository conversationRepository;
    private final AiRunRepository runRepository;
    private final AiEventRepository eventRepository;
    private final AgentRunRegistry registry;
    private final AiRunExecutor runExecutor;
    private final LlmConfigService llmConfigService;
    private final RmqctlWorkspace workspace;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AiRunService(AiConversationService conversationService,
                        AiConversationRepository conversationRepository,
                        AiRunRepository runRepository,
                        AiEventRepository eventRepository,
                        AgentRunRegistry registry,
                        AiRunExecutor runExecutor,
                        LlmConfigService llmConfigService,
                        RmqctlWorkspace workspace,
                        ObjectMapper objectMapper) {
        this(conversationService, conversationRepository, runRepository, eventRepository, registry,
                runExecutor, llmConfigService, workspace, objectMapper, Clock.systemUTC());
    }

    AiRunService(AiConversationService conversationService,
                 AiConversationRepository conversationRepository,
                 AiRunRepository runRepository,
                 AiEventRepository eventRepository,
                 AgentRunRegistry registry,
                 AiRunExecutor runExecutor,
                 LlmConfigService llmConfigService,
                 RmqctlWorkspace workspace,
                 ObjectMapper objectMapper,
                 Clock clock) {
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.registry = registry;
        this.runExecutor = runExecutor;
        this.llmConfigService = llmConfigService;
        this.workspace = workspace;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Admits one turn and starts generating it.
     *
     * <p>The returned emitter is already streaming: the user's turn is persisted, the observer is
     * attached and {@code run_started} has been published before this returns, so a client that reads
     * the response body cannot miss the first frame.
     *
     * @throws BusinessException 404 when the conversation is not the caller's, 400 for an unusable
     *     message, 409 when the conversation already has an answer in progress
     * @throws LlmGatewayException 400 when no LLM provider is configured
     */
    public SseEmitter sendMessage(Long conversationId, RunRequest request) {
        Objects.requireNonNull(request, "request");
        String owner = AiConversationService.currentOwner();
        RmqAiConversation conversation = conversationService.requireOwned(conversationId, owner);
        String message = requireMessage(request.message());
        LlmConfigVO config = llmConfigService.getConfig();
        if (config == null || !config.isReady()) {
            throw new LlmGatewayException(400, "llm.config.incomplete",
                    "LLM provider is not configured or enabled",
                    "Configure and enable an LLM provider in Studio LLM Settings before using AI chat.");
        }
        String engine = resolveEngine(request.engine(), conversation.getEngine(), config);
        String model = resolveModel(request.model(), conversation.getModel(), config);
        if (!StringUtils.hasText(model)) {
            throw new LlmGatewayException(400, "llm.config.model_required",
                    "No model is configured for the " + engine + " engine",
                    "Pick a model in the AI settings.");
        }

        // Admission: one active run per conversation, which is what makes a single writer per timeline
        // a guarantee rather than a hope.
        runRepository.findActiveByConversationId(conversationId).ifPresent(active -> {
            log.info("refusing a second run for conversation {}: run {} is {}",
                    conversationId, active.getId(), active.getStatus());
            throw busy();
        });

        int turn = runRepository.maxTurn(conversationId) + 1;
        // MAX(seq) and never rmq_ai_conversation.last_seq: that column is a cache for the reconnect fast
        // path and a run that died mid-flush leaves it low, which would reuse timeline positions.
        int seedSeq = eventRepository.maxSeq(conversationId);
        String resumeSessionId = resolveResume(request.resume(), conversation);
        RmqAiRun run = insertRun(conversation, turn, engine, model, resumeSessionId, seedSeq);

        // Prepared at admission, not on the worker: an unusable workspace configuration is an operator
        // error the caller should hear about now, and a degraded conversation must say so in its first
        // persisted event rather than in a log line nobody reads.
        RmqctlWorkspace.Preparation preparation =
                workspace.prepare(conversation.getId(), conversation.getInstanceId()).orElse(null);

        long timeoutMillis = runExecutor.streamTimeoutMillis(engine);
        AgentRunHandle handle = runExecutor.newHandle(run.getId());
        AiEventSink sink = runExecutor.newSink(conversation.getId(), run.getId(), turn, seedSeq);
        AgentStreamSession session = runExecutor.newSession(run.getId(), timeoutMillis);
        AiRunExecutor.RunContext context = new AiRunExecutor.RunContext(conversation, run, sink, handle,
                config, engine, message, resumeSessionId, request.enhance(),
                Duration.ofMillis(timeoutMillis), preparation);
        registry.register(run.getId(), handle);
        try {
            // enhancedPrompt stays null on this row: the rewrite happens on the worker, after the turn
            // is persisted, and it reaches the timeline as a thinking block with source=enhance — which
            // is where it belongs, since merging it into the model's reasoning is the bug this design
            // exists to prevent.
            int admissionSeq = sink.writeUser(message, null);
            if (preparation == null) {
                admissionSeq = sink.writeTimeline(degradedNotice(conversation));
            }
            applyTurnToConversation(conversation, message, request.mode());
            session.noteWatermark(admissionSeq);
            registry.attach(run.getId(), session);
            session.finishReplay();
            registry.publish(run.getId(), 0L, new LiveEvent.RunStarted(run.getId(), conversation.getId(),
                    conversation.getTitle(), turn));
            runExecutor.submit(context);
        } catch (RejectedExecutionException exception) {
            log.warn("agent run {} was rejected: the run executor is saturated", run.getId());
            reject(context, session);
        } catch (RuntimeException exception) {
            log.error("could not start agent run {}", run.getId(), exception);
            runExecutor.terminate(context, RunStatus.FAILED, StopReason.PROVIDER_ERROR,
                    AiRunExecutor.ERROR_CODE_INTERNAL, exception.toString());
            session.sendControlError(500, AiRunExecutor.ERROR_CODE_INTERNAL,
                    "Failed to start the agent run", "Retry the message.");
            session.complete();
        }
        return session.emitter();
    }

    /**
     * Attaches an observer to a run, replaying everything after {@code afterSeq} first. This is the
     * reconnect path: the client that lost its connection, a second tab, or a page reload that found an
     * {@code activeRun}.
     *
     * <p>The observer is registered before the replay is flushed and every live frame carries the seq of
     * the row it belongs to, so the two hazards of replay-then-tail are both covered: nothing is lost in
     * the gap, and nothing already replayed is delivered twice.
     */
    public SseEmitter attach(Long runId, int afterSeq) {
        String owner = AiConversationService.currentOwner();
        RmqAiRun run = requireOwnedRun(runId, owner);
        AgentStreamSession session = runExecutor.newSession(run.getId(),
                runExecutor.streamTimeoutMillis(run.getEngine()));
        AgentEventProjector projector = new AgentEventProjector(run.getId());
        List<RmqAiEvent> rows = eventRepository.findByConversationIdAfterSeq(run.getConversationId(),
                Math.max(0, afterSeq), AiConversationService.DEFAULT_TIMELINE_LIMIT);
        boolean terminalReplayed = false;
        for (RmqAiEvent row : rows) {
            session.noteWatermark(row.getSeq());
            Optional<TimelineEvent> event = AiEventCodec.read(objectMapper, row);
            if (event.isEmpty()) {
                continue;
            }
            terminalReplayed = terminalReplayed || event.get() instanceof TimelineEvent.RunStatus;
            projector.replay(event.get()).ifPresent(session::sendReplayed);
        }
        boolean live = registry.attach(run.getId(), session);
        session.finishReplay();
        if (!live) {
            // Nothing is generating here any more, so no further frame will ever arrive. A run reaped by
            // the startup reaper or the orphan sweep has no run_status row at all — there was no sink to
            // write one — so the terminal frame is synthesised from the row instead. This is exactly why
            // a reload must consult rmq_ai_run.status and not only the events.
            RunStatus status = statusOf(run.getStatus());
            if (!terminalReplayed && status != null && status.isTerminal()) {
                session.sendReplayed(new LiveEvent.RunFinished(run.getId(), status, run.getDurationMs()));
            }
            session.complete();
        }
        log.debug("attached an observer to agent run {} after seq {} ({} replayed row(s), live={})",
                run.getId(), afterSeq, rows.size(), live);
        return session.emitter();
    }

    /**
     * A stream that carries one refusal and nothing else: an {@code event: error} frame with the status,
     * a stable code and the reason, then {@code done}.
     *
     * <p>This exists because the two streaming endpoints <strong>cannot</strong> answer a refusal the way
     * the other nine do. Their client sends {@code Accept: text/event-stream} and their mappings declare
     * the same as producible, so when {@link #sendMessage} or {@link #attach} throws, no JSON converter
     * can write the {@code Result} envelope {@code GlobalExceptionHandler} returns: the exception handler
     * itself fails on content negotiation and the browser is left with a 500 error page instead of the
     * reason. The transport error frame is the one shape that can still reach the client, and it is the
     * shape the TypeScript client turns into a thrown {@code AiStreamError} carrying {@code status},
     * {@code code}, {@code message} and {@code hint} — the same channel {@link #reject} already uses for
     * a saturated executor.
     *
     * <p>Only the two documented refusal types are converted. Anything else is a bug and is left to
     * propagate, because hiding an {@code NullPointerException} inside a stream frame would cost more
     * than the ugly 500 it avoids.
     */
    public SseEmitter refusalStream(BusinessException refusal) {
        return refusalStream(refusal.getCode(), codeForStatus(refusal.getCode()), refusal.getMessage(), null);
    }

    /** The same, for a refusal that already carries its own stable code and hint. */
    public SseEmitter refusalStream(LlmGatewayException refusal) {
        return refusalStream(refusal.getStatusCode(), refusal.getCode(), refusal.getMessage(), refusal.getHint());
    }

    /**
     * The same, for a refusal that is not an exception the services threw: a request body that failed
     * jakarta validation, where the caller already knows the status, the code and the reason.
     */
    public SseEmitter refusalStream(int status, String code, String message, String hint) {
        log.warn("refusing a run stream with {} {}: {}", status, code, message);
        // runId 0: no run was admitted, so there is nothing to observe, cancel or reap. The session is
        // completed before it is returned, which ResponseBodyEmitter buffers and replays on initialise —
        // the same ordering the gateway's error emitter has always relied on.
        AgentStreamSession session = runExecutor.newSession(0L, runExecutor.streamTimeoutMillis(null));
        session.sendControlError(status, code, message, hint);
        session.complete();
        return session.emitter();
    }

    private static String codeForStatus(int status) {
        return switch (status) {
            case 400 -> REFUSED_INVALID_CODE;
            case 404 -> REFUSED_NOT_FOUND_CODE;
            case 409 -> REFUSED_BUSY_CODE;
            default -> REFUSED_CODE;
        };
    }

    /**
     * Stops a run. Idempotent, and it never kills anything except the run it was given.
     *
     * <p>The run row is re-read before it is returned so the caller sees the terminal state the worker
     * wrote, when the worker got there first.
     *
     * @throws BusinessException 404 when the run does not exist or belongs to somebody else, 409 when a
     *     different run of the same conversation is the active one
     */
    public RmqAiRun stop(Long runId) {
        String owner = AiConversationService.currentOwner();
        RmqAiRun run = requireOwnedRun(runId, owner);
        Optional<RmqAiRun> active = runRepository.findActiveByConversationId(run.getConversationId());
        if (active.isPresent() && !Objects.equals(active.get().getId(), run.getId())) {
            log.warn("{}: refused to stop run {} of conversation {}, run {} is the active one",
                    STALE_STOP_CODE, run.getId(), run.getConversationId(), active.get().getId());
            throw new BusinessException(409, BUSY_MESSAGE);
        }
        RunStatus status = statusOf(run.getStatus());
        if (status == null || status.isTerminal()) {
            log.debug("stop of run {} is a no-op: it is already {}", run.getId(), run.getStatus());
            return run;
        }
        if (registry.stop(run.getId(), AbortReason.USER_STOP)) {
            log.info("agent run {} is being stopped at the user's request", run.getId());
        } else {
            // The row says active but nothing in this process owns it, so no worker will ever write the
            // terminal state. Writing it here is what keeps the stop button from spinning until the
            // orphan sweep gets around to it.
            log.warn("run {} is non-terminal with no owner in this process; stopping it directly", run.getId());
            runExecutor.terminate(detachedContext(run), RunStatus.STOPPED, StopReason.USER_STOP, null, null);
        }
        return runRepository.findById(runId).orElse(run);
    }

    /**
     * Persists the generation speed the client measured while the run streamed. The server sees
     * the same deltas but not the same clock — a client estimate is what the user watched, so the
     * replay should show that exact number. Idempotent: a duplicate report simply overwrites.
     *
     * @throws BusinessException 404 when the run does not exist or belongs to somebody else
     */
    public RmqAiRun reportSpeed(Long runId, double tokensPerSecond) {
        String owner = AiConversationService.currentOwner();
        RmqAiRun run = requireOwnedRun(runId, owner);
        run.setTokensPerSecond(tokensPerSecond);
        runRepository.update(run);
        return run;
    }

    /** The run row, owner-checked. Somebody else's run is a 404, not a 403: no id enumeration. */
    RmqAiRun requireOwnedRun(Long runId, String owner) {
        RmqAiRun run = runRepository.findById(runId)
                .orElseThrow(() -> {
                    log.debug("AI run {} not found", runId);
                    return new BusinessException(404, "AI run not found");
                });
        conversationService.requireOwned(run.getConversationId(), owner);
        return run;
    }

    private RmqAiRun insertRun(RmqAiConversation conversation, int turn, String engine, String model,
                               String resumeSessionId, int seedSeq) {
        LocalDateTime now = LocalDateTime.now(clock);
        RmqAiRun run = new RmqAiRun();
        run.setConversationId(conversation.getId());
        run.setTurn(turn);
        run.setStatus(RunStatus.QUEUED.name());
        run.setEngine(engine);
        run.setModel(model);
        run.setResumedFrom(resumeSessionId);
        // The user's turn is this run's first row, so the run owns every seq above the seed.
        run.setStartSeq(seedSeq + 1);
        run.setEndSeq(seedSeq);
        run.setGmtCreate(now);
        run.setGmtModified(now);
        try {
            RmqAiRun inserted = runRepository.insert(run);
            log.info("admitted agent run {} for conversation {} (turn {}, engine {}, model {}, resume {})",
                    inserted.getId(), conversation.getId(), turn, engine, model,
                    resumeSessionId == null ? "no" : "yes");
            return inserted;
        } catch (DuplicateKeyException exception) {
            // uk_ai_run_conversation_turn: two admissions raced for the same turn. Refusing is correct —
            // admitting both would give one timeline two writers and one seq counter each.
            log.warn("a second run was admitted for conversation {} turn {} at the same time",
                    conversation.getId(), turn);
            throw busy();
        }
    }

    /**
     * Turns an admission the executor rejected into a run that ended for a reason the user can see: a
     * persisted error and terminal state, a transport-level error frame, then {@code done}. Saturation
     * is a fact about this server, and hiding it behind a spinner that never resolves is worse than
     * saying no.
     */
    private void reject(AiRunExecutor.RunContext context, AgentStreamSession session) {
        context.getSink().writeTimeline(new TimelineEvent.Error(AiRunExecutor.ERROR_CODE_OVERLOADED,
                OVERLOADED_MESSAGE, OVERLOADED_HINT));
        session.sendControlError(503, AiRunExecutor.ERROR_CODE_OVERLOADED, OVERLOADED_MESSAGE,
                OVERLOADED_HINT);
        runExecutor.terminate(context, RunStatus.FAILED, StopReason.OVERLOADED,
                AiRunExecutor.ERROR_CODE_OVERLOADED, OVERLOADED_MESSAGE);
    }

    /**
     * The first turn names the conversation and the request may change its mode. {@code gmt_modified} is
     * bumped on every turn, including one that changes nothing, because the conversation list is ordered
     * by it: a conversation answered just now has to float to the top.
     */
    private void applyTurnToConversation(RmqAiConversation conversation, String message, String mode) {
        RmqAiConversation update = new RmqAiConversation();
        update.setId(conversation.getId());
        if (StringUtils.hasText(mode)) {
            String normalized = AiConversationService.normalizeMode(mode);
            if (!normalized.equals(conversation.getMode())) {
                update.setMode(normalized);
                conversation.setMode(normalized);
            }
        }
        // Only the placeholder is replaced, so a title the user chose survives later turns.
        if (AiConversationService.DEFAULT_TITLE.equals(conversation.getTitle())) {
            String title = AiConversationService.deriveTitle(message);
            update.setTitle(title);
            conversation.setTitle(title);
        }
        update.setGmtModified(LocalDateTime.now(clock));
        try {
            conversationRepository.update(update);
        } catch (RuntimeException exception) {
            // A title is not worth a run: the turn is already persisted and the answer is worth more.
            log.warn("could not update conversation {} after admitting a run: {}",
                    conversation.getId(), exception.toString());
        }
    }

    /**
     * A context for a terminal write with no worker: no handle to kill, no provider configuration to
     * stream with, and a sink seeded from the current {@code MAX(seq)} so the terminal event lands after
     * everything the abandoned run managed to write.
     */
    private AiRunExecutor.RunContext detachedContext(RmqAiRun run) {
        RmqAiConversation conversation = conversationRepository.findById(run.getConversationId()).orElse(null);
        int seedSeq = eventRepository.maxSeq(run.getConversationId());
        AiEventSink sink = runExecutor.newSink(run.getConversationId(), run.getId(),
                run.getTurn() == null ? 0 : run.getTurn(), seedSeq);
        return new AiRunExecutor.RunContext(conversation, run, sink, null, null, run.getEngine(), null,
                null, false, null, null);
    }

    /** The notice a conversation gets when it has to do without RocketMQ tools. */
    private static TimelineEvent.Notice degradedNotice(RmqAiConversation conversation) {
        return StringUtils.hasText(conversation.getInstanceId())
                ? RmqctlWorkspace.degradedNotice()
                : RmqctlWorkspace.unboundNotice();
    }

    private static String requireMessage(String message) {
        if (!StringUtils.hasText(message)) {
            throw new BusinessException(400, "message must not be blank");
        }
        String trimmed = message.strip();
        if (trimmed.length() > MAX_MESSAGE_CHARS) {
            throw new BusinessException(400,
                    "message must not exceed " + MAX_MESSAGE_CHARS + " characters");
        }
        return trimmed;
    }

    private static BusinessException busy() {
        return new BusinessException(409, BUSY_MESSAGE);
    }

    /** Per-request wins, then what the conversation was created with, then the stored configuration. */
    static String resolveEngine(String requested, String stored, LlmConfigVO config) {
        String candidate = firstNonBlank(requested, stored);
        if (candidate == null) {
            return config.normalizeEngine();
        }
        String normalized = candidate.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case LlmConfigVO.ENGINE_HTTP, LlmConfigVO.ENGINE_CLAUDE_CODE, LlmConfigVO.ENGINE_QODER ->
                    normalized;
            default -> {
                log.warn("ignoring unknown agent engine {}; falling back to {}", candidate,
                        config.normalizeEngine());
                yield config.normalizeEngine();
            }
        };
    }

    /** Per-request wins, then the conversation's, then the stored configuration. Case is preserved. */
    static String resolveModel(String requested, String stored, LlmConfigVO config) {
        String candidate = firstNonBlank(requested, stored);
        if (candidate != null) {
            return candidate;
        }
        return StringUtils.hasText(config.getModel()) ? config.getModel().trim() : null;
    }

    /**
     * An explicit {@code resume=false} starts a fresh provider session; anything else resumes whatever
     * the conversation last recorded. A conversation with no recorded session id has nothing to resume,
     * which is the normal state of its first turn.
     */
    static String resolveResume(Boolean resume, RmqAiConversation conversation) {
        if (Boolean.FALSE.equals(resume)) {
            return null;
        }
        String sessionId = conversation.getRuntimeSessionId();
        return StringUtils.hasText(sessionId) ? sessionId.trim() : null;
    }

    static RunStatus statusOf(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return RunStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.warn("unknown run status {}; treating it as terminal so nothing is killed on a guess",
                    value);
            return null;
        }
    }

    private static String firstNonBlank(String requested, String stored) {
        if (StringUtils.hasText(requested)) {
            return requested.trim();
        }
        return StringUtils.hasText(stored) ? stored.trim() : null;
    }

    /**
     * One turn as the streaming endpoint received it.
     *
     * @param engine per-request engine override; null keeps the conversation's
     * @param model per-request model override; null keeps the conversation's
     * @param mode conversation mode to switch to; null keeps the current one
     * @param enhance rewrite the message into a structured prompt first, streamed back as thinking with
     *     {@code source=enhance} so it is never mistaken for the model's reasoning
     * @param resume {@code false} starts a fresh provider session, null or true resumes the recorded one
     */
    public record RunRequest(String message, String model, String engine, String mode, boolean enhance,
                             Boolean resume) {

        /** A plain turn with no overrides. */
        public static RunRequest of(String message) {
            return new RunRequest(message, null, null, null, false, null);
        }
    }
}
