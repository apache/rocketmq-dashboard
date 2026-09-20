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
import org.apache.rocketmq.studio.ops.ai.conversation.event.LiveEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * One browser's view of one run: an SSE emitter plus the observer bookkeeping around it, and nothing
 * else.
 *
 * <h2>The one structural rule</h2>
 * {@code onCompletion}, {@code onTimeout} and {@code onError} <strong>detach the observer</strong>.
 * They do not cancel the run. The session this replaces wired all three to {@code cancel()}, so a TCP
 * disconnect killed the worker — which meant closing a tab aborted the answer, and the only way to
 * "stop" was to hang up, which is also why stopping could never kill the subprocess reliably. Here the
 * run belongs to {@link AiRunExecutor} and lives on its own thread; a detached client reconnects and
 * replays from the database. The honest cost is that a run nobody watches any more still runs to
 * completion, bounded by the CLI wall-clock budget and the orphan sweep.
 *
 * <h2>Replay, tail, and the race between them</h2>
 * An observer that attaches to a run already in progress is handed the persisted events first and
 * then tails the live ones. Two hazards come with that and both are handled here:
 * <ul>
 *   <li><strong>Duplication.</strong> A live frame whose persisted twin was already in the replay must
 *       not be sent twice. Every live frame therefore travels with the seq of the row it belongs to
 *       (0 when it has no persisted counterpart, e.g. a text delta), the session remembers the highest
 *       seq it replayed, and {@link #deliver} drops anything at or below it.</li>
 *   <li><strong>Interleaving.</strong> The observer is registered <em>before</em> the replay is sent,
 *       so a frame cannot slip in between two replayed rows and reorder the transcript. Frames that
 *       arrive during the replay are buffered and drained afterwards, still deduplicated. A frame
 *       published in that window belongs to a row written after the replay query, so it can only be
 *       too new, never too old.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * Every domain frame goes out as {@code event: agent} with the polymorphic {@code type} discriminator
 * inside the JSON, so the client has one dispatch instead of one SSE listener per event kind.
 * {@code event: error} and {@code event: done} are the two control frames. A {@code comment("hb")}
 * heartbeat goes out every {@value #HEARTBEAT_INTERVAL_MILLIS} ms: the client's idle timeout is twice
 * that, so one late heartbeat is survivable and a buffering proxy in between is not.
 *
 * <p>All sends are serialised on one lock. An {@link SseEmitter} is not safe to write from two threads
 * at once, and this object is written from the worker thread, the replaying HTTP thread and the
 * heartbeat scheduler.
 */
@Slf4j
final class AgentStreamSession {

    /** SSE event name carrying every domain frame. */
    static final String AGENT_EVENT = "agent";

    /** SSE event name of the transport-level terminal error frame. */
    static final String ERROR_EVENT = "error";

    /** SSE event name of the terminal control frame. Replaces the old {@code data: [DONE]} sentinel. */
    static final String DONE_EVENT = "done";

    /** Heartbeat comment body. Carries no data, so a client that ignores comments is unaffected. */
    static final String HEARTBEAT_COMMENT = "hb";

    /** How often the heartbeat goes out. The client's idle timeout is derived as twice this. */
    static final long HEARTBEAT_INTERVAL_MILLIS = 15_000L;

    /**
     * Cap on frames buffered while a replay is in flight. A replay is a bounded database read, so
     * reaching this means something is pathologically slow; dropping the newest frames is the safe
     * failure because the client re-reads the persisted timeline when the run finishes.
     */
    private static final int MAX_BUFFERED_DURING_REPLAY = 1024;

    private enum State {
        REPLAYING, LIVE, CLOSED
    }

    private final long runId;
    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final Consumer<AgentStreamSession> detachListener;
    private final ScheduledExecutorService heartbeatScheduler;

    private final AtomicReference<State> state = new AtomicReference<>(State.REPLAYING);
    private final AtomicLong maxSeqSeen = new AtomicLong();
    private final AtomicBoolean detachNotified = new AtomicBoolean();
    private final Object sendLock = new Object();
    private final Deque<LiveFrame> buffered = new ArrayDeque<>();

    private ScheduledFuture<?> heartbeat;
    private boolean overflowLogged;

    /**
     * @param heartbeatScheduler nullable, so a unit test can construct a session without one; in
     *     production it is the scheduler {@link AiRunExecutor} owns
     */
    AgentStreamSession(long runId, SseEmitter emitter, ObjectMapper objectMapper,
                       Consumer<AgentStreamSession> detachListener,
                       ScheduledExecutorService heartbeatScheduler) {
        this.runId = runId;
        this.emitter = Objects.requireNonNull(emitter, "emitter");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.detachListener = Objects.requireNonNull(detachListener, "detachListener");
        this.heartbeatScheduler = heartbeatScheduler;
        // Detach only. Cancelling the run here is precisely the coupling this class exists to remove.
        emitter.onCompletion(this::detach);
        emitter.onTimeout(this::detach);
        emitter.onError(ignored -> detach());
        startHeartbeat();
    }

    SseEmitter emitter() {
        return emitter;
    }

    long runId() {
        return runId;
    }

    /**
     * Records a seq this observer has already seen, as the dedup watermark for the live frames that
     * follow. Called for every replayed row, including the ones that project to no live frame at all —
     * the user's own turn among them — so the watermark is the highest seq replayed and not merely the
     * highest seq sent.
     */
    void noteWatermark(long seq) {
        maxSeqSeen.accumulateAndGet(seq, Math::max);
    }

    /**
     * Sends one replayed frame. Called before {@link #finishReplay()}, in ascending seq order, so the
     * transcript a reconnecting client sees is the one the database holds.
     */
    void sendReplayed(LiveEvent event) {
        sendAgent(event);
    }

    /**
     * Ends the replay: live frames stop being buffered and the buffer is drained in arrival order.
     * A session that is used without a replay (the observer that started the run) is simply moved to
     * LIVE, with the watermark the caller already recorded through {@link #noteWatermark(long)}.
     */
    void finishReplay() {
        Deque<LiveFrame> pending;
        synchronized (sendLock) {
            if (state.get() != State.REPLAYING) {
                return;
            }
            state.set(State.LIVE);
            pending = new ArrayDeque<>(buffered);
            buffered.clear();
        }
        pending.forEach(frame -> deliver(frame.seq(), frame.event()));
    }

    /**
     * Hands a live frame to this observer.
     *
     * @param seq the seq of the persisted row this frame belongs to, or 0 when it has none. A frame
     *     with no persisted counterpart is never dropped: it cannot be replayed from the database
     *     either, and losing a delta is worse than showing it twice.
     * @return false when the observer is gone and the caller may stop trying
     */
    boolean deliver(long seq, LiveEvent event) {
        if (event == null || state.get() == State.CLOSED) {
            return false;
        }
        synchronized (sendLock) {
            if (state.get() == State.REPLAYING) {
                if (buffered.size() >= MAX_BUFFERED_DURING_REPLAY) {
                    if (!overflowLogged) {
                        overflowLogged = true;
                        log.warn("run {} replay buffered {} live frames; dropping the rest",
                                runId, MAX_BUFFERED_DURING_REPLAY);
                    }
                    return true;
                }
                buffered.addLast(new LiveFrame(seq, event));
                return true;
            }
        }
        if (seq > 0 && seq <= maxSeqSeen.get()) {
            log.debug("dropping live frame seq {} of run {}: already replayed up to {}",
                    seq, runId, maxSeqSeen.get());
            return true;
        }
        return sendAgent(event);
    }

    /** The transport-level error control frame, for a run that could not be admitted at all. */
    void sendControlError(int status, String code, String message, String hint) {
        synchronized (sendLock) {
            if (state.get() == State.CLOSED) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(ERROR_EVENT).data(objectMapper.writeValueAsString(
                        new ControlError(status, code, message, hint == null ? "" : hint))));
            } catch (IOException | RuntimeException exception) {
                log.debug("could not send the error frame of run {}: {}", runId, exception.toString());
                detach();
            }
        }
    }

    /**
     * Sends {@code done} and closes the emitter, exactly once. Idempotent: the run's terminal state is
     * written once by the executor, but every observer may already have been closed by its own
     * transport.
     */
    void complete() {
        if (!beginTerminal()) {
            return;
        }
        synchronized (sendLock) {
            try {
                emitter.send(SseEmitter.event().name(DONE_EVENT).data(""));
                emitter.complete();
            } catch (IOException | RuntimeException exception) {
                log.debug("could not complete the stream of run {}: {}", runId, exception.toString());
                closeQuietly();
            } finally {
                stopHeartbeat();
            }
        }
        notifyDetach();
    }

    /** Closes the emitter as a failure, exactly once. */
    void completeWithError(Throwable throwable) {
        if (!beginTerminal()) {
            return;
        }
        synchronized (sendLock) {
            try {
                emitter.completeWithError(throwable);
            } catch (RuntimeException exception) {
                log.debug("could not fail the stream of run {}: {}", runId, exception.toString());
            } finally {
                stopHeartbeat();
            }
        }
        notifyDetach();
    }

    /**
     * Unregisters this observer. Called by the emitter callbacks and by any send that failed, and
     * never touches the run.
     */
    void detach() {
        if (state.getAndSet(State.CLOSED) == State.CLOSED) {
            notifyDetach();
            return;
        }
        synchronized (sendLock) {
            buffered.clear();
        }
        stopHeartbeat();
        notifyDetach();
    }

    boolean isClosed() {
        return state.get() == State.CLOSED;
    }

    /** The exactly-once gate on the terminal frames, the idiom the replaced session used. */
    private boolean beginTerminal() {
        State previous = state.getAndUpdate(current -> current == State.CLOSED ? current : State.CLOSED);
        return previous != State.CLOSED;
    }

    private boolean sendAgent(LiveEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (IOException | RuntimeException exception) {
            // A frame that cannot be serialised is a contract bug, not a client problem: log it loudly
            // and keep the run going, the persisted timeline is still complete.
            log.warn("could not serialise a live frame of run {}: {}", runId, exception.toString());
            return true;
        }
        synchronized (sendLock) {
            if (state.get() == State.CLOSED) {
                return false;
            }
            try {
                emitter.send(SseEmitter.event().name(AGENT_EVENT).data(payload));
                return true;
            } catch (IOException | RuntimeException exception) {
                log.debug("observer of run {} went away: {}", runId, exception.toString());
                detach();
                return false;
            }
        }
    }

    private void startHeartbeat() {
        if (heartbeatScheduler == null || heartbeatScheduler.isShutdown()) {
            return;
        }
        try {
            heartbeat = heartbeatScheduler.scheduleAtFixedRate(this::heartbeat,
                    HEARTBEAT_INTERVAL_MILLIS, HEARTBEAT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            log.debug("could not schedule the heartbeat of run {}: {}", runId, exception.toString());
        }
    }

    private void heartbeat() {
        if (state.get() == State.CLOSED) {
            stopHeartbeat();
            return;
        }
        synchronized (sendLock) {
            if (state.get() == State.CLOSED) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment(HEARTBEAT_COMMENT));
            } catch (IOException | RuntimeException exception) {
                log.debug("heartbeat of run {} failed, detaching: {}", runId, exception.toString());
                detach();
            }
        }
    }

    private void stopHeartbeat() {
        ScheduledFuture<?> scheduled = heartbeat;
        if (scheduled != null) {
            scheduled.cancel(false);
            heartbeat = null;
        }
    }

    private void closeQuietly() {
        try {
            emitter.complete();
        } catch (RuntimeException exception) {
            log.debug("could not close the emitter of run {}: {}", runId, exception.toString());
        }
    }

    private void notifyDetach() {
        if (detachNotified.compareAndSet(false, true)) {
            detachListener.accept(this);
        }
    }

    /** A live frame waiting for a replay to finish, with the seq it must be deduplicated against. */
    private record LiveFrame(long seq, LiveEvent event) {
    }

    /**
     * Payload of the {@code event: error} control frame. A plain record rather than a map so the four
     * field names cannot drift from what the client's {@code parseStreamError} reads.
     */
    private record ControlError(int status, String code, String message, String hint) {
    }
}
