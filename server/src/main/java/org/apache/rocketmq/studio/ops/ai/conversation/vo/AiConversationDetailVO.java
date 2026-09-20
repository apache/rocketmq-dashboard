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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * {@link AiConversationVO} plus the run still streaming, so a reload can re-attach instead of
 * showing a dead transcript. Mirrors the frozen TS {@code interface AiConversationDetailVO extends
 * AiConversationVO} in {@code web/src/api/aiConversations.ts}: Jackson serialises the inherited
 * getters into the same flat JSON object, which is what the contract pins — verified by
 * {@code AiConversationVoAssemblerTest}.
 *
 * <p>No {@code @AllArgsConstructor} here on purpose: on a subclass Lombok would include only
 * {@code activeRun}, which is a trap rather than a convenience. The builder is {@code @SuperBuilder}
 * so it spans the parent fields too.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiConversationDetailVO extends AiConversationVO {

    /** The run in QUEUED/RUNNING, or null when the conversation is idle. */
    private AiActiveRunRef activeRun;
}
