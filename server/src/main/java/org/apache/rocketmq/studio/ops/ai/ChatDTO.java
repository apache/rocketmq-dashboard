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
package org.apache.rocketmq.studio.ops.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatDTO {
    @NotBlank(message = "message is required")
    @Size(max = 16_384, message = "message must not exceed 16384 characters")
    private String message;
    @Size(max = 64, message = "mode must not exceed 64 characters")
    private String mode;
    @Size(max = 256, message = "model must not exceed 256 characters")
    private String model;
    @Size(max = 64, message = "engine must not exceed 64 characters")
    private String engine;
    private boolean enhance;
    @Size(max = 128, message = "conversationId must not exceed 128 characters")
    private String conversationId;
}
