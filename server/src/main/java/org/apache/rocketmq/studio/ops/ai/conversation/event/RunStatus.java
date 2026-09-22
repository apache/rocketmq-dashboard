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
package org.apache.rocketmq.studio.ops.ai.conversation.event;

import java.util.List;

/**
 * Lifecycle state of a run. Serialised as the uppercase enum name, both in
 * {@code rmq_ai_run.status} and on the wire ({@code run_finished.status},
 * {@code run_status.status}).
 *
 * <p>QUEUED and RUNNING are the non-terminal states; the admission rule guarantees at most one of
 * them per conversation. COMPLETED, STOPPED and FAILED are terminal.
 */
public enum RunStatus {

    /** Accepted, waiting for an execution slot. */
    QUEUED,

    /** The agent subprocess is streaming. */
    RUNNING,

    /** The provider reported a successful result. */
    COMPLETED,

    /** Ended without success: user stop, timeout, shutdown, orphan reaper. See {@link StopReason}. */
    STOPPED,

    /** The provider reported an error result, or the stream broke. */
    FAILED;

    /**
     * Non-terminal statuses as column values, for the admission query and the startup reaper.
     * Kept here so every caller agrees on what "still running" means.
     */
    public static final List<String> ACTIVE_STATUSES = List.of(QUEUED.name(), RUNNING.name());

    public boolean isTerminal() {
        return this != QUEUED && this != RUNNING;
    }
}
