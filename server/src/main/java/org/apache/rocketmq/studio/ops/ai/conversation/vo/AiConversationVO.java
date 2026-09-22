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
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * One conversation as the REST surface exposes it. Mirrors the frozen TS interface
 * {@code AiConversationVO} in {@code web/src/api/aiEvents.ts} — field names and nullability are a
 * cross-language contract and must not drift.
 *
 * <p>The entity's {@code gmtCreate}/{@code gmtModified} are exposed as {@code createdAt}/
 * {@code updatedAt}. The type stays a plain {@link LocalDateTime} serialised by the shared mapper's
 * default (ISO-8601 without offset, same as {@code MessageQueryHistoryVO.queriedAt}); the frontend's
 * {@code formatUtcDateTime} exists precisely to read that offset-less UTC form.
 *
 * <p>{@code @SuperBuilder} rather than the house {@code @Builder} because
 * {@link AiConversationDetailVO} extends this class (the TS side is {@code interface ... extends}),
 * and a plain {@code @Builder} on a subclass would silently drop the parent fields.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationVO {

    private Long id;
    private String title;
    private String owner;
    private String engine;
    private String model;
    private String mode;

    /** The RocketMQ instance the agent's tools are pinned to; null when the conversation is unbound. */
    private String instanceId;

    /** The upstream agent CLI's own session id ({@code --resume}); null until the first run finishes. */
    private String runtimeSessionId;

    /** Cached high-water mark of {@code rmq_ai_event.seq}; never null on the wire (defaults to 0). */
    private Integer lastSeq;

    private boolean archived;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
