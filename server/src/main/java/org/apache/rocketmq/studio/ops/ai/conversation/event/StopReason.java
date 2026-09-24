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

/**
 * Why a run reached a non-terminal-to-terminal transition other than provider success. Carried by
 * the persisted {@code run_status} event so a reloaded conversation can say why it stopped without
 * joining {@code rmq_ai_run}. Serialised as the uppercase enum name.
 */
public enum StopReason {

    /** The user pressed stop. The child process was killed. */
    USER_STOP,

    /** The server is shutting down and cancelled the run. */
    SHUTDOWN,

    /** The run exceeded its wall-clock budget. */
    TIMEOUT,

    /** The provider stopped because its output budget was exhausted. */
    OUTPUT_LIMIT,

    /** The provider reported an error result, or its stream broke. */
    PROVIDER_ERROR,

    /**
     * The startup reaper found the run still non-terminal after a restart. Nothing survived to
     * report why, so this is the honest terminal reason for anything orphaned by a crash.
     */
    SERVER_RESTART,

    /** The upstream refused the work because it is saturated. */
    OVERLOADED,

    /**
     * Found non-terminal with no live owner and no restart to blame, e.g. the stale-run watchdog
     * reclaimed a run whose executor vanished.
     */
    ORPHANED
}
