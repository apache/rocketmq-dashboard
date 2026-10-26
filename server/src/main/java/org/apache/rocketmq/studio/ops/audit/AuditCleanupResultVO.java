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
package org.apache.rocketmq.studio.ops.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of one audit-log cleanup call. The deleted count alone cannot tell a complete sweep from
 * one that ran out of budget, so the answer carries the ceiling signal next to it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditCleanupResultVO {
    /** Rows removed by this call. */
    private int deleted;
    /**
     * True when the sweep stopped at its per-call ceiling, which means rows older than the retention
     * window may still be stored: the caller must not report a complete cleanup.
     */
    private boolean truncated;
}
