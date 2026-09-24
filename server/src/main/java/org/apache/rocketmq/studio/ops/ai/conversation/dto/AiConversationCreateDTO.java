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
package org.apache.rocketmq.studio.ops.ai.conversation.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code POST /api/ai/conversations}. Mirrors {@code AiConversationCreateRequest} in
 * {@code web/src/api/aiConversations.ts} — a frozen cross-language contract, so field names and
 * nullability must not drift. Both fields are optional: a conversation can start unbound and be
 * pinned to an instance later by the run that needs the tools.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationCreateDTO {

    /** The RocketMQ instance the agent's tools are pinned to ({@code rmqctl --instance-id}). */
    @Size(max = 128, message = "instanceId must not exceed 128 characters")
    private String instanceId;

    /** {@code chat} by default; the backend rejects an unknown mode. */
    @Size(max = 16, message = "mode must not exceed 16 characters")
    private String mode;
}
