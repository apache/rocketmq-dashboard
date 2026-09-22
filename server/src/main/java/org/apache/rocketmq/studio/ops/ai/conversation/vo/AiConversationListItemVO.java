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
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;

import java.time.LocalDateTime;

/**
 * One row of the conversation list. Mirrors the frozen TS interface
 * {@code AiConversationListItemVO} in {@code web/src/api/aiEvents.ts}.
 *
 * <p>{@code lastRunId}/{@code lastRunStatus} come from the conversation's newest run and are null
 * for a conversation that has never been used; {@code lastRunStatus} serialises as the uppercase
 * enum name, exactly what the TS {@code RunStatus} union expects.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationListItemVO {

    private Long id;
    private String title;
    private String engine;
    private String model;
    private String mode;
    private String instanceId;

    private Long lastRunId;
    private RunStatus lastRunStatus;

    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
