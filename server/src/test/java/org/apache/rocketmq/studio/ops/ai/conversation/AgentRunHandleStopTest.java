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

import org.apache.rocketmq.studio.ops.ai.ClaudeCodeAgentProvider;
import org.apache.rocketmq.studio.ops.ai.CliProcessEnvironment;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.LlmProperties;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.AgentStreamOptions;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stopping a run has to kill the agent's subprocess tree, which is the one behaviour the previous
 * design could not deliver: it cancelled a {@link Future}, i.e. interrupted a thread, and neither the
 * stdout drain (a virtual thread nobody interrupts) nor {@code rmqctl} (a grandchild, unreachable by
 * {@code destroyForcibly} on {@code claude}) noticed.
 *
 * <p>Two kinds of test, because they fail for different reasons. The mock ones pin the
 * <em>protocol</em>: SIGTERM first, descendants before the parent, one bounded retry, and a second stop
 * that does nothing at all. The real-process one pins the <em>outcome</em>: after a stop there is no
 * child and no grandchild left. Only the second can catch a surviving grandchild, since a mock never had
 * one — and only the first can catch a wrong order, since a real tree dies either way.
 */
class AgentRunHandleStopTest {

    private static final long RUN_ID = 41L;

    @Test
    void stopShouldSignalGracefullyThenKillDescendantsBeforeTheParentTest() throws Exception {
        Process process = mock(Process.class);
        ProcessHandle rmqctl = descendant(1001L);
        ProcessHandle mcpChild = descendant(1002L);
        AgentRunHandle handle = new AgentRunHandle(RUN_ID, Duration.ofSeconds(3));
        when(process.descendants()).thenAnswer(invocation -> Stream.of(rmqctl, mcpChild));
        // SIGTERM ignored, so the graceful phase has to escalate; the wait after SIGKILL succeeds, so
        // the retry is not exercised here.
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false, true);
        handle.attachProcess(process);

        assertThat(handle.stop(AbortReason.USER_STOP)).isTrue();

        InOrder order = inOrder(process, rmqctl, mcpChild);
        // Graceful first: claude flushes its result frame on SIGTERM, which is what leaves a
        // runtime_session_id behind so the next turn can still --resume.
        order.verify(process).destroy();
        order.verify(process).waitFor(3000L, TimeUnit.MILLISECONDS);
        // Descendants before the parent, or the parent gets a window in which it can re-spawn one.
        order.verify(rmqctl).destroyForcibly();
        order.verify(mcpChild).destroyForcibly();
        order.verify(process).destroyForcibly();
        order.verify(process).waitFor(2000L, TimeUnit.MILLISECONDS);
        verify(process, times(1)).destroyForcibly();
    }

    /**
     * A grandchild does not receive the signal sent to its parent, so a graceful exit is not a clean
     * exit: {@code rmqctl} can outlive {@code claude} by as long as its own tool-call budget. The
     * descendants were snapshotted before SIGTERM precisely so they can still be found afterwards.
     */
    @Test
    void gracefulExitShouldStillKillSurvivingDescendantsTest() throws Exception {
        Process process = mock(Process.class);
        ProcessHandle rmqctl = descendant(2001L);
        AgentRunHandle handle = new AgentRunHandle(RUN_ID, Duration.ofSeconds(3));
        when(process.descendants()).thenAnswer(invocation -> Stream.of(rmqctl));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        handle.attachProcess(process);

        handle.stop(AbortReason.USER_STOP);

        verify(process).destroy();
        verify(rmqctl).destroyForcibly();
        verify(process, never()).destroyForcibly();
    }

    @Test
    void subprocessSurvivingSigkillShouldBeSentAnotherOneTest() throws Exception {
        Process process = mock(Process.class);
        AgentRunHandle handle = new AgentRunHandle(RUN_ID, Duration.ofSeconds(3));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);
        handle.attachProcess(process);

        handle.stop(AbortReason.USER_STOP);

        // Bounded, not infinite: one retry after the documented wait, then the handle gives up and says
        // so in the log rather than blocking a request thread forever.
        verify(process, times(2)).destroyForcibly();
    }

    @Test
    void secondStopShouldBeANoOpTest() throws Exception {
        Process process = mock(Process.class);
        AgentRunHandle handle = new AgentRunHandle(RUN_ID, Duration.ofSeconds(3));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        handle.attachProcess(process);

        assertThat(handle.stop(AbortReason.USER_STOP)).isTrue();
        assertThat(handle.stop(AbortReason.SHUTDOWN)).isFalse();
        assertThat(handle.stop(AbortReason.TIMEOUT)).isFalse();

        // One decision, one signal, one reason. A repeated stop must not escalate a grace period that
        // already ran, and must not relabel why the run ended.
        verify(process, times(1)).destroy();
        assertThat(handle.abortReason()).contains(AbortReason.USER_STOP);
        assertThat(handle.isStopRequested()).isTrue();
    }

    @Test
    void shutdownShouldUseAShorterGraceThanAUserStopTest() throws Exception {
        Process userStopped = mock(Process.class);
        Process drained = mock(Process.class);
        when(userStopped.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(drained.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        AgentRunHandle stopHandle = new AgentRunHandle(RUN_ID, Duration.ofSeconds(3));
        AgentRunHandle drainHandle = new AgentRunHandle(RUN_ID + 1, Duration.ofSeconds(3));
        stopHandle.attachProcess(userStopped);
        drainHandle.attachProcess(drained);

        stopHandle.stop(AbortReason.USER_STOP);
        drainHandle.stop(AbortReason.SHUTDOWN);

        verify(userStopped).waitFor(3000L, TimeUnit.MILLISECONDS);
        // A redeploy has a fixed JVM budget; three seconds per run would blow it with a full executor.
        verify(drained).waitFor(AgentRunHandle.DEFAULT_SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void abortReasonShouldDecideTheTerminalStateWrittenToTheRunRowTest() {
        assertThat(AbortReason.USER_STOP.status()).isEqualTo(RunStatus.STOPPED);
        assertThat(AbortReason.USER_STOP.stopReason()).isEqualTo(StopReason.USER_STOP);
        assertThat(AbortReason.SHUTDOWN.status()).isEqualTo(RunStatus.STOPPED);
        // A redeploy is not a user cancel, which is the whole reason the reason is discriminated.
        assertThat(AbortReason.SHUTDOWN.stopReason()).isEqualTo(StopReason.SHUTDOWN);
        assertThat(AbortReason.TIMEOUT.status()).isEqualTo(RunStatus.FAILED);
        assertThat(AbortReason.TIMEOUT.stopReason()).isEqualTo(StopReason.TIMEOUT);
        assertThat(AbortReason.ORPHANED.status()).isEqualTo(RunStatus.FAILED);
        assertThat(AbortReason.ORPHANED.stopReason()).isEqualTo(StopReason.ORPHANED);
        assertThat(AbortReason.SHUTDOWN.usesFullGrace()).isFalse();
        assertThat(AbortReason.USER_STOP.usesFullGrace()).isTrue();
    }

    @Test
    void stopBeforeTheSubprocessRegisteredShouldKillItOnRegistrationTest() throws Exception {
        Process process = mock(Process.class);
        AgentRunHandle handle = new AgentRunHandle(RUN_ID, Duration.ofSeconds(3));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);

        // A stop that lands between admission and ProcessBuilder.start(): there is nothing to kill yet,
        // but the run must not come alive afterwards either.
        assertThat(handle.stop(AbortReason.USER_STOP)).isTrue();
        verify(process, never()).destroy();

        handle.attachProcess(process);

        verify(process).destroy();
        assertThat(handle.attachedProcess()).isSameAs(process);
    }

    @Test
    void stopShouldCancelTheWorkerAfterTheSubprocessIsDeadTest() throws Exception {
        Process process = mock(Process.class);
        @SuppressWarnings("unchecked")
        Future<Object> worker = mock(Future.class);
        AgentRunHandle handle = new AgentRunHandle(RUN_ID, Duration.ofSeconds(3));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        handle.attachProcess(process);
        handle.attachWorker(worker);

        handle.stop(AbortReason.USER_STOP);

        // After, not before: interrupting the worker while the child is still alive would trade the
        // graceful result frame for an interrupt, and with it the session id the next turn resumes.
        InOrder order = inOrder(process, worker);
        order.verify(process).destroy();
        order.verify(worker).cancel(true);
    }

    /**
     * The real thing. {@code sh} starts a backgrounded {@code sleep}, which is a grandchild of this JVM
     * exactly the way {@code rmqctl} is a grandchild of it under {@code claude}. Killing the shell alone
     * leaves that sleep running for its full 30 seconds, reparented to init and invisible to us —
     * precisely the leak the previous implementation had, and one no mock can show.
     */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void stopShouldKillTheRealSubprocessAndItsGrandchildTest() throws Exception {
        AgentRunHandle handle = new AgentRunHandle(RUN_ID, Duration.ofSeconds(3));
        StreamingTestProvider provider = new StreamingTestProvider(
                List.of("sh", "-c", "sleep 30 & sleep 30"), 30);
        AtomicReference<Process> started = new AtomicReference<>();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                try {
                    provider.streamEvents(LlmConfigVO.builder().build(),
                            AgentStreamOptions.builder()
                                    .prompt("list the topics")
                                    .model("qwen3.8-max")
                                    // The provider registers the subprocess through this sink right
                                    // after start(), which is the seam a stop depends on.
                                    .processSink(process -> {
                                        started.set(process);
                                        handle.attachProcess(process);
                                    })
                                    .build(),
                            event -> {
                            });
                } catch (RuntimeException expected) {
                    // The CLI dies with a non-zero exit and no result frame, which the provider reports
                    // as llm.provider.cli_error. That is the point of the test, not a failure of it.
                }
            });

            Process process = awaitProcess(started);
            List<Long> grandchildren = awaitDescendants(process);
            assertThat(process.isAlive()).isTrue();
            assertThat(grandchildren).isNotEmpty();

            assertThat(handle.stop(AbortReason.USER_STOP)).isTrue();

            assertThat(process.waitFor(3, TimeUnit.SECONDS))
                    .as("the subprocess survived the stop")
                    .isTrue();
            assertThat(process.isAlive()).isFalse();
            assertThat(process.descendants()).isEmpty();
            for (long pid : grandchildren) {
                assertThat(awaitGone(pid, Duration.ofSeconds(3)))
                        .as("grandchild pid %s survived the stop", pid)
                        .isTrue();
            }
        } finally {
            worker.shutdownNow();
            Process process = started.get();
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }

    private static ProcessHandle descendant(long pid) {
        ProcessHandle handle = mock(ProcessHandle.class);
        // Real pids, because the kill path deduplicates descendants by pid: two mocks both answering 0
        // would collapse into one and the test would pass while only killing a single grandchild.
        when(handle.pid()).thenReturn(pid);
        return handle;
    }

    private static Process awaitProcess(AtomicReference<Process> started) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (started.get() == null) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the provider never started its subprocess");
            }
            Thread.sleep(20L);
        }
        return started.get();
    }

    private static List<Long> awaitDescendants(Process process) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            List<Long> pids = process.descendants().map(ProcessHandle::pid).toList();
            if (!pids.isEmpty()) {
                return pids;
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("the subprocess never started a grandchild");
    }

    private static boolean awaitGone(long pid, Duration budget) throws InterruptedException {
        long deadline = System.nanoTime() + budget.toNanos();
        while (System.nanoTime() < deadline) {
            if (!ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                return true;
            }
            Thread.sleep(20L);
        }
        return false;
    }

    /**
     * Drives {@code streamEvents} with a fixed command through the provider's own
     * {@code startProcess(ProcessBuilder)} seam, so the run really spawns a process and really registers
     * it with the handle. Without the override it would try to exec a {@code claude} binary.
     */
    private static class StreamingTestProvider extends ClaudeCodeAgentProvider {

        private final List<String> command;
        private final long timeoutSeconds;

        StreamingTestProvider(List<String> command, long timeoutSeconds) {
            super(null, new CliProcessEnvironment(new LlmProperties()));
            this.command = command;
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        protected List<String> buildCommand(LlmConfigVO config, String prompt, String modelOverride) {
            return new ArrayList<>(command);
        }

        @Override
        protected List<String> buildStreamCommand(LlmConfigVO config, AgentStreamOptions options) {
            return new ArrayList<>(command);
        }

        @Override
        protected Map<String, String> childEnv(LlmConfigVO config) {
            return Map.of();
        }

        @Override
        protected String binaryName() {
            return "sh";
        }

        @Override
        protected long streamTimeoutSeconds() {
            return timeoutSeconds;
        }
    }
}
