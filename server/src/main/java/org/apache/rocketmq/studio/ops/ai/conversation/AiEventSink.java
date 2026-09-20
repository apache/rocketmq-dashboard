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
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.apache.rocketmq.studio.ops.ai.conversation.event.LiveEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The single writer of one run's timeline: it allocates seq, coalesces deltas into blocks, serialises
 * them, and publishes the live frames. One sink per run, and one run per conversation, so there is
 * exactly one writer per conversation — which is the invariant the whole seq scheme rests on.
 *
 * <h2>seq allocation: seeded from MAX(seq), not from the conversation row</h2>
 * Admission reads {@code eventRepository.maxSeq(conversationId)} — an index-only query on
 * {@code uk_ai_event_conversation_seq} — and hands the value to this class as the seed. Every flush
 * then reserves {@code n} contiguous seqs for its batch of {@code n} rows, and after the write
 * {@code rmq_ai_conversation.last_seq} is updated to the high-water mark.
 *
 * <p>That column is a <em>cache</em> for the reconnect fast path and never the authority. A run that
 * crashed between an insert and the cache update leaves it low, and seeding from it would reuse
 * timeline positions that already exist. {@code uk_ai_event_conversation_seq} is the backstop that
 * turns such a bug into a thrown {@link DuplicateKeyException} instead of a silently duplicated or
 * shifted timeline, and {@link #insert} deliberately lets that one escape rather than logging it away.
 *
 * <p><strong>Rejected alternative.</strong> The race-free counter idiom
 * {@code UPDATE rmq_ai_conversation SET last_seq = LAST_INSERT_ID(last_seq + n)} would allocate seq in
 * the database and needs no seed at all. It was rejected because reading the value back requires
 * either {@code @Select}/{@code @Update} annotations on the mapper — which breaks this project's
 * "no annotations, plain {@code BaseMapper}" convention — or a {@code setSql} + {@code selectObjs}
 * dance inside one {@code @Transactional} connection block, which is subtler to get right than the
 * thing it replaces. And once the state machine guarantees a single writer per conversation, it buys
 * nothing: there is no race left to be free of.
 *
 * <h2>Buffered writes</h2>
 * The coalescing rules are the ones the web client already models in
 * {@code coalesceLiveToTimeline} ({@code web/src/pages/ai/render/foldTimeline.ts}), whose doc comment
 * names this class as the other half of the same rule:
 * <ul>
 *   <li>adjacent persisted {@code text} / {@code thinking} blocks are merged, per scope, up to
 *       {@link AgentEventProjector#COALESCE_MAX_CHARS} characters — the projector owns that cut;</li>
 *   <li>and up to {@value #DEFAULT_FLUSH_INTERVAL_MILLIS} ms of inactivity — this class owns that one,
 *       because a pure function on the client cannot model a clock;</li>
 *   <li>a thinking block is also cut when its {@link org.apache.rocketmq.studio.ops.ai.conversation.event.ThinkingSource}
 *       changes, so a prompt rewrite never merges into model reasoning;</li>
 *   <li>every other event type is a durability boundary: pending content is flushed before it, so the
 *       persisted order is the arrival order.</li>
 * </ul>
 * Without this, one token is one row: a single answer writes thousands of rows and the timeline read
 * becomes the most expensive query in the system.
 *
 * <p><strong>Live deltas are not buffered.</strong> Coalescing exists to keep the database cheap, not
 * to make the stream smoother; a text delta goes out the moment the provider produces it.
 *
 * <h2>Serialisation</h2>
 * All writes go through one queue under one lock, so callbacks cannot interleave: the timed flush runs
 * on a scheduler thread while the provider streams on its own, and a durability boundary may be
 * reached from either. A re-entrant write (a flush triggered from inside a write) only enqueues; the
 * outermost caller drains. The live publish happens <em>outside</em> the lock, because a slow SSE
 * client must not stall the run.
 *
 * <h2>Failure discipline</h2>
 * A {@link RuntimeException} from a persistence call is caught and logged, and the run continues:
 * losing one timeline row must not cost the user the answer they are watching being generated. That is
 * the same discipline {@code QueryHistoryService}'s retention purge follows. The one exception is
 * {@link DuplicateKeyException}, which means the allocator itself is wrong and continuing would
 * corrupt every later position.
 */
@Slf4j
public final class AiEventSink {

    /**
     * Inactivity window after which buffered content is persisted anyway. Matches the client's model
     * of the server's writer; the character ceiling lives on {@link AgentEventProjector}.
     */
    public static final long DEFAULT_FLUSH_INTERVAL_MILLIS = 150L;

    private final long conversationId;
    private final long runId;
    private final int turn;
    private final AiEventRepository eventRepository;
    private final AiConversationRepository conversationRepository;
    private final AgentEventProjector projector;
    private final ObjectMapper objectMapper;
    private final LivePublisher livePublisher;
    private final ScheduledExecutorService flushScheduler;
    private final long flushIntervalMillis;
    private final Clock clock;

    /** High-water mark of allocated seqs. Seeded from MAX(seq), so the first row is seed + 1. */
    private final AtomicInteger seq;

    private final ReentrantLock lock = new ReentrantLock();
    private final Deque<RmqAiEvent> queue = new ArrayDeque<>();

    private boolean draining;
    private boolean closed;
    private ScheduledFuture<?> scheduledFlush;
    private int lastSeqCache;

    public AiEventSink(long conversationId, long runId, int turn, int seedSeq,
                       AiEventRepository eventRepository, AiConversationRepository conversationRepository,
                       AgentEventProjector projector, ObjectMapper objectMapper,
                       LivePublisher livePublisher, ScheduledExecutorService flushScheduler) {
        this(conversationId, runId, turn, seedSeq, eventRepository, conversationRepository, projector,
                objectMapper, livePublisher, flushScheduler, DEFAULT_FLUSH_INTERVAL_MILLIS,
                Clock.systemUTC());
    }

    /** Visible for tests: a short flush window makes the time boundary observable without sleeping. */
    AiEventSink(long conversationId, long runId, int turn, int seedSeq,
                AiEventRepository eventRepository, AiConversationRepository conversationRepository,
                AgentEventProjector projector, ObjectMapper objectMapper,
                LivePublisher livePublisher, ScheduledExecutorService flushScheduler,
                long flushIntervalMillis, Clock clock) {
        this.conversationId = conversationId;
        this.runId = runId;
        this.turn = turn;
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
        this.conversationRepository = conversationRepository;
        this.projector = Objects.requireNonNull(projector, "projector");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.livePublisher = livePublisher;
        this.flushScheduler = flushScheduler;
        this.flushIntervalMillis = flushIntervalMillis <= 0 ? DEFAULT_FLUSH_INTERVAL_MILLIS : flushIntervalMillis;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.seq = new AtomicInteger(Math.max(0, seedSeq));
        this.lastSeqCache = Math.max(0, seedSeq);
    }

    /** The highest seq allocated so far, i.e. the value {@code last_seq} is cached at. */
    public int highWaterSeq() {
        return seq.get();
    }

    public long runId() {
        return runId;
    }

    public int turn() {
        return turn;
    }

    /** True when the projector is holding a block that has not been persisted yet. */
    public boolean hasPending() {
        lock.lock();
        try {
            return projector.hasPending();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Projects one provider event: persists whatever the projection produced, and hands the live frame
     * to the publisher with the seq of its persisted twin (0 when it has none, which is the case for
     * deltas — they only become a row at a coalescing boundary).
     */
    public void emit(AgentEvent event) {
        if (event == null) {
            return;
        }
        LiveFrame frame = null;
        lock.lock();
        try {
            if (closed) {
                log.debug("dropping an agent event for run {}: the sink is closed", runId);
                return;
            }
            AgentEventProjector.Projection projection = projector.project(event);
            long liveSeq = 0L;
            if (!projection.persisted().isEmpty()) {
                // A durability boundary: whatever was buffered goes first, so the persisted order is
                // the arrival order. The projector already applied that ordering to this batch.
                cancelScheduledFlush();
                liveSeq = writeAll(projection.persisted());
            }
            if (projector.hasPending()) {
                scheduleFlush();
            }
            if (projection.live() != null) {
                frame = new LiveFrame(liveSeq, projection.live());
            }
        } finally {
            lock.unlock();
        }
        if (frame != null) {
            publish(frame.seq(), frame.event());
        }
    }

    /** Persists the user's turn. Returns the seq it landed on. */
    public int writeUser(String text, String enhancedPrompt) {
        return writeTimeline(new TimelineEvent.User(text, enhancedPrompt));
    }

    /**
     * Persists one timeline event that did not come out of the projector — a degraded-mode notice, the
     * terminal {@code run_status}. Anything the projector is still buffering is written first.
     *
     * @return the seq the event landed on
     */
    public int writeTimeline(TimelineEvent event) {
        Objects.requireNonNull(event, "event");
        lock.lock();
        try {
            if (closed) {
                log.warn("refusing to write a {} event for run {}: the sink is closed",
                        event.getClass().getSimpleName(), runId);
                return seq.get();
            }
            cancelScheduledFlush();
            return writeOne(event);
        } finally {
            lock.unlock();
        }
    }

    /** Persists an error and publishes its live twin, the pair the projector emits for a bad result. */
    public int emitError(String code, String message, String hint) {
        int writtenAt = writeTimeline(new TimelineEvent.Error(code, message, hint));
        publish(writtenAt, new LiveEvent.Error(code, message, hint));
        return writtenAt;
    }

    /**
     * Cuts the coalescing buffer and persists what it held. Called on the time boundary, at every
     * durability boundary, and as step 1 of finalisation — skipping that last one silently loses the
     * final chunk of assistant text, which is the part the user was waiting for.
     *
     * @return the seq the flushed block landed on, or the current high-water mark when nothing was
     *     pending
     */
    public int flushPending() {
        lock.lock();
        try {
            cancelScheduledFlush();
            if (closed) {
                return seq.get();
            }
            return flushBuffer();
        } finally {
            lock.unlock();
        }
    }

    /** Refreshes {@code rmq_ai_conversation.last_seq} to the allocated high-water mark. */
    public void updateLastSeqCache() {
        lock.lock();
        try {
            updateLastSeqCache(seq.get());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ends the writer: cancels the timed flush, persists whatever is still buffered, drains the queue
     * and refreshes the {@code last_seq} cache. Idempotent.
     */
    public void close() {
        lock.lock();
        try {
            cancelScheduledFlush();
            if (!closed) {
                flushBuffer();
                drain();
            }
            closed = true;
            updateLastSeqCache(seq.get());
        } finally {
            lock.unlock();
        }
    }

    /** Writes everything still queued. Callers hold the lock. */
    private void drain() {
        if (draining) {
            // A re-entrant write: the outermost caller drains, so ordering stays FIFO and no callback
            // can interleave with another one's rows.
            return;
        }
        draining = true;
        try {
            RmqAiEvent row = queue.pollFirst();
            while (row != null) {
                insert(row);
                row = queue.pollFirst();
            }
        } finally {
            draining = false;
        }
    }

    private void insert(RmqAiEvent row) {
        try {
            eventRepository.insert(row);
        } catch (DuplicateKeyException exception) {
            // The UNIQUE key is the backstop for a wrongly seeded allocator. Surfacing it is the point:
            // a duplicate timeline position is corruption, not a row to be logged and dropped.
            log.error("timeline position {} of conversation {} is already taken by run {}; "
                            + "the seq allocator was seeded from something other than MAX(seq)",
                    row.getSeq(), conversationId, runId);
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("could not persist the {} event of run {} at seq {}: {}",
                    row.getType(), runId, row.getSeq(), exception.toString());
        }
    }

    /** Allocates a contiguous block of seqs for these events and queues the rows. Callers hold the lock. */
    private int writeAll(List<TimelineEvent> events) {
        List<AiEventCodec.Payload> payloads = new ArrayList<>(events.size());
        for (TimelineEvent event : events) {
            AiEventCodec.Payload payload = serialise(event);
            if (payload != null) {
                payloads.add(payload);
            }
        }
        if (payloads.isEmpty()) {
            return seq.get();
        }
        int high = seq.addAndGet(payloads.size());
        int first = high - payloads.size() + 1;
        LocalDateTime now = LocalDateTime.now(clock);
        for (int index = 0; index < payloads.size(); index++) {
            queue.addLast(row(payloads.get(index), first + index, now));
        }
        drain();
        updateLastSeqCache(high);
        return high;
    }

    /** Flushes the projector's buffer ahead of this event, then writes both. Callers hold the lock. */
    private int writeOne(TimelineEvent event) {
        List<TimelineEvent> batch = new ArrayList<>(2);
        Optional<TimelineEvent> pending = projector.flushPending();
        pending.ifPresent(batch::add);
        batch.add(event);
        return writeAll(batch);
    }

    /** Callers hold the lock. */
    private int flushBuffer() {
        Optional<TimelineEvent> pending = projector.flushPending();
        if (pending.isEmpty()) {
            return seq.get();
        }
        return writeAll(List.of(pending.get()));
    }

    private RmqAiEvent row(AiEventCodec.Payload payload, int assignedSeq, LocalDateTime now) {
        RmqAiEvent row = new RmqAiEvent();
        row.setConversationId(conversationId);
        row.setRunId(runId);
        row.setTurn(turn);
        row.setSeq(assignedSeq);
        row.setType(payload.type());
        row.setPayload(payload.json());
        row.setGmtCreate(now);
        row.setGmtModified(now);
        return row;
    }

    /**
     * Serialises one event through {@link AiEventCodec}, which reads the {@code type} column out of the
     * payload so the two can never disagree.
     *
     * @return null when the event cannot be serialised, which the codec logs and which is skipped here
     *     rather than allowed to fail the run
     */
    private AiEventCodec.Payload serialise(TimelineEvent event) {
        return AiEventCodec.write(objectMapper, runId, event).orElse(null);
    }

    private void scheduleFlush() {
        if (scheduledFlush != null || flushScheduler == null || closed) {
            return;
        }
        try {
            scheduledFlush = flushScheduler.schedule(this::timedFlush, flushIntervalMillis,
                    TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            log.debug("could not schedule the coalescing flush of run {}: {}", runId, exception.toString());
        }
    }

    private void cancelScheduledFlush() {
        if (scheduledFlush != null) {
            scheduledFlush.cancel(false);
            scheduledFlush = null;
        }
    }

    private void timedFlush() {
        lock.lock();
        try {
            scheduledFlush = null;
            if (closed) {
                return;
            }
            flushBuffer();
        } catch (RuntimeException exception) {
            log.warn("timed flush of run {} failed: {}", runId, exception.toString());
        } finally {
            lock.unlock();
        }
    }

    private void updateLastSeqCache(int high) {
        if (conversationRepository == null || high <= lastSeqCache) {
            return;
        }
        RmqAiConversation update = new RmqAiConversation();
        update.setId(conversationId);
        update.setLastSeq(high);
        update.setGmtModified(LocalDateTime.now(clock));
        try {
            conversationRepository.update(update);
            lastSeqCache = high;
        } catch (RuntimeException exception) {
            log.warn("could not refresh the last_seq cache of conversation {}: {}",
                    conversationId, exception.toString());
        }
    }

    private void publish(long assignedSeq, LiveEvent event) {
        if (livePublisher == null) {
            return;
        }
        try {
            livePublisher.publish(assignedSeq, event);
        } catch (RuntimeException exception) {
            // An observer that cannot be written to is that observer's problem; the run keeps going and
            // the persisted timeline stays complete.
            log.warn("could not publish a live frame of run {}: {}", runId, exception.toString());
        }
    }

    /** Where a live frame goes. The seq travels with it so a late observer can deduplicate. */
    @FunctionalInterface
    public interface LivePublisher {

        /**
         * @param seq the seq of the persisted row this frame belongs to, or 0 when it has none
         */
        void publish(long seq, LiveEvent event);
    }

    /** A live frame waiting to be published outside the write lock. */
    private record LiveFrame(long seq, LiveEvent event) {
    }
}
