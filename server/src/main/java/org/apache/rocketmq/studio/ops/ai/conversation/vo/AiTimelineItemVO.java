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

import java.time.LocalDateTime;

import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;

/**
 * One persisted timeline row as the history API returns it. Mirrors the frozen TS interface
 * {@code TimelineItem} in {@code web/src/api/aiEvents.ts}.
 *
 * <p>{@code runId} is required (never null) because {@code rmq_ai_event.run_id} is NOT NULL: the
 * envelope must not be weaker than the storage, and every event belongs to exactly one run.
 *
 * <p>{@code event} is the DESERIALISED {@link TimelineEvent}, not the raw {@code type} + {@code
 * payload} columns; a row whose payload cannot be decoded is replaced by a warn-level
 * {@code notice} placeholder rather than failing the whole timeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTimelineItemVO {

    private Long id;
    private Integer turn;
    private Integer seq;
    private LocalDateTime createdAt;
    private Long runId;
    private TimelineEvent event;
}
