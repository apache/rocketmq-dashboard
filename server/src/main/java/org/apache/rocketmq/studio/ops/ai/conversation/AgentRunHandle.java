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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.ops.ai.AgentProcessTree;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.AgentStreamOptions;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cancellation for one run, and the thing that finally makes "stop" mean something.
 *
 * <h2>The gap this closes</h2>
 * The session this replaces cancelled the run with {@code Future.cancel(true)}, i.e. a thread
 * interrupt. That does not kill an agent subprocess, for three separate reasons:
 * <ol>
 *   <li>{@code ClaudeCodeAgentProvider.spawn} is interruptible only inside {@code process.waitFor};
 *       an interrupt that lands anywhere else is swallowed or turned into an ordinary return.</li>
 *   <li>the stdout drain runs on a virtual thread that nobody interrupts, so it keeps reading a
 *       process nobody killed;</li>
 *   <li>{@code destroyForcibly()} on {@code claude} does not reach {@code rmqctl}, which is a
 *       <em>grandchild</em> — the CLI spawns it as an MCP stdio server — and the JDK does not kill a
 *       process tree for you.</li>
 * </ol>
 * So the handle holds the {@link Process} itself: the provider registers it through
 * {@link AgentStreamOptions.AgentProcessSink} immediately after {@code start()}, and a stop kills the
 * real tree instead of hoping an interrupt lands on the thread that happens to own the child.
 *
 * <h2>Graceful first, and why that is not just politeness</h2>
 * {@code destroy()} sends SIGTERM and the handle waits out
 * {@code studio.ai.conversation.stop-grace} before killing anything. {@code claude} flushes its
 * {@code result} frame on SIGTERM, and that frame carries {@code session_id} — so a stopped run still
 * leaves a usable {@code runtime_session_id} behind and the <em>next</em> turn can {@code --resume}.
 * Going straight to SIGKILL would silently break multi-turn context for every stopped conversation.
 *
 * <h2>Kill order: descendants before the parent</h2>
 * {@link ProcessHandle#descendants()} is pure JDK, needs no {@code setsid} and no image support, and
 * is the only way to reach {@code rmqctl}. The order matters: killing the parent first gives it a
 * window in which it can re-spawn a child, or leave one reparented to init and invisible to us. So
 * every descendant is destroyed forcibly first, then the parent, then a bounded
 * {@value #HARD_KILL_WAIT_SECONDS} s wait, then one more {@code destroyForcibly()} if it is somehow
 * still there.
 *
 * <p>The descendant list is snapshotted <em>before</em> SIGTERM, and the survivors are killed even when
 * the parent exited gracefully. Both follow from the same fact: a signal sent to a process does not
 * reach its children, and once the parent is reaped its children belong to init, where
 * {@code descendants()} can no longer find them. Waiting for the graceful exit before looking would
 * therefore guarantee losing track of {@code rmqctl} in exactly the case the graceful path exists for.
 *
 * <h2>Two phases, so a drain does not cost N grace periods</h2>
 * {@link #stop(AbortReason)} is the single-run path a user's stop button takes. A shutdown drain
 * instead calls {@link #requestStop(AbortReason)} on every handle and only then {@link #awaitStop()}
 * on each: SIGTERM goes out to all runs at once and the grace periods overlap, so the drain costs one
 * grace period rather than one per run.
 *
 * <p>Thread-safe, and {@link #stop(AbortReason)} is idempotent: the second and later calls are no-ops,
 * because aborting is a decision about the run and a run can only be aborted once. A stale stop must
 * never escalate a grace period that is already running, and must never touch a process a later run
 * owns.
 */
@Slf4j
public final class AgentRunHandle implements AgentStreamOptions.AgentProcessSink {

    /** Bounded wait after SIGKILL before giving up and trying once more. */
    static final int HARD_KILL_WAIT_SECONDS = 2;

    /**
     * Grace used when the server itself is going away. Shorter than a user stop on purpose: the JVM
     * has a fixed shutdown budget and spending three seconds per run would blow it.
     */
    static final Duration DEFAULT_SHUTDOWN_GRACE = Duration.ofSeconds(1);

    @Getter
    private final long runId;

    private final Duration stopGrace;
    private final Duration shutdownGrace;

    /** The abort decision. Its CAS is what makes {@link #stop(AbortReason)} exactly-once. */
    private final AtomicReference<AbortReason> abortReason = new AtomicReference<>();

    /**
     * The subprocess, registered by the provider right after {@code start()}. An AtomicReference
     * rather than a plain field because a resume-recovery retry legitimately spawns a second process
     * for the same run, and because a stop can race the registration.
     */
    private final AtomicReference<Process> process = new AtomicReference<>();

    /** The executor task running the provider, cancelled only after the process is really dead. */
    private final AtomicReference<Future<?>> worker = new AtomicReference<>();

    /** Processes already hard-killed, so a registration/stop race cannot wait out the grace twice. */
    private final Set<Process> terminated = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Descendants snapshotted <em>before</em> SIGTERM went out. Once the parent is reaped its children
     * are reparented to init and {@link Process#descendants()} can no longer see them, so a graceful
     * exit would otherwise leave every grandchild alive and unkillable: a grandchild does not receive
     * the signal sent to its parent.
     */
    private final AtomicReference<List<ProcessHandle>> descendantsSnapshot = new AtomicReference<>();

    /** Serialises {@link #finishKill} so a second caller waits for the first instead of skipping it. */
    private final Object killLock = new Object();

    /**
     * @param stopGrace SIGTERM-to-SIGKILL grace for a user stop, from
     *     {@code studio.ai.conversation.stop-grace}
     */
    public AgentRunHandle(long runId, Duration stopGrace) {
        this(runId, stopGrace, DEFAULT_SHUTDOWN_GRACE);
    }

    public AgentRunHandle(long runId, Duration stopGrace, Duration shutdownGrace) {
        this.runId = runId;
        this.stopGrace = positiveOr(stopGrace, Duration.ofSeconds(3));
        this.shutdownGrace = positiveOr(shutdownGrace, DEFAULT_SHUTDOWN_GRACE);
    }

    /**
     * Registers the freshly started subprocess. Called by the provider before it reads any output, so
     * a stop that raced the start still finds something to kill: {@link #requestStop} publishes the
     * reason before reading the process, and this publishes the process before reading the reason, so
     * at least one of the two paths always sees both.
     */
    @Override
    public void attachProcess(Process process) {
        if (process == null) {
            return;
        }
        this.process.set(process);
        AbortReason reason = abortReason.get();
        if (reason == null) {
            return;
        }
        log.info("agent run {} was stopped before its subprocess registered; killing it now", runId);
        signal(process, reason);
        finishKill(process, reason);
        cancelWorker();
    }

    /** This handle as the narrow SPI view a provider is handed. */
    public AgentStreamOptions.AgentProcessSink processSink() {
        return this;
    }

    /** Hands over the executor task so a stop can unwind the drain loops once the child is dead. */
    public void attachWorker(Future<?> worker) {
        this.worker.set(worker);
    }

    /** The subprocess this handle owns, if the provider registered one. Visible for tests. */
    Process attachedProcess() {
        return process.get();
    }

    /** True once an abort has been decided, whatever decided it. */
    public boolean isStopRequested() {
        return abortReason.get() != null;
    }

    /** Why this run was aborted, or empty while it is still running. */
    public Optional<AbortReason> abortReason() {
        return Optional.ofNullable(abortReason.get());
    }

    /**
     * Aborts the run and blocks until the subprocess tree is gone: SIGTERM, the grace period, then
     * descendants before the parent.
     *
     * @return true when this call made the decision; false when the run was already aborted, in which
     *     case this call did nothing at all.
     */
    public boolean stop(AbortReason reason) {
        if (!requestStop(reason)) {
            return false;
        }
        awaitStop();
        return true;
    }

    /**
     * Phase one: record the abort and send SIGTERM. Returns as soon as the signal is out, so a
     * shutdown drain can signal every run before waiting for any of them.
     */
    public boolean requestStop(AbortReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (!abortReason.compareAndSet(null, reason)) {
            return false;
        }
        Process child = process.get();
        if (child == null) {
            log.debug("agent run {} aborted ({}) before its subprocess was registered", runId, reason);
            return true;
        }
        log.info("stopping agent run {} ({}), grace {}", runId, reason, graceFor(reason));
        signal(child, reason);
        return true;
    }

    /**
     * Phase two: wait out the grace, then hard-kill the tree. Safe to call when phase one found no
     * process yet — a late {@link #attachProcess(Process)} kills on registration instead.
     */
    public void awaitStop() {
        AbortReason reason = abortReason.get();
        Process child = process.get();
        if (reason != null && child != null) {
            finishKill(child, reason);
        }
        // Only now: the worker's interruptible waits are inside the provider, and interrupting it
        // while the child is still alive would trade the graceful result frame for an interrupt.
        cancelWorker();
    }

    private void cancelWorker() {
        Future<?> task = worker.get();
        if (task != null) {
            task.cancel(true);
        }
    }

    private void finishKill(Process child, AbortReason reason) {
        synchronized (killLock) {
            if (!terminated.add(child)) {
                return;
            }
            snapshotDescendants(child);
            if (awaitExit(child, graceFor(reason))) {
                log.debug("agent run {} subprocess exited on SIGTERM", runId);
                // The parent took the signal; its children did not. rmqctl usually notices its stdio
                // pipe close and exits, but it may be midway through a tool call with a 60s budget, so
                // whatever is left is killed rather than left to become an invisible orphan.
                AgentProcessTree.destroyDescendants(descendantsToKill(child));
                return;
            }
            hardKill(child);
        }
    }

    /**
     * Descendants first, then the parent, then one bounded retry. See the class javadoc for why the
     * order is not interchangeable: killing the parent first gives it a window to re-spawn a child, or
     * to leave one reparented to init where nothing will ever find it again.
     */
    private void hardKill(Process child) {
        AgentProcessTree.destroyForcibly(child, descendantsToKill(child));
        if (!awaitExit(child, Duration.ofSeconds(HARD_KILL_WAIT_SECONDS))) {
            log.warn("agent run {} subprocess survived SIGKILL for {}s; sending it again",
                    runId, HARD_KILL_WAIT_SECONDS);
            child.destroyForcibly();
        }
    }

    /** SIGTERM, with the descendant list captured first so a graceful exit can still find them. */
    private void signal(Process child, AbortReason reason) {
        snapshotDescendants(child);
        try {
            child.destroy();
        } catch (RuntimeException exception) {
            log.warn("could not signal agent run {} ({}): {}", runId, reason, exception.toString());
        }
    }

    private void snapshotDescendants(Process child) {
        if (descendantsSnapshot.get() != null) {
            return;
        }
        descendantsSnapshot.compareAndSet(null, enumerate(child));
    }

    /**
     * Collects every descendant this handle knows about: the ones snapshotted before SIGTERM plus
     * whatever is enumerable now, which catches a child spawned after the snapshot. Deduplicated by pid
     * because the two lists normally overlap completely.
     */
    private List<ProcessHandle> descendantsToKill(Process child) {
        Map<Long, ProcessHandle> known = new LinkedHashMap<>();
        List<ProcessHandle> snapshot = descendantsSnapshot.get();
        if (snapshot != null) {
            snapshot.forEach(handle -> known.put(handle.pid(), handle));
        }
        enumerate(child).forEach(handle -> known.put(handle.pid(), handle));
        known.values().forEach(descendant -> {
            log.info("killing descendant {} of agent run {}", describe(descendant), runId);
        });
        return List.copyOf(known.values());
    }

    private List<ProcessHandle> enumerate(Process child) {
        return AgentProcessTree.descendants(child, "agent run " + runId);
    }

    private boolean awaitExit(Process child, Duration timeout) {
        try {
            return child.waitFor(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return !child.isAlive();
        }
    }

    private Duration graceFor(AbortReason reason) {
        return reason.usesFullGrace() ? stopGrace : shutdownGrace;
    }

    /**
     * Best-effort label for a log line. Never allowed to throw: a kill that failed because its own log
     * message could not be built would leave the grandchild alive, which is the one thing this class
     * exists to prevent.
     */
    private static String describe(ProcessHandle handle) {
        ProcessHandle.Info info = handle.info();
        String fallback = "pid " + handle.pid();
        return info == null ? fallback : info.command().orElse(fallback);
    }

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }
}
