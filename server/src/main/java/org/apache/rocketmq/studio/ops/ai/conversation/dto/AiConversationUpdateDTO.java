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
 * Body of {@code PATCH /api/ai/conversations/{id}} — rename and/or archive. Mirrors
 * {@code AiConversationUpdateRequest} in {@code web/src/api/aiConversations.ts}: omitted (null)
 * fields are left untouched, which is why {@code archived} is the boxed {@link Boolean}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationUpdateDTO {

    @Size(max = 512, message = "title must not exceed 512 characters")
    private String title;

    private Boolean archived;
}
