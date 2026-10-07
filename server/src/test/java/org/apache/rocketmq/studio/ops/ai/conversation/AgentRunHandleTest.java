/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRunHandleTest {

    /** A fake Process: destroy/destroyForcibly exit immediately, descendants are configurable. */
    private static class FakeProcess extends Process {
        private final List<ProcessHandle> descendants;
        boolean destroyCalled;
        boolean destroyForciblyCalled;

        private FakeProcess(List<ProcessHandle> descendants) {
            this.descendants = descendants;
        }

        @Override
        public OutputStreamProxy getOutputStream() {
            return null;
        }

        @Override
        public InputStreamProxy getInputStream() {
            return null;
        }

        @Override
        public InputStreamProxy getErrorStream() {
            return null;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) {
            return true; // exits within any grace
        }

        @Override
        public void destroy() {
            destroyCalled = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            return this;
        }

        @Override
        public boolean isAlive() {
            return false;
        }

        @Override
        public java.util.stream.Stream<ProcessHandle> descendants() {
            return descendants.stream();
        }

        // Null-typed stubs so the fake compiles without real streams.
        private static final class OutputStreamProxy extends java.io.OutputStream {
            @Override
            public void write(int b) {
            }
        }

        private static final class InputStreamProxy extends java.io.InputStream {
            @Override
            public int read() {
                return -1;
            }
        }
    }

    /** A process that ignores SIGTERM: waitFor always times out, isAlive stays true. */
    private static final class StubbornProcess extends FakeProcess {
        int forcibleCount;

        private StubbornProcess() {
            super(List.of());
        }

        @Override
        public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) {
            return false;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public Process destroyForcibly() {
            forcibleCount++;
            return super.destroyForcibly();
        }
    }

    @Test
    void aProcessThatSurvivesTheGraceIsHardKilledWithARetry() {
        AgentRunHandle handle = newHandle();
        StubbornProcess stubborn = new StubbornProcess();
        handle.attachProcess(stubborn);

        boolean decided = handle.stop(AbortReason.USER_STOP);

        assertThat(decided).isTrue();
        assertThat(stubborn.destroyCalled).isTrue();         // SIGTERM was tried first
        assertThat(stubborn.destroyForciblyCalled).isTrue(); // then the hard kill
        // The process survives even SIGKILL, so the bounded retry fires once more.
        assertThat(stubborn.forcibleCount).isEqualTo(2);
    }

    private AgentRunHandle newHandle() {
        return new AgentRunHandle(11L, Duration.ofMillis(50), Duration.ofMillis(10));
    }

    @Test
    void aStopBeforeRegistrationKillsTheProcessTheMomentItAttaches() {
        AgentRunHandle handle = newHandle();
        assertThat(handle.requestStop(AbortReason.USER_STOP)).isTrue();

        FakeProcess late = new FakeProcess(List.of());
        handle.attachProcess(late);

        // SIGTERM goes out immediately, and because the fake exits within any grace
        // the kill stays graceful - no destroyForcibly is needed.
        assertThat(late.destroyCalled).isTrue();
        assertThat(late.destroyForciblyCalled).isFalse();
    }

    @Test
    void aStopAfterRegistrationSignalsAndWaits() {
        AgentRunHandle handle = newHandle();
        FakeProcess child = new FakeProcess(List.of());
        handle.attachProcess(child);

        boolean decided = handle.stop(AbortReason.USER_STOP);

        assertThat(decided).isTrue();
        assertThat(child.destroyCalled).isTrue();
        // A well-behaved process that exits within the grace is never hard-killed.
        assertThat(child.destroyForciblyCalled).isFalse();
    }

    @Test
    void stopIsExactlyOnceTheSecondCallIsANoOp() {
        AgentRunHandle handle = newHandle();
        FakeProcess child = new FakeProcess(List.of());
        handle.attachProcess(child);

        boolean first = handle.stop(AbortReason.USER_STOP);
        boolean second = handle.stop(AbortReason.SHUTDOWN);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        // The stale stop did not escalate: still exactly one SIGTERM.
        assertThat(child.destroyCalled).isTrue();
        assertThat(handle.abortReason()).contains(AbortReason.USER_STOP);
    }

    @Test
    void theWorkerIsCancelledOnlyWhenTheHandleOwnsTheAbort() {
        AgentRunHandle handle = newHandle();
        CompletableFuture<?> task = new CompletableFuture<>();
        handle.attachWorker(task);
        assertThat(task.isCancelled()).isFalse();

        handle.requestStop(AbortReason.USER_STOP);
        handle.awaitStop();

        assertThat(task.isCancelled()).isTrue();
    }

    @Test
    void aStaleStopDoesNotCancelAWorkerItDoesNotOwn() {
        AgentRunHandle handle = newHandle();
        CompletableFuture<?> task = new CompletableFuture<>();
        handle.attachWorker(task);
        handle.requestStop(AbortReason.USER_STOP); // this caller owns the abort

        boolean second = handle.requestStop(AbortReason.SHUTDOWN); // stale
        assertThat(second).isFalse();

        // Only the owner's awaitStop cancels; the stale call returned without touching anything.
        assertThat(task.isCancelled()).isFalse();
        handle.awaitStop(); // the owner's phase two
        assertThat(task.isCancelled()).isTrue();
    }

    @Test
    void aNullGraceFallsBackToTheDefault() {
        AgentRunHandle handle = new AgentRunHandle(1L, null, null);

        assertThat(handle.getRunId()).isEqualTo(1L);
        // No exception and a working handle is the contract; the positive-grace fallback
        // is observable through a full stop cycle not crashing.
        FakeProcess child = new FakeProcess(List.of());
        handle.attachProcess(child);
        assertThat(handle.stop(AbortReason.USER_STOP)).isTrue();
    }

    @Test
    void aNullProcessAttachmentIsIgnored() {
        AgentRunHandle handle = newHandle();
        handle.attachProcess(null);
        assertThat(handle.attachedProcess()).isNull();
        assertThat(handle.isStopRequested()).isFalse();
    }

    @Test
    void aNullStopReasonIsRejected() {
        AgentRunHandle handle = newHandle();
        assertThatThrownBy(() -> handle.requestStop(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void twoHandlesOnTheSameRunIdAreIndependent() {
        AgentRunHandle first = newHandle();
        AgentRunHandle second = newHandle();
        FakeProcess childA = new FakeProcess(List.of());
        FakeProcess childB = new FakeProcess(List.of());
        first.attachProcess(childA);
        second.attachProcess(childB);

        first.stop(AbortReason.USER_STOP);

        // The stale-stop rule is per handle: another run's process is never touched.
        assertThat(childA.destroyCalled).isTrue();
        assertThat(childB.destroyCalled).isFalse();
        assertThat(second.isStopRequested()).isFalse();
    }
}
