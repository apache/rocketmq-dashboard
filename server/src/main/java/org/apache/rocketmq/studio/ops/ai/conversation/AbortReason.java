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

import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;

/**
 * Why a run is being aborted, discriminated at the point of the decision rather than reconstructed
 * afterwards.
 *
 * <p>The distinction is not cosmetic. Before this existed there was exactly one abort path — a TCP
 * disconnect — so every cancellation was labelled a user cancel, and a redeploy that drained the
 * executor wrote {@code USER_STOP} into {@code rmq_ai_run.stop_reason} for runs nobody had stopped.
 * Carrying the reason on the handle means the {@code @PreDestroy} drain writes {@code SHUTDOWN}, the
 * orphan sweep writes {@code ORPHANED}, and the stop endpoint writes {@code USER_STOP}, each of which
 * is what actually happened.
 *
 * <p>Each constant also fixes the terminal {@link RunStatus} it produces, so no caller has to reason
 * about which aborts are "stopped" and which are "failed": the two a human or the server asked for
 * are {@link RunStatus#STOPPED}, the two that mean the run did not get to finish are
 * {@link RunStatus#FAILED}.
 */
public enum AbortReason {

    /** The user pressed stop. The agent saw the whole budget it was given, and gave it up. */
    USER_STOP(RunStatus.STOPPED, StopReason.USER_STOP, true),

    /**
     * The server is shutting down. Uses a shorter grace than a user stop: the JVM has a fixed budget
     * to finish its own shutdown, and spending three seconds per run on SIGTERM would blow it when
     * sixteen runs are draining at once.
     */
    SHUTDOWN(RunStatus.STOPPED, StopReason.SHUTDOWN, false),

    /** The wall-clock budget ran out. */
    TIMEOUT(RunStatus.FAILED, StopReason.TIMEOUT, true),

    /** The run outlived its owner: the sweep found it non-terminal with no live handle. */
    ORPHANED(RunStatus.FAILED, StopReason.ORPHANED, true);

    private final RunStatus status;
    private final StopReason stopReason;
    private final boolean fullGrace;

    AbortReason(RunStatus status, StopReason stopReason, boolean fullGrace) {
        this.status = status;
        this.stopReason = stopReason;
        this.fullGrace = fullGrace;
    }

    /** The terminal status this abort produces. */
    public RunStatus status() {
        return status;
    }

    /** The value written to {@code rmq_ai_run.stop_reason} and to the persisted {@code run_status}. */
    public StopReason stopReason() {
        return stopReason;
    }

    /**
     * Whether the subprocess gets the configured {@code studio.ai.conversation.stop-grace} between
     * SIGTERM and SIGKILL. False only for {@link #SHUTDOWN}, which uses the handle's shorter
     * shutdown grace.
     */
    public boolean usesFullGrace() {
        return fullGrace;
    }
}
