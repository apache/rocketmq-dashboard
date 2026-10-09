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
package org.apache.rocketmq.studio.ops.ai.conversation.agent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the availability contract of {@link CliBinaryProbe}: exit 0 within the budget means
 * available, everything else (non-zero exit, timeout, I/O failure, interrupt) means unavailable,
 * a timed-out probe is destroyed, and an interrupt restores the thread's interrupt flag.
 */
class CliBinaryProbeTest {

    private final List<ProcessBuilder> appliedEnvironmentTo = new ArrayList<>();
    private final List<ProcessBuilder> startedFrom = new ArrayList<>();

    private CliBinaryProbe probe(Process process) {
        return new CliBinaryProbe(
                (builder, environment) -> appliedEnvironmentTo.add(builder),
                builder -> {
                    startedFrom.add(builder);
                    return process;
                });
    }

    @Test
    void reportsAvailableWhenTheProbeExitsZero() {
        assertThat(probe(new FakeProcess(0, true)).isAvailable("rmqctl")).isTrue();
        assertThat(startedFrom).hasSize(1);
        assertThat(startedFrom.get(0).command()).containsExactly("sh", "-c", "command -v rmqctl");
        // the isolated environment is applied to the exact builder that gets started, and the
        // probe's stderr is merged into stdout so an unread pipe cannot wedge the probe
        assertThat(appliedEnvironmentTo).hasSize(1);
        assertThat(appliedEnvironmentTo.get(0)).isSameAs(startedFrom.get(0));
        assertThat(startedFrom.get(0).redirectErrorStream()).isTrue();
    }

    @Test
    void reportsUnavailableWhenTheProbeExitsNonZero() {
        assertThat(probe(new FakeProcess(1, true)).isAvailable("claude")).isFalse();
    }

    @Test
    void aTimedOutProbeIsDestroyedAndReportedUnavailable() {
        FakeProcess process = new FakeProcess(0, false);
        assertThat(probe(process).isAvailable("qodercli")).isFalse();
        assertThat(process.destroyedForcibly).isTrue();
    }

    @Test
    void anIoFailureMeansUnavailable() {
        CliBinaryProbe probe = new CliBinaryProbe(
                (builder, environment) -> {
                },
                builder -> {
                    throw new IOException("no such shell");
                });
        assertThat(probe.isAvailable("rmqctl")).isFalse();
    }

    @Test
    void anInterruptRestoresTheFlagAndReportsUnavailable() {
        FakeProcess process = new FakeProcess(0, true) {

            @Override
            public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
                throw new InterruptedException("probe interrupted");
            }
        };
        assertThat(probe(process).isAvailable("rmqctl")).isFalse();
        // the probe must not swallow the interrupt: assert and clear the restored flag
        assertThat(Thread.interrupted()).isTrue();
    }

    /** A probe process whose outcome is scripted: the exit code and whether it finishes in time. */
    static class FakeProcess extends Process {

        private final int exitCode;
        private final boolean finishesWithinBudget;
        boolean destroyedForcibly;

        FakeProcess(int exitCode, boolean finishesWithinBudget) {
            this.exitCode = exitCode;
            this.finishesWithinBudget = finishesWithinBudget;
        }

        @Override
        public OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getErrorStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int waitFor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            return finishesWithinBudget;
        }

        @Override
        public void destroy() {
            // nothing to destroy in a scripted process
        }

        @Override
        public Process destroyForcibly() {
            destroyedForcibly = true;
            return this;
        }
    }
}
