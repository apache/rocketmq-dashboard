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
package org.apache.rocketmq.studio.ops.ai.conversation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Envelope of {@code GET /api/ai/conversations/{id}/events}. Mirrors the frozen TS interface
 * {@code AiTimelineVO} in {@code web/src/api/aiEvents.ts}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTimelineVO {

    private List<AiTimelineItemVO> items;

    /** Cursor for the next page ({@code seq} of the last returned row), or null at the tail. */
    private Integer nextAfter;

    /** The run still streaming, so a reload can re-attach instead of showing a dead transcript. */
    private AiActiveRunRef activeRun;

    /** Stats of the runs referenced by {@code items}, so a replay can show each run's speed. */
    private List<AiRunStatsVO> runs;
}
