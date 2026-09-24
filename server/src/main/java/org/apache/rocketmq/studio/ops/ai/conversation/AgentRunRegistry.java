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

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.ops.ai.conversation.event.LiveEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The in-memory index of what this server is currently generating: {@code runId} to the run's
 * {@link AgentRunHandle} plus every {@link AgentStreamSession} watching it.
 *
 * <h2>Why a registry at all</h2>
 * Generation is decoupled from the HTTP connection, so nothing else can answer these questions: a
 * stop request arrives naming a run and has to find the subprocess to kill; a reconnecting client has
 * to find the live stream to tail; the orphan sweep has to know whether a run that the database still
 * calls RUNNING has an owner in this JVM. A run that is not registered here is not executing here,
 * which — this being a single-instance deployment — means it is not executing at all.
 *
 * <h2>Observers are a list, not a single slot</h2>
 * Two tabs on the same conversation, or one tab that reconnected before the old connection's timeout
 * fired, are both legitimate. Each observer is independent: detaching one leaves the others and the
 * run untouched, and a failing send only removes that observer.
 *
 * <p>{@link CopyOnWriteArrayList} because fan-out iterates while attach/detach mutate, and both are
 * rare relative to reads.
 */
@Slf4j
@Component
public class AgentRunRegistry {

    private final ConcurrentMap<Long, Registration> runs = new ConcurrentHashMap<>();

    /** Publishes a run so observers can attach and a stop can find its subprocess. */
    public void register(long runId, AgentRunHandle handle) {
        Registration replaced = runs.put(runId, new Registration(handle));
        if (replaced != null) {
            log.warn("run {} was registered twice; the previous registration had {} observers",
                    runId, replaced.observers.size());
        }
    }

    /**
     * Drops the run without touching its observers. Used when the caller has already sent their
     * terminal frames itself.
     */
    public void unregister(long runId) {
        runs.remove(runId);
    }

    /**
     * Ends a run for everybody: drops the registration and completes every observer still attached, so
     * each one gets its {@code done} frame and a closed emitter. This is the last step of finalisation,
     * and it is idempotent — finalisation calls it from a {@code finally} that can run after an earlier
     * call already removed the run.
     *
     * @return the number of observers that were completed
     */
    public int finish(long runId) {
        Registration registration = runs.remove(runId);
        if (registration == null) {
            return 0;
        }
        List<AgentStreamSession> observers = new ArrayList<>(registration.observers);
        registration.observers.clear();
        observers.forEach(AgentStreamSession::complete);
        return observers.size();
    }

    /** The handle of a run executing in this JVM. */
    public Optional<AgentRunHandle> handle(long runId) {
        return Optional.ofNullable(runs.get(runId)).map(registration -> registration.handle);
    }

    /**
     * Whether this JVM still owns the run. The orphan sweep uses this to tell a run whose worker died
     * from one that is merely slow: a non-terminal row with no handle here has nobody left to write its
     * terminal state.
     */
    public boolean isLive(long runId) {
        return runs.containsKey(runId);
    }

    /** Run ids this JVM owns. Used by the shutdown drain and by tests. */
    public Set<Long> liveRunIds() {
        return Set.copyOf(runs.keySet());
    }

    public int size() {
        return runs.size();
    }

    /**
     * Adds an observer.
     *
     * @return false when the run is not executing here, which tells the caller to finish the replay
     *     and close the stream instead of waiting for frames that will never come
     */
    public boolean attach(long runId, AgentStreamSession session) {
        Registration registration = runs.get(runId);
        if (registration == null) {
            return false;
        }
        registration.observers.add(session);
        // The run may have been unregistered between the lookup and the add, in which case this
        // observer would wait forever for a terminal frame nobody will send.
        if (!runs.containsKey(runId)) {
            registration.observers.remove(session);
            return false;
        }
        return true;
    }

    /** Removes one observer. Never touches the run or the other observers. */
    public void detach(long runId, AgentStreamSession session) {
        Registration registration = runs.get(runId);
        if (registration != null) {
            registration.observers.remove(session);
        }
    }

    /**
     * Fans one live frame out to every observer.
     *
     * @param seq the seq of the persisted row this frame belongs to, or 0 when it has none; each
     *     observer deduplicates it against what it already replayed
     */
    public void publish(long runId, long seq, LiveEvent event) {
        Registration registration = runs.get(runId);
        if (registration == null || event == null) {
            return;
        }
        for (AgentStreamSession observer : registration.observers) {
            try {
                if (!observer.deliver(seq, event)) {
                    registration.observers.remove(observer);
                }
            } catch (RuntimeException exception) {
                log.warn("dropping an observer of run {} after a failed delivery: {}",
                        runId, exception.toString());
                registration.observers.remove(observer);
            }
        }
    }

    /**
     * Stops a run.
     *
     * @return false when the run is not executing here. That is not an error: the caller distinguishes
     *     "already finished" (the run row is terminal, a 200 no-op) from "somebody else's run is now
     *     active" (a 409) using the row, and only asks here when the row says the run is active.
     */
    public boolean stop(long runId, AbortReason reason) {
        AgentRunHandle handle = handle(runId).orElse(null);
        if (handle == null) {
            return false;
        }
        boolean decided = handle.stop(reason);
        if (decided) {
            log.info("run {} aborted ({}) through the registry", runId, reason);
        }
        return decided;
    }

    /**
     * Aborts every run this JVM owns, in two phases so the grace periods overlap: SIGTERM goes out to
     * all of them first, then each is waited for. A sequential stop would cost one grace period per
     * run and blow the shutdown budget with a full executor.
     *
     * <p>The reason is {@link AbortReason#SHUTDOWN} and not {@link AbortReason#USER_STOP} precisely so
     * that a redeploy does not write "the user stopped this" into sixteen run rows.
     *
     * @return the handles that were signalled, so a caller can wait on them again if it wants to
     */
    public List<AgentRunHandle> drain(AbortReason reason) {
        List<AgentRunHandle> handles = new ArrayList<>();
        runs.values().forEach(registration -> handles.add(registration.handle));
        handles.forEach(handle -> handle.requestStop(reason));
        handles.forEach(AgentRunHandle::awaitStop);
        return handles;
    }

    /** One run's cancellation handle and everybody watching it. */
    private static final class Registration {

        private final AgentRunHandle handle;
        private final CopyOnWriteArrayList<AgentStreamSession> observers = new CopyOnWriteArrayList<>();

        private Registration(AgentRunHandle handle) {
            this.handle = handle;
        }
    }
}
