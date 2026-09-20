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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of the streaming {@code POST /api/ai/conversations/{id}/messages}. Mirrors
 * {@code AiMessageRequest} in {@code web/src/api/aiConversations.ts}.
 *
 * <p>{@code model}/{@code engine}/{@code mode} are optional overrides that the run row snapshots at
 * admission time, so a later settings change never rewrites history. The {@code @Size(max = 8192)}
 * on {@code message} is the same budget {@code AiRunService} enforces server-side so no caller can
 * bypass it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageDTO {

    @NotBlank(message = "message is required")
    @Size(max = 8192, message = "message must not exceed 8192 characters")
    private String message;

    @Size(max = 128, message = "model must not exceed 128 characters")
    private String model;

    @Size(max = 16, message = "engine must not exceed 16 characters")
    private String engine;

    @Size(max = 16, message = "mode must not exceed 16 characters")
    private String mode;

    /** Rewrite the prompt before handing it to the agent; streamed back as {@code source:'enhance'}. */
    private boolean enhance;

    /**
     * Resume the provider session ({@code claude --resume}). Boxed on purpose: the TS request type is
     * {@code resume?: boolean} and the frontend omits the field, so a primitive would deserialise to
     * {@code false} — which {@code AiRunService.resolveResume} reads as an explicit "start a fresh
     * provider session", silently ending multi-turn resuming. {@code null} keeps the conversation's own
     * state, {@code false} forces a fresh session.
     */
    private Boolean resume;
}
