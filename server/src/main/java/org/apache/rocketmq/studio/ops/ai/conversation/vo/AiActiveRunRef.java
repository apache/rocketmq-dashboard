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

/**
 * Minimal reference to the run that is still generating: {@code {id, status}} on the wire. Mirrors
 * the TS {@code AiActiveRunRef} in {@code web/src/api/aiConversations.ts}, which both
 * {@code AiTimelineVO.activeRun} and {@code AiConversationDetailVO.activeRun} point at — one Java
 * type for both so the two inline TS shapes cannot drift apart.
 *
 * <p>{@code status} serialises as the uppercase enum name ({@code QUEUED} or {@code RUNNING};
 * only non-terminal runs are ever exposed here).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiActiveRunRef {

    private Long id;
    private RunStatus status;
}
