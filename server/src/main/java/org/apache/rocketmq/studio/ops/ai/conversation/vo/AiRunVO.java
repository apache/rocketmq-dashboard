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
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;

import java.time.LocalDateTime;

/**
 * One execution of the agent loop. Mirrors the frozen TS interface {@code AiRunVO} in
 * {@code web/src/api/aiEvents.ts}.
 *
 * <p>{@code engine}/{@code model} are the values snapshotted at admission, so history stays truthful
 * after the user changes settings. {@code status} and {@code stopReason} serialise as the uppercase
 * enum names the TS unions expect; both timestamp columns are the offset-less UTC
 * {@link LocalDateTime} form the frontend's {@code formatUtcDateTime} reads.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRunVO {

    private Long id;
    private Long conversationId;
    private Integer turn;
    private RunStatus status;
    private String engine;
    private String model;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private Integer inputTokens;
    private Integer outputTokens;

    private StopReason stopReason;
    private String errorCode;
    private String errorMessage;
}
